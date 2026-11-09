package hmgapi.mods.wireless.dipo;

/**
 * ApCheck 시험(1.7.2 P-7 모드 부분): Bridge.ensureHotspot이 순정 AP 켜기를 부를지. 실제 WifiManager·NetworkInterface 호출은
 * 안드로이드에 묶여 있어 시험하지 않고, 그 값으로 정하는 부분만 본다.
 */
public final class ApCheckTest {
    static int fail;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fail = 1;
    }

    public static void main(String[] a) {
        check("P-7: stock AP already on in mode 2: skipped as before",
                ApCheck.decide(true, 2, "", null) == ApCheck.SKIP_STOCK_ON);
        check("P-7: our AP address on wlan0 (package hotspot.sh AP_IP): stock AP-on skipped (wifi_ap_on not rewritten)",
                ApCheck.decide(false, 0, "", new String[] { "192.168.43.1" }) == ApCheck.SKIP_OURS);
        check("P-7: sys.kp.aa_ap=1 (the package saw our AP): skipped even if wlan0 can't be read",
                ApCheck.decide(false, 0, "1", null) == ApCheck.SKIP_OURS);
        check("P-7: no AP of ours (another address, aa_ap 0, nothing on wlan0): the stock AP-on is called as before",
                ApCheck.decide(false, 0, "0", new String[] { "192.168.0.1" }) == ApCheck.CALL
                        && ApCheck.decide(false, -1, "", new String[0]) == ApCheck.CALL
                        && ApCheck.decide(false, 0, "", null) == ApCheck.CALL);
        check("P-7: stock AP on but not mode 2 and nothing of ours: called (as before)",
                ApCheck.decide(true, 1, "", null) == ApCheck.CALL);
        check("review 6: the framework knows an AP that isn't mode 2 (e.g. a stock hotspot on the same default address): called as before, not skipped",
                ApCheck.decide(true, 1, "", new String[] { "192.168.43.1" }) == ApCheck.CALL
                        && ApCheck.decide(true, 0, "1", new String[] { "192.168.43.1" }) == ApCheck.CALL);
        check("P-7: the address matches the package's AP_IP", "192.168.43.1".equals(ApCheck.OUR_AP_IP) && "sys.kp.aa_ap".equals(ApCheck.AA_AP_PROP));
        System.exit(fail);
    }
}
