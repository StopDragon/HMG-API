package hmgapi.mods.wireless;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

/**
 * 한국 차 빌드(korea-build) 1.7.3(모드 2.4.1, 계획 A-1): 시동 뒤 무선 안드로이드 오토가 저절로 붙지 않는 문제의 고침(1안).
 *
 * 원인(순정 코드 확인, drafts/1.7.3-work/a1-analysis.md): GPM(HKMC_GPM)은 폰과 버전 협상을 마치면
 * WirelessExplorer.handleVersionResponse에서 준비(1)를 PP(폰 프로젝션 앱)에 한 번 보낸다. 시스템 서비스 PPService는
 * 그 순간 등록된 PP 리스너에게만 넘기고 쌓아 두지 않는다. 시동 직후 PP가 GPM보다 늦게 뜨면 준비(1)는 버려지고,
 * PP는 뜰 때 다시 묻지 않으며 GPM도 다시 보내지 않는다(1.7.2 리포트 두 차 실패 부팅 4번 모두 이 꼴).
 *
 * 고침: PP가 뜨면 PPService가 GPM의 GalReceiverService$PPActionListener.runningPP()를 부른다. 그 뒤에
 * GPM이 연결 중이 아니고(getGpmConnectMode() == 0, getConnectedDevice() 비어 있음) 버전 응답까지 끝난 무선
 * 기기(mALConnectedThread의 DeviceInfo에 mDevice_Registry가 있고 무선 지원, mWLDeviceMap에도 있음)마다 순정과 같은
 * 호출 getWirelessListener().requestDeviceReadyStatusUpdate(getWLDeviceInfo(기기), 1)을 한 번 더 부른다.
 * PP는 이미 연결 중이면 스스로 무시하고, 처음 보는 기기면 상태를 새로 만든다(PP 코드 확인).
 *
 * 이 클래스는 반사만 쓴다(안드로이드·HMG-API 클래스를 부르지 않음): 훅은 Wireless가 걸고, PC 시험은
 * tools/aaready가 같은 이름의 가짜 GPM 클래스로 한다. 니로(DEPEHEV 260902) GPM 이름 기준이다. 다른 차에서 클래스·필드가
 * 없으면 한 줄 남기고 아무것도 하지 않는다(카플레이 쪽은 건드리지 않음). 로그에는 몇 대인지만 쓴다(주소·이름 없음).
 *
 * 리뷰 반영(2026-11-10):
 *  - PP가 뜬 뒤 0.5초 안에 정상 협상이 끝나 순정이 준비(1)를 이미 보낸 기기는 건너뛴다. GPM의
 *    GalReceiverService.requestWLDeviceReadyStatusUpdate 앞 훅(noteReady)이 기기별 마지막 준비(1) 시각을 메모리에만 적는다.
 *    그 훅을 못 걸면 시각이 없으므로 전처럼 모두 보낸다.
 *  - 맵 복사는 순정 handleVersionResponse와 같은 잠금(WirelessExplorer 객체) 안에서만, PP로 보내는 호출은 잠금 밖에서 한다.
 *  - 훅을 걸지(설정), 언제 부를지(신호, 0.5초 뒤 한 번)를 install·onRunningPP로 떼어 PC에서 시험한다.
 *  - 6단계 리뷰 L5: runningPP가 짧게 두 번 오면 준비(1)도 두 번 나갔다. 우리가 다시 보낸 시각을 기기별로 적어
 *    RESEND_GAP_MS 안에는 같은 기기에 다시 보내지 않는다(로그 "(방금 보냄 k)").
 */
final class AaReady {
    private AaReady() {}

    static final String EXPLORER = "com.google.android.projection.protocol.WirelessExplorer";
    static final String PP_LISTENER = "com.google.android.projection.service.GalReceiverService$PPActionListener";
    /** 로그 줄 머리(패키지 aa_events가 이 글자로 찾는다): "AA 준비 다시 보냄 N대" */
    static final String LINE = "AA 준비 다시 보냄 ";
    static final String GAL_SERVICE = "com.google.android.projection.service.GalReceiverService";
    /** runningPP 뒤 다시 보내기까지(ms) */
    static final long DELAY_MS = 500L;
    /** 같은 기기에 우리가 다시 보낸 뒤 이 시간 안에는 또 보내지 않는다(ms, L5) */
    static final long RESEND_GAP_MS = 5000L;
    /** 기기 주소 → 우리가 마지막으로 다시 보낸 시각(ms). 메모리에만 */
    private static final Map<String, Long> RESENT_AT = new java.util.HashMap<String, Long>();

