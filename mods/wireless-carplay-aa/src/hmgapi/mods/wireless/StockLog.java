package hmgapi.mods.wireless;
// Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-08, mod 2.3.0.
// Korea Projection 1.7.2 수정(2026-11-08, 모드 2.3.1 판 그대로, 계획 M-13): 관찰 기록을 넓혔다.
//   - updateTBTInfo: 아이콘 말고도 옆길·방향·거리 오류·combi·단위·막대·막대 오류(그리고 그때의 거리)
//   - HUD 목록 updateTPTurnListNew: 앞 5바이트(F2, 수, 아이콘 3칸) 16진과 전체 길이. 이름 바이트는 길이만
//   - updateRemainDis, updateCluDataNew_14 값, 도로 이벤트(updateRoadEventInfo) 프레임 16진
//   - 교차로(도로) 이름은 글자 수만
//   - (리뷰 반영) 줄 한도 300을 종류별로 나눴다. 2C·remain dis는 거리 칸을 빼고 비교한다
//   - (2차 리뷰 반영) updateTBTSubInfo(9·10인자) 값도 남긴다: 안내 끝 지우기 값을 순정 무안내 값에 맞추려고

/**
 * Observation lines for the car navigation's own turn-by-turn calls (in its process), so a
 * drive with the stock navigation shows what it sends the cluster and HUD (it logs none of them
 * itself). Only with Korea Projection's debug log on (DEBUG_FILE, made by the package from a file
 * on the USB stick; off by default). Numbers only, never a name; a line only when the values
 * change, at most MAX lines per process. It never blocks or changes a call (Wireless does that,
 * as before, while a phone guides).
 *
 * 1.7.2: 순정의 "곧 회전" 막대가 시작되는 거리(D)와 근거리 값, HUD 목록 프레임 모양(빈 목록 {F2,00}인지,
 * 아이콘 3칸인지, 계획 V-6)을 순정 운행 한 번으로 얻으려고 넓혔다. 회전 줄은 거리만 바뀔 때는 남기지 않고,
 * 다른 값이 바뀔 때 그때의 거리를 함께 남긴다(막대가 15에서 10으로 바뀌는 거리 = D).
 */
final class StockLog {
    /** Korea Projection's debug flag (svc/hotspot.sh); root-only, so read in the zygote */
    static final String DEBUG_FILE = "/data/local/tmp/korea.projection/debug";
    static final int MAX = 300;
    /**
     * 종류별 줄 한도(합계 MAX). 거리처럼 자주 바뀌는 종류가 한도를 다 써서 회전 줄(차-7의 D·근거리 값)을
     * 잃지 않게 나눴다(리뷰 4).
     */
    static final String[] KINDS = { "tbt", "hud list", "lanes", "2c", "remain dis", "clu14", "road event", "road name length", "sub info" };
    static final int[] KIND_MAX = { 100, 40, 30, 30, 25, 20, 20, 15, 20 };
    final int[] kindLines = new int[KINDS.length];
    /** 훅 지점이 아니라 Wireless가 직접 거는 관찰 전용 메서드의 이름표(지도에 없는 메서드라 HookPlan과 무관) */
    static final String CLU_DATA_14 = "update_clu_data_new_14", ROAD_EVENT = "update_road_event_info";
    /** HUD 목록 프레임에서 16진으로 남기는 앞부분: F2, 아이콘 수, 아이콘 3칸(그 뒤는 이름 UTF-16) */
    static final int HUD_HEAD = 5;

    final boolean on;
    int lines;
    String tbt, lanes, sub2c, hud, remain, clu14, roadEvent, roadName, sub;

    /** 그 종류의 줄 한도가 남았는지 */
    private boolean room(String kind) {
        for (int k = 0; k < KINDS.length; k++) if (KINDS[k].equals(kind)) return kindLines[k] < KIND_MAX[k];
        return false;
    }

    private void count(String kind) {
        for (int k = 0; k < KINDS.length; k++) if (KINDS[k].equals(kind)) kindLines[k]++;
    }

    StockLog(boolean on) {
        this.on = on;
    }

