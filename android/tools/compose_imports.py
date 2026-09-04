"""Compose helpers used without their import.

Kotlin needs an import for every one of these, and forgetting one is not a
typo the eye catches: the call reads exactly like the fifty others around it
that do compile. It has now cost a CI round trip once (Modifier.height in two
new screens), which is two minutes to be told something a grep answers.

Only symbols with exactly one plausible import are listed. Anything
ambiguous would produce false alarms, and a check that cries wolf gets
ignored, which is worse than not having it.
"""
import re
import sys
from pathlib import Path

MODIFIERS = {
    "height": "androidx.compose.foundation.layout.height",
    "width": "androidx.compose.foundation.layout.width",
    "size": "androidx.compose.foundation.layout.size",
    "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
    "fillMaxHeight": "androidx.compose.foundation.layout.fillMaxHeight",
    "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize",
    "padding": "androidx.compose.foundation.layout.padding",
    "widthIn": "androidx.compose.foundation.layout.widthIn",
    "heightIn": "androidx.compose.foundation.layout.heightIn",
    "aspectRatio": "androidx.compose.foundation.layout.aspectRatio",
    "background": "androidx.compose.foundation.background",
    "border": "androidx.compose.foundation.border",
    "clickable": "androidx.compose.foundation.clickable",
    "verticalScroll": "androidx.compose.foundation.verticalScroll",
    "horizontalScroll": "androidx.compose.foundation.horizontalScroll",
    "clip": "androidx.compose.ui.draw.clip",
    "alpha": "androidx.compose.ui.draw.alpha",
}

# Bare calls, not modifier chains.
CALLS = {
    "rememberScrollState": "androidx.compose.foundation.rememberScrollState",
    "mutableStateOf": "androidx.compose.runtime.mutableStateOf",
    "mutableIntStateOf": "androidx.compose.runtime.mutableIntStateOf",
    "mutableStateMapOf": "androidx.compose.runtime.mutableStateMapOf",
    "LaunchedEffect": "androidx.compose.runtime.LaunchedEffect",
    "produceState": "androidx.compose.runtime.produceState",
    "Spacer": "androidx.compose.foundation.layout.Spacer",
    # Both of these were used without their import in the same commit, and
    # this file did not know them: a checklist only catches what is on it,
    # so anything it misses goes on it the same day.
    "Image": "androidx.compose.foundation.Image",
    "CircularProgressIndicator": "androidx.compose.material3.CircularProgressIndicator",
    "asImageBitmap": "androidx.compose.ui.graphics.asImageBitmap",
    "rememberLauncherForActivityResult": "androidx.activity.compose.rememberLauncherForActivityResult",
}

problems = []
for path in sorted(Path(sys.argv[1]).rglob("*.kt")):
    if "/build/" in str(path):
        continue
    text = path.read_text()
    imports = set(re.findall(r"^import ([\w.]+)", text, re.M))
    body = "\n".join(l for l in text.splitlines() if not l.startswith("import "))
    for symbol, needed in MODIFIERS.items():
        if re.search(r"\.%s\(" % symbol, body) and needed not in imports:
            problems.append(f"{path}: uses .{symbol}() without importing {needed}")
    for symbol, needed in CALLS.items():
        if re.search(r"(?<![\w.]){}\(".format(symbol), body) and needed not in imports:
            problems.append(f"{path}: uses {symbol}() without importing {needed}")

for problem in problems:
    print(problem)
sys.exit(1 if problems else 0)
