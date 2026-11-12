#!/usr/bin/env bash
# Self-test for scripts/make-brand-variant.sh and scripts/check-brand-literals.py (AATD-90). Needs no
# astrid-web checkout and no Gradle: it feeds both a profile and resources it writes itself.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
fail() { echo "✗ $*" >&2; exit 1; }
has() { grep -qxF "$2" "$1" || fail "$1 lacks: $2"; }
lacks() { ! grep -q "^$2=" "$1" || fail "$1 should not set $2"; }

# --- make-brand-variant: env, then the android block over it -------------------------------------
cat > "$work/acme.brand.json" <<'JSON'
{ "env": {
    "NEXT_PUBLIC_BRAND_NAME": "Acme", "NEXT_PUBLIC_BRAND_WORDMARK": "ACME",
    "NEXT_PUBLIC_BRAND_DOMAIN": "tasks.acme.example", "NEXT_PUBLIC_BRAND_APP_URL_SCHEME": "AcmeTasks",
    "NEXT_PUBLIC_BRAND_SUPPORT_EMAIL": "helpdesk@acme.example", "BRAND_AGENT_EMAIL_DOMAIN": "agents.acme.example",
    "NEXT_PUBLIC_BRAND_ACCENT_COLOR": "#a855f7", "NEXT_PUBLIC_BRAND_INBOUND_TASK_EMAIL": "",
    "BRAND_ANDROID_APP_LINKS": "example.acme.tasks:AC:AC:AC,example.acme.beta:BE:BE" },
  "expect": { "forbidLiterals": ["Astrid", "astrid.cc"] } }
JSON
"$root/scripts/make-brand-variant.sh" "$work/acme.brand.json" --out "$work/out" >/dev/null
p="$work/out/brand.properties"
has "$p" "astridApplicationId=example.acme.tasks"   # the first package the server vouches for
has "$p" "astridAppName=Acme"
has "$p" "astridWordmark=ACME"
has "$p" "astridBaseUrl=https://tasks.acme.example"
has "$p" "astridUrlScheme=acmetasks"                 # schemes are lowercase, as Gradle requires
has "$p" "astridSupportEmail=helpdesk@acme.example"
has "$p" "astridAgentEmailDomain=agents.acme.example"
has "$p" "astridAccentColor=#a855f7"
lacks "$p" astridInboundTaskEmail                    # blank stays out: the build uses Astrid's
lacks "$p" astridHandoffClientId

python3 -I - "$work/acme.brand.json" <<'PY'
import json, sys
p = json.load(open(sys.argv[1]))
p["android"] = {"applicationId": "com.acme.android", "appName": "Acme Tasks", "handoffClientId": "acme-android",
                "linkAutoVerify": True}
json.dump(p, open(sys.argv[1], "w"))
PY
"$root/scripts/make-brand-variant.sh" "$work/acme.brand.json" --out "$work/out" >/dev/null
has "$p" "astridApplicationId=com.acme.android"
has "$p" "astridAppName=Acme Tasks"
has "$p" "astridHandoffClientId=acme-android"
has "$p" "astridLinkAutoVerify=true"

# --- versioning and the Play listing (AATD-90, second slice) ------------------------------------
lacks "$p" astridVersionCode                         # no android.versionCode: Astrid's default
pl="$work/out/play"
[[ -s "$pl/listings/en-US/title.txt" ]] || fail "no Play listing written without an android.play block"
has "$pl/listings/en-US/title.txt" "Acme Tasks"      # the draft takes the app's name …
grep -qF "tasks.acme.example" "$pl/listings/en-US/full-description.txt" || fail "the draft lacks the brand's domain"
for f in listings/en-US/short-description.txt release-notes/en-US/default.txt; do
  [[ -s "$pl/$f" ]] || fail "the draft has no $f"
done
! grep -rqi "astrid" "$pl" || fail "the generated listing names Astrid"

