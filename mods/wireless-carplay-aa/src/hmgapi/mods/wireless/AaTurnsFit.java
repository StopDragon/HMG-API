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

// Korea Projection 1.7.4(2026-11-15, 모드 2.4.3, 계획 D-20, 소유자 결정 "2.4.1을 전체 다 살리자"): 2.4.1의 AaTurns를 보정 층
//   AaTurnsFit으로 되살렸다(설정 tbt_fit 켬·기본, 끄면 Wireless가 2.4.2의 AaTurns를 쓴다). 2.4.2에서 얻은 것은 지켰다:
//   - 순정 표가 받는 입력은 순정대로: 회전교차로는 side가 오른쪽이 아니면 B 계열(192~202), 거리가 아직 없으면(음수) 아이콘과
//     상관없이 표시 2(보내지 않을 때도 필드를 덮음)
//   - 안내가 끝나면(상태 1·3 아님) 회전 기억을 비운다(다음 경로의 출발이 첫 출발로 나감)
//   - D를 고정하지 않는다(소유자 결정 2026-11-15): 이 회전이 안내되기 시작할 때의 남은 거리가 350m·1050m보다 짧으면 그 거리를 D로
//   - 재탐색 뒤 옛 회전(T-6 ①): drafts/1.7.5-reroute.patch의 고침을 넣었다(turnInReroute·distStale·rerouteDue). 4차 리뷰 중간
//     (거리 낡음 중 첫 출발이 아이콘 0)은 "진짜 거리 없음(distance < 0)만 표시 2로 덮기"로 고쳤다
// Korea Projection 1.7.4 베타 3(2026-11-15, 모드 2.4.4): 큰 그림 옆길 비트의 왼쪽 절반을 순정 값에 맞췄다(좌회전 칸 36 = 비트 11,
//   급좌 30 = 9, 약간 좌 42 = 13, 회전교차로 출구도 같은 식. 전에는 칸÷3). 카플레이 CpTurnsFit.streetBit와 같은 규칙

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
final class AaTurnsFit implements AaTbt {
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
    /** where the turn log lines go (Wireless: the mod's log); null: none. 줄 받는 곳은 AaTurns.Sink와 같다 */
    AaTurns.Sink log;
    /** turn log: the last values logged and how many lines so far */
    String turnKey;
    int turnLogs;
    static final int TURN_LOGS_MAX = 300;
    /**
     * 2.4.10(1.8.1 I-4 준비, drafts/1.8.1-work/tbt-inv.md 1-3절): 디버그 로그가 켜졌을 때만 받은 다음 회전 거리(m)를 숫자로 남기는 곳.
     * null이면 남기지 않는다(Wireless가 디버그일 때만 넣음). 회전 줄과 따로 센다(Wireless.aaDiagLogs, 프로세스당 DIAG_LOGS_MAX줄)
     */
    AaTurns.Sink diag;
    static final int DIAG_LOGS_MAX = 1200;
    /** 거리 진단 줄을 남기는 문턱: 회전 이벤트 뒤 첫 거리, PASS_WOBBLE 넘게 오름, 마지막으로 남긴 값보다 이만큼(m) 이상 줄어듦 */
    static final int DIAG_DROP_STEP = 50;
    /** 마지막으로 남긴 거리(m, 없으면 -1): 줄어듦 문턱(DIAG_DROP_STEP)에만 쓴다 */
    int diagLast = -1;
    /** 마지막으로 받은 거리(m, 없으면 -1): 오름 판단과 줄의 prev(회전 지남 판단 passed와 같은 기준) */
    int diagPrev = -1;
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
    static final int NEAR_TURN = 13, NEAR_UTURN = 31, NEAR_EXIT_RIGHT = 17, NEAR_EXIT_LEFT = 18;
    /**
     * 2.4.10(1.8.1, 카플레이 CpTurnsFit과 같은 값. 패키지가 달라 여기에 그대로 둔다): 회전교차로 곧 회전 32(L-1, 순정 니로 PHEV 11-25 표본 1개),
     * 왼쪽 고가 44·지하차도 46(T1-1, 순정 운행 지하차도 4번·왼쪽 고가 2번, 옆길 0, 방향 90/270도)
     */
    static final int NEAR_ROUNDABOUT_R = 32, NEAR_OVERPASS_L = 44, NEAR_UNDERPASS = 46;
    /**
     * 2.4.10(#572, 소유자 승인 2026-11-25: 1.7.4 D-16 "순정 GPM대로 RIGHT만 A 계열"을 보정 층에서만 고쳐 씀. 순정 층 AaTurns는 D-16 그대로).
     * 참이면 회전교차로 side를 "도는 방향"으로 읽는다: LEFT(반시계 = 오른쪽 통행)·모름은 A 계열(96~107), RIGHT(시계 = 왼쪽 통행)는 B 계열(192~202).
     * 까닭: 한국 폰은 오른쪽 통행인데도 회전교차로에 side 1(LEFT)을 보낸다(#583·#79 리포트 6줄 모두). 각도 표(90 오른쪽, 270 왼쪽)도 반시계
     * 기준이다. 순정 규칙대로면 거울 그림(B)이 나가 #572에서 HUD 그림이 반대로 보였다.
     * **그래서 RIGHT를 보내는 앱은 2.4.9와 달리 B 계열이 된다.** RIGHT는 시계 방향(왼쪽 통행)이라는 뜻으로 읽으므로 그 앱에는 왼쪽 통행 그림이
     * 맞다고 본다. 한국 길에서 RIGHT를 보내는 앱은 기록에 없다(확인 안 함, 차 확인 목록).
     * L-1 곧 회전 32는 A 계열에서만 나가므로 거짓으로 되돌리면 한국 폰에서 32도 나가지 않는다.
     * 되돌릴 때 함께 바뀌는 시험: aaturnsfit "2.4.10 #572 …"·"2.4.10 L-1: Korean …" 항목
     */
    static final boolean ROUNDABOUT_LEFT_IS_A = true;
    static final String[] UNDERPASS_WORDS = { "지하차도", "지하도로" }, OVERPASS_WORDS = { "고가차도", "고가도로" };
    /**
     * 시설로 들어가지 않는 뜻일 수 있는 낱말(CpTurnsFit.FACILITY_NOT_WORDS와 같음, "방면"을 넣은 것은 소유자 결정 2026-11-25: 차 확인 뒤 다시 정함).
     * AA는 안내 문구 없이 도로 이름만 오므로 부정 낱말은 대개 걸리지 않는다
     */
    static final String[] FACILITY_NOT_WORDS = { "옆길", "측도", "아래", "밑", "하부", "진입하지", "하지 마", "금지", "지나", "나와", "통과 후", "방면",
            "사거리", "삼거리", "오거리", "교차로", "입구", "앞" };
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
    /** 1.7.4(2.4.3): 이 회전이 안내되기 시작한 뒤 본 가장 긴 남은 거리(m, D 계산, dynamicD) */
    int nearFirst;
    /**
     * 1.7.4(2.4.3, 소유자 결정 2026-11-15 "D값을 고정하지 않기"): 이 회전의 D = max(D_MIN, min(기준 D, nearFirst)).
     * nearFirst는 이 회전이 안내되기 시작한 뒤(회전이 바뀐 뒤 처음 받은 유효한 남은 거리) 본 가장 긴 남은 거리다.
     * 그래서 앞 회전 직후 100m 앞 회전은 D=100m(막대가 가득 찬 채 시작), 처음 거리가 기준보다 길면 기준 D(350m·1050m)에서 들어간다.
     * 거리가 도로 늘면(차가 되돌아감·경로 바뀜) 그만큼 D도 늘고(기준까지), 재탐색·새 경로·다른 회전이면 처음부터 다시 잡는다.
     * 큰 그림 진입·경계 여유·막대가 모두 같은 D를 쓴다.
     * 순정 내비와 다른 점: 순정 내비는 D를 줄이지 않는다(차 로그 2026-11-09 15:29:36 바로 다음 회전이 210m에서 막대 60,
     * drafts/1.7.4-work/mod.md 2026-11-15 절). 소유자가 그 근거를 보고 이렇게 정했다.
     * D_MIN: 아주 짧은 거리(예: 10m)에서 D가 그 거리로 줄어 막대가 90에서 바로 10으로 떨어지지 않게 두는 바닥. 값 30m는 순정 교차로
     * 확대 안내 단계 1(NOW, road_guidance_config.bin, FINDINGS "D 값 확정")이다.
     */
    static final int D_MIN = 30;
    /** 2.4.3 리뷰 중간 1(B): 남은 거리가 D_MIN 이하였다가 이만큼 넘게 늘면 회전을 지난 것(m). 회전 바로 앞 흔들림(약 10m)보다 크게 */
    static final int NEAR_PASS_RISE = 20;

