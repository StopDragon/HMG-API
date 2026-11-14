package hmgapi.mods.wireless.dipo;

import java.util.LinkedHashMap;

import com.tcc.iap2.Iap2NotiMsg;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

/**
 * Host test of CpTurns: a drive through iAP2 route guidance messages, checking every
 * cluster call against what the turn-by-turn build's DiPO manager (e/u, e/c, e/v)
 * sends for the same input (worked out from its smali).
 */
public final class CpTurnsTest {
    static int fail;
    static Cluster c;
    /** 1.7.2(M-2): 안내 끝에 보내는 순정 무안내 회전·이름 지우기와 2C 지우기 값 */
    static final String CLEAR_TBT = "tbt 8 0 0.0 0.0 -3 0 15 0 -1", CLEAR_ROAD = "road []", CLEAR_DEST = "dest 0 []";
    static final String CLEAR_2C = "2c 15 15 65535 15 15 65535 15 15 65535";
    /** 리뷰 반영: 남은 거리·시간 지우기(AA와 같은 순정 무효 값) */
    static final String CLEAR_SUB = "sub 0.0 -3 15 0 0 3 3 0.0 -1 15";

    static void expect(String what, String... want) {
        List<String> got = c.calls;
        String w = String.join("\n", want), g = String.join("\n", got);
        if (w.equals(g)) {
            System.out.println("ok    " + what);
        } else {
            System.out.println("FAIL  " + what + "\n  wanted:\n    " + w.replace("\n", "\n    ") + "\n  got:\n    " + g.replace("\n", "\n    "));
            fail = 1;
        }
        got.clear();
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fail = 1;
    }

    static String utf16(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(String.format("%02x%02x", ch & 0xff, ch >> 8));
        String r = b.toString();
        while (r.endsWith("00")) r = r.substring(0, r.length() - 2);
        return r;
    }

    /** iAP2 types: 1/5 small ints, 6 uint32, 7 uint64, 8 enum, 10 string, 13 groups */
    static final int U8 = 1, U16 = 5, U32 = 6, U64 = 7, ENUM = 8, STR = 10, GROUPS = 13;

    static Iap2NotiMsg msg(Object... items) {
        Iap2NotiMsg m = new Iap2NotiMsg();
        for (int i = 0; i < items.length; i += 3) m.getItemGroup().add((Integer) items[i], (Integer) items[i + 1], items[i + 2]);
        return m;
    }

    static int[] i(int... v) {
        return v;
    }

    static long[] l(long... v) {
        return v;
    }

    static String[] s(String v) {
        return new String[] { v };
    }

    /** a RouteGuidanceManeuverUpdate for component 1 */
    static Iap2NotiMsg maneuver(int index, int type, int side, int junction, int exit, String after, int distance) {
        return msg(0, U16, i(1), 1, U16, i(index), 3, ENUM, i(type), 4, STR, s(after), 5, U32, i(distance),
                8, ENUM, i(side), 9, ENUM, i(junction), 11, U8, i(exit));
    }

