// SPDX-License-Identifier: GPL-3.0-or-later
package org.hangzhou.mocklocation;

import android.content.Context;
import android.location.Location;

import com.huawei.hmf.tasks.Task;
import com.huawei.hms.api.HuaweiApiAvailability;
import com.huawei.hms.location.FusedLocationProviderClient;
import com.huawei.hms.location.LocationServices;

import java.util.function.BiConsumer;

final class HuaweiRoute {
    static final String UNAVAILABLE = "设备服务不可用";

    static Fusion.Client create(Context context) {
        try {
            if (HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(context) != 0) return null;
            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
            return new Fusion.Client() {
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
                    : new IllegalStateException("Location task did not complete successfully"));
        });
    }
}
