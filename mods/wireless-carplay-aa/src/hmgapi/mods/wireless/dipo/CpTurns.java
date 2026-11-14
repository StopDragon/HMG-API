package hmgapi.mods.wireless.dipo;
// Modified by Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-07:
//   - an icon for every maneuver (angle bands, then the type alone, then the blank icon 8);
//     turn() no longer stops before the distance, HUD and trip when the exact table has none
//   - a maneuver update without the exit angle keeps the angle for the same maneuver type
//   - while guidance is on, the last state is resent (refresh) and the HUD list every 2 s
//   - the crossroad name only when it changes (or every 10 s); maneuver values logged as numbers
// fallbackIcon, bandedTurn, roundaboutIcon, NO_ICON, the blank HUD list in sendHud, the 47-50
// arrows in aheadIcon and the refresh/HUD resend are adapted from HMG-Seogaemo
// (https://github.com/seogaemo/hmg-seogaemo, AGPL-3.0, commit 26eeb97e,
// modules/wireless-carplay-and-aa/payload/src/hmgapi/mods/wireless/dipo/CpTurns.java.src).
// Modified by Korea Projection, 2026-11-08 (mod 2.3.0, "match the stock Korean navigation"):
//   - Korean stock DiPO numbers: on-ramp (9) 160, highway off-ramp left/right (22/23) 165/164
//   - fallbackIcon: off-ramp 163, ferry exit/change 176, turn at the end 70/67, U-turn when
//     possible 72, end of directions 112, change highway 163 (numbers the stock engine uses)
//   - roundaboutIcon: the stock DiPO angles exactly, else 8 directions in 45-degree bands
//   - aheadIcon: "none" is 8 (the engine's no-guidance icon), not 64 (a loading glyph on Gen5W)
//   - lanes: laneValues and LaneGlyphs (the car-verified multi-arrow glyphs and 2-bit per-arrow
//     colours) are ported from HMG-Seogaemo (same repository, licence and commit 26eeb97 as above)
//   - HUD distance (2C): the 2nd and 3rd points from the maneuver list, metres under 1 km as the
//     stock navigation sends them, 65535 (invalid) for a point there isn't and when guidance ends
// Korea Projection 1.7.2 수정(2026-11-08, 모드 2.3.1 판 그대로, 계획 M-2·M-3):
//   - 안내가 끝나면(stop·disconnected) 계기판 회전을 순정 무안내 값으로 지운다: 표시 8, 방향 0,
//     거리 무효(distanceErr -3), 단위 15, 막대 무효(barErr -1). 도로·목적지 이름은 빈 문자열로 지운다
//   - 안내 끝 2C는 순정 엔진 setClusterClearData와 같은 (단위 15, 소수 15, 정수 65535) x 3
//   - 회전(tbtInfo)의 막대는 "멀리 있음" 값(막대 0, barErr -1 → 계기판 막대 15)으로 보낸다
//   - (HUD 빈 목록·아이콘 수는 아래 2026-11-09 수정에서 순정 {F2,00}·3칸으로 맞췄다)
// Korea Projection 1.7.2 수정(2026-11-08, 계획 M-15):
//   - HUD 목록 이름을 UTF-16LE 78바이트 안에서 글자 경계로 자른다(서로게이트 쌍을 반으로 자르지 않음)
// Korea Projection 1.7.2 수정(2026-11-09, 계획 M-2 HUD·M-4·M-5·M-11, 소유자 결정 2026-11-09 "순정대로"):
//   - 곧 회전 화면(M-4): 차로 안내가 함께 오는 회전만, 남은 거리가 D 안이면 순정 SetCase* 값(표시 13·31·17/18·25,
//     회전교차로는 출구 아이콘)과 방향·옆길 비트·막대를 보낸다. 그 밖은 지금처럼 멀리 있음(막대 무효)
//   - D(M-5): 일반 350m, 고속도로 출구 1050m(순정처럼 고정, 처음 거리가 짧아도 줄이지 않음). 막대는 10..90. 차 속도는 모드에서 못 읽어 보정 안 함
//   - 막대는 순정 단속 정보 프레임의 막대 칸에도 넣는다(PhoneBarOut 방송 → 순정 내비 프로세스, 1.7.2 시험판 3)
//   - 도로 이름: 곧 회전일 때만 보내고 멀리 있을 때·경로 탐색 중에는 빈 이름(순정 sendTurnNameData)
//   - 경로 탐색 중(상태 3) 표시 9, 재탐색(상태 5) 표시 10: 방향·옆길·거리 무효로 바로 보내고 2C·HUD 목록을 지운다
//     (순정 SetClusterCalculate + 강제 지우기). 그 상태를 벗어나면 보통 회전 표시로 돌아간다
//   - HUD 목록(M-2): 빈 목록은 순정과 같은 {F2,00} 2바이트, 아이콘은 3칸(셋째 칸은 그다음 회전)
//   - updateRemainDis(0x2C 남은 거리)는 보내지 않는다(M-11, 순정 DiPO·GPM도 부르지 않음)
// Korea Projection 1.7.2 수정(2026-11-09, 차-5 뒤 진단): 디버그 로그가 켜졌을 때만 "CarPlay diag:" 줄을 남긴다.
//   차로 안내를 받으면 그 번호·차로 수, 회전마다 곧 회전 판단 근거(링크한 차로·받은 차로 수·남은 거리 m·D·판정과 이유)와
//   보낸 updateTBTInfo 인자. 숫자만, 판정이나 인자가 바뀔 때만 남긴다(거리만 바뀐 줄은 남기지 않음).
// Korea Projection 1.7.2 수정(2026-11-09, 소유자 결정 "D 안의 모든 회전"): 곧 회전 화면에서 차로 안내 조건(item 12 연결·
//   차로 수)을 뺐다. 카카오내비는 카플레이로 차로 안내를 보내지 않아 큰 그림이 한 번도 나오지 않았다(차-5). 이제 남은 거리
//   (item 10, m)가 D 안이고 큰 그림이 있는 종류면 곧 회전이다. 목적지·경유지·출발·페리, 거리 없음·오래된 거리는 멀리 있음 그대로.
//   차로 프레임은 받으면 지금처럼 따로 보낸다. 위 M-4 설명의 "차로 안내가 함께 오는 회전만"은 이 수정으로 바뀌었다.

// Korea Projection 1.7.4(2026-11-12, 모드 2.4.2, 소유자 결정 "순정과 똑같이", 계획 D-15 방안 A):
//   회전 프레임을 순정 DiPO(e/u.a)와 같게 보낸다. 지운 것: 곧 회전 화면(13·31·17·18·25)·옆길 비트·방향·막대,
//   350m·1050m 경계 여유, 막대 방송(PhoneBarOut), 경로 탐색 9·재탐색 10 표시, 표 밖 입력의 대체 번호(fallbackIcon·
//   bandedTurn·roundaboutIcon, 빈 칸 8, 출발 1), 교차로·목적지 이름의 10초 묶기와 "곧 회전일 때만 이름", 거리 모름의
//   거리 오류 -3, 멀리 있음 막대 오류 -1. 좌측통행 회전교차로는 순정 B 계열. 위 옛 수정 기록 가운데 이 항목들은 이제 맞지 않는다.
//   판단을 기다리며 그대로 둔 것: 각도 없는 좌·우회전 70·67, 출구 각도 남기기, 안내 끝 지우기, 다시 보내기, 출처 앱 바뀜 정리,
//   HUD 목록·2C·차로·남은 거리·다음 회전 목록(drafts/1.7.4-work/mod.md)

import android.content.Context;
import android.text.format.DateFormat;

import com.tcc.iap2.Iap2NotiMsg;

import java.util.Calendar;
import java.util.LinkedHashMap;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

