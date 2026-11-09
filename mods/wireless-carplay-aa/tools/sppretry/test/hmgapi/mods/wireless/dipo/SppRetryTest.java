package hmgapi.mods.wireless.dipo;

/**
 * SppRetry 시험. Bridge.sppTry가 실행마다 부르는 SppRetry.run과 Step.line을 그대로 부른다(가짜 BTCM).
 * Bridge 쪽에 남은 것(busy 계산, postDelayed, onSppUp 호출, 예외 잡기)은 안드로이드에 묶여 있어 시험하지 않는다.
 */
public final class SppRetryTest {
    static int fail;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fail = 1;
    }

    /** 가짜 BTCM과 차 상태 */
    static final class Fake implements SppRetry.Btcm {
        boolean[] results;   // IbtcmSppConnect가 차례로 돌려줄 값
        int calls;
        boolean busy, bt = true;
        int upAfterCall = -1;   // 이 번째 호출 뒤로는 SPP가 열려 있음
        Runnable duringConnect;   // 연결 중에 끼어드는 일(새 연결 이벤트 등)
        final StringBuilder log = new StringBuilder();
        final java.util.List<Long> delays = new java.util.ArrayList<Long>();
        SppRetry.Step lastStep;

        @Override
        public boolean sppUp() {
            return upAfterCall >= 0 && calls >= upAfterCall;
        }

        @Override
        public boolean btConnected() {
            return bt;
        }

        @Override
        public boolean connect() {
            boolean r = results[Math.min(calls, results.length - 1)];
            calls++;
            if (duringConnect != null) duringConnect.run();
            return r;
        }
    }

    /** 실행 한 번(Bridge.sppTry와 같은 호출). 다음 대기 시간 또는 -1 */
    static long run(SppRetry r, int gen, Fake f) {
        SppRetry.Step st = r.run(gen, f.busy, f);
        f.lastStep = st;
        String line = st.line();
        if (line != null) f.log.append(line).append('\n');
        if (st.next > 0) f.delays.add(st.next);
        return st.next;
    }

    /** 끝날 때까지 돌린다(안전하게 20번에서 자름). 실행 수 */
    static int drive(SppRetry r, int gen, Fake f) {
        int runs = 0;
        while (runs < 20) {
            runs++;
            if (run(r, gen, f) < 0) break;
        }
        return runs;
    }

    public static void main(String[] a) {
        check("M-9: up to 3 calls, checks 5 s after false and 10 s after true",
                SppRetry.MAX_TRIES == 3 && SppRetry.GAP_MS == 5000L && SppRetry.CHECK_MS == 10000L);

        SppRetry r = new SppRetry();
        Fake f = new Fake();
        f.results = new boolean[] { false };
        int runs = drive(r, r.start(), f);
        check("M-9: false every time: 3 calls 5 s apart, then it ends (gave up), 4 runs in all",
                f.calls == 3 && runs == 4 && f.delays.toString().equals("[5000, 5000, 5000]") && f.lastStep.decision == SppRetry.GAVE_UP);
        check("M-9: log lines (what Bridge logs): numbered tries, then the stop reason",
                f.log.toString().equals("IbtcmSppConnect try 1 of 3 = false, check again in 5 s\n"
                        + "IbtcmSppConnect try 2 of 3 = false, check again in 5 s\n"
                        + "IbtcmSppConnect try 3 of 3 = false, check again in 5 s\n"
                        + "SPP connect retry stopped after 3 tries: SPP not up after 3 tries\n"));

        r = new SppRetry();
        f = new Fake();
        f.results = new boolean[] { true };
        runs = drive(r, r.start(), f);
        check("M-9: true but SPP never opens: 3 calls 10 s apart, then it ends",
                f.calls == 3 && runs == 4 && f.delays.toString().equals("[10000, 10000, 10000]"));

        r = new SppRetry();
        f = new Fake();
        f.results = new boolean[] { false, true };
        f.upAfterCall = 2;
        drive(r, r.start(), f);
        check("M-9: false, then true and SPP opens: stops (SPP up) after 2 calls; Bridge then starts the session (tries > 0)",
                f.calls == 2 && f.lastStep.decision == SppRetry.UP && f.lastStep.tries == 2
                        && f.log.toString().endsWith("SPP connect retry stopped after 2 tries: SPP up\n"));

        r = new SppRetry();
        f = new Fake();
        f.results = new boolean[] { true };
        f.upAfterCall = 0;
        runs = drive(r, r.start(), f);
        check("M-9: SPP already up at the first run: no call, \"SPP already up\" (as before, no session start from here)",
                f.calls == 0 && runs == 1 && r.tries() == 0 && f.lastStep.tries == 0 && f.log.toString().equals("SPP already up\n"));

        r = new SppRetry();
        f = new Fake();
        f.results = new boolean[] { false };
        int g = r.start();
        run(r, g, f);
        f.bt = false;
        run(r, g, f);
        check("M-9: the phone's Bluetooth dropped: no more calls", f.calls == 1 && f.lastStep.decision == SppRetry.BT_GONE && f.lastStep.next < 0);

        r = new SppRetry();
        f = new Fake();
        f.results = new boolean[] { false };
        f.bt = false;
        g = r.start();
        run(r, g, f);
        check("M-9: the first call doesn't look at the Bluetooth check (unchanged first try)", f.calls == 1);

        r = new SppRetry();
        f = new Fake();
        f.results = new boolean[] { false };
        g = r.start();
        run(r, g, f);
        f.busy = true;
        run(r, g, f);
        check("M-9: a session, USB or wireless CarPlay came up: stops quietly", f.calls == 1 && f.lastStep.decision == SppRetry.BUSY
                && f.lastStep.line() == null);

        r = new SppRetry();
        f = new Fake();
        f.results = new boolean[] { false };
        g = r.start();
        run(r, g, f);
        int g2 = r.start();
        run(r, g, f);
        check("M-9: a newer connection event: the old chain stops quietly (stale)", f.lastStep.decision == SppRetry.STALE && f.lastStep.line() == null);
        check("M-9: ... and the new one counts from 0", r.tries() == 0 && r.decide(g2, false, false, true) == SppRetry.TRY);

        // 리뷰 8: 판단(TRY)과 결과 사이에 새 연결 이벤트가 끼면 옛 체인은 다시 걸지 않고, 새 체인 횟수를 늘리지 않는다
        final SppRetry rr = new SppRetry();
        f = new Fake();
        f.results = new boolean[] { false };
        g = rr.start();
        final int[] newer = new int[1];
        f.duringConnect = new Runnable() {
            @Override
            public void run() {
                newer[0] = rr.start();
            }
        };
        long next = run(rr, g, f);
        check("review 8: a newer connection during the call: the old chain ends (no reschedule), the new one is untouched",
                next < 0 && rr.tries() == 0 && rr.decide(newer[0], false, false, true) == SppRetry.TRY
                        && f.log.toString().equals("IbtcmSppConnect = false (a newer connection took over)\n"));

        r = new SppRetry();
        g = r.start();
        r.tried(g, false);
        r.cancel();
        check("M-9: SPP up (onSppUp) cancels what's left", r.decide(g, false, false, true) == SppRetry.STALE);

        boolean clean = true;
        for (int d = 0; d <= SppRetry.GAVE_UP; d++) {
            String w = SppRetry.why(d);
            if (w.contains(":") || w.matches(".*[0-9a-fA-F]{2}[:-][0-9a-fA-F]{2}.*")) clean = false;
        }
        check("M-9: stop reasons carry no address or name (numbers and words only)", clean);
        check("2nd review 6: a MAC is masked completely, other text only as a length, null as null",
                "xx:xx:xx:xx:xx:xx".equals(Mac.mask("A1:B2:C3:D4:E5:F6")) && !Mac.mask("A1:B2:C3:D4:E5:F6").contains("F6")
                        && "(6 chars)".equals(Mac.mask("iPhone")) && "null".equals(Mac.mask(null)));
        System.exit(fail);
    }
}
