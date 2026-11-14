#!/bin/sh
# 순정 내비 프로세스의 Wireless.navigation(폰 안내 중 순정 계기판 호출 막기와 끝난 뒤 다시 보내기: resend·scheduleBackup)과
# 옛 설정이 남은 차를 가짜 시계·대기열·계기판(stubs/, test/FakeNav)으로 돌린다. 모드 소스는 그대로 컴파일한다.
# 1.7.3 K-10 tools/trapbar에서 막대(TrapBar) 몫을 빼고 남긴 것이다(1.7.4).
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$CP" $(find stubs test -name '*.java') ../../src/hmgapi/mods/wireless/*.java
java -Dfile.encoding=UTF-8 -cp "$CP" hmgapi.mods.wireless.NaviReplayTest
