// SPDX-License-Identifier: GPL-3.0-or-later
package org.hangzhou.mocklocation.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import org.hangzhou.mocklocation.MainActivity;

public final class LocationSmokeTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle report = new Bundle();
        MainActivity activity = null;
        MainActivity.Controls controls = null;
        int result = Activity.RESULT_CANCELED;
        try {
            Intent launch = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity) startActivitySync(launch);
            waitForIdleSync();
            final MainActivity.Controls ui = activity.new Controls();
            controls = ui;
            runOnMainSync(() -> ui.select("start", 30.250001, 120.155002));
            awaitStatus(ui, "Android：已提交", 10_000);
            boolean googleAvailable = !ui.status().contains("Google 融合：设备服务不可用");
            if (googleAvailable) awaitStatus(ui, "Google 融合：已提交模拟位置", 15_000);
            runOnMainSync(() -> ui.select("probe", 0, 0));
            awaitStatus(ui, "Android：已读回", 10_000);
            if (googleAvailable) awaitStatus(ui, "融合接口：已读回 Google 融合", 10_000);
            runOnMainSync(() -> ui.select("stop", 0, 0));
            awaitStatus(ui, "已停止模拟", 10_000);
            if (googleAvailable) awaitStatus(ui, "Google 融合：已停止", 10_000);
            report.putString("stream", "HANGZHOU_SMOKE_PASS\n");
            result = Activity.RESULT_OK;
        } catch (Throwable failure) {
            report.putString("stream", "HANGZHOU_SMOKE_FAIL: " + failure + "\n");
        } finally {
            if (controls != null) controls.select("stop", 0, 0);
            if (activity != null) activity.finish();
            finish(result, report);
        }
    }

    private static void awaitStatus(MainActivity.Controls controls, String expected, long timeout) {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < end) {
            if (controls.status().contains(expected)) return;
            SystemClock.sleep(200);
        }
        throw new AssertionError("Expected " + expected + " but got " + controls.status());
    }
}
