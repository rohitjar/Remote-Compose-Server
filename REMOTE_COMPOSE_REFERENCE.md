# Remote Compose — Complete Technical Reference

> AndroidX Remote Compose, `androidx.compose.remote:*:1.0.0-alpha13`.
> Distilled from a full read of the library sources (creation + player + core).
> Document API level: **8**. Wire version: **1.1.0**.

This is a standalone reference for building server-driven UI (SDUI) with Remote Compose: how it works internally, the full authoring (creation) and runtime (player) API surface, the three ways to change a running document, the gotchas we proved empirically, and the architecture options for scaling.

> **See also:** [ARCHITECTURE.md](ARCHITECTURE.md) — the shared-binary architecture this
> project implements on top of these mechanics (one cached binary per layout version,
> per-user data injected client-side via named `USER:` slots).

---

## 0. TL;DR & mental model

**Remote Compose = a binary UI format + a player.** You author UI on a server (or any JVM) with a Compose-like DSL, serialize it to a compact binary (`.rc`, typically ~0.5–2 KB), ship the bytes to a device, and a player inflates and renders them. The player can also run **on-device logic** baked into the document (math, conditionals, loops, animations, expressions) — so a document is not a static snapshot; it's a little program.

```
   ┌─────────────── SERVER / JVM (generation) ───────────────┐
   │ rcDocument { Box { Text(...); Image(...) } }             │
   │     │ RcScope DSL → RemoteComposeWriter                  │
   │     ▼                                                    │
   │ binary .rc (ByteArray, ~0.5–2 KB)                        │
   └───────────────────────────│─────────────────────────────┘
                               │  (HTTP / asset / push)
   ┌───────────────────────────▼─────────────────────────────┐
   │ DEVICE (player)                                          │
   │  CoreDocument.initFromBuffer(bytes)                      │
   │      → inflate components + register variables           │
   │  RemoteComposePlayer.setDocument(...)                    │
   │      → initializeContext + applyDataOperations           │
   │  paint loop: evaluate expressions → draw → needsRepaint  │
   │  touch → onClick → HostNamedActionOperation → host cb    │
   └──────────────────────────────────────────────────────────┘
```

**The single most important fact for SDUI:** there are **three independent channels** to get content into a *running* player (detailed in §2):

1. **Full replace** — `setDocument(bytes)` re-inflates the entire UI. Change anything (fields, layout, logic) from the backend, no app release.
2. **Delta update** — `setDocument(updateBytes)` → `applyUpdate(delta)` patches data values *by ID* in place, no re-inflation (cheap). Bounded: data-only, same structure.
3. **Named overrides** — `StateUpdater.setUserLocal{String,Int,Float,Color,Bitmap}(name, value)` sets a named slot live.

Pick the channel by **change cadence** (§6).

---

## 1. Lifecycle & pipeline (with call chain)

### 1.1 Generation (server / JVM)
- **Entry:** `createRcBuffer(profile, vararg tags, content)` / `createRawRcBuffer(...)` — `creation/dsl/RcDocCreator.kt`. `createRcBuffer` wraps content in a root; `createRawRcBuffer` does not.
- Internally builds a `RemoteComposeWriter(profile, *tags)`, runs the `RcScope.() -> Unit` DSL against a `RcScopeImpl(writer)`, then `writer.buffer()` / `bufferSize()` → `ByteArray`.
- **Header tags** (`RemoteComposeWriter.HTag(Header.X, value)`): `DOC_WIDTH`, `DOC_HEIGHT`, `DOC_DENSITY_AT_GENERATION`, `DOC_DENSITY_BEHAVIOR` (`CoreDocument.DENSITY_BEHAVIOR_PIXELS` / `DENSITY_BEHAVIOR_DP`), `DOC_PROFILES`, `DOC_CONTENT_DESCRIPTION`, `DOC_DESIRED_FPS`.
- Each DSL call emits one or more **operations** into a `WireBuffer`. E.g. `remoteNamedText("USER:x","")` → `addNamedString` → a `NamedVariable` op (registers name↔id) **plus** a `TextData` op (initial value).

### 1.2 Inflation (device)
- `CoreDocument.initFromBuffer(RemoteComposeBuffer)` (`core/.../CoreDocument.java:949`):
  - reads the operation stream, then `inflateComponents(mOperations)` restructures the flat op list into a component tree (Box/Column/Row/TextLayout/…).
- `RemoteComposePlayer.setDocument(RemoteDocument)` (`player_view/.../RemoteComposePlayer.java:260`) then:
  - `value.reinflate()`, version check `canBeDisplayed(...)`,
  - if `isUpdateDoc()` → `updateDocument(value)` (delta path, §2.2) and return,
  - else `mInner.setDocument(value)` → which calls **`CoreDocument.initializeContext(mARContext, ...)`** and **`applyDataOperations(mARContext)`**.
