package hmgapi.hook;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PC 시험용 가짜 Hooks: findAndHookMethod는 메서드를 찾아 훅을 적어 두기만 한다(실제로 바꾸지 않음).
 * call()이 HMG-API 런타임처럼 before → (결과가 없으면) 원래 메서드 → after 차례로 부른다.
 * 반사로 부른 호출(우리 다시 보내기)에는 훅이 걸리지 않는다: 실제로는 걸리지만 Wireless.REPLAYING 표시로 그대로 통과하므로 같다.
 */
public final class Hooks {
    public static final Map<Method, MethodHook> HOOKED = new LinkedHashMap<Method, MethodHook>();

    public static Method findAndHookMethod(Class<?> cls, String name, Object... parameterTypesAndHook) {
        int n = parameterTypesAndHook.length - 1;
        Class<?>[] types = new Class<?>[n];
        for (int i = 0; i < n; i++) {
            Object t = parameterTypesAndHook[i];
            try {
                types[i] = t instanceof Class ? (Class<?>) t : Class.forName((String) t, false, cls.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            Method m = cls.getDeclaredMethod(name, types);
            HOOKED.put(m, (MethodHook) parameterTypesAndHook[n]);
            return m;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static Method hooked(String name) {
        for (Method m : HOOKED.keySet()) if (m.getName().equals(name)) return m;
        return null;
    }

    /** 순정이 훅 걸린 메서드를 부른 것처럼 */
    public static Object call(Object target, Method m, Object... args) throws Throwable {
        MethodHook h = HOOKED.get(m);
        MethodHook.Param p = new MethodHook.Param(m, target, args);
        if (h != null) h.before(p);
        if (!p.returnEarly()) m.invoke(target, p.args);
        if (h != null) h.after(p);
        return p.getResult();
    }
}