    /** Public, synthetic fixtures for the ordinary turns observed on India 260430.
     * No car dumps or private route strings are needed for this regression. */
    static void indiaTurns() {
        // 1.7.4: 순정 DiPO 표대로. 좌측통행(side 1)·표 밖 각도·출발은 표에 없어 0(보내지 않음)
        check("1.7.4: left-hand traffic left/right/straight with angles: not in the stock table (0, not sent)",
                CpTurns.exactIcon(1, 1, 0, -90, 1) == 0 && CpTurns.exactIcon(2, 1, 0, 90, 1) == 0 && CpTurns.exactIcon(3, 1, 0, 0, 1) == 0);
        for (int side = 0; side <= 1; side++) {
            check("1.7.4 (owner decision pending): left/right without an angle still 70/67, side " + side,
                    CpTurns.exactIcon(1, side, 0, 0, 1) == 70 && CpTurns.exactIcon(2, side, 0, 0, 1) == 67);
            check("1.7.4: departure (11) is not in the stock table: 0 (was NO_INFO 1), side " + side,
                    CpTurns.exactIcon(11, side, 0, 0, 1) == 0);
        }
        check("invalid traffic sides get nothing",
                CpTurns.exactIcon(1, 2, 0, 0, 1) == 0 && CpTurns.exactIcon(2, -1, 0, 0, 1) == 0
                        && CpTurns.exactIcon(3, 2, 0, 0, 1) == 0 && CpTurns.exactIcon(11, 2, 0, 0, 1) == 0);
        check("1.7.4: unlisted turn angles get no arrow (no nearest-arrow band); departure with an angle or a junction neither",
                CpTurns.exactIcon(1, 0, 0, -91, 1) == 0 && CpTurns.exactIcon(2, 0, 0, 91, 1) == 0
                        && CpTurns.exactIcon(11, 1, 0, 90, 1) == 0 && CpTurns.exactIcon(11, 1, 1, 0, 1) == 0);
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.clock = 1000000L;
        t.maneuver(maneuver(0, 2, 1, 0, 90, "Test Road", 100));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("100"),
                12, ENUM, i(2), 13, U16, i(0)));
        expect("1.7.4: India Apple right turn with left-hand traffic (side 1, 90): not in the stock table, not sent (HUD neither)",
                "road [Test Road]");
        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(11)));
        t.routeGuidance(msg(0, U16, i(1)));
        expect("1.7.4: Google departure: not in the stock table (no NO_INFO 1, no turn frame); the HUD frozen with the cluster (no clear)",
                "road [Test Road]");
        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(2)));
        t.routeGuidance(msg(0, U16, i(1)));
        expect("Google right without angle reaches cluster and HUD after departure",
                "road [Test Road]", "tbt 67 0 0.0 100.0 0 0 0 0 0",
                "hud f201430000" + utf16("Test Road"), "2c 0 0 100 0 0 65535 0 0 65535");
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("India route stop still clears HUD (1.7.2: and the turn)", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
    }

    /** the 2C calls among the recorded ones (the rest cleared) */
    static String only2c() {
        StringBuilder b = new StringBuilder();
        for (String x : c.calls) if (x.startsWith("2c ")) b.append(b.length() == 0 ? "" : "\n").append(x);
        c.calls.clear();
        return b.toString();
    }

    static String arr(int[] v) {
        return java.util.Arrays.toString(v);
    }

    /** Korea Projection 2.3.0: the Korean stock numbers (1.7.1 map 2-A, 2-C, 2-D, 2-H) */
    static void korea230() {
        // 2-A: the stock DiPO numbers that differ from the US build (1.7.4: the full table is in stockTable())
        check("2.3.0 on-ramp (9): 160 (was 172); 1.7.4: not without the exact geometry", CpTurns.exactIcon(9, 0, 0, 0, 1) == 160
                && CpTurns.exactIcon(9, 1, 0, 0, 1) == 0 && CpTurns.exactIcon(5, 0, 0, 0, 1) == 172);
        check("2.3.0 highway off-ramp left (22): 165 (was 82)", CpTurns.exactIcon(22, 0, 0, 0, 1) == 165);
        check("2.3.0 highway off-ramp right (23): 164 (was 81)", CpTurns.exactIcon(23, 0, 0, 0, 1) == 164);
        // 1.7.4: 순정 표에 없는 종류는 대체 번호 없이 0(보내지 않음)
        int[] outside = { 0, 6, 7, 8, 10, 11, 13, 14, 16, 17, 18, 20, 21, 26, 27, 30, 36, 40, 46, 51 };
        for (int ty : outside) check("1.7.4: type " + ty + " is not in the stock table: 0 at any angle (no fallback)",
                CpTurns.exactIcon(ty, 0, 0, 0, 1) == 0 && CpTurns.exactIcon(ty, 0, 0, 45, 1) == 0 && CpTurns.exactIcon(ty, 0, 1, -90, 1) == 0
                        && CpTurns.exactIcon(ty, 1, 0, 0, 1) == 0);
        check("2.3.0 turn ahead with no arrow: 8 (was 64)", CpTurns.aheadIcon(99, 0, 0, 0) == 8 && CpTurns.aheadIcon(9, 0, 0, 0) == 8);

        // 2-D: lanes (HMG-Seogaemo's glyph sets and colours)
        check("2.3.0 lane straight + left, left preferred: glyphs 9, 12; straight grey, left blue",
                arr(CpTurns.laneValues(2, i(0, -90), i(-90))).equals(arr(i(0x0b, 9, 12, 0))));
        check("2.3.0 lane left + straight + right, straight good: glyphs 28, 22, 24; grey, white, grey",
                arr(CpTurns.laneValues(1, i(-90, 0, 90), i(0))).equals(arr(i(0x37, 28, 22, 24))));
        check("2.3.0 lane straight + U-turn, not good: glyphs 9, 10, both grey",
                arr(CpTurns.laneValues(0, i(0, 180), null)).equals(arr(i(0x0f, 9, 10, 0))));
        check("2.3.0 lane right + slight right listed in another order: the same row (18, 21)",
                arr(CpTurns.laneValues(2, i(45, 90), i(90))).equals(arr(CpTurns.laneValues(2, i(90, 45), i(90))))
                        && CpTurns.laneValues(2, i(45, 90), i(90))[1] == 18);
        check("2.3.0 lane with no verified set (U-turn, left, straight, right): only the highlighted arrow, single glyph",
                arr(CpTurns.laneValues(1, i(-180, -90, 0, 90), i(90))).equals(arr(i(1, 3, 0, 0))));
        check("2.3.0 lane sharp right + right (no row): folded to one right arrow",
                arr(CpTurns.laneValues(2, i(135, 90), i(135))).equals(arr(i(2, 3, 0, 0))));
        check("2.3.0 lane with no angle: the status on slot 0 only, no glyph",
                arr(CpTurns.laneValues(2, null, null)).equals(arr(i(2, 0, 0, 0))));

        // HUD list: a next turn without an arrow (8) is left out, count 1
        c = new Cluster();
        CpTurns hl = new CpTurns(new android.content.Context(), c, null);
        hl.clock = 4000000L;
        hl.maneuver(maneuver(0, 2, 0, 0, 90, "Q", 300));
        hl.maneuver(maneuver(1, 99, 0, 0, 0, "Q2", 200));
        hl.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0, 1)));
        String hudLine = null;
        for (String x : c.calls) if (x.startsWith("hud ")) hudLine = x;
        check("2.3.0 HUD list: next turn with no arrow (8) is not sent, count 1", ("hud f201430000" + utf16("Q")).equals(hudLine));
        c.calls.clear();

        // 2-H: HUD distances for three points, 65535 for none and at the end
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurns h = new CpTurns(new android.content.Context(), c, null);
        h.clock = 3000000L;
        h.maneuver(maneuver(0, 2, 0, 0, 90, "R1", 500));
        h.maneuver(maneuver(1, 1, 0, 0, -90, "R2", 300));
        h.maneuver(maneuver(2, 3, 0, 0, 0, "R3", 900));
        h.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(500), 11, STR, s("500"), 12, ENUM, i(2), 13, U16, i(0, 1, 2)));
        check("2.3.0 2C three points: 500 m, 800 m, 1.7 km", only2c().equals("2c 0 0 500 0 0 800 1 7 1"));
        h.routeGuidance(msg(0, U16, i(1), 10, U32, i(800), 11, STR, s("0.8"), 12, ENUM, i(0)));
        check("2.3.0 2C under 1 km in m (the phone said 0.8 km): 800 m, 1.1 km, 2.0 km",
                only2c().equals("2c 0 0 800 1 1 1 1 0 2"));
        h.routeGuidance(msg(0, U16, i(1), 10, U32, i(1200), 11, STR, s("1.2"), 13, U16, i(0, 1)));
        check("2.3.0 2C with two points: the third 65535", only2c().equals("2c 1 2 1 1 5 1 0 0 65535"));
        h.routeGuidance(msg(0, U16, i(1), 11, STR, s("1.1"), 13, U16, i(0, 1, 2)));
        check("2.3.0 2C: an update without the distance in metres (item 10) leaves the 2nd and 3rd points empty, not the old value",
                only2c().equals("2c 1 1 1 0 0 65535 0 0 65535"));
        h.routeGuidance(msg(0, U16, i(1), 10, U32, i(1100)));
        check("2.3.0 2C: item 10 again, the points are back", only2c().equals("2c 1 1 1 1 4 1 1 3 2"));
        h.routeGuidance(msg(0, U16, i(1), 13, U16, i(0)));
        check("2.3.0 2C with one point: the 2nd and 3rd 65535", only2c().equals("2c 1 1 1 0 0 65535 0 0 65535"));
        h.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        check("2.3.0 guidance end: 2C 65535 for all three points (1.7.2: unit and tenths 15, as the stock engine)",
                only2c().equals(CLEAR_2C) && CpTurns.HUD_NO_POINT == 65535);
        Car.unit = Car.DISTANCE_MILES;
    }

    static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < n; k++) b.append(s);
        return b.toString();
    }

    /** 1.7.2(M-15): 카플레이 HUD 목록 이름도 글자 경계로 자름 */
    static void m15() throws Exception {
        String emoji = new String(Character.toChars(0x1F600));
        check("1.7.2 M-15: CarPlay HUD name: same cut as AA (78 bytes, Hangul, emoji, ASCII, empty)",
                CpTurns.HUD_NAME_MAX == 78 && CpTurns.hudName(repeat("전", 40), 78).equals(repeat("전", 39))
                        && CpTurns.hudName(repeat("a", 38) + emoji, 78).equals(repeat("a", 38))
                        && CpTurns.hudName(repeat("a", 37) + emoji, 78).equals(repeat("a", 37) + emoji)
                        && CpTurns.hudName("Road A", 78).equals("Road A") && CpTurns.hudName("", 78).equals(""));
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.clock = 7000000L;
        t.maneuver(maneuver(0, 2, 0, 0, 90, repeat("a", 38) + emoji + "끝", 300));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0)));
        String hud = null;
        for (String x : c.calls) if (x.startsWith("hud ")) hud = x;
        check("1.7.2 M-15: CarPlay HUD frame: emoji at the 78-byte edge left out whole (5 + 76 bytes)",
                ("hud f201430000" + utf16(repeat("a", 38))).equals(hud));
        c.calls.clear();
    }

    /** 1.7.2(M-2·M-3): 안내 끝 지우기 값과 멀리 있음 막대 */
    static void korea172() {
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.clock = 6000000L;
        t.maneuver(maneuver(0, 2, 0, 0, 90, "Road A", 300));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0)));
        String tbt = null;
        for (String x : c.calls) if (x.startsWith("tbt ")) tbt = x;
        check("1.7.4: a turn goes out as the stock DiPO (side street 0, direction 0, distance error 0, bar 0, barErr 0)",
                "tbt 67 0 0.0 300.0 0 0 0 0 0".equals(tbt));
        c.calls.clear();
        t.stop();
        expect("1.7.2 M-2: route guidance stopped after a turn: stock no-guidance turn (8, distance and bar invalid), empty names, 2C (15,15,65535)x3, HUD",
                CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        t.stop();
        expect("1.7.2 M-2: stopped again with nothing shown since: neither the turn nor the HUD is cleared twice (2nd review 2)");
        check("1.7.2 M-2: clear values within NavigationCluster's ranges (distanceErr -3..0, scale 0..4 or 15, barErr -2..0)",
                CpTurns.CLEAR_ICON == 8 && CpTurns.CLEAR_DIST_ERR >= -3 && CpTurns.CLEAR_DIST_ERR <= 0 && CpTurns.CLEAR_UNIT == 15
                        && CpTurns.CLEAR_BAR_ERR == -1
                        && CpTurns.HUD_CLEAR_UNIT == 15 && CpTurns.HUD_CLEAR_TENTHS == 15);

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("250"), 13, U16, i(0)));
        c.calls.clear();
        t.disconnected();
        expect("1.7.2 M-2: iAP2 session ended during guidance: the turn and names are cleared too",
                CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");

        CpTurns d = new CpTurns(new android.content.Context(), c, null);
        d.clock = 6100000L;
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(6), 4, STR, s("Home")));
        expect("1.7.2: state 6 with only a destination name (no maneuver yet)", "dest 0 [Home]");
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("1.7.2 M-2: a destination name alone is cleared at the end too (no HUD clear: nothing was sent to the HUD)",
                CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST);

        CpTurns n = new CpTurns(new android.content.Context(), c, null);
        n.stop();
        expect("2nd review 2: stop with nothing ever shown (CarPlay not guiding): nothing sent, the stock HUD and lanes stay");
        // 1.7.2(M-2, 소유자 결정 2026-11-09): 순정 빈 목록은 {F2,00} 2바이트(Java·ClusterService가 엔진 바이트를 그대로 넘김, H)
        c.hudFrames.clear();
        t.maneuver(maneuver(0, 2, 0, 0, 90, "Road A", 300));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0)));
        t.stop();
        boolean v6 = c.hudFrames.size() >= 2;
        for (int k = 1; k < c.hudFrames.size(); k++) v6 &= java.util.Arrays.equals(c.hudFrames.get(k), new byte[] { (byte) 0xF2, 0 });
        c.calls.clear();
        check("1.7.2 M-2: the HUD's empty list is the stock {F2,00} (2 bytes), sent twice at the end", v6);

        CpTurns l = new CpTurns(new android.content.Context(), c, null);
        l.lanes(msg(2, GROUPS, new Iap2NotiMsg.Iap2ObjGroup[0]));
        c.calls.clear();
        l.stop();
        expect("2nd review 2: only lanes were sent: stop clears the HUD side once (no turn clear)", CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        l.stop();
        l.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("2nd review 2: repeated stops and state 0 afterwards send nothing");
    }

    /** 곧 회전 화면이 될 수 있는 회전 하나: 종류, 나가는 길 각도, 다른 길 각도(item 10), 링크한 차로 안내(item 12, -1이면 없음) */
    static Iap2NotiMsg nearManeuver(int index, int type, int side, int junction, int exit, int[] angles, int lane, String after) {
        Iap2NotiMsg m = msg(0, U16, i(1), 1, U16, i(index), 3, ENUM, i(type), 4, STR, s(after), 5, U32, i(500),
                8, ENUM, i(side), 9, ENUM, i(junction), 11, U8, i(exit));
        if (angles != null) m.getItemGroup().add(10, U8, angles);
        if (lane >= 0) m.getItemGroup().add(12, U16, i(lane));
        return m;
    }

    /** 차로 안내(LaneGuidance) 번호 index에 차로 n개 */
    static Iap2NotiMsg laneMsg(int index, int n) {
        Iap2NotiMsg lanes = new Iap2NotiMsg();
        Iap2NotiMsg.Iap2ObjGroup[] g = new Iap2NotiMsg.Iap2ObjGroup[n];
        for (int k = 0; k < n; k++) {
            g[k] = lanes.new Iap2ObjGroup();
            g[k].add(1, U16, i(1)).add(2, U16, i(0));
        }
        lanes.getItemGroup().add(0, U16, i(1)).add(1, U16, i(index)).add(2, GROUPS, g);
        return lanes;
    }

    /** 다음 회전까지 m(item 10)과 표시 거리(item 11, m) */
    static Iap2NotiMsg rgAt(int state, int meters) {
        return msg(0, U16, i(1), 1, ENUM, i(state), 10, U32, i(meters), 11, STR, s(String.valueOf(meters)), 12, ENUM, i(2), 13, U16, i(0));
    }

    /** rgAt과 같고, 지금 회전 번호(item 13)를 idx로 */
    static Iap2NotiMsg rgAtIdx(int state, int meters, int idx) {
        return msg(0, U16, i(1), 1, ENUM, i(state), 10, U32, i(meters), 11, STR, s(String.valueOf(meters)), 12, ENUM, i(2), 13, U16, i(idx));
    }

    static String last(String prefix) {
        String r = null;
        for (String x : c.calls) if (x.startsWith(prefix)) r = x;
        return r;
    }

    /**
     * 1.7.4 끝난 기준 ①: 순정 DiPO 표 46줄(drafts/1.7.4-tbt-diff.md 1장, 순정 e/u.a의 ManeuverType 스위치)의 입력마다
     * 계기판 회전 프레임이 순정과 같다: updateTBTInfo(표 번호, 옆길 0, 방향 0, 거리, 오류 0, 0, 단위, 막대 0, 막대 오류 0).
     * 남은 거리(item 10)를 D(350m) 안의 100m로 줘도 번호가 바뀌지 않는다(곧 회전 화면 없음).
     * 줄: {표 줄 번호, 종류, 통행 방향, 교차로 종류, 출구 각도, 경로 상태, 순정 번호(0: 보내지 않음)}
     */
    static final int[][] STOCK_DIPO = {
        { 1, 1, 0, 0, -120, 1, 69 }, { 1, 1, 1, 0, -120, 1, 69 }, { 2, 1, 0, 0, -90, 1, 70 }, { 3, 1, 0, 0, -60, 1, 71 }, { 3, 1, 1, 0, -60, 1, 71 },
        { 4, 1, 0, 0, -45, 1, 145 }, { 5, 1, 1, 1, -120, 1, 200 }, { 6, 1, 1, 1, -90, 1, 201 }, { 7, 1, 1, 1, -30, 1, 203 },
        { 8, 2, 0, 0, 60, 1, 66 }, { 8, 2, 1, 0, 60, 1, 66 }, { 9, 2, 0, 0, 90, 1, 67 }, { 10, 2, 0, 0, 120, 1, 68 }, { 11, 2, 0, 1, 0, 1, 96 },
        { 12, 2, 1, 1, 0, 1, 193 }, { 13, 2, 1, 1, 90, 1, 195 }, { 14, 2, 1, 1, 120, 1, 196 }, { 15, 2, 1, 1, 180, 1, 198 },
        { 16, 2, 0, 0, 45, 1, 144 }, { 17, 3, 0, 0, 0, 1, 65 }, { 18, 4, 0, 0, 0, 1, 72 }, { 18, 4, 0, 0, 180, 1, 72 }, { 19, 4, 1, 0, 0, 1, 73 },
        { 20, 5, 0, 0, 0, 1, 172 }, { 21, 9, 0, 0, 0, 1, 160 }, { 22, 12, 0, 0, 0, 1, 112 }, { 23, 12, 0, 0, 0, 2, 3 }, { 23, 12, 1, 1, 30, 2, 3 },
        { 24, 15, 0, 0, 0, 1, 176 }, { 25, 19, 0, 1, -180, 1, 102 }, { 26, 22, 0, 0, 0, 1, 165 }, { 27, 23, 0, 0, 0, 1, 164 },
        { 28, 24, 0, 0, 0, 1, 114 }, { 29, 25, 0, 0, 0, 1, 113 }, { 30, 28, 0, 1, 135, 1, 100 }, { 31, 29, 0, 1, 90, 1, 99 },
        { 32, 31, 0, 1, 0, 1, 97 }, { 33, 31, 1, 1, 0, 1, 192 }, { 34, 32, 0, 1, -45, 1, 104 }, { 35, 33, 0, 1, -90, 1, 105 },
        { 36, 34, 0, 1, -135, 1, 104 }, { 37, 35, 0, 1, -180, 1, 102 }, { 38, 47, 0, 0, -150, 1, 69 }, { 38, 47, 0, 0, -140, 1, 69 },
        { 38, 47, 0, 0, -135, 1, 69 }, { 39, 47, 0, 0, -30, 1, 71 }, { 40, 48, 0, 0, 30, 1, 66 }, { 41, 48, 0, 0, 135, 1, 68 },
        { 41, 48, 0, 0, 150, 1, 68 }, { 42, 49, 0, 0, -45, 1, 71 }, { 43, 50, 0, 0, 45, 1, 66 }, { 44, 52, 0, 0, 0, 1, 174 },
        { 45, 53, 0, 0, 0, 1, 173 },
        // 46: 표에 맞는 줄 없음(그 밖 모든 입력) → 보내지 않음. 표 줄의 조건을 하나만 어긋낸 것들
        { 46, 1, 1, 0, -90, 1, 0 }, { 46, 1, 0, 0, -91, 1, 0 }, { 46, 1, 0, 1, -120, 1, 0 }, { 46, 2, 1, 0, 90, 1, 0 }, { 46, 2, 0, 0, 75, 1, 0 },
        { 46, 2, 0, 1, 90, 1, 0 }, { 46, 3, 1, 0, 0, 1, 0 }, { 46, 3, 0, 0, 10, 1, 0 }, { 46, 4, 0, 1, 0, 1, 0 }, { 46, 5, 1, 0, 0, 1, 0 },
        { 46, 5, 0, 0, 30, 1, 0 }, { 46, 9, 0, 0, 30, 1, 0 }, { 46, 9, 1, 0, 0, 1, 0 }, { 46, 12, 1, 0, 0, 1, 0 }, { 46, 12, 0, 1, 0, 1, 0 },
        { 46, 15, 0, 0, 20, 1, 0 }, { 46, 19, 0, 1, 180, 1, 0 }, { 46, 22, 0, 0, -30, 1, 0 }, { 46, 23, 0, 0, 30, 1, 0 }, { 46, 28, 0, 1, 130, 1, 0 },
        { 46, 31, 0, 1, 10, 1, 0 }, { 46, 32, 0, 1, -46, 1, 0 }, { 46, 35, 1, 1, -180, 1, 0 }, { 46, 47, 0, 0, -134, 1, 0 }, { 46, 47, 0, 0, -151, 1, 0 },
        { 46, 48, 0, 0, 151, 1, 0 }, { 46, 48, 0, 1, 30, 1, 0 }, { 46, 49, 0, 0, -44, 1, 0 }, { 46, 52, 0, 0, 10, 1, 0 }, { 46, 53, 1, 0, 0, 1, 0 },
        { 46, 0, 0, 0, 0, 1, 0 }, { 46, 6, 0, 1, 0, 1, 0 }, { 46, 7, 0, 1, 0, 1, 0 }, { 46, 8, 0, 0, 0, 1, 0 }, { 46, 10, 0, 0, 0, 1, 0 },
        { 46, 11, 0, 0, 0, 1, 0 }, { 46, 13, 0, 0, -30, 1, 0 }, { 46, 14, 0, 0, 30, 1, 0 }, { 46, 16, 0, 0, 0, 1, 0 }, { 46, 17, 0, 0, 0, 1, 0 },
        { 46, 18, 0, 0, 0, 1, 0 }, { 46, 20, 0, 0, 0, 1, 0 }, { 46, 21, 0, 0, 0, 1, 0 }, { 46, 26, 0, 0, 0, 1, 0 }, { 46, 27, 0, 0, 0, 1, 0 },
        { 46, 30, 0, 1, 45, 1, 0 }, { 46, 36, 0, 1, 0, 1, 0 }, { 46, 46, 0, 1, 0, 1, 0 }, { 46, 51, 0, 0, 0, 1, 0 }, { 46, 99, 0, 0, 0, 1, 0 },
    };

    /** 1.7.4 끝난 기준 ①·②: 표의 줄마다 새 CpTurns에 보내고 계기판 호출을 본다 */
    static void stockTable() {
        Car.unit = Car.DISTANCE_KM;
        for (int[] r : STOCK_DIPO) {
            c = new Cluster();
            CpTurns t = new CpTurns(new android.content.Context(), c, null);
            t.clock = 12000000L;
            t.maneuver(maneuver(0, r[1], r[2], r[3], r[4], "Road " + r[0], 100));
            t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(r[5]), 10, U32, i(100), 11, STR, s("100"), 12, ENUM, i(2), 13, U16, i(0)));
            String tbt = null;
            int tbts = 0;
            for (String x : c.calls) if (x.startsWith("tbt ")) { tbt = x; tbts++; }
            boolean road = c.calls.contains("road [Road " + r[0] + "]");
            String in = "type " + r[1] + " side " + r[2] + " junction " + r[3] + " exit " + r[4] + " state " + r[5];
            if (r[6] != 0) {
                check("stock DiPO row " + r[0] + ": " + in + " -> tbt " + r[6] + " 0 0.0 100.0 0 0 0 0 0, crossroad name sent",
                        tbts == 1 && ("tbt " + r[6] + " 0 0.0 100.0 0 0 0 0 0").equals(tbt) && road);
            } else {
                check("stock DiPO row 46 (no row matches): " + in + " -> no updateTBTInfo, only the crossroad name", tbts == 0 && road);
            }
        }
        // ② 표 밖 입력: 앞 화살표·거리가 계기판에 남는다(아무것도 덮지 않음)
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.clock = 12100000L;
        t.maneuver(maneuver(0, 2, 0, 0, 90, "Road A", 300));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(300), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0)));
        c.calls.clear();
        t.maneuver(maneuver(0, 8, 0, 0, 0, "Road B", 280));
        t.routeGuidance(msg(0, U16, i(1), 10, U32, i(280), 11, STR, s("280")));
        check("1.7.4 (2): off-ramp (8, not in the stock table) after a right turn: no updateTBTInfo (the 67 and its distance stay), no blank 8, crossroad name sent",
                last("tbt ") == null && c.calls.contains("road [Road B]") && !c.calls.contains("hud f2"));
        Car.unit = Car.DISTANCE_MILES;
    }

    /**
     * 1.7.4 끝난 기준 ③·④: 이름·회전·거리가 섞여 와도 받은 순서대로 바로 나간다(묶기·경계 여유 없음). 경로 상태 3(경로 계산)·
     * 5(재탐색)에도 표 번호를 그대로 보내고, 표시 9·10이나 상태가 돌아올 때 "다시 보냄"이 없다.
     */
    static void stockOrder() {
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.clock = 13000000L;
        t.maneuver(maneuver(0, 2, 0, 0, 90, "Road A", 400));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(400), 11, STR, s("400"), 12, ENUM, i(2), 13, U16, i(0)));
        t.clock += 100L;
        t.routeGuidance(msg(0, U16, i(1), 10, U32, i(351), 11, STR, s("351")));
        t.clock += 100L;
        t.routeGuidance(msg(0, U16, i(1), 10, U32, i(349), 11, STR, s("349")));
        t.clock += 100L;
        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 4, STR, s("Road A2")));
        t.routeGuidance(msg(0, U16, i(1), 10, U32, i(360), 11, STR, s("360")));
        t.clock += 100L;
        t.maneuver(maneuver(0, 1, 0, 0, -90, "Road B", 300));
        t.routeGuidance(msg(0, U16, i(1), 10, U32, i(300), 11, STR, s("300")));
        java.util.List<String> got = new java.util.ArrayList<String>();
        for (String x : c.calls) if (x.startsWith("tbt ") || x.startsWith("road ")) got.add(x);
        expectList("1.7.4 (3): across 350 m, a name change and a new turn, 100 ms apart: every update goes out at once, in order, same icon at every distance",
                got, "road [Road A]", "tbt 67 0 0.0 400.0 0 0 0 0 0", "road [Road A]", "tbt 67 0 0.0 351.0 0 0 0 0 0",
                "road [Road A]", "tbt 67 0 0.0 349.0 0 0 0 0 0", "road [Road A2]", "tbt 67 0 0.0 360.0 0 0 0 0 0",
                "road [Road B]", "tbt 70 0 0.0 300.0 0 0 0 0 0");
        c.calls.clear();
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(5), 11, STR, s("300")));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(3), 11, STR, s("300")));
        got = new java.util.ArrayList<String>();
        for (String x : c.calls) if (x.startsWith("tbt ") || x.startsWith("road ") || x.startsWith("hud ") || x.startsWith("2c ")) got.add(x);
        expectList("1.7.4 (4): rerouting (5) and loading (3): the table icon as before, no display 9/10, no HUD or 2C clear",
                got, "road [Road B]", "tbt 70 0 0.0 300.0 0 0 0 0 0", "2c 0 0 300 0 0 65535 0 0 65535",
                "road [Road B]", "tbt 70 0 0.0 300.0 0 0 0 0 0", "2c 0 0 300 0 0 65535 0 0 65535");
        c.calls.clear();
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1)));
        int tbts = 0;
        for (String x : c.calls) if (x.startsWith("tbt ")) tbts++;
        check("1.7.4 (4): back to state 1: one update, one frame (no extra resend of the remembered turn)", tbts == 1);
        check("1.7.4: no near-turn, bar or search constants left (CpTurns has no NEAR_*, D_*, DISPLAY_SEARCH fields)", noField("NEAR_TURN")
                && noField("D_GENERAL") && noField("DISPLAY_SEARCH") && noField("NEAR_EXIT_MARGIN") && noField("barOut") && noField("CROSSROAD_MS"));
        Car.unit = Car.DISTANCE_MILES;
    }

    static boolean noField(String name) {
        try {
            CpTurns.class.getDeclaredField(name);
            return false;
        } catch (NoSuchFieldException e) {
            return true;
        }
    }

    static void expectList(String what, java.util.List<String> got, String... want) {
        String w = String.join("\n", want), g = String.join("\n", got);
        if (w.equals(g)) {
            System.out.println("ok    " + what);
        } else {
            System.out.println("FAIL  " + what + "\n  wanted:\n    " + w.replace("\n", "\n    ") + "\n  got:\n    " + g.replace("\n", "\n    "));
            fail = 1;
        }
    }

    /**
     * 1.7.4 소유자 결정 1(2026-11-12): 표 밖 대체 번호의 유일한 예외. 폰이 각도 없이 "좌회전"(1)·"우회전"(2)을 교차로(junction 0)로
     * 보내면 순정 표의 좌·우회전 번호 70·67을 보낸다. 그 밖의 표 밖 입력은 보내지 않는다.
     */
    static void angleless() {
        Car.unit = Car.DISTANCE_KM;
        int[][] rows = {
            // {종류, 통행 방향, 교차로, 각도, 보낼 번호(0: 보내지 않음)}
            { 1, 0, 0, 0, 70 }, { 1, 1, 0, 0, 70 }, { 2, 0, 0, 0, 67 }, { 2, 1, 0, 0, 67 },
            { 1, 0, 1, 0, 0 }, { 2, 0, 1, 90, 0 }, { 1, 2, 0, 0, 0 }, { 47, 0, 0, 0, 0 }, { 48, 0, 0, 0, 0 }, { 49, 0, 0, 0, 0 },
            { 50, 0, 0, 0, 0 }, { 13, 0, 0, 0, 0 }, { 14, 0, 0, 0, 0 }, { 20, 0, 0, 0, 0 }, { 21, 0, 0, 0, 0 }, { 3, 0, 0, 30, 0 } };
        for (int[] r : rows) {
            c = new Cluster();
            CpTurns t = new CpTurns(new android.content.Context(), c, null);
            t.clock = 14000000L;
            t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(r[0]), 4, STR, s("Road"), 5, U32, i(200), 8, ENUM, i(r[1]), 9, ENUM, i(r[2]),
                    11, U8, i(r[3])));
            t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("200"), 12, ENUM, i(2), 13, U16, i(0)));
            String in = "type " + r[0] + " side " + r[1] + " junction " + r[2] + " exit " + r[3];
            check(r[4] != 0 ? "owner decision 1: " + in + " (no angle) -> " + r[4] : "owner decision 1: " + in + " (outside the table) -> not sent",
                    r[4] != 0 ? ("tbt " + r[4] + " 0 0.0 200.0 0 0 0 0 0").equals(last("tbt ")) : last("tbt ") == null);
        }
        Car.unit = Car.DISTANCE_MILES;
    }

    /**
     * 리뷰 반영(2026-11-12): 표 밖 회전에서는 계기판 회전 프레임과 함께 HUD 목록·HUD 거리(2C)도 멈춘다. 교차로 이름은 순정대로
     * 새 이름, 남은 거리·다음 회전 목록은 폰 값 그대로. 표 안 회전이 다시 오면 모두 새 값.
     */
    static void outsideFreeze() {
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.clock = 15000000L;
        t.maneuver(maneuver(0, 2, 0, 0, 90, "Right Rd", 300));
        t.maneuver(maneuver(1, 1, 0, 0, -90, "Next Rd", 500));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 7, U32, i(5000), 10, U32, i(300), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0, 1)));
        expect("freeze 1: a right turn (in the table): turn frame, HUD list, 2C, trip, turns ahead",
                "road [Right Rd]", "tbt 67 0 0.0 300.0 0 0 0 0 0", "hud f202434600" + utf16("Right Rd"), "2c 0 0 300 0 0 800 0 0 65535",
                "sub 5.0 0 1 -1 -1 -1 -1 0.0 -1 0", "list {1 70 0 [Next Rd] [] 500.0 0}");
        t.maneuver(maneuver(0, 13, 0, 0, 0, "Keep Rd", 200));
        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(4800), 10, U32, i(200), 11, STR, s("200")));
        expect("freeze 2: keep left (13, outside the table): only the new crossroad name; turn frame, HUD list and 2C stay; trip and turns ahead as the phone sends",
                "road [Keep Rd]", "sub 4.8 0 1 -1 -1 -1 -1 0.0 -1 0", "list {1 70 0 [Next Rd] [] 500.0 0}");
        t.clock += 2000L;
        t.refresh();
        expect("freeze 2: refresh resends the last HUD list exactly as sent (Right Rd), and no turn frame or 2C",
                "hud f202434600" + utf16("Right Rd"), "road [Keep Rd]", "sub 4.8 0 1 -1 -1 -1 -1 0.0 -1 0", "list {1 70 0 [Next Rd] [] 500.0 0}");
        t.maneuver(maneuver(0, 1, 0, 0, -90, "Left Rd", 150));
        t.routeGuidance(msg(0, U16, i(1), 10, U32, i(150), 11, STR, s("150")));
        expect("freeze 3: a left turn (in the table) again: every value new",
                "road [Left Rd]", "tbt 70 0 0.0 150.0 0 0 0 0 0", "hud f202464600" + utf16("Left Rd"), "2c 0 0 150 0 0 650 0 0 65535",
                "sub 4.8 0 1 -1 -1 -1 -1 0.0 -1 0", "list {1 70 0 [Next Rd] [] 500.0 0}");
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 리뷰 반영(2026-11-12): 카플레이 거리 단위 yd(3)·ft(4)·그 밖(기본 m) */
    static void cpUnits() {
        int[][] u = { { 0, 1 }, { 1, 2 }, { 2, 0 }, { 3, 4 }, { 4, 3 }, { 9, 0 } };
        for (int[] x : u) {
            c = new Cluster();
            CpTurns t = new CpTurns(new android.content.Context(), c, null);
            t.clock = 16000000L;
            t.maneuver(maneuver(0, 2, 0, 0, 90, "R", 300));
            t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("12"), 12, ENUM, i(x[0]), 13, U16, i(0)));
            check("stock DiPO units: iAP2 unit " + x[0] + " -> cluster unit " + x[1], ("tbt 67 0 0.0 12.0 0 0 " + x[1] + " 0 0").equals(last("tbt ")));
        }
    }

    public static void main(String[] a) throws Exception {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        Car.unit = Car.DISTANCE_MILES;
        c = new Cluster();
        final CountDownLatch restarted = new CountDownLatch(1);
        CpTurns t = new CpTurns(new android.content.Context(), c, new Runnable() {
            @Override
            public void run() {
                restarted.countDown();
            }
        });
        // a fixed clock: the crossroad name and refresh() go by it (Korea Projection)
        t.clock = 1000000L;
        final int RIGHT = 2, LEFT = 1, STRAIGHT = 3, UTURN = 4, ARRIVE = 12, MILES = 1, KM = 0, FEET = 4;

        t.maneuver(maneuver(0, RIGHT, 0, 0, 90, "Main St", 300));
        t.maneuver(maneuver(1, LEFT, 0, 0, -90, "Oak Ave", 800));
        t.maneuver(maneuver(2, STRAIGHT, 0, 0, 0, "Elm St", 0));
        t.maneuver(maneuver(3, UTURN, 0, 0, 0, "Pine St", 2000));
        expect("maneuvers alone: nothing sent");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 7, U32, i(5000), 9, ENUM, i(MILES), 11, STR, s("0.2"),
                12, ENUM, i(MILES), 13, U16, i(0, 1, 2, 3), 5, U64, l(1700000000L), 19, STR, s("Maps")));
        expect("right turn in 0.2 mi: crossroad, turn, HUD list with the next turn, HUD distance, trip, turns ahead",
                "road [Main St]", "tbt 67 0 0.0 0.2 0 0 2 0 0", "hud f202434600" + utf16("Main St"), "2c 2 2 0 0 0 65535 0 0 65535",
                "sub 3.1 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 11, STR, s("0.1")));
        expect("partial update (distance only): same HUD list not resent; 1.7.4: the crossroad name with every update (stock DiPO)",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535", "sub 3.1 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        Iap2NotiMsg lanes = new Iap2NotiMsg();
        Iap2NotiMsg.Iap2ObjGroup l1 = lanes.new Iap2ObjGroup(), l2 = lanes.new Iap2ObjGroup();
        l1.add(1, U16, i(1)).add(2, U16, i(0));
        l2.add(2, U16, i(90, 45)).add(3, U16, i(90));
        lanes.getItemGroup().add(0, U16, i(1)).add(2, GROUPS, new Iap2NotiMsg.Iap2ObjGroup[] { l1, l2 });
        t.lanes(lanes);
        StringBuilder z = new StringBuilder();
        for (int k = 0; k < 14; k++) z.append("00");
        // Korea Projection 2.3.0: lane glyphs and colours from HMG-Seogaemo. Lane 2 has no status (counts as 1):
        // right (highlighted) white 01, slight right grey 11 -> 0x0d; the right + slight right glyphs 18, 21
        expect("lanes: straight; right + slight right highlighted (2.3.0: combined glyphs, per-arrow colours)",
                "lanes f10200010d" + z + "0100001215");

        Car.unit = Car.DISTANCE_KM;
        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(800), 11, STR, s("1.5"), 12, ENUM, i(KM)));
        expect("km: turn in the phone's km, trip in m (owner's km, under 1 km), turns ahead in m/km",
                "road [Main St]", "tbt 67 0 0.0 1.5 0 0 1 0 0", "2c 1 5 1 0 0 65535 0 0 65535", "sub 800.0 0 0 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] 800.0 0} {2 72 0 [Pine St] [] 2.0 1}");
        Car.unit = Car.DISTANCE_MILES;

        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 5, U32, i(250)));
        t.routeGuidance(msg(0, U16, i(1), 11, STR, s("0.1"), 12, ENUM, i(MILES)));
        expect("maneuver update without optional angle retains its explicit right-turn direction (Korea Projection: and the kept angle)",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535", "sub 0.4 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");
        t.maneuver(maneuver(0, RIGHT, 0, 0, 90, "Main St", 250));

        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(100), 6, U64, l(0), 5, U64, l(0)));
        expect("trip under 161 m with miles: feet; no arrival time",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535", "sub 328.0 0 3 -1 -1 -1 -1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(0), 6, U64, l(600)));
        Calendar in10 = Calendar.getInstance();
        in10.add(Calendar.SECOND, 600);
        expect("arrival from the time remaining (no distance)",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535",
                "sub 0.0 -1 0 " + in10.get(Calendar.HOUR_OF_DAY) + " " + in10.get(Calendar.MINUTE) + " 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 6, U64, l(0), 5, U64, l(600000000L)));
        expect("arrival as seconds since 2001 (Apple's epoch): 2020-01-06 10:40",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535", "sub 0.0 -1 0 10 40 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        boolean noSrcLine = true;
        for (String x : Dipo.logs) if (x.contains("route guidance source Maps")) noSrcLine = false;
        check("1.7.4 T-5 1: debug log off: the first source name isn't logged", noSrcLine);
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("Waze"), 5, U64, l(0)));
        check("source change: route guidance restarted", restarted.await(2, TimeUnit.SECONDS));
        expect("source change (Maps to Waze): HUD cleared; the reset state is 0 until the phone resends it, so cleared again"
                + " (1.7.2: the old app's turn and names cleared too)",
                CLEAR_2C, "lanes f1", "hud f2", "hud f2", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST);

        t.maneuver(maneuver(5, ARRIVE, 0, 0, 0, "Home Rd", 100));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(6), 4, STR, s("Home"), 11, STR, s("300"), 12, ENUM, i(FEET), 13, U16, i(5)));
        expect("state 6 with destination, arrival maneuver in 300 ft",
                "dest 0 [Home]", "road [Home Rd]", "tbt 112 0 0.0 300.0 0 0 3 0 0", "hud f201700000" + utf16("Home Rd"),
                "2c 3 0 300 0 0 65535 0 0 65535");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(2)));
        expect("arrived (state 2): icon 3, no HUD list for it", "road [Home Rd]", "tbt 3 0 0.0 300.0 0 0 3 0 0", "2c 3 0 300 0 0 65535 0 0 65535");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("state 0: turn, names and HUD cleared (1.7.2: stock no-guidance values)", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1)));
        t.stop();
        c.calls.clear();
        t.stop();
        expect("route guidance stopped (car took turn-by-turn back) after it was already cleared: nothing more (2nd review 2)");

        t.disconnected();
        expect("iAP2 session ended with nothing shown since: nothing more (2nd review 2)");
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("0.5"), 12, ENUM, i(MILES), 13, U16, i(5), 19, STR, s("Maps")));
        expect("after the session ended: maneuvers gone, a new source isn't a change", "");
        c.calls.clear();

        t.maneuver(msg(0, U16, i(7), 1, U16, i(0), 3, ENUM, i(LEFT), 4, STR, s("Comp 7 Rd"), 8, ENUM, i(0), 9, ENUM, i(0), 11, U8, i(-60)));
        t.routeGuidance(msg(0, U16, i(7), 1, ENUM, i(1), 11, STR, s("0.3"), 12, ENUM, i(MILES), 13, U16, i(0)));
        expect("a second component keeps its own state", "road [Comp 7 Rd]", "tbt 71 0 0.0 0.3 0 0 2 0 0",
                "hud f201470000" + utf16("Comp 7 Rd"), "2c 2 3 0 0 0 65535 0 0 65535");

        t.maneuver(msg(1, U16, i(0), 3, ENUM, i(STRAIGHT), 4, STR, s("Any Rd"), 8, ENUM, i(0), 9, ENUM, i(0)));
        t.routeGuidance(msg(1, ENUM, i(1), 11, STR, s("0.4"), 13, U16, i(0)));
        expect("no component ids: every known component gets it (1.7.4: each with its crossroad name)",
                "road [Any Rd]", "tbt 65 0 0.0 0.4 0 0 2 0 0", "hud f201410000" + utf16("Any Rd"), "2c 2 4 0 0 0 65535 0 0 65535",
                "road [Any Rd]", "tbt 65 0 0.0 0.4 0 0 2 0 0", "2c 2 4 0 0 0 65535 0 0 65535");

        t.routeGuidance(msg(0, U16, i(0), 1, ENUM, i(1), 11, STR, s("0.4"), 13, U16, i(0)));
        expect("component 0's first update: DiPO starts it empty (state 0), so the HUD is cleared (and the turn shown before)",
                CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");

        // ---- Korea Projection (korea-build): icons for every maneuver, refresh, crossroad name ----
        final int[] woke = new int[1];
        CpTurns k = new CpTurns(new android.content.Context(), c, null);
        k.clock = 2000000L;
        k.onActive = new Runnable() {
            @Override
            public void run() {
                woke[0]++;
            }
        };
        c.calls.clear();
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT), 4, STR, s("A"), 5, U32, i(300), 8, ENUM, i(0), 9, ENUM, i(0)));
        k.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0)));
        expect("left turn with no exit angle (Apple Maps, Naver Map in Korea): icon 70 (1.7.4: kept, owner decision pending)",
                "road [A]", "tbt 70 0 0.0 300.0 0 0 0 0 0", "hud f201460000" + utf16("A"), "2c 0 0 300 0 0 65535 0 0 65535");
        check("guidance on: the refresh is started once", woke[0] == 1 && k.active());

        k.refresh();
        expect("refresh right after a send: nothing");
        k.clock += 1000L;
        k.refresh();
        expect("refresh after 1 s without the phone: the crossroad name and turn again (HUD list unchanged, not resent)",
                "road [A]", "tbt 70 0 0.0 300.0 0 0 0 0 0", "2c 0 0 300 0 0 65535 0 0 65535");
        k.clock += 1000L;
        k.refresh();
        expect("refresh 2 s after the HUD list: the HUD list resent too",
                "hud f201460000" + utf16("A"), "road [A]", "tbt 70 0 0.0 300.0 0 0 0 0 0", "2c 0 0 300 0 0 65535 0 0 65535");
        k.clock += 10000L;
        k.refresh();
        expect("refresh 10 s later: the same",
                "hud f201460000" + utf16("A"), "road [A]", "tbt 70 0 0.0 300.0 0 0 0 0 0", "2c 0 0 300 0 0 65535 0 0 65535");
        check("refresh started only once", woke[0] == 1);

        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(99)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("200")));
        expect("1.7.4: a maneuver not in the stock table: no updateTBTInfo (the previous arrow stays), HUD list and 2C frozen with it, the crossroad name sent",
                "road [A]");
        k.clock += 2000L;
        k.refresh();
        expect("1.7.4: refresh: the last HUD list sent resent as it was, still no turn frame or 2C",
                "hud f20146000041", "road [A]");

        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(RIGHT), 11, U8, i(75)));
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 5, U32, i(150)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("150")));
        expect("1.7.4: right turn at 75 degrees (not in the stock table): not sent (no 67 band), HUD frozen too",
                "road [A]");
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("100")));
        expect("a new maneuver type without an angle: the old angle isn't carried over (left without an angle: 70; HUD list already 70)",
                "road [A]", "tbt 70 0 0.0 100.0 0 0 0 0 0", "2c 0 0 100 0 0 65535 0 0 65535");
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT), 11, U8, i(-130)));
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT), 5, U32, i(90)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("90")));
        expect("the same type named again without an angle (a new maneuver at a reused index): the sharp angle isn't kept",
                "road [A]", "tbt 70 0 0.0 90.0 0 0 0 0 0", "2c 0 0 90 0 0 65535 0 0 65535");

        k.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("state 0: turn, names and HUD cleared (1.7.2: stock no-guidance values)", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        k.clock += 5000L;
        k.refresh();
        expect("after state 0 nothing is resent");
        check("after state 0 the refresh ends", !k.active());

        k.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("90")));
        c.calls.clear();
        check("guidance back on: the refresh started again", woke[0] == 2 && k.active());
        k.clock += CpTurns.REFRESH_MAX_MS;
        k.refresh();
        expect("5 minutes without the phone: nothing resent");
        check("5 minutes without the phone: the refresh ends", !k.active());

        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("80")));
        c.calls.clear();
        k.disconnected();
        c.calls.clear();
        check("iAP2 session ended: the refresh ends", !k.active());

        check("1.7.4: no fallback icons: left-hand 90 left, 75 right, 30 right, keep left, unknown, roundabout -100, arrive j1: all 0",
                CpTurns.exactIcon(1, 1, 0, -90, 1) == 0 && CpTurns.exactIcon(2, 0, 0, 75, 1) == 0 && CpTurns.exactIcon(2, 0, 0, 30, 1) == 0
                        && CpTurns.exactIcon(13, 0, 0, 0, 1) == 0 && CpTurns.exactIcon(99, 0, 0, 0, 1) == 0
                        && CpTurns.exactIcon(99, 0, 1, -100, 1) == 0 && CpTurns.exactIcon(4, 1, 1, 0, 1) == 0
                        && CpTurns.exactIcon(12, 0, 1, 0, 1) == 0);
        check("turns ahead: sharp/slight 47-50 get their arrows", CpTurns.aheadIcon(47, 0, 0, 0) == 69
                && CpTurns.aheadIcon(48, 0, 0, 0) == 68 && CpTurns.aheadIcon(49, 0, 0, 0) == 71 && CpTurns.aheadIcon(50, 0, 0, 0) == 66);

        check("icons: sharp left 69, slight right 66, roundabout exit 96, 1.7.4: left-hand roundabout 192 (stock B), U-turn left-hand traffic 73",
                CpTurns.exactIcon(1, 0, 0, -120, 1) == 69 && CpTurns.exactIcon(2, 0, 0, 60, 1) == 66 && CpTurns.exactIcon(2, 0, 1, 0, 1) == 96
                        && CpTurns.exactIcon(31, 1, 1, 0, 1) == 192 && CpTurns.exactIcon(4, 1, 0, 0, 1) == 73 && CpTurns.exactIcon(47, 0, 0, -140, 1) == 69
                        && CpTurns.exactIcon(99, 0, 0, 0, 1) == 0);
        check("lane angles: 0 straight, 90 right, -45 slight left, 180 back",
                CpTurns.laneIcon(0) == 1 && CpTurns.laneIcon(90) == 3 && CpTurns.laneIcon(-45) == 8 && CpTurns.laneIcon(180) == 5
                        && CpTurns.laneIcon(270) == 7);
        indiaTurns();
        korea230();
        korea172();
        m15();
        beta2();
        stockTable();
        stockOrder();
        angleless();
        outsideFreeze();
        cpUnits();
        System.exit(fail);
    }


    /** 1.7.2 베타 2: 애플 지도 이름 번갈아(#132), 경로 시작 거리 0.0(#123) */
    static void beta2() {
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.clock = 9700000L;
        t.maneuver(maneuver(0, 2, 0, 0, 90, "Road S", 300));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(500), 11, STR, s("500"), 12, ENUM, i(2), 13, U16, i(0), 19, STR, s("Apple 지도")));
        c.calls.clear();
        t.clock += 10000L;
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("Apple Maps")));
        check("beta 2: a new source name (never seen): cleared once as before", c.calls.contains(CLEAR_TBT));
        t.maneuver(maneuver(0, 2, 0, 0, 90, "Road S", 300));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(480), 11, STR, s("480"), 12, ENUM, i(2), 13, U16, i(0)));
        c.calls.clear();
        t.clock += 20000L;
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("Apple 지도")));
        check("beta 2 (#132): back to a name seen 30 s ago (Apple Maps / Apple 지도): not cleared", !c.calls.contains(CLEAR_TBT) && !c.calls.contains(CLEAR_DEST));
        t.clock += 15000L;
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("Apple Maps")));
        check("beta 2 (#132): flipping again within a minute: still not cleared", !c.calls.contains(CLEAR_TBT));
        boolean noted = false;
        for (String x : Dipo.logs) if (x.contains("display kept, data asked again")) noted = true;
        check("beta 2: the kept name change is logged (no app names in that line)", noted);
        LinkedHashMap<Integer, ?> mm = t.maneuvers.get(1);
        check("beta 2 (review 1): a name seen again: stored turns emptied so the phone's resend rebuilds them (another app may have been in between)",
                mm == null || mm.isEmpty());
        c.calls.clear();
        t.clock += 3000L;
        t.refresh();
        check("beta 2 (2nd review 1): waiting for the resend: the old HUD list not resent, nothing cleared yet",
                !c.calls.contains(CLEAR_TBT) && last("hud ") == null);
        t.clock += 8000L;
        t.refresh();
        check("beta 2 (2nd review 1): no turns resent within 10 s: the old picture cleared", c.calls.contains(CLEAR_TBT));
        t.clock += 5000L;
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("Waze")));
        check("beta 2: a different app (never seen): cleared", c.calls.contains(CLEAR_TBT));
        t.maneuver(maneuver(0, 2, 0, 0, 90, "Road S", 300));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(450), 11, STR, s("450"), 12, ENUM, i(2), 13, U16, i(0)));
        c.calls.clear();
        t.clock += 120000L;
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("Apple Maps")));
        check("beta 2: back to a name last seen over a minute ago: cleared as a real app change", c.calls.contains(CLEAR_TBT));

        // 1.7.4: 경로 시작. 출발(11·18)은 순정 표에 없어 회전 프레임을 보내지 않는다. 거리를 모르면 순정처럼 0·오류 0(#123의 0.0이 다시 보임).
        // HUD 거리(판단 대기, 지금대로)는 출발의 0·모르는 거리에서 첫 칸을 비운다
        c = new Cluster();
        CpTurns d = new CpTurns(new android.content.Context(), c, null);
        d.clock = 9900000L;
        d.maneuver(maneuver(0, 11, 0, 0, 0, "Start", 0));
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("0"), 12, ENUM, i(2), 13, U16, i(0)));
        check("1.7.4 (#123): route start (StartRoute, shown 0): no turn frame at all (stock: not in the table)", last("tbt ") == null);
        boolean hud0 = false;
        for (String x : c.calls) if (x.startsWith("2c ") && !x.startsWith("2c 15 15 65535")) hud0 = true;
        check("beta 2 (#123): no HUD distance 0 at the route start (HUD side unchanged)", !hud0);
        c.calls.clear();
        d.maneuver(maneuver(0, 2, 0, 0, 90, "Road T", 300));
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s(""), 12, ENUM, i(2), 13, U16, i(0)));
        String st = last("tbt ");
        check("1.7.4 (#123 back): a turn with no shown distance yet: distance 0, error 0 (stock DiPO)", "tbt 67 0 0.0 0.0 0 0 0 0 0".equals(st));
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(600), 11, STR, s("600"), 12, ENUM, i(2), 13, U16, i(0)));
        st = last("tbt ");
        check("beta 2: once the phone gives the distance: sent as usual (error 0)", st != null && st.contains(" 600.0 0 "));
        c.calls.clear();
        d.maneuver(maneuver(1, 1, 0, 0, -90, "Road U", 300));
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s(""), 12, ENUM, i(2), 13, U16, i(1)));
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s(""), 12, ENUM, i(2), 13, U16, i(1)));
        int blanks = 0;
        for (String x : c.calls) if (x.equals(CLEAR_2C)) blanks++;
        check("beta 2 (review 2): distance unknown mid-route: the HUD's first point emptied once (not the last turn's distance)", blanks == 1);
        c.calls.clear();
        d.maneuver(maneuver(0, 18, 0, 0, 0, "Start", 0));
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("0"), 12, ENUM, i(2), 13, U16, i(0)));
        check("1.7.4: start with a U-turn (type 18): not in the stock table, no turn frame", last("tbt ") == null);
    }

}
