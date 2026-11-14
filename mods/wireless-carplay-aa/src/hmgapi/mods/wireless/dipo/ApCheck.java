package hmgapi.mods.wireless.dipo;
// Korea Projection 1.7.2 추가(2026-11-09, 계획 P-7 모드 부분, 소유자 결정 2026-11-09 "순정대로").

/**
 * 브리지가 순정 AP 켜기(setWifiApEnabledForConnectivity)를 부를지 정한다(Bridge.ensureHotspot).
 *
 * 순정 PhoneProjection은 켜기 전에 "이미 켜짐(isWifiApEnabled이고 getWifiApMode 2)"이면 건너뛴다(wireless/a/f.k(),
 * drafts/stock-re/decision-stock-check.md 3장). 그런데 순정 상태 머신은 Korea Projection 패키지가 띄운 hostapd를 모른다.
 * 그래서 우리 AP가 돌 때도 매번 순정 켜기를 불렀고, 그때마다 wifi_ap_on이 1로 다시 써졌다(V-3). 순정과 같은 모양으로
 * "우리 AP가 이미 켜짐"도 건너뛴다. 우리 AP 신호는 패키지가 이미 쓰는 것과 같다:
 *   - wlan0에 우리 AP 주소(hotspot.sh AP_IP)가 있음(패키지의 AA 신호 조건 wlan_has_ip와 같은 값)
 *   - 또는 sys.kp.aa_ap=1(패키지가 우리 hostapd와 wlan0 주소를 확인한 뒤에만 켬)
 * 순정 AP 경로 차(tcc803x, 주소가 다름)는 둘 다 아니므로 예전처럼 순정 켜기를 부른다. 순정 프레임워크가 AP를 켰다고 알면
 * (모드 2가 아니어도) 우리 AP가 아니므로 예전처럼 부른다.
 */
final class ApCheck {
    /**
     * Q-1(1.7.4): Wi-Fi 장치 이름을 한 곳에서 정한다(1.8.0 USB 동글 준비). Wireless.WIFI_IF와 같은 값이어야 한다
     * (DiPO 앱 파트는 클래스 로더가 달라 따로 둔다. tools/dupsame 시험이 두 값을 비교한다). 밖에서 바꾸는 길은 아직 없다
     */
    static final String WIFI_IF = "wlan0";
    /** Korea Projection 패키지 hotspot.sh의 AP_IP(우리 hostapd가 wlan0에 거는 주소) */
    static final String OUR_AP_IP = "192.168.43.1";
    /** 패키지가 우리 AP를 확인했을 때 켜는 속성(AaWifi.PROP과 같은 이름) */
    static final String AA_AP_PROP = "sys.kp.aa_ap";

    static final int CALL = 0, SKIP_STOCK_ON = 1, SKIP_OURS = 2;

    private ApCheck() {
    }

    /**
     * frameworkOn·mode: 순정 WifiManager가 아는 AP 상태. aaAp: sys.kp.aa_ap 값. wlanAddrs: wlan0의 IPv4 주소들(null 가능).
     * SKIP_STOCK_ON: 순정 AP가 이미 모드 2로 켜짐(예전과 같은 검사). SKIP_OURS: 우리 AP가 이미 켜짐. CALL: 순정 켜기를 부른다.
     */
    static int decide(boolean frameworkOn, int mode, String aaAp, String[] wlanAddrs) {
        if (frameworkOn && mode == 2) return SKIP_STOCK_ON;
        // 순정 프레임워크가 AP를 안다(켜졌지만 모드 2가 아님): 우리 hostapd가 아니다. 예전처럼 모드 2로 켜기를 부른다(리뷰 6:
        // 192.168.43.1은 안드로이드 순정 핫스팟 기본 주소이기도 하다). 우리 hostapd는 프레임워크가 모르므로 늘 꺼짐으로 보인다
        if (frameworkOn) return CALL;
        if ("1".equals(aaAp)) return SKIP_OURS;
        if (wlanAddrs != null) {
            for (String a : wlanAddrs) if (OUR_AP_IP.equals(a)) return SKIP_OURS;
        }
        return CALL;
    }
}
