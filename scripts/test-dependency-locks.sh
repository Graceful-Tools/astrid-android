#!/usr/bin/env bash
# ADEV-352: every resolved version is locked and every artifact's checksum is pinned, so a
# transitive version cannot change without a commit, and the Socket gate in astrid-web sees every
# version the build uses. Reads files only: no Gradle run, so it costs nothing in predeploy.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
fail() { echo "✗ $*" >&2; exit 1; }

# ADEV-352: locking is on for every project's configurations, and for the plugins' classpath.
grep -q 'lockAllConfigurations()' build.gradle.kts || fail "build.gradle.kts does not lock every configuration"
grep -A2 'configurations.classpath' build.gradle.kts | grep -q 'resolutionStrategy.activateDependencyLocking()' \
  || fail "build.gradle.kts does not lock the plugins' buildscript classpath"

# ADEV-352: each included project has its lockfile, and the plugins have theirs.
[[ -s buildscript-gradle.lockfile ]] || fail "buildscript-gradle.lockfile is missing"
projects="$(grep -o 'include(.*)' settings.gradle.kts | grep -o '":[a-z0-9-]*"' | tr -d '":')"
[[ -n "$projects" ]] || fail "found no included projects in settings.gradle.kts"
for p in $projects; do
  grep -q '=' "$p/gradle.lockfile" 2>/dev/null || fail "$p/gradle.lockfile is missing: scripts/write-dependency-locks.sh"
done

# ADEV-352: checksums are verified, not just recorded.
meta=gradle/verification-metadata.xml
[[ -f "$meta" ]] || fail "$meta is missing: scripts/write-dependency-locks.sh"
grep -q '<verify-metadata>true</verify-metadata>' "$meta" || fail "$meta does not verify metadata"
grep -q '<sha256 ' "$meta" || fail "$meta pins no sha256 checksums"

# ADEV-352: aapt2 is one jar per OS, and Gradle records only the one the generating machine
# downloads. Without the Linux one CI fails verification; without macOS the dev Mac does.
for v in $(grep -o 'name="aapt2" version="[^"]*"' "$meta" | sed 's/.*version="//; s/"//'); do
  for os in linux osx windows; do
    grep -q "aapt2-$v-$os.jar" "$meta" || fail "$meta has no aapt2 $v checksum for $os: scripts/write-dependency-locks.sh adds it"
  done
done

# ADEV-352: the build's own Bouncy Castle is held at a version Socket accepts (AGP 9.5.1 brings
# 1.80.2, critical CVE). No lockfile may pin an older one.
floor="$(sed -n 's/^astrid.bouncyCastle=//p' gradle.properties)"
[[ -n "$floor" ]] || fail "gradle.properties does not set astrid.bouncyCastle"
if grep -h '^org.bouncycastle:' -- *gradle.lockfile */gradle.lockfile | grep -v ":$floor=" | grep -q .; then
  fail "a lockfile pins a Bouncy Castle other than $floor"
fi

echo "✓ dependency locks and checksums (ADEV-352)"
