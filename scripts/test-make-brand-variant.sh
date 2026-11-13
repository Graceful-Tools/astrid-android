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
lacks "$p" astridAccentInkColor                      # no android.accentInk: the app derives it
python3 -I -c "import json,sys; p=json.load(open(sys.argv[1])); p['android'].update(accentInk='#6B21A8', accentInkDark='#d8b4fe'); json.dump(p, open(sys.argv[1],'w'))" "$work/acme.brand.json"
"$root/scripts/make-brand-variant.sh" "$work/acme.brand.json" --out "$work/out" >/dev/null
has "$p" "astridAccentInkColor=#6b21a8"              # AATD-90: the accent as text, light and dark
has "$p" "astridAccentInkDarkColor=#d8b4fe"
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
bad "a['accentInk']='purple'" "a malformed accent ink was accepted"
bad "a['accentInk']='#f3e8ff'" "an accent ink under 4.5:1 on a light card was accepted"
bad "a['accentInkDark']='#3b0764'" "a dark-theme accent ink under 4.5:1 on a dark card was accepted"

echo '{"env":{"BRAND_ANDROID_APP_LINKS":"not a package:AA"}}' > "$work/bad.brand.json"
! "$root/scripts/make-brand-variant.sh" "$work/bad.brand.json" --out "$work/bad" 2>/dev/null \
  || fail "a malformed application id was accepted"

# --- artwork from one source (AATD-90, third slice) ---------------------------------------------
if command -v sips >/dev/null || command -v magick >/dev/null || command -v convert >/dev/null; then
  png() {  # $1 path, $2 size, $3 r g b a: a solid square PNG, no image library needed
    python3 -I - "$@" <<'PNG'
import struct, sys, zlib
path, size, rgba = sys.argv[1], int(sys.argv[2]), bytes(int(v) for v in sys.argv[3:7])
row = b"\x00" + rgba * size
chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d))
open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
                       + chunk(b"IDAT", zlib.compress(row * size)) + chunk(b"IEND", b""))
PNG
  }
  px() { python3 -I -c 'import struct,sys; d=open(sys.argv[1],"rb").read(24); print(*struct.unpack(">II", d[16:24]))' "$1"; }
  png "$work/icon.png" 1024 168 85 247 255
  png "$work/mono.png" 1024 255 255 255 200
  png "$work/mascot.png" 800 10 20 30 255
  python3 -I - "$work/acme.brand.json" <<'PY3'
import json, sys
p = json.load(open(sys.argv[1]))
p["android"].update({"appIcon": "icon.png", "monochromeIcon": "mono.png", "characterImage": "mascot.png",
                     "iconBackground": "#112233"})
json.dump(p, open(sys.argv[1], "w"))
PY3
  "$root/scripts/make-brand-variant.sh" "$work/acme.brand.json" --out "$work/out" >/dev/null
  r="$work/out/res"
  for d in mdpi:48:108:24 hdpi:72:162:36 xhdpi:96:216:48 xxhdpi:144:324:72 xxxhdpi:192:432:96; do
    IFS=: read -r dpi legacy fg note <<<"$d"
    [[ "$(px "$r/mipmap-$dpi/ic_launcher.png")" == "$legacy $legacy" ]] || fail "mipmap-$dpi/ic_launcher.png is not ${legacy}px"
    [[ "$(px "$r/mipmap-$dpi/ic_launcher_foreground.png")" == "$fg $fg" ]] || fail "mipmap-$dpi foreground is not ${fg}px"
    [[ "$(px "$r/mipmap-$dpi/ic_launcher_monochrome.png")" == "$fg $fg" ]] || fail "mipmap-$dpi monochrome is not ${fg}px"
    [[ "$(px "$r/drawable-$dpi/ic_notification.png")" == "$note $note" ]] || fail "drawable-$dpi/ic_notification.png is not ${note}px"
  done
  [[ "$(px "$r/drawable-nodpi/astrid_character.png")" == "512 512" ]] || fail "the character art is not 512px"
  grep -qF '<color name="ic_launcher_background">#FF112233</color>' "$r/values/brand_icon.xml" \
    || fail "the icon background is not android.iconBackground"
  grep -qF '<monochrome' "$r/mipmap-anydpi-v26/ic_launcher.xml" || fail "a monochrome source did not make a themed icon"

  # No silhouette: no themed layer (never Astrid's), and the notification icon is left to the app.
  python3 -I -c 'import json,sys; p=json.load(open(sys.argv[1])); del p["android"]["monochromeIcon"]; del p["android"]["iconBackground"]; json.dump(p,open(sys.argv[1],"w"))' "$work/acme.brand.json"
  "$root/scripts/make-brand-variant.sh" "$work/acme.brand.json" --out "$work/out" >/dev/null 2>&1
  ! grep -qF '<monochrome' "$r/mipmap-anydpi-v26/ic_launcher.xml" || fail "no silhouette still declared a themed icon"
  [[ ! -e "$r/mipmap-mdpi/ic_launcher_monochrome.png" && ! -e "$r/drawable-mdpi/ic_notification.png" ]] \
    || fail "a dropped silhouette left its icons behind"
  grep -qF '#FFA855F7' "$r/values/brand_icon.xml" || fail "the icon background did not default to the accent"

  # Dropping the art entirely goes back to Astrid's: nothing generated is left.
  python3 -I -c 'import json,sys; p=json.load(open(sys.argv[1])); [p["android"].pop(k) for k in ("appIcon","characterImage")]; json.dump(p,open(sys.argv[1],"w"))' "$work/acme.brand.json"
  "$root/scripts/make-brand-variant.sh" "$work/acme.brand.json" --out "$work/out" >/dev/null
  [[ -z "$(find "$r" -type f 2>/dev/null)" ]] || fail "dropping the art left: $(find "$r" -type f | head -3)"

  png "$work/small.png" 512 1 2 3 255
  bad "a['appIcon']='$work/small.png'" "an app icon that is not 1024x1024 was accepted"
  bad "a['characterImage']='$work/nope.png'" "a missing character image was accepted"
else
  echo "  (no sips or ImageMagick here: the artwork checks are skipped)"
fi

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
