package ro.harnagea.carvideo;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;

/**
 * Draws the video on the car screen. Android Auto hands us a raw surface; we put a
 * private virtual display on it and show a normal Android view (VideoView + message)
 * there, which gives correct aspect ratio and an easy "paused while driving" overlay.
 */
final class VideoRenderer implements SurfaceCallback {

    interface Listener {
        void onVideoCompleted();

        void onVideoError();
    }

    private final CarContext ctx;
    private final Listener listener;

    private VirtualDisplay display;
    private Presentation presentation;
    private VideoView videoView;
    private TextView message;

    private Uri uri;
    private Uri loadedUri;
    private int resumePositionMs;
    private boolean wantPlaying;
    private boolean blocked = true;
    private String blockedText = "Checking the car's speed…";

    VideoRenderer(CarContext ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    // ---- Controls -------------------------------------------------------------------------

    void setVideo(Uri newUri) {
        uri = newUri;
        resumePositionMs = 0;
        wantPlaying = true;
        loadedUri = null;
        apply();
    }

    boolean hasVideo() {
        return uri != null;
    }

    boolean isPlayingIntent() {
        return wantPlaying && uri != null;
    }

    void setPlaying(boolean play) {
        wantPlaying = play;
        apply();
    }

    void seekBy(int deltaMs) {
        if (videoView == null || loadedUri == null) return;
        int pos = Math.max(0, videoView.getCurrentPosition() + deltaMs);
        int dur = videoView.getDuration();
        if (dur > 0) pos = Math.min(pos, Math.max(0, dur - 1000));
        videoView.seekTo(pos);
    }

    void setBlocked(boolean isBlocked, String text) {
        blocked = isBlocked;
        if (text != null) blockedText = text;
        apply();
    }

    void release() {
        tearDown();
    }

    // ---- SurfaceCallback ------------------------------------------------------------------

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer container) {
        Surface surface = container.getSurface();
        if (surface == null || container.getWidth() <= 0 || container.getHeight() <= 0) return;
        tearDown();
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        display = dm.createVirtualDisplay("CarVideo", container.getWidth(), container.getHeight(),
                container.getDpi(), surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        presentation = new Presentation(ctx, display.getDisplay(),
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        FrameLayout root = new FrameLayout(presentation.getContext());
        root.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(presentation.getContext());
        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        message = new TextView(presentation.getContext());
        message.setBackgroundColor(Color.BLACK);
        message.setTextColor(Color.WHITE);
        message.setGravity(Gravity.CENTER);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        int pad = (int) (container.getDpi() / 160f * 48);
        message.setPadding(pad, pad, pad, pad);
        root.addView(message, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                wantPlaying = false;
                resumePositionMs = 0;
                listener.onVideoCompleted();
            }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                wantPlaying = false;
                loadedUri = null;
                listener.onVideoError();
                return true;
            }
        });

        presentation.setContentView(root);
        try {
            presentation.show();
        } catch (Throwable t) {
            tearDown();
            return;
        }
        loadedUri = null;
        apply();
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect visibleArea) {
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect stableArea) {
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer container) {
        tearDown();
    }

    // ---- Internals ------------------------------------------------------------------------

    private void apply() {
        if (videoView == null || message == null) return;

        if (blocked) {
            if (videoView.isPlaying()) videoView.pause();
            if (loadedUri != null) {
                int pos = videoView.getCurrentPosition();
                if (pos > 0) resumePositionMs = pos;
            }
            message.setText(blockedText);
            message.setVisibility(TextView.VISIBLE);
            return;
        }

        if (uri == null) {
            message.setText("Tap the list button to pick a video");
            message.setVisibility(TextView.VISIBLE);
            return;
        }

        message.setVisibility(TextView.GONE);
        if (!uri.equals(loadedUri)) {
            videoView.setVideoURI(uri);
            loadedUri = uri;
            if (resumePositionMs > 0) videoView.seekTo(resumePositionMs);
        }
        if (wantPlaying) {
            videoView.start();
        } else if (videoView.isPlaying()) {
            videoView.pause();
        }
    }

    private void tearDown() {
        if (videoView != null && loadedUri != null) {
            try {
                resumePositionMs = videoView.getCurrentPosition();
                videoView.stopPlayback();
            } catch (Throwable ignored) {
            }
        }
        videoView = null;
        message = null;
        loadedUri = null;
        if (presentation != null) {
            try {
                presentation.dismiss();
            } catch (Throwable ignored) {
            }
            presentation = null;
        }
        if (display != null) {
            display.release();
            display = null;
        }
    }
}
