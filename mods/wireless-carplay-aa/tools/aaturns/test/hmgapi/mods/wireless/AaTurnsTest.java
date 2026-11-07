package hmgapi.mods.wireless;

import android.os.Handler;

import java.util.List;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

/**
 * Host test of AaTurns: a drive through the AA library's callbacks, checking every
 * cluster call against what the turn-by-turn build's GpmNaviTBTManager sends for the
 * same input (worked out from its code).
 */
public final class AaTurnsTest {
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

    static String utf16(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(String.format("%02x%02x", ch & 0xff, ch >> 8));
        String r = b.toString();
        while (r.endsWith("00")) r = r.substring(0, r.length() - 2);
        return r;
    }

    public static void main(String[] a) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        Car.unit = Car.DISTANCE_MILES;
        c = new Cluster();
        Handler h = new Handler();
        AaTurns t = new AaTurns(new android.content.Context(), c, h);
        final int RIGHT = 2, LEFT = 1, TURN = 4, MILES = 4, MILES_P1 = 5, METERS = 1, FEET = 6;

        t.nextTurn("Main St", RIGHT, TURN, -1, 0);
        expect("no route yet: road name only", "road [Main St]");
        t.status(1);
        t.nextTurn("Main St", RIGHT, TURN, -1, 0);
        expect("right turn: turn, HUD list, HUD distance", "road [Main St]", "tbt 67 65535 90.0 0.0 0 0 0 32 0",
                "hud f201430000" + utf16("Main St"), "2c 0 0 0 0 0 65535 0 0 65535");
        t.nextTurnDistance(320, 30, 200, MILES_P1);
        expect("0.2 mi: HUD list unchanged, not resent", "tbt 67 65535 90.0 0.2 0 0 2 32 0", "2c 2 2 0 0 0 65535 0 0 65535");
        t.nextTurnDistance(100, 10, 100, METERS);
        expect("100 m with miles set: feet", "tbt 67 65535 90.0 328.1 0 0 3 32 0", "2c 3 0 328 0 0 65535 0 0 65535");
        Car.unit = Car.DISTANCE_KM;
        t.nextTurnDistance(1500, 60, 932, MILES);
        expect("0.9 mi with km set: 1.5 km", "tbt 67 65535 90.0 1.5 0 0 1 32 0", "2c 1 5 1 0 0 65535 0 0 65535");
        Car.unit = Car.DISTANCE_MILES;

        String lanes = "02" + "01aabbcc" + "02000000";
        t.nextTurn(lanes, 0, 34, 0, 0);
        expect("lanes (count, a free byte, lane types; arrows from byte 19)", "lanes f102000102" + "0000000000000000000000000000" + "aabbcc");
        t.nextTurn(lanes, 0, 34, 0, 0);
        expect("same lanes: not resent");
        t.nextTurn("02zz", 0, 34, 0, 0);
        expect("bad lane string: nothing sent");

        t.nextTurn("Oak Ave", LEFT, 64, 270, TURN | 255 << 8);
        t.nextTurnDistance(800, 0, 500, MILES | 0x200);
        t.nextTurnDistance(90, 9, 295000, FEET);
        expect("distance between the turns ahead and their end: HUD list gets the next turn",
                "tbt 67 65535 90.0 295.0 0 0 3 32 0", "hud f202434600" + utf16("Main St"), "2c 3 0 295 0 0 65535 0 0 65535");
        t.nextTurn(null, 0, 67, 0, 1);
        expect("end of the turns ahead: the cluster's list", "list {1 70 270 [Oak Ave] [] " + (800 / 1609f) + " 2}");
        t.nextTurnDistance(80, 8, 262000, FEET);
        expect("next distance: HUD list already has it, not resent", "tbt 67 65535 90.0 262.0 0 0 3 32 0", "2c 3 0 262 0 0 65535 0 0 65535");

        t.nextTurnDistance(16093, 1800000000, 0, 0x100 | MILES_P1 | 0x800 | 1 << 12);
        expect("trip: 10.0 mi, arrival 8:00, point 1", "sub 10.0 0 2 8 0 1 1 0.0 -1 1");
        check("unit check scheduled", h.delayed != null);
        h.delayed.run();
        expect("tick, nothing changed: nothing sent");
        Car.unit = Car.DISTANCE_KM;
        h.delayed.run();
        expect("owner switched to km: resent", "sub 16.0 0 1 8 0 1 1 0.0 -1 1");
        Car.unit = Car.DISTANCE_MILES;