/**
 * CarPlay turn-by-turn on the cluster and HUD: a port of the turn-by-turn build's DiPO
 * route guidance manager (e/u, with its route update e/c and maneuver e/v), fed by
 * HMG-API's own iAP2 listener instead of DiPO's, and sending through HMG-API's Cluster API.
 * DiPO's own manager only exists when stepofspec is 999 or more, and stays off.
 *
 * The iPhone sends RouteGuidanceUpdate (120: state, distances, the current maneuver
 * list), RouteGuidanceManeuverUpdate (121: one maneuver by index) and LaneGuidance
 * (122), each for a display component, often field by field. Kept per component as in
 * DiPO. What it sends, as the build did:
 *   - the turn (updateTBTInfo: icon, distance in the phone's units), crossroad name and,
 *     when state is 6, the destination name
 *   - the HUD list (0xF2 frame: icon, next icon, road) and the HUD distance (2C)
 *   - remaining distance and arrival time (updateTBTSubInfo, in the owner's units)
 *   - up to 3 upcoming turns (updateTPTurnList, owner's units) and lanes (0xF1 frame)
 * Never the cluster's alive or operation status: phone guidance doesn't claim the
 * cluster (the cluster doesn't come back from that on these cars).
 * Also from the build: when the source app changes (Apple Maps to Waze, say), what the
 * old app left is cleared and route guidance restarted, so the phone resends all of it.
 * Not ported: DiPO's per-region icon filters (China, Europe, Middle East, Australia,
 * Brazil); on US and Canada cars the build sends every icon.
 * One thread: every call comes from the same handler.
 */
final class CpTurns {
    final Context context;
    final Cluster cluster;
    /** restarts the phone's route guidance updates (Stop, then Start) on a thread of its own */
    final Runnable restart;
    /** component id -> its route guidance state (DiPO e/u.Al) */
    final LinkedHashMap<Integer, Update> updates = new LinkedHashMap<Integer, Update>();
    /** component id -> maneuver index -> maneuver (DiPO e/u.Am) */
    final LinkedHashMap<Integer, LinkedHashMap<Integer, Maneuver>> maneuvers = new LinkedHashMap<Integer, LinkedHashMap<Integer, Maneuver>>();
    String source;
    /**
     * 최근에 본 출처 이름과 마지막으로 본 시각(1.7.2 베타 2, #132). iOS 26.1의 애플 지도는 `Apple Maps`와 `Apple 지도`를
     * 번갈아 보낸다. SOURCE_SAME_MS 안에 본 이름으로 돌아오면 같은 앱으로 보고 지우지 않는다(앱 이름 표는 쓰지 않는다).
     */
    final LinkedHashMap<String, Long> recentSources = new LinkedHashMap<String, Long>();
    static final long SOURCE_SAME_MS = 60000L;
    /** 같은 앱으로 보고 회전을 다시 받기로 한 시각(0이면 아님)과 기다리는 한도: 넘으면 계기판·HUD를 지운다 */
    long sourceWaitAt;
    static final long SOURCE_WAIT_MS = 10000L;
    static final int SOURCE_KEEP = 4;
    /** T-5 ①(1.7.4): 첫 출처 이름 디버그 줄 수(프로세스당) */
    int sourceLogs;
    static final int SOURCE_LOGS_MAX = 20;
    /** last HUD list sent (type, next, road), so an unchanged one isn't resent */
    int hudType, hudNext, hudThird;
    String hudRoad;
    /**
     * The last HUD list frame and when it went out. The HUD (a separate unit) can miss a frame
     * or lose its state while the cluster keeps working, so the unchanged frame is resent every
     * HUD_RESEND_MS from refresh() (from HMG-Seogaemo).
     */
    byte[] lastHud;
    long hudAt;
    static final long HUD_RESEND_MS = 2000L;
    /**
     * The component whose turn-by-turn is on the cluster (null: none) and when turn() last sent
     * it; refresh() resends it after REFRESH_MS without one. The phone only sends what changed,
     * and the car's own navigation calls the cluster every 0.5-0.75 s.
     */
    Integer activeId;
    long lastSendAt;
    static final long REFRESH_MS = 900L;
    /** when the phone last sent route guidance; refresh() stops after REFRESH_MAX_MS of silence */
    long phoneAt;
    static final long REFRESH_MAX_MS = 300000L;
    /** run (on this thread) when guidance becomes active, so the caller starts calling refresh() */
    Runnable onActive;
    /** maneuver log: the last values logged and how many lines so far (numbers only, no names) */
    String maneuverKey;
    int maneuverLogs;
    static final int MANEUVER_LOGS_MAX = 300;
    /** 진단 줄(디버그 로그가 켜졌을 때만): 차로 안내 번호·차로 수. 숫자만. Dipo가 debug를 켠다(Wireless.debug, 패키지의 디버그 파일) */
    boolean debug;
    String laneDiagKey;
    int diagLogs;
    static final int DIAG_LOGS_MAX = 300;
    /** the time (ms since boot: the wall clock jumps when GPS or the network sets it); tests set clock */
    long clock = -1L;
    /**
     * 계기판에 회전이나 목적지 이름을 보낸 뒤 아직 지우지 않았는지(M-2). 폰은 안내가 없을 때도 상태 0을
     * 여러 번 보내므로, 지우기(clearTurn)는 보낸 것이 있을 때 한 번만 한다.
     */
    boolean shown;
    /** HUD 목록·차로·2C를 보낸 뒤 아직 지우지 않았는지(2차 리뷰 2, hudBlank) */
    boolean hudShown;
    /** 거리를 몰라 HUD 첫 칸을 비워 보냈다(같은 값을 되풀이하지 않게, 거리를 알면 풀림) */
    boolean hudDistBlank;

    /**
     * 안내 끝 지우기 값(M-2, 1.7.4에서도 그대로: 순정 DiPO는 운영 상태 2로 계기판을 넘기지만 우리는 운영 상태를
     * 보내지 않으므로 이 지우기가 그 대신이다). 표시 8, 거리 무효 -3, 단위 15, 막대 무효(barErr -1).
     */
    static final int CLEAR_ICON = 8, CLEAR_DIST_ERR = -3, CLEAR_UNIT = 15, CLEAR_BAR_ERR = -1;
    /**
     * 남은 거리·시간(updateTBTSubInfo) 지우기 값: 순정 엔진은 0xFF·거리 0xFFFF를 넣고, 서비스가 범위 밖 값을
     * 시간 종류·형식 3, 단위 15로 바꾼다. 도착 지점 종류는 9인자판 기본값 15(없음)를 쓴다.
     */
    static final int CLEAR_TIME = 3, CLEAR_EST_POINT = 15;
    /** 안내 끝 2C 한 칸: 단위 15, 소수 15, 정수 65535(순정 setClusterClearData) */
    static final int HUD_CLEAR_UNIT = 15, HUD_CLEAR_TENTHS = 15;
    /** iAP2 RouteGuidanceState 6: 목적지 이름을 보내는 상태(순정 DiPO e/u.a의 c.a() == 6) */
    static final int STATE_DESTINATION = 6;

    long now() {
        return clock >= 0L ? clock : android.os.SystemClock.elapsedRealtime();
    }

    CpTurns(Context context, Cluster cluster, Runnable restart) {
        this.context = context;
        this.cluster = cluster;
        this.restart = restart;
    }

    /** RouteGuidanceUpdate (DiPO e/u.c) */
    void routeGuidance(Iap2NotiMsg msg) {
        if (msg == null) return;
        phoneAt = now();
        sourceCheck(msg);
        int[] ids = componentIds(msg);
        if (ids == null) {
            for (Integer id : updates.keySet()) {
                Update u = updates.get(id);
                if (u != null) u.read(id, msg);
                turn(id, u);
            }
            return;
        }
        for (int k = 0; k < ids.length; k++) {
            Integer id = ids[k];
            Update u = updates.get(id);
            if (u != null) {
                u.read(id, msg);
            } else {
                u = new Update(id, msg);
                updates.put(id, u);
            }
            turn(id, u);
        }
    }

    /** the components a RouteGuidanceUpdate is for (item 0); null if it doesn't say */
    static int[] componentIds(Iap2NotiMsg msg) {
        Iap2NotiMsg.Iap2ObjGroup g = msg.getItemGroup();
        if (g == null) return null;
        int[] ids = null;
        for (int k = 0; k < g.getItemCnt(); k++) {
            Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
            if (it == null) return null;
            if (it.getItemParamType() == 5 && it.getItemId() == 0 && it.getItemObjCount() > 0) ids = it.getItemIntArray();
        }
        return ids;
    }

