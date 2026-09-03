#!/usr/bin/env python3
"""Refuse a single quote inside an inline single-quoted ssh argument.

vps-ops.yml runs some remote scripts as `ssh host '<whole script>'`. One
single quote anywhere inside that argument — in a command, or even in a
comment — closes it early, and everything after it stops being a script
and becomes shell words. The failure is silent: ssh still runs, the step
still exits 0, and the rest of the script appears in the log as text.

That happened once and the run reported success, so this is a check
rather than a comment. Steps written with a `<<'REMOTE'` heredoc are
exempt: quoting is not a hazard there.
"""
import sys
import yaml

MARKER = 'root@"$VPS_HOST" \''
failures = []

for path in sys.argv[1:]:
    workflow = yaml.safe_load(open(path, encoding="utf-8"))
    for job in workflow.get("jobs", {}).values():
        for step in job.get("steps", []):
            run = step.get("run")
            if not run or "<<'REMOTE'" in run or MARKER not in run:
                continue
            rest = run.split(MARKER, 1)[1].split("\n")
            # The argument closes on the first line whose content starts
            # with the quote (`'` alone, or `'; do` in the retry loop).
            # Anything after that is the runner's own shell, where an
            # apostrophe in a comment is harmless.
            body = []
            for line in rest:
                if line.lstrip().startswith("'"):
                    break
                body.append(line)
            for number, line in enumerate(body, 1):
                if "'" in line:
                    failures.append(f"{path}: {step.get('name')}: line {number}: {line.strip()[:110]}")

if failures:
    print("Single quote inside an inline ssh argument — it ends the argument early:")
    print("\n".join(failures))
    sys.exit(1)

print("ssh quoting: no single quotes inside any inline ssh argument")
