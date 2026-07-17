package androidx.compose.remote.creation.json;

import androidx.compose.remote.creation.RemoteComposeWriter;

import com.remotecompose.rc.core.RemoteImageKt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

/**
 * Teaches the JSON parser's {@code bitmap} component to handle URL ids.
 *
 * The stock parser hands {@code component.get("id")} straight to
 * {@link RemoteComposeWriter#addBitmap(Object)}, which only accepts an in-memory platform
 * bitmap pre-registered by host code — a URL String NPEs inside the writer, and no image
 * layout node is ever emitted. This shim routes http(s) ids through the sized
 * {@code addBitmapUrl} slot + {@code image} component pair (the same pattern as core-ui's
 * RemoteUrlImage).
 *
 * <b>Slot contract:</b> the player throws "dimensions don't match" when the consumer delivers
 * a bitmap larger than the declared slot, and renders shrunken when it delivers a smaller one —
 * so delivered and declared must be exactly equal. The consumer's {@code BitmapLoader} only
 * receives the URL, so the slot size is carried in the URL itself: the slot is computed from
 * the component's numeric {@code width}/{@code height}/{@code size} modifiers
 * (× {@link #SLOT_SCALE} for display-density crispness, capped at 512) and appended as a
 * {@code #rc-size=WxH} fragment. Fragments never reach the network; the consumer's loader
 * parses the fragment and returns the image scaled to exactly that size. Components without a
 * numeric size get no fragment and the always-safe {@link RemoteImageKt#IMAGE_SLOT_PX}² slot
 * (today's behavior), which also keeps documents working against consumers that don't yet
 * understand the fragment.
 *
 * Lives in the library's package because {@link JsonComponentParser} is package-private.
 */
public final class UrlBitmapSupport {
    /**
     * Multiplier from JSON layout units (dp) to slot pixels, so images stay crisp on
     * high-density screens: 4 covers xxxhdpi, Android's densest standard bucket — a 40 dp icon
     * is delivered at 160 px and fit-scaled down on lighter screens. Raising it improves
     * sharpness and raises declared bitmap memory (slot px × 4 bytes per image, checked
     * against Limits.MAX_BITMAP_MEMORY at document load).
     */
    private static final int SLOT_SCALE = 4;

    /** URL fragment carrying the slot size to the consumer's BitmapLoader: {@code #rc-size=WxH}. */
    private static final String SIZE_FRAGMENT = "#rc-size=";

    private UrlBitmapSupport() {}

    /** Registers the URL-aware {@code bitmap}/{@code image} component parser on {@code parser}. */
    public static void install(RemoteComposeJsonParser parser) {
        JsonComponentParser urlAware = (component, modifier, writer, p) -> {
            Object id = component.opt("id");
            if (id instanceof String) {
                String s = (String) id;
                if (s.startsWith("http://") || s.startsWith("https://")) {
                    int[] slot = slotFromJson(component);
                    String url = s;
                    if (slot != null) {
                        url = s + SIZE_FRAGMENT + slot[0] + "x" + slot[1];
                    } else {
                        slot = new int[] {RemoteImageKt.IMAGE_SLOT_PX, RemoteImageKt.IMAGE_SLOT_PX};
                    }
                    int imageId = writer.addBitmapUrl(url, slot[0], slot[1]);
                    writer.image(modifier, imageId, scaleType(component),
                            (float) component.optDouble("alpha", 1.0));
                    return;
                }
                if (s.isEmpty()) {
                    // Placeholder: keep the component's modifiers (size/padding) in the
                    // layout so siblings don't shift, but draw nothing.
                    writer.startBox(modifier, 0, 0);
                    writer.endBox();
                    return;
                }
            }
            // Stock behavior: id is an in-memory platform bitmap object.
            writer.addBitmap(component.get("id"));
        };
        parser.registerComponentParser("bitmap", urlAware);
        parser.registerComponentParser("image", urlAware);

        // dp semantics for plain numbers: the stock parsers record width/height/size as
        // raw-pixel EXACT dimensions — DimensionModifierOperation only density-scales the
        // EXACT_DP type, which the JSON path never emits — so "width": 40 renders 20dp-worth
        // of pixels on a 2× screen even when the header declares densityBehavior=DP. Wrapping
        // the number in an "N * density" expression makes the player resolve it per device.
        // String-valued dimensions (expressions) pass through untouched.
        parser.registerModifierParser("width", (mod, key, rm, p2) ->
                rm.width(dpFloat(mod.opt(key), p2)));
        parser.registerModifierParser("height", (mod, key, rm, p2) ->
                rm.height(dpFloat(mod.opt(key), p2)));
        parser.registerModifierParser("size", (mod, key, rm, p2) -> {
            float v = dpFloat(mod.opt(key), p2);
            rm.width(v);
            rm.height(v);
        });
    }

    private static float dpFloat(Object value, RemoteComposeJsonParser parser) {
        if (value instanceof Number) {
            return parser.parseFloat(((Number) value).floatValue() + " * density");
        }
        return parser.parseFloat(value);
    }

    /**
     * Reads the component's numeric {@code width}/{@code height}/{@code size} modifiers and
     * converts them to slot pixels. A single declared dimension is used for both (square icons
     * commonly pair one dimension with {@code weight}). Returns null when no numeric size is
     * declared — expression-valued sizes have no creation-time value.
     */
    private static int[] slotFromJson(JSONObject component) {
        float w = Float.NaN;
        float h = Float.NaN;
        JSONArray mods = component.optJSONArray("modifiers");
        if (mods != null) {
            for (int i = 0; i < mods.length(); i++) {
                JSONObject mod = mods.optJSONObject(i);
                if (mod == null) continue;
                for (Iterator<String> it = mod.keys(); it.hasNext(); ) {
                    String key = it.next();
                    Object value = mod.opt(key);
                    if (!(value instanceof Number)) continue;
                    float v = ((Number) value).floatValue();
                    switch (key.toLowerCase(Locale.ROOT)) {
                        case "width": w = v; break;
                        case "height": h = v; break;
                        case "size": w = v; h = v; break;
                        default: break;
                    }
                }
            }
        }
        if (Float.isNaN(w)) w = h;
        if (Float.isNaN(h)) h = w;
        if (Float.isNaN(w) || w <= 0 || h <= 0) return null;
        return new int[] {toSlotPx(w), toSlotPx(h)};
    }

    private static int toSlotPx(float layoutUnits) {
        return Math.min((int) Math.ceil(layoutUnits) * SLOT_SCALE, RemoteImageKt.IMAGE_SLOT_PX);
    }

    private static int scaleType(JSONObject component) {
        switch (component.optString("scale", "fit")) {
            case "none": return RemoteComposeWriter.IMAGE_SCALE_NONE;
            case "crop": return RemoteComposeWriter.IMAGE_SCALE_CROP;
            case "fill_bounds": return RemoteComposeWriter.IMAGE_SCALE_FILL_BOUNDS;
            case "fill_width": return RemoteComposeWriter.IMAGE_SCALE_FILL_WIDTH;
            case "fill_height": return RemoteComposeWriter.IMAGE_SCALE_FILL_HEIGHT;
            default: return RemoteComposeWriter.IMAGE_SCALE_FIT;
        }
    }
}