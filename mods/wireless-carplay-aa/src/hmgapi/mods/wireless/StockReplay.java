package hmgapi.mods.wireless;
// Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-08, 모드 2.3.1 판 그대로(1.7.2 리뷰 2, 2차 리뷰 1·6·7·8 반영).

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 폰 안내가 끝난 뒤 순정 내비의 계기판 값을 다시 보내기 위한 기억(내비 프로세스 안, Wireless.navigation이 쓴다).
 * 안드로이드 없이 PC에서 시험하려고 따로 뺐다. 실제 보내기(Method.invoke)는 Wireless가 한다.
 *
 * 왜: 폰 안내가 끝나면 카플레이(CpTurns.stop)·AA(AaTurns.stop)가 계기판을 순정 무안내 값으로 지운다. 이 지우기는
 * DiPO·GPM 프로세스에서 나가고 순정 값은 내비 프로세스에서 나가므로 순서가 정해져 있지 않다. 또 순정 엔진은 회전·HUD
 * 목록·차로·2C 등을 값이 바뀔 때만 보낸다. 그래서 지우기가 순정 값보다 늦게 도착하거나, 폰 안내 전에 보낸 순정 값이
 * 그 뒤 바뀌지 않았으면 계기판이 빈 채로 남는다.
 *
 * 어떻게(2차 리뷰 반영):
 *  - NAVI_TBT 순정 호출을 막았든 통과했든 **항상** 메서드별 최신 값(대상·메서드·인자 사본)으로 기억한다. 기억 순서는
 *    최근 갱신 순서다(갱신하면 맨 뒤로). 그래서 기억은 늘 "엔진이 마지막으로 보낸 값"이다(메서드 13개).
 *  - 폰 안내 중 막은 일이 있었고 그 뒤 처음 통과하는 순정 호출에서 안내 끝을 알아챈다(endedAt).
 *  - 다시 보내기는 순정 호출 훅 안(순정 스레드)에서 한다: 끝을 안 뒤 지나는 순정 호출 때 REPLAY_MS[0](1.5초)가
 *    지났으면 1차, REPLAY_MS[1](5초)가 지났으면 2차로, 기억한 값을 갱신 순서대로 보낸다. 지금 지나는 호출의
 *    메서드는 빼고(그 호출이 바로 뒤에 최신 값으로 나간다). 별도 타이머와의 경쟁·옛 값이 나중에 도착하는 문제가 없다.
 *  - 순정 호출이 오지 않으면(엔진이 갱신을 멈춤) 백업 타이머 하나(BACKUP_MS)가 남은 차례를 한 번에 끝낸다.
 *    세대 번호가 같고 아직 끝나지 않았을 때만 보낸다.
 *  - 그 사이 폰 안내가 다시 시작되면(막힘) 이번 끝은 버린다(세대가 바뀜).
 * 한 번의 끝에 다시 보내기는 많아야 두 차례이고, 2차(또는 백업) 뒤 끝난다. 끝나지 않는 반복은 없다.
 * 우리가 다시 보내는 호출은 Wireless가 REPLAYING 표시로 이 기억·막기를 건너뛰게 한다(되먹임 없음).
 */
final class StockReplay {
    /** 폰 안내 끝을 안 뒤 다시 보내는 때(ms). 지우기가 늦게 와도 그 뒤에 한 번 더 보낸다 */
    static final long[] REPLAY_MS = { 1500L, 5000L };
    /** 순정 호출이 오지 않을 때 남은 차례를 끝내는 백업 타이머(ms, 끝을 안 때부터) */
    static final long BACKUP_MS = 6000L;

    /** 메서드 이름 → {대상, 메서드 객체, 인자 사본}. 최근 갱신 순서 */
    private final LinkedHashMap<String, Object[]> last = new LinkedHashMap<String, Object[]>();
    /** 지난 다시 보내기 뒤 폰 안내 때문에 막은 순정 호출이 있었다 */
    private boolean blockedSince;
    /** 폰 안내 끝을 안 시각(-1: 기다리는 끝 없음) */
    private long endedAt = -1L;
    private int step, gen;

    /** call()의 결과: 지금 보낼 것과, 백업 타이머를 걸어야 하면 그 세대 번호 */
    static final class Out {
        /** 지금 다시 보낼 {대상, 메서드 객체, 인자} 목록(갱신 순서) */
        final List<Object[]> resend = new ArrayList<Object[]>();
        /** 0이 아니면 이 세대로 BACKUP_MS 뒤 backup()을 부른다 */
        int backupGen;
        /** 몇 번째 차례인지(로그용, 0: 없음) */
        int round;
    }

    /**
     * 순정 호출 하나(우리가 다시 보낸 호출은 빼고). phone: 폰 안내 중이라 이 호출을 막는다. now: 시각(ms).
     */
    synchronized Out call(String name, Object target, Object method, Object[] args, boolean phone, long now) {
        Out o = new Out();
        last.remove(name);
        last.put(name, new Object[] { target, method, args == null ? new Object[0] : args.clone() });
        if (phone) {
            blockedSince = true;
            if (endedAt >= 0) {
                // 다시 보내기 전에 폰 안내가 다시 시작됐다: 이번 끝은 버린다
                endedAt = -1L;
                gen++;
            }
            return o;
        }
        if (!blockedSince) return o;
        if (endedAt < 0) {
            endedAt = now;
            step = 0;
            o.backupGen = ++gen;
            return o;
        }
        if (step < REPLAY_MS.length && now - endedAt >= REPLAY_MS[step]) {
            // 여러 차례가 지났으면 한 번만 보내고 마지막 차례로 친다
            while (step < REPLAY_MS.length && now - endedAt >= REPLAY_MS[step]) step++;
            o.round = step;
            snapshot(o.resend, name);
            if (step >= REPLAY_MS.length) finish();
        }
        return o;
    }

    /** 백업 타이머: 순정 호출이 오지 않아 남은 차례가 있으면 지금 한 번 보내고 끝낸다 */
    synchronized Out backup(int g) {
        Out o = new Out();
        if (g != gen || endedAt < 0 || step >= REPLAY_MS.length) return o;
        o.round = REPLAY_MS.length;
        snapshot(o.resend, null);
        finish();
        return o;
    }

    private void snapshot(List<Object[]> to, String except) {
        for (Iterator<Map.Entry<String, Object[]>> it = last.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Object[]> e = it.next();
            if (e.getKey().equals(except)) continue;
            Object[] v = e.getValue();
            to.add(new Object[] { v[0], v[1], ((Object[]) v[2]).clone(), e.getKey() });
        }
    }

    private void finish() {
        endedAt = -1L;
        blockedSince = false;
        step = REPLAY_MS.length;
        gen++;
    }

    /** 시험용: 기억한 메서드 이름(갱신 순서) */
    synchronized List<String> names() {
        return new ArrayList<String>(last.keySet());
    }

    /** 시험용: 기다리는 끝이 있는지 */
    synchronized boolean pending() {
        return endedAt >= 0;
    }
}
