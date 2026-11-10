package android.os;

import java.util.ArrayList;
import java.util.List;

/**
 * host stand-in: post runs at once; postDelayed is kept until a test runs it.
 * delayed: the newest pending delayed runnable (the tests' unit-check timer). 1.7.3: several can be pending
 * (pending), so a one-shot timer next to the unit check doesn't replace it.
 */
public class Handler {
    public Runnable delayed;
    public final List<Runnable> pending = new ArrayList<Runnable>();
    /** 1.7.3 시나리오 시험용: 늦게 돌릴 일이 돌 시각(ms, now 기준). now는 시험이 맞춘다 */
    public final java.util.Map<Runnable, Long> dueAt = new java.util.HashMap<Runnable, Long>();
    public static long now;

    public boolean post(Runnable r) { r.run(); return true; }

    public boolean postDelayed(Runnable r, long ms) {
        pending.remove(r);
        pending.add(r);
        dueAt.put(r, Long.valueOf(now + ms));
        delayed = r;
        return true;
    }

    public void removeCallbacks(Runnable r) {
        pending.remove(r);
        dueAt.remove(r);
        if (delayed == r) delayed = pending.isEmpty() ? null : pending.get(pending.size() - 1);
    }

    /** 1.7.3 시험용: r이 걸려 있으면 떼어 내고 돌린다 */
    public boolean runPending(Runnable r) {
        if (!pending.contains(r)) return false;
        removeCallbacks(r);
        r.run();
        return true;
    }
}
