package hmgapi.mods.wireless;

import android.phoneprojection.WLDeviceInfo;

import com.google.android.projection.protocol.WirelessExplorer;
import com.google.android.projection.service.GalReceiverService;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.7.3(A-1) AaReady PC 시험: 순정 GPM 이름의 가짜 클래스로 "언제 보내고 언제 안 보내는지", 반사 실패에 안전한지,
 * 로그에 주소가 없는지 본다. 기기 주소는 가짜 값이다.
 */
public final class AaReadyTest {
    static int fail;
    static final String A = "00:11:22:33:44:01", B = "00:11:22:33:44:02", C = "00:11:22:33:44:03", D = "00:11:22:33:44:04",
            E = "00:11:22:33:44:05";
    static final List<String> logs = new ArrayList<String>();
    static final List<String> sent = new ArrayList<String>();
    /** 시험 내내 남은 모든 줄(마지막에 주소가 섞였는지 본다) */
    static final List<String> allLogs = new ArrayList<String>();
    /** PP로 보내는 호출 중 WirelessExplorer 잠금을 잡은 채 부른 횟수(0이어야 함, 리뷰 3) */
    static int lockedCalls;
    static final AaReady.Log LOG = new AaReady.Log() {
        @Override
        public void line(String s) {
            logs.add(s);
            allLogs.add(s);
        }
    };

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what + (ok ? "" : "\n  logs " + logs + "\n  sent " + sent));
        if (!ok) fail = 1;
    }

    /** 받은 준비 값을 "주소 시리얼 값"으로 적는 가짜 PP 리스너. 주소 throwOn이면 예외 */
    static void listener(final String throwOn) {
        WirelessExplorer.setWirelessListener(new WirelessExplorer.WirelessListener() {
            @Override
            public void requestDeviceReadyStatusUpdate(WLDeviceInfo info, int ready) {
                if (WirelessExplorer.getWirelessExplorer() != null && Thread.holdsLock(WirelessExplorer.getWirelessExplorer())) lockedCalls++;
                if (info.addr.equals(throwOn)) throw new IllegalStateException("dead PP");
                sent.add(info.addr + " " + info.serial + " " + ready);
            }

            @Override
            public void requestDeviceConnectStatusUpdate(WLDeviceInfo info, int status) {
                sent.add("connect " + status);
            }
        });
    }

    static void clear() {
        logs.clear();
        sent.clear();
    }

    public static void main(String[] a) {
        ClassLoader cl = AaReadyTest.class.getClassLoader();
        GalReceiverService svc = new GalReceiverService();
        Object pp = svc.listener();

        // 실패 부팅 꼴: 폰 A가 버전 응답까지 마침(준비 1은 PP가 없어 버려짐), 그 뒤 PP가 뜸
        WirelessExplorer we = WirelessExplorer.reset();
        listener(null);
        we.rfcomm(A);
        we.version(A, "SER-A", true);
        we.rfcomm(B);                      // B: RFCOMM만, 버전 응답 전
        we.rfcomm(C);
        we.version(C, "SER-C", false);     // C: 무선 미지원
        we.rfcomm(D);
        we.version(D, "SER-D", true);
        we.dropWl(D);                      // D: 협상 기록(mWLDeviceMap)에 없음
        we.rfcomm(E);
        we.version(E, "SER-E", true);
        clear();
        int n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("A-1: not connecting, two phones past the version answer (A, E): ready 1 sent to each, with their serial",
                n == 2 && sent.size() == 2 && sent.contains(A + " SER-A 1") && sent.contains(E + " SER-E 1"));
        check("A-1: phones before the version answer (B), without wireless (C) or not in the negotiation map (D): not sent",
                !sent.toString().contains(B) && !sent.toString().contains(C) && !sent.toString().contains(D));
        check("A-1: only ready (no connect status or other calls)", !sent.toString().contains("connect"));
        check("A-1: one log line, the count only", logs.size() == 1 && "AA 준비 다시 보냄 2대".equals(logs.get(0)));
        check("review 3: the calls to PP are made outside the WirelessExplorer lock", lockedCalls == 0);

        // 리뷰 2: PP가 뜬 뒤(since 1000) 순정이 A에 이미 준비(1)를 보냈으면 A는 건너뛴다. 그 전(900)에 보낸 E는 다시 보낸다
        clear();
        AaReady.clearReady();
        AaReady.noteReady(new WLDeviceInfo(A, "SER-A"), Integer.valueOf(1), 1200L);
        AaReady.noteReady(new WLDeviceInfo(E, "SER-E"), Integer.valueOf(1), 900L);
        AaReady.noteReady(new WLDeviceInfo(E, "SER-E"), Integer.valueOf(0), 1300L);   // 준비(0)는 적지 않음
        AaReady.noteReady(null, Integer.valueOf(1), 1300L);
        n = AaReady.resend(cl, pp, LOG, 1000L, -1L);
        check("review 2: stock already sent ready 1 to A after PP started: A skipped, E sent, '1대 (이미 보냄 1)'",
                n == 1 && sent.equals(java.util.Arrays.asList(E + " SER-E 1")) && "AA 준비 다시 보냄 1대 (이미 보냄 1)".equals(logs.get(0)));
        clear();
        n = AaReady.resend(cl, pp, LOG, 1300L, -1L);
        check("review 2: nothing noted after PP started (since 1300): both sent as before", n == 2);
        clear();
        AaReady.clearReady();
        n = AaReady.resend(cl, pp, LOG, 1000L, -1L);
        check("review 2: no times noted (the ready hook missing): both sent as before", n == 2);

        // 리뷰 5: 설정 끄면 훅을 걸지 않음, 신호가 없으면 부르지 않음, 있으면 0.5초 뒤 한 번
        final int[] installed = { 0 };
        AaReady.Installer inst = new AaReady.Installer() {
            @Override
            public void install() {
                installed[0]++;
            }
        };
        boolean off = true, on = false;
        try {
            off = AaReady.install(false, inst);
            on = AaReady.install(true, inst);
        } catch (Throwable t) {
            off = true;
        }
        check("review 5: setting aa_ready_resend off: no hook; on: hooked once", !off && on && installed[0] == 1);
        final List<Long> posted = new ArrayList<Long>();
        final List<Runnable> jobs = new ArrayList<Runnable>();
        AaReady.Scheduler sch = new AaReady.Scheduler() {
            @Override
            public void postDelayed(Runnable r, long ms) {
                posted.add(Long.valueOf(ms));
                jobs.add(r);
            }
        };
        final int[] ran = { 0 };
        Runnable job = new Runnable() {
            @Override
            public void run() {
                ran[0]++;
            }
        };
        boolean noSig = AaReady.onRunningPP(false, sch, job);
        check("review 5: no package signal (sys.kp.aa_ap not 1): nothing scheduled", !noSig && posted.isEmpty());
        boolean sig = AaReady.onRunningPP(true, sch, job);
        check("review 5: with the signal: one call scheduled 500 ms later, not run at once",
                sig && posted.equals(java.util.Arrays.asList(Long.valueOf(500L))) && ran[0] == 0);
        jobs.get(0).run();
        check("review 5: the scheduled call runs the resend once (no repeat)", ran[0] == 1 && jobs.size() == 1);

        // 6단계 L5: runningPP가 짧게 두 번 옴 → 두 번째(2초 뒤)는 같은 기기에 다시 보내지 않음, 5초 뒤면 다시 보냄
        clear();
        AaReady.clearReady();
        listener(null);
        n = AaReady.resend(cl, pp, LOG, -1L, 10000L);
        int first = sent.size();
        clear();
        n = AaReady.resend(cl, pp, LOG, -1L, 12000L);
        check("stage 6 L5: runningPP twice within 2 s: the second round sends nothing again, '0대 (방금 보냄 2)'",
                first == 2 && n == 0 && sent.isEmpty() && "AA 준비 다시 보냄 0대 (방금 보냄 2)".equals(logs.get(0)));
        clear();
        n = AaReady.resend(cl, pp, LOG, -1L, 15000L);
        check("stage 6 L5: 5 s after our last resend: sent again (a real new PP start)", n == 2 && AaReady.RESEND_GAP_MS == 5000L);
        clear();
        n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("stage 6 L5: no time given (-1): not checked, sent as before", n == 2);

        // 6단계 뒤 리뷰 L5: 시각 맵이 커지지 않음(10분 넘은 항목은 다음에 적을 때 지움)
        AaReady.clearReady();
        for (int k = 0; k < 50; k++) AaReady.noteReady(new WLDeviceInfo("00:11:22:33:55:" + (10 + k), "S"), Integer.valueOf(1), 1000L + k);
        check("L5 map: 50 phones noted", AaReady.mapSize() == 50);
        AaReady.noteReady(new WLDeviceInfo(A, "SER-A"), Integer.valueOf(1), 1000L + AaReady.KEEP_MS + 100L);
        check("L5 map: an entry 10 min later drops the old ones (only the new one left)", AaReady.mapSize() == 1);
        clear();
        n = AaReady.resend(cl, pp, LOG, -1L, 5000000L);
        n = AaReady.resend(cl, pp, LOG, -1L, 5000000L + AaReady.KEEP_MS + 1L);
        check("L5 map: our own resend times are pruned the same way (2 phones, not 4)", AaReady.mapSize() <= 3 && n == 2);

        // GPM이 연결 중(무선·유선): 보내지 않음
        clear();
        svc.connectMode = 1;
        n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("A-1: GPM connecting (mode 1): nothing sent, '0대 (연결 중)'", n == 0 && sent.isEmpty() && "AA 준비 다시 보냄 0대 (연결 중)".equals(logs.get(0)));
        svc.connectMode = 2;
        clear();
        n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("A-1: GPM connecting (mode 2): nothing sent", n == 0 && sent.isEmpty());
        svc.connectMode = 0;

        // 이미 연결된 기기가 있음: 보내지 않음
        clear();
        we.setConnected(A);
        n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("A-1: a phone already connected: nothing sent, '0대 (이미 연결됨)'", n == 0 && sent.isEmpty() && "AA 준비 다시 보냄 0대 (이미 연결됨)".equals(logs.get(0)));
        we.setConnected("");
        clear();
        we.setConnected(null);
        n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("A-1: connected device null (stock after onClose is \"\", null too): treated as none, sent", n == 2);
        we.setConnected("");

        // 한 기기에서 PP 쪽 예외: 다른 기기는 보냄, 실패 수만
        clear();
        listener(A);
        n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("A-1: PP throws for one phone: the other still sent, '1대 (실패 1)'", n == 1 && sent.equals(java.util.Arrays.asList(E + " SER-E 1"))
                && "AA 준비 다시 보냄 1대 (실패 1)".equals(logs.get(0)));
        listener(null);

        // 보낼 기기 0대: 로그만
        clear();
        WirelessExplorer.reset();
        listener(null);
        n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("A-1: no phone negotiated (normal boot: PP came first): nothing sent, '0대'", n == 0 && sent.isEmpty() && "AA 준비 다시 보냄 0대".equals(logs.get(0)));

        // GPM이 아직 준비 전(탐색기·리스너 없음)
        clear();
        WirelessExplorer.clear();
        n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("A-1: no WirelessExplorer yet: nothing sent, one line", n == 0 && sent.isEmpty() && logs.size() == 1);
        we = WirelessExplorer.reset();
        we.rfcomm(A);
        we.version(A, "SER-A", true);
        clear();
        n = AaReady.resend(cl, pp, LOG, -1L, -1L);
        check("A-1: no PP listener in GPM yet: nothing sent", n == 0 && sent.isEmpty() && "AA 준비 다시 보냄 0대 (GPM 준비 전)".equals(logs.get(0)));

        // 다른 차의 GPM(클래스 없음): 아무것도 안 하고 한 줄, 예외 없음
        clear();
        ClassLoader none = new java.net.URLClassLoader(new java.net.URL[0], null);
        listener(null);
        n = AaReady.resend(none, pp, LOG, -1L, -1L);
        check("A-1: GPM without WirelessExplorer (another car's build): -1, one line, nothing sent",
                n == -1 && sent.isEmpty() && logs.size() == 1 && logs.get(0).startsWith("AA 준비 다시 보냄 못 함: GPM 구조가 다름"));
        clear();
        n = AaReady.resend(cl, new Object(), LOG, -1L, -1L);
        check("A-1: a listener object without this$0 (another structure): -1, one line", n == -1 && sent.isEmpty() && logs.size() == 1);

        // 로그에 주소·시리얼이 없음
        boolean clean = true;
        for (String s : allLogs) if (s.contains("00:11") || s.contains("SER-")) clean = false;
        check("A-1: no address or serial in any log line (" + allLogs.size() + " lines)", clean && allLogs.size() >= 10);
        System.exit(fail);
    }
}
