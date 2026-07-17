# Remote Compose Server — API Reference

Server-driven-UI backend serving RemoteCompose `.rc` binaries and per-user data.

**Base URLs**

| Environment | Base URL |
| --- | --- |
| Production (EC2, behind nginx) | `https://devtestingmyjarapp.link/rc` |
| Local | `http://localhost:8080` |

All endpoints send permissive CORS headers (`Access-Control-Allow-Origin: *`) and answer
`OPTIONS` preflights with `204`. Errors are always JSON: `{"error": "<message>"}`.

**Registered screens:** `button`, `dummy`, `greeting`, `image_list`, `jar_button`,
`jar_image`, `profile`. Adding a screen to `ScreenRegistry` exposes its full v1 surface
automatically.

---

## 1. Health check

```
GET /health
```

**Request**

```bash
curl https://devtestingmyjarapp.link/rc/health
```

**Response — 200**

```json
{"status":"ok","screens":["button", "dummy", "greeting", "image_list", "jar_button", "jar_image", "profile"]}
```

---

## 2. Screen manifest (the consumer's single entry point)

```
GET /v1/screens/{key}
```

The **only** URL a consumer constructs. Returns the screen's `layoutVersion` and the links
for everything else — binary, data, analytics. The consumer follows those links verbatim
and never builds them itself, so the backend can relocate artifacts without a client release.

**Request**

```bash
curl https://devtestingmyjarapp.link/rc/v1/screens/profile
```

**Response — 200**

```json
{
  "screen": "profile",
  "layoutVersion": 3,
  "binary":    { "url": "https://devtestingmyjarapp.link/rc/v1/screens/profile/binary/3", "method": "GET" },
  "data":      { "url": "https://devtestingmyjarapp.link/rc/v1/screens/profile/data",     "method": "POST" },
  "analytics": { "url": "https://devtestingmyjarapp.link/rc/v1/analytics",                "method": "POST" }
}
```

**Errors** — `404` unknown sub-path, `405` non-GET.

---

## 3. Screen binary (version-pinned, immutable)

```
GET  /v1/screens/{key}/binary/{version}?density=&width=&height=
POST /v1/screens/{key}/binary/{version}      (body: ScreenRequest envelope)
```

Returns the compiled `.rc` skeleton for that `layoutVersion`. The URL identifies immutable
bytes: GET responses carry `Cache-Control: public, max-age=31536000, immutable`. Client
render metrics go in query params (GET) or the envelope (POST).

**Request**

```bash
curl "https://devtestingmyjarapp.link/rc/v1/screens/profile/binary/3?density=2.625&width=412&height=915" -o profile.rc
```

**Response — 200** — binary body (`Content-Type: application/octet-stream`,
`Content-Disposition: attachment; filename="profile-v3.rc"`).

**Response — 409 (stale version)** — the client's manifest is outdated; re-fetch it:

```json
{"error":"Stale layoutVersion 2","currentLayoutVersion":3}
```

**Errors** — `400` missing/invalid version or render failure, `405` other methods.

---

## 4. Per-user screen data

```
POST /v1/screens/{key}/data        (body: ScreenRequest envelope — preferred)
GET  /v1/screens/{key}/data?density=&args=<url-encoded JSON>
```

Fills the binary's named `USER:` slots. One cached skeleton per layout version; this JSON
carries everything user-specific.

**Request**

```bash
curl -X POST https://devtestingmyjarapp.link/rc/v1/screens/profile/data \
  -H "Content-Type: application/json" \
  -d '{
        "args": { "userId": "u_12345" },
        "context": { "density": 2.625, "widthDp": 412, "heightDp": 915, "locale": "en-IN", "appVersion": "8.2.1" }
      }'
```

**Response — 200** (`ScreenData`: values grouped by slot type; ints double as visibility
toggles — 1 VISIBLE, 0 GONE, 2 INVISIBLE):

```json
{
  "strings":    { "user_name": "Hubert Blaine", "phone": "+91 8861256789" },
  "ints":       { "kyc_badge_visible": 1 },
  "floats":     { "savings_progress": 0.72 },
  "colors":     { "accent": "#7029CC" },
  "bitmapUrls": { "avatar": "https://cdn.myjar.app/userProfile/avatar_u12345.webp" }
}
```

**Errors** — `400` bad envelope or handler failure, `405` other methods.

---

## 5. Analytics ingest

```
POST /v1/analytics
```

Accepts the payloads baked by the authoring analytics DSL and forwarded by the consumer.
Current implementation logs and acknowledges (ingest stub).

**Request**

```bash
curl -X POST https://devtestingmyjarapp.link/rc/v1/analytics \
  -H "Content-Type: application/json" \
  -d '{"event":"profile_cta_clicked","screen":"profile","layoutVersion":3,"ts":1768638000000}'
```

**Response — 202**

```json
{"accepted":true}
```

**Errors** — `405` non-POST.

---

## 6. Parse JSON document → binary (ad-hoc, no registered screen)

```
POST /v1/parse
```

Body is a **RemoteCompose JSON document** (the official androidx-main
`{header, resources, root}` format). Returns the compiled `.rc` bytes directly.
URL `bitmap` ids are supported (see notes below).

