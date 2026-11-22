# Shared by the scenarios: finger strokes, screen captures and pixel checks (1920x1200 landscape).
pkg=org.odclcslkrpb5k.drawing
width=1920 height=1200

# The input commands for one finger stroke from (x1,y1) to (x2,y2), for a single adb shell.
stroke() {
  local cmd="input motionevent DOWN $1 $2" i
  for i in $(seq 1 20); do
    cmd+="; input motionevent MOVE $(($1 + ($3 - $1) * i / 20)) $(($2 + ($4 - $2) * i / 20))"
  done
  printf '%s; input motionevent UP %s %s' "$cmd" "$3" "$4"
}

# Any input makes the last capture stale; pixel checks then refuse to read it until the next
# capture (a check after redo once read the screenshot taken before it, and could not fail).
stale=0
adb() {
  case "$*" in *input*) stale=1 ;; esac
  command adb "$@"
}

tap() { adb shell input tap "$1" "$2"; }

# Captures the screen as raw RGBA into $OUT_DIR/$1.raw, and as $1.png to look at.
capture() {
  adb exec-out screencap -p >"$OUT_DIR/$1.png"
  adb exec-out screencap >"$OUT_DIR/$1.raw"
  raw=$OUT_DIR/$1.raw
  stale=0
  header=$(($(stat -c %s "$raw") - width * height * 4))
  [ "$header" -ge 12 ] && [ "$header" -le 16 ] || { echo "scenario: unexpected capture size"; exit 1; }
}

# Stops the scenario when the screen has had input since the last capture. Called by every check
# (not by pixel, which runs in a subshell where exit would not stop the scenario).
fresh() { [ "$stale" -eq 0 ] || { echo "scenario: a pixel check after input, before a new capture"; exit 1; }; }

pixel() {
  dd if="$raw" bs=1 skip=$((header + ($2 * width + $1) * 4)) count=3 status=none | od -An -tu1
}

# True when the pixel at (x,y) is not near-white paper.
inked() { fresh; local r g b; read -r r g b <<<"$(pixel "$1" "$2")"; [ "$r" -lt 200 ] || [ "$g" -lt 200 ] || [ "$b" -lt 200 ]; }

# Fails the scenario on a crash (Java or native), an ANR, a StrictMode violation in the app's
# process, Ink refusing input, or any failure the app logs; each failing check prints what it found.
# The log is cleared before each scenario (see all), so only this scenario's lines count.
no_errors() {
  local log=$OUT_DIR/logcat.txt failed=0 pid found errors allowed
  adb logcat -d >"$log"
  # Injected input is stamped up to a few ms ahead of the clock Ink renders by, which Ink reports
  # and skips for that frame; real touches carry past timestamps. Only that, under 100 ms, is
  # allowed (the device checklist confirms it never appears with fingers): each Ink error line is
  # paired with the next line of the same thread, which must be that message.
  found=$(awk -v ok='current_elapsed_time` must be non-negative. Got -[0-9]{1,2}\\.[0-9]+ms' '
    /InkInProgressShape: Error/ { waiting[$4] = $0; next }
    ($4 in waiting) { if ($0 !~ ok) print waiting[$4] "\n" $0; delete waiting[$4] }
    END { for (t in waiting) print waiting[t] " (no message line)" }' "$log")
  [ -z "$found" ] || { echo "scenario: Ink refused input:"; echo "$found"; failed=1; }
  # Any warning or worse under one of the app's own log tags, read from its source, fails.
  local tags
  tags=$(grep -rhoE 'const val TAG = "[A-Za-z]+"' "$(dirname "${BASH_SOURCE[0]}")/../../app/src/main" |
    sed -E 's/.*"(.*)"/\1/' | sort -u | paste -sd'|')
  [ -n "$tags" ] || { echo "scenario: no app log tags found in the source"; exit 1; }
  found=$(grep -E " [WEF] ($tags)( *):" "$log" || true)
  [ -z "$found" ] || { echo "scenario: the app logged a failure:"; echo "$found"; failed=1; }
  found=$(grep -E "Process: $pkg|>>> $pkg <<<|Fatal signal|ANR in $pkg" "$log" || true)
  [ -z "$found" ] || { echo "scenario: the app crashed or stopped responding:"; echo "$found"; failed=1; }
  # StrictMode lines by process id (column 3); other processes on the image log their own. In debug
  # builds a violation also kills the app, which the crash check above sees.
  pid=$(adb shell pidof $pkg | tr -d '\r' || true)
  if [ -n "$pid" ]; then
    found=$(awk -v p="$pid" '$3 == p && /StrictMode policy violation/' "$log")
    [ -z "$found" ] || { echo "scenario: StrictMode violation in the app:"; echo "$found"; failed=1; }
  fi
  [ "$failed" -eq 0 ] || exit 1
}

# True when any pixel in column x between rows y1 and y2 is inked (a thin line crosses it).
inked_column() {
  local y
  for ((y = $2; y <= $3; y += 2)); do inked "$1" "$y" && return 0; done
  return 1
}

# Positions on screen (1920x1200, density 240): home's first two tiles, and the drawing screen's
# home button (top of the left rail).
# Home: "new page", "coloring pages", then the saved pages, newest first. The tiles are 456 px
# apart; in the coloring picker the first tile goes back and the second is the first page.
NEW_TILE="246 167"
COLORING_TILE="702 167"
SAVED_TILE="1158 167"
FIRST_COLORING="702 167"
FILL_BUTTON="84 594"
# The brush button, under home, and its tray's slots in one row beside it.
BRUSH_BUTTON="84 270"
THICK_BRUSH="606 270"
HIGHLIGHTER="774 270"
ERASER="942 270"
RED_SWATCH="1850 64"
YELLOW_SWATCH="1850 260"
HOME_BUTTON="84 102"
# The drawing screen's left rail, top to bottom after a spacer: stamps, undo, redo; and the stamp
# tray's first face (happy), beside the rail.
STAMPS_BUTTON="84 762"
UNDO_BUTTON="84 930"
REDO_BUTTON="84 1098"
HAPPY_FACE="270 348"
SAD_FACE="438 348"

# A one-finger drag from (x1,y1) to (x2,y2), as one adb shell command.
drag() { stroke "$@"; }

# Waits, up to 20 s, until a check on a fresh capture passes: what = what is awaited, then the check.
# Screens fill in from the disk thread after they first draw, so a fixed sleep is too short on a
# loaded machine (load 18 on 8 cores dropped a first stroke, and a first tap hit an empty home).
wait_for() {
  local what=$1 i
  shift
  for i in $(seq 1 40); do
    capture waiting
    "$@" && return 0
    sleep 0.5
  done
  echo "scenario: $what did not appear within 20 s"
  exit 1
}

# Home, its tiles laid out: the "new page" tile's purple plus.
home_open() { wait_for "home" colored 246 167 91 63 168; }

# A page, open and taking touches: its color rail's top (red) swatch.
page_open() { wait_for "a page" colored 1850 64 229 57 53; }

# The coloring pages, previews loaded (the tiles appear only then): its second row of tiles, which
# home, with at most two saved pages here, does not have.
picker_open() { wait_for "the coloring pages" colored 246 466 255 255 255; }

# True when the pixel at (x, y) is within 16 of the color r g b in every channel.
colored() {
  fresh
  local r g b
  read -r r g b <<<"$(pixel "$1" "$2")"
  [ $((r > $3 ? r - $3 : $3 - r)) -le 16 ] && [ $((g > $4 ? g - $4 : $4 - g)) -le 16 ] &&
    [ $((b > $5 ? b - $5 : $5 - b)) -le 16 ]
}