    /**
     * RouteGuidanceManeuverUpdate (DiPO e/u.d): item 0 the components, item 1 the index.
     * Stored under the index the message carries (the build's fix, by Isaac Wisdom: stock
     * wrote the index into a copy and stored every maneuver under -1, so none was found).
     */
    void maneuver(Iap2NotiMsg msg) {
        if (msg == null) return;
        phoneAt = now();
        int index = -1;
        int[] ids = null;
        Iap2NotiMsg.Iap2ObjGroup g = msg.getItemGroup();
        if (g != null) {
            int[] found = null;
            boolean whole = true;
            for (int k = 0; k < g.getItemCnt(); k++) {
                Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
                if (it == null) {
                    whole = false;
                    break;
                }
                int id = it.getItemId();
                if (it.getItemObjCount() <= 0 || (id != 0 && id != 1)) continue;
                int[] v = it.getItemIntArray();
                if (v == null || v.length == 0) continue;
                if (id == 0) found = v;
                else index = v[0];
            }
            if (whole) ids = found;
        }
        if (index < 0) return;
        if (ids == null) {
            for (Integer id : maneuvers.keySet()) {
                LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
                if (m == null) continue;
                Maneuver x = m.get(index);
                if (x != null) x.read(index, id, msg);
                else m.put(index, new Maneuver(index, id, msg));
            }
            return;
        }
        for (int k = 0; k < ids.length; k++) {
            int id = ids[k];
            LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
            if (m != null) {
                Maneuver x = m.get(index);
                if (x != null) x.read(index, id, msg);
                else m.put(index, new Maneuver(index, id, msg));
            } else {
                m = new LinkedHashMap<Integer, Maneuver>();
                m.put(index, new Maneuver(index, id, msg));
                maneuvers.put(id, m);
            }
        }
    }

    /** LaneGuidance (the build's applyLanes): up to 8 lanes as the cluster's 0xF1 frame */
    void lanes(Iap2NotiMsg msg) {
        Iap2NotiMsg.Iap2ObjGroup g = msg == null ? null : msg.getItemGroup();
        if (g == null) return;
        int[] status = new int[8], a = new int[8], b = new int[8], c = new int[8];
        int n = 0;
        Iap2NotiMsg.Iap2ObjGroup[] lanes = null;
        int index = -1;
        for (int k = 0; k < g.getItemCnt(); k++) {
            Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
            if (it == null) continue;
            if (it.getItemParamType() == 13 && it.getItemId() == 2) lanes = it.getItemGroupArray();
            // item 1: 이 차로 안내의 번호(LaneGuidanceIndex, 순정 DiPO onLaneGuidanceInformation과 같음)
            if (it.getItemId() == 1 && (it.getItemParamType() == 1 || it.getItemParamType() == 5) && it.getItemObjCount() > 0) {
                int[] v = it.getItemIntArray();
                if (v != null && v.length > 0) index = v[0];
            }
        }
        if (lanes != null) {
            for (int k = 0; k < lanes.length && n < 8; k++) {
                int[] one = laneOne(lanes[k]);
                status[n] = one[0];
                a[n] = one[1];
                b[n] = one[2];
                c[n] = one[3];
                n++;
            }
        }
        byte[] f = new byte[81];
        f[0] = (byte) 0xF1;
        f[1] = (byte) n;
        for (int k = 0; k < n; k++) {
            f[k + 3] = (byte) status[k];
            f[k * 3 + 19] = (byte) a[k];
            f[k * 3 + 20] = (byte) b[k];
            f[k * 3 + 21] = (byte) c[k];
        }
        cluster.laneInfo(f);
        hudShown = true;
        if (debug) {
            // 진단: 차로 안내(0x5204)를 받았는지와 그 번호·차로 수(숫자만, 같은 값이면 남기지 않음)
            try {
                String k = index + " " + n;
                if (!k.equals(laneDiagKey)) {
                    laneDiagKey = k;
                    diag("CarPlay diag: lane guidance index " + index + " lanes " + n);
                }
            } catch (Throwable e) {
                // 남기지 않는다
            }
        }
    }

    /** 진단 줄 하나(debug일 때만 부른다). 프로세스당 DIAG_LOGS_MAX 줄까지 */
    void diag(String line) {
        if (diagLogs >= DIAG_LOGS_MAX) return;
        diagLogs++;
        Dipo.log(line);
    }

    /**
     * One lane: {status byte, up to 3 arrow image ids}. The phone gives a lane a status (0 not
     * good, 1 good, 2 preferred), its arrow angles and the angle along the route (highlight);
     * laneValues turns them into the cluster's per-arrow colours and glyphs (from HMG-Seogaemo,
     * commit 26eeb97, AGPL-3.0).
     */
    static int[] laneOne(Iap2NotiMsg.Iap2ObjGroup g) {
        if (g == null) {
            int[] r = new int[4];
            r[0] = 1;
            return r;
        }
        int[] angles = null, highlight = null;
        int status = -1;
        for (int k = 0; k < g.getItemCnt(); k++) {
            Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
            if (it == null) continue;
            int t = it.getItemParamType();
            if (t != 5 && t != 8) continue;
            int[] v = it.getItemIntArray();
            if (v == null || v.length == 0) continue;
            int id = it.getItemId();
            if (id == 1) {
                status = v[0];
            } else if (id == 2) {
                angles = v;
            } else if (id == 3) {
                highlight = v;
            }
        }
        return laneValues(status, angles, highlight);
    }

    /**
     * Ported from HMG-Seogaemo (https://github.com/seogaemo/hmg-seogaemo, AGPL-3.0, commit 26eeb97,
     * CpTurns.java.src laneValues), unchanged.
     * One lane as {status byte, up to 3 arrow image ids}. The status byte holds 2 bits per arrow
     * slot, slot 0 lowest: 00 not drawn, 01 normal (white), 10 recommended (blue), 11 inactive
     * (grey), car-verified by HMG-Seogaemo; the arrows are drawn overlaid, so a lane with several
     * directions needs the matching component glyphs (LaneGlyphs), not the single ones.
     *
     * The colours mirror CarPlay per lane, from the lane status and the highlight angle (an arrow
     * "matches" when its angle equals a highlight angle; none valid, or none matching, means all
     * arrows match): status 0 grey on every arrow; status 1 white on the matching arrows and grey
     * on the others; status 2 blue on the matching arrows and grey on the others. Blue only ever
     * comes from the phone's status 2. A status that is not 0..2 counts as 1. A lane with no valid
     * angle sends the lane's value on slot 0 only. A set of directions with no verified glyph row
     * sends only the highlighted arrow (else the first) as a single glyph.
     */
    static int[] laneValues(int status, int[] angles, int[] highlights) {
        int[] r = new int[4];
        // the colour of an arrow on the route (matching) and off it
        int on = status == 2 ? 2 : status == 0 ? 3 : 1, off = 3;
        // the lane's distinct direction classes (laneIcon values), in the phone's order, and which one the route takes
        int[] cls = new int[8];
        boolean[] hit = new boolean[8];
        int n = 0;
        boolean anyHit = false;
        if (angles != null) {
            for (int k = 0; k < angles.length; k++) {
                int a = angles[k];
                if (a < -180 || a > 180) continue;
                int c = laneIcon(a), i = 0;
                while (i < n && cls[i] != c) i++;
                if (i == n) cls[n++] = c;
                if (highlights != null) for (int h : highlights) if (h == a) hit[i] = anyHit = true;
            }
        }
        if (n == 0) {
            r[0] = on;
            return r;
        }
        // the arrows that take the lane's colour: the highlighted ones, or all when none matches
        boolean[] rec = new boolean[8];
        for (int i = 0; i < n; i++) rec[i] = !anyHit || hit[i];
        boolean snap = false;
        int[][] row = LaneGlyphs.find(cls, n, false);
        if (row == null) {
            row = LaneGlyphs.find(cls, n, true);
            snap = row != null;
        }
        if (row == null) {
            // not a verified set (more than 3 directions, or none of the table's): only the
            // highlighted arrow, else the first, as a single glyph
            int pick = 0;
            for (int i = n - 1; i >= 0; i--) if (rec[i]) pick = i;
            row = new int[][] { { cls[pick] }, { cls[pick] } };
        }
        for (int j = 0; j < row[0].length; j++) {
            boolean slotRec = false;
            for (int i = 0; i < n; i++) if (rec[i] && LaneGlyphs.fold(cls[i], snap) == row[0][j]) slotRec = true;
            r[0] |= (slotRec ? on : off) << (2 * j);
            r[1 + j] = row[1][j];
        }
        return r;
    }