**Request**

```bash
curl -X POST https://devtestingmyjarapp.link/rc/v1/parse \
  --data-binary @home.json -o home.rc
```

with `home.json`:

```json
{
  "header": { "apiLevel": 8, "width": 360, "height": 640, "densityBehavior": 2 },
  "root": {
    "column": {
      "modifiers": [ "fillMaxSize", { "background": "#1D1829" } ],
      "children": [
        { "text": { "value": "Hello Remote Compose", "fontSize": 18, "fontWeight": 700, "color": "#FFFFFF" } },
        { "spacer": { "modifiers": [ { "height": 12 } ] } },
        { "bitmap": { "id": "https://cdn.myjar.app/logos/hamburger/wsHamburger.webp", "scale": "fit",
                      "modifiers": [ { "width": 48 }, { "height": 48 } ] } }
      ]
    }
  }
}
```

**Response — 200** — binary body (`Content-Type: application/octet-stream`,
`Content-Disposition: attachment; filename="document.rc"`).

**Response — 400** (parser error, message from the androidx parser):

```json
{"error":"Expected a ':' after a key at 9 [character 10 line 1]"}
```

### Document format notes

- `header.densityBehavior`: `0` legacy/mixed (default), `1` raw pixels, `2` dp (density
  applied by the player — use this). Requires `header.apiLevel >= 8`.
- `bitmap` components: `id` may be an `http(s)` URL (server extension — the player fetches
  it at render time through the app's image loader; a 512² slot is declared per image).
  `scale` is optional: `fit` (default), `crop`, `none`, `fill_bounds`, `fill_width`,
  `fill_height`. Optional `alpha` (0.0–1.0). An empty `id` renders an invisible
  placeholder box that keeps the declared size.
- Give the root container `"fillMaxSize"` so its background paints the whole screen.

---

## 7. Parse and save on the server

```
POST /v1/parse/{name}
```

Same body as `/v1/parse`, but saves the compiled binary as `{name}.rc` in the server's
documents directory (`$RC_DOCUMENTS_DIR`, default `./rc-documents`; on EC2 a host volume)
and returns where it landed. **Re-posting the same name replaces that file in place** —
no duplicates, no versioning.

`{name}` must match `[A-Za-z0-9_-]+` (a trailing `.rc` is accepted and stripped).

**Request**

```bash
curl -X POST https://devtestingmyjarapp.link/rc/v1/parse/home_screen \
  --data-binary @home.json
```

**Response — 200**

```json
{
  "name": "home_screen",
  "bytes": 3311,
  "path": "/opt/server/rc-documents/home_screen.rc",
  "url": "https://devtestingmyjarapp.link/rc/v1/documents/home_screen.rc"
}
```

**Errors** — `400` invalid name or parser error, `405` non-POST.

---

## 8. List saved documents

```
GET /v1/documents
```

**Request**

```bash
curl https://devtestingmyjarapp.link/rc/v1/documents
```

**Response — 200**

```json
{"documents":["home_screen", "weather_widget"]}
```

---

## 9. Fetch a saved document

```
GET /v1/documents/{name}.rc
```

**Request**

```bash
curl https://devtestingmyjarapp.link/rc/v1/documents/home_screen.rc -o home_screen.rc
```

**Response — 200** — binary body (`Content-Type: application/octet-stream`,
`Content-Disposition: attachment; filename="home_screen.rc"`).

**Response — 404**

```json
{"error":"No saved document named 'home_screen'"}
```

---

## 10. Legacy screen binary (CLI / manual use only)

```
GET /{key}?density=&width=&height=
```

Pre-manifest convenience endpoint; not used by the app.

**Request**

```bash
curl "https://devtestingmyjarapp.link/rc/profile?density=2.625" -o profile.rc
```

**Response — 200** — binary body.

---

## The ScreenRequest envelope

Every v1 link that declares `method: POST` accepts exactly this body; GET variants take
the same data as query params. All fields optional.

```json
{
  "args":    { "any": "screen-specific JSON, relayed verbatim by the client" },
  "context": {
    "density": 2.625,
    "widthDp": 412,
    "heightDp": 915,
    "safeAreaTop": 24,
    "safeAreaBottom": 48,
    "locale": "en-IN",
    "appVersion": "8.2.1"
  }
}
```

`args` is untrusted input (it transits the client) — screens treat values as lookup keys,
never identity claims.

---

## Deployment notes

- Image: `ghcr.io/rohitjar/remote-compose-server:latest`, rebuilt by GitHub Actions on
  every push to `main`; Watchtower on the EC2 instance redeploys the container within ~60s.
- `RC_DOCUMENTS_DIR` — where `/v1/parse/{name}` saves binaries. Mount a host volume so
  files survive redeploys: `-v /home/ec2-user/rc-documents:/opt/server/rc-documents`.
- `RC_PUBLIC_BASE_URL` — base URL used in manifest links and saved-document URLs when the
  server sits behind a path-prefixed proxy (set to `https://devtestingmyjarapp.link/rc`).
  Alternative: have the proxy send `X-Forwarded-Prefix`.