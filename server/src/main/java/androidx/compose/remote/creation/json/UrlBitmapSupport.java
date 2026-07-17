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
 * The slot is sized from the component's own {@code width}/{@code height}/{@code size}
 * modifiers so the document's declared bitmap memory (slot px × 4 bytes each, checked against
 * Limits.MAX_BITMAP_MEMORY at load) matches what the layout actually needs. Modifier values may
 * be numbers or simple arithmetic over {@code windowWidth}/{@code windowHeight}, which are
 * resolved against the document's declared header dimensions. Anything unresolvable falls back
 * to the {@link RemoteImageKt#IMAGE_SLOT_PX}² slot; resolved sizes are capped at that value
 * because the consumer's image loader never delivers a larger bitmap anyway.
 *
 * Lives in the library's package because {@link JsonComponentParser} is package-private.
 */
public final class UrlBitmapSupport {
    private UrlBitmapSupport() {}

    /**
     * Registers the URL-aware {@code bitmap}/{@code image} component parser on {@code parser}.
     *
     * @param docWidth  the document's declared header width, used to resolve
     *                  {@code windowWidth} in dimension expressions (NaN if undeclared)
     * @param docHeight the document's declared header height, likewise for {@code windowHeight}
     */
    public static void install(RemoteComposeJsonParser parser, float docWidth, float docHeight) {
        JsonComponentParser urlAware = (component, modifier, writer, p) -> {
            Object id = component.opt("id");
            if (id instanceof String) {
                String s = (String) id;
                if (s.startsWith("http://") || s.startsWith("https://")) {
                    int[] slot = slotSize(component, docWidth, docHeight);
                    int imageId = writer.addBitmapUrl(s, slot[0], slot[1]);
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
    }

    /**
     * Derives the bitmap slot dimensions from the component's size modifiers. A single declared
     * dimension is used for both (square icons commonly pair one dimension with {@code weight});
     * unresolvable or absent sizes fall back to {@link RemoteImageKt#IMAGE_SLOT_PX}.
     */
    private static int[] slotSize(JSONObject component, float docWidth, float docHeight) {
        float w = Float.NaN;
        float h = Float.NaN;
        JSONArray mods = component.optJSONArray("modifiers");
        if (mods != null) {
            for (int i = 0; i < mods.length(); i++) {
                JSONObject mod = mods.optJSONObject(i);
                if (mod == null) continue;
                for (Iterator<String> it = mod.keys(); it.hasNext(); ) {
                    String key = it.next();
                    switch (key.toLowerCase(Locale.ROOT)) {
                        case "width":
                            w = evalDimension(mod.opt(key), docWidth, docHeight);
                            break;
                        case "height":
                            h = evalDimension(mod.opt(key), docWidth, docHeight);
                            break;
                        case "size":
                            float v = evalDimension(mod.opt(key), docWidth, docHeight);
                            w = v;
                            h = v;
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        if (Float.isNaN(w)) w = h;
        if (Float.isNaN(h)) h = w;
        if (Float.isNaN(w) || w <= 0 || Float.isNaN(h) || h <= 0) {
            return new int[] {RemoteImageKt.IMAGE_SLOT_PX, RemoteImageKt.IMAGE_SLOT_PX};
        }
        return new int[] {
            Math.min((int) Math.ceil(w), RemoteImageKt.IMAGE_SLOT_PX),
            Math.min((int) Math.ceil(h), RemoteImageKt.IMAGE_SLOT_PX),
        };
    }

    /** Evaluates a modifier value to a concrete float, or NaN if it can't be resolved. */
    private static float evalDimension(Object value, float docWidth, float docHeight) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            try {
                return new DimensionExpr((String) value, docWidth, docHeight).parse();
            } catch (RuntimeException e) {
                return Float.NaN;
            }
        }
        return Float.NaN;
    }

    /**
     * Evaluator for the static subset of the parser's dimension expressions: numbers,
     * {@code + - * /}, parentheses, unary minus, and the {@code windowWidth}/{@code windowHeight}
     * variables (with or without {@code ()}). Any other token throws, signalling the caller to
     * fall back to the default slot — dynamic variables like {@code density} or {@code time}
     * have no creation-time value.
     */
    private static final class DimensionExpr {
        private final String src;
        private final float docWidth;
        private final float docHeight;
        private int pos;

        DimensionExpr(String src, float docWidth, float docHeight) {
            this.src = src;
            this.docWidth = docWidth;
            this.docHeight = docHeight;
        }

        float parse() {
            float v = expr();
            skipWs();
            if (pos != src.length()) throw new IllegalArgumentException(src);
            return v;
        }

        private float expr() {
            float v = term();
            while (true) {
                skipWs();
                if (consume('+')) v += term();
                else if (consume('-')) v -= term();
                else return v;
            }
        }

        private float term() {
            float v = unary();
            while (true) {
                skipWs();
                if (consume('*')) v *= unary();
                else if (consume('/')) v /= unary();
                else return v;
            }
        }

        private float unary() {
            skipWs();
            if (consume('-')) return -unary();
            return atom();
        }

        private float atom() {
            skipWs();
            if (consume('(')) {
                float v = expr();
                skipWs();
                if (!consume(')')) throw new IllegalArgumentException(src);
                return v;
            }
            if (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isDigit(c) || c == '.') return number();
                if (Character.isLetter(c)) return variable();
            }
            throw new IllegalArgumentException(src);
        }

        private float number() {
            int start = pos;
            while (pos < src.length()
                    && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            return Float.parseFloat(src.substring(start, pos));
        }

        private float variable() {
            int start = pos;
            while (pos < src.length() && Character.isLetter(src.charAt(pos))) pos++;
            String name = src.substring(start, pos);
            if (pos + 1 < src.length() && src.charAt(pos) == '(' && src.charAt(pos + 1) == ')') {
                pos += 2;
            }
            float v;
            switch (name) {
                case "windowWidth": v = docWidth; break;
                case "windowHeight": v = docHeight; break;
                default: throw new IllegalArgumentException(name);
            }
            if (Float.isNaN(v)) throw new IllegalArgumentException(name);
            return v;
        }

        private boolean consume(char c) {
            if (pos < src.length() && src.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipWs() {
            while (pos < src.length() && src.charAt(pos) == ' ') pos++;
        }
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