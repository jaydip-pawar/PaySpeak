#!/usr/bin/env zsh
ADB=~/Library/Android/sdk/platform-tools/adb

echo ""
echo "Connected devices:"
echo "──────────────────────────────────────"

DEVICES=("${(@f)$($ADB devices | tail -n +2 | grep -v '^$' | awk '{print $1}')}")

if [[ ${#DEVICES[@]} -eq 0 ]]; then
  echo "No devices connected. Connect a device or start an emulator first."
  exit 1
fi

for i in $(seq 1 ${#DEVICES[@]}); do
  echo "  $i  ${DEVICES[$i]}"
done

echo "──────────────────────────────────────"
echo ""
printf "Enter number: "
read NUM

DEVICE=${DEVICES[$NUM]}

if [[ -z "$DEVICE" ]]; then
  echo "Invalid selection, aborting."
  exit 1
fi

echo ""
echo "→ Deploying to $DEVICE..."
cd "$(dirname "$0")/.." || exit 1
./gradlew installDebug && $ADB -s "$DEVICE" shell am start -n com.pp.payspeak/.ui.MainActivity
