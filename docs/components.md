# AgarthaVision · Components Guide

> How to build the **Clinical Pulse** moodboard on top of the
> [shadcn/compose](https://shadcn-compose.site) component library
> (Kotlin + Jetpack Compose, KMP).
>
> This document is the source of truth for tokens, typography, and which
> shadcn component to reach for. If a screen needs something that isn't in
> the library, this doc says so explicitly and gives a fallback.

---

## 1. Setup

Follow the official install guide at
[shadcn-compose.site/docs/installation](https://shadcn-compose.site/docs/installation).
The library exposes one root composable:

```kotlin
ShadcnTheme(
    shadcnLightColors = AgarthaLightColors,
    shadcnRadius      = AgarthaRadius,
    // (typography is configured via Material's Typography — see §3)
) {
    // app navigation here
}
```

Wrap your `setContent { … }` inside `MainActivity` with `ShadcnTheme`.
Every shadcn component reads from these overrides, so once the tokens are in
place the entire UI inherits the Clinical Pulse identity.

---

## 2. Color tokens — `AgarthaLightColors`

Override `ShadcnColors` with the Clinical Pulse palette. The moodboard names
are kept as comments so the mapping stays traceable.

```kotlin
import androidx.compose.ui.graphics.Color
import com.shadcn.compose.theme.ShadcnColors

object AgarthaLightColors : ShadcnColors {

    // — Surfaces ————————————————————————————————————————————————
    override val background        = Color(0xFFFAFAF7) // Bone
    override val card              = Color(0xFFFFFFFF) // pure white card on bone
    override val popover           = Color(0xFFFFFFFF)
    override val sidebar           = Color(0xFFFAFAF7) // Bone

    // — Text ————————————————————————————————————————————————————
    override val foreground         = Color(0xFF0E1B2C) // Ink
    override val cardForeground     = Color(0xFF0E1B2C)
    override val popoverForeground  = Color(0xFF0E1B2C)
    override val sidebarForeground  = Color(0xFF0E1B2C)
    override val mutedForeground    = Color(0xFF5A6577) // Mute · secondary text

    // — Primary action (Clinical Blue) ——————————————————————————
    override val primary             = Color(0xFF1F5BFF)
    override val primaryForeground   = Color(0xFFFFFFFF)
    override val sidebarPrimary          = Color(0xFF1F5BFF)
    override val sidebarPrimaryForeground = Color(0xFFFFFFFF)

    // — Secondary surfaces (Paper) ———————————————————————————————
    // "Paper" is the resting chip / inset card colour from the moodboard.
    override val secondary           = Color(0xFFF1EFE9) // Paper
    override val secondaryForeground = Color(0xFF0E1B2C)
    override val muted               = Color(0xFFF1EFE9) // Paper
    override val accent              = Color(0xFFE5F7FF) // Diagnostic Cyan @ tint
    override val accentForeground    = Color(0xFF0E1B2C)
    override val sidebarAccent          = Color(0xFFE5F7FF)
    override val sidebarAccentForeground = Color(0xFF0E1B2C)

    // — Destructive (Alert Coral) ————————————————————————————————
    override val destructive          = Color(0xFFFF5A4A)
    override val destructiveForeground = Color(0xFFFFFFFF)

    // — Lines & focus ————————————————————————————————————————————
    override val border         = Color(0xFFE5E3DF) // Ink @ 15%, baked solid
    override val input          = Color(0xFF0E1B2C) // 1.5px ink border per mood
    override val ring           = Color(0xFF1F5BFF) // Clinical Blue focus ring
    override val sidebarBorder  = Color(0xFFE5E3DF)
    override val sidebarRing    = Color(0xFF1F5BFF)

    // — Snackbar / toast ————————————————————————————————————————
    override val snackbar       = Color(0xFF0E1B2C)

    // — Charts (used by Progress, sparklines, EPG history) ———————
    override val chart1 = Color(0xFF1F5BFF) // Clinical Blue — primary series
    override val chart2 = Color(0xFF7FE3FF) // Diagnostic Cyan — secondary
    override val chart3 = Color(0xFF0E1B2C) // Ink — reference line
    override val chart4 = Color(0xFFFF5A4A) // Alert Coral — threshold breach
    override val chart5 = Color(0xFF5A6577) // Mute — comparison series
}
```

### Semantic colour cheatsheet

| Moodboard name      | Hex        | shadcn token              | Use for                              |
|---------------------|------------|---------------------------|--------------------------------------|
| Bone                | `#FAFAF7`  | `background`, `sidebar`   | App background, scaffold             |
| Paper               | `#F1EFE9`  | `secondary`, `muted`      | Chips, inset cards, skeleton base    |
| Ink                 | `#0E1B2C`  | `foreground`, `input`     | All primary text + outlined inputs   |
| Mute                | `#5A6577`  | `mutedForeground`         | Captions, metadata, helper text      |
| Clinical Blue       | `#1F5BFF`  | `primary`, `ring`         | Primary actions, focus, progress     |
| Diagnostic Cyan     | `#7FE3FF`  | `chart2` + raw            | Live signal dot, overlay highlights  |
| Diagnostic Cyan 10% | `#E5F7FF`  | `accent`                  | Hover / soft highlight backgrounds   |
| Alert Coral         | `#FF5A4A`  | `destructive`             | Errors, discard, threshold breach    |

> **Rule:** Clinical Blue = "act" · Diagnostic Cyan = "signal / live" · Coral
> = "danger". Don't swap them.

---

## 3. Typography

shadcn-compose uses Material's `Typography`. Load Geist and JetBrains Mono
from Google Fonts (via `androidx.compose.ui.text.googlefonts`) and define a
single `AgarthaTypography` object you pass through `MaterialTheme` (which
`ShadcnTheme` wraps).

```kotlin
val Geist        = GoogleFont("Geist")
val JetBrainsMono = GoogleFont("JetBrains Mono")

val GeistFamily        = FontFamily(Font(googleFont = Geist,        fontProvider = provider))
val JetBrainsMonoFamily = FontFamily(Font(googleFont = JetBrainsMono, fontProvider = provider))
```

| Role            | Family         | Size · Weight     | Letter-spacing | Where it appears                              |
|-----------------|----------------|-------------------|----------------|-----------------------------------------------|
| Display         | Geist 500      | 56–96 sp / 500    | −0.04 em       | Hero numerals (e.g. EPG `1,284`)              |
| Headline        | Geist 500      | 28–32 sp / 500    | −0.02 em       | Screen titles ("Sample SMP-0429 …")           |
| Title           | Geist 500      | 20–22 sp / 500    | −0.01 em       | Card titles, dialog titles                    |
| Body            | Geist 400      | 16 sp / 400       |  0             | Paragraphs, long copy                         |
| Label           | Geist 500      | 14 sp / 500       |  0             | Buttons, tabs, list rows                      |
| Caption / Meta  | Geist 400      | 12 sp / 400       |  0             | Helper text                                   |
| Mono · Data     | JetBrains Mono | 11–14 sp / 400    |  0             | IDs, timestamps, GPS, EPG readouts            |
| Mono · Eyebrow  | JetBrains Mono | 10 sp / 400 · UC  |  +0.12 em      | Section labels ("DETECTION", "EDGE INFERENCE")|

> **Rule:** Numerics get tabular figures everywhere they line up vertically
> (`fontFeatureSettings = "tnum"`).

---

## 4. Radius — `AgarthaRadius`

Clinical Pulse uses generous rounding (pill buttons, soft cards). Anchor the
scale at `lg = 12.dp` so the derived `xl = 16.dp` matches the card radius
from the moodboard.

```kotlin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.shadcn.compose.theme.ShadcnRadius

object AgarthaRadius : ShadcnRadius {
    override val radius = 12.dp                       // base
    override val sm     = max(0.dp, radius - 4.dp)    // 8  — chips, mini tags
    override val md     = max(0.dp, radius - 2.dp)    // 10 — inputs, popovers
    override val lg     = radius                      // 12 — most controls
    override val xl     = max(0.dp, radius + 4.dp)    // 16 — cards, dialogs
    override val full   = 999.dp                      // pill buttons
}
```

**Spacing scale (use these, not arbitrary dp):**
`4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56 · 64` dp.
Card outer padding = `24.dp`. Screen edge padding = `20.dp`. Cluster gap
(buttons in a row) = `12.dp`.

---

## 5. Component playbook

For each shadcn-compose component, this section says **what to use it for in
AgarthaVision** and any non-default props/styling you need to keep on-brand.
Components are listed alphabetically, matching the library's docs.

### Accordion
**Use for** sample detail expansion in lists (history, audit trail, "show
metadata"). Keep the trigger row 56.dp tall with the chevron on the right.
Avoid nesting more than one level.

### Alert
**Use for** inline status banners in the validation dashboard.
Variants → Clinical mapping:
- `default` → neutral info ("Synced to DOH endpoint 14:22").
- `destructive` → red coral ("Specimen outside biological window").
Add a one-line description with mono-font timestamps where relevant.

### Alert Dialog
**Use for** any irreversible HITL action: **Discard sample**, **Override AI
finding**, **Delete report draft**. Destructive confirm button uses
`destructive` token; cancel is the secondary variant.

### Avatar
**Use for** the technologist chip in the app bar and on validated samples
("Validated by J. Novabos"). Initials fallback on Paper background, Ink
text, `lg` radius — circular default is fine.

### Badge
**Use for** status pills throughout: `LIVE`, `QUEUED`, `VALIDATED`,
`DOH-COMPLIANT`, taxonomy tags like `Trichuris trichiura 0.91`.
Variant mapping:
- `default` → Clinical Blue fill (active states).
- `secondary` → Paper fill, Ink text (resting tags).
- `outline` → Ink 1.5px border, transparent fill (taxonomy chips).
- `destructive` → Coral (threshold breach).
Always use JetBrains Mono for the badge text, 11 sp, uppercase, `+0.06 em`.

### Button
**Use for** every CTA. Variants → moodboard mapping:
- `default` → **Clinical Blue, pill (`full` radius)** — "Validate sample".
- `secondary` → **Paper fill, Ink text, pill** — neutral actions.
- `outline` → **Ink 1.5px border, transparent** — "Re-scan".
- `destructive` → **Coral fill** — "Discard".
- `ghost` → text-only, used inside cards.
- `link` → reserved for cross-screen jumps in the audit trail.
Default size is `default` (40.dp tall, 24.dp horizontal padding). Use
`lg` (48.dp) for the primary action on the capture screen so it stays
thumb-reachable. Icon buttons (`icon` size) are 40×40 with `lg` radius —
never pill — to read as utility chrome.

### Calendar
**Use for** the date filter in the dashboard ("Validated between …").
Selected day cell uses `primary`; today's ring uses `ring`. Keep the
weekday header in JetBrains Mono 10 sp uppercase.

### Card
**Workhorse container.** Two recipes:
1. **Elevated detection card** — `background = card (#FFFFFF)`, `xl` radius,
   shadow `0 12 32 -16 rgba(14,27,44,.18)`, `border = none`. Used for the
   primary HITL review card.
2. **Resting list card** — `background = secondary (Paper)`, `lg` radius,
   no shadow. Used for queued samples list.

Internal padding `24.dp`, header gap `16.dp`.

### Carousel
**Use for** browsing the 12 microscopy fields per slide. Dot indicator uses
`mutedForeground` for inactive, `primary` for active. Edge-to-edge inside
the round viewport card; do not show arrow chrome on mobile.

### Checkbox
**Use for** multi-select on the queue ("Sync these 7 samples") and for
"Mark for educational repository". Checked state uses `primary`.

### Combobox
**Use for** site picker ("Brgy. San Roque ▾") and species override. Should
filter as the technologist types — this is critical for the override flow
where they may be searching for a species name.

### Date Picker
**Use for** report range selection in the DOH export screen. Always paired
with a Combobox for the time slot.

### Dialog
**Use for** non-destructive modal flows: **EPG calibration**, **GPS
override**, **Manual species edit**. Title in Geist 22 sp / 500, body in
Geist 16 sp / 400. Buttons right-aligned in the footer.

### Drawer (Bottom Sheet)
**Use for** the sample-quick-look on the dashboard map and for the field
**Capture-options sheet** (lighting / exposure / focal step). Drag handle
visible (24.dp wide pill in `mutedForeground`). Snap points at 30% / 60% /
95%.

### Dropdown Menu
**Use for** the kebab menu on each sample row (Re-scan · Export · Discard).
Destructive items get the Coral colour. Item height 36.dp, padding 12.dp.

### Input
**Use for** Sample ID entry, EPG manual override, notes field.
Default state: 1.5px Ink border (the `input` token), `lg` radius, 14.dp
vertical padding. Mono-font value for IDs/numbers, sans for free text.
Focus ring uses `ring` (Clinical Blue) at 2px outside.

### Popover
**Use for** "what is EPG?" inline explainers, confidence-score breakdowns,
and the GPS-accuracy tooltip. Background `popover` white, `xl` radius,
elevation matches the elevated card.

### Progress
**Use for** the **AI confidence bar** beneath every detection ("0.91"),
**upload-sync progress** in the offline queue, and **biological-window
countdown** on capture. Track uses `secondary` (Paper); fill uses `primary`
(Clinical Blue). For the bio-window countdown only, switch the fill to
`destructive` when remaining ≤ 10 minutes.

### Radio Group
**Use for** the species override selector (single choice between the three
STH classes + "Artifact"). Show JetBrains Mono confidence next to each
option.

### Select
**Use for** simple dropdowns with ≤ 5 options (magnification: 400× / 1000×
/ 1200×; shift: AM / PM). For longer or searchable lists, prefer Combobox.

### Sidebar
**Use for** the dashboard navigation on tablet / foldable / desktop dev
view (Capture · Queue · Validate · Reports · Sites · Settings). Hidden on
phones — switch to a bottom nav (custom — see §6).

### Skeleton
**Use for** the image loading state inside the detection card and for
queued-sample list rows during sync. Base colour is `muted` (Paper); the
shimmer highlights to `accent`.

### Slider
**Use for** **manual exposure / focus assist** during capture and the
confidence-threshold filter on the dashboard ("Show samples below X
confidence"). Active track `primary`, thumb white with Ink ring.

### Sonner (toast)
**Use for** transient confirmations: "Sample queued", "Synced 7 samples",
"Edge inference complete". Position bottom-center, 4-second auto-dismiss.
Use `destructive` variant for failures. **Never** use a toast for anything
the user must act on — that's an Alert Dialog.

### Switch
**Use for** binary settings (Offline mode · Enable GPS · Auto-validate
above 0.95). Track on = `primary`. Off = `mutedForeground`.

### Tabs
**Use for** the segmented view of a sample: **Image · Detections · Metadata
· Audit**. Underline indicator in `primary`, inactive labels in
`mutedForeground`, active label in `foreground`. Mono caption (JetBrains
Mono 10 sp uppercase) under each label is optional.

---

## 6. What's **not** in shadcn-compose — build these

The library covers ~85% of the surface, but the moodboard depends on a few
specialised pieces that don't exist out of the box. Build these once in
`ui/agartha/` and treat them as first-class:

| Custom component        | Built from                       | Where it's used                            |
|-------------------------|----------------------------------|---------------------------------------------|
| `MicroscopyViewport`    | `Box` + `Canvas` + `Image`       | Capture screen, detection card             |
| `DetectionOverlay`      | `Canvas` over `MicroscopyViewport` | Draws bounding circles + species labels    |
| `EpgReadout`            | Composition of `Text` styles     | Big "1,284 EPG" display (mood §02)         |
| `BiologicalWindowChip`  | `Badge` + countdown `LaunchedEffect` | App-bar timer ("BIO 47:12")             |
| `OfflineQueueBadge`     | `Badge` with leading dot         | Persistent queue counter in the app bar    |
| `BottomNavBar` (phone)  | `Surface` + `Row` of `IconButton`| Phone replacement for `Sidebar`            |
| `GeoMapMarker`          | `Canvas` + Maps SDK              | Map view on Reports screen                 |
| `AuditTimeline`         | `LazyColumn` of `Row` + `Divider`| Sample audit-trail tab                     |

Match the tokens defined above — no new colours, no new radii.

---

## 7. Reference: which screen uses what

A quick map from the architectural-design flows to the components above.

### `CaptureScreen`
`MicroscopyViewport` · `Slider` (focus) · `Badge` (LIVE / mag) ·
`BiologicalWindowChip` · `Button (lg, primary)` for Capture · `IconButton`
for settings · `Sonner` for "Snapshot saved" · `Drawer` for capture
options.

### `QueueScreen`
`Card (resting)` per sample · `Badge` (status) · `Checkbox` (multi-select) ·
`Progress` (per-row sync) · `DropdownMenu` (row kebab) · `Sonner` on sync
success · `Skeleton` while loading.

### `ValidateScreen` (HITL)
`Card (elevated)` for the detection panel · `MicroscopyViewport` +
`DetectionOverlay` · `EpgReadout` · `Progress` (confidence) ·
`Tabs` (Image / Detections / Metadata / Audit) · `RadioGroup` (species
override) · `Combobox` (species search) · `Button (primary)` Validate ·
`Button (destructive)` Discard · `AlertDialog` on Discard / Override.

### `ReportsScreen`
`Sidebar` (or `BottomNavBar` on phone) · `DatePicker` · `Tabs` (Map /
Table) · `Chart` (using `chart1–5` tokens) · `Card (elevated)` summary ·
`Button (outline)` Export DOH PDF.

### `SettingsScreen`
`Switch` · `Select` · `Input` · `Alert` for "Edge device offline" ·
`AlertDialog` for "Reset device pairing".

---

## 8. Do / Don't

**Do**
- Always pass tokens (`ShadcnTheme.colors.primary`) instead of hard-coding.
- Use `JetBrains Mono` for any string that is a *value* (ID, EPG number,
  GPS, confidence, timestamp).
- Use `Geist` for any string that is a *label or sentence*.
- Use `Badge (outline)` for taxonomy tags so they read as data, not action.
- Reserve `destructive` for irreversible loss of data or specimen.

**Don't**
- Don't tint the background of a `Card (elevated)` — keep it pure white.
- Don't use a `Button (default)` for navigation. Use `link` or `ghost`.
- Don't recolour `Diagnostic Cyan` to indicate "success" — that's a signal
  colour, not a status colour. Use `primary` for "validated".
- Don't introduce new radii. The four-step scale is the system.
- Don't replace `Sonner` with a custom snackbar — the library's one already
  uses `snackbar` and stacks correctly.

---

*Source library: [shadcn-compose.site](https://shadcn-compose.site) ·
GitHub: [derangga/shadcn-ui-kmp](https://github.com/derangga/shadcn-ui-kmp).
This guide reflects the Clinical Pulse moodboard (`Moodboards.html`,
artboard 01).*
