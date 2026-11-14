package hmgapi.mods.wireless.dipo;
// Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-09, 모드 2.3.3.

import android.content.Context;
import android.content.Intent;

/**
 * 카플레이 막대를 순정 내비 프로세스로 알린다(hmgapi.mods.wireless.PhoneBar의 보내는 쪽과 같은 코드).
 * DiPO 앱 파트는 진입 클래스와 클래스 로더가 달라 PhoneBar를 쓰지 못해 같은 코드를 두었다(Mac과 같은 이유).
 * 이름·값·간격은 PhoneBar와 같아야 한다(PC 시험이 비교한다).
 */
final class PhoneBarOut {
    static final String ACTION = "hmgapi.mods.wireless.PHONE_BAR";
    static final String EXTRA_BAR = "bar";
    static final int NONE = -1;
    static final long RESEND_MS = 10000;

    private int last = Integer.MIN_VALUE;
    private long at;

    /** 값이 바뀌었거나, 막대가 있는 동안 RESEND_MS가 지났을 때만 true(그 값을 기억한다) */
    boolean due(int bar, long now) {
        if (bar == last && (bar == NONE || now - at < RESEND_MS)) return false;
        last = bar;
        at = now;
        return true;
    }

    /** 방송 보내기(실패해도 회전 보내기에는 영향 없음). 보낸 값이면 true */
    boolean send(Context c, int bar, long now) {
        if (c == null || !due(bar, now)) return false;
        try {
            Intent i = new Intent(ACTION);
            i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            i.putExtra(EXTRA_BAR, bar);
            c.sendBroadcast(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
