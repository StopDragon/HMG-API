package android.content;

import java.util.HashMap;
import java.util.Map;

/** 시험용 Intent: 이름·플래그·int 값만 */
public class Intent {
    public static final int FLAG_RECEIVER_FOREGROUND = 0x10000000;
    public final String action;
    public int flags;
    public final Map<String, Integer> ints = new HashMap<String, Integer>();

    public Intent(String action) { this.action = action; }
    public Intent addFlags(int f) { flags |= f; return this; }
    public Intent putExtra(String k, int v) { ints.put(k, v); return this; }
    public int getIntExtra(String k, int d) { Integer v = ints.get(k); return v == null ? d : v; }
}
