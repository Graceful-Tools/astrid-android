#!/usr/bin/env bash
# Self-test for scripts/port-ios-strings.py --check (AATD-96). A fixture stands in for astrid-ios.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
fail() { echo "✗ $*" >&2; exit 1; }

repo="$work/repo"; ios="$work/ios"; loc="$ios/Astrid App/Resources/Localizations"
res="$repo/app/src/main/res"
mkdir -p "$repo/scripts" "$res/values" "$loc/en.lproj" "$loc/de.lproj"
cp "$root/scripts/port-ios-strings.py" "$repo/scripts/"
cat > "$res/values/strings.xml" <<'XML'
<resources>
    <string name="app_name">Astrid</string>
    <string name="auth_tagline">Get it done!</string>
    <string name="common_cancel">Cancel</string>
    <string name="language_german" translatable="false">Deutsch</string>
</resources>
XML
echo '<resources></resources>' > "$res/values/core_keys.xml"
printf '"auth.tagline" = "Get it done!";\n"common.cancel" = "Cancel";\n' > "$loc/en.lproj/Localizable.strings"
printf '"auth.tagline" = "Erledigen Sie es!";\n"common.cancel" = "Abbrechen";\n' > "$loc/de.lproj/Localizable.strings"
port() { python3 -I -B "$repo/scripts/port-ios-strings.py" --ios "$ios" "$@"; }

port >/dev/null
de="$res/values-de/strings.xml"
grep -q 'Erledigen Sie es!' "$de" || fail "the port did not write the German translation"

# AATD-96: a port that is current passes.
out="$(port --check 2>&1)" || fail "a current port failed --check: $out"

# AATD-96: iOS moving on fails the check, names the file, and writes nothing.
printf '"auth.tagline" = "Erledige es!";\n"common.cancel" = "Abbrechen";\n' > "$loc/de.lproj/Localizable.strings"
before="$(cat "$de")"
if out="$(port --check 2>&1)"; then fail "--check passed although iOS changed a translation: $out"; fi
grep -q 'values-de/strings.xml' <<<"$out" || fail "the failure does not name the stale file: $out"
[[ "$(cat "$de")" == "$before" ]] || fail "--check rewrote the file it was checking"

# AATD-96: a string no locale translates fails, even when the ported files are current.
port >/dev/null
cat > "$res/values/strings.xml" <<'XML'
<resources>
    <string name="auth_tagline">Get it done!</string>
    <string name="common_cancel">Cancel</string>
    <string name="android_only">Only on Android</string>
</resources>
XML
port >/dev/null
if out="$(port --check 2>&1)"; then fail "--check passed with an untranslated string: $out"; fi
grep -q 'android_only' <<<"$out" || fail "the failure does not name the untranslated string: $out"

# AATD-96: a hand translation in strings_extra.xml counts, and wins over the port.
printf '<resources>\n    <string name="android_only">Nur auf Android</string>\n</resources>\n' > "$res/values-de/strings_extra.xml"
port >/dev/null
out="$(port --check 2>&1)" || fail "a hand translation in strings_extra.xml was not counted: $out"

echo "✓ port-ios-strings --check: current passes, drift and untranslated strings fail (AATD-96)"
