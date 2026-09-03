# Design tokens

Read out of the Figma file (`upZH3FVJURpJJXVF4SwLkF`), not chosen. Screens
01, 02 and 32 use these values identically, which is why they are the
palette rather than three screens that happen to agree.

**The accent is green.** It is the brand colour, confirmed by the founder.
Anything that needs a colour — the app, the marketing site, a store
listing, a social image — uses these values and does not invent a
near-neighbour. A colour that is almost right is harder to notice, and
harder to correct, than one that is obviously absent.

| Role | Value | Where |
|---|---|---|
| Background | `#0A0A0A` | every screen's ground; not pure black |
| Surface | `#0E1110` | raised panels, e.g. the auth form card |
| Surface border | `#1F2925` | 1px hairline around a panel |
| Field | `#0E0E0E` | text input interior, slightly darker than its panel |
| Field border | `#212926` | 1px hairline around an input |
| Accent | `#12B886` | primary buttons, the eyebrow above a title, links |
| On accent | `#04110D` | text on top of Accent — a near-black green, never `#000` |
| Text primary | `#F5F5F2` | titles and body |
| Text secondary | `#9A9F9C` | supporting text, field labels, placeholders |
| Text tertiary | `#6B706E` | legal and disclaimer lines only |

## Type

Inter, bundled in the app (SIL OFL 1.1, `android/LICENSES/Inter-OFL.txt`).

| Role | Size | Weight | Notes |
|---|---|---|---|
| Eyebrow | 12 | SemiBold | accent coloured, `0.16em` tracking, above a title |
| Display | 38-44 | Bold | the one big line on a screen |
| Body | 16 | Regular | 24 line height |
| Field | 15 | Regular | typed and placeholder text |
| Label | 13 | Medium | above a field, and inline links |
| Button | 16 | Bold | |
| Legal | 11 | Regular | tertiary colour |

## Measures

Screen margin 24. Panel radius 24 with 18 of inset — which is where the
design's 42px left edge comes from, 24 plus 18. Inputs are 58 tall with a
14 radius; the primary button is 58 tall and fully rounded.

## One deliberate departure

The Figma frames position everything absolutely on a 393x852 canvas. The
code does not reproduce those offsets. A button fixed 690px from the top is
correct only on a phone that is exactly 852 tall, and wrong again the moment
a reader scales their font up; the same reading is expressed with real
spacing and weighted space. Screens with inputs also scroll, because the
frames assume nothing covers them and on a real phone the keyboard takes
roughly half the screen.
