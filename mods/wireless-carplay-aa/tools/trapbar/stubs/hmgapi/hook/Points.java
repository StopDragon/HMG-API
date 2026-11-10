package hmgapi.hook;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/** PC 시험용 가짜 Points: 시험이 정한 클래스만 돌려준다 */
public final class Points {
    public static final Map<String, Class<?>> CLASSES = new HashMap<String, Class<?>>();

    public static Class<?> cls(ClassLoader cl, String id) {
        Class<?> c = CLASSES.get(id);
        if (c == null) throw new IllegalArgumentException("no point " + id);
        return c;
    }

    public static Field field(ClassLoader cl, String id) {
        throw new IllegalArgumentException("no field " + id);
    }

    public static Object call(ClassLoader cl, String id, Object target, Object... args) {
        return null;
    }

    public static Object get(ClassLoader cl, String id, Object target) {
        return null;
    }
}
