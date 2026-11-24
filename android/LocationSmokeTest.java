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
            awaitStatus(ui, "已向系统发送模拟位置", 10_000);
            runOnMainSync(() -> ui.select("probe", 0, 0));
            awaitStatus(ui, "已读回系统模拟位置", 10_000);
            runOnMainSync(() -> ui.select("stop", 0, 0));
            awaitStatus(ui, "已停止模拟", 10_000);
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

    private static void awaitStatus(MainActivity.Controls controls, String prefix, long timeout) {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < end) {
            if (controls.status().startsWith(prefix)) return;
            SystemClock.sleep(200);
        }
        throw new AssertionError("Expected " + prefix + " but got " + controls.status());
    }
}
