# Codex — Astrid Android operational adapter

**Repository:** https://github.com/Graceful-Tools/astrid-android · **Core (pinned):** astrid-core ·
**Web/API:** astrid-web · **Design and behaviour to match:** astrid-ios

## Read docs/ASTRID.md before writing code

It owns the architecture and the rules. Critical ones:

1. **No business logic in `app/`** — ask the core.
2. **Every backend write is a core command**; complete only with `CompleteTask`.
3. **Match iOS**; diverge only for Android interaction/capability and record it in docs/PARITY.md.
4. **Bug fixes are TDD**: RED test naming the task id → GREEN → `npm run predeploy`.
5. **Generated bindings are never hand-edited** (`corebridge/src/main/java/uniffi/`).

## Commands

```bash
source ~/dev-tools/android-env.sh   # JDK, SDK, NDK on the dev Mac
npm run predeploy                   # the standard gate
npm run predeploy:quick             # host-only core build, no lint
npm run predeploy:full              # + instrumented tests on a running emulator
scripts/build-core.sh --core ../astrid-core   # build against a local core checkout
```

## Tasks

`/fixstuff` (interactive) and `/fixall` (the Ready queue) — `.claude/commands/`. The
**Astrid Android To-do** board (`85eef4ce-3d4d-4072-9ca7-bd631ca815b1`, key AATD). Per-task
process is astrid-web `docs/FIXALL_WORKFLOW.md`: strategy comment → RED-GREEN-refactor → gate →
completion report → complete. Use `listIds` (not `listId`) and the `X-OAuth-Token` header.

`/fixall` also runs unattended at `:10`/`:40` (LaunchAgent `cc.astrid.fixall-android`,
`scripts/fixall-loop.sh`) — install, inspect and uninstall in [docs/AUTOMATION.md](docs/AUTOMATION.md).
Working here interactively for long? Take the lock **from this checkout** (the cwd keys it), so a
tick cannot sweep your edits: `../astrid-web/node_modules/.bin/tsx ../astrid-web/scripts/fixall-session.ts
acquire --pid <your claude pid> --harness claude-code` (not `$$` — that pid is dead at once).

## Approvals

**Always ask before:** publishing to Google Play, signing a release with a real key, deleting files.
**Autonomous:** analysis, builds, tests, implementation, commits, docs, pushing `main` (a push
publishes nothing to users).

*This file is for Codex. Claude Code reads [CLAUDE.md](./CLAUDE.md) (same content).*
