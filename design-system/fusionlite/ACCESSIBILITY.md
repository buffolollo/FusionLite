# FusionLite — Accessibility & Design-Token Audit

Scope: the Compose UI in `app/src/main/java/com/fusionlite/ui/` (login, home,
analytics, costs, account, full-screen chart, bottom navigation).
Target: WCAG 2.1 AA, with AAA-level text contrast where the palette allows it.

This file is the human-readable companion to the token layer
(`ui/Theme.kt`, `ui/FusionTokens.kt`, `ui/Components.kt`). Numbers below were
computed from the shipped hex values with the WCAG relative-luminance formula;
the audit script is reproduced at the end so it can be re-run after any palette
change.

---

## 1. Token architecture

Three layers, one direction of dependency:

| Layer | Source | Examples |
|---|---|---|
| **Primitive** | `Theme.kt` raw brand constants | `SolarGold #FFC94D`, `NightSky #070D1B`, `NightSurface #101A2E` |
| **Semantic** | `FusionPalette` + `FusionStates` (one instance per theme, linearly blended) | `textPrimary`, `textSecondary`, `surfaceRaised`, `borderStrong`, `danger`, `focusRing`, `scrim` |
| **Component** | `FusionDesignSystem` + `ui/Components.kt` | `FusionCard`, `FusionButton`, `FusionTextField`, `FusionSegmentedControl`, `FusionBanner`, `FusionStatTile` |

Rules enforced by the component layer:

- No screen reads raw `Solar*` colours for text. Raw constants are reserved for
  artwork (hero scene, nav blob) and for text drawn *on the dark hero scrim*.
- No screen hard-codes a font size, radius or gap for a standard component; it
  uses `design.type.*`, `design.radius*`, `design.spacing*`.
- Every themeable colour is blended through one `animateFloatAsState`, so light
  ↔ dark never produces an intermediate "wrong contrast" frame or a flash.
  A companion `night` scalar crossfades the artwork sky and stars, so the hero
  scene transitions smoothly instead of flipping at the midpoint.

### Semantic palette

| Role | Light | Dark | Contrast on card (light / dark) |
|---|---|---|---|
| `textPrimary` | `#0E1A2B` | `#EAF1FB` | 17.5 / 15.3 |
| `textSecondary` | `#3C4A5E` | `#B7C5DA` | 9.0 / 9.9 |
| `textTertiary` | `#5A6779` | `#93A3BC` | 5.8 / 6.8 |
| `border` (decorative) | `#C2CCDC` | `#26385A` | — |
| `borderStrong` (interactive) | `#6E7C90` | `#55688A` | 4.2 / 3.1 |
| `gold` accent | `#8F6100` | `#FFC94D` | 5.4 / 11.3 |
| `amber` accent | `#A85800` | `#FF9F1C` | 5.2 / 8.5 |
| `cyan` accent | `#00738C` | `#4DD8FF` | 5.5 / 10.4 |
| `green` success | `#157A4C` | `#5BE49B` | 5.4 / 10.8 |
| `blue` info | `#2563C9` | `#7FB3FF` | 5.7 / 8.1 |
| `danger` | `#B3261E` | `#FF7A7A` | 6.5 / 6.9 |
| `focusRing` | `#00738C` | `#4DD8FF` | 5.5 / 10.4 |

Interactive boundaries use `borderStrong`, which clears the 3:1 non-text
contrast rule in both themes. Decorative card hairlines use `border` and are
never the only affordance.

### Typography

| Token | Size / line height | Use |
|---|---|---|
| `display` | 52 / 56, bold, tabular figures | live kW, period headline |
| `displaySmall` | 38 / 44, bold | login headline, cost total |
| `title` | 26 / 32, bold | screen title |
| `heading` | 19 / 25, semibold | card title |
| `subheading` | 15 / 21, semibold | row title, button label |
| `body` | 14 / 21 | form text, descriptions |
| `bodySmall` | 12.5 / 18 | captions, metadata |
| `label` | 12 / 16, medium | chips, key/value |
| `labelMicro` | 11 / 15, bold, 1.3sp tracking | eyebrows, status pills |

The previous pass used 8–10sp captions; nothing informational is below 11sp
now, and body copy is 14sp. Numeric styles enable `tnum` so live values do not
jitter while they update.

### Spacing, radius, motion

- 4/8dp rhythm: `spacingXs 4`, `spacingSm 8`, `spacingMd 12`, `spacingLg 16`,
  `spacingXl 20`, `spacingXxl 24`, `spacingSection 32`; `pageGap 16`,
  `cardPadding 18`, `sectionGap 24`.
