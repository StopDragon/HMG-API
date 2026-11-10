package com.google.android.projection.protocol;

import android.bluetooth.BluetoothDevice;
import android.phoneprojection.WLDeviceInfo;

import java.util.HashMap;

/**
 * PC 시험용 가짜 GPM WirelessExplorer: AaReady가 반사로 읽는 이름만 순정(니로 DEPEHEV 260902, baksmali -a 18)과 같게 둔다.
 * 순정 코드는 옮기지 않았다.
 */
public class WirelessExplorer {
    private static WirelessExplorer mWirelessExplorer;
    private static WirelessListener mWirelessListener;
    HashMap<String, DeviceInfo> mALConnectedThread = new HashMap<String, DeviceInfo>();
    HashMap<String, WLDeviceInfo> mWLDeviceMap = new HashMap<String, WLDeviceInfo>();
    private String mConnectedDeviceAddress = "";

    public interface WirelessListener {
        void requestDeviceReadyStatusUpdate(WLDeviceInfo info, int ready);

        void requestDeviceConnectStatusUpdate(WLDeviceInfo info, int status);
    }

    public static class DeviceRegistry {
        private BluetoothDevice mRegBluetoothDevice;
        private String mWirelessDeviceSerial;
        private boolean mWirelessSupport;

        public void associate(BluetoothDevice d, String serial, boolean support) {
            mRegBluetoothDevice = d;
            mWirelessDeviceSerial = serial;
            mWirelessSupport = support;
        }

        public String getWirelessDeviceSerial() {
            return mWirelessDeviceSerial;
        }

        public boolean getWirelessSupport() {
            return mWirelessSupport;
        }
    }

    static class DeviceInfo {
        BluetoothDevice mDevice;
        DeviceRegistry mDevice_Registry;
    }

    public static WirelessExplorer getWirelessExplorer() {
        return mWirelessExplorer;
    }

    public static WirelessListener getWirelessListener() {
        return mWirelessListener;
    }

    public static void setWirelessListener(WirelessListener l) {
        mWirelessListener = l;
    }

    public String getConnectedDevice() {
        return mConnectedDeviceAddress;
    }

    /** 순정처럼 기기에서 새 정보를 만든다(협상한 시리얼 포함) */
    public WLDeviceInfo getWLDeviceInfo(BluetoothDevice d) {
        DeviceInfo i = mALConnectedThread.get(d.getAddress());
        String serial = i == null || i.mDevice_Registry == null ? null : i.mDevice_Registry.getWirelessDeviceSerial();
        return new WLDeviceInfo(d.getAddress(), serial);
    }

    // ---- 시험에서 상태를 만드는 곳(순정에는 없음)

    public static WirelessExplorer reset() {
        mWirelessExplorer = new WirelessExplorer();
        mWirelessListener = null;
        return mWirelessExplorer;
    }

    public static void clear() {
        mWirelessExplorer = null;
        mWirelessListener = null;
    }

    public void setConnected(String a) {
        mConnectedDeviceAddress = a;
    }

    /** RFCOMM 협상 시작(startRfcommNego: 두 맵에 들어감) */
    public void rfcomm(String addr) {
        DeviceInfo i = new DeviceInfo();
        i.mDevice = new BluetoothDevice(addr);
        mALConnectedThread.put(addr, i);
        mWLDeviceMap.put(addr, new WLDeviceInfo(addr, null));
    }

    /** 버전 응답(handleVersionResponse: 레지스트리가 채워짐) */
    public void version(String addr, String serial, boolean support) {
        DeviceInfo i = mALConnectedThread.get(addr);
        DeviceRegistry r = new DeviceRegistry();
        r.associate(i.mDevice, serial, support);
        i.mDevice_Registry = r;
    }

    public void dropWl(String addr) {
        mWLDeviceMap.remove(addr);
    }
}
