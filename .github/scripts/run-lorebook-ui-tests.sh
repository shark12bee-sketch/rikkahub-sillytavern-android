#!/usr/bin/env bash
# Use one emulator and run each explicit class separately. Preserve every class report.
set -u
result=0
if [ -n "${UI_TEST_CLASS:-}" ]; then
  IFS=',' read -r -a filters <<< "$UI_TEST_CLASS"
else
  filters=("")
fi
index=0
for selected in "${filters[@]}"; do
  args=()
  if [ -n "$selected" ]; then
    args+=("-Pandroid.testInstrumentationRunnerArguments.class=$selected")
  elif [ "${SELECTION_REGRESSION:-}" = true ]; then
    args+=("-Pandroid.testInstrumentationRunnerArguments.annotation=me.rerere.rikkahub.ui.pages.extensions.LorebookSelectionIntegration")
  fi
  ./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
    -Pandroid.testInstrumentationRunnerArguments.package=me.rerere.rikkahub.ui.pages.extensions "${args[@]}" -x :web:buildWebUi --stacktrace || result=1
  if [ -n "${UI_TEST_CLASS:-}" ]; then
    destination="focused-ui-results/$index"
    mkdir -p "$destination"
    if [ -d app/build/outputs/androidTest-results ]; then
      mv app/build/outputs/androidTest-results "$destination/results"
    else
      result=1
    fi
    if [ -d app/build/reports/androidTests ]; then
      mv app/build/reports/androidTests "$destination/reports"
    fi
  fi
  index=$((index + 1))
done
for folder in round5-layout round6-layout worldbook-editor-round7 worldbook-editor-round8; do
  remote="/sdcard/Android/data/me.rerere.rikkahub.huadeng.debug/files/$folder"
  if adb shell test -d "$remote"; then
    adb pull "$remote" "$folder" || result=1
  fi
done
exit "$result"
