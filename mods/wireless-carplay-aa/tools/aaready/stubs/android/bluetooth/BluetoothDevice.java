package android.bluetooth;

/** PC 시험용 가짜: 주소만 */
public final class BluetoothDevice {
    final String address;

    public BluetoothDevice(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }
}