- `initializeContext` (`CoreDocument.java:1170`): resets state, sets `context.mRemoteComposeState`, then `applyDataOperations` runs ops in **DATA mode**: `registerVariables(context, ops)` (subscribes `VariableSupport` ops as listeners) then `applyOperations(context, ops)` which calls **`op.apply(context)`** for every op (recursing into containers). This is where `NamedVariable.apply()` → `context.loadVariableName(name, id, type)` populates the name↔id registry, and `TextData/BitmapData/FloatConstant/...apply()` cache initial data.

### 1.3 Render & repaint loop
- `CoreDocument.paint(context, theme)` (`CoreDocument.java:1647`): runs ops in PAINT mode — `VariableSupport.updateVariables(context)` re-reads variables, expressions evaluate, layout measures, draw ops hit the canvas.
- `needsRepaint()` returns ms until next frame (animations, loops, conditionals, time/sensor variables drive this; `-1` = idle).
- **Data overrides → repaint:** a state change calls `RemoteComposeState.updateListeners(id)` → `VariableSupport.markDirty()` on each subscribed op; the view `invalidate()`s; next `paint` re-reads. (E.g. a `CoreText` registers `listensTo(textId)` and re-reads `getText(id)` in `updateVariables`.)

### 1.4 Interaction
- Touch → `RemoteComposeView.onTouchEvent` → `performClick()` → `CoreDocument.onClick(context, x, y)` hit-tests components.
- A clicked component with an `onClick { hostAction("name") }` modifier runs a **`HostNamedActionOperation`** → `context.runNamedAction(textId, value)` → `AndroidRemoteContext.runNamedAction` resolves `textId`→string → `CoreDocument.runNamedAction(name, value)` → notifies registered `ActionCallback`s → surfaces in the Compose `onNamedAction(name, value, stateUpdater)` callback.
- `onClick` can ALSO run **on-device** `setValue(...)` mutations with **no host round-trip** (§3.3).

---

## 2. The three runtime update channels (the crux of SDUI)

| Channel | API | Re-inflate? | Can change… | Cost | Use for |
|---|---|---|---|---|---|
| **Full replace** | `setDocument(byte[])` | Yes | **anything** (fields, layout, logic) | full inflate | new screen / new layout from backend |
| **Delta update** | `setDocument(updateBytes)`→`applyUpdate` | No | data **values only**, by ID | cheap | bulk value refresh, same shape |
| **Named override** | `StateUpdater.setUserLocal*` | No | one named slot's value | cheapest | live per-field values |

### 2.1 Full document replacement — "change anything, no app release"
`RemoteComposePlayer.setDocument(byte[] | InputStream | RemoteDocument)`. Re-inflates and swaps the whole UI. The consumer is a **dumb renderer**: it just hands bytes to the player. New fields, new layout, new on-device logic — all server-side. This is the channel that makes RC true backend-driven UI without a Play Store release.

```kotlin
val bytes: ByteArray = api.fetchScreen("profile", userId)   // backend-generated .rc
player.setDocument(bytes)                                    // entire UI updates
```

### 2.2 Delta update — `CoreDocument.applyUpdate(delta)` (ID-matched, data-only)
A document whose header sets **`setUpdateDoc(true)`** (`CoreDocument.java:2059`) is treated as a delta. `setDocument` routes it to `updateDocument` → `applyUpdate(delta)` (`CoreDocument.java:applyUpdate`):
- It builds ID→op maps over the **live** doc for `TextData`, `BitmapData`, `FloatConstant`, `IntegerConstant`, `LongConstant`, `DataListFloat`.
- For each op in the delta with a **matching id**, it calls `op.update(...)` + `markDirty()`.
- **Bounded:** only those data-op types, only IDs that already exist; **no** add/remove components, **no** layout change. The delta's IDs must line up with the original document's IDs (so generate both from the same source/seed).
- **Why it's useful:** patch many values at once (a whole new data payload) without re-inflating the tree — cheaper than full replace, broader than one named slot.

### 2.3 Named-variable overrides — `StateUpdater`
`RemoteComposePlayer.getStateUpdater()` → `StateUpdater` (impl `StateUpdaterImpl` wrapping the `RemoteContext`). Methods:
```
setUserLocalString(name, String?)   setUserLocalInt(name, Integer?)
setUserLocalFloat(name, Float?)     setUserLocalColor(name, Integer?)
setUserLocalBitmap(name, Bitmap?)   setNamedLong(name, Long?)
// null value clears the override (except color)
```
Each prepends the **`USER:`** domain (`getUserDomainString(name) = RemoteDomains.USER + ":" + name`) and calls `RemoteContext.setNamed*Override("USER:"+name, value)`. Resolution is an exact lookup in `AndroidRemoteContext.mVarNameHashMap`. `RemoteComposePlayer` also exposes direct `setUserLocal*` / `setSystemLocalString` / `setLocalString(domain,name,...)` and `clearUserLocal*` variants.

> **Two gotchas that make or break this — see §5.** (USER: prefix on the server name, and root-level declaration.)

