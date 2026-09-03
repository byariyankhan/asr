# Working agreement

More than one agent works in this repository, on more than one machine.
Everything below exists because two agents editing the same files is how
work gets silently lost — it already happened here once, with 73 edited
files that had to be thrown away because nobody could tell what was in
them.

Read this before touching anything.

## Who owns what

| Directory | Owner | Notes |
|---|---|---|
| `android/` | Claude | The app. Being built from Figma, screen by screen. |
| `backend/` | Claude | Live in production at `https://api.joinasr.io`. |
| `infra/`, `.github/` | Claude | Deploy, nginx, TLS, the VPS. See the warning below. |
| `web/` | whoever is assigned it | The `joinasr.io` marketing site. |
| `docs/` | shared | Say in the commit which file you changed and why. |

If a directory is not yours, do not edit it — not even a one-line fix, not
even an obvious one. Open the question instead. A change that looks
obvious from inside one folder is exactly the kind that breaks another.

## Rules

1. **Never commit to `master`.** Branch first: `git checkout -b <name>/<topic>`.
2. **One folder, one agent, one branch.** Do not start work in a folder
   somebody else has open.
3. **Nothing merges until CI is green.** For `android/` that is not a
   formality: the app cannot be compiled in some of the environments used
   here at all, so GitHub Actions is the only proof the code builds.
4. **Never commit a secret.** `.env` is not in git and never will be. Keys,
   tokens and passwords live in `/opt/asr/.env` on the server and in GitHub
   repository secrets. `android/app/google-services.json` is the one
   deliberate exception — Google ships it inside every APK; it is not a
   secret.
5. **Push your work before you stop.** An uncommitted tree on one laptop is
   invisible to everyone else and cannot be reviewed, kept, or recovered.

## Do not touch the server

`infra/` and `.github/workflows/` deploy to a VPS that also runs a second,
unrelated production site. A careless change there has already taken one
certificate into the wrong nginx file. Nothing in those directories is
worth a guess. If your task seems to need a change there, stop and say so.

## Ask before critical work

The founder decides these, not the agent. Ask first, in one message, and
wait -- do not start and report afterwards. This exists because the avatar
work was built end to end off a one-line "yes" to storing photos, and the
consequential decisions inside it (private bucket, hand-rolled signing,
JPEG only, and an edit to the account-deletion path) were never put to
anybody.

Critical means any of:

- **Production data or the server.** A migration, anything in the
  deletion or purge path, nginx, the deploy, the VPS.
- **A new third-party service, credential, or recurring cost.**
- **What leaves the phone, or who can see it.** Any change to what is
  stored, retained, or made visible to another person.
- **Anything hard to undo.** Deletes, key rotation, a published package
  name, a schema change that drops a column.

Not critical, get on with it: screens built from the Figma file, tests,
documentation, refactoring inside a directory you own, fixing a build.

Where a task turns out to need a critical decision halfway through, stop
at that point and ask. Finishing the parts that do not depend on the
answer first is fine and preferred.

## What good looks like

- Small commits that each do one thing, with a message that says **why**,
  not what — the diff already says what.
- Anything you claim works, you have run. "It should work" is not a report.
- If you could not finish something, say which part and why, plainly.
