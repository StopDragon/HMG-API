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
 *
 * 2.4.10(1.8.1 I-3, #620·#518, 설계 drafts/1.8.1-work/design-i1-i3.md 2장): AA가 우리 AP보다 먼저 물은 부팅.
 *  - 순정 준비(1)가 신호 0(우리 AP 꺼짐)일 때 PP에 가면 PP는 "AP 모드 모름"으로 버린다. 그것을 "이미 보냄"으로 세어 0대가 됐다.
 *    그래서 신호 0일 때 온 준비(1)는 READY_AT이 아니라 READY_OFF_AT에 적는다(noteReady의 apOn). readySince 조건(리뷰 2)은 그대로다.
 *  - 그런 기기에는 준비(1)만이 아니라 **준비(0) → 준비(1)**를 잇달아 보낸다. 순정 PP(PhoneProjection a.smali
 *    requestWLDeviceReadyStatusUpdate, 2026-11-25 읽음)는 "BT 자동 연결 대상(lD)이 있고, 받은 값이 1이고, 이 기기에 저장된 준비가
 *    이미 1"이면 mReadyTrueUpdate를 거짓으로 두어 CONNECTIVITY 갈래(우리 AP가 답하는 갈래)에서 연결하지 않는다. 버린 준비(1)도
 *    저장값을 1로 남기므로 준비(1)만 다시 보내면 막힐 수 있다. 준비(0)는 저장값을 0으로 바꾸고 lD가 이 기기면 비운다. 이 순서는 순정이
 *    SPP를 다시 걸 때 보내는 것과 같다(#613 15:04:33, #620 06:49:48에서 그 뒤 연결됨).
 *  - 신호 1에서 온 준비가 있는 기기, AP 꺼짐 준비가 없는 기기(16:20 꼴: PP가 그 준비를 못 받음)는 지금처럼 준비(1)만 보낸다.
 *  - 한도: runningPP마다 resend 한 번, 같은 기기 RESEND_GAP_MS, B-1 60초 한도는 그대로. 맵은 KEEP_MS로 지운다.
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

    /** 기기 주소 → 순정이 마지막으로 준비(1)를 보낸 시각(ms, 신호 1일 때). 메모리에만 있고 로그·파일에 쓰지 않는다 */
    private static final Map<String, Long> READY_AT = new java.util.HashMap<String, Long>();
    /** 2.4.10(I-3): 기기 주소 → 신호 0(우리 AP 꺼짐)일 때 순정이 준비(1)를 보낸 마지막 시각(ms). PP는 그 준비를 AP 모름으로 버렸다 */
    private static final Map<String, Long> READY_OFF_AT = new java.util.HashMap<String, Long>();

    /** 2.4.10 전과 같은 꼴(신호를 모름 = 켜짐으로 봄). 시험과 옛 부르기용 */
    static void noteReady(Object info, Object ready, long now) {
        noteReady(info, ready, now, true);
    }

    /**
     * GalReceiverService.requestWLDeviceReadyStatusUpdate(info, ready) 앞에서 부른다. 준비(1)만 적는다.
     * apOn: 그때의 패키지 신호(AaWifi.on). 거짓이면 READY_OFF_AT에 적는다(I-3: PP가 AP 모름으로 버린 준비)
     */
    static void noteReady(Object info, Object ready, long now, boolean apOn) {
        if (info == null || !Integer.valueOf(1).equals(ready)) return;
        try {
            Object a = info.getClass().getMethod("getWLDevBtAddr").invoke(info);
            if (a == null) return;
            Map<String, Long> m = apOn ? READY_AT : READY_OFF_AT;
            synchronized (m) {
                prune(m, now);
                m.put(a.toString(), Long.valueOf(now));
            }
        } catch (Throwable t) {
            // 적지 못하면 전처럼(그 기기도 다시 보냄)
        }
    }

    /** 리뷰 1 M6: 0 → 1을 보낸 기기의 AP 꺼짐 표시를 지운다(같은 방아쇠로 두 번 보내지 않게) */
    static void clearReadyOff(Object addr) {
        synchronized (READY_OFF_AT) {
            READY_OFF_AT.remove(String.valueOf(addr));
        }
    }

    /** 2.4.10(I-3): since(ms) 뒤에 순정이 신호 0일 때 이 주소로 준비(1)를 보냈다(PP가 버림) */
    static boolean readyOffSince(Object addr, long since) {
        synchronized (READY_OFF_AT) {
            Long t = READY_OFF_AT.get(String.valueOf(addr));
            return t != null && t.longValue() >= since;
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
        synchronized (READY_OFF_AT) {
            READY_OFF_AT.clear();
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

    /** 시험용: 세 맵의 크기 */
    static int mapSize() {
        synchronized (READY_AT) {
            synchronized (READY_OFF_AT) {
                synchronized (RESENT_AT) {
                    return READY_AT.size() + READY_OFF_AT.size() + RESENT_AT.size();
                }
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
        int sent = 0, failed = 0, already = 0, recent = 0, apOff = 0;
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
                // 2.4.10(I-3): PP가 신호 0에서 받은 준비(1)를 버렸으면 순정 SPP 다시 걸기와 같은 순서로 0 → 1(클래스 설명).
                // 같은 스레드에서 잇달아 부른다. 0 뒤 1이 실패하면 실패로 센다(PP는 순정 SPP 끊김 뒤와 같은 상태로 남음)
                boolean off = since >= 0 && readyOffSince(addr, since);
                if (off) ready.invoke(listener, info, Integer.valueOf(0));
                ready.invoke(listener, info, Integer.valueOf(1));
                sent++;
                if (off) {
                    apOff++;
                    // 리뷰 1 M6: 이 방아쇠(AP 꺼짐 때 받은 준비)에는 0 → 1을 한 번만. 우리 1이 준비 시각 훅(noteReady)을 지나 READY_AT에
                    // 적힌다는 데 기대지 않고 여기서 표시를 지운다. 그러지 않으면 다음 runningPP마다(RESEND_GAP_MS 뒤) 0을 또 보내
                    // PP의 BT 자동 연결 대상(lD)을 되풀이해 지울 수 있다. 다음 runningPP는 2.4.9처럼 1만(또는 "이미 보냄") 다룬다
                    clearReadyOff(addr);
                }
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
                + (apOff > 0 ? " (AP 꺼짐 때 받음 " + apOff + ")" : "") + (failed > 0 ? " (실패 " + failed + ")" : ""));
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
