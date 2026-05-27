# AgarthaVision · Design System (KomoUI 0.3.0)

> How to build the **Clinical Pulse** moodboard on top of
> [KomoUI](https://github.com/derangga/komoui) `0.3.0` (Kotlin + Jetpack Compose).
>
> This document is the **source of truth** for tokens, typography, and component usage.
> It supersedes the original `components.md` which referenced the legacy `shadcn-ui-kmp` package.
>
> **Migration note:** KomoUI 0.3.0 is the rebranded continuation of `shadcn-ui-kmp`.
> All imports changed from `com.shadcn.ui.*` → `com.komoui.*` and the theme wrapper
> changed from `ShadcnTheme` → `KomoTheme`. The implementation uses `KomoStyles` and
> `KomoRadius` (the current interfaces); `ShadcnColors`/`ShadcnRadius` are legacy aliases.

---

## 1. Setup

Add the dependency (see `02_PROJECT_ARCHITECTURE.md` version catalog):

```kotlin
// gradle/libs.versions.toml
komoui = "0.3.0"

// app/build.gradle.kts
implementation(libs.komoui)
```

Wrap `setContent { … }` in `MainActivity` via the `AgarthaVisionTheme` composable defined in `ui/theme/Theme.kt`:

```kotlin
// MainActivity.kt
setContent {
    AgarthaVisionTheme {
        // app navigation here
    }
}

// ui/theme/Theme.kt
@Composable
fun AgarthaVisionTheme(content: @Composable () -> Unit) {
    KomoTheme(
        isDarkTheme         = false,            // locked light — see §8
        komoLightColors     = AgarthaLightStyles,
        komoDarkColors      = AgarthaLightStyles, // same palette intentionally
        materialLightColors = AgarthaMaterialColorScheme,
        materialDarkColors  = AgarthaMaterialColorScheme,
        komoRadius          = AgarthaRadius,
        typography          = AgarthaTypography,
        content             = content,
    )
}
```

Access design tokens via `MaterialTheme.styles` (colors), `MaterialTheme.radius`, and `MaterialTheme.isDark`.

---

## 2. Color Tokens — `AgarthaLightStyles`

Override `KomoStyles` with the Clinical Pulse palette.

```kotlin
import androidx.compose.ui.graphics.Color
import com.komoui.themes.KomoStyles

object AgarthaLightStyles : KomoStyles {

    // — Surfaces ————————————————————————————————————————————
    override val background        = Color(0xFFFAFAF7) // Bone
    override val card              = Color(0xFFFFFFFF) // pure white card on bone
    override val popover           = Color(0xFFFFFFFF)
    override val sidebar           = Color(0xFFFAFAF7) // Bone

    // — Text ————————————————————————————————————————————————
    override val foreground         = Color(0xFF0E1B2C) // Ink
    override val cardForeground     = Color(0xFF0E1B2C)
    override val popoverForeground  = Color(0xFF0E1B2C)
    override val sidebarForeground  = Color(0xFF0E1B2C)
    override val mutedForeground    = Color(0xFF5A6577) // Mute · secondary text

    // — Primary action (Clinical Blue) ——————————————————————
    override val primary             = Color(0xFF1F5BFF)
    override val primaryForeground   = Color(0xFFFFFFFF)
    override val sidebarPrimary          = Color(0xFF1F5BFF)
    override val sidebarPrimaryForeground = Color(0xFFFFFFFF)

    // — Secondary surfaces (Paper) ———————————————————————————
    override val secondary           = Color(0xFFF1EFE9) // Paper
    override val secondaryForeground = Color(0xFF0E1B2C)
    override val muted               = Color(0xFFF1EFE9) // Paper
    override val accent              = Color(0xFFE5F7FF) // Diagnostic Cyan @ tint
    override val accentForeground    = Color(0xFF0E1B2C)
    override val sidebarAccent          = Color(0xFFE5F7FF)
    override val sidebarAccentForeground = Color(0xFF0E1B2C)

    // — Destructive (Alert Coral) ————————————————————————————
    override val destructive          = Color(0xFFFF5A4A)
    override val destructiveForeground = Color(0xFFFFFFFF)

    // — Lines & focus ————————————————————————————————————————
    override val border         = Color(0xFFE5E3DF) // Ink @ 15%
    override val input          = Color(0xFF0E1B2C) // 1.5px ink border
    override val ring           = Color(0xFF1F5BFF) // Clinical Blue focus ring
    override val sidebarBorder  = Color(0xFFE5E3DF)
    override val sidebarRing    = Color(0xFF1F5BFF)

    // — Snackbar / toast ————————————————————————————————————
    override val snackbar       = Color(0xFF0E1B2C)

    // — Charts (Progress, sparklines, EPG history) ———————————
    override val chart1 = Color(0xFF1F5BFF) // Clinical Blue — primary series
    override val chart2 = Color(0xFF7FE3FF) // Diagnostic Cyan — secondary
    override val chart3 = Color(0xFF0E1B2C) // Ink — reference line
    override val chart4 = Color(0xFFFF5A4A) // Alert Coral — threshold breach
    override val chart5 = Color(0xFF5A6577) // Mute — comparison series
}
```

### Semantic Colour Cheatsheet

| Moodboard name      | Hex        | Token                     | Use for                              |
|---------------------|------------|---------------------------|--------------------------------------|
| Bone                | `#FAFAF7`  | `background`, `sidebar`   | App background, scaffold             |
| Paper               | `#F1EFE9`  | `secondary`, `muted`      | Chips, inset cards, skeleton base    |
| Ink                 | `#0E1B2C`  | `foreground`, `input`     | All primary text + outlined inputs   |
| Mute                | `#5A6577`  | `mutedForeground`         | Captions, metadata, helper text      |
| Clinical Blue       | `#1F5BFF`  | `primary`, `ring`         | Primary actions, focus, progress     |
| Diagnostic Cyan     | `#7FE3FF`  | `chart2` + raw            | Live signal dot, overlay highlights  |
| Diagnostic Cyan 10% | `#E5F7FF`  | `accent`                  | Hover / soft highlight backgrounds   |
| Alert Coral         | `#FF5A4A`  | `destructive`             | Errors, discard, threshold breach    |

> **Rule:** Clinical Blue = "act" · Diagnostic Cyan = "signal / live" · Coral = "danger". Don't swap them.

---

## 3. Typography

Fonts are **bundled** in `app/src/main/res/font/` for offline field-site operation.

Download from Google Fonts and rename exactly as shown before adding to `res/font/`:

| Source file | Save as |
|---|---|
| `Geist-Regular.ttf` (weight 400) | `geist_regular.ttf` |
| `Geist-Medium.ttf` (weight 500) | `geist_medium.ttf` |
| `JetBrainsMono-Regular.ttf` (weight 400) | `jetbrains_mono_regular.ttf` |

```kotlin
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val GeistFamily = FontFamily(
    Font(resId = R.font.geist_regular, weight = FontWeight.Normal),
    Font(resId = R.font.geist_medium,  weight = FontWeight.Medium),
)
val JetBrainsMonoFamily = FontFamily(
    Font(resId = R.font.jetbrains_mono_regular, weight = FontWeight.Normal),
)
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

> **Rule:** Numerics get tabular figures everywhere they line up vertically (`fontFeatureSettings = "tnum"`).

---

## 4. Radius — `AgarthaRadius`

```kotlin
import androidx.compose.ui.unit.dp
import com.komoui.themes.KomoRadius

object AgarthaRadius : KomoRadius {
    override val radius = 12.dp  // base anchor
    override val sm     =  8.dp  // chips, mini tags
    override val md     = 10.dp  // inputs, popovers
    override val lg     = 12.dp  // most controls
    override val xl     = 16.dp  // cards, dialogs
    override val xxl    = 20.dp  // large panels
    override val xl3    = 24.dp  // bottom sheets
    override val full   = 999.dp // pill buttons
}
```

**Spacing scale (use these, not arbitrary dp):**
`4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56 · 64` dp.
Card outer padding = `24.dp`. Screen edge padding = `20.dp`. Cluster gap = `12.dp`.

---

## 5. Component Playbook

All components are imported from `com.komoui.components.*`.

For each component, this section says **what to use it for in AgarthaVision** and any
non-default props/styling to keep on-brand. Components are listed alphabetically.

### Accordion
**Use for** sample detail expansion in lists (history, audit trail, "show metadata"). Keep the trigger row 56.dp tall with the chevron on the right. Avoid nesting more than one level.

### Alert
**Use for** inline status banners (records dashboard, capture connectivity).
Variants → Clinical mapping: `default` → neutral info ("Synced 4 samples to Supabase"). `destructive` → red coral ("Cloud connection lost. Recording stopped."). Add a one-line description with mono-font timestamps where relevant.

### Alert Dialog
**Use for** any irreversible HITL action: Discard sample, Override AI finding, Delete report draft. Destructive confirm button uses `destructive` token; cancel is the secondary variant.

### Avatar
**Use for** the technologist chip in the app bar and on validated samples ("Validated by J. Novabos"). Initials fallback on Paper background, Ink text, `lg` radius.

### Badge
**Use for** status pills throughout: `LIVE`, `QUEUED`, `VALIDATED`, `DOH-COMPLIANT`, taxonomy tags like `Trichuris trichiura 0.91`.
Variant mapping: `default` → Clinical Blue fill (active states). `secondary` → Paper fill, Ink text (resting tags). `outline` → Ink 1.5px border, transparent fill (taxonomy chips). `destructive` → Coral (threshold breach).
Always use JetBrains Mono for badge text, 11 sp, uppercase, `+0.06 em`.

### Button
**Use for** every CTA. Variants → moodboard mapping: `default` → Clinical Blue, pill (`full` radius) — "Validate sample". `secondary` → Paper fill, Ink text, pill. `outline` → Ink 1.5px border, transparent — "Re-scan". `destructive` → Coral fill — "Discard". `ghost` → text-only, used inside cards. `link` → reserved for cross-screen jumps.
Default size is `default` (40.dp tall, 24.dp padding). Use `lg` (48.dp) for the primary capture button.

### Calendar
**Use for** the date filter in the dashboard ("Validated between …"). Selected day uses `primary`; today's ring uses `ring`. Weekday header in JetBrains Mono 10 sp uppercase.

### Card
**Workhorse container.** Two recipes:
1. **Elevated detection card** — `background = card (#FFFFFF)`, `xl` radius, shadow `0 12 32 -16 rgba(14,27,44,.18)`, `border = none`.
2. **Resting list card** — `background = secondary (Paper)`, `lg` radius, no shadow.
Internal padding `24.dp`, header gap `16.dp`.

### Carousel
**Use for** browsing microscopy fields per slide. Dot indicator uses `mutedForeground` inactive, `primary` active. Edge-to-edge; no arrow chrome on mobile.

### Checkbox
**Use for** multi-select on the queue ("Sync these 7 samples") and "Mark for educational repository". Checked state uses `primary`.

### Combobox
**Use for** site picker ("Brgy. San Roque ▾") and species override. Filter as the technologist types.

### Date Picker
**Use for** report range selection in the DOH export screen. Always paired with a Combobox for time slot.

### Dialog
**Use for** non-destructive modal flows: EPG calibration, GPS override, Manual species edit. Title in Geist 22 sp / 500, body in Geist 16 sp / 400. Buttons right-aligned.

### Drawer (Bottom Sheet)
**Use for** sample-quick-look on dashboard map, Capture-options sheet (lighting / exposure), and the `VerificationQueueSheet` (badge → list of flagged frames → tap-row opens `VerificationSheet`). Drag handle visible (24.dp pill in `mutedForeground`). Snap points at 30% / 60% / 95%. Nested-sheet flow is permitted: queue sheet at 60% snap dismisses, then verification sheet opens at 95% snap.

### Dropdown Menu
**Use for** kebab menu on each sample row (Re-scan · Export · Discard). Destructive items get Coral. Item height 36.dp, padding 12.dp.

### Input
**Use for** Sample ID entry, EPG manual override, notes. Default: 1.5px Ink border (`input` token), `lg` radius, 14.dp vertical padding. Mono font for IDs/numbers, sans for free text.

### Popover
**Use for** "what is EPG?" explainers, confidence breakdowns, GPS tooltip. Background `popover` white, `xl` radius.

### Progress
**Use for** AI confidence bar ("0.91") and upload-sync progress. Track uses `secondary` (Paper); fill uses `primary` (Clinical Blue). Use `destructive` fill when surfacing a failing sync retry.

### Radio Group
**Use for** species override selector (single choice: Ascaris / Trichuris / Hookworm / Artifact). Show JetBrains Mono confidence next to each option.

### Select
**Use for** simple dropdowns ≤ 5 options (magnification: 400× / 1000× / 1200×; shift: AM / PM).

### Sidebar
**Use for** dashboard navigation on tablet/foldable. Hidden on phones — switch to `BottomNavBar` (custom).

### Skeleton
**Use for** image loading state in detection cards and queued-sample list during sync. Base: `muted` (Paper); shimmer: `accent`.

### Slider
**Use for** manual exposure/focus during capture and confidence threshold filter. Active track `primary`, thumb white with Ink ring.

### Sonner (toast)
**Use for** transient confirmations: "Sample queued", "Synced 7 samples". Bottom-center, 4-second auto-dismiss. `destructive` variant for failures. Never use for actions — that's an AlertDialog.

> **Bottom-control overlap.** On screens with a persistent bottom action (e.g.
> `CaptureScreen`'s Start/Stop Recording button), the default bottom-center Sonner
> will overlay the button. Lift the host by `cardPadding * 2` (≈ 48 dp) — or move
> it to top-center for that screen — so the action stays tappable.

### Switch
**Use for** binary settings (Offline mode · Enable GPS · Auto-validate above 0.95). Track on = `primary`, off = `mutedForeground`.

### Tabs
**Use for** segmented sample view: Image · Detections · Metadata · Audit. Underline indicator in `primary`, inactive labels in `mutedForeground`, active in `foreground`.

---

## 6. Custom Components (not in KomoUI — build these)

These live in `ui/components/` and use only the tokens defined above.

| Custom component        | Built from                           | Where it's used                            |
|-------------------------|--------------------------------------|--------------------------------------------|
| `MicroscopyViewport`    | `Box` + `AndroidView(PreviewView)`   | Capture screen, sample detail screen        |
| `DetectionOverlay`      | `Canvas` over `MicroscopyViewport`   | Draws bounding boxes + species labels       |
| `VerificationSheet`     | `Drawer` (`BottomSheet`) + `Pager`   | Verifying / rejecting flagged frames        |
| `DetectionToast`        | `Sonner` variant with "view" action  | "egg detected · 0.91 · Ascaris ▸"          |
| `BottomNavBar` (phone)  | `Surface` + `Row` of `IconButton`    | Phone replacement for `Sidebar`            |

**Phase 2 components** (not built in Phase 1): `EpgReadout`, `BiologicalWindowChip`,
`OfflineQueueBadge`, `GeoMapMarker`, `AuditTimeline`. See
[ADR-002](adr/002-supabase-and-roboflow-for-mvp.md).

---

## 7. Screen → Component Map

### Phase 1 (MVP)

| Screen                | Components Used                                                                                  |
|-----------------------|--------------------------------------------------------------------------------------------------|
| `LoginScreen`         | `Input` · `Button (lg, primary)` · `Sonner` · `Alert (destructive)`                              |
| `CaptureScreen`       | `MicroscopyViewport` · `Button (lg, primary)` (Start/Stop session) · `Badge` (REC) · `DetectionToast` · `VerificationSheet` · `Alert (destructive)` (cloud loss) |
| `VerificationSheet`   | `Drawer` · `MicroscopyViewport` + `DetectionOverlay` · `Progress` (confidence) · `Button (default)` (Verify) · `Button (destructive)` (Reject) |
| `RecordsScreen`       | `Card (resting)` · `Badge` · `Skeleton` · `BottomNavBar`                                         |
| `SessionDetailScreen` | `Card (resting)` · `Tabs` (Image · Detections · Metadata) · `Button (outline)` (Export CSV)      |
| `SampleDetailScreen`  | `Card (elevated)` · `MicroscopyViewport` + `DetectionOverlay` · `Progress` · `Tabs`              |
| `SettingsScreen`      | `Switch` · `Select` · `Input` · `Alert` · `AlertDialog` (sign out)                               |

### Phase 2 (deferred)

`QueueScreen`, `ValidateScreen` (edit/reject flow with audit), `ReportsScreen` (admin
dashboard with charts + DOH PDF export). See [ADR-002](adr/002-supabase-and-roboflow-for-mvp.md).

---

## 8. Do / Don't

**Do**
- Always use tokens (`MaterialTheme.styles.primary`) instead of hardcoded hex.
- Use `JetBrains Mono` for any string that is a *value* (ID, EPG, GPS, confidence, timestamp).
- Use `Geist` for any string that is a *label or sentence*.
- Use `Badge (outline)` for taxonomy tags so they read as data, not action.
- Reserve `destructive` for irreversible loss of data or specimen.

**Don't**
- Don't tint the background of a `Card (elevated)` — keep it pure white.
- Don't use `Button (default)` for navigation. Use `link` or `ghost`.
- Don't recolour Diagnostic Cyan to mean "success" — it's a signal colour.
- Don't introduce new radii. The four-step scale is the system.
- Don't replace `Sonner` with a custom snackbar.

---

*Library: [KomoUI 0.3.0](https://github.com/derangga/komoui) ·
Docs: [shadcn-compose.site](https://shadcn-compose.site) ·
Design: Clinical Pulse moodboard v0.1*
