package android.content;

import java.util.HashMap;
import java.util.Map;

/** PC 시험용 가짜 Intent(정수·문자열 값만) */
public class Intent {
    public static final int FLAG_RECEIVER_FOREGROUND = 0x10000000;
    public final String action;
    public int flags;
    public final Map<String, Object> extras = new HashMap<String, Object>();

    public Intent(String action) { this.action = action; }

    public String getAction() { return action; }

    public Intent addFlags(int f) { flags |= f; return this; }

    public Intent putExtra(String k, int v) { extras.put(k, Integer.valueOf(v)); return this; }

    public int getIntExtra(String k, int d) {
        Object v = extras.get(k);
        return v instanceof Integer ? (Integer) v : d;
    }

    public String getStringExtra(String k) { return null; }

    public <T> T getParcelableExtra(String k) { return null; }

    public ComponentName getComponent() { return null; }
}