python3 -I - "$work/acme.brand.json" <<'PY2'
import json, sys
p = json.load(open(sys.argv[1]))
p["android"].update({"versionCode": 42, "versionName": "4.2.0", "play": {
    "title": "Acme Tasks: Team To-dos", "shortDescription": "Acme's lists.",
    "fullDescription": "Line one.\nLine two.", "releaseNotes": "First Acme release."}})
json.dump(p, open(sys.argv[1], "w"))
PY2
"$root/scripts/make-brand-variant.sh" "$work/acme.brand.json" --out "$work/out" >/dev/null
has "$p" "astridVersionCode=42"
has "$p" "astridVersionName=4.2.0"
has "$pl/listings/en-US/title.txt" "Acme Tasks: Team To-dos"
has "$pl/listings/en-US/short-description.txt" "Acme's lists."
has "$pl/listings/en-US/full-description.txt" "Line two."
has "$pl/release-notes/en-US/default.txt" "First Acme release."

bad() {  # a profile the script must refuse: $1 a Python edit of p["android"], $2 why
  cp "$work/acme.brand.json" "$work/bad.brand.json"
  python3 -I -c "import json,sys; p=json.load(open(sys.argv[1])); a=p['android']; $1; json.dump(p, open(sys.argv[1],'w'))" "$work/bad.brand.json"
  ! "$root/scripts/make-brand-variant.sh" "$work/bad.brand.json" --out "$work/bad" 2>/dev/null || fail "$2"
}
bad "a['play']['title']='x'*31" "a title over Play's 30 characters was accepted"
bad "a['play']['shortDescription']='x'*81" "a short description over 80 was accepted"
bad "a['versionCode']='1.5'" "a versionCode that is not a whole number was accepted"
bad "a['versionCode']=0" "versionCode 0 was accepted"

echo '{"env":{"BRAND_ANDROID_APP_LINKS":"not a package:AA"}}' > "$work/bad.brand.json"
! "$root/scripts/make-brand-variant.sh" "$work/bad.brand.json" --out "$work/bad" 2>/dev/null \
  || fail "a malformed application id was accepted"

# --- check-brand-literals: text is checked, names are not, the budget is exact -------------------
res="$work/res"; mkdir -p "$res/values" "$res/values-de"
cat > "$res/values/values.xml" <<'XML'
<resources>
  <string name="review_loving_title">Loving %1$s?</string>
  <string name="astrid_named_resource">Fine</string>
  <plurals name="tasks"><item quantity="one">%d task</item></plurals>
</resources>
XML
cat > "$res/values-de/values-de.xml" <<'XML'
<resources><string name="x">Fine</string></resources>
XML
audit() { python3 -I "$root/scripts/check-brand-literals.py" "$work/acme.brand.json" "$res" "$@" >/dev/null 2>&1; }
audit || fail "a clean build failed the audit (a resource NAME is not text)"

# Plant one in a translation and one in a plural: both must fire.
cat > "$res/values-de/values-de.xml" <<'XML'
<resources><string name="x">Gefällt Ihnen Astrid?</string>
<plurals name="p"><item quantity="other">see ASTRID.CC</item></plurals></resources>
XML
! audit || fail "a planted literal did not fail the audit"
audit --budget 2 || fail "two literals did not meet a budget of two"
! audit --budget 3 || fail "under the budget should fail, so the budget gets lowered"
! audit --budget 1 || fail "over the budget passed"

# The listing is text a person is shown too: --text scans its .txt files.
txt="$work/play"; mkdir -p "$txt/listings/en-US"; echo "Acme Tasks" > "$txt/listings/en-US/title.txt"
audit --budget 2 --text "$txt" || fail "a clean listing changed the count"
echo "Synced with astrid.cc" > "$txt/listings/en-US/full-description.txt"
! audit --budget 2 --text "$txt" || fail "a listing naming Astrid did not count"
audit --budget 3 --text "$txt" || fail "the listing's one hit did not make three"

echo "✓ make-brand-variant and the brand literal audit"
