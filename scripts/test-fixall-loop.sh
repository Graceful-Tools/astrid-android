#!/bin/bash
#
# test-fixall-loop.sh — the regression test for scripts/fixall-loop.sh.
#
# Adapted from astrid-ios/scripts/test-fixall-loop.sh. The loop is run FOR REAL, but
# from a copy inside a throwaway sandbox: REPO comes from the script's own path, so
# $SANDBOX/astrid-android/scripts/fixall-loop.sh guards $SANDBOX/astrid-android, finds
# its siblings at $SANDBOX/astrid-web and $SANDBOX/astrid-core, and pushes to bare
# origins in the sandbox. Board scripts are a stub `tsx` that logs every argv; `claude`,
# `caffeinate` and `sysctl` are stubs too. Nothing here touches the network, the board,
# the real working-tree lock, launchd, or this checkout.
#
# macOS bash 3.2: empty arrays under `set -u` need ${a[@]+"${a[@]}"} — there are none here.
set -u
cd "$(dirname "$0")/.."

LOOP_SRC="$PWD/scripts/fixall-loop.sh"
[ -x "$LOOP_SRC" ] || { echo "✗ $LOOP_SRC is missing or not executable"; exit 1; }
command -v zsh >/dev/null 2>&1 || { echo "✗ zsh is required (the loop is a zsh script)"; exit 1; }

# pwd -P: the loop resolves its own path (${0:A}), so /var/… must already read /private/var/….
TMP=$(cd "$(mktemp -d "${TMPDIR:-/tmp}/test-fixall-android.XXXXXX")" && pwd -P); trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin"
export CALLS="$TMP/calls.log"

# ── Stubs ─────────────────────────────────────────────────────────────────────
cat > "$TMP/bin/tsx" <<'STUB'
#!/bin/bash
echo "tsx $* [cwd=$(basename "$PWD")]" >> "$CALLS"
case "$*" in
  *fixall-session.ts*acquire*) exit "${STUB_LOCK_EXIT:-0}" ;;
  *fixall-session.ts*)         exit 0 ;;
  *fixall-pause.ts*yield*)
    [ "${STUB_YIELD_EXIT:-0}" = "2" ] && echo "claude-code (pid 999) is working /w/web — this tick stands down"
    [ "${STUB_YIELD_EXIT:-0}" = "1" ] && echo "boom"
    exit "${STUB_YIELD_EXIT:-0}" ;;
  *ready-tasks.ts*)
    echo "  (sweep chatter on stdout)"
    case "${STUB_QUEUE:-two}" in
      two)   echo '{"version":1,"tasks":[{"id":"11111111-1111-1111-1111-111111111111","action":"ready"},{"id":"22222222-2222-2222-2222-222222222222","action":"ready"}]}' ;;
      empty) echo '{"version":1,"tasks":[]}' ;;
      fail)  exit 1 ;;
    esac
    exit 0 ;;
  *add-task-comment.ts*)       exit 0 ;;
  *release-stuck-doing.ts*)    exit 0 ;;
  *open-fixall-prs.ts*)        exit 0 ;;
esac
exit 0
STUB

cat > "$TMP/bin/claude" <<'STUB'
#!/bin/bash
echo "claude $* MAX_TASKS=${ASTRID_FIXALL_MAX_TASKS:-} LOCK_HELD=${ASTRID_FIXALL_LOCK_HELD:-} CLAIMS=${ASTRID_FIXALL_CLAIMS_FILE:-} TOOLCHAIN=${STUB_TOOLCHAIN_MARK:-unset}" >> "$CALLS"
echo "$$" > "$CLAUDE_PID_FILE"
[ -n "${ASTRID_FIXALL_CLAIMS_FILE:-}" ] && echo "33333333-3333-3333-3333-333333333333" >> "$ASTRID_FIXALL_CLAIMS_FILE"
case "${STUB_CLAUDE_DOES:-}" in
  mess)         echo "half-finished" > leftover.txt ;;
  killed)       git checkout -q -b fix/aatd-9-half-done; echo half > half.txt ;;
  branch)       git checkout -q -b fix/aatd-10-done; echo done > done.txt; git add done.txt; git commit -q -m "fix: done" ;;
  core-mess)    echo "core edit" > "$CORE_DIR/core-edit.txt" ;;
  says-failed)  echo "RESULT: FAILED — the astrid MCP server timed out" ;;
  recovers)     echo "RESULT: FAILED — first attempt"; echo "RESULT: OK — 1 task" ;;
