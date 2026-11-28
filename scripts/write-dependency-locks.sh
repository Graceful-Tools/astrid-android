#!/usr/bin/env bash
# Rewrites the dependency locks and checksums (ADEV-352) after any change to a version in
# gradle/libs.versions.toml, a plugin, or a project's dependencies. Commit what it changes in the
# same commit as the version change. docs/DEPENDENCIES.md has the whole procedure.
#
#   scripts/write-dependency-locks.sh
#
# 1. Every project's gradle.lockfile and the plugins' buildscript-gradle.lockfile, from scratch.
# 2. gradle/verification-metadata.xml, from scratch, so versions no longer used drop out. The
#    build's own tasks run too: lint and aapt2 resolve tools that no configuration declares.
# 3. aapt2's jar for every OS: Gradle records only the one this machine downloaded, and CI runs Linux.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f "$HOME/dev-tools/android-env.sh" ]] && source "$HOME/dev-tools/android-env.sh"

projects="$(grep -o 'include(.*)' settings.gradle.kts | grep -o '":[a-z0-9-]*"' | tr -d '"')"
deps=(buildEnvironment dependencies)
for p in $projects; do deps+=("$p:dependencies"); done

# Removed first: the old checksums would refuse whatever the version change brought in.
meta=gradle/verification-metadata.xml
rm -f "$meta"

echo "── lockfiles ──"
./gradlew --console=plain -q --write-locks "${deps[@]}" >/dev/null

echo "── checksums ──"
# Native libraries are not needed to resolve anything, and unit tests are not run: only downloads matter.
./gradlew --console=plain -q --write-verification-metadata sha256 "${deps[@]}" \
  lintDebug assembleDebug assembleDebugAndroidTest compileDebugUnitTestKotlin :baselineprofile:assemble >/dev/null

echo "── aapt2 for every OS ──"
python3 -I - "$meta" <<'PY'
import hashlib, re, sys, urllib.request
path = sys.argv[1]
s = open(path).read()
origin = "Downloaded from Google Maven by scripts/write-dependency-locks.sh"
for v in sorted(set(re.findall(r'name="aapt2" version="([^"]+)"', s))):
    for os_ in ("linux", "osx", "windows"):
        name = f"aapt2-{v}-{os_}.jar"
        if f'"{name}"' in s:
            continue
        url = f"https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/{v}/{name}"
        digest = hashlib.sha256(urllib.request.urlopen(url, timeout=120).read()).hexdigest()
        entry = (f'         <artifact name="{name}">\n'
                 f'            <sha256 value="{digest}" origin="{origin}"/>\n'
                 f'         </artifact>\n')
        start = s.index(f'<component group="com.android.tools.build" name="aapt2" version="{v}">')
        start = s.index("\n", start) + 1
        s = s[:start] + entry + s[start:]
        print(f"added {name}")
open(path, "w").write(s)
PY

scripts/test-dependency-locks.sh
git status --short -- '*.lockfile' "$meta"
