package com.google.android.projection.service;

import android.phoneprojection.WLDeviceInfo;

/**
 * PC 시험용 가짜 GPM GalReceiverService: AaReady·Wireless가 쓰는 이름만, 접근 수준은 순정(니로 260902)과 같게.
 * 순정: public final 클래스, getGpmConnectMode()는 public final, 안쪽 PPActionListener는 패키지 접근(final this$0).
 */
public final class GalReceiverService {
    public int connectMode;

    public final int getGpmConnectMode() {
        return connectMode;
    }

    /** 순정과 같은 이름·인자(준비 시각 훅이 거는 곳). 가짜에서는 아무것도 하지 않는다 */
    public final void requestWLDeviceReadyStatusUpdate(WLDeviceInfo info, int ready) {
    }

    public Object listener() {
        return new PPActionListener();
    }

    /** 순정은 IPPActionListener를 구현하는 안쪽 클래스(패키지 접근)다. javac도 바깥 객체를 final this$0에 둔다 */
    class PPActionListener {
        public void runningPP() {
        }
    }
}