    /**
     * Ported from HMG-Seogaemo (https://github.com/seogaemo/hmg-seogaemo, AGPL-3.0, commit 26eeb97,
     * CpTurns.java.src LaneGlyphs), unchanged: image ids for the arrows of a multi-direction lane,
     * the 26 sets car-verified by HMG-Seogaemo (2026-11-05). Single directions use the plain ids
     * 1..8 (laneIcon values, which double as the direction classes here). Each row is {direction
     * classes in slot order, image ids in slot order}; the lane's set of directions matches a row
     * whatever order the phone lists its angles in, and slot k of the status byte belongs to the
     * k-th class of the row.
     */
    static final class LaneGlyphs {
        static final int ST = 1, SLR = 2, R = 3, SHR = 4, UT = 5, SHL = 6, L = 7, SLL = 8;
        static final int[][][] SETS = {
            { { UT, L }, { 10, 14 } },
            { { UT, SLL }, { 10, 15 } },
            { { ST, L }, { 9, 12 } },
            { { ST, SLL }, { 9, 13 } },
            { { ST, SHL }, { 9, 11 } },
            { { ST, UT }, { 9, 10 } },
            { { L, SLL }, { 12, 13 } },
            { { ST, R }, { 16, 18 } },
            { { ST, SLR }, { 16, 19 } },
            { { ST, SHR }, { 16, 17 } },
            { { R, SLR }, { 18, 21 } },
            { { L, R }, { 31, 32 } },
            { { SLL, SLR }, { 30, 33 } },
            { { UT, R }, { 26, 32 } },
            { { UT, SLR }, { 26, 33 } },
            { { L, ST, R }, { 28, 22, 24 } },
            { { SLL, ST, R }, { 30, 22, 24 } },
            { { L, ST, SLR }, { 28, 22, 33 } },
            { { SHL, ST, R }, { 27, 22, 24 } },
            { { L, ST, SHR }, { 28, 22, 25 } },
            { { SLL, ST, SLR }, { 30, 22, 33 } },
            { { L, SLL, ST }, { 12, 13, 9 } },
            { { ST, SLR, R }, { 16, 19, 18 } },
            { { UT, L, ST }, { 26, 31, 22 } },
            { { UT, ST, R }, { 26, 22, 24 } },
            { { UT, L, R }, { 26, 31, 32 } },
        };

        /** snap: slight and sharp turns count as the plain left / right */
        static int fold(int c, boolean snap) {
            if (!snap) return c;
            return c == SLR || c == SHR ? R : c == SLL || c == SHL ? L : c;
        }

        /** the row for the classes cls[0..n) (one class: the plain glyph), or null when none matches */
        static int[][] find(int[] cls, int n, boolean snap) {
            int mask = 0, count = 0, first = 0;
            for (int i = 0; i < n; i++) {
                int c = fold(cls[i], snap);
                if ((mask & (1 << c)) != 0) continue;
                mask |= 1 << c;
                if (count++ == 0) first = c;
            }
            if (count == 1) return new int[][] { { first }, { first } };
            for (int[][] row : SETS) {
                int m = 0;
                for (int c : row[0]) m |= 1 << c;
                if (m == mask) return row;
            }
            return null;
        }
    }

    /** a lane angle (degrees, right positive) as the cluster's arrow: 1 straight, 2-4 right, 5 back, 6-8 left */
    static int laneIcon(int a) {
        if (a > 180) a -= 360;
        else if (a < -180) a += 360;
        if (a >= 0) {
            if (a <= 22) return 1;
            if (a <= 67) return 2;
            if (a <= 112) return 3;
            return a <= 157 ? 4 : 5;
        }
        a = -a;
        if (a <= 22) return 1;
        if (a <= 67) return 8;
        if (a <= 112) return 7;
        return a <= 157 ? 6 : 5;
    }

    /** the iAP2 session ended (DiPO e/u.disconnected) */
    void disconnected() {
        reset();
        recentSources.clear();
        clearTurn();
        hudBlank();
        source = null;
    }

    /** route guidance stopped (DiPO e/u.nS: the car's navigation took turn-by-turn back, or state 0) */
    void stop() {
        activeId = null;
        sourceWaitAt = 0L;
        clearTurn();
        hudBlank();
    }

    /**
     * 안내 끝(M-2): 계기판의 회전과 도로·목적지 이름을 순정 무안내 값으로 지운다. 지우지 않으면 순정 엔진이
     * "바뀔 때만" 보내므로 폰의 마지막 회전이 계기판에 남는다. 보낸 것이 없으면 아무것도 하지 않는다.
     */
    void clearTurn() {
        if (!shown) return;
        shown = false;
        // 진단(1.7.2 베타 2, #61·#123 "안내를 끝내도 잠시 남음"): 지운 때와 폰의 마지막 갱신 뒤 지난 시간(숫자만)
        Dipo.log("CarPlay: turn-by-turn cleared, " + (now() - phoneAt) / 1000L + " s after the phone's last update");
        cluster.tbtInfo(CLEAR_ICON, 0, 0f, 0f, CLEAR_DIST_ERR, 0, CLEAR_UNIT, 0, CLEAR_BAR_ERR);
        // 남은 거리·도착 시각도 순정 무효 값으로 지운다(리뷰 6, AA release와 같은 값)
        cluster.tbtSubInfo(0f, CLEAR_DIST_ERR, CLEAR_UNIT, 0, 0, CLEAR_TIME, CLEAR_TIME, 0f, -1, CLEAR_EST_POINT);
        cluster.crossroadName("");
        cluster.destinationName(0, "");
    }

    /**
     * Called about every second on the same thread while active() (the caller stops calling it
     * once it's false): the last known state goes out again when the phone has said nothing for a
     * moment, so the cluster and HUD don't age it out. Stops by itself on state 0 or after
     * REFRESH_MAX_MS without anything from the phone (what's shown is then left to age out).
     */
    void refresh() {
        Integer id = activeId;
        if (id == null) return;
        Update u = updates.get(id);
        if (u == null || u.state == 0) {
            activeId = null;
            return;
        }
        long t = now();
        if (t - phoneAt >= REFRESH_MAX_MS) {
            Dipo.log("CarPlay: no route guidance from the phone for " + (t - phoneAt) / 1000L + " s, turn-by-turn not resent any more");
            activeId = null;
            return;
        }
        if (lastHud != null && t - hudAt >= HUD_RESEND_MS) {
            cluster.turnListNew(lastHud);
            hudAt = t;
        }
        if (t - lastSendAt >= REFRESH_MS) turn(id, u);
    }

    /** whether refresh() still has something to resend */
    boolean active() {
        return activeId != null;
    }

    /** forget every component's state and maneuvers (DiPO e/u.nT) */
    void reset() {
        activeId = null;
        sourceWaitAt = 0L;
        for (LinkedHashMap<Integer, Maneuver> m : maneuvers.values()) {
            if (m != null) m.clear();
        }
        for (Update u : updates.values()) {
            if (u != null) u.clear();
        }
    }

