package android.phoneprojection;

/** PC 시험용 가짜: 순정 WirelessExplorer.getWLDeviceInfo가 만드는 기기 정보(주소·시리얼만) */
public final class WLDeviceInfo {
    public final String addr, serial;

    public String getWLDevBtAddr() {
        return addr;
    }

    public WLDeviceInfo(String addr, String serial) {
        this.addr = addr;
        this.serial = serial;
    }
}
