package hmgapi.mods.wireless.dipo;
// Korea Projection 1.7.4(2026-11-15, 모드 2.4.3, 계획 D-20).

import com.tcc.iap2.Iap2NotiMsg;

/**
 * 카플레이 길안내 관리자. Dipo가 부른다(한 스레드).
 *   - CpTurns: 순정 DiPO e/u와 같게 전달(2.4.2, 설정 tbt_fit 끔)
 *   - CpTurnsFit: 그 위에 현대·기아 순정 내비 화면에 맞춘 보정(2.4.1의 보정을 되살림, 설정 tbt_fit 켬·기본)
 */
interface CpTbt {
    void routeGuidance(Iap2NotiMsg msg);

    void maneuver(Iap2NotiMsg msg);

    void lanes(Iap2NotiMsg msg);

    void refresh();

    boolean active();

    void disconnected();

    void stop();
}
