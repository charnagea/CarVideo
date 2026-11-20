package ro.harnagea.carvideo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.car.app.CarContext;
import androidx.car.app.hardware.CarHardwareManager;
import androidx.car.app.hardware.common.CarValue;
import androidx.car.app.hardware.common.OnCarDataAvailableListener;
import androidx.car.app.hardware.info.CarInfo;
import androidx.car.app.hardware.info.Speed;
import androidx.car.app.versioning.CarAppApiLevels;
import androidx.core.content.ContextCompat;

/**
 * Decides whether video may be shown on the dash.
 *
 * Android Auto doesn't tell apps the gear or handbrake position, so "parked" means:
 * the car has reported zero speed continuously for PARKED_DELAY_MS. Any movement pauses
 * video immediately. If no speed source is available at all, video stays off (fail-safe).
 */
final class DrivingGuard {

    interface Listener {
        void onDrivingStateChanged(boolean videoAllowed, String reason);
    }

    /** How long the car must stand still before video is allowed. */
    static final long PARKED_DELAY_MS = 10_000;
    /** Car sensor speed above this (m/s) counts as moving (~2 km/h). */
    private static final float CAR_MOVING_MPS = 0.5f;
    /** GPS is noisy when standing still, so use a higher threshold (~5 km/h). */
    private static final float GPS_MOVING_MPS = 1.5f;
    /** Wait this long for the car's speed before falling back to the phone's GPS. */
    private static final long CAR_SPEED_TIMEOUT_MS = 5_000;

    private final CarContext ctx;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private CarInfo carInfo;
    private OnCarDataAvailableListener<Speed> speedListener;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastFix;

    private boolean gotCarSpeed;
    private long stillSince = -1;
    private boolean allowed;
    private String reason;
    private boolean started;

    DrivingGuard(CarContext ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    boolean isAllowed() {
        return allowed;
    }

    String reason() {
        return reason;
    }

    void start() {
        if (started) return;
        started = true;
        report(false, "Checking the car's speed…");

        if (ctx.getCarAppApiLevel() >= CarAppApiLevels.LEVEL_3) {
            try {
                CarHardwareManager hw = ctx.getCarService(CarHardwareManager.class);
                carInfo = hw.getCarInfo();
                speedListener = new OnCarDataAvailableListener<Speed>() {
                    @Override
                    public void onCarDataAvailable(Speed data) {
                        CarValue<Float> v = data.getRawSpeedMetersPerSecond();
                        if (v.getStatus() == CarValue.STATUS_SUCCESS && v.getValue() != null) {
                            if (!gotCarSpeed) {
                                gotCarSpeed = true;
                                stopGps();
                            }
                            onSpeed(Math.abs(v.getValue()), CAR_MOVING_MPS);
                        }
                    }
                };
                carInfo.addSpeedListener(ContextCompat.getMainExecutor(ctx), speedListener);
            } catch (Throwable t) {
                carInfo = null;
                speedListener = null;
            }
        }
        handler.postDelayed(fallbackCheck, CAR_SPEED_TIMEOUT_MS);
    }

    void stop() {
        started = false;
        handler.removeCallbacksAndMessages(null);
        if (carInfo != null && speedListener != null) {
            try {
                carInfo.removeSpeedListener(speedListener);
            } catch (Throwable ignored) {
            }
        }
        stopGps();
    }

    private final Runnable fallbackCheck = new Runnable() {
        @Override
        public void run() {
            if (!gotCarSpeed) startGps();
        }
    };

    private final Runnable parkedCheck = new Runnable() {
        @Override
        public void run() {
            if (stillSince >= 0 && SystemClock.elapsedRealtime() - stillSince >= PARKED_DELAY_MS - 100) {
                report(true, null);
            }
        }
    };

    private void startGps() {
        if (locationManager != null) return;
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            report(false, "Can't read the car's speed. Open Car Video on your phone and allow location.");
            return;
        }
        locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                onGpsFix(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
                if (!gotCarSpeed) {
                    stillSince = -1;
                    report(false, "Speed unknown (GPS off) – video disabled");
                }
            }
        };
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                    locationListener, Looper.getMainLooper());
            if (!allowed) report(false, "Waiting for GPS to confirm the car is stopped…");
        } catch (Throwable t) {
            locationManager = null;
            report(false, "Speed unknown – video disabled");
        }
    }

    private void stopGps() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Throwable ignored) {
            }
        }
        locationManager = null;
        locationListener = null;
    }

    private void onGpsFix(Location fix) {
        if (gotCarSpeed) return;
        float speed;
        if (fix.hasSpeed()) {
            speed = fix.getSpeed();
        } else if (lastFix != null && fix.getTime() > lastFix.getTime()) {
            speed = fix.distanceTo(lastFix) * 1000f / (fix.getTime() - lastFix.getTime());
        } else {
            lastFix = fix;
            return;
        }
        lastFix = fix;
        onSpeed(speed, GPS_MOVING_MPS);
    }

    private void onSpeed(float metersPerSecond, float movingThreshold) {
        if (metersPerSecond > movingThreshold) {
            stillSince = -1;
            handler.removeCallbacks(parkedCheck);
            report(false, "Video paused while the car is moving");
        } else if (stillSince < 0) {
            stillSince = SystemClock.elapsedRealtime();
            handler.removeCallbacks(parkedCheck);
            handler.postDelayed(parkedCheck, PARKED_DELAY_MS);
            if (!allowed) report(false, "Car stopped – video in a few seconds…");
        }
    }

    private void report(boolean nowAllowed, String why) {
        if (nowAllowed == allowed && (why == null ? reason == null : why.equals(reason))) return;
        allowed = nowAllowed;
        reason = why;
        listener.onDrivingStateChanged(nowAllowed, why);
    }
}
