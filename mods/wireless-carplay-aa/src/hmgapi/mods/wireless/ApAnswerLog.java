package hmgapi.mods.wireless;

import java.util.HashMap;
import java.util.Map;

/**
 * 2.4.10(1.8.1 M-2, drafts/1.8.1-work/freeze-popups.md 3-3절): GPM·PhoneProjection이 WifiManager.isWifiApEnabled()·getWifiApMode()를
 * 물었을 때 무엇을 답했는지(값과 그때의 패키지 신호)를 남길지 정한다. R-4(AA가 "AP 모드 2" 뒤 멈춤)는 우리 AP가 이미 떠 있을 때도 나서,
 * 부팅 처음 몇 번만으로는 못 가른다. 그래서 물음마다 처음 FIRST번과 값이 바뀔 때마다 남기고, 프로세스당 MAX줄에서 멈춘다.
 * 값은 참거짓·숫자뿐이다(SSID·비밀번호·주소 없음). 안드로이드 클래스를 쓰지 않아 PC에서 시험한다(tools/aaready).
 */
final class ApAnswerLog {
    static final int FIRST = 5, MAX = 40;
    /** 로그 줄 머리 */
    static final String HEAD = "Android Auto AP answer: ";
    private final Map<String, String> last = new HashMap<String, String>();
    private final Map<String, Integer> count = new HashMap<String, Integer>();
    private int total;

    /**
     * 남길 줄 또는 남기지 않으면 null. what: 메서드 이름, value: 답한 값, sig: 그때 신호(sys.kp.aa_ap=1), who: 프로세스 짧은 이름(gpm·pp)
     */
    synchronized String line(String who, String what, Object value, boolean sig) {
        if (total >= MAX) return null;
        String v = String.valueOf(value) + " sig " + (sig ? 1 : 0);
        Integer c = count.get(what);
        int n = c == null ? 0 : c.intValue();
        boolean changed = !v.equals(last.get(what));
        if (n >= FIRST && !changed) return null;
        last.put(what, v);
        count.put(what, Integer.valueOf(n + 1));
        total++;
        return HEAD + who + " " + what + " " + v + (total == MAX ? " (limit " + MAX + ")" : "");
    }
}
