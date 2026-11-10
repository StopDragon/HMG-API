package hmgapi.mods.wireless;
// Modified by Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-08 (mod 2.3.0,
// "match the stock Korean navigation", 1.7.1 map 2-B, 2-E, 2-H):
//   - ramps as the Korean stock GPM (GpmNaviTBTManager): on-ramp right/left/unknown 161/162/160,
//     off-ramp 164/165/163 (the US build had 173/174/172 and 81/82/86); forks unchanged
//   - the crossroad name only when it changes or every 10 s (as CpTurns)
//   - a log line per turn ("AA: turn ev N side N angle N -> icon N"), numbers only, on change,
//     up to 300 lines
//   - HUD distance (2C): metres under 1 km; 65535 (invalid) for the points it has no distance for
//     and when guidance ends, as the stock navigation
// Korea Projection 1.7.2 수정(2026-11-08, 모드 2.3.1 판 그대로, 계획 M-3·M-14):
//   - 회전은 순정 "멀리 있음" 값으로 보낸다: 막대 0·barErr -1(막대 32 고정 대신), 옆길 0(65535 대신), 방향 0
//   - 포커스가 순정 내비로 넘어가면(stop) 안내 중이었을 때 계기판을 지운다(release, 카플레이 M-2와 같은 값)
//   - 지우기 값: 표시 8(0 대신), 거리 무효 -3, 단위 15, 막대 무효 -1, 2C (단위 15, 소수 15, 정수 65535) x 3
// Korea Projection 1.7.2 수정(2026-11-08, 계획 M-15):
//   - HUD 목록 이름을 UTF-16LE 78바이트 안에서 글자 경계로 자른다(이모지 같은 서로게이트 쌍을 반으로 자르지 않음)
//   - 도로 이름 끝 글자 깨짐("우회전" → "우회쀀")의 원인은 AA 라이브러리 레시피(aa-tbt-*)의 UTF-8 자르기였다(레시피에서 고침)
// Korea Projection 1.7.2 수정(2026-11-09, 계획 M-2 HUD·M-12·M-11, 소유자 결정 2026-11-09 "순정대로"):
//   - 곧 회전 화면(M-12, 카플레이 M-4와 같은 규칙): AA가 차로(이벤트 34)를 준 회전만, 남은 거리가 D(일반 350m,
//     출구 1050m, 처음 거리가 더 짧으면 그 거리) 안이면 순정 근거리 값(13·31·17/18·25, 회전교차로는 출구 아이콘)과
//     방향·나가는 길 옆길 비트·막대를 보낸다. AA는 교차로의 다른 길 각도를 주지 않아 옆길은 나가는 길 비트뿐이다
//   - 도로 이름은 곧 회전일 때만 보내고 멀리 있을 때는 빈 이름(순정)
//   - 재탐색(상태 3)은 순정 표시 10(방향·옆길·거리 무효, 2C·HUD 목록 지우기). 상태 1로 돌아오면 보통 회전 표시로.
//     AA에는 "경로 계산 중" 상태가 없어 표시 9는 보내지 않는다
//   - HUD 목록(M-2): 빈 목록 {F2,00}, 아이콘 3칸(셋째 칸은 그다음 회전). updateRemainDis는 보내지 않는다(M-11)
// Korea Projection 1.7.2 수정(2026-11-09, 소유자 결정 "D 안의 모든 회전"): 곧 회전 화면에서 차로(이벤트 34) 조건을 뺐다.
//   남은 거리(m)가 D 안이고 큰 그림이 있는 회전이면 곧 회전이다(카플레이 CpTurns와 같은 규칙). 위 M-12의 "차로를 준 회전만"은 바뀌었다
// Korea Projection 1.7.2 시험판 3(2026-11-09, 모드 2.3.3, 순정 내비 운행 로그 기준):
//   - 막대를 순정 단속 정보 프레임의 막대 칸에도 넣는다(PhoneBar 방송 → 순정 내비 프로세스). 순정은 두 곳에 같은 값을 보낸다
//   - D는 고정(350m·1050m, 처음 거리가 짧아도 줄이지 않음), 막대 최대 90(순정 값). 위의 "처음 거리가 더 짧으면 그 거리"는 바뀌었다
// Korea Projection 1.7.3(2026-11-10, 모드 2.4.1, 계획 A-2·N-3):
//   - 이름 바뀜 붙잡기(A-2): 폰이 같은 지점에 대해 갈림길(9)과 이름 바뀜(2)을 2~4초마다 번갈아 보내 계기판 아이콘이
//     173↔65로 깜빡였다(#65). 여러 번 조각으로 고치다 반대쪽 꼴이 돌아와 "A-2 규칙"(필드 nameHold 위 설명) 하나로 정리했다:
//     애매한 이벤트는 다음 거리 표본 하나로 판단(늘었으면 지남, 같거나 줄면 같은 지점), 거리가 안 오면 2초 뒤 안전한 쪽,
//     붙잡기는 8초 한도. 원칙은 "지난 회전 그림을 보이는 것이 깜빡임보다 나쁘다". 설정 aa_name_hold로 끌 수 있다
//   - 곧 회전 경계 히스테리시스(N-3): D 안에 들어가면 곧 회전, 나올 때는 D + NEAR_EXIT_MARGIN을 넘어야 멀리 있음
//     (경계 근처 거리 흔들림에 작은↔큰 그림이 왔다갔다하던 것, #67). 카플레이(CpTurns)와 같은 규칙.
//     리뷰 반영: AA 회전 열쇠에는 번호가 없어 이름 없는 길의 같은 방향 연속 회전이 같은 열쇠다. 남은 거리가 지난번보다
//     NEAR_NEW_TURN_JUMP 넘게 늘면 새 회전으로 보고 히스테리시스를 새로 시작한다
//   - 도로 이름만 바뀔 때(로그 H, 이슈 전체 조사): 같은 회전(종류·방향·각도)에서 도로 이름만 바뀌면 남은 거리·곧 회전을 지우지
//     않는다. 전에는 이름이 바뀔 때마다 10ms 동안 멀리 있음이 나가 큰 그림이 2초마다 깜빡였다(350m 경계와 무관)

import android.content.Context;
import android.os.Handler;
import android.text.format.DateFormat;
import android.util.Log;

import java.util.Calendar;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

/**
 * Android Auto turn-by-turn on the cluster and HUD, for one Android Auto connection.
 * GPM has its own (GpmNaviTBTManager), but makes it only when stepofspec is 999 or
 * more, and that one also claims the cluster (alive + status), which these cars never
 * give back. This is the turn-by-turn build's manager in Java, fed by the AA library's
 * callbacks (gpm.navigation_status.*) and sending through HMG-API's Cluster API: turn,
 * distance, HUD turn list and distance, lanes, the next turns, remaining distance and
 * arrival time, destination name. It never sends alive or operation status.
 *
 * The AA library (the Wireless mod's library delta) packs more than stock into the
 * callbacks: next-turn events 32 (destination name; number = destination point),
 * 33 (debug dump), 34 (lanes, hex string), 64..66 (turns ahead: road, side, angle,
 * number = event | exit << 8), 67 (end of the turns ahead, number = how many); and
 * distance flags 0x200 (a turn ahead, index in bits 10-11) and 0x100 (remaining trip:
 * meters, seconds, unit, 0x800 + point << 12).
 *
 * Everything runs on one thread (the handler's), in the order the callbacks came.
 */
