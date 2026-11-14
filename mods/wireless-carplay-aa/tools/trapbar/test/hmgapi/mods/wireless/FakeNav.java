package hmgapi.mods.wireless;

import java.util.ArrayList;
import java.util.List;

/**
 * PC 시험용 가짜 NavigationCluster(순정 내비 프로세스의 계기판 창구). 이름·인자 꼴만 순정과 같고(단속 정보 17개 정수 판,
 * 객체 판), 받은 호출을 "시각 종류 값"으로 적는다. 순정 코드는 들어 있지 않다.
 */
public class FakeNav {
    /** 받은 계기판 호출: "t=<ms> int bar=<막대> cam=<단속 칸>" 꼴 */
    static final List<String> SENT = new ArrayList<String>();
    /** 다시 보내기(REPLAYING) 중에 한 번 부를 일(다른 스레드의 순정 호출 흉내) */
    static Runnable onReplay;
    /** 순정 호출 몸체 안에서, 그 호출이 계기판에 닿기 전에 한 번 부를 일(before와 after 사이에 방송이 끼어드는 흉내) */
    static Runnable onStockBody;
    /** 이 이름의 호출은 예외를 던진다(다시 보내기 실패 흉내) */
    static String throwOn;

    static void clear() {
        SENT.clear();
        onReplay = null;
        onStockBody = null;
        throwOn = null;
    }

    private void rec(String s) {
        boolean replay = Wireless.REPLAYING.get() != null;
        // 순정 호출 몸체: 계기판에 닿기 전에 우리 다시 보내기가 먼저 닿는 경우(2차 리뷰 1의 경쟁)를 흉내 낸다
        if (!replay && onStockBody != null) {
            Runnable r = onStockBody;
            onStockBody = null;
            r.run();
        }
        SENT.add("t=" + android.os.SystemClock.now + " " + s + (replay ? " replay" : ""));
        if (replay && onReplay != null) {
            Runnable r = onReplay;
            onReplay = null;
            r.run();
        }
    }

    public void updateTBTTrapInfo(int cam, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10,
            int bar, int a12, int a13, int a14, int a15, int a16) {
        if ("int".equals(throwOn)) throw new IllegalStateException("cluster down");
        rec("int bar=" + bar + " cam=" + cam);
    }

    public void updateTBTTrapInfoNew(TrapInfo t) {
        if ("new".equals(throwOn)) throw new IllegalStateException("cluster down");
        rec("new bar=" + t.barGraphLevel + " cam=" + t.camera);
    }

    public void updateTBTInfo(int icon) {
        if ("tbt".equals(throwOn)) throw new IllegalStateException("cluster down");
        rec("tbt " + icon);
    }

    public void updateRemainDis(int meters) {
        rec("remain " + meters);
    }

    /** 순정 단속 정보 객체 판(모든 칸이 public int, barGraphLevel 있음) */
    public static class TrapInfo {
        public int camera, speedLimit, barGraphLevel, distance;

        public TrapInfo() {}
    }

    /** 객체 판인데 int가 아닌 칸이 있는 꼴(사본이 칸을 다 못 옮김: 훅을 걸지 않아야 함) */
    public static class BadNav {
        public void updateTBTTrapInfo(int cam, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8, int a9, int a10,
                int bar, int a12, int a13, int a14, int a15, int a16) {}

        public void updateTBTTrapInfoNew(BadInfo t) {}
    }

    public static class BadInfo {
        public int barGraphLevel;
        public String name;

        public BadInfo() {}
    }
}
