# Google Play listing (draft)

The text for the Play Console listing, in Gradle Play Publisher's layout, so a publishing step
could read it without changes. Nothing here uploads anything — the Play upload is Jon's
(docs/RELEASE.md).

- `listings/en-US/` — title (≤ 30), short description (≤ 80), full description (≤ 4000)
- `release-notes/en-US/default.txt` — "What's new" for the first release (≤ 500)
- `permissions.md`, `data-safety.md` — answers for Play's declaration forms

`PlayListingTest` (app unit tests) enforces the limits. Screenshots come from the smoke test
(`/data/local/tmp/astrid-*.png`, docs/RELEASE.md); the feature graphic (1024 × 500) is still to make.
Other languages: add `listings/<locale>/` once the English is approved.
