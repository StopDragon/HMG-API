package android.os;

/** PC 시험용 가짜 시계: 시험이 now를 정한다(Handler.runUntil이 앞으로 돌림) */
public final class SystemClock {
    public static long now = 1000000L;

    public static long elapsedRealtime() { return now; }
}
