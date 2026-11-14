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
        expect("no route yet: nothing");
        t.status(1);
        t.nextTurn("Main St", RIGHT, TURN, -1, 0);
        // 1.7.4: 순정 GpmNaviTBTManager처럼 교차로 이름(그 이벤트의 도로) → updateTBTInfo(표시, 65535, 방향, 거리, 0, 0, 단위, 32, 0)
        expect("right turn: crossroad name, turn as the stock GPM (side street 65535, direction 90, bar 32), HUD list, HUD distance",
                "road [Main St]", "tbt 67 65535 90.0 0.0 0 0 0 32 0", "hud f201430000" + utf16("Main St"), "2c 0 0 0 0 0 65535 0 0 65535");
        t.nextTurnDistance(320, 30, 200, MILES_P1);
        expect("0.2 mi (1.7.4: the phone's unit as stock; 320 m inside 350 m is no near layout): no crossroad name on a distance",
                "tbt 67 65535 90.0 0.2 0 0 2 32 0", "2c 2 2 0 0 0 65535 0 0 65535");
        t.nextTurnDistance(100, 10, 100, METERS);
        expect("100 m in metres with miles set: metres (1.7.4: stock keeps the phone's unit)", "tbt 67 65535 90.0 100.0 0 0 0 32 0", "2c 0 0 100 0 0 65535 0 0 65535");
        Car.unit = Car.DISTANCE_KM;
        t.nextTurnDistance(1500, 60, 932, MILES);
        expect("0.9 mi with km set: miles (1.7.4: no conversion to the owner's unit)", "tbt 67 65535 90.0 0.932 0 0 2 32 0", "2c 2 9 0 0 0 65535 0 0 65535");
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
        expect("distance between the turns ahead and their end: HUD list gets the next turn (1.7.4: same icon at 90 m)",
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
        expect("depart (first): road as destination, no distance yet: icon 2, bar 254, error -2 (stock what 0, no crossroad name)",
                "dest 0 [Start Rd]", "tbt 2 65535 0.0 -1.0 0 0 0 254 -2");
        d.nextTurn("Circle", RIGHT, 13, 100, 0);
        expect("1.7.4 (T-ma): roundabout before any distance: the stock sends icon 2 whatever the turn (distance still -1)",
                "road [Circle]", "tbt 2 65535 90.0 -1.0 0 0 0 254 -2");
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
        reviewFixes();
        stockReplay();
        m15();
        stockGpm();
        stockUnits();
        stockOrder();
        reviewStock();
        check("2nd review 6: a MAC is masked completely, other text only as a length, null as null",
                "xx:xx:xx:xx:xx:xx".equals(Mac.mask("A1:B2:C3:D4:E5:F6")) && !Mac.mask("A1:B2:C3:D4:E5:F6").contains("F6")
                        && "(6 chars)".equals(Mac.mask("iPhone")) && "null".equals(Mac.mask(null)));
        System.exit(fail);
    }

    /**
     * 1.7.4 끝난 기준 ①: 순정 GPM 표 53줄(drafts/1.7.4-tbt-diff.md 2장, 순정 GpmNaviTBTManager setTurnDisplayType·
     * setRampDisplayType·setRoundAboutDisplayType·onNextTurn)의 입력마다 updateTBTInfo가 순정과 같다:
     * (표시, 옆길 65535, 방향, 거리, 0, 0, 단위, 막대 32, 오류 0). 각 줄 앞에 오른쪽 회전(67, 방향 90)을 보내 두고,
     * 순정이 방향을 바꾸지 않는 줄(램프·회전교차로 나가기·목적지)은 90이 남는지, 표시를 바꾸지 않는 줄(갈림길 방향 모름)은 67이
     * 남는지 본다. 그 이벤트 때 바로 나가고(교차로 이름 먼저), 거리가 오면 같은 표시로 다시 나간다(남은 거리 150m, 곧 회전 화면 없음).
     * 줄: {표 줄 번호, 이벤트, side, 각도, 순정 표시, 순정 방향, 교차로 이름(1: 보냄)}. 표시 -1: 아무것도 보내지 않음
     */
    static final int[][] STOCK_GPM = {
        { 1, 14, 3, -1, 65, 0, 1 }, { 2, 10, 3, -1, 65, 0, 1 },
        { 3, 3, 2, -1, 66, 45, 1 }, { 4, 3, 1, -1, 71, 315, 1 }, { 5, 3, 3, -1, 64, 0, 1 },
        { 6, 4, 2, -1, 67, 90, 1 }, { 7, 4, 1, -1, 70, 270, 1 }, { 8, 4, 3, -1, 64, 0, 1 },
        { 9, 5, 2, -1, 68, 135, 1 }, { 10, 5, 1, -1, 69, 225, 1 }, { 11, 5, 3, -1, 64, 0, 1 },
        { 12, 6, 2, -1, 73, 270, 1 }, { 13, 6, 1, -1, 72, 270, 1 }, { 13, 6, 3, -1, 72, 270, 1 },
        { 14, 2, 2, -1, 67, 90, 1 }, { 15, 2, 1, -1, 70, 270, 1 }, { 16, 2, 3, -1, 65, 0, 1 },
        { 19, 1, 3, -1, 67, 90, 1 },
        { 20, 7, 2, -1, 161, 90, 1 }, { 21, 7, 1, -1, 162, 90, 1 }, { 22, 7, 3, -1, 160, 90, 1 },
        { 23, 8, 2, -1, 164, 90, 1 }, { 24, 8, 1, -1, 165, 90, 1 }, { 25, 8, 3, -1, 163, 90, 1 },
        { 26, 9, 2, -1, 173, 0, 1 }, { 27, 9, 1, -1, 174, 0, 1 }, { 28, 9, 3, -1, 67, 0, 1 },
        { 29, 16, 3, -1, 176, 0, 1 },
        { 30, 13, 2, 180, 96, 0, 1 }, { 31, 13, 2, 135, 97, 45, 1 }, { 31, 13, 2, 179, 97, 45, 1 }, { 32, 13, 2, 90, 99, 90, 1 },
        { 32, 13, 2, 134, 99, 90, 1 }, { 33, 13, 2, 45, 100, 135, 1 }, { 33, 13, 2, 89, 100, 135, 1 }, { 34, 13, 2, 0, 102, 180, 1 },
        { 34, 13, 2, 44, 102, 180, 1 }, { 34, 13, 2, 316, 102, 180, 1 }, { 34, 13, 2, 360, 102, 180, 1 }, { 35, 13, 2, 271, 103, 225, 1 },
        { 35, 13, 2, 315, 103, 225, 1 }, { 36, 13, 2, 226, 105, 270, 1 }, { 36, 13, 2, 270, 105, 270, 1 }, { 37, 13, 2, 181, 106, 315, 1 },
        { 37, 13, 2, 225, 106, 315, 1 }, { 38, 13, 2, -1, 64, 0, 1 }, { 38, 13, 2, 361, 64, 0, 1 },
        { 39, 13, 1, 180, 192, 0, 1 }, { 40, 13, 3, 150, 193, 45, 1 }, { 41, 13, 1, 100, 195, 90, 1 }, { 42, 13, 3, 60, 196, 135, 1 },
        { 43, 13, 1, 20, 198, 180, 1 }, { 44, 13, 3, 300, 199, 225, 1 }, { 45, 13, 1, 250, 201, 270, 1 }, { 46, 13, 3, 200, 202, 315, 1 },
        { 47, 12, 2, -1, 32, 90, 1 }, { 48, 12, 1, -1, 33, 90, 1 }, { 49, 12, 3, -1, 64, 0, 1 },
        { 50, 19, 3, -1, 3, 90, 0 }, { 51, 0, 3, -1, 64, 90, 0 },
        { 52, 11, 2, 90, -1, 0, 0 }, { 53, 17, 3, -1, -1, 0, 0 },
        // 표 밖 이벤트 번호: 순정 onNextTurn의 어느 갈래에도 맞지 않아 아무것도 보내지 않는다
        { 0, 15, 2, -1, -1, 0, 0 }, { 0, 18, 2, -1, -1, 0, 0 }, { 0, 20, 2, -1, -1, 0, 0 }, { 0, 21, 1, -1, -1, 0, 0 }, { 0, 99, 2, -1, -1, 0, 0 },
    };

    static String lastTbt() {
        String r = null;
        for (String x : c.calls) if (x.startsWith("tbt ")) r = x;
        return r;
    }

    static void stockGpm() {
        final int RIGHT = 2, TURN = 4, METERS = 1;
        Car.unit = Car.DISTANCE_MILES;   // 차 설정 단위는 쓰지 않는다(순정은 폰 단위 그대로)
        for (int[] r : STOCK_GPM) {
            c = new Cluster();
            AaTurns t = new AaTurns(new android.content.Context(), c, new Handler());
            t.status(1);
            t.nextTurn("Before", RIGHT, TURN, -1, 0);
            t.nextTurnDistance(400, 40, 400, METERS);
            c.calls.clear();
            String in = "ev " + r[1] + " side " + r[2] + " angle " + r[3];
            String row = r[0] == 0 ? "stock GPM, event outside the table: " : "stock GPM row " + r[0] + ": ";
            t.nextTurn("Road " + r[0], r[2], r[1], r[3], 0);
            if (r[4] < 0) {
                check(row + in + " -> nothing sent (no crossroad name, no turn frame)", c.calls.isEmpty());
                t.nextTurnDistance(150, 15, 150, METERS);
                check(row + in + " -> the next distance resends the last turn (67, as the stock mDisplayType kept)",
                        "tbt 67 65535 90.0 150.0 0 0 0 32 0".equals(lastTbt()));
                continue;
            }
            String want = "tbt " + r[4] + " 65535 " + (float) r[5] + " 400.0 0 0 0 32 0";
            boolean road = c.calls.contains("road [Road " + r[0] + "]");
            boolean firstRoad = !c.calls.isEmpty() && c.calls.get(0).equals("road [Road " + r[0] + "]");
            check(row + in + " -> " + want + (r[6] == 1 ? ", crossroad name first" : ", no crossroad name"),
                    want.equals(lastTbt()) && (r[6] == 1 ? firstRoad : !road));
            c.calls.clear();
            t.nextTurnDistance(150, 15, 150, METERS);
            check(row + in + " -> distance 150 m: the same icon and direction again, no crossroad name",
                    ("tbt " + r[4] + " 65535 " + (float) r[5] + " 150.0 0 0 0 32 0").equals(lastTbt()) && !c.calls.toString().contains("road ["));
        }
        // 17: 첫 출발(앞 도로 없음): 목적지 이름(도로) → 거리 -1 → 아이콘 2·막대 254·오류 -2. 교차로 이름 없음
        c = new Cluster();
        AaTurns d = new AaTurns(new android.content.Context(), c, new Handler());
        d.status(1);
        d.nextTurn("Start Rd", 3, 1, -1, 0);
        expect("stock GPM row 17: first DEPART: destination name, then icon 2 with no distance (bar 254, error -2)",
                "dest 0 [Start Rd]", "tbt 2 65535 0.0 -1.0 0 0 0 254 -2");
        // 18(T-마): 거리가 오기 전의 회전: 순정은 아이콘과 상관없이 2. 거리가 오면 그 회전 표시
        d.nextTurn("Ramp Rd", 2, 7, -1, 0);
        expect("stock GPM row 18 (T-ma): an on-ramp before any distance: icon 2 (not 161), bar 254, error -2",
                "road [Ramp Rd]", "tbt 2 65535 0.0 -1.0 0 0 0 254 -2");
        d.nextTurnDistance(500, 50, 500, METERS);
        expect("stock GPM row 18: the first distance: a ramp event keeps the overwritten icon 2 (stock setTurnDisplayType has no ramp case)",
                "tbt 2 65535 0.0 500.0 0 0 0 32 0", "2c 0 0 500 0 0 65535 0 0 65535");
        d.nextTurn("Turn Rd", 2, 4, -1, 0);
        expect("stock GPM: the next turn event sets its own icon again (67)", "road [Turn Rd]", "tbt 67 65535 90.0 500.0 0 0 0 32 0",
                "hud f201430000" + utf16("Turn Rd"), "2c 0 0 500 0 0 65535 0 0 65535");
    }

    /** 1.7.4: 거리 단위는 순정 setDistanceValue대로(폰 단위 그대로, 차 설정 단위로 바꾸지 않음) */
    static void stockUnits() {
        final int[][] u = {
            // {meters, e3, phone unit, cluster unit}, 거리 문자열
            { 999, 999000, 1, 0 }, { 1000, 1000000, 1, 1 }, { 2500, 2500, 2, 1 }, { 2500, 2500, 3, 1 }, { 3219, 2000, 4, 2 },
            { 3219, 2000, 5, 2 }, { 100, 328000, 6, 3 }, { 100, 109000, 7, 4 }, { 100, 100, 0, 15 }, { 100, 100, 9, 15 } };
        final String[] want = { "999.0", "1.0", "2.5", "2.5", "2.0", "2.0", "328.0", "109.0", "0.1", "0.1" };
        for (int carUnit : new int[] { Car.DISTANCE_KM, Car.DISTANCE_MILES }) {
            Car.unit = carUnit;
            for (int k = 0; k < u.length; k++) {
                c = new Cluster();
                AaTurns t = new AaTurns(new android.content.Context(), c, new Handler());
                t.status(1);
                t.nextTurn("R", 2, 4, -1, 0);
                c.calls.clear();
                t.nextTurnDistance(u[k][0], 0, u[k][1], u[k][2]);
                check("stock GPM distance: " + u[k][0] + " m / " + u[k][1] + " e3 in phone unit " + u[k][2] + " (car set to "
                        + (carUnit == Car.DISTANCE_KM ? "km" : "miles") + ") -> " + want[k] + " unit " + u[k][3],
                        ("tbt 67 65535 90.0 " + want[k] + " 0 0 " + u[k][3] + " 32 0").equals(lastTbt()));
            }
        }
        Car.unit = Car.DISTANCE_MILES;
    }

    /**
     * 1.7.4 끝난 기준 ③·④: 이름 바뀜·회전·거리가 섞여 와도 받은 순서대로 바로 나간다(붙잡기·경계 여유·10초 묶기 없음).
     * 재탐색(상태 3)은 아무것도 바꾸지 않는다: 표시 10이나 HUD·2C 지우기가 없고, 상태 1로 돌아와도 기억한 회전을 다시 보내지 않는다.
     */
    static void stockOrder() {
        final int RIGHT = 2, UNSPEC = 3, FORK = 9, NAME = 2, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        Handler h = new Handler();
        AaTurns t = new AaTurns(new android.content.Context(), c, h);
        t.clock = 80000000L;
        t.status(1);
        // #65 꼴: 같은 지점에 갈림길(173)과 이름 바뀜(65)이 번갈아, 사이에 거리. 350m 경계를 오간다
        t.nextTurn("Fork Rd", RIGHT, FORK, -1, 0);
        t.clock += 100L;
        t.nextTurnDistance(352, 30, 352, METERS);
        t.clock += 100L;
        t.nextTurn("Name Rd", UNSPEC, NAME, -1, 0);
        t.clock += 100L;
        t.nextTurnDistance(348, 30, 348, METERS);
        t.clock += 100L;
        t.nextTurn("Fork Rd", RIGHT, FORK, -1, 0);
        t.clock += 100L;
        t.nextTurnDistance(351, 30, 351, METERS);
        java.util.List<String> got = new java.util.ArrayList<String>();
        for (String x : c.calls) if (x.startsWith("tbt ") || x.startsWith("road ")) got.add(x);
        check("1.7.4 (3): fork / name change / fork with distances across 350 m, 100 ms apart: each goes out at once, in order "
                + "(no hold, no near layout, no 10 s name grouping): " + got,
                got.equals(java.util.Arrays.asList("road [Fork Rd]", "tbt 173 65535 0.0 0.0 0 0 0 32 0", "tbt 173 65535 0.0 352.0 0 0 0 32 0",
                        "road [Name Rd]", "tbt 65 65535 0.0 352.0 0 0 0 32 0", "tbt 65 65535 0.0 348.0 0 0 0 32 0",
                        "road [Fork Rd]", "tbt 173 65535 0.0 348.0 0 0 0 32 0", "tbt 173 65535 0.0 351.0 0 0 0 32 0")));
        check("1.7.4 (3): no timer left waiting (no hold deadline)", h.delayed == null);
        c.calls.clear();
        t.status(3);
        expect("1.7.4 (4): rerouting (3): nothing sent (stock onStatus does nothing: no display 10, no HUD or 2C clear)");
        t.nextTurn("Old Rd", RIGHT, 4, -1, 0);
        expect("1.7.4 (4): a turn event while rerouting goes out as usual", "road [Old Rd]", "tbt 67 65535 90.0 351.0 0 0 0 32 0",
                "hud f201430000" + utf16("Old Rd"), "2c 0 0 351 0 0 65535 0 0 65535");
        t.status(1);
        expect("1.7.4 (4, T-6 1 gone): rerouting over (3 -> 1): the remembered turn is not resent");
        t.nextTurnDistance(300, 30, 300, METERS);
        expect("1.7.4 (4): the new route's next distance goes out with the last turn received (stock keeps it until a new event)",
                "tbt 67 65535 90.0 300.0 0 0 0 32 0", "2c 0 0 300 0 0 65535 0 0 65535");
        check("1.7.4: no near-turn, hold or reroute fields left (AaTurns has no nearOn, nameHold, rerouting, barOut, NEAR_EXIT_MARGIN, CROSSROAD_MS)",
                noField("nearOn") && noField("nameHold") && noField("rerouting") && noField("barOut") && noField("NEAR_EXIT_MARGIN")
                        && noField("CROSSROAD_MS") && noField("D_GENERAL") && noField("DISPLAY_RESEARCH"));
        Car.unit = Car.DISTANCE_MILES;
    }

    static boolean noField(String name) {
        try {
            AaTurns.class.getDeclaredField(name);
            return false;
        } catch (NoSuchFieldException e) {
            return true;
        }
    }

    /** 리뷰 반영(2026-11-12): 경로 끝 뒤 새 경로, 상태보다 먼저 온 첫 출발, 회전 전 거리, 도로 없는 첫 출발 */
    static void reviewStock() {
        final int RIGHT = 2, DEPART = 1, DEST = 19, METERS = 1;
        Car.unit = Car.DISTANCE_KM;
        c = new Cluster();
        AaTurns t = new AaTurns(new android.content.Context(), c, new Handler());
        t.status(1);
        t.nextTurn("Start A", 3, DEPART, -1, 0);
        t.nextTurnDistance(500, 50, 500, METERS);
        t.nextTurn("Home", 3, DEST, -1, 0);
        t.status(2);
        c.calls.clear();
        t.status(1);
        t.nextTurn("Start B", 3, DEPART, -1, 0);
        expect("review 2: route over (status 2), then a new route's DEPART: a first departure (destination name, icon 2), not the old route's 3",
                "dest 0 [Start B]", "tbt 2 65535 0.0 -1.0 0 0 0 254 -2");
        check("review 2: the turn memory is cleared at the route end like stop() (event, road, icon, direction)",
                t.event == DEPART && "Start B".equals(t.road) && t.displayType == 2 && t.direction == 0);

        c = new Cluster();
        AaTurns e = new AaTurns(new android.content.Context(), c, new Handler());
        e.nextTurn("Early", 3, DEPART, -1, 0);
        check("review 3: a first DEPART before any status: only the destination name (2.4.1, not gated by status), no turn frame, but the icon field is already 2 (stock overwrites it always)",
                c.calls.equals(java.util.Arrays.asList("dest 0 [Early]")) && e.displayType == 2);
        c.calls.clear();
        e.status(1);
        e.nextTurnDistance(400, 40, 400, METERS);
        expect("review 3: the next distance after status 1: icon 2 with the distance", "tbt 2 65535 0.0 400.0 0 0 0 32 0", "2c 0 0 400 0 0 65535 0 0 65535");

        c = new Cluster();
        AaTurns f = new AaTurns(new android.content.Context(), c, new Handler());
        f.status(1);
        f.nextTurnDistance(300, 30, 300, METERS);
        expect("review 4: a distance before any turn event: icon 0 with the distance (stock mDisplayType starts at 0)", "tbt 0 65535 0.0 300.0 0 0 0 32 0",
                "2c 0 0 300 0 0 65535 0 0 65535");

        c = new Cluster();
        AaTurns g = new AaTurns(new android.content.Context(), c, new Handler());
        g.status(1);
        g.nextTurn(null, 3, DEPART, -1, 0);
        expect("review 4: a first DEPART with no road: destination name null, icon 2", "dest 0 [null]", "tbt 2 65535 0.0 -1.0 0 0 0 254 -2");
        g.nextTurnDistance(300, 30, 300, METERS);
        c.calls.clear();
        g.nextTurn(null, 3, DEPART, -1, 0);
        expect("review 4: the road is still none, so the next DEPART is a first departure again (stock mRoad stays null)",
                "dest 0 [null]", "tbt 2 65535 0.0 -1.0 0 0 0 254 -2");
        g.nextTurn("Real Rd", RIGHT, 4, -1, 0);
        expect("review 4: a turn event after that: its own icon (distance still -1, so 2 as stock)", "road [Real Rd]", "tbt 2 65535 90.0 -1.0 0 0 0 254 -2");
        Car.unit = Car.DISTANCE_MILES;
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

        // 1.7.4: 교차로 이름은 순정 GPM처럼 회전 이벤트마다 그 이벤트의 도로 이름(멀리 있어도, 묶지 않음). 거리 이벤트에는 없음
        t.nextTurn("01" + "01aabbcc", 0, 34, 0, 0);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        t.nextTurnDistance(200, 60, 200, METERS);
        t.nextTurn("Road A", RIGHT, TURN, -1, 0);
        int names = 0;
        for (String x : c.calls) if (x.equals("road [Road A]")) names++;
        java.util.List<String> roadTbt = new java.util.ArrayList<String>();
        for (String x : c.calls) if (x.startsWith("road ") || x.startsWith("tbt ")) roadTbt.add(x);
        check("1.7.4 crossroad name: with every turn event (twice), never with a distance, no 10 s grouping: " + roadTbt, names == 2
                && roadTbt.equals(java.util.Arrays.asList("road [Road A]", "tbt 67 65535 90.0 0.0 0 0 0 32 0", "tbt 67 65535 90.0 200.0 0 0 0 32 0",
                        "road [Road A]", "tbt 67 65535 90.0 200.0 0 0 0 32 0")));
        c.calls.clear();
        t.nextTurn("Road B", RIGHT, TURN, -1, 0);
        check("1.7.4 crossroad name: a new name goes out with its event at once (no wait for a distance)",
                c.calls.size() > 0 && c.calls.get(0).equals("road [Road B]"));
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

        // 1.7.4: 줄은 회전 값(종류·방향·각도·아이콘)이 바뀔 때만 하나(숫자만, 곧 회전 칸 없음). Road A·Road B·거리는 같은 값
        check("1.7.4 turn log: numbers only, one line for the same values (distance, name, resend), no near/far part",
                log.size() == 1 && log.get(0).equals("AA: turn ev 4 side 2 angle -1 -> icon 67"));
        log.clear();
        int before = t.turnLogs;   // 8 ramps + 1 turn line
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
        t.nextTurnDistance(400, 20, 400, METERS);
        String tbt = null;
        for (String x : c.calls) if (x.startsWith("tbt ")) tbt = x;
        check("1.7.4: AA turn values as the stock GPM: side street 65535, direction 225 (sharp left), bar 32, barErr 0",
                "tbt 69 65535 225.0 400.0 0 0 0 32 0".equals(tbt));
        check("1.7.4: the stock direction for a sharp left", t.direction == 225);
        check("1.7.2 M-14: clear values within NavigationCluster's ranges",
                AaTurns.CLEAR_ICON == 8 && AaTurns.CLEAR_DIST_ERR == -3 && AaTurns.CLEAR_UNIT == 15 && AaTurns.CLEAR_BAR_ERR == -1
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
        check("2nd review 3: ... and the next phone turn reaches the cluster again", c.calls.contains("tbt 67 65535 90.0 150.0 0 0 0 32 0"));
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


    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fail = 1;
    }
}
