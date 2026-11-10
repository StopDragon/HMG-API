#!/bin/sh
# Korea Projection 1.7.3(모드 2.4.1, 계획 A-1): PP가 뜬 뒤 무선 AA 준비(1) 다시 보내기(AaReady)의 PC 시험.
# 순정 GPM과 같은 이름의 가짜 클래스(stubs)로 반사 경로까지 돌린다. 순정 코드는 들어 있지 않다.
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$CP" $(find stubs test -name '*.java') ../../src/hmgapi/mods/wireless/AaReady.java
java -Dfile.encoding=UTF-8 -cp "$CP" hmgapi.mods.wireless.AaReadyTest
