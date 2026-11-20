package ro.harnagea.carvideo;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.OnClickListener;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.versioning.CarAppApiLevels;

import java.util.List;

/** List of videos stored on the phone. */
final class VideoListScreen extends Screen {

    interface Callback {
        void onPicked(List<VideoItem> list, int position);
    }

    private final Callback callback;
    private List<VideoItem> items;
    private boolean loading = true;
    private boolean noPermission;

    VideoListScreen(@NonNull final CarContext carContext, Callback callback) {
        super(carContext);
        this.callback = callback;
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean allowed = VideoRepository.hasReadPermission(carContext);
                final List<VideoItem> result = allowed ? VideoRepository.queryMediaStore(carContext) : null;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        noPermission = !allowed;
                        items = result;
                        loading = false;
                        invalidate();
                    }
                });
            }
        }, "video-list").start();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ListTemplate.Builder template = new ListTemplate.Builder()
                .setTitle("Videos on your phone")
                .setHeaderAction(Action.BACK);
        if (loading) return template.setLoading(true).build();

        ItemList.Builder list = new ItemList.Builder();
        if (noPermission) {
            list.setNoItemsMessage("Open Car Video on your phone and allow access to videos");
        } else if (items == null || items.isEmpty()) {
            list.setNoItemsMessage("No videos found on your phone");
        } else {
            int limit = Math.min(items.size(), maxRows());
            for (int i = 0; i < limit; i++) {
                final VideoItem item = items.get(i);
                final int position = i;
                Row.Builder row = new Row.Builder()
                        .setTitle(item.title)
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick() {
                                getScreenManager().pop();
                                callback.onPicked(items, position);
                            }
                        });
                String details = item.details();
                if (details.length() > 0) row.addText(details);
                list.addItem(row.build());
            }
        }
        return template.setSingleList(list.build()).build();
    }

    private int maxRows() {
        int limit = 6;
        if (getCarContext().getCarAppApiLevel() >= CarAppApiLevels.LEVEL_2) {
            try {
                limit = getCarContext().getCarService(ConstraintManager.class)
                        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST);
            } catch (Throwable ignored) {
            }
        }
        return Math.max(1, limit);
    }
}