    /**
     * The line for one call to the hook point "framework.navigation_cluster." + point (its
     * arguments; phone: a phone was guiding, so Wireless skips the call), or null: nothing to log.
     * update_tp_crossroad_name는 args[0]에 이름 문자열(또는 null)을 받는다(Wireless가 객체에서 꺼내 준다).
     */
    synchronized String line(String point, Object[] args, boolean phone) {
        if (!on || lines >= MAX || args == null) return null;
        String v, kind;
        if ("update_tbt_info".equals(point)) {
            // displayType, sideStreet, direction, distance, distanceErr, combiSideStreet, scale, barGraph, barGraphErr
            if (args.length < 9) return null;
            for (int k = 0; k < 9; k++) {
                if (k == 2 || k == 3 ? !(args[k] instanceof Float) : !(args[k] instanceof Integer)) return null;
            }
            kind = "tbt";
            String key = "icon " + args[0] + " side " + args[1] + " dir " + args[2] + " distErr " + args[4] + " combi " + args[5]
                    + " unit " + args[6] + " bar " + args[7] + " barErr " + args[8];
            if (key.equals(tbt)) return null;
            tbt = key;
            v = key + " dist " + args[3];
        } else if ("update_tbt_sub_info".equals(point) || "update_tbt_sub_info_b".equals(point)) {
            // 2차 리뷰 반영: 남은 거리·시간의 무안내 값(시·분·시간 종류·형식·도착 지점)을 차에서 보려고 남긴다.
            // distance, distanceErr, unit, hours, minutes, timeType, timeFormat, compass, compassErr[, estPointType]
            // 거리만 바뀐 줄은 남기지 않고, 다른 값이 바뀔 때 그때 거리를 함께 남긴다(시·분은 도착 시각이라 숫자만)
            int n = "update_tbt_sub_info_b".equals(point) ? 10 : 9;
            if (args.length < n) return null;
            for (int k = 0; k < n; k++) {
                if (k == 0 || k == 7 ? !(args[k] instanceof Float) : !(args[k] instanceof Integer)) return null;
            }
            kind = "sub info";
            StringBuilder key = new StringBuilder(n == 10 ? "b" : "a");
            for (int k = 1; k < n; k++) key.append(' ').append(args[k]);
            if (key.toString().equals(sub)) return null;
            sub = key.toString();
            v = key + " dist " + args[0];
        } else if ("update_lane_info".equals(point)) {
            if (args.length < 1 || !(args[0] instanceof byte[])) return null;
            kind = "lanes";
            v = hex((byte[]) args[0]);
            if (v.equals(lanes)) return null;
            lanes = v;
        } else if ("update_tbt_sub_info_2c".equals(point)) {
            String n = ints(args, 9);
            if (n == null) return null;
            kind = "2c";
            // 거리 칸(소수·정수)은 빼고 비교한다: 세 칸의 단위와 지점이 있는지(정수 65535 = 없음)만 본다
            StringBuilder key = new StringBuilder();
            for (int k = 0; k < 9; k += 3) key.append(args[k]).append(((Integer) args[k + 2]) == 65535 ? "x " : "d ");
            if (key.toString().equals(sub2c)) return null;
            sub2c = key.toString();
            v = n;
        } else if ("update_tp_turn_list_new".equals(point)) {
            if (args.length < 1 || !(args[0] instanceof byte[])) return null;
            byte[] f = (byte[]) args[0];
            kind = "hud list";
            // 앞부분만 16진(그대로, 뒤 0도 남긴다: 빈 목록이 {F2}인지 {F2,00}인지 보려고), 이름은 길이만
            v = rawHex(f, Math.min(f.length, HUD_HEAD)) + " len " + f.length + " name bytes " + Math.max(0, f.length - HUD_HEAD);
            if (v.equals(hud)) return null;
            hud = v;
        } else if ("update_remain_dis".equals(point)) {
            // remained_distance, scale_unit, under_decimal, nv_display_TG
            String n = ints(args, 4);
            if (n == null) return null;
            kind = "remain dis";
            // 거리(정수·소수)는 빼고 단위·TG·무효 여부만 비교한다
            String key = args[1] + " " + args[3] + (((Integer) args[0]) == 65535 ? " x" : " d");
            if (key.equals(remain)) return null;
            remain = key;
            v = n;
        } else if (CLU_DATA_14.equals(point)) {
            // display, dir, dir1, pattern_guide_lane, pattern_guide_color
            String n = ints(args, 5);
            if (n == null) return null;
            kind = "clu14";
            v = n;
            if (v.equals(clu14)) return null;
            clu14 = v;
        } else if (ROAD_EVENT.equals(point)) {
            if (args.length < 1 || !(args[0] instanceof byte[])) return null;
            kind = "road event";
            v = rawHex((byte[]) args[0], Math.min(((byte[]) args[0]).length, 16));
            if (v.equals(roadEvent)) return null;
            roadEvent = v;
        } else if ("update_tp_crossroad_name".equals(point)) {
            // 이름은 남기지 않고 글자 수만(순정은 곧 회전일 때만 이름을 넣고 멀리 있을 때는 비운다)
            if (args.length < 1 || (args[0] != null && !(args[0] instanceof String))) return null;
            kind = "road name length";
            v = args[0] == null ? "null" : String.valueOf(((String) args[0]).length());
            if (v.equals(roadName)) return null;
            roadName = v;
        } else {
            return null;
        }
        if (!room(kind)) return null;
        count(kind);
        lines++;
        return "stock navigation: " + kind + " " + v + (phone ? " (phone guiding, skipped)" : "");
    }

    /** 앞 n개 인자가 모두 Integer이면 공백으로 이은 숫자, 아니면 null */
    static String ints(Object[] args, int n) {
        if (args.length < n) return null;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < n; k++) {
            if (!(args[k] instanceof Integer)) return null;
            b.append(k == 0 ? "" : " ").append(args[k]);
        }
        return b.toString();
    }

    /** the frame in hex, trailing zeros left out, at most 81 bytes (the lane frame's size) */
    static String hex(byte[] b) {
        int end = Math.min(b.length, 81);
        while (end > 1 && b[end - 1] == 0) end--;
        return rawHex(b, end);
    }

    /** 앞 n바이트를 16진으로(뒤 0을 빼지 않는다) */
    static String rawHex(byte[] b, int n) {
        StringBuilder s = new StringBuilder();
        for (int k = 0; k < n; k++) {
            s.append(Character.forDigit((b[k] >> 4) & 15, 16)).append(Character.forDigit(b[k] & 15, 16));
        }
        return s.toString();
    }
}
