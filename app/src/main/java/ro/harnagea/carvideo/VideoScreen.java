package ro.harnagea.carvideo;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.List;

/** Main car screen: full-screen video with four buttons (list, back 10 s, play/pause, forward 30 s). */
final class VideoScreen extends Screen implements VideoRenderer.Listener, DrivingGuard.Listener {

    private final VideoRenderer renderer;
    private final DrivingGuard guard;
    private List<VideoItem> playlist = new ArrayList<>();
    private int index = -1;

    VideoScreen(@NonNull CarContext carContext) {
        super(carContext);
        renderer = new VideoRenderer(carContext, this);
        carContext.getCarService(AppManager.class).setSurfaceCallback(renderer);
        guard = new DrivingGuard(carContext, this);
        guard.start();

        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                guard.stop();
                renderer.release();
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ActionStrip strip = new ActionStrip.Builder()
                .addAction(iconAction(R.drawable.ic_list, new OnClickListener() {
                    @Override
                    public void onClick() {
                        openList();
                    }
                }))
                .addAction(iconAction(R.drawable.ic_rewind, new OnClickListener() {
                    @Override
                    public void onClick() {
                        renderer.seekBy(-10_000);
                    }
                }))
                .addAction(iconAction(renderer.isPlayingIntent() ? R.drawable.ic_pause : R.drawable.ic_play,
                        new OnClickListener() {
                            @Override
                            public void onClick() {
                                togglePlay();
                            }
                        }))
                .addAction(iconAction(R.drawable.ic_forward, new OnClickListener() {
                    @Override
                    public void onClick() {
                        renderer.seekBy(30_000);
                    }
                }))
                .build();
        return new NavigationTemplate.Builder().setActionStrip(strip).build();
    }

    private Action iconAction(@DrawableRes int icon, OnClickListener onClick) {
        return new Action.Builder()
                .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), icon)).build())
                .setOnClickListener(onClick)
                .build();
    }

    private void openList() {
        getScreenManager().push(new VideoListScreen(getCarContext(), new VideoListScreen.Callback() {
            @Override
            public void onPicked(List<VideoItem> list, int position) {
                playlist = list;
                playIndex(position);
            }
        }));
    }

    private void togglePlay() {
        if (!renderer.hasVideo()) {
            openList();
            return;
        }
        if (!guard.isAllowed()) {
            String why = guard.reason();
            CarToast.makeText(getCarContext(), why != null ? why : "Video only plays when parked",
                    CarToast.LENGTH_LONG).show();
            return;
        }
        renderer.setPlaying(!renderer.isPlayingIntent());
        invalidate();
    }

    private void playIndex(int i) {
        if (i < 0 || i >= playlist.size()) return;
        index = i;
        renderer.setVideo(playlist.get(i).uri);
        if (!guard.isAllowed()) {
            CarToast.makeText(getCarContext(), "Will start when the car is parked", CarToast.LENGTH_SHORT).show();
        }
        invalidate();
    }

    // ---- DrivingGuard.Listener ------------------------------------------------------------

    @Override
    public void onDrivingStateChanged(boolean videoAllowed, String reason) {
        renderer.setBlocked(!videoAllowed, reason);
        invalidate();
    }

    // ---- VideoRenderer.Listener -----------------------------------------------------------

    @Override
    public void onVideoCompleted() {
        if (index + 1 < playlist.size()) {
            playIndex(index + 1);
        } else {
            invalidate();
        }
    }

    @Override
    public void onVideoError() {
        CarToast.makeText(getCarContext(), "Can't play this video", CarToast.LENGTH_LONG).show();
        invalidate();
    }
}
