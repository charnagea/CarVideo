package ro.harnagea.carvideo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Phone-side screen: grants permissions and explains how to enable the app in Android Auto. */
public final class SetupActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(20);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Car Video (dash)");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        col.addView(title);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(0, pad, 0, pad);
        col.addView(status);

        Button grant = new Button(this);
        grant.setText("Allow access to videos and location");
        grant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions(new String[]{
                        VideoRepository.readPermission(),
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, 1);
            }
        });
        col.addView(grant);

        TextView steps = new TextView(this);
        steps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        steps.setPadding(0, pad, 0, 0);
        steps.setText(
                "One-time setup in Android Auto:\n\n"
                        + "1. Open Settings → search \"Android Auto\" → open it.\n"
                        + "2. Scroll to the bottom and tap \"Version\" about 10 times, then confirm "
                        + "to turn on developer mode.\n"
                        + "3. Tap the ⋮ menu (top right) → Developer settings → turn on "
                        + "\"Unknown sources\".\n"
                        + "4. Back in Android Auto settings → Customise launcher → make sure "
                        + "Car Video is ticked.\n"
                        + "5. Plug the phone into the car and open Car Video on the dash.\n\n"
                        + "Video only shows after the car has been stopped for "
                        + (DrivingGuard.PARKED_DELAY_MS / 1000)
                        + " seconds, and pauses the moment it moves. "
                        + "Location is a backup in case the car doesn't share its speed.");
        col.addView(steps);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        updateStatus();
    }

    private void updateStatus() {
        boolean videos = VideoRepository.hasReadPermission(this);
        boolean location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        status.setText((videos ? "✓" : "✗") + " Access to videos\n"
                + (location ? "✓" : "✗") + " Location (speed backup)");
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
