#!/bin/sh
# PC 시험: 무선 카플레이 길안내 다시 요청(dipo/RgAsk, 1.8.0 모드 2.4.7). 세션마다 한 번 시작, 요청 횟수·간격 한도,
# 길안내가 오면 멈춤, 세션 끝·DiPO 멈춤으로 무효, 묵은 예약은 아무것도 안 함
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -d "$CP" $(find test -name '*.java') ../../src/hmgapi/mods/wireless/dipo/RgAsk.java
java -cp "$CP" hmgapi.mods.wireless.dipo.RgAskTest
