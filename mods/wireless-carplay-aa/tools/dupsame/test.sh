#!/bin/sh
# Korea Projection 1.7.4(모드 2.4.2, 계획 L-3·Q-1): 클래스 로더가 달라 두 벌로 둔 것이 같은 동작인지 PC에서 본다.
#  - Mac.mask: wireless/Mac.java와 wireless/dipo/Mac.java(합치지 않음, 리뷰 근거는 drafts/1.7.4-work/mod.md)
#  - Wi-Fi 장치 이름: Wireless.WIFI_IF와 dipo ApCheck.WIFI_IF
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$CP" test/DupSameTest.java \
    ../../src/hmgapi/mods/wireless/Mac.java ../../src/hmgapi/mods/wireless/dipo/Mac.java
java -Dfile.encoding=UTF-8 -cp "$CP" DupSameTest ../../src/hmgapi/mods/wireless
