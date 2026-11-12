#!/usr/bin/env bash
# Write the Android brand for a deployment's profile (AATD-90) — iOS scripts/make-brand-variant.sh.
#
#   scripts/make-brand-variant.sh acme                         # ../astrid-web/brands/acme.brand.json
#   scripts/make-brand-variant.sh path/to/acme.brand.json      # any profile, no astrid-web needed
#   scripts/make-brand-variant.sh acme --out /tmp/brands/acme  # somewhere other than brands/<slug>
#
# Reads the profile astrid-web deploys from (`brands/*.brand.json`): its `env`, plus an optional
# `android` block for what only the app knows —
#
#   "android": { "applicationId": "example.acme.tasks", "appName": "Acme Tasks",
#                "handoffClientId": "acme-android", "linkHosts": "tasks.acme.example",
#                "linkAutoVerify": true, "accentHoverColor": "#9333ea", "accentTextColor": "#ffffff",
#                "versionCode": 3, "versionName": "1.2.0",
#                "play": { "title": "…", "shortDescription": "…", "fullDescription": "…", "releaseNotes": "…" } }
#
# — and writes brands/<slug>/brand.properties, its Play listing (play/, a neutral draft in the
# brand's name when there is no android.play) and a copy of the profile, so `./gradlew assembleRelease -PastridBrand=<slug>` is
# that deployment's app (brands/README.md has the keys). The applicationId otherwise comes from the
# first package in the server's BRAND_ANDROID_APP_LINKS, which is the one the server vouches for.
# A value the profile leaves out stays out, so the build uses Astrid's. A brand's res/ directory
# (icons, colours) is left alone; generating that from the profile comes next in AATD-90.
#
# Generated: re-run it after the profile changes rather than editing the file. predeploy re-runs it
# for acme and fails if the committed copy is stale. scripts/test-make-brand-variant.sh tests it.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"

profile="" out=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --out) out="$2"; shift 2 ;;
    -h|--help) sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "unknown option: $1" >&2; exit 2 ;;
    *) profile="$1"; shift ;;
  esac
done
[[ -n "$profile" ]] || { echo "usage: $0 <profile.json | name> [--out <dir>]" >&2; exit 2; }

if [[ ! -f "$profile" ]]; then
  web="${ASTRID_WEB_DIR:-$root/../astrid-web}"
  [[ -f "$web/brands/$profile.brand.json" ]] \
    || { echo "no profile $profile, and no $web/brands/$profile.brand.json — pass the profile's path" >&2; exit 1; }
  profile="$web/brands/$profile.brand.json"
fi
slug="$(basename "$profile")"; slug="${slug%.json}"; slug="${slug%.brand}"
out="${out:-$root/brands/$slug}"
mkdir -p "$out"

python3 -I - "$profile" "$out/brand.properties" "$out/play" <<'PY'
import json, os, re, sys

profile_path, target, play_dir = sys.argv[1:4]
profile = json.load(open(profile_path, encoding="utf-8"))
env = {k: str(v).strip() for k, v in (profile.get("env") or {}).items()}
raw_android = profile.get("android") or {}
play = raw_android.get("play") or {}
android = {k: (str(v).strip().lower() if isinstance(v, bool) else str(v).strip())
           for k, v in raw_android.items() if k != "play"}

def first_package(links):
    # BRAND_ANDROID_APP_LINKS: "<package>:<sha256 fingerprint>[,…]" — the fingerprint has colons too.
    for entry in links.split(","):
        package = entry.strip().split(":", 1)[0].strip()
        if package:
            return package
    return ""

