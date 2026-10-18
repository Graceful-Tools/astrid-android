Pull tasks from the **Astrid Android To-do** and work through them with the user.

## The workflow itself is shared

Read [`../astrid-web/docs/FIXALL_WORKFLOW.md`](../../../astrid-web/docs/FIXALL_WORKFLOW.md) — the
canonical per-task loop, shared by web, iOS, Windows and Android because it is one workflow. This
file holds only what is different **here**.

## Steps

1. **Pull the board.** Resolved by name, never by a hardcoded id:
   ```
   get_lists {}                                   → "Astrid Android To-do", take its id
   get_agent_queue { agent: "claude", listId: <that id> }
   ```
   Queue debugging only: `cd ../astrid-web && npx tsx scripts/ready-tasks.ts android --harness claude-code`.
2. **Present the tasks** by title and ask which to work on.
3. **For each task:**
   - Post the session link (`cd ../astrid-web && npx tsx scripts/post-session-link.ts <taskId>`) and a
     strategy comment.
   - **Find where the fix lives.** A rule, a row projection, a picker, the Outbox, sync: that is
     astrid-core — work it in `../astrid-core`, push there, bump `core/Cargo.toml`'s rev here, and
     say both revisions in the report. Shared behaviour starts on web (`../astrid-web`).
   - **RED → GREEN:** a test naming the task id. View models over `FakeCore` in `app/src/test`; the
     bridge against the real core in `corebridge/src/test`; on a device in `app/src/androidTest`.
   - `npm run predeploy` (and `npm run predeploy:full` with the emulator running when the screen
     changed). Compare with iOS: the smoke tests leave screenshots in `/data/local/tmp/astrid-*.png`.
   - Commit on `main` (or a branch merged to it), push, post the completion report, complete the task.

## What is different here

- `source ~/dev-tools/android-env.sh` first on the dev Mac (JDK, SDK, NDK; Homebrew is not writable).
- Emulator: `emulator -avd astrid35 -no-window -no-audio -no-snapshot &`. Close Chrome on it
  (`adb shell am force-stop com.android.chrome`) if a sign-in check left a Custom Tab on top — it
  covers the app and every device test times out.
- **Match iOS.** Behaviour, wording and design follow astrid-ios; a divergence needs an Android reason,
  recorded in docs/PARITY.md.
- **A push to `main` reaches nobody.** Releases are a Play upload, which always needs Jon.
- Generated bindings (`corebridge/src/main/java/uniffi/`) are never edited; the gate fails if they drift.
