package hmgapi.hook;

/** PC 시험용 가짜 Cluster(이 시험은 AA 회전 안내를 돌리지 않음: 컴파일용) */
public final class Cluster {
    public static final int UNIT_M = 0, UNIT_KM = 1, UNIT_MI = 2, UNIT_FT = 3, UNIT_YD = 4;

    public static Cluster of(android.content.Context c) { return null; }

    public void tbtInfo(int a, int b, float c, float d, int e, int f, int g, int h, int i) {}
    public void tbtSubInfo(float a, int b, int c, int d, int e, int f, int g, float h, int i, int j) {}
    public void tbtSubInfo2C(int a, int b, int c, int d, int e, int f, int g, int h, int i) {}
    public void turnListNew(byte[] f) {}
    public void laneInfo(byte[] f) {}
    public void destinationName(int type, String name) {}
    public void crossroadName(String name) {}
    public void remainDis(float a, int b, int c, int d, int e, int f, int g, float h, int i, int j) {}

    public static final class Turn {
        public int type, direction, traffic, no, unit;
        public String name = "", exitNumber = "";
        public float distance;
    }

    public void turnList(Turn[] t) {}
}
