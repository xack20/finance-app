#!/usr/bin/env bash
# Build + stably sign the Hisaab companion.
#
# Why: Compose Desktop's createDistributable ad-hoc-signs the .app, and an ad-hoc signature changes
# every rebuild — so macOS forgets the Full Disk Access grant (TCC keys ad-hoc apps on the per-build
# code hash). Re-signing with your Apple Development cert gives the app a STABLE Designated Requirement,
# so the FDA grant you make once survives every rebuild. Reading ~/Library/Messages/chat.db needs FDA.
#
# Usage: macHelper/build-and-sign.sh   (run from anywhere)
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.10-amzn}"

./gradlew :macHelper:createDistributable

APP="macHelper/build/compose/binaries/main/app/HisaabCompanion.app"
ID=$(security find-identity -v -p codesigning | awk '/Apple Development/{print $2; exit}')
if [ -z "${ID:-}" ]; then
  echo "No 'Apple Development' codesigning identity found — the app stays ad-hoc signed and you'll" >&2
  echo "have to re-grant Full Disk Access after each rebuild." >&2
  exit 1
fi

codesign --force --deep --sign "$ID" "$APP"
codesign --verify --verbose "$APP"
echo
echo "Signed + ready: $APP"
echo "If this is the first signed build, re-grant Full Disk Access to it once (remove any stale entry first)."
