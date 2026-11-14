package android.os;

/** PC 시험용 가짜: 스레드를 만들지 않는다 */
public class HandlerThread {
    public static int started;

    public HandlerThread(String name) {}

    public void start() { started++; }

    public Looper getLooper() { return Looper.MAIN; }
}
