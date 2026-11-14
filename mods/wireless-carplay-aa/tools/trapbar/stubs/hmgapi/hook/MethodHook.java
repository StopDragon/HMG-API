package hmgapi.hook;

import java.lang.reflect.Member;

/** PC 시험용 가짜(HMG-API MethodHook과 같은 모양). Param 만들기만 시험이 할 수 있게 public */
public abstract class MethodHook {
    protected void beforeHookedMethod(Param param) throws Throwable {}

    protected void afterHookedMethod(Param param) throws Throwable {}

    public static final class Param {
        public final Member method;
        public final Object thisObject;
        public final Object[] args;
        private Object result;
        private boolean returnEarly;

        public Param(Member method, Object thisObject, Object[] args) {
            this.method = method;
            this.thisObject = thisObject;
            this.args = args;
        }

        public Object getResult() { return result; }

        public void setResult(Object r) {
            result = r;
            returnEarly = true;
        }

        public boolean returnEarly() { return returnEarly; }
    }

    /** 시험용: 훅을 부른다(protected를 같은 패키지에서 부를 수 있게) */
    public void before(Param p) throws Throwable { beforeHookedMethod(p); }

    public void after(Param p) throws Throwable { afterHookedMethod(p); }
}
