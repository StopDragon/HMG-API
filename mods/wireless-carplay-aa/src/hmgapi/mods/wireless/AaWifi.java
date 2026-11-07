package hmgapi.mods.wireless;

import java.lang.reflect.Method;

import android.net.wifi.WifiManager;

import hmgapi.hook.Hooks;
import hmgapi.hook.LoadedApp;
import hmgapi.hook.MethodHook;
import hmgapi.hook.ModContext;

/**
 * 한국 차 빌드(korea-build) 전용: 무선 안드로이드 오토가 Wi-Fi 서비스 밖에서 띄운 AP를 쓰게 한다.
 *
 * 한국 내수 차는 Korea Projection 패키지가 hostapd를 직접 띄운다(순정 Wi-Fi 서비스는 AP를 모른다).
 * 순정 안드로이드 오토 앱(HKMC_GPM, com.google.android.projection.sink)과 폰 프로젝션 앱
 * (PhoneProjection, com.daudio.app.phoneprojection)은 WifiManager.isWifiApEnabled()가 참이고
 * getWifiApMode()가 2(카플레이·AA용 AP)일 때만 폰에 Wi-Fi 정보를 보낸다. 그래서 거기서 멈춘다.
 * SSID·비밀번호(getWifiApConfiguration의 ConnectivitySSID·ConnectivityPSK)와 호스트 IP·BSSID(wlan0에서
 * 직접 읽음)는 이미 우리 AP 값과 같아 손대지 않는다.
 *
 * 패키지가 우리 AP를 돌리고 차주가 켜기 파일을 둔 동안에만 시스템 속성 sys.kp.aa_ap=1을 건다(재부팅하면
 * 사라짐). 이 값이 1일 때만, 이 두 앱의 프로세스 안에서만:
 *  - isWifiApEnabled() → true, getWifiApMode() → 2
 *  - setWifiApEnabledForConnectivity·setWifiApEnabled → 순정 Wi-Fi 서비스를 부르지 않고 true
 *    (PhoneProjection은 AP가 켜져 있다고 보면 끄고 다시 켜려 한다: 순정 Wi-Fi 서비스가 우리 AP·드라이버를
 *    건드리지 않게 막는다)
 *  - setWifiApConfiguration → 저장하지 않고 true(PhoneProjection의 비밀번호 새로 만들기가 softap.conf를 바꾸면
 *    우리 hostapd는 옛 비밀번호를 쓰고 폰에는 새 비밀번호가 가서 카플레이까지 끊긴다, 리뷰 M3)
 * 값이 1이 아니면 원래 메서드를 그대로 부른다. 카플레이(DiPO·Iap2) 프로세스에는 걸지 않는다.
 *
 * AOSP 이름 그대로인 메서드라 HMG-API 지도 점이 없다. 그래서 Hooks.findAndHookMethod로 직접 걸고,
 * 모드가 꺼졌는지는 여기서 본다. 로그에는 값(SSID·비밀번호)을 쓰지 않는다.
 */
final class AaWifi {
    private AaWifi() {}

    static final String PHONEPROJ = "com.daudio.app.phoneprojection";
    static final String PROP = "sys.kp.aa_ap";
    /** 한 프로세스에 한 번만 건다(같은 프로세스에 앱이 여럿 올라와도) */
    private static boolean hooked;
    private static Method getProp;
    /** 켜기·끄기 막음 로그는 프로세스마다 몇 줄만 */
    private static int blockLogs;

    static boolean on(ModContext mod) {
        if (!mod.enabled()) return false;
        try {
            if (getProp == null) getProp = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class);
            return "1".equals(getProp.invoke(null, PROP, "0"));
        } catch (Throwable t) {
            return false;
        }
    }

    static synchronized void hook(final ModContext mod, LoadedApp app) {
        if (hooked) return;
        hooked = true;
        final String who = app.processName;
        int n = 0;
        n += hook1(mod, "isWifiApEnabled", new Object[0], Boolean.TRUE);
        n += hook1(mod, "getWifiApMode", new Object[0], Integer.valueOf(2));
        n += hookSet(mod, "setWifiApEnabledForConnectivity");
        n += hookSet(mod, "setWifiApEnabled");
        n += hookSet(mod, "setWifiApConfiguration");
        mod.log("Android Auto AP hooks in " + who + ": " + n + " of 5 (active only while " + PROP + "=1)");
    }

    private static int hook1(final ModContext mod, final String name, Object[] types, final Object result) {
        try {
            Object[] a = new Object[types.length + 1];
            System.arraycopy(types, 0, a, 0, types.length);
            a[types.length] = new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    if (on(mod)) p.setResult(result);
                }
            };
            Hooks.findAndHookMethod(WifiManager.class, name, a);
            return 1;
        } catch (Throwable t) {
            mod.error("Android Auto AP: no WifiManager." + name, t);
            return 0;
        }
    }

    /** setWifiApEnabled*(WifiConfiguration, boolean)과 setWifiApConfiguration(WifiConfiguration): 우리 AP가 도는 동안 막는다 */
    private static int hookSet(final ModContext mod, final String name) {
        try {
            MethodHook h = new MethodHook() {
                @Override
                protected void beforeHookedMethod(Param p) {
                    if (!on(mod)) return;
                    p.setResult(Boolean.TRUE);
                    if (blockLogs < 5) {
                        blockLogs++;
                        mod.log("Android Auto AP: " + name + (p.args.length > 1 ? "(" + p.args[1] + ")" : "") + " skipped, the package runs the AP");
                    }
                }
            };
            if ("setWifiApConfiguration".equals(name)) {
                Hooks.findAndHookMethod(WifiManager.class, name, "android.net.wifi.WifiConfiguration", h);
            } else {
                Hooks.findAndHookMethod(WifiManager.class, name, "android.net.wifi.WifiConfiguration", boolean.class, h);
            }
            return 1;
        } catch (Throwable t) {
            mod.error("Android Auto AP: no WifiManager." + name, t);
            return 0;
        }
    }
}
