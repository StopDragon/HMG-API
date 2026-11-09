#!/bin/sh
# AA 라이브러리 레시피(aa-tbt-*) 세그먼트의 UTF-8 자르기 함수(0x17a, 32바이트) 호스트 시험.
# 레시피의 실제 바이트를 작은 Thumb 해석기로 돌려, 이름을 글자 경계에서 자르는지 본다(1.7.2 M-15).
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -encoding UTF-8 -d "$CP" $(find test -name '*.java')
java -cp "$CP" AaTrimTest ../../recipes/aa-tbt-48ee793a.recipe ../../recipes/aa-tbt-a0bea1c4.recipe
