package hmgapi.mods.wireless.dipo;

import java.util.ArrayList;

/** RgAsk PC 시험: 가짜 시계로 예약을 돌린다 */
public final class RgAskTest {
    static int fail;

    static void check(String what, Object want, Object got) {
        if (want == null ? got == null : want.equals(got)) {
            System.out.println("ok    " + what);
        } else {
            System.out.println("FAIL  " + what + ": wanted [" + want + "], got [" + got + "]");
            fail = 1;
        }
    }

    /** 가짜 Host: 예약을 시각순으로 모으고, 요청 시각과 로그를 남긴다 */
    static final class Fake implements RgAsk.Host {
        long now;
        final ArrayList<long[]> at = new ArrayList<long[]>();
        final ArrayList<Runnable> runs = new ArrayList<Runnable>();
        final ArrayList<Long> asks = new ArrayList<Long>();
        final ArrayList<String> logs = new ArrayList<String>();
        boolean throwOnAsk;
        /** 마지막 요청이 받은 still(Stop 뒤 Start 전에 묻는 것) */
        RgAsk.Still lastStill;

        @Override
        public void schedule(Runnable r, long ms) {
            at.add(new long[] { now + ms });
            runs.add(r);
        }

        @Override
        public void ask(RgAsk.Still still) {
            asks.add(now);
            lastStill = still;
            if (throwOnAsk) throw new IllegalStateException("binder died");
        }

        @Override
        public void log(String m) {
            logs.add(m);
        }

        /** until까지 예약을 시각순으로 실행한다 */
        void runUntil(long until) {
            while (true) {
                int best = -1;
                for (int i = 0; i < at.size(); i++) if (at.get(i)[0] <= until && (best < 0 || at.get(i)[0] < at.get(best)[0])) best = i;
                if (best < 0) break;
                now = at.get(best)[0];
                Runnable r = runs.get(best);
                at.remove(best);
                runs.remove(best);
                r.run();
            }
            now = until;
        }

        String last() {
            return logs.isEmpty() ? null : logs.get(logs.size() - 1);
        }
    }

    public static void main(String[] args) {
        // 1. 길안내가 끝내 오지 않음: 1 + 4번 요청(1.5·4.5·11.5·26.5·56.5초), 그 뒤 멈춤, 더 예약 없음
        Fake f = new Fake();
        RgAsk a = new RgAsk(f);
        a.ready();
        f.runUntil(600000L);
        check("no guidance: 5 asks in all", 5, f.asks.size());
        check("no guidance: ask times", "[1500, 4500, 11500, 26500, 56500]", f.asks.toString());
        check("no guidance: stopped with a log", true, f.last() != null && f.last().contains("stopped asking"));
        check("no guidance: nothing left scheduled", 0, f.at.size());
        check("2.4.7 busy: not busy once it stopped asking", false, a.busy());

        // 2. 같은 세션에서 식별 수락이 또 와도 다시 시작하지 않음
        a.ready();
        f.runUntil(1200000L);
        check("same session: no new asks", 5, f.asks.size());

        // 3. 첫 요청 뒤 길안내가 옴: 거기서 멈춤
        f = new Fake();
        a = new RgAsk(f);
        a.ready();
        f.runUntil(2000L);
        check("first ask made", 1, f.asks.size());
        a.heard();
        f.runUntil(600000L);
        check("guidance came: no more asks", 1, f.asks.size());
        check("guidance came: logged once", true, f.last() != null && f.last().contains("came back") && f.last().contains("(1 ask)"));
        check("2.4.7 busy: not busy once guidance came back", false, a.busy());
        // 2.4.7: 식별 수락 뒤 길안내를 기다리는 동안은 busy(모르는 회전 번호의 다시 요청이 양보함), 세션 끝이면 아님
        f = new Fake();
        a = new RgAsk(f);
        check("2.4.7 busy: not busy before the CarPlay channel is ready", false, a.busy());
        a.ready();
        check("2.4.7 busy: busy from ready until guidance comes", true, a.busy());
        a.reset();
        check("2.4.7 busy: not busy after a session end", false, a.busy());

        // 4. 첫 요청 전에 온 길안내(아직 블루투스 세션의 것일 수 있음)는 멈추지 않는다
        f = new Fake();
        a = new RgAsk(f);
        a.ready();
        f.runUntil(500L);
        a.heard();
        f.runUntil(2000L);
        check("guidance before the first ask: the ask still goes", 1, f.asks.size());
        a.heard();
        f.runUntil(600000L);
        check("then guidance: stops", 1, f.asks.size());

        // 5. 세션이 끝남(reset): 남은 예약은 아무것도 안 하고, 다음 세션은 새로 시작
        f = new Fake();
        a = new RgAsk(f);
        a.ready();
        f.runUntil(5000L);
        check("before reset: 2 asks", 2, f.asks.size());
        a.reset();
        f.runUntil(100000L);
        check("after reset: stale timers do nothing", 2, f.asks.size());
        a.ready();
        f.runUntil(100000L + 1500L);
        check("next session: asks again", 3, f.asks.size());
        a.heard();
        f.runUntil(700000L);
        check("next session: stops on guidance", 3, f.asks.size());

        // 6. 요청이 실패(예외)해도 한도 안에서 이어 가고 한도에서 멈춤
        f = new Fake();
        f.throwOnAsk = true;
        a = new RgAsk(f);
        a.ready();
        f.runUntil(600000L);
        check("ask throws: still 5 asks at most", 5, f.asks.size());
        check("ask throws: nothing left scheduled", 0, f.at.size());

        // 6b. 요청 중에 DiPO가 멈추면(reset) 그 요청의 Start는 보내지 않는다(still false), 멈춘 이유가 로그에 남음
        f = new Fake();
        a = new RgAsk(f);
        a.ready();
        f.runUntil(2000L);
        check("in-flight ask: still true before reset", true, f.lastStill.still());
        a.reset();
        check("in-flight ask: still false after reset", false, f.lastStill.still());
        check("reset while asking: logged", true, f.last() != null && f.last().contains("ended by"));
        RgAsk.Still old = f.lastStill;
        a.ready();
        f.runUntil(5000L);
        check("next session asks again", 2, f.asks.size());
        check("old ask stays false in the next session", false, old.still());
        check("new ask is live", true, f.lastStill.still());
        a.heard();
        check("in-flight ask: still false after guidance came", false, f.lastStill.still());

        // 7. 시작 전 heard·reset은 아무것도 예약하지 않음
        f = new Fake();
        a = new RgAsk(f);
        a.heard();
        a.reset();
        f.runUntil(600000L);
        check("never ready: no asks", 0, f.asks.size());
        check("never ready: no logs", 0, f.logs.size());

        // 8. 로그에 숫자 말고 다른 값이 없음(폰 이름·주소 없음)
        boolean plain = true;
        Fake g = new Fake();
        new RgAsk(g).ready();
        g.runUntil(600000L);
        for (String m : g.logs) {
            if (!m.matches("[A-Za-z0-9 :()/.,-]+")) plain = false;
        }
        check("logs: plain words and numbers only", true, plain);

        System.exit(fail);
    }
}
