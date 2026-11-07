package hmgapi.mods.wireless.dipo;

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
                "road [Test Road]", "tbt 67 0 0.0 100.0 0 0 0 0 0",
                "hud f201430000" + utf16("Test Road"), "2c 0 0 100 0 0 65535 0 0 65535");
        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(11)));
        t.routeGuidance(msg(0, U16, i(1)));
        expect("Google departure clears prior HUD turn and sends non-directional NO_INFO",
                "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2",
                "road [Test Road]", "tbt 1 0 0.0 100.0 0 0 0 0 0", "2c 0 0 100 0 0 65535 0 0 65535");
        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(2)));
        t.routeGuidance(msg(0, U16, i(1)));
        expect("Google right without angle reaches cluster and HUD after departure",
                "tbt 67 0 0.0 100.0 0 0 0 0 0",
                "hud f201430000" + utf16("Test Road"), "2c 0 0 100 0 0 65535 0 0 65535");
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("India route stop still clears HUD", "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2");
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
        check("2.3.0 guidance end: 2C 65535 for all three points (stock), was 0",
                only2c().equals("2c 0 0 65535 0 0 65535 0 0 65535") && CpTurns.HUD_NO_POINT == 65535);
        Car.unit = Car.DISTANCE_MILES;
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
        expect("partial update (distance only): same HUD list and crossroad name not resent",
                "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535", "sub 3.1 0 2 22 13 1 1 0.0 -1 0",
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
                "tbt 67 0 0.0 1.5 0 0 1 0 0", "2c 1 5 1 0 0 65535 0 0 65535", "sub 800.0 0 0 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] 800.0 0} {2 72 0 [Pine St] [] 2.0 1}");
        Car.unit = Car.DISTANCE_MILES;

        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 5, U32, i(250)));
        t.routeGuidance(msg(0, U16, i(1), 11, STR, s("0.1"), 12, ENUM, i(MILES)));
        expect("maneuver update without optional angle retains its explicit right-turn direction (Korea Projection: and the kept angle)",
                "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535", "sub 0.4 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");
        t.maneuver(maneuver(0, RIGHT, 0, 0, 90, "Main St", 250));

        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(100), 6, U64, l(0), 5, U64, l(0)));
        expect("trip under 161 m with miles: feet; no arrival time",
                "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535", "sub 328.0 0 3 -1 -1 -1 -1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(0), 6, U64, l(600)));
        Calendar in10 = Calendar.getInstance();
        in10.add(Calendar.SECOND, 600);
        expect("arrival from the time remaining (no distance)",
                "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535",
                "sub 0.0 -1 0 " + in10.get(Calendar.HOUR_OF_DAY) + " " + in10.get(Calendar.MINUTE) + " 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 6, U64, l(0), 5, U64, l(600000000L)));
        expect("arrival as seconds since 2001 (Apple's epoch): 2020-01-06 10:40",
                "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 65535 0 0 65535", "sub 0.0 -1 0 10 40 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("Waze"), 5, U64, l(0)));
        check("source change: route guidance restarted", restarted.await(2, TimeUnit.SECONDS));
        expect("source change (Maps to Waze): HUD cleared; the reset state is 0 until the phone resends it, so cleared again",
                "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2", "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2");

        t.maneuver(maneuver(5, ARRIVE, 0, 0, 0, "Home Rd", 100));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(6), 4, STR, s("Home"), 11, STR, s("300"), 12, ENUM, i(FEET), 13, U16, i(5)));
        expect("state 6 with destination, arrival maneuver in 300 ft",
                "dest 0 [Home]", "road [Home Rd]", "tbt 112 0 0.0 300.0 0 0 3 0 0", "hud f201700000" + utf16("Home Rd"),
                "2c 3 0 300 0 0 65535 0 0 65535");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(2)));
        expect("arrived (state 2): icon 3, no HUD list for it", "tbt 3 0 0.0 300.0 0 0 3 0 0", "2c 3 0 300 0 0 65535 0 0 65535");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("state 0: HUD cleared", "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1)));
        t.stop();
        c.calls.clear();
        t.stop();
        expect("route guidance stopped (car took turn-by-turn back): HUD cleared", "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2");

        t.disconnected();
        expect("iAP2 session ended: HUD cleared", "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2");
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("0.5"), 12, ENUM, i(MILES), 13, U16, i(5), 19, STR, s("Maps")));
        expect("after the session ended: maneuvers gone, a new source isn't a change", "");
        c.calls.clear();

        t.maneuver(msg(0, U16, i(7), 1, U16, i(0), 3, ENUM, i(LEFT), 4, STR, s("Comp 7 Rd"), 8, ENUM, i(0), 9, ENUM, i(0), 11, U8, i(-60)));
        t.routeGuidance(msg(0, U16, i(7), 1, ENUM, i(1), 11, STR, s("0.3"), 12, ENUM, i(MILES), 13, U16, i(0)));
        expect("a second component keeps its own state", "road [Comp 7 Rd]", "tbt 71 0 0.0 0.3 0 0 2 0 0",
                "hud f201470000" + utf16("Comp 7 Rd"), "2c 2 3 0 0 0 65535 0 0 65535");

        t.maneuver(msg(1, U16, i(0), 3, ENUM, i(STRAIGHT), 4, STR, s("Any Rd"), 8, ENUM, i(0), 9, ENUM, i(0)));
        t.routeGuidance(msg(1, ENUM, i(1), 11, STR, s("0.4"), 13, U16, i(0)));
        expect("no component ids: every known component gets it",
                "road [Any Rd]", "tbt 65 0 0.0 0.4 0 0 2 0 0", "hud f201410000" + utf16("Any Rd"), "2c 2 4 0 0 0 65535 0 0 65535",
                "tbt 65 0 0.0 0.4 0 0 2 0 0", "2c 2 4 0 0 0 65535 0 0 65535");

        t.routeGuidance(msg(0, U16, i(0), 1, ENUM, i(1), 11, STR, s("0.4"), 13, U16, i(0)));
        expect("component 0's first update: DiPO starts it empty (state 0), so the HUD is cleared",
                "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2");

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
                "road [A]", "tbt 70 0 0.0 300.0 0 0 0 0 0", "hud f201460000" + utf16("A"), "2c 0 0 300 0 0 65535 0 0 65535");
        check("guidance on: the refresh is started once", woke[0] == 1 && k.active());

        k.refresh();
        expect("refresh right after a send: nothing");
        k.clock += 1000L;
        k.refresh();
        expect("refresh after 1 s without the phone: the turn again (HUD list and crossroad unchanged, not resent)",
                "tbt 70 0 0.0 300.0 0 0 0 0 0", "2c 0 0 300 0 0 65535 0 0 65535");
        k.clock += 1000L;
        k.refresh();
        expect("refresh 2 s after the HUD list: the HUD list resent too",
                "hud f201460000" + utf16("A"), "tbt 70 0 0.0 300.0 0 0 0 0 0", "2c 0 0 300 0 0 65535 0 0 65535");
        k.clock += 10000L;
        k.refresh();
        expect("refresh 10 s after the crossroad name: the name resent",
                "hud f201460000" + utf16("A"), "road [A]", "tbt 70 0 0.0 300.0 0 0 0 0 0", "2c 0 0 300 0 0 65535 0 0 65535");
        check("refresh started only once", woke[0] == 1);

        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(99)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("200")));
        expect("a maneuver with no icon at all: blank icon 8, the HUD list emptied, the distance still sent",
                "tbt 8 0 0.0 200.0 0 0 0 0 0", "hud f2", "2c 0 0 200 0 0 65535 0 0 65535");
        k.clock += 2000L;
        k.refresh();
        expect("the empty HUD list is resent like any other", "hud f2", "tbt 8 0 0.0 200.0 0 0 0 0 0", "2c 0 0 200 0 0 65535 0 0 65535");

        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(RIGHT), 11, U8, i(75)));
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 5, U32, i(150)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("150")));
        expect("right turn at 75 degrees (not in the table): the 67 band; the angle kept through a distance-only update",
                "tbt 67 0 0.0 150.0 0 0 0 0 0", "hud f201430000" + utf16("A"), "2c 0 0 150 0 0 65535 0 0 65535");
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("100")));
        expect("a new maneuver type without an angle: the old angle isn't carried over (left by type: 70)",
                "tbt 70 0 0.0 100.0 0 0 0 0 0", "hud f201460000" + utf16("A"), "2c 0 0 100 0 0 65535 0 0 65535");
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT), 11, U8, i(-130)));
        k.maneuver(msg(0, U16, i(1), 1, U16, i(0), 3, ENUM, i(LEFT), 5, U32, i(90)));
        k.routeGuidance(msg(0, U16, i(1), 11, STR, s("90")));
        expect("the same type named again without an angle (a new maneuver at a reused index): the sharp angle isn't kept",
                "tbt 70 0 0.0 90.0 0 0 0 0 0", "2c 0 0 90 0 0 65535 0 0 65535");

        k.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("state 0: HUD cleared", "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2");
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
        System.exit(fail);
    }
}
