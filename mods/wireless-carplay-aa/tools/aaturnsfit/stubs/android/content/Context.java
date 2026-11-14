package android.content;

import java.util.ArrayList;
import java.util.List;

public class Context {
    /** 시험용: 보낸 방송(sendBroadcast) 기록 */
    public final List<Intent> sent = new ArrayList<Intent>();

    public void sendBroadcast(Intent i) { sent.add(i); }
}
