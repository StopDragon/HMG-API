package hmgapi.mods.wireless.dipo;
// Korea Projection 1.8.0 베타 2(2026-11-18, 모드 2.4.7): 무선 카플레이에서 iAP2가 블루투스 세션에서 카플레이 채널 세션으로
// 넘어갈 때 길안내 구독을 다시 요청한다. 업스트림 무선 2.2.0-rc.5의 RgWatch를 참고만 했고, 우리 Bridge 구조
// (채널 세션 식별 수락 = dipoDropNoti)에 맞춰 새로 썼다.

/**
 * 무선 카플레이 길안내 다시 요청(1.8.0, 모드 2.4.7).
 *
 * DiPO는 iAP2가 아직 블루투스 세션에 있을 때 길안내(StartRouteGuidanceUpdate)를 요청한다. 그 뒤 브리지가 iAP2를
 * 카플레이 채널 세션으로 넘기고 블루투스 세션을 끝내면 그 구독이 함께 사라질 수 있다. 그러면 폰이 길안내를 보내지 않아
 * 계기판에 회전이 나오지 않는다. 우리 쪽 기존 다시 요청은 길안내 앱이 바뀔 때만 있었다.
 *
 * 순서:
 *   1. 채널 세션의 식별이 수락되면(세션마다 한 번) FIRST_MS 뒤에 다시 요청한다(Stop, 200ms, Start).
 *   2. 요청한 뒤 길안내 알림(120·121)이 오지 않으면 AGAIN_MS 간격으로 다시 요청한다. 다시 요청은 최대 AGAIN_MS.length번이다.
 *      전체 요청은 많아야 1 + AGAIN_MS.length번이고 첫 요청 뒤 55초 안에 끝난다. 마지막 확인(멈춤 기록)은 그 30초 뒤다.
 *   3. 끝나는 조건: 길안내 알림이 옴, 한도를 다 씀, 채널 세션이 끝남(reset), DiPO가 스스로 길안내를 멈춤(reset).
 *      끝난 뒤에는 다음 채널 세션의 식별 수락 전까지 아무것도 예약하지 않는다.
 *
 * 폰은 Start를 받으면 경로가 없어도 현재 상태를 한 번 보낸다는 것을 전제로 한다. 그렇지 않은 폰이면 한도까지 요청하고 멈춘다.
 * 안드로이드 없이 PC에서 시험한다(tools/rgask). 모든 메서드는 synchronized다: 식별 수락은 iAP2 스레드,
 * 알림은 길안내 스레드, 예약 실행은 Host가 정한 스레드에서 온다.
 */
final class RgAsk {
    /** 식별 수락 뒤 첫 요청까지(ms): 채널 세션이 길안내 요청을 받을 수 있게 조금 기다린다 */
    static final long FIRST_MS = 1500L;
    /** 길안내가 오지 않을 때 다시 요청하는 간격(ms). 첫 요청 뒤 3·10·25·55초 */
    static final long[] AGAIN_MS = { 3000L, 7000L, 15000L, 30000L };

    /** 요청 스레드가 Stop과 Start 사이에 묻는다: 아직 이 요청이 유효한가(리뷰: DiPO가 그 사이에 멈추면 Start를 보내지 않음) */
    interface Still {
        boolean still();
    }

    interface Host {
        /** r을 ms 뒤에 실행한다 */
        void schedule(Runnable r, long ms);

        /** 길안내를 다시 요청한다(Stop, 잠깐, still이면 Start). 오래 걸려도 되며 이 객체의 잠금 밖에서 부른다 */
        void ask(Still still);

        void log(String m);
    }

    final Host host;
    /** 세대: reset·새 세션마다 올린다. 예약된 실행의 세대가 다르면 아무것도 하지 않는다 */
    int gen;
    /** 이번 채널 세션에서 시작했는지(세션마다 한 번) */
    boolean started;
    /** 요청한 뒤 길안내를 기다리는 중인지 */
    boolean waiting;
    /** 이번 세션에서 요청한 횟수 */
    int asks;

    RgAsk(Host host) {
        this.host = host;
    }

    /** 카플레이 채널 세션의 식별이 수락됨. 세션마다 처음 한 번만 시작한다 */
    synchronized void ready() {
        if (started) return;
        started = true;
        waiting = true;
        asks = 0;
        final int g = ++gen;
        host.schedule(tick(g), FIRST_MS);
    }

    /** 길안내 알림(120·121)이 옴: 기다리는 중이면 멈춘다 */
    synchronized void heard() {
        if (!waiting || asks == 0) return;
        waiting = false;
        gen++;
        host.log("CarPlay: route guidance came back after the iAP2 handover (" + asks + " ask" + (asks == 1 ? "" : "s") + ")");
    }

    /** 채널 세션이 끝났거나 DiPO가 스스로 길안내를 멈춤: 예약을 모두 무효로 하고 다음 세션을 기다린다 */
    synchronized void reset() {
        // 리뷰: 다시 요청 중에 끊겼으면 왜 멈췄는지 차 로그에서 보이게 한 줄(숫자만)
        if (waiting && asks > 0) host.log("CarPlay: route guidance asks ended by a session end or DiPO's stop (" + asks + " asked)");
        gen++;
        started = false;
        waiting = false;
        asks = 0;
    }

    Runnable tick(final int g) {
        return new Runnable() {
            @Override
            public void run() {
                fire(g);
            }
        };
    }

    /** 2.4.7: 이 세션의 다시 요청이 아직 진행 중인가(식별 수락 뒤 길안내를 기다리는 중). 그동안 다른 다시 요청은 양보한다 */
    synchronized boolean busy() {
        return waiting;
    }

    /** 세대 g의 요청이 아직 유효한가 */
    synchronized boolean live(int g) {
        return g == gen && waiting;
    }

    void fire(final int g) {
        int n;
        synchronized (this) {
            if (g != gen || !waiting) return;
            if (asks > AGAIN_MS.length) {
                waiting = false;
                gen++;
                host.log("CarPlay: no route guidance after " + asks + " asks since the iAP2 handover, stopped asking");
                return;
            }
            n = ++asks;
            // 다음 확인을 먼저 예약한다(요청이 실패해도 한도 안에서 다시 한다). 마지막 요청 뒤에도 한 번 더 확인해 멈춤을 남긴다
            host.schedule(tick(g), AGAIN_MS[Math.min(n - 1, AGAIN_MS.length - 1)]);
        }
        host.log("CarPlay: route guidance asked again after the iAP2 handover (" + n + "/" + (AGAIN_MS.length + 1) + ")");
        try {
            host.ask(new Still() {
                @Override
                public boolean still() {
                    return live(g);
                }
            });
        } catch (Throwable t) {
            // 세션이 사라지는 중: 다음 확인에서 한도 안에서 다시 한다
        }
    }
}