    interface Log {
        void line(String s);
    }

    /** 훅을 거는 쪽(Wireless: Hooks.findAndHookMethod, 시험: 가짜) */
    interface Installer {
        void install() throws Throwable;
    }

    /** 늦게 한 번 부르는 쪽(Wireless: GPM 메인 Handler, 시험: 가짜) */
    interface Scheduler {
        void postDelayed(Runnable r, long ms);
    }

    /** 설정이 꺼져 있으면 훅을 걸지 않는다(2.4.0과 같음). 걸었으면 true */
    static boolean install(boolean setting, Installer i) throws Throwable {
        if (!setting) return false;
        i.install();
        return true;
    }

    /** 패키지 신호(sys.kp.aa_ap=1)를 읽는 쪽(Wireless: AaWifi.on, 시험: 가짜). 부를 때마다 새로 읽는다 */
    interface Signal {
        boolean on();

        /** 리뷰 낮음 4: 모드가 꺼졌다(Wireless: !mod.enabled()). 참이면 다시 보기를 줄 없이 바로 끝낸다 */
        boolean modOff();
    }

    /** B-1(1.7.4): 신호가 0이면 이 간격(ms)으로 다시 본다 */
    static final long RECHECK_MS = 1000L;
    /** B-1(1.7.4): 다시 보는 최대 횟수. RECHECK_MS × RECHECK_MAX = 60초에서 반드시 끝난다 */
    static final int RECHECK_MAX = 60;
    /** runningPP마다 하나씩 는다. 다시 보기는 자기 번호가 최신일 때만 이어 간다(새 PP 시작이 이어받음) */
    private static int gen;

    private static synchronized int nextGen() {
        return ++gen;
    }

    private static synchronized boolean current(int g) {
        return g == gen;
    }

    /**
     * runningPP 뒤. 신호가 1이면 DELAY_MS 뒤 한 번 job을 부르게 하고 true(2.4.1과 같음).
     * B-1(1.7.4, #64): AP 신호와 PP 시작이 같은 초면 신호가 아직 0이다. 그때는 RECHECK_MS마다 최대 RECHECK_MAX번 다시 보고,
     * 1이 되면 job을 한 번만 부른 뒤 끝낸다. 60초 내내 0이면 로그 한 줄(숫자만)을 남기고 끝낸다. 반복 타이머는 남지 않는다:
     * 한 번 볼 때마다 다음 한 번만 걸고, 그 사이 새 runningPP가 오면 옛 다시 보기는 조용히 멈춘다(새 것이 이어받음).
     * 같은 기기에 겹쳐 보내는 것은 job(resend)의 readySince·resentRecently가 막는다. 신호를 기다리기 시작했으면 false.
     */
    static boolean onRunningPP(Signal sig, Scheduler s, Runnable job, Log log) {
        int g = nextGen();
        if (off(sig)) return false;   // 모드가 꺼짐: 걸지 않음(옛 다시 보기도 세대가 바뀌어 멈춤)
        if (read(sig)) {
            s.postDelayed(job, DELAY_MS);
            return true;
        }
        s.postDelayed(new Recheck(g, 1, sig, s, job, log), RECHECK_MS);
        return false;
    }

