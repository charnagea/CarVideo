package ro.harnagea.carvideo;

import java.util.Locale;

public final class Format {
    private Format() {}

    public static String duration(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%d:%02d", m, sec);
    }

    public static String size(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024) return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
    }

    private static final String[] VIDEO_EXT = {
            ".mp4", ".m4v", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".3g2",
            ".ts", ".m2ts", ".mts", ".mpg", ".mpeg", ".wmv", ".flv", ".vob"
    };

    public static boolean isVideoName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.US);
        if (n.startsWith(".")) return false;
        for (String e : VIDEO_EXT) if (n.endsWith(e)) return true;
        return false;
    }
}