esac
sleep "${STUB_CLAUDE_SLEEP:-0}"
exit "${STUB_CLAUDE_EXIT:-0}"
STUB

cat > "$TMP/bin/caffeinate" <<'STUB'
#!/bin/bash
echo "caffeinate $*" >> "$CALLS"
STUB

# sysctl answers the two reads the load guard makes, from the test's chosen numbers.
cat > "$TMP/bin/sysctl" <<'STUB'
#!/bin/bash
case "$*" in
  *vm.loadavg*) [ -n "${STUB_LOAD:-}" ] && echo "{ 1.00 ${STUB_LOAD} 1.00 }" ;;
  *hw.ncpu*)    echo "${STUB_NCPU:-8}" ;;
esac
STUB

# The toolchain env: proves the loop sources it before the run.
cat > "$TMP/android-env.sh" <<'STUB'
export STUB_TOOLCHAIN_MARK=sourced
export JAVA_HOME=/stub/jdk
export ANDROID_HOME=/stub/sdk
STUB
chmod +x "$TMP"/bin/*
export CLAUDE_PID_FILE="$TMP/claude.pid"

# ── The sandbox: android checkout, web sibling, core sibling, bare origins ───
SANDBOX="$TMP/sandbox"
REPO="$SANDBOX/astrid-android"
CORE_DIR="$SANDBOX/astrid-core"
export CORE_DIR
mkdir -p "$REPO/scripts" "$SANDBOX/astrid-web" "$CORE_DIR"
cp "$LOOP_SRC" "$REPO/scripts/fixall-loop.sh"
echo "core" > "$CORE_DIR/README.md"
init_repo() {  # init_repo <dir> <bare origin>
  ( cd "$1" && git init -q -b main . && git config user.email t@example.com && git config user.name Test \
    && git add -A && git commit -q --allow-empty -m init \
    && git init -q --bare -b main "$2" && git remote add origin "$2" && git push -q -u origin main ) >/dev/null 2>&1
}
init_repo "$REPO" "$SANDBOX/origin.git"
init_repo "$CORE_DIR" "$SANDBOX/core-origin.git"
LOOP="$REPO/scripts/fixall-loop.sh"
sgit() { git -C "$REPO" "$@"; }
cgit() { git -C "$CORE_DIR" "$@"; }

STALL_STATE="$TMP/stall.state"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); }
bad() { FAIL=$((FAIL+1)); echo "✗ $1"; [ -n "${2:-}" ] && echo "$2" | sed 's/^/    /'; return 0; }

# Output goes to a FILE, never $(…): the watchdog's orphaned sleep would hold a pipe open.
run() {  # run   (env vars set by the caller select the stub behaviour)
  : > "$CALLS"; : > "$CLAUDE_PID_FILE"
  FIXALL_TSX="$TMP/bin/tsx" \
  CLAUDE_BIN="$TMP/bin/claude" \
  FIXALL_CAFFEINATE="${FIXALL_CAFFEINATE-$TMP/bin/caffeinate}" \
  FIXALL_SYSCTL="$TMP/bin/sysctl" \
  STUB_LOAD="${STUB_LOAD-1.50}" \
  FIXALL_ANDROID_ENV="${FIXALL_ANDROID_ENV-$TMP/android-env.sh}" \
  FIXALL_STALL_STATE="$STALL_STATE" \
  FIXALL_STALL_ALERT_AFTER=3 \
  FIXALL_MAX_MINUTES=1 \
  FIXALL_MAX_USD= \
  "$LOOP" > "$TMP/out.txt" 2>&1
  STATUS=$?
  OUT=$(cat "$TMP/out.txt")
}
last_is()   { if echo "$OUT" | tail -1 | grep -q "^$1"; then ok; else bad "$2" "$OUT"; fi; }
out_has()   { if echo "$OUT" | grep -qF -- "$1"; then ok; else bad "$2" "$OUT"; fi; }
called()    { if grep -qF -- "$1" "$CALLS"; then ok; else bad "$2" "$(cat "$CALLS")"; fi; }
not_called(){ if grep -qF -- "$1" "$CALLS"; then bad "$2" "$(cat "$CALLS")"; else ok; fi; }
line_of()   { grep -nF -- "$1" "$CALLS" | head -1 | cut -d: -f1; }
clean()     { ( cd "$REPO" && git checkout -q main 2>/dev/null; git clean -qfd && git checkout -q -- . ) ; ( cd "$CORE_DIR" && git clean -qfd && git checkout -q -- . ); rm -f "$STALL_STATE"; }

# ── A finished run ──────────────────────────────────────────────────────────
clean; run
last_is "RESULT: OK" "a finished run ends on RESULT: OK, last"
[ "$STATUS" = 0 ] && ok || bad "a finished run exits 0 (got $STATUS)" "$OUT"
called "fixall-session.ts acquire --pid" "the loop takes the working-tree lock"
called "--scheduled" "…as --scheduled, so the loops cannot pause each other"
if grep "fixall-session.ts acquire" "$CALLS" | grep -q "cwd=astrid-android"; then ok
else bad "…from THIS checkout (the lock keys on the cwd's git dir)" "$(cat "$CALLS")"; fi
called "fixall-pause.ts yield --self-pid" "the loop asks whether to stand down, about OTHER sessions"
called "ready-tasks.ts android --json --harness claude-code" "the queue is read by board NAME (ready-tasks.ts android)"
if grep "ready-tasks.ts" "$CALLS" | grep -q "cwd=astrid-web"; then ok
else bad "…from astrid-web, whose .env.local the scripts read" "$(cat "$CALLS")"; fi
called "claude -p /fixall" "the run is claude -p /fixall"
called "--add-dir $SANDBOX/astrid-web" "the session may reach astrid-web (board scripts)"
called "--add-dir $CORE_DIR" "…and astrid-core"
called "MAX_TASKS=1" "one task per scheduled run"
called "LOCK_HELD=1" "the session is told the launcher holds the lock"
called "TOOLCHAIN=sourced" "the Android toolchain env is sourced before the run"
out_has "toolchain: JAVA_HOME=/stub/jdk" "…and the log says which toolchain"
out_has "load ok — 5-minute average 1.50 against a budget of 32" "the load guard reports what it saw"
if grep -qE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' "$LOOP_SRC"; then
  bad "no board id in the loop — it is resolved by name" "$(grep -nE '[0-9a-f]{8}-[0-9a-f]{4}-' "$LOOP_SRC")"
else ok; fi
not_called "--list " "nothing is addressed by list id"

# ── Doing is not a dead end; PRs; claims ────────────────────────────────────
STALE=$(line_of "release-stuck-doing.ts --agent claude --board android --stale-minutes 180")
QUEUE=$(line_of "ready-tasks.ts android")
CLAUDE_L=$(line_of "claude -p")
PRS=$(line_of "open-fixall-prs.ts --claims-file")
CLAIMS=$(line_of "release-stuck-doing.ts --agent claude --claims-file")
if [ -n "$STALE" ] && [ -n "$QUEUE" ] && [ "$STALE" -lt "$QUEUE" ]; then ok
else bad "abandoned Doing claims are released at tick start, by board name, before the queue" "$(cat "$CALLS")"; fi
if [ -n "$PRS" ] && [ -n "$CLAIMS" ] && [ "$CLAUDE_L" -lt "$PRS" ] && [ "$PRS" -lt "$CLAIMS" ]; then ok
else bad "after the run: open PRs, then release the run's own claims" "$(cat "$CALLS")"; fi
if grep "open-fixall-prs.ts" "$CALLS" | grep -q -- "--repo $REPO "; then ok
else bad "PRs are opened against THIS repo" "$(cat "$CALLS")"; fi
called "CLAIMS=/" "the session gets a claims file"

# ── Held awake for exactly the run ──────────────────────────────────────────
clean; STUB_CLAUDE_SLEEP=0.3 run
CAFF=$(grep '^caffeinate ' "$CALLS" | head -1 | sed -n 's/.*-w \([0-9][0-9]*\).*/\1/p')
if [ -n "$CAFF" ] && [ "$CAFF" = "$(cat "$CLAUDE_PID_FILE")" ]; then ok
else bad "caffeinate -w names the run's own pid" "$(cat "$CALLS")"; fi
if grep '^caffeinate ' "$CALLS" | grep -q -- ' -d'; then bad "no -d on caffeinate" "$(cat "$CALLS")"; else ok; fi

