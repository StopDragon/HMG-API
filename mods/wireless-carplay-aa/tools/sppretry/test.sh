#!/bin/sh
# Korea Projection(korea-build) 1.7.2: 브리지 SPP 연결 재시도(dipo/SppRetry)의 PC 시험.
# 횟수, 간격, 멈추는 조건(SPP 열림, 세션·USB, 블루투스 끊김, 새 연결 이벤트), 끝나는지.
# 1.7.2(P-7): 브리지가 순정 AP 켜기를 건너뛸지(dipo/ApCheck)도 함께 본다.
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -d "$CP" $(find test -name '*.java') ../../src/hmgapi/mods/wireless/dipo/SppRetry.java ../../src/hmgapi/mods/wireless/dipo/Mac.java ../../src/hmgapi/mods/wireless/dipo/ApCheck.java
java -cp "$CP" hmgapi.mods.wireless.dipo.SppRetryTest
java -cp "$CP" hmgapi.mods.wireless.dipo.ApCheckTest
