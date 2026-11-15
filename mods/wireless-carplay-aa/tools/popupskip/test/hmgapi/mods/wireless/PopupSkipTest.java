package hmgapi.mods.wireless;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.7.4(2.4.5) PopupSkip PC 시험: 언제 순정 팝업을 건너뛰고 언제 순정 그대로 두는지, 훅을 못 걸 때 죽지 않는지,
 * 로그가 한 번에 한 줄이고 기기 정보가 없는지 본다.
 */
public final class PopupSkipTest {
    static int fail, okCount;
    static final List<String> logs = new ArrayList<String>();
    static final PopupSkip.Log LOG = new PopupSkip.Log() {
        @Override
        public void line(String s) {
            logs.add(s);
        }
    };

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what + (ok ? "" : "\n  logs " + logs));
        if (ok) okCount++;
        else fail = 1;
    }

    static PopupSkip.Devices devices(final int n) {
        return new PopupSkip.Devices() {
            @Override
            public int count() {
                return n;
            }
        };
    }

    static final PopupSkip.Devices THROWS = new PopupSkip.Devices() {
        @Override
        public int count() {
            throw new SecurityException("no provider");
        }
    };

    /** 이름으로 클래스를 찾는 가짜 훅 걸기(Wireless는 Hooks.findAndHookMethod가 같은 일을 한다) */
    static PopupSkip.Installer finder(final String cls, final boolean[] called) {
        return new PopupSkip.Installer() {
            @Override
            public void install() throws Throwable {
                called[0] = true;
                Class<?> c = Class.forName(cls, false, PopupSkipTest.class.getClassLoader());
                c.getDeclaredMethod("onReceive", Object.class, Object.class);
            }
        };
    }

    static boolean skip(boolean modOn, String action, PopupSkip.Devices d) {
        logs.clear();
        return PopupSkip.skip(modOn, action, d, LOG);
    }

    public static void main(String[] a) {
        final String G = PopupSkip.ACTION;

        // 건너뜀: GLOBAL_POPUP + 등록 기기 없음(종류 7·9)
        check("GLOBAL_POPUP, 등록 기기 0 → 건너뜀", skip(true, G, devices(0)));
        check("  건너뜀 줄 한 줄", logs.size() == 1 && logs.get(0).startsWith(PopupSkip.LINE) && logs.get(0).contains("건너뜀"));

        // 순정 그대로: 다른 action
        check("DEVICE_POPUP → 순정 그대로", !skip(true, "com.daudio.app.PhoneProjection.Wireless.DEVICE_POPUP", devices(0)));
        check("INIT_POPUP → 순정 그대로", !skip(true, "com.daudio.app.PhoneProjection.Wireless.INIT_POPUP", devices(0)));
        check("  다른 action은 줄 없음", logs.isEmpty());
        check("대소문자 다른 GLOBAL_POPUP → 순정 그대로", !skip(true, G.toLowerCase(), devices(0)));
        check("GLOBAL_POPUP 앞부분만 → 순정 그대로", !skip(true, "com.daudio.app.PhoneProjection.Wireless.GLOBAL", devices(0)));

        // 순정 그대로: intent·action null
        check("action null(intent null 포함) → 순정 그대로", !skip(true, null, devices(0)));
        check("  null은 줄 없음", logs.isEmpty());

        // 순정 그대로: 모드 꺼짐
        check("모드 꺼짐 → 순정 그대로", !skip(false, G, devices(0)));
        check("  모드 꺼짐은 줄 없음", logs.isEmpty());

        // 순정 그대로: 등록 기기 있음(종류 8·10, 기기 목록에서 골라 연결)
        check("등록 기기 1 → 순정 그대로", !skip(true, G, devices(1)));
        check("  기기 수만 남김", logs.size() == 1 && logs.get(0).contains("등록 기기 1대"));
        check("등록 기기 3 → 순정 그대로", !skip(true, G, devices(3)));

        // 순정 그대로: 기기 수 모름
        check("기기 수 -1 → 순정 그대로", !skip(true, G, devices(-1)));
        check("  모름 줄", logs.size() == 1 && logs.get(0).contains("모름"));
        check("기기 수 읽기 예외 → 순정 그대로(죽지 않음)", !skip(true, G, THROWS));
        check("기기 읽는 쪽 null → 순정 그대로", !skip(true, G, null));

        // 훅 걸기: 설정 끔이면 걸지 않음
        logs.clear();
        boolean[] called = {false};
        check("설정 끔 → 훅 안 검", !PopupSkip.install(false, finder(PopupSkip.RECEIVER, called), LOG));
        check("  설정 끔이면 걸기 시도도 없음·줄 없음", !called[0] && logs.isEmpty());

        // 훅 걸기: 클래스 있음
        logs.clear();
        called[0] = false;
        check("리시버 클래스 있음 → 훅 걸림", PopupSkip.install(true, finder(PopupSkip.RECEIVER, called), LOG));
        check("  걸림 줄 한 줄", called[0] && logs.size() == 1 && logs.get(0).contains("걸림"));

        // 훅 걸기: 클래스 없음 → 죽지 않고 한 줄
        logs.clear();
        boolean ok;
        try {
            ok = !PopupSkip.install(true, finder(PopupSkip.RECEIVER + "Missing", called), LOG);
        } catch (Throwable t) {
            ok = false;
        }
        check("리시버 클래스 없음 → 예외 없이 false", ok);
        check("  없음 줄 한 줄(ClassNotFoundException)", logs.size() == 1 && logs.get(0).contains("없음") && logs.get(0).contains("ClassNotFoundException"));

        // 훅 걸기: 메서드 없음 → 죽지 않고 한 줄
        logs.clear();
        try {
            ok = !PopupSkip.install(true, new PopupSkip.Installer() {
                @Override
                public void install() throws Throwable {
                    Class.forName(PopupSkip.RECEIVER).getDeclaredMethod("onReceive", String.class);
                }
            }, LOG);
        } catch (Throwable t) {
            ok = false;
        }
        check("onReceive 모양 다름 → 예외 없이 false", ok && logs.size() == 1 && logs.get(0).contains("NoSuchMethodException"));

        // 같은 키를 여러 번 떼도 한 번에 한 줄
        logs.clear();
        int lines = 0;
        for (int i = 0; i < 5; i++) {
            PopupSkip.skip(true, G, devices(0), LOG);
            lines = logs.size();
        }
        check("다섯 번 뗌 → 다섯 줄(한 번에 한 줄)", lines == 5);

        // 로그에 기기 이름·주소 같은 값이 없다(숫자는 기기 수뿐)
        boolean clean = true;
        for (String s : logs) if (s.matches(".*[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}.*")) clean = false;
        check("로그에 MAC 모양 없음", clean);

        System.out.println(fail == 0 ? "PopupSkip: " + okCount + " ok" : "PopupSkip: FAIL");
        System.exit(fail);
    }
}