# ── Quiet ticks ─────────────────────────────────────────────────────────────
clean; STUB_QUEUE=empty run
last_is "RESULT: SKIPPED — nothing to do" "an empty queue is a skip"
not_called "claude -p" "…that starts no session"
out_has "QUEUE: 0 item(s)" "…and says what it read"

clean; STUB_QUEUE=fail run
out_has "QUEUE: unknown" "a queue nobody could read is reported"
called "claude -p" "…and is NOT read as empty: the run happens"

clean; FIXALL_DRY_RUN=1 run
last_is "RESULT: SKIPPED — dry run" "a dry run passes every guard and stops"
not_called "claude -p" "…without starting claude"
out_has "would run:" "…and says what it would have run"

# ── The toolchain ───────────────────────────────────────────────────────────
clean; FIXALL_ANDROID_ENV="$TMP/no-such-env.sh" run
last_is "RESULT: FAILED — no Android toolchain env" "a missing toolchain env fails the tick"
not_called "claude -p" "…before spending a session on it"

# ── No core checkout ────────────────────────────────────────────────────────
clean; mv "$CORE_DIR" "$TMP/core-away"; run; mv "$TMP/core-away" "$CORE_DIR"
out_has "no astrid-core checkout" "a missing core is warned about"
not_called "--add-dir $CORE_DIR" "…and not passed as an allowed directory"
last_is "RESULT: OK" "…and the run still happens (degraded, not failed)"

