# Build plan

*Every row is a task on the **Astrid Android To-do** board (key AATD). Work them in order with the
fixstuff process: strategy comment → RED test naming the task id → GREEN → `npm run predeploy` →
completion report → complete. Polishing after M2 is driven from the board, not from this file.*

**Goal:** behave like the iOS app, with changes only for Android interaction and capability.

## M0 — foundation

| Task | What |
|---|---|
| [AATD-1](https://astrid.cc/t/AATD-1) | Repo scaffold, docs, predeploy gate, CI |
| [AATD-2](https://astrid.cc/t/AATD-2) | astrid-web: `android-app` platform and an `android` hand-off client (deployed) |
| [AATD-3](https://astrid.cc/t/AATD-3) | astrid-core: `Platform::Android`, hand-off client chosen by platform |
| [AATD-4](https://astrid.cc/t/AATD-4) | Core bridge: UniFFI crate, cargo-ndk, generated Kotlin, CoreSession/CoreRules |

## M1 — a working app (sign in, read, write, offline)

| Task | What |
|---|---|
| [AATD-5](https://astrid.cc/t/AATD-5) | App shell: CoreSession lifecycle, Keystore credential store, iOS theme, change stream |
| [AATD-6](https://astrid.cc/t/AATD-6) | Browser hand-off sign-in, sign out |
| [AATD-7](https://astrid.cc/t/AATD-7) | Sidebar drawer: lists, favourites, virtual lists, counts |
| [AATD-8](https://astrid.cc/t/AATD-8) | Task list: rows, complete with rollover, quick add, refresh |
| [AATD-9](https://astrid.cc/t/AATD-9) | Task detail: every field, comments, delete |
| [AATD-10](https://astrid.cc/t/AATD-10) | Live updates, Outbox indicator, background sync |

## M2 — parity with iOS

| Task | What |
|---|---|
| [AATD-11](https://astrid.cc/t/AATD-11) | Search |
| [AATD-12](https://astrid.cc/t/AATD-12) | Board view |
| [AATD-13](https://astrid.cc/t/AATD-13) | Settings |
| [AATD-14](https://astrid.cc/t/AATD-14) | Reminders as notifications |
| [AATD-15](https://astrid.cc/t/AATD-15) | List settings, sharing, members |
| [AATD-16](https://astrid.cc/t/AATD-16) | Share-to-Astrid, shortcuts, deep links |
| [AATD-17](https://astrid.cc/t/AATD-17) | Chat and attachments |

## M3 — quality and release

| Task | What |
|---|---|
| [AATD-18](https://astrid.cc/t/AATD-18) | Instrumented smoke tests, accessibility, startup performance |
| [AATD-19](https://astrid.cc/t/AATD-19) | Release build and Play listing draft (Play upload needs Jon) |

## State, 2026-10-18 morning

M0, M1 and M2 are on `main`; AATD-1…18, 21 and 22 are complete. Open: AATD-19 (Play — needs
Jon), AATD-20 (board drag-and-drop), and the polish rows in [PARITY.md](./PARITY.md). Cross-repo
work it needed: astrid-web e6410f63 (deployed), astrid-core 8750331, 4e046e1, c0c83ed, 778d817,
c2bcc92, 3d27881.

## Decisions taken while planning

- **applicationId `com.gracefultools.astrid`.** Permanent once on Play; change it before the first
  upload if another id is wanted.
- **minSdk 28 (Android 9), target 35.** AndroidKeyStore AES-GCM and the notification channel APIs
  are all present at 28; below it the share of active devices is small.
- **Compose + Material 3 components, iOS colours and spacing.** The components behave as Android
  users expect; the look follows iOS's Theme (Ocean default) — not Material You dynamic colour.
- **Sign-in through the browser hand-off first.** It is the flow the core already implements and the
  server already trusts; native Google sign-in through Credential Manager is a later polish task.
