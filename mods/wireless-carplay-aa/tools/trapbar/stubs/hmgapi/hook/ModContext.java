package hmgapi.hook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PC 시험용 가짜 ModContext: 로그 줄과 지도 지점 훅을 적어 둔다. 설정은 모두 기본값 */
public final class ModContext {
    public final List<String> logs = new ArrayList<String>();
    public final Map<String, MethodHook> points = new LinkedHashMap<String, MethodHook>();
    public final Map<String, Boolean> settings = new java.util.HashMap<String, Boolean>();

    public java.lang.reflect.Member hook(ClassLoader cl, String point, MethodHook hook) {
        points.put(point, hook);
        return null;
    }

    public Class<?> appPart(LoadedApp app, String className) throws Exception {
        throw new ClassNotFoundException(className);
    }

    public void nativePatch(LoadedApp app, String lib) {}

    public boolean enabled() { return true; }

    public boolean settingBoolean(String key, boolean fallback) {
        Boolean v = settings.get(key);
        return v == null ? fallback : v.booleanValue();
    }

    public void log(String message) { logs.add(message); }

    public void error(String message, Throwable t) { logs.add("ERROR " + message + " " + t); }

    /** 시험용: 이 글자가 든 줄 수 */
    public int count(String part) {
        int n = 0;
        for (String l : logs) if (l.contains(part)) n++;
        return n;
    }
}
