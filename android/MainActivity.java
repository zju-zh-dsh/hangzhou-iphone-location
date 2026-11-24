// SPDX-License-Identifier: GPL-3.0-or-later
package org.hangzhou.mocklocation;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Insets;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

public final class MainActivity extends Activity {
    private static final String PICKER_HOST = "picker.hangzhou-location.invalid";
    private static final String NO_PERMISSION = "请在开发者选项的「选择模拟位置信息应用」中选择本工具。";
    private static final String READY = "在地图上选点后，点「开始模拟」。";
    private static final String RESTORED = "已停止模拟。请在系统地图核对当前位置。";
    private static final String RESTORE_ERROR = "未能移除模拟定位。请重新选本工具为模拟定位应用后重试，或重启手机。";
    private static final String[] PROVIDERS = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused"};
    private static volatile String status = READY;
    private static final int SAVE_GPX = 42;
    private WebView view;
    private String pendingGpx;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) pendingGpx = state.getString("gpx");
        FrameLayout frame = new FrameLayout(this);
        if (Build.VERSION.SDK_INT >= 35) {
            frame.setOnApplyWindowInsetsListener((parent, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                parent.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        view = new WebView(this);
        frame.addView(view, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setAllowFileAccess(false);
        view.getSettings().setAllowContentAccess(false);
        view.addJavascriptInterface(new Controls(), "HangzhouAndroid");
        view.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView web, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (!isPicker(url)) return null;
                String path = url.getPath();
                String mime;
                if ("/index.html".equals(path)) mime = "text/html";
                else if ("/maplibre-gl.js".equals(path)) mime = "application/javascript";
                else if ("/maplibre-gl.css".equals(path)) mime = "text/css";
                else return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Collections.emptyMap(), null);
                try {
                    return new WebResourceResponse(mime, "UTF-8", getAssets().open(path.substring(1)));
                } catch (IOException error) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Collections.emptyMap(), null);
                }
            }

            @Override public boolean shouldOverrideUrlLoading(WebView web, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (isPicker(url)) return false;
                if (request.isForMainFrame() && request.hasGesture() && "https".equals(url.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, url)); } catch (RuntimeException ignored) { }
                }
                return true;
            }
        });
        view.loadUrl("https://" + PICKER_HOST + "/index.html");
    }

    private static boolean isPicker(Uri url) {
        return "https".equals(url.getScheme()) && PICKER_HOST.equals(url.getHost()) && url.getPort() == -1;
    }

    private static boolean isInScope(double lat, double lon) {
        return lat >= 29.08 && lat <= 30.65 && lon >= 118.25 && lon <= 120.70;
    }

    public final class Controls {
        @JavascriptInterface public String status() { return status; }

        @JavascriptInterface public void select(String action, double lat, double lon) {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if ("settings".equals(action)) {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
                    catch (RuntimeException unavailable) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                    return;
                }
                if ("stop".equals(action)) {
                    if (!stopService(new Intent(MainActivity.this, MockService.class))) {
                        boolean clean = removeProviders(getSystemService(LocationManager.class), PROVIDERS);
                        status = clean ? RESTORED : RESTORE_ERROR;
                    }
                    return;
                }
                if (!isInScope(lat, lon)) {
                    status = "请选择杭州地图范围内的有效坐标。";
                    return;
                }
                if ("start".equals(action)) {
                    try {
                        startForegroundService(new Intent(MainActivity.this, MockService.class)
                                .putExtra("lat", lat).putExtra("lon", lon));
                    } catch (RuntimeException error) { status = "无法启动模拟定位，请检查开发者选项。"; }
                } else if ("copy".equals(action)) {
                    String coords = String.format(Locale.ROOT, "%.6f, %.6f", lat, lon);
                    getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("WGS 84", coords));
                    status = "已复制坐标 " + coords;
                } else if ("gpx".equals(action)) {
                    pendingGpx = gpx(lat, lon);
                    try {
                        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/gpx+xml")
                                .putExtra(Intent.EXTRA_TITLE, "hangzhou-point.gpx");
                        startActivityForResult(create, SAVE_GPX);
                    } catch (RuntimeException unavailable) { status = "手机上没有可用的文件保存器。"; }
                }
            });
        }
    }

    static String gpx(double lat, double lon) {
        String point = String.format(Locale.ROOT, "lat=\"%.6f\" lon=\"%.6f\"", lat, lon);
        Instant time = Instant.now();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<gpx version=\"1.1\" creator=\"Hangzhou Location\" xmlns=\"http://www.topografix.com/GPX/1/1\">"
                + "<wpt " + point + "><name>杭州定位点</name></wpt><trk><trkseg>"
                + "<trkpt " + point + "><time>" + time + "</time></trkpt>"
                + "<trkpt " + point + "><time>" + time.plusSeconds(1) + "</time></trkpt>"
                + "</trkseg></trk></gpx>\n";
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("gpx", pendingGpx);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SAVE_GPX) return;
        String file = pendingGpx;
        pendingGpx = null;
        if (result != RESULT_OK || data == null || data.getData() == null || file == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            Objects.requireNonNull(output).write(file.getBytes(StandardCharsets.UTF_8));
            status = "已保存 GPX。";
        } catch (IOException | RuntimeException error) { status = "保存 GPX 失败，请重试。"; }
    }

    @Override protected void onDestroy() {
        view.removeJavascriptInterface("HangzhouAndroid");
        view.destroy();
        super.onDestroy();
    }

    private static boolean removeProviders(LocationManager manager, String[] names) {
        boolean removed = true;
        for (String name : names) {
            try { manager.removeTestProvider(name); }
            catch (IllegalArgumentException absent) { }
            catch (SecurityException denied) { removed = false; }
        }
        return removed;
    }

    public static final class MockService extends Service {
        private static final String CHANNEL = "hangzhou_location";
        private static final String STOP = "org.hangzhou.mocklocation.STOP";
        private final Handler clock = new Handler(Looper.getMainLooper());
        private final ArrayList<String> installed = new ArrayList<>();
        private LocationManager manager;
        private double latitude, longitude;
        private String endMessage = RESTORED;
        private final Runnable repeat = this::pulse;

        @Override public IBinder onBind(Intent intent) { return null; }

        @Override public void onCreate() {
            super.onCreate();
            manager = getSystemService(LocationManager.class);
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel(CHANNEL, "模拟定位", NotificationManager.IMPORTANCE_LOW));
        }

        @Override public int onStartCommand(Intent intent, int flags, int id) {
            if (intent == null || STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
            latitude = intent.getDoubleExtra("lat", Double.NaN);
            longitude = intent.getDoubleExtra("lon", Double.NaN);
            if (!isInScope(latitude, longitude)) { stopSelf(); return START_NOT_STICKY; }
            try {
                startForeground(7, notification());
                if (installed.isEmpty()) {
                    install(LocationManager.GPS_PROVIDER);
                    for (String provider : PROVIDERS) {
                        if (!provider.equals(LocationManager.GPS_PROVIDER) && manager.getAllProviders().contains(provider)) {
                            try { install(provider); } catch (IllegalArgumentException unavailable) { }
                        }
                    }
                }
                clock.removeCallbacks(repeat);
                pulse();
            } catch (SecurityException denied) { endMessage = NO_PERMISSION; stopSelf(); }
            catch (RuntimeException failure) { endMessage = "无法启动系统模拟定位，请检查系统定位开关。"; stopSelf(); }
            return START_NOT_STICKY;
        }

        private void install(String provider) {
            boolean satellite = LocationManager.GPS_PROVIDER.equals(provider);
            boolean network = LocationManager.NETWORK_PROVIDER.equals(provider);
            manager.addTestProvider(provider, network, satellite, network, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
            installed.add(provider);
            manager.setTestProviderEnabled(provider, true);
        }

        private void pulse() {
            try {
                for (String provider : installed) {
                    Location location = new Location(provider);
                    location.setLatitude(latitude);
                    location.setLongitude(longitude);
                    location.setAccuracy(10.0f);
                    location.setAltitude(0);
                    location.setBearing(0);
                    location.setSpeed(0);
                    location.setTime(System.currentTimeMillis());
                    location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                    manager.setTestProviderLocation(provider, location);
                }
                status = String.format(Locale.ROOT, "模拟定位运行中 · %.6f, %.6f", latitude, longitude);
                clock.postDelayed(repeat, 1000);
            } catch (SecurityException denied) { endMessage = NO_PERMISSION; stopSelf(); }
            catch (RuntimeException failure) { endMessage = "系统没有接受模拟位置，请检查定位开关后重试。"; stopSelf(); }
        }

        private Notification notification() {
            int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
            PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, MockService.class).setAction(STOP), flags);
            return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentTitle("杭州模拟定位正在运行").setContentText("返回应用或点「停止模拟」恢复定位")
                    .setContentIntent(open).setOngoing(true)
                    .addAction(new Notification.Action.Builder(null, "停止模拟", stop).build()).build();
        }

        @Override public void onDestroy() {
            clock.removeCallbacks(repeat);
            boolean clean = removeProviders(manager, installed.toArray(new String[0]));
            installed.clear();
            status = clean ? endMessage : RESTORE_ERROR;
            stopForeground(STOP_FOREGROUND_REMOVE);
            super.onDestroy();
        }
    }
}
