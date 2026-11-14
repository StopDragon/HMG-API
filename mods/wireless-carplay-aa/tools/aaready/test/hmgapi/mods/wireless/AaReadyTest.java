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
        final int[] ran = { 0 };
        Runnable job = new Runnable() {
            @Override
            public void run() {
                ran[0]++;
            }
        };
        final boolean[] sigv = { true };
        final boolean[] modOffv = { false };
        AaReady.Signal sig = new AaReady.Signal() {
            @Override
            public boolean on() {
                return sigv[0];
            }

            @Override
            public boolean modOff() {
                return modOffv[0];
            }
        };
        Sched sch = new Sched();
        boolean sigNow = AaReady.onRunningPP(sig, sch, job, LOG);
        check("review 5: with the signal: one call scheduled 500 ms later, not run at once",
                sigNow && sch.posted.equals(java.util.Arrays.asList(Long.valueOf(500L))) && ran[0] == 0);
        sch.runAll(null);
        check("review 5: the scheduled call runs the resend once (no repeat)", ran[0] == 1 && sch.q.isEmpty() && sch.posted.size() == 1);

        // B-1(1.7.4) 재현: AP 신호가 PP 시작보다 늦게 1이 되는 첫 부팅(#64). 2.4.1은 신호를 한 번만 읽어 다시 보지 않았다
        clear();
        ran[0] = 0;
        sigv[0] = false;
        sch = new Sched();
        boolean noSig = AaReady.onRunningPP(sig, sch, job, LOG);
        check("B-1 repro: signal 0 at runningPP (AP comes up in the same second): a recheck is scheduled 1 s later",
                !noSig && sch.posted.equals(java.util.Arrays.asList(Long.valueOf(1000L))) && ran[0] == 0);
        sch.runUntil(2999L);
        check("B-1: still 0 at 1 s and 2 s: nothing sent yet, nothing logged, one check pending", ran[0] == 0 && logs.isEmpty() && sch.q.size() == 1);
        sigv[0] = true;
        sch.runAll(null);
        check("B-1: signal 1 at 3 s: the resend runs once at 3 s, then nothing is left scheduled",
                ran[0] == 1 && sch.now == 3000L && sch.q.isEmpty());
        check("B-1: one line with the wait in seconds (numbers only)", logs.equals(java.util.Arrays.asList("AA 준비 다시 보냄 신호 기다림 3초")));

        // B-1: 60초 내내 0 → 60번 보고 끝, 로그 한 줄, 남는 타이머 없음
        clear();
        ran[0] = 0;
        sigv[0] = false;
        sch = new Sched();
        AaReady.onRunningPP(sig, sch, job, LOG);
        sch.runAll(null);
        check("B-1: signal 0 for 60 s: exactly 60 checks, the last at 60 s, then nothing scheduled (the loop ends)",
                sch.ran == 60 && sch.now == 60000L && sch.q.isEmpty() && ran[0] == 0 && AaReady.RECHECK_MAX * AaReady.RECHECK_MS == 60000L);
        check("B-1: giving up leaves one line, numbers only: '0대 (신호 없음 60초)'",
                logs.equals(java.util.Arrays.asList("AA 준비 다시 보냄 0대 (신호 없음 60초)")));
        sigv[0] = true;
        sch.runAll(null);
        check("B-1: a signal after giving up sends nothing (no check left)", ran[0] == 0 && sch.ran == 60);

        // B-1: 기다리는 중에 runningPP가 또 옴(PP 다시 시작·리스너 바뀜) → 옛 다시 보기는 멈추고 새 것 하나만 이어 간다
        clear();
        ran[0] = 0;
        sigv[0] = false;
        sch = new Sched();
        AaReady.onRunningPP(sig, sch, job, LOG);
        sch.runUntil(10000L);
        AaReady.onRunningPP(sig, sch, job, LOG);
        sch.runUntil(12500L);
        check("B-1: second runningPP at 10 s: the old chain stops, only one check pending", sch.q.size() == 1);
        sigv[0] = true;
        sch.runAll(null);
        check("B-1: signal 1 at 13 s: the resend runs once, not twice", ran[0] == 1 && sch.q.isEmpty()
                && logs.equals(java.util.Arrays.asList("AA 준비 다시 보냄 신호 기다림 3초")));
        clear();
        sigv[0] = false;
        sch = new Sched();
        AaReady.onRunningPP(sig, sch, job, LOG);
        sch.runUntil(30000L);
        AaReady.onRunningPP(sig, sch, job, LOG);
        sch.runAll(null);
        check("B-1: a new runningPP restarts the 60 s, the whole thing still ends (30 s + 60 s, one give-up line)",
                sch.now == 90000L && sch.q.isEmpty() && logs.equals(java.util.Arrays.asList("AA 준비 다시 보냄 0대 (신호 없음 60초)")));
        clear();
        ran[0] = 0;
        sch = new Sched();
        AaReady.onRunningPP(sig, sch, job, LOG);
        sch.runUntil(5000L);
        sigv[0] = true;
        AaReady.onRunningPP(sig, sch, job, LOG);
        sch.runAll(null);
        check("B-1: a new runningPP with the signal already 1 takes over: one resend (500 ms), the old chain sends nothing",
                ran[0] == 1 && sch.q.isEmpty() && logs.isEmpty());

        // B-1: 신호 읽기 예외는 0으로, job 예외는 한 줄 남기고 끝(GPM 메인 스레드로 예외가 나가지 않음)
        clear();
        ran[0] = 0;
        sch = new Sched();
        AaReady.onRunningPP(new AaReady.Signal() {
            @Override
            public boolean on() {
                throw new IllegalStateException("no SystemProperties");
            }

            @Override
            public boolean modOff() {
                throw new IllegalStateException("no ModContext");
            }
        }, sch, job, LOG);
        sch.runAll(null);
        check("B-1: the signal read throws: treated as 0, ends at 60 s with one line", ran[0] == 0 && sch.now == 60000L && sch.q.isEmpty() && logs.size() == 1);
        clear();
        sigv[0] = false;
        sch = new Sched();
        AaReady.onRunningPP(sig, sch, new Runnable() {
            @Override
            public void run() {
                throw new IllegalStateException("boom");
            }
        }, LOG);
        sch.runUntil(1500L);
        sigv[0] = true;
        String thrown = sch.runAll(null);
        check("B-1: the resend throws inside a check: caught, one line with the kind only, nothing left scheduled",
                thrown == null && sch.q.isEmpty() && logs.size() == 2 && "AA 준비 다시 보냄 못 함 (IllegalStateException)".equals(logs.get(1)));

        // 리뷰 낮음 4: 기다리는 중에 모드가 꺼짐 → 다음 볼 때 줄 없이 끝. 처음부터 꺼져 있으면 걸지 않음
        clear();
        ran[0] = 0;
        sigv[0] = false;
        sch = new Sched();
        AaReady.onRunningPP(sig, sch, job, LOG);
        sch.runUntil(5000L);
        modOffv[0] = true;
        sigv[0] = true;
        sch.runAll(null);
        check("review L4: the mod turned off while waiting: the next check ends it, no line, nothing sent",
                ran[0] == 0 && logs.isEmpty() && sch.q.isEmpty() && sch.now == 6000L);
        sch = new Sched();
        boolean offStart = AaReady.onRunningPP(sig, sch, job, LOG);
        check("review L4: the mod already off at runningPP: nothing scheduled", !offStart && sch.posted.isEmpty() && logs.isEmpty());
        modOffv[0] = false;
        // 리뷰 낮음 3: 로그가 실패해도 다시 보내기는 함
        clear();
        ran[0] = 0;
        sigv[0] = false;
        sch = new Sched();
        AaReady.onRunningPP(sig, sch, job, new AaReady.Log() {
            @Override
            public void line(String s) {
                throw new IllegalStateException("log full");
            }
        });
        sch.runUntil(1500L);
        sigv[0] = true;
        String th = sch.runAll(null);
        check("review L3: the log throws when the signal comes: the resend still runs once, nothing escapes", ran[0] == 1 && th == null && sch.q.isEmpty());

        // B-1: 늦게 온 신호로 실제 resend를 부름. PP가 뜬 뒤(since) 순정이 이미 준비(1)를 보낸 기기는 겹쳐 보내지 않음(readySince)
        clear();
        AaReady.clearReady();
        listener(null);
        sigv[0] = false;
        sch = new Sched();
        final Sched fs = sch;
        final ClassLoader fcl = cl;
        final Object fpp = pp;
        final long since = 1000L;
        AaReady.onRunningPP(sig, sch, new Runnable() {
            @Override
            public void run() {
                AaReady.resend(fcl, fpp, LOG, since, since + fs.now);
            }
        }, LOG);
        sch.runUntil(2500L);
        AaReady.noteReady(new WLDeviceInfo(A, "SER-A"), Integer.valueOf(1), since + 2500L);   // 기다리는 사이 순정이 A에 보냄
        sigv[0] = true;
        sch.runAll(null);
        check("B-1: late signal: stock already sent ready to A after PP started, so only E is sent ('1대 (이미 보냄 1)')",
                sent.equals(java.util.Arrays.asList(E + " SER-E 1"))
                        && logs.equals(java.util.Arrays.asList("AA 준비 다시 보냄 신호 기다림 3초", "AA 준비 다시 보냄 1대 (이미 보냄 1)")));
        clear();
        sigv[0] = true;
        sch = new Sched();
        final Sched fs2 = sch;
        AaReady.onRunningPP(sig, sch, new Runnable() {
            @Override
            public void run() {
                AaReady.resend(fcl, fpp, LOG, since, since + 3000L + fs2.now);
            }
        }, LOG);
        sch.runAll(null);
        check("B-1: runningPP again right after our late resend: nothing sent twice ('0대 (이미 보냄 1) (방금 보냄 1)')",
                sent.isEmpty() && logs.equals(java.util.Arrays.asList("AA 준비 다시 보냄 0대 (이미 보냄 1) (방금 보냄 1)")));
        sigv[0] = true;
        AaReady.clearReady();

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
    /** B-1 시험용 가짜 시계·대기열: postDelayed는 지금+ms에 걸고, runUntil은 그 시각까지 차례로 돌린다 */
    static final class Sched implements AaReady.Scheduler {
        long now;
        int ran;
        final List<Long> posted = new ArrayList<Long>();
        final List<Object[]> q = new ArrayList<Object[]>();

        @Override
        public void postDelayed(Runnable r, long ms) {
            posted.add(Long.valueOf(ms));
            q.add(new Object[] { Long.valueOf(now + ms), r });
        }

        void runUntil(long t) {
            while (true) {
                Object[] first = null;
                for (Object[] e : q) if (first == null || (Long) e[0] < (Long) first[0]) first = e;
                if (first == null || (Long) first[0] > t) break;
                q.remove(first);
                now = (Long) first[0];
                if (first[1] instanceof AaReady.Recheck) ran++;
                ((Runnable) first[1]).run();
            }
            if (t != Long.MAX_VALUE && now < t) now = t;
        }

        /** 남은 것을 모두 돌린다(무한 반복이면 10000번에서 멈추고 FAIL). 밖으로 나온 예외 이름, 없으면 null */
        String runAll(Object unused) {
            try {
                for (int n = 0; n < 10000 && !q.isEmpty(); n++) runUntil(earliest());
                return q.isEmpty() ? null : "never ends";
            } catch (Throwable t) {
                return t.getClass().getSimpleName();
            }
        }

        long earliest() {
            long m = Long.MAX_VALUE;
            for (Object[] e : q) m = Math.min(m, (Long) e[0]);
            return m;
        }
    }
}
