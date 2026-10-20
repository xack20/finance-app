#!/usr/bin/env bash
# Build the debug APK with the next build number and upload it to the testing Google Drive folder as
# Hisaab-b<N>-<sha>[-dirty].apk, then delete older Hisaab-*.apk there so the folder holds only the newest.
# Unique names keep phones from saving "Hisaab (1).apk"; N is also the versionCode, so each build installs
# as an update (Android refuses a lower versionCode).
#
# One-time setup (rclone opens a browser for the Google login):
#   brew install rclone
#   rclone config create hisaab-drive drive scope=drive root_folder_id=<drive folder id>
#
# Usage: composeApp/publish-apk.sh   (run from anywhere; HISAAB_APK_REMOTE overrides the remote)
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/21.0.10-amzn}"
REMOTE="${HISAAB_APK_REMOTE:-hisaab-drive:}"
APK="composeApp/build/outputs/apk/debug/composeApp-debug.apk"
# Outside the repo so a fresh clone or git clean can't reset it below what's installed on the phone.
COUNTER="$HOME/.hisaab/apk-build-number"

BUILD=$(( $(cat "$COUNTER" 2>/dev/null || echo 1) + 1 ))
NAME="Hisaab-b${BUILD}-$(git rev-parse --short HEAD)$(git diff --quiet HEAD || echo -dirty).apk"

./gradlew :composeApp:assembleDebug -Phisaab.buildNumber="$BUILD"
rclone copyto "$APK" "${REMOTE}${NAME}" --progress
mkdir -p "$(dirname "$COUNTER")"
echo "$BUILD" > "$COUNTER" # only after a successful upload, so a failed run reuses the number

{ rclone lsf "$REMOTE" --include 'Hisaab-*.apk' | grep -vxF "$NAME" || true; } | while read -r old; do
  rclone deletefile "${REMOTE}${old}"
done
echo "Uploaded ${NAME} (versionCode ${BUILD})"