---

## 3. Creation / authoring DSL catalog

Root scope: **`RcScope`** (`creation/dsl/RcScope.kt`, impl `RcScopeImp.kt`). Entry: `rcDocument { ... }` (project helper) → `createRcBuffer`.

### 3.1 Layout composables
| Composable | Notes |
|---|---|
| `Box(modifier, horizontal, vertical) { }` | anchor-positioned container |
| `FitBox(...) { }` | wraps content size |
| `Column(modifier, horizontal, vertical) { }` | vertical stack (`RcColumnScope`: `weight`) |
| `Row(modifier, horizontal, vertical) { }` | horizontal stack (`RcRowScope`: `weight`) |
| `Flow(modifier, …, maxItemsInEachRow, maxLines) { }` | wrapping flow layout |
| `CollapsibleColumn/Row(...) { }` | priority-based collapsing (`horizontal/verticalCollapsiblePriority`) |
| **`StateLayout(stateIndex: RcInteger, modifier) { }`** | **renders one of N children by an integer variable — data-driven structure switch** |
| `Custom(config, properties, modifier) { }` | platform-native host |
| `Spacer(modifier)` | filler |
| `RcRoot { }` | explicit document root |

Positioning enums: `RcHorizontalPositioning`, `RcVerticalPositioning`, `RcColumnVerticalPositioning`, `RcRowHorizontalPositioning` (Start/Center/End/SpaceBetween/…).

### 3.2 Text
- `Text(text: String, modifier, color, fontSize: RcSp, fontWeight, textAlign, overflow, maxLines) { }`
- `Text(text: RcText, …)` — **binds to a variable id** (live; this is how named-slot text updates).
- Typed color overloads: `Text(text, color: RcColor, …)`, `Text(text, color: RcColorValue, …)`.
- Enums: `RcTextAlign` (Start/Center/End/…), `RcTextOverflow` (Clip/Ellipsis/Visible).

### 3.3 Images & Canvas
- `Image(image: RcImage, modifier, contentDescription, contentScale: RcContentScale, alpha)`.
- `Canvas(modifier) { RcCanvasScope }` — full vector drawing (shapes, paths, text-on-path, bitmaps, transforms, clip, gradients, shaders). See §3.9.

### 3.4 State & variable creation
**Text/strings**
```
remoteText(s): RcText                         remoteNamedText(name, s): RcText
remoteArrayOf(vararg s): RcTextList           textLookup(list, index: RcFloat|RcInteger): RcText
textMerge(a,b) / a + b / "x" join t           RcText.substring(start,len)
createTextFromFloat(value, whole, decimal, flags) / RcFloat.format(whole,decimal,flags)
RcText.transform(start,len, op)  // UPPERCASE/LOWERCASE/CAPITALIZE/TRIM
textLength(t): RcFloat            textMeasure(t, mode): RcFloat
```
**Color**
```
remoteColor(int): RcColor             remoteColorValue(int|long): RcColorValue
remoteNamedColor(name, int|long): RcColor
remoteThemedColor(light, dark)        // day/night pair (Int, RcColor, or named-theme overloads)
remoteColorExpression(alpha, hue: RcFloat, sat, value): RcColor
getColorAttribute(base: RcColor, type): RcFloat   // extract H/S/B/R/G/B/A
```
**Float / Integer / Bool**
```
remoteFloat(f) / remoteNamedFloat(name,f)        remoteInteger(i) / remoteNamedInteger(name,i)
remoteLong(l)  remoteBoolean(b): RcInteger  remoteBool(b): RcBool
remoteFloatArray(FloatArray) / remoteFloatList(...) / remoteDynamicFloatArray(size)
remoteFloatMap(keys: Array<String>, values: FloatArray)
setArrayValue(arr, index, value)    arr[index] (get/set)
```

### 3.5 On-device math & expressions (`RcFloat`, `creation/dsl/RcFloat.kt`)
All evaluated **on the device**, no host round-trip:
- Arithmetic: `+ - * / %`, `unaryMinus`.
- Compare/clamp: `min`, `max`, `clamp(min,max,v)`, `step`, `smoothStep`.
- Trig: `sin cos tan asin acos atan atan2`; `toDeg toRad`.
- Math: `sqrt cbrt pow exp log log2 ln abs sign ceil floor round fraction inverse hypot mad copySign`.
- Conditional: **`ifThenElse(cond, a, b)`** / `ifElse`.
- Interpolation/anim: `lerp(x,y,t)`, `cubic(...)`, `pingPong(max,x)`, `anim(duration, curve, spec, initial, wrap)` (curves: CubicStandard/Accelerate/Decelerate/Linear/Anticipate/Overshoot/Custom, Spline, EaseOutBounce, EaseOutElastic).
- Arrays: `arrayMax/Min/Sum/Avg/Length`, `arraySumXY` (dot), `arraySumSqr`, `arraySpline`, `splineLoop`.
- Random: `rand()`, `random(min,max)`, `noiseFrom(seed)`.
- Custom: `rFun { x -> … }`.
- Paths from expressions: `remoteXYPath(...)`, `remotePolarPath(...)`.
- Integer ops: `+ - * / %`, `min max abs sign`, bitwise `and or xor shl shr ushr`.

