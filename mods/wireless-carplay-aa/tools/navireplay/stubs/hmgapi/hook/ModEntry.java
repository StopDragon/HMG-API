package hmgapi.hook;

/** PC 시험용 가짜 ModEntry */
public abstract class ModEntry {
    public void zygoteInit(ModContext mod) throws Throwable {}

    public void appLoaded(ModContext mod, LoadedApp app) throws Throwable {}
}
