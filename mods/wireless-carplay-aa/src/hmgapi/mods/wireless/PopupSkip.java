package hmgapi.mods.wireless;

/**
 * 한국 차 빌드(korea-build) 1.7.4(모드 2.4.5): 별표 키 안내 팝업 건너뛰기(설정 star_popup_skip, 기본 켬).
 *
 * 원인(니로 순정 PhoneProjection 코드 확인, drafts/1.7.4-work/mod.md "화면 멈춤의 계기인 순정 팝업" 절):
 *  - 모드가 무선 기능 플래그를 켜면 순정 무선 서비스(WirelessPhoneProjectionService)가 부팅 때 뜬다.
 *    순정 니로는 이 기능이 꺼져 있어 서비스가 아예 없다.
 *  - 그 서비스의 키 리스너는 별표 키(279, 설정 "폰 프로젝션 바로가기")를 뗄 때마다 GLOBAL_POPUP을 방송한다.
 *    카플레이 연결 여부는 보지 않는다.
 *  - WirelessPhoneProjectionReceiver가 그것을 받아 연결 방법 안내(WirelessAlertDialogActivity)를 띄운다.
 *  - 같은 키로 DiPO도 카플레이 화면을 띄우므로 순서 경쟁이 생긴다. 니로에서 팝업이 카플레이 화면을 덮은 뒤
 *    화면·키가 재부팅까지 묶였다(2026-11-15 16:59:34).
 *
 * 고침: 리시버의 onReceive 앞에서 action이 GLOBAL_POPUP이고 순정 무선 등록 기기가 하나도 없으면(팝업 종류 7·9,
 * 연결 방법 안내뿐) 원래 메서드를 부르지 않는다. 다른 action은 그대로 넘긴다.
 *  - 등록 기기가 있으면(종류 8·10) 순정 그대로 둔다. 그 팝업은 등록 기기 목록을 보여 주고, 누르면 그 기기에
 *    연결한다(SEND_CONNECTION_PHONEPROJECTION). 순정 무선을 실제로 쓰는 차의 기능이다.
 *  - 기기 수는 순정 표(device_list)의 전체 줄 수로 센다. 순정은 현재 사용자 줄만 센다. 다른 사용자의 기기가 있으면
 *    우리는 순정 그대로 둔다(덜 막는 쪽).
 *  - 기기 수를 못 읽으면 순정 그대로 둔다.
 *
 * 이 클래스는 판단만 한다(안드로이드·HMG-API 클래스를 부르지 않음). 훅은 Wireless가 걸고, PC 시험은
 * tools/popupskip이 한다. 로그에는 기기 수만 쓴다(이름·주소 없음). 키를 뗄 때 한 줄이다.
 */
final class PopupSkip {
    private PopupSkip() {}

    static final String PHONEPROJ = "com.daudio.app.phoneprojection";
    static final String RECEIVER = "com.daudio.app.phoneprojection.wireless.receiver.WirelessPhoneProjectionReceiver";
    static final String ACTION = "com.daudio.app.PhoneProjection.Wireless.GLOBAL_POPUP";
    static final String DEVICES = "content://com.daudio.app.phoneprojection.wireless.provider.WPPDBProvider/device_list";
    /** 로그 줄 머리 */
    static final String LINE = "별표 키 안내 팝업 ";

    interface Log {
        void line(String s);
    }

    /** 훅을 거는 쪽(Wireless: Hooks.findAndHookMethod, 시험: 가짜). 클래스·메서드가 없으면 예외 */
    interface Installer {
        void install() throws Throwable;
    }

    /** 순정 무선 등록 기기 수를 읽는 쪽(Wireless: ContentResolver, 시험: 가짜). 못 읽으면 예외나 음수 */
    interface Devices {
        int count() throws Throwable;
    }

    /**
     * 설정이 꺼져 있으면 훅을 걸지 않는다(순정 그대로). 걸었으면 true. 클래스·메서드가 없어 못 걸면 한 줄 남기고
     * false(예외를 밖으로 내지 않는다).
     */
    static boolean install(boolean setting, Installer i, Log log) {
        if (!setting) return false;
        try {
            i.install();
            log.line(LINE + "건너뛰기 훅 걸림 (등록 기기 없을 때만)");
            return true;
        } catch (Throwable t) {
            log.line(LINE + "건너뛰기 훅 없음, 순정 그대로 (" + t.getClass().getSimpleName() + ")");
            return false;
        }
    }

    /**
     * onReceive 앞에서 부른다. true면 원래 메서드를 부르지 않는다.
     * modOn: 모드가 켜져 있는가(꺼졌으면 순정 그대로). action: 받은 intent의 action(null 가능).
     */
    static boolean skip(boolean modOn, String action, Devices d, Log log) {
        if (!modOn || !ACTION.equals(action)) return false;
        int n;
        try {
            n = d == null ? -1 : d.count();
        } catch (Throwable t) {
            n = -1;
        }
        if (n != 0) {
            log.line(LINE + (n < 0 ? "순정 그대로 (등록 기기 수 모름)" : "순정 그대로 (등록 기기 " + n + "대)"));
            return false;
        }
        log.line(LINE + "건너뜀 (등록 기기 없음)");
        return true;
    }
}