### 3.6 Conditionals, loops, skip — data-driven structure
```kotlin
conditionalOperations(op: RcConditionOp, a: RcFloat, b: RcFloat) { /* rendered only if true */ }
//   RcConditionOp: Eq, Neq, Lt, Lte, Gt, Gte   (op: ConditionalOperations on the wire)

rcLoop(start: RcFloat, step: Float, end: RcFloat) { idx -> /* repeated UI; idx is RcFloat */ }
//   unrolled loop with dynamic bounds (op: LoopOperation) — repeat rows for N items

skip(kind: RcSkipKind, value: Int) { /* included only if condition holds */ }
//   IfApiLessThan/GreaterThan/EqualTo/NotEqualTo, IfProfileIncludes/Excludes
```
These let **one skeleton** carry branching/looping driven by data (e.g. show a badge if `count > 0`, render a row per list element). Combined with `StateLayout` (pick layout by integer) this covers most "structure varies by data" needs without a new binary.

### 3.7 Actions (`RcActionScope`, in `onClick`/`onLongClick`/touch handlers)
```
setValue(v: RcFloat,   Float | RcFloat)     // on-device, no round-trip
setValue(v: RcInteger, Int   | RcInteger)
setValue(v: RcText,    String)
setValue(v: RcBool,    Boolean | RcBool)
hostAction(name: String)                    // async host callback (only thing that leaves the device)
```

### 3.8 Modifiers (`creation/dsl/Modifier.kt`)
- Size/layout: `padding`, `size`, `width/height` (Float/RcFloat/RcDp), `fillMaxWidth/Height/Size`, `fillParentMax*`, `wrapContent*`, `widthIn/heightIn`, `requiredWidthIn/HeightIn`, `weight`, `offset`.
- Visual: `background(Int|Long|RcColor|RcColorValue)`, `border(...)`, `dynamicBorder`, `clip(RectShape|CircleShape|RoundedRectShape)`, `zIndex`.
- Interaction: `onClick`, `onLongClick`, `onDoubleClick`, `onTouchDown/Up/Cancel`, `ripple`.
- Scroll: `verticalScroll(pos)`, `horizontalScroll()`.
- Draw/effect: `drawWithContent { }`, `graphicsLayer(map)` (ALPHA, matrix, clip), `marquee(...)`.
- Layout compute: `computeMeasure { }`, `computePosition { }` (read/write x,y,w,h; read parent size).
- A11y: `semantics { contentDescription/role/text/stateDescription/enabled/clickable }`.
- Meta: `componentId`, `animationSpec`, `visibility(RcInteger)`, `alignByBaseline`.

### 3.9 Canvas / paint / shaders (summary)
- Shapes: `drawRect/Circle/Oval/Line/Arc/Sector/RoundRect` (Float or RcFloat).
- Paths: `drawPath`, `drawTweenPath`, `RcPath.tween/combine`, `remoteXYPath/remotePolarPath`, `remotePath{moveTo/lineTo/quadTo/close}`.
- Text: `drawTextAnchored`, `drawTextOnPath`, `drawTextOnCircle`, bitmap-font variants, `createBitmapFont`.
- Bitmaps: `drawBitmap`, `drawScaledBitmap`, `createBitmap`, `drawOnBitmap`.
- Transform: `save/restore`, `scale`, `rotate`, `skew`, `matrixFromPath`, `clipRect/clipPath`.
- Touch on canvas: `addTouch(...)` with stop modes (Gently/Instantly/Notches…).
- Paint (`RcPaintScope`): `color/alpha/style/strokeWidth/strokeCap/strokeJoin/antiAlias/blendMode/colorFilter/textSize/typeface/shader/linearGradient/radialGradient/sweepGradient`.

### 3.10 Data structures (for data-driven UI)
- `remoteArrayOf(...)` / `addStringList(...)` → `RcTextList` + `textLookup(list, idx)`.
- `remoteFloatArray` / `remoteFloatList` / `remoteDynamicFloatArray` / `remoteFloatMap(keys,values)`.
- `addDataMap(DataMap...)` / `addDataMap(keys, ids)` → keyed field access (`DataMap`).
- `DataListFloat` — float array that is **delta-updatable** by id (§2.2).

### 3.11 Lower-level writer (`RemoteComposeWriter.java`)
When the DSL lacks something: `addText`, `addNamedString/Color/Float/Integer`, `addBitmap`, `addList`, `addFloatArray/FloatList/FloatMap`, `addDataMap`, `setArrayValue`, `conditionalOperations/endConditionalOperations`, `loop`, `beginSkip/endSkip`, `startStateLayout/endStateLayout`, `addComponentVisibilityOperation`. The DSL ultimately calls these.

