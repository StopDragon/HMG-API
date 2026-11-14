package hmgapi.mods.wireless;
// Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-09, 모드 2.3.3.

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import hmgapi.hook.Car;
import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;
import hmgapi.hook.Points;

/**
 * 순정 내비 프로세스에서 단속 정보 프레임(NavigationCluster.updateTBTTrapInfo / updateTBTTrapInfoNew)의 막대 칸만
 * 폰 막대로 바꾼다(판단은 PhoneBar.Navi, 인자 사본은 TrapArgs). 단속 카메라·제한속도 등 다른 칸은 순정 값 그대로다.
 *   - 순정 호출: 폰이 안내 중이고 폰 막대가 살아 있으면 막대 칸만 바꿔 보낸다. 마지막 순정 호출(인자 사본)을 기억한다.
 *   - 폰 방송(PhoneBar.ACTION): 값이 바뀌면 기억한 순정 호출에 막대만 바꿔 다시 보낸다.
 *   - 순정의 다른 계기판 호출(check)과 시계(EXPIRE_MS 뒤 한 번): 폰 안내가 끝났거나 방송이 끊겼으면 순정 막대로 되돌린다.
 * 다시 보내기는 한 스레드(Wireless.replayHandler)에서만, MIN_GAP_MS 간격 이상으로 한다(방송이 몰려도 계기판 프레임은
 * 많아야 초당 5번). 다시 보내는 동안 순정 호출이 끼어들었으면 최신 순정 값으로 한 번 더 보낸다(리뷰 1: 옛 단속 값이
 * 마지막으로 남지 않게). 순정 호출을 한 번도 못 봤으면 보내지 않는다. 지도에 없는 메서드라 이 프로세스에서만 이름으로
 * 건다(observeOnly와 같음). 없는 차에서는 한 줄 남기고 아무것도 하지 않는다. 로그에는 숫자와 예외 종류만 남긴다.
 */
final class TrapBar {
    static final long MIN_GAP_MS = 200;
    static final int LOGS_MAX = 30;

    private final ModContext mod;
    private final LoadedApp app;
    private final Handler handler;
    private final PhoneBar.Navi navi = new PhoneBar.Navi();
    /** 마지막 순정 호출 하나(대상, 메서드, 인자 사본, 순정 막대 그대로). 두 판을 따로 기억한다(2차 리뷰 4) */
    private static final class Slot {
        Object target;
        Method method;
        Object[] args;
    }
    private final Slot slotInt = new Slot(), slotNew = new Slot();
    private Field barField;
    /** 이 스레드의 순정 호출에 실제로 넣은 막대(after에서 지금 값과 비교, 2차 리뷰 1) */
    private final ThreadLocal<Integer> sentBar = new ThreadLocal<Integer>();
    /** 순정 호출마다 하나씩 는다(다시 보내는 사이 끼어든 순정 호출 알아보기) */
    private long seq;
    private long lastSendAt = -MIN_GAP_MS;
    private int pendingBar;
    private boolean barPosted, sendPosted;
    private int logs;
    /** 실패 종류별 처음 한 번만 남긴다(리뷰 8) */
    private boolean failStock, failSend;

    private final Runnable barRun = new Runnable() {
        @Override
        public void run() {
            int b;
            synchronized (TrapBar.this) {
                barPosted = false;
                b = pendingBar;
            }
            boolean phone = Car.phoneGuiding(app.context);
            Integer r;
            synchronized (TrapBar.this) {
                r = navi.onBar(b, phone, now());
            }
            if (r != null) send();
            // 폰 쪽이 사라져 방송이 끊기면 순정 호출이 없어도 EXPIRE_MS 뒤 한 번 되돌린다(리뷰 5)
            handler.removeCallbacks(expireRun);
            if (b != PhoneBar.NONE) handler.postDelayed(expireRun, PhoneBar.EXPIRE_MS + 500);
        }
    };

    private final Runnable expireRun = new Runnable() {
        @Override
        public void run() {
            check(Car.phoneGuiding(app.context));
        }
    };

    private final Runnable sendRun = new Runnable() {
        @Override
        public void run() {
            synchronized (TrapBar.this) {
                sendPosted = false;
            }
            send();
        }
    };

    private TrapBar(ModContext mod, LoadedApp app, Handler handler) {
        this.mod = mod;
        this.app = app;
        this.handler = handler;
    }

