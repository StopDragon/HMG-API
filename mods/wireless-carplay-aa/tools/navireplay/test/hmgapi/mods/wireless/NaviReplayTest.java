package hmgapi.mods.wireless;

import android.content.Context;
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
 * 순정 내비 프로세스의 Wireless.navigation(폰 안내 중 순정 계기판 호출 막기, 끝난 뒤 다시 보내기: resend·scheduleBackup)을
 * 가짜 시계·대기열·계기판으로 돌린다. 1.7.3 K-10 TrapBarTest에서 막대(TrapBar) 몫을 빼고 남긴 것이다(1.7.4: 막대를 뺐다).
 * 1.7.4 추가: 단속 정보 프레임은 훅이 걸리지 않고 그대로 나감, 옛 설정 aa_name_hold가 남아 있어도 읽지 않음.
 * 모드 소스는 바꾸지 않고 그대로 컴파일한다(Wireless.appLoaded로 프로세스를 흉내 낸다).
 */
public final class NaviReplayTest {
    static int fail;
    static ModContext mod;
    static Context ctx;
    static FakeNav nav;
    static Method mTbt, mRemain;

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
        new Wireless().appLoaded(mod, new LoadedApp(Wireless.NAVIGATION, NaviReplayTest.class.getClassLoader(), ctx));
        nav = new FakeNav();
        mTbt = FakeNav.class.getMethod("updateTBTInfo", int.class);
        mRemain = FakeNav.class.getMethod("updateRemainDis", int.class);
    }

    /** 순정 내비의 계기판 호출(Wireless.navigation 훅을 지나감). 계기판에 실제로 나갔으면 true */
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
        oldSetting();
        replay();
        System.exit(fail);
    }

    /** 1.7.4: 순정 계기판 호출 훅만 걸리고, 단속 정보 프레임에는 훅·방송 받기가 없다(막대를 뺐다) */
    static void install() throws Throwable {
        boot(FakeNav.class, false);
        check("install: Wireless.navigation hooked every stock turn-by-turn point", mod.points.size() == Wireless.NAVI_TBT.length);
        check("1.7.4: no trap frame hook and no phone bar receiver (bar removed)", Hooks.HOOKED.isEmpty() && ctx.receivers.isEmpty());
        boolean trapHooked = false;
        for (String m : Wireless.NAVI_TBT) if (m.contains("trap")) trapHooked = true;
        check("1.7.4: trap frames are not in the blocked list: cameras and speed limits pass while a phone guides", !trapHooked);
        Car.guiding = true;
        nav.updateTBTTrapInfo(5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 50, 0, 0, 0, 0, 0);
        check("1.7.4: a stock trap frame while the phone guides goes out with the stock bar (50) and camera (5)", last().endsWith("int bar=50 cam=5"));
        Car.guiding = false;
    }

    /** 1.7.4 끝난 기준 ⑤: 설치된 차에 옛 설정 aa_name_hold(켬/끔)가 남아 있어도 읽지 않고 오류도 없다 */
    static void oldSetting() throws Throwable {
        for (boolean v : new boolean[] { true, false }) {
            Hooks.HOOKED.clear();
            Handler.clearAll();
            mod = new ModContext();
            mod.settings.put("aa_name_hold", v);
            mod.settings.put("aa_ap", false);   // 다른 훅(핫스폿 신호)은 이 시험의 가짜 안드로이드에 없어 끈다
            ctx = new Context();
            new Wireless().appLoaded(mod, new LoadedApp(Wireless.GPM, NaviReplayTest.class.getClassLoader(), ctx));
            check("old setting aa_name_hold=" + v + " left on the car: Android Auto app loads, the key is never read, no error",
                    !mod.asked.contains("aa_name_hold") && mod.asked.contains("tbt") && mod.count("ERROR") == 0
                            && mod.points.containsKey("gpm.navigation_status.next_turn"));
        }
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
