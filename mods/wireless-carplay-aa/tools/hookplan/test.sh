#!/bin/sh
# Host check (Korea Projection, korea-build): does this mod's hooks load on a car that lacks some hook points?
# HMG-API's HookPlan refuses all of a mod's hooks when one declared point isn't mapped on the car.
# The points below are the ones the Kia Sportage NQ5 (NQ5.KOR 260604) Car Report couldn't find (#46).
set -e
cd "$(dirname "$0")"
ROOT=../../../..
CP=$(mktemp -d); trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -d "$CP" $ROOT/core/runtime/java/hmgapi/engine/*.java HookPlanCheck.java 2>/dev/null
out=$(java -cp "$CP" HookPlanCheck mod.json ../../mod.json)
echo "$out"
case "$out" in *"hooks load"*) echo "ok    hooks load without btsettings.projection_helper.* (NQ5 260604)" ;; *) echo "FAIL  hooks refused"; exit 1 ;; esac