# ── Stand down while someone works this Mac ─────────────────────────────────
clean; STUB_YIELD_EXIT=2 run
last_is "RESULT: SKIPPED" "a tick stands down while someone is working"
out_has "is working /w/web" "…and the log says who"
not_called "claude -p" "…without starting a session"
clean; STUB_YIELD_EXIT=1 run
out_has "could not read the pause state" "an unreadable pause state is reported"
called "claude -p" "…and the run still happens"

# ── The load guard ──────────────────────────────────────────────────────────
clean; STUB_LOAD=40.20 run
last_is "RESULT: SKIPPED — machine is buried" "a buried machine skips the tick"
not_called "claude -p" "…without starting a session"
clean; STUB_LOAD= run
out_has "could not read the load" "an unreadable load is said out loud"
called "claude -p" "…and does not stall the loop"

# ── Lock held ───────────────────────────────────────────────────────────────
clean; STUB_LOCK_EXIT=2 run
last_is "RESULT: SKIPPED — another live session holds this checkout" "a held lock is a skip"
not_called "fixall-pause.ts" "…decided before anything else"

# ── The stall alarm: once per stall, on the work waiting behind it ──────────
clean
echo wip > "$REPO/work-in-progress.txt"
run; not_called "add-task-comment.ts" "the first dirty-tree skip stays quiet"
last_is "RESULT: SKIPPED — working tree is dirty" "a dirty tree is a skip"
run; not_called "add-task-comment.ts" "the second is still quiet"
run
called "add-task-comment.ts 11111111-1111-1111-1111-111111111111" "the third skip in a row says so on the top Ready task"
called "work-in-progress.txt" "…naming what is in the way"
run; not_called "add-task-comment.ts" "a fourth does not post again — once per stall"
rm -f "$REPO/work-in-progress.txt"
run; not_called "add-task-comment.ts" "a tick that gets past the guards posts nothing"
echo wip > "$REPO/work-in-progress.txt"
run; not_called "add-task-comment.ts" "…and the count starts again from one"
rm -f "$REPO/work-in-progress.txt"

