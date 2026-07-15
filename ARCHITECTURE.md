# Shared-Binary SDUI Architecture

**One cached binary per screen, per-user data injected on the consumer.**

This document describes the architecture that splits every server-driven screen into two
independently-delivered artifacts:

| Artifact | Contents | Changes when | Delivery |
|---|---|---|---|
| **Binary skeleton** (`.rc`) | Layout, styling, static text, actions, *named slots* | You redesign the screen | Downloaded once per `layoutVersion`, cached on device (CDN-cacheable) |
| **Data payload** (JSON) | Per-user values for the named slots | Per user / per refresh | Fetched every launch (~300 bytes) |

The result: 5M users share one ~9 KB binary; the per-launch network cost of a warm start is
two tiny JSON requests. A redesign ships by bumping `layoutVersion` — no app release.

For the underlying Remote Compose mechanics (update channels, density model, DSL catalog),
see [REMOTE_COMPOSE_REFERENCE.md](REMOTE_COMPOSE_REFERENCE.md). This document covers the
architecture built on top of it.

---

## 1. High-level flow (manifest-first)

The client hardcodes exactly **one URL shape** — the manifest. Everything else (binary,
data, analytics) is a link it follows verbatim:

```
1. GET /v1/screens/{key}            ← the ONLY URL the client constructs
      → { "screen", "layoutVersion",
          "binary":    { "url": ".../v1/screens/{key}/binary/{version}" },   ← version-pinned
          "data":      { "url": ".../v1/screens/{key}/data" },
          "analytics": { "url": ".../v1/analytics", "method": "POST" } }

2. GET {binary.url}?density=…       (cache MISS only — URL names immutable bytes)
3. GET {data.url}                   (every launch, ~300 B)

on device:  skeleton → CoreDocument → RemoteDocumentPlayer
                                          │
            data ──→ StateUpdater.setUserLocal*(slot, value) ──→ fills USER: slots → render

4. host actions (taps) → analytics payloads → POST {analytics.url}
```

- **Cold start**: manifest + data + binary. Binary is written to disk.
- **Warm start**: manifest + data only. Binary comes from disk — never re-downloaded while
  the manifest keeps pointing at the same version-pinned URL.