    /** 이 회전의 D(m): 위 규칙 */
    static int dynamicD(int base, int first) {
        return Math.max(D_MIN, Math.min(base, first));
    }

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
    /**
     * 2.4.10(1.8.1 I-4 안 A, 소유자 결정 11-25, #603): 이번 회전에서 거리로 "같은 지점"이라고 판단해 붙잡았던 이름 바뀜의 도로 이름과
     * 그때 회전의 열쇠(종류·방향·각도). 폰이 같은 회전 → 같은 이름 바뀜을 몇 초마다 되풀이하면, 실제 회전 이벤트가 붙잡기를 풀고 다음
     * 이름 바뀜은 거리 없이 2초 기한(PEND_WAIT_MS)을 맞아 풀려 65가 깜빡였다. 이미 같은 지점으로 판단한 것과 같은 이름 바뀜이면 기한에서
     * 풀지 않고 다시 붙잡는다. releaseName·forgetName, 그리고 열쇠가 다른 실제 회전이 오면 잊는다. NAME_HOLD_MS와 "거리가 늘면 풂"은 그대로다
     */
    String heldName, heldKey;
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
    /**
     * T-6 ①(1.7.4): 이번 재탐색이 시작된 뒤 폰이 회전을 보냈다(새 경로의 회전). 재탐색이 끝나는 순간 이것이 거짓이면
     * 기억한 회전을 옛 거리로 다시 보내지 않는다(2.3.3~2.4.1은 옛 거리로 보냈음). 폰이 다음 거리를 주면 그 거리와 함께 나간다.
     * 폰이 REROUTE_WAIT_MS 안에 회전도 거리도 주지 않으면 rerouteDue가 그 회전을 거리 없이 한 번 보낸다.
     */
    boolean turnInReroute;
    /**
     * T-6 ①(재리뷰 중간 1): 기억한 거리(distance·unit)는 옛 경로 것이다. 재탐색이 시작될 때 세우고, 재탐색이 끝난 뒤 폰이 준
     * 거리(setDistance)에서만 내린다. 재탐색 중에 온 거리는 새 경로 것인지 알 수 없어 내리지 않는다. 서 있는 동안 sendTurn은
     * "거리가 아직 없는 회전" 갈래(distance < 0과 같은 꼴)로 보내고 HUD 거리(2C)는 보내지 않는다. 안내 끝·연결 끝에서 거짓
     */
    boolean distStale;
    /** T-6 ①: 지금 경로(안내 시작 또는 재탐색 이후)에서 폰이 회전을 보냈다. 안내 끝·포커스 순정에서 거짓 */
    boolean turnInRoute;
    /** T-6 ①(리뷰 중간 1): 재탐색 끝 뒤 폰이 아무것도 다시 주지 않을 때 기다리는 시간(ms, 오케스트레이터 결정) */
    static final long REROUTE_WAIT_MS = 3000L;
    /** T-6 ①: 재탐색 끝 뒤 한 번짜리 기한. 새 회전·거리·새 재탐색·안내 끝·연결 끝·포커스 바뀜에서 풀린다 */
    final Runnable rerouteDue = new Runnable() {
        @Override
        public void run() {
            try {
                onRerouteDue();
            } catch (Throwable t) {
                timerFailed(t);
            }
        }
    };