clean; export STUB_QUEUE=empty
echo wip > "$REPO/work-in-progress.txt"; run; run; run; unset STUB_QUEUE
not_called "add-task-comment.ts" "a stall with nothing Ready behind it is only logged"
out_has "nothing Ready is waiting behind it" "…and the log says so"
rm -f "$REPO/work-in-progress.txt"

clean; STUB_LOCK_EXIT=2 run; STUB_LOCK_EXIT=2 run; STUB_LOCK_EXIT=2 run
called "add-task-comment.ts" "a lock nobody releases is a stall too (AWTD-1207)"

clean; STUB_YIELD_EXIT=2 run; STUB_YIELD_EXIT=2 run; STUB_YIELD_EXIT=2 run
called "is working /w/web" "three stood-down ticks say so, with the reason"

# ── Branch guards ───────────────────────────────────────────────────────────
clean; sgit checkout -q -b some-fix; echo x > "$REPO/x.txt"; sgit add x.txt; sgit commit -q -m x
run
last_is "RESULT: SKIPPED — HEAD is on some-fix, not main" "unmerged work off main is left alone"
sgit checkout -q main; sgit branch -q -D some-fix

clean; sgit checkout -q -b merged-already
run
if [ "$(sgit rev-parse --abbrev-ref HEAD)" = "main" ] && echo "$OUT" | grep -q "returned to main from merged-already"; then ok
else bad "a clean branch already in origin/main is not a reason to skip: back to main" "$OUT"; fi
sgit branch -q -D merged-already

clean; echo y > "$REPO/y.txt"; sgit add y.txt; sgit commit -q -m "local only"
run
last_is "RESULT: SKIPPED — main has 1 commit(s) origin/main lacks" "unpushed commits on main are work in progress"
sgit reset -q --hard origin/main

# ── The core ────────────────────────────────────────────────────────────────
clean; echo dirty > "$CORE_DIR/core-wip.txt"
run
last_is "RESULT: SKIPPED — astrid-core is dirty" "a dirty astrid-core stands the tick down"
rm -f "$CORE_DIR/core-wip.txt"

clean; cgit checkout -q -b someone-elses-branch
run
last_is "RESULT: OK" "astrid-core on another branch is no reason to skip (a shared clone)"
cgit checkout -q main; cgit branch -q -D someone-elses-branch

clean; STUB_CLAUDE_DOES=core-mess run
last_is "RESULT: FAILED — run left the tree dirty" "a run that leaves astrid-core dirty has not finished"
if [ -f "$CORE_DIR/core-edit.txt" ] && [ "$(cgit rev-parse --abbrev-ref HEAD)" = "main" ]; then ok
else bad "…its core files and HEAD are left exactly where they are" "$(cgit status)"; fi
CORE_WIP=$(git -C "$SANDBOX/core-origin.git" branch --list 'wip/fixall-android-core-*' | tr -d ' *' | head -1)
if [ -n "$CORE_WIP" ] && git -C "$SANDBOX/core-origin.git" show "$CORE_WIP:core-edit.txt" >/dev/null 2>&1; then ok
else bad "…and a copy is pushed to a wip/ branch on the core's origin" "$(git -C "$SANDBOX/core-origin.git" branch)"; fi
called "add-task-comment.ts 33333333-3333-3333-3333-333333333333" "…and the task it claimed hears about it"

# ── Died runs and where their work goes ─────────────────────────────────────
clean; STUB_CLAUDE_EXIT=3 run
last_is "RESULT: FAILED — claude exited 3" "a run that died ends on RESULT: FAILED, last"
[ "$STATUS" != 0 ] && ok || bad "…and exits non-zero" "$OUT"
called "add-task-comment.ts 33333333-3333-3333-3333-333333333333" "…and says so on the task it claimed"

