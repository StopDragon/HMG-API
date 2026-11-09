package hmgapi.mods.wireless.dipo;
// Korea Projection (StopDragon/HMG-API, branch korea-build), 2026-11-08, 모드 2.3.1 판 그대로(1.7.2 리뷰 5, 2차 리뷰 6).

/**
 * 로그용 기기 주소(MAC) 가리기(Bridge·Dipo가 쓴다). 프로젝트 규칙상 MAC은 로그에 남기지 않으므로 끝 바이트도 남기지 않고
 * 모두 가린다(2차 리뷰 6, 소유자 결정). 주소 꼴이 아니면 글자 수만 남긴다. 같은 코드가 다른 꾸러미에도 있다:
 * 진입 클래스(Wireless)와 DiPO 앱 파트(dipo)는 클래스 로더가 달라 한 곳에 두지 않았다.
 */
final class Mac {
    private Mac() {}

    static String mask(String a) {
        if (a == null) return "null";
        if (a.length() == 17 && a.charAt(2) == ':' && a.charAt(5) == ':' && a.charAt(14) == ':') return "xx:xx:xx:xx:xx:xx";
        return "(" + a.length() + " chars)";
    }
}