    /** 모드가 꺼졌는지. 읽다가 예외가 나면 꺼지지 않은 것으로 본다(60초 한도가 끝을 지킴) */
    static boolean off(Signal sig) {
        try {
            return sig.modOff();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 신호 읽기에서 예외가 나면 0으로 본다 */
    static boolean read(Signal sig) {
        try {
            return sig.on();
        } catch (Throwable t) {
            return false;
        }
    }

    /** B-1 다시 보기 한 번(k번째). GPM 메인 Handler에서 돈다. 예외를 밖으로 내지 않는다 */
    static final class Recheck implements Runnable {
        final int g, k;
        final Signal sig;
        final Scheduler s;
        final Runnable job;
        final Log log;

        Recheck(int g, int k, Signal sig, Scheduler s, Runnable job, Log log) {
            this.g = g;
            this.k = k;
            this.sig = sig;
            this.s = s;
            this.job = job;
            this.log = log;
        }

        @Override
        public void run() {
            try {
                if (!current(g)) return;   // 새 runningPP가 이어받음
                if (off(sig)) return;   // 모드가 꺼짐: 줄 없이 끝(리뷰 낮음 4)
                if (read(sig)) {
                    try {
                        log.line(LINE + "신호 기다림 " + k + "초");
                    } catch (Throwable ignored) {
                        // 리뷰 낮음 3: 로그가 실패해도 다시 보내기는 한다
                    }
                    job.run();
                    return;
                }
                if (k >= RECHECK_MAX) {
                    log.line(LINE + "0대 (신호 없음 " + (RECHECK_MAX * RECHECK_MS / 1000L) + "초)");
                    return;
                }
                s.postDelayed(new Recheck(g, k + 1, sig, s, job, log), RECHECK_MS);
            } catch (Throwable t) {
                try {
                    log.line(LINE + "못 함 (" + t.getClass().getSimpleName() + ")");
                } catch (Throwable ignored) {
                    // 로그도 못 쓰면 그냥 끝낸다
                }
            }
        }
    }

    /** 기기 주소 → 순정이 마지막으로 준비(1)를 보낸 시각(ms). 메모리에만 있고 로그·파일에 쓰지 않는다 */
    private static final Map<String, Long> READY_AT = new java.util.HashMap<String, Long>();

    /** GalReceiverService.requestWLDeviceReadyStatusUpdate(info, ready) 앞에서 부른다. 준비(1)만 적는다 */
    static void noteReady(Object info, Object ready, long now) {
        if (info == null || !Integer.valueOf(1).equals(ready)) return;
        try {
            Object a = info.getClass().getMethod("getWLDevBtAddr").invoke(info);
            if (a == null) return;
            synchronized (READY_AT) {
                prune(READY_AT, now);
                READY_AT.put(a.toString(), Long.valueOf(now));
            }
        } catch (Throwable t) {
            // 적지 못하면 전처럼(그 기기도 다시 보냄)
        }
    }

    /** since(ms) 뒤에 순정이 이 주소로 준비(1)를 보냈다 */
    static boolean readySince(Object addr, long since) {
        synchronized (READY_AT) {
            Long t = READY_AT.get(String.valueOf(addr));
            return t != null && t.longValue() >= since;
        }
    }

    /** 시험용: 적어 둔 시각을 비운다 */
    static void clearReady() {
        synchronized (READY_AT) {
            READY_AT.clear();
        }
        synchronized (RESENT_AT) {
            RESENT_AT.clear();
        }
    }

    /** 시각 맵에서 오래된 항목(KEEP_MS보다 오래됨)을 지운다(6단계 뒤 리뷰 L5: 맵이 커지지 않게). 부르는 쪽이 맵을 잠금 */
    static final long KEEP_MS = 600000L;

    static void prune(Map<String, Long> m, long now) {
        for (java.util.Iterator<Map.Entry<String, Long>> it = m.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Long> e = it.next();
            if (now - e.getValue().longValue() > KEEP_MS) it.remove();
        }
    }

    /** 시험용: 두 맵의 크기 */
    static int mapSize() {
        synchronized (READY_AT) {
            synchronized (RESENT_AT) {
                return READY_AT.size() + RESENT_AT.size();
            }
        }
    }

    /** now(ms) 기준 RESEND_GAP_MS 안에 우리가 이 주소로 다시 보냈다 */
    static boolean resentRecently(Object addr, long now) {
        synchronized (RESENT_AT) {
            Long t = RESENT_AT.get(String.valueOf(addr));
            return t != null && now - t.longValue() < RESEND_GAP_MS;
        }
    }

    /**
     * runningPP() 뒤에 한 번 부른다. ppListener는 훅이 받은 PPActionListener 객체(this$0이 GalReceiverService).
     * since: runningPP 시각(ms). 그 뒤에 순정이 이미 준비(1)를 보낸 기기는 건너뛴다(-1이면 보지 않음).
     * now: 지금 시각(ms). 우리가 RESEND_GAP_MS 안에 다시 보낸 기기는 건너뛴다(-1이면 보지 않음, L5).
     * 보낸 기기 수를 돌려준다. GPM 구조가 달라 아무것도 못 했으면 -1.
     */
    static int resend(ClassLoader cl, Object ppListener, Log log, long since, long now) {
        Object svc, we, listener;
        Map<?, ?> al, wl;
        Method getInfo, ready;
        try {
            Field outer = ppListener.getClass().getDeclaredField("this$0");
            outer.setAccessible(true);
            svc = outer.get(ppListener);
            int mode = ((Integer) svc.getClass().getMethod("getGpmConnectMode").invoke(svc)).intValue();
            if (mode != 0) {
                log.line(LINE + "0대 (연결 중)");
                return 0;
            }
            Class<?> wec = Class.forName(EXPLORER, false, cl);
            we = wec.getMethod("getWirelessExplorer").invoke(null);
            listener = wec.getMethod("getWirelessListener").invoke(null);
            if (we == null || listener == null) {
                log.line(LINE + "0대 (GPM 준비 전)");
                return 0;
            }
            Object connected = wec.getMethod("getConnectedDevice").invoke(we);
            if (connected != null && connected.toString().length() > 0) {
                log.line(LINE + "0대 (이미 연결됨)");
                return 0;
            }
            al = (Map<?, ?>) field(wec, "mALConnectedThread").get(we);
            wl = (Map<?, ?>) field(wec, "mWLDeviceMap").get(we);
            getInfo = method(wec, "getWLDeviceInfo", 1);
            ready = method(Class.forName(EXPLORER + "$WirelessListener", false, cl), "requestDeviceReadyStatusUpdate", 2);
        } catch (Throwable t) {
            log.line(LINE + "못 함: GPM 구조가 다름 (" + t.getClass().getSimpleName() + ")");
            return -1;
        }
        if (al == null || wl == null) {
            log.line(LINE + "0대");
            return 0;
        }
        // 순정 handleVersionResponse(synchronized)와 같은 잠금 안에서 복사만 하고, PP로 보내는 호출은 잠금 밖에서 한다
        List<Object[]> devices;
        synchronized (we) {
            devices = snapshot(al);
            for (Object[] e : devices) e[2] = Boolean.valueOf(e[0] != null && wl.containsKey(e[0]));
        }
        int sent = 0, failed = 0, already = 0, recent = 0;
        for (Object[] e : devices) {
            try {
                Object addr = e[0], di = e[1];
                if (addr == null || di == null || !Boolean.TRUE.equals(e[2])) continue;   // 협상(RFCOMM) 기록이 없는 기기
                Object reg = field(di.getClass(), "mDevice_Registry").get(di);
                if (reg == null) continue;   // 버전 응답 전
                if (!Boolean.TRUE.equals(reg.getClass().getMethod("getWirelessSupport").invoke(reg))) continue;   // 무선 미지원
                Object dev = field(di.getClass(), "mDevice").get(di);
                if (dev == null) continue;
                if (since >= 0 && readySince(addr, since)) {   // PP가 뜬 뒤 순정이 이미 보냄(리뷰 2)
                    already++;
                    continue;
                }
                if (now >= 0 && resentRecently(addr, now)) {   // runningPP가 짧게 두 번 옴(L5)
                    recent++;
                    continue;
                }
                Object info = getInfo.invoke(we, dev);
                if (info == null) continue;
                ready.invoke(listener, info, Integer.valueOf(1));
                sent++;
                if (now >= 0) {
                    synchronized (RESENT_AT) {
                        prune(RESENT_AT, now);
                        RESENT_AT.put(String.valueOf(addr), Long.valueOf(now));
                    }
                }
            } catch (Throwable t) {
                failed++;
            }
        }
        log.line(LINE + sent + "대" + (already > 0 ? " (이미 보냄 " + already + ")" : "") + (recent > 0 ? " (방금 보냄 " + recent + ")" : "")
                + (failed > 0 ? " (실패 " + failed + ")" : ""));
        return sent;
    }

    /** 복사(부르는 쪽이 WirelessExplorer 잠금을 잡음). 순정이 잠금 밖에서 바꾸는 경우를 위해 몇 번 다시 시도한다 */
    static List<Object[]> snapshot(Map<?, ?> m) {
        for (int k = 0; k < 3; k++) {
            try {
                List<Object[]> r = new ArrayList<Object[]>();
                for (Map.Entry<?, ?> e : m.entrySet()) r.add(new Object[] { e.getKey(), e.getValue(), null });
                return r;
            } catch (ConcurrentModificationException e) {
                // 다시
            }
        }
        return new ArrayList<Object[]>();
    }

    static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** 이름과 인자 수로 찾는다(인자 클래스 WLDeviceInfo·BluetoothDevice를 이 쪽에서 몰라도 되게) */
    static Method method(Class<?> c, String name, int args) throws NoSuchMethodException {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == args) return m;
        }
        throw new NoSuchMethodException(name);
    }
}