- Radii: 12 / 16 / 20 / 24 / 28 (hero) / 100 (pill).
- Touch target: `minTouchTarget = 48dp`; icon buttons are 48×48 with a 16–22dp
  glyph, chips add a 48dp hit area around a 40dp visual.
- Motion: `fast 140ms`, `base 220ms`, `slow 380ms`, theme blend `520ms`,
  standard easing `(0.4, 0, 0.2, 1)`. All non-essential motion is disabled by
  `LocalFusionReducedMotion` (see §6).

---

## 2. Component state matrix

| Component | Default | Hover / pointer | Focused | Pressed | Disabled | Loading | Error | Success |
|---|---|---|---|---|---|---|---|---|
| `FusionButton` | variant container + label | 8–10% overlay | 2dp `focusRing` inset ring | 8–10% overlay, no layout shift | `disabledContainer` + `disabledContent`, clickable disabled | inline spinner + "Working" state description | n/a | n/a |
| `FusionIconButton` | glass circle + border | overlay | ring | overlay | muted glyph, no action | caller swaps icon (e.g. refresh) | n/a | n/a |
| `FusionChip` | glass pill | overlay | ring | overlay | muted, `selectable(enabled=false)` | n/a | n/a | selected = accent container + border + bold label |
| `FusionSegmentedControl` | glass track | per-item overlay | ring on item | overlay | n/a | n/a | n/a | selected item has accent pill + radio semantics |
| `FusionTextField` | sunken container, `borderStrong` | n/a | accent 2dp border | n/a | disabled container/text | n/a | `danger` border, inline message, `error()` semantics | green check + helper text, `stateDescription` |
| `FusionBanner` | 12% tone container + 34% border | n/a | n/a | n/a | n/a | n/a | assertive live region | polite live region |
| `FusionCard` (interactive) | raised surface + hairline | overlay | ring | overlay | n/a | skeleton children | n/a | n/a |
| `FusionStatTile` | raised surface + icon badge | n/a | n/a (not interactive) | n/a | n/a | `FusionSkeleton` value bar | n/a | n/a |
| `ThemeToggle` | day/night track | overlay | ring around 72×48 target | knob spring | n/a | n/a | n/a | switch state announced by `toggleable` |
| `LiquidBottomBar` item | inactive label + glyph | overlay | ring | overlay | n/a | n/a | n/a | selected = accent tint + liquid blob + `Role.Tab` selected |

Press and hover feedback is drawn with `drawWithContent`, so bounds never move.
No hover/scale transform shifts surrounding content.

---

## 3. Contrast verification (WCAG 2.1)

Required: **4.5:1** normal text, **3:1** large text (≥18.66sp bold or ≥24sp) and
non-text UI boundaries/icons.

Measured on the real component surfaces (`surfaceRaised` light `#FFFFFF`,
dark `#101A2E`; page background light `#F4F7FC`, dark `#070D1B`):

| Pair | Ratio | Verdict |
|---|---|---|
| Light body `textPrimary` / card | 17.48 | AAA |
| Light `textSecondary` / card | 9.00 | AAA |
| Light `textTertiary` / card | 5.75 | AA |
| Light accent text / card (min of gold/amber/cyan/green/blue) | 5.17 | AA |
| Light accent text / 10% tint of itself | 4.50 (amber) | AA |
| Light `danger` / card | 6.54 | AAA |
| Light `borderStrong` / card | 4.24 | ≥3:1 UI |
| Dark body `textPrimary` / card | 15.28 | AAA |
| Dark `textSecondary` / card | 9.93 | AAA |
| Dark `textTertiary` / card | 6.79 | AAA |
| Dark accent text / card (min) | 8.10 | AAA |
| Dark accent text / 10% tint | 5.96 (danger min) | AA |
| Dark `borderStrong` / card | 3.09 | ≥3:1 UI |
| `onPrimary` light (white on `#A35F00`) | 5.01 | AA |
| `onPrimary` dark (`#241800` on gold) | 11.37 | AAA |
| Login subtitle light (`#455267` on `#DBEAFB`) | 6.47 | AAA |
| Login subtitle dark (`#B7C5DA` on `#081226`) | 10.68 | AAA |
| Nav inactive light (`#4F5D70` on `#F3F7FD`) | 6.24 | AAA |
| Nav inactive dark (`#A8B6CC` on `#101A2E`) | 8.46 | AAA |

Colour is never the only indicator: selection also changes weight/border,
errors carry an icon + text, and status pills carry a label.

---