final class AaTurns {
    // AA protocol numbers (Protos.NavigationNextTurnEvent)
    static final int UNKNOWN = 0, DEPART = 1, NAME_CHANGE = 2, SLIGHT_TURN = 3, TURN = 4, SHARP_TURN = 5, U_TURN = 6,
            ON_RAMP = 7, OFF_RAMP = 8, FORK = 9, MERGE = 10, ROUNDABOUT_ENTER = 11, ROUNDABOUT_EXIT = 12,
            ROUNDABOUT_ENTER_AND_EXIT = 13, STRAIGHT = 14, FERRY_BOAT = 16, FERRY_TRAIN = 17, DESTINATION = 19;
    static final int LEFT = 1, RIGHT = 2, UNSPECIFIED = 3;
    static final int METERS = 1, KILOMETERS = 2, KILOMETERS_P1 = 3, MILES = 4, MILES_P1 = 5, FEET = 6, YARDS = 7;
    // the library's extra events and flags
    static final int EV_DEST_NAME = 32, EV_DUMP = 33, EV_LANES = 34, EV_AHEAD = 64, EV_AHEAD_END = 67;
    static final int FLAG_TRIP = 0x100, FLAG_AHEAD = 0x200, FLAG_POINT = 0x800;
    static final byte HUD_LIST = (byte) 0xF2, LANES = (byte) 0xF1;

