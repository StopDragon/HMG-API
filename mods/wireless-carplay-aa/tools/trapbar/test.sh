#!/bin/sh
# K-10(Korea Projection 1.7.3): 순정 내비 프로세스의 단속 정보 막대(TrapBar)와 Wireless의 순정 값 다시 보내기
# (resend·scheduleBackup)를 가짜 시계·대기열·계기판(stubs/, test/FakeNav)으로 돌린다. 모드 소스는 그대로 컴파일한다.
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$CP" $(find stubs test -name '*.java') ../../src/hmgapi/mods/wireless/*.java
java -Dfile.encoding=UTF-8 -cp "$CP" hmgapi.mods.wireless.TrapBarTest
