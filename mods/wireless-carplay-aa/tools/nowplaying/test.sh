#!/bin/sh
# Host test of the Wireless mod's song info on wireless CarPlay (dipo/NowPlaying): when
# NowPlaying starts, once per CarPlay-channel session, its retries and stale tries.
set -e
cd "$(dirname "$0")"
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -d "$CP" $(find test -name '*.java') ../../src/hmgapi/mods/wireless/dipo/NowPlaying.java
java -cp "$CP" hmgapi.mods.wireless.dipo.NowPlayingTest
