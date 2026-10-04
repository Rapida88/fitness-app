package de.chris.fitbisapril;

import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.datatypes.StepsRecord;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;

import org.json.JSONObject;

import java.time.LocalDate;
import java.util.concurrent.Executor;

/** Schritte aus Health Connect (Android 14+), z. B. vom Mi Band über die Mi-Fitness-App. Nur ab API 34 laden. */
final class Steps {
    static final String PERM = "android.permission.health.READ_STEPS";

    interface Cb { void done(boolean ok, String text); }

    static void read(Context ctx, int days, final Cb cb) {
        HealthConnectManager hcm = ctx.getSystemService(HealthConnectManager.class);
        if (hcm == null) { cb.done(false, "unsupported"); return; }
        final Handler h = new Handler(Looper.getMainLooper());
        Executor onMain = new Executor() { @Override public void execute(Runnable r) { h.post(r); } };
        final JSONObject out = new JSONObject();
        final int[] left = { days };
        final String[] err = { null };
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            final LocalDate d = today.minusDays(i);
            LocalTimeRangeFilter f = new LocalTimeRangeFilter.Builder()
                .setStartTime(d.atStartOfDay())
                .setEndTime(d.plusDays(1).atStartOfDay())
                .build();
            AggregateRecordsRequest<Long> req = new AggregateRecordsRequest.Builder<Long>(f)
                .addAggregationType(StepsRecord.STEPS_COUNT_TOTAL)
                .build();
            hcm.aggregate(req, onMain, new OutcomeReceiver<AggregateRecordsResponse<Long>, HealthConnectException>() {
                @Override public void onResult(AggregateRecordsResponse<Long> r) {
                    Long v = null;
                    try { v = r.get(StepsRecord.STEPS_COUNT_TOTAL); } catch (Exception e) { }
                    try { if (v != null) out.put(d.toString(), v.longValue()); } catch (Exception e) { }
                    finish();
                }
                @Override public void onError(HealthConnectException e) {
                    if (err[0] == null) err[0] = e.getErrorCode() == HealthConnectException.ERROR_SECURITY ? "no_perm" : "hc_" + e.getErrorCode();
                    finish();
                }
                private void finish() {
                    if (--left[0] > 0) return;
                    if (out.length() == 0 && err[0] != null) cb.done(false, err[0]);
                    else cb.done(true, out.toString());
                }
            });
        }
    }
}
