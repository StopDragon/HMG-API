#!/bin/sh
# Host test of the Wireless mod's fitted CarPlay turn-by-turn (dipo/CpTurnsFit, 설정 tbt_fit 켬): 2.4.1의 cpturns 시험을
# 되살린 것(1.7.4 모드 2.4.3)에 D값 시험을 더했다. 순정 층(CpTurns)은 tools/cpturns가 본다.
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
D=../../src/hmgapi/mods/wireless/dipo
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$CP" $(find stubs test ../aaturnsfit/stubs -name '*.java') \
    ../../stubs/com/tcc/iap2/Iap2NotiMsg.java $D/CpTbt.java $D/CpTurns.java $D/CpTurnsFit.java $D/PhoneBarOut.java ../../src/hmgapi/mods/wireless/PhoneBar.java
java -Dfile.encoding=UTF-8 -cp "$CP" hmgapi.mods.wireless.dipo.CpTurnsTest
