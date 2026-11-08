package com.ambitsportlocal.background;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.CancellationSignal;
import android.location.LocationManager;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.widget.Toast;
/** Internal command entry point, launched explicitly by the app's Android Intent bridge. */
public class SyncControlActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (getIntent().getBooleanExtra("weatherLocation", false)) {
            locateWeather();
            return;
        }
        int minutes = (int)getIntent().getDoubleExtra("minutes", 0);
        if (minutes == -1) {
            SyncService.emit(this, "AmbitBackgroundState", SyncService.running ? getSharedPreferences(SyncService.PREFS, 0).getInt("minutes", 0) : 0);
            finish(); return;
        }
        if (minutes != 0 && minutes != 15 && minutes != 30 && minutes != 60) minutes = 0;
        getSharedPreferences(SyncService.PREFS, 0).edit().putInt("minutes", minutes).apply();
        try {
            if (minutes == 0) stopService(new Intent(this, SyncService.class));
            else startForegroundService(new Intent(this, SyncService.class));
            SyncService.emit(this, "AmbitBackgroundState", minutes);
        } catch (Exception e) {
            getSharedPreferences(SyncService.PREFS, 0).edit().putInt("minutes", 0).apply();
            SyncService.emit(this, "AmbitBackgroundState", 0);
            Toast.makeText(this, "Service indisponible : vérifiez les autorisations Bluetooth.", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void locateWeather() {
        if (android.os.Build.VERSION.SDK_INT < 30 ||
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finishLocation("");
            return;
        }
        LocationManager manager = getSystemService(LocationManager.class);
        CancellationSignal cancel = new CancellationSignal();
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable timeout = () -> {
            cancel.cancel();
            finishLocation("");
        };
        handler.postDelayed(timeout, 25000);
        try {
            manager.getCurrentLocation(LocationManager.GPS_PROVIDER, cancel, getMainExecutor(), location -> {
                handler.removeCallbacks(timeout);
                if (cancel.isCanceled()) return;
                finishLocation(location == null ? "" : location.getLatitude() + "," + location.getLongitude());
            });
        } catch (RuntimeException e) {
            handler.removeCallbacks(timeout);
            finishLocation("");
        }
    }

    private void finishLocation(String coordinates) {
        SyncService.emit(this, "AmbitWeatherLocation", coordinates);
        finish();
    }
}
