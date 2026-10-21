# Automation — the scheduled /fixall loop

`/fixall` on the **Astrid Android To-do** board runs unattended on the dev Mac, as the iOS and web
loops do. Assigning a task to Claude Agent starts nothing by itself (Astrid's polling mode calls out
to no one), so a LaunchAgent polls.

| | |
|---|---|
| Label | `cc.astrid.fixall-android` |
| When | `:10` and `:40` (iOS runs `:00`/`:30`, web `:20`/`:50`) |
| What | `scripts/run-fixall-loop.mjs` (node shim, for TCC) → `scripts/fixall-loop.sh` → `claude -p /fixall` |
| Log | `~/Library/Logs/astrid-fixall-android.log` — every tick ends in one `RESULT: OK/SKIPPED/FAILED` line |
| Runs on | the Claude Code CLI subscription; no API key anywhere in the path |

## Install, inspect, uninstall

Always from the **main checkout** (`~/mycode/astrid-android`), never a worktree: the loop works the
checkout it is installed from.

```bash
./scripts/launchd/install.sh cc.astrid.fixall-android      # render the template, bootstrap it
launchctl list | grep fixall-android                        # loaded? (pid, last exit, label)
launchctl print gui/$(id -u)/cc.astrid.fixall-android | head -30
tail -50 ~/Library/Logs/astrid-fixall-android.log
grep RESULT ~/Library/Logs/astrid-fixall-android.log | tail

FIXALL_DRY_RUN=1 npm run fixall:loop    # every guard, then stop before claude
npm run fixall:loop                     # one real pass by hand (prompts: no bypass mode)
npm run test:scripts                    # the loop's guard tests (also in predeploy on macOS)

launchctl bootout gui/$(id -u)/cc.astrid.fixall-android    # stop it
rm ~/Library/LaunchAgents/cc.astrid.fixall-android.plist   # and remove it
```

The installed plist sets `FIXALL_PERMISSION_MODE=bypassPermissions` (an unattended run cannot
answer a prompt); by hand the loop uses `acceptEdits`. Re-run `install.sh` after moving the checkout.

## What a tick does

1. **Guards, cheapest first** — each skip exits 0:
   the working-tree lock (astrid-web `scripts/fixall-session.ts`, keyed to this checkout);
   stand down while anyone works this Mac (`fixall-pause.ts yield` — an interactive session's lock
   in any repo, or `npm run fixall:pause` in astrid-web); dirty tree, unmerged branch, or unpushed
   commits on `main`; a dirty `../astrid-core`; a 5-minute load average over cores × 4.
   Three skips in a row are reported **once**, as a comment on the top Ready task.
2. Fast-forward `main`; restart if the loop itself changed.
3. Release Doing claims idle for 3 hours (`release-stuck-doing.ts --board android`).
4. **Is there work?** `ready-tasks.ts android --json --harness claude-code` (lane sweep included).
   Empty → `SKIPPED`. The board is resolved **by name**; no id lives in the loop.
5. Source `~/dev-tools/android-env.sh` (JDK, SDK, NDK, cargo — launchd's PATH has none of them).
6. `claude -p /fixall --add-dir ../astrid-web --add-dir ../astrid-core`, one task, 75-minute
   watchdog, `$10` CLI budget bound, `caffeinate -w` on the run.
7. Afterwards: unfinished work on a task branch is committed as `UNFINISHED, UNVERIFIED` and
   pushed; leftovers on `main` or in astrid-core are only *copied* to a pushed `wip/` branch and
   left in place. PRs are opened for finished task branches, abandoned claims released, and a
   died run says why on the task it claimed.

Not covered, by design (same as astrid-windows): the loop holds no board id, so it does not wake
on unanswered comments (`agent-queue-status.ts` needs a list id) and does not post to list chat.

No astrid MCP server is registered for this repo in `~/.claude.json`, so unattended runs read the
board through astrid-web's OAuth scripts (the documented fallback). Registering the server for
`~/mycode/astrid-android` would give runs the `mcp__astrid__*` tools.

## Working here by hand

A long interactive session should take the working-tree lock **from this checkout** — the lock
keys on the cwd's git directory, so running it from astrid-web locks the wrong tree:

```bash
../astrid-web/node_modules/.bin/tsx ../astrid-web/scripts/fixall-session.ts acquire --pid <claude pid> --harness claude-code
../astrid-web/node_modules/.bin/tsx ../astrid-web/scripts/fixall-session.ts status   # must say "live"
```

Use the long-lived `claude` process's pid (`ps -p $PPID -o pid,comm`), never `$$`: a one-shot
shell's pid is dead the moment the command returns and the lock reads as abandoned. Holding it also
stands the iOS and web loops down for as long as the session lives.