---

## 4. Player / runtime catalog

### 4.1 `RemoteDocumentPlayer` (Compose) — `player_compose/.../RemoteDocumentPlayer.kt`
```kotlin
@Composable
fun RemoteDocumentPlayer(
    document: CoreDocument, documentWidth: Int, documentHeight: Int,
    modifier: Modifier = Modifier, debugMode: Int = 0,
    init:  (RemoteComposePlayer) -> Unit = {},                 // once, on view create
    update:(RemoteComposePlayer) -> Unit = {},                 // every recomposition
    onAction:      (actionId: Int, value: String?) -> Unit = { _,_ -> },
    onNamedAction: (name: String, value: Any?, stateUpdater: StateUpdater) -> Unit = { _,_,_ -> },
    bitmapLoader: BitmapLoader? = null,
)
```
- `init` captures the `RemoteComposePlayer` (and thus `stateUpdater`) for programmatic pushes.
- `update` runs on **every recomposition** — never put click/business logic here.
- `onNamedAction` fires when a baked `hostAction(name)` triggers; gives you a `StateUpdater` to respond.

### 4.2 `RemoteComposePlayer` (View) — `player_view/.../RemoteComposePlayer.java`
Public surface (all `@RestrictTo(LIBRARY_GROUP)`):
- **Load:** `setDocument(byte[] | InputStream | RemoteDocument)`, `updateDocument(byte[] | RemoteDocument)`, `prepareDocument(doc)` / `setPreparedDocument(doc)` (pre-resolve bitmaps).
- **State:** `getStateUpdater()`, `getDocument()`, `setUserLocalString/Int/Color/Float/Bitmap`, `clearUserLocal*`, `setSystemLocalString`, `setLocalString(domain,name,…)`.
- **Theme:** `setTheme(THEME_LIGHT|THEME_DARK|THEME_UNSPECIFIED)`, `reloadPalette()`.
- **Query:** `getNamedColors/Floats/Strings/Images()`.
- **Interaction:** `addIdActionListener(cb)`.
- **Images:** `setBitmapLoader(BitmapLoader)`.
- **Limits:** `setMaxOpCount`, `setMaxImageDimension`, `setMaxBitmapMemory`, `setDefaultMaxFps`, `setMaxFps`.
- **Debug/perf:** `setDebug(0..3)`, `getOpsPerFrame()`, `getAnimationTime()`, `getEvalTime()`.
- **Advanced:** `setFloatSystemVariables`, `setShaderControl`, `loadMacros()`.

### 4.3 `StateUpdater` — see §2.3.

### 4.4 `AndroidRemoteContext` (variable system) — `player_core/.../platform/AndroidRemoteContext.java`
- Registry: `mVarNameHashMap: HashMap<String, ArrayList<VarName>>`; `loadVariableName(name,id,type)` populates it (called by `NamedVariable.apply`); `getVariableId(name)`, `getStringVariableName(name)`.
- Overrides (by name → id): `setNamedStringOverride`, `setNamedIntegerOverride`, `setNamedFloatOverride`, `setNamedColorOverride`, `setNamedDataOverride`, `setNamedLong`, plus `clearNamed*Override`. Each iterates the name's `VarName` list and calls `overrideText/Int/Float/Data/Color(id, value)` → `RemoteComposeState.override*` → `updateListeners(id)` → repaint.
- Reads: `getText/getFloat/getInteger/getColor/getLong(id)`, `getFromId(id)`.
- Collections: `addCollection(id, ArrayAccess)`, `putDataMap/getDataMap(id)`.
- Images: `loadBitmap(imageId, encoding, type, w, h, data)` — encodings `INLINE/URL/FILE/EMPTY`; types `PNG_8888/PNG_ALPHA_8/RAW8888/RAW8`. `ENCODING_URL` calls `mBitmapLoader.loadBitmap(urlString)`.

### 4.5 `CoreDocument` — `core/.../CoreDocument.java`
`initFromBuffer`, `initializeContext`, `applyDataOperations`, `applyOperations`, `registerVariables`, `paint`, `needsRepaint`, `onClick`, `performClick`, `runNamedAction`, `getNamedVariables(type)`, `getNamedColors`, `getClickAreas`, `displayHierarchy`, `getWidth/Height/Density/ContentSizing/ContentScroll`, `isUpdateDoc/setUpdateDoc`, `applyUpdate`, `getOpsPerFrame/getStats/bitmapMemory`.

> Note: `getNamedVariables(type)` walks the inflated op tree; a `NamedVariable` only shows up (and only registers at runtime) if it survives inflation at a reachable position — **declare at the document root** (§5).

