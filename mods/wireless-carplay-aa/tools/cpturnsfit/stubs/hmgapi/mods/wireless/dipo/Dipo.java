package hmgapi.mods.wireless.dipo;

/** host stand-in: CpTurns only logs through it (1.7.2 진단 시험: 남긴 줄을 모은다) */
final class Dipo {
    static final java.util.List<String> logs = new java.util.ArrayList<String>();

    static void log(String m) {
        logs.add(m);
    }
}