        t.status(2);
        expect("guidance ends: everything cleared", "dest 0 []", "tbt 0 0 0.0 0.0 -1 0 0 0 -1",
                "sub 0.0 -1 0 0 0 0 0 0.0 -1 0", "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2");
        check("unit check stopped", h.delayed == null);

        t.status(1);
        t.nextTurn("Home", 0, 32, 0, 2);
        expect("destination name", "dest 0 [Home]");
        t.nextTurn("Home", 0, 32, 0, 3);
        expect("same destination: not resent");
        check("destination point kept", t.estPoint == 3);

        AaTurns d = new AaTurns(new android.content.Context(), c, new Handler());
        d.status(1);
        d.nextTurn("Start Rd", 0, 1, 0, 0);
        expect("depart (first): road as destination, no distance yet", "dest 0 [Start Rd]", "tbt 2 65535 0.0 -1.0 0 0 0 254 -2");
        d.nextTurn("Circle", RIGHT, 13, 100, 0);
        expect("roundabout, exit at 100 degrees", "road [Circle]", "tbt 99 65535 90.0 -1.0 0 0 0 254 -2", "hud f201630000" + utf16("Circle"));
        d.nextTurn("Old Rd", 0, 11, 0, 0);
        expect("roundabout enter: not for the cluster");
        d.stop();
        d.end();
        expect("connection ends during a route: cleared", "dest 0 []", "tbt 0 0 0.0 0.0 -1 0 0 0 -1",
                "sub 0.0 -1 0 0 0 0 0 0.0 -1 0", "2c 0 0 65535 0 0 65535 0 0 65535", "lanes f1", "hud f2", "hud f2");
        d.nextTurn("S 3 0a0102", 0, 33, 0, 0);
        expect("navigation message: nothing to the cluster");
        check("... passed on to mods that asked (projection events)", "S 3 0a0102".equals(hmgapi.hook.ProjectionHooks.last));
        korea230();
        System.exit(fail);
    }

    /** Korea Projection 2.3.0: Korean stock GPM ramps, crossroad name on change, turn log, 2C, StockLog */
    static void korea230() {
        final int RIGHT = 2, LEFT = 1, UNSPEC = 3, ON_RAMP = 7, OFF_RAMP = 8, FORK = 9, TURN = 4, METERS = 1, KM_P1 = 3;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurns t = new AaTurns(new android.content.Context(), c, new Handler());
        final java.util.List<String> log = new java.util.ArrayList<String>();
        t.log = new AaTurns.Sink() {
            @Override
            public void line(String s) {
                log.add(s);
            }
        };
        t.clock = 5000000L;
        t.status(1);
        int[][] ramps = { { ON_RAMP, RIGHT, 161 }, { ON_RAMP, LEFT, 162 }, { ON_RAMP, UNSPEC, 160 },
            { OFF_RAMP, RIGHT, 164 }, { OFF_RAMP, LEFT, 165 }, { OFF_RAMP, UNSPEC, 163 }, { FORK, RIGHT, 173 }, { FORK, LEFT, 174 } };
        for (int[] r : ramps) {
            t.nextTurn("Ramp", r[1], r[0], -1, 0);
            check("2.3.0 ramp ev " + r[0] + " side " + r[1] + ": icon " + r[2] + " (stock GPM)", t.displayType == r[2]);
        }
        c.calls.clear();
        log.clear();

        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(800, 60, 800, METERS);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        int names = 0;
        for (String x : c.calls) if (x.startsWith("road ")) names++;
        check("2.3.0 crossroad name: sent once while it doesn't change", names == 1);
        c.calls.clear();
        t.clock += AaTurns.CROSSROAD_MS;
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        check("2.3.0 crossroad name: resent after 10 s", c.calls.size() > 0 && c.calls.get(0).equals("road [Road A]"));
        c.calls.clear();
        t.nextTurn("Road B", RIGHT, TURN, -1, 0);
        check("2.3.0 crossroad name: sent at once when it changes", c.calls.size() > 0 && c.calls.get(0).equals("road [Road B]"));
        c.calls.clear();
        t.stop();
        t.nextTurn("Road B", RIGHT, TURN, -1, 0);
        check("2.3.0 crossroad name: sent again after the car's navigation took over", c.calls.size() > 0 && c.calls.get(0).equals("road [Road B]"));
        c.calls.clear();

        check("2.3.0 turn log: one line, numbers only, not repeated for the same values (distance, name, resend)",
                log.size() == 1 && log.get(0).equals("AA: turn ev 4 side 2 angle -1 -> icon 67"));
        log.clear();
        int before = t.turnLogs;   // 8 ramps + 1 turn
        for (int k = 0; k < 400; k++) t.nextTurn("R", k % 2 == 0 ? RIGHT : LEFT, TURN, k, 0);
        check("2.3.0 turn log: at most 300 lines in all", before == 9 && log.size() == 300 - before && t.turnLogs == AaTurns.TURN_LOGS_MAX);
        boolean names2 = false;
        for (String x : log) if (x.contains("R ") || x.contains("Road")) names2 = true;
        check("2.3.0 turn log: no road names", !names2);
        c.calls.clear();

        t.nextTurnDistance(800, 60, 800, KM_P1);
        String hud = null;
        for (String x : c.calls) if (x.startsWith("2c ")) hud = x;
        check("2.3.0 AA 2C: 0.8 km in m, no 2nd/3rd point (65535)", "2c 0 0 800 0 0 65535 0 0 65535".equals(hud));
        c.calls.clear();
        t.status(2);
        hud = null;
        for (String x : c.calls) if (x.startsWith("2c ")) hud = x;
        check("2.3.0 AA guidance end: 2C 65535 for all three points", "2c 0 0 65535 0 0 65535 0 0 65535".equals(hud));
        Car.unit = Car.DISTANCE_MILES;

        // StockLog: the car navigation's values, debug only
        StockLog off = new StockLog(false);
        boolean none = off.line("update_tbt_info", new Object[] { 67, 0, 0f, 100f, 0, 0, 0, 0, 0 }, false) == null
                && off.line("update_lane_info", new Object[] { new byte[] { (byte) 0xf1, 2 } }, true) == null
                && off.line("update_tbt_sub_info_2c", new Object[] { 0, 0, 170, 0, 0, 170, 0, 0, 400 }, false) == null;
        check("2.3.0 stock observation: no lines with the debug log off", none);
        StockLog on = new StockLog(true);
        check("2.3.0 stock observation: icon", "stock navigation: tbt icon 67".equals(on.line("update_tbt_info", new Object[] { 67, 0, 0f, 100f, 0, 0, 0, 0, 0 }, false)));
        check("2.3.0 stock observation: same icon not logged again", on.line("update_tbt_info", new Object[] { 67, 0, 0f, 90f, 0, 0, 0, 0, 0 }, false) == null);
        byte[] lf = new byte[81];
        lf[0] = (byte) 0xf1;
        lf[1] = 2;
        lf[3] = 1;
        lf[19] = 9;
        check("2.3.0 stock observation: lane frame in hex, marked when a phone guides (skipped)",
                "stock navigation: lanes f102000100000000000000000000000000000009 (phone guiding, skipped)".equals(on.line("update_lane_info", new Object[] { lf }, true)));
        check("2.3.0 stock observation: 2C numbers",
                "stock navigation: 2c 0 0 170 0 0 170 0 0 400".equals(on.line("update_tbt_sub_info_2c", new Object[] { 0, 0, 170, 0, 0, 170, 0, 0, 400 }, false)));
        check("2.3.0 stock observation: other calls (names) never logged",
                on.line("update_tp_crossroad_name", new Object[] { "x" }, false) == null);
        int n = 3;
        for (int k = 0; k < 500; k++) if (on.line("update_tbt_info", new Object[] { k, 0, 0f, 0f, 0, 0, 0, 0, 0 }, false) != null) n++;
        check("2.3.0 stock observation: at most 300 lines", n == StockLog.MAX && on.lines == StockLog.MAX);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fail = 1;
    }
}
