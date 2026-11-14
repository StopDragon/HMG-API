import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 1.7.4(L-3·Q-1) 두 벌 동일성 시험. 인자: 모드 소스의 hmgapi/mods/wireless 폴더.
 * 기기 주소는 가짜 값이다.
 */
public final class DupSameTest {
    static int fail;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fail = 1;
    }

    static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** "static String mask(" 부터 파일 끝까지(주석·package 줄 빼고 동작 코드만 비교) */
    static String maskBody(String src) {
        int i = src.indexOf("static String mask(");
        return i < 0 ? null : src.substring(i);
    }

    /** 주석을 지운 코드 */
    static String code(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    static String constant(String src, String name) {
        Matcher m = Pattern.compile("static final String " + name + " = \"([^\"]*)\";").matcher(src);
        return m.find() ? m.group(1) : null;
    }

    public static void main(String[] a) throws Exception {
        Path dir = Paths.get(a[0]);
        // L-3: Mac 두 벌
        Method m1 = Class.forName("hmgapi.mods.wireless.Mac").getDeclaredMethod("mask", String.class);
        Method m2 = Class.forName("hmgapi.mods.wireless.dipo.Mac").getDeclaredMethod("mask", String.class);
        m1.setAccessible(true);
        m2.setAccessible(true);
        String[] in = { null, "", "00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF", "00-11-22-33-44-55", "00:11:22:33:44:5", "00:11:22:33:44:555",
                "001122334455", "x0:11:22:33:44:5x", "0011:2233:4455:66", "폰 이름", "abc" };
        boolean same = true;
        for (String s : in) {
            Object r1 = m1.invoke(null, s), r2 = m2.invoke(null, s);
            if (!String.valueOf(r1).equals(String.valueOf(r2))) same = false;
        }
        check("L-3: the two Mac.mask give the same output for " + in.length + " inputs (address, near-address, name, null)", same);
        check("L-3: an address is fully hidden by both", "xx:xx:xx:xx:xx:xx".equals(m1.invoke(null, "00:11:22:33:44:55"))
                && "xx:xx:xx:xx:xx:xx".equals(m2.invoke(null, "00:11:22:33:44:55")));
        check("L-3: anything else leaves only its length in both", "(12 chars)".equals(m1.invoke(null, "001122334455"))
                && "(12 chars)".equals(m2.invoke(null, "001122334455")));
        String b1 = maskBody(read(dir.resolve("Mac.java"))), b2 = maskBody(read(dir.resolve("dipo/Mac.java")));
        check("L-3: the mask code is the same text in both files (only the package line and the class comment differ)",
                b1 != null && b1.equals(b2));

        // Q-1: Wi-Fi 장치 이름
        String w = constant(read(dir.resolve("Wireless.java")), "WIFI_IF");
        String d = constant(read(dir.resolve("dipo/ApCheck.java")), "WIFI_IF");
        check("Q-1: Wireless.WIFI_IF = ApCheck.WIFI_IF = \"wlan0\" (as 2.4.1)", "wlan0".equals(w) && "wlan0".equals(d));
        // 그 두 상수 밖의 코드에 장치 이름 글자가 박혀 있지 않음(주석·로그 문구 속 wlan0은 그대로 둠)
        final List<String> stray = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Object o : files.toArray()) {
                Path p = (Path) o;
                if (!p.toString().endsWith(".java")) continue;
                String c = code(read(p)).replace("static final String WIFI_IF = \"wlan0\";", "");
                if (c.contains("\"wlan0\"")) stray.add(dir.relativize(p).toString());
            }
        }
        check("Q-1: no other \"wlan0\" literal in the mod's code " + stray, stray.isEmpty());
        String bridge = code(read(dir.resolve("dipo/Bridge.java")));
        check("Q-1: Bridge reads the AP addresses through ApCheck.WIFI_IF", bridge.contains("NetworkInterface.getByName(ApCheck.WIFI_IF)"));
        System.exit(fail);
    }
}