    final Context context;
    /** 곧 회전 막대를 순정 내비 프로세스로 알린다(순정 단속 정보 프레임의 막대 칸, 1.7.2 시험판 3) */
    final PhoneBar.Sender barOut = new PhoneBar.Sender();
    final Cluster cluster;
    final Handler handler;
    /** where the turn log lines go (Wireless: the mod's log); null: none */
    Sink log;
    /** one log line (numbers only) */
    interface Sink {
        void line(String s);
    }
    /** turn log: the last values logged and how many lines so far */
    String turnKey;
    int turnLogs;
    static final int TURN_LOGS_MAX = 300;
    /** the crossroad name last sent and when; resent only when it changes or after CROSSROAD_MS */
    String crossroad;
    long crossroadAt;
    static final long CROSSROAD_MS = 10000L;
    /** the time (ms since boot); tests set clock */
    long clock = -1L;
    /** the 2C value for a point there isn't, and for guidance over (the stock navigation's) */
    static final int HUD_NO_POINT = 65535;
    /**
     * 순정 무안내 회전 값(M-14, 카플레이 CpTurns M-2와 같음): 표시 8(안내 없음), 거리 무효(distanceErr -3),
     * 단위 15, 막대 무효(barErr -1). 안내 끝 2C 한 칸은 단위 15, 소수 15, 정수 65535(순정 setClusterClearData).
     */
    static final int CLEAR_ICON = 8, CLEAR_DIST_ERR = -3, CLEAR_UNIT = 15, HUD_CLEAR_UNIT = 15, HUD_CLEAR_TENTHS = 15;
    /**
     * 남은 거리·시간(updateTBTSubInfo) 지우기 값: 순정 엔진은 0xFF·거리 0xFFFF를 넣고, 서비스가 범위 밖 값을
     * 시간 종류·형식 3, 단위 15로 바꾼다. 도착 지점 종류는 9인자판 기본값 15(없음)를 쓴다.
     */
    static final int CLEAR_TIME = 3, CLEAR_EST_POINT = 15;
    /**
     * 멀리 있음 값(M-3): 순정 엔진은 "곧 회전"(교차로 확대도·표지판 안내 중)일 때만 막대 1~10·방향·옆길을 넣고,
     * 그 밖은 막대 15(barErr -1)·방향 0xFF·combi 15·옆길 0이다. NavigationCluster는 방향 0..360·combi 0..4만
     * 받으므로(밖이면 0) 방향 0·combi 0으로 보낸다. 1.7.2(M-12): 차로가 있는 회전이 D 안이면 곧 회전 값(near)을 보내고,
     * 그 밖은 이 값이다. 회전교차로의 곧 회전 방향은 direction 필드에서 온다.
     */
    static final int BAR_FAR = 0, BAR_FAR_ERR = -1;
    /** AA NavigationStatus: 1 안내 중, 3 재탐색(그 밖 0 없음, 2 안내 안 함). 재탐색은 순정 표시 10 */
    static final int STATUS_ACTIVE = 1, STATUS_REROUTING = 3, DISPLAY_RESEARCH = 10;
    /** 곧 회전 D(m): 순정 교차로 확대 안내 단계 5 + MarginalDist 50(차-1). 차 속도는 모드에서 못 읽어 보정하지 않는다 */
    static final int D_GENERAL = 350, D_EXPRESSWAY = 1050;
    static final int NEAR_TURN = 13, NEAR_UTURN = 31, NEAR_EXIT_RIGHT = 17, NEAR_EXIT_LEFT = 18, NEAR_FORK = 25;
    /** 마지막 차로 프레임의 차로 수(이벤트 34). 1 이상이면 이 회전에 차로 안내가 있다(M-12) */
    int laneCount;
    /** 다음 회전까지 남은 거리(m, 폰 값 그대로, 없으면 -1)와 이 회전의 열쇠(곧 회전 판단) */
    int remainMeters = -1;
    String nearKey;
    /** 1.7.3(N-3): 이 회전이 곧 회전으로 들어갔다(나올 때는 D + NEAR_EXIT_MARGIN을 넘어야 함). 회전이 바뀌면 풀린다 */
    boolean nearOn;
    /** 곧 회전에서 나오는 여유(m). 들어갈 때는 D, 나올 때는 D + 이 값(카플레이와 같음) */
    static final int NEAR_EXIT_MARGIN = 50;
    /** 같은 열쇠라도 남은 거리가 지난 판단보다 이만큼 넘게 늘면 새 회전(지난 회전을 지남). 거리 흔들림보다 크게 둔다 */
    static final int NEAR_NEW_TURN_JUMP = 100;
    /** near()가 마지막으로 본 남은 거리(m, 없으면 -1) */
    int nearLast = -1;
    /*
     * 1.7.3 A-2 규칙(하나로 정리, 2026-11-10 "A-2 규칙 정리"). 원칙: 지난 회전의 그림을 보이는 것이 아이콘 깜빡임보다 나쁘다.
     *  - 애매한 이벤트 두 가지: ① 실제 회전이 걸린 채 온 이름 바뀜(ev 2), ② 종류·방향·각도가 같고 도로 이름만 다른 회전 이벤트.
     *    둘 다 바로 보내지 않고 "다음 거리 표본 하나"로 판단한다. 그 거리가 이벤트 때 거리보다 PASS_WOBBLE 넘게 늘었으면
     *    회전을 지난 것(① 이름 바뀜을 보임, ② 새 회전), 같거나 줄었으면 같은 지점(① 아이콘 그대로 이름만, ② 같은 회전 이름 갱신).
     *  - 거리가 안 오면 한 번짜리 기한(PEND_WAIT_MS): ① 풀어서 이름 바뀜(안전한 쪽), ② 남은 거리가 SAME_PASSED_NEAR 이하면
     *    새 회전(멀리 있음), 아니면 같은 회전으로 이름만.
     *  - 붙잡은 뒤(①이 같은 지점으로 판단됨): 거리가 PASS_WOBBLE 넘게 늘거나 마지막 실제 회전 이벤트에서 NAME_HOLD_MS가 지나면 푼다.
     *    시간 풀기도 같은 기한 타이머로 한다(거리가 안 와도 풀림).
     *  - 타이머는 nameDue 하나뿐이고 늘 많아야 하나 걸린다(걸 때마다 앞의 것을 지움). 새 경로·재탐색·안내 끝·포커스 순정·연결 끝이면 지운다.
     *  - 설정 aa_name_hold(기본 켬)를 끄면(nameHold=false) 이 규칙을 쓰지 않는다: 이름 바뀜은 바로 보이고 이름만 다른 이벤트도
     *    바로 보낸다(2.4.0처럼). 곧 회전 히스테리시스와 "도로 이름만 바뀌면 거리 유지"는 설정과 상관없이 쓴다.
     */
    /** 설정 aa_name_hold(Wireless가 넣음). false면 A-2 규칙을 쓰지 않는다 */
    boolean nameHold = true;
    /** ① 이름 바뀜이 다음 거리를 기다림 / 붙잡음(같은 지점으로 판단됨) */
    boolean namePend, nameHeld;
    /** ① 기다릴 때의 기준 거리(m). -1이면 ②가 먼저 풀린 뒤 그 거리를 기준으로 한 번 더 기다린다 */
    int nameBase = -1;
    /** 붙잡은 뒤 마지막으로 본 거리(m) */
    int holdLast = -1;
    String nameRoad;
    int nameSide, nameAngle, nameNumber;
    /** ② 이름만 다른 같은 꼴 회전 이벤트가 다음 거리를 기다림, 그 기준 거리(m) */
    boolean samePend;
    int sameBase = -1;
    /** 마지막 실제 회전(이름 바뀜이 아닌 회전) 이벤트가 온 시각(ms) */
    long turnAt;
    /** 실제 회전 이벤트 뒤 남은 거리가 늘었다(그 회전을 지남). 다음 실제 회전 이벤트까지 이름 바뀜을 붙잡지 않는다 */
    boolean turnPassed;
    /** 다른 회전 이벤트 뒤 아직 거리가 없다: 그 첫 거리는 새 회전의 것이라 "지남"을 세지 않는다(같은 방향 연속 갈림길) */
    boolean turnSinceDist;
    /** 거리가 이만큼 넘게 늘면 회전을 지난 것(m, 흔들림 여유) */
    static final int PASS_WOBBLE = 5;
    /** 다음 거리를 기다리는 한도(ms): 안 오면 안전한 쪽으로 정한다 */
    static final long PEND_WAIT_MS = 2000L;
    /** 붙잡기 한도: 마지막 실제 회전 이벤트에서 이만큼 지나면 푼다(ms, #65는 2~4초마다 옴) */
    static final long NAME_HOLD_MS = 8000L;
    /** ② 기한에서 남은 거리가 이 값 이하면 앞 회전을 지났을 법하다(새 회전으로 보고 멀리 있음) */
    static final int SAME_PASSED_NEAR = 50;
    /** 이름 규칙의 한 번짜리 기한(위 설명). 예외가 나도 GPM이 죽지 않게 잡는다 */
    final Runnable nameDue = new Runnable() {
        @Override
        public void run() {
            try {
                onNameDue();
            } catch (Throwable t) {
                timerFailed(t);
            }
        }
    };
    /** 붙잡기 로그 줄 수(프로세스 전체 한도는 TURN_LOGS_MAX, 여기서는 연결마다 20줄까지, end()에서 0으로) */
    int nameHoldLogs;
    /** 타이머 예외를 한 번만 남긴다 */
    boolean timerFailLogged;
    /** 재탐색 표시(10)를 보내는 중 */
    boolean rerouting;
    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                unitTick();
            } catch (Throwable t) {
                timerFailed(t);   // Handler가 직접 돌리므로 예외가 나가면 GPM이 죽는다(6단계 뒤 리뷰 L3)
            }
        }
    };

    // the next turn
    int event, turnSide, turnAngle, turnNumber, displayType, direction, unit;
    String road;
    float distance;
    boolean navLive;
    /**
     * stop()가 계기판을 지운 뒤 아직 계기판에 아무것도 보내지 않았다(1.7.2). 그동안 포커스 신호가 다시 오거나
     * 연결·경로가 끝나도 같은 지우기를 또 보내지 않는다(그 사이 순정 내비가 보낸 값을 지우지 않게, 리뷰 1·3).
     * 계기판에 실제로 보낼 때만(sent) 풀린다. 내비 메시지·앞쪽 회전 저장처럼 보내지 않는 이벤트로는 풀리지 않는다.
     */
    boolean clearedByStop;

    /**
     * 포커스가 순정 내비에 있다(stop 뒤, 2차 리뷰 3). 그동안 폰 이벤트는 상태만 저장하고 계기판에는 보내지 않는다.
     * 순정 값을 아이콘 0·폰 거리로 덮지 않게 한다. 포커스가 폰으로 돌아오면(focusPhone) 다시 보낸다.
     */
    boolean focusNative;

    /** 포커스가 폰(프로젝션)으로 돌아왔다: 다시 계기판에 보낸다. 기억한 목적지 이름은 바로 다시 보낸다 */
    void focusPhone() {
        if (!focusNative) return;
        focusNative = false;
        if (navLive && destName != null && destName.length() > 0) sendDestName();
        if (navLive && rerouting) sendRerouting();
    }

    /** 계기판에 무언가를 실제로 보냈다: 다음 stop·경로 끝에서는 다시 지운다 */
    private void sent() {
        clearedByStop = false;
    }
    // destination and the remaining trip
    String destName;
    int estPoint;
    int tripMeters = -1, tripSeconds = -1, tripUnit, tripGen, sentGen;
    int lastUsm = -2;
    // HUD turn list (what was sent last)
    int hudType = -1, hudNext, hudThird;
    boolean hudFresh;
    String hudRoad, laneKey;
    // the turns ahead (index 0..2)
    final String[] aheadName = new String[3];
    final int[] aheadEv = new int[3], aheadSide = new int[3], aheadAng = new int[3], aheadExit = new int[3];
    final int[] aheadMeters = new int[3], aheadUnit = new int[3], aheadE3 = new int[3];
    final boolean[] aheadOk = new boolean[3];

    AaTurns(Context context, Cluster cluster, Handler handler) {
        this.context = context;
        this.cluster = cluster;
        this.handler = handler;
    }

    long now() {
        return clock >= 0L ? clock : android.os.SystemClock.elapsedRealtime();
    }

    /** the crossroad name, when it changed or CROSSROAD_MS after the last one (stock DiPO sends it with every update) */
    void sendCrossroad(String name) {
        if (focusNative) return;
        String r = name == null ? "" : name;
        long t = now();
        if (r.equals(crossroad) && t - crossroadAt < CROSSROAD_MS) return;
        cluster.crossroadName(name);
        sent();
        crossroad = r;
        crossroadAt = t;
    }

    // ---- from the AA library

    void nextTurn(String str, int side, int ev, int angle, int number) {
        if (ev == EV_DUMP) {
            Log.d("NAVDUMP", String.valueOf(str));
            hmgapi.hook.ProjectionHooks.navigationDump(str);   // to mods that asked for projection events (EGMP's route data)
            return;
        }
        if (ev == EV_LANES) {
            sendLanes(str);
            return;
        }
        if (ev == EV_DEST_NAME) {
            if (str == null || str.length() == 0) {
                destName = "";
                sendDestName();
                destName = null;
                return;
            }
            if (number >= 0) estPoint = number;
            if (!str.equals(destName)) {
                destName = str;
                sendDestName();
            }
            return;
        }
        if (ev == EV_AHEAD_END) {
            if (number > 0) sendAhead(Math.min(number, 3));
            return;
        }
        if (ev >= EV_AHEAD && ev < EV_AHEAD_END) {
            int k = ev - EV_AHEAD;
            aheadName[k] = str == null ? "" : str;
            aheadSide[k] = side;
            aheadAng[k] = angle;
            aheadEv[k] = number & 0xff;
            aheadExit[k] = (number >> 8) & 0xff;
            aheadOk[k] = false;
            return;
        }
        if (!nameHold) {   // 설정 aa_name_hold 끔: 2.4.0처럼 바로(이름만 바뀐 같은 회전은 거리 유지)
            if (setTurn(str, side, ev, angle, number)) sendTurn();
            return;
        }
        if (ev == NAME_CHANGE) {
            if (nameHeld || namePend) {   // 이미 붙잡았거나 기다리는 중: 이름 바뀜 값만 새로
                keepName(str, side, angle, number);
                if (nameHeld && !samePend) sendTurn();   // 붙잡음: 아이콘 그대로 이름만
                return;
            }
            if (holdsName()) {   // ① 실제 회전이 걸려 있음: 다음 거리로 판단(그동안 보내지 않음)
                keepName(str, side, angle, number);
                namePend = true;
                nameBase = samePend ? -1 : remainMeters;
                armDue();
                return;
            }
        } else {
            // 실제 회전 이벤트: 그 회전을 보인다(#65에서 갈림길이 다시 오면 여기). 이름 바뀜 판단은 버린다
            turnAt = now();
            turnPassed = false;
            namePend = false;
            nameHeld = false;
            nameRoad = null;
        }
        if (setTurn(str, side, ev, angle, number) && !samePend) sendTurn();
        armDue();
    }

    private void keepName(String str, int side, int angle, int number) {
        nameRoad = str;
        nameSide = side;
        nameAngle = angle;
        nameNumber = number;
    }

    /** 이름 바뀜을 붙잡을 만한 실제 회전(아이콘이 다른 회전)이 걸려 있고, 그 회전 이벤트가 NAME_HOLD_MS 안에 왔다 */
    boolean holdsName() {
        if (!navLive || rerouting || turnPassed || turnAt == 0L || now() - turnAt >= NAME_HOLD_MS) return false;
        switch (event) {
            case SLIGHT_TURN: case TURN: case SHARP_TURN: case U_TURN: case ON_RAMP: case OFF_RAMP: case FORK: case MERGE:
            case ROUNDABOUT_EXIT: case ROUNDABOUT_ENTER_AND_EXIT: case STRAIGHT:
                return true;
            default:
                return false;
        }
    }

    /** 기한 타이머를 지금 상태에 맞게 다시 건다(많아야 하나). 기다림이면 PEND_WAIT_MS, 붙잡음이면 한도까지, 아니면 없음 */
    void armDue() {
        handler.removeCallbacks(nameDue);
        if (samePend || namePend) handler.postDelayed(nameDue, PEND_WAIT_MS);
        else if (nameHeld) handler.postDelayed(nameDue, Math.max(0L, turnAt + NAME_HOLD_MS - now()));
    }

    /** 기한: 다음 거리가 안 왔거나 붙잡기 한도가 됐다. 안전한 쪽으로 정하고 한 번 보낸다 */
    void onNameDue() {
        boolean send = false;
        if (samePend) {
            samePend = false;
            if (remainMeters >= 0 && remainMeters <= SAME_PASSED_NEAR) {
                newTurnNoDistance();   // 앞 회전을 지났을 법함: 새 회전, 거리를 모름(멀리 있음)
            }
            send = true;   // 아니면 같은 회전: 거리 그대로 새 이름
            if (namePend && nameBase < 0) nameBase = remainMeters;
        }
        if (namePend) {
            namePend = false;
            releaseName();   // 거리가 안 옴: 지난 회전 그림을 남기지 않는다(정차 중 #65는 깜빡임을 받아들임)
            send = true;
        } else if (nameHeld && now() - turnAt >= NAME_HOLD_MS) {
            releaseName();
            send = true;
        }
        if (send) sendTurn();
        armDue();
    }

    /** 같은 꼴 다음 회전으로 본다(거리를 모름): 곧 회전·히스테리시스를 새로 */
    private void newTurnNoDistance() {
        remainMeters = -1;
        nearOn = false;
        nearLast = -1;
        turnSinceDist = true;
    }

    /** 타이머 안 예외: 한 번만 남기고 넘어간다 */
    void timerFailed(Throwable t) {
        if (timerFailLogged || log == null) return;
        timerFailLogged = true;
        log.line("AA: timer error " + t.getClass().getSimpleName());
    }

    /** 이름 규칙을 버린다(새 경로·재탐색·안내 끝·포커스 순정·연결 끝): 기다림·붙잡음·기한 모두 */
    void forgetName() {
        namePend = false;
        nameHeld = false;
        samePend = false;
        nameRoad = null;
        turnAt = 0L;
        turnPassed = false;
        handler.removeCallbacks(nameDue);
    }

    /** 붙잡기를 끝낸다: 이름 바뀜을 이제 다음 회전으로 쓴다(보내지는 않음, 부르는 쪽이 보냄) */
    void releaseName() {
        nameHeld = false;
        namePend = false;
        String r = nameRoad;
        nameRoad = null;
        turnAt = 0L;
        holdLast = -1;
        setTurn(r, nameSide, NAME_CHANGE, nameAngle, nameNumber);
    }

    /** 계기판·HUD에 보일 도로 이름: 이름 바뀜을 붙잡았으면 그 이름(이름만 바꾼다), 아니면 회전의 이름 */
    String shownRoad() {
        return nameHeld && nameRoad != null && nameRoad.length() > 0 ? nameRoad : road;
    }

    /** 다음 회전을 기억한다. 계기판에 보낼 회전이면 true(부르는 쪽이 sendTurn) */
    boolean setTurn(String str, int side, int ev, int angle, int number) {
        hudFresh = false;
        boolean first = road == null;
        // 다른 회전이 왔다: 남은 거리는 앞 회전의 것이므로 새 거리가 올 때까지 곧 회전을 판단하지 않는다(리뷰 1)
        // 1.7.3 추가(로그 H): 도로 이름만 바뀐 같은 회전(종류·방향·각도 같음)은 남은 거리를 지우지 않는다. 지우면 다음 거리가
        // 올 때까지(약 10ms) 멀리 있음으로 나가 큰 그림이 2초마다 깜빡였다(#65·#67·#71·#104). 이름 없는 길의 같은 방향 연속
        // 회전은 near()의 "거리가 크게 늘면 새 회전"(NEAR_NEW_TURN_JUMP)이 가른다
        boolean sameTurn = ev == event && side == turnSide && angle == turnAngle;
        boolean sameRoad = str == null ? road == null : str.equals(road);
        if (!sameTurn) remainMeters = -1;
        // ② 이름만 다르면 다음 거리에서 판단(바로 보내지 않음, A-2 규칙), 무엇이든 다르면 그 첫 거리는 새 회전의 것
        samePend = nameHold && ev != NAME_CHANGE && sameTurn && !sameRoad && remainMeters >= 0;
        if (samePend) sameBase = remainMeters;
        turnSinceDist = !(sameTurn && sameRoad);
        event = ev;
        road = str;
        turnSide = side;
        turnAngle = angle;
        turnNumber = number;
        switch (ev) {
            case DEPART:
                if (first) {
                    if (!focusNative) {
                        cluster.destinationName(0, road);
                        sent();
                    }
                    distance = -1f;
                } else {
                    setTurnType(ev, turnSide);
                }
                break;
            case NAME_CHANGE: case SLIGHT_TURN: case TURN: case SHARP_TURN: case STRAIGHT: case MERGE:
                setTurnType(ev, turnSide);
                break;
            case U_TURN:
                if (side == UNSPECIFIED) turnSide = LEFT;
                setTurnType(ev, turnSide);
                break;
            case ON_RAMP: case OFF_RAMP: case FORK: case FERRY_BOAT:
                setRampType(ev, turnSide);
                break;
            case ROUNDABOUT_EXIT: case ROUNDABOUT_ENTER_AND_EXIT:
                setRoundaboutType(ev, turnSide, turnAngle);
                break;
            case DESTINATION:
                displayType = 3;
                break;
            case UNKNOWN:
                displayType = 64;
                break;
            default:
                return false;   // ROUNDABOUT_ENTER, FERRY_TRAIN: not for the cluster
        }
        return true;
    }

    void nextTurnDistance(int meters, int seconds, int e3, int unitFlags) {
        if ((unitFlags & FLAG_AHEAD) != 0) {
            int k = (unitFlags >> 10) & 3;
            if (k > 2) return;
            aheadMeters[k] = meters;
            aheadE3[k] = e3;
            aheadUnit[k] = unitFlags & 0xff;
            aheadOk[k] = true;
            return;
        }
        if ((unitFlags & FLAG_TRIP) != 0) {
            if ((unitFlags & FLAG_POINT) != 0) {
                int p = (unitFlags >> 12) & 15;
                estPoint = p > 4 ? 0 : p;
            }
            tripMeters = meters;
            tripSeconds = seconds;
            tripUnit = unitFlags & 0xff;
            tripGen++;
            sendRemain();
            return;
        }
        // 1.7.3 A-2 규칙: 다음 거리 표본 하나로 애매한 이벤트를 판단한다(위 필드 설명)
        boolean fresh = turnSinceDist;
        turnSinceDist = false;
        if (samePend) {   // ② 이름만 다른 같은 꼴 회전
            samePend = false;
            if (meters > sameBase + PASS_WOBBLE) {
                newTurnNoDistance();   // 거리가 늘었음: 앞 회전을 지난 다음 회전
                turnSinceDist = false;
                fresh = true;
            } else {
                fresh = false;   // 같은 회전의 이름 갱신
            }
        }
        boolean passed = !fresh && remainMeters >= 0 && meters > remainMeters + PASS_WOBBLE;
        if (passed && turnAt != 0L) turnPassed = true;
        if (namePend) {   // ① 이름 바뀜
            if (nameBase < 0) {
                // 기준 거리가 없었음(거리 전이었거나 ② 판단 중에 옴): 이 거리를 기준으로 한 표본 더(그동안 지금 회전을 보인다)
                nameBase = meters;
            } else {
                namePend = false;
                // passed: 이 거리가 앞 거리(= 이름 바뀜 때 기준 거리)보다 PASS_WOBBLE 넘게 늘었음
                if (passed || now() - turnAt >= NAME_HOLD_MS) {
                    releaseName();   // 회전을 지남(또는 붙잡기 한도): 이름 바뀜을 보인다
                } else {
                    nameHeld = true;   // 같은 지점(#65): 아이콘 그대로 이름만
                    holdLast = meters;
                    if (log != null && nameHoldLogs < 20) {
                        nameHoldLogs++;
                        log.line("AA: name change kept icon " + displayType + " (ev " + event + ")");
                    }
                }
            }
        } else if (nameHeld) {   // 붙잡은 뒤: 늘었거나 한도면 푼다
            if ((holdLast >= 0 && meters > holdLast + PASS_WOBBLE) || now() - turnAt >= NAME_HOLD_MS) releaseName();
            else holdLast = meters;
        }
        armDue();
        remainMeters = meters;
        setDistance(meters, e3, unitFlags);
        setTurnType(event, turnSide);
        sendTurn();
    }

    void status(int s) {
        if (s == STATUS_ACTIVE || s == STATUS_REROUTING) {   // active, rerouting
            if (!navLive) {
                crossroad = null;   // a new route: the name goes out again
                // 새 경로: 옛 경로의 남은 거리로 곧 회전을 띄우지 않는다(소유자 결정 11-09 뒤 리뷰, 차로 조건이 막던 것)
                remainMeters = -1;
                nearKey = null;
                nearLast = -1;
                forgetName();
            }
            navLive = true;
            if (s == STATUS_REROUTING && !rerouting) {
                rerouting = true;
                nearKey = null;
                nearLast = -1;
                forgetName();
                // 재탐색: 새 경로의 거리가 올 때까지 곧 회전을 판단하지 않는다(옛 경로 거리로 큰 그림이 나오지 않게)
                remainMeters = -1;
                // 새 경로: 차로는 다시 온 것만 센다(리뷰 2). 포커스와 상관없이 비우고, 재탐색 뒤 같은 차로 프레임도 다시 나가게 한다
                laneCount = 0;
                laneKey = "";
                if (log != null) log.line("AA: rerouting, display " + DISPLAY_RESEARCH);
                sendRerouting();
            } else if (s == STATUS_ACTIVE && rerouting) {
                // 재탐색이 끝났다: 기억한 회전이 있으면 보통 표시로 바로 돌아간다(HUD 목록은 지웠으므로 다시 나간다)
                rerouting = false;
                if (log != null) log.line("AA: rerouting over");
                // 6단계 뒤 리뷰 L1: 판단을 기다리는 중이면 옛 거리로 보내지 않는다(다음 거리·기한이 보냄)
                if ((event != 0 || displayType != 0) && !samePend && !namePend) sendTurn();
            }
            return;
        }
        rerouting = false;
        handler.removeCallbacks(tick);
        destName = null;
        navLive = false;
        remainMeters = -1;
        nearKey = null;
        nearLast = -1;
        tripMeters = -1;
        tripSeconds = -1;
        estPoint = 0;
        forgetName();
        // 포커스가 순정이면 계기판은 순정 것이다: 그동안 보낸 것이 없으므로 지우지 않는다(2차 리뷰 3)
        if (!clearedByStop && !focusNative) release();
        clearedByStop = false;
    }

    /**
     * The car's navigation took over (focus native): forget the turn. 1.7.2(M-14): 안내 중이었으면 계기판도
     * 지운다(release). 지우지 않으면 순정 엔진이 "바뀔 때만" 보내므로 폰의 마지막 회전이 남는다. 경로 자체는
     * 폰에서 이어지므로 navLive는 그대로 두어, 포커스가 폰으로 돌아오면 다음 회전부터 다시 보낸다.
     */
    void stop() {
        // 막대 칸은 순정 값으로 되돌린다(포커스가 순정으로 넘어감)
        PhoneBar.send(context, barOut, PhoneBar.NONE, now());
        boolean was = focusNative;
        focusNative = true;
        remainMeters = -1;
        nearKey = null;
        nearLast = -1;
        forgetName();
        if (!was && navLive && !clearedByStop) {
            // 포커스 신호가 다시 와도(그 사이 보낸 것이 없으면) 다시 지우지 않는다(리뷰 1)
            // 남은 거리 확인(250ms)도 멈춘다. 폰이 남은 거리를 다시 보내면 sendRemain이 다시 건다.
            // release가 destName을 비우므로, 폰이 같은 목적지 이름을 다시 보내면 그대로 다시 나간다.
            handler.removeCallbacks(tick);
            release();
            clearedByStop = true;
        }
        crossroad = null;
        event = 0;
        road = null;
        turnSide = 0;
        turnAngle = 0;
        turnNumber = 0;
        unit = 0;
        displayType = 0;
        direction = 0;
    }

    /** The connection ended: clear what we showed, if a route was on. */
    void end() {
        handler.removeCallbacks(tick);
        forgetName();
        nameHoldLogs = 0;
        PhoneBar.send(context, barOut, PhoneBar.NONE, now());
        if (navLive) status(2);
    }

    // ---- the next turn

    void sendTurn() {
        if (!navLive || focusNative) return;
        if (rerouting) {
            // 재탐색 중에는 폰 이벤트마다 표시 10만 다시 보낸다(AA에는 카플레이 refresh 같은 주기 재전송이 없음, 리뷰 7)
            cluster.tbtInfo(DISPLAY_RESEARCH, 0, 0f, 0f, CLEAR_DIST_ERR, 0, CLEAR_UNIT, 0, 0);
            PhoneBar.send(context, barOut, 0, now());
            sent();
            return;
        }
        // 멀리 있음 값(M-3): 막대 무효, 옆길 0, 방향 0. 거리가 아직 없을 때(첫 출발)는 2.3.0까지와 같은 값
        int bar = BAR_FAR, barErr = BAR_FAR_ERR;
        if (distance < 0f) {
            bar = 254;
            barErr = -2;
            if (displayType < 64) displayType = 2;
        }
        int[] near = distance < 0f ? null : near();
        logTurn(near);
        // 도로 이름은 곧 회전일 때만(순정 sendTurnNameData: 멀리 있으면 빈 이름)
        sendCrossroad(near != null ? shownRoad() : "");
        if (near != null) cluster.tbtInfo(near[0], near[1], near[2] * 7.5f, distance, 0, 0, unit, near[3], 0);
        else cluster.tbtInfo(displayType, 0, 0f, distance, 0, 0, unit, bar, barErr);
        // 순정처럼 단속 정보 프레임의 막대 칸에도 같은 값(멀리 있으면 0)
        PhoneBar.send(context, barOut, near != null ? near[3] : 0, now());
        sent();
        sendHudList();
        sendHudDistance();
    }

    /** the turn's values (numbers only: no road names) when they change, up to TURN_LOGS_MAX lines */
    void logTurn(int[] near) {
        if (log == null) return;
        String k = event + " " + turnSide + " " + turnAngle + " " + displayType + " " + (near == null ? "far" : near[0] + " " + near[4]);
        if (k.equals(turnKey) || turnLogs >= TURN_LOGS_MAX) return;
        turnKey = k;
        turnLogs++;
        log.line("AA: turn ev " + event + " side " + turnSide + " angle " + turnAngle + " -> icon " + displayType
                + (near == null ? " far" : " near display " + near[0] + " bar " + near[3] + " d " + near[4]));
    }

    /** 재탐색 표시(순정 10): 방향·옆길·거리 무효, 막대 0. 도로 이름을 비우고 2C·HUD 목록을 지운다(순정 강제 지우기) */
    void sendRerouting() {
        if (focusNative) return;
        cluster.tbtInfo(DISPLAY_RESEARCH, 0, 0f, 0f, CLEAR_DIST_ERR, 0, CLEAR_UNIT, 0, 0);
        PhoneBar.send(context, barOut, 0, now());
        cluster.crossroadName("");
        crossroad = "";
        crossroadAt = now();
        cluster.tbtSubInfo2C(HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT, HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT,
                HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT);
        cluster.turnListNew(new byte[] { HUD_LIST, 0 });
        hudType = -1;
        hudNext = 0;
        hudThird = 0;
        hudFresh = false;
        hudRoad = null;
        sent();
    }

    /**
     * 곧 회전 화면 값 {표시 번호, 옆길 비트, 방향 칸(7.5도 단위), 막대(10..90), D} 또는 멀리 있음이면 null(M-12).
     * 조건(소유자 결정 2026-11-09, 차로 조건 없음): 남은 거리(m)를 앎, 곧 회전 화면이 있는 회전, 남은 거리 <= D.
     */
    int[] near() {
        if (remainMeters < 0) return null;
        // 이 회전의 열쇠(종류·방향·각도). 1.7.3 추가: 열쇠에서 도로 이름을 뺐다(이름만 바뀌어도 같은 회전, 위 setTurn과 같은 규칙)
        String key = event + " " + turnSide + " " + turnAngle;
        // 다른 회전, 또는 같은 열쇠라도 거리가 크게 늘었음(같은 방향 연속 회전, 리뷰 4): 히스테리시스는 새로 시작(N-3)
        if (!key.equals(nearKey) || (nearLast >= 0 && remainMeters > nearLast + NEAR_NEW_TURN_JUMP)) nearOn = false;
        nearLast = remainMeters;
        nearKey = key;
        // 소유자 결정 2026-11-09: 차로 조건을 뺐다(카플레이와 같은 규칙). D 안의 회전이면 곧 회전
        int[] disp = nearDisplay(event, turnSide, displayType, direction);
        if (disp == null) return null;
        int d = event == OFF_RAMP ? D_EXPRESSWAY : D_GENERAL;
        // D는 순정처럼 고정이다(차 로그 2026-11-09: 순정은 210m에서 처음 본 회전도 막대 60으로 시작한다)
        // 1.7.3(N-3): 들어갈 때는 D, 이미 곧 회전이면 D + NEAR_EXIT_MARGIN을 넘어야 나간다(경계 흔들림에 그림이 왔다갔다하지 않게)
        if (remainMeters > (nearOn ? d + NEAR_EXIT_MARGIN : d)) {
            nearOn = false;
            return null;
        }
        nearOn = true;
        int bits = disp[1] < 0 ? 0 : 1 << disp[1];
        return new int[] { disp[0], bits, disp[2], bar(remainMeters, d), d };
    }

    /** 막대 = 10 x max(1, floor(남은 x 10 / D)), 90까지(순정 SetCase*, 차 로그 2026-11-09 최대 90) */
    static int bar(int remain, int d) {
        int b = (int) ((long) remain * 10L / d);
        if (b < 1) b = 1;
        if (b > 9) b = 9;
        return b * 10;
    }

    /**
     * 곧 회전 {표시 번호, 나가는 길 옆길 비트 번호(-1: 옆길 없음), 방향 칸} 또는 곧 회전 화면이 없는 회전이면 null. 순정 SetCase*:
     * 회전 13(방향: 약간 6/42, 보통 12/36, 급 18/30, 직진 0), 유턴 31(오른쪽 통행 36), 출구 17/18(12/36), Y자 갈림 25,
     * 회전교차로는 출구 아이콘 그대로(방향은 출구 각도). 방향을 모르는 회전(아이콘 64), 이름 바뀜·출발·목적지·페리는 멀리 있음.
     */
    static int[] nearDisplay(int ev, int side, int displayType, int direction) {
        switch (ev) {
            case SLIGHT_TURN: case TURN: case SHARP_TURN: {
                if (side != LEFT && side != RIGHT) return null;
                int slot = ev == SLIGHT_TURN ? 6 : ev == TURN ? 12 : 18;
                if (side == LEFT) slot = 48 - slot;
                return new int[] { NEAR_TURN, slot / 3, slot };
            }
            case STRAIGHT: case MERGE:
                return new int[] { NEAR_TURN, 0, 0 };
            case ON_RAMP: {
                int slot = side == RIGHT ? 6 : side == LEFT ? 42 : 0;
                return new int[] { NEAR_TURN, slot / 3, slot };
            }
            case U_TURN:
                return new int[] { NEAR_UTURN, -1, side == RIGHT ? 12 : 36 };
            case OFF_RAMP:
                return side == LEFT ? new int[] { NEAR_EXIT_LEFT, -1, 36 } : new int[] { NEAR_EXIT_RIGHT, -1, 12 };
            case FORK:
                if (side != LEFT && side != RIGHT) return null;
                return new int[] { NEAR_FORK, -1, side == RIGHT ? 6 : 42 };
            case ROUNDABOUT_ENTER_AND_EXIT: {
                // 회전교차로: 출구 아이콘 그대로, 방향은 출구 각도(setRoundaboutType의 direction, 시계 방향)
                if (displayType == 64 || displayType <= 0) return null;
                int cw = ((direction % 360) + 360) % 360, bit, slot;
                if (cw >= 348 || cw <= 12) {
                    bit = 0;
                    slot = 0;
                } else if (cw > 168 && cw < 192) {
                    // 뒤쪽 출구(순정 SetSideStreetBit): 179도 이하 비트 7·칸 21, 그 밖 비트 8·칸 27
                    bit = cw <= 179 ? 7 : 8;
                    slot = cw <= 179 ? 21 : 27;
                } else {
                    bit = Math.min(15, (int) ((cw - 12) / 22.5) + 1);
                    slot = 3 * bit;
                }
                return new int[] { displayType, bit, slot };
            }
            default:
                return null;
        }
    }



    /** Distance to the next turn, in the owner's unit (Settings) where the phone's differs. */
    void setDistance(int meters, int e3, int u) {
        int usm = Car.distanceUnit(context);
        if (meters >= 0) {
            if (usm == Car.DISTANCE_KM) {
                if (u >= MILES) {
                    if (meters < 1000) {
                        u = METERS;
                    } else {
                        e3 = meters;
                        u = KILOMETERS;
                    }
                }
            } else if (usm == Car.DISTANCE_MILES) {
                if (meters >= 161) {
                    if (u < MILES) {
                        e3 = meters * 1000 / 1609;
                        u = MILES_P1;
                    }
                } else if (u != FEET && u != YARDS) {
                    e3 = meters * 3281;
                    u = FEET;
                }
            }
        }
        distance = (float) (e3 / 1000.0d);
        if (u == METERS) {
            if (meters >= 1000) {
                distance = (float) (meters / 1000.0d);
                unit = Cluster.UNIT_KM;
            } else {
                distance = meters;
                unit = Cluster.UNIT_M;
            }
        } else if (u == KILOMETERS || u == KILOMETERS_P1) {
            unit = Cluster.UNIT_KM;
        } else if (u == MILES || u == MILES_P1) {
            unit = Cluster.UNIT_MI;
        } else if (u == FEET) {
            unit = Cluster.UNIT_FT;
        } else if (u == YARDS) {
            unit = Cluster.UNIT_YD;
        } else {
            unit = 15;
        }
    }

    void setTurnType(int ev, int side) {
        switch (ev) {
            case STRAIGHT: case MERGE:
                displayType = 65;
                direction = 0;
                break;
            case SLIGHT_TURN:
                set(side, 66, 30, 71, 330);
                break;
            case TURN:
                set(side, 67, 90, 70, 270);
                break;
            case SHARP_TURN:
                set(side, 68, 135, 69, 225);
                break;
            case U_TURN:
                displayType = side == RIGHT ? 73 : 72;
                direction = 270;
                break;
            case NAME_CHANGE:
                if (side == RIGHT || side == LEFT) {
                    set(side, 67, 90, 70, 270);
                } else {
                    displayType = 65;
                    direction = 0;
                }
                break;
            default:
                break;   // ramps, roundabouts, destination: set by their own
        }
    }

    /** right type/direction, left type/direction, otherwise 64 (unknown) straight */
    private void set(int side, int rType, int rDir, int lType, int lDir) {
        if (side == RIGHT) {
            displayType = rType;
            direction = rDir;
        } else if (side == LEFT) {
            displayType = lType;
            direction = lDir;
        } else {
            displayType = 64;
            direction = 0;
        }
    }

    /** ramps as the Korean stock GPM: highway entry 160-162, highway exit 163-165 (the stock engine's) */
    void setRampType(int ev, int side) {
        if (ev == ON_RAMP) {
            if (side == RIGHT) {
                displayType = 161;
                direction = 30;
            } else if (side == LEFT) {
                displayType = 162;
                direction = 330;
            } else {
                displayType = 160;
                direction = 0;
            }
        } else if (ev == OFF_RAMP) {
            if (side == RIGHT) {
                displayType = 164;
                direction = 30;
            } else if (side == LEFT) {
                displayType = 165;
                direction = 330;
            } else {
                displayType = 163;
                direction = 0;
            }
        } else if (ev == FORK) {
            if (side == RIGHT) displayType = 173;
            else if (side == LEFT) displayType = 174;
            direction = 0;
        } else if (ev == FERRY_BOAT) {
            displayType = 176;
            direction = 0;
        }
    }

    /** Roundabouts: the exit's angle picks the icon (the cluster draws them for right-hand traffic). */
    void setRoundaboutType(int ev, int side, int a) {
        if (ev == ROUNDABOUT_EXIT) {
            if (side == RIGHT) {
                displayType = 32;
            } else if (side == LEFT) {
                displayType = 33;
            } else {
                displayType = 64;
                direction = 0;
            }
            return;
        }
        if (a == 180) { displayType = 96; direction = 0; }
        else if (a >= 135 && a < 180) { displayType = 97; direction = 45; }
        else if (a >= 90 && a < 135) { displayType = 99; direction = 90; }
        else if (a >= 45 && a < 90) { displayType = 100; direction = 135; }
        else if ((a >= 0 && a < 45) || (a > 315 && a <= 360)) { displayType = 102; direction = 180; }
        else if (a > 270 && a <= 315) { displayType = 103; direction = 225; }
        else if (a > 225 && a <= 270) { displayType = 105; direction = 270; }
        else if (a > 180 && a <= 225) { displayType = 106; direction = 315; }
        else { displayType = 64; direction = 0; }
    }

    // ---- HUD

    /** HUD 목록 이름의 최대 바이트(UTF-16LE). 순정 내비는 이름 칸을 0x50바이트까지 쓴다(drafts/stock-re 3-3) */
    static final int HUD_NAME_MAX = 78;

    /**
     * UTF-16LE로 max 바이트 안에 드는 이름 앞부분. 글자 경계에서 자르고, 서로게이트 쌍(이모지 등)의
     * 앞 반쪽만 남기지 않는다. 1.7.2(M-15)
     */
    static String hudName(String s, int max) {
        int n = Math.min(s.length(), max / 2);
        if (n > 0 && n < s.length() && Character.isHighSurrogate(s.charAt(n - 1)) && Character.isLowSurrogate(s.charAt(n))) n--;
        return s.substring(0, n);
    }

    /** The HUD's turn list: this turn's icon, the next one's (if known) and the road name, when one of them changed. */
    void sendHudList() {
        int type = displayType;
        if (type < 64) return;
        int next = 0, third = 0;
        if (aheadOk[0]) {
            next = aheadType(aheadEv[0], aheadSide[0]);
            // 셋째 칸(M-2): 순정은 아이콘을 3칸까지 채운다
            if (aheadOk[1]) third = aheadType(aheadEv[1], aheadSide[1]);
            hudFresh = true;
        } else if (hudFresh) {
            next = hudNext;
            third = hudThird;
        }
        String sr = shownRoad();
        String r = sr == null ? "" : sr;
        if (hudType == type && hudNext == next && hudThird == third && r.equals(hudRoad)) return;
        byte[] name;
        try {
            name = hudName(r, HUD_NAME_MAX).getBytes("UTF-16LE");
        } catch (java.io.UnsupportedEncodingException e) {
            return;
        }
        int n = name.length;
        byte[] f = new byte[n + 5];
        f[0] = HUD_LIST;
        f[1] = (byte) (1 + (next != 0 ? 1 : 0) + (third != 0 ? 1 : 0));
        f[2] = (byte) type;
        f[3] = (byte) next;
        f[4] = (byte) third;
        System.arraycopy(name, 0, f, 5, n);
        cluster.turnListNew(f);
        sent();
        hudType = type;
        hudNext = next;
        hudThird = third;
        hudRoad = r;
    }

    /**
     * The HUD's distance: whole number (up to 4095) and tenths for km/mi; under 1 km in metres, as the
     * stock navigation. The 2nd and 3rd points are HUD_NO_POINT: the turns ahead come at other times
     * and their distance isn't known to be from the car (1.7.1: not filled for Android Auto).
     */
    void sendHudDistance() {
        float d = distance;
        int u = unit;
        if (d < 0f || u < 0 || u > 4) return;
        int whole, tenths = 0;
        if (u == Cluster.UNIT_KM && d < 1f) {
            u = Cluster.UNIT_M;
            whole = (int) (d * 1000f + 0.5f);
        } else {
            whole = Math.min((int) d, 4095);
            if (u == Cluster.UNIT_KM || u == Cluster.UNIT_MI) tenths = Math.max(0, Math.min(9, (int) ((d - whole) * 10f + 0.5f)));
        }
        cluster.tbtSubInfo2C(u, tenths, whole, 0, 0, HUD_NO_POINT, 0, 0, HUD_NO_POINT);
        sent();
    }

    void hudBlank() {
        laneKey = "";
        crossroad = null;
        // 세 칸 모두 순정 지우기 값(단위 15, 소수 15, 정수 65535), 1.7.2(M-14)
        cluster.tbtSubInfo2C(HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT, HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT,
                HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT);
        byte[] lanes = new byte[81];
        lanes[0] = LANES;
        cluster.laneInfo(lanes);
        hudType = -1;
        hudNext = 0;
        hudThird = 0;
        hudFresh = false;
        hudRoad = null;
        laneCount = 0;
        // 빈 목록은 순정과 같은 {F2, 00}(M-2)
        cluster.turnListNew(new byte[] { HUD_LIST, 0 });
        cluster.turnListNew(new byte[] { HUD_LIST, 0 });
    }

    /** Clears the turn, the remaining trip, the destination and the HUD. */
    void release() {
        PhoneBar.send(context, barOut, PhoneBar.NONE, now());
        destName = "";
        sendDestName();
        destName = null;
        // 순정 무안내 값(M-14): 표시 8, 거리 무효 -3, 단위 15, 막대 무효 -1(2.3.0까지는 표시 0, -1, 단위 0)
        cluster.tbtInfo(CLEAR_ICON, 0, 0f, 0f, CLEAR_DIST_ERR, 0, CLEAR_UNIT, 0, BAR_FAR_ERR);
        // 남은 거리·시간도 순정 무효 값으로(리뷰 6, 카플레이 clearTurn과 같음)
        cluster.tbtSubInfo(0f, CLEAR_DIST_ERR, CLEAR_UNIT, 0, 0, CLEAR_TIME, CLEAR_TIME, 0f, -1, CLEAR_EST_POINT);
        // 도로(교차로) 이름도 지운다(2차 리뷰 4, 카플레이 clearTurn과 같음). 순정은 이 칸을 거의 보내지 않는다
        cluster.crossroadName("");
        hudBlank();
    }

    void sendDestName() {
        if (destName == null) return;
        // 포커스가 순정인 동안 이름은 기억만 한다(빈 이름 = 지우기는 release가 보낸다). 폰으로 돌아오면 focusPhone이 보낸다
        if (focusNative && destName.length() > 0) return;
        cluster.destinationName(0, destName);
        if (destName.length() > 0) sent();
    }

    // ---- lanes: "nn" lane count, then per lane 8 hex digits (type, then 3 arrow bytes)

    void sendLanes(String s) {
        if (s == null) s = "";
        int n = 0;
        if (s.length() >= 2) {
            n = hexByte(s, 0);
            if (n < 0 || n > 8) return;
        }
        byte[] f = new byte[81];
        f[0] = LANES;
        f[1] = (byte) n;
        for (int k = 0; k < n; k++) {
            int at = k * 8 + 2;
            int b = hexByte(s, at);
            if (b < 0) return;
            f[k + 3] = (byte) b;
            for (int j = 0; j < 3; j++) {
                b = hexByte(s, at + 2 + j * 2);
                if (b < 0) return;
                f[k * 3 + 19 + j] = (byte) b;
            }
        }
        // 차로 수는 포커스와 상관없이 기억한다: 1 이상이면 이 회전에 차로 안내가 있다(곧 회전 화면 판단, M-12)
        laneCount = n;
        // 재탐색 중에는 차로 프레임을 계기판에 보내지 않는다(표시 10 화면, 리뷰 7)
        if (focusNative || rerouting || s.equals(laneKey)) return;
        cluster.laneInfo(f);
        sent();
        laneKey = s;
    }

    static int hexByte(String s, int at) {
        if (at + 1 >= s.length()) return -1;
        int hi = hex(s.charAt(at)), lo = hex(s.charAt(at + 1));
        return hi < 0 || lo < 0 ? -1 : hi << 4 | lo;
    }

    static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return -1;
    }

    // ---- the turns ahead (cluster turn list)

    static int aheadType(int ev, int side) {
        switch (ev) {
            case STRAIGHT: case MERGE: return 65;
            case SLIGHT_TURN: return side == RIGHT ? 66 : side == LEFT ? 71 : 64;
            case TURN: return side == RIGHT ? 67 : side == LEFT ? 70 : 64;
            case SHARP_TURN: return side == RIGHT ? 68 : side == LEFT ? 69 : 64;
            case U_TURN: return side == RIGHT ? 73 : 72;
            default: return 64;
        }
    }

    void sendAhead(int n) {
        if (focusNative) return;
        int count = 0;
        for (int k = 0; k < n; k++) if (aheadOk[k]) count++;
        if (count == 0) return;
        int usm = Car.distanceUnit(context);
        Cluster.Turn[] list = new Cluster.Turn[count];
        int i = 0;
        for (int k = 0; k < n && i < count; k++) {
            if (!aheadOk[k]) continue;
            Cluster.Turn t = new Cluster.Turn();
            t.type = aheadType(aheadEv[k], aheadSide[k]);
            t.direction = Math.max(aheadAng[k], 0);
            t.traffic = 0;
            t.no = i + 1;
            t.name = aheadName[k] == null ? "" : aheadName[k];
            t.exitNumber = aheadExit[k] == 255 || aheadExit[k] < 0 ? "" : String.valueOf(aheadExit[k]);
            int m = aheadMeters[k], e3 = aheadE3[k], u = aheadUnit[k];
            int cu = Cluster.UNIT_M;
            float d = m;
            if (e3 >= 0) {
                if (u == KILOMETERS || u == KILOMETERS_P1) { cu = Cluster.UNIT_KM; d = e3 / 1000f; }
                else if (u == MILES || u == MILES_P1) { cu = Cluster.UNIT_MI; d = e3 / 1000f; }
                else if (u == FEET) { cu = Cluster.UNIT_FT; d = e3 / 1000f; }
                else if (u == YARDS) { cu = Cluster.UNIT_FT; d = e3 / 1000f * 3f; }
            }
            if (usm == Car.DISTANCE_KM) {
                if (m < 1000) { cu = Cluster.UNIT_M; d = m; }
                else { cu = Cluster.UNIT_KM; d = m / 1000f; }
            } else if (usm == Car.DISTANCE_MILES) {
                if (m < 161) { cu = Cluster.UNIT_FT; d = m * 3.28084f; }
                else { cu = Cluster.UNIT_MI; d = m / 1609f; }
            }
            t.distance = Math.max(0f, Math.min(65535f, d));
            t.unit = cu;
            list[i++] = t;
        }
        cluster.turnList(list);
        sent();
        if (aheadOk[0]) {
            hudNext = aheadType(aheadEv[0], aheadSide[0]);
            hudThird = aheadOk[1] ? aheadType(aheadEv[1], aheadSide[1]) : 0;
            hudFresh = true;
        }
        for (int k = 0; k < 3; k++) aheadOk[k] = false;
    }

    // ---- remaining distance and arrival time

    /** Sends the remaining trip when it changed or the owner changed units; then checks again every 250 ms. */
    void sendRemain() {
        if (!navLive || focusNative || tripMeters < 0) return;
        if (tripGen == sentGen) {
            int usm = Car.distanceUnit(context);
            if (usm == -1 || usm == lastUsm) return;
        }
        sentGen = tripGen;
        int m = tripMeters, u = tripUnit;
        int usm = Car.distanceUnit(context);
        lastUsm = usm;
        if (usm == Car.DISTANCE_KM) u = m < 1000 ? METERS : KILOMETERS;
        else if (usm == Car.DISTANCE_MILES) u = m < 161 ? FEET : MILES;
        int whole, tenths = 0, cu;
        if (u == KILOMETERS || u == KILOMETERS_P1 || (u != MILES && u != MILES_P1 && u != FEET && u != YARDS && m >= 1000)) {
            whole = m / 1000;
            tenths = m % 1000 / 100;
            cu = Cluster.UNIT_KM;
        } else if (u == MILES || u == MILES_P1) {
            int t = m * 10 / 1609;
            tenths = t % 10;
            whole = t / 10;
            cu = Cluster.UNIT_MI;
        } else if (u == FEET) {
            whole = m * 3281 / 1000;
            cu = Cluster.UNIT_FT;
        } else if (u == YARDS) {
            whole = m * 1094 / 1000;
            cu = Cluster.UNIT_YD;
        } else {
            whole = m;
            cu = Cluster.UNIT_M;
        }
        int hour = -1, minute = -1, timeType = -1, timeFormat = -1;
        int s = tripSeconds;
        Calendar c = null;
        if (s >= 500000000) {   // a clock time (seconds since 1970)
            c = Calendar.getInstance();
            c.setTimeInMillis(s * 1000L);
        } else if (s >= 0 && s <= 604800) {   // seconds from now
            c = Calendar.getInstance();
            c.add(Calendar.SECOND, s);
        }
        if (c != null) {
            hour = c.get(Calendar.HOUR_OF_DAY);
            minute = c.get(Calendar.MINUTE);
            timeType = 1;
            timeFormat = context == null || DateFormat.is24HourFormat(context) ? 2 : 1;
        }
        cluster.tbtSubInfo(whole + tenths / 10f, 0, cu, hour, minute, timeType, timeFormat, 0f, -1, estPoint);
        sent();
        scheduleTick();
    }

    void scheduleTick() {
        if (tripMeters < 0) return;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 250);
    }

    void unitTick() {
        if (tripMeters < 0) return;
        sendRemain();
        scheduleTick();
    }
}
