package android.content;

import java.util.ArrayList;
import java.util.List;

/** PC 시험용 가짜 Context: 방송 받기를 적어 두고, 보낸 방송도 적는다 */
public class Context {
    public final List<BroadcastReceiver> receivers = new ArrayList<BroadcastReceiver>();
    public final List<IntentFilter> filters = new ArrayList<IntentFilter>();
    public final List<Intent> sent = new ArrayList<Intent>();
    public boolean failRegister;

    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f) {
        if (failRegister) throw new SecurityException("no");
        receivers.add(r);
        filters.add(f);
        return null;
    }

    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f, String permission, Object handler) {
        return registerReceiver(r, f);
    }

    public void sendBroadcast(Intent i) { sent.add(i); }
}
