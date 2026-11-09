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
    /** 1.7.2(M-14): 순정 무안내 회전 값과 2C 지우기 값(카플레이 M-2와 같음) */
    static final String CLEAR_TBT = "tbt 8 0 0.0 0.0 -3 0 15 0 -1", CLEAR_2C = "2c 15 15 65535 15 15 65535 15 15 65535";
    /** 리뷰 반영: 남은 거리·시간 지우기도 순정 무효 값(거리 무효 -3, 단위 15, 시간 종류·형식 3, 도착 지점 15) */
    static final String CLEAR_SUB = "sub 0.0 -3 15 0 0 3 3 0.0 -1 15";
    /** 2차 리뷰 4: AA 지우기도 도로(교차로) 이름을 지운다(카플레이와 같음) */
    static final String CLEAR_ROAD = "road []";

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
        expect("no route yet: nothing (1.7.2: the road name only goes out during a route, and then only for a near turn)");
        t.status(1);
        t.nextTurn("Main St", RIGHT, TURN, -1, 0);
        expect("right turn: turn, HUD list, HUD distance (1.7.2: far, so an empty road name)", "road []", "tbt 67 0 0.0 0.0 0 0 0 0 -1",
                "hud f201430000" + utf16("Main St"), "2c 0 0 0 0 0 65535 0 0 65535");
        t.nextTurnDistance(320, 30, 200, MILES_P1);
        // 소유자 결정 2026-11-09 "D 안의 모든 회전": 320m는 D(350m) 안이라 차로가 없어도 곧 회전(13, 나가는 길 비트 4, 90도, 막대 90, 도로 이름).
        // 시험판 3: D는 순정처럼 고정(처음 거리로 줄이지 않음), 막대 최대 90
        expect("0.2 mi: HUD list unchanged, not resent (1.7.2 owner 11-09: 320 m is inside D, near without lanes)",
                "road [Main St]", "tbt 13 16 90.0 0.2 0 0 2 90 0", "2c 2 2 0 0 0 65535 0 0 65535");
        t.nextTurnDistance(100, 10, 100, METERS);
        expect("100 m with miles set: feet (near, D 350 m, bar 20)", "tbt 13 16 90.0 328.1 0 0 3 20 0", "2c 3 0 328 0 0 65535 0 0 65535");
        Car.unit = Car.DISTANCE_KM;
        t.nextTurnDistance(1500, 60, 932, MILES);
        expect("0.9 mi with km set: 1.5 km (a new, longer distance: far again, the name emptied)", "road []", "tbt 67 0 0.0 1.5 0 0 1 0 -1", "2c 1 5 1 0 0 65535 0 0 65535");
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
        // 1.7.2(M-12): 차로가 왔고 90m <= D(350m, 이 회전의 처음 거리 1500m가 더 김): 곧 회전 13, 오른쪽 90도(칸 12), 나가는 길 비트 4,
        // 막대 10 x floor(90 x 10 / 350) = 20, 도로 이름
        expect("distance between the turns ahead and their end: HUD list gets the next turn (1.7.2: lanes and 90 m: near layout)",
                "road [Main St]", "tbt 13 16 90.0 295.0 0 0 3 20 0", "hud f202434600" + utf16("Main St"), "2c 3 0 295 0 0 65535 0 0 65535");
        t.nextTurn(null, 0, 67, 0, 1);
        expect("end of the turns ahead: the cluster's list", "list {1 70 270 [Oak Ave] [] " + (800 / 1609f) + " 2}");
        t.nextTurnDistance(80, 8, 262000, FEET);
        expect("next distance: HUD list already has it, not resent", "tbt 13 16 90.0 262.0 0 0 3 20 0", "2c 3 0 262 0 0 65535 0 0 65535");

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
        expect("guidance ends: everything cleared", "dest 0 []", CLEAR_TBT,
                CLEAR_SUB, CLEAR_ROAD, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
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
        expect("depart (first): road as destination, no distance yet (1.7.2: far, empty road name)", "dest 0 [Start Rd]", "road []", "tbt 2 0 0.0 -1.0 0 0 0 254 -2");
        d.nextTurn("Circle", RIGHT, 13, 100, 0);
        expect("roundabout, exit at 100 degrees (1.7.2: far, the empty name already sent)", "tbt 99 0 0.0 -1.0 0 0 0 254 -2", "hud f201630000" + utf16("Circle"));
        d.nextTurn("Old Rd", 0, 11, 0, 0);
        expect("roundabout enter: not for the cluster");
        d.stop();
        d.end();
        expect("connection ends during a route: cleared (1.7.2: once, by the focus change before it)", "dest 0 []", CLEAR_TBT,
                CLEAR_SUB, CLEAR_ROAD, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        d.nextTurn("S 3 0a0102", 0, 33, 0, 0);
        expect("navigation message: nothing to the cluster");
        check("... passed on to mods that asked (projection events)", "S 3 0a0102".equals(hmgapi.hook.ProjectionHooks.last));
        korea230();
        korea172();
        near172();
        reviewFixes();
        stockReplay();
        m15();
        phoneBar();
        check("2nd review 6: a MAC is masked completely, other text only as a length, null as null",
                "xx:xx:xx:xx:xx:xx".equals(Mac.mask("A1:B2:C3:D4:E5:F6")) && !Mac.mask("A1:B2:C3:D4:E5:F6").contains("F6")
                        && "(6 chars)".equals(Mac.mask("iPhone")) && "null".equals(Mac.mask(null)));
        System.exit(fail);
    }

    /** HUD 프레임(16진)에서 이름 칸(5바이트 뒤)을 UTF-16LE로 읽는다(스텁이 뺀 끝 0바이트는 되살림) */
    static String hudFrameName(String line) throws Exception {
        String h = line.substring(4);
        if ((h.length() / 2 - 5) % 2 != 0) h += "00";
        byte[] b = new byte[h.length() / 2];
        for (int k = 0; k < b.length; k++) b[k] = (byte) Integer.parseInt(h.substring(2 * k, 2 * k + 2), 16);
        return new String(b, 5, b.length - 5, "UTF-16LE");
    }

    static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < n; k++) b.append(s);
        return b.toString();
    }

    /** 1.7.2(M-15): HUD 목록 이름을 글자 경계로 자름(한글 경계, 이모지, ASCII, 빈 이름) */
    static void m15() {
        String emoji = new String(Character.toChars(0x1F600));
        String h40 = repeat("우", 40);
        check("1.7.2 M-15: HUD name limit stays 78 bytes (stock name field up to 0x50)", AaTurns.HUD_NAME_MAX == 78);
        check("1.7.2 M-15: 40 Hangul -> 39 (78 bytes), the last whole", AaTurns.hudName(h40, 78).equals(repeat("우", 39)));
        check("1.7.2 M-15: 39 Hangul fit exactly, unchanged", AaTurns.hudName(repeat("우", 39), 78).equals(repeat("우", 39)));
        check("1.7.2 M-15: \"우회전\" short, unchanged", AaTurns.hudName("우회전", 78).equals("우회전"));
        check("1.7.2 M-15: emoji across the 78-byte edge is dropped whole, not split (38 a + emoji + b -> 38 a)",
                AaTurns.hudName(repeat("a", 38) + emoji + "b", 78).equals(repeat("a", 38)));
        check("1.7.2 M-15: emoji ending exactly at 78 bytes is kept (37 a + emoji)",
                AaTurns.hudName(repeat("a", 37) + emoji, 78).equals(repeat("a", 37) + emoji));
        check("1.7.2 M-15: ASCII short unchanged, long cut to 39", AaTurns.hudName("Main St", 78).equals("Main St")
                && AaTurns.hudName(repeat("x", 50), 78).equals(repeat("x", 39)));
        check("1.7.2 M-15: empty stays empty", AaTurns.hudName("", 78).equals(""));
        boolean whole = true;
        for (int k = 0; k < 120; k++) {
            String s = repeat("a", k % 3) + repeat(emoji + "가", k % 45);
            String r = AaTurns.hudName(s, 78);
            int n = r.length();
            if (n * 2 > 78 || !s.startsWith(r) || (n > 0 && Character.isHighSurrogate(r.charAt(n - 1)))
                    || (n < s.length() && n * 2 + (Character.isHighSurrogate(s.charAt(n)) ? 4 : 2) <= 78)) whole = false;
        }
        check("1.7.2 M-15: mixed emoji/Hangul: never over 78 bytes, never half a pair, never shorter than needed", whole);

        // 프레임 전체: 긴 한글 이름은 83바이트(머리 5 + 이름 78)이고, 이름은 글자 39개 그대로
        Cluster c2 = new Cluster();
        AaTurns t = new AaTurns(new android.content.Context(), c2, new Handler());
        t.status(1);
        t.nextTurn(h40, 2, 4, -1, 0);
        String hud = null;
        for (String x : c2.calls) if (x.startsWith("hud ")) hud = x;
        boolean ok = false;
        try {
            ok = hud != null && (hud.length() - 4) / 2 == 5 + 78 && hudFrameName(hud).equals(repeat("우", 39));
        } catch (Exception e) {
            ok = false;
        }
        check("1.7.2 M-15: AA HUD frame with a 40-Hangul road: 83 bytes, 39 whole Hangul", ok);
        c2.calls.clear();
        t.nextTurn(repeat("a", 38) + emoji, 2, 4, -1, 0);
        hud = null;
        for (String x : c2.calls) if (x.startsWith("hud ")) hud = x;
        try {
            ok = hud != null && hudFrameName(hud).equals(repeat("a", 38));
        } catch (Exception e) {
            ok = false;
        }
        check("1.7.2 M-15: AA HUD frame: an emoji at the edge is left out whole", ok);
    }

    /** Korea Projection 2.3.0: Korean stock GPM ramps, crossroad name on change, turn log, 2C, StockLog */
    static String last(String prefix) {
        String r = null;
        for (String x : c.calls) if (x.startsWith(prefix)) r = x;
        return r;
    }

    /** 1.7.2(M-12·M-2, 소유자 결정 2026-11-09): AA 곧 회전 화면, 재탐색 10, HUD {F2,00}·3칸 */
    static void near172() {
        final int RIGHT = 2, LEFT = 1, TURN = 4, SLIGHT = 3, SHARP = 5, U_TURN = 6, OFF_RAMP = 8, FORK = 9, NAME = 2, DEPART = 1, DEST = 19,
                RB_EXIT = 13, RB_EXIT_ONLY = 12, METERS = 1;
        check("1.7.2 M-12 display: turns 13 (slight/turn/sharp right 6/12/18, left 42/36/30), U-turn 31 (36), exits 17/18, fork 25",
                java.util.Arrays.toString(AaTurns.nearDisplay(TURN, RIGHT, 67, 90)).equals("[13, 4, 12]")
                        && java.util.Arrays.toString(AaTurns.nearDisplay(TURN, LEFT, 70, 270)).equals("[13, 12, 36]")
                        && java.util.Arrays.toString(AaTurns.nearDisplay(SLIGHT, RIGHT, 66, 30)).equals("[13, 2, 6]")
                        && java.util.Arrays.toString(AaTurns.nearDisplay(SHARP, LEFT, 69, 225)).equals("[13, 10, 30]")
                        && java.util.Arrays.toString(AaTurns.nearDisplay(U_TURN, LEFT, 72, 270)).equals("[31, -1, 36]")
                        && java.util.Arrays.toString(AaTurns.nearDisplay(OFF_RAMP, RIGHT, 164, 30)).equals("[17, -1, 12]")
                        && java.util.Arrays.toString(AaTurns.nearDisplay(OFF_RAMP, LEFT, 165, 330)).equals("[18, -1, 36]")
                        && java.util.Arrays.toString(AaTurns.nearDisplay(FORK, RIGHT, 173, 0)).equals("[25, -1, 6]")
                        && java.util.Arrays.toString(AaTurns.nearDisplay(RB_EXIT, 0, 99, 90)).equals("[99, 4, 12]")
                        && java.util.Arrays.toString(AaTurns.nearDisplay(RB_EXIT, 0, 102, 180)).equals("[102, 8, 27]"));
        check("1.7.2 M-12 display: unknown side, name change, departure, destination, unknown roundabout: no near layout",
                AaTurns.nearDisplay(TURN, 3, 64, 0) == null && AaTurns.nearDisplay(NAME, 0, 65, 0) == null
                        && AaTurns.nearDisplay(DEPART, 0, 2, 0) == null && AaTurns.nearDisplay(DEST, 0, 3, 0) == null
                        && AaTurns.nearDisplay(RB_EXIT, 0, 64, 0) == null && AaTurns.nearDisplay(RB_EXIT_ONLY, RIGHT, 32, 90) == null);
        check("1.7.2 M-12 bar (as CarPlay): bar(300, 350) 80, bar(1, 350) 10", AaTurns.bar(300, 350) == 80 && AaTurns.bar(1, 350) == 10);

        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurns t = new AaTurns(new android.content.Context(), c, new Handler());
        t.clock = 9000000L;
        t.status(1);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(500, 30, 500, METERS);
        check("1.7.2 M-12: 500 m (beyond D 350 m): far (bar invalid, empty road name)",
                "tbt 67 0 0.0 500.0 0 0 0 0 -1".equals(last("tbt ")) && "road []".equals(last("road ")));
        t.nextTurnDistance(200, 20, 200, METERS);
        check("1.7.2 M-12 (owner 11-09): no lanes from AA, 200 m inside D: near (13, exit bit 4, 90 degrees, bar 50), the road name",
                "tbt 13 16 90.0 200.0 0 0 0 50 0".equals(last("tbt ")) && "road [Road A]".equals(last("road ")));
        c.calls.clear();
        t.nextTurn("01" + "01aabbcc", 0, 34, 0, 0);
        t.nextTurnDistance(150, 15, 150, METERS);
        expect("1.7.2 M-12: lanes and 150 m (first distance 500 m, D 350 m): the lane frame, then 13, exit bit 4, 90 degrees, bar 40",
                "lanes f1010001000000000000000000000000000000aabbcc", "tbt 13 16 90.0 150.0 0 0 0 40 0",
                "2c 0 0 150 0 0 65535 0 0 65535");
        t.nextTurn("Road Z", LEFT, TURN, -1, 0);
        check("review 1: the next turn arrives with the old 150 m still known: not near until its own distance comes (no flash of the old one)",
                "tbt 70 0 0.0 150.0 0 0 0 0 -1".equals(last("tbt ")) && "road []".equals(last("road ")));
        t.nextTurnDistance(600, 40, 600, METERS);
        t.nextTurnDistance(300, 20, 300, METERS);
        check("review 1: then its own distances: near at 300 m (first 600 m, D 350 m, bar 80)", "tbt 13 4096 270.0 300.0 0 0 0 80 0".equals(last("tbt ")));
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(150, 15, 150, METERS);
        c.calls.clear();
        t.nextTurn("00", 0, 34, 0, 0);
        t.nextTurnDistance(140, 14, 140, METERS);
        check("1.7.2 M-12 (owner 11-09): AA says no lanes now: still near inside D (lanes don't matter)",
                "tbt 13 16 90.0 140.0 0 0 0 40 0".equals(last("tbt ")) && !"road []".equals(last("road ")));
        c.calls.clear();
        // 소유자 결정 11-09: 목적지는 D 안이어도 멀리 있음(막대 무효, 빈 이름)
        t.nextTurn("Home", RIGHT, DEST, -1, 0);
        t.nextTurnDistance(100, 10, 100, METERS);
        check("1.7.2 (owner 11-09): AA destination at 100 m: far (bar invalid, empty road name)",
                last("tbt ") != null && last("tbt ").endsWith(" 100.0 0 0 0 0 -1") && last("tbt ").startsWith("tbt ") && !last("tbt ").startsWith("tbt 13 ")
                        && "road []".equals(last("road ")));
        c.calls.clear();
        // 출구 900m: 처음 거리 1500m, D 1050m -> 17, 막대 80
        t.nextTurn("01" + "01aabbcc", 0, 34, 0, 0);
        t.nextTurn("Exit 5", RIGHT, OFF_RAMP, -1, 0);
        t.nextTurnDistance(1500, 60, 1500, METERS);
        check("1.7.2 M-12: exit at 1.5 km: far", "tbt 164 0 0.0 1.5 0 0 1 0 -1".equals(last("tbt ")));
        t.nextTurnDistance(900, 50, 900, METERS);
        check("1.7.2 M-12: exit at 900 m (D 1050 m): 17, 90 degrees, no side bits, bar 80",
                "tbt 17 0 90.0 900.0 0 0 0 80 0".equals(last("tbt ")) && "road [Exit 5]".equals(last("road ")));
        c.calls.clear();
        c.hudFrames.clear();

        // 재탐색(상태 3) 10, 다시 안내(1)면 보통 회전으로
        t.status(3);
        expect("1.7.2 AA rerouting (status 3): display 10 with direction, side and distance invalid, the name emptied, 2C and the HUD list cleared",
                "tbt 10 0 0.0 0.0 -3 0 15 0 0", "road []", CLEAR_2C, "hud f2");
        check("1.7.2 AA rerouting: the HUD's empty list is {F2,00}", c.hudFrames.size() == 1 && java.util.Arrays.equals(c.hudFrames.get(0), new byte[] { (byte) 0xF2, 0 }));
        t.status(3);
        expect("1.7.2 AA rerouting: repeated status 3: nothing again");
        t.nextTurn("Exit 5", RIGHT, OFF_RAMP, -1, 0);
        t.nextTurnDistance(880, 50, 880, METERS);
        t.nextTurn("01" + "01aabbcc", 0, 34, 0, 0);
        expect("1.7.2 AA rerouting: phone turns meanwhile resend display 10 only (review 7: AA has no refresh); lanes kept back",
                "tbt 10 0 0.0 0.0 -3 0 15 0 0", "tbt 10 0 0.0 0.0 -3 0 15 0 0");
        t.status(1);
        // 새 경로: D는 고정 1050m(시험판 3, 순정 차 로그), 880m는 막대 80.
        // 차로는 재탐색 중에 다시 온 것(01)만 센다
        check("1.7.2 AA rerouting over (status 1): back to the turn at once (new route: D stays 1050 m, bar 80), HUD list sent again",
                "tbt 17 0 90.0 880.0 0 0 0 80 0".equals(last("tbt ")) && last("hud ") != null);
        boolean nine = false;
        for (String x : c.calls) if (x.startsWith("tbt 9 ")) nine = true;
        check("1.7.2 AA: no route-calculating state in AA: display 9 never sent", !nine);
        c.calls.clear();
        // 리뷰(소유자 결정 11-09 뒤): 재탐색 중 새 거리가 오지 않으면 옛 경로 거리로 큰 그림을 띄우지 않는다(멀리 있음)
        t.status(3);
        t.status(1);
        check("1.7.2 AA (owner 11-09 review): rerouting over without a new distance: far, not near from the old route's 880 m",
                "tbt 164 0 0.0 880.0 0 0 0 0 -1".equals(last("tbt ")));
        t.nextTurnDistance(700, 40, 700, METERS);
        check("1.7.2 AA: then its new distance: near again (D 1050 m, bar 60)", "tbt 17 0 90.0 700.0 0 0 0 60 0".equals(last("tbt ")));
        c.calls.clear();
        t.status(3);
        c.calls.clear();
        t.status(2);
        check("1.7.2 AA rerouting then the route ends: the stock no-guidance clear", c.calls.contains(CLEAR_TBT) && c.calls.contains(CLEAR_ROAD));
        c.calls.clear();

        // 포커스가 순정일 때 재탐색: 보내지 않고, 폰으로 돌아오면 10
        AaTurns f = new AaTurns(new android.content.Context(), c, new Handler());
        f.status(1);
        f.nextTurn("Road F", RIGHT, TURN, -1, 0);
        f.nextTurnDistance(600, 30, 600, METERS);
        f.stop();
        c.calls.clear();
        f.status(3);
        expect("1.7.2 AA rerouting while the car's navigation has the focus: nothing sent");
        f.focusPhone();
        check("1.7.2 AA rerouting: focus back on the phone: display 10", "tbt 10 0 0.0 0.0 -3 0 15 0 0".equals(last("tbt ")));
        c.calls.clear();

        // HUD 3칸(M-2)
        AaTurns w = new AaTurns(new android.content.Context(), c, new Handler());
        w.status(1);
        w.nextTurn("Oak", LEFT, 64, 270, TURN | 255 << 8);
        w.nextTurnDistance(800, 0, 800, METERS | 0x200);
        w.nextTurn("Pine", LEFT, 65, 0, U_TURN | 255 << 8);
        w.nextTurnDistance(1200, 0, 1200, METERS | 0x200 | 1 << 10);
        w.nextTurn("Main", RIGHT, TURN, -1, 0);
        w.nextTurnDistance(300, 30, 300, METERS);
        check("1.7.2 M-2: AA HUD list with 3 icons (count 3: 67, next left 70, then U-turn 72)",
                ("hud f203434648" + utf16("Main")).equals(last("hud ")));
        c.calls.clear();
        w.status(2);
        int nf = c.hudFrames.size();
        byte[] empty = { (byte) 0xF2, 0 };
        boolean twoByte = nf >= 2 && java.util.Arrays.equals(c.hudFrames.get(nf - 1), empty) && java.util.Arrays.equals(c.hudFrames.get(nf - 2), empty);
        check("1.7.2 M-2: AA route end: the HUD's empty list is {F2,00}, twice", twoByte);
        c.calls.clear();
        Car.unit = Car.DISTANCE_MILES;
    }

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

        // 1.7.2(M-12): 도로 이름은 곧 회전일 때만 나가므로, 차로를 주고 D 안(200m)에서 시험한다
        t.nextTurn("01" + "01aabbcc", 0, 34, 0, 0);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(200, 60, 200, METERS);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        int names = 0;
        for (String x : c.calls) if (x.equals("road [Road A]")) names++;
        check("2.3.0 crossroad name: sent once while it doesn't change", names == 1);
        c.calls.clear();
        t.clock += AaTurns.CROSSROAD_MS;
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        check("2.3.0 crossroad name: resent after 10 s", c.calls.size() > 0 && c.calls.get(0).equals("road [Road A]"));
        c.calls.clear();
        t.nextTurn("Road B", RIGHT, TURN, -1, 0);
        // 1.7.2: 다른 회전이면 그 회전의 거리가 올 때까지 멀리 있음(빈 이름), 거리가 오면 바로 새 이름(리뷰 1)
        t.nextTurnDistance(200, 60, 200, METERS);
        check("2.3.0 crossroad name: sent at once when it changes", c.calls.contains("road [Road B]"));
        c.calls.clear();
        t.stop();
        expect("1.7.2 M-14: the car's navigation took over during a route: cluster cleared with the stock no-guidance values",
                "dest 0 []", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        t.focusPhone();
        t.nextTurn("01" + "01aabbcc", 0, 34, 0, 0);
        t.nextTurn("Road B", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(150, 60, 150, METERS);
        check("2.3.0 crossroad name: sent again after the car's navigation took over (and the focus came back)", c.calls.contains("road [Road B]"));
        c.calls.clear();

        // 1.7.2: 줄은 곧 회전 여부·D가 바뀔 때도 하나(숫자만): Road A 멀리·곧 회전, Road B 멀리(거리 오기 전)·곧 회전,
        // 순정에서 돌아온 뒤 Road B 멀리·곧 회전(D는 늘 350m)
        check("2.3.0 turn log: numbers only, not repeated for the same values (distance, name, resend)",
                log.size() == 6 && log.get(5).equals("AA: turn ev 4 side 2 angle -1 -> icon 67 near display 13 bar 40 d 350") && log.get(0).equals("AA: turn ev 4 side 2 angle -1 -> icon 67 far")
                        && log.get(1).equals("AA: turn ev 4 side 2 angle -1 -> icon 67 near display 13 bar 50 d 350"));
        log.clear();
        int before = t.turnLogs;   // 8 ramps + 6 turn lines
        for (int k = 0; k < 400; k++) t.nextTurn("R", k % 2 == 0 ? RIGHT : LEFT, TURN, k, 0);
        check("2.3.0 turn log: at most 300 lines in all", before == 14 && log.size() == 300 - before && t.turnLogs == AaTurns.TURN_LOGS_MAX);
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
        check("2.3.0 AA guidance end: 2C 65535 for all three points (1.7.2: unit and tenths 15)", CLEAR_2C.equals(hud));
        Car.unit = Car.DISTANCE_MILES;

        // StockLog: the car navigation's values, debug only
        StockLog off = new StockLog(false);
        boolean none = off.line("update_tbt_info", new Object[] { 67, 0, 0f, 100f, 0, 0, 0, 0, 0 }, false) == null
                && off.line("update_lane_info", new Object[] { new byte[] { (byte) 0xf1, 2 } }, true) == null
                && off.line("update_tbt_sub_info_2c", new Object[] { 0, 0, 170, 0, 0, 170, 0, 0, 400 }, false) == null;
        check("2.3.0 stock observation: no lines with the debug log off", none);
        StockLog on = new StockLog(true);
        check("2.3.0 stock observation: icon (1.7.2: with every value of the turn frame and the distance then)",
                "stock navigation: tbt icon 67 side 0 dir 0.0 distErr 0 combi 0 unit 0 bar 0 barErr 0 dist 100.0".equals(on.line("update_tbt_info", new Object[] { 67, 0, 0f, 100f, 0, 0, 0, 0, 0 }, false)));
        check("2.3.0 stock observation: same values not logged again (1.7.2: a new distance alone isn't a new line)", on.line("update_tbt_info", new Object[] { 67, 0, 0f, 90f, 0, 0, 0, 0, 0 }, false) == null);
        byte[] lf = new byte[81];
        lf[0] = (byte) 0xf1;
        lf[1] = 2;
        lf[3] = 1;
        lf[19] = 9;
        check("2.3.0 stock observation: lane frame in hex, marked when a phone guides (skipped)",
                "stock navigation: lanes f102000100000000000000000000000000000009 (phone guiding, skipped)".equals(on.line("update_lane_info", new Object[] { lf }, true)));
        check("2.3.0 stock observation: 2C numbers",
                "stock navigation: 2c 0 0 170 0 0 170 0 0 400".equals(on.line("update_tbt_sub_info_2c", new Object[] { 0, 0, 170, 0, 0, 170, 0, 0, 400 }, false)));
        check("2.3.0 stock observation: other calls (names) never logged (1.7.2: the crossroad name as its length only)",
                "stock navigation: road name length 1".equals(on.line("update_tp_crossroad_name", new Object[] { "x" }, false))
                        && on.line("update_tp_destination_name", new Object[] { "x" }, false) == null);
        int n = 1;   // 회전 줄은 위에서 1줄
        for (int k = 0; k < 500; k++) if (on.line("update_tbt_info", new Object[] { k, 0, 0f, 0f, 0, 0, 0, 0, 0 }, false) != null) n++;
        check("2.3.0 stock observation: at most 300 lines (review: turn lines have their own 100)", n == 100 && on.lines == 103
                && on.line("update_lane_info", new Object[] { new byte[] { (byte) 0xf1, 3 } }, false) != null);
        stockLog172();
    }

    /** 1.7.2(M-3·M-14): 멀리 있음 값, 포커스가 순정으로 넘어갈 때 지우기 */
    static void korea172() {
        final int RIGHT = 2, LEFT = 1, TURN = 4, SHARP = 5, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        Handler h = new Handler();
        AaTurns t = new AaTurns(new android.content.Context(), c, h);
        t.status(1);
        t.nextTurn("Road A", LEFT, SHARP, -1, 0);
        // 멀리 있음 값을 보려고 D(350m) 밖 거리(소유자 결정 11-09부터 D 안이면 차로 없이도 곧 회전)
        t.nextTurnDistance(400, 20, 400, METERS);
        String tbt = null;
        for (String x : c.calls) if (x.startsWith("tbt ")) tbt = x;
        check("1.7.2 M-3: AA turn far-away values: side street 0 (not 65535), direction 0 (not 225), bar 0 and barErr -1 (not 32)",
                "tbt 69 0 0.0 400.0 0 0 0 0 -1".equals(tbt));
        check("1.7.2 M-3: the turn's own direction is still worked out (for the close-turn screen later)", t.direction == 225);
        check("1.7.2 M-14: clear values within NavigationCluster's ranges",
                AaTurns.CLEAR_ICON == 8 && AaTurns.CLEAR_DIST_ERR == -3 && AaTurns.CLEAR_UNIT == 15 && AaTurns.BAR_FAR_ERR == -1
                        && AaTurns.HUD_CLEAR_UNIT == 15 && AaTurns.HUD_CLEAR_TENTHS == 15);
        t.nextTurnDistance(16093, 1800, 0, 0x100 | METERS);
        check("1.7.2: trip sent, its 250 ms check scheduled", h.delayed != null);
        c.calls.clear();
        t.stop();
        expect("1.7.2 M-14: focus to the car's navigation during a route: turn 8, trip, 2C (15,15,65535)x3, lanes and HUD cleared",
                "dest 0 []", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        check("1.7.2 M-14: the trip check stops with it", h.delayed == null);
        check("1.7.2 M-14: the route stays live on the phone (turns go out again when focus comes back)", t.navLive);
        t.end();
        expect("1.7.2 M-14: the connection ends right after: not cleared twice");

        AaTurns u = new AaTurns(new android.content.Context(), c, new Handler());
        u.stop();
        expect("1.7.2 M-14: focus change with no route: nothing sent");
        u.status(1);
        u.nextTurn("Road B", RIGHT, TURN, -1, 0);
        u.stop();
        u.focusPhone();
        c.calls.clear();
        u.nextTurn("Road C", RIGHT, TURN, -1, 0);
        u.nextTurnDistance(300, 30, 300, METERS);
        c.calls.clear();
        u.end();
        expect("1.7.2 M-14: new turns after the focus came back, then the connection ends: cleared again",
                "dest 0 []", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 1.7.2(M-13): 넓힌 순정 관찰 기록. 숫자만, 이름은 길이만, 디버그 때만, 최대 줄 수 */
    static void stockLog172() {
        StockLog off = new StockLog(false);
        check("1.7.2 stock observation: new kinds silent with the debug log off",
                off.line("update_remain_dis", new Object[] { 90, 0, 0, 1 }, false) == null
                        && off.line(StockLog.CLU_DATA_14, new Object[] { 1, 2, 3, 4, 5 }, false) == null
                        && off.line("update_tp_turn_list_new", new Object[] { new byte[] { (byte) 0xf2, 0 } }, false) == null
                        && off.line("update_tp_crossroad_name", new Object[] { "Road" }, false) == null);
        StockLog on = new StockLog(true);
        String far = on.line("update_tbt_info", new Object[] { 67, 0, 0f, 400f, 0, 0, 0, 0, -1 }, false);
        String close = on.line("update_tbt_info", new Object[] { 13, 0x1011, 90f, 340f, 0, 0, 0, 90, 0 }, true);
        String bar = on.line("update_tbt_info", new Object[] { 13, 0x1011, 90f, 300f, 0, 0, 0, 80, 0 }, true);
        check("1.7.2 stock observation: far-away turn (bar invalid)",
                "stock navigation: tbt icon 67 side 0 dir 0.0 distErr 0 combi 0 unit 0 bar 0 barErr -1 dist 400.0".equals(far));
        check("1.7.2 stock observation: close-turn values (icon 13, side street, direction, bar) with the distance they start at",
                "stock navigation: tbt icon 13 side 4113 dir 90.0 distErr 0 combi 0 unit 0 bar 90 barErr 0 dist 340.0 (phone guiding, skipped)".equals(close));
        check("1.7.2 stock observation: the bar going down is a new line with its distance", bar != null && bar.contains("bar 80") && bar.endsWith("dist 300.0 (phone guiding, skipped)"));
        check("1.7.2 stock observation: a turn frame with wrong types isn't logged",
                on.line("update_tbt_info", new Object[] { 67, 0, 0, 400f, 0, 0, 0, 0, -1 }, false) == null);
        byte[] hud = new byte[5 + 8];
        hud[0] = (byte) 0xf2;
        hud[1] = 3;
        hud[2] = 67;
        hud[3] = 70;
        hud[4] = 65;
        byte[] name = "가나다라".getBytes(java.nio.charset.Charset.forName("UTF-16LE"));
        System.arraycopy(name, 0, hud, 5, 8);
        String h = on.line("update_tp_turn_list_new", new Object[] { hud }, false);
        check("1.7.2 stock observation: HUD list head in hex (3 icons), the name as a length only",
                "stock navigation: hud list f203434641 len 13 name bytes 8".equals(h));
        StringBuilder nh = new StringBuilder();
        for (byte x : name) nh.append(String.format("%02x", x & 0xff));
        check("1.7.2 stock observation: no name bytes in the HUD line", !h.contains(nh.toString()) && !h.contains("00ac"));
        check("1.7.2 stock observation: empty HUD list {F2,00} kept as two bytes (V-6)",
                "stock navigation: hud list f200 len 2 name bytes 0".equals(on.line("update_tp_turn_list_new", new Object[] { new byte[] { (byte) 0xf2, 0 } }, false))
                        && "stock navigation: hud list f2 len 1 name bytes 0".equals(on.line("update_tp_turn_list_new", new Object[] { new byte[] { (byte) 0xf2 } }, false)));
        check("1.7.2 stock observation: updateRemainDis numbers",
                "stock navigation: remain dis 65535 15 0 0".equals(on.line("update_remain_dis", new Object[] { 65535, 15, 0, 0 }, false)));
        check("1.7.2 stock observation: updateCluDataNew_14 numbers",
                "stock navigation: clu14 65535 65535 65535 0 0".equals(on.line(StockLog.CLU_DATA_14, new Object[] { 65535, 65535, 65535, 0, 0 }, false)));
        check("1.7.2 stock observation: road event frame in hex",
                "stock navigation: road event 010203000000".equals(on.line(StockLog.ROAD_EVENT, new Object[] { new byte[] { 1, 2, 3, 0, 0, 0 } }, false)));
        String rn = on.line("update_tp_crossroad_name", new Object[] { "중봉대로" }, false);
        check("1.7.2 stock observation: crossroad name as its length only, never the text",
                "stock navigation: road name length 4".equals(rn) && !rn.contains("중봉"));
        check("1.7.2 stock observation: same length not logged again; an empty name is 0",
                on.line("update_tp_crossroad_name", new Object[] { "파랑로다" }, false) == null
                        && "stock navigation: road name length 0".equals(on.line("update_tp_crossroad_name", new Object[] { "" }, false)));
        check("1.7.2 stock observation: a crossroad object that isn't a string isn't logged",
                on.line("update_tp_crossroad_name", new Object[] { new Object() }, false) == null);
        int sum = 0;
        for (int m : StockLog.KIND_MAX) sum += m;
        check("review 4: per-kind caps add up to the old 300", sum == StockLog.MAX && StockLog.KINDS.length == StockLog.KIND_MAX.length);

        // 리뷰 4: 거리만 바뀐 2C·remain dis는 남기지 않고, 다른 값이 바뀔 때 그때 거리를 함께 남긴다
        StockLog d = new StockLog(true);
        String a1 = d.line("update_tbt_sub_info_2c", new Object[] { 0, 0, 170, 0, 0, 400, 0, 0, 65535 }, false);
        String a2 = d.line("update_tbt_sub_info_2c", new Object[] { 0, 0, 160, 0, 0, 390, 0, 0, 65535 }, false);
        String a3 = d.line("update_tbt_sub_info_2c", new Object[] { 0, 0, 150, 0, 0, 380, 0, 0, 900 }, false);
        String a4 = d.line("update_tbt_sub_info_2c", new Object[] { 1, 2, 1, 0, 0, 380, 0, 0, 900 }, false);
        check("review 4: 2C distance-only change not logged; a new point or unit is, with its distances",
                "stock navigation: 2c 0 0 170 0 0 400 0 0 65535".equals(a1) && a2 == null
                        && "stock navigation: 2c 0 0 150 0 0 380 0 0 900".equals(a3) && "stock navigation: 2c 1 2 1 0 0 380 0 0 900".equals(a4));
        String r1 = d.line("update_remain_dis", new Object[] { 90, 0, 0, 1 }, false);
        String r2 = d.line("update_remain_dis", new Object[] { 80, 0, 0, 1 }, false);
        String r3 = d.line("update_remain_dis", new Object[] { 65535, 15, 0, 0 }, false);
        check("review 4: remain dis distance-only change not logged; unit/TG/invalid change is, with the distance",
                "stock navigation: remain dis 90 0 0 1".equals(r1) && r2 == null && "stock navigation: remain dis 65535 15 0 0".equals(r3));

        // 리뷰 4: 종류별 한도. 한 종류가 다 차도 다른 종류(특히 회전)는 계속 남는다
        StockLog f = new StockLog(true);
        int k2 = 0;
        for (int k = 0; k < 400; k++) if (f.line("update_tbt_sub_info_2c", new Object[] { k, 0, 1, 0, 0, 65535, 0, 0, 65535 }, false) != null) k2++;
        int kc = 0;
        for (int k = 0; k < 400; k++) if (f.line(StockLog.CLU_DATA_14, new Object[] { k, 1, 1, 1, 1 }, false) != null) kc++;
        int kr = 0;
        for (int k = 0; k < 400; k++) if (f.line("update_remain_dis", new Object[] { 1, k, 0, 0 }, false) != null) kr++;
        check("review 4: 2C stops at 30, CluDataNew_14 at 20, remain dis at 25", k2 == 30 && kc == 20 && kr == 25);
        check("review 4: a turn line still goes out after the others filled up",
                f.line("update_tbt_info", new Object[] { 13, 0, 90f, 300f, 0, 0, 0, 90, 0 }, false) != null);
        int kt = 1;
        for (int k = 0; k < 400; k++) if (f.line("update_tbt_info", new Object[] { k, 0, 0f, 0f, 0, 0, 0, 0, 0 }, false) != null) kt++;
        check("review 4: turn lines stop at 100", kt == 100);

        // 2차 리뷰 반영: updateTBTSubInfo 무안내 값 관찰(거리만 바뀐 줄은 남기지 않음)
        StockLog sb = new StockLog(true);
        String s1 = sb.line("update_tbt_sub_info", new Object[] { 0f, 0, 15, 255, 255, 3, 3, 0f, -1 }, false);
        String s2 = sb.line("update_tbt_sub_info", new Object[] { 0f, 0, 15, 255, 255, 3, 3, 0f, -1 }, false);
        String s3 = sb.line("update_tbt_sub_info_b", new Object[] { 900f, 0, 0, 18, 1, 1, 2, 0f, -1, 1 }, true);
        String s4 = sb.line("update_tbt_sub_info_b", new Object[] { 800f, 0, 0, 18, 1, 1, 2, 0f, -1, 1 }, true);
        check("2nd review 8: stock SubInfo values logged (9 and 10 args), distance alone isn't a new line",
                "stock navigation: sub info a 0 15 255 255 3 3 0.0 -1 dist 0.0".equals(s1) && s2 == null
                        && "stock navigation: sub info b 0 0 18 1 1 2 0.0 -1 1 dist 900.0 (phone guiding, skipped)".equals(s3) && s4 == null);
        check("2nd review 8: SubInfo with wrong types isn't logged",
                sb.line("update_tbt_sub_info", new Object[] { 0, 0, 15, 0, 0, 3, 3, 0f, -1 }, false) == null);
    }

    /** 리뷰 반영(1·3): stop 반복, 계기판에 보내지 않는 중간 이벤트 */
    static void reviewFixes() {
        final int RIGHT = 2, TURN = 4, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurns t = new AaTurns(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(200, 20, 200, METERS);
        c.calls.clear();
        t.stop();
        check("review 1: first focus-native clears", c.calls.contains(CLEAR_TBT));
        c.calls.clear();
        t.stop();
        t.stop();
        expect("review 1: focus-native again with nothing sent since: nothing (the car's navigation values stay)");

        t.nextTurn("S 3 0a0102", 0, 33, 0, 0);
        t.nextTurn("Oak Ave", RIGHT, 64, 90, TURN);
        t.nextTurnDistance(500, 0, 500, METERS | 0x200);
        expect("review 3: navigation message and turns-ahead data while the car's navigation has focus: nothing to the cluster");
        t.stop();
        t.status(2);
        expect("review 3: ... so neither another focus-native nor the route end clears again");

        AaTurns u = new AaTurns(new android.content.Context(), c, new Handler());
        u.status(1);
        u.nextTurn("Road B", RIGHT, TURN, -1, 0);
        u.stop();
        c.calls.clear();
        u.nextTurnDistance(150, 15, 150, METERS);
        u.nextTurn("Road D", RIGHT, TURN, -1, 0);
        u.nextTurn("Home", 0, 32, 0, 2);
        u.nextTurnDistance(16093, 1800, 0, 0x100 | METERS);
        u.nextTurn("02" + "01aabbcc" + "02000000", 0, 34, 0, 0);
        expect("2nd review 3: while the focus is native, phone turns, distances, destination, trip and lanes don't reach the cluster");
        u.status(2);
        expect("2nd review 3: a route end while the focus is native clears nothing (the cluster is the car navigation's)");

        AaTurns v = new AaTurns(new android.content.Context(), c, new Handler());
        v.status(1);
        v.nextTurn("Road E", RIGHT, TURN, -1, 0);
        v.stop();
        v.nextTurn("Home", 0, 32, 0, 2);
        v.nextTurnDistance(150, 15, 150, METERS);
        c.calls.clear();
        v.focusPhone();
        expect("2nd review 3: focus back to the phone: a destination name that came while the focus was native goes out now", "dest 0 [Home]");
        v.nextTurn("Road E", RIGHT, TURN, -1, 0);
        check("2nd review 3: ... and the next phone turn reaches the cluster again", c.calls.contains("tbt 67 0 0.0 150.0 0 0 0 0 -1"));
        c.calls.clear();
        v.focusPhone();
        expect("2nd review 3: focus to the phone again: nothing repeated");
        v.status(2);
        expect("2nd review 3: then the route end clears", "dest 0 []", CLEAR_TBT, CLEAR_SUB, CLEAR_ROAD, CLEAR_2C, "lanes f1", "hud f2", "hud f2");
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 리뷰 2·2차 리뷰 1: 폰 안내 끝 뒤 순정 계기판 값 다시 보내기(StockReplay) */
    static String names(java.util.List<Object[]> l) {
        StringBuilder b = new StringBuilder();
        for (Object[] x : l) b.append(b.length() == 0 ? "" : ",").append(x[3]);
        return b.toString();
    }

    static void stockReplay() {
        check("review 2: resends 1.5 s and 5 s after the end, backup timer at 6 s",
                StockReplay.REPLAY_MS.length == 2 && StockReplay.REPLAY_MS[0] == 1500L && StockReplay.REPLAY_MS[1] == 5000L
                        && StockReplay.BACKUP_MS > StockReplay.REPLAY_MS[1]);
        StockReplay r = new StockReplay();
        Object nc = new Object(), m = new Object();
        // 폰 안내 전 순정 값(HUD 목록·차로·회전): 통과한 호출도 기억한다(2차 리뷰 1)
        r.call("update_tp_turn_list_new", nc, m, new Object[] { new byte[] { (byte) 0xf2, 1, 67 } }, false, 0);
        r.call("update_lane_info", nc, m, new Object[] { new byte[] { (byte) 0xf1, 2 } }, false, 10);
        Object[] tbt = { 67, 0, 0f, 300f, 0, 0, 0, 0, -1 };
        StockReplay.Out o = r.call("update_tbt_info", nc, m, tbt, false, 20);
        tbt[3] = 999f;   // 호출한 쪽이 배열을 바꿔도 기억은 그대로
        check("2nd review 1: stock calls before any phone guidance are remembered, nothing resent, no backup",
                r.names().size() == 3 && o.resend.isEmpty() && o.backupGen == 0 && !r.pending());
        // 폰 안내 중: SubInfo만 막힌다(HUD 목록·차로·회전은 바뀌지 않아 다시 오지 않음)
        o = r.call("update_tbt_sub_info_b", nc, m, new Object[] { 1.2f }, true, 1000);
        r.call("update_tbt_sub_info_b", nc, m, new Object[] { 1.1f }, true, 1700);
        check("2nd review 1: a blocked call isn't an end and resends nothing", o.resend.isEmpty() && !r.pending());
        // 폰 안내 끝: 첫 통과 호출로 알아챈다
        o = r.call("update_tbt_sub_info_b", nc, m, new Object[] { 0f }, false, 10000);
        check("2nd review 1: first stock call after phone guidance marks the end and asks for one backup timer",
                r.pending() && o.resend.isEmpty() && o.backupGen > 0);
        int backupGen = o.backupGen;
        o = r.call("update_tbt_sub_info_b", nc, m, new Object[] { 0f }, false, 10700);
        check("review 2: before 1.5 s nothing is resent", o.resend.isEmpty());
        o = r.call("update_tbt_sub_info_b", nc, m, new Object[] { 0f }, false, 11500);
        check("2nd review 1: at 1.5 s (in the stock call's own hook) round 1 resends the values from before phone guidance too,"
                + " in update order, without the method passing right now",
                o.round == 1 && names(o.resend).equals("update_tp_turn_list_new,update_lane_info,update_tbt_info")
                        && ((Object[]) o.resend.get(2)[2])[3].equals(300f) && o.resend.get(0)[0] == nc && o.resend.get(0)[1] == m);
        r.call("update_lane_info", nc, m, new Object[] { new byte[] { (byte) 0xf1, 3 } }, false, 12000);
        o = r.call("update_tbt_sub_info_b", nc, m, new Object[] { 0f }, false, 12800);
        check("2nd review 1: between rounds nothing is resent", o.resend.isEmpty());
        o = r.call("update_tbt_sub_info_b", nc, m, new Object[] { 0f }, false, 15100);
        check("2nd review 1: at 5 s round 2: the stock's newer lane frame (3, not 2), newest last; then it ends",
                o.round == 2 && names(o.resend).equals("update_tp_turn_list_new,update_tbt_info,update_lane_info")
                        && ((byte[]) ((Object[]) o.resend.get(2)[2])[0])[1] == 3 && !r.pending());
        check("2nd review 1: the backup timer after it finished sends nothing", r.backup(backupGen).resend.isEmpty());
        o = r.call("update_tbt_sub_info_b", nc, m, new Object[] { 0f }, false, 30000);
        check("2nd review 1: afterwards, without new phone guidance, stock calls resend nothing and start nothing",
                o.resend.isEmpty() && o.backupGen == 0 && !r.pending());
        check("2nd review 1: values stay remembered for the next phone guidance", r.names().size() == 4);

        // 폰 안내가 다시 시작되면 이번 끝은 버린다
        StockReplay q = new StockReplay();
        q.call("update_tbt_info", nc, m, new Object[] { 1 }, true, 0);
        o = q.call("update_tbt_info", nc, m, new Object[] { 2 }, false, 100);
        int g1 = o.backupGen;
        q.call("update_tbt_info", nc, m, new Object[] { 3 }, true, 500);
        check("review 2: phone guidance back before the resend: the end is dropped, its backup sends nothing",
                !q.pending() && q.backup(g1).resend.isEmpty());
        o = q.call("update_lane_info", nc, m, new Object[] { 4 }, false, 1000);
        StockReplay.Out late = q.call("update_lane_info", nc, m, new Object[] { 4 }, false, 7000);
        check("2nd review 1: a late stock call past both times sends once, as the last round, then ends",
                late.round == 2 && names(late.resend).equals("update_tbt_info") && ((Object[]) late.resend.get(0)[2])[0].equals(3) && !q.pending());

        // 순정 호출이 오지 않으면 백업 타이머 한 번
        StockReplay w = new StockReplay();
        w.call("update_tp_turn_list_new", nc, m, new Object[] { 9 }, false, 0);
        w.call("update_tbt_sub_info_b", nc, m, new Object[] { 1f }, true, 100);
        o = w.call("update_tbt_sub_info_b", nc, m, new Object[] { 0f }, false, 200);
        StockReplay.Out b = w.backup(o.backupGen);
        check("2nd review 1: no stock call after the end: the backup sends everything once (newest last) and ends",
                names(b.resend).equals("update_tp_turn_list_new,update_tbt_sub_info_b") && !w.pending() && w.backup(o.backupGen).resend.isEmpty());
        check("2nd review 1: a stale backup generation sends nothing", w.backup(o.backupGen + 5).resend.isEmpty());
    }

    /** 1.7.2 시험판 3: 막대를 순정 단속 정보 프레임에도(PhoneBar 보내기·순정 내비 쪽 판단, AA 방송) */
    static void phoneBar() {
        PhoneBar.Sender s = new PhoneBar.Sender();
        check("trial 3: sender: the first value goes out", s.due(90, 0L));
        check("trial 3: sender: the same value not again within 10 s", !s.due(90, 9999L));
        check("trial 3: sender: the same bar again after 10 s (the stock navigation may have restarted)", s.due(90, 10000L));
        check("trial 3: sender: a new value at once", s.due(80, 10001L));
        check("trial 3: sender: NONE once, never repeated", s.due(PhoneBar.NONE, 10002L) && !s.due(PhoneBar.NONE, 99999L));

        PhoneBar.Navi n = new PhoneBar.Navi();
        check("trial 3: navigation side: no stock trap frame seen yet: nothing resent (never makes up cameras or limits)", n.onBar(70, true, 0L) == null);
        check("trial 3: stock trap call while the phone guides with a live bar: the phone's bar goes in", n.onStock(0, true, 100L) == 70);
        check("trial 3: same phone bar again: nothing resent", n.onBar(70, true, 200L) == null);
        Integer r = n.onBar(60, true, 300L);
        check("trial 3: phone bar changes: resent with the new bar", r != null && r == 60);
        check("trial 3: stock trap call with the phone not guiding: the stock bar untouched", n.onStock(30, false, 400L) == 30);
        r = n.onBar(50, true, 500L);
        check("trial 3: phone guides again: resent with the phone's bar", r != null && r == 50);
        r = n.onBar(PhoneBar.NONE, true, 600L);
        check("trial 3: phone guidance ends (NONE): resent with the stock's last bar", r != null && r == 30);
        check("trial 3: NONE again: nothing more", n.onBar(PhoneBar.NONE, true, 700L) == null);
        n.onBar(40, true, 1000L);
        check("trial 3: check while the bar is live: nothing", n.check(true, 1000L + PhoneBar.EXPIRE_MS - 1) == null);
        r = n.check(true, 1000L + PhoneBar.EXPIRE_MS);
        check("trial 3: no broadcast for EXPIRE_MS (3 min, phone side gone): back to the stock bar", r != null && r == 30);
        n.onBar(40, true, 50000L);
        r = n.check(false, 50001L);
        check("trial 3: phone stops guiding (focus back to the car's navigation): back to the stock bar", r != null && r == 30);
        check("trial 3: then nothing more", n.check(false, 50002L) == null);
        r = n.onBar(250, true, 60000L);
        check("trial 3: out-of-range bar from a broadcast: treated as NONE (nothing to restore now)", r == null && n.onStock(20, true, 60001L) == 20);

        // AA: 곧 회전이면 막대, 멀리 있으면 0, 포커스가 순정으로 넘어가면 NONE을 알린다
        Cluster cc = new Cluster();
        android.content.Context ctx = new android.content.Context();
        AaTurns t = new AaTurns(ctx, cc, new Handler());
        t.clock = 1000L;
        t.status(1);
        t.nextTurn("Road P", 2, 4, -1, 0);
        t.nextTurnDistance(600, 30, 600, 1);
        t.nextTurnDistance(300, 20, 300, 1);
        java.util.List<Integer> bars = new java.util.ArrayList<Integer>();
        boolean named = true;
        for (android.content.Intent i : ctx.sent) {
            bars.add(i.getIntExtra(PhoneBar.EXTRA_BAR, -99));
            if (!PhoneBar.ACTION.equals(i.action) || i.ints.size() != 1) named = false;
        }
        check("trial 3: AA far then near at 300 m: broadcasts 0 then 80 (only the bar, nothing else)", bars.contains(0) && bars.get(bars.size() - 1) == 80 && named);
        int before = ctx.sent.size();
        t.stop();
        check("trial 3: AA focus to the car's navigation: NONE broadcast",
                ctx.sent.size() > before && ctx.sent.get(ctx.sent.size() - 1).getIntExtra(PhoneBar.EXTRA_BAR, -99) == PhoneBar.NONE);
        check("trial 3: bar never above 90 (stock maximum), at least 10", AaTurns.bar(350, 350) == 90 && AaTurns.bar(1, 350) == 10 && AaTurns.bar(349, 350) == 90);
        trapArgs();
    }

    /** 순정 단속 정보 프레임과 같은 꼴의 시험용 객체(칸이 모두 public int) */
    public static class FakeTrap {
        public static final int CREATOR = 7;
        public int fixedSpeed, barGraphLevel, speedLimit, cameraSafetyAlert, displayCountry;
    }

    /** 칸 하나가 private인 객체: 사본이 칸을 빠뜨리므로 쓰지 않아야 한다 */
    public static class FakeTrapPrivate {
        public int barGraphLevel;
        private int hidden;
    }

    /** final 칸이 있는 객체: 사본에 옮길 수 없다 */
    public static class FakeTrapFinal {
        public int barGraphLevel;
        public final int fixed = 1;
    }

    /** int가 아닌 칸이 있는 객체 */
    public static class FakeTrapLong {
        public int barGraphLevel;
        public long stamp;
    }

    /** 1.7.2 시험판 3 리뷰 11: 단속 정보 인자 사본(막대 칸만 바꾸기) */
    static void trapArgs() {
        try {
            java.lang.reflect.Field f = TrapArgs.barField(FakeTrap.class);
            check("trial 3 review: a trap object of public ints only: usable, bar field found", f != null && "barGraphLevel".equals(f.getName()));
            check("trial 3 review: a private field: not used (the copy would drop it)", TrapArgs.barField(FakeTrapPrivate.class) == null);
            check("trial 3 review: a non-int field: not used", TrapArgs.barField(FakeTrapLong.class) == null);
            check("trial 3 2nd review: a final field: not used", TrapArgs.barField(FakeTrapFinal.class) == null);
            FakeTrap o = new FakeTrap();
            o.fixedSpeed = 60;
            o.speedLimit = 50;
            o.cameraSafetyAlert = 53;
            o.displayCountry = 1;
            o.barGraphLevel = 0;
            Object[] w = TrapArgs.withBar(new Object[] { o }, true, f, 70);
            FakeTrap n = (FakeTrap) w[0];
            check("trial 3 review: object copy: only the bar changes, cameras and limits kept, the stock object untouched",
                    n != o && n.barGraphLevel == 70 && n.fixedSpeed == 60 && n.speedLimit == 50 && n.cameraSafetyAlert == 53 && n.displayCountry == 1
                            && o.barGraphLevel == 0);
            Object[] a = new Object[17];
            for (int i = 0; i < 17; i++) a[i] = Integer.valueOf(i + 100);
            check("trial 3 review: 17-int frame with an out-of-range value at the bar place (other firmware order): not used", TrapArgs.intBar(a) == -1);
            a[11] = Integer.valueOf(0);
            check("trial 3 review: 17-int frame: the bar read from argument 11", TrapArgs.intBar(a) == 0);
            Object[] w2 = TrapArgs.withBar(a, false, null, 40);
            boolean same = true;
            for (int i = 0; i < 17; i++) if (i != 11 && !a[i].equals(w2[i])) same = false;
            check("trial 3 review: 17-int copy: only argument 11 changes, the stock array untouched", same && (Integer) w2[11] == 40 && (Integer) a[11] == 0);
            check("trial 3 review: 16 arguments: not used", TrapArgs.intBar(new Object[16]) == -1);
        } catch (Exception e) {
            check("trial 3 review: trap args threw " + e, false);
        }
        PhoneBar.Navi n = new PhoneBar.Navi();
        n.onStock(20, false, 0L);
        n.onBar(60, true, 10L);
        check("trial 3 review: current bar is the phone's while overriding", n.current() == 60);
        n.onBar(PhoneBar.NONE, true, 20L);
        check("trial 3 review: current bar is the stock's after the end", n.current() == 20);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fail = 1;
    }
}
