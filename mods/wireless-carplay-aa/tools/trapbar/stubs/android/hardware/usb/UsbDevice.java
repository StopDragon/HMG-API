package android.hardware.usb;

/** PC 시험용 가짜(컴파일용) */
public class UsbDevice {
    public int getInterfaceCount() { return 0; }

    public UsbInterface getInterface(int i) { return null; }

    public String getDeviceName() { return ""; }
}
