#!/usr/bin/env bash
set -euo pipefail
index=0
for selector in worldbook custom-thought; do
  if [ "$selector" = worldbook ]; then
    filter='-Pandroid.testInstrumentationRunnerArguments.package=me.rerere.rikkahub.ui.pages.extensions'
  else
    filter='-Pandroid.testInstrumentationRunnerArguments.class=me.rerere.rikkahub.ui.components.message.CustomThoughtUiTest'
  fi
  ./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "$filter" -x :web:buildWebUi --stacktrace
  destination="combined-ui-results/$selector"
  mkdir -p "$destination"
  mv app/build/outputs/androidTest-results "$destination/results"
  mv app/build/reports/androidTests "$destination/reports"
done
for folder in round5-layout round6-layout worldbook-editor-round7 worldbook-editor-round8; do
  remote="/sdcard/Android/data/me.rerere.rikkahub.huadeng.debug/files/$folder"
  if adb shell test -d "$remote"; then adb pull "$remote" "$folder"; fi
done
