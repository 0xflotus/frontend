#!/bin/bash
set -e

CHROME_APP="Google Chrome Canary.app"

if [ -a "$HOME/Applications/$CHROME_APP" ]; then
  CHROME_PATH="$HOME/Applications"
elif [ -a "/Applications/$CHROME_APP" ]; then
  CHROME_PATH="/Applications"
else
  echo "Error - $CHROME_APP was not found in /Applications or ~/Applications"
  false
fi

echo "Using '$CHROME_APP' from $CHROME_PATH"

"$CHROME_PATH/$CHROME_APP/Contents/MacOS/Google Chrome Canary" \
  --remote-debugging-port=9222 \
  --no-first-run \
  https://prod.circlehost:4443 &

lein with-profile +devtools repl
