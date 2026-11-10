package android.os;

/** PC 시험용 가짜(모든 Handler가 한 줄 대기열을 쓴다) */
public final class Looper {
    static final Looper MAIN = new Looper();

    public static Looper getMainLooper() { return MAIN; }
}
