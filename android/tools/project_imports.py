"""Types this project declares, used in a file that never imported them.

The Compose check next door knows a fixed list of framework symbols. This
one knows nothing in advance: it reads every declaration under the source
root, and then flags a file that uses one of those names while it is neither
declared in that file's own package nor imported.

That narrowness is the point. It only ever considers names this repository
itself declares, so the standard library, AndroidX and every framework
symbol are invisible to it and cannot produce a false alarm. What it catches
is the one mistake that reads exactly like working code: `UsageSnapshot` in
a signature, in a file whose imports stop at `UsageReader`.

Comments and string literals are stripped first, because prose is full of
capitalised words and every one of them would otherwise be a finding.
"""

import re
import sys
from pathlib import Path

# Top level only: no leading whitespace. A name nested inside another
# declaration -- `Ok` inside `sealed interface ApiResult` -- is reached
# through its parent and is not a thing a file imports on its own.
DECL = re.compile(
    r"^(?:@\w+\s+)*(?:public |internal |private |abstract |open |sealed |data |value |enum |annotation |inline )*"
    r"(?:class|interface|object)\s+([A-Z]\w*)",
    re.M,
)
IMPORT = re.compile(r"^import\s+([\w.]+)(?:\s+as\s+(\w+))?", re.M)

# Anything a file names for itself, at any depth: a nested class, an enum
# entry, a colour on an object. These shadow a same-named declaration in
# another package, and a file that resolves the name locally is not missing
# an import for it.
LOCAL = re.compile(
    r"^\s*(?:@\w+\s+)*(?:\w+ )*(?:class|interface|object|val|var)\s+([A-Z]\w*)|^\s{4,}([A-Z]\w*)\s*(?:,|;|$)",
    re.M,
)
PACKAGE = re.compile(r"^package\s+([\w.]+)", re.M)
# Bare uses only. `AsrColors.Breach` is a member of something already
# imported, not a use of the `Breach` this project also declares elsewhere,
# and treating the two as the same name is most of what a naive scan gets
# wrong.
WORD = re.compile(r"(?<![.\w])[A-Z]\w*\b")


def strip(source: str) -> str:
    """Source with comments and string literals blanked out."""
    out, i, n = [], 0, len(source)
    while i < n:
        two = source[i : i + 2]
        if source[i : i + 3] == '"""':
            end = source.find('"""', i + 3)
            i = n if end < 0 else end + 3
        elif two == "//":
            end = source.find("\n", i)
            i = n if end < 0 else end
        elif two == "/*":
            end = source.find("*/", i + 2)
            i = n if end < 0 else end + 2
        elif source[i] == '"':
            i += 1
            while i < n and source[i] != '"':
                i += 2 if source[i] == "\\" else 1
            i += 1
        else:
            out.append(source[i])
            i += 1
    return "".join(out)


def main(root: str) -> int:
    files = sorted(Path(root).rglob("*.kt"))
    declared: dict[str, set[str]] = {}
    for path in files:
        text = path.read_text()
        package = PACKAGE.search(text)
        if not package:
            continue
        for name in DECL.findall(text):
            declared.setdefault(name, set()).add(package.group(1))

    problems = []
    for path in files:
        text = path.read_text()
        package = PACKAGE.search(text)
        if not package:
            continue
        here = package.group(1)
        imported = set()
        for full, alias in IMPORT.findall(text):
            imported.add(alias or full.rsplit(".", 1)[-1])
            if full.endswith(".*"):
                imported.add("*" + full[:-2])
        wildcards = {w[1:] for w in imported if w.startswith("*")}
        body = strip(text)
        local = {a or b for a, b in LOCAL.findall(body)}
        for name in set(WORD.findall(body)):
            homes = declared.get(name)
            if not homes or name in imported or name in local:
                continue
            if here in homes or homes & wildcards:
                continue
            problems.append(
                f"{path}: uses {name}, declared in {sorted(homes)[0]}, without importing it"
            )

    for problem in sorted(problems):
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
