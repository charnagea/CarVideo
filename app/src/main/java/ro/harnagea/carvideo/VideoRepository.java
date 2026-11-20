package ro.harnagea.carvideo;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds videos through Android's media index (covers internal storage, SD cards and most USB sticks). */
public final class VideoRepository {
    private VideoRepository() {}

    public static String readPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_VIDEO"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean hasReadPermission(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return c.checkSelfPermission(readPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    public static List<VideoItem> queryMediaStore(Context c) {
        List<VideoItem> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Uri> collections = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                for (String volume : MediaStore.getExternalVolumeNames(c)) {
                    collections.add(MediaStore.Video.Media.getContentUri(volume));
                }
            } catch (Exception ignored) {
            }
        }
        if (collections.isEmpty()) collections.add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);

        String[] projection = {"_id", "_display_name", "duration", "_size", "_data", "bucket_display_name"};
        for (Uri collection : collections) {
            Cursor cur = null;
            try {
                cur = c.getContentResolver().query(collection, projection, null, null, null);
                if (cur == null) continue;
                while (cur.moveToNext()) {
                    long id = cur.getLong(0);
                    String name = cur.getString(1);
                    long duration = cur.isNull(2) ? 0 : cur.getLong(2);
                    long size = cur.isNull(3) ? 0 : cur.getLong(3);
                    String path = cur.getString(4);
                    String bucket = cur.getString(5);
                    String key = path != null ? path : collection + "/" + id;
                    if (!seen.add(key)) continue;
                    if (name == null) name = "Video " + id;
                    out.add(new VideoItem(ContentUris.withAppendedId(collection, id), stripExtension(name),
                            duration, size, bucket, path, false));
                }
            } catch (Exception ignored) {
                // A volume can disappear (USB unplugged) while we read it.
            } finally {
                if (cur != null) cur.close();
            }
        }
        sort(out);
        return out;
    }

    public static void sort(List<VideoItem> list) {
        Collections.sort(list, new Comparator<VideoItem>() {
            @Override
            public int compare(VideoItem a, VideoItem b) {
                String la = a.location == null ? "" : a.location.toLowerCase(Locale.ROOT);
                String lb = b.location == null ? "" : b.location.toLowerCase(Locale.ROOT);
                int r = la.compareTo(lb);
                if (r != 0) return r;
                return a.title.toLowerCase(Locale.ROOT).compareTo(b.title.toLowerCase(Locale.ROOT));
            }
        });
    }

    public static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
