// SPDX-License-Identifier: GPL-3.0-or-later
package org.hangzhou.mocklocation;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Coordinates the public fusion APIs on the main thread. */
final class Fusion {
    interface Client {
        void setMode(boolean enabled, BiConsumer<Void, Exception> done);
        void submit(Location fix, BiConsumer<Void, Exception> done);
        void read(BiConsumer<Location, Exception> done);
    }

    private static Fusion instance;
    private final Route google;
    private final Route huawei;
    private volatile String status = "";

    static Fusion get(Context context) {
        if (instance == null) instance = new Fusion(context.getApplicationContext());
        return instance;
    }

    private Fusion(Context context) {
        google = new Route("Google 融合", googleClient(context), "设备服务不可用");
        huawei = new Route("华为融合", HuaweiRoute.create(context), HuaweiRoute.UNAVAILABLE);
        refresh();
    }

    private static Client googleClient(Context context) {
        try {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != 0) return null;
            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
            return new Client() {
                public void setMode(boolean enabled, BiConsumer<Void, Exception> done) {
                    watch(client.setMockMode(enabled), done);
                }
                public void submit(Location fix, BiConsumer<Void, Exception> done) {
                    watch(client.setMockLocation(fix), done);
                }
                public void read(BiConsumer<Location, Exception> done) {
                    watch(client.getLastLocation(), done);
                }
            };
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    private static <T> void watch(Task<T> task, BiConsumer<T, Exception> done) {
        task.addOnCompleteListener(result -> {
            if (result.isSuccessful()) done.accept(result.getResult(), null);
            else done.accept(null, result.getException() != null ? result.getException()
                    : new IllegalStateException("Location task cancelled"));
        });
    }

    void begin() { google.begin(); huawei.begin(); }
    void submit(Location fix) { google.submit(fix); huawei.submit(fix); }
    void stop() { google.stop(); huawei.stop(); }
    boolean canUse() { return google.canUse() || huawei.canUse(); }
    String status() { return status; }

    void read(BiConsumer<String, Location> result) {
        google.read(result);
        huawei.read(result);
    }

    private void refresh() { status = google.label + "：" + google.state + " · " + huawei.label + "：" + huawei.state; }

    private final class Route {
        private final String label;
        private final Client client;
        private final Handler main = new Handler(Looper.getMainLooper());
        private String state;
        private boolean wanted, enabled, busy, failed;
        private int stopRetries;
        private Location next;
        private final Runnable retry = this::advance;

        Route(String label, Client client, String unavailable) {
            this.label = label;
            this.client = client;
            this.state = client == null ? unavailable : "待启动";
        }

        private void report(String message) { state = message; refresh(); }

        void begin() {
            if (client == null) return;
            main.removeCallbacks(retry);
            wanted = true;
            failed = false;
            next = null;
            stopRetries = 0;
            advance();
        }

        void submit(Location fix) {
            if (!canUse()) return;
            next = fix;
            advance();
        }

        void stop() {
            if (client == null) return;
            main.removeCallbacks(retry);
            wanted = false;
            next = null;
            advance();
        }

        boolean canUse() { return client != null && wanted && !failed; }

        private void advance() {
            if (busy) return;
            if (!wanted) {
                if (enabled) changeMode(false);
                else report("已停止");
            } else if (!failed && !enabled) {
                changeMode(true);
            } else if (!failed && next != null) {
                Location pending = next;
                next = null;
                busy = true;
                call(done -> client.submit(pending, done), error -> {
                    busy = false;
                    if (wanted) report(error == null ? "已提交模拟位置" : "提交失败，等待下一次尝试");
                    advance();
                });
            }
        }

        private void changeMode(boolean activate) {
            busy = true;
            report(activate ? "正在启动" : "正在停止");
            call(done -> client.setMode(activate, done), error -> {
                busy = false;
                if (error == null) {
                    enabled = activate;
                    report(activate ? "已启动" : "已停止");
                } else if (activate) {
                    failed = true;
                    report("设备拒绝开启模拟定位");
                } else {
                    report("停止未确认，请重试恢复真实定位");
                    if (!wanted && stopRetries++ < 2) main.postDelayed(retry, 1000);
                    return;
                }
                advance();
            });
        }

        private void call(Consumer<BiConsumer<Void, Exception>> operation, Consumer<Exception> done) {
            try { operation.accept((ignored, error) -> main.post(() -> done.accept(error))); }
            catch (RuntimeException error) { main.post(() -> done.accept(error)); }
        }

        void read(BiConsumer<String, Location> result) {
            if (!canUse() || !enabled) return;
            try {
                client.read((fix, error) -> {
                    if (error == null && fix != null) main.post(() -> result.accept(label, fix));
                });
            } catch (RuntimeException unavailable) {
                // The platform probe remains independent when SDK readback fails.
            }
        }
    }
}
