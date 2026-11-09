package hmgapi.mods.wireless.dipo;
// Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-08, 모드 2.3.1 판 그대로(계획 M-9).

/**
 * 브리지의 SPP 연결 재시도 판단(Bridge.scheduleSppConnect가 쓴다). 안드로이드 없이 PC에서 시험하려고 따로 뺐다.
 *
 * 왜: 브리지는 아이폰 블루투스가 붙은 3초 뒤 IbtcmSppConnect를 한 번만 불렀다. 결과가 false이거나(니로 11-03 03:52·
 * 04:01, 11-06 04:07), true인데 SPP가 열리지 않으면(11-06 05:00) 다음 블루투스 이벤트까지 무선 카플레이가
 * 시작되지 않았다(drafts/stock-re/connection-timeline-carplay.md 7번, 장면 F9).
 *
 * 어떻게: 한 번 부른 뒤 false이면 GAP_MS, true이면 CHECK_MS 뒤에 다시 본다. 그때 SPP가 열렸거나(세션 시작),
 * USB 카플레이·무선 카플레이 중이거나, 블루투스가 끊겼거나, 더 새 연결 이벤트가 왔으면 멈춘다. 아니면 다시
 * 부른다. 모두 MAX_TRIES번까지 부르고, 마지막 시도 뒤 한 번 더 확인하고 끝난다. 그래서 한 연결 이벤트에
 * 실행은 많아야 MAX_TRIES + 1번이고 끝나지 않는 반복은 없다.
 *
 * 효과는 차에서 확인해야 한다(2차 리뷰 5 반영으로 근거를 고침):
 *  - 순정 BTCM btcmSppConnect는 먼저 af()(preConditionCheckForConnectSpp)를 부른다. af는 그 폰이 HFP로 붙어 있으면
 *    false를 돌려준다. 하지만 애플(vendor 0x4c)은 af 결과와 상관없이 au() 경로로 간다(FINDINGS "PC에서 확인: AA 자동
 *    연결이 실패하는 이유"). 그래서 니로 AA 실패(02:07, 안드로이드 폰, 3번 모두 false)처럼 "재시도가 늘 실패한다"고
 *    단정할 수 없다. au() 경로의 세부는 아직 보지 않았다.
 *  - 부작용: af는 애플에도 먼저 불려, 아이폰 HFP가 아직 안 붙었고 다른 폰이 HFP로 붙어 있으면 그 폰의 HFP를 끊는다.
 *    재시도는 아이폰이 HFP 연결 기기일 때만 하므로(btConnected) 이 갈래는 첫 시도(원래 동작)에만 있다. af의 "연결
 *    기기가 2대면 하나를 끊음" 단계는 차에서 두 폰으로 확인한다.
 */
final class SppRetry {
    /** IbtcmSppConnect를 부르는 최대 횟수(처음 1번 + 재시도 2번) */
    static final int MAX_TRIES = 3;
    /** true를 받은 뒤 SPP가 열리기를 기다리는 시간 */
    static final long CHECK_MS = 10000L;
    /** false를 받은 뒤 다시 부르기까지의 간격 */
    static final long GAP_MS = 5000L;

    /** decide()의 답: 지금 IbtcmSppConnect를 부른다 / 멈춘다(이유별) */
    static final int TRY = 0, STALE = 1, BUSY = 2, UP = 3, BT_GONE = 4, GAVE_UP = 5;

    private int gen, tries;

    /** 새 연결 이벤트: 이전 재시도를 버리고 새로 센다. 이 번호를 실행할 때 넘긴다 */
    synchronized int start() {
        gen++;
        tries = 0;
        return gen;
    }

    /** 지금 하던 재시도를 버린다(SPP가 열렸을 때 등) */
    synchronized void cancel() {
        gen++;
    }

    synchronized int tries() {
        return tries;
    }

    /**
     * 실행할 때마다 한 번 부른다. busy: 세션·USB·무선 카플레이 중이거나 꺼짐, sppUp: 그 기기의 SPP가 이미 열림,
     * btConnected: 그 기기의 블루투스가 아직 붙어 있음(처음 시도는 이것을 보지 않는다, 원래 동작 그대로).
     */
    synchronized int decide(int g, boolean busy, boolean sppUp, boolean btConnected) {
        if (g != gen) return STALE;
        if (busy) return BUSY;
        if (sppUp) return UP;
        if (tries > 0 && !btConnected) return BT_GONE;
        if (tries >= MAX_TRIES) return GAVE_UP;
        return TRY;
    }

    /**
     * IbtcmSppConnect를 부른 뒤: 센 횟수를 늘리고 다음 확인까지 기다릴 시간을 돌려준다. 그 사이 새 연결 이벤트가
     * 와서 세대가 바뀌었으면 -1(이 체인은 여기서 끝, 리뷰 8).
     */
    synchronized long tried(int g, boolean result) {
        if (g != gen) return -1L;
        tries++;
        return result ? CHECK_MS : GAP_MS;
    }

    /** BTCM에 묻는 것(Bridge가 HfDeviceBTCMManager로 채운다, 시험은 가짜로) */
    interface Btcm {
        /** 그 기기의 SPP가 열려 있는지(IbtcmIsSppConnectedDevice) */
        boolean sppUp();

        /** 그 기기의 블루투스가 아직 붙어 있는지 */
        boolean btConnected();

        /** IbtcmSppConnect */
        boolean connect();
    }

    /** 실행 한 번의 결과 */
    static final class Step {
        /** decide()의 답 */
        int decision;
        /** 이 체인에서 지금까지 부른 횟수(이번 호출 포함) */
        int tries;
        /** 이번 IbtcmSppConnect 결과(TRY일 때) */
        boolean result;
        /** 다음 실행까지 기다릴 시간, -1이면 끝 */
        long next = -1L;

        /** 로그 줄(숫자·영어 낱말만, 기기 주소·이름 없음), 남길 것이 없으면 null */
        String line() {
            if (decision == TRY) {
                return next < 0 ? "IbtcmSppConnect = " + result + " (a newer connection took over)"
                        : "IbtcmSppConnect try " + tries + " of " + MAX_TRIES + " = " + result + ", check again in " + next / 1000L + " s";
            }
            if (decision == UP && tries == 0) return "SPP already up";
            if (decision == STALE || decision == BUSY) return null;
            return "SPP connect retry stopped after " + tries + " tries: " + why(decision);
        }
    }

    /**
     * Bridge.sppTry의 실행 한 번(판단 → 필요하면 연결 → 다음 대기). Bridge와 시험이 같은 코드를 쓴다.
     * busy: 세션·USB·무선 카플레이 중이거나 꺼짐(호출한 쪽이 계산).
     */
    Step run(int g, boolean busy, Btcm b) {
        Step st = new Step();
        int t0 = tries();
        boolean up = !busy && b.sppUp();
        boolean bt = !busy && !up && t0 > 0 && b.btConnected();
        st.decision = decide(g, busy, up, bt);
        st.tries = t0;
        if (st.decision != TRY) return st;
        st.result = b.connect();
        st.next = tried(g, st.result);
        st.tries = t0 + 1;
        return st;
    }

    /** 로그용 이유(숫자·영어 낱말만, 기기 주소·이름 없음) */
    static String why(int d) {
        switch (d) {
            case BUSY: return "session or USB up";
            case UP: return "SPP up";
            case BT_GONE: return "Bluetooth no longer connected";
            case GAVE_UP: return "SPP not up after " + MAX_TRIES + " tries";
            default: return "newer connection";
        }
    }
}
