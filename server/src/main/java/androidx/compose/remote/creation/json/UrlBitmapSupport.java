package androidx.compose.remote.creation.json;

import androidx.compose.remote.creation.RemoteComposeWriter;

import com.remotecompose.rc.core.RemoteImageKt;

/**
 * Teaches the JSON parser's {@code bitmap} component to handle URL ids.
 *
 * The stock parser hands {@code component.get("id")} straight to
 * {@link RemoteComposeWriter#addBitmap(Object)}, which only accepts an in-memory platform
 * bitmap pre-registered by host code — a URL String NPEs inside the writer, and no image
 * layout node is ever emitted. This shim routes http(s) ids through the sized
 * {@code addBitmapUrl} slot + {@code image} component pair (the same pattern as core-ui's
 * RemoteUrlImage), declaring a {@link RemoteImageKt#IMAGE_SLOT_PX}² slot so the player's
 * RemoteBitmapDecoder bounds check passes; the consumer's image loader downscales to fit.
 *
 * Lives in the library's package because {@link JsonComponentParser} is package-private.
 */
public final class UrlBitmapSupport {
    private UrlBitmapSupport() {}

    /** Registers the URL-aware {@code bitmap}/{@code image} component parser on {@code parser}. */
    public static void install(RemoteComposeJsonParser parser) {
        JsonComponentParser urlAware = (component, modifier, writer, p) -> {
            Object id = component.opt("id");
            if (id instanceof String) {
                String s = (String) id;
                if (s.startsWith("http://") || s.startsWith("https://")) {
                    int slot = RemoteImageKt.IMAGE_SLOT_PX;
                    int imageId = writer.addBitmapUrl(s, slot, slot);
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

    private static int scaleType(org.json.JSONObject component) {
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