    /** 재탐색 끝 기한을 푼다(걸려 있지 않아도 됨) */
    void dropRerouteDue() {
        handler.removeCallbacks(rerouteDue);
    }

    /**
     * 재탐색이 끝나고 REROUTE_WAIT_MS 동안 폰이 회전도 거리도 다시 주지 않았다: 폰 기준으로 회전은 그대로이므로 그 회전을
     * 한 번 보낸다. distStale이 서 있으므로 sendTurn이 "거리가 아직 없는 회전" 갈래(2.4.x에서 이미 차에 나간 꼴)로 보낸다
     * (재리뷰 낮음: 새 프레임 꼴을 만들지 않음). 옛 경로 거리는 tbt·HUD 어디에도 싣지 않는다
     */
    void onRerouteDue() {
        if (!navLive || focusNative || rerouting || !turnInRoute || samePend || namePend) return;
        if (event == 0 && displayType == 0) return;
        if (log != null) log.line("AA: no turn or distance " + REROUTE_WAIT_MS / 1000L + " s after rerouting, turn resent without distance");
        sendTurn();
    }
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
    public void focusPhone() {
        if (!focusNative) return;
        focusNative = false;
        dropRerouteDue();
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

    /**
     * 연결 하나의 AA 길안내 관리자(Wireless가 부른다). 설정 tbt_fit이 켜졌으면 보정 층(AaTurnsFit, aa_name_hold 적용),
     * 꺼졌으면 순정 층(AaTurns, 2.4.2와 같음). 2.4.3 리뷰 낮음 5: 고르는 곳을 한 곳에 두어 시험한다
     */
    static AaTbt make(boolean fit, boolean nameHold, Context context, Cluster cluster, Handler handler, AaTurns.Sink log) {
        if (fit) {
            AaTurnsFit f = new AaTurnsFit(context, cluster, handler);
            f.nameHold = nameHold;
            f.log = log;
            return f;
        }
        AaTurns s = new AaTurns(context, cluster, handler);
        s.log = log;
        return s;
    }

    AaTurnsFit(Context context, Cluster cluster, Handler handler) {
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

    public void nextTurn(String str, int side, int ev, int angle, int number) {
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
        dropRerouteDue();   // T-6 ①: 폰이 회전을 줬다
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
            // 2.4.10(I-4 안 A): 다른 회전이면 "같은 지점으로 판단한 이름 바뀜"도 잊는다(같은 회전이 되풀이되면 기억해 둠)
            if (heldKey != null && !heldKey.equals(ev + " " + side + " " + angle)) {
                heldName = null;
                heldKey = null;
            }
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
        if (namePend && heldAgain()) {
            // 2.4.10(I-4 안 A): 이번 회전에서 이미 같은 지점으로 판단한 이름 바뀜이 되풀이됨: 풀지 않고 다시 붙잡는다(아이콘 그대로 이름만)
            namePend = false;
            nameHeld = true;
            holdLast = remainMeters;
            send = true;
        } else if (namePend) {
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

    /** 2.4.10(I-4 안 A): 기다리는 이름 바뀜이 이번 회전에서 같은 지점으로 판단해 붙잡았던 것과 같고, 회전도 같고, 붙잡기 한도 안이다 */
    boolean heldAgain() {
        return heldName != null && heldName.equals(nameRoad) && heldKey != null && heldKey.equals(event + " " + turnSide + " " + turnAngle)
                && !turnPassed && turnAt != 0L && now() - turnAt < NAME_HOLD_MS;
    }

    /** 같은 꼴 다음 회전으로 본다(거리를 모름): 곧 회전·히스테리시스를 새로 */
    private void newTurnNoDistance() {
        remainMeters = -1;
        nearOn = false;
        nearLast = -1;
        // 2.4.3 리뷰 중간 1(A): 같은 꼴 다음 회전이다. 열쇠도 비워 near()가 다음 거리로 처음 거리(D)를 다시 잡게 한다
        nearKey = null;
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
        heldName = null;
        heldKey = null;
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
        heldName = null;
        heldKey = null;
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
        if (rerouting) turnInReroute = true;   // T-6 ①: 새 경로의 회전
        turnInRoute = true;
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

    public void nextTurnDistance(int meters, int seconds, int e3, int unitFlags) {
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
        dropRerouteDue();   // T-6 ①: 폰이 거리를 줬다(그 거리와 함께 나간다)
        diagDistance(meters, turnSinceDist);
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
                    heldName = nameRoad;   // 2.4.10(I-4 안 A): 되풀이되면 기한에서 다시 붙잡도록 기억
                    heldKey = event + " " + turnSide + " " + turnAngle;
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

    public void status(int s) {
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
                turnInReroute = false;   // T-6 ①: 지금 기억한 회전은 옛 경로 것
                distStale = true;   // T-6 ①(재리뷰 중간 1): 기억한 거리도 옛 경로 것
                // 3차 리뷰 낮음 2: 앞쪽 회전(목록·HUD 다음 칸)도 옛 경로 것이다. 재탐색 뒤 폰이 새로 준 것만 나가게 지운다
                for (int k = 0; k < 3; k++) aheadOk[k] = false;
                dropRerouteDue();
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
                // T-6 ①(1.7.4): 재탐색 중에 폰이 보낸 회전이 없으면 이 순간 보내지 않는다. 폰이 다음 거리를 주면 그 거리와 함께
                // 나간다. 아무것도 안 오면 REROUTE_WAIT_MS 뒤 rerouteDue가 거리 없이 한 번 보낸다(리뷰 중간 1). 회전이 왔으면
                // 바로 보내되 distStale이라 옛 거리는 싣지 않는다(재리뷰 중간 1)
                if (!turnInReroute) {
                    dropRerouteDue();
                    if (turnInRoute) {
                        handler.postDelayed(rerouteDue, REROUTE_WAIT_MS);
                        if (log != null) log.line("AA: rerouting over, waiting for the phone's next distance");
                    }
                } else if ((event != 0 || displayType != 0) && !samePend && !namePend) sendTurn();
                turnInReroute = false;
            }
            return;
        }
        rerouting = false;
        handler.removeCallbacks(tick);
        dropRerouteDue();
        turnInRoute = false;
        distStale = false;
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
        // 2.4.2에서 얻은 것: 경로가 끝나면 지우기와 짝으로 회전 기억도 비운다(stop()과 같은 값, 거리도).
        // 다음 경로의 출발이 첫 출발(목적지 이름 + 아이콘 2)로 나가고 앞 경로의 표시가 나가지 않는다
        event = 0;
        road = null;
        turnSide = 0;
        turnAngle = 0;
        turnNumber = 0;
        unit = 0;
        displayType = 0;
        direction = 0;
        distance = 0f;
    }

    /**
     * The car's navigation took over (focus native): forget the turn. 1.7.2(M-14): 안내 중이었으면 계기판도
     * 지운다(release). 지우지 않으면 순정 엔진이 "바뀔 때만" 보내므로 폰의 마지막 회전이 남는다. 경로 자체는
     * 폰에서 이어지므로 navLive는 그대로 두어, 포커스가 폰으로 돌아오면 다음 회전부터 다시 보낸다.
     */
    public void stop() {
        // 막대 칸은 순정 값으로 되돌린다(포커스가 순정으로 넘어감)
        PhoneBar.send(context, barOut, PhoneBar.NONE, now());
        boolean was = focusNative;
        focusNative = true;
        dropRerouteDue();
        turnInRoute = false;
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
    public void end() {
        handler.removeCallbacks(tick);
        dropRerouteDue();
        distStale = false;
        forgetName();
        nameHoldLogs = 0;
        PhoneBar.send(context, barOut, PhoneBar.NONE, now());
        if (navLive) status(2);
    }

    // ---- the next turn

    void sendTurn() {
        // 순정: 거리가 아직 없으면(음수) 표시 필드 자체를 2로 덮는다(아이콘과 상관없이, 보내지 않을 때도). 2.4.2에서 순정대로 바로잡음.
        // 거리 낡음(distStale, 재탐색 뒤)은 진짜 거리 없음이 아니라 덮지 않는다(폰이 준 아이콘 그대로, 3차 리뷰 중간 1).
        // 4차 리뷰 중간: 낡음 중 첫 출발은 distance가 -1이라 여기서 2가 된다(전에는 0이 나갔음)
        if (distance < 0f) displayType = 2;
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
        // T-6 ①(재리뷰 중간 1·낮음): 거리가 옛 경로 것이면(distStale) 거리가 아직 없는 회전과 같은 갈래로 보낸다(-1)
        float d = distStale ? -1f : distance;
        if (d < 0f) {
            bar = 254;
            barErr = -2;
        }
        int[] near = d < 0f ? null : near();
        logTurn(near);
        // 도로 이름은 곧 회전일 때만(순정 sendTurnNameData: 멀리 있으면 빈 이름)
        sendCrossroad(near != null ? shownRoad() : "");
        if (near != null) cluster.tbtInfo(near[0], near[1], near[2] * 7.5f, d, 0, 0, unit, near[3], 0);
        else cluster.tbtInfo(displayType, 0, 0f, d, 0, 0, unit, bar, barErr);
        // 순정처럼 단속 정보 프레임의 막대 칸에도 같은 값(멀리 있으면 0)
        PhoneBar.send(context, barOut, near != null ? near[3] : 0, now());
        sent();
        sendHudList();
        sendHudDistance();
    }

    /**
     * 2.4.10(1.8.1 I-4 준비): 받은 다음 회전 거리 한 줄 "AA diag: dist Nm prev Pm (new|rise|drop) ev E side S"(숫자와 고정 낱말만).
     * 1초마다 오는 거리를 다 남기면 한도를 금방 쓰므로 아래만 남긴다.
     *  - new: 회전 이벤트 뒤 첫 거리
     *  - rise: 바로 앞에 **받은** 거리보다 PASS_WOBBLE 넘게 오름(nextTurnDistance의 회전 지남 판단 passed와 같은 기준, prev도 그 값)
     *  - drop: 마지막으로 **남긴** 거리보다 DIAG_DROP_STEP 이상 줄어듦(보통의 다가감을 50m마다 한 줄로)
     * 그래서 폰이 거리를 번갈아 보내는 꼴(#603 280m ↔ 600m)과 작은 되오름(130 → 140)도 줄로 보인다
     */
    void diagDistance(int meters, boolean afterTurn) {
        if (diag == null) return;
        int prev = diagPrev;
        diagPrev = meters;
        try {
            String why;
            if (afterTurn || prev < 0 || diagLast < 0) why = "new";
            else if (meters > prev + PASS_WOBBLE) why = "rise";
            else if (meters <= diagLast - DIAG_DROP_STEP) why = "drop";
            else return;
            diag.line("AA diag: dist " + meters + "m prev " + (prev < 0 ? "-" : prev + "m") + " (" + why + ") ev " + event + " side " + turnSide);
            diagLast = meters;
        } catch (Throwable t) {
            // 진단 줄이 실패해도 안내는 이어 간다
        }
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
        // 다른 회전, 또는 같은 열쇠라도 거리가 크게 늘었음(같은 방향 연속 회전, 리뷰 4): 히스테리시스는 새로 시작(N-3).
        // 1.7.4(2.4.3): 새 회전이면 그 회전의 처음 거리(nearFirst)도 지금 거리로 다시 잡는다(D)
        // 2.4.3 리뷰 중간 1(B): 앞 회전을 거의 다 지난 뒤(남은 거리 D_MIN 이하) 거리가 NEAR_PASS_RISE 넘게 다시 늘면 같은 열쇠라도
        // 다음 회전이다(같은 꼴 연속 회전). 회전 멀리서의 GPS 흔들림(조금 늘었다 줄어듦)은 이 조건에 걸리지 않는다
        boolean passed = nearLast >= 0 && nearLast <= D_MIN && remainMeters > nearLast + NEAR_PASS_RISE;
        boolean newTurn = !key.equals(nearKey) || passed || (nearLast >= 0 && remainMeters > nearLast + NEAR_NEW_TURN_JUMP);
        if (newTurn) {
            nearOn = false;
            nearFirst = remainMeters;
        } else if (remainMeters > nearFirst) {
            nearFirst = remainMeters;
        }
        nearLast = remainMeters;
        nearKey = key;
        // 소유자 결정 2026-11-09: 차로 조건을 뺐다(카플레이와 같은 규칙). D 안의 회전이면 곧 회전
        int[] disp = nearDisplay(event, turnSide, displayType, direction);
        if (disp == null) return null;
        // 1.7.4(2.4.3): D는 고정이 아니다(dynamicD). 기준은 순정 설정값(일반 350m, 고속도로 출구 1050m)
        int d = dynamicD(event == OFF_RAMP ? D_EXPRESSWAY : D_GENERAL, nearFirst);
        // 1.7.3(N-3): 들어갈 때는 D, 이미 곧 회전이면 D + NEAR_EXIT_MARGIN을 넘어야 나간다(경계 흔들림에 그림이 왔다갔다하지 않게)
        if (remainMeters > (nearOn ? d + NEAR_EXIT_MARGIN : d)) {
            nearOn = false;
            return null;
        }
        nearOn = true;
        // 2.4.10(1.8.1 T1-1): 도로 이름에 고가·지하차도 낱말이 있으면 순정 번호(44·46), 옆길 0(갈림의 다른 길 비트도 넣지 않음)
        int[] f = facilityNear(event, turnSide, road);
        if (f != null) return new int[] { f[0], 0, f[1], bar(remainMeters, d), d };
        int bits = disp[1] < 0 ? 0 : 1 << disp[1];
        // 1.8.0(2.4.7): 갈림의 다른 길 비트는 카플레이와 같은 규칙(오른쪽 직진 비트 0, 왼쪽 22.5도 비트 1, 순정과 같음)
        // (CpTurnsFit.forkOtherBit과 같은 값. 패키지가 달라 여기에 그대로 둔다)
        if (event == FORK) bits |= turnSide == LEFT ? 1 << 1 : 1;
        // 2.4.10: 진입 램프(왼쪽·오른쪽)는 순정 오른쪽 진입 side 3처럼 직진 비트 0을 더한다(왼쪽은 거울, 순정 값 못 봄)
        if (event == ON_RAMP && (turnSide == LEFT || turnSide == RIGHT)) bits |= 1;
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
     * 각도(시계 방향, 도)의 옆길 비트 번호(순정 SetSideStreetBit, 카플레이 CpTurnsFit.streetBit와 같은 규칙): 직진(348~12도) 0,
     * 뒤쪽(180도 ±12도) -1, 오른쪽 12~168도는 22.5도 칸마다 비트 1~7, 왼쪽 192~348도는 그 거울로 비트 14~8(270도 = 비트 11).
     * 2.4.4 후보(나, 니로 순정 줄 side 2065 dir 270): 전에는 "칸÷3"으로 왼쪽 36을 비트 12로 보냈다. 비트 8·9·12~14는 추정
     */
    static int streetBit(int cw) {
        cw = ((cw % 360) + 360) % 360;
        if (cw >= 348 || cw <= 12) return 0;
        if (cw > 168 && cw < 192) return -1;
        if (cw < 180) return Math.min(7, (int) ((cw - 12) / 22.5) + 1);
        return 15 - Math.min(7, (int) ((360 - cw - 12) / 22.5) + 1);
    }

    /** 방향 칸(7.5도 단위)의 옆길 비트 번호: 칸이 가리키는 각도로 streetBit(왼쪽 36 = 비트 11, 30 = 9, 42 = 13) */
    static int slotBit(int slot) {
        return streetBit((int) (slot * 7.5f));
    }

    /**
     * 곧 회전 {표시 번호, 나가는 길 옆길 비트 번호(-1: 옆길 없음), 방향 칸} 또는 곧 회전 화면이 없는 회전이면 null. 순정 SetCase*:
     * 회전 13(방향: 약간 6/42, 보통 12/36, 급 18/30, 직진 0), 유턴 31(오른쪽 통행 36), 출구 17/18(12/36), 갈림 13(약간 3/45, 1.8.0 전에는 Y자 25),
     * 회전교차로는 A 계열이면 32(2.4.10 L-1), B 계열은 출구 아이콘 그대로(방향은 출구 각도). 고가·지하차도 44·46은 near()가 도로 이름으로
     * 바꾼다(2.4.10 T1-1). 방향을 모르는 회전(아이콘 64), 이름 바뀜·출발·목적지·페리는 멀리 있음.
     */
    static int[] nearDisplay(int ev, int side, int displayType, int direction) {
        switch (ev) {
            case SLIGHT_TURN: case TURN: case SHARP_TURN: {
                if (side != LEFT && side != RIGHT) return null;
                int slot = ev == SLIGHT_TURN ? 6 : ev == TURN ? 12 : 18;
                if (side == LEFT) slot = 48 - slot;
                return new int[] { NEAR_TURN, slotBit(slot), slot };
            }
            case STRAIGHT: case MERGE:
                return new int[] { NEAR_TURN, 0, 0 };
            case ON_RAMP: {
                // 2.4.10(1.8.1, 소유자 결정 11-25): 카플레이 L-2와 같게 순정 오른쪽 진입 모양 13, 22.5도(칸 3), 옆길 3(비트 0·1, 직진 비트는
                // near()가 더함). 근거: K7 순정 운행 2026-11-25 16:29:00(표본 1개). 왼쪽 진입은 그 거울(337.5도 칸 45, 비트 0·14)로 두었고 순정 값은
                // 못 봤다. 방향 모름은 지금처럼 직진. 전에는 오른쪽 45도(칸 6)·왼쪽 315도(칸 42)였다
                if (side == RIGHT) return new int[] { NEAR_TURN, 1, 3 };
                if (side == LEFT) return new int[] { NEAR_TURN, 14, 45 };
                return new int[] { NEAR_TURN, slotBit(0), 0 };
            }
            case U_TURN:
                return new int[] { NEAR_UTURN, -1, side == RIGHT ? 12 : 36 };
            case OFF_RAMP:
                return side == LEFT ? new int[] { NEAR_EXIT_LEFT, -1, 36 } : new int[] { NEAR_EXIT_RIGHT, -1, 12 };
            case FORK:
                // 1.8.0: Y자 25가 아니라 회전 13 + 비스듬한 방향(오른쪽 22.5도 = 칸 3·비트 1, 왼쪽 337.5도 = 칸 45·비트 14).
                // 다른 길 비트(오른쪽 갈림 직진 0, 왼쪽 갈림 22.5도 1)는 near()가 더한다. 근거: 니로 순정 운행 2026-11-17·11-20
                // (오른쪽 갈림 13, dir 22.5, side 3 / 왼쪽 갈림 13, dir 337.5, side 16386, 25는 없음)
                if (side != LEFT && side != RIGHT) return null;
                return side == RIGHT ? new int[] { NEAR_TURN, 1, 3 } : new int[] { NEAR_TURN, 14, 45 };
            case ROUNDABOUT_ENTER_AND_EXIT: {
                // 회전교차로: 출구 아이콘 그대로, 방향은 출구 각도(setRoundaboutType의 direction, 시계 방향)
                // 2.4.10(1.8.1 L-1): 오른쪽 통행 그림(A 계열 96~107)이면 순정처럼 32 + 출구 칸·비트(카플레이와 같음). B 계열은 그대로
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
                    bit = streetBit(cw);
                    slot = bit <= 7 ? 3 * bit : 3 * (bit + 1);
                }
                return new int[] { displayType >= 96 && displayType <= 107 ? NEAR_ROUNDABOUT_R : displayType, bit, slot };
            }
            default:
                return null;
        }
    }

    /**
     * 2.4.10(1.8.1 T1-1): 고가·지하차도 곧 회전 {표시 번호, 방향 칸} 또는 바꾸지 않으면 null(CpTurnsFit.facilityNear와 같은 규칙).
     * AA는 안내 문구를 주지 않아 도로 이름만 본다. 대상은 왼쪽·오른쪽이 있는 보통·약간 회전과 갈림뿐이다(급회전·유턴·램프·회전교차로·
     * 직진은 그대로). 지하차도 46(왼쪽 칸 36, 오른쪽 칸 12), 고가는 왼쪽만 44
     */
    static int[] facilityNear(int ev, int side, String road) {
        if (ev != TURN && ev != SLIGHT_TURN && ev != FORK) return null;
        if (side != LEFT && side != RIGHT) return null;
        int under = facilityWord(road, UNDERPASS_WORDS), over = facilityWord(road, OVERPASS_WORDS);
        if ((under | over) >= 2 || (under != 0 && over != 0)) return null;
        if (under == 1) return new int[] { NEAR_UNDERPASS, side == LEFT ? 36 : 12 };
        if (over == 1 && side == LEFT) return new int[] { NEAR_OVERPASS_L, 36 };
        return null;
    }

    /** 글 s에 words 가운데 하나가 있으면 1, 그런데 FACILITY_NOT_WORDS도 있으면 2, 없으면 0 */
    static int facilityWord(String s, String[] words) {
        if (s == null || s.length() == 0) return 0;
        boolean hit = false;
        for (String w : words) if (s.indexOf(w) >= 0) hit = true;
        if (!hit) return 0;
        for (String n : FACILITY_NOT_WORDS) if (s.indexOf(n) >= 0) return 2;
        return 1;
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
        if (!rerouting) distStale = false;   // T-6 ①: 재탐색이 끝난 뒤 폰이 준 거리(재탐색 중 거리는 새 경로 것인지 모름)
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
        // 순정 setRoundAboutDisplayType: side가 오른쪽이면 A 계열(96~106), 아니면 B 계열(192~202). 2.4.2에서 순정대로 바로잡음
        // 2.4.10(1.8.1 #572): 한국 폰은 회전교차로에 side 1(LEFT, 반시계 방향 = 오른쪽 통행)을 보낸다(#583·#79 리포트 6줄 모두 ev 13 side 1).
        // 순정 규칙대로면 오른쪽 통행인 한국 길에 왼쪽 통행 그림(B 계열)이 나가 HUD에 반대로 보였다(#572 TMHEV 네이버 지도).
        // 각도 표(90 오른쪽, 270 왼쪽)도 반시계 방향 기준이다. 그래서 보정 층에서는 RIGHT(시계 방향)일 때만 B 계열, 그 밖은 A 계열(순정 내비도
        // 이 회전교차로에서 HUD 목록 107 = A 계열을 보냄, 11-25 니로 PHEV). 순정 층(AaTurns)은 그대로 둔다
        boolean r = ROUNDABOUT_LEFT_IS_A ? side != RIGHT : side == RIGHT;
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
        if (distStale) return;   // T-6 ①(재리뷰 중간 1): 옛 경로 거리는 HUD에도 싣지 않는다
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
