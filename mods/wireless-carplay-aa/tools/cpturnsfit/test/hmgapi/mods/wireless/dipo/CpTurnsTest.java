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
 * Host test of CpTurnsFit: a drive through iAP2 route guidance messages, checking every
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
        check("India Apple Maps left/right and straight", CpTurnsFit.icon(1, 1, 0, -90, 1) == 70
                && CpTurnsFit.icon(2, 1, 0, 90, 1) == 67 && CpTurnsFit.icon(3, 1, 0, 0, 1) == 65);
        for (int side = 0; side <= 1; side++) {
            check("explicit ordinary turns without optional angle, side " + side,
                    CpTurnsFit.icon(1, side, 0, 0, 1) == 70 && CpTurnsFit.icon(2, side, 0, 0, 1) == 67);
            check("departure is NO_INFO, not a straight arrow, side " + side,
                    CpTurnsFit.icon(11, side, 0, 0, 1) == 1);
        }
        check("invalid traffic sides do not gain ordinary turn/departure fallbacks",
                CpTurnsFit.icon(1, 2, 0, 0, 1) == 0 && CpTurnsFit.icon(2, -1, 0, 0, 1) == 0
                        && CpTurnsFit.icon(3, 2, 0, 0, 1) == 0 && CpTurnsFit.icon(11, 2, 0, 0, 1) == 0);
        // Korea Projection: an angle the table lacks gets the nearest arrow (band); departure stays no turn
        check("unlisted turn angles get the nearest arrow; departure with an angle or a junction stays no turn",
                CpTurnsFit.icon(1, 1, 0, -91, 1) == 70 && CpTurnsFit.icon(2, 1, 0, 91, 1) == 67
                        && CpTurnsFit.icon(11, 1, 0, 90, 1) == 0 && CpTurnsFit.icon(11, 1, 1, 0, 1) == 0
                        && CpTurnsFit.fallbackIcon(11, 1, 0, 90) == CpTurnsFit.NO_ICON && CpTurnsFit.fallbackIcon(11, 1, 1, 0) == CpTurnsFit.NO_ICON);
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 1000000L;
        t.maneuver(maneuver(0, 2, 1, 0, 90, "Test Road", 100));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("100"),
                12, ENUM, i(2), 13, U16, i(0)));
        expect("India Apple right turn reaches cluster and HUD",
                "road []", "tbt 67 0 0.0 100.0 0 0 0 0 -1",
                "hud f201430000" + utf16("Test Road"), "2c 0 0 100 0 0 65535 0 0 65535");
        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(11)));
        t.routeGuidance(msg(0, U16, i(1)));
        expect("Google departure clears prior HUD turn and sends non-directional NO_INFO",
                CLEAR_2C, "lanes f1", "hud f2", "hud f2",
                "road []", "tbt 1 0 0.0 100.0 0 0 0 0 -1", "2c 0 0 100 0 0 65535 0 0 65535");
        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(2)));
        t.routeGuidance(msg(0, U16, i(1)));
        expect("Google right without angle reaches cluster and HUD after departure",
                "tbt 67 0 0.0 100.0 0 0 0 0 -1",
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
        // 2-A: the stock DiPO numbers that differ from the US build
        check("2.3.0 on-ramp (9): 160 (was 172), also without the exact geometry",
                CpTurnsFit.icon(9, 0, 0, 0, 1) == 160 && CpTurnsFit.fallbackIcon(9, 1, 0, 0) == 160 && CpTurnsFit.icon(5, 0, 0, 0, 1) == 172);
        check("2.3.0 highway off-ramp left (22): 165 (was 82)", CpTurnsFit.icon(22, 0, 0, 0, 1) == 165);
        check("2.3.0 highway off-ramp right (23): 164 (was 81)", CpTurnsFit.icon(23, 0, 0, 0, 1) == 164);
        // 2-A last row: types the stock table leaves out
        check("2.3.0 off-ramp (8): 163", CpTurnsFit.icon(8, 0, 0, 0, 1) == 0 && CpTurnsFit.fallbackIcon(8, 0, 0, 0) == 163);
        check("2.3.0 exit ferry (16): 176", CpTurnsFit.fallbackIcon(16, 0, 0, 0) == 176);
        check("2.3.0 change ferry (17): 176", CpTurnsFit.fallbackIcon(17, 0, 0, 0) == 176);
        check("2.3.0 left turn at the end (20): 70", CpTurnsFit.fallbackIcon(20, 0, 0, 0) == 70);
        check("2.3.0 right turn at the end (21): 67", CpTurnsFit.fallbackIcon(21, 0, 0, 0) == 67);
        check("2.3.0 U-turn when possible (26): 72", CpTurnsFit.fallbackIcon(26, 0, 0, 0) == 72);
        check("2.3.0 end of directions (27): 112", CpTurnsFit.fallbackIcon(27, 0, 0, 0) == 112);
        check("2.3.0 change highway (51): 163", CpTurnsFit.fallbackIcon(51, 0, 0, 0) == 163);
        check("2.3.0 no turn (0), exit roundabout (7), start (11): blank 8",
                CpTurnsFit.fallbackIcon(0, 0, 0, 0) == 8 && CpTurnsFit.fallbackIcon(7, 0, 0, 0) == 8 && CpTurnsFit.fallbackIcon(11, 0, 0, 0) == 8);
        check("2.3.0 off-ramp (8) with an angle: still 163 (no left/right by angle)",
                CpTurnsFit.icon(8, 0, 0, 45, 1) == 0 && CpTurnsFit.fallbackIcon(8, 0, 0, 45) == 163 && CpTurnsFit.fallbackIcon(8, 0, 0, -45) == 163);
        check("2.3.0 highway off-ramp left/right (22/23) with an angle: 165/164",
                CpTurnsFit.fallbackIcon(22, 0, 0, -30) == 165 && CpTurnsFit.fallbackIcon(23, 0, 0, 30) == 164 && CpTurnsFit.icon(22, 0, 0, -30, 1) == 0);
        check("2.3.0 U-turn when possible (26) with an angle: 72, left-hand traffic 73",
                CpTurnsFit.fallbackIcon(26, 0, 0, -180) == 72 && CpTurnsFit.fallbackIcon(26, 1, 0, 180) == 73 && CpTurnsFit.fallbackIcon(26, 0, 0, 0) == 72);
        check("2.3.0 on-ramp (9) with an angle: 160", CpTurnsFit.fallbackIcon(9, 0, 0, 30) == 160 && CpTurnsFit.fallbackIcon(9, 0, 0, -60) == 160);
        check("2.3.0 the other added types with an angle keep their number (51 163, 16/17 176, 20/21 70/67, 27 112)",
                CpTurnsFit.fallbackIcon(51, 0, 0, 20) == 163 && CpTurnsFit.fallbackIcon(16, 0, 0, 90) == 176 && CpTurnsFit.fallbackIcon(17, 0, 0, -90) == 176
                        && CpTurnsFit.fallbackIcon(20, 0, 0, -90) == 70 && CpTurnsFit.fallbackIcon(21, 0, 0, 90) == 67 && CpTurnsFit.fallbackIcon(27, 0, 0, 30) == 112);
        check("2.3.0 turn ahead with no arrow: 8 (was 64)", CpTurnsFit.aheadIcon(99, 0, 0, 0) == 8 && CpTurnsFit.aheadIcon(9, 0, 0, 0) == 8);

        // 2-C: roundabout bands and their edges (right-hand traffic)
        int[][] edges = { { 22, 96 }, { 23, 97 }, { 67, 97 }, { 68, 99 }, { 112, 99 }, { 113, 100 }, { 157, 100 }, { 158, 102 },
            { -22, 96 }, { -23, 107 }, { -67, 107 }, { -68, 105 }, { -112, 105 }, { -113, 104 }, { -157, 104 }, { -158, 102 },
            { 1, 96 }, { -46, 107 }, { 45, 97 }, { 200, 102 }, { 300, 107 }, { 270, 105 } };
        for (int[] e : edges) check("2.3.0 roundabout band: " + e[0] + " degrees -> " + e[1], CpTurnsFit.roundaboutIcon(0, e[0]) == e[1]);
        // the stock DiPO's exact angles come first
        int[][] exact = { { -45, 104 }, { 315, 104 }, { 90, 99 }, { 135, 100 }, { -90, 105 }, { -135, 104 }, { 180, 102 }, { -180, 102 } };
        for (int[] e : exact) check("2.3.0 roundabout stock angle: " + e[0] + " degrees -> " + e[1], CpTurnsFit.roundaboutIcon(0, e[0]) == e[1]);
        check("2.3.0 roundabout through icon(): exit 4 at 0 -> 97 (stock), type 33 at -45 -> 104 (stock angle), -46 -> 107, 1 -> 96",
                CpTurnsFit.icon(31, 0, 1, 0, 1) == 97 && CpTurnsFit.icon(33, 0, 1, -45, 1) == 0 && CpTurnsFit.fallbackIcon(33, 0, 1, -45) == 104
                        && CpTurnsFit.fallbackIcon(33, 0, 1, -46) == 107 && CpTurnsFit.fallbackIcon(31, 0, 1, 1) == 96);
        check("2.3.0 roundabout, left-hand traffic: the side-1 stock angles (-30 107, 120 100, -120 104), no B family",
                CpTurnsFit.roundaboutIcon(1, -30) == 107 && CpTurnsFit.roundaboutIcon(1, 120) == 100 && CpTurnsFit.roundaboutIcon(1, -120) == 104
                        && CpTurnsFit.roundaboutIcon(1, 45) == 97 && CpTurnsFit.roundaboutIcon(1, -45) == 107);

        // 2-D: lanes (HMG-Seogaemo's glyph sets and colours)
        check("2.3.0 lane straight + left, left preferred: glyphs 9, 12; straight grey, left blue",
                arr(CpTurnsFit.laneValues(2, i(0, -90), i(-90))).equals(arr(i(0x0b, 9, 12, 0))));
        check("2.3.0 lane left + straight + right, straight good: glyphs 28, 22, 24; grey, white, grey",
                arr(CpTurnsFit.laneValues(1, i(-90, 0, 90), i(0))).equals(arr(i(0x37, 28, 22, 24))));
        check("2.3.0 lane straight + U-turn, not good: glyphs 9, 10, both grey",
                arr(CpTurnsFit.laneValues(0, i(0, 180), null)).equals(arr(i(0x0f, 9, 10, 0))));
        check("2.3.0 lane right + slight right listed in another order: the same row (18, 21)",
                arr(CpTurnsFit.laneValues(2, i(45, 90), i(90))).equals(arr(CpTurnsFit.laneValues(2, i(90, 45), i(90))))
                        && CpTurnsFit.laneValues(2, i(45, 90), i(90))[1] == 18);
        check("2.3.0 lane with no verified set (U-turn, left, straight, right): only the highlighted arrow, single glyph",
                arr(CpTurnsFit.laneValues(1, i(-180, -90, 0, 90), i(90))).equals(arr(i(1, 3, 0, 0))));
        check("2.3.0 lane sharp right + right (no row): folded to one right arrow",
                arr(CpTurnsFit.laneValues(2, i(135, 90), i(135))).equals(arr(i(2, 3, 0, 0))));
        check("2.3.0 lane with no angle: the status on slot 0 only, no glyph",
                arr(CpTurnsFit.laneValues(2, null, null)).equals(arr(i(2, 0, 0, 0))));

        // HUD list: a next turn without an arrow (8) is left out, count 1
        c = new Cluster();
        CpTurnsFit hl = new CpTurnsFit(new android.content.Context(), c, null);
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
        CpTurnsFit h = new CpTurnsFit(new android.content.Context(), c, null);
        h.clock = 3000000L;
        h.maneuver(maneuver(0, 2, 0, 0, 90, "R1", 500));
        h.maneuver(maneuver(1, 1, 0, 0, -90, "R2", 300));
        h.maneuver(maneuver(2, 3, 0, 0, 0, "R3", 900));
        h.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(500), 11, STR, s("500"), 12, ENUM, i(2), 13, U16, i(0, 1, 2)));
        check("2.3.0 2C three points: 500 m, 800 m, 1.7 km", only2c().equals("2c 0 0 500 0 0 800 1 7 1"));
        // 2.4.4 후보(4차 리뷰 중간 1): 500m → 800m는 받으면 멀리 있음이라 붙잡지 않고 바로 보낸다
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
                only2c().equals(CLEAR_2C) && CpTurnsFit.HUD_NO_POINT == 65535);
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
                CpTurnsFit.HUD_NAME_MAX == 78 && CpTurnsFit.hudName(repeat("전", 40), 78).equals(repeat("전", 39))
                        && CpTurnsFit.hudName(repeat("a", 38) + emoji, 78).equals(repeat("a", 38))
                        && CpTurnsFit.hudName(repeat("a", 37) + emoji, 78).equals(repeat("a", 37) + emoji)
                        && CpTurnsFit.hudName("Road A", 78).equals("Road A") && CpTurnsFit.hudName("", 78).equals(""));
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
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
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 6000000L;
        t.maneuver(maneuver(0, 2, 0, 0, 90, "Road A", 300));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0)));
        String tbt = null;
        for (String x : c.calls) if (x.startsWith("tbt ")) tbt = x;
        check("1.7.2 M-3: a turn goes out with the far-away bar (bar 0, barErr -1), side street 0, direction 0",
                "tbt 67 0 0.0 300.0 0 0 0 0 -1".equals(tbt));
        c.calls.clear();
        t.stop();
        expect("1.7.2 M-2: route guidance stopped after a turn: stock no-guidance turn (8, distance and bar invalid), empty names, 2C (15,15,65535)x3, HUD",
                CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        t.stop();
        expect("1.7.2 M-2: stopped again with nothing shown since: neither the turn nor the HUD is cleared twice (2nd review 2)");
        check("1.7.2 M-2: clear values within NavigationCluster's ranges (distanceErr -3..0, scale 0..4 or 15, barErr -2..0)",
                CpTurnsFit.CLEAR_ICON == 8 && CpTurnsFit.CLEAR_DIST_ERR >= -3 && CpTurnsFit.CLEAR_DIST_ERR <= 0 && CpTurnsFit.CLEAR_UNIT == 15
                        && CpTurnsFit.BAR_FAR_ERR == -1 && CpTurnsFit.BAR_FAR >= 0 && CpTurnsFit.BAR_FAR <= 100
                        && CpTurnsFit.HUD_CLEAR_UNIT == 15 && CpTurnsFit.HUD_CLEAR_TENTHS == 15);

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("250"), 13, U16, i(0)));
        c.calls.clear();
        t.disconnected();
        expect("1.7.2 M-2: iAP2 session ended during guidance: the turn and names are cleared too",
                CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");

        CpTurnsFit d = new CpTurnsFit(new android.content.Context(), c, null);
        d.clock = 6100000L;
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(6), 4, STR, s("Home")));
        expect("1.7.2: state 6 with only a destination name (no maneuver yet)", "dest 0 [Home]");
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("1.7.2 M-2: a destination name alone is cleared at the end too (no HUD clear: nothing was sent to the HUD)",
                CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST);

        CpTurnsFit n = new CpTurnsFit(new android.content.Context(), c, null);
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

        CpTurnsFit l = new CpTurnsFit(new android.content.Context(), c, null);
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

    /** 2.4.3: 마지막 tbt 줄이 {표시, 막대}인지 */
    static boolean db(int disp, int bar) {
        String t = last("tbt ");
        if (t == null) return false;
        String[] p = t.split(" ");
        return Integer.parseInt(p[1]) == disp && Integer.parseInt(p[8]) == bar;
    }

    /** 2.4.3 D값(소유자 결정 2026-11-15): 카플레이도 AA와 같은 규칙(이 회전에 폰이 처음 준 남은 거리, 기준 350m·1050m, 바닥 30m) */
    static void dynamicD() {
        Car.unit = Car.DISTANCE_KM;
        check("2.4.3 CarPlay D rule: max(30, min(base, first))", CpTurnsFit.dynamicD(350, 100) == 100 && CpTurnsFit.dynamicD(1050, 2000) == 1050
                && CpTurnsFit.dynamicD(350, 5) == 30);
        // ① 좌회전 직후 100m 앞 우회전(목록이 다음 회전으로 넘어감)
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17000000L;
        t.maneuver(nearManeuver(0, 1, 0, 0, -90, null, -1, "Left Rd"));
        t.maneuver(nearManeuver(1, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.routeGuidance(rgAtIdx(1, 40, 0));
        t.routeGuidance(rgAtIdx(1, 100, 1));
        check("2.4.3 CarPlay D (1): a right turn 100 m after a left turn: near at once, bar 90 (D 100 m)", db(13, 90));
        t.routeGuidance(rgAtIdx(1, 50, 1));
        check("2.4.3 CarPlay D (1): 50 m: bar 50", db(13, 50));
        // ② 처음 500m: 350m에서 진입
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17100000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road"));
        t.routeGuidance(rgAt(1, 500));
        check("2.4.3 CarPlay D (2): first 500 m: far", db(67, 0));
        t.routeGuidance(rgAt(1, 351));
        check("2.4.3 CarPlay D (2): 351 m: far", db(67, 0));
        t.routeGuidance(rgAt(1, 350));
        check("2.4.3 CarPlay D (2): 350 m: near, bar 90", db(13, 90));
        t.routeGuidance(rgAt(1, 175));
        check("2.4.3 CarPlay D (2): 175 m: bar 50", db(13, 50));
        // ③ 고속도로 출구(23) 처음 1500m → 1050m, 처음 600m → D 600m
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17200000L;
        t.maneuver(nearManeuver(0, 23, 0, 0, 0, null, -1, "Exit"));
        t.routeGuidance(rgAt(1, 1500));
        check("2.4.3 CarPlay D (3): highway exit at 1500 m: far (164)", db(164, 0));
        t.routeGuidance(rgAt(1, 1050));
        check("2.4.3 CarPlay D (3): 1050 m: near 17, bar 90", db(17, 90));
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17250000L;
        t.maneuver(nearManeuver(0, 23, 0, 0, 0, null, -1, "Exit"));
        t.routeGuidance(rgAt(1, 600));
        check("2.4.3 CarPlay D (3'): highway exit first seen at 600 m: near, bar 90 (D 600 m)", db(17, 90));
        t.routeGuidance(rgAt(1, 300));
        check("2.4.3 CarPlay D (3'): 300 m: bar 50", db(17, 50));
        // ④ 처음 거리(item 10) 없음
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17300000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road"));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("200"), 12, ENUM, i(2), 13, U16, i(0)));
        check("2.4.3 CarPlay D (4): no distance in metres yet: far", db(67, 0));
        t.routeGuidance(rgAt(1, 200));
        check("2.4.3 CarPlay D (4): its first distance 200 m: near, bar 90", db(13, 90));
        // ⑤ 거리가 늘어남: D도 늘어남(기준까지)
        t.routeGuidance(rgAt(1, 100));
        check("2.4.3 CarPlay D (5): 100 m: bar 50 (D 200 m)", db(13, 50));
        t.routeGuidance(rgAt(1, 300));
        // 2.4.4 후보: 100m → 300m(뜀)는 한 번 붙잡는다(앞 프레임 막대 50 그대로). 다음 갱신에서 2.4.3처럼 D가 300m로 는다
        check("2.4.4 CarPlay D (5): back to 300 m: held once, the bar-50 frame stays", db(13, 50) && "jump-hold".equals(t.nearWhy));
        t.routeGuidance(rgAt(1, 300));
        check("2.4.3 CarPlay D (5): back to 300 m: D grows to 300 m, still near, bar 90", db(13, 90));
        // 4차 리뷰 중간 1: 300m → 600m는 받으면 멀리 있음이라 붙잡지 않는다
        t.routeGuidance(rgAt(1, 600));
        check("2.4.3 CarPlay D (5): 600 m: D 350 m (base), far", db(67, 0));
        // ⑥ 재탐색(상태 5) 뒤: 처음 거리로 다시
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17400000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road"));
        t.routeGuidance(rgAt(1, 500));
        t.routeGuidance(rgAt(1, 300));
        check("2.4.3 CarPlay D (6): before rerouting: bar 80 (D 350 m)", db(13, 80));
        t.routeGuidance(rgAt(5, 300));
        check("2.4.3 CarPlay D (6): rerouting: display 10", db(10, 0));
        t.routeGuidance(rgAt(1, 120));
        check("2.4.3 CarPlay D (6): after rerouting, first 120 m: near, bar 90 (D caught again)", db(13, 90));
        // ⑦ 아주 짧은 처음 거리
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17500000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road"));
        t.routeGuidance(rgAt(1, 10));
        check("2.4.3 CarPlay D (7): first seen at 10 m: D 30 m, bar 30", db(13, 30));
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 마지막 tbt 줄이 멀리 있음(막대 무효 barErr -1)인지 */
    static boolean farNow() {
        String t = last("tbt ");
        return t != null && Integer.parseInt(t.split(" ")[9]) == -1;
    }

    /** c.calls에 멀리 있음 tbt 줄(barErr -1)이 하나라도 있는지 */
    static boolean farAny() {
        for (String x : c.calls) if (x.startsWith("tbt ") && Integer.parseInt(x.split(" ")[9]) == -1) return true;
        return false;
    }

    /** c.calls에 곧 회전 tbt 줄(barErr 0)이 하나라도 있는지 */
    static boolean anyNear() {
        for (String x : c.calls) if (x.startsWith("tbt ") && Integer.parseInt(x.split(" ")[9]) == 0) return true;
        return false;
    }

    /**
     * 1.8.0: 갈림(13 왼쪽 유지, 14 오른쪽 유지)의 곧 회전은 순정처럼 회전 13 + 비스듬한 방향 + 다른 길 비트다
     * (2.4.7: 오른쪽 갈림은 직진 비트 0, 왼쪽 갈림은 오른쪽 22.5도 비트 1).
     * 근거: 니로 순정 운행 2026-11-17·11-20. 오른쪽 갈림 "tbt icon 13 side 3 dir 22.5", 왼쪽 갈림 "tbt icon 13 side 16386 dir 337.5"
     * (세 번 모두, 직진 비트 없음), 25는 없음.
     */
    static void forkLikeStock() {
        Car.unit = Car.DISTANCE_KM;
        int[][] cases = { { 14, 0 }, { 13, 0 }, { 14, 45 }, { 14, 8 }, { 14, -20 }, { 13, 20 }, { 13, -45 } };
        String[] want = { "tbt 13 3 22.5 300.0 ", "tbt 13 16386 337.5 300.0 ", "tbt 13 5 45.0 300.0 ",
                "tbt 13 3 22.5 300.0 ", "tbt 13 3 22.5 300.0 ", "tbt 13 16386 337.5 300.0 ", "tbt 13 8194 315.0 300.0 " };
        String[] why = { "keep right, no angle: side 3 (straight + 22.5), dir 22.5 (stock)",
                "2.4.7 keep left, no angle: side 16386 (22.5 = bit 1 + 337.5 = bit 14, no straight bit, stock), dir 337.5",
                "keep right with the phone's exit angle 45: that angle (bit 2, dir 45.0) plus the straight bit",
                "review M1: keep right with a straight angle 8: the default 22.5, not a straight arrow",
                "review M1: keep right with a left angle -20: the default 22.5, not a left arrow",
                "review M1: keep left with a right angle 20: the default 337.5 (side 16386)",
                "2.4.7 keep left with the phone's exit angle -45: bit 13, dir 315.0 plus the 22.5 bit (not straight)" };
        for (int k = 0; k < cases.length; k++) {
            c = new Cluster();
            CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
            t.clock = 31000000L + k * 1000000L;
            t.maneuver(nearManeuver(0, cases[k][0], 0, 0, cases[k][1], null, -1, "Fork Rd"));
            t.routeGuidance(rgAt(1, 300));
            String l = last("tbt ");
            check("1.8.0 fork near like stock: " + why[k] + " (got " + l + ")", l != null && l.startsWith(want[k]) && !c.calls.toString().contains("tbt 25 "));
        }
        // 멀리(D 밖)는 전과 같다: 오른쪽 유지 66, 왼쪽 유지 71(순정 운행에서도 66·71)
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 35000000L;
        t.maneuver(nearManeuver(0, 14, 0, 0, 0, null, -1, "Fork Rd"));
        t.routeGuidance(rgAt(1, 900));
        check("1.8.0 fork far is unchanged: keep right at 900 m is 66 (got " + last("tbt ") + ")", last("tbt ").startsWith("tbt 66 "));
    }

    /**
     * 2.4.4 후보(베타 3, 공개 이슈 76번 2026-11-15 쏘렌토 MQ4 하이브리드·네이버 지도): 네이버 지도는 회전이 바뀌는 갱신에
     * item 10을 앞 회전의 남은 거리 그대로 넣는다. 그 값을 새 회전의 처음 거리로 잡으면 D가 바닥 30m가 되어 큰 그림이 약 1초 나간다.
     */
    static void staleNext() {
        Car.unit = Car.DISTANCE_KM;
        // ① 차 로그 09:11:57: 앞 회전 15m → 새 회전(Y자 13)의 첫 갱신 15m(앞 값) → 다음 갱신 2000m
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17600000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.maneuver(nearManeuver(1, 13, 0, 0, 0, null, -1, "Fork Rd"));
        t.routeGuidance(rgAtIdx(1, 300, 0));
        t.routeGuidance(rgAtIdx(1, 15, 0));
        check("2.4.4 stale next (1): the right turn at 15 m: near", !farNow());
        c.calls.clear();
        t.routeGuidance(rgAtIdx(1, 15, 1));
        check("2.4.4 stale next (1): new turn whose first distance is the old turn's last 15 m: far, decision put off (put-off)",
                farNow() && !anyNear() && "put-off".equals(t.nearWhy));
        t.routeGuidance(rgAtIdx(1, 2000, 1));
        check("2.4.4 stale next (1): the real 2000 m next: far, no big picture at any point", farNow() && !anyNear());
        t.routeGuidance(rgAtIdx(1, 350, 1));
        check("2.4.4 stale next (1): 350 m: near 13 (fork, 1.8.0), bar 90 (D 350 m)", db(13, 90));
        // ② 차 로그 11:39:30 유턴: 17m → 380m(막대 50 → 90으로 뛰던 줄)
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17700000L;
        t.maneuver(nearManeuver(0, 1, 0, 0, -90, null, -1, "Left Rd"));
        t.maneuver(nearManeuver(1, 4, 0, 0, 180, null, -1, "Back Rd"));
        t.routeGuidance(rgAtIdx(1, 200, 0));
        t.routeGuidance(rgAtIdx(1, 17, 0));
        c.calls.clear();
        t.routeGuidance(rgAtIdx(1, 17, 1));
        check("2.4.4 stale next (2): U-turn whose first distance is the old 17 m: far", farNow() && !anyNear());
        t.routeGuidance(rgAtIdx(1, 380, 1));
        check("2.4.4 stale next (2): the real 380 m: far (not hold with bar 90)", farNow() && !anyNear());
        t.routeGuidance(rgAtIdx(1, 350, 1));
        check("2.4.4 stale next (2): 350 m: near 31, bar 90", db(31, 90));
        // ③ 진짜로 바로 붙은 회전: 같은 값이 한 번 더 오면 받아들인다(영원히 미루지 않음)
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17800000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.maneuver(nearManeuver(1, 1, 0, 0, -90, null, -1, "Left Rd"));
        t.routeGuidance(rgAtIdx(1, 100, 0));
        t.routeGuidance(rgAtIdx(1, 15, 0));
        t.routeGuidance(rgAtIdx(1, 15, 1));
        check("2.4.4 stale next (3): first 15 m on the new turn: put off", farNow());
        t.routeGuidance(rgAtIdx(1, 15, 1));
        check("2.4.4 stale next (3): the same 15 m again: taken, near, D 30 m bar 50", db(13, 50));
        // ③' 다음 갱신이 더 짧아도(차가 다가감) 받아들인다: 미루기는 회전마다 한 번
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17850000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.maneuver(nearManeuver(1, 1, 0, 0, -90, null, -1, "Left Rd"));
        t.routeGuidance(rgAtIdx(1, 100, 0));
        t.routeGuidance(rgAtIdx(1, 20, 0));
        t.routeGuidance(rgAtIdx(1, 20, 1));
        t.routeGuidance(rgAtIdx(1, 12, 1));
        check("2.4.4 stale next (3'): put off once, then 12 m: near, D 30 m bar 40", db(13, 40));
        // ③'' 미루는 사이 거리 없는 갱신(item 10 없음)이 와도 새 거리가 오면 받아들인다
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17870000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.maneuver(nearManeuver(1, 1, 0, 0, -90, null, -1, "Left Rd"));
        t.routeGuidance(rgAtIdx(1, 100, 0));
        t.routeGuidance(rgAtIdx(1, 15, 0));
        t.routeGuidance(rgAtIdx(1, 15, 1));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("15"), 12, ENUM, i(2), 13, U16, i(1)));
        check("2.4.4 stale next (3''): no item 10 in between: still far (stale-dist)", farNow() && "stale-dist".equals(t.nearWhy));
        t.routeGuidance(rgAtIdx(1, 15, 1));
        check("2.4.4 stale next (3''): item 10 again: taken, near bar 50", db(13, 50));
        // ④ 새 회전의 처음 거리가 앞 회전의 마지막보다 길면 미루지 않는다(① D 시험과 같은 경우: 40m → 100m 바로 곧 회전)
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17900000L;
        t.maneuver(nearManeuver(0, 1, 0, 0, -90, null, -1, "Left Rd"));
        t.maneuver(nearManeuver(1, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.routeGuidance(rgAtIdx(1, 40, 0));
        t.routeGuidance(rgAtIdx(1, 41, 1));
        check("2.4.4 stale next (4): new turn first 41 m > old last 40 m: near at once, bar 90", db(13, 90));
        // ⑤ 재탐색 뒤 첫 회전은 앞 경로의 마지막 거리와 견주지 않는다(D 시험 ⑥: 300m → 재탐색 → 120m 바로 곧 회전)
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17950000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road"));
        t.routeGuidance(rgAt(1, 300));
        t.routeGuidance(rgAt(5, 300));
        t.maneuver(nearManeuver(3, 1, 0, 0, -90, null, -1, "New Rd"));
        t.routeGuidance(rgAtIdx(1, 120, 3));
        check("2.4.4 stale next (5): after rerouting, a new turn at 120 m (< old 300 m): near at once, bar 90", db(13, 90));
        // ⑥ 같은 회전 번호에서 종류만 바뀜(폰이 회전 정보를 고쳐 보냄): 회전이 바뀐 것이 아니므로 미루지 않는다(멀리 있음이 끼면 반대로 깜빡임)
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 17980000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.routeGuidance(rgAt(1, 300));
        t.routeGuidance(rgAt(1, 150));
        check("2.4.4 stale next (6): right turn at 150 m: near", db(13, 50));
        c.calls.clear();
        t.maneuver(nearManeuver(0, 4, 0, 0, 180, null, -1, "Right Rd"));
        t.routeGuidance(rgAt(1, 140));
        check("2.4.4 stale next (6): same maneuver index, type changed to U-turn at 140 m: near at once, no far frame", db(31, 90) && !farAny());
        Car.unit = Car.DISTANCE_MILES;
    }

    /** c.calls의 tbt 줄 수 */
    static int tbtCount() {
        int n = 0;
        for (String x : c.calls) if (x.startsWith("tbt ")) n++;
        return n;
    }

    /**
     * 2.4.4 후보 재설계: 카카오내비 순서(니로 차 로그 2026-11-15 베타 2, logs/2026-11-15-niro-1.7.4-beta.2/3-log.txt 14:01~14:04).
     * 회전이 바뀔 때 새 회전의 거리가 옛 회전 번호로 먼저 오고, 30~130ms 뒤에 새 번호가 같은 거리로 온다(네이버 지도와 반대).
     * 문제 1: 앞 값 미루기가 이 새 번호를 미뤄 멀리 있음이 낀다. 문제 2: 지난 회전 그림이 새 거리의 막대로 한 번 나간다.
     */
    static void kakaoOrder() {
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 19500000L;
        t.maneuver(nearManeuver(3, 2, 0, 0, 90, null, -1, "R3"));
        t.maneuver(nearManeuver(6, 1, 0, 0, -90, null, -1, "L6"));
        t.maneuver(nearManeuver(9, 1, 0, 0, -90, null, -1, "L9"));
        t.maneuver(nearManeuver(12, 1, 0, 0, -90, null, -1, "L12"));
        t.maneuver(nearManeuver(14, 10, 0, 0, 0, null, -1, "Dest"));
        t.routeGuidance(rgAtIdx(1, 36, 3));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 10, 3));
        check("2.4.4 Kakao 14:01:36.180: right turn 3 at 10 m: near bar 20 (D 36 m)", db(13, 20));
        // 14:01:36.220 옛 번호 3 + 새 거리 274m
        c.calls.clear();
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        check("2.4.4 Kakao 14:01:36.220: turn 3 with the next turn's 274 m: the right-turn picture not redrawn with bar 90 (frame kept)",
                tbtCount() == 0 && "jump-hold".equals(t.nearWhy));
        // 14:01:36.350 새 번호 6 + 같은 274m
        t.clock += 130L;
        t.routeGuidance(rgAtIdx(1, 274, 6));
        check("2.4.4 Kakao 14:01:36.350: turn 6 at 274 m: near at once (left, bar 90, D 274 m), no far frame", db(13, 90) && !farAny());
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 242, 6));
        check("2.4.4 Kakao 14:01:40: 242 m: bar 80 (D 274 m)", db(13, 80));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 4, 6));
        check("2.4.4 Kakao 14:02:02.170: 4 m: bar 10", db(13, 10));
        c.calls.clear();
        t.clock += 20L;
        t.routeGuidance(rgAtIdx(1, 485, 6));
        // 4차 리뷰 중간 1: 받아들이면 멀리 있음이 될 뛴 값은 붙잡지 않고 바로 보낸다(HEAD와 같은 바른 출력)
        check("2.4.4 Kakao 14:02:02.190: turn 6 with 485 m (far if taken): sent at once as far, no bar redraw", tbtCount() == 1 && farNow()
                && !anyNear());
        t.clock += 100L;
        t.routeGuidance(rgAtIdx(1, 485, 9));
        check("2.4.4 Kakao 14:02:02.290: turn 9 at 485 m: far (D 350 m), no near frame of turn 6", farNow() && !anyNear());
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 1, 9));
        check("2.4.4 Kakao 14:04:20.620: turn 9 at 1 m: near bar 10", db(13, 10));
        c.calls.clear();
        t.clock += 20L;
        t.routeGuidance(rgAtIdx(1, 297, 9));
        t.clock += 140L;
        t.routeGuidance(rgAtIdx(1, 297, 12));
        check("2.4.4 Kakao 14:04:20.640-.780: 297 m kept, then turn 12 at 297 m: one near frame, bar 90 (D 297 m), no bar 80 of turn 9",
                tbtCount() == 1 && db(13, 90) && !farAny());
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 1, 12));
        c.calls.clear();
        t.clock += 50L;
        t.routeGuidance(rgAtIdx(1, 200, 12));
        t.clock += 60L;
        t.routeGuidance(rgAtIdx(1, 200, 14));
        check("2.4.4 Kakao 14:04:58.670-.780: 200 m kept, then the destination (type 10) far, no bar 60 of turn 12",
                tbtCount() == 1 && farNow() && !anyNear());
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 카카오 순서 시험의 시작: 우회전 3을 36m → 10m까지(곧 회전, 막대 20) */
    static CpTurnsFit kakaoAt10(long clock) {
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = clock;
        t.maneuver(nearManeuver(3, 2, 0, 0, 90, null, -1, "R3"));
        t.maneuver(nearManeuver(6, 1, 0, 0, -90, null, -1, "L6"));
        t.maneuver(nearManeuver(9, 1, 0, 0, -90, null, -1, "L9"));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(36), 11, STR, s("36"), 12, ENUM, i(2), 13, U16, i(3, 6, 9)));
        t.clock += 1000L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(10), 11, STR, s("10"), 12, ENUM, i(2), 13, U16, i(3, 6, 9)));
        return t;
    }

    /** 2.4.4 후보 재설계: 붙잡기의 refresh·재탐색·HUD·item 10 없는 번호 갱신 */
    static void kakaoOrderMore() {
        Car.unit = Car.DISTANCE_KM;
        // HUD: 붙잡는 갱신은 2C도 보내지 않고, 새 번호에서 274m + 다음 구간 500m
        CpTurnsFit t = kakaoAt10(19600000L);
        check("2.4.4 Kakao HUD: before: 2C 10 m, 510 m", last("2c ").startsWith("2c 0 0 10 0 0 510 "));
        c.calls.clear();
        t.clock += 40L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(274), 11, STR, s("274"), 12, ENUM, i(2), 13, U16, i(3, 6, 9)));
        check("2.4.4 Kakao HUD: held: nothing sent (no tbt, no 2C, no HUD list)", c.calls.isEmpty());
        t.clock += 130L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(274), 11, STR, s("274"), 12, ENUM, i(2), 13, U16, i(6, 9)));
        check("2.4.4 Kakao HUD: turn 6: 2C 274 m, 774 m; near bar 90", last("2c ").startsWith("2c 0 0 274 0 0 774 ") && db(13, 90));
        // refresh가 REFRESH_MS 안이면 붙잡기를 풀지 않는다(lastSendAt)
        t = kakaoAt10(19700000L);
        c.calls.clear();
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.clock += 100L;
        t.refresh();
        check("2.4.4 Kakao refresh 100 ms after the hold: nothing resent", tbtCount() == 0);
        t.clock += 30L;
        t.routeGuidance(rgAtIdx(1, 274, 6));
        check("2.4.4 Kakao refresh: then turn 6: near bar 90, no far", db(13, 90) && !farAny());
        // 새 번호가 1초 넘게 늦으면 refresh가 붙잡기를 풀어 2.4.3처럼 D를 늘려 받아들인다(지난 회전 그림 bar 90, 알려진 한계).
        // 그 뒤 새 번호가 같은 값으로 오면 앞 회전의 마지막 값이 뛴 값이므로 미루지 않는다
        t = kakaoAt10(19800000L);
        c.calls.clear();
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.clock += 1000L;
        t.refresh();
        check("2.4.4 Kakao late number: refresh 1 s later takes the jump on the same turn (D 274 m, bar 90, right turn)", db(13, 90)
                && last("tbt ").contains(" 90.0 274.0 "));
        c.calls.clear();
        t.clock += 300L;
        t.routeGuidance(rgAtIdx(1, 270, 6));
        check("2.4.4 Kakao late number: turn 6 at 270 m: near at once, left (dir 270), not put off against the jumped 274 m",
                db(13, 90) && !farAny() && last("tbt ").split(" ")[3].equals("270.0"));
        // 되돌아감(새 번호가 오지 않음): 다음 갱신에서 받아들인다(영원히 붙잡지 않음)
        t = kakaoAt10(19900000L);
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 270, 3));
        check("2.4.4 Kakao no new turn: the next update takes it (D 274 m, bar 90)", db(13, 90) && !"jump-hold".equals(t.nearWhy));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 137, 3));
        check("2.4.4 Kakao no new turn: 137 m: bar 50 (D 274 m)", db(13, 50));
        // 새 번호 갱신에 item 10이 없어도 붙잡은 값(새 회전의 거리)으로 받는다
        t = kakaoAt10(20000000L);
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        c.calls.clear();
        t.clock += 100L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("274"), 12, ENUM, i(2), 13, U16, i(6)));
        check("2.4.4 Kakao number without item 10 right after the hold: taken (near bar 90), not stale-dist", db(13, 90) && !farAny());
        // 재탐색·안내 끝·이름 돌아옴(nearForget)은 붙잡은 값을 버린다: 그 뒤 item 10 없는 새 번호는 stale-dist(멀리 있음)
        t = kakaoAt10(20100000L);
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.clock += 100L;
        t.routeGuidance(rgAtIdx(5, 274, 3));
        t.clock += 1000L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("274"), 12, ENUM, i(2), 13, U16, i(6)));
        check("2.4.4 Kakao: rerouting forgets the held value: a new turn without item 10 afterwards is stale-dist (far)",
                farNow() && "stale-dist".equals(t.nearWhy));
        t = kakaoAt10(20200000L);
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.stop();
        t.clock += 1000L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("274"), 12, ENUM, i(2), 13, U16, i(6)));
        check("2.4.4 Kakao: the guidance stopped forgets the held value too", farNow() && "stale-dist".equals(t.nearWhy));
        // GPS 흔들림(같은 회전 150 → 165 → 140m)은 붙잡지 않는다
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 20300000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road"));
        t.routeGuidance(rgAt(1, 200));
        t.routeGuidance(rgAt(1, 150));
        c.calls.clear();
        t.routeGuidance(rgAt(1, 165));
        check("2.4.4: wobble 150 -> 165 m: not held (frame sent, D 200 m, bar 80)", tbtCount() == 1 && db(13, 80));
        t.routeGuidance(rgAt(1, 20));
        c.calls.clear();
        t.routeGuidance(rgAt(1, 35));
        check("2.4.4: right at the turn 20 -> 35 m (rise 15 <= 20): not held", tbtCount() == 1);
        // 네이버 지도 순서는 그대로 미룬다(앞 회전 마지막 값이 뛴 값이 아님)
        t = afterTurn0(20400000L, 13, 15);
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 1));
        check("2.4.4 Naver order still put off after the redesign", farNow() && "put-off".equals(t.nearWhy));
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 우회전 3을 36m부터 거리 차례로 보낸 관리자(1초 간격, 회전 6·9 있음) */
    static CpTurnsFit turn3(long clock, int... ms) {
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = clock;
        t.maneuver(nearManeuver(3, 2, 0, 0, 90, null, -1, "R3"));
        t.maneuver(nearManeuver(6, 1, 0, 0, -90, null, -1, "L6"));
        t.maneuver(nearManeuver(9, 1, 0, 0, -90, null, -1, "L9"));
        for (int m : ms) {
            t.routeGuidance(rgAtIdx(1, m, 3));
            t.clock += 1000L;
        }
        return t;
    }

    /** 2.4.4 후보 재설계 3차 리뷰(중간 3·낮음 6) 반영 시험 */
    static void redesignReview() {
        Car.unit = Car.DISTANCE_KM;
        // 중간 1: 카카오 순서에서 다음 회전이 가까움(오름 18m, 붙잡기 문턱 20 이하): 3@10 → 3@28 → 6@28. 6@28을 미루지 않는다
        CpTurnsFit t = turn3(21000000L, 36, 10);
        t.routeGuidance(rgAtIdx(1, 28, 3));
        check("2.4.4 3rd review M1: 3@10 -> 3@28 (rise 18 m): not held, the old picture redrawn (bar 70, as before)", db(13, 70));
        c.calls.clear();
        t.clock += 100L;
        t.routeGuidance(rgAtIdx(1, 28, 6));
        check("2.4.4 3rd review M1: then 6@28: near at once (left, D 30 m), not put off", db(13, 90) && !farAny()
                && last("tbt ").split(" ")[3].equals("270.0"));
        // 경계: 10 → 30m(오름 20, 붙잡지 않음)와 10 → 31m(붙잡음) 모두 새 회전은 바로 큰 그림
        t = turn3(21100000L, 36, 10);
        t.routeGuidance(rgAtIdx(1, 30, 3));
        t.clock += 100L;
        c.calls.clear();
        t.routeGuidance(rgAtIdx(1, 30, 6));
        check("2.4.4 3rd review M1 edge: 3@10 -> 3@30 -> 6@30: near at once", db(13, 90) && !farAny());
        t = turn3(21150000L, 36, 10, 31);
        c.calls.clear();
        t.routeGuidance(rgAtIdx(1, 31, 6));
        check("2.4.4 3rd review M1 edge: 3@10 -> 3@31 (held) -> 6@31: near at once", db(13, 90) && !farAny());
        // 경계: 35 → 135m(오름 100, 붙잡지 않음) → 6@135: 바로 큰 그림
        t = turn3(21200000L, 300, 35);
        t.routeGuidance(rgAtIdx(1, 135, 3));
        t.clock += 100L;
        c.calls.clear();
        t.routeGuidance(rgAtIdx(1, 135, 6));
        check("2.4.4 3rd review M1 edge: 3@35 -> 3@135 -> 6@135: near at once (D 135 m, bar 90)", db(13, 90) && !farAny());
        // 네이버의 묵은 값(앞 값과 같음)은 늘어난 값이 아니므로 그대로 미룬다
        t = turn3(21250000L, 300, 15);
        t.routeGuidance(rgAtIdx(1, 15, 6));
        check("2.4.4 3rd review M1: Naver stale (same as the last) still put off", farNow() && "put-off".equals(t.nearWhy));
        // 붙잡기 문턱 경계(같은 회전): 앞 값 30m 이하에서 오름 20m는 붙잡지 않고 21m는 붙잡음. 앞 값 31m에서 오름 21m는 붙잡지 않음.
        // 어디서든 오름 100m는 붙잡지 않고 101m는 붙잡음
        int[][] edge = { { 10, 30, 0 }, { 10, 31, 1 }, { 30, 51, 1 }, { 31, 52, 0 }, { 200, 300, 0 }, { 200, 301, 1 } };
        for (int[] e : edge) {
            t = turn3(21300000L, 350, e[0]);
            c.calls.clear();
            t.routeGuidance(rgAtIdx(1, e[1], 3));
            boolean held = tbtCount() == 0;
            check("2.4.4 3rd review edge: " + e[0] + " -> " + e[1] + " m on the same turn: " + (e[2] == 1 ? "held" : "not held"),
                    held == (e[2] == 1) && ("jump-hold".equals(t.nearWhy)) == (e[2] == 1));
        }
        // 중간 2: 새 회전의 진짜 첫 거리가 앞 회전 마지막과 같음(0@20 → 1@20, 5차 리뷰 뒤 미루는 것은 같은 값뿐), 정차(item 10 없는 갱신·refresh만)
        t = afterTurn0(21400000L, 1, 20);
        t.routeGuidance(rgAtIdx(1, 20, 1));
        check("2.4.4 3rd review M2: 0@20 -> 1@20: put off", farNow() && "put-off".equals(t.nearWhy));
        t.clock += 1000L;
        t.refresh();
        check("2.4.4 3rd review M2: stopped, refresh 1 s later: still far (first refresh does not release)", farNow());
        t.clock += 1000L;
        t.refresh();
        check("2.4.4 3rd review M2: stopped, refresh 2 s after the put-off: the put-off 20 m taken (near, D 30 m, bar 60)", db(13, 60));
        // 중간 2: item 10 없는 갱신만 오는 정차도 한도 뒤에 풀린다
        t = afterTurn0(21500000L, 1, 20);
        t.routeGuidance(rgAtIdx(1, 20, 1));
        t.clock += 1000L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("20"), 12, ENUM, i(2), 13, U16, i(1)));
        check("2.4.4 3rd review M2: update without item 10 1 s later: still far", farNow() && "stale-dist".equals(t.nearWhy));
        t.clock += 1000L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("20"), 12, ENUM, i(2), 13, U16, i(1)));
        check("2.4.4 3rd review M2: update without item 10 2 s later: taken (bar 60)", db(13, 60));
        // 네이버 순서(15m → 약 1초 뒤 2000m)는 한도와 상관없이 막힌다: refresh가 1초에 끼어도
        t = afterTurn0(21600000L, 13, 15);
        c.calls.clear();
        t.routeGuidance(rgAtIdx(1, 15, 1));
        t.clock += 900L;
        t.refresh();
        t.clock += 60L;
        t.routeGuidance(rgAtIdx(1, 2000, 1));
        t.clock += 1000L;
        t.refresh();
        t.clock += 1000L;
        t.refresh();
        check("2.4.4 3rd review M2: Naver 15 m, refresh at 0.9 s, real 2000 m at 0.96 s, refreshes after: far all along", farNow() && !anyNear());
        // 낮음: put-off 진단 줄에 old가 붙지 않는다(item 10이 실제로 왔음). 그 뒤 refresh는 old(이번에 거리 없음)
        t = afterTurn0(21700000L, 13, 15);
        t.debug = true;
        diagLines();
        t.routeGuidance(rgAtIdx(1, 15, 1));
        java.util.List<String> d = diagLines();
        check("2.4.4 3rd review low: the put-off diag line says remain 15m (no old) and put-off",
                d.size() == 1 && d.get(0).contains("remain 15m d ") && d.get(0).contains("(put-off)"));
        // 낮음: 상태 0·탐색 뒤 nearWhy는 비운다
        t = kakaoAt10(21800000L);
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.routeGuidance(rgAtIdx(5, 274, 3));
        check("2.4.4 3rd review low: after rerouting the reason is cleared (not jump-hold)", "".equals(t.nearWhy));
        t = kakaoAt10(21850000L);
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        check("2.4.4 3rd review low: after state 0 the reason is cleared", "".equals(t.nearWhy));
        // 낮음: 출처 바뀜·연결 끊김도 붙잡은 값을 버린다
        t = kakaoAt10(21900000L);
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.disconnected();
        t.maneuver(nearManeuver(6, 1, 0, 0, -90, null, -1, "L6"));
        c.calls.clear();
        t.clock += 100L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("274"), 12, ENUM, i(2), 13, U16, i(6)));
        check("2.4.4 3rd review low: disconnected forgets the held value (a new turn without item 10: no near)", !anyNear());
        t = kakaoAt10(21950000L);
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("App A")));
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("App B")));
        t.maneuver(nearManeuver(6, 1, 0, 0, -90, null, -1, "L6"));
        c.calls.clear();
        t.clock += 100L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("274"), 12, ENUM, i(2), 13, U16, i(6)));
        check("2.4.4 3rd review low: a new source forgets the held value (a new turn without item 10: no near)", !anyNear());
        // 낮음: "nearLastJump = ... !newKey": 붙잡은 값을 새 회전의 첫 값으로 받은 뒤 곧바로 다음 번호가 같은 값(묵은 값)이면 미룬다
        t = kakaoAt10(22000000L);
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.clock += 100L;
        t.routeGuidance(rgAtIdx(1, 274, 6));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 274, 9));
        check("2.4.4 3rd review low: the held value taken as turn 6's first, then turn 9 with the same 274 m (stale): put off",
                farNow() && "put-off".equals(t.nearWhy));
        // 낮음: 미루기의 !jumpPending: 붙잡는 중에 새 회전이 앞 값(붙잡기 전 값) 이하로 와도 미루지 않는다
        t = turn3(22100000L, 300, 25);
        t.routeGuidance(rgAtIdx(1, 300, 3));
        check("2.4.4 3rd review low: 3@25 -> 3@300 held", "jump-hold".equals(t.nearWhy));
        c.calls.clear();
        t.clock += 100L;
        t.routeGuidance(rgAtIdx(1, 20, 6));
        check("2.4.4 3rd review low: then 6@20 (<= the old 25 m) while held: taken at once (near, D 30 m, bar 60), not put off", db(13, 60) && !farAny());
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 회전 18(우회전)·21(종류 type) 관리자 */
    static CpTurnsFit turns1821(long clock, int type) {
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = clock;
        t.maneuver(nearManeuver(18, 2, 0, 0, 90, null, -1, "R18"));
        t.maneuver(nearManeuver(21, type, 0, 0, -90, null, -1, "L21"));
        return t;
    }

    /** 시각(ms, 시작 기준)마다 갱신을 보내고, phase부터 1초마다 refresh()를 부른다(Dipo 타이머). until까지 */
    static void timed(CpTurnsFit t, long[] at, Iap2NotiMsg[] msgs, long phase, long until, long clearAt) {
        long start = t.clock, tick = phase;
        int k = 0;
        boolean cleared = false;
        while (true) {
            long next = k < at.length ? at[k] : Long.MAX_VALUE;
            if (tick <= until && tick < next) {
                t.clock = start + tick;
                if (!cleared && tick >= clearAt) { c.calls.clear(); cleared = true; }
                t.refresh();
                tick += 1000L;
            } else if (next != Long.MAX_VALUE) {
                t.clock = start + next;
                if (!cleared && next >= clearAt) { c.calls.clear(); cleared = true; }
                t.routeGuidance(msgs[k]);
                k++;
            } else {
                break;
            }
        }
    }

    /** 2.4.4 후보 4차 리뷰(중간 3·낮음 5) 반영 시험 */
    static void review4() {
        Car.unit = Car.DISTANCE_KM;
        // 중간 1: 잘못 받은 큰 그림(묵은 15m를 두 번째에 받아들임) 뒤 실제 2000m가 오면, 붙잡지 않고 바로 멀리 있음
        CpTurnsFit t = turns1821(23000000L, 13);
        t.routeGuidance(rgAtIdx(1, 300, 18));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 18));
        t.clock += 30L;
        t.routeGuidance(rgAtIdx(1, 15, 21));
        check("2.4.4 4th review M1: 21@15 put off", farNow() && "put-off".equals(t.nearWhy));
        t.clock += 300L;
        t.routeGuidance(rgAtIdx(1, 15, 21));
        check("2.4.4 4th review M1: 21@15 again: taken (near 13 (fork, 1.8.0), bar 50)", db(13, 50));
        c.calls.clear();
        t.clock += 700L;
        t.routeGuidance(rgAtIdx(1, 2000, 21));
        check("2.4.4 4th review M1: 21@2000 (would be far): sent at once as far, not held", tbtCount() == 1 && farNow()
                && !"jump-hold".equals(t.nearWhy));
        // 중간 1: 뛴 값을 받아도 곧 회전이 유지되면 그대로 붙잡는다(카카오 3@274)
        t = turn3(23100000L, 36, 10);
        c.calls.clear();
        t.routeGuidance(rgAtIdx(1, 274, 3));
        check("2.4.4 4th review M1: 3@10 -> 3@274 (still near if taken): held", tbtCount() == 0 && "jump-hold".equals(t.nearWhy));
        // 중간 2: 앞 회전 마지막 갱신이 조금(2m) 오른 값이어도 네이버 순서는 미룬다
        t = turns1821(23200000L, 13);
        t.routeGuidance(rgAtIdx(1, 300, 18));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 13, 18));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 18));
        c.calls.clear();
        t.clock += 30L;
        t.routeGuidance(rgAtIdx(1, 15, 21));
        check("2.4.4 4th review M2: 18@13 -> 18@15 (rise 2 m) -> 21@15: put off", farNow() && "put-off".equals(t.nearWhy));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 2000, 21));
        check("2.4.4 4th review M2: then 21@2000: far, no near at any point", farNow() && !anyNear());
        // 오름 크기·시간 경계: 오름 10m를 500ms 안에 받았으면 건너뜀(바로 큰 그림), 9m면 미룸, 501ms 지나면 미룸
        int[][] e = { { 10, 400, 1 }, { 9, 400, 0 }, { 10, 500, 1 }, { 10, 501, 0 } };
        for (int[] x : e) {
            t = turn3(23300000L, 300, 10);
            t.routeGuidance(rgAtIdx(1, 10 + x[0], 3));
            t.clock += x[1];
            c.calls.clear();
            t.routeGuidance(rgAtIdx(1, 10 + x[0], 6));
            boolean skipped = !farNow();
            check("2.4.4 4th review edge: rise " + x[0] + " m, number " + x[1] + " ms later: " + (x[2] == 1 ? "taken at once" : "put off"),
                    skipped == (x[2] == 1));
        }
        // 낮음 2: 받아들이면 nearPutOff를 비운다(같은 회전이 나중에 다시 와도 한 번 미룰 수 있게 한도 시계가 남지 않음)
        t = afterTurn0(23400000L, 1, 20);
        t.routeGuidance(rgAtIdx(1, 12, 1));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 12, 1));
        check("2.4.4 4th review low 2: taken, nearPutOff cleared", db(13, 40) && t.updates.get(1).nearPutOff == null);
        // 타이머 위상 훑기(50ms 간격 20가지): 네이버 순서는 큰 그림 0, 카카오 순서는 멀리 있음·지난 회전 그림 0
        int bad = 0;
        for (long ph = 0; ph < 1000; ph += 50) {
            t = turns1821(23500000L + ph * 10000L, 13);
            timed(t, new long[] { 0, 1000, 1040, 1990, 2990 },
                    new Iap2NotiMsg[] { rgAtIdx(1, 300, 18), rgAtIdx(1, 15, 18), rgAtIdx(1, 15, 21), rgAtIdx(1, 2000, 21), rgAtIdx(1, 1985, 21) },
                    ph, 4000, 1040);
            if (anyNear()) bad++;
        }
        check("2.4.4 4th review: Naver order over 20 timer phases: no near frame after the change (bad " + bad + ")", bad == 0);
        bad = 0;
        for (long ph = 0; ph < 1000; ph += 50) {
            t = turn3(24000000L + ph * 10000L);
            timed(t, new long[] { 0, 1000, 1040, 1170, 2170 },
                    new Iap2NotiMsg[] { rgAtIdx(1, 36, 3), rgAtIdx(1, 10, 3), rgAtIdx(1, 274, 3), rgAtIdx(1, 274, 6), rgAtIdx(1, 250, 6) },
                    ph, 3000, 1040);
            boolean ok = !farAny();
            for (String x : c.calls) if (x.startsWith("tbt ") && !x.split(" ")[3].equals("270.0")) ok = false;
            if (!ok) bad++;
        }
        check("2.4.4 4th review: Kakao order over 20 timer phases: no far frame, no right-turn frame after the jump (bad " + bad + ")", bad == 0);
        Car.unit = Car.DISTANCE_MILES;
    }

    /**
     * 2.4.4 후보(나): 옆길 비트를 순정 내비가 보낸 값에 맞춘다(니로 차 로그 logs/usb-1111-before-173·logs/car-1111-173beta1의
     * "stock navigation: tbt icon 13" 줄, FINDINGS "좌회전 큰 그림의 옆길 비트가 한 칸 어긋남"). 뒤쪽(180도 ±12)에 비트가 없어
     * 왼쪽 절반은 비트 8~14(오른쪽 비트 1~7과 거울), 방향 칸은 나가는 길 각도 그대로(7.5도 단위)
     */
    static void stockSideBits() {
        int[][] e = { { -90, 11, 36 }, { -113, 10, 33 }, { -112, 10, 33 }, { 90, 4, 12 }, { 22, 1, 3 }, { 23, 1, 3 }, { 0, 0, 0 } };
        for (int[] x : e) {
            check("2.4.4 stock side bits: exit " + x[0] + " deg: bit " + x[1] + ", slot " + x[2],
                    CpTurnsFit.exitBit(x[0]) == x[1] && CpTurnsFit.exitSlot(x[0]) == x[2]);
        }
        // 순정 줄 다섯 가지(다른 길 각도 + 나가는 길): side 2065 dir 270, 1025 dir 247.5, 2064 dir 90, 3 dir 22.5, 17 dir 0
        check("2.4.4 stock side 2065 dir 270: roads 0, 90, exit -90", CpTurnsFit.sideBits(new int[] { 0, 90, -90 }, CpTurnsFit.exitBit(-90)) == 2065);
        check("2.4.4 stock side 1025 dir 247.5: road 0, exit -113", CpTurnsFit.sideBits(new int[] { 0 }, CpTurnsFit.exitBit(-113)) == 1025
                && CpTurnsFit.exitSlot(-113) * 7.5f == 247.5f);
        check("2.4.4 stock side 2064 dir 90: roads 90, -90, exit 90", CpTurnsFit.sideBits(new int[] { 90, -90 }, CpTurnsFit.exitBit(90)) == 2064);
        check("2.4.4 stock side 3 dir 22.5: road 0, exit 22", CpTurnsFit.sideBits(new int[] { 0 }, CpTurnsFit.exitBit(22)) == 3
                && CpTurnsFit.exitSlot(22) * 7.5f == 22.5f);
        check("2.4.4 stock side 17 dir 0: roads 0, 90, exit 0", CpTurnsFit.sideBits(new int[] { 0, 90 }, CpTurnsFit.exitBit(0)) == 17);
        // 큰 그림 한 프레임: 좌회전(나가는 길 -90, 다른 길 0·90) = 순정 side 2065 dir 270
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 25000000L;
        t.maneuver(nearManeuver(0, 1, 0, 0, -90, new int[] { 0, 90, -90 }, -1, "Left Rd"));
        t.routeGuidance(rgAt(1, 200));
        check("2.4.4 stock: a left turn near frame is side 2065 dir 270.0 (was 4096+)", last("tbt ").startsWith("tbt 13 2065 270.0 "));
        // 각도 없이 종류만: 좌 36(270도) 비트 11, 급좌 30(225도) 비트 9, 약간 좌 42(315도) 비트 13, 우 12 비트 4
        check("2.4.4 stock: type-only directions: left bit 11, sharp left bit 9, slight left bit 13, right bit 4",
                java.util.Arrays.toString(CpTurnsFit.nearDisplay(1, 0, 0, 0, 70)).equals("[13, 11, 36]")
                        && java.util.Arrays.toString(CpTurnsFit.nearDisplay(47, 0, 0, 0, 72)).equals("[13, 9, 30]")
                        && java.util.Arrays.toString(CpTurnsFit.nearDisplay(49, 0, 0, 0, 71)).equals("[13, 13, 42]")
                        && java.util.Arrays.toString(CpTurnsFit.nearDisplay(2, 0, 0, 0, 67)).equals("[13, 4, 12]"));
        // 뒤쪽 출구는 순정 규칙 그대로(179도 이하 비트 7·칸 21, 그 밖 비트 8·칸 27), 왼쪽 경계 192도는 비트 8, 348도 직전은 비트 14
        check("2.4.4 stock: behind exits 7/21 and 8/27 kept; left edges 192 deg bit 8, 347 deg bit 14, no bit 15",
                CpTurnsFit.exitBit(175) == 7 && CpTurnsFit.exitSlot(175) == 21 && CpTurnsFit.exitBit(-175) == 8 && CpTurnsFit.exitSlot(-175) == 27
                        && CpTurnsFit.streetBit(-168) == 8 && CpTurnsFit.streetBit(-13) == 14 && CpTurnsFit.streetBit(-12) == 0);
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 2.4.4 후보 5차 리뷰(중간 1·낮음 7) 반영 시험 */
    static void review5() {
        Car.unit = Car.DISTANCE_KM;
        // 중간 1: 진짜로 가까운 연속 회전(새 회전 첫 값 < 앞 회전 마지막 값)은 미루지 않는다(HEAD·베타 2와 같은 출력: 바로 큰 그림)
        int[][] seq = { { 40, 35, 90 }, { 12, 10, 30 }, { 120, 100, 90 }, { 25, 20, 60 } };
        for (int[] x : seq) {
            CpTurnsFit t = turns1821(26000000L + x[0] * 1000L, 1);
            t.routeGuidance(rgAtIdx(1, 300, 18));
            t.clock += 1000L;
            t.routeGuidance(rgAtIdx(1, x[0], 18));
            c.calls.clear();
            t.clock += 40L;
            t.routeGuidance(rgAtIdx(1, x[1], 21));
            check("2.4.4 5th review M1: 18@" + x[0] + " -> 21@" + x[1] + ": near at once (bar " + x[2] + "), not put off", db(13, x[2]) && !farAny());
        }
        // 네이버 묵은 값(앞 값과 같음)은 그대로 미룬다
        CpTurnsFit t = turns1821(26200000L, 13);
        t.routeGuidance(rgAtIdx(1, 300, 18));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 18));
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 15, 21));
        check("2.4.4 5th review M1: Naver 18@15 -> 21@15 (equal): still put off", farNow() && "put-off".equals(t.nearWhy));
        // 낮음 3: 다른 회전을 받아들이면 nearPutOff를 비운다: 18@15 → 21@15(미룸) → 18@14 → 3.5초 뒤 item 10 없는 21은 묵은 14m를 받지 않음
        t.clock += 300L;
        t.routeGuidance(rgAtIdx(1, 14, 18));
        check("2.4.4 5th review low 3: back to 18@14: accepted, nearPutOff cleared", t.updates.get(1).nearPutOff == null);
        c.calls.clear();
        t.clock += 3500L;
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("14"), 12, ENUM, i(2), 13, U16, i(21)));
        check("2.4.4 5th review low 3: 3.5 s later turn 21 without item 10: stale-dist (the old 14 m not taken)",
                !anyNear() && "stale-dist".equals(t.nearWhy));
        // 낮음 2: 작은 오름 뒤 같은 값의 중복 갱신은 nearLastJump를 끄지 않는다(3@10 → 3@28 → 40ms 3@28 → 6@28)
        t = turn3(26300000L, 36, 10);
        t.routeGuidance(rgAtIdx(1, 28, 3));
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 28, 3));
        c.calls.clear();
        t.clock += 60L;
        t.routeGuidance(rgAtIdx(1, 28, 6));
        check("2.4.4 5th review low 2: 3@10 -> 3@28 -> 3@28 (dup) -> 6@28: near at once, not put off", db(13, 90) && !farAny());
        // 중복이어도 500ms 한도는 그대로: 오른 뒤 600ms에 새 번호(같은 값)는 미룬다
        t = turn3(26350000L, 36, 10);
        t.routeGuidance(rgAtIdx(1, 28, 3));
        t.clock += 300L;
        t.routeGuidance(rgAtIdx(1, 28, 3));
        t.clock += 300L;
        t.routeGuidance(rgAtIdx(1, 28, 6));
        check("2.4.4 5th review low 2: the dup keeps the rise time: 600 ms after the rise the new turn is put off", farNow()
                && "put-off".equals(t.nearWhy));
        // 낮음 7: nearLastJump의 !newKey(100ms 순서): 붙잡은 값을 6의 첫 값으로 받은 100ms 뒤 9@274(같은 값)는 미룬다
        t = kakaoAt10(26400000L);
        t.clock += 40L;
        t.routeGuidance(rgAtIdx(1, 274, 3));
        t.clock += 100L;
        t.routeGuidance(rgAtIdx(1, 274, 6));
        t.clock += 100L;
        t.routeGuidance(rgAtIdx(1, 274, 9));
        check("2.4.4 5th review low 7: held value taken as turn 6's first, 100 ms later 9@274: put off", farNow() && "put-off".equals(t.nearWhy));
        // 낮음 7: nearForget·reset()·Update.clear()가 미루기·오름 상태를 비운다(상태를 직접 본다)
        t = turn3(26500000L, 36, 10);
        t.routeGuidance(rgAtIdx(1, 28, 3));
        CpTurnsFit.Update u = t.updates.get(1);
        u.nearPutOff = "x";
        boolean before = u.nearLastJump && u.nearPrevLast >= 0;
        t.nearForget();
        check("2.4.4 5th review low 7: nearForget clears nearPutOff, nearLastJump, nearPrevLast, nearJump, nearKey",
                before && u.nearPutOff == null && !u.nearLastJump && u.nearPrevLast == -1 && u.nearJump == -1 && u.nearKey == null);
        t = turn3(26550000L, 36, 10);
        t.routeGuidance(rgAtIdx(1, 274, 3));
        u = t.updates.get(1);
        u.nearPutOff = "x";
        u.nearLastJump = true;
        boolean held = u.nearJump == 274;
        t.reset();
        check("2.4.4 5th review low 7: reset() (Update.clear()) clears the held value and the put-off/rise state",
                held && u.nearJump == -1 && u.nearPutOff == null && !u.nearLastJump && u.nearPrevLast == -1 && u.nearKey == null);
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 회전 하나를 구성요소 ids에 */
    static Iap2NotiMsg nearManeuverIds(int[] ids, int index, int type, int exit, String after) {
        return msg(0, U16, ids, 1, U16, i(index), 3, ENUM, i(type), 4, STR, s(after), 5, U32, i(500),
                8, ENUM, i(0), 9, ENUM, i(0), 11, U8, i(exit));
    }

    /** 구성요소 ids에 다음 회전까지 m(item 10)·표시 거리(item 11)·회전 목록(item 13) */
    static Iap2NotiMsg rgIds(int[] ids, int meters, int... list) {
        return msg(0, U16, ids, 1, ENUM, i(1), 10, U32, i(meters), 11, STR, s(String.valueOf(meters)), 12, ENUM, i(2), 13, U16, list);
    }

    /** 앞 회전 0(우회전) 300m → 15m를 보낸 관리자(회전 1은 종류 type) */
    static CpTurnsFit afterTurn0(long clock, int type, int last) {
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = clock;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.maneuver(nearManeuver(1, type, 0, 0, 0, null, -1, "Next Rd"));
        t.routeGuidance(rgAtIdx(1, 300, 0));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, last, 0));
        return t;
    }

    /** 2.4.4 후보 리뷰(높음 1·중간 4·낮음 4) 반영 시험: 시계를 올리고 refresh()를 부르는 순서 */
    static void staleNextReview() {
        Car.unit = Car.DISTANCE_KM;
        // H1: 미룬 뒤 1초 타이머 refresh()가 같은 Update로 다시 판단해도 앞 회전 값을 받아들이지 않는다
        CpTurnsFit t = afterTurn0(18000000L, 13, 15);
        c.calls.clear();
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 1));
        check("2.4.4 review H1: put off (far, reason put-off)", farNow() && !anyNear() && "put-off".equals(t.nearWhy));
        t.clock += 1000L;
        t.refresh();
        check("2.4.4 review H1: refresh 1 s later (no new update): still far, the old 15 m not taken", farNow() && !anyNear()
                && "stale-dist".equals(t.nearWhy));
        // 3차 리뷰 중간 2 뒤: 미룬 뒤 2초(PUT_OFF_MAX_MS) 전의 refresh는 풀지 않는다(실제 값은 약 1초 뒤에 옴)
        t.clock += 900L;
        t.refresh();
        check("2.4.4 review H1: another refresh at 1.9 s: still far", farNow() && !anyNear());
        t.clock += 50L;
        t.routeGuidance(rgAtIdx(1, 2000, 1));
        check("2.4.4 review H1: the real 2000 m: far, no big picture at any point", farNow() && !anyNear());
        // H1 유턴(17m → refresh → 380m): hold로 막대 90이 남지 않는다
        t = afterTurn0(18100000L, 4, 17);
        c.calls.clear();
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 17, 1));
        t.clock += 1000L;
        t.refresh();
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 380, 1));
        check("2.4.4 review H1: U-turn 17 m, refresh, 380 m: far all along", farNow() && !anyNear());
        // H1': 같은 값이 새 갱신으로 다시 오면(진짜로 바로 붙은 회전) refresh 뒤라도 받아들인다
        t = afterTurn0(18200000L, 1, 15);
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 1));
        t.clock += 1000L;
        t.refresh();
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 1));
        check("2.4.4 review H1': the same 15 m in a new update after a refresh: taken, near bar 50", db(13, 50));
        // M4: 미루는 갱신에서 HUD 둘째·셋째 지점(item 10 + 다음 구간)을 앞 회전 거리로 계산하지 않는다
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 18300000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.maneuver(nearManeuver(1, 13, 0, 0, 0, null, -1, "Fork Rd"));
        t.maneuver(nearManeuver(2, 1, 0, 0, -90, null, -1, "Left Rd"));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(300), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0, 1, 2)));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(15), 11, STR, s("15"), 12, ENUM, i(2), 13, U16, i(0, 1, 2)));
        check("2.4.4 review M4: before: the HUD 2nd point is 15 m + 500 m", last("2c ").startsWith("2c 0 0 15 0 0 515 "));
        c.calls.clear();
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(15), 11, STR, s("15"), 12, ENUM, i(2), 13, U16, i(1, 2)));
        check("2.4.4 review M4: put off: the HUD 2nd and 3rd points empty (not the old 15 m + 500 m)",
                farNow() && last("2c ").endsWith(" 0 0 65535 0 0 65535"));
        check("2.4.4 review M4: put off: the cluster's distance number is item 11 as sent (15, a known limit, owner to decide)",
                last("tbt ").split(" ")[4].equals("15.0") && last("tbt ").split(" ")[5].equals("0"));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(2000), 11, STR, s("2000"), 12, ENUM, i(2), 13, U16, i(1, 2)));
        check("2.4.4 review M4: the real 2000 m: HUD 2nd point 2000 m + 500 m (2.5 km)", last("2c ").split(" ")[5].equals("5") && last("2c ").split(" ")[6].equals("2"));
        // M1: 재탐색·안내 끝 뒤 새 경로에서 같은 번호·종류의 회전도 다시 미룬다
        t = afterTurn0(18400000L, 13, 15);
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 1));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 2000, 1));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(5, 2000, 1));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 300, 0));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 0));
        c.calls.clear();
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 1));
        check("2.4.4 review M1: after rerouting, the same index and type again with the old 15 m: put off again", farNow() && !anyNear());
        t = afterTurn0(18500000L, 13, 15);
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 1));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 2000, 1));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 300, 0));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 0));
        c.calls.clear();
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 1));
        check("2.4.4 review M1: after the guidance ended (state 0), the same turn again: put off again", farNow() && !anyNear());
        // 2차 리뷰: 이름 돌아옴(방금 본 앱 이름으로 돌아와 회전을 다시 받음)의 nearForget(): 곧 회전 상태(열쇠·처음 거리·앞 회전 거리)를 잊는다
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 18550000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(300), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0), 19, STR, s("App A")));
        t.clock += 1000L;
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("App B")));
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.routeGuidance(rgAt(1, 300));
        t.clock += 1000L;
        t.routeGuidance(rgAt(1, 150));
        check("2nd review: before the name comes back: near, bar 50 (D 300 m)", db(13, 50));
        t.clock += 1000L;
        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("App A")));
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.clock += 1000L;
        t.routeGuidance(rgAt(1, 140));
        check("2nd review: name back, the same turn resent at 140 m: D caught again (140 m, bar 90), not put off against the old 150 m",
                db(13, 90) && "in-d".equals(t.nearWhy));
        // L3: 한 갱신에 구성요소가 둘(1, 2)이면 near 상태는 구성요소마다 따로: 곧 회전 중에 멀리 있음이 섞이지 않고 막대가 줄어든다
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 18600000L;
        int[] two = i(1, 2);
        t.maneuver(nearManeuverIds(two, 0, 2, 90, "Right Rd"));
        t.routeGuidance(rgIds(two, 500, 0));
        check("2.4.4 review L3: two components at 500 m: far", farNow());
        c.calls.clear();
        for (int m : new int[] { 340, 300, 250, 200 }) {
            t.clock += 1000L;
            t.routeGuidance(rgIds(two, m, 0));
        }
        check("2.4.4 review L3: 340-200 m on both components: no far frame mixed in", !farAny());
        check("2.4.4 review L3: 200 m: bar 50 (D 350 m kept, not caught again per component)", db(13, 50));
        // M2 (a) 알려진 한계: 새 회전의 첫 값이 앞 회전 마지막보다 1~2m 크면 앞 값으로 보지 않는다(여유 없음, mod.md 근거)
        t = afterTurn0(18700000L, 13, 15);
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 17, 1));
        check("2.4.4 review M2 (a) known limit: old last 15 m, new first 17 m: taken (near, D 30 m)", db(13, 50));
        // M2 (b) 알려진 한계: 앞 값이 두 번 이어 오면(15m 미룸 → 10m) 두 번째는 받아들인다(미루기는 회전마다 한 번)
        t = afterTurn0(18800000L, 13, 15);
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 15, 1));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 10, 1));
        check("2.4.4 review M2 (b) known limit: 15 m put off, then 10 m: taken (near, D 30 m, bar 30)", db(13, 30));
        // M3 알려진 한계: 진짜로 붙은 회전(25m → 20m)은 멀리 있음 한 프레임, 그 뒤 item 10 없는 갱신만 오면 다음 item 10까지 멀리 있음
        t = afterTurn0(18900000L, 1, 25);
        c.calls.clear();
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 20, 1));
        // 5차 리뷰 중간 1: 미루기는 앞 값과 같을 때만이라, 앞 25m보다 짧은 진짜 20m는 바로 큰 그림(전의 "한 프레임 멀리 있음" 한계가 없어짐)
        check("2.4.4 review M3 (5th review M1): a real close turn at 20 m (< old 25 m): near at once (D 30 m, bar 60)", db(13, 60) && !farAny());
        // L2 알려진 한계: 재탐색 상태 없이 경로가 바뀌어 새 회전이 앞 회전 거리보다 가까우면(800m → 300m) 한 프레임 늦다
        c = new Cluster();
        t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 19000000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.maneuver(nearManeuver(7, 1, 0, 0, -90, null, -1, "Left Rd"));
        t.routeGuidance(rgAtIdx(1, 800, 0));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 300, 7));
        // 5차 리뷰 중간 1: 같은 값이 아니라 미루지 않는다(전의 한 프레임 늦음 한계가 없어짐)
        check("2.4.4 review L2 (5th review M1): a new route without rerouting, its turn at 300 m (< old 800 m): near at once", db(13, 90));
        t.clock += 1000L;
        t.routeGuidance(rgAtIdx(1, 290, 7));
        check("2.4.4 review L2: the next update 290 m: near (D 290 m, bar 90)", db(13, 90));
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 2.4.3 리뷰 낮음 5·8: tbt_fit에 따라 Dipo가 고르는 관리자(CpTurnsFit.make), 경로 탐색 줄 한도 */
    static void layerChoice() {
        Runnable r = new Runnable() {
            @Override
            public void run() {}
        };
        CpTbt on = CpTurnsFit.make(true, new android.content.Context(), new Cluster(), null, r, true);
        CpTbt off = CpTurnsFit.make(false, new android.content.Context(), new Cluster(), null, r, false);
        check("2.4.3 layer: tbt_fit on -> CpTurnsFit (debug and onActive set)", on instanceof CpTurnsFit && ((CpTurnsFit) on).debug
                && ((CpTurnsFit) on).onActive == r);
        check("2.4.3 layer: tbt_fit off -> CpTurns (stock DiPO, as 2.4.2)", off instanceof CpTurns && !((CpTurns) off).debug && ((CpTurns) off).onActive == r);
        c = new Cluster();
        CpTbt s = CpTurnsFit.make(false, new android.content.Context(), c, null, r, false);
        s.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road"));
        s.routeGuidance(rgAt(1, 200));
        check("2.4.3 layer: tbt_fit off at 200 m: the stock frame (67, bar 0, barErr 0), no near layout", "tbt 67 0 0.0 200.0 0 0 0 0 0".equals(last("tbt ")));
        c = new Cluster();
        CpTurnsFit q = new CpTurnsFit(new android.content.Context(), c, null);
        q.clock = 18000000L;
        q.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road"));
        Dipo.logs.clear();
        for (int k = 0; k < 300; k++) q.routeGuidance(rgAt(k % 2 == 0 ? 3 : 1, 200));
        int n = 0;
        for (String x : Dipo.logs) if (x.contains("route search")) n++;
        check("2.4.3 review L8: route search start/over lines capped at " + CpTurnsFit.SEARCH_LOGS_MAX + " per process (got " + n + ")", n == CpTurnsFit.SEARCH_LOGS_MAX);
    }

    static String last(String prefix) {
        String r = null;
        for (String x : c.calls) if (x.startsWith(prefix)) r = x;
        return r;
    }

    /** 곧 회전 하나(안내 문구 item 2를 더함): nearManeuver + description */
    static Iap2NotiMsg nearManeuverDesc(int type, int exit, String desc, String after) {
        Iap2NotiMsg m = nearManeuver(0, type, 0, 0, exit, null, -1, after);
        if (desc != null) m.getItemGroup().add(2, STR, s(desc));
        return m;
    }

    /** 새 길안내 하나를 meters에서 받고 마지막 tbt 줄 */
    static String nearTbt(Iap2NotiMsg man, int meters, long clock) {
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = clock;
        t.maneuver(man);
        t.routeGuidance(rgAt(1, meters));
        return last("tbt ");
    }

    /**
     * 2.4.10(1.8.1): T1-1 고가·지하차도, L-1 회전교차로 32, L-2 각도 없는 진입 램프, L-3 목적지 접근, T1-4 거리 문자열 로그, I-6 진단 줄 한도.
     * 순정 값: 1.8.1-work/tbtlog-1125.md 2장. 폰 문구는 실제 표본이 없어 만든 것이다(도로 이름 꼴 "반포지하차도"·"OO지하차도 방면"은
     * 11-20·11-05 기록의 교차로 이름에서 봄)
     */
    static void mode181() {
        Car.unit = Car.DISTANCE_KM;
        long ck = 41000000L;
        // T1-1 지하차도 46: 오른쪽 90도, 왼쪽 270도, 옆길 0
        String[][] tc = {
            // 종류, 각도, 안내 문구, 도로 이름, 바라는 tbt 앞부분, 까닭
            { "2", "90", null, "반포지하차도", "tbt 46 0 90.0 300.0 ", "underpass in the road name, right turn: 46, side 0, 90" },
            { "1", "-90", null, "반포지하차도", "tbt 46 0 270.0 300.0 ", "underpass, left turn: 46, side 0, 270" },
            { "14", "0", "지하차도 진입", "Some Rd", "tbt 46 0 90.0 300.0 ", "underpass in the instruction, keep right (no angle): 46 at 90, no fork bit" },
            { "13", "0", null, "OO지하도로", "tbt 46 0 270.0 300.0 ", "지하도로, keep left: 46 at 270, no fork bits" },
            { "50", "0", null, "OO지하차도", "tbt 46 0 90.0 300.0 ", "slight right: 46 at 90" },
            { "1", "-90", null, "OO고가차도", "tbt 44 0 270.0 300.0 ", "overpass, left: 44, side 0, 270" },
            { "13", "0", "고가도로 진입", "Some Rd", "tbt 44 0 270.0 300.0 ", "고가도로 in the instruction, keep left: 44" },
            { "2", "90", null, "OO고가차도", "tbt 13 ", "overpass on the right: unchanged (owner Y-8, right overpass never seen)" },
            { "2", "90", null, "OO지하차도 방면", "tbt 13 ", "방면 (toward): unchanged" },
            { "2", "90", "지하차도 옆길로", "Some Rd", "tbt 13 ", "옆길: unchanged" },
            { "1", "-90", "고가차도 아래로", "Some Rd", "tbt 13 ", "아래: unchanged" },
            { "2", "90", "지하차도 진입하지 마세요", "Some Rd", "tbt 13 ", "진입하지 마세요: unchanged" },
            { "2", "90", "측도 이용", "OO지하차도", "tbt 13 ", "a not-word in the instruction, the facility in the road name: unchanged (both texts count)" },
            { "2", "90", null, "OO고가차도사거리", "tbt 13 ", "an intersection named after an overpass (사거리): unchanged" },
            { "1", "-90", null, "OO지하차도 앞", "tbt 13 ", "앞 (in front of): unchanged" },
            { "2", "90", null, "해찬나래지하차도 방면", "tbt 13 ", "the one real phone sample (11-05 CarPlay, 방면): unchanged until the owner decides" },
            { "2", "90", "지하차도 옆길", "OO지하차도", "tbt 13 ", "one text with a not-word: unchanged even if the other text is plain" },
            { "2", "90", null, "OO지하차도 고가차도", "tbt 13 ", "both facilities: unchanged" },
            { "3", "0", null, "OO지하차도", "tbt 13 1 0.0 300.0 ", "straight (no left/right): unchanged (13, straight bit)" },
            { "48", "0", null, "OO지하차도", "tbt 13 ", "sharp right: unchanged (not in the list)" },
            { "4", "180", null, "OO지하차도", "tbt 31 ", "U-turn: unchanged" },
            { "23", "0", null, "OO지하차도", "tbt 17 ", "highway exit: unchanged" },
            { "2", "90", null, "Some Rd", "tbt 13 ", "no facility word: unchanged" },
        };
        for (String[] k : tc) {
            String l = nearTbt(nearManeuverDesc(Integer.parseInt(k[0]), Integer.parseInt(k[1]), k[2], k[3]), 300, ck += 1000000L);
            check("2.4.10 T1-1: " + k[5] + " (got " + l + ")", l != null && l.startsWith(k[4]));
        }
        // 멀리(D 밖)는 그대로: 폰 아이콘 67, 막대 무효
        String far = nearTbt(nearManeuverDesc(2, 90, null, "반포지하차도"), 900, ck += 1000000L);
        check("2.4.10 T1-1: far (900 m) is unchanged: 67, bar invalid (got " + far + ")", far != null && far.equals("tbt 67 0 0.0 900.0 0 0 0 0 -1"));
        // 막대 시작은 지금 규칙(D = 350m): 340m에서 막대 90
        String bar = nearTbt(nearManeuverDesc(2, 90, null, "반포지하차도"), 340, ck += 1000000L);
        check("2.4.10 T1-1: the bar starts as before (D 350 m: 340 m -> 90) (got " + bar + ")", bar != null && bar.startsWith("tbt 46 0 90.0 340.0 ") && bar.endsWith(" 90 0"));
        check("2.4.10 T1-1: facilityWord: 1 hit, 2 hit with a not-word, 0 none or null",
                CpTurnsFit.facilityWord("반포지하차도", CpTurnsFit.UNDERPASS_WORDS) == 1
                        && CpTurnsFit.facilityWord("지하차도 지나서", CpTurnsFit.UNDERPASS_WORDS) == 2
                        && CpTurnsFit.facilityWord("Some Rd", CpTurnsFit.UNDERPASS_WORDS) == 0 && CpTurnsFit.facilityWord(null, CpTurnsFit.OVERPASS_WORDS) == 0);

        // L-1 회전교차로: 순정 니로 PHEV(32, 315도, 옆길 8208 = 비트 4·13). 폰이 출구 -45도와 다른 길 90도를 주면 같은 값
        c = new Cluster();
        CpTurnsFit rt = new CpTurnsFit(new android.content.Context(), c, null);
        rt.clock = ck += 1000000L;
        rt.maneuver(nearManeuver(0, 32, 0, 1, -45, new int[] { 90 }, -1, "Round Rd"));
        rt.routeGuidance(rgAt(1, 400));
        rt.routeGuidance(rgAt(1, 90));
        String l1 = last("tbt ");
        check("2.4.10 L-1: roundabout exit -45 with another road at 90, 90 m left of D 350: 32, side 8208, 315.0, bar 20 (stock 07:40:21) (got " + l1 + ")",
                l1 != null && l1.startsWith("tbt 32 8208 315.0 90.0 ") && l1.endsWith(" 20 0"));
        l1 = nearTbt(nearManeuver(0, 32, 0, 1, -45, null, -1, "Round Rd"), 300, ck += 1000000L);
        check("2.4.10 L-1: no other-road angles: only the exit bit 13 (8192) (got " + l1 + ")", l1 != null && l1.startsWith("tbt 32 8192 315.0 300.0 "));
        l1 = nearTbt(nearManeuver(0, 2, 0, 1, 90, null, -1, "Round Rd"), 300, ck += 1000000L);
        check("2.4.10 L-1: roundabout right exit 90: 32 at 90.0, exit bit 4 (got " + l1 + ")", l1 != null && l1.startsWith("tbt 32 16 90.0 300.0 "));
        String far1 = nearTbt(nearManeuver(0, 32, 0, 1, -45, null, -1, "Round Rd"), 900, ck += 1000000L);
        check("2.4.10 L-1: far is unchanged: the exit icon 104 (got " + far1 + ")", far1 != null && far1.startsWith("tbt 104 0 0.0 900.0 "));
        check("2.4.10 L-1: left-hand traffic (side 1) and an exit without an angle keep the exit icon; entering (6) keeps its icon",
                arr(CpTurnsFit.nearDisplay(31, 1, 1, 0, 192)).equals("[192, 0, 0]")
                        && arr(CpTurnsFit.nearDisplay(2, 0, 1, 0, 96)).equals("[96, 0, 0]")
                        && arr(CpTurnsFit.nearDisplay(6, 0, 1, 90, 97)).equals("[97, 4, 12]")
                        && arr(CpTurnsFit.nearDisplay(2, 1, 1, 90, 195)).equals("[195, 4, 12]"));

        // L-2 진입 램프(9): 각도 없음 → 13, 22.5도, 옆길 3(순정 K7 16:29:00)
        String l2 = nearTbt(nearManeuver(0, 9, 0, 0, 0, null, -1, "Ramp"), 120, ck += 1000000L);
        check("2.4.10 L-2: on-ramp without an angle: 13, side 3, 22.5 (stock K7) (got " + l2 + ")", l2 != null && l2.startsWith("tbt 13 3 22.5 120.0 "));
        l2 = nearTbt(nearManeuver(0, 9, 1, 0, 0, null, -1, "Ramp"), 120, ck += 1000000L);
        check("2.4.10 L-2: left-hand traffic: the mirror 13, side 16385 (bits 0, 14), 337.5 (not seen on a stock car) (got " + l2 + ")",
                l2 != null && l2.startsWith("tbt 13 16385 337.5 120.0 "));
        l2 = nearTbt(nearManeuver(0, 9, 0, 0, 45, null, -1, "Ramp"), 120, ck += 1000000L);
        check("2.4.10 L-2: on-ramp with the phone's angle 45: that angle, no extra straight bit (got " + l2 + ")", l2 != null && l2.startsWith("tbt 13 4 45.0 120.0 "));
        String far2 = nearTbt(nearManeuver(0, 9, 0, 0, 0, null, -1, "Ramp"), 900, ck += 1000000L);
        check("2.4.10 L-2: far is unchanged: 160 (got " + far2 + ")", far2 != null && far2.startsWith("tbt 160 0 0.0 900.0 "));

        // L-3 목적지 접근: 순정 B·C·D "tbt icon 113 side 0 dir 0.0 … bar 0 barErr -1"(가까워도 곧 회전 아님)
        String l3 = nearTbt(nearManeuver(0, 25, 0, 0, 0, null, -1, "Dest"), 30, ck += 1000000L);
        check("2.4.10 L-3: destination on the right (25) at 30 m: 113, side 0, dir 0, bar 0, barErr -1 as stock (got " + l3 + ")",
                "tbt 113 0 0.0 30.0 0 0 0 0 -1".equals(l3));
        l3 = nearTbt(nearManeuver(0, 24, 0, 0, 0, null, -1, "Dest"), 190, ck += 1000000L);
        check("2.4.10 L-3: destination on the left (24) at 190 m: 114 the same way (got " + l3 + ")", "tbt 114 0 0.0 190.0 0 0 0 0 -1".equals(l3));

        // T1-4 거리 문자열 로그: 유무·단위만, 문자열은 남기지 않음
        Dipo.logs.clear();
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = ck += 1000000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Right Rd"));
        t.routeGuidance(rgAt(1, 900));
        t.routeGuidance(rgAt(1, 800));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(700), 11, STR, s("abc"), 12, ENUM, i(2), 13, U16, i(0)));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(650), 11, STR, s("abc"), 12, ENUM, i(0), 13, U16, i(0)));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(600), 11, STR, s(""), 12, ENUM, i(0), 13, U16, i(0)));
        java.util.List<String> ml = new java.util.ArrayList<String>();
        for (String x : Dipo.logs) if (x.startsWith("CarPlay: maneuver ")) ml.add(x);
        check("2.4.10 T1-4: maneuver lines end with dist str 1/2/0 and the unit; the distance or the unit changing alone adds no line (got " + ml + ")",
                ml.size() == 3 && ml.get(0).endsWith("(exact) dist str 1 units 2") && ml.get(1).endsWith("(exact) dist str 2 units 2")
                        && ml.get(2).endsWith(" dist str 0 units 0") && !ml.toString().contains("abc"));
        // 수집 USB(public/usb-tbtlog/NEMESIS.sh 193행)는 머리말 "CarPlay: maneuver "만 보고, keep()이 허용 문자 밖(여기서는 ">")을 지운다
        check("2.4.10 T1-4: the tbtlog USB filter still takes the line (prefix) and keep() leaves the new suffix whole",
                ml.size() == 3 && ml.get(0).startsWith("CarPlay: maneuver ")
                        && ml.get(0).replaceAll("[^A-Za-z0-9 ._:=,()/+-]", "").endsWith("(exact) dist str 1 units 2"));
        check("2.4.10 T1-4: distStrState", CpTurns.distStrState(null) == 0 && CpTurns.distStrState("") == 0 && CpTurns.distStrState("1.5") == 1
                && CpTurns.distStrState("-1") == 2 && CpTurns.distStrState("x") == 2);

        // I-6 진단 줄 한도 1200
        Dipo.logs.clear();
        t = new CpTurnsFit(new android.content.Context(), new Cluster(), null);
        t.debug = true;
        for (int k = 0; k < 1300; k++) t.diag("CarPlay diag: lane guidance index " + k + " lanes 1");
        int n = 0;
        for (String x : Dipo.logs) if (x.startsWith("CarPlay diag:")) n++;
        check("2.4.10 I-6: diag lines capped at 1200 per process (got " + n + ")", n == 1200 && CpTurnsFit.DIAG_LOGS_MAX == 1200);
        Dipo.logs.clear();
    }

    /** 진단 줄("CarPlay diag:")만 꺼내고 모은 로그를 비운다 */
    static java.util.List<String> diagLines() {
        java.util.List<String> r = new java.util.ArrayList<String>();
        for (String x : Dipo.logs) if (x.startsWith("CarPlay diag:")) r.add(x);
        Dipo.logs.clear();
        return r;
    }

    /**
     * 1.7.2 차-5 뒤 진단 줄: 디버그일 때만, 숫자만, 판정·인자가 바뀔 때만. 카카오내비 세션(차로 안내 0x5204 없음,
     * item 10 있음)처럼 회전이 D 안으로 들어와도 차로가 없으면 "far (no-link)"로 남고, 차로가 오면 그 수와 near가 남는다.
     */
    static void diag172() {
        Car.unit = Car.DISTANCE_KM;
        // 디버그가 꺼져 있으면 진단 줄이 없다(보내는 값은 같다)
        c = new Cluster();
        CpTurnsFit off = new CpTurnsFit(new android.content.Context(), c, null);
        off.clock = 9000000L;
        Dipo.logs.clear();
        off.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road A"));
        off.routeGuidance(rgAt(1, 200));
        check("1.7.2 diag: no diag lines with the debug log off (2.4.3: D = first 200 m, bar 90)", diagLines().isEmpty()
                && "tbt 13 16 90.0 200.0 0 0 0 90 0".equals(last("tbt ")));

        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.debug = true;
        t.clock = 9100000L;
        Dipo.logs.clear();
        // 카카오 세션처럼: 차로 안내 없음, 1.4 km에서 시작해 D 안으로
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road A"));
        t.routeGuidance(rgAt(1, 1400));
        java.util.List<String> d = diagLines();
        check("1.7.2 diag: far turn (beyond D) without a lane link: one line with lanes, remain, D, reason and the tbt arguments",
                d.size() == 1 && d.get(0).equals("CarPlay diag: maneuver 0 type 2 link - lanes - rg lanes 0/0/0 remain 1400m d 350 -> far (far); "
                        + "tbt display 67 side 0 dir 0.0 bar 0 barErr -1 unit 0 dist 1400.0"));
        // 거리만 바뀌고 같은 판정: 줄을 남기지 않는다(refresh의 다시 보내기도)
        t.routeGuidance(rgAt(1, 1000));
        t.clock += 1000L;
        t.refresh();
        check("1.7.2 diag: same reason (far), only the distance changes: no new line", diagLines().isEmpty());
        // 소유자 결정 11-09: 차로가 없어도 D 안이면 near (in-d). 300m -> 막대 10 x floor(3000/350) = 80, 나가는 길 비트만
        t.routeGuidance(rgAt(1, 300));
        d = diagLines();
        check("1.7.2 diag (owner 11-09): no lanes, inside D: near (in-d) with the near tbt arguments",
                d.size() == 1 && d.get(0).equals("CarPlay diag: maneuver 0 type 2 link - lanes - rg lanes 0/0/0 remain 300m d 350 -> near (in-d); "
                        + "tbt display 13 side 16 dir 90.0 bar 80 barErr 0 unit 0 dist 300.0")
                        && "tbt 13 16 90.0 300.0 0 0 0 80 0".equals(last("tbt ")));
        // 목적지는 D 안이어도 멀리 있음(type)
        CpTurnsFit g = new CpTurnsFit(new android.content.Context(), c, null);
        g.debug = true;
        g.clock = 9150000L;
        g.maneuver(nearManeuver(0, 12, 0, 0, 0, null, -1, "Home"));
        g.routeGuidance(rgAt(1, 100));
        d = diagLines();
        check("1.7.2 (owner 11-09): destination inside D without lanes: far, reason type",
                "tbt 112 0 0.0 100.0 0 0 0 0 -1".equals(last("tbt ")) && d.size() == 1 && d.get(0).contains(" -> far (type); "));
        t.routeGuidance(rgAt(1, 64));
        d = diagLines();
        check("1.7.2 diag: near, the bar steps down (80 -> 10): one line", d.size() == 1 && d.get(0).contains(" -> near (in-d); ") && d.get(0).contains(" bar 10 "));
        // item 10 없이 표시 문자열만 오는 앱: no-dist
        CpTurnsFit s = new CpTurnsFit(new android.content.Context(), c, null);
        s.debug = true;
        s.clock = 9200000L;
        s.maneuver(nearManeuver(0, 2, 0, 0, 90, null, 3, "Road A"));
        s.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("64"), 12, ENUM, i(2), 13, U16, i(0)));
        d = diagLines();
        check("1.7.2 diag: no item 10 (distance in metres): reason no-dist, remain -",
                d.size() == 1 && d.get(0).contains(" link 3 lanes - ") && d.get(0).contains(" remain - d 350 -> far (no-dist); "));
        // 차로 안내가 오면 한 줄(번호·차로 수), 링크한 회전이 D 안이면 near와 보낸 근거리 인자
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, new int[] { 0, 90, -90, 180 }, 3, "Road A"));
        t.lanes(laneMsg(3, 2));
        t.routeGuidance(rgAt(1, 60));
        d = diagLines();
        check("1.7.2 diag: lane guidance received: index and lane count",
                d.size() == 2 && d.get(0).equals("CarPlay diag: lane guidance index 3 lanes 2"));
        // 처음 거리 1400m > D, 60m -> 막대 10 x max(1, floor(600/350)) = 10
        check("1.7.2 diag: near turn: link, lanes, remain, D, reason and the near tbt arguments",
                d.size() == 2 && d.get(1).equals("CarPlay diag: maneuver 0 type 2 link 3 lanes 2 rg lanes 0/0/0 remain 60m d 350 -> near (in-d); "
                        + "tbt display 13 side 2065 dir 90.0 bar 10 barErr 0 unit 0 dist 60.0")
                        && "tbt 13 2065 90.0 60.0 0 0 0 10 0".equals(last("tbt ")));
        // 곧 회전에서 막대가 같으면(55m, 막대 10) 줄이 없고, 막대가 바뀌면(200m, 막대 50) 한 줄
        t.routeGuidance(rgAt(1, 55));
        check("1.7.2 diag: near, same bar: no new line", diagLines().isEmpty());
        t.routeGuidance(rgAt(1, 200));
        d = diagLines();
        // 2.4.4 후보: 55m → 200m(회전을 지난 꼴)는 한 번 붙잡고 진단 줄 하나(held)만 남는다
        check("2.4.4 diag: 55 -> 200 m held once: one held line", d.size() == 1 && d.get(0).endsWith("remain 200m -> held (jump-hold)"));
        t.routeGuidance(rgAt(1, 200));
        d = diagLines();
        check("1.7.2 diag: near, the bar changes: one line with the new bar",
                d.size() == 1 && d.get(0).endsWith("tbt display 13 side 2065 dir 90.0 bar 50 barErr 0 unit 0 dist 200.0"));
        // 같은 차로 안내를 다시 받으면 줄을 남기지 않는다
        t.lanes(laneMsg(3, 2));
        check("1.7.2 diag: the same lane guidance again: no new line", diagLines().isEmpty());
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 1.7.2(M-4·M-5·M-2, 소유자 결정 2026-11-09): 곧 회전 화면, 탐색 중 9·10, HUD 3칸 */
    static void near172() {
        // 옆길 비트와 방향 칸(순정 SetSideStreetBit)
        // 2.4.4 후보(나): 왼쪽 절반은 순정 차 로그대로 한 칸 당김(270도 = 비트 11, 왼쪽 끝 347도 = 비트 14, 비트 15 없음)
        check("1.7.2 M-4 side bits: straight 0, right 90 bit 4, left 270 bit 11 (2.4.4, was 12), behind (180 +-12) none",
                CpTurnsFit.streetBit(0) == 0 && CpTurnsFit.streetBit(90) == 4 && CpTurnsFit.streetBit(-90) == 11
                        && CpTurnsFit.streetBit(180) == -1 && CpTurnsFit.streetBit(170) == -1 && CpTurnsFit.streetBit(-170) == -1
                        && CpTurnsFit.streetBit(12) == 0 && CpTurnsFit.streetBit(13) == 1 && CpTurnsFit.streetBit(45) == 2
                        && CpTurnsFit.streetBit(347) == 14 && CpTurnsFit.streetBit(-13) == 14);
        check("1.7.2 M-4 side bits: T junction (straight, right, left; incoming road 180 left out) with the right exit = 0x0811 (2.4.4: stock side 2065; car-3 picture showed our old 0x1011)",
                CpTurnsFit.sideBits(new int[] { 0, 90, -90, 180 }, CpTurnsFit.exitBit(90)) == 0x0811);
        check("1.7.2 M-4 side bits: no other angles: the exit's bit only (V-9); behind exits bit 7 (<=179) / 8",
                CpTurnsFit.sideBits(null, CpTurnsFit.exitBit(-45)) == 1 << 13 && CpTurnsFit.exitSlot(-45) == 42
                        && CpTurnsFit.exitSlot(170) == 21 && CpTurnsFit.sideBits(null, CpTurnsFit.exitBit(170)) == 1 << 7
                        && CpTurnsFit.exitSlot(-170) == 27 && CpTurnsFit.sideBits(null, CpTurnsFit.exitBit(-170)) == 1 << 8);
        // 2.4.4 후보(나): 200도는 뒤쪽(192도 미만)이 아닌 왼쪽 첫 칸 = 비트 8(칸 27). 전에는 비트 9로 셌다
        check("review 4: an exit at -160 degrees (200 clockwise) is a street bit (2.4.4: bit 8, slot 27), not the behind rule",
                CpTurnsFit.streetBit(-160) == 8 && CpTurnsFit.exitBit(-160) == 8 && CpTurnsFit.exitSlot(-160) == 27
                        && CpTurnsFit.sideBits(null, CpTurnsFit.exitBit(-160)) == 1 << 8);
        // 표시 번호(순정 SetCase*)
        int[] r = CpTurnsFit.nearDisplay(2, 0, 0, 0, 67), l = CpTurnsFit.nearDisplay(1, 0, 0, -90, 70), u = CpTurnsFit.nearDisplay(4, 0, 0, 0, 72),
                ul = CpTurnsFit.nearDisplay(4, 1, 0, 0, 73), xr = CpTurnsFit.nearDisplay(23, 0, 0, 0, 164), xl = CpTurnsFit.nearDisplay(22, 0, 0, 0, 165),
                fk = CpTurnsFit.nearDisplay(13, 0, 0, 0, 71), rb = CpTurnsFit.nearDisplay(29, 0, 1, 90, 99), sr = CpTurnsFit.nearDisplay(48, 0, 0, 0, 68);
        check("1.7.2 M-4 display: turn 13 (right 12, left 36), U-turn 31 (36; left-hand traffic 12), exit right 17 / left 18, keep left 13 at 337.5 (1.8.0), roundabout 32 at its exit slot (2.4.10 L-1, was its exit icon 99)",
                arr(r).equals("[13, 4, 12]") && arr(l).equals("[13, 11, 36]") && arr(u).equals("[31, -1, 36]") && arr(ul).equals("[31, -1, 12]")
                        && arr(xr).equals("[17, -1, 12]") && arr(xl).equals("[18, -1, 36]") && arr(fk).equals("[13, 14, 45]")
                        && arr(rb).equals("[32, 4, 12]") && arr(sr).equals("[13, 6, 18]"));
        check("1.7.2 M-4 display: destination, waypoint, departure, ferry, no turn: no near layout (far, bar invalid)",
                CpTurnsFit.nearDisplay(12, 0, 0, 0, 112) == null && CpTurnsFit.nearDisplay(10, 0, 0, 0, 112) == null
                        && CpTurnsFit.nearDisplay(24, 0, 0, 0, 114) == null && CpTurnsFit.nearDisplay(11, 0, 0, 0, 1) == null
                        && CpTurnsFit.nearDisplay(15, 0, 0, 0, 176) == null && CpTurnsFit.nearDisplay(0, 0, 0, 0, 8) == null);
        // 막대와 D
        check("1.7.2 M-4 bar: 10 x max(1, floor(remain x 10 / D)), up to 90 (trial 3: the stock maximum)",
                CpTurnsFit.bar(300, 350) == 80 && CpTurnsFit.bar(10, 350) == 10 && CpTurnsFit.bar(0, 350) == 10 && CpTurnsFit.bar(350, 350) == 90 && CpTurnsFit.bar(349, 350) == 90 && CpTurnsFit.bar(315, 350) == 90
                        && CpTurnsFit.bar(34, 350) == 10 && CpTurnsFit.bar(35, 350) == 10 && CpTurnsFit.bar(70, 350) == 20);
        check("1.7.2 M-5 D: 350 m, 1050 m for highway exits", CpTurnsFit.D_GENERAL == 350 && CpTurnsFit.D_EXPRESSWAY == 1050
                && CpTurnsFit.expressway(23) && CpTurnsFit.expressway(22) && CpTurnsFit.expressway(8) && !CpTurnsFit.expressway(2) && !CpTurnsFit.expressway(13));

        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 8000000L;
        // 소유자 결정 2026-11-09 "D 안의 모든 회전": 차로 안내가 없어도 D 안이면 곧 회전(카카오내비는 차로 안내를 보내지 않음)
        // 2.4.3: D는 이 회전의 처음 거리(200m, 소유자 결정 2026-11-15) -> 막대 가득(90), 옆길 0x0811(직진·오른쪽·왼쪽, 2.4.4 순정 비트), 90도, 도로 이름
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, new int[] { 0, 90, -90, 180 }, -1, "Road A"));
        t.routeGuidance(rgAt(1, 200));
        expect("1.7.2 M-4 (owner 11-09): a turn without lane guidance inside D: near, turn 13, side 0x0811, 90 degrees, the road name (2.4.3: D = first 200 m, bar 90)",
                "road [Road A]", "tbt 13 2065 90.0 200.0 0 0 0 90 0", "hud f201430000" + utf16("Road A"), "2c 0 0 200 0 0 65535 0 0 65535");
        // 링크한 차로 안내에 차로가 없어도 곧 회전, 190m -> 막대 50
        t.lanes(laneMsg(3, 0));
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, new int[] { 0, 90, -90, 180 }, 3, "Road A"));
        t.routeGuidance(rgAt(1, 190));
        check("1.7.2 M-4 (owner 11-09): lane guidance with no lanes: near all the same (D 200 m: bar 90)", "tbt 13 2065 90.0 190.0 0 0 0 90 0".equals(last("tbt ")));
        c.calls.clear();
        // 차로 2개: 차로 프레임은 따로 나가고 곧 회전은 그대로. 180m -> 막대 10 x floor(1800/350) = 50
        t.lanes(laneMsg(3, 2));
        t.routeGuidance(rgAt(1, 180));
        expect("1.7.2 M-4: with lane guidance and inside D: the lane frame, then turn 13, side 0x0811, 90 degrees (D 200 m: 180 m -> bar 90)",
                "lanes f102000101" + "0000000000000000000000000000" + "01000001", "tbt 13 2065 90.0 180.0 0 0 0 90 0",
                "2c 0 0 180 0 0 65535 0 0 65535");
        t.routeGuidance(rgAt(1, 30));
        check("1.7.2 M-4: 30 m: the bar never below 10", "tbt 13 2065 90.0 30.0 0 0 0 10 0".equals(last("tbt ")));
        c.calls.clear();
        // 종류를 다시 말한 갱신에 각도·차로 링크가 없음: 곧 회전 그대로, 옆길은 나가는 길 비트만
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road A"));
        t.routeGuidance(rgAt(1, 25));
        check("1.7.2 M-4 (owner 11-09): the same maneuver resent without angles or lane link: still near, only the exit bit, the name kept",
                "tbt 13 16 90.0 25.0 0 0 0 10 0".equals(last("tbt ")) && last("road ") == null);
        c.calls.clear();

        // 고속도로 오른쪽 출구: 처음 거리 1200m(D 1050m보다 김), 900m에서 17, 막대 10 x floor(9000/1050) = 80, 옆길 없음
        CpTurnsFit h = new CpTurnsFit(new android.content.Context(), c, null);
        h.clock = 8100000L;
        h.lanes(laneMsg(0, 3));
        h.maneuver(nearManeuver(0, 23, 0, 0, 0, null, 0, "Exit Rd"));
        h.routeGuidance(rgAt(1, 1200));
        check("1.7.2 M-5: highway exit at 1200 m (beyond D 1050 m): far", "tbt 164 0 0.0 1200.0 0 0 0 0 -1".equals(last("tbt ")));
        h.routeGuidance(rgAt(1, 900));
        check("1.7.2 M-4/M-5: highway exit right at 900 m (D 1050 m): display 17, direction 90, no side bits, bar 80",
                "tbt 17 0 90.0 900.0 0 0 0 80 0".equals(last("tbt ")));
        // 1.7.3(N-3): 나올 때는 D + 50m를 넘어야 멀리 있음. 1100m(= D + 50)는 아직 곧 회전, 1101m부터 멀리 있음
        h.routeGuidance(rgAt(1, 1100));
        // 2.4.4 후보: 900m → 1100m(뜀)는 한 번 붙잡고 다음 갱신에서 판단한다
        check("2.4.4: 900 -> 1100 m held once (the 900 m frame stays)", "tbt 17 0 90.0 900.0 0 0 0 80 0".equals(last("tbt ")));
        h.routeGuidance(rgAt(1, 1100));
        check("1.7.3 N-3: 1100 m (D + 50 m) after near: still near (bar 90)", "tbt 17 0 90.0 1100.0 0 0 0 90 0".equals(last("tbt ")));
        h.routeGuidance(rgAt(1, 1101));
        check("1.7.2 M-5 (1.7.3 N-3): farther than D + 50 m: far again (icon 164, bar invalid)", "tbt 164 0 0.0 1101.0 0 0 0 0 -1".equals(last("tbt ")));
        c.calls.clear();
        // 일반 회전 400m(D 350m 밖): 멀리 있음. 340m: 곧 회전, 처음 거리 400m라 D 350m, 막대 90
        CpTurnsFit g = new CpTurnsFit(new android.content.Context(), c, null);
        g.clock = 8200000L;
        g.lanes(laneMsg(0, 2));
        g.maneuver(nearManeuver(0, 4, 0, 0, 0, null, 0, "U Rd"));
        g.routeGuidance(rgAt(1, 400));
        check("1.7.2 M-5: U-turn at 400 m (D 350 m): far", "tbt 72 0 0.0 400.0 0 0 0 0 -1".equals(last("tbt ")));
        g.routeGuidance(rgAt(1, 340));
        check("1.7.2 M-4: U-turn at 340 m: display 31, 270 degrees, no side bits, bar 90",
                "tbt 31 0 270.0 340.0 0 0 0 90 0".equals(last("tbt ")) && "road [U Rd]".equals(last("road ")));
        c.calls.clear();
        g.stop();
        check("1.7.2 M-4: guidance end clears the road name shown with the near turn",
                c.calls.contains(CLEAR_ROAD) && c.calls.contains(CLEAR_TBT));
        c.calls.clear();
        // 리뷰 1: 곧 회전을 지난 뒤 다음 회전(번호 1)으로 바뀐 갱신에 거리(item 10)가 없으면 앞 회전의 30m로 판단하지 않는다
        CpTurnsFit v = new CpTurnsFit(new android.content.Context(), c, null);
        v.clock = 8250000L;
        v.lanes(laneMsg(0, 2));
        v.maneuver(nearManeuver(0, 2, 0, 0, 90, null, 0, "Road V"));
        v.maneuver(nearManeuver(1, 1, 0, 0, -90, null, 0, "Road W"));
        v.routeGuidance(rgAt(1, 30));
        check("review 1: near at 30 m first (2.4.3: first distance 30 m = D_MIN: bar 90)", "tbt 13 16 90.0 30.0 0 0 0 90 0".equals(last("tbt ")));
        v.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("30"), 13, U16, i(1)));
        check("review 1: the list moves on to the next turn without its distance: far (no flash of a near turn with the old distance)",
                "tbt 70 0 0.0 30.0 0 0 0 0 -1".equals(last("tbt ")) && "road []".equals(last("road ")));
        v.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(600), 11, STR, s("600"), 13, U16, i(1)));
        v.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(300), 11, STR, s("300"), 13, U16, i(1)));
        check("review 1: then its own distances: near at 300 m (first 600 m, D 350 m, bar 80, left 270, 2.4.4 bit 11)",
                "tbt 13 2048 270.0 300.0 0 0 0 80 0".equals(last("tbt ")));
        c.calls.clear();
        // 리뷰 3: 안내가 끝나면 옛 경로의 차로 수를 버린다(진단 줄용). 소유자 결정 11-09부터 차로와 상관없이 D 안이면 곧 회전
        v.routeGuidance(rgAt(0, 0));
        v.maneuver(nearManeuver(0, 2, 0, 0, 90, null, 0, "Road N"));
        v.routeGuidance(rgAt(1, 200));
        check("review 3: a new route after the end: old lane counts forgotten, near inside D without lanes (owner 11-09)",
                "tbt 13 16 90.0 200.0 0 0 0 90 0".equals(last("tbt ")) && v.laneCounts.isEmpty());
        v.lanes(laneMsg(0, 2));
        v.routeGuidance(rgAt(1, 190));
        check("review 3: its lanes come: near (D 200 m: bar 90)", "tbt 13 16 90.0 190.0 0 0 0 90 0".equals(last("tbt ")));
        c.calls.clear();

        // 목적지: 차로가 있어도 막대 무효 그대로
        CpTurnsFit e = new CpTurnsFit(new android.content.Context(), c, null);
        e.clock = 8300000L;
        e.lanes(laneMsg(0, 2));
        e.maneuver(nearManeuver(0, 12, 0, 0, 0, null, 0, "Home"));
        e.routeGuidance(rgAt(1, 100));
        check("1.7.2 M-4: destination keeps the bar invalid (no near layout)", "tbt 112 0 0.0 100.0 0 0 0 0 -1".equals(last("tbt ")));
        c.calls.clear();

        // 탐색 중(상태 3) 9, 재탐색(상태 5) 10, 벗어나면 보통 회전
        CpTurnsFit q = new CpTurnsFit(new android.content.Context(), c, null);
        q.clock = 8400000L;
        q.maneuver(maneuver(0, 2, 0, 0, 90, "Road Q", 300));
        q.routeGuidance(rgAt(1, 300));
        c.calls.clear();
        c.hudFrames.clear();
        q.routeGuidance(rgAt(3, 300));
        expect("1.7.2 route search (state 3): display 9 with direction, side and distance invalid at once, then the name emptied and 2C and the HUD list cleared (stock)",
                "tbt 9 0 0.0 0.0 -3 0 15 0 0", "road []", CLEAR_2C, "hud f2");
        check("1.7.2 route search: the HUD's empty list is {F2,00}", c.hudFrames.size() == 1 && java.util.Arrays.equals(c.hudFrames.get(0), new byte[] { (byte) 0xF2, 0 }));
        q.routeGuidance(rgAt(3, 300));
        expect("1.7.2 route search: still searching: display 9 again, nothing cleared again", "tbt 9 0 0.0 0.0 -3 0 15 0 0");
        q.clock += 1000L;
        q.refresh();
        expect("1.7.2 route search: refresh resends display 9", "tbt 9 0 0.0 0.0 -3 0 15 0 0");
        q.routeGuidance(rgAt(5, 300));
        expect("1.7.2 rerouting (state 5): display 10, cleared once", "tbt 10 0 0.0 0.0 -3 0 15 0 0", "road []", CLEAR_2C, "hud f2");
        q.routeGuidance(rgAt(1, 280));
        // 280m는 D 안: 곧 회전. 2.4.3: 재탐색 뒤 처음 거리 280m로 D를 다시 잡아 막대 90
        expect("1.7.2 route found (state 1): back to the turn (near inside D), the HUD list sent again (2.4.3: D caught again, 280 m, bar 90)",
                "road [Road Q]", "tbt 13 16 90.0 280.0 0 0 0 90 0", "hud f201430000" + utf16("Road Q"), "2c 0 0 280 0 0 65535 0 0 65535");
        q.routeGuidance(rgAt(4, 280));
        check("1.7.2: state 4 (locating) is not a route search: the turn as usual", "tbt 13 16 90.0 280.0 0 0 0 90 0".equals(last("tbt ")));
        c.calls.clear();
        q.routeGuidance(rgAt(3, 0));
        q.routeGuidance(rgAt(0, 0));
        check("1.7.2 route search then no route (state 0): cleared with the stock no-guidance values",
                c.calls.contains(CLEAR_TBT) && c.calls.contains(CLEAR_ROAD));
        c.calls.clear();

        // HUD 3칸(M-2): 셋째 칸은 그다음 회전
        CpTurnsFit w = new CpTurnsFit(new android.content.Context(), c, null);
        w.clock = 8500000L;
        w.maneuver(maneuver(0, 2, 0, 0, 90, "Road W", 300));
        w.maneuver(maneuver(1, 1, 0, 0, -90, "Road X", 200));
        w.maneuver(maneuver(2, 4, 0, 0, 0, "Road Y", 400));
        w.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("300"), 12, ENUM, i(2), 13, U16, i(0, 1, 2)));
        check("1.7.2 M-2: HUD list with 3 icons (count 3: this turn 67, next 70, then U-turn 72)",
                ("hud f203434648" + utf16("Road W")).equals(last("hud ")));
        c.calls.clear();
    }

    public static void main(String[] a) throws Exception {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        Car.unit = Car.DISTANCE_MILES;
        c = new Cluster();
        final CountDownLatch restarted = new CountDownLatch(1);
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, new Runnable() {
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
                "road []", "tbt 67 0 0.0 0.2 0 0 2 0 -1", "hud f202434600" + utf16("Main St"), "2c 2 2 0 0 0 65535 0 0 65535",
                "sub 3.1 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 11, STR, s("0.1")));
        expect("partial update (distance only): same HUD list and crossroad name not resent",
                "tbt 67 0 0.0 0.1 0 0 2 0 -1", "2c 2 1 0 0 0 65535 0 0 65535", "sub 3.1 0 2 22 13 1 1 0.0 -1 0",
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
                "tbt 67 0 0.0 1.5 0 0 1 0 -1", "2c 1 5 1 0 0 65535 0 0 65535", "sub 800.0 0 0 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] 800.0 0} {2 72 0 [Pine St] [] 2.0 1}");
        Car.unit = Car.DISTANCE_MILES;

        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 5, U32, i(250)));
        t.routeGuidance(msg(0, U16, i(1), 11, STR, s("0.1"), 12, ENUM, i(MILES)));
        expect("maneuver update without optional angle retains its explicit right-turn direction (Korea Projection: and the kept angle)",
                "tbt 67 0 0.0 0.1 0 0 2 0 -1", "2c 2 1 0 0 0 65535 0 0 65535", "sub 0.4 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");
        t.maneuver(maneuver(0, RIGHT, 0, 0, 90, "Main St", 250));

        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(100), 6, U64, l(0), 5, U64, l(0)));
        expect("trip under 161 m with miles: feet; no arrival time",
                "tbt 67 0 0.0 0.1 0 0 2 0 -1", "2c 2 1 0 0 0 65535 0 0 65535", "sub 328.0 0 3 -1 -1 -1 -1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(0), 6, U64, l(600)));
        Calendar in10 = Calendar.getInstance();
        in10.add(Calendar.SECOND, 600);
        expect("arrival from the time remaining (no distance)",
                "tbt 67 0 0.0 0.1 0 0 2 0 -1", "2c 2 1 0 0 0 65535 0 0 65535",
                "sub 0.0 -1 0 " + in10.get(Calendar.HOUR_OF_DAY) + " " + in10.get(Calendar.MINUTE) + " 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 6, U64, l(0), 5, U64, l(600000000L)));
        expect("arrival as seconds since 2001 (Apple's epoch): 2020-01-06 10:40",
                "tbt 67 0 0.0 0.1 0 0 2 0 -1", "2c 2 1 0 0 0 65535 0 0 65535", "sub 0.0 -1 0 10 40 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("Waze"), 5, U64, l(0)));
        check("source change: route guidance restarted", restarted.await(2, TimeUnit.SECONDS));
        expect("source change (Maps to Waze): HUD cleared; the reset state is 0 until the phone resends it, so cleared again"
                + " (1.7.2: the old app's turn and names cleared too)",
                CLEAR_2C, "lanes f1", "hud f2", "hud f2", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST);

        t.maneuver(maneuver(5, ARRIVE, 0, 0, 0, "Home Rd", 100));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(6), 4, STR, s("Home"), 11, STR, s("300"), 12, ENUM, i(FEET), 13, U16, i(5)));
        expect("state 6 with destination, arrival maneuver in 300 ft",
                "dest 0 [Home]", "road []", "tbt 112 0 0.0 300.0 0 0 3 0 -1", "hud f201700000" + utf16("Home Rd"),
                "2c 3 0 300 0 0 65535 0 0 65535");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(2)));
        expect("arrived (state 2): icon 3, no HUD list for it", "tbt 3 0 0.0 300.0 0 0 3 0 -1", "2c 3 0 300 0 0 65535 0 0 65535");

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
        expect("a second component keeps its own state", "road []", "tbt 71 0 0.0 0.3 0 0 2 0 -1",
                "hud f201470000" + utf16("Comp 7 Rd"), "2c 2 3 0 0 0 65535 0 0 65535");

        t.maneuver(msg(1, U16, i(0), 3, ENUM, i(STRAIGHT), 4, STR, s("Any Rd"), 8, ENUM, i(0), 9, ENUM, i(0)));
        t.routeGuidance(msg(1, ENUM, i(1), 11, STR, s("0.4"), 13, U16, i(0)));
        expect("no component ids: every known component gets it (1.7.2: far, the empty road name already sent)",
                "tbt 65 0 0.0 0.4 0 0 2 0 -1", "hud f201410000" + utf16("Any Rd"), "2c 2 4 0 0 0 65535 0 0 65535",
                "tbt 65 0 0.0 0.4 0 0 2 0 -1", "2c 2 4 0 0 0 65535 0 0 65535");

        t.routeGuidance(msg(0, U16, i(0), 1, ENUM, i(1), 11, STR, s("0.4"), 13, U16, i(0)));
        expect("component 0's first update: DiPO starts it empty (state 0), so the HUD is cleared (and the turn shown before)",
                CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");

        // ---- Korea Projection (korea-build): icons for every maneuver, refresh, crossroad name ----
        final int[] woke = new int[1];
        CpTurnsFit k = new CpTurnsFit(new android.content.Context(), c, null);
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
        expect("left turn with no exit angle (Apple Maps, Naver Map in Korea): icon 70 by type, not skipped",
                "road []", "tbt 70 0 0.0 300.0 0 0 0 0 -1", "hud f201460000" + utf16("A"), "2c 0 0 300 0 0 65535 0 0 65535");
        check("guidance on: the refresh is started once", woke[0] == 1 && k.active());

        k.refresh();
        expect("refresh right after a send: nothing");
        k.clock += 1000L;
        k.refresh();
        expect("refresh after 1 s without the phone: the turn again (HUD list and crossroad unchanged, not resent)",
                "tbt 70 0 0.0 300.0 0 0 0 0 -1", "2c 0 0 300 0 0 65535 0 0 65535");
        k.clock += 1000L;
        k.refresh();
        expect("refresh 2 s after the HUD list: the HUD list resent too",
                "hud f201460000" + utf16("A"), "tbt 70 0 0.0 300.0 0 0 0 0 -1", "2c 0 0 300 0 0 65535 0 0 65535");
        k.clock += 10000L;
        k.refresh();
        expect("refresh 10 s after the crossroad name: the name resent",
                "hud f201460000" + utf16("A"), "road []", "tbt 70 0 0.0 300.0 0 0 0 0 -1", "2c 0 0 300 0 0 65535 0 0 65535");
        check("refresh started only once", woke[0] == 1);

        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(99)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("200")));
        expect("a maneuver with no icon at all: blank icon 8, the HUD list emptied, the distance still sent",
                "tbt 8 0 0.0 200.0 0 0 0 0 -1", "hud f2", "2c 0 0 200 0 0 65535 0 0 65535");
        k.clock += 2000L;
        k.refresh();
        expect("the empty HUD list is resent like any other", "hud f2", "tbt 8 0 0.0 200.0 0 0 0 0 -1", "2c 0 0 200 0 0 65535 0 0 65535");

        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(RIGHT), 11, U8, i(75)));
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 5, U32, i(150)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("150")));
        expect("right turn at 75 degrees (not in the table): the 67 band; the angle kept through a distance-only update",
                "tbt 67 0 0.0 150.0 0 0 0 0 -1", "hud f201430000" + utf16("A"), "2c 0 0 150 0 0 65535 0 0 65535");
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("100")));
        expect("a new maneuver type without an angle: the old angle isn't carried over (left by type: 70)",
                "tbt 70 0 0.0 100.0 0 0 0 0 -1", "hud f201460000" + utf16("A"), "2c 0 0 100 0 0 65535 0 0 65535");
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT), 11, U8, i(-130)));
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT), 5, U32, i(90)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("90")));
        expect("the same type named again without an angle (a new maneuver at a reused index): the sharp angle isn't kept",
                "tbt 70 0 0.0 90.0 0 0 0 0 -1", "2c 0 0 90 0 0 65535 0 0 65535");

        k.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("state 0: turn, names and HUD cleared (1.7.2: stock no-guidance values)", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        k.clock += 5000L;
        k.refresh();
        expect("after state 0 nothing is resent");
        check("after state 0 the refresh ends", !k.active());

        k.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("90")));
        c.calls.clear();
        check("guidance back on: the refresh started again", woke[0] == 2 && k.active());
        k.clock += CpTurnsFit.REFRESH_MAX_MS;
        k.refresh();
        expect("5 minutes without the phone: nothing resent");
        check("5 minutes without the phone: the refresh ends", !k.active());

        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("80")));
        c.calls.clear();
        k.disconnected();
        c.calls.clear();
        check("iAP2 session ended: the refresh ends", !k.active());

        check("fallback icons: left-hand traffic 90 left 70, 75 right 67, 30 slight right 66, keep left 71, unknown 8, roundabout -100 105",
                CpTurnsFit.icon(1, 1, 0, -90, 1) == 70 && CpTurnsFit.icon(2, 0, 0, 75, 1) == 67 && CpTurnsFit.icon(2, 0, 0, 30, 1) == 66
                        && CpTurnsFit.fallbackIcon(13, 0, 0, 0) == 71 && CpTurnsFit.fallbackIcon(99, 0, 0, 0) == CpTurnsFit.NO_ICON
                        && CpTurnsFit.fallbackIcon(99, 0, 1, -100) == 105 && CpTurnsFit.fallbackIcon(4, 1, 0, 0) == 73
                        && CpTurnsFit.fallbackIcon(12, 0, 0, 0) == 112);
        check("turns ahead: sharp/slight 47-50 get their arrows", CpTurnsFit.aheadIcon(47, 0, 0, 0) == 69
                && CpTurnsFit.aheadIcon(48, 0, 0, 0) == 68 && CpTurnsFit.aheadIcon(49, 0, 0, 0) == 71 && CpTurnsFit.aheadIcon(50, 0, 0, 0) == 66);

        check("icons: sharp left 69, slight right 66, roundabout exit 96, U-turn left-hand traffic 73 (2.4.3: left-hand roundabout exit 4 is the stock 192)",
                CpTurnsFit.icon(1, 0, 0, -120, 1) == 69 && CpTurnsFit.icon(2, 0, 0, 60, 1) == 66 && CpTurnsFit.icon(2, 0, 1, 0, 1) == 96
                        && CpTurnsFit.icon(31, 1, 1, 0, 1) == 192 && CpTurnsFit.icon(4, 1, 0, 0, 1) == 73 && CpTurnsFit.icon(47, 0, 0, -140, 1) == 69
                        && CpTurnsFit.icon(99, 0, 0, 0, 1) == 0);
        check("lane angles: 0 straight, 90 right, -45 slight left, 180 back",
                CpTurnsFit.laneIcon(0) == 1 && CpTurnsFit.laneIcon(90) == 3 && CpTurnsFit.laneIcon(-45) == 8 && CpTurnsFit.laneIcon(180) == 5
                        && CpTurnsFit.laneIcon(270) == 7);
        indiaTurns();
        korea230();
        korea172();
        near172();
        diag172();
        m15();
        phoneBar();
        beta2();
        hyst173();
        dynamicD();
        staleNext();
        forkLikeStock();
        unknownManeuverAsk();
        staleNextReview();
        kakaoOrder();
        kakaoOrderMore();
        redesignReview();
        review4();
        stockSideBits();
        review5();
        layerChoice();
        mode181();
        System.exit(fail);
    }

    /** 곧 회전(1) / 멀리 있음(0) 배치 순서: 보낸 tbt 줄에서 배치가 바뀐 곳만 남긴다(13·17·25·31 = 곧 회전) */
    static String layouts() {
        StringBuilder b = new StringBuilder();
        char prev = 0;
        for (String x : c.calls) {
            if (!x.startsWith("tbt ")) continue;
            String d = x.split(" ")[1];
            char k = ("13".equals(d) || "17".equals(d) || "18".equals(d) || "25".equals(d) || "31".equals(d)) ? '1' : '0';
            if (k != prev) b.append(k);
            prev = k;
        }
        return b.toString();
    }

    /** 1.7.3(N-3, #67): D 경계 근처 거리 흔들림에 작은↔큰 그림이 왔다갔다하지 않음(들어갈 때 D, 나올 때 D + 50m) */
    static void hyst173() {
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
        t.clock = 9100000L;
        check("1.7.3 N-3: exit margin 50 m", CpTurnsFit.NEAR_EXIT_MARGIN == 50);
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, new int[] { 0, 90, -90, 180 }, -1, "Road A"));
        for (int m : new int[] { 420, 380, 352, 349, 356, 345, 362, 351, 340, 399, 348, 300, 200 }) t.routeGuidance(rgAt(1, m));
        check("1.7.3 N-3: 420..349..399..200 m around D 350 m: far, then near once, never back to far", "01".equals(layouts()));
        c.calls.clear();
        t.routeGuidance(rgAt(1, 400));
        // 2.4.4 후보: 200m → 400m(뜀)는 한 번 붙잡고 다음 갱신에서 판단한다
        check("2.4.4: 200 -> 400 m held once (nothing sent)", tbtCount() == 0);
        t.routeGuidance(rgAt(1, 400));
        check("1.7.3 N-3: 400 m (D + 50) still near", "1".equals(layouts()));
        c.calls.clear();
        t.routeGuidance(rgAt(1, 401));
        t.routeGuidance(rgAt(1, 380));
        t.routeGuidance(rgAt(1, 351));
        check("1.7.3 N-3: past D + 50 m: far, and stays far until D again (380, 351 m)", "0".equals(layouts()));
        c.calls.clear();
        t.routeGuidance(rgAt(1, 350));
        check("1.7.3 N-3: back inside D (350 m): near", "1".equals(layouts()));
        c.calls.clear();
        // 다른 회전으로 바뀌면 히스테리시스는 새로 시작: 새 회전 370m는 멀리 있음
        t.maneuver(nearManeuver(1, 2, 0, 0, 90, new int[] { 0, -90 }, -1, "Road B"));
        t.routeGuidance(rgAtIdx(1, 370, 1));
        check("1.7.3 N-3: a new maneuver at 370 m: far (the old turn's near doesn't carry over)",
                last("tbt ") != null && !last("tbt ").startsWith("tbt 13 "));
        c.calls.clear();
        // 고속 출구 D 1050m: 1040 -> 1080 -> 1060 -> 1101
        t.maneuver(nearManeuver(2, 23, 0, 0, 0, null, -1, "Exit Rd"));
        for (int m : new int[] { 1200, 1060, 1040, 1080, 1060, 1100, 1020 }) t.routeGuidance(rgAtIdx(1, m, 2));
        check("1.7.3 N-3: highway exit around 1050 m: far, then near once", "01".equals(layouts()));
        t.routeGuidance(rgAtIdx(1, 1101, 2));
        check("1.7.3 N-3: highway exit past 1100 m: far again", "010".equals(layouts()));

        // 1.7.3 추가(로그 H 확인): 카플레이는 같은 회전의 도로 이름만 바뀌어도(같은 번호, after road A<->B) 곧 회전이 끊기지 않는다
        c.calls.clear();
        t.maneuver(nearManeuver(3, 2, 0, 0, 90, new int[] { 0, 90 }, -1, "Road A"));
        t.routeGuidance(rgAtIdx(1, 300, 3));
        for (int k = 0; k < 10; k++) {
            t.maneuver(nearManeuver(3, 2, 0, 0, 90, new int[] { 0, 90 }, -1, k % 2 == 0 ? "Road B" : "Road A"));
            t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("300"), 13, U16, i(3)));   // 거리(item 10) 없는 갱신
            t.routeGuidance(rgAtIdx(1, 300 - k * 5, 3));
        }
        check("1.7.3 name only (CarPlay): same maneuver, road A<->B, with and without distance updates: near the whole time", "01".equals(layouts()) || "1".equals(layouts()));
        check("1.7.3 name only (CarPlay): no far frame after the first near", layouts().endsWith("1") && layouts().indexOf("10") < 0);
    }

    /** 1.7.2 시험판 3: 카플레이 막대를 순정 단속 정보 프레임에도(PhoneBarOut 방송) */
    static void phoneBar() {
        check("trial 3: DiPO's copy of the sender matches PhoneBar (name, value key, NONE, interval)",
                PhoneBarOut.ACTION.equals(hmgapi.mods.wireless.PhoneBar.ACTION) && PhoneBarOut.EXTRA_BAR.equals(hmgapi.mods.wireless.PhoneBar.EXTRA_BAR)
                        && PhoneBarOut.NONE == hmgapi.mods.wireless.PhoneBar.NONE && PhoneBarOut.RESEND_MS == hmgapi.mods.wireless.PhoneBar.RESEND_MS);
        PhoneBarOut o = new PhoneBarOut();
        check("trial 3: DiPO sender: first value, not again within 10 s, again after 10 s, NONE once",
                o.due(50, 0L) && !o.due(50, 9999L) && o.due(50, 10000L) && o.due(PhoneBarOut.NONE, 10001L) && !o.due(PhoneBarOut.NONE, 90000L));
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        android.content.Context ctx = new android.content.Context();
        CpTurnsFit t = new CpTurnsFit(ctx, c, null);
        t.clock = 9500000L;
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road B"));
        t.routeGuidance(rgAt(1, 500));
        t.routeGuidance(rgAt(1, 300));
        t.routeGuidance(rgAt(1, 290));
        java.util.List<Integer> bars = new java.util.ArrayList<Integer>();
        boolean only = true;
        for (android.content.Intent i : ctx.sent) {
            bars.add(i.getIntExtra(PhoneBarOut.EXTRA_BAR, -99));
            if (!PhoneBarOut.ACTION.equals(i.action) || i.ints.size() != 1) only = false;
        }
        check("trial 3: CarPlay far at 500 m then near at 300 m: broadcasts 0 then 80, not again for the same bar at 290 m (only the bar)",
                bars.equals(java.util.Arrays.asList(0, 80)) && only);
        check("trial 3: the turn frame carries the same bar", "tbt 13 16 90.0 290.0 0 0 0 80 0".equals(last("tbt ")));
        t.routeGuidance(rgAt(5, 290));
        check("trial 3: rerouting: bar 0", ctx.sent.get(ctx.sent.size() - 1).getIntExtra(PhoneBarOut.EXTRA_BAR, -99) == 0);
        t.routeGuidance(rgAt(1, 100));
        t.stop();
        check("trial 3: guidance end: NONE (back to the stock bar)", ctx.sent.get(ctx.sent.size() - 1).getIntExtra(PhoneBarOut.EXTRA_BAR, -99) == PhoneBarOut.NONE);
        int n = ctx.sent.size();
        t.disconnected();
        check("trial 3: then the session ends: no second NONE", ctx.sent.size() == n);
    }


    /** 1.7.2 베타 2: 애플 지도 이름 번갈아(#132), 경로 시작 거리 0.0(#123) */
    static void beta2() {
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, null);
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

        // 경로 시작: 출발(종류 11)에 표시 거리 "0" -> 거리 무효(-3), HUD 거리는 보내지 않음
        c = new Cluster();
        CpTurnsFit d = new CpTurnsFit(new android.content.Context(), c, null);
        d.clock = 9900000L;
        d.maneuver(maneuver(0, 11, 0, 0, 0, "Start", 0));
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("0"), 12, ENUM, i(2), 13, U16, i(0)));
        String st = last("tbt ");
        check("beta 2 (#123): route start (StartRoute, shown 0): distance invalid (-3), not 0.0", st != null && st.startsWith("tbt 1 0 0.0 0.0 -3 "));
        boolean hud0 = false;
        for (String x : c.calls) if (x.startsWith("2c ") && !x.startsWith("2c 15 15 65535")) hud0 = true;
        check("beta 2 (#123): no HUD distance 0 at the route start", !hud0);
        c.calls.clear();
        d.maneuver(maneuver(0, 2, 0, 0, 90, "Road T", 300));
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s(""), 12, ENUM, i(2), 13, U16, i(0)));
        st = last("tbt ");
        check("beta 2 (#123): a turn with no shown distance yet: distance invalid", st != null && st.contains(" 0.0 -3 "));
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
        d.maneuver(maneuver(0, 18, 0, 0, 0, "Start", 0));
        d.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("0"), 12, ENUM, i(2), 13, U16, i(0)));
        st = last("tbt ");
        check("beta 2 (review 3): start with a U-turn (type 18) at 0: distance invalid too", st != null && st.contains(" 0.0 -3 "));
    }

    /** 2.4.7: unknownManeuverAsk의 다시 요청 횟수(restart가 따로 스레드에서 세므로 잠깐 기다려 읽는다) */
    static int asksAfter(java.util.concurrent.atomic.AtomicInteger n, int want) throws InterruptedException {
        for (int k = 0; k < 100 && n.get() < want; k++) Thread.sleep(10);
        Thread.sleep(30);
        return n.get();
    }

    static int unknownLines(String part) {
        int n = 0;
        for (String l : Dipo.logs) if (l.startsWith("CarPlay: maneuver ") && l.contains(part)) n++;
        return n;
    }

    /**
     * 2.4.7(#465·#450): 회전 번호(120)는 오는데 그 회전 내용(121)이 없으면 길안내를 다시 요청한다. 처음 본 뒤 3초, 같은 번호에 한 번,
     * 요청 사이 30초, 연결 한 번에 3번, busy면 양보, 경로 끝은 번호만 잊고 끊김·앱 바뀜은 횟수까지 처음부터. 줄은 숫자만
     */
    static void unknownManeuverAsk() throws Exception {
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        final java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        CpTurnsFit t = new CpTurnsFit(new android.content.Context(), c, new Runnable() {
            @Override
            public void run() {
                asked.incrementAndGet();
            }
        });
        final boolean[] busy = { false };
        t.busy = new CpTurnsFit.Busy() {
            @Override
            public boolean busy() {
                return busy[0];
            }
        };
        int logs0 = Dipo.logs.size();
        long t0 = 41000000L;
        t.clock = t0;
        t.routeGuidance(rgAtIdx(1, 300, 5));
        t.clock = t0 + 1000L;
        t.routeGuidance(rgAtIdx(1, 290, 5));
        check("2.4.7 unknown turn: not asked at first sight nor within 3 s (the phone's 121 can come after its 120)", asksAfter(asked, 1) == 0);
        t.clock = t0 + 3500L;
        t.routeGuidance(rgAtIdx(1, 280, 5));
        check("2.4.7 unknown turn: still unknown after 3 s: route guidance asked again once", asksAfter(asked, 1) == 1);
        check("2.4.7 unknown turn: one line, numbers only (maneuver 5, 3 s, 1/3)",
                unknownLines("maneuver 5 unknown for 3 s, route guidance asked again (1/3)") == 1);
        t.clock = t0 + 40000L;
        t.routeGuidance(rgAtIdx(1, 270, 5));
        check("2.4.7 unknown turn: the same number is asked about only once", asksAfter(asked, 2) == 1);
        // 폰이 회전 내용을 다시 보냄: 보인다
        t.maneuver(nearManeuver(5, 2, 0, 0, 90, null, -1, "Road U"));
        t.clock = t0 + 41000L;
        t.routeGuidance(rgAtIdx(1, 260, 5));
        check("2.4.7 unknown turn: once the phone resends the maneuver, it's shown (near 13)", last("tbt ") != null && last("tbt ").startsWith("tbt 13 "));
        check("2.4.7 unknown turn: a known turn forgets the unknown number", t.unknownIdx == -1);
        // 다른 번호: 30초 간격 안이면 기다림(첫 요청 3.5초 뒤 + 30초 = 33.5초 뒤까지는 이미 지났으므로 여기서는 시각을 앞으로 둔다)
        t.clock = t0 + 50000L;
        t.routeGuidance(rgAtIdx(1, 300, 6));
        t.clock = t0 + 54000L;
        busy[0] = true;
        t.routeGuidance(rgAtIdx(1, 290, 6));
        check("2.4.7 unknown turn: another ask is in progress (RgAsk waiting or a restart just now): this one yields", asksAfter(asked, 2) == 1);
        busy[0] = false;
        t.clock = t0 + 55000L;
        t.routeGuidance(rgAtIdx(1, 280, 6));
        check("2.4.7 unknown turn: not busy any more: asked (2/3)", asksAfter(asked, 2) == 2 && unknownLines("(2/3)") == 1);
        t.clock = t0 + 60000L;
        t.routeGuidance(rgAtIdx(1, 300, 7));
        t.clock = t0 + 64000L;
        t.routeGuidance(rgAtIdx(1, 290, 7));
        check("2.4.7 unknown turn: less than 30 s since the last ask: waits", asksAfter(asked, 3) == 2);
        t.clock = t0 + 86000L;
        t.routeGuidance(rgAtIdx(1, 280, 7));
        check("2.4.7 unknown turn: 30 s after the last ask: asked (3/3)", asksAfter(asked, 3) == 3);
        // 경로 끝(stop): 번호는 잊지만 횟수는 연결 단위로 남는다
        t.routeGuidance(rgAtIdx(0, 0, 7));
        t.clock = t0 + 130000L;
        t.routeGuidance(rgAtIdx(1, 300, 8));
        t.clock = t0 + 134000L;
        t.routeGuidance(rgAtIdx(1, 290, 8));
        t.clock = t0 + 170000L;
        t.routeGuidance(rgAtIdx(1, 280, 8));
        check("2.4.7 unknown turn: 3 asks in this connection: no 4th, even on a new route", asksAfter(asked, 4) == 3);
        check("2.4.7 unknown turn: the limit is logged once (numbers only)", unknownLines("still unknown after 3 asks") == 1);
        t.clock = t0 + 210000L;
        t.routeGuidance(rgAtIdx(1, 270, 8));
        check("2.4.7 unknown turn: the limit line isn't repeated", unknownLines("still unknown after 3 asks") == 1);
        // 연결 끝: 처음부터
        t.disconnected();
        t.clock = t0 + 300000L;
        t.routeGuidance(rgAtIdx(1, 300, 9));
        t.clock = t0 + 304000L;
        t.routeGuidance(rgAtIdx(1, 290, 9));
        check("2.4.7 unknown turn: after a disconnect the count starts over (1/3)", asksAfter(asked, 4) == 4);
        // 길 찾는 중·안내 없음은 묻지 않는다
        CpTurnsFit q = new CpTurnsFit(new android.content.Context(), new Cluster(), new Runnable() {
            @Override
            public void run() {
                asked.incrementAndGet();
            }
        });
        q.clock = t0;
        q.routeGuidance(rgAtIdx(0, 300, 3));
        q.clock = t0 + 5000L;
        q.routeGuidance(rgAtIdx(0, 300, 3));
        check("2.4.7 unknown turn: not guiding (state 0): never asks", asksAfter(asked, 5) == 4);
        // restart가 없으면(시험·설정) 아무 일도 없다
        CpTurnsFit z = new CpTurnsFit(new android.content.Context(), new Cluster(), null);
        z.clock = t0;
        z.routeGuidance(rgAtIdx(1, 300, 3));
        z.clock = t0 + 5000L;
        z.routeGuidance(rgAtIdx(1, 300, 3));
        check("2.4.7 unknown turn: no restart: nothing asked, no line", z.unknownAsks == 0);
        boolean plain = true;
        for (int k = logs0; k < Dipo.logs.size(); k++) {
            String l = Dipo.logs.get(k);
            if (l.startsWith("CarPlay: maneuver ") && (l.contains(" unknown") || l.contains("asked again")) && !l.matches("[A-Za-z0-9 :,()/.]*")) plain = false;
        }
        check("2.4.7 unknown turn: its lines are plain words and numbers (no road or app names)", plain);
    }

}