    static TrapBar install(final ModContext mod, final LoadedApp app) {
        Class<?> nc;
        try {
            nc = Points.cls(null, "framework.navigation_cluster");
        } catch (Throwable t) {
            mod.log("navigation: no NavigationCluster for the turn bar (" + t.getClass().getSimpleName() + ")");
            return null;
        }
        final TrapBar tb = new TrapBar(mod, app, Wireless.replayHandler());
        // 방송 받기를 먼저 건다: 못 걸면 훅도 걸지 않는다(리뷰 10)
        try {
            app.context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    tb.onBar(i.getIntExtra(PhoneBar.EXTRA_BAR, PhoneBar.NONE));
                }
            }, new IntentFilter(PhoneBar.ACTION));
        } catch (Throwable t) {
            mod.log("navigation: turn bar receiver not registered (" + t.getClass().getSimpleName() + ")");
            return null;
        }
        int hooked = 0;
        try {
            Object[] a = new Object[TrapArgs.INT_ARGS + 1];
            for (int i = 0; i < TrapArgs.INT_ARGS; i++) a[i] = int.class;
            a[TrapArgs.INT_ARGS] = tb.hook(false);
            Hooks.findAndHookMethod(nc, "updateTBTTrapInfo", a);
            hooked++;
        } catch (Throwable t) {
            mod.log("navigation: no updateTBTTrapInfo(17 ints) (" + t.getClass().getSimpleName() + ")");
        }
        try {
            Class<?> info = null;
            for (Method m : nc.getDeclaredMethods()) {
                if ("updateTBTTrapInfoNew".equals(m.getName()) && m.getParameterTypes().length == 1) info = m.getParameterTypes()[0];
            }
            if (info == null) throw new NoSuchMethodException("updateTBTTrapInfoNew");
            // 칸이 모두 public int일 때만(사본이 칸을 빠짐없이 옮길 때만) 건다(리뷰 3)
            Field f = TrapArgs.barField(info);
            if (f == null) throw new NoSuchFieldException("not all public int");
            info.getConstructor();
            tb.barField = f;
            Hooks.findAndHookMethod(nc, "updateTBTTrapInfoNew", info, tb.hook(true));
            hooked++;
        } catch (Throwable t) {
            mod.log("navigation: updateTBTTrapInfoNew not used for the turn bar (" + t.getClass().getSimpleName() + ")");
        }
        mod.log("navigation: phone turn bar goes into the stock trap frame (" + hooked + " hooks)");
        return hooked == 0 ? null : tb;
    }

    private MethodHook hook(final boolean asNew) {
        return new MethodHook() {
            @Override
            protected void beforeHookedMethod(Param p) {
                if (Wireless.REPLAYING.get() != null) return;   // 우리가 다시 보내는 값: 그대로 통과
                sentBar.remove();
                try {
                    stock(p, asNew);
                } catch (Throwable t) {
                    // 순정 호출은 그대로 나간다
                    if (!failStock) {
                        failStock = true;
                        mod.log("navigation: stock trap frame not handled (" + t.getClass().getSimpleName() + ")");
                    }
                }
            }

            @Override
            protected void afterHookedMethod(Param p) {
                // 이 순정 호출이 우리 재전송보다 늦게 닿았을 수 있다: 넣은 막대가 지금 값과 다르면 한 번 더 보낸다(2차 리뷰 1)
                Integer b = sentBar.get();
                if (b == null) return;
                sentBar.remove();
                boolean differ;
                synchronized (TrapBar.this) {
                    differ = b != navi.current();
                }
                if (differ) postSend(MIN_GAP_MS);
            }
        };
    }

    private static long now() {
        return android.os.SystemClock.elapsedRealtime();
    }

    /** 순정 단속 정보 호출: 사본을 기억하고, 폰 막대가 있으면 막대 칸만 바꾼다 */
    private void stock(MethodHook.Param p, boolean asNew) throws Exception {
        int stockBar;
        if (asNew) {
            if (p.args == null || p.args.length != 1 || p.args[0] == null || barField == null) return;
            stockBar = barField.getInt(p.args[0]);
        } else {
            stockBar = TrapArgs.intBar(p.args);
            if (stockBar < 0) return;   // 순서가 다른 펌웨어일 수 있다: 바꾸지 않는다(리뷰 3)
        }
        Member m = p.method;
        if (!(m instanceof Method)) return;
        Object[] copy = asNew ? new Object[] { TrapArgs.copy(p.args[0]) } : p.args.clone();
        Slot slot = asNew ? slotNew : slotInt;
        if (stockBar < 0 || stockBar > PhoneBar.MAX) {
            // 막대가 범위 밖인 객체 판: 최신 단속 값으로 기억만 하고 막대는 바꾸지 않는다(2차 리뷰 2)
            synchronized (this) {
                slot.target = p.thisObject;
                slot.method = (Method) m;
                slot.args = copy;
                seq++;
            }
            return;
        }
        // 폰 막대가 없으면 폰 안내 여부를 묻지 않는다(binder 줄이기, 리뷰 10)
        boolean need;
        synchronized (this) {
            need = navi.phoneBar != PhoneBar.NONE;
        }
        boolean phone = need && Car.phoneGuiding(app.context);
        int b;
        synchronized (this) {
            slot.target = p.thisObject;
            slot.method = (Method) m;
            slot.args = copy;
            seq++;
            b = navi.onStock(stockBar, phone, now());
        }
        sentBar.set(b);
        if (b == stockBar) return;
        Object[] w = TrapArgs.withBar(p.args, asNew, barField, b);
        p.args[0] = w[0];
        if (!asNew) p.args[TrapArgs.BAR_ARG] = w[TrapArgs.BAR_ARG];
        log("stock trap frame: bar " + stockBar + " -> " + b);
    }

    /** 폰 방송(메인 스레드): 값만 적고 다시 보내기 스레드로 넘긴다. 몰려 오면 마지막 값만 쓴다 */
    void onBar(int bar) {
        synchronized (this) {
            pendingBar = bar;
            if (barPosted) return;
            barPosted = true;
        }
        handler.post(barRun);
    }

    /** 순정의 다른 계기판 호출 때(Wireless.navigation 훅): 되돌릴 때가 되었으면 되돌린다 */
    void check(boolean phone) {
        Integer r;
        synchronized (this) {
            r = navi.check(phone, now());
        }
        if (r != null) postSend(0);
    }

    private void postSend(long delay) {
        synchronized (this) {
            if (sendPosted) return;
            sendPosted = true;
        }
        handler.postDelayed(sendRun, delay);
    }

    /**
     * 기억한 순정 호출에 지금 정한 막대(PhoneBar.Navi.current)를 넣어 다시 보낸다(다시 보내기 스레드에서만).
     * MIN_GAP_MS 안이면 그만큼 미룬다. 보내는 사이 순정 호출이 끼어들었으면 최신 값으로 한 번 더 보낸다.
     */
    private void send() {
        Object[] t = new Object[2];
        Method[] m = new Method[2];
        Object[][] a = new Object[2][];
        long s;
        int bar;
        synchronized (this) {
            if (slotInt.method == null && slotNew.method == null) return;
            long wait = lastSendAt + MIN_GAP_MS - now();
            if (wait > 0) {
                if (!sendPosted) {
                    sendPosted = true;
                    handler.postDelayed(sendRun, wait);
                }
                return;
            }
            s = seq;
            bar = navi.current();
            Slot[] slots = { slotInt, slotNew };
            for (int k = 0; k < 2; k++) {
                Slot sl = slots[k];
                if (sl.method == null || sl.args == null) continue;
                try {
                    a[k] = TrapArgs.withBar(sl.args, k == 1, barField, bar);
                    t[k] = sl.target;
                    m[k] = sl.method;
                } catch (Throwable e) {
                    fail(e);
                }
            }
            lastSendAt = now();
        }
        boolean outer = Wireless.REPLAYING.get() != null;
        Wireless.REPLAYING.set(Boolean.TRUE);
        try {
            for (int k = 0; k < 2; k++) {
                if (m[k] == null) continue;
                try {
                    m[k].invoke(t[k], a[k]);
                    log("trap frame resent with bar " + bar);
                } catch (Throwable e) {
                    fail(e);
                }
            }
        } finally {
            if (!outer) Wireless.REPLAYING.remove();
        }
        boolean again;
        synchronized (this) {
            again = seq != s;
        }
        if (again) postSend(MIN_GAP_MS);
    }

    private void fail(Throwable e) {
        if (failSend) return;
        failSend = true;
        mod.log("navigation: trap frame resend failed (" + e.getClass().getSimpleName() + ")");
    }

    private void log(String s) {
        if (logs >= LOGS_MAX) return;
        logs++;
        mod.log("navigation: " + s);
    }
}