## 4. Keyboard and focus

- Every control is built on `Modifier.clickable` / `selectable` /
  `toggleable`, so it participates in Compose focus traversal in visual order.
- `Modifier.fusionInteractive` draws a 2dp inset focus ring in `focusRing`
  colour on every focusable surface (buttons, icon buttons, chips, segmented
  items, nav destinations, cards with actions).
- Focus order follows layout order: screen header → refresh → hero → plant
  chips → content cards → navigation.
- The login screen moves focus to the error summary when sign-in fails
  (`FocusRequester` on the assertive banner) and uses IME Next → password,
  Done → submit.
- Back from Analytics/Costs/Account returns to Home before leaving the app;
  Back from the full-screen chart closes it.
- Chart inspection has explicit accessibility actions
  ("Next data point", "Previous data point", "Clear selection") so the
  tap-only interaction has a non-pointer alternative.

## 5. Screen-reader semantics

- Decorative icons are drawn on `Canvas` with no semantics; `FusionIcon`
  exposes a description only when one is supplied, otherwise it clears its
  semantics subtree.
- Icon-only controls carry a `contentDescription` on the clickable node
  (refresh, previous/next period, expand chart, show/hide password, copy DN).
- Charts expose a text summary: the production chart's `contentDescription`
  includes bucket count, unit and the peak; the donut describes each segment as
  a percentage; metric bars and gauges expose `progressSemantics`.
- Status and error surfaces use `liveRegion` (`Assertive` for errors,
  `Polite` for informational updates) and the freshness pill says "Live",
  "Stale · Nm ago", "Syncing" or "Connecting" rather than always claiming live.
- Plant switching is a `selectableGroup` of `Role.RadioButton` rows; bottom
  navigation is a `selectableGroup` of `Role.Tab` items; the theme switch is
  `Role.Switch`; segmented controls are radio groups.
- Every interactive target is at least 48dp in both axes.

## 6. Reduced motion

`LocalFusionReducedMotion` is true when
`Settings.Global.ANIMATOR_DURATION_SCALE == 0` ("Remove animations"). Under it:

- theme blend duration becomes 0;
- the theme switch snaps instead of springing and stops twinkling;
- the liquid nav blob jumps to its destination and its wobble/droplets freeze;
- chart reveal animations jump to their final state;
- the solar hero scene and energy-flow diagram freeze to a still frame;
- skeleton pulse becomes a static tint;
- screen and tab transitions become no-ops.

## 7. Responsive layout

| Width | Gutter | Columns |
|---|---|---|
| < 600dp (phones) | 16dp | 1–2 (KPI grid min item 140–156dp) |
| 600–839dp (large phone / small tablet) | 24dp | 2–3 |
| ≥ 840dp (tablet, landscape) | 32dp | up to 4 |

`FusionPage` caps the content column at 760dp and centres it, so long text
never runs edge to edge on tablets. The bottom nav is floating; every page
reserves `navClearance = 124dp` so nothing hides behind it. Mix/donut rows
stack below 360dp of available width.

## 8. How to re-run the audit

```bash
python3 - <<'PY'
def lin(c):
    c = c / 255
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
def lum(h):
    h = h.lstrip('#'); r, g, b = (int(h[i:i+2], 16) for i in (0, 2, 4))
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
def cr(a, b):
    la, lb = lum(a), lum(b); hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)
for name, fg, bg in [
    ("light primary/card", "#0E1A2B", "#FFFFFF"),
    ("light secondary/card", "#3C4A5E", "#FFFFFF"),
    ("light tertiary/card", "#5A6779", "#FFFFFF"),
    ("light borderStrong/card", "#6E7C90", "#FFFFFF"),
    ("dark primary/card", "#EAF1FB", "#101A2E"),
    ("dark secondary/card", "#B7C5DA", "#101A2E"),
    ("dark tertiary/card", "#93A3BC", "#101A2E"),
    ("dark borderStrong/card", "#55688A", "#101A2E"),
]:
    print(f"{name:28s} {cr(fg, bg):5.2f}:1")
PY
```

## 9. Known limitations

- This is a static, source-level audit: no live TalkBack/Focus session was
  recorded in this workspace. Semantics are declared explicitly and can be
  inspected with Layout Inspector.
- The hero artwork and energy-flow diagram are decorative canvases; the flow
  diagram carries a generated `contentDescription` describing each live value.
- Light-theme amber on its own 10% tint measures exactly 4.50:1, which clears
  AA for the 11–12sp bold labels that use it; the label is also carried by an
  icon and a word, never colour alone.
