package android.os;

/** host stand-in (Korea Projection): CpTurns reads the time since boot; the tests set CpTurns.clock instead */
public final class SystemClock {
    private SystemClock() {}

    public static long elapsedRealtime() {
        return System.nanoTime() / 1000000L;
    }
}
