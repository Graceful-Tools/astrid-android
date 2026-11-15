#!/bin/zsh -l
#
# fixall-loop.sh — one scheduled, unattended pass of /fixall on the ANDROID board.
#
# WHY THIS EXISTS. Assigning a task to Claude Agent in Astrid starts nothing: in
# polling mode Astrid calls out to no one, by design (astrid-web
# docs/AGENT_POLLING_MODE.md). Something has to poll. Until 2026-10-20 every
# Android /fixall ran because someone typed it (Jon, 2026-10-20: "Yes android
# should get its own scheduled agent").
#
# Ported from astrid-web/scripts/fixall-loop.sh and astrid-ios's copy of it, with
# the board addressed the way astrid-windows/scripts/fixall-loop.ps1 does it. The
# differences are consequences of where this runs:
#
#   * The task tooling is astrid-web's, in the sibling checkout, run through ITS
#     tsx and ITS .env.local (`cd "$WEB"`). Nothing here re-implements "which
#     tasks are ready": a second copy drifts, and the drift is silent, because a
#     wrong queue looks exactly like a quiet day.
#   * THE BOARD IS PINNED BY ID (AATD-81), overridable with FIXALL_LIST_ID. It
#     used to be resolved by name, but two lists are called "Astrid Android To-do"
#     (an empty duplicate appeared 2026-10-25), and a duplicate name is an error,
#     never a reason to pick the first match (FIXALL_WORKFLOW.md: explicit list ids
#     take precedence). The SAME id goes to every board call — the queue, the
#     stall alarm, both Doing sweeps and the wake acknowledgement — so one tick can
#     never read one board and act on another.
#   * IT WAKES ON REPLIES, not just Ready (AATD-81). Guard 3 is the shared
#     preflight, astrid-web's agent-queue-status.ts: Ready ∪ `attention` (task
#     comments and list chat nobody answered). Until then this loop read only
#     `ready-tasks.ts`, so a question Jon answered on a handed-back task waited
#     for some unrelated Ready task to start a run. What the loop itself has to
#     say still goes on a TASK as a comment: a died run on the task it claimed, a
#     stall on the top Ready task that is waiting behind it.
#   * The Android toolchain is not on launchd's PATH. JDK, SDK and NDK live
#     under ~/dev-tools and ~/Library/Android/sdk (Homebrew is not writable on
#     this Mac), so the loop sources ~/dev-tools/android-env.sh before the run —
#     without it every gradle and cargo-ndk step of predeploy fails.
#   * astrid-core is a sibling checkout the run may need (a rule, a service, the
#     Outbox, sync). It goes on the session's allowed directories, a dirty core
#     stands the tick down, and leftovers there are copied, never moved: it is a
#     plain clone other sessions share, so its HEAD and files are not ours.
#
# IT RUNS ON THE CLAUDE CODE CLI SUBSCRIPTION, NEVER THE ANTHROPIC API. `claude -p`
# is the whole runtime; no API key is read anywhere in this path, and
# --max-budget-usd is a CLI safety bound rather than metered spend.
#
# launchd calls this at :10 and :40 through scripts/run-fixall-loop.mjs (the node
# shim exists for a TCC reason — read the top of that file before "simplifying"
# it). By hand:
#
#   npm run fixall:loop                     # one real pass
#   FIXALL_DRY_RUN=1 npm run fixall:loop    # every guard, then stop before claude
#
# The last line is always exactly one RESULT: line:
#
#   RESULT: OK      — a run happened
#   RESULT: SKIPPED — deliberately did nothing, and why
#   RESULT: FAILED  — tried and could not
#
# A skip is the common, healthy case and exits 0. A skip that never ends is an
# outage, so consecutive skips are counted and the third says so — once.
#
# Environment:
#   FIXALL_MODEL        model for the unattended run (default: opus)
#   FIXALL_MAX_MINUTES  watchdog, kills a wedged run (default: 75)
#   FIXALL_MAX_USD      CLI budget bound for one run (default: 10; empty = none)
#   FIXALL_MAX_TASKS    Ready tasks one run may take (default: 1)
#   FIXALL_PERMISSION_MODE  claude --permission-mode (default: acceptEdits; the
#                       installed plist sets bypassPermissions — see the template)
#   FIXALL_DRY_RUN=1    run every guard, stop before starting claude
#   FIXALL_FORCE=1      skip the tree, core and load guards (testing only)
#   CLAUDE_BIN          the claude CLI (default: ~/.local/bin/claude)
#   FIXALL_WEB          astrid-web checkout (default: ../astrid-web)
#   FIXALL_CORE         astrid-core checkout (default: ../astrid-core; empty = none)
#   FIXALL_TSX          tsx (default: astrid-web's). The test points it at a stub.
#   FIXALL_ANDROID_ENV  toolchain env file (default: ~/dev-tools/android-env.sh;
#                       empty = do not source, as the test does)
#   FIXALL_CAFFEINATE   caffeinate binary (default: caffeinate; empty = none)
#   FIXALL_SYSCTL       sysctl the load guard reads (default: from PATH)
#   FIXALL_MAX_LOAD     skip if the 5-minute load is over this
#                       (default: cores x FIXALL_MAX_LOAD_PER_CPU, which defaults to 4)
#   FIXALL_STALL_STATE  consecutive-skip state (default: beside the log)
#   FIXALL_STALL_ALERT_AFTER  skips in a row before saying so (default: 3)
#   FIXALL_STALE_DOING_MINUTES  idle Doing claim released at tick start (default: 180)
#   FIXALL_LIST_ID      the board (default: the AATD board's id — see the header)
#   FIXALL_SEEN_FILE    which inbox items already woke a run (default: the shared
#                       preflight's own default path, so switching to it re-wakes nothing)
#   FIXALL_PREFLIGHT_DIR  where the preflight artifact goes (default: $TMPDIR)

