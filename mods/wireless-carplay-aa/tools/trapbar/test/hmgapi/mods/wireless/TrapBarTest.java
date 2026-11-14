package hmgapi.mods.wireless;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import hmgapi.hook.Car;
import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.Points;

/**
 * K-10(1.7.3): 순정 내비 프로세스의 TrapBar(단속 정보 프레임 막대 칸)와 Wireless의 순정 값 다시 보내기(resend·scheduleBackup)를
 * 가짜 시계·가짜 대기열·가짜 계기판으로 돌린다. 모드 소스는 바꾸지 않고 그대로 컴파일한다(Wireless.appLoaded로 순정 내비
 * 프로세스를 흉내 내 navigation()·TrapBar.install을 실제 경로로 탄다).
 */
public final class TrapBarTest {
    static int fail;
    static ModContext mod;
    static Context ctx;
    static FakeNav nav;
    static Method mInt, mNew, mTbt, mRemain;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what + (ok ? "" : "\n  sent " + FakeNav.SENT + "\n  logs " + mod.logs));
        if (!ok) fail = 1;
    }

    /** 순정 내비 프로세스 하나를 새로 띄운다(훅·대기열·기록을 비움) */
    static void boot(Class<?> navClass, boolean failRegister) throws Throwable {
        Hooks.HOOKED.clear();
        Handler.clearAll();
        FakeNav.clear();
        Points.CLASSES.clear();
        if (navClass != null) Points.CLASSES.put("framework.navigation_cluster", navClass);
        Car.guiding = false;
        Car.phoneGuidingCalls = 0;
        SystemClock.now += 1000000L;
        mod = new ModContext();
        ctx = new Context();
        ctx.failRegister = failRegister;
        new Wireless().appLoaded(mod, new LoadedApp(Wireless.NAVIGATION, TrapBarTest.class.getClassLoader(), ctx));
        nav = new FakeNav();
        mInt = Hooks.hooked("updateTBTTrapInfo");
        mNew = Hooks.hooked("updateTBTTrapInfoNew");
        mTbt = FakeNav.class.getMethod("updateTBTInfo", int.class);
        mRemain = FakeNav.class.getMethod("updateRemainDis", int.class);
    }

    static void stockInt(int cam, int bar) throws Throwable {
        Object[] a = new Object[TrapArgs.INT_ARGS];
        for (int i = 0; i < a.length; i++) a[i] = Integer.valueOf(0);
        a[0] = Integer.valueOf(cam);
        a[TrapArgs.BAR_ARG] = Integer.valueOf(bar);
        Hooks.call(nav, mInt, a);
    }

    static void stockNew(int cam, int bar) throws Throwable {
        FakeNav.TrapInfo t = new FakeNav.TrapInfo();
        t.camera = cam;
        t.barGraphLevel = bar;
        t.speedLimit = 80;
        Hooks.call(nav, mNew, t);
    }

    /** 폰 막대 방송(PhoneBar.ACTION). 받기는 바로 하고, 처리는 대기열에서 */
    static void phoneBar(int b) {
        ctx.receivers.get(0).onReceive(ctx, new Intent(PhoneBar.ACTION).putExtra(PhoneBar.EXTRA_BAR, b));
    }

    /** 순정 내비의 다른 계기판 호출(Wireless.navigation 훅을 지나감). 계기판에 실제로 나갔으면 true */
    static boolean naviCall(String point, Method m, int v) throws Throwable {
        MethodHook h = mod.points.get("framework.navigation_cluster." + point);
        MethodHook.Param p = new MethodHook.Param(m, nav, new Object[] { Integer.valueOf(v) });
        h.before(p);
        if (!p.returnEarly()) m.invoke(nav, p.args);
        h.after(p);
        return !p.returnEarly();
    }

    static void run(long ms) {
        Handler.runUntil(SystemClock.now + ms);
    }

    static String last() {
        return FakeNav.SENT.isEmpty() ? "" : FakeNav.SENT.get(FakeNav.SENT.size() - 1);
    }

    static List<String> sentWith(String part) {
        List<String> r = new ArrayList<String>();
        for (String s : FakeNav.SENT) if (s.contains(part)) r.add(s);
        return r;
    }

    static long timeOf(String s) {
        return Long.parseLong(s.substring(2, s.indexOf(' ')));
    }

    public static void main(String[] args) throws Throwable {
        install();
        stockAndOverride();
        gapAndBurst();
        interleaved();
        afterHook();
        expiry();
        twoSlots();
        guards();
        logs();
        replay();
        System.exit(fail);
    }

    /** 설치: 두 판 훅, 방송 받기, 없는 차·받기 실패에서 null */
    static void install() throws Throwable {
        boot(FakeNav.class, false);
        check("install: both trap frame forms hooked (17 ints and the object form)", mInt != null && mNew != null && Hooks.HOOKED.size() == 2);
        check("install: listens for the phone bar broadcast", ctx.receivers.size() == 1 && ctx.filters.get(0).actions.contains(PhoneBar.ACTION));
        check("install: one log line with the hook count", mod.count("phone turn bar goes into the stock trap frame (2 hooks)") == 1);
        check("install: Wireless.navigation hooked every stock turn-by-turn point", mod.points.size() == Wireless.NAVI_TBT.length);
        boot(null, false);
        check("install: no NavigationCluster on this car: no hooks, no receiver, one line",
                Hooks.HOOKED.isEmpty() && ctx.receivers.isEmpty() && mod.count("no NavigationCluster for the turn bar") == 1);
        boot(FakeNav.class, true);
        check("install (review 10): the receiver can't be registered: no trap hooks at all", Hooks.HOOKED.isEmpty()
                && mod.count("turn bar receiver not registered") == 1);
        boot(FakeNav.BadNav.class, false);
        check("install (review 3): object form with a non-int field: only the 17-int form is hooked",
                Hooks.hooked("updateTBTTrapInfo") != null && Hooks.hooked("updateTBTTrapInfoNew") == null
                        && mod.count("updateTBTTrapInfoNew not used for the turn bar") == 1 && mod.count("(1 hooks)") == 1);
    }

    /** 순정 호출 통과·기억, 폰 막대로 다시 보내기, 덮어쓴 순정 호출, NONE으로 되돌리기 */
    static void stockAndOverride() throws Throwable {
        boot(FakeNav.class, false);
        phoneBar(70);
        run(10);
        check("no stock trap call seen yet: a phone bar sends nothing", FakeNav.SENT.isEmpty());
        Car.phoneGuidingCalls = 0;
        stockInt(5, 50);
        check("stock call, phone not guiding: passes with the stock bar", last().endsWith("int bar=50 cam=5"));
        check("a phone bar is alive (70 came before): phone guidance asked once for this stock call", Car.phoneGuidingCalls == 1);
        Car.guiding = true;
        phoneBar(70);
        run(10);
        check("phone guiding, bar 70: the remembered stock frame is resent with bar 70 (camera kept)", last().equals(sentWithLast("int bar=70 cam=5 replay")));
        int n = FakeNav.SENT.size();
        phoneBar(70);
        run(10);
        check("the same bar again: nothing resent", FakeNav.SENT.size() == n);
        stockInt(6, 40);
        check("stock call while the phone bar is on: only the bar slot changes (40 -> 70), camera 6 kept", last().endsWith("int bar=70 cam=6"));
        check("log: numbers only for the changed stock frame", mod.count("navigation: stock trap frame: bar 40 -> 70") == 1);
        run(1000);
        phoneBar(PhoneBar.NONE);
        run(1000);
        check("phone bar NONE: back to the stock bar (40) at once", last().endsWith("int bar=40 cam=6 replay"));
        n = FakeNav.SENT.size();
        Car.phoneGuidingCalls = 0;
        stockInt(7, 30);
        check("stock call after NONE: passes as stock, and phone guidance isn't asked (review 10: binder saving)",
                last().endsWith("int bar=30 cam=7") && Car.phoneGuidingCalls == 0 && FakeNav.SENT.size() == n + 1);
    }

    static String sentWithLast(String tail) {
        String l = last();
        return l.endsWith(tail) ? l : "(want *" + tail + ")";
    }

    /** MIN_GAP_MS(200) 간격, 몰려 온 방송은 마지막 값 하나 */
    static void gapAndBurst() throws Throwable {
        boot(FakeNav.class, false);
        Car.guiding = true;
        stockInt(1, 50);
        phoneBar(70);
        run(0);
        String a = last();
        run(50);
        phoneBar(60);
        run(0);
        check("a second bar 50 ms later is held back (min gap 200 ms)", last().equals(a));
        run(300);
        List<String> r = sentWith("replay");
        check("then sent once the gap is over: bar 60, at least 200 ms after bar 70",
                r.size() == 2 && r.get(1).contains("bar=60") && timeOf(r.get(1)) - timeOf(r.get(0)) >= TrapBar.MIN_GAP_MS);
        run(1000);
        int before = sentWith("replay").size();
        phoneBar(80);
        phoneBar(85);
        phoneBar(90);
        run(1000);
        r = sentWith("replay");
        check("a burst of three broadcasts before the thread runs: one resend with the last value (90)",
                r.size() == before + 1 && r.get(r.size() - 1).contains("bar=90"));
        for (int k = 0; k < 20; k++) {
            phoneBar(10 + (k % 9) * 10);
            run(20);
        }
        run(1000);
        r = sentWith("replay");
        boolean gaps = true;
        for (int k = before + 1; k < r.size(); k++) if (timeOf(r.get(k)) - timeOf(r.get(k - 1)) < TrapBar.MIN_GAP_MS) gaps = false;
        check("20 bars every 20 ms: resends never closer than 200 ms (at most 5 a second)", gaps && r.size() - before - 1 <= 3);
    }

    /** 다시 보내는 사이 다른 스레드의 순정 호출이 끼어듦: 최신 순정 값으로 한 번 더(리뷰 1) */
    static void interleaved() throws Throwable {
        boot(FakeNav.class, false);
        Car.guiding = true;
        stockInt(1, 50);
        FakeNav.onReplay = new Runnable() {
            @Override
            public void run() {
                Thread th = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            stockInt(9, 55);   // 새 단속 값(카메라 9)
                        } catch (Throwable t) {
                            throw new RuntimeException(t);
                        }
                    }
                });
                th.start();
                try {
                    th.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        phoneBar(70);
        run(0);
        List<String> r = sentWith("replay");
        check("review 1: a stock call came in during the resend (camera 9)", r.size() == 1 && r.get(0).contains("cam=1")
                && FakeNav.SENT.toString().contains("int bar=70 cam=9"));
        run(TrapBar.MIN_GAP_MS);
        r = sentWith("replay");
        check("review 1: resent once more after 200 ms with the newest stock values (camera 9, bar 70): no old camera left last",
                r.size() == 2 && r.get(1).contains("int bar=70 cam=9 replay"));
        run(5000);
        check("review 1: and only once (no loop)", sentWith("replay").size() == 2);
    }

    /** 순정 호출이 막대를 넣고 나가기 전에 방송이 바뀜: after에서 한 번 더(2차 리뷰 1) */
    static void afterHook() throws Throwable {
        boot(FakeNav.class, false);
        Car.guiding = true;
        stockInt(1, 50);
        phoneBar(70);
        run(10);
        run(1000);
        FakeNav.onStockBody = new Runnable() {
            @Override
            public void run() {
                phoneBar(40);   // 순정 호출 몸체가 도는 사이 방송(다시 보내기 스레드가 먼저 처리함)
                Handler.runUntil(SystemClock.now);
            }
        };
        int before = sentWith("replay").size();
        stockInt(2, 50);
        check("2nd review 1: race: our resend with bar 40 landed first, then the stock frame with the old bar 70 landed last",
                last().endsWith("int bar=70 cam=2") && sentWith("int bar=40 cam=2 replay").size() == 1);
        run(TrapBar.MIN_GAP_MS + 10);
        List<String> r = sentWith("replay");
        check("2nd review 1: the after hook sees the bar it put (70) is no longer current: the latest bar (40) goes out once more",
                r.size() == before + 2 && last().endsWith("int bar=40 cam=2 replay"));
    }

    /** 방송이 끊기면 EXPIRE_MS(+0.5초) 뒤 한 번 순정 막대로, 폰 안내가 끝나면 순정의 다음 계기판 호출 때 */
    static void expiry() throws Throwable {
        boot(FakeNav.class, false);
        Car.guiding = true;
        stockInt(3, 50);
        phoneBar(70);
        run(10);
        long t0 = SystemClock.now;
        run(PhoneBar.EXPIRE_MS - 1000);
        check("expiry: still the phone bar just before 3 minutes", last().endsWith("int bar=70 cam=3 replay"));
        run(2000);
        String l = last();
        check("expiry: the broadcasts stopped: back to the stock bar (50) once, about 3 min 0.5 s after the last one",
                l.endsWith("int bar=50 cam=3 replay") && timeOf(l) >= t0 + PhoneBar.EXPIRE_MS && timeOf(l) <= t0 + PhoneBar.EXPIRE_MS + 600);
        int n = FakeNav.SENT.size();
        run(10L * PhoneBar.EXPIRE_MS);
        check("expiry: nothing more afterwards (no repeating timer)", FakeNav.SENT.size() == n && Handler.pending() == 0);
        // 새 방송이 오면 만료 시계가 다시 걸린다(앞의 것은 지움)
        phoneBar(60);
        run(10);
        run(PhoneBar.EXPIRE_MS / 2);
        phoneBar(60);
        run(10);
        long t1 = SystemClock.now;
        run(PhoneBar.EXPIRE_MS - 1000);
        check("expiry: a later broadcast restarts the 3 minutes (no early restore)", last().contains("bar=60"));
        run(2000);
        check("expiry: restored 3 min after the later broadcast", last().endsWith("int bar=50 cam=3 replay") && timeOf(last()) >= t1 + PhoneBar.EXPIRE_MS);
        // 폰 안내가 끝나면(포커스·경로 끝) 순정의 다음 계기판 호출에서 되돌린다
        phoneBar(70);
        run(10);
        Car.guiding = false;
        naviCall("update_tbt_info", mTbt, 5);
        run(TrapBar.MIN_GAP_MS + 10);
        check("phone guidance over: the next stock cluster call puts the stock bar back (check, within the 200 ms gap)",
                last().endsWith("int bar=50 cam=3 replay"));
    }

    /** 두 판(17개 정수, 객체)을 따로 기억해 둘 다 다시 보냄. 범위 밖 막대 객체는 기억만(2차 리뷰 2·4) */
    static void twoSlots() throws Throwable {
        boot(FakeNav.class, false);
        Car.guiding = true;
        stockInt(1, 50);
        stockNew(2, 60);
        phoneBar(70);
        run(10);
        List<String> r = sentWith("replay");
        check("2nd review 4: both forms remembered and resent with the phone bar, each with its own values",
                r.size() == 2 && r.get(0).contains("int bar=70 cam=1") && r.get(1).contains("new bar=70 cam=2"));
        stockNew(4, 30);
        check("object form while the phone bar is on: only barGraphLevel changes (camera 4 kept)", last().endsWith("new bar=70 cam=4"));
        run(1000);
        stockNew(5, 255);
        check("2nd review 2: object form with a bar out of range (255): passes unchanged", last().endsWith("new bar=255 cam=5"));
        run(1000);
        phoneBar(80);
        run(10);
        r = sentWith("replay");
        check("2nd review 2: ...but it is remembered: the next resend carries camera 5 with the phone bar",
                r.get(r.size() - 1).contains("new bar=80 cam=5") && r.get(r.size() - 2).contains("int bar=80 cam=1"));
    }

    /** 다른 펌웨어 꼴(인자 수), 실패는 종류별 한 줄 */
    static void guards() throws Throwable {
        boot(FakeNav.class, false);
        Car.guiding = true;
        // 인자 수가 다른 호출(같은 이름의 다른 꼴)은 바꾸지도 기억하지도 않는다
        MethodHook h = Hooks.HOOKED.get(mInt);
        MethodHook.Param p = new MethodHook.Param(mInt, nav, new Object[] { Integer.valueOf(1), Integer.valueOf(2) });
        h.before(p);
        h.after(p);
        phoneBar(70);
        run(10);
        check("review 3: a trap call with another argument count: not remembered, nothing resent", FakeNav.SENT.isEmpty());
        stockInt(1, 50);
        stockNew(2, 50);
        FakeNav.throwOn = "int";
        phoneBar(60);
        run(10);
        check("review 8: one form fails on resend: the other still goes out", last().endsWith("new bar=60 cam=2 replay"));
        FakeNav.throwOn = "new";
        run(1000);
        phoneBar(40);
        run(10);
        check("review 8: resend failures logged once per kind (no address, only the exception type)",
                mod.count("trap frame resend failed (InvocationTargetException)") == 1);
    }

    /** 로그는 30줄까지 */
    static void logs() throws Throwable {
        boot(FakeNav.class, false);
        Car.guiding = true;
        stockInt(1, 50);
        for (int k = 0; k < 60; k++) {
            phoneBar(10 + (k % 2) * 10);
            run(300);
        }
        int lines = 0;
        for (String l : mod.logs) if (l.startsWith("navigation: trap frame resent") || l.startsWith("navigation: stock trap frame")) lines++;
        check("trap bar log lines stop at 30 (LOGS_MAX)", lines == TrapBar.LOGS_MAX && sentWith("replay").size() == 60);
    }

    /** Wireless.navigation의 순정 값 다시 보내기(StockReplay + resend + scheduleBackup) */
    static void replay() throws Throwable {
        boot(FakeNav.class, false);
        Car.guiding = true;
        check("phone guiding: the stock turn-by-turn call is held back", !naviCall("update_tbt_info", mTbt, 11) && FakeNav.SENT.isEmpty());
        naviCall("update_remain_dis", mRemain, 900);
        Car.guiding = false;
        long t0 = SystemClock.now;
        boolean out = naviCall("update_tbt_info", mTbt, 12);
        check("phone guidance over: the stock call goes out again, the backup timer (6 s) is set", out && last().endsWith("tbt 12")
                && Handler.pending() >= 1);
        run(1600);
        naviCall("update_tbt_info", mTbt, 13);
        check("round 1 (1.5 s, inside a stock call): the other remembered values (remaining distance 900) are resent",
                FakeNav.SENT.toString().contains("remain 900 replay")
                        && mod.count("navigation: stock cluster values resent after phone guidance, round 1: 1 calls") == 1);
        run(3500);
        naviCall("update_tbt_info", mTbt, 14);
        check("round 2 (5 s): resent again, then done", mod.count("round 2: 1 calls") == 1);
        int n = FakeNav.SENT.size();
        Handler.runUntil(t0 + StockReplay.BACKUP_MS + 1000);
        check("the backup timer finds nothing left (its generation is over)", FakeNav.SENT.size() == n && Handler.pending() == 0);

        // 순정 호출이 다시 오지 않으면 백업 타이머가 남은 차례를 한 번에
        boot(FakeNav.class, false);
        Car.guiding = true;
        naviCall("update_tbt_info", mTbt, 21);
        naviCall("update_remain_dis", mRemain, 700);
        Car.guiding = false;
        t0 = SystemClock.now;
        naviCall("update_remain_dis", mRemain, 650);
        run(StockReplay.BACKUP_MS + 100);
        List<String> r = sentWith("replay");
        check("no more stock calls: the backup (6 s) resends every remembered value once, round 2",
                r.size() == 2 && FakeNav.SENT.toString().contains("tbt 21 replay") && FakeNav.SENT.toString().contains("remain 650 replay")
                        && mod.count("round 2: 2 calls") == 1 && timeOf(r.get(0)) >= t0 + StockReplay.BACKUP_MS);
        check("the replay marker is cleared after the resend (Wireless.REPLAYING)", Wireless.REPLAYING.get() == null);
        // 백업 때 폰 안내가 다시 시작됐으면 보내지 않는다
        boot(FakeNav.class, false);
        Car.guiding = true;
        naviCall("update_tbt_info", mTbt, 31);
        Car.guiding = false;
        naviCall("update_remain_dis", mRemain, 500);
        Car.guiding = true;
        run(StockReplay.BACKUP_MS + 100);
        check("backup while the phone guides again: nothing resent", sentWith("replay").isEmpty());
        // 다시 보내다 실패하면 개수만
        boot(FakeNav.class, false);
        Car.guiding = true;
        naviCall("update_tbt_info", mTbt, 41);
        naviCall("update_remain_dis", mRemain, 400);
        Car.guiding = false;
        naviCall("update_remain_dis", mRemain, 380);
        FakeNav.throwOn = "tbt";
        run(StockReplay.BACKUP_MS + 100);
        check("a resend that throws is counted: 'round 2: 1 calls, 1 failed'",
                mod.count("round 2: 1 calls, 1 failed") == 1);
        // Wireless.resend 바로: 빈 목록이면 아무것도 안 함
        int lines = mod.logs.size();
        Wireless.resend(mod, new StockReplay.Out());
        check("Wireless.resend with nothing to send: no call, no line", mod.logs.size() == lines);
    }
}