    /** SourceName (item 19) changed: clear the old app's leftovers and have the phone resend */
    void sourceCheck(Iap2NotiMsg msg) {
        Iap2NotiMsg.Iap2ObjGroup g = msg.getItemGroup();
        if (g == null) return;
        for (int k = 0; k < g.getItemCnt(); k++) {
            Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
            if (it == null || it.getItemId() != 19 || it.getItemParamType() != 10 || it.getItemObjCount() <= 0) continue;
            String[] v = it.getItemStringArray();
            String s = v == null || v.length == 0 ? null : v[0];
            if (s == null || s.length() == 0) continue;
            String was = source;
            source = s;
            // T-5 ①(1.7.4): 디버그 로그가 켜졌을 때만 어느 지도 앱이 회전을 보내는지(출처 이름만, 앱 이름이다) 남긴다.
            // 출처가 바뀐 줄(아래)은 2.4.1부터 늘 남는다. 프로세스당 SOURCE_LOGS_MAX 줄까지
            if (was == null && debug && sourceLogs < SOURCE_LOGS_MAX) {
                sourceLogs++;
                Dipo.log("CarPlay: route guidance source " + s + " (debug)");
            }
            long t = now();
            Long seen = recentSources.remove(s);
            recentSources.put(s, t);
            while (recentSources.size() > SOURCE_KEEP) recentSources.remove(recentSources.keySet().iterator().next());
            if (was == null || was.equals(s)) return;
            if (seen != null && t - seen < SOURCE_SAME_MS) {
                // 방금 본 이름으로 돌아왔다: 계기판·HUD·막대는 지우지 않는다(같은 앱의 이름 바뀜이면 깜빡이지 않게).
                // 다만 실제로 다른 앱에서 돌아온 것일 수도 있어(리뷰 1) 받아 둔 회전·상태는 비우고 폰에게 다시 보내 달라고 한다.
                // 화면은 폰이 다시 보낼 때까지 마지막 그림을 유지한다.
                Dipo.log("CarPlay: route guidance source name back to one seen " + (t - seen) / 1000L + " s ago, display kept, data asked again");
                // 안내 상태(updates)는 남긴다: 비우면 상태 0이 되어 바로 "안내 끝"으로 지워진다. 받아 둔 회전만 비운다
                for (LinkedHashMap<Integer, Maneuver> m : maneuvers.values()) {
                    if (m != null) m.clear();
                }
                // 옛 HUD 목록은 다시 보내지 않는다. 폰이 SOURCE_WAIT_MS 안에 회전을 다시 주지 않으면 turn()이 지운다(2차 리뷰 1)
                lastHud = null;
                sourceWaitAt = t;
                if (restart != null) new Thread(restart, "hmgapi-cp-rg-restart").start();
                return;
            }
            Dipo.log("CarPlay: route guidance source changed from " + was + " to " + s + ", clearing turn-by-turn");
            reset();
            hudBlank();
            if (restart != null) new Thread(restart, "hmgapi-cp-rg-restart").start();
            return;
        }
    }

    /**
     * one component's update to the cluster: 순정 DiPO e/u.a(Integer, Object)와 같게 보낸다(1.7.4, 소유자 결정 2026-11-12
     * "순정과 똑같이"). 순정이 하는 일:
     *   - 상태 6이면 목적지 이름을 갱신마다 보낸다
     *   - 현재 회전 목록의 첫 회전이 있으면 그 회전의 AfterManeuverRoadName을 교차로 이름으로 갱신마다 보낸다(멀리 있어도)
     *   - 순정 표(exactIcon)에 맞는 아이콘이 있을 때만 updateTBTInfo(아이콘, 0, 0, 거리, 0, 0, 단위, 0, 0)를 보낸다.
     *     표에 없으면 보내지 않는다(앞 화살표·거리가 계기판에 남는다). 거리 문자열이 비었거나 숫자가 아니면 0이다
     *   - 경로 상태 3(경로 계산)·5(재탐색)를 따로 다루지 않는다
     * 순정에 없고 판단을 기다리는 것(1.7.4 mod.md "판단 필요")은 지금 동작 그대로 둔다: 상태 0·연결 끝 지우기(clearTurn),
     * 다시 보내기(refresh), 출처 앱 바뀜 정리(sourceCheck), HUD 목록·2C·차로·남은 거리·다음 회전(sendHud 등).
     */
    void turn(Integer id, Update u) {
        if (id < 0 || u == null) return;
        if (u.state == 0) {
            stop();
            return;
        }
        int[] list = u.list == null ? null : u.list.clone();
        float dist = 0f;
        // 순정: 빈 문자열·숫자 아님·NaN·무한이면 0(거리 오류 칸은 늘 0)
        boolean distKnown = false;
        if (u.nextStr != null && !u.nextStr.equals("")) {
            try {
                dist = Float.parseFloat(u.nextStr);
                distKnown = !(Float.isNaN(dist) || Float.isInfinite(dist));
                if (!distKnown) dist = 0f;
            } catch (NumberFormatException e) {
                dist = 0f;
            }
        }
        long t = now();
        if (u.state == STATE_DESTINATION) {
            cluster.destinationName(0, u.destination);
            shown = true;
        }
        LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
        Maneuver x = m == null || list == null ? null : m.get(list[0]);
        if (x == null) {
            // 이름이 돌아와 회전을 다시 받는 중인데 한도 안에 오지 않으면 옛 그림을 지운다
            if (sourceWaitAt != 0L && t - sourceWaitAt >= SOURCE_WAIT_MS) {
                Dipo.log("CarPlay: no turns resent " + (t - sourceWaitAt) / 1000L + " s after the source name came back, clearing");
                sourceWaitAt = 0L;
                clearTurn();
                hudBlank();
            }
            return;
        }
        sourceWaitAt = 0L;
        int unit;
        switch (u.nextUnits) {
            case 0: unit = Cluster.UNIT_KM; break;
            case 1: unit = Cluster.UNIT_MI; break;
            case 3: unit = Cluster.UNIT_YD; break;
            case 4: unit = Cluster.UNIT_FT; break;
            default: unit = Cluster.UNIT_M; break;
        }
        int icon = exactIcon(x.type, x.side, x.junction, x.exit, u.state);
        // HUD 거리: 모르는 거리면 0 대신 첫 칸을 비운다(#123, 소유자 결정 2로 HUD는 계속 전달)
        boolean hudDistKnown = distKnown && dist >= 0f;
        logManeuver(x, u.state, icon, icon != 0 ? "stock" : "not in the stock table, not sent");
        boolean was = activeId != null;
        activeId = id;
        lastSendAt = t;
        if (!was && onActive != null) onActive.run();
        // 순정: 교차로 이름은 갱신마다(멀리 있어도, 표 밖 회전이어도)
        cluster.crossroadName(x.afterRoad == null ? "" : x.afterRoad);
        shown = true;
        // 표 밖 회전(순정은 회전 프레임을 보내지 않아 앞 화살표·거리가 남는다): HUD 목록·HUD 거리(2C)도 계기판과 함께 멈춘다
        // (리뷰 반영 2026-11-12, 계기판과 HUD가 서로 다른 회전을 보이지 않게). refresh는 마지막으로 보낸 HUD 목록을 그대로 다시 보낸다.
        // 출발(11·18)도 표 밖이라 여기서 멈춘다(2.4.1의 "출발이면 앞 앱 HUD 지우기"는 없어짐)
        if (icon != 0) {
            cluster.tbtInfo(icon, 0, 0f, dist, 0, 0, unit, 0, 0);
            // HUD 목록·2C는 소유자 결정 2(폰 정보 계속 전달)
            sendHud(icon, id, u);
            if (hudDistKnown) {
                hudDistBlank = false;
                sendHudDist(dist, unit, id, u);
            } else if (!hudDistBlank) {
                hudDistBlank = true;
                cluster.tbtSubInfo2C(HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT, HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT,
                        HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT);
            }
        }
        // 남은 거리·도착 시각·다음 회전 목록은 폰 값 그대로(표 밖 회전이어도)
        sendTrip(u);
        sendAhead(id, u);
    }

    /** 다음 회전 작은 아이콘(aheadIcon)에 그릴 것이 없을 때의 값. HUD 목록·다음 회전 목록에서만 쓴다(지금대로) */
    static final int NO_ICON = 8;

    /** the maneuver's values (numbers only: no road or instruction text) when they change, up to MANEUVER_LOGS_MAX lines */
    void logManeuver(Maneuver x, int state, int icon, String how) {
        String k = x.type + " " + x.side + " " + x.junction + " " + x.exit + " " + state + " " + icon;
        if (k.equals(maneuverKey) || maneuverLogs >= MANEUVER_LOGS_MAX) return;
        maneuverKey = k;
        maneuverLogs++;
        Dipo.log("CarPlay: maneuver type " + x.type + " side " + x.side + " junction " + x.junction + " exit " + x.exit
                + " state " + state + " -> icon " + icon + " (" + how + ")");
    }