set -u
export PATH="/opt/homebrew/bin:$HOME/.local/bin:$PATH"

REPO="${0:A:h:h}"
PARENT="${REPO:h}"
WEB="${FIXALL_WEB:-$PARENT/astrid-web}"
CORE="${FIXALL_CORE-$PARENT/astrid-core}"
TSX="${FIXALL_TSX:-$WEB/node_modules/.bin/tsx}"
CLAUDE="${CLAUDE_BIN:-$HOME/.local/bin/claude}"
MODEL="${FIXALL_MODEL:-opus}"
MAX_MINUTES="${FIXALL_MAX_MINUTES:-75}"
MAX_USD="${FIXALL_MAX_USD-10}"
ANDROID_ENV="${FIXALL_ANDROID_ENV-$HOME/dev-tools/android-env.sh}"
STALL_STATE="${FIXALL_STALL_STATE:-$HOME/Library/Logs/astrid-fixall-android-stall.state}"
STALL_ALERT_AFTER="${FIXALL_STALL_ALERT_AFTER:-3}"
LOG_HINT="~/Library/Logs/astrid-fixall-android.log"
# The one board this loop works. Pinned, not looked up by name (header, AATD-81).
LIST_ID="${FIXALL_LIST_ID:-85eef4ce-3d4d-4072-9ca7-bd631ca815b1}"
# Machine-local, outside any checkout, so `npm ci` and a re-clone keep it: the
# path agent-queue-status.ts itself defaults to (astrid-web scripts/lib/fixall-seen-file.ts).
SEEN_FILE="${FIXALL_SEEN_FILE:-$HOME/Library/Caches/astrid-fixall/seen-claude-$LIST_ID.json}"

echo "──────── fixall loop (android) $(date '+%Y-%m-%d %H:%M:%S') ────────"

cd "$REPO" || { echo "RESULT: FAILED — cannot enter $REPO"; exit 1; }

if [ ! -x "$TSX" ]; then
  echo "RESULT: FAILED — no tsx at $TSX (astrid-web checkout missing or not installed)"
  exit 1
fi

# Every board script runs from astrid-web: loadScriptEnv() reads .env.local from the cwd.
web_tool() { ( cd "$WEB" && "$TSX" "$@" ); }

# A comment on one task. The only place this loop can speak, since it holds no
# board id (header). Never fatal.
comment_on_task() {  # comment_on_task <taskId> <markdown>
  [ -n "$1" ] || return 0
  web_tool scripts/add-task-comment.ts "$1" "$2" >/dev/null 2>&1 \
    || echo "  (could not comment on task $1)"
}

# The Ready queue on the pinned board, for the stall alarm's "top Ready task".
# Prints the JSON verdict line; exit status is ready-tasks.ts's (non-zero = could not tell).
read_queue() {
  web_tool scripts/ready-tasks.ts android --json --harness claude-code --list "$LIST_ID" 2>/dev/null \
    | grep -E '^\{.*"tasks"' | tail -1
  return ${pipestatus[1]}
}

# ── The stall alarm ──────────────────────────────────────────────────────────
# Every guard below is right to refuse once. What none may do is refuse silently
# for ever: astrid-ios skipped 25 ticks across 12 hours on a dirty tree
# (AITD-426), and astrid-web's loop worked one task in 48 ticks behind a lock
# nobody released (AWTD-1207). So consecutive skips are counted, and the Nth says
# so ONCE — on the top Ready task, the work actually waiting behind the stall. A
# stall with nothing queued behind it costs nobody anything and is only logged.
#
# State is one line: <count> <alerted 0|1> <first-skip ISO8601>.
stall_field() { [ -f "$STALL_STATE" ] && awk -v f="$1" 'NR==1 { print $f }' "$STALL_STATE" 2>/dev/null; return 0; }
clear_stall() { rm -f "$STALL_STATE" 2>/dev/null; return 0; }