### 4.6 `BitmapLoader` & `Limits`
- `BitmapLoader { InputStream loadBitmap(String url) }` (`player_core/.../platform/BitmapLoader.java`). Wire via `RemoteComposePlayer.setBitmapLoader(...)` or `RemoteDocumentPlayer(bitmapLoader = …)`. Implement with Glide/OkHttp.
- `Limits.ENABLE_IMAGE_URLS` must be enabled for URL images; `MAX_IMAGE_DIMENSION`, `MAX_BITMAP_MEMORY`, `MAX_OP_COUNT`, `DEFAULT_MAX_FPS`, `MAX_FPS`.

### 4.7 System variables (read on-device, drive `needsRepaint`)
Time: `animationTime, deltaTime, touchTime, dayOfWeek, dayOfMonth, hour, minutes, seconds, continuousSeconds, month, utcOffset`. Display: `density, fontSize, apiLevel, windowWidth/Height, componentWidth/Height`. Sensors (via `mSensorsSupport`): acceleration/gyro/magnetic/light (ID-based). These let documents animate/clock/react with zero host involvement.

---

## 5. The two gotchas (proven empirically in this project)

### 5.1 Named-variable updates need BOTH conditions or they silently no-op
For `StateUpdater.setUserLocalString("x", v)` to work:
1. **USER: prefix on the server name.** The player looks up `"USER:" + name`. So the server must declare `remoteNamedText("USER:x", …)` (matches `RemoteString.createNamedRemoteString(name, default, Domain.User)` which calls `addNamedString(domain.prefixed(name), …)`).
2. **Declare at the DOCUMENT ROOT.** The runtime only registers a `NamedVariable` (via `loadVariableName`) for ops reached during `applyDataOperations`. If `remoteNamedText` is nested deep inside `Row{Column{…}}`, the op is buried and `mVarNameHashMap` stays **empty** → override is a silent no-op. Declare the named variable at the start of `rcDocument { }` (root scope) and pass the returned `RcText`/`RcColor` into children.

Diagnosing: reflect `StateUpdaterImpl.mRemoteContext` → `AndroidRemoteContext.mVarNameHashMap` — `size 0` means registration failed. The working low-level path is `overrideText(id, value)` directly; the player then `invalidate()`s and the bound `Text` re-reads via `CoreText.updateVariables → getText(id)`. See the project memory `rc-named-state-update`.

### 5.2 `applyUpdate` is ID-matched and data-only
The delta path (§2.2) only updates `TextData/BitmapData/Float/Int/Long/DataListFloat` whose **IDs already exist** in the live document. It cannot add/remove components or change layout. Generate the delta from the **same source/seed** as the original so IDs align.

---

## 6. SDUI architecture & scaling (decision guide)

**The scaling rule:** you generate binaries per **layout/content-version**, NOT per **user**. Cache binaries by `(screen, layoutVersion, contentHash)` on a CDN; identical UI states share one cached binary. The backend mostly serves **data + cached bytes**. 5M users ≠ 5M binaries.

**Match channel to change cadence:**

| You want to change… | Cadence | Channel | Release? |
|---|---|---|---|
| One value live after load (count, price) | per second/interaction | Named override (§2.3) | no |
| A whole new data payload, same shape | per refresh | Delta `applyUpdate` (§2.2) | no |
| Per-user content baked at fetch | per request | Full replace (§2.1), cache by content | no |
| Add a field / change layout / change logic | when you redesign | Full replace (§2.1), bump layoutVersion | **no** |

**Why no inherent limitation:** the consumer ships once as a **generic host** — fetch bytes → `setDocument` → optional live overrides → forward `onNamedAction`. It never names a screen's fields, so backend changes (new field, new layout, new on-device logic) never require an app release. Structure that varies by data is handled either by **baking** it per-request (`rcLoop`/`conditionalOperations`/`StateLayout` evaluated at generation) or by **on-device logic** in one skeleton driven by named/delta data.

**Recommended hybrid ("Skeleton + Versioned Binary + Live Overrides"):**
- **L1 Layout** — backend generates the screen `.rc` from a data-shaped DSL composable; served + CDN-cached by version/content; consumer `setDocument(bytes)`. Redesign = server-only.
- **L2 Per-user data** — baked at generation (cheap, cache by content hash) — the common case.
- **L3 Live values** — named slots (`USER:*` at root) + `setUserLocal*`, or a delta `.rc` via `applyUpdate`, for values that change after load.

**Honest constraints:**
- Variable-length lists: bake per-request via `rcLoop` (L2), or declare a max and toggle `visibility` live (L3). Named slots are fixed-count.
- Delta `applyUpdate` is data-only + ID-aligned (§5.2).
- Per-request generation cost is low (sub-ms, ~0.5–2 KB) but is real; lean on content-hash caching.

---

## 7. Setup (coordinates)

