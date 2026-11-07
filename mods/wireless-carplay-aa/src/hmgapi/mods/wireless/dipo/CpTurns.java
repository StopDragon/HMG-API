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
    /** last HUD list sent (type, next, road), so an unchanged one isn't resent */
    int hudType, hudNext;
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
    /** the crossroad name last sent and when; resent only when it changes or after CROSSROAD_MS */
    String crossroad;
    long crossroadAt;
    static final long CROSSROAD_MS = 10000L;
    /** maneuver log: the last values logged and how many lines so far (numbers only, no names) */
    String maneuverKey;
    int maneuverLogs;
    static final int MANEUVER_LOGS_MAX = 300;
    /** the time (ms since boot: the wall clock jumps when GPS or the network sets it); tests set clock */
    long clock = -1L;
    /** the destination name last sent (state 6) and when, as the crossroad name */
    String destination;
    long destinationAt;

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
        for (int k = 0; k < g.getItemCnt(); k++) {
            Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
            if (it != null && it.getItemParamType() == 13 && it.getItemId() == 2) lanes = it.getItemGroupArray();
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
        hudBlank();
        source = null;
    }

    /** route guidance stopped (DiPO e/u.nS: the car's navigation took turn-by-turn back, or state 0) */
    void stop() {
        activeId = null;
        hudBlank();
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
            if (was == null || was.equals(s)) return;
            Dipo.log("CarPlay: route guidance source changed from " + was + " to " + s + ", clearing turn-by-turn");
            reset();
            hudBlank();
            if (restart != null) new Thread(restart, "hmgapi-cp-rg-restart").start();
            return;
        }
    }

    /** one component's update to the cluster (DiPO e/u.a(Integer, Object), with the build's changes) */
    void turn(Integer id, Update u) {
        if (id < 0 || u == null) return;
        if (u.state == 0) {
            stop();
            return;
        }
        int[] list = u.list == null ? null : u.list.clone();
        float dist = 0f;
        if (u.nextStr != null && !u.nextStr.equals("")) {
            try {
                dist = Float.parseFloat(u.nextStr);
                if (Float.isNaN(dist) || Float.isInfinite(dist)) dist = 0f;
            } catch (NumberFormatException e) {
                dist = 0f;
            }
        }
        long t = now();
        String dest = u.destination == null ? "" : u.destination;
        if (u.state == 6 && (!dest.equals(destination) || t - destinationAt >= CROSSROAD_MS)) {
            cluster.destinationName(0, u.destination);
            destination = dest;
            destinationAt = t;
        }
        LinkedHashMap<Integer, Maneuver> m = maneuvers.get(id);
        if (m == null || list == null) return;
        Maneuver x = m.get(list[0]);
        if (x == null) return;
        int unit;
        switch (u.nextUnits) {
            case 0: unit = Cluster.UNIT_KM; break;
            case 1: unit = Cluster.UNIT_MI; break;
            case 3: unit = Cluster.UNIT_YD; break;
            case 4: unit = Cluster.UNIT_FT; break;
            default: unit = Cluster.UNIT_M; break;
        }
        int icon = icon(x.type, x.side, x.junction, x.exit, u.state);
        // StartRoute has no direction: clear the previous app's HUD arrow (upstream 83eb4ef;
        // once, so refresh() doesn't clear it again every second)
        if (x.type == 11 && icon == 1 && hudType != -1) hudBlank();
        String how = icon == 0 ? null : exactIcon(x.type, x.side, x.junction, x.exit, u.state) != 0 ? "exact" : "band";
        if (icon == 0) {
            icon = fallbackIcon(x.type, x.side, x.junction, x.exit);
            how = icon == NO_ICON ? "blank" : x.exit != 0 ? "angle" : "type";
        }
        logManeuver(x, u.state, icon, how);
        boolean was = activeId != null;
        activeId = id;
        lastSendAt = t;
        if (!was && onActive != null) onActive.run();
        String road = x.afterRoad == null ? "" : x.afterRoad;
        if (!road.equals(crossroad) || t - crossroadAt >= CROSSROAD_MS) {
            cluster.crossroadName(x.afterRoad);
            crossroad = road;
            crossroadAt = t;
        }
        cluster.tbtInfo(icon, 0, 0f, dist, 0, 0, unit, 0, 0);
        sendHud(icon, id, u);
        sendHudDist(dist, unit, id, u);
        sendTrip(u);
        sendAhead(id, u);
    }

    /**
     * The cluster icon for a maneuver: iAP2 ManeuverType, driving side (0 right-hand
     * traffic, 1 left), junction type (0 intersection, 1 roundabout) and exit angle, as
     * the turn-by-turn build maps them, then a plain turn at an angle the table doesn't list
     * (bandedTurn). 0: neither; turn() then uses fallbackIcon.
     */
    static int icon(int type, int side, int junction, int exit, int state) {
        int i = exactIcon(type, side, junction, exit, state);
        return i != 0 ? i : bandedTurn(type, junction, exit);
    }

    /**
     * The cluster's "no turn icon": distances, lanes and trip still go out. Gen5W firmware
     * (libExSLNavi RGIconIDToClusterDispType) falls back to 8; 64 showed as a spinning
     * "loading" glyph. Below the HUD list's 64, so sendHud blanks the HUD list instead
     * (from HMG-Seogaemo).
     */
    static final int NO_ICON = 8;

    /**
     * A plain intersection turn at an angle the exact table doesn't list (75 degrees, 30,
     * left-hand traffic ...): the nearest of the three basic arrows. Slight up to 67 degrees,
     * turn up to 112, sharp past that (the lane arrows' bands); a type that says sharp or
     * slight keeps that. No angle (0) or a roundabout: 0 (from HMG-Seogaemo).
     */
    static int bandedTurn(int type, int junction, int exit) {
        if (junction != 0 || exit == 0) return 0;
        boolean left;
        switch (type) {
            case 1: case 47: case 49: left = true; break;
            case 2: case 48: case 50: left = false; break;
            default: return 0;
        }
        int a = Math.abs(exit);
        if (a > 180) a = 360 - a;
        int sharpness = type == 47 || type == 48 ? 2 : type == 49 || type == 50 ? 0 : a <= 67 ? 0 : a <= 112 ? 1 : 2;
        // slight 66/71, turn 67/70, sharp 68/69 (right/left)
        return left ? 71 - sharpness : 66 + sharpness;
    }

    /**
     * An icon for a maneuver neither table lists, so the rest still goes out. A misleading
     * arrow is worse than none: with an exit angle, the nearest direction arrow; a type with a
     * clear meaning but no angle (Apple Maps and Naver Map in Korea send none), its own icon;
     * else NO_ICON, never "straight" for a turn (from HMG-Seogaemo).
     */
    static int fallbackIcon(int type, int side, int junction, int exit) {
        // departure (StartRoute) is no turn, whatever angle comes with it (upstream 83eb4ef)
        if (type == 11) return NO_ICON;
        // Korea Projection 2.3.0: the Korean stock numbers by type, whatever the angle (1.7.1 map 2-A):
        // types the stock DiPO table leaves out (it then sends nothing and the previous arrow stays),
        // with numbers the stock engine itself uses, and the stock ramps without their exact geometry
        switch (type) {
            case 8: case 51: return 163;        // off-ramp, change highway: highway exit, straight
            case 9: return 160;                 // on-ramp
            case 22: return 165;                // highway off-ramp left
            case 23: return 164;                // highway off-ramp right
            case 16: case 17: return 176;       // exit / change ferry
            case 20: return 70;                 // left turn at the end of the road
            case 21: return 67;                 // right turn at the end of the road
            case 26: return side == 1 ? 73 : 72; // U-turn when possible
            case 27: return 112;                // end of directions: destination ahead
            default: break;
        }
        int a = bandedTurn(type, junction, exit);
        if (a != 0) return a;
        if (exit != 0) {
            if (junction != 0) return roundaboutIcon(side, exit);
            boolean left;
            switch (type) {
                case 1: case 47: case 49: case 13: left = true; break;
                case 2: case 48: case 50: case 14: left = false; break;
                default: left = exit < 0; break;
            }
            int deg = Math.abs(exit);
            if (deg > 180) deg = 360 - deg;
            if (deg >= 157) return side == 0 ? 72 : 73;
            if (deg <= 22 && type != 1 && type != 2) return 65;
            int sharpness = deg <= 67 ? 0 : deg <= 112 ? 1 : 2;
            return left ? 71 - sharpness : 66 + sharpness;
        }
        switch (type) {
            case 1: return 70;    // left turn
            case 2: return 67;    // right turn
            case 47: return 69;   // sharp left
            case 48: return 68;   // sharp right
            case 49: return 71;   // slight left
            case 50: return 66;   // slight right
            case 13: return 71;   // keep left
            case 14: return 66;   // keep right
            case 3: return 65;
            case 4: case 18: return side == 0 ? 72 : 73;
            case 5: return 172;
            case 6: return side == 0 ? 96 : 97;   // enter a roundabout
            case 10: case 12: return 112;
            case 15: return 176;
            default: return NO_ICON;       // 0 no turn, 7 exit roundabout, 11 start ...
        }
    }

    /**
     * A roundabout exit the exact table doesn't list for this type: first the exit angles the
     * stock DiPO table uses, with its icons (right-hand traffic: 0 96, 90 99, 135 100, 180 102,
     * -45 104, -90 105, -135 104; left-hand traffic: its side-1 values), then the nearest of the
     * stock engine's 8 roundabout directions (A family; 1.7.1 map 2-C): 45-degree bands centred on
     * 0, 45, 90 ... Left-hand traffic keeps the A family (B 192-203 can't be checked in Korea).
     */
    static int roundaboutIcon(int side, int exit) {
        int e = exit;
        if (e > 180) e -= 360;
        else if (e < -180) e += 360;
        if (side == 1) {
            switch (e) {
                case 0: return 97;
                case 90: return 99;
                case 120: return 100;
                case 180: case -180: return 102;
                case -120: return 104;
                case -90: return 105;
                case -30: return 107;
                default: break;
            }
        } else {
            switch (e) {
                case 0: return 96;
                case 90: return 99;
                case 135: return 100;
                case 180: case -180: return 102;
                case -45: return 104;
                case -90: return 105;
                case -135: return 104;
                default: break;
            }
        }
        int a = Math.abs(e);
        if (a <= 22) return 96;
        if (a >= 158) return 102;
        if (e > 0) return a <= 67 ? 97 : a <= 112 ? 99 : 100;
        return a <= 67 ? 107 : a <= 112 ? 105 : 104;
    }

    /** the maneuver's values (numbers only: no road or instruction text) when they change, up to MANEUVER_LOGS_MAX lines */
    void logManeuver(Maneuver x, int state, int icon, String how) {
        String k = x.type + " " + x.side + " " + x.junction + " " + x.exit + " " + state + " " + icon;
        if (k.equals(maneuverKey) || maneuverLogs >= MANEUVER_LOGS_MAX) return;
        maneuverKey = k;
        maneuverLogs++;
        Dipo.log("CarPlay: maneuver type " + x.type + " side " + x.side + " junction " + x.junction + " exit " + x.exit
                + " state " + state + " -> icon " + icon + " (" + how + ")");
    }

    /** the turn-by-turn build's exact table (0: not in it) */
    static int exactIcon(int type, int side, int junction, int exit, int state) {
        switch (type) {
            // Ordinary left/right types carry the direction even when the optional
            // exit angle is absent (the parser resets it to zero on every update).
            case 1:   // left turn
                if (junction == 0 && exit == -120) return 69;
                if ((side == 0 || side == 1) && junction == 0 && (exit == -90 || exit == 0)) return 70;
                if (junction == 0 && exit == -60) return 71;
                if (junction == 0 && exit == -45) return 145;
                if (side == 1 && junction == 1 && exit == -120) return 104;
                if (side == 1 && junction == 1 && exit == -90) return 105;
                if (side == 1 && junction == 1 && exit == -30) return 107;
                return 0;
            case 2:   // right turn
                if (junction == 0 && exit == 60) return 66;
                if ((side == 0 || side == 1) && junction == 0 && (exit == 90 || exit == 0)) return 67;
                if (junction == 0 && exit == 120) return 68;
                if (side == 0 && junction == 1 && exit == 0) return 96;
                if (side == 1 && junction == 1 && exit == 0) return 97;
                if (side == 1 && junction == 1 && exit == 90) return 99;
                if (side == 1 && junction == 1 && exit == 120) return 100;
                if (side == 1 && junction == 1 && exit == 180) return 102;
                if (side == 0 && junction == 0 && exit == 45) return 144;
                return 0;
            case 3:   // straight
                return (side == 0 || side == 1) && junction == 0 && exit == 0 ? 65 : 0;
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
            case 11:  // departure: NO_INFO, not a turn inferred from missing geometry
                return (side == 0 || side == 1) && junction == 0 && exit == 0 ? 1 : 0;
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
                if (side == 1 && junction == 1 && exit == 0) return 96;
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

    /** the HUD's list frame (0xF2): count, this icon, the next turn's icon, 0, road after this turn (UTF-16LE) */
    void sendHud(int type, Integer id, Update u) {
        if (type == NO_ICON) {
            // no arrow for this maneuver: the empty list blanks the HUD's turn, so the previous
            // turn's arrow doesn't stay up (from HMG-Seogaemo, seen on their car 2026-11-05)
            if (lastHud != null && lastHud.length == 1) return;
            byte[] empty = { (byte) 0xF2 };
            cluster.turnListNew(empty);
            lastHud = empty;
            hudAt = now();
            hudType = -1;
            hudNext = 0;
            hudRoad = null;
            return;
        }
        if (type < 64) return;
        int next = 0;
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
        }
        if (hudType == type && hudNext == next && hudRoad != null && hudRoad.equals(road)) return;
        byte[] r;
        try {
            r = road.getBytes("UTF-16LE");
        } catch (java.io.UnsupportedEncodingException e) {
            return;
        }
        int n = Math.min(r.length, 78);
        byte[] f = new byte[n + 5];
        f[0] = (byte) 0xF2;
        f[1] = (byte) (next != 0 ? 2 : 1);
        f[2] = (byte) type;
        f[3] = (byte) next;
        System.arraycopy(r, 0, f, 5, n);
        Dipo.log("CarPlay: hud list type " + type + " next " + next);
        cluster.turnListNew(f);
        lastHud = f;
        hudAt = now();
        hudType = type;
        hudNext = next;
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
        // no points: 65535 as the stock navigation sends when guidance ends (Korea Projection 2.3.0; was 0)
        cluster.tbtSubInfo2C(0, 0, HUD_NO_POINT, 0, 0, HUD_NO_POINT, 0, 0, HUD_NO_POINT);
        byte[] lanes = new byte[81];
        lanes[0] = (byte) 0xF1;
        cluster.laneInfo(lanes);
        hudType = -1;
        hudNext = 0;
        hudRoad = null;
        lastHud = null;
        crossroad = null;
        destination = null;
        byte[] empty = { (byte) 0xF2 };
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
            boolean hasExit = false, hasType = false;
            exit = 0;
            for (int k = 0; k < g.getItemCnt(); k++) {
                Iap2NotiMsg.Iap2ObjItem it = g.getObjItem(k);
                int item = it.getItemId(), n = it.getItemObjCount();
                switch (it.getItemParamType()) {
                    case 1: {
                        int[] v = n > 0 ? it.getItemIntArray() : null;
                        if (item == 10) {
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
                        if (item == 12 && v != null) linkedLane = v[0];
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
        }
    }
}