    /**
     * 순정 DiPO 표(e/u.a의 ManeuverType 스위치, 1.7.4에서 한 줄씩 대조: drafts/1.7.4-tbt-diff.md 1장). 0이면 표에 없음 →
     * 순정처럼 updateTBTInfo를 보내지 않는다. 좌측통행(side 1) 회전교차로는 순정 B 계열(192~203).
     * 예외 하나(판단 대기, mod.md 1.7.4 "판단 필요" 1): 좌회전(1)·우회전(2)이 교차로(junction 0)에서 각도 없이(0) 오면
     * 지금처럼 70·67을 보낸다(순정 표에는 없음. 애플 지도·네이버 지도는 카플레이로 각도를 보내지 않는다).
     */
    static int exactIcon(int type, int side, int junction, int exit, int state) {
        switch (type) {
            case 1:   // left turn
                if (junction == 0 && exit == -120) return 69;
                if (side == 0 && junction == 0 && exit == -90) return 70;
                if (junction == 0 && exit == -60) return 71;
                if (junction == 0 && exit == -45) return 145;
                if (side == 1 && junction == 1 && exit == -120) return 200;
                if (side == 1 && junction == 1 && exit == -90) return 201;
                if (side == 1 && junction == 1 && exit == -30) return 203;
                if ((side == 0 || side == 1) && junction == 0 && exit == 0) return 70;   // 판단 대기(위 예외)
                return 0;
            case 2:   // right turn
                if (junction == 0 && exit == 60) return 66;
                if (side == 0 && junction == 0 && exit == 90) return 67;
                if (junction == 0 && exit == 120) return 68;
                if (side == 0 && junction == 1 && exit == 0) return 96;
                if (side == 1 && junction == 1 && exit == 0) return 193;
                if (side == 1 && junction == 1 && exit == 90) return 195;
                if (side == 1 && junction == 1 && exit == 120) return 196;
                if (side == 1 && junction == 1 && exit == 180) return 198;
                if (side == 0 && junction == 0 && exit == 45) return 144;
                if ((side == 0 || side == 1) && junction == 0 && exit == 0) return 67;   // 판단 대기(위 예외)
                return 0;
            case 3:   // straight
                return side == 0 && junction == 0 && exit == 0 ? 65 : 0;
            case 4:   // U-turn
                if (side == 0 && junction == 0) return 72;
                if (side == 1 && junction == 0) return 73;
                return 0;
            case 5:   // follow road
                return side == 0 && junction == 0 && exit == 0 ? 172 : 0;
            case 9:   // on-ramp: 160 in the Korean stock DiPO (the US build had 172)
                return side == 0 && junction == 0 && exit == 0 ? 160 : 0;
            case 12: {   // arrive; state 2 (arrived): 3
                int i = side == 0 && junction == 0 ? 112 : 0;
                return state == 2 ? 3 : i;
            }
            case 15: return side == 0 && junction == 0 && exit == 0 ? 176 : 0;
            case 19: return side == 0 && junction == 1 && exit == -180 ? 102 : 0;
            // highway off-ramp left / right: 165 / 164 in the Korean stock DiPO (the US build had 82 / 81)
            case 22: return side == 0 && junction == 0 && exit == 0 ? 165 : 0;
            case 23: return side == 0 && junction == 0 && exit == 0 ? 164 : 0;
            case 24: return side == 0 && junction == 0 && exit == 0 ? 114 : 0;
            case 25: return side == 0 && junction == 0 && exit == 0 ? 113 : 0;
            case 28: return side == 0 && junction == 1 && exit == 135 ? 100 : 0;
            case 29: return side == 0 && junction == 1 && exit == 90 ? 99 : 0;
            case 31:
                if (side == 0 && junction == 1 && exit == 0) return 97;
                if (side == 1 && junction == 1 && exit == 0) return 192;
                return 0;
            case 32: return side == 0 && junction == 1 && exit == -45 ? 104 : 0;
            case 33: return side == 0 && junction == 1 && exit == -90 ? 105 : 0;
            case 34: return side == 0 && junction == 1 && exit == -135 ? 104 : 0;
            case 35: return side == 0 && junction == 1 && exit == -180 ? 102 : 0;
            case 47:
                if (junction == 0 && exit <= -135 && exit >= -150) return 69;
                if (junction == 0 && exit == -30) return 71;
                return 0;
            case 48:
                if (junction == 0 && exit == 30) return 66;
                if (junction == 0 && exit >= 135 && exit <= 150) return 68;
                return 0;
            case 49: return junction == 0 && exit == -45 ? 71 : 0;
            case 50: return junction == 0 && exit == 45 ? 66 : 0;
            case 52: return side == 0 && junction == 0 && exit == 0 ? 174 : 0;
            case 53: return side == 0 && junction == 0 && exit == 0 ? 173 : 0;
            default: return 0;
        }
    }

    /**
     * an upcoming turn's small icon (HUD list and turn list): 65 straight, 66-68 right, 69-71 left,
     * 72/73 U-turn, else NO_ICON 8 (Korea Projection 2.3.0: was 64, a loading glyph on Gen5W; only
     * HMG-API sends this slot, the stock DiPO sends no turn list)
     */
    static int aheadIcon(int type, int side, int junction, int exit) {
        if (type == 1) {
            if (exit == -120 && junction == 0) return 69;
            if (exit == -90 && side == 0 && junction == 0) return 70;
            if (exit == -60 && junction == 0) return 71;
            return 70;
        }
        if (type == 2) {
            if (exit == 60 && junction == 0) return 66;
            if (exit == 90 && side == 0 && junction == 0) return 67;
            if (exit == 120 && junction == 0) return 68;
            return 67;
        }
        if (type == 3) return 65;
        if (type == 4) return side == 0 ? 72 : 73;
        if (type == 47) return 69;   // sharp left (47-50 from HMG-Seogaemo)
        if (type == 48) return 68;   // sharp right
        if (type == 49) return 71;   // slight left
        if (type == 50) return 66;   // slight right
        return NO_ICON;
    }

    /** HUD 목록 이름의 최대 바이트(UTF-16LE). 순정 내비는 이름 칸을 0x50바이트까지 쓴다(drafts/stock-re 3-3) */
    static final int HUD_NAME_MAX = 78;

    /**
     * UTF-16LE로 max 바이트 안에 드는 이름 앞부분. 글자 경계에서 자르고, 서로게이트 쌍(이모지 등)의
     * 앞 반쪽만 남기지 않는다. 1.7.2(M-15). AaTurns.hudName과 같다(클래스 로더가 달라 두 벌)
     */
    static String hudName(String s, int max) {
        int n = Math.min(s.length(), max / 2);
        if (n > 0 && n < s.length() && Character.isHighSurrogate(s.charAt(n - 1)) && Character.isLowSurrogate(s.charAt(n))) n--;
        return s.substring(0, n);
    }

    /** the HUD's list frame (0xF2): count, this icon, the next turn's icon, 0, road after this turn (UTF-16LE) */
    void sendHud(int type, Integer id, Update u) {
        if (type < 64) return;
        int next = 0, third = 0;
        String road = "";
        LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
        int[] list = u == null ? null : u.list;
        if (m != null && list != null && list.length > 0) {
            Maneuver x = m.get(list[0]);
            if (x != null && x.afterRoad != null) road = x.afterRoad;
            if (list.length > 1) {
                Maneuver y = m.get(list[1]);
                if (y != null && y.distance > 0) next = aheadIcon(y.type, y.side, y.junction, y.exit);
                // no arrow for the next turn: the list has this turn only (count 1)
                if (next == NO_ICON) next = 0;
            }
            // 셋째 칸(M-2): 순정은 아이콘을 3칸까지 채운다. 아이콘이 없는 칸은 0(그 자리 비움)
            if (list.length > 2) {
                Maneuver z = m.get(list[2]);
                if (z != null && z.distance > 0) third = aheadIcon(z.type, z.side, z.junction, z.exit);
                if (third == NO_ICON) third = 0;
            }
        }
        if (hudType == type && hudNext == next && hudThird == third && hudRoad != null && hudRoad.equals(road)) return;
        byte[] r;
        try {
            r = hudName(road, HUD_NAME_MAX).getBytes("UTF-16LE");
        } catch (java.io.UnsupportedEncodingException e) {
            return;
        }
        int n = r.length;
        byte[] f = new byte[n + 5];
        f[0] = (byte) 0xF2;
        f[1] = (byte) (1 + (next != 0 ? 1 : 0) + (third != 0 ? 1 : 0));
        f[2] = (byte) type;
        f[3] = (byte) next;
        f[4] = (byte) third;
        System.arraycopy(r, 0, f, 5, n);
        Dipo.log("CarPlay: hud list type " + type + " next " + next + " third " + third);
        cluster.turnListNew(f);
        hudShown = true;
        lastHud = f;
        hudAt = now();
        hudType = type;
        hudNext = next;
        hudThird = third;
        hudRoad = road;
    }

