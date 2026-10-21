# Data safety — draft answers for Play Console

Draft (AATD-19); Jon checks these against astrid.cc/privacy before submitting.

- **Collected:** account email address and name, profile photo (optional), and what the user
  writes — tasks, lists, comments, chat messages — plus files and photos they attach.
- **Purpose:** app functionality and account management only.
- **Shared with third parties:** none, except AI agents and connected services (GitHub, Google Tasks)
  the user connects and assigns work to, which Play counts as user-initiated transfers.
- **Not collected:** location, contacts, device identifiers, advertising ID, analytics, crash
  reports — the app has no analytics or advertising SDK.
- **Encrypted in transit:** yes (HTTPS only).
- **Deletion:** in the app (Account & Access → Delete account) and on astrid.cc.
- **Ads:** none. **Target audience:** for Jon to choose (suggested 13+, not designed for children).

**For Jon to reconcile:** astrid.cc/privacy lists "usage data to improve the service". If the
server keeps usage data for the app's requests, Play wants *App activity → App interactions*
declared as collected (analytics); the app itself sends nothing beyond its API calls.
