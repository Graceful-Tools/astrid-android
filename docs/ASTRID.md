# Astrid for Android — single source of truth for AI agents

*Read by every agent working in this repo, and by anyone new to it.* [CLAUDE.md](../CLAUDE.md) and
[AGENTS.md](../AGENTS.md) are thin adapters with commands and workflow; **architecture changes here
and nowhere else.**

Read this before touching code that involves tasks, completion, repeating tasks, the Outbox, sync,
chat, list members, permissions, or any API call.

## 0. Non-negotiable rules

Carried from astrid-ios `ASTRID.md` §0 and astrid-windows `docs/ASTRID.md` §0. Each exists because
breaking it shipped a regression on another platform.

1. **No business logic in `app/`.** The shell is screens, navigation, and Android adapters. If it
   decides anything about tasks, lists, sync, permissions, ordering, dates or user-facing wording of
   a rule, it belongs in astrid-core. Rows, pickers, field order, board columns, permissions — ask
   the core.
2. **All backend writes go through the core.** The app never builds an HTTP request to astrid.cc.
   Every write is a core command (`CreateTask`, `UpdateTask`, `PostComment`, …), which journals it
   through the Outbox.
3. **Complete a task ONLY with `CompleteTask`.** Never `UpdateTask` with `completed: true` — that
   skips repeat rollover.
4. **When completing from a screen where the user edited fields first**, commit the edit
   (`CommitAllEditing`) before `CompleteTask`, so rollover anchors on what the user sees.
5. **Next-occurrence math lives ONLY in astrid-core** (`repeating`). A change starts in
   `astrid-web/types/repeating.ts`; this app takes it by bumping the core pin.
6. **Preserve offline behaviour.** A failure of kind `offline` means *it will send later* — never
   show it as an error.
7. **API paths are `/api/v1/...` only** — enforced by the core's request guard.
8. **TDD for bug fixes:** RED test naming the task id, then GREEN, then `npm run predeploy`.
9. **Reuse before you write.** User-facing strings live in `res/values*/strings.xml`; colours and
   spacing in `ui/theme`. Never inline a role comparison — permissions come from the core's
   `listAccess`.
10. **Match iOS.** Behaviour, information architecture, wording and visual design follow the iOS
    app (`astrid-ios/Astrid App`). Diverge only where Android interaction or capability requires it
    (back gesture, navigation drawer, Material date pickers, notifications, share intents, Custom
    Tabs sign-in), and record the divergence in [PARITY.md](./PARITY.md).

## 1. Why the code is shaped this way

The Mac app adds zero business logic over the iOS service layer; Windows rebuilt that layer once
in Rust (astrid-core) so it could follow the same rule; the Apple apps now run on the same core.
Android is the third client on it. One port of the shell, no second copy of the rules.

```
app/Astrid (Compose)  ──  corebridge (Kotlin facade + UniFFI bindings)  ──  core/astrid-android (Rust, UniFFI)
                                                                                 │
                                         astrid_core::rules::run_json  ──────────┤  pure rules, synchronous
                                         astrid_core::app::App::run_json ────────┘  cache, Outbox, sync, network
```

- **Two doors, one envelope.** `CoreRules.ask` answers pure rules synchronously (safe while
  composing); `CoreSession.run` runs commands against the cache and network on the core's own Tokio
  pool. Both speak JSON in the `/api/v1` wire shape inside `{"ok", "value", "error"}`. A new
  capability is a new command `kind`, not a new native symbol.
- **One change stream.** The core announces every cache change (`{"change":"task","id":…}`,
  `{"change":"synced",…}`) on one listener; `CoreEvents` turns it into a `SharedFlow` and visible
  screens re-read what moved.
- **The core is pinned by revision** in `core/Cargo.toml` and bumped deliberately. Building against
  a local checkout: `scripts/build-core.sh --core ../astrid-core`.
- **Generated Kotlin is committed** (`corebridge/src/main/java/uniffi/`) and never hand-edited; the
  gate fails if it differs from what the pinned core generates. Native `.so` files are built, not
  committed.

## 2. Platform adapters (the only Android-specific code with behaviour)

| Need | iOS | Android |
|---|---|---|
| Session at rest | Keychain | AES-GCM key in AndroidKeyStore, sealed file (`KeystoreCredentialStore`) |
| Sign-in | ASWebAuthenticationSession / native Apple, Google | Browser hand-off in a Custom Tab, `astrid://auth/callback` |
| Reachability | NWPathMonitor | `ConnectivityManager.NetworkCallback` → `NetworkRestored` |
| Background refresh | BGTask | WorkManager periodic work → `Sync` |
| Reminders | UserNotifications | NotificationManager + AlarmManager/WorkManager, actions → `CompleteTask` / `SnoozeReminder` |
| Share extension | Share extension | `ACTION_SEND` activity |
| Sidebar | Overlay sidebar (iPhone) | `ModalNavigationDrawer` |

## 3. Where things live

| Area | Path |
|---|---|
| Native crate | `core/astrid-android/` |
| Bindings + facade | `corebridge/src/main/java/` (`uniffi/` generated; `com/gracefultools/astrid/core/` hand-written) |
| App entry, DI | `app/.../AstridApplication.kt`, `AppGraph.kt` |
| Screens | `app/.../ui/<feature>/` — one `*Screen.kt` + `*ViewModel.kt` per feature |
| Theme | `app/.../ui/theme/` — colours ported from iOS `Theme.swift` |
| Strings | `app/src/main/res/values/strings.xml` |
| Brand values (server, support / inbound-task addresses, agent email domain) | `app/.../Brand.kt`, from BuildConfig fields that Gradle properties set (`-PastridBaseUrl=…`, `-PastridSupportEmail=…`, `-PastridInboundTaskEmail=…`, `-PastridAgentEmailDomain=…`); unset, hosted Astrid's. iOS `Brand.swift`. Web links are `Brand.webUrl(path)`, never a literal origin |

## 4. Change order for shared behaviour

Web first (with its tests, regenerate fixtures) → astrid-core → bump the pin here → mirror into the
other clients. Deploy web before shipping a client that depends on it.