domain = env.get("NEXT_PUBLIC_BRAND_DOMAIN", "").removeprefix("https://").removeprefix("http://").strip("/")
values = [
    ("astridApplicationId", android.get("applicationId") or first_package(env.get("BRAND_ANDROID_APP_LINKS", ""))),
    ("astridAppName", android.get("appName") or env.get("NEXT_PUBLIC_BRAND_NAME", "")),
    ("astridWordmark", env.get("NEXT_PUBLIC_BRAND_WORDMARK", "")),
    ("astridBaseUrl", f"https://{domain}" if domain else ""),
    ("astridLinkHosts", android.get("linkHosts", "")),
    ("astridLinkAutoVerify", android.get("linkAutoVerify", "")),
    ("astridUrlScheme", env.get("NEXT_PUBLIC_BRAND_APP_URL_SCHEME", "").lower().removesuffix("://")),
    ("astridHandoffClientId", android.get("handoffClientId", "")),
    ("astridSupportEmail", env.get("NEXT_PUBLIC_BRAND_SUPPORT_EMAIL", "")),
    ("astridInboundTaskEmail", env.get("NEXT_PUBLIC_BRAND_INBOUND_TASK_EMAIL", "")),
    ("astridAgentEmailDomain", env.get("BRAND_AGENT_EMAIL_DOMAIN", "")),
    ("astridAccentColor", env.get("NEXT_PUBLIC_BRAND_ACCENT_COLOR", "")),
    ("astridAccentHoverColor", android.get("accentHoverColor", "")),
    ("astridAccentTextColor", android.get("accentTextColor", "")),
    ("astridVersionCode", android.get("versionCode", "")),
    ("astridVersionName", android.get("versionName", "")),
]

if values[0][1] and not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+", values[0][1]):
    sys.exit(f"not an Android application id: {values[0][1]!r}")
scheme = dict(values)["astridUrlScheme"]
if scheme and not re.fullmatch(r"[a-z][a-z0-9+.-]*", scheme):
    sys.exit(f"not a URL scheme: {scheme!r}")

code = dict(values)["astridVersionCode"]
if code and not (re.fullmatch(r"[0-9]+", code) and 1 <= int(code) <= 2100000000):
    sys.exit(f"versionCode must be a whole number from 1 to 2100000000 (Play's limit): {code!r}")

def escape(value):  # java.util.Properties: backslash, and a leading space would be dropped
    return value.replace("\\", "\\\\").replace("\n", "\\n")

lines = [
    f"# Generated by scripts/make-brand-variant.sh from {os.path.basename(profile_path)} (AATD-90).",
    "# Re-run it after the profile changes; do not edit. Keys: brands/README.md.",
]
lines += [f"{key}={escape(value)}" for key, value in values if value]
open(target, "w", encoding="utf-8").write("\n".join(lines) + "\n")

# The Play listing, in app/src/main/play's layout (Gradle Play Publisher's). From android.play when
# the profile has one; otherwise a neutral draft in the brand's own name, to edit in the profile.
values = dict(values)
name = values["astridAppName"] or "Tasks"
site = domain or "your server"
draft = {
    "title": name,
    "shortDescription": "Shared to-do lists for families and teams, with reminders and AI agents.",
    "fullDescription": "\n".join([
        f"{name} is a to-do app for the people you share your life and work with. Families and teams "
        "keep shared lists, see who is doing what, and talk it over right beside the tasks.",
        "",
        "• My Tasks gathers everything that is yours, from every list",
        "• Due dates, priorities, repeating tasks and subtasks",
        "• Shared lists with comments, a chat for each list, and assignees",
        "• Assign a task to an AI agent and follow its progress in the comments",
        "• Reminders with Complete and Snooze right on the notification",
        "• Works offline and syncs when you are back online",
        "",
        f"Your tasks sync with {site}.",
    ] + ([f"Privacy policy: https://{domain}/privacy"] if domain else [])),
    "releaseNotes": f"The first {name} for Android.",
}
# Play Console's limits, as PlayListingTest holds Astrid's to.
files = [("title", "listings/en-US/title.txt", 30),
         ("shortDescription", "listings/en-US/short-description.txt", 80),
         ("fullDescription", "listings/en-US/full-description.txt", 4000),
         ("releaseNotes", "release-notes/en-US/default.txt", 500)]
for key, path, limit in files:
    text = str(play.get(key) or draft[key]).strip()
    if len(text) > limit:
        sys.exit(f"android.play.{key} is {len(text)} characters; Play allows {limit}")
    os.makedirs(os.path.dirname(os.path.join(play_dir, path)), exist_ok=True)
    open(os.path.join(play_dir, path), "w", encoding="utf-8").write(text + "\n")
PY
# The profile beside it: the audit reads its expect.forbidLiterals in CI, which has no astrid-web.
[[ "$(cd "$(dirname "$profile")" && pwd)/$(basename "$profile")" -ef "$out/profile.brand.json" ]] \
  || cp "$profile" "$out/profile.brand.json"
echo "✓ ${out#"$root"/}/brand.properties from $(basename "$profile")"
