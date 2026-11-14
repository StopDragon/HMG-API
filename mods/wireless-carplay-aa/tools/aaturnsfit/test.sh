#!/bin/sh
# Host test of the Wireless mod's fitted Android Auto turn-by-turn (AaTurnsFit, 설정 tbt_fit 켬): 2.4.1의 aaturns 시험을
# 되살린 것(1.7.4 모드 2.4.3)에 재탐색(T-6 ①)·D값 시험을 더했다. 순정 층(AaTurns)은 tools/aaturns가 본다.
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
W=../../src/hmgapi/mods/wireless
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$CP" $(find stubs test -name '*.java') $W/AaTbt.java $W/AaTurns.java $W/AaTurnsFit.java \
    $W/StockLog.java $W/StockReplay.java $W/Mac.java $W/PhoneBar.java $W/TrapArgs.java
java -Dfile.encoding=UTF-8 -cp "$CP" hmgapi.mods.wireless.AaTurnsTest
