package hmgapi.mods.wireless;
// Korea Projection 1.7.4(2026-11-15, 모드 2.4.3, 계획 D-20).

/**
 * 안드로이드 오토 길안내 관리자 하나(연결마다). Wireless가 GPM 훅에서 부른다(한 스레드).
 *   - AaTurns: 순정 GPM GpmNaviTBTManager와 같게 전달(2.4.2, 설정 tbt_fit 끔)
 *   - AaTurnsFit: 그 위에 현대·기아 순정 내비 화면에 맞춘 보정(곧 회전 큰 그림·막대·경계 여유·이름 붙잡기·대체 번호·재탐색 표시,
 *     2.4.1의 보정을 되살림, 설정 tbt_fit 켬·기본)
 */
interface AaTbt {
    void nextTurn(String str, int side, int ev, int angle, int number);

    void nextTurnDistance(int meters, int seconds, int e3, int unitFlags);

    void status(int s);

    void stop();

    void focusPhone();

    void end();
}
