# Figma screens

File `upZH3FVJURpJJXVF4SwLkF`, page `0:1`. 37 screens, every one 393x852.

The node id is the address — it is what `get_design_context` and
`get_screenshot` take, and recovering it means dumping 125k characters of
file metadata, so it is written down once, here. The names are verbatim
from the file; rename a frame in Figma and this table is what goes stale,
not the code.

Grouped by the order they get built, which is not their numbering: the
loop a person lives in daily (13, 20-24) matters more than the account
screens, and nothing can be tried at all until 01-02 exist.

## Onboarding and authentication

| # | Screen | Node |
|---|---|---|
| 01 | Onboarding / Welcome | `2:2` |
| 02 | Auth / Sign Up | `32:2` |
| 32 | Auth / Log In | `37:19` |
| 33 | Auth / Forgot Password | `160:2` |
| 34 | Auth / Check Email | `160:13` |
| 35 | Auth / Reset Password | `160:26` |

## Profile and setup

| # | Screen | Node |
|---|---|---|
| 03 | Profile Setup / About You | `44:2` |
| 04 | Setup / Challenge Duration | `40:2` |
| 05 | Setup / Usage Access | `119:2` |
| 06 | Setup / Choose Apps | `55:2` |
| 07 | Setup / Set Daily Limits | `60:2` |
| 08 | Setup / Add Witnesses | `67:25` |
| 09 | Setup / Protection Access | `119:29` |
| 10 | Permission / App Blocking Disclosure | `119:55` |
| 11 | Review / Start Challenge | `124:2` |
| 12 | Challenge / Started | `125:2` |

## The daily loop

| # | Screen | Node |
|---|---|---|
| 13 | Dashboard / Home | `76:2` |
| 20 | Blocked App / Limit Reached | `128:2` |
| 21 | Earn Time / Choose Activity | `131:2` |
| 22 | Permission / Activity Tracking — First Walk | `119:73` |
| 23 | Earn Time / Activity Progress — Walk | `133:2` |
| 24 | Earn Time / Completed | `135:2` |

## Progress and accountability

| # | Screen | Node |
|---|---|---|
| 14 | Progress / Overview | `88:2` |
| 15 | Accountability / My Witnesses | `91:2` |
| 16 | Accountability / Two-Way Overview | `172:2` |
| 17 | Supporting / Person Detail | `173:2` |
| 18 | Witness Invite / Incoming | `164:2` |
| 19 | Notifications / Inbox | `136:2` |
| 25 | Notification / React — Breach | `143:2` |
| 26 | Challenge / Failed — Pact Broken | `152:2` |
| 27 | Protection / Lost | `154:2` |

## Account and legal

| # | Screen | Node |
|---|---|---|
| 28 | Profile / Overview | `107:2` |
| 29 | Profile / Personal Details | `117:2` |
| 30 | Account / Email & Password | `160:42` |
| 31 | Account / Delete Account | `112:15` |
| 36 | Legal / Privacy Policy | `160:66` |
| 37 | Legal / Terms of Service | `160:90` |
