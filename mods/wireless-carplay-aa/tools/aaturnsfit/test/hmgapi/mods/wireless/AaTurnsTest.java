package hmgapi.mods.wireless;

import android.os.Handler;

import java.util.List;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

/**
 * Host test of AaTurnsFit: a drive through the AA library's callbacks, checking every
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
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, h);
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
        expect("100 m with miles set: feet (near; 2.4.3: D is this turn's first distance 320 m, bar 30)", "tbt 13 16 90.0 328.1 0 0 3 30 0", "2c 3 0 328 0 0 65535 0 0 65535");
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

        AaTurnsFit d = new AaTurnsFit(new android.content.Context(), c, new Handler());
        d.status(1);
        d.nextTurn("Start Rd", 0, 1, 0, 0);
        expect("depart (first): road as destination, no distance yet (1.7.2: far, empty road name)", "dest 0 [Start Rd]", "road []", "tbt 2 0 0.0 -1.0 0 0 0 254 -2");
        d.nextTurn("Circle", RIGHT, 13, 100, 0);
        // 2.4.3: 2.4.2에서 순정대로 바로잡은 것을 지킨다: 거리가 아직 없으면 아이콘과 상관없이 표시 2(순정 GPM, T-마). 2.4.1은 99였다
        expect("roundabout before any distance (2.4.2 stock fix kept): icon 2, not 99; no HUD list for 2", "tbt 2 0 0.0 -1.0 0 0 0 254 -2");
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
        aa173();
        aa173review();
        aaNameOnly();
        aaNameOnlyReview();
        aaStage6();
        scenarioTable();
        check("2nd review 6: a MAC is masked completely, other text only as a length, null as null",
                "xx:xx:xx:xx:xx:xx".equals(Mac.mask("A1:B2:C3:D4:E5:F6")) && !Mac.mask("A1:B2:C3:D4:E5:F6").contains("F6")
                        && "(6 chars)".equals(Mac.mask("iPhone")) && "null".equals(Mac.mask(null)));
        dynamicD();
        sameKindD();
        marginD();
        layerChoice();
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
        check("1.7.2 M-15: HUD name limit stays 78 bytes (stock name field up to 0x50)", AaTurnsFit.HUD_NAME_MAX == 78);
        check("1.7.2 M-15: 40 Hangul -> 39 (78 bytes), the last whole", AaTurnsFit.hudName(h40, 78).equals(repeat("우", 39)));
        check("1.7.2 M-15: 39 Hangul fit exactly, unchanged", AaTurnsFit.hudName(repeat("우", 39), 78).equals(repeat("우", 39)));
        check("1.7.2 M-15: \"우회전\" short, unchanged", AaTurnsFit.hudName("우회전", 78).equals("우회전"));
        check("1.7.2 M-15: emoji across the 78-byte edge is dropped whole, not split (38 a + emoji + b -> 38 a)",
                AaTurnsFit.hudName(repeat("a", 38) + emoji + "b", 78).equals(repeat("a", 38)));
        check("1.7.2 M-15: emoji ending exactly at 78 bytes is kept (37 a + emoji)",
                AaTurnsFit.hudName(repeat("a", 37) + emoji, 78).equals(repeat("a", 37) + emoji));
        check("1.7.2 M-15: ASCII short unchanged, long cut to 39", AaTurnsFit.hudName("Main St", 78).equals("Main St")
                && AaTurnsFit.hudName(repeat("x", 50), 78).equals(repeat("x", 39)));
        check("1.7.2 M-15: empty stays empty", AaTurnsFit.hudName("", 78).equals(""));
        boolean whole = true;
        for (int k = 0; k < 120; k++) {
            String s = repeat("a", k % 3) + repeat(emoji + "가", k % 45);
            String r = AaTurnsFit.hudName(s, 78);
            int n = r.length();
            if (n * 2 > 78 || !s.startsWith(r) || (n > 0 && Character.isHighSurrogate(r.charAt(n - 1)))
                    || (n < s.length() && n * 2 + (Character.isHighSurrogate(s.charAt(n)) ? 4 : 2) <= 78)) whole = false;
        }
        check("1.7.2 M-15: mixed emoji/Hangul: never over 78 bytes, never half a pair, never shorter than needed", whole);

        // 프레임 전체: 긴 한글 이름은 83바이트(머리 5 + 이름 78)이고, 이름은 글자 39개 그대로
        Cluster c2 = new Cluster();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c2, new Handler());
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
    /** 보낸 tbt 줄의 표시 번호를 바뀐 곳만 차례로(예: "173 65") */
    static String icons() {
        StringBuilder b = new StringBuilder();
        String prev = null;
        for (String x : c.calls) {
            if (!x.startsWith("tbt ")) continue;
            String d = x.split(" ")[1];
            if (d.equals(prev)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(d);
            prev = d;
        }
        return b.toString();
    }

    /** 1.7.3(모드 2.4.1): A-2 이름 바뀜 붙잡기(#65)와 N-3 곧 회전 경계 히스테리시스(#67) */
    static void aa173() {
        final int RIGHT = 2, UNSPEC = 3, LEFT = 1, FORK = 9, NAME = 2, TURN = 4, OFF_RAMP = 8, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        final java.util.List<String> log = new java.util.ArrayList<String>();
        t.log = new AaTurns.Sink() {
            @Override
            public void line(String s) {
                log.add(s);
            }
        };
        t.clock = 20000000L;
        t.status(1);
        // #65 07:16:31~07:17:23: 갈림길(9, 오른쪽)과 이름 바뀜(2, 방향 없음)이 2~4초마다 번갈아 옴, 거리는 줄어듦
        t.nextTurn("Fork Rd", RIGHT, FORK, -1, 0);
        t.nextTurnDistance(2000, 90, 2000, METERS);
        int m = 2000;
        for (int k = 0; k < 10; k++) {
            t.clock += 3000L;
            m -= 120;
            if (k % 2 == 0) t.nextTurn("Name Rd", UNSPEC, NAME, -1, 0);
            else t.nextTurn("Fork Rd", RIGHT, FORK, -1, 0);
            t.nextTurnDistance(m, 60, m, METERS);
        }
        check("1.7.3 A-2: fork and name change alternating (#65): the icon stays 173 (no 65)", "173".equals(icons()));
        check("1.7.3 A-2: a log line says the name change kept the icon (numbers only)",
                log.contains("AA: name change kept icon 173 (ev 9)"));
        c.calls.clear();
        // 이름만 바뀜: 곧 회전(D 안)에서 이름 바뀜이 오면 아이콘은 큰 그림(25) 그대로, 도로 이름만 바뀐다
        t.clock += 2000L;
        t.nextTurn("Fork Rd", RIGHT, FORK, -1, 0);
        t.nextTurnDistance(300, 20, 300, METERS);
        check("1.7.3 A-2: the fork at 300 m: near fork 25", last("tbt ").startsWith("tbt 25 ") && "road [Fork Rd]".equals(last("road ")));
        c.calls.clear();
        t.clock += 2000L;
        t.nextTurn("Name Rd", UNSPEC, NAME, -1, 0);
        check("A-2 rule: the name change alone sends nothing (decided at the next distance)", c.calls.isEmpty() && t.namePend);
        t.nextTurnDistance(300, 20, 300, METERS);
        check("1.7.3 A-2: name change inside D, next distance the same (300 m): still the near fork (25), only the road name changes",
                "25".equals(icons()) && "road [Name Rd]".equals(last("road ")) && last("tbt ").startsWith("tbt 25 "));
        t.clock += 2000L;
        t.nextTurnDistance(280, 18, 280, METERS);
        check("1.7.3 A-2: its distance (280 m, same stretch): still the fork, near (25), the name change's road",
                "25".equals(icons()) && last("tbt ").startsWith("tbt 25 ") && "road [Name Rd]".equals(last("road ")));
        c.calls.clear();
        // 갈림길을 지남: 남은 거리가 크게 늘면(30m -> 1500m) 붙잡아 둔 이름 바뀜이 다음 회전이 된다(65)
        t.clock += 2000L;
        t.nextTurnDistance(30, 2, 30, METERS);
        t.clock += 2000L;
        t.nextTurnDistance(1500, 80, 1500, METERS);
        check("1.7.3 A-2: past the fork (30 m -> 1.5 km): the held name change takes over (65)", "65".equals(last("tbt ").split(" ")[1]));
        c.calls.clear();
        // 실제 회전 이벤트가 15초 넘게 오지 않으면(폰이 이름 바뀜만 보냄) 붙잡기를 풀어 순정대로 이름 바뀜을 보인다
        t.clock += 2000L;
        t.nextTurn("Turn Rd", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(1200, 60, 1200, METERS);
        t.clock += 3000L;
        t.nextTurn("Name Rd 2", UNSPEC, NAME, -1, 0);
        t.clock += AaTurnsFit.NAME_HOLD_MS;
        t.nextTurnDistance(1100, 55, 1100, METERS);
        check("1.7.3 A-2: no real turn event for 15 s: the name change shows (67 -> 65)", "67 65".equals(icons()));
        c.calls.clear();
        t.clock += 2000L;
        t.nextTurn("Turn Rd", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(1000, 50, 1000, METERS);
        t.clock += AaTurnsFit.NAME_HOLD_MS + 1L;
        t.nextTurn("Name Rd 3", UNSPEC, NAME, -1, 0);
        check("1.7.3 A-2: a name change 15 s after the last real turn: shown as stock (65)", "67 65".equals(icons()));
        c.calls.clear();
        // 이름 바뀜 다음 이름 바뀜(실제 회전 없음), 출발·목적지: 붙잡지 않는다(순정대로)
        t.clock += 2000L;
        t.nextTurn("Name Rd 4", UNSPEC, NAME, -1, 0);
        check("1.7.3 A-2: name change after a name change: nothing held (65)", "65".equals(icons()) && !t.nameHeld);
        // 재탐색·새 경로에서는 붙잡은 것을 버린다
        t.nextTurn("Fork Rd", RIGHT, FORK, -1, 0);
        t.nextTurn("Name Rd", UNSPEC, NAME, -1, 0);
        check("1.7.3 A-2: waiting for the next distance while the fork is on", t.namePend);
        t.status(3);
        check("1.7.3 A-2: rerouting drops the waiting name change", !t.nameHeld && !t.namePend && t.nameRoad == null);
        t.status(1);
        t.nextTurn("Name Rd", UNSPEC, NAME, -1, 0);
        check("1.7.3 A-2: after rerouting, a name change isn't held by the old fork", !t.nameHeld);
        t.status(2);

        // N-3: 곧 회전 경계 히스테리시스(카플레이와 같은 규칙)
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.clock = 30000000L;
        t.status(1);
        check("1.7.3 N-3: AA exit margin 50 m (as CarPlay)", AaTurnsFit.NEAR_EXIT_MARGIN == 50);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        for (int d : new int[] { 420, 380, 352, 349, 356, 345, 362, 351, 340, 399, 348, 360 }) {
            t.clock += 1000L;
            t.nextTurnDistance(d, 20, d, METERS);
        }
        check("1.7.3 N-3: 420..349..399..360 m around D 350 m: small picture, then the near one once (67 13)", "67 13".equals(icons()));
        c.calls.clear();
        t.nextTurnDistance(400, 20, 400, METERS);
        check("1.7.3 N-3: 400 m (D + 50) still near", "13".equals(icons()));
        c.calls.clear();
        t.nextTurnDistance(401, 20, 401, METERS);
        t.nextTurnDistance(380, 20, 380, METERS);
        t.nextTurnDistance(351, 20, 351, METERS);
        check("1.7.3 N-3: past D + 50 m: far, and stays far until D again", "67".equals(icons()));
        c.calls.clear();
        t.nextTurnDistance(350, 20, 350, METERS);
        check("1.7.3 N-3: back inside D (350 m): near", "13".equals(icons()));
        c.calls.clear();
        t.nextTurn("Road B", LEFT, TURN, -1, 0);
        t.nextTurnDistance(370, 20, 370, METERS);
        check("1.7.3 N-3: a new turn at 370 m: far (the old turn's near doesn't carry over)", "70".equals(icons()));
        c.calls.clear();
        t.nextTurn("Exit 5", RIGHT, OFF_RAMP, -1, 0);
        for (int d : new int[] { 1200, 1060, 1040, 1080, 1060, 1100, 1020 }) t.nextTurnDistance(d, 50, d, METERS);
        check("1.7.3 N-3: highway exit around D 1050 m: far, then near once (164 17)", "164 17".equals(icons()));
        t.nextTurnDistance(1101, 50, 1101, METERS);
        check("1.7.3 N-3: highway exit past 1100 m: far again (164 17 164)", "164 17 164".equals(icons()));

        // 리뷰 4: 이름 없는 길의 같은 방향 연속 회전(열쇠가 같음). 앞 회전을 지나 다음 회전이 390m면 멀리 있음(큰 그림 아님)
        c.calls.clear();
        t.nextTurn("", RIGHT, TURN, -1, 0);
        for (int d : new int[] { 600, 300, 120, 20 }) t.nextTurnDistance(d, 20, d, METERS);
        t.nextTurn("", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(390, 30, 390, METERS);
        check("1.7.3 N-3 (review 4): two right turns on an unnamed road, the next at 390 m: far (67), not the near picture",
                last("tbt ").startsWith("tbt 67 "));
        t.nextTurnDistance(340, 25, 340, METERS);
        t.nextTurnDistance(385, 25, 385, METERS);
        check("1.7.3 N-3 (review 4): then inside D (340 m) near, and a 45 m wobble (385 m) stays near", last("tbt ").startsWith("tbt 13 "));
        t.status(2);
    }

    /** 리뷰 반영(1·5·6): 회전을 지난 뒤 거리가 이름 바뀜보다 먼저 와도 지난 큰 그림이 남지 않음, 포커스 순정·HUD 이름, 로그 줄 수 */
    static void aa173review() {
        final int RIGHT = 2, UNSPEC = 3, FORK = 9, NAME = 2, TURN = 4, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        final java.util.List<String> log = new java.util.ArrayList<String>();
        t.log = new AaTurns.Sink() {
            @Override
            public void line(String s) {
                log.add(s);
            }
        };
        t.clock = 40000000L;
        t.status(1);
        check("review 1: hold time 8 s (the #65 events came every 2-4 s)", AaTurnsFit.NAME_HOLD_MS == 8000L);
        // 리뷰어 재현: 우회전 곧 회전(큰 그림 13) → 지남 → 다음 단계 거리(1500m)가 먼저 → 이름 바뀜 → 거리 줄어듦
        t.nextTurn("Turn Rd", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(200, 20, 200, METERS);
        t.clock += 1000L;
        t.nextTurnDistance(15, 2, 15, METERS);
        check("review 1: the right turn at 15 m: near (13)", last("tbt ").startsWith("tbt 13 "));
        c.calls.clear();
        t.clock += 1000L;
        t.nextTurnDistance(1500, 80, 1500, METERS);
        t.clock += 500L;
        t.nextTurn("Next Rd", UNSPEC, NAME, -1, 0);
        check("review 1: distance first (1.5 km), then the name change: not held, the name change shows (65) at once",
                !t.nameHeld && last("tbt ").startsWith("tbt 65 "));
        for (int k = 0; k < 5; k++) {
            t.clock += 2000L;
            t.nextTurnDistance(1400 - k * 100, 70, 1400 - k * 100, METERS);
        }
        check("review 1: from the 1.5 km update on: never the near right-turn picture (no tbt 13), 65 to the end", "67 65".equals(icons()) && !c.calls.toString().contains("tbt 13 "));
        // 다음 실제 회전 이벤트가 오면 다시 붙잡을 수 있다
        t.clock += 1000L;
        t.nextTurn("Fork Rd", RIGHT, FORK, -1, 0);
        t.nextTurnDistance(900, 50, 900, METERS);
        t.clock += 2000L;
        t.nextTurn("Name Rd", UNSPEC, NAME, -1, 0);
        t.nextTurnDistance(880, 48, 880, METERS);
        check("review 1: the next real turn (fork) clears 'passed': a name change is held again", t.nameHeld && last("tbt ").startsWith("tbt 173 "));
        // HUD 목록 이름(리뷰 5): 붙잡는 동안 HUD 목록 프레임에 이름 바뀜의 도로 이름이 나간다(아이콘은 173 그대로)
        check("review 5: the HUD list carries the name change's road with the fork icon (173 = 0xad)",
                ("hud f201ad0000" + utf16("Name Rd")).equals(last("hud ")));
        // 8초 넘게 실제 회전이 없으면 풂
        t.clock += AaTurnsFit.NAME_HOLD_MS;
        t.nextTurnDistance(850, 45, 850, METERS);
        check("review 1: no real turn for 8 s: the held name change shows (65)", !t.nameHeld && last("tbt ").startsWith("tbt 65 "));
        // 포커스 순정(stop)이면 붙잡기를 버림(리뷰 5)
        t.clock += 1000L;
        t.nextTurn("Fork Rd", RIGHT, FORK, -1, 0);
        t.nextTurn("Name Rd", UNSPEC, NAME, -1, 0);
        t.nextTurnDistance(840, 45, 840, METERS);
        check("A-2 rule: a name change after a new turn event has no reference distance yet: waits one more sample", t.namePend && !t.nameHeld);
        t.nextTurnDistance(835, 45, 835, METERS);
        check("review 5: held before the focus goes to the car's navigation", t.nameHeld);
        t.stop();
        check("review 5: focus native (stop): the hold is dropped", !t.nameHeld && t.nameRoad == null && !t.turnPassed);
        // 로그 줄 수는 연결 끝에서 0으로(리뷰 6)
        check("review 6: hold log lines counted", t.nameHoldLogs > 0);
        t.end();
        check("review 6: the connection end resets the hold log count", t.nameHoldLogs == 0);
    }

    /** 1.7.3 추가(로그 H, #65·#67·#71·#104): 같은 회전에서 도로 이름만 2초마다 A↔B로 바뀌어도 곧 회전이 끊기지 않음 */
    static void aaNameOnly() {
        final int RIGHT = 2, LEFT = 1, TURN = 4, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.clock = 50000000L;
        t.status(1);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(300, 20, 300, METERS);
        c.calls.clear();
        int m = 300;
        for (int k = 0; k < 30; k++) {
            t.clock += 2000L;
            t.nextTurn(k % 2 == 0 ? "Road B" : "Road A", RIGHT, TURN, -1, 0);   // 이름만 바뀜(종류·방향·각도 같음)
            t.clock += 10L;
            if (k % 3 == 0 && m > 20) m -= 10;
            t.nextTurnDistance(m, 20, m, METERS);
        }
        boolean noFar = true;
        for (String x : c.calls) if (x.startsWith("tbt ") && !x.startsWith("tbt 13 ")) noFar = false;
        check("1.7.3 name only: same turn, road A<->B every 2 s at ~300 m: never a far frame (only tbt 13)", noFar && "13".equals(icons()));
        check("1.7.3 name only: the road name still follows the phone", "road [Road A]".equals(last("road ")));
        // 다른 회전(방향이 다름)이면 지금처럼 새로 시작: 자기 거리가 올 때까지 멀리 있음
        c.calls.clear();
        t.nextTurn("Road C", LEFT, TURN, -1, 0);
        check("1.7.3 name only: a different turn (left) starts over: far until its own distance", last("tbt ").startsWith("tbt 70 "));
        t.nextTurnDistance(250, 20, 250, METERS);
        check("1.7.3 name only: then near with its own distance", last("tbt ").startsWith("tbt 13 ") && last("tbt ").contains(" 250.0 "));
        // 이름 없는 같은 방향 연속 회전 규칙과 함께: 이름만 바뀐 다음 회전이 앞 회전을 지나 390m면 새 회전(멀리 있음)
        c.calls.clear();
        t.nextTurnDistance(20, 2, 20, METERS);
        t.nextTurn("Road D", LEFT, TURN, -1, 0);
        t.nextTurnDistance(390, 30, 390, METERS);
        check("1.7.3 name only + review 4: next same-direction turn with another name, past the old one, at 390 m: far (70)",
                last("tbt ").startsWith("tbt 70 "));
        t.status(2);
    }

    /** 1.7.3 "도로 이름만 바뀔 때" 리뷰: 연속 갈림길 붙잡기, 이름만 다른 새 회전에서 옛 거리 프레임 없음, 히스테리시스 띠 */
    static void aaNameOnlyReview() {
        final int RIGHT = 2, UNSPEC = 3, FORK = 9, NAME = 2, TURN = 4, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.clock = 60000000L;
        t.status(1);
        // 리뷰 1: 같은 방향 갈림길 두 개. 앞 갈림길(A)을 10m에서 지나고 다음 갈림길(B) 이벤트, 그 첫 거리 900m, 이어 이름 바뀜이 번갈아
        t.nextTurn("Fork A", RIGHT, FORK, -1, 0);
        for (int d : new int[] { 900, 400, 100, 10 }) {
            t.clock += 1000L;
            t.nextTurnDistance(d, 20, d, METERS);
        }
        t.clock += 1000L;
        t.nextTurn("Fork B", RIGHT, FORK, -1, 0);
        t.clock += 10L;
        t.nextTurnDistance(900, 50, 900, METERS);
        check("name-only review 1: the next fork's first distance (900 m after 10 m) doesn't count as 'passed' for it", !t.turnPassed);
        c.calls.clear();
        int m = 880;
        for (int k = 0; k < 8; k++) {
            t.clock += 3000L;
            m -= 60;
            if (k % 2 == 0) t.nextTurn("Name Rd", UNSPEC, NAME, -1, 0);
            else t.nextTurn("Fork B", RIGHT, FORK, -1, 0);
            t.nextTurnDistance(m, 40, m, METERS);
        }
        check("name-only review 1: consecutive same-side forks: the second fork's name changes are held too (173 only, no 65)", "173".equals(icons()));
        // 리뷰 3: 곧 회전이 바뀐 직후의 모든 tbt 프레임에 지난 곧 회전(옛 거리)이 없다
        t.status(2);
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.clock = 61000000L;
        t.status(1);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        for (int d : new int[] { 300, 120, 15 }) t.nextTurnDistance(d, 20, d, METERS);
        check("name-only review 2: the right turn at 15 m: near", last("tbt ").startsWith("tbt 13 "));
        c.calls.clear();
        t.nextTurn("Road B", RIGHT, TURN, -1, 0);   // 이름만 다른 같은 방향 다음 회전
        check("name-only review 2: a same-side turn with another name: no frame at once (judged at its distance)", c.calls.isEmpty() || last("tbt ") == null);
        t.nextTurnDistance(420, 30, 420, METERS);
        boolean stale = false;
        for (String x : c.calls) if (x.startsWith("tbt 13 ")) stale = true;
        check("name-only review 3: every tbt frame after the turn changed: no near picture with the old distance (only far 67)",
                !stale && "67".equals(icons()) && "road []".equals(last("road ")));
        // 리뷰 3: 히스테리시스 띠(380m, D와 D+50 사이)에서 이름만 바뀌어도 곧 회전 유지(열쇠에 이름이 없음)
        for (int d : new int[] { 340, 300 }) t.nextTurnDistance(d, 20, d, METERS);
        t.nextTurnDistance(380, 25, 380, METERS);
        check("name-only review 3: near at 380 m (inside D + 50 after being near)", last("tbt ").startsWith("tbt 13 "));
        c.calls.clear();
        t.nextTurn("Road B2", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(382, 25, 382, METERS);
        boolean far = false;
        for (String x : c.calls) if (x.startsWith("tbt ") && !x.startsWith("tbt 13 ")) far = true;
        check("name-only review 3: only the name changes at 380 m (hysteresis band): stays near, the new name shown",
                !far && last("tbt ").startsWith("tbt 13 ") && "road [Road B2]".equals(last("road ")));
        t.status(2);
    }

    /** 6단계 전체 리뷰 M1·L1·L2 */
    static void aaStage6() {
        final int RIGHT = 2, LEFT = 1, UNSPEC = 3, FORK = 9, NAME = 2, TURN = 4, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        Handler h = new Handler();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, h);
        t.clock = 70000000L;
        t.status(1);
        // M1 리뷰어 재현: 좌회전 10m → 이름 바뀜 → 다음 지점이 가까움(55, 52, 49m): 좌회전 큰 그림이 남지 않음
        t.nextTurn("Left Rd", LEFT, TURN, -1, 0);
        for (int d : new int[] { 300, 100, 10 }) {
            t.clock += 1000L;
            t.nextTurnDistance(d, 10, d, METERS);
        }
        check("stage 6 M1: the left turn at 10 m: near (13)", last("tbt ").startsWith("tbt 13 "));
        c.calls.clear();
        t.clock += 500L;
        t.nextTurn("Next Rd", UNSPEC, NAME, -1, 0);
        check("A-2 rule (was stage 6 M1): a name change at 10 m: nothing sent until the next distance", last("tbt ") == null && t.namePend);
        for (int d : new int[] { 55, 52, 49, 46 }) {
            t.clock += 1500L;
            t.nextTurnDistance(d, 5, d, METERS);
        }
        check("stage 6 M1: then 55 m (grew): the name change shows (65), and 52, 49 m never bring the left-turn picture back (no tbt 13)",
                !c.calls.toString().contains("tbt 13 ") && "65".equals(icons()) && !t.nameHeld);
        t.status(2);
        // M1: 붙잡은 뒤 거리가 15m 넘게 늘면 풂(폰이 다음 안내를 이름 바뀜으로 바꿈)
        c = new Cluster();
        h = new Handler();
        t = new AaTurnsFit(new android.content.Context(), c, h);
        t.clock = 71000000L;
        t.status(1);
        t.nextTurn("Left Rd", LEFT, TURN, -1, 0);
        t.nextTurnDistance(300, 20, 300, METERS);
        t.clock += 2000L;
        t.nextTurn("Next Rd", UNSPEC, NAME, -1, 0);
        check("A-2 rule: a name change at 300 m: waiting for the next distance", t.namePend);
        t.clock += 1000L;
        t.nextTurnDistance(290, 20, 290, METERS);
        check("A-2 rule: next distance shorter (290 m): same point, held (the #65 alternation stays blocked)", t.nameHeld && last("tbt ").startsWith("tbt 13 "));
        t.clock += 1000L;
        t.nextTurnDistance(296, 20, 296, METERS);
        check("A-2 rule: a 6 m rise (> 5 m wobble) after holding: released, the name change shows (65)", !t.nameHeld && last("tbt ").startsWith("tbt 65 "));
        t.status(2);
        // L1: 연속 갈림길. 갈림길 A 큰 그림(300m) → 이름만 다른 갈림길 B 이벤트 → 거리 전에 이름 바뀜(붙잡힘): 프레임 없음
        c = new Cluster();
        h = new Handler();
        t = new AaTurnsFit(new android.content.Context(), c, h);
        t.clock = 72000000L;
        t.status(1);
        t.nextTurn("Fork A", RIGHT, FORK, -1, 0);
        t.nextTurnDistance(300, 20, 300, METERS);
        check("stage 6 L1: fork A at 300 m: near fork (25)", last("tbt ").startsWith("tbt 25 "));
        c.calls.clear();
        t.clock += 500L;
        t.nextTurn("Fork B", RIGHT, FORK, -1, 0);
        t.clock += 500L;
        t.nextTurn("Name Rd", UNSPEC, NAME, -1, 0);
        check("stage 6 L1: fork B (name only differs) then a name change before B's distance: no frame with fork A's distance",
                t.namePend && t.samePend && last("tbt ") == null);
        t.clock += 100L;
        t.nextTurnDistance(900, 50, 900, METERS);
        check("stage 6 L1: fork B's own distance (900 m): far fork (173), never the near picture in between",
                last("tbt ").startsWith("tbt 173 ") && !c.calls.toString().contains("tbt 25 "));
        t.status(2);
        // L2: 이름만 다른 같은 방향 다음 회전 뒤 거리가 안 오면 1초 뒤 한 번 멀리 있음(2.4.0 동작), 반복 없음
        c = new Cluster();
        h = new Handler();
        t = new AaTurnsFit(new android.content.Context(), c, h);
        t.clock = 73000000L;
        t.status(1);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        for (int d : new int[] { 200, 15 }) t.nextTurnDistance(d, 5, d, METERS);
        c.calls.clear();
        t.nextTurn("Road B", RIGHT, TURN, -1, 0);
        check("stage 6 L2: a same-side turn with another name: nothing at once, a one-shot 1 s timer set",
                c.calls.isEmpty() && h.pending.contains(t.nameDue) && AaTurnsFit.PEND_WAIT_MS == 2000L);
        check("stage 6 L2: no distance within 1 s: one far frame (67, bar invalid) and the old near picture is gone",
                h.runPending(t.nameDue) && last("tbt ").startsWith("tbt 67 ") && last("tbt ").endsWith(" 0 -1") && !t.samePend);
        check("stage 6 L2: the timer doesn't come back (one-shot)", !h.pending.contains(t.nameDue));
        t.nextTurnDistance(400, 20, 400, METERS);
        check("stage 6 L2: then its own distance (400 m): far as a new turn", last("tbt ").startsWith("tbt 67 ") && last("tbt ").contains(" 400.0 "));
        // 거리가 제때 오면 기한 취소
        t.nextTurnDistance(15, 2, 15, METERS);
        t.nextTurn("Road C", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(380, 20, 380, METERS);
        check("stage 6 L2: the distance came in time: the timer is cancelled", !h.pending.contains(t.nameDue) && !t.samePend);
        // 새 이벤트·끝·포커스 순정이면 취소
        t.nextTurnDistance(15, 2, 15, METERS);
        t.nextTurn("Road D", RIGHT, TURN, -1, 0);
        t.nextTurn("Road E", LEFT, TURN, -1, 0);
        check("stage 6 L2: a different turn event cancels the timer", !h.pending.contains(t.nameDue));
        t.nextTurnDistance(15, 2, 15, METERS);
        t.nextTurn("Road F", LEFT, TURN, -1, 0);
        t.stop();
        check("stage 6 L2: focus to the car's navigation (stop) cancels the timer", !h.pending.contains(t.nameDue) && !t.samePend);
        t.focusPhone();
        t.nextTurn("Road F", LEFT, TURN, -1, 0);
        t.nextTurnDistance(15, 2, 15, METERS);
        t.nextTurn("Road G", LEFT, TURN, -1, 0);
        t.end();
        check("stage 6 L2: the connection end cancels the timer", !h.pending.contains(t.nameDue) && !t.samePend);
    }

    /**
     * A-2 규칙 시나리오 표의 한 줄: 가짜 시계 하나로 이벤트·거리·기다림을 돌리고, 기한 타이머는 걸린 시각에 돈다.
     * 계기판 tbt 프레임마다 시각을 붙여 "지난 회전 그림이 남은 시간"과 "아이콘이 바뀐 횟수"를 잰다.
     */
    static final class Sc {
        static final int RIGHT = 2, LEFT = 1, UNSPEC = 3, FORK = 9, NAME = 2, TURN = 4, METERS = 1;
        final Cluster cl = new Cluster();
        final Handler h = new Handler();
        final AaTurnsFit t;
        /** {시각, 표시 번호} */
        final List<long[]> frames = new java.util.ArrayList<long[]>();
        long passAt = -1, markAt;
        java.util.Set<Integer> old = new java.util.HashSet<Integer>();

        Sc(boolean nameHold) {
            Car.unit = Car.DISTANCE_KM;
            t = new AaTurnsFit(new android.content.Context(), cl, h);
            t.nameHold = nameHold;
            t.clock = 90000000L;
            sync();
            t.status(1);
            stamp();
        }

        void sync() { Handler.now = t.clock; }

        /** 1.7.4: 보낸 tbt 줄 전체(인자까지 보는 시험용) */
        final List<String> tbts = new java.util.ArrayList<String>();
        /** 1.7.4 재리뷰: 계기판·HUD로 보낸 모든 호출(프레임 전체 비교용) */
        final List<String> all = new java.util.ArrayList<String>();

        /** all에서 from번째부터 끝까지 */
        List<String> since(int from) { return new java.util.ArrayList<String>(all.subList(from, all.size())); }

        void stamp() {
            all.addAll(cl.calls);
            for (String x : cl.calls) if (x.startsWith("tbt ")) {
                frames.add(new long[] { t.clock, Integer.parseInt(x.split(" ")[1]) });
                tbts.add(x);
            }
            cl.calls.clear();
        }

        Sc ev(String road, int side, int e) { sync(); t.nextTurn(road, side, e, -1, 0); stamp(); return this; }
        Sc dist(int m) { sync(); t.nextTurnDistance(m, 20, m, METERS); stamp(); return this; }
        Sc st(int s) { sync(); t.status(s); stamp(); return this; }

        /** ms만큼 시간이 흐른다: 그 사이 걸린 타이머를 시각 차례로 돌린다 */
        Sc after(long ms) {
            long until = t.clock + ms;
            while (true) {
                Runnable next = null;
                long at = Long.MAX_VALUE;
                for (Runnable r : h.pending) {
                    Long d = h.dueAt.get(r);
                    if (d != null && d <= until && d < at) { at = d; next = r; }
                }
                if (next == null) break;
                t.clock = Math.max(t.clock, at);
                sync();
                h.runPending(next);
                stamp();
            }
            t.clock = until;
            sync();
            return this;
        }

        /** 지금 회전을 지났다(또는 끝났다): 이 표시 번호들이 "지난 회전 그림" */
        Sc pass(int... displays) {
            passAt = t.clock;
            old.clear();
            for (int d : displays) old.add(d);
            return this;
        }

        Sc mark() { markAt = t.clock; return this; }

        /** 지난 회전 그림이 pass 뒤에 남은 시간(ms). 끝까지 남았으면 지금까지 */
        long oldLeft() {
            int shown = -1;
            for (long[] f : frames) if (f[0] <= passAt) shown = (int) f[1];
            if (!old.contains(shown)) {
                // pass 때 이미 다른 그림이었더라도 뒤에 지난 그림이 다시 나가면 그 길이를 잰다
                shown = -1;
            }
            long start = old.contains(shown) ? passAt : -1, worst = 0;
            for (long[] f : frames) {
                if (f[0] < passAt) continue;
                boolean isOld = old.contains((int) f[1]);
                if (isOld && start < 0) start = f[0];
                if (!isOld && start >= 0) { worst = Math.max(worst, f[0] - start); start = -1; }
            }
            if (start >= 0) worst = Math.max(worst, t.clock - start);
            return worst;
        }

        /** mark 뒤 아이콘(표시 번호)이 바뀐 횟수 */
        int changes() {
            int n = 0, prev = Integer.MIN_VALUE;
            for (long[] f : frames) {
                if (f[0] <= markAt) { prev = (int) f[1]; continue; }
                if (prev != Integer.MIN_VALUE && f[1] != prev) n++;
                prev = (int) f[1];
            }
            return n;
        }

        boolean sent(int display) {
            for (long[] f : frames) if (f[0] >= markAt && f[1] == display) return true;
            return false;
        }
    }

    /** 한 줄 확인: 지난 그림 최대 ms, 아이콘 바뀜 최대 횟수 */
    static void row(String name, Sc x, long maxOld, int maxChanges, boolean extra) {
        long o = x.passAt < 0 ? 0 : x.oldLeft();
        int ch = x.changes();
        check(String.format("A-2 table: %-62s old picture %5d ms (max %5d), icon changes %d (max %d)", name, o, maxOld, ch, maxChanges),
                o <= maxOld && ch <= maxChanges && extra);
    }

    /** A-2 규칙 시나리오 표(지금까지 리뷰가 재현한 꼴 모두) */
    static void scenarioTable() {
        final int R = Sc.RIGHT, L = Sc.LEFT, U = Sc.UNSPEC, FORK = Sc.FORK, NAME = Sc.NAME, TURN = Sc.TURN;
        // 1. #65 번갈아(300m 근처, 거리가 옴): 갈림길 아이콘만, 이름 바뀜(65) 없음
        Sc x = new Sc(true);
        x.ev("Fork", R, FORK).dist(700).after(1000).mark();
        int m = 680;
        for (int k = 0; k < 12; k++) {
            x.after(1500).ev(k % 2 == 0 ? "Name" : "Fork", k % 2 == 0 ? U : R, k % 2 == 0 ? NAME : FORK);
            x.after(500).dist(m -= 40);
        }
        row("#65 alternation around 300 m, distances coming", x, 0, 1, !x.sent(65));
        // 2. #65 번갈아 40m 정체(같은 거리가 옴): 깜빡임 없음
        x = new Sc(true);
        x.ev("Fork", R, FORK).dist(40).mark();
        for (int k = 0; k < 10; k++) x.after(1500).ev(k % 2 == 0 ? "Name" : "Fork", k % 2 == 0 ? U : R, k % 2 == 0 ? NAME : FORK).after(500).dist(40);
        row("#65 alternation stopped at 40 m, distance repeated", x, 0, 0, !x.sent(65));
        // 3. #65 번갈아 40m 정체, 거리가 안 옴: 2초 기한마다 풀림(원칙: 깜빡임을 받아들임)
        x = new Sc(true);
        x.ev("Fork", R, FORK).dist(40).mark();
        for (int k = 0; k < 4; k++) x.after(3000).ev(k % 2 == 0 ? "Name" : "Fork", k % 2 == 0 ? U : R, k % 2 == 0 ? NAME : FORK);
        x.after(3000);
        row("#65 alternation stopped at 40 m, no distance, every 3 s (accepted flicker)", x, 0, 4, x.sent(65));
        // 3b. 같은 꼴인데 1초마다(기한 2초 안): 깜빡임 없음
        x = new Sc(true);
        x.ev("Fork", R, FORK).dist(40).mark();
        for (int k = 0; k < 6; k++) x.after(1000).ev(k % 2 == 0 ? "Name" : "Fork", k % 2 == 0 ? U : R, k % 2 == 0 ? NAME : FORK);
        row("#65 alternation stopped at 40 m, no distance, every 1 s", x, 0, 0, !x.sent(65));
        // 4. 회전 직후 가까운 다음 지점(10m → 55m): 지난 좌회전 그림은 다음 거리 표본까지만
        x = new Sc(true);
        x.ev("Left", L, TURN).dist(300).after(1000).dist(100).after(1000).dist(10).after(500).pass(13, 70).mark();
        x.ev("Next", U, NAME).after(1500).dist(55).after(1500).dist(52).after(1500).dist(49).after(1500).dist(46);
        row("passed a turn, next point close (10 m -> 55, 52, 49 m)", x, 1500, 1, !x.sent(13) || x.oldLeft() <= 1500);
        // 5. 60m → 이름 바뀜 → 70, 67 … 52m
        x = new Sc(true);
        x.ev("Left", L, TURN).dist(300).after(1000).dist(60).after(500).pass(13, 70).mark();
        x.ev("Next", U, NAME).after(1000).dist(70);
        for (int d = 67; d >= 52; d -= 3) x.after(1000).dist(d);
        row("passed a turn at 60 m, next distances 70, 67 ... 52 m", x, 1000, 1, true);
        // 6. 회전 직후 정차(거리가 안 옴)
        x = new Sc(true);
        x.ev("Left", L, TURN).dist(300).after(1000).dist(20).after(500).pass(13, 70).mark();
        x.ev("Next", U, NAME).after(60000);
        row("passed a turn, then stopped (no distance for 60 s)", x, AaTurnsFit.PEND_WAIT_MS, 1, x.sent(65));
        // 6b. 붙잡은 뒤 정차(실제 회전 이벤트도 거리도 안 옴): 붙잡기 한도(8초)에서 풀림
        x = new Sc(true);
        x.ev("Fork", R, FORK).dist(300).after(1000).ev("Name", U, NAME).after(500).dist(300).pass(25, 173).mark();
        x.after(60000);
        row("held, then stopped (nothing for 60 s): the 8 s hold limit", x, AaTurnsFit.NAME_HOLD_MS, 1, x.sent(65) && !x.t.nameHeld);
        // 7. 같은 방향 연속 갈림길, 거리 먼저: B의 번갈아도 막힘
        x = new Sc(true);
        x.ev("Fork A", R, FORK).dist(300).after(1000).dist(10).after(500).pass(25).mark();
        x.ev("Fork B", R, FORK).after(100).dist(900);
        m = 900;
        for (int k = 0; k < 6; k++) x.after(1500).ev(k % 2 == 0 ? "Name" : "Fork B", k % 2 == 0 ? U : R, k % 2 == 0 ? NAME : FORK).after(500).dist(m -= 30);
        row("consecutive same-side forks, B's distance first, then B alternates", x, 100, 1, !x.sent(65));
        // 8. 같은 방향 연속 갈림길, 이름 먼저(B 이벤트 → 이름 바뀜 → B의 거리)
        x = new Sc(true);
        x.ev("Fork A", R, FORK).dist(300).after(1000).pass(25).mark();
        x.ev("Fork B", R, FORK).after(300).ev("Name", U, NAME).after(300).dist(900).after(1000).dist(870).after(1000).dist(840);
        row("consecutive same-side forks, the name change before B's distance", x, 600, 1, !x.sent(65));
        // 9. 같은 회전의 이름만 A↔B 2초마다, 거리가 옴(300m)
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(300).mark();
        m = 300;
        for (int k = 0; k < 10; k++) x.after(2000).ev(k % 2 == 0 ? "Road B" : "Road A", R, TURN).after(10).dist(m -= 5);
        row("same turn, road name A<->B every 2 s, distances coming (300 m)", x, 0, 0, true);
        // 10. 같은 회전의 이름만 A↔B 2초마다, 거리가 안 옴(정차 300m)
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(300).mark();
        for (int k = 0; k < 10; k++) x.after(2000).ev(k % 2 == 0 ? "Road B" : "Road A", R, TURN);
        x.after(3000);
        row("same turn, road name A<->B every 2 s, no distance (stopped at 300 m)", x, 0, 0, x.frames.get(x.frames.size() - 1)[1] == 13);
        // 11. 380m 띠(D와 D+50 사이)에서 이름만 바뀜
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(340).after(1000).dist(380).mark();
        for (int k = 0; k < 4; k++) x.after(2000).ev(k % 2 == 0 ? "Road B" : "Road A", R, TURN).after(10).dist(382 - k);
        row("same turn, road name changes in the 380 m hysteresis band", x, 0, 0, true);
        // 12. 이름만 다른 같은 방향 다음 회전, 앞 회전을 15m에서 지나고 거리가 안 옴
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(200).after(1000).dist(15).after(500).pass(13).mark();
        x.ev("Road B", R, TURN).after(10000);
        row("next same-side turn with another name after 15 m, no distance", x, AaTurnsFit.PEND_WAIT_MS, 1, !x.t.samePend);
        // 13. 재탐색 시작·끝(판단을 기다리는 중)
        x = new Sc(true);
        x.ev("Fork", R, FORK).dist(300).after(500).ev("Name", U, NAME).after(300).pass(25).mark();
        x.st(3).after(3000).st(1).after(3000);
        row("rerouting starts and ends while a name change waits", x, 300, 2, !x.sent(25) && x.h.pending.isEmpty());
        // 13b. 재탐색 중에 이름만 다른 같은 꼴 이벤트(판단 기다림)가 생긴 채 재탐색이 끝남: 옛 거리로 바로 보내지 않음(L1)
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(300).after(10).st(3).ev("Road A", R, TURN).dist(320).ev("Road B", R, TURN).after(10).mark();
        boolean waiting = x.t.samePend;
        x.st(1);
        int framesAtEnd = x.frames.size();
        x.after(100).dist(900);
        row("rerouting ends while a same-shape event waits: nothing with the old distance", x, 0, 1,
                waiting && framesAtEnd == x.frames.size() - 1 && !x.sent(13));
        // 1.7.4 T-6 ①(리뷰 중간 1): 재탐색 끝 뒤 폰이 아무것도 다시 주지 않으면 3초 뒤 그 회전을 거리 없이 한 번
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).st(1);
        int n0 = x.tbts.size();
        boolean armed3 = x.h.pending.contains(x.t.rerouteDue);
        x.after(2999);
        boolean quiet = x.tbts.size() == n0;
        x.after(1);
        String due = x.tbts.size() == n0 + 1 ? x.tbts.get(n0) : null;
        x.after(20000);
        check("1.7.4 T-6 1 (review M1): rerouting over, nothing from the phone: nothing at once or within 3 s (timer armed)",
                armed3 && quiet && n0 > 0 && "tbt 10 0 0.0 0.0 -3 0 15 0 0".equals(x.tbts.get(n0 - 1)));
        // 재리뷰 낮음(결정): 새 꼴을 만들지 않고 "거리가 아직 없는 회전" 갈래(거리 -1, 막대 254·-2)로. 단위 칸(1, km)은 그 갈래 그대로 남음
        check("1.7.4 T-6 1 (review M1, re-review): at 3 s the turn goes once on the existing no-distance path (no old 1.5 km) [" + due + "]",
                "tbt 67 0 0.0 -1.0 0 0 1 254 -2".equals(due));
        check("1.7.4 T-6 1 (review M1): only once (nothing more after 20 s, no timer left)",
                x.tbts.size() == n0 + 1 && !x.h.pending.contains(x.t.rerouteDue));
        // 3초 안에 거리가 오면 그 거리로 나가고 기한은 보내지 않음
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).st(1).after(1000).dist(1400);
        n0 = x.tbts.size();
        x.after(10000);
        check("1.7.4 T-6 1 (review M1): a distance within 3 s: the turn goes with that distance, the timer sends nothing",
                n0 > 0 && x.tbts.get(n0 - 1).contains(" 0.0 1.4 ") && x.tbts.size() == n0 && !x.h.pending.contains(x.t.rerouteDue));
        // 3초 안에 새 회전·새 재탐색·안내 끝·포커스 순정·연결 끝: 기한이 풀림
        String[] why = { "turn", "reroute", "end of guidance", "focus native", "connection end" };
        for (int k = 0; k < why.length; k++) {
            x = new Sc(true);
            x.ev("Road A", R, TURN).dist(1500).st(3).st(1).after(1000);
            if (k == 0) x.ev("Road B", L, TURN);
            if (k == 1) x.st(3);
            if (k == 2) x.st(2);
            if (k == 3) { x.t.stop(); x.stamp(); }
            if (k == 4) { x.t.end(); x.stamp(); }
            n0 = x.tbts.size();
            boolean dropped = !x.h.pending.contains(x.t.rerouteDue);
            x.after(10000);
            boolean noLate = true;
            for (int j = n0; j < x.tbts.size(); j++) if (x.tbts.get(j).endsWith(" 0 0.0 0.0 -3 0 15 0 -1") && !x.tbts.get(j).startsWith("tbt 8 ")) noLate = false;
            check("1.7.4 T-6 1 (review M1): " + why[k] + " within 3 s releases the timer (nothing sent by it)", dropped && noLate);
        }
        // 포커스가 순정에서 폰으로 돌아옴: 기한 풀림(재탐색 끝 뒤 포커스가 오가는 경우)
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).st(1);
        x.t.focusNative = true;
        x.t.focusPhone();
        check("1.7.4 T-6 1 (review M1): focus back to the phone releases the timer", !x.h.pending.contains(x.t.rerouteDue));
        // 기한은 하나만: 재탐색이 짧게 두 번 끝나도 한 번만 보냄
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).st(1).after(1000).st(3).st(1);
        n0 = x.tbts.size();
        x.after(10000);
        int late = x.tbts.size() - n0;
        int pend = 0;
        for (Runnable r : x.h.pending) if (r == x.t.rerouteDue) pend++;
        check("1.7.4 T-6 1 (review M1): two reroutes in a row: one timer, one send", late == 1 && pend == 0);
        // 판단 기다림(samePend·namePend) 기한과 부딪치지 않음: 기한이 돌 때 판단 중이면 보내지 않음
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).st(1);
        x.t.samePend = true;
        n0 = x.tbts.size();
        x.after(3000);
        check("1.7.4 T-6 1 (review M1): the timer sends nothing while a same-shape/name decision waits", x.tbts.size() == n0);
        // 재리뷰 중간 1: 재탐색 전 거리 1.5km가 재탐색 뒤 어느 길로도 나가지 않음(프레임 전체 비교)
        // (A) 재탐색 중 회전만 오고 거리는 안 옴 → 끝나는 순간 그 회전을 거리 없이
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3);
        x.ev("Road B", L, TURN);
        int a0 = x.all.size();
        x.st(1);
        check("1.7.4 re-review (A): a turn during rerouting, no distance: at the end, whole frames = no-distance turn + HUD list " + x.since(a0),
                x.since(a0).equals(java.util.Arrays.asList("tbt 70 0 0.0 -1.0 0 0 1 254 -2", "hud f201460000" + utf16("Road B"))));
        // (B) 3초 기한 뒤 새 회전이 거리보다 먼저 옴
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).st(1).after(3000);
        a0 = x.all.size();
        x.ev("Road C", L, TURN);
        check("1.7.4 re-review (B): after the 3 s timer a new turn comes before any distance: no-distance turn " + x.since(a0),
                x.since(a0).equals(java.util.Arrays.asList("tbt 70 0 0.0 -1.0 0 0 1 254 -2", "hud f201460000" + utf16("Road C"))));
        // (D) 끝나고 3초 안에 이름 바뀜(ev 2)이 옴
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).st(1).after(1000);
        a0 = x.all.size();
        x.ev("Road D", U, NAME).after(5000);
        boolean old15 = false;
        for (String y : x.since(a0)) if (y.contains(" 1.5 ") || y.startsWith("2c ")) old15 = true;
        check("1.7.4 re-review (D): a name change within 3 s of the end: no 1.5 km and no HUD distance anywhere " + x.since(a0),
                !x.since(a0).isEmpty() && !old15 && x.since(a0).get(0).contains(" -1.0 0 0 1 254 -2"));
        // 옛 거리가 HUD 거리(2C)로도 나가지 않음: (A)~(D) 모두 재탐색 뒤 2C 없음은 위에서. 재탐색 뒤 새 거리가 오면 표시가 내려가 정상
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).st(1).after(500);
        a0 = x.all.size();
        x.dist(1200);
        boolean got12 = false, got2c = false;
        for (String y : x.since(a0)) {
            if (y.startsWith("tbt ") && y.contains(" 1.2 ")) got12 = true;
            if (y.equals("2c 1 2 1 0 0 65535 0 0 65535")) got2c = true;
        }
        x.after(500);
        a0 = x.all.size();
        x.ev("Road E", L, TURN);
        check("1.7.4 re-review: the first distance after rerouting clears the mark: tbt and HUD 2C carry it, a later turn is normal",
                got12 && got2c && !x.t.distStale && !x.since(a0).get(0).contains("-1.0"));
        // 재탐색 중에 온 거리는 표시를 내리지 않음(결정)
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).dist(900);
        check("1.7.4 re-review: a distance during rerouting keeps the mark (unknown route)", x.t.distStale);
        // 안내 끝·연결 끝에서 표시가 초기화됨
        x.st(2);
        boolean afterEnd = !x.t.distStale;
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3);
        x.t.end();
        check("1.7.4 re-review: the end of guidance and the connection end reset the mark", afterEnd && !x.t.distStale);
        // 3차 리뷰 중간 1: 거리 낡음 프레임이 64 미만 아이콘(목적지 3, 회전교차로 출구 32)을 2로 바꾸지 않고, 필드에도 남기지 않음
        int[][] small = { { 19, 3 }, { 12, 32 } };   // {폰 이벤트(DESTINATION, ROUNDABOUT_EXIT), 아이콘}
        for (int[] sm : small) {
            x = new Sc(true);
            x.ev("Road A", R, TURN).dist(1500).st(3).ev("Goal", R, sm[0]);
            a0 = x.all.size();
            x.st(1);
            String staleF = x.tbts.isEmpty() ? null : x.tbts.get(x.tbts.size() - 1);
            x.after(500).dist(700);
            String next = x.tbts.get(x.tbts.size() - 1);
            check("1.7.4 3rd review M1: icon " + sm[1] + " after rerouting: the stale frame keeps the phone's icon, 700 m then 'tbt " + sm[1] + " … 700.0' ["
                    + staleF + " / " + next + "]",
                    ("tbt " + sm[1] + " 0 0.0 -1.0 0 0 1 254 -2").equals(staleF) && next.startsWith("tbt " + sm[1] + " ") && next.contains(" 700.0 ")
                            && x.t.displayType == sm[1]);
            // 3초 기한 경로도 같음
            x = new Sc(true);
            x.ev("Goal", R, sm[0]).dist(1500).st(3).st(1).after(3000);
            String dueF = x.tbts.get(x.tbts.size() - 1);
            x.dist(700);
            String next2 = x.tbts.get(x.tbts.size() - 1);
            check("1.7.4 3rd review M1: icon " + sm[1] + " on the 3 s timer path: same [" + dueF + " / " + next2 + "]",
                    ("tbt " + sm[1] + " 0 0.0 -1.0 0 0 1 254 -2").equals(dueF) && next2.startsWith("tbt " + sm[1] + " ") && next2.contains(" 700.0 "));
        }
        // 3차 리뷰 낮음 2: 재탐색 전 앞쪽 회전(거리 800m)은 재탐색 뒤 목록·HUD 다음 칸으로 나가지 않음. 재탐색 뒤 새로 준 것만
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500);
        x.t.nextTurn("Old Ahead", L, 64, 270, Sc.TURN | 255 << 8);
        x.t.nextTurnDistance(800, 0, 800, Sc.METERS | 0x200);
        x.stamp();
        x.st(3).st(1);
        a0 = x.all.size();
        x.t.nextTurn(null, 0, 67, 0, 1);
        x.stamp();
        boolean oldAhead = false;
        for (String y : x.since(a0)) if (y.contains("Old Ahead")) oldAhead = true;
        check("1.7.4 3rd review L2: the turns ahead from before rerouting are not listed after it " + x.since(a0), !oldAhead && x.since(a0).isEmpty());
        x.t.nextTurn("New Ahead", L, 64, 270, Sc.TURN | 255 << 8);
        x.t.nextTurnDistance(600, 0, 600, Sc.METERS | 0x200);
        a0 = x.all.size();
        x.t.nextTurn(null, 0, 67, 0, 1);
        x.stamp();
        check("1.7.4 3rd review L2: a turn ahead the phone sends after rerouting is listed " + x.since(a0),
                x.since(a0).size() == 1 && x.since(a0).get(0).startsWith("list {1 ") && x.since(a0).get(0).contains("[New Ahead]"));
        // 재탐색 없는 경로: 앞쪽 회전은 그대로 나감
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500);
        x.t.nextTurn("Kept Ahead", L, 64, 270, Sc.TURN | 255 << 8);
        x.t.nextTurnDistance(800, 0, 800, Sc.METERS | 0x200);
        x.stamp();
        a0 = x.all.size();
        x.t.nextTurn(null, 0, 67, 0, 1);
        x.stamp();
        check("1.7.4 3rd review L2: without rerouting the turns ahead are listed as before", x.since(a0).size() == 1 && x.since(a0).get(0).contains("[Kept Ahead]"));
        // 리뷰 낮음 5: 재탐색 → 회전 → 안내 끝 → 새 경로가 재탐색으로 시작 → 끝: 옛 회전을 보내지 않음(바로도, 3초 뒤도)
        x = new Sc(true);
        x.ev("Road A", R, TURN).dist(1500).st(3).ev("Road B", R, TURN).st(2).st(3);
        n0 = x.tbts.size();
        x.st(1).after(10000);
        boolean old = false;
        for (int j = n0; j < x.tbts.size(); j++) if (!x.tbts.get(j).startsWith("tbt 10 ") && !x.tbts.get(j).startsWith("tbt 8 ")) old = true;
        check("1.7.4 T-6 1 (review L5): reroute, turn, guidance end, a new route starting with a reroute, its end: no old turn sent",
                !old && !x.h.pending.contains(x.t.rerouteDue));
        // 14. 안내 끝(판단을 기다리는 중)
        x = new Sc(true);
        x.ev("Fork", R, FORK).dist(300).after(500).ev("Name", U, NAME).after(300).pass(25).mark();
        x.st(2).after(5000);
        row("guidance ends while a name change waits", x, 0, 1, x.h.pending.isEmpty() && x.frames.get(x.frames.size() - 1)[1] == 8);
        // 15. end() 단독: 안내 시작 전 기한이 걸린 채(이름만 다른 같은 꼴 이벤트)
        Sc y = new Sc(true);
        y.st(2);
        y.ev("Road A", R, TURN).dist(300).ev("Road B", R, TURN).mark();
        boolean armed = y.h.pending.contains(y.t.nameDue);
        y.t.end();
        y.stamp();
        y.after(5000);
        row("end() alone, before guidance, with the timer armed", y, 0, 0, armed && y.h.pending.isEmpty());
        // 16. 연결 끝 뒤 기한: 아무것도 나가지 않음
        x = new Sc(true);
        x.ev("Fork", R, FORK).dist(300).after(500).ev("Name", U, NAME).mark();
        x.t.end();
        x.stamp();
        int after = x.frames.size();
        x.after(10000);
        row("the timer after the connection end", x, 0, 1, x.frames.size() == after && x.h.pending.isEmpty());
        // 17. 설정 aa_name_hold 끔: 이름 바뀜은 바로(2.4.0), 이름만 바뀌면 거리는 유지
        x = new Sc(false);
        x.ev("Fork", R, FORK).dist(300).mark();
        for (int k = 0; k < 4; k++) x.after(2000).ev(k % 2 == 0 ? "Name" : "Fork", k % 2 == 0 ? U : R, k % 2 == 0 ? NAME : FORK).after(10).dist(290 - k * 10);
        row("setting aa_name_hold off: #65 alternation flickers as 2.4.0", x, 0, 8, x.sent(65) && x.h.pending.isEmpty());
        x = new Sc(false);
        x.ev("Road A", R, TURN).dist(300).mark();
        for (int k = 0; k < 4; k++) x.after(2000).ev(k % 2 == 0 ? "Road B" : "Road A", R, TURN).after(10).dist(295 - k * 5);
        row("setting off: a road-name-only change still keeps the distance (no far)", x, 0, 0, true);

        // L3: Handler가 직접 돌리는 타이머(nameDue, tick) 안에서 예외가 나도 밖으로 나가지 않는다(나가면 GPM이 죽음)
        x = new Sc(true);
        final List<String> lines = new java.util.ArrayList<String>();
        x.t.log = new AaTurns.Sink() {
            @Override
            public void line(String s2) {
                lines.add(s2);
            }
        };
        x.ev("Fork", R, FORK).dist(300).after(500).ev("Name", U, NAME);
        boolean threw = false;
        try {
            java.lang.reflect.Field f = AaTurnsFit.class.getDeclaredField("cluster");
            f.setAccessible(true);
            f.set(x.t, null);   // 계기판 호출에서 NullPointerException이 나게
            x.after(AaTurnsFit.PEND_WAIT_MS + 10);
            x.t.tick.run();
            x.t.tripMeters = 100;
            x.t.tick.run();
        } catch (Throwable e) {
            threw = true;
        }
        check("L3: an exception inside nameDue/tick stays inside (no crash), logged once with its type only",
                !threw && lines.contains("AA: timer error NullPointerException") && java.util.Collections.frequency(lines, "AA: timer error NullPointerException") == 1);
    }

    /** 2.4.3: 마지막 tbt 줄의 {표시, 막대}(없으면 null) */
    static int[] dispBar() {
        String t = last("tbt ");
        if (t == null) return null;
        String[] p = t.split(" ");
        return new int[] { Integer.parseInt(p[1]), Integer.parseInt(p[8]) };
    }

    static boolean db(int disp, int bar) {
        int[] x = dispBar();
        return x != null && x[0] == disp && x[1] == bar;
    }

    /**
     * 2.4.3 D값(소유자 결정 2026-11-15): 이 회전이 안내되기 시작한 뒤 처음 남은 거리가 기준(350m·1050m)보다 짧으면 그 거리가 D.
     * 큰 그림 진입·막대가 같은 D를 쓴다. D_MIN 30m. 시각에 기대지 않는다(거리 순서만).
     */
    static void dynamicD() {
        final int RIGHT = 2, LEFT = 1, TURN = 4, OFF_RAMP = 8, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        check("2.4.3 D rule: max(30, min(base, first))", AaTurnsFit.dynamicD(350, 100) == 100 && AaTurnsFit.dynamicD(350, 500) == 350
                && AaTurnsFit.dynamicD(1050, 1500) == 1050 && AaTurnsFit.dynamicD(350, 10) == 30 && AaTurnsFit.D_MIN == 30);
        // ① 좌회전 직후 100m 앞 우회전: D 100m, 막대 가득에서 시작해 줄어듦
        c = new Cluster();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Left Rd", LEFT, TURN, -1, 0);
        t.nextTurnDistance(40, 4, 40, METERS);
        t.nextTurn("Right Rd", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(100, 10, 100, METERS);
        check("2.4.3 D (1): a right turn 100 m after a left turn: near at once, bar 90 (D 100 m, not a third of 350 m)", db(13, 90));
        t.nextTurnDistance(50, 5, 50, METERS);
        check("2.4.3 D (1): at 50 m: bar 50 (D 100 m)", db(13, 50));
        t.nextTurnDistance(10, 1, 10, METERS);
        check("2.4.3 D (1): at 10 m: bar 10", db(13, 10));
        // ② 일반도로 처음 500m: 350m에서 진입
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Road", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(500, 50, 500, METERS);
        check("2.4.3 D (2): first 500 m on a normal road: far", db(67, 0));
        t.nextTurnDistance(351, 35, 351, METERS);
        check("2.4.3 D (2): 351 m: still far (D 350 m)", db(67, 0));
        t.nextTurnDistance(350, 35, 350, METERS);
        check("2.4.3 D (2): 350 m: near, bar 90", db(13, 90));
        t.nextTurnDistance(175, 17, 175, METERS);
        check("2.4.3 D (2): 175 m: bar 50", db(13, 50));
        // ③ 고속도로 출구 처음 1500m: 1050m에서 진입
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Exit", RIGHT, OFF_RAMP, -1, 0);
        t.nextTurnDistance(1500, 60, 1500, METERS);
        check("2.4.3 D (3): highway exit at 1500 m: far (164)", db(164, 0));
        t.nextTurnDistance(1050, 50, 1050, METERS);
        check("2.4.3 D (3): 1050 m: near 17, bar 90", db(17, 90));
        t.nextTurnDistance(525, 30, 525, METERS);
        check("2.4.3 D (3): 525 m: bar 50", db(17, 50));
        // ③' 고속도로 출구 처음 600m: D 600m
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Exit", RIGHT, OFF_RAMP, -1, 0);
        t.nextTurnDistance(600, 30, 600, METERS);
        check("2.4.3 D (3'): highway exit first seen at 600 m: near at once, bar 90 (D 600 m)", db(17, 90));
        t.nextTurnDistance(300, 20, 300, METERS);
        check("2.4.3 D (3'): 300 m: bar 50", db(17, 50));
        // ④ 처음 거리 없음: 거리가 올 때까지 멀리 있음, 처음 온 거리로 D
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Road", RIGHT, TURN, -1, 0);
        check("2.4.3 D (4): a turn with no distance yet: far (no near layout without a distance)", db(67, 0));
        t.nextTurnDistance(200, 20, 200, METERS);
        check("2.4.3 D (4): its first distance 200 m: near, bar 90 (D 200 m)", db(13, 90));
        // ⑤ 거리가 늘어남: 조금 늘면 D도 늘고(기준까지), 크게 늘면(100m 넘게) 새 회전으로 다시 잡음
        t.nextTurnDistance(150, 15, 150, METERS);
        check("2.4.3 D (5): 150 m: bar 70 (D 200 m)", db(13, 70));
        t.nextTurnDistance(240, 24, 240, METERS);
        check("2.4.3 D (5): back to 240 m (not a jump of 100 m): D grows to 240 m, still near, bar 90", db(13, 90));
        t.nextTurnDistance(120, 12, 120, METERS);
        check("2.4.3 D (5): 120 m: bar 50 (D 240 m)", db(13, 50));
        t.nextTurnDistance(600, 60, 600, METERS);
        check("2.4.3 D (5): a jump to 600 m (the car went back or another turn of the same kind): far (D 350 m again)", db(67, 0));
        t.nextTurnDistance(175, 17, 175, METERS);
        check("2.4.3 D (5): then 175 m: bar 50 (D 350 m)", db(13, 50));
        // ⑥ 재탐색: 재탐색이 끝난 뒤 처음 거리로 D를 다시 잡는다. 재탐색 끝에 옛 회전을 다시 보내지 않음(T-6 ①)
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Road", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(500, 50, 500, METERS);
        t.nextTurnDistance(300, 30, 300, METERS);
        check("2.4.3 D (6): before rerouting: 300 m of a 500 m turn: bar 80 (D 350 m)", db(13, 80));
        t.status(3);
        c.calls.clear();
        t.status(1);
        check("2.4.3 D (6, T-6 1): rerouting over with no turn meanwhile: the old turn is not sent again", last("tbt ") == null);
        t.nextTurnDistance(120, 12, 120, METERS);
        check("2.4.3 D (6): first distance after rerouting 120 m: near, bar 90 (D caught again: 120 m)", db(13, 90));
        // ⑦ 아주 짧은 처음 거리: D_MIN 30m 바닥
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Road", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(10, 1, 10, METERS);
        check("2.4.3 D (7): a turn first seen at 10 m: D 30 m (floor), bar 30, not 90 that drops at once", db(13, 30));
        // ⑧ 경계 여유는 그 회전의 D 기준: D 200m에서 250m(D + 50)까지 곧 회전 유지
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Road", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(200, 20, 200, METERS);
        t.nextTurnDistance(195, 20, 195, METERS);
        check("2.4.3 D (8): D 200 m, the near layout on", db(13, 90));
        Car.unit = Car.DISTANCE_MILES;
    }

    /**
     * 2.4.3 리뷰 중간 1: 같은 종류·방향·각도의 회전이 이어질 때도 다음 회전의 처음 거리로 D를 다시 잡는다.
     * GPS 흔들림(같은 회전에서 거리가 조금 늘었다 줄어듦)에는 D를 다시 잡지 않는다.
     */
    static void sameKindD() {
        final int RIGHT = 2, TURN = 4, NAME = 2, FORK = 9, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        // A: aa_name_hold 켬
        c = new Cluster();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.clock = 90000000L;
        t.status(1);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        for (int d : new int[] { 600, 300, 20, 8 }) {
            t.clock += 1000L;
            t.nextTurnDistance(d, 1, d, METERS);
        }
        t.clock += 1000L;
        t.nextTurn("Road B", RIGHT, TURN, -1, 0);
        t.clock += 500L;
        t.nextTurnDistance(150, 15, 150, METERS);
        check("2.4.3 review M1 (A, name hold on): right turn Road A 600-300-20-8 m, then right turn Road B at 150 m: D 150 m, bar 90", db(13, 90));
        // B: aa_name_hold 끔
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.nameHold = false;
        t.clock = 91000000L;
        t.status(1);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(200, 20, 200, METERS);
        t.nextTurnDistance(5, 1, 5, METERS);
        t.nextTurn("Road B", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(90, 9, 90, METERS);
        check("2.4.3 review M1 (B, name hold off): right turn Road A 200-5 m, then right turn Road B at 90 m: D 90 m, bar 90", db(13, 90));
        // B': 열쇠도 이름도 같은 다음 회전(이름 없는 길): 앞 회전을 거의 지난 뒤 거리가 다시 늘면 새 회전
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.nameHold = false;
        t.status(1);
        t.nextTurn("", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(200, 20, 200, METERS);
        t.nextTurnDistance(10, 1, 10, METERS);
        t.nextTurnDistance(90, 9, 90, METERS);
        check("2.4.3 review M1 (B'): the same key with no new event, 10 m then 90 m (passed and the next one): D 90 m, bar 90", db(13, 90));
        // GPS 흔들림: 같은 회전 150 → 165 → 140: D 그대로(200), 막대가 튀지 않음
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Road", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(200, 20, 200, METERS);
        t.nextTurnDistance(150, 15, 150, METERS);
        check("2.4.3 wobble: 150 m of a 200 m turn: bar 70", db(13, 70));
        t.nextTurnDistance(165, 16, 165, METERS);
        check("2.4.3 wobble: 165 m (GPS wobble up): bar 80, D still 200 m (not caught again)", db(13, 80));
        t.nextTurnDistance(140, 14, 140, METERS);
        check("2.4.3 wobble: back to 140 m: bar 70 (D 200 m)", db(13, 70));
        // 가까이서 흔들림: 20m → 30m(10m 오름): 같은 회전
        t.nextTurnDistance(20, 2, 20, METERS);
        t.nextTurnDistance(30, 3, 30, METERS);
        check("2.4.3 wobble near the turn: 20 m then 30 m: still this turn (D 200 m, bar 10)", db(13, 10));
        // A-2: 같은 지점에서 갈림길·이름 바뀜이 번갈아(이름 붙잡기 켬): 갈림길 아이콘 유지, D 그대로
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.clock = 92000000L;
        t.status(1);
        t.nextTurn("Fork Rd", RIGHT, FORK, -1, 0);
        t.nextTurnDistance(300, 30, 300, METERS);
        t.clock += 2000L;
        t.nextTurn("Name Rd", 3, NAME, -1, 0);
        t.clock += 300L;
        t.nextTurnDistance(290, 29, 290, METERS);
        check("2.4.3 A-2 kept: fork then a name change for the same point: fork near 25 held, D 300 m (bar 90)", db(25, 90));
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 2.4.3 리뷰 낮음 5: 경계 여유는 그 회전의 D 기준(D 200m: 250m까지 유지, 251m에서 멀리 있음) */
    static void marginD() {
        final int RIGHT = 2, TURN = 4, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Road", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(200, 20, 200, METERS);
        t.nextTurnDistance(150, 15, 150, METERS);
        t.nextTurnDistance(250, 25, 250, METERS);
        check("2.4.3 margin: D 200 m, back to 250 m (D + 50): still near (D grows to 250 m, bar 90)", db(13, 90));
        c = new Cluster();
        t = new AaTurnsFit(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Road", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(500, 50, 500, METERS);
        t.nextTurnDistance(340, 34, 340, METERS);
        check("2.4.3 margin: base D 350 m: 340 m near", db(13, 90));
        t.nextTurnDistance(400, 40, 400, METERS);
        check("2.4.3 margin: 400 m (D + 50 = 400): still near", db(13, 90));
        t.nextTurnDistance(401, 40, 401, METERS);
        check("2.4.3 margin: 401 m: far", db(67, 0));
        t.nextTurnDistance(351, 35, 351, METERS);
        check("2.4.3 margin: 351 m after leaving: far (enter at D 350 m)", db(67, 0));
        t.nextTurnDistance(350, 35, 350, METERS);
        check("2.4.3 margin: 350 m: near again", db(13, 90));
        Car.unit = Car.DISTANCE_MILES;
    }

    /** 2.4.3 리뷰 낮음 5: tbt_fit에 따라 Wireless가 고르는 관리자(AaTurnsFit.make) */
    static void layerChoice() {
        AaTurns.Sink sink = new AaTurns.Sink() {
            @Override
            public void line(String x) {}
        };
        AaTbt on = AaTurnsFit.make(true, false, new android.content.Context(), new Cluster(), new Handler(), sink);
        AaTbt off = AaTurnsFit.make(false, true, new android.content.Context(), new Cluster(), new Handler(), sink);
        check("2.4.3 layer: tbt_fit on -> AaTurnsFit with aa_name_hold as set (off here) and the log", on instanceof AaTurnsFit
                && !((AaTurnsFit) on).nameHold && ((AaTurnsFit) on).log == sink);
        check("2.4.3 layer: tbt_fit off -> AaTurns (stock GPM, as 2.4.2) with the log", off instanceof AaTurns && ((AaTurns) off).log == sink);
        c = new Cluster();
        AaTbt s = AaTurnsFit.make(false, true, new android.content.Context(), c, new Handler(), sink);
        s.status(1);
        s.nextTurn("Road", 2, 4, -1, 0);
        s.nextTurnDistance(200, 20, 200, 1);
        check("2.4.3 layer: tbt_fit off at 200 m: the stock frame (67, side street 65535, bar 32), no near layout", "tbt 67 65535 90.0 200.0 0 0 0 32 0".equals(last("tbt ")));
    }

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
                java.util.Arrays.toString(AaTurnsFit.nearDisplay(TURN, RIGHT, 67, 90)).equals("[13, 4, 12]")
                        && java.util.Arrays.toString(AaTurnsFit.nearDisplay(TURN, LEFT, 70, 270)).equals("[13, 12, 36]")
                        && java.util.Arrays.toString(AaTurnsFit.nearDisplay(SLIGHT, RIGHT, 66, 30)).equals("[13, 2, 6]")
                        && java.util.Arrays.toString(AaTurnsFit.nearDisplay(SHARP, LEFT, 69, 225)).equals("[13, 10, 30]")
                        && java.util.Arrays.toString(AaTurnsFit.nearDisplay(U_TURN, LEFT, 72, 270)).equals("[31, -1, 36]")
                        && java.util.Arrays.toString(AaTurnsFit.nearDisplay(OFF_RAMP, RIGHT, 164, 30)).equals("[17, -1, 12]")
                        && java.util.Arrays.toString(AaTurnsFit.nearDisplay(OFF_RAMP, LEFT, 165, 330)).equals("[18, -1, 36]")
                        && java.util.Arrays.toString(AaTurnsFit.nearDisplay(FORK, RIGHT, 173, 0)).equals("[25, -1, 6]")
                        && java.util.Arrays.toString(AaTurnsFit.nearDisplay(RB_EXIT, 0, 99, 90)).equals("[99, 4, 12]")
                        && java.util.Arrays.toString(AaTurnsFit.nearDisplay(RB_EXIT, 0, 102, 180)).equals("[102, 8, 27]"));
        check("1.7.2 M-12 display: unknown side, name change, departure, destination, unknown roundabout: no near layout",
                AaTurnsFit.nearDisplay(TURN, 3, 64, 0) == null && AaTurnsFit.nearDisplay(NAME, 0, 65, 0) == null
                        && AaTurnsFit.nearDisplay(DEPART, 0, 2, 0) == null && AaTurnsFit.nearDisplay(DEST, 0, 3, 0) == null
                        && AaTurnsFit.nearDisplay(RB_EXIT, 0, 64, 0) == null && AaTurnsFit.nearDisplay(RB_EXIT_ONLY, RIGHT, 32, 90) == null);
        check("1.7.2 M-12 bar (as CarPlay): bar(300, 350) 80, bar(1, 350) 10", AaTurnsFit.bar(300, 350) == 80 && AaTurnsFit.bar(1, 350) == 10);

        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
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
        check("1.7.2 M-12 (owner 11-09): AA says no lanes now: still near inside D (lanes don't matter; 2.4.3: D = first 150 m, bar 90)",
                "tbt 13 16 90.0 140.0 0 0 0 90 0".equals(last("tbt ")) && !"road []".equals(last("road ")));
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
        // 1.7.4 T-6 ①(재리뷰 중간 1, 오케스트레이터 결정): 재탐색 중에 온 거리(880m)는 새 경로 것인지 알 수 없어 쓰지 않는다.
        // 2.4.1까지는 여기서 880m 곧 회전(tbt 17 … 880.0 … 80)이었다. 이제 거리가 아직 없는 회전 갈래(-1, 막대 254·-2)로 나가고
        // HUD는 목록만(2C 거리 없음). 차로는 재탐색 중에 다시 온 것(01)만 센다
        expect("1.7.4 AA rerouting over with a turn sent meanwhile: that turn at once, without the distance that came during rerouting",
                "tbt 164 0 0.0 -1.0 0 0 0 254 -2", "hud f201a40000" + utf16("Exit 5"));
        boolean nine = false;
        for (String x : c.calls) if (x.startsWith("tbt 9 ")) nine = true;
        check("1.7.2 AA: no route-calculating state in AA: display 9 never sent", !nine);
        c.calls.clear();
        // 리뷰(소유자 결정 11-09 뒤): 재탐색 중 새 거리가 오지 않으면 옛 경로 거리로 큰 그림을 띄우지 않는다(멀리 있음)
        t.status(3);
        c.calls.clear();
        t.status(1);
        // T-6 ①(1.7.4): 2.4.1까지는 여기서 옛 경로의 회전(164, 880m)을 다시 보냈다. 재탐색 중에 폰이 회전을 보내지 않았으면
        // 끝나도 아무것도 보내지 않는다(표시 10이 남고, 폰의 다음 회전·거리가 바꾼다). 옛 경로 거리로 곧 회전도 띄우지 않음(11-09 리뷰)은 그대로
        check("1.7.4 T-6 1: rerouting over with no turn from the phone meanwhile: the old route's turn is not sent again",
                last("tbt ") == null && c.calls.isEmpty());
        t.nextTurnDistance(700, 40, 700, METERS);
        check("1.7.2 AA: then its new distance: near again (2.4.3: after rerouting D is caught again from the first distance, 700 m, bar 90)",
                "tbt 17 0 90.0 700.0 0 0 0 90 0".equals(last("tbt ")));
        // T-6 ①: 재탐색 중 거리만 오고 회전은 안 옴 → 끝날 때 보내지 않고, 끝난 뒤 다음 거리에서 보냄
        t.status(3);
        t.nextTurnDistance(650, 40, 650, METERS);
        c.calls.clear();
        t.status(1);
        check("1.7.4 T-6 1: only a distance during rerouting: still nothing at its end", last("tbt ") == null);
        t.nextTurnDistance(600, 40, 600, METERS);
        check("1.7.4 T-6 1: the first distance after rerouting shows the turn (the phone kept it; 2.4.3: D = 600 m, bar 90)",
                "tbt 17 0 90.0 600.0 0 0 0 90 0".equals(last("tbt ")));
        // T-6 ①: 재탐색 중 폰이 새 회전을 보냄 → 끝나면 바로 그 새 회전(기존 동작)
        t.status(3);
        t.nextTurn("New Road", LEFT, TURN, -1, 0);
        c.calls.clear();
        t.status(1);
        expect("1.7.4 T-6 1: a new turn during rerouting is sent at its end, whole frames: no distance (the old 600 m nowhere), HUD list only",
                "tbt 70 0 0.0 -1.0 0 0 0 254 -2", "hud f201460000" + utf16("New Road"));
        // 재탐색이 또 와도 앞 재탐색 중에 온 회전을 "새 경로 것"으로 남기지 않음
        t.status(3);
        c.calls.clear();
        t.status(1);
        check("1.7.4 T-6 1: a second rerouting with no turn: nothing at its end (the flag is per rerouting)", last("tbt ") == null);
        c.calls.clear();
        t.status(3);
        c.calls.clear();
        t.status(2);
        check("1.7.2 AA rerouting then the route ends: the stock no-guidance clear", c.calls.contains(CLEAR_TBT) && c.calls.contains(CLEAR_ROAD));
        c.calls.clear();

        // 포커스가 순정일 때 재탐색: 보내지 않고, 폰으로 돌아오면 10
        AaTurnsFit f = new AaTurnsFit(new android.content.Context(), c, new Handler());
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
        AaTurnsFit w = new AaTurnsFit(new android.content.Context(), c, new Handler());
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
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
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
        t.clock += AaTurnsFit.CROSSROAD_MS;
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

        // 1.7.2: 줄은 곧 회전 여부·D가 바뀔 때도 하나(숫자만): Road A 멀리·곧 회전, 순정에서 돌아온 뒤 Road B 멀리·곧 회전(D는 늘 350m).
        // 1.7.3 추가(로그 H): 같은 회전에서 이름만 Road A → Road B로 바뀌면 멀리 있음으로 떨어지지 않는다(전에는 멀리·곧 회전 두 줄이 더 있었음)
        check("2.3.0 turn log: numbers only, not repeated for the same values (distance, name, resend)",
                // 2.4.3: D는 각 회전의 처음 거리(200m, 순정에서 돌아온 뒤 150m)라 막대가 가득(90) 찬 채 시작한다
                log.size() == 4 && log.get(3).equals("AA: turn ev 4 side 2 angle -1 -> icon 67 near display 13 bar 90 d 150") && log.get(0).equals("AA: turn ev 4 side 2 angle -1 -> icon 67 far")
                        && log.get(1).equals("AA: turn ev 4 side 2 angle -1 -> icon 67 near display 13 bar 90 d 200"));
        log.clear();
        int before = t.turnLogs;   // 8 ramps + 4 turn lines
        for (int k = 0; k < 400; k++) t.nextTurn("R", k % 2 == 0 ? RIGHT : LEFT, TURN, k, 0);
        check("2.3.0 turn log: at most 300 lines in all", before == 12 && log.size() == 300 - before && t.turnLogs == AaTurnsFit.TURN_LOGS_MAX);
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
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, h);
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
                AaTurnsFit.CLEAR_ICON == 8 && AaTurnsFit.CLEAR_DIST_ERR == -3 && AaTurnsFit.CLEAR_UNIT == 15 && AaTurnsFit.BAR_FAR_ERR == -1
                        && AaTurnsFit.HUD_CLEAR_UNIT == 15 && AaTurnsFit.HUD_CLEAR_TENTHS == 15);
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

        AaTurnsFit u = new AaTurnsFit(new android.content.Context(), c, new Handler());
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
        AaTurnsFit t = new AaTurnsFit(new android.content.Context(), c, new Handler());
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

        AaTurnsFit u = new AaTurnsFit(new android.content.Context(), c, new Handler());
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

        AaTurnsFit v = new AaTurnsFit(new android.content.Context(), c, new Handler());
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
        AaTurnsFit t = new AaTurnsFit(ctx, cc, new Handler());
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
        check("trial 3: bar never above 90 (stock maximum), at least 10", AaTurnsFit.bar(350, 350) == 90 && AaTurnsFit.bar(1, 350) == 10 && AaTurnsFit.bar(349, 350) == 90);
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
