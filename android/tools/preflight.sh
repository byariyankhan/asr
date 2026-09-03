#!/usr/bin/env bash
# Everything about the Android module that can be checked without an Android
# SDK, which is the whole of what the development sandbox can do: it has no
# SDK and cannot reach dl.google.com, so the CI job is the compiler of record
# and a push is the only way to find out whether the code builds.
#
# That round trip costs a couple of minutes, and two of them have now been
# spent on faults a parser would have caught in milliseconds: a duplicate key
# in the version catalog, and a "--" inside an XML comment. Neither is a
# Kotlin error, so neither showed up as one; both stopped the build before
# the compiler ran.
#
# Run this before every push that touches android/.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
failed=0

fail() { echo "FAIL  $*" >&2; failed=1; }
pass() { echo "ok    $*"; }

# The version catalog. TOML forbids duplicate keys, and Gradle rejects the
# whole catalog when it finds one.
if python3 -c "
import sys, tomllib
tomllib.load(open('$here/gradle/libs.versions.toml','rb'))
" 2>/tmp/preflight.err; then
  pass "gradle/libs.versions.toml parses"
else
  fail "gradle/libs.versions.toml: $(cat /tmp/preflight.err)"
fi

# Every manifest in the module. XML comments cannot contain "--", which is
# easy to write and invisible until the manifest merger refuses it.
while IFS= read -r manifest; do
  if python3 -c "
import sys, xml.dom.minidom
xml.dom.minidom.parse('$manifest')
" 2>/tmp/preflight.err; then
    pass "${manifest#"$here/"} parses"
  else
    fail "${manifest#"$here/"}: $(cat /tmp/preflight.err)"
  fi
done < <(find "$here" -name "AndroidManifest.xml" -not -path "*/build/*")

# Layouts, themes, strings and the rest.
while IFS= read -r res; do
  if ! python3 -c "
import sys, xml.dom.minidom
xml.dom.minidom.parse('$res')
" 2>/tmp/preflight.err; then
    fail "${res#"$here/"}: $(cat /tmp/preflight.err)"
  fi
done < <(find "$here" -path "*/src/*/res/*" -name "*.xml" -not -path "*/build/*")

# Every screen must be reachable. A Composable nobody calls compiles fine and
# ships as nothing at all, which is exactly the sort of thing that gets
# noticed a week later.
while IFS= read -r screen; do
  name="$(basename "$screen" .kt)"
  callers=$(grep -rl "\b$name(" "$here/app/src/main" --include="*.kt" | grep -v "/$name.kt$" || true)
  if [ -z "$callers" ]; then
    echo "warn  $name is not called from anywhere in main"
  fi
done < <(find "$here/app/src/main" -path "*/ui/screens/*Screen.kt")

if [ "$failed" -ne 0 ]; then
  echo
  echo "Preflight failed. Fix the above before pushing: CI would spend two"
  echo "minutes arriving at the same answer."
  exit 1
fi

echo
echo "Preflight clean. This proves nothing about whether the Kotlin compiles;"
echo "only the CI job can say that."