clean; STUB_CLAUDE_DOES=mess run
last_is "RESULT: FAILED — run left the tree dirty" "a run that leaves the tree dirty is not OK (AITD-426)"
if [ -f "$REPO/leftover.txt" ] && [ "$(sgit rev-parse --abbrev-ref HEAD)" = "main" ] && [ "$(sgit log -1 --format=%s)" = "init" ]; then ok
else bad "…changes on main are left in place, nothing committed to main (AWTD-1095)" "$(sgit status; sgit log --oneline -2)"; fi
WIP=$(git -C "$SANDBOX/origin.git" branch --list 'wip/fixall-android-*' | tr -d ' *' | head -1)
if [ -n "$WIP" ] && git -C "$SANDBOX/origin.git" show "$WIP:leftover.txt" >/dev/null 2>&1 \
   && git -C "$SANDBOX/origin.git" log -1 --format=%s "$WIP" | grep -q "UNFINISHED, UNVERIFIED"; then ok
else bad "…and a copy is pushed as an UNFINISHED wip/ branch" "$(git -C "$SANDBOX/origin.git" branch)"; fi
if grep -q "leftover.txt" "$CALLS"; then ok; else bad "…and the claimed task hears what was left" "$(cat "$CALLS")"; fi

clean; STUB_CLAUDE_DOES=killed STUB_CLAUDE_EXIT=143 run
last_is "RESULT: FAILED — killed after" "a watchdog-killed run reports FAILED"
if [ "$(sgit rev-parse --abbrev-ref HEAD)" = "main" ] && [ -z "$(sgit status --porcelain)" ] \
   && git -C "$SANDBOX/origin.git" show fix/aatd-9-half-done:half.txt >/dev/null 2>&1 \
   && git -C "$SANDBOX/origin.git" log -1 --format=%s fix/aatd-9-half-done | grep -q UNFINISHED; then ok
else bad "a killed run's work is committed on its task branch, pushed, and HEAD is back on main" "$OUT"; fi

clean; STUB_CLAUDE_DOES=branch run
if echo "$OUT" | tail -1 | grep -q '^RESULT: OK' && [ "$(sgit rev-parse --abbrev-ref HEAD)" = "main" ] \
   && git -C "$SANDBOX/origin.git" rev-parse -q --verify fix/aatd-10-done >/dev/null; then ok
else bad "a clean task branch is pushed and the loop returns to main" "$OUT"; fi

# ── The run's own verdict (AITD-440) ────────────────────────────────────────
clean; STUB_CLAUDE_DOES=says-failed run
last_is "RESULT: FAILED — run reported: the astrid MCP server timed out" "a run that SAYS it failed is FAILED though it exits 0"
[ "$STATUS" != 0 ] && ok || bad "…and the loop exits non-zero" "$OUT"
clean; STUB_CLAUDE_DOES=recovers run
last_is "RESULT: OK" "only the run's LAST RESULT: line counts"

# ── Runs what is merged (self-update) ───────────────────────────────────────
clean
UP="$TMP/upstream"; git clone -q "$SANDBOX/origin.git" "$UP" 2>/dev/null
git -C "$UP" config user.email t@example.com; git -C "$UP" config user.name Test
push_up() { git -C "$UP" pull -q --ff-only origin main 2>/dev/null; echo "$2" >> "$UP/$1"; git -C "$UP" add -A; git -C "$UP" commit -q -m "up: $1"; git -C "$UP" push -q origin main 2>/dev/null; }
push_up NOTES.md "merged elsewhere"
run
if [ "$(sgit rev-parse HEAD)" = "$(git -C "$UP" rev-parse HEAD)" ] && echo "$OUT" | grep -q "updated main"; then ok
else bad "a tick fast-forwards main to origin/main" "$OUT"; fi
if echo "$OUT" | grep -q "restarting"; then bad "…without restarting when the loop did not change" "$OUT"; else ok; fi
push_up scripts/fixall-loop.sh "# a change to the loop itself"
run
if [ "$(grep -c "restarting on the new version" "$TMP/out.txt")" = 1 ] && echo "$OUT" | tail -1 | grep -q '^RESULT: OK'; then ok
else bad "a changed loop restarts itself once, on the new version, and finishes" "$OUT"; fi

echo ""
if [ "$FAIL" = 0 ]; then echo "✓ fixall-loop (android): $PASS checks passed"; exit 0; fi
echo "✗ fixall-loop (android): $FAIL failed, $PASS passed"; exit 1