- **Data-only change** (server updates a user's values): next launch is still a cache HIT;
  the same binary renders the new values. Verified end-to-end (see §8).

### Why a manifest?

The indirection is the scaling mechanism. Because the client treats every URL as opaque:

- Binaries can move to a CDN (manifest points at `https://cdn.…/profile-v2.rc`) — no app change.
- Data can move to a dedicated user-service — no app change.
- New capabilities (analytics today; config flags, prefetch hints, A/B variants tomorrow)
  are new manifest fields old clients simply ignore.
- The `/v1/` namespace versions the API itself, so the manifest schema can evolve behind
  `/v2/` without breaking shipped apps.

The version-pinned binary URL doubles as the cache key: it names **immutable bytes**
(served with `Cache-Control: immutable`), so any cache layer — device disk, CDN, OkHttp —
can hold it forever. A redesign changes the URL, which *is* the invalidation.

## 2. The contract: named `USER:` slots

The binary is user-agnostic because every per-user value is a **named variable slot**
declared in the document instead of a baked literal. The player exposes these for override
via `StateUpdater`.

### Server side (declaration)

Declared at the top of `rcDocument { }` in the screen composable
([`ProfileScreen.kt`](feature-profile/src/main/kotlin/com/remotecompose/rc/feature/profile/ProfileScreen.kt)):

```kotlin
fun ProfileScreen(ctx: RenderContext): ByteArray = rcDocument(ctx) {
    val name        = remoteNamedText("USER:profile.name", "")        // RcText
    val kycVerified = remoteNamedInteger("USER:profile.kyc.verified", 0) // RcInteger
    // …

    Column {
        Text(name, fontSize = 16.rsp)                       // bind, don't bake
        Box(Modifier.visibility(kycVerified)) { /* chip */ } // 1=VISIBLE, 0=GONE
    }
}
```

Available slot types (creation DSL, `RcScope`): `remoteNamedText`, `remoteNamedInteger`,
`remoteNamedFloat`, `remoteNamedColor`, `remoteNamedBitmap`, `remoteNamedBitmapUrl`.

### Two rules that make or break it (violations are SILENT no-ops)

1. **`USER:` prefix on the server-side name.** The player resolves overrides by looking up
   `"USER:" + name`, so the document must declare `remoteNamedText("USER:x", …)` while the
   client calls `setUserLocalString("x", …)` — each side adds/expects the prefix itself.
2. **Declare at the document root.** The player only registers named variables reached
   during `applyDataOperations`. A `remoteNamedText` nested inside `Row { Column { … } }`
   never registers, and every override against it silently does nothing. Declare all slots
   first, at the root of `rcDocument { }`, and pass the returned handles (`RcText`,
   `RcInteger`, …) down into components.

### Conditional / variant UI

Named slots can't add or remove components. For per-user structure, bake **every variant**
and toggle with `Modifier.visibility(RcInteger)` (1 = VISIBLE, 0 = GONE, 2 = INVISIBLE).
Example — the KYC row bakes both the green "Verified" chip and the "Complete Now" text;
`profile.kyc.verified` / `profile.kyc.pending` ints show exactly one.

Variable-length lists follow the same pattern: bake a sensible maximum number of row
templates, each with its own visibility int + content slots. Screens whose lists can't be
bounded should stay on per-request generation instead (this is a per-screen choice).

## 3. The wire contract: `ScreenData`

Defined in [`core-ui/…/core/ScreenData.kt`](core-ui/src/main/kotlin/com/remotecompose/rc/core/ScreenData.kt)
(server) and mirrored in the Android app (`ScreenData.kt`). Values are grouped **by slot
type** so the consumer can apply them without knowing any field names:

```json
{
  "strings":    { "profile.name": "Sheen Kumar", "profile.age": "23" },
  "ints":       { "profile.kyc.verified": 1, "profile.kyc.pending": 0 },
  "floats":     { },
  "colors":     { "accent": "#FF6B00" },
  "bitmapUrls": { "profile.avatar": "https://cdn.example.com/u/123.png" }
}
```

- Keys are **bare slot names** (no `USER:` prefix).
- `colors` are ARGB hex strings, parsed with `android.graphics.Color.parseColor`.
- `bitmapUrls` are loaded by the client (Coil) and pushed with `setUserLocalBitmap`.

This is the moat that keeps the client generic: adding a field to a screen = declare the
slot server-side + emit the key in that screen's data. **Zero client changes.**

### Parameterized requests: the `{args, context}` envelope

Screens that need REQUEST inputs (an entity id for a detail screen, navigation context)
use one standard envelope, on both the binary and data endpoints. The client's rule is
mechanical, driven by the manifest link's `method`:

- `"method": "GET"` → render metrics as query params; `args` as one URL-encoded JSON param.
- `"method": "POST"` → JSON body:

```json
{
  "args":    { "txnId": "12345" },
  "context": { "density": 2.0, "widthDp": 360, "heightDp": 718, "locale": "en-IN" }
}
```

The three inputs travel differently on purpose:

| Input | Source | Client's role |
|---|---|---|
| `args` | **Server-authored** — baked into the host action that navigates here (`hostAction("navigate", {"screen":"txn_detail","args":{…}})`) | Relays it verbatim, never parses it |
| `context` | Client capabilities (metrics, locale) | Same shape for every screen, forever |
| identity | Auth headers | Never inside `args` |

Server side, both transports normalize into `ScreenRequest`
([`ScreenRequest.kt`](core-ui/src/main/kotlin/com/remotecompose/rc/core/ScreenRequest.kt)):
`Screen.data(request)` reads `request.args` (**untrusted input — authorize it**; an id is a
lookup key, never an identity claim), and `Screen.render(request)` defaults to ignoring
args entirely — screen arguments parameterize *data*, not *layout*, which is what keeps the
binary shared and cacheable. The envelope shape is part of the `/v1/` contract: new fields
are additive; incompatible changes mean `/v2/`.

Because both endpoints accept both verbs, **flipping a link's method is a manifest-only,
server-side change** — shipped clients follow automatically. Current defaults: binary GET
(keeps the version-pinned URL CDN/immutable-cacheable), data POST.

## 4. Server implementation

### `Screen` interface ([core-ui/…/core/Screen.kt](core-ui/src/main/kotlin/com/remotecompose/rc/core/Screen.kt))

```kotlin
interface Screen {
    val key: String                       // endpoint path + CLI name
    val layoutVersion: Int get() = 1      // bump on redesign → clients re-download
    fun render(ctx: RenderContext): ByteArray
    fun data(): ScreenData = ScreenData() // per-user payload (default: empty)
}
```

Screens self-register via `ServiceLoader` (`META-INF/services/com.remotecompose.rc.core.Screen`);
the server exposes endpoints for every discovered screen with no central wiring.

### Endpoints ([server/…/ScreenServer.kt](server/src/main/kotlin/com/remotecompose/server/ScreenServer.kt))

Every registered screen automatically exposes its v1 surface:

| Endpoint | Returns | Caching |
|---|---|---|
| `GET /v1/screens/{key}` | `ScreenManifest` JSON (layoutVersion + links with methods) | never cached (it *is* the bootstrap probe) |
| `GET`/`POST` `/v1/screens/{key}/binary/{version}` | binary `.rc` skeleton (query params or envelope) | GET responses: `Cache-Control: public, max-age=31536000, immutable` — the URL names immutable bytes |
| `GET`/`POST` `/v1/screens/{key}/data` | `ScreenData` JSON (query params or `{args, context}` envelope) | per-user, not cached |
| `POST /v1/analytics` | `202 {"accepted":true}` | ingest stub — logs the baked host-action payload; swap for a real pipeline server-side only |
| `GET /{key}?density=…` | binary (legacy) | kept for the CLI / manual curl; the app never calls it |

Manifest links are built from the request's `Host` (+ `X-Forwarded-Proto`) header
([`ScreenManifest.kt`](core-ui/src/main/kotlin/com/remotecompose/rc/core/ScreenManifest.kt)),
so the same server works unchanged through adb-reverse, LAN IPs, or an HTTPS tunnel.

Requesting a **stale binary version** (`…/binary/1` when current is 2) answers `409` with
`currentLayoutVersion` — the client's cue to re-fetch the manifest rather than receive a
layout it didn't expect.

The binary is **deterministic** — byte-identical across requests for the same
(version, metrics) — so it is safe to cache at any layer (device disk, CDN, OkHttp cache).

### Example screen wiring (Profile)

- [`ProfileScreen.kt`](feature-profile/src/main/kotlin/com/remotecompose/rc/feature/profile/ProfileScreen.kt)
  — skeleton with 9 slots (7 texts, 2 visibility ints).
- [`ProfileScreenData.kt`](feature-profile/src/main/kotlin/com/remotecompose/rc/feature/profile/ProfileScreenData.kt)
  — domain model + `toScreenData()` mapping. **This mapping and the slot declarations are
  the only two places slot names appear — keep them in sync.**
- [`ProfileScreenProvider.kt`](feature-profile/src/main/kotlin/com/remotecompose/rc/feature/profile/ProfileScreenProvider.kt)
  — `layoutVersion = 2`, `data()` (production: resolve the authenticated user here).

## 5. Client implementation (Android app: `RemoteCompose`)

### Fetch + cache (`RemoteScreenService.kt`)

The app knows one constant: `API_BASE`. Per screen:

```
1. fetchManifest():   GET {API_BASE}/screens/<key>   → links + layoutVersion
2. fetchScreenCached(manifest):
     filesDir/rc-cache/<screen>-v<N>-<metrics>.rc exists?  → return bytes  (cache HIT)
     else GET {manifest.binary.url}?density=…              → write file, evict older versions
3. fetchData(manifest):  GET {manifest.data.url}
4. postAnalytics(json):  POST {manifest.analytics.url}     (async fire-and-forget)
```

All HTTP goes through one OkHttp client (built in `RemoteScreenService.initialize(context)`,
called from `MainActivity.onCreate`) with two inspection interceptors:
- **Chucker** — in-app inspector recording every request/response (debug builds only; the
  `library-no-op` artifact strips it from release). Open via its notification or launcher
  shortcut.
- **HttpLoggingInterceptor** — mirrors full wire traffic to logcat, tag `RC_HTTP`.

### Render + data application (`MainActivity.kt`, `RCFromServer`)

1. Skeleton (cached) and `/data` are fetched **in parallel**. A data failure is non-fatal:
   the skeleton renders with its baked defaults.
2. `applyScreenData(stateUpdater, data)` iterates the typed maps and calls the matching
   `setUserLocalString/Int/Float/Color` per key — fully generic, no screen knowledge.
3. `bitmapUrls` resolve via Coil off the main thread; each finished bitmap is pushed with
   `setUserLocalBitmap` as it arrives.

### ⚠ The `update`-not-`init` rule

`RemoteDocumentPlayer`'s `init` callback fires in the `AndroidView` **factory — before
`setDocument`**. At that point the document's named variables aren't registered and every
`setUserLocal*` is a silent no-op. Apply data in the **`update`** callback instead: it runs
after `setDocument` on every recomposition, so values (re-)apply idempotently — including
after the player is recreated. This was verified against the `alpha13` player sources.

### Live updates after load

`update` receives the `RemoteComposePlayer`; keep its `stateUpdater` to push values at any
time (ticker, WebSocket push, pull-to-refresh that re-fetches only `/data`). Overrides live
in the player instance — after recreation, `update` re-applies the current state.

### Analytics loop

Screens bake analytics via the authoring DSL (`onClick { analyticsApi.postEvent(…) }`,
serialized by `HostActionAnalyticsApi`). At runtime a tap surfaces in `handleNamedAction`
on the `"analytics"` channel; the client forwards the payload **as-is** to
`manifest.analytics.url` (fire-and-forget POST). The client neither builds nor understands
event schemas — event names, params, and restrictions are all authored server-side, so the
analytics taxonomy evolves without app releases too.

## 6. Versioning & cache semantics

- `layoutVersion` is the **only** invalidation signal. Data changes never touch it.
- The version is embedded in the binary URL, so "is my cache fresh?" reduces to "does the
  manifest still point at a URL I have?" — no ETag round-trip needed on the client.
- Client cache key: `(screen, layoutVersion, density, widthDp, heightDp)`. The metrics are
  in the key because generation currently bakes them (density model, REFERENCE §8). If/when
  generation becomes device-independent (`DENSITY_BEHAVIOR_DP` everywhere), metrics drop
  out and one binary serves every device.
- Stale cached versions of a screen are evicted when a new version downloads.
- The manifest is one ~240-byte request per launch. If even that should go, batch it
  (a `/v1/manifest?screens=a,b,c` bootstrap covering several screens in one response) or
  push version bumps via your config system — both are additive, no client change.

## 7. Adding a dynamic screen (checklist)

1. Write the skeleton: declare all `USER:` slots at the root of `rcDocument { }`, bind them
   into components; bake variant UI behind visibility ints.
2. Define the domain model + `toScreenData()` mapping (slot names ↔ keys, one place).
3. Implement `Screen`: `key`, `layoutVersion = 1`, `render()`, `data()`.
4. Register in `META-INF/services/com.remotecompose.rc.core.Screen` (feature module).
5. Verify: `curl /v1/screens/<key>` (manifest), follow its `data` link, and check the
   binary carries the slots — `strings <key>.rc | grep USER:` must list every slot.
6. On redesign later: change the skeleton, **bump `layoutVersion`** — done.

No client step exists. That's the point.

## 8. Verified behavior (2026-07, Pixel 9 Pro XL emulator + physical device)

| Scenario | Result |
|---|---|
| Cold start | manifest → data ∥ `binary/2` download (8813 B); all 9 slots rendered with fetched values |
| Warm start | manifest + data only; cache HIT — no binary request; identical render |
| Data changed server-side, same `layoutVersion` | cache HIT (binary untouched) — new name rendered, KYC flipped Verified chip → "Complete Now" via visibility ints |
| Binary determinism | byte-identical across fetches; served `Cache-Control: immutable`; stale version URL → `409 {currentLayoutVersion}` |
| Analytics loop | tapping the Age row fired the baked `profile_row_clicked` payload → client POSTed it to `manifest.analytics.url` → server ingested it (`202`) |
| Wire visibility | every request/response in Chucker (in-app) and logcat `RC_HTTP` (full headers + JSON bodies; binary body size logged) |

## 9. Constraints & future work

- **Fixed-count structure**: named slots can't create components. Bounded lists → baked
  templates + visibility; unbounded lists → per-request generation for that screen.
- **Delta channel** (`applyUpdate`, update-docs): complementary bulk-refresh mechanism for
  same-shape payloads; ID-aligned and server-generated. Not used yet — named slots cover
  the current need.
- **Density**: cache key still carries device metrics (§6). Aligning the DSL path and the
  dashboard JSON path on one density behavior removes this (REFERENCE §8.6).
- **Version skew**: server generates with `remote-creation` **alpha14**, the app plays with
  **alpha13**. Fine today; keep both on the same alpha when upgrading.
- **`/data` auth**: the demo serves static defaults; production must resolve the
  authenticated user in `Screen.data()` and treat the payload as PII (no CDN caching).
- **Analytics ingest is a stub**: `POST /v1/analytics` logs and acks. Production swaps the
  handler for a queue/warehouse write — the manifest link and client stay unchanged.
- **Manifest batching**: one manifest request per screen per launch today; a multi-screen
  bootstrap (`/v1/manifest?screens=…`) is a straightforward additive follow-up.
