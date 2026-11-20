package ro.harnagea.carvideo;

import android.net.Uri;

/** One playable video, wherever it lives (phone storage, USB stick, SD card, copied file). */
public final class VideoItem {
    public final Uri uri;
    public final String title;
    public final long durationMs;
    public final long sizeBytes;
    /** Human-readable place: folder name, "USB / SD", "Copied from phone"... */
    public final String location;
    /** Absolute file path when known, used to avoid listing the same file twice. */
    public final String path;
    /** True for files this app copied from a phone (can be deleted from the list). */
    public final boolean copied;

    public VideoItem(Uri uri, String title, long durationMs, long sizeBytes,
                     String location, String path, boolean copied) {
        this.uri = uri;
        this.title = title;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.location = location;
        this.path = path;
        this.copied = copied;
    }

    /** Second line shown under the title. */
    public String details() {
        StringBuilder sb = new StringBuilder();
        if (durationMs > 0) sb.append(Format.duration(durationMs));
        if (sizeBytes > 0) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(Format.size(sizeBytes));
        }
        if (location != null && location.length() > 0) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(location);
        }
        return sb.toString();
    }
}