    /** the 2C value for "no point" and for guidance over, as the stock navigation sends it */
    static final int HUD_NO_POINT = 65535;

    /**
     * The HUD's distances (updateTBTSubInfo2C: unit, tenths for km/mi, whole; for the next three
     * points). The 1st is the distance to this maneuver as the phone shows it, but under 1 km in
     * metres, as the stock navigation sends it. The 2nd and 3rd (Korea Projection 2.3.0, 1.7.1 map
     * 2-H) add the next maneuvers' distances in the current list (iAP2 DistanceBetweenManeuver,
     * metres, taken as the distance from the maneuver before) to the distance to this maneuver in
     * metres (item 10), only when the phone's last update carried it, in the owner's units; a point
     * the phone hasn't given is HUD_NO_POINT, as in the stock navigation.
     */
    void sendHudDist(float f, int unit, Integer id, Update u) {
        if (f < 0f || unit < 0 || unit > 4) return;
        int[] p1;
        if (unit == Cluster.UNIT_KM && f < 1f) {
            p1 = new int[] { Cluster.UNIT_M, 0, (int) (f * 1000f + 0.5f) };
        } else {
            int whole = (int) f;
            if (whole > 4095) whole = 4095;
            int tenths = 0;
            if (unit == Cluster.UNIT_KM || unit == Cluster.UNIT_MI) {
                tenths = (int) ((f - whole) * 10f + 0.5f);
                if (tenths < 0) tenths = 0;
                if (tenths > 9) tenths = 9;
            }
            p1 = new int[] { unit, tenths, whole };
        }
        int[] p2 = { 0, 0, HUD_NO_POINT }, p3 = { 0, 0, HUD_NO_POINT };
        LinkedHashMap<Integer, Maneuver> m = id == null ? null : maneuvers.get(id);
        int[] list = u == null ? null : u.list;
        if (m != null && list != null && list.length > 1) {
            int owner = Car.distanceUnit(context);
            boolean miles = owner == Car.DISTANCE_MILES
                    || (owner != Car.DISTANCE_KM && (unit == Cluster.UNIT_MI || unit == Cluster.UNIT_FT || unit == Cluster.UNIT_YD));
            long at = u.distNext;
            Maneuver y = m.get(list[1]);
            // only with the distance to this maneuver from the phone's last update (item 10), not an older one
            if (u.distNextFresh && at >= 0 && y != null && y.distance > 0) {
                at += y.distance;
                p2 = hudPoint(at, miles);
                Maneuver z = list.length > 2 ? m.get(list[2]) : null;
                if (z != null && z.distance > 0) p3 = hudPoint(at + z.distance, miles);
            }
        }
        cluster.tbtSubInfo2C(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2]);
        hudShown = true;
    }

    /** one 2C point {unit, tenths, whole}: m under 1 km, else km (miles: ft under 161 m, else mi), whole up to 65534 */
    static int[] hudPoint(long m, boolean miles) {
        int unit, tenths = 0;
        long whole;
        if (miles) {
            if (m < 161) {
                unit = Cluster.UNIT_FT;
                whole = m * 3281 / 1000;
            } else {
                long t = m * 10 / 1609;
                unit = Cluster.UNIT_MI;
                whole = t / 10;
                tenths = (int) (t % 10);
            }
        } else if (m < 1000) {
            unit = Cluster.UNIT_M;
            whole = m;
        } else {
            unit = Cluster.UNIT_KM;
            whole = m / 1000;
            tenths = (int) (m % 1000 / 100);
        }
        if (whole > 65534) {
            whole = 65534;
            tenths = 0;
        }
        return new int[] { unit, tenths, (int) whole };
    }

    /** HUD list and extras cleared (the build's sendHudBlank) */
    void hudBlank() {
        hudType = -1;
        hudNext = 0;
        hudThird = 0;
        hudRoad = null;
        lastHud = null;
        // 카플레이가 HUD 목록·차로·2C를 보낸 적이 없으면 지우지 않는다: 안내하지 않던 카플레이가 순정 내비의
        // HUD·차로를 지우지 않게(2차 리뷰 2). 보낸 뒤에는 한 번 지우고 다시 보낼 때까지 지우지 않는다.
        if (!hudShown) return;
        hudShown = false;
        // 세 칸 모두 순정 엔진의 지우기 값(단위 15, 소수 15, 정수 65535)으로 보낸다(M-2, 2.3.0까지는 단위·소수 0)
        cluster.tbtSubInfo2C(HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT, HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT,
                HUD_CLEAR_UNIT, HUD_CLEAR_TENTHS, HUD_NO_POINT);
        byte[] lanes = new byte[81];
        lanes[0] = (byte) 0xF1;
        cluster.laneInfo(lanes);
        // 빈 목록은 순정과 같은 {F2, 00}(M-2)
        byte[] empty = { (byte) 0xF2, 0 };
        cluster.turnListNew(empty);
        cluster.turnListNew(empty);
    }

    /**
     * Remaining distance and arrival time (updateTBTSubInfo). Distance in the owner's
     * units (Settings), else the phone's. Arrival: a clock time (seconds since 2001 or
     * 1970) or, failing that, now + the time remaining (up to a week).
     */
    void sendTrip(Update u) {
        int d = u.distLeft, units = u.distLeftUnits;
        int whole = 0, tenths = 0, unit = Cluster.UNIT_M;
        if (d > 0) {
            whole = d;
            int owner = Car.distanceUnit(context);
            if (owner == Car.DISTANCE_KM) units = d < 1000 ? 2 : 0;
            else if (owner == Car.DISTANCE_MILES) units = d < 161 ? 4 : 1;
            switch (units) {
                case 0:
                    whole = d / 1000;
                    tenths = (d % 1000) / 100;
                    unit = Cluster.UNIT_KM;
                    break;
                case 1: {
                    int t = d * 10 / 1609;
                    tenths = t % 10;
                    whole = t / 10;
                    unit = Cluster.UNIT_MI;
                    break;
                }
                case 2:
                    break;
                case 3:
                    whole = d * 1094 / 1000;
                    unit = Cluster.UNIT_YD;
                    break;
                case 4:
                    whole = d * 3281 / 1000;
                    unit = Cluster.UNIT_FT;
                    break;
                default:
                    if (d >= 1000) {
                        whole = d / 1000;
                        tenths = (d % 1000) / 100;
                        unit = Cluster.UNIT_KM;
                    }
                    break;
            }
        }
        int hour = -1, minute = -1, timeType = -1, timeFormat;
        Calendar cal = null;
        long eta = u.eta;
        if (eta < 500000000L) {
            long left = u.timeLeft;
            if (left > 0 && left <= 604800L) {
                cal = Calendar.getInstance();
                cal.add(Calendar.SECOND, (int) left);
            }
        } else {
            if (eta < 1500000000L) eta += 978307200L;   // Apple's 2001 epoch
            cal = Calendar.getInstance();
            cal.setTimeInMillis(eta * 1000L);
        }
        if (cal != null) {
            hour = cal.get(Calendar.HOUR_OF_DAY);
            minute = cal.get(Calendar.MINUTE);
            timeType = 1;
            timeFormat = context == null || DateFormat.is24HourFormat(context) ? 2 : 1;
        } else {
            timeFormat = -1;
        }
        if (d <= 0 && timeType < 0) return;
        cluster.tbtSubInfo(whole + tenths / 10f, d > 0 ? 0 : -1, unit, hour, minute, timeType, timeType < 0 ? -1 : timeFormat, 0f, -1, 0);
    }

    /** up to 3 upcoming turns after this one (updateTPTurnList), distances in the owner's units */
    void sendAhead(Integer id, Update u) {
        int[] list = u == null ? null : u.list;
        if (id == null || list == null || list.length <= 1) return;
        LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
        if (m == null) return;
        int count = 0;
        for (int k = 1; k < list.length && count < 3; k++) {
            Maneuver x = m.get(list[k]);
            if (x != null && x.distance > 0) count++;
        }
        if (count == 0) return;
        int owner = Car.distanceUnit(context);
        Cluster.Turn[] turns = new Cluster.Turn[count];
        int n = 0;
        for (int k = 1; k < list.length && n < 3; k++) {
            Maneuver x = m.get(list[k]);
            if (x == null || x.distance <= 0 || n >= count) continue;
            Cluster.Turn t = new Cluster.Turn();
            t.type = aheadIcon(x.type, x.side, x.junction, x.exit);
            t.direction = 0;
            t.traffic = 0;
            t.no = n + 1;
            t.name = x.afterRoad == null ? "" : x.afterRoad;
            t.exitNumber = "";
            int d = x.distance;
            float f = d;
            int unit = Cluster.UNIT_M;
            if (owner == Car.DISTANCE_KM) {
                if (d >= 1000) {
                    unit = Cluster.UNIT_KM;
                    f = d / 1000f;
                }
            } else if (owner == Car.DISTANCE_MILES) {
                if (d < 161) {
                    unit = Cluster.UNIT_FT;
                    f = d * 3.28084f;
                } else {
                    unit = Cluster.UNIT_MI;
                    f = d / 1609f;
                }
            }
            if (f < 0f) f = 0f;
            if (f > 65535f) f = 65535f;
            t.distance = f;
            t.unit = unit;
            turns[n++] = t;
        }
        if (n > 0) cluster.turnList(turns);
    }

    /** one display component's route guidance state (DiPO e/c) */
    static final class Update {
        int state, maneuverState, distLeft, distLeftUnits, distNext, nextUnits, count, laneIndex, laneCount;
        /** the last update carried distNext (item 10); else the HUD's 2nd and 3rd points stay empty */
        boolean distNextFresh;
        /** item 10(다음 회전까지 m)을 한 번이라도 받았는지(곧 회전 화면 판단, M-4) */
        boolean distNextSeen;
        String road, destination, distLeftStr, nextStr, sourceName;
        long eta, timeLeft;
        int[] list;
        boolean visible, lanesShown, supports;

        /** as DiPO: a component id of 0 or less starts with nothing set (not even the defaults) */
        Update(int id, Iap2NotiMsg msg) {
            if (msg == null || id <= 0) return;
            clear();
            read(id, msg);
        }

        void clear() {
            state = 0;
            maneuverState = 1;
            road = "";
            destination = "";
            eta = 0;
            timeLeft = 0;
            distLeft = 0;
            distLeftStr = "";
            distLeftUnits = 0;
            distNext = 0;
            distNextFresh = false;
            distNextSeen = false;
            nextStr = "";
            nextUnits = 0;
            list = null;
            count = 0;
            visible = false;
            laneIndex = 0;
            laneCount = 0;
            lanesShown = false;
            sourceName = "";
            supports = false;
        }

        /** the fields this message carries (by iAP2 type: 1/5 uint8/16, 2/6 uint32, 3/7 uint64, 8 enum, 9 bool, 10 string) */
        void read(int id, Iap2NotiMsg msg) {
            Iap2NotiMsg.Iap2ObjGroup g = msg == null ? null : msg.getItemGroup();
            if (g == null) return;
            distNextFresh = false;
            for (int k = 0; k < g.getItemCnt(); k++) {
                Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
                int item = it.getItemId(), n = it.getItemObjCount();
                switch (it.getItemParamType()) {
                    case 1:
                    case 5: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        switch (item) {
                            case 13:
                                if (v != null) {
                                    list = new int[n];
                                    for (int j = 0; j < n; j++) list[j] = v[j];
                                } else {
                                    list = null;
                                }
                                break;
                            case 14: count = v != null ? v[0] : 0; break;
                            case 16: laneIndex = v != null ? v[0] : 0; break;
                            case 17: laneCount = v != null ? v[0] : 0; break;
                        }
                        break;
                    }
                    case 2:
                    case 6: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (v == null) break;
                        if (item == 7) distLeft = v[0];
                        else if (item == 10) {
                            distNext = v[0];
                            distNextFresh = true;
                            distNextSeen = true;
                        }
                        break;
                    }
                    case 3:
                    case 7: {
                        long[] v = n > 0 ? it.getItemLongArray() : null;
                        if (v == null) break;
                        if (item == 5) eta = v[0];
                        else if (item == 6) timeLeft = v[0];
                        break;
                    }
                    case 8: {
                        int[] v = it.getItemIntArray();
                        if (v == null) break;
                        switch (item) {
                            case 1: state = v[0]; break;
                            case 2: maneuverState = v[0]; break;
                            case 9: distLeftUnits = v[0]; break;
                            case 12: nextUnits = v[0]; break;
                        }
                        break;
                    }
                    case 9: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (v == null) break;
                        if (item == 15) visible = v[0] != 0;
                        else if (item == 18) lanesShown = v[0] != 0;
                        else if (item == 20) supports = v[0] != 0;
                        break;
                    }
                    case 10: {
                        String[] v = n > 0 ? it.getItemStringArray() : null;
                        if (v == null) break;
                        switch (item) {
                            case 3: road = v[0]; break;
                            case 4: destination = v[0]; break;
                            case 8: distLeftStr = v[0]; break;
                            case 11: nextStr = v[0]; break;
                            case 19: sourceName = v[0]; break;
                        }
                        break;
                    }
                }
            }
        }
    }

    /** one maneuver (DiPO e/v) */
    static final class Maneuver {
        int index, type, distance, distUnits, side, junction, exit, linkedLane;
        /** 이 회전에 링크한 차로 안내(item 12)가 있다(M-4) */
        boolean hasLane;
        String description, afterRoad, distStr;
        int[] angles;

        Maneuver(int index, int id, Iap2NotiMsg msg) {
            if (msg == null) return;
            clear();
            read(index, id, msg);
        }

        void clear() {
            index = -1;
            description = "";
            type = 0;
            afterRoad = "";
            distance = 0;
            distStr = "";
            distUnits = 2;
            side = 0;
            junction = 0;
            angles = null;
            exit = 0;
            linkedLane = 0;
            hasLane = false;
        }

        /**
         * DiPO reset the exit angle with every update, so an update carrying only the distance
         * left the turn with no angle and no icon. Now a partial update (neither the type nor the
         * angle in it) keeps the angle. One that names the type again is taken as the whole
         * maneuver (the phone may reuse an index for a new one), so without an angle it has none.
         */
        void read(int index, int id, Iap2NotiMsg msg) {
            Iap2NotiMsg.Iap2ObjGroup g = msg.getItemGroup();
            this.index = index;
            int wasExit = exit;
            boolean hasExit = false, hasType = false, hasAngles = false, hasLinked = false;
            exit = 0;
            for (int k = 0; k < g.getItemCnt(); k++) {
                Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
                int item = it.getItemId(), n = it.getItemObjCount();
                switch (it.getItemParamType()) {
                    case 1: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (item == 10) {
                            hasAngles = true;
                            if (v != null) {
                                angles = new int[n];
                                for (int j = 0; j < n; j++) angles[j] = v[j];
                            } else {
                                angles = null;
                            }
                        } else if (item == 11 && v != null) {
                            exit = v[0];
                            hasExit = true;
                        }
                        break;
                    }
                    case 5: {
                        int[] v = it.getItemIntArray();
                        if (item == 12 && v != null && v.length > 0) {
                            linkedLane = v[0];
                            hasLinked = true;
                        }
                        break;
                    }
                    case 6: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (item == 5 && v != null) distance = v[0];
                        break;
                    }
                    case 8: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (v == null) break;
                        switch (item) {
                            case 3: type = v[0]; hasType = true; break;
                            case 7: distUnits = v[0]; break;
                            case 8: side = v[0]; break;
                            case 9: junction = v[0]; break;
                        }
                        break;
                    }
                    case 10: {
                        String[] v = it.getItemStringArray();
                        if (v == null) break;
                        switch (item) {
                            case 2: description = v[0]; break;
                            case 4: afterRoad = v[0]; break;
                            case 6: distStr = v[0]; break;
                        }
                        break;
                    }
                }
            }
            if (!hasExit && !hasType) exit = wasExit;
            // 종류를 다시 말한 갱신은 회전 전체다(위와 같음): 그 안에 없는 다른 길 각도·차로 링크는 없는 것으로 본다(M-4)
            if (hasType && !hasAngles) angles = null;
            if (hasLinked) hasLane = true;
            else if (hasType) hasLane = false;
        }
    }
}