**Server (JVM, generation):**
```
androidx.compose.remote:remote-core:1.0.0-alpha13
androidx.compose.remote:remote-creation-core:1.0.0-alpha13
androidx.compose.remote:remote-creation-jvm:1.0.0-alpha13
```
**Consumer (Android, player):**
```
androidx.compose.remote:remote-core:1.0.0-alpha13
androidx.compose.remote:remote-creation-compose:1.0.0-alpha13   // optional: compose-side creation
androidx.compose.remote:remote-player-compose:1.0.0-alpha13
androidx.compose.remote:remote-player-core:1.0.0-alpha13
androidx.compose.remote:remote-player-view:1.0.0-alpha13
```
Enable URL images: `Limits.ENABLE_IMAGE_URLS = true` and set a `BitmapLoader`.

**Minimal generate (server):**
```kotlin
fun MyScreen(): ByteArray = rcDocument {            // createRcBuffer under the hood
    val name = remoteNamedText("USER:name", "")     // root-level + USER: prefix
    Box(Modifier.fillMaxSize().padding(24f)) {
        Column { Text(name, fontSize = 18.rsp) }
    }
}
```

**Minimal render (consumer):**
```kotlin
val doc = CoreDocument().also { it.initFromBuffer(RemoteComposeBuffer.fromInputStream(bytes.inputStream())) }
RemoteDocumentPlayer(
    document = doc, documentWidth = doc.width, documentHeight = doc.height,
    bitmapLoader = myBitmapLoader,
    init = { it.stateUpdater.setUserLocalString("name", "Rohit") },   // push per-user value
    onNamedAction = { n, _, su -> if (n == "cta") su.setUserLocalString("name", "Tapped") },
)
```

---

## 8. Density, width & height — the authored-size model (verified from alpha13 bytecode)

