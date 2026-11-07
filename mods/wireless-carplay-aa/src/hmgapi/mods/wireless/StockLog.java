package hmgapi.mods.wireless;
// Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-08, mod 2.3.0.

/**
 * Observation lines for the car navigation's own turn-by-turn calls (in its process): the icon of
 * updateTBTInfo, the updateLaneInfo frame in hex and the nine updateTBTSubInfo2C numbers, so a
 * drive with the stock navigation shows what it sends the cluster and HUD (it logs none of them
 * itself). Only with Korea Projection's debug log on (DEBUG_FILE, made by the package from a file
 * on the USB stick; off by default). Numbers only, never a name; a line only when the values
 * change, at most MAX lines per process. It never blocks or changes a call (Wireless does that,
 * as before, while a phone guides).
 */
final class StockLog {
    /** Korea Projection's debug flag (svc/hotspot.sh); root-only, so read in the zygote */
    static final String DEBUG_FILE = "/data/local/tmp/korea.projection/debug";
    static final int MAX = 300;

    final boolean on;
    int lines;
    String tbt, lanes, sub2c;

    StockLog(boolean on) {
        this.on = on;
    }

    /**
     * The line for one call to the hook point "framework.navigation_cluster." + point (its
     * arguments; phone: a phone was guiding, so Wireless skips the call), or null: nothing to log.
     */
    synchronized String line(String point, Object[] args, boolean phone) {
        if (!on || lines >= MAX || args == null) return null;
        String v, kind;
        if ("update_tbt_info".equals(point)) {
            if (args.length < 1 || !(args[0] instanceof Integer)) return null;
            kind = "tbt icon";
            v = String.valueOf(args[0]);
            if (v.equals(tbt)) return null;
            tbt = v;
        } else if ("update_lane_info".equals(point)) {
            if (args.length < 1 || !(args[0] instanceof byte[])) return null;
            kind = "lanes";
            v = hex((byte[]) args[0]);
            if (v.equals(lanes)) return null;
            lanes = v;
        } else if ("update_tbt_sub_info_2c".equals(point)) {
            if (args.length < 9) return null;
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < 9; k++) {
                if (!(args[k] instanceof Integer)) return null;
                b.append(k == 0 ? "" : " ").append(args[k]);
            }
            kind = "2c";
            v = b.toString();
            if (v.equals(sub2c)) return null;
            sub2c = v;
        } else {
            return null;
        }
        lines++;
        return "stock navigation: " + kind + " " + v + (phone ? " (phone guiding, skipped)" : "");
    }

    /** the frame in hex, trailing zeros left out, at most 81 bytes (the lane frame's size) */
    static String hex(byte[] b) {
        int end = Math.min(b.length, 81);
        while (end > 1 && b[end - 1] == 0) end--;
        StringBuilder s = new StringBuilder();
        for (int k = 0; k < end; k++) {
            s.append(Character.forDigit((b[k] >> 4) & 15, 16)).append(Character.forDigit(b[k] & 15, 16));
        }
        return s.toString();
    }
}