skip_tracked() {  # skip_tracked <one-line reason> [detail]  — prints RESULT and exits 0
  local reason="$1" detail="${2:-}" count alerted since queue top
  count=$(stall_field 1); count=$(( ${count:-0} + 1 ))
  alerted=$(stall_field 2); alerted=${alerted:-0}
  since=$(stall_field 3)
  [ -n "$since" ] || since=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

  if [ "$count" -ge "$STALL_ALERT_AFTER" ] && [ "$alerted" != "1" ]; then
    queue=$(read_queue)
    top=$(echo "$queue" | grep -oE '"id":"[0-9a-fA-F-]+","action":"ready"' | head -1 | cut -d'"' -f4)
    if [ -n "$top" ]; then
      comment_on_task "$top" "## Scheduled /fixall (android) has been skipping

This task is Ready, but the loop has skipped **$count ticks in a row** (since \`$since\`):

> $reason

That guard is doing its job; it will keep refusing until what is in its way is cleared, so this says so once rather than waiting to be asked.${detail:+

$detail}

Log: \`$LOG_HINT\`"
      echo "  stalled $count ticks — said so on task $top"
    else
      echo "  stalled $count ticks — nothing Ready is waiting behind it, so not posting"
    fi
    alerted=1
  fi

  echo "$count $alerted $since" > "$STALL_STATE" 2>/dev/null
  echo "RESULT: SKIPPED — $reason"
  exit 0
}

# ── Guard 1: one session per working tree ────────────────────────────────────
# Run FROM this checkout: the lock keys on `git rev-parse --absolute-git-dir` of
# the cwd, so taking it after a `cd "$WEB"` would lock the WEB tree instead.
# --scheduled registers this loop as a holder without publishing a machine-wide
# pause, so the three loops on this Mac never stand each other down.
"$TSX" "$WEB/scripts/fixall-session.ts" acquire --pid $$ --harness claude-code --scheduled
LOCK_STATUS=$?
if [ "$LOCK_STATUS" -eq 2 ]; then
  skip_tracked "another live session holds this checkout"
elif [ "$LOCK_STATUS" -ne 0 ]; then
  echo "RESULT: FAILED — could not take the working-tree lock (exit $LOCK_STATUS)"
  exit 1
fi
release_lock() { "$TSX" "$WEB/scripts/fixall-session.ts" release --pid $$ >/dev/null 2>&1; }
trap release_lock EXIT INT TERM

# The lock is held by THIS script, whose pid the session cannot see; the
# workflow's own acquire is skipped when this is set.
export ASTRID_FIXALL_LOCK_HELD=1
# One Ready task per run (FIXALL_WORKFLOW.md → "One task per scheduled run"): a
# gradle + cargo predeploy is minutes, and a run that takes the whole queue does
# not fit the watchdog. The next tick takes the next task.
export ASTRID_FIXALL_MAX_TASKS="${FIXALL_MAX_TASKS:-1}"

# ── Guard 1b: stand down while someone is working this Mac ───────────────────
# The lock is per working tree; contention is per machine. An interactive
# `fixall-session.ts acquire` in ANY repo, or `npm run fixall:pause` in
# astrid-web, stands every loop down (AWTD-1212). Unreadable state runs anyway.
YIELD_OUT=$("$TSX" "$WEB/scripts/fixall-pause.ts" yield --self-pid $$ 2>&1)
YIELD_STATUS=$?
if [ "$YIELD_STATUS" -eq 2 ]; then
  skip_tracked "$YIELD_OUT" \
    "If nobody is working, \`npm run fixall:pause -- status\` in astrid-web says what is holding it, and \`npm run fixall:resume\` lifts an explicit pause."
elif [ "$YIELD_STATUS" -ne 0 ]; then
  echo "  ⚠️  could not read the pause state, running anyway: $YIELD_OUT"
fi

# ── Guard 2: never clobber work in progress ──────────────────────────────────
# /fixstuff takes no lock. A merged branch is not work in progress (astrid-web,
# 2026-09-26: 9½ hours skipped on a clean, merged branch), so the loop goes back
# to main itself. Unpushed commits on main ARE (AWTD-1095).
GUARDS_CONFIRMED_CLEAN=0
CORE_CONFIRMED_CLEAN=0
if [ "${FIXALL_FORCE:-0}" != "1" ]; then
  BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
  DIRTY=$(git status --porcelain 2>/dev/null)
  if [ -n "$DIRTY" ]; then
    skip_tracked "working tree is dirty, leaving it alone" "On branch \`$BRANCH\`, uncommitted:

\`\`\`
$(echo "$DIRTY" | head -20)
\`\`\`"
  fi
  if [ "$BRANCH" != "main" ]; then
    if git -c core.fsmonitor=false fetch -q origin main 2>/dev/null && git merge-base --is-ancestor HEAD origin/main; then
      if git checkout -q main && git merge -q --ff-only origin/main; then
        echo "  returned to main from $BRANCH (already in origin/main)"
      else
        skip_tracked "HEAD is on $BRANCH (merged) but could not return to main"
      fi
    else
      skip_tracked "HEAD is on $BRANCH, not main, with commits origin/main lacks" \
        "Merging \`$BRANCH\` to \`main\` (or checking \`main\` out) starts the ticks again."
    fi
  else
    git -c core.fsmonitor=false fetch -q origin main 2>/dev/null
    AHEAD=$(git rev-list --count origin/main..HEAD 2>/dev/null)
    if [ -n "$AHEAD" ] && [ "$AHEAD" -gt 0 ]; then
      skip_tracked "main has $AHEAD commit(s) origin/main lacks — someone's work in progress"
    fi
  fi
  GUARDS_CONFIRMED_CLEAN=1

  # The core is a shared clone: a branch there is someone's checkout and no
  # reason to skip, but uncommitted work is, since this run may edit the core too.
  if [ -n "$CORE" ] && [ -d "$CORE/.git" ]; then
    CORE_DIRTY=$(git -C "$CORE" status --porcelain 2>/dev/null)
    if [ -n "$CORE_DIRTY" ]; then
      skip_tracked "astrid-core is dirty, leaving it alone" "In \`$CORE\`, uncommitted:

\`\`\`
$(echo "$CORE_DIRTY" | head -20)
\`\`\`"
    fi
    CORE_CONFIRMED_CLEAN=1
  fi
fi

# ── Guard 2b: not while the machine is buried ────────────────────────────────
# Three loops share this Mac (iOS simulator suites, web predeploy, this one's
# gradle + cargo), plus self-hosted runners. A run cannot finish a task without a
# green predeploy, and a buried machine turns that into timeouts that pass alone
# (astrid-web AWTD-1192/1197). The 5-minute average, integer comparison, explicit
# sysctl seam — each of those choices is explained in astrid-web's copy.
if [ "${FIXALL_FORCE:-0}" != "1" ]; then
  SYSCTL="${FIXALL_SYSCTL:-$(command -v sysctl 2>/dev/null || echo /usr/sbin/sysctl)}"
  LOAD_5M=$("$SYSCTL" -n vm.loadavg 2>/dev/null | awk '{print $3}')
  NCPU=$("$SYSCTL" -n hw.ncpu 2>/dev/null)
  MAX_LOAD="${FIXALL_MAX_LOAD:-}"
  if [ -z "$MAX_LOAD" ] && [ -n "$NCPU" ] && [ "$NCPU" -gt 0 ] 2>/dev/null; then
    MAX_LOAD=$(( NCPU * ${FIXALL_MAX_LOAD_PER_CPU:-4} ))
  fi
  LOAD_INT="${LOAD_5M%%.*}"
  if [ -z "$LOAD_5M" ] || [ -z "$MAX_LOAD" ] || ! [ "$LOAD_INT" -ge 0 ] 2>/dev/null; then
    echo "  ⚠️  could not read the load from $SYSCTL (load='$LOAD_5M' cores='$NCPU') — not guarding this tick"
  elif [ "$LOAD_INT" -gt "$MAX_LOAD" ]; then
    skip_tracked "machine is buried — 5-minute load $LOAD_5M is over $MAX_LOAD, so predeploy would time out"
  else
    echo "  load ok — 5-minute average $LOAD_5M against a budget of $MAX_LOAD"
  fi
fi

# Got past them: whatever the last stall was, it is over.
clear_stall

# ── Run what is merged, not what someone last pulled by hand ─────────────────
# Nothing else updates this checkout. If the loop itself changed, exec the new
# version (same pid, so the lock is simply reacquired). A failed fetch degrades
# to running the current checkout. fsmonitor off: a stuck daemon hung a pull.
if [ "$GUARDS_CONFIRMED_CLEAN" -eq 1 ] && [ "${FIXALL_SELF_UPDATED:-0}" != "1" ]; then
  BEFORE=$(git rev-parse HEAD)
  if GIT_TERMINAL_PROMPT=0 git -c core.fsmonitor=false fetch -q origin main 2>/dev/null \
     && git -c core.fsmonitor=false merge -q --ff-only origin/main 2>/dev/null; then
    AFTER=$(git rev-parse HEAD)
    if [ "$BEFORE" != "$AFTER" ]; then
      echo "  updated main $(git rev-parse --short "$BEFORE") → $(git rev-parse --short "$AFTER")"
      if ! git diff --quiet "$BEFORE" "$AFTER" -- scripts/fixall-loop.sh; then
        echo "  the loop changed — restarting on the new version"
        FIXALL_SELF_UPDATED=1 exec "$0" "$@"
      fi
    fi
  else
    echo "  ⚠️  could not fast-forward main to origin/main — running on $(git rev-parse --short HEAD)"
  fi
fi

# ── Doing must not be a dead end ─────────────────────────────────────────────
# A claim idle past the watchdog goes back to Ready naming its branch (astrid-web,
# 2026-09-28). On the pinned board, like the queue. Before guard 3, so a released
# task is work for this tick. Never fatal.
web_tool scripts/release-stuck-doing.ts --agent claude --list "$LIST_ID" \
  --stale-minutes "${FIXALL_STALE_DOING_MINUTES:-180}" --repo "$REPO" 2>&1 | sed 's/^/ /'

# ── Guard 3: is there actually any work? ─────────────────────────────────────
# A few HTTP requests instead of a whole session that finds an empty queue, and
# the SAME question the web loop asks (astrid-web scripts/agent-queue-status.ts,
# AWTD-963) — both halves of it (AATD-81):
#
#   queue  — Ready ∩ (assigned to claude ∪ unassigned) ∩ due. Waiting is the
#            server's: its settle job wakes a task, which then simply arrives here.
#   inbox  — `attention`: task comments and list chat nobody answered. A reply
#            on a handed-back task is work even when Ready is empty.
#
# Exit 0 = work, 3 = idle (skip), anything else = could not tell — which is NOT
# idle: run, and let the agent report properly.
#
# Waking is two-phase. The preflight never writes the seen-file
# (--no-write-seen); it hands back the keys that woke it, and the run's outcome
# records exactly those after the run ("Acknowledge", below). A run that dies
# must not mute what woke it on the first failure, and new comments that arrive
# DURING the run were never in the keys, so they wake the next tick.
PREFLIGHT=$(mktemp "${FIXALL_PREFLIGHT_DIR:-${TMPDIR:-/tmp}}/fixall-android-preflight.XXXXXX") || PREFLIGHT=""
# An array, not ${PREFLIGHT:+--output "$PREFLIGHT"}: zsh does not split that, and
# the helper would get "--output <path>" as ONE argument.
OUTPUT_ARGS=()
[ -n "$PREFLIGHT" ] && OUTPUT_ARGS=(--output "$PREFLIGHT")
QUEUE_OUT=$(web_tool scripts/agent-queue-status.ts --agent claude --list "$LIST_ID" --board android \
  --include-unassigned --no-write-seen --seen "$SEEN_FILE" "${OUTPUT_ARGS[@]}" 2>&1)
QUEUE_STATUS=$?
QUEUE_LINES=$(echo "$QUEUE_OUT" | grep -E '^(QUEUE|SEEN|ATTENTION):')
QUEUE_KEYS=$(echo "$QUEUE_OUT" | sed -n 's/^KEYS: //p' | head -1)
echo "${QUEUE_LINES:-QUEUE: no verdict}" | sed 's/^/  /'
if [ "$QUEUE_STATUS" -eq 3 ]; then
  [ -n "$PREFLIGHT" ] && rm -f "$PREFLIGHT"
  echo "RESULT: SKIPPED — nothing to do for claude (no Ready task, no new reply)"
  exit 0
elif [ "$QUEUE_STATUS" -ne 0 ]; then
  echo "  QUEUE: unknown — agent-queue-status.ts exited $QUEUE_STATUS; running anyway so the agent can report"
  QUEUE_KEYS=""
fi
# Nothing acknowledges a tick that never starts claude: drop the artifact on the
# way out unless the acknowledgement below decided to keep it.
KEEP_PREFLIGHT=0
drop_preflight() { [ -n "$PREFLIGHT" ] && [ "$KEEP_PREFLIGHT" != "1" ] && rm -f "$PREFLIGHT"; return 0; }
trap 'drop_preflight; release_lock' EXIT INT TERM

# ── The toolchain launchd does not give us ───────────────────────────────────
# Without it every gradle and cargo-ndk step of predeploy fails, and the run
# burns its budget learning that. A missing file is a FAILED tick, not a skip:
# it will not fix itself.
if [ -n "$ANDROID_ENV" ]; then
  if [ ! -f "$ANDROID_ENV" ]; then
    echo "RESULT: FAILED — no Android toolchain env at $ANDROID_ENV (set FIXALL_ANDROID_ENV)"
    exit 1
  fi
  source "$ANDROID_ENV"
  echo "  toolchain: JAVA_HOME=${JAVA_HOME:-unset} ANDROID_HOME=${ANDROID_HOME:-unset}"
fi

if [ ! -x "$CLAUDE" ]; then
  echo "RESULT: FAILED — no claude CLI at $CLAUDE (set CLAUDE_BIN)"
  exit 1
fi

# ── The session's allowed directories ────────────────────────────────────────
# astrid-web for the board scripts (claim, comments, the OAuth fallback when no
# astrid MCP server is registered for this repo), astrid-core for a task whose
# fix is in the core. Without --add-dir a -p session refuses the siblings
# outright (astrid-windows, 2026-09-26: "allowed working directory is
# astrid-windows only"). A missing core is a degraded run, not a failed one.
ADD_DIRS=(--add-dir "$WEB")
if [ -n "$CORE" ] && [ -d "$CORE" ]; then
  ADD_DIRS+=(--add-dir "$CORE")
else
  echo "  ⚠️  no astrid-core checkout at ${CORE:-(none)} — a task whose fix is in the core cannot be worked this run"
fi

# The prompt (AATD-81): /fixall, and say outright that the inbox is part of the
# run — a reply-only wake has an EMPTY Ready queue, and "Ready is empty, stop"
# would answer nobody.
PROMPT="/fixall Read get_agent_queue's attention (task replies and list chat) as well as its queue, and answer it on the task even when the Ready queue is empty."

if [ "${FIXALL_DRY_RUN:-0}" = "1" ]; then
  echo "  would run: $CLAUDE -p \"$PROMPT\" --model $MODEL --permission-mode ${FIXALL_PERMISSION_MODE:-acceptEdits} ${ADD_DIRS[*]}${MAX_USD:+ --max-budget-usd $MAX_USD}"
  echo "RESULT: SKIPPED — dry run, every guard passed and claude was not started"
  exit 0
fi

# ── The run ──────────────────────────────────────────────────────────────────
# Two bounds: the watchdog catches a run that HANGS (launchd starts no second copy
# of a live label), the budget one that stays BUSY. Output goes to a file so the
# loop can read the run's own verdict back; a plain redirect keeps $! = claude.
BUDGET_ARGS=()
[ -n "$MAX_USD" ] && BUDGET_ARGS=(--max-budget-usd "$MAX_USD")

RUN_OUT=$(mktemp "${TMPDIR:-/tmp}/fixall-android-run.XXXXXX") || RUN_OUT=/dev/null
# Every task this run claims is recorded here by astrid-web's claim-fixall-task.ts,
# so the ones it leaves in Doing are released after it — exactly, no heuristic.
CLAIMS_FILE=$(mktemp "${TMPDIR:-/tmp}/fixall-android-claims.XXXXXX")
export ASTRID_FIXALL_CLAIMS_FILE="$CLAIMS_FILE"

echo "→ /fixall ($MODEL, watchdog ${MAX_MINUTES}m${MAX_USD:+, cap \$$MAX_USD})"
"$CLAUDE" -p "$PROMPT" \
  --model "$MODEL" \
  --permission-mode "${FIXALL_PERMISSION_MODE:-acceptEdits}" \
  "${ADD_DIRS[@]}" \
  "${BUDGET_ARGS[@]}" > "$RUN_OUT" 2>&1 &
CLAUDE_PID=$!

# Hold the Mac awake for exactly as long as the run lives: -w on the run's pid,
# not caffeinate as a wrapper (the watchdog's kill would land on the wrapper).
CAFFEINATE_BIN="${FIXALL_CAFFEINATE-caffeinate}"
CAFFEINATE_PID=""
if [ -n "$CAFFEINATE_BIN" ] && command -v "$CAFFEINATE_BIN" >/dev/null 2>&1; then
  "$CAFFEINATE_BIN" -i -m -w "$CLAUDE_PID" &
  CAFFEINATE_PID=$!
elif [ -n "$CAFFEINATE_BIN" ]; then
  echo "  ⚠️  no caffeinate at $CAFFEINATE_BIN — idle sleep may kill this run"
fi

( sleep $((MAX_MINUTES * 60)); kill -TERM "$CLAUDE_PID" 2>/dev/null ) &
WATCHDOG_PID=$!

wait "$CLAUDE_PID"
STATUS=$?
kill "$WATCHDOG_PID" 2>/dev/null
[ -n "$CAFFEINATE_PID" ] && { kill "$CAFFEINATE_PID" 2>/dev/null || true; }
cat "$RUN_OUT" 2>/dev/null
RUN_VERDICT=$(grep -E '^RESULT: ' "$RUN_OUT" 2>/dev/null | tail -1)
UNTRUSTED=$(grep -E 'has not been trusted|Ignoring [0-9]+ permissions\.allow' "$RUN_OUT" 2>/dev/null | head -2)
[ "$RUN_OUT" != /dev/null ] && rm -f "$RUN_OUT"

if [ -n "$UNTRUSTED" ]; then
  echo "  ⚠️  the CLI did not trust this workspace, so .claude/settings.json was ignored:"
  echo "$UNTRUSTED" | sed 's/^/     /'
fi

# ── Leave the checkout where the next tick can use it ────────────────────────
# Only when the guards confirmed it clean going in: under FIXALL_FORCE the tree
# may have held someone's work before the run, and that is not ours to move.
#
# On a TASK branch the leftovers are the run's own: commit them as an UNFINISHED
# WIP (--no-verify: unverified by definition), push the branch, return to main.
# On MAIN they could be anyone's (AWTD-1095), so they are only SNAPSHOTTED — a
# commit built on a temporary index and pushed to a wip/ branch — and HEAD, index
# and files are left exactly as they were. The next tick skips on the dirty tree
# and the stall alarm says so; losing nobody's work is worth a skipped tick.
snapshot_to_branch() {  # snapshot_to_branch <git dir> <branch name> → prints branch on success
  local dir="$1" snap="$2" idx tree commit head_branch
  head_branch=$(git -C "$dir" rev-parse --abbrev-ref HEAD 2>/dev/null)
  idx=$(mktemp "${TMPDIR:-/tmp}/fixall-android-index.XXXXXX")
  cp "$(git -C "$dir" rev-parse --path-format=absolute --git-path index)" "$idx" 2>/dev/null || rm -f "$idx"
  if GIT_INDEX_FILE="$idx" git -C "$dir" add -A \
     && tree=$(GIT_INDEX_FILE="$idx" git -C "$dir" write-tree) \
     && commit=$(git -C "$dir" commit-tree "$tree" -p HEAD -m "wip: uncommitted changes on $head_branch after a scheduled /fixall (android) run — UNFINISHED, UNVERIFIED

Snapshotted by astrid-android/scripts/fixall-loop.sh (claude exit $STATUS). They may
be the run's or another session's, so the checkout was left exactly as it was;
this commit is only a copy. Predeploy has not been run on this. Do not ship it.") \
     && git -C "$dir" branch "$snap" "$commit" \
     && git -C "$dir" push -q origin "$snap" 2>/dev/null; then
    rm -f "$idx"; echo "$snap"; return 0
  fi
  rm -f "$idx"; return 1
}

LEFT_DIRTY=""
SAVED_BRANCH=""
CORE_SAVED=""
if [ "$GUARDS_CONFIRMED_CLEAN" -eq 1 ]; then
  END_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
  DIRTY_AFTER=$(git status --porcelain 2>/dev/null)
  if [ -n "$DIRTY_AFTER" ]; then
    LEFT_DIRTY="$DIRTY_AFTER"
    if [ "$END_BRANCH" = "main" ] || [ "$END_BRANCH" = "HEAD" ]; then
      if SAVED_BRANCH=$(snapshot_to_branch "$REPO" "wip/fixall-android-$(date +%Y%m%d-%H%M%S)"); then
        echo "  $END_BRANCH has uncommitted changes — left them in place, copy pushed to $SAVED_BRANCH"
      else
        SAVED_BRANCH=""
        echo "  ⚠️  $END_BRANCH has uncommitted changes and no copy could be pushed — left them in place"
      fi
    elif git add -A && git commit -q --no-verify -m "wip: scheduled /fixall (android) run ended mid-task — UNFINISHED, UNVERIFIED

Saved by scripts/fixall-loop.sh (claude exit $STATUS) so the checkout can return
to main. Predeploy has not been run on this. Continue from here; do not ship it."; then
      SAVED_BRANCH="$END_BRANCH"
      echo "  run left uncommitted changes — saved as a WIP commit on $END_BRANCH"
    else
      echo "  ⚠️  run left $END_BRANCH with uncommitted changes and they could not be committed — leaving it for a human"
    fi
  fi
  if [ "$END_BRANCH" != "main" ] && [ -z "$(git status --porcelain 2>/dev/null)" ]; then
    if git push -q -u origin "$END_BRANCH" 2>/dev/null; then
      echo "  run left $END_BRANCH — pushed it to origin for review"
    else
      echo "  ⚠️  run left $END_BRANCH and it could not be pushed — it is still in $REPO"
    fi
    git checkout -q main && echo "  returned to main from $END_BRANCH"
  fi
fi

# The core, one layer out, copied and never moved (header). Only when the guards
# saw it clean going in, so what is there now is this run's.
if [ "$CORE_CONFIRMED_CLEAN" -eq 1 ] && [ -n "$(git -C "$CORE" status --porcelain 2>/dev/null)" ]; then
  if CORE_SAVED=$(snapshot_to_branch "$CORE" "wip/fixall-android-core-$(date +%Y%m%d-%H%M%S)"); then
    echo "  astrid-core has uncommitted changes — left them in place, copy pushed to $CORE_SAVED"
  else
    CORE_SAVED=""
    echo "  ⚠️  astrid-core has uncommitted changes and no copy could be pushed — left them in place"
  fi
  [ -n "$LEFT_DIRTY" ] || LEFT_DIRTY="(astrid-core) $(git -C "$CORE" status --porcelain 2>/dev/null | head -20)"
fi

# A run that hands back a dirty tree has not finished, whatever it exits
# (AITD-426). And a run that SAYS it failed has failed (AITD-440): only the LAST
# RESULT: line of its output counts.
REASON=""
if [ "$STATUS" -eq 0 ] && [ -n "$LEFT_DIRTY" ]; then
  STATUS=90
  REASON="run left the tree dirty"
elif [ "$STATUS" -eq 0 ] && [[ "$RUN_VERDICT" == "RESULT: FAILED"* ]]; then
  STATUS=91
  REASON="${RUN_VERDICT#RESULT: FAILED}"; REASON="${REASON# — }"
  REASON="run reported: ${REASON:-no reason given}"
elif [ "$STATUS" -ge 128 ]; then
  REASON="killed after ${MAX_MINUTES}m watchdog timeout (signal $((STATUS - 128)))"
elif [ "$STATUS" -ne 0 ]; then
  REASON="claude exited $STATUS"
fi

# A completed task's branch gets a PR: `gh` is denied inside the session, the
# runner can run it. --repo points it at THIS repo's origin.
PR_OUT=$(web_tool scripts/open-fixall-prs.ts --claims-file "$CLAIMS_FILE" --repo "$REPO" 2>&1)
[ -n "$PR_OUT" ] && echo "$PR_OUT" | sed 's/^/ /'

# A died run cannot write its own completion comment, so the loop says it on the
# task it claimed — before the release below, which hands the task back.
if [ "$STATUS" -ne 0 ]; then
  NOTE="**Scheduled /fixall (android) did not finish** — $REASON."
  [ -n "$SAVED_BRANCH" ] && NOTE="$NOTE Unfinished work is on \`$SAVED_BRANCH\` (WIP, unverified — a resume point, not something to ship)."
  [ -n "$CORE_SAVED" ] && NOTE="$NOTE A copy of its astrid-core changes is on \`$CORE_SAVED\`."
  [ -n "$LEFT_DIRTY" ] && NOTE="$NOTE

Uncommitted at the end of the run:

\`\`\`
$(echo "$LEFT_DIRTY" | head -20)
\`\`\`"
  NOTE="$NOTE

Log: \`$LOG_HINT\`"
  if [ -s "$CLAIMS_FILE" ]; then
    while IFS= read -r task_id; do
      [ -n "$task_id" ] && comment_on_task "$task_id" "$NOTE"
    done < "$CLAIMS_FILE"
  else
    echo "  (the run claimed no task, so there is no task to report this on)"
  fi
fi

# Anything this run claimed that is STILL in Doing was abandoned mid-task.
web_tool scripts/release-stuck-doing.ts --agent claude --claims-file "$CLAIMS_FILE" --repo "$REPO" 2>&1 | sed 's/^/ /'
rm -f "$CLAIMS_FILE"

# ── Acknowledge what woke the run (phase two, AATD-81) ───────────────────────
# Exactly the preflight's keys, never a recomputed set. A finished run had its
# chance at them: seen. A failed or killed one gets a strike (--failed), and a
# key out of strikes is muted (astrid-web scripts/lib/wake-keys.ts), so a run
# that keeps dying on one comment cannot start a session every tick forever.
# An acknowledgement that does not land fails the tick and KEEPS the preflight
# artifact: otherwise the same reply silently wakes run after run.
if [ -n "$QUEUE_KEYS" ]; then
  ACK_ARGS=(--mark-seen)
  [ "$STATUS" -ne 0 ] && ACK_ARGS+=(--failed)
  ACK_OUT=$(web_tool scripts/agent-queue-status.ts --agent claude --list "$LIST_ID" --seen "$SEEN_FILE" \
    "${ACK_ARGS[@]}" --seen-keys "$QUEUE_KEYS" 2>&1)
  ACK_STATUS=$?
  [ -n "$ACK_OUT" ] && echo "$ACK_OUT" | sed 's/^/  /'
  if [ "$ACK_STATUS" -ne 0 ]; then
    KEEP_PREFLIGHT=1
    ACK_REASON="could not acknowledge the wake keys (exit $ACK_STATUS) — preflight kept at ${PREFLIGHT:-(none)}"
    if [ "$STATUS" -eq 0 ]; then STATUS=92; REASON="$ACK_REASON"; else REASON="$REASON; $ACK_REASON"; fi
  fi
fi

if [ "$STATUS" -eq 0 ]; then
  echo "RESULT: OK — run finished (see the tasks for what changed)"
  exit 0
fi
echo "RESULT: FAILED — $REASON"
exit 1
