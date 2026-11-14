package android.os;

import java.util.ArrayList;
import java.util.List;

/**
 * PC 시험용 가짜 Handler: 모든 Handler가 하나의 대기열을 쓴다. 메시지는 runUntil이 시각 차례로(같으면 넣은 차례로) 돌리고,
 * 돌리기 전에 SystemClock.now를 그 메시지 시각으로 맞춘다. post는 바로 돌리지 않는다(실제처럼 대기열에 넣음).
 */
public class Handler {
    static final class Msg {
        final long at;
        final long order;
        final Runnable r;
        final Handler h;

        Msg(long at, long order, Runnable r, Handler h) {
            this.at = at;
            this.order = order;
            this.r = r;
            this.h = h;
        }
    }

    static final List<Msg> QUEUE = new ArrayList<Msg>();
    static long orders;

    public Handler() {}

    public Handler(Looper l) {}

    public boolean post(Runnable r) { return postDelayed(r, 0); }

    public boolean postDelayed(Runnable r, long ms) {
        QUEUE.add(new Msg(SystemClock.now + ms, orders++, r, this));
        return true;
    }

    public void removeCallbacks(Runnable r) {
        for (int i = QUEUE.size() - 1; i >= 0; i--) if (QUEUE.get(i).r == r && QUEUE.get(i).h == this) QUEUE.remove(i);
    }

    /** 시험용: until(ms)까지 할 일을 모두 돌리고 시계를 until로 둔다. 돌린 개수 */
    public static int runUntil(long until) {
        int n = 0;
        while (true) {
            Msg next = null;
            for (Msg m : QUEUE) if (m.at <= until && (next == null || m.at < next.at || (m.at == next.at && m.order < next.order))) next = m;
            if (next == null) break;
            QUEUE.remove(next);
            if (next.at > SystemClock.now) SystemClock.now = next.at;
            next.r.run();
            if (++n > 100000) throw new IllegalStateException("endless queue");
        }
        if (until > SystemClock.now) SystemClock.now = until;
        return n;
    }

    /** 시험용: 남은 할 일 수 */
    public static int pending() { return QUEUE.size(); }

    public static void clearAll() { QUEUE.clear(); }
}
