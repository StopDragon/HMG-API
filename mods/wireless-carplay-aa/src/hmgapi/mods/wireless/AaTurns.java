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

// Korea Projection 1.7.4(2026-11-12, 모드 2.4.2, 소유자 결정 "순정과 똑같이", 계획 D-15 방안 A):
//   회전 프레임을 순정 GPM(GpmNaviTBTManager, 니로 260902 odex)과 같게 보낸다. 지운 것: 곧 회전 화면(13·31·17·18·25)·
//   옆길 비트·막대와 막대 방송(PhoneBar), 350m·1050m 경계 여유, 이름 바뀜 붙잡기(A-2)와 설정 aa_name_hold, 재탐색 표시 10과
//   재탐색 끝의 다시 보내기(T-6 ①이 함께 없어짐), 교차로 이름의 "곧 회전일 때만"·10초 묶기, 차 설정 단위로 바꾸기.
//   인자는 순정대로 옆길 65535·방향(순정 각도 값)·막대 32·오류 0, 거리 음수면 아이콘과 상관없이 2·막대 254·오류 -2.
//   회전교차로는 side가 오른쪽이 아니면 순정 B 계열. 위 옛 수정 기록 가운데 이 항목들은 이제 맞지 않는다.
//   판단을 기다리며 그대로 둔 것: 안내 상태(navLive)와 안내 끝·포커스 순정 지우기(release), HUD 목록·2C·차로·남은 거리·
//   다음 회전 목록·라이브러리 목적지 이름, 단속 정보 비우기를 보내지 않음(drafts/1.7.4-work/mod.md)

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
 * 1.7.4: 회전·거리·교차로 이름·첫 출발 목적지 이름은 순정 GpmNaviTBTManager와 같은 때, 같은 값으로 보낸다:
 *   - 회전 이벤트(이름 바뀜·그다음 출발·회전·램프·회전교차로): 교차로 이름(그 이벤트의 도로) → 아이콘 → updateTBTInfo
 *   - 첫 출발(앞 도로 없음): 목적지 이름(도로) → 거리 -1 → updateTBTInfo(아이콘 2)
 *   - 목적지 3, 모름 64: updateTBTInfo만. ROUNDABOUT_ENTER·FERRY_TRAIN·표 밖 이벤트 번호: 아무것도 안 보냄
 *   - 거리: 마지막 이벤트로 아이콘을 다시 정하고 updateTBTInfo(이름은 안 보냄)
 *   - 표에 없는 방향(예: 갈림길 방향 모름, 램프의 방향)은 앞 값을 그대로 쓴다
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
    /** the time (ms since boot); tests set clock */
    long clock = -1L;
    /** the 2C value for a point there isn't, and for guidance over (the stock navigation's) */
    static final int HUD_NO_POINT = 65535;
    /**
     * 안내 끝 지우기 값(M-14, 1.7.4에서도 그대로: 순정 GPM은 운영 상태 2로 계기판을 넘기지만 우리는 운영 상태를
     * 보내지 않으므로 이 지우기가 그 대신이다). 표시 8, 거리 무효 -3, 단위 15, 막대 무효(barErr -1).
     * 안내 끝 2C 한 칸은 단위 15, 소수 15, 정수 65535(순정 setClusterClearData).
     */
    static final int CLEAR_ICON = 8, CLEAR_DIST_ERR = -3, CLEAR_UNIT = 15, CLEAR_BAR_ERR = -1, HUD_CLEAR_UNIT = 15, HUD_CLEAR_TENTHS = 15;
    /**
     * 남은 거리·시간(updateTBTSubInfo) 지우기 값: 순정 엔진은 0xFF·거리 0xFFFF를 넣고, 서비스가 범위 밖 값을
     * 시간 종류·형식 3, 단위 15로 바꾼다. 도착 지점 종류는 9인자판 기본값 15(없음)를 쓴다.
     */
    static final int CLEAR_TIME = 3, CLEAR_EST_POINT = 15;
    /**
     * 순정 GpmNaviTBTManager.updateTBTInfo의 인자: (표시, 옆길 65535, 방향, 거리, 0, 0, 단위, 막대 32, 막대 오류 0).
     * 거리가 음수면 표시 필드를 2로 덮고 막대 254·막대 오류 -2(아이콘과 상관없이).
     */
    static final int SIDE_STREET = 65535, BAR = 32, BAR_NO_DIST = 254, BAR_ERR_NO_DIST = -2, ICON_NO_DIST = 2;
    /** AA NavigationStatus: 1 안내 중, 3 재탐색(둘 다 "안내 중"으로 본다. 순정 onStatus는 빈 메서드) */
    static final int STATUS_ACTIVE = 1, STATUS_REROUTING = 3;
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
    /** 타이머 예외를 한 번만 남긴다 */
    boolean timerFailLogged;

    // the next turn (순정 GpmNaviTBTManager의 mEvent·mTurnSide·mTurnAngle·mTurnNumber·mDisplayType·mDirection·mUnit·mRoad·mChangedDistance)
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

    /** 타이머 안 예외: 한 번만 남기고 넘어간다 */
    void timerFailed(Throwable t) {
        if (timerFailLogged || log == null) return;
        timerFailLogged = true;
        log.line("AA: timer error " + t.getClass().getSimpleName());
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
        setTurn(str, side, ev, angle, number);
    }

    /**
     * 순정 onNextTurn + 핸들러(a.handleMessage): 받은 회전을 기억하고 그 갈래대로 바로 보낸다. 표 밖 이벤트 번호와
     * ROUNDABOUT_ENTER·FERRY_TRAIN은 기억만 하고(순정도 mEvent 등은 바꿈) 아무것도 보내지 않는다.
     */
    void setTurn(String str, int side, int ev, int angle, int number) {
        hudFresh = false;
        boolean first = road == null;
        event = ev;
        road = str;
        turnSide = side;
        turnAngle = angle;
        turnNumber = number;
        switch (ev) {
            case DEPART:
                if (first) {   // 순정 what 0: 목적지 이름 → 거리 -1 → 회전
                    if (!focusNative) {   // 2.4.1과 같은 조건(안내 상태와 무관)
                        cluster.destinationName(0, road);
                        sent();
                    }
                    distance = -1f;
                    sendTurn();
                    return;
                }
                break;   // 순정 what 1: 이름 → setTurnDisplayType(출발: 바꾸지 않음) → 회전
            case NAME_CHANGE: case SLIGHT_TURN: case TURN: case SHARP_TURN: case STRAIGHT: case MERGE:
                break;
            case U_TURN:
                if (side == UNSPECIFIED) turnSide = LEFT;   // 순정도 방향 모름은 LEFT로 바꾼다
                break;
            case ON_RAMP: case OFF_RAMP: case FORK: case FERRY_BOAT:
                sendCrossroad(road);
                setRampType(ev, turnSide);
                sendTurn();
                return;
            case ROUNDABOUT_EXIT: case ROUNDABOUT_ENTER_AND_EXIT:
                sendCrossroad(road);
                setRoundaboutType(ev, turnSide, turnAngle);
                sendTurn();
                return;
            case DESTINATION:
                displayType = 3;
                sendTurn();
                return;
            case UNKNOWN:
                displayType = 64;
                sendTurn();
                return;
            default:
                return;   // ROUNDABOUT_ENTER, FERRY_TRAIN, 표 밖 이벤트 번호: 순정도 계기판에 보내지 않음
        }
        sendCrossroad(road);
        setTurnType(ev, turnSide);
        sendTurn();
    }

    /** 교차로 이름(순정 updateTPCrossroadName): 회전 이벤트마다 그 이벤트의 도로 이름 그대로(묶지 않음) */
    void sendCrossroad(String name) {
        if (!navLive || focusNative) return;
        cluster.crossroadName(name);
        sent();
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
        // 순정 what 3: 거리 → 마지막 이벤트로 setTurnDisplayType 다시 → 회전(이름 없음)
        setDistance(meters, e3, unitFlags & 0xff);
        setTurnType(event, turnSide);
        sendTurn();
    }

    void status(int s) {
        if (s == STATUS_ACTIVE || s == STATUS_REROUTING) {   // 안내 중(재탐색 포함). 순정은 상태를 쓰지 않는다
            navLive = true;
            return;
        }
        handler.removeCallbacks(tick);
        destName = null;
        navLive = false;
        tripMeters = -1;
        tripSeconds = -1;
        estPoint = 0;
        // 포커스가 순정이면 계기판은 순정 것이다: 그동안 보낸 것이 없으므로 지우지 않는다(2차 리뷰 3)
        if (!clearedByStop && !focusNative) release();
        clearedByStop = false;
        // 경로가 끝났다: 지우기와 짝으로 회전 기억도 비운다(stop()과 같은 값, 거리도). 다음 경로의 출발이 "첫 출발"
        // (목적지 이름 + 아이콘 2)로 처리되고 앞 경로의 표시가 나가지 않는다(리뷰 반영 2026-11-12)
        forgetTurn();
        distance = 0f;
    }

    /** 회전 기억을 비운다(순정 clearMessage: mEvent·mRoad·mTurnSide·mTurnAngle·mTurnNumber·mUnit·mDisplayType·mDirection) */
    void forgetTurn() {
        event = 0;
        road = null;
        turnSide = 0;
        turnAngle = 0;
        turnNumber = 0;
        unit = 0;
        displayType = 0;
        direction = 0;
    }

    /**
     * The car's navigation took over (focus native): forget the turn (순정 stop()의 clearMessage와 같이 기억을 비운다).
     * 1.7.2(M-14): 안내 중이었으면 계기판도 지운다(release, 순정은 운영 상태 2). 경로 자체는 폰에서 이어지므로
     * navLive는 그대로 두어, 포커스가 폰으로 돌아오면 다음 회전부터 다시 보낸다.
     */
    void stop() {
        boolean was = focusNative;
        focusNative = true;
        if (!was && navLive && !clearedByStop) {
            // 포커스 신호가 다시 와도(그 사이 보낸 것이 없으면) 다시 지우지 않는다(리뷰 1)
            // 남은 거리 확인(250ms)도 멈춘다. 폰이 남은 거리를 다시 보내면 sendRemain이 다시 건다.
            // release가 destName을 비우므로, 폰이 같은 목적지 이름을 다시 보내면 그대로 다시 나간다.
            handler.removeCallbacks(tick);
            release();
            clearedByStop = true;
        }
        forgetTurn();
    }

    /** The connection ended: clear what we showed, if a route was on. */
    void end() {
        handler.removeCallbacks(tick);
        if (navLive) status(2);
    }

    // ---- the next turn

    /** 순정 GpmNaviTBTManager.updateTBTInfo와 같은 인자로 보낸다 */
    void sendTurn() {
        // 순정: 거리가 음수면 표시 필드 자체를 2로 덮는다(다음 회전 이벤트가 다시 정할 때까지). 보내지 않을 때도 덮는다
        if (distance < 0f) displayType = ICON_NO_DIST;
        if (!navLive || focusNative) return;
        int bar = BAR, barErr = 0;
        if (distance < 0f) {
            bar = BAR_NO_DIST;
            barErr = BAR_ERR_NO_DIST;
        }
        logTurn();
        cluster.tbtInfo(displayType, SIDE_STREET, direction, distance, 0, 0, unit, bar, barErr);
        sent();
        sendHudList();
        sendHudDistance();
    }

    /** the turn's values (numbers only: no road names) when they change, up to TURN_LOGS_MAX lines */
    void logTurn() {
        if (log == null) return;
        String k = event + " " + turnSide + " " + turnAngle + " " + displayType;
        if (k.equals(turnKey) || turnLogs >= TURN_LOGS_MAX) return;
        turnKey = k;
        turnLogs++;
        log.line("AA: turn ev " + event + " side " + turnSide + " angle " + turnAngle + " -> icon " + displayType);
    }

    /**
     * 거리(순정 setDistanceValue): 폰 단위 그대로. 미터는 1000 이상이면 km(단위 1), 아니면 m(단위 0). km·km 소수 1,
     * 마일·마일 소수 2, 피트 3, 야드 4는 e3/1000. 그 밖 단위 15.
     */
    void setDistance(int meters, int e3, int u) {
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

    /** 순정 setTurnDisplayType: 표에 없는 이벤트(출발·램프·회전교차로 등)는 표시·방향을 바꾸지 않는다 */
    void setTurnType(int ev, int side) {
        switch (ev) {
            case STRAIGHT: case MERGE:
                displayType = 65;
                direction = 0;
                break;
            case SLIGHT_TURN:
                set(side, 66, 45, 71, 315);
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
                break;   // ramps, roundabouts, destination, depart: set by their own (or kept)
        }
    }

    /** right type/direction, left type/direction, otherwise 64 (unknown) and direction 0 */
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

    /**
     * 순정 setRampDisplayType: 진입 161/162/160, 진출 164/165/163(방향은 바꾸지 않음), 갈림길 173/174(방향 모름은
     * 표시를 바꾸지 않음) 방향 0, 페리 176 방향 0
     */
    void setRampType(int ev, int side) {
        if (ev == ON_RAMP) {
            displayType = side == RIGHT ? 161 : side == LEFT ? 162 : 160;
        } else if (ev == OFF_RAMP) {
            displayType = side == RIGHT ? 164 : side == LEFT ? 165 : 163;
        } else if (ev == FORK) {
            if (side == RIGHT) displayType = 173;
            else if (side == LEFT) displayType = 174;
            direction = 0;
        } else if (ev == FERRY_BOAT) {
            displayType = 176;
            direction = 0;
        }
    }

    /**
     * 순정 setRoundAboutDisplayType: 출구 각도 띠로 고르고, side가 오른쪽이면 A 계열(96~106), 아니면 B 계열(192~202).
     * 회전교차로 나가기(12)는 오른쪽 32·왼쪽 33(방향은 바꾸지 않음), 방향 모름 64·방향 0.
     */
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
        boolean r = side == RIGHT;
        if (a == 180) { displayType = r ? 96 : 192; direction = 0; }
        else if (a >= 135 && a < 180) { displayType = r ? 97 : 193; direction = 45; }
        else if (a >= 90 && a < 135) { displayType = r ? 99 : 195; direction = 90; }
        else if (a >= 45 && a < 90) { displayType = r ? 100 : 196; direction = 135; }
        else if ((a >= 0 && a < 45) || (a > 315 && a <= 360)) { displayType = r ? 102 : 198; direction = 180; }
        else if (a > 270 && a <= 315) { displayType = r ? 103 : 199; direction = 225; }
        else if (a > 225 && a <= 270) { displayType = r ? 105 : 201; direction = 270; }
        else if (a > 180 && a <= 225) { displayType = r ? 106 : 202; direction = 315; }
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
        String r = road == null ? "" : road;
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
        // 빈 목록은 순정과 같은 {F2, 00}(M-2)
        cluster.turnListNew(new byte[] { HUD_LIST, 0 });
        cluster.turnListNew(new byte[] { HUD_LIST, 0 });
    }

    /** Clears the turn, the remaining trip, the destination and the HUD. */
    void release() {
        destName = "";
        sendDestName();
        destName = null;
        // 순정 무안내 값(M-14): 표시 8, 거리 무효 -3, 단위 15, 막대 무효 -1(2.3.0까지는 표시 0, -1, 단위 0)
        cluster.tbtInfo(CLEAR_ICON, 0, 0f, 0f, CLEAR_DIST_ERR, 0, CLEAR_UNIT, 0, CLEAR_BAR_ERR);
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
        if (focusNative || s.equals(laneKey)) return;
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
