package com.ambitsportlocal.background;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;

/** Explicitly enabled connected-device foreground service. No boot/autostart receiver. */
public class SyncService extends Service {
    static final String CHANNEL = "ambit3_sync";
    static final int ID = 3102;
    static final String PREFS = "ambit3_background";
    private PowerManager.WakeLock lock;
    private int minutes;
    static boolean running = false;
    static void emit(Context context, String event, Object value) {
        try {
            Object app = context.getApplicationContext();
            Object host = app.getClass().getMethod("getReactHost").invoke(app);
            Class<?> hostType = Class.forName("com.facebook.react.ReactHost");
            Object react = hostType.getMethod("getCurrentReactContext").invoke(host);
            if (react != null) Class.forName("com.facebook.react.bridge.ReactContext")
                .getMethod("emitDeviceEvent", String.class, Object.class).invoke(react, event, value);
        } catch (Exception e) { Log.w("Ambit3Sync", "React event unavailable", e); }
    }
    private PendingIntent alarmIntent() {
        return PendingIntent.getService(this, 3103, new Intent(this, SyncService.class).setAction("TICK"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private void schedule() {
        ((AlarmManager)getSystemService(ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + minutes * 60000L, alarmIntent());
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            getSharedPreferences(PREFS, 0).edit().putInt("minutes", 0).apply();
            emit(this, "AmbitBackgroundState", 0);
            stopSelf(); return START_NOT_STICKY;
        }
        minutes = getSharedPreferences(PREFS, 0).getInt("minutes", 0);
        if (minutes != 15 && minutes != 30 && minutes != 60) { stopSelf(); return START_NOT_STICKY; }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY;
        }
        NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Synchronisation de la montre", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null); manager.createNotificationChannel(channel);
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent open = PendingIntent.getActivity(this, 3104, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 3105, new Intent(this, SyncService.class).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Ambit3 · synchronisation automatique")
            .setContentText("Cycle " + minutes + " min · selon disponibilité de la montre")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "Arrêter", stop).build()).build();
        boolean weatherLocation = Build.VERSION.SDK_INT >= 30
            && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= 29) {
            int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            if (weatherLocation) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            try {
                startForeground(ID, notification, types);
            } catch (SecurityException e) {
                weatherLocation = false;
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            }
        }
        else startForeground(ID, notification);
        running = true;
        emit(this, "AmbitBackgroundState", minutes);
        if (intent != null && "TICK".equals(intent.getAction())) {
            if (lock != null && lock.isHeld()) lock.release();
            lock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Ambit3:sync");
            lock.acquire(120000L);
            emit(this, "AmbitBackgroundTick", null);
            if (weatherLocation) refreshWeatherLocation();
        }
        schedule();
        return START_NOT_STICKY;
    }
    private android.os.CancellationSignal weatherRequest;

    @android.annotation.TargetApi(30)
    private void refreshWeatherLocation() {
        if (weatherRequest != null) weatherRequest.cancel();
        android.os.CancellationSignal request = new android.os.CancellationSignal();
        weatherRequest = request;
        new Handler(Looper.getMainLooper()).postDelayed(request::cancel, 25000);
        try {
            getSystemService(android.location.LocationManager.class).getCurrentLocation(
                android.location.LocationManager.GPS_PROVIDER, request, getMainExecutor(), location -> {
                    if (location != null && !request.isCanceled()) {
                        emit(this, "AmbitBackgroundWeatherLocation", location.getLatitude() + "," + location.getLongitude());
                    }
                });
        } catch (RuntimeException e) {
            request.cancel();
        }
    }

    @Override public void onDestroy() {
        if (weatherRequest != null) weatherRequest.cancel();
        running = false;
        emit(this, "AmbitBackgroundState", 0);
        ((AlarmManager)getSystemService(ALARM_SERVICE)).cancel(alarmIntent());
        if (lock != null && lock.isHeld()) lock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
