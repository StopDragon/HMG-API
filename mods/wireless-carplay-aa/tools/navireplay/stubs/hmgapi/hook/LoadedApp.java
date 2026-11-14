package hmgapi.hook;

import android.content.Context;

/** PC 시험용 가짜 LoadedApp */
public final class LoadedApp {
    public final String packageName;
    public final String processName;
    public final ClassLoader classLoader;
    public final Context context;

    public LoadedApp(String packageName, ClassLoader cl, Context context) {
        this.packageName = packageName;
        this.processName = packageName;
        this.classLoader = cl;
        this.context = context;
    }
}
