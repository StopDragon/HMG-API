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
        check("India Apple Maps left/right and straight", CpTurns.icon(1, 1, 0, -90, 1) == 70
                && CpTurns.icon(2, 1, 0, 90, 1) == 67 && CpTurns.icon(3, 1, 0, 0, 1) == 65);
        for (int side = 0; side <= 1; side++) {
            check("explicit ordinary turns without optional angle, side " + side,
                    CpTurns.icon(1, side, 0, 0, 1) == 70 && CpTurns.icon(2, side, 0, 0, 1) == 67);
            check("departure is NO_INFO, not a straight arrow, side " + side,
                    CpTurns.icon(11, side, 0, 0, 1) == 1);
        }
        check("invalid traffic sides do not gain ordinary turn/departure fallbacks",
                CpTurns.icon(1, 2, 0, 0, 1) == 0 && CpTurns.icon(2, -1, 0, 0, 1) == 0
                        && CpTurns.icon(3, 2, 0, 0, 1) == 0 && CpTurns.icon(11, 2, 0, 0, 1) == 0);
        // Korea Projection: an angle the table lacks gets the nearest arrow (band); departure stays no turn
        check("unlisted turn angles get the nearest arrow; departure with an angle or a junction stays no turn",
                CpTurns.icon(1, 1, 0, -91, 1) == 70 && CpTurns.icon(2, 1, 0, 91, 1) == 67
                        && CpTurns.icon(11, 1, 0, 90, 1) == 0 && CpTurns.icon(11, 1, 1, 0, 1) == 0
                        && CpTurns.fallbackIcon(11, 1, 0, 90) == CpTurns.NO_ICON && CpTurns.fallbackIcon(11, 1, 1, 0) == CpTurns.NO_ICON);
        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
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
                CpTurns.icon(9, 0, 0, 0, 1) == 160 && CpTurns.fallbackIcon(9, 1, 0, 0) == 160 && CpTurns.icon(5, 0, 0, 0, 1) == 172);
        check("2.3.0 highway off-ramp left (22): 165 (was 82)", CpTurns.icon(22, 0, 0, 0, 1) == 165);
        check("2.3.0 highway off-ramp right (23): 164 (was 81)", CpTurns.icon(23, 0, 0, 0, 1) == 164);
        // 2-A last row: types the stock table leaves out
        check("2.3.0 off-ramp (8): 163", CpTurns.icon(8, 0, 0, 0, 1) == 0 && CpTurns.fallbackIcon(8, 0, 0, 0) == 163);
        check("2.3.0 exit ferry (16): 176", CpTurns.fallbackIcon(16, 0, 0, 0) == 176);
        check("2.3.0 change ferry (17): 176", CpTurns.fallbackIcon(17, 0, 0, 0) == 176);
        check("2.3.0 left turn at the end (20): 70", CpTurns.fallbackIcon(20, 0, 0, 0) == 70);
        check("2.3.0 right turn at the end (21): 67", CpTurns.fallbackIcon(21, 0, 0, 0) == 67);
        check("2.3.0 U-turn when possible (26): 72", CpTurns.fallbackIcon(26, 0, 0, 0) == 72);
        check("2.3.0 end of directions (27): 112", CpTurns.fallbackIcon(27, 0, 0, 0) == 112);
        check("2.3.0 change highway (51): 163", CpTurns.fallbackIcon(51, 0, 0, 0) == 163);
        check("2.3.0 no turn (0), exit roundabout (7), start (11): blank 8",
                CpTurns.fallbackIcon(0, 0, 0, 0) == 8 && CpTurns.fallbackIcon(7, 0, 0, 0) == 8 && CpTurns.fallbackIcon(11, 0, 0, 0) == 8);
        check("2.3.0 off-ramp (8) with an angle: still 163 (no left/right by angle)",
                CpTurns.icon(8, 0, 0, 45, 1) == 0 && CpTurns.fallbackIcon(8, 0, 0, 45) == 163 && CpTurns.fallbackIcon(8, 0, 0, -45) == 163);
        check("2.3.0 highway off-ramp left/right (22/23) with an angle: 165/164",
                CpTurns.fallbackIcon(22, 0, 0, -30) == 165 && CpTurns.fallbackIcon(23, 0, 0, 30) == 164 && CpTurns.icon(22, 0, 0, -30, 1) == 0);
        check("2.3.0 U-turn when possible (26) with an angle: 72, left-hand traffic 73",
                CpTurns.fallbackIcon(26, 0, 0, -180) == 72 && CpTurns.fallbackIcon(26, 1, 0, 180) == 73 && CpTurns.fallbackIcon(26, 0, 0, 0) == 72);
        check("2.3.0 on-ramp (9) with an angle: 160", CpTurns.fallbackIcon(9, 0, 0, 30) == 160 && CpTurns.fallbackIcon(9, 0, 0, -60) == 160);
        check("2.3.0 the other added types with an angle keep their number (51 163, 16/17 176, 20/21 70/67, 27 112)",
                CpTurns.fallbackIcon(51, 0, 0, 20) == 163 && CpTurns.fallbackIcon(16, 0, 0, 90) == 176 && CpTurns.fallbackIcon(17, 0, 0, -90) == 176
                        && CpTurns.fallbackIcon(20, 0, 0, -90) == 70 && CpTurns.fallbackIcon(21, 0, 0, 90) == 67 && CpTurns.fallbackIcon(27, 0, 0, 30) == 112);
        check("2.3.0 turn ahead with no arrow: 8 (was 64)", CpTurns.aheadIcon(99, 0, 0, 0) == 8 && CpTurns.aheadIcon(9, 0, 0, 0) == 8);

        // 2-C: roundabout bands and their edges (right-hand traffic)
        int[][] edges = { { 22, 96 }, { 23, 97 }, { 67, 97 }, { 68, 99 }, { 112, 99 }, { 113, 100 }, { 157, 100 }, { 158, 102 },
            { -22, 96 }, { -23, 107 }, { -67, 107 }, { -68, 105 }, { -112, 105 }, { -113, 104 }, { -157, 104 }, { -158, 102 },
            { 1, 96 }, { -46, 107 }, { 45, 97 }, { 200, 102 }, { 300, 107 }, { 270, 105 } };
        for (int[] e : edges) check("2.3.0 roundabout band: " + e[0] + " degrees -> " + e[1], CpTurns.roundaboutIcon(0, e[0]) == e[1]);
        // the stock DiPO's exact angles come first
        int[][] exact = { { -45, 104 }, { 315, 104 }, { 90, 99 }, { 135, 100 }, { -90, 105 }, { -135, 104 }, { 180, 102 }, { -180, 102 } };
        for (int[] e : exact) check("2.3.0 roundabout stock angle: " + e[0] + " degrees -> " + e[1], CpTurns.roundaboutIcon(0, e[0]) == e[1]);
        check("2.3.0 roundabout through icon(): exit 4 at 0 -> 97 (stock), type 33 at -45 -> 104 (stock angle), -46 -> 107, 1 -> 96",
                CpTurns.icon(31, 0, 1, 0, 1) == 97 && CpTurns.icon(33, 0, 1, -45, 1) == 0 && CpTurns.fallbackIcon(33, 0, 1, -45) == 104
                        && CpTurns.fallbackIcon(33, 0, 1, -46) == 107 && CpTurns.fallbackIcon(31, 0, 1, 1) == 96);
        check("2.3.0 roundabout, left-hand traffic: the side-1 stock angles (-30 107, 120 100, -120 104), no B family",
                CpTurns.roundaboutIcon(1, -30) == 107 && CpTurns.roundaboutIcon(1, 120) == 100 && CpTurns.roundaboutIcon(1, -120) == 104
                        && CpTurns.roundaboutIcon(1, 45) == 97 && CpTurns.roundaboutIcon(1, -45) == 107);

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
        check("1.7.2 M-3: a turn goes out with the far-away bar (bar 0, barErr -1), side street 0, direction 0",
                "tbt 67 0 0.0 300.0 0 0 0 0 -1".equals(tbt));
        c.calls.clear();
        t.stop();
        expect("1.7.2 M-2: route guidance stopped after a turn: stock no-guidance turn (8, distance and bar invalid), empty names, 2C (15,15,65535)x3, HUD",
                CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_DEST, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        t.stop();
        expect("1.7.2 M-2: stopped again with nothing shown since: neither the turn nor the HUD is cleared twice (2nd review 2)");
        check("1.7.2 M-2: clear values within NavigationCluster's ranges (distanceErr -3..0, scale 0..4 or 15, barErr -2..0)",
                CpTurns.CLEAR_ICON == 8 && CpTurns.CLEAR_DIST_ERR >= -3 && CpTurns.CLEAR_DIST_ERR <= 0 && CpTurns.CLEAR_UNIT == 15
                        && CpTurns.BAR_FAR_ERR == -1 && CpTurns.BAR_FAR >= 0 && CpTurns.BAR_FAR <= 100
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

    static String last(String prefix) {
        String r = null;
        for (String x : c.calls) if (x.startsWith(prefix)) r = x;
        return r;
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
        CpTurns off = new CpTurns(new android.content.Context(), c, null);
        off.clock = 9000000L;
        Dipo.logs.clear();
        off.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road A"));
        off.routeGuidance(rgAt(1, 200));
        check("1.7.2 diag: no diag lines with the debug log off", diagLines().isEmpty()
                && "tbt 13 16 90.0 200.0 0 0 0 50 0".equals(last("tbt ")));

        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
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
        CpTurns g = new CpTurns(new android.content.Context(), c, null);
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
        CpTurns s = new CpTurns(new android.content.Context(), c, null);
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
                        + "tbt display 13 side 4113 dir 90.0 bar 10 barErr 0 unit 0 dist 60.0")
                        && "tbt 13 4113 90.0 60.0 0 0 0 10 0".equals(last("tbt ")));
        // 곧 회전에서 막대가 같으면(55m, 막대 10) 줄이 없고, 막대가 바뀌면(200m, 막대 50) 한 줄
        t.routeGuidance(rgAt(1, 55));
        check("1.7.2 diag: near, same bar: no new line", diagLines().isEmpty());
        t.routeGuidance(rgAt(1, 200));
        d = diagLines();
        check("1.7.2 diag: near, the bar changes: one line with the new bar",
                d.size() == 1 && d.get(0).endsWith("tbt display 13 side 4113 dir 90.0 bar 50 barErr 0 unit 0 dist 200.0"));
        // 같은 차로 안내를 다시 받으면 줄을 남기지 않는다
        t.lanes(laneMsg(3, 2));
        check("1.7.2 diag: the same lane guidance again: no new line", diagLines().isEmpty());
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 1.7.2(M-4·M-5·M-2, 소유자 결정 2026-11-09): 곧 회전 화면, 탐색 중 9·10, HUD 3칸 */
    static void near172() {
        // 옆길 비트와 방향 칸(순정 SetSideStreetBit)
        check("1.7.2 M-4 side bits: straight 0, right 90 bit 4, left 270 bit 12, behind (180 +-12) none",
                CpTurns.streetBit(0) == 0 && CpTurns.streetBit(90) == 4 && CpTurns.streetBit(-90) == 12
                        && CpTurns.streetBit(180) == -1 && CpTurns.streetBit(170) == -1 && CpTurns.streetBit(-170) == -1
                        && CpTurns.streetBit(12) == 0 && CpTurns.streetBit(13) == 1 && CpTurns.streetBit(45) == 2
                        && CpTurns.streetBit(347) == 15 && CpTurns.streetBit(-13) == 15);
        check("1.7.2 M-4 side bits: T junction (straight, right, left; incoming road 180 left out) with the right exit = 0x1011 (car-3 picture)",
                CpTurns.sideBits(new int[] { 0, 90, -90, 180 }, CpTurns.exitBit(90)) == 0x1011);
        check("1.7.2 M-4 side bits: no other angles: the exit's bit only (V-9); behind exits bit 7 (<=179) / 8",
                CpTurns.sideBits(null, CpTurns.exitBit(-45)) == 1 << 14 && CpTurns.exitSlot(-45) == 42
                        && CpTurns.exitSlot(170) == 21 && CpTurns.sideBits(null, CpTurns.exitBit(170)) == 1 << 7
                        && CpTurns.exitSlot(-170) == 27 && CpTurns.sideBits(null, CpTurns.exitBit(-170)) == 1 << 8);
        check("review 4: an exit at -160 degrees (200 clockwise) is bit 9 (slot 27), not the behind bit 8",
                CpTurns.exitBit(-160) == 9 && CpTurns.exitSlot(-160) == 27 && CpTurns.sideBits(null, CpTurns.exitBit(-160)) == 1 << 9);
        // 표시 번호(순정 SetCase*)
        int[] r = CpTurns.nearDisplay(2, 0, 0, 0, 67), l = CpTurns.nearDisplay(1, 0, 0, -90, 70), u = CpTurns.nearDisplay(4, 0, 0, 0, 72),
                ul = CpTurns.nearDisplay(4, 1, 0, 0, 73), xr = CpTurns.nearDisplay(23, 0, 0, 0, 164), xl = CpTurns.nearDisplay(22, 0, 0, 0, 165),
                fk = CpTurns.nearDisplay(13, 0, 0, 0, 71), rb = CpTurns.nearDisplay(29, 0, 1, 90, 99), sr = CpTurns.nearDisplay(48, 0, 0, 0, 68);
        check("1.7.2 M-4 display: turn 13 (right 12, left 36), U-turn 31 (36; left-hand traffic 12), exit right 17 / left 18, keep left 25, roundabout its exit icon",
                arr(r).equals("[13, 4, 12]") && arr(l).equals("[13, 12, 36]") && arr(u).equals("[31, -1, 36]") && arr(ul).equals("[31, -1, 12]")
                        && arr(xr).equals("[17, -1, 12]") && arr(xl).equals("[18, -1, 36]") && arr(fk).equals("[25, -1, 42]")
                        && arr(rb).equals("[99, 4, 12]") && arr(sr).equals("[13, 6, 18]"));
        check("1.7.2 M-4 display: destination, waypoint, departure, ferry, no turn: no near layout (far, bar invalid)",
                CpTurns.nearDisplay(12, 0, 0, 0, 112) == null && CpTurns.nearDisplay(10, 0, 0, 0, 112) == null
                        && CpTurns.nearDisplay(24, 0, 0, 0, 114) == null && CpTurns.nearDisplay(11, 0, 0, 0, 1) == null
                        && CpTurns.nearDisplay(15, 0, 0, 0, 176) == null && CpTurns.nearDisplay(0, 0, 0, 0, 8) == null);
        // 막대와 D
        check("1.7.2 M-4 bar: 10 x max(1, floor(remain x 10 / D)), up to 90 (trial 3: the stock maximum)",
                CpTurns.bar(300, 350) == 80 && CpTurns.bar(10, 350) == 10 && CpTurns.bar(0, 350) == 10 && CpTurns.bar(350, 350) == 90 && CpTurns.bar(349, 350) == 90 && CpTurns.bar(315, 350) == 90
                        && CpTurns.bar(34, 350) == 10 && CpTurns.bar(35, 350) == 10 && CpTurns.bar(70, 350) == 20);
        check("1.7.2 M-5 D: 350 m, 1050 m for highway exits", CpTurns.D_GENERAL == 350 && CpTurns.D_EXPRESSWAY == 1050
                && CpTurns.expressway(23) && CpTurns.expressway(22) && CpTurns.expressway(8) && !CpTurns.expressway(2) && !CpTurns.expressway(13));

        c = new Cluster();
        CpTurns t = new CpTurns(new android.content.Context(), c, null);
        t.clock = 8000000L;
        // 소유자 결정 2026-11-09 "D 안의 모든 회전": 차로 안내가 없어도 D 안이면 곧 회전(카카오내비는 차로 안내를 보내지 않음)
        // 시험판 3: D는 순정처럼 고정 350m(처음 거리로 줄이지 않음), 200m -> 막대 50, 옆길 0x1011(직진·오른쪽·왼쪽), 90도, 도로 이름
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, new int[] { 0, 90, -90, 180 }, -1, "Road A"));
        t.routeGuidance(rgAt(1, 200));
        expect("1.7.2 M-4 (owner 11-09): a turn without lane guidance inside D: near, turn 13, side 0x1011, 90 degrees, bar 50, the road name",
                "road [Road A]", "tbt 13 4113 90.0 200.0 0 0 0 50 0", "hud f201430000" + utf16("Road A"), "2c 0 0 200 0 0 65535 0 0 65535");
        // 링크한 차로 안내에 차로가 없어도 곧 회전, 190m -> 막대 50
        t.lanes(laneMsg(3, 0));
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, new int[] { 0, 90, -90, 180 }, 3, "Road A"));
        t.routeGuidance(rgAt(1, 190));
        check("1.7.2 M-4 (owner 11-09): lane guidance with no lanes: near all the same", "tbt 13 4113 90.0 190.0 0 0 0 50 0".equals(last("tbt ")));
        c.calls.clear();
        // 차로 2개: 차로 프레임은 따로 나가고 곧 회전은 그대로. 180m -> 막대 10 x floor(1800/350) = 50
        t.lanes(laneMsg(3, 2));
        t.routeGuidance(rgAt(1, 180));
        expect("1.7.2 M-4: with lane guidance and inside D: the lane frame, then turn 13, side 0x1011, 90 degrees, bar 50",
                "lanes f102000101" + "0000000000000000000000000000" + "01000001", "tbt 13 4113 90.0 180.0 0 0 0 50 0",
                "2c 0 0 180 0 0 65535 0 0 65535");
        t.routeGuidance(rgAt(1, 30));
        check("1.7.2 M-4: 30 m: the bar never below 10", "tbt 13 4113 90.0 30.0 0 0 0 10 0".equals(last("tbt ")));
        c.calls.clear();
        // 종류를 다시 말한 갱신에 각도·차로 링크가 없음: 곧 회전 그대로, 옆길은 나가는 길 비트만
        t.maneuver(nearManeuver(0, 2, 0, 0, 90, null, -1, "Road A"));
        t.routeGuidance(rgAt(1, 25));
        check("1.7.2 M-4 (owner 11-09): the same maneuver resent without angles or lane link: still near, only the exit bit, the name kept",
                "tbt 13 16 90.0 25.0 0 0 0 10 0".equals(last("tbt ")) && last("road ") == null);
        c.calls.clear();

        // 고속도로 오른쪽 출구: 처음 거리 1200m(D 1050m보다 김), 900m에서 17, 막대 10 x floor(9000/1050) = 80, 옆길 없음
        CpTurns h = new CpTurns(new android.content.Context(), c, null);
        h.clock = 8100000L;
        h.lanes(laneMsg(0, 3));
        h.maneuver(nearManeuver(0, 23, 0, 0, 0, null, 0, "Exit Rd"));
        h.routeGuidance(rgAt(1, 1200));
        check("1.7.2 M-5: highway exit at 1200 m (beyond D 1050 m): far", "tbt 164 0 0.0 1200.0 0 0 0 0 -1".equals(last("tbt ")));
        h.routeGuidance(rgAt(1, 900));
        check("1.7.2 M-4/M-5: highway exit right at 900 m (D 1050 m): display 17, direction 90, no side bits, bar 80",
                "tbt 17 0 90.0 900.0 0 0 0 80 0".equals(last("tbt ")));
        h.routeGuidance(rgAt(1, 1100));
        check("1.7.2 M-5: farther than D: far again (icon 164, bar invalid)", "tbt 164 0 0.0 1100.0 0 0 0 0 -1".equals(last("tbt ")));
        c.calls.clear();
        // 일반 회전 400m(D 350m 밖): 멀리 있음. 340m: 곧 회전, 처음 거리 400m라 D 350m, 막대 90
        CpTurns g = new CpTurns(new android.content.Context(), c, null);
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
        CpTurns v = new CpTurns(new android.content.Context(), c, null);
        v.clock = 8250000L;
        v.lanes(laneMsg(0, 2));
        v.maneuver(nearManeuver(0, 2, 0, 0, 90, null, 0, "Road V"));
        v.maneuver(nearManeuver(1, 1, 0, 0, -90, null, 0, "Road W"));
        v.routeGuidance(rgAt(1, 30));
        check("review 1: near at 30 m first", "tbt 13 16 90.0 30.0 0 0 0 10 0".equals(last("tbt ")));
        v.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("30"), 13, U16, i(1)));
        check("review 1: the list moves on to the next turn without its distance: far (no flash of a near turn with the old distance)",
                "tbt 70 0 0.0 30.0 0 0 0 0 -1".equals(last("tbt ")) && "road []".equals(last("road ")));
        v.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(600), 11, STR, s("600"), 13, U16, i(1)));
        v.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 10, U32, i(300), 11, STR, s("300"), 13, U16, i(1)));
        check("review 1: then its own distances: near at 300 m (first 600 m, D 350 m, bar 80, left 270)",
                "tbt 13 4096 270.0 300.0 0 0 0 80 0".equals(last("tbt ")));
        c.calls.clear();
        // 리뷰 3: 안내가 끝나면 옛 경로의 차로 수를 버린다(진단 줄용). 소유자 결정 11-09부터 차로와 상관없이 D 안이면 곧 회전
        v.routeGuidance(rgAt(0, 0));
        v.maneuver(nearManeuver(0, 2, 0, 0, 90, null, 0, "Road N"));
        v.routeGuidance(rgAt(1, 200));
        check("review 3: a new route after the end: old lane counts forgotten, near inside D without lanes (owner 11-09)",
                "tbt 13 16 90.0 200.0 0 0 0 50 0".equals(last("tbt ")) && v.laneCounts.isEmpty());
        v.lanes(laneMsg(0, 2));
        v.routeGuidance(rgAt(1, 190));
        check("review 3: its lanes come: near", "tbt 13 16 90.0 190.0 0 0 0 50 0".equals(last("tbt ")));
        c.calls.clear();

        // 목적지: 차로가 있어도 막대 무효 그대로
        CpTurns e = new CpTurns(new android.content.Context(), c, null);
        e.clock = 8300000L;
        e.lanes(laneMsg(0, 2));
        e.maneuver(nearManeuver(0, 12, 0, 0, 0, null, 0, "Home"));
        e.routeGuidance(rgAt(1, 100));
        check("1.7.2 M-4: destination keeps the bar invalid (no near layout)", "tbt 112 0 0.0 100.0 0 0 0 0 -1".equals(last("tbt ")));
        c.calls.clear();

        // 탐색 중(상태 3) 9, 재탐색(상태 5) 10, 벗어나면 보통 회전
        CpTurns q = new CpTurns(new android.content.Context(), c, null);
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
        // 280m는 D 안: 곧 회전(소유자 결정 11-09, D 350m 고정, 막대 80)
        expect("1.7.2 route found (state 1): back to the turn (near inside D), the HUD list sent again",
                "road [Road Q]", "tbt 13 16 90.0 280.0 0 0 0 80 0", "hud f201430000" + utf16("Road Q"), "2c 0 0 280 0 0 65535 0 0 65535");
        q.routeGuidance(rgAt(4, 280));
        check("1.7.2: state 4 (locating) is not a route search: the turn as usual", "tbt 13 16 90.0 280.0 0 0 0 80 0".equals(last("tbt ")));
        c.calls.clear();
        q.routeGuidance(rgAt(3, 0));
        q.routeGuidance(rgAt(0, 0));
        check("1.7.2 route search then no route (state 0): cleared with the stock no-guidance values",
                c.calls.contains(CLEAR_TBT) && c.calls.contains(CLEAR_ROAD));
        c.calls.clear();

        // HUD 3칸(M-2): 셋째 칸은 그다음 회전
        CpTurns w = new CpTurns(new android.content.Context(), c, null);
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
        k.clock += CpTurns.REFRESH_MAX_MS;
        k.refresh();
        expect("5 minutes without the phone: nothing resent");
        check("5 minutes without the phone: the refresh ends", !k.active());

        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("80")));
        c.calls.clear();
        k.disconnected();
        c.calls.clear();
        check("iAP2 session ended: the refresh ends", !k.active());

        check("fallback icons: left-hand traffic 90 left 70, 75 right 67, 30 slight right 66, keep left 71, unknown 8, roundabout -100 105",
                CpTurns.icon(1, 1, 0, -90, 1) == 70 && CpTurns.icon(2, 0, 0, 75, 1) == 67 && CpTurns.icon(2, 0, 0, 30, 1) == 66
                        && CpTurns.fallbackIcon(13, 0, 0, 0) == 71 && CpTurns.fallbackIcon(99, 0, 0, 0) == CpTurns.NO_ICON
                        && CpTurns.fallbackIcon(99, 0, 1, -100) == 105 && CpTurns.fallbackIcon(4, 1, 0, 0) == 73
                        && CpTurns.fallbackIcon(12, 0, 0, 0) == 112);
        check("turns ahead: sharp/slight 47-50 get their arrows", CpTurns.aheadIcon(47, 0, 0, 0) == 69
                && CpTurns.aheadIcon(48, 0, 0, 0) == 68 && CpTurns.aheadIcon(49, 0, 0, 0) == 71 && CpTurns.aheadIcon(50, 0, 0, 0) == 66);

        check("icons: sharp left 69, slight right 66, roundabout exit 96/97, U-turn left-hand traffic 73",
                CpTurns.icon(1, 0, 0, -120, 1) == 69 && CpTurns.icon(2, 0, 0, 60, 1) == 66 && CpTurns.icon(2, 0, 1, 0, 1) == 96
                        && CpTurns.icon(31, 1, 1, 0, 1) == 96 && CpTurns.icon(4, 1, 0, 0, 1) == 73 && CpTurns.icon(47, 0, 0, -140, 1) == 69
                        && CpTurns.icon(99, 0, 0, 0, 1) == 0);
        check("lane angles: 0 straight, 90 right, -45 slight left, 180 back",
                CpTurns.laneIcon(0) == 1 && CpTurns.laneIcon(90) == 3 && CpTurns.laneIcon(-45) == 8 && CpTurns.laneIcon(180) == 5
                        && CpTurns.laneIcon(270) == 7);
        indiaTurns();
        korea230();
        korea172();
        near172();
        diag172();
        m15();
        phoneBar();
        beta2();
        System.exit(fail);
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
        CpTurns t = new CpTurns(ctx, c, null);
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

        // 경로 시작: 출발(종류 11)에 표시 거리 "0" -> 거리 무효(-3), HUD 거리는 보내지 않음
        c = new Cluster();
        CpTurns d = new CpTurns(new android.content.Context(), c, null);
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

}
