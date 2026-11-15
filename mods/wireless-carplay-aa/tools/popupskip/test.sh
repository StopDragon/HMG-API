#!/bin/sh
# Korea Projection 1.7.4(모드 2.4.5): 별표 키 안내 팝업 건너뛰기(PopupSkip)의 PC 시험.
# 순정 PhoneProjection과 같은 이름의 가짜 리시버(stubs)로 클래스 찾기까지 돌린다. 순정 코드는 들어 있지 않다.
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$CP" $(find stubs test -name '*.java') ../../src/hmgapi/mods/wireless/PopupSkip.java
java -Dfile.encoding=UTF-8 -cp "$CP" hmgapi.mods.wireless.PopupSkipTest