> This section was reconstructed by disassembling the shipped `alpha13` classes (the consumer app's merged dex), not from prose. It exists because density/width/height were mis-set in this project; the rules below are what the library actually does.

### 8.1 Header tag IDs, types & defaults (confirmed)
The `Header` op stores a fixed preamble + a tag map (`Header.writeMap`). Tag wire id ↔ name (from `Header.KEYS` / `KEY_NAMES`):

| Tag id | Name | Type | Default if absent |
|---|---|---|---|
| 5 | `DOC_WIDTH` | int | **256** |
| 6 | `DOC_HEIGHT` | int | **256** |
| 7 | `DOC_DENSITY_AT_GENERATION` | float | **1.0** |
| 27 | `DENSITY_BEHAVIOR` | int | **0 (LEGACY)** |

`CoreDocument` density-behavior constants: **`DENSITY_BEHAVIOR_LEGACY = 0`** (the default), **`DENSITY_BEHAVIOR_PIXELS = 1`**, **`DENSITY_BEHAVIOR_DP = 2`**. The player reads it via `featureIntValue(27)` into `mDensityBehavior`. (Note: the earlier §1.1 bullet listing the density tags is conceptually right; the precise ids/constants are here.)

### 8.2 The one rule: width/height and density are written **independently**, content is authored in **dp/sp**
- `RemoteComposeWriter.header(width, height, desc, density, caps)` → `RemoteComposeBuffer.header(width, height, density, caps)` passes all three **straight through**. The writer **never multiplies size by density**. So `DOC_WIDTH`/`DOC_HEIGHT` are written **verbatim, in pixels**, and `DOC_DENSITY_AT_GENERATION` is a separate float.
- The canonical compose-side `RemoteCreationDisplayInfo` confirms the convention: it stores `size` (a raw-pixel `IntSize`) and a `Density` **independently**; its `createCreationDisplayInfo` default uses `DisplayMetrics.widthPixels`/`heightPixels` for size and `Configuration.densityDpi` for density, with **`density = densityDpi / 160f`** (the `160f` divisor is in the bytecode).
- DSL units `RcDp` / `RcSp` are **plain value-class wrappers over a float** — no density baked in. `24.rdp`, `24f`, and `RcDp(24f)` are identical; all mean **24 dp**. Under `DENSITY_BEHAVIOR_DP` the **player** multiplies authored dp/sp by `getDensity()` (= `DOC_DENSITY_AT_GENERATION`) **exactly once**, at render. Proven in `PaddingModifierOperation.updateVariables`: `if (getDensityBehavior() == 2) { float d = getDensity(); mLeftValue *= d; mTopValue *= d; mRightValue *= d; mBottomValue *= d; }`. So pre-multiplying a value yourself (the old `dp()=v*3` / `rsdp=v*3`) makes it render `v * 3 * density` ≈ density² too large.

Mental model: **the binary's coordinate space is pixels (`DOC_WIDTH`×`DOC_HEIGHT`); modifier/font values are dp/sp; density is metadata the player uses to scale dp→px a single time.** Keep width/height/density mutually consistent (`px = dp × density`) but author every modifier/font value in plain dp/sp.

### 8.3 Two pitfalls that broke this project (both = applying density twice)
1. **A helper that pre-multiplies a dp/sp value.** e.g. `val Int.rsdp get() = RcSp(this * 3f)`, or `fun dp(v:Int) = v * DENSITY`. With `DENSITY_BEHAVIOR_DP` the player then multiplies *again* by the device density → text/boxes render ~density² too large (overflow, clipping). Fix: author in raw dp/sp (`.rdp` / `.rsp` / bare float); let the player do the single scale. There is **no** `rsdp`.
2. **Putting pixels in `DOC_WIDTH` while declaring `DENSITY_BEHAVIOR_DP` *and* pre-scaled content.** The header px size is fine; the bug is only when the *content* is also pre-scaled. Pick one scaling site — the player — and let dp content + `DOC_DENSITY_AT_GENERATION` do the rest.

### 8.4 Correct `rcDocument` pattern (this project)
```kotlin
fun rcDocument(
    widthDp: Int = 400, heightDp: Int = 800,
    density: Float = 3.0f,                    // densityDpi / 160f; pass the device's real value for exact sizing
    content: RcScope.() -> Unit,
): ByteArray = createRcBuffer(
    profile = makeProfile(),
    tags = arrayOf(
        HTag(Header.DOC_WIDTH,  (widthDp  * density).toInt()),   // pixels = dp × density
        HTag(Header.DOC_HEIGHT, (heightDp * density).toInt()),
        HTag(Header.DOC_DENSITY_AT_GENERATION, density),         // stored independently
        HTag(Header.DOC_DENSITY_BEHAVIOR, CoreDocument.DENSITY_BEHAVIOR_DP),
    ),
    content = content,
)
// Inside content: author in dp/sp only — Text(fontSize = 18.rsp), Modifier.padding(16f) / .padding(16.rdp). Never ×density.
```

### 8.5 Consumer integration (so width/height actually fit the screen)
`DOC_WIDTH`/`DOC_HEIGHT` are pixels in the **generation** density space. With `DENSITY_BEHAVIOR_DP` the player rescales from `DOC_DENSITY_AT_GENERATION` to the device density, but it still needs to be told the on-screen size. For a screen-filling layout, let the player fit the document rather than rendering at its raw intrinsic pixel size:
```kotlin
RemoteDocumentPlayer(
    document = doc,
    documentWidth = doc.width, documentHeight = doc.height,
    modifier = Modifier.fillMaxSize(),   // ← uncomment; otherwise a 1080-px-wide doc overflows a narrower screen
)
```
For pixel-exact output, generate per device by passing the consumer's real `displayMetrics` (width/heightPixels and `density = densityDpi/160f`) into `rcDocument`.

### 8.6 Empirical header dump of the current assets (parsed from the bytes)
| asset | DOC_WIDTH | DOC_HEIGHT | DENSITY_AT_GEN | DENSITY_BEHAVIOR |
|---|---|---|---|---|
| `profile.rc` | 1200 | 2400 | 3.0 | 2 (DP) |
| `button.rc` | 1050 | 2100 | 2.625 | 1 (PIXELS) |
| `greeting.rc` | **0** | **0** | 1.0 | — (absent) |
| `detail.rc` / `home.rc` | 1050 | 2100 | — (absent → 1.0) | — (absent → LEGACY) |

Reading: `greeting.rc` is a stale build with no real size (regenerate it). The JSON path (`DocumentBuilder.kt`) still omits the density/behavior tags and manually pre-scales by `2.625` — it works only because nothing scales again, but it is the *opposite* convention to the composable path and should be aligned the same way (dp content + `DENSITY_BEHAVIOR_DP`) if unified.

---

## 9. Source map (for future deep dives)
Sources extracted from the Gradle cache (`~/.gradle/caches/.../androidx.compose.remote/.../1.0.0-alpha13-sources.jar`):
- **Creation/DSL:** `remote-creation-core` → `androidx/compose/remote/creation/dsl/{RcScope,RcScopeImp,Modifier,RcTypes,RcFloat,RcActionScope,RcDocCreator,RcPaintScope}.kt` and `creation/RemoteComposeWriter.java`. JVM impl: `remote-creation-jvm`.
- **Core runtime:** `remote-core` → `androidx/compose/remote/core/{CoreDocument,RemoteContext,RemoteComposeState,RemoteComposeBuffer}.java` and `core/operations/**` (`NamedVariable, TextData, BitmapData, FloatConstant, IntegerConstant, FloatExpression, IntegerExpression, ConditionalOperations, LoopOperation, HostNamedActionOperation, DataMap, DataListFloat`).
- **Player:** `remote-player-core` → `player/core/state/{StateUpdater,StateUpdaterImpl}.java`, `player/core/platform/{AndroidRemoteContext,BitmapLoader}.java`; `remote-player-view` → `player/view/RemoteComposePlayer.java` + `player/view/platform/RemoteComposeView.java`; `remote-player-compose` → `player/compose/RemoteDocumentPlayer.kt`.

---
*Reference compiled from alpha13 sources. APIs are `@RestrictTo(LIBRARY_GROUP)` and may change in later alphas; re-verify signatures against the sources jar when upgrading.*
