package android.content;

import java.util.ArrayList;
import java.util.List;

/** PC 시험용 가짜 */
public class IntentFilter {
    public final List<String> actions = new ArrayList<String>();

    public IntentFilter() {}

    public IntentFilter(String a) { actions.add(a); }

    public void addAction(String a) { actions.add(a); }
}
