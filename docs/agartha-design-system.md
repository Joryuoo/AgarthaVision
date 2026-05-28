# AgarthaVision — iOS Design System

**Version:** 1.0
**Last updated:** 2026-05-28
**Platform:** iOS (light mode)
**Aesthetic:** Clinical, iOS-native, glass surfaces, restrained color, monospaced precision for technical data

This document is the single source of truth for designing every screen of the AgarthaVision iOS app. It is written to be consumable by both human designers and AI design assistants. Every value is concrete — hex codes, pixel sizes, font weights, spacing — so a downstream tool can reproduce a screen by reading the relevant section.

---

## Table of Contents

1. [Product Overview](#1-product-overview)
2. [Design Principles](#2-design-principles)
3. [Design Tokens](#3-design-tokens)
4. [Iconography](#4-iconography)
5. [Components](#5-components)
6. [Patterns](#6-patterns)
7. [Screen Specifications](#7-screen-specifications)
8. [Content & Voice](#8-content--voice)
9. [Implementation Notes](#9-implementation-notes)

---

## 1. Product Overview

**AgarthaVision** is an iOS mobile app for medical technologists ("medtechs") performing parasitology examinations. The app runs live AI inference on a microscope feed to detect parasite eggs (helminths) in stool/fecal smear samples, then routes flagged frames into a human-in-the-loop verification queue. All results are organized by patient-level "smear sessions" and synced to a backend record system.

**Primary user:** A licensed medtech in a Philippine health facility (RHU, hospital lab, OPD), often working long shifts under variable lighting and connectivity. Speed, confidence, and clarity matter more than visual flourish.

**Primary jobs to be done:**
- Sign in to a provisioned account
- Start or resume a smear session (one per patient)
- Capture microscope frames — AI auto-flags, medtech can also manual-capture
- Verify each flagged frame (Yes/No questionnaire + species confirmation)
- Browse past sessions and individual samples
- Read out final EPG (Eggs Per Gram) and species composition

**Tech context:** SwiftUI / UIKit, deployed for iPhone (notch + Dynamic Island devices supported). On-device AI inference for the live detection; results sync to Supabase backend when online.

---

## 2. Design Principles

1. **The work is the point.** Chrome (nav, tab bar, buttons) is light translucent glass; the microscope image, the EPG number, and the species are always the loudest things on screen.
2. **Clinical, not consumer.** Restrained brand color, monospaced fonts for technical data, italicized binomial species names. Avoid playful gradients on data; reserve gradients for the brand layer (hero cards, the logomark).
3. **No invented features.** Every interactive element must correspond to an actual app capability. If the AI can't be overridden, don't show an "Edit" button. If a status doesn't exist, don't show a badge for it.
4. **iOS-native first.** When in doubt, do what Apple's first-party apps do: grouped lists for settings, glass tab bar at the bottom, bottom sheets for forms, large titles for tab roots, push-stack for drill-downs.
5. **Numbers are heroes.** EPG, confidence %, frame counts, durations — these get large monospaced display weight. Never make a medtech squint at a tiny percentage.
6. **One color per state.** Brand blue = primary / interactive. Green = verified / synced / live. Amber = pending / warning. Red = danger / destructive. Purple = AI provenance. Don't mix.

---

## 3. Design Tokens

### 3.1 Color Palette

#### Brand

| Token | Hex | Use |
|---|---|---|
| `--brand` | `#1E40AF` | Primary actions, links, active states, logomark |
| `--brand-deep` | `#1E3A8A` | Hover/pressed states, deeper accents |
| `--brand-soft` | `#DBE7FF` | Soft tints on brand-themed cards |
| `--brand-tint` | `#EEF2FF` | Backgrounds for badges, soft buttons |

The brand color is a clinical, slightly desaturated navy-leaning blue. It is **not** iOS systemBlue (`#007AFF`) — that reads too consumer for a medical app.

#### Neutrals (iOS-flavored)

| Token | Hex | Use |
|---|---|---|
| `--ink` | `#0F172A` | Primary text, headings |
| `--ink-soft` | `#1D1D1F` | Slightly softer headings |
| `--body` | `#3C3C43` | Body copy |
| `--muted` | `#6E6E73` | Labels, metadata, secondary text |
| `--subtle` | `#8E8E93` | Placeholder text, inactive icons |
| `--faint` | `#C7C7CC` | Chevrons, disabled |

#### Surfaces

| Token | Hex / Value | Use |
|---|---|---|
| `--bg` | `#F2F2F7` | Screen background (iOS system gray 6) |
| `--surface` | `#FFFFFF` | Cards, list items, sheet surfaces |
| `--grouped` | `#F5F5F7` | Grouped list backgrounds (Settings) |
| `--glass` | `rgba(255, 255, 255, 0.72)` | Floating chrome with blur(40px) saturate(180%) |
| `--glass-strong` | `rgba(255, 255, 255, 0.86)` | More opaque glass (modal headers, dock chrome) |

#### Lines

| Token | Value | Use |
|---|---|---|
| `--hairline` | `rgba(60, 60, 67, 0.12)` | Card borders, section dividers |
| `--hairline-soft` | `rgba(60, 60, 67, 0.08)` | Subtler row separators inside cards |
| `--separator` | `rgba(60, 60, 67, 0.16)` | Stronger separators between major sections |

#### Semantic

| Token | Hex | Use |
|---|---|---|
| `--success` | `#34C759` | Verified, synced, live, confirmed |
| `--success-deep` | `#248A3D` | Success text on light bg |
| `--warning` | `#FF9F0A` | Pending, AI flag |
| `--danger` | `#FF3B30` | Destructive, recording (REC dot), reject |
| `--danger-deep` | `#D70015` | Danger text on light bg |
| `--info` | `#007AFF` | iOS info blue (rare; default to `--brand`) |
| `--ai-accent` | `#AF52DE` | AI-detected provenance (purple) |

### 3.2 Typography

#### Font stacks

```css
--font-display: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Inter", system-ui, sans-serif;
--font-text:    -apple-system, BlinkMacSystemFont, "SF Pro Text", "Inter", system-ui, sans-serif;
--font-mono:    'JetBrains Mono', 'SF Mono', Menlo, ui-monospace, monospace;
```

On Apple devices SF Pro and SF Mono load natively. Inter and JetBrains Mono are web fallbacks.

#### Type scale & usage

| Role | Family | Size | Weight | Letter-spacing | Used on |
|---|---|---|---|---|---|
| Hero number | display | 72px | 800 | -3px | EPG hero on Session Detail |
| Display L | display | 32-36px | 700-800 | -0.8 to -1.4px | Resume card session ID, big EPG |
| Display M | display | 22-28px | 700 | -0.4 to -0.7px | Section h2, sheet titles, large titles |
| Display S | display | 18-22px | 700 | -0.3 to -0.5px | Card headings, species name |
| Body L | text | 17px | 600 | -0.4px | Nav bar titles, status bar time |
| Body M | text | 15px | 400-600 | -0.2px | Default body, list rows, button labels |
| Body S | text | 13-14px | 400-500 | -0.1px | Sub-rows, captions |
| Caption | text | 11-12px | 500 | 0 | Inline meta |
| Eyebrow | mono | 10-11px | 600-700 | 0.8-1.6px UPPERCASE | Section labels, monospace meta |
| Mono L | mono | 22-24px | 600-700 | -0.5 to -0.8px | Counters (324), session IDs (#324) |
| Mono M | mono | 13-15px | 600-700 | -0.1 to -0.3px | Timestamps, telemetry, IDs |
| Mono S | mono | 9-11px | 600-700 | 0.4-1.2px UPPERCASE | Labels above mono values |

#### Special: italic binomial nomenclature

Species names are **always italic** when written in full Linnean form:
- `<i>Ascaris lumbricoides</i>` ✓
- `<i>Trichuris trichiura</i>` ✓
- Common names (Hookworm) — not italic
- Abbreviated form (A. lumbricoides) — italic on the species, not the abbreviation

### 3.3 Spacing & Radii

#### Spacing scale (px)

`2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 48, 56, 72, 88`

Most spacing falls into a few common values:
- `4-8px` — inline gaps, icon-text padding
- `12-16px` — card internal padding, gaps between sibling rows
- `18-22px` — card-to-card vertical rhythm, screen edge padding
- `28-36px` — section-level spacing
- `48-88px` — between major page sections (gallery doc only; never inside a phone)

#### Border radii

| Token | Value | Use |
|---|---|---|
| Tight | 6-8px | Small icon badges, tags |
| Medium | 10-14px | Buttons, input fields, mini cards |
| Card | 14-18px | Standard cards, list items |
| Large | 18-22px | Resume hero card, prominent CTAs |
| Sheet | 28-30px | Bottom sheet top corners |
| Phone | 46px / 56px | Phone screen / phone bezel (mockup only) |
| Pill | 100px | Chips, badges, full pills |

### 3.4 Elevation & Shadows

| Token | Value | Use |
|---|---|---|
| `--shadow-sm` | `0 1px 2px rgba(15, 23, 42, 0.05)` | List rows, subtle cards |
| `--shadow-md` | `0 6px 18px rgba(15, 23, 42, 0.06), 0 1px 3px rgba(15, 23, 42, 0.04)` | Standard cards, modals |
| `--shadow-lg` | `0 24px 60px rgba(15, 23, 42, 0.12), 0 8px 18px rgba(15, 23, 42, 0.05)` | Floating sheets, popovers |
| `--shadow-brand` | `0 6-12px 18-32px rgba(30, 64, 175, 0.22-0.32)` | Primary CTAs, hero cards |

Shadows are always soft and downward; never offset diagonally.

### 3.5 Animation & Motion

- Standard transition: `0.15-0.2s ease`
- Pulse animation (REC dot, live indicator): `1.6s ease-in-out infinite`, scales 1.0 → 0.92 with `box-shadow` expanding ring fading out
- Hover/press: subtle scale 0.97 (record button), no major motion
- Sheet animation: slide up from bottom with overshoot easing (system default)

---

## 4. Iconography

**Library:** SF Symbols (system) when shipping; for design mockups use inline SVG with Lucide / Feather-style outlines.

**Style:**
- Outlined (`fill: none, stroke: currentColor`)
- Stroke width: `1.8` for nav/list, `2-2.5` for emphasis (large icons, status indicators)
- Stroke linecap: `round`
- Stroke linejoin: `round`
- Standard size: 17-22px in list rows, 24-26px in tab bar, 14-18px inside buttons

**Color convention:**
- Default: `--ink` or `--body`
- Active/interactive: `--brand`
- Destructive: `--danger`
- Inside circular icon tiles in Settings: white on colored background

**Standard icons used:**

| Concept | Lucide / SF Symbol |
|---|---|
| Back | chevron-left |
| Close | x |
| Search | search |
| Filter | filter / sliders |
| Plus / new | plus |
| More | more-vertical (3 dots) / ellipsis |
| Check / verified | check |
| AI / sparkle | sparkle (star burst) |
| Camera | camera |
| Camera flip | camera-rotate |
| Photo / image | image |
| Bar chart | bar-chart |
| History | clock-rewind |
| Download / export | download |
| Share | share |
| Settings | settings (gear) |
| Eye | eye |
| Bell | bell |
| Home | house |
| Layers / sessions | layers / stack |
| Cube / records | box / package |
| Sync | refresh-cw |
| Wifi | wifi |
| Trash / delete | trash |
| Edit | edit-2 (pencil square) |
| Play | play |
| Stop | square (filled) |

---

## 5. Components

### 5.1 Navigation

#### 5.1.1 Status bar
- Height: 54px
- Position: top of every screen
- Content: Left "9:41" time (17px, 600, color `--ink`); Right cluster (signal/wifi/battery icons, color `--ink` or `#fff` on dark)
- Padding: `20px 28px 0`

#### 5.1.2 Dynamic Island
- Compact: 126×38px black pill centered at top: 11px
- Live Activity (Recording): expands to include left red pulsing dot + right monospaced time code `00:02:47`
  - Height stays 38px, width grows to fit content
  - Background: `#0A0A0B`
  - Pulsing dot animation defined in §3.5

#### 5.1.3 Nav bar (UINavigationBar)
- Height: 56px
- Top position: 47px (below status bar)
- Background variants:
  - **Glass**: `--glass` with `backdrop-filter: blur(40px) saturate(180%)` + bottom hairline. Default for most screens.
  - **Transparent**: `background: transparent` and no border. Used on Sample Detail (the screen background shows through).
  - **Dark glass**: `rgba(0, 0, 0, 0.45)` + blur. Used on Sample Detail with full-bleed photo.
- Three-column layout:
  - Left (`min-width: 60px`): back button or icon button
  - Center: title (`17px, 600, --ink`, letter-spacing -0.4px)
  - Right (`min-width: 60px`): icon button(s)
- Back button: SF Symbol chevron-left + label in `--brand`, no border

#### 5.1.4 Large title area (alternative to nav bar)
- Used when the screen is a tab root (Sessions, Records)
- Title: 34px display 700, -0.8px letter-spacing
- Sub: 13px, `--muted`, 500
- Padding: 8px 22px 12px

#### 5.1.5 Bottom tab bar
- Height: 83px (10px top padding + ~45px content + 28px home-indicator safe area)
- Background: `--glass-strong` with `blur(40px) saturate(180%)`
- Top hairline border
- 4 equal tabs as `display: grid; grid-template-columns: repeat(4, 1fr)`
- Tab content:
  - Icon: outlined 24×24, stroke-width 1.8
  - Label: 10px, 600
  - Active: color `--brand`
  - Inactive: color `--muted`
- **Tabs:** Today (house icon), Sessions (layers icon), Records (cube icon), Settings (gear icon)

**Where it appears:** Today, Session Picker, Records Dashboard, Settings (the four tab root screens).
**Where it does NOT appear:** Capture (full-immersion), Verification Queue (drill from Capture), Session Detail (drill-down), Sample Detail (drill-down), Modal sheets, Login (pre-auth).

#### 5.1.6 Top segmented tabs (used on Sample Detail)
- Position: directly below nav bar, full width
- 3 equal flex children
- Each tab: 14px vertical padding, brand blue text, 15px / 600
- Active tab: underline indicator — 3px tall bar, brand blue, 25% width margins (so it doesn't span full tab width), bottom-aligned to the hairline
- Inactive tabs: text color `rgba(30, 64, 175, 0.5)` (brand at 50% opacity)
- Bottom hairline border across the whole row

#### 5.1.7 Home indicator
- 134×5px pill, `rgba(0, 0, 0, 0.55)` (light variant: `rgba(255, 255, 255, 0.55)`)
- Position: `bottom: 9px`, centered horizontally
- z-index above main content, below modals

### 5.2 Buttons

#### Primary
- Background: `--brand`
- Color: `#fff`
- Padding: 16px vertical
- Border-radius: 14px
- Font: 16px, 600, -0.2px letter-spacing
- Shadow: `0 6px 18px rgba(30, 64, 175, 0.28), 0 2px 6px rgba(30, 64, 175, 0.18)`
- Icon (if any): 18×18 leading, gap 8px

#### Secondary
- Background: `--brand-tint` (`#EEF2FF`)
- Color: `--brand`
- Same dimensions as primary
- No shadow

#### Ghost / Tertiary
- Background: `rgba(120, 120, 128, 0.12-0.14)` (iOS system gray fill)
- Color: `--ink`
- Used for utility buttons (Filter, Switch camera, Camera flip)

#### Danger (destructive)
- Background: `rgba(255, 59, 48, 0.12-0.14)`
- Color: `--danger`
- Border: `0.5px solid rgba(255, 59, 48, 0.16-0.18)`
- Same dimensions as primary

#### Pill button (small inline action)
- Padding: 7-9px vertical, 12-18px horizontal
- Border-radius: 100px
- Font: 12-14px, 600-700
- Used for tabbed CTAs ("Review", "Review next"), context menu items

### 5.3 Form Inputs

#### Text field
- Background: `#fff`
- Border: `0.5px solid --hairline`
- Border-radius: 14px
- Padding: 15px right, 16px bottom, 15px top, 44px left (for icon)
- Font: 15px, body color
- Leading icon: 18×18, `--muted`, positioned left: 14px
- Placeholder: color `--subtle`
- Shadow: subtle `0 1px 2px rgba(15, 23, 42, 0.04)`

#### Search bar
- Background: `rgba(120, 120, 128, 0.12)` (iOS chrome material thin)
- Border-radius: 12px
- Padding: 9-10px vertical, 12px horizontal
- Leading magnifying glass icon, 16×16, `--muted`
- Placeholder text 15px, `--muted`

#### Dropdown / picker row
- Used inside grouped lists or sheets
- Layout: label on left, value on right, chevron indicator
- Value color: `--brand` (interactive) or `--ink` (static)
- Chevron: `chevron-down` 14×14, `--brand`

#### Yes/No segmented group
- Two inline pill buttons, `display: flex; gap: 8px`
- Each button: 7px vertical, 22px horizontal padding, 10px radius, 13px / 700 font
- Selected: brand background, white text, brand shadow
- Unselected: `rgba(120, 120, 128, 0.14)` background, `--ink` text
- Used for binary questions on Verification Sheet

#### Slider (iOS UISlider style)
- Track: 4px tall, `rgba(120, 120, 128, 0.16)` rail
- Fill: `--brand`, from 0 to value position
- Knob: 24×24 white circle, `0 1px 4px rgba(0,0,0,0.2)` shadow
- Used for AI confidence threshold on Settings

#### Toggle (iOS UISwitch style)
- 51×31px pill
- ON: `--success` background, knob at right (`left: 22px`)
- OFF: `rgba(120, 120, 128, 0.16)` background, knob at left (`left: 2px`)
- Knob: 27×27 white circle with subtle shadow
- Transition: 0.2s

### 5.4 Cards

#### Standard card
- Background: `--surface` (`#fff`)
- Border: `0.5px solid --hairline-soft`
- Border-radius: 14-18px
- Padding: 14-18px
- Shadow: `--shadow-sm`

#### Hero / brand card
- Background: `linear-gradient(135deg, var(--brand) 0%, #3B82F6 50-100%)`
- Color: `#fff`
- Border-radius: 22px
- Padding: 16-18px
- Shadow: brand-tinted, e.g. `0 12px 32px rgba(30, 64, 175, 0.28)`
- Often includes a radial highlight: `radial-gradient(circle, rgba(255,255,255,0.18) 0%, transparent 60%)` in a corner

#### Stat tile (KPI)
- Background: `--surface`
- Border-radius: 14px
- Padding: 11-12px
- Text-align: center
- 3 stacked elements: large number → uppercase mono label → tiny delta tag

### 5.5 Lists (iOS grouped)

#### List section
- Section label: 10-13px uppercase, `--muted`, letter-spacing 1.2px, padding 12-14px / 6px
- Section card: `--surface` background, 14px radius, `0.5px solid --hairline-soft` border
- Section footer (optional): 11-12px, `--muted`, after the card

#### Row
- Padding: 11-13px vertical, 14-16px horizontal
- Bottom border: `0.5px solid --hairline-soft` (except last)
- Layout: optional icon tile → label → trailing value → chevron
- Min height: 44px (iOS HIG touch target)

#### Icon tile (within list row)
- 28-30×28-30px rounded square (7-8px radius)
- Colored background: brand / success / amber / danger / etc.
- White SF Symbol icon centered, 15-17px

#### Disclosure chevron
- `chevron-right` 11-14×17-20px
- Color: `--faint` (`#C7C7CC`)

### 5.6 Sheets & Modals

#### Bottom sheet
- Position: `position: absolute; bottom: 0; left: 0; right: 0`
- Background: `rgba(248, 248, 250, 0.96)` with `backdrop-filter: blur(60px) saturate(180%)`
- Border-radius: 30px on top corners only
- Padding: 8px top, 22px horizontal, 32px bottom
- Shadow: `0 -16px 48px rgba(15, 23, 42, 0.22)`
- Drag handle: 36×5px pill, `rgba(60, 60, 67, 0.3)`, margin 0 auto 14-18px

#### Dim layer (behind sheet)
- `position: absolute; inset: 0`
- Background: `rgba(15, 23, 42, 0.32)`
- `backdrop-filter: blur(6px)`
- z-index: just below sheet

#### Context menu (iOS popover)
- Background: `rgba(248, 248, 250, 0.92)` + `blur(40px) saturate(180%)`
- Border: `0.5px solid --hairline`
- Border-radius: 14px
- Shadow: `0 18px 36px rgba(15, 23, 42, 0.18)`
- Item: 11px vertical / 14px horizontal padding, 15px / 400, bottom hairline
- Destructive item: color `--danger`

### 5.7 Badges & Pills

| Variant | Background | Text | Use |
|---|---|---|---|
| `ai` | `rgba(175, 82, 222, 0.14)` | `#8E3FBF` | AI provenance |
| `manual` | `rgba(120, 120, 128, 0.14)` | `#515154` | Manual capture provenance |
| `verified` | `rgba(52, 199, 89, 0.16)` | `--success-deep` | Verified status |
| `pending` | `rgba(255, 159, 10, 0.18)` | `#B86E00` | Pending review |
| `live` | `rgba(52, 199, 89, 0.16)` | `--success-deep` | Live indicator (with leading green dot, glowing) |

Common spec: 3-4px vertical, 8-9px horizontal padding, 100px radius, 11px / 700 font, 0.3-0.4px letter-spacing, UPPERCASE.

**Important:** There is **no "rejected" badge.** Detections are either verified, pending, or deleted (removed from queue entirely).

### 5.8 Status Indicators

#### Pulsing dot (recording)
- 9×9 circle, `--danger`
- Expanding box-shadow ring fading out
- Animation: `pulse 1.6s ease-in-out infinite`

#### Live dot (sync, status)
- 6-8×6-8 circle, `--success`
- Glowing shadow: `0 0 6-8px var(--success)`

#### Confidence bar
- 100px wide × 6px tall, 100px radius
- Background: `rgba(120, 120, 128, 0.16)`
- Fill: `linear-gradient(90deg, var(--warning), var(--success))` (yellow-to-green for the percentage value)

### 5.9 Charts & Data Viz

#### Sparkline (Today: EPG this week)
- 320×70 viewBox SVG
- Filled area: `linear-gradient(180deg, brand-28% → brand-0%)`
- Line: brand stroke, 2.5 width, rounded caps/joins
- 7 data points marked with white-filled brand-stroked circles (2.5 radius)
- Today's point: larger filled brand circle (5.5 radius) with inset white circle (2.5)
- Grid lines: 3 horizontal lines at 25/50/75%, very faint `rgba(60,60,67,0.07)`
- Axis labels below: 9px mono, `--muted`, centered, with "Today" in brand color and 700 weight

#### Horizontal bar chart (Today: species distribution)
- Each row: 100px label (italic species name) → flex bar track → 32px mono value
- Bar track: 8px tall, 100px radius, light gray
- Bar fill: per-species gradient (e.g. brand → lighter brand, indigo → lighter indigo, purple → lighter purple)

#### EPG hero number
- Display font, 72px, 800 weight, -3px letter-spacing
- Color: `--brand-deep`
- Delta tag inline: smaller badge with `--success-deep` text on green tint, up/down arrow icon

---

## 6. Patterns

### 6.1 Navigation patterns

- **Tab bar persistence:** Tab bar visible on the 4 root screens (Today, Sessions, Records, Settings). Tab bar hidden on detail/drill-down (Session Detail, Sample Detail), full-screen experiences (Capture, Sample Detail Image view if going full-bleed), and all modals/sheets.
- **Push transitions:** Drill-down screens get a back button with the parent screen's name (e.g., "Records" on Session Detail).
- **Modal sheets:** Always slide up from bottom, dim the underlying screen with blur, drag handle at top.
- **Context menu:** Triggered by a "•••" button on rows. Replaces older "long-press" pattern with a more discoverable affordance.

### 6.2 Form patterns

- **Bottom-of-sheet action row:** Always 2 buttons. Secondary on left (smaller, ghost or danger), primary on right (larger, brand-filled). Common pairs:
  - **Verification Sheet:** Cancel + Submit
  - **Manual Sheet:** Discard + Save label
  - Login: (no secondary, full-width Log in)
- **Quick-pick chips:** For high-frequency selections (top 4-5 species). Chip = pill, italic species name, brand-filled when selected.
- **Yes/No questions:** Question text on its own line, Yes/No buttons below. Default state: no answer pre-selected unless there's a clear "expected good" outcome.

### 6.3 Color semantics (canonical reference)

| Color | What it means | Examples |
|---|---|---|
| `--brand` (blue) | Primary, interactive, active state | Buttons, links, active tab, Yes (positive) |
| `--success` (green) | Verified, synced, live, confirmed, good state | Verified badge, sync icon, Live dot |
| `--warning` (amber) | Pending, AI flag, low-confidence | Pending badge, AI bounding box, warning EPG levels |
| `--danger` (red) | Destructive, recording, error | Delete button, REC dot, error states |
| `--ai-accent` (purple) | AI provenance | AI badge, AI bounding boxes when AI source is the emphasis, AI toast |
| `--muted` (gray) | Inactive, secondary, manual provenance | Manual badge, inactive tab text, metadata |

Never use a color outside its semantic meaning. Don't use red just because it's "loud" — use it when something is destructive or recording.

### 6.4 Data formatting

- **Session IDs:** `#324` (mono, hash prefix)
- **Sample IDs / UUIDs:** full UUID in 12px mono, word-break: break-all
- **Timestamps (display):** `2026-05-28 · 22:03` or `19:42:18 · 4032×3024`
- **Durations (capture):** `00:02:47` mono time-code style
- **Percentages:** `94%` mono, no decimal
- **EPG values:** `312` mono (no decimal)
- **Counts:** `142` mono in stat tiles, integer body in card descriptions ("142 captured")
- **Species names:** italic binomial when full (`Ascaris lumbricoides`); common name (Hookworm) not italic; chip-form abbreviated as `A. lumbricoides`

---

## 7. Screen Specifications

### 7.1 Login

**Purpose:** Authenticate a provisioned medtech. Pre-auth screen; no tab bar.

**Layout (top → bottom):**
1. Status bar
2. Dynamic Island (compact)
3. Background: vertical gradient `#FFFFFF → #F0F4FF → #E8EFFF`
4. Brand row (top-left, padding 100px from top): logomark + "AgarthaVision" wordmark with "Parasitology · Mobile" eyebrow below
5. Heading: "Welcome back." (30px display 700, -0.8px tracking)
6. Sub: "Sign in with your provisioned medtech account to begin a session."
7. Email field with email icon + autofill placeholder (e.g., `m.santos@laguna-rhu.ph`)
8. Password field with lock icon + show/hide eye toggle on right + "Forgot?" link in field label row
9. Primary "Log in" button (full width, brand-filled)
10. Security note (subtle brand-tinted info banner with shield icon): "Access is restricted to medical technologists provisioned by your facility's administrator."
11. Footer at bottom: "AgarthaVision v2.4 · Build 1182" + "Need access? Contact your admin" link

**Functionality:**
- Tap "Log in" → authenticate via Supabase → on success navigate to Today
- Email/password fields use iOS UITextField with appropriate keyboard types and `textContentType` for autofill
- "Forgot?" link → password reset flow
- "Contact your admin" link → mailto: or in-app support
- If already authenticated → skip Login, go straight to Today

**States:**
- Loading (after Log in tapped): button shows spinner, fields disabled
- Error: shake animation + red field border + error text below

---

### 7.2 Today (Home Dashboard)

**Purpose:** First screen after login. The medtech's daily briefing. Resume any active session in one tap, scan today's numbers, see weekly EPG trend and species mix, clear the pending verification queue.

**Layout (top → bottom):**
1. Status bar
2. Dynamic Island (compact)
3. Header row (10px / 22px padding):
   - Avatar circle 44×44, brand gradient bg, white "MS" initials in display 700
   - Stack: "GOOD EVENING" (10px mono uppercase, muted) → "M. Santos" (22px display 700) → "Wednesday, May 28 · Day 12" (12px, muted)
   - Notification bell button 38×38 (white circle, hairline border) with red 9×9 badge dot at top-right
4. Active session resume card (margin 0 18px 14):
   - Background: brand gradient with radial highlight
   - "ACTIVE SESSION" mono label (top-left) + "Recording" live pill with pulsing dot (top-right, on white-tinted bg)
   - "#324" session ID in 36px display 800 white
   - "Started 47 min ago · Patient 23424" meta line in white 12px 85% opacity
   - 4 inline mono stats: Frames 142, Verified 87, EPG 312, Pending 12 (each with uppercase 9px label below in 72% opacity white)
   - "▶ Resume capture" full-width white pill button with brand text and play icon
5. KPI row (grid of 4 equal tiles, 6px gap, padding 0 18px):
   - Sessions `3` / `+1`
   - Samples `224` / `+38%`
   - Verified `179` / `80%`
   - EPG avg `203` / `Heavy` (warn-amber delta)
6. "EPG this week" dash card with sparkline chart (see §5.9), 70px tall SVG, 7 day labels below
7. "Top species this week" dash card: 3 horizontal bars (A. lumbricoides 48%, T. trichiura 31%, Hookworm 21%)
8. Pending action card (amber-tinted shadow):
   - Left: 42×42 amber-tinted icon tile with warning icon
   - Body: "12 frames awaiting review" (13px / 700) + "Across 2 sessions · 7 high-confidence" sub
   - Right: brand pill button "Review"
9. Tab bar (Today active, see §5.1.5)

**Functionality:**
- "Resume capture" → Capture screen with active session loaded
- KPI tile tap → opens daily detail (not yet designed)
- Sparkline tap → opens analytics (future)
- Species bar tap → filter Records by that species
- "Review" button → Verification Queue
- Notification bell → opens notifications (future)
- Avatar tap → opens Settings
- Tab bar switch → root screens

**States:**
- No active session: hide Resume card, show "Start new session" CTA in its place
- No KPI data: show "—" in number slots
- No pending review: hide pending action card
- Greeting: changes based on time of day (Good morning / afternoon / evening)

---

### 7.3 Session Picker

**Purpose:** Browse and select a session to work on, or start a new one. Tab root screen.

**Layout:**
1. Status bar, Dynamic Island
2. Large title "Sessions" + sub "Wednesday, May 28 · 3 active" (padding 0 22px 14px)
3. Search bar "Search by ID or label" (iOS chrome material thin style)
4. Session feed (flex 1, padding 0 18px):
   - **Active session card** (elevated treatment):
     - Background: `linear-gradient(180deg, #fff 0%, #F4F7FF 100%)`
     - Border: `0.5px solid rgba(30, 64, 175, 0.18)`
     - Brand-tinted shadow
     - Top row: `#324` mono 22px 700 + `SMEAR-2026-0528` mono 10px uppercase muted + on right: "Active" live badge + "•••" overflow button
     - Sub: "Started 19:16 · 47 min ago" mono 11px muted
     - Body: "Patient 23424 · Fecal smear · Resp ward" 14px body
     - Stat row (border-top hairline): Samples 142, Verified 87 (green), EPG 312, Sync `✓ Live` (right-aligned, green)
   - **Secondary cards** (regular surface, no elevation):
     - Same structure but without the brand tint
     - No active badge, optionally "Paused" or "Draft" status in right-aligned val
5. Floating "+ New session" CTA (above tab bar): full-width brand button with plus icon, `bottom: 95px`
6. Tab bar (Sessions active)

**Context menu state (variant):**
- Underlying feed dimmed to 0.45 opacity
- Tab bar dimmed to 0.45 opacity
- Floating context menu (iOS popover) anchored near "•••" button
- Menu items: Resume (▶), Rename (✏), Export samples (↓), End session (red — destructive)

**Functionality:**
- Tap a session card → navigates to Capture (if Active) or Session Detail (if older/completed)
- "•••" overflow → context menu
- "+ New session" → opens new session creation flow (not yet detailed)
- Search → filter feed in real time
- Tab bar persists

**States:**
- Empty (no sessions yet): centered illustration + "Start your first session" CTA
- Loading: skeleton placeholders for first 2-3 cards
- Search returning nothing: "No matches" empty state

---

### 7.4 Capture

**Purpose:** The core microscopy capture screen. Live AI inference runs over the camera feed and surfaces detections. Manual capture button available. This is where the medtech spends most of their session time.

**Layout:**
1. Background: full-bleed live camera feed (microscope through phone camera)
2. Status bar (light-mode chrome over photo): time + status icons in `--ink`
3. Dynamic Island with **expanded Live Activity**: red pulsing dot + monospaced timecode `00:02:47`
4. Top chrome (glass, floating, padding 12px / 20px):
   - Counter block: "CAPTURED" 10px mono uppercase label → row: 24px mono `324` + small 11px `frames` muted
   - Icon group (3 icons, each 38×38 rounded with subtle gray fill): Bar chart (stats), Clock-rewind (history), Check-square (export)
5. AI detection toast (centered, top: 130px): purple pill with sparkle icon + species name + confidence chip
6. Side rail of chips (right edge, top: 184): "4K · 60 fps" (green dot prefix), "AI · auto" (sparkle prefix)
7. Focus reticle (decorative): 84×84 square outline, center of viewfinder
8. Bottom capture dock (glass, floating, bottom: 28px):
   - Meta row (border-bottom hairline): "Live" badge + "Session #324 · 142 captured" / right: "2.3 GB · 47%" mono
   - Controls grid (1fr auto 1fr):
     - Left: gallery/photos icon button 52×52
     - Center: **Record button** — 72×72 white circle with brand-tinted record indicator (red rounded square 30×30 inside) and 4px transparent black border ring
     - Right: "End Session" pill — red-tinted with red text, stop square icon
9. Home indicator (light variant — over photo background)

**Functionality:**
- Tap record button → captures a frame manually (queue as "Manual" provenance)
- Tap "End Session" → confirmation dialog → ends session, returns to Today
- Tap "Live" badge → could expand to show session details
- Tap AI toast → opens Verification Queue, optionally jumping to the just-detected frame
- Tap counter or stats icon → opens stats overlay
- Tap clock-rewind → opens Verification Queue (recent flags)
- Tap export icon → exports current session frames

**Tab bar:** HIDDEN (full-immersion screen).

**States:**
- Idle (no recording): no Live Activity in Dynamic Island; "Start Session" CTA in place of "End Session"
- AI detection: toast appears for 3 seconds with detected species + confidence
- Low storage: warning chip appears in bottom dock
- Bad focus: warning toast at top

---

### 7.5 Verification Queue

**Purpose:** Inbox of all flagged frames from the current session (both AI detections and manual captures), with status indicators. Reached from Capture or the pending action card on Today.

**Layout:**
1. Status bar, Dynamic Island (compact)
2. Nav bar (glass): Left "← Capture" / Center "Verification" / Right filter icon
3. Stats card (margin 4 18 14):
   - Left: `47` (display 700) + "FLAGGED FRAMES" (mono uppercase label)
   - Right breakdown: 3 small rows with colored dot, label, mono count
     - Purple dot · AI detected · 31
     - Gray dot · Manual · 16
     - Amber dot · Pending review · 12
4. Filter chips row (overflowing horizontal): All (active, dark pill) / Pending / AI / Manual — each with count appended
5. Verify list (padding 0 18 100):
   - Each row: 64×64 thumbnail with AI bounding box overlay (color-coded by status) → body: species (italic), Frame # · confidence% · timestamp (mono) → status badges (AI/Manual + Pending/Verified) → right chevron
   - Example rows:
     - `Ascaris lumbricoides` · Frame 324 · 94% · 19:42 · [AI] [PENDING]
     - `Trichuris trichiura` · Frame 287 · 88% · 19:38 · [AI] [VERIFIED]
     - `Unlabeled` (muted) · Frame 263 · — · 19:31 · [MANUAL] [PENDING]
     - `Hookworm` · Frame 241 · 62% · 19:26 · [AI] [PENDING]
     - `Ascaris lumbricoides` · Frame 198 · 96% · 19:18 · [AI] [VERIFIED]
6. Floating action bar (glass, bottom: 28):
   - Left: "12 pending" (14px / 600 ink)
   - Right: "Filter" (ghost) + "Review next" (primary)

**Note:** **There is no "Rejected" status.** Detections are either pending, verified, or deleted entirely.

**Functionality:**
- Row tap → opens Verification Sheet (for AI) or Manual Sheet (for Manual provenance)
- Filter chip tap → filter list
- Filter icon (top-right) → advanced filter modal
- "Review next" → opens Verification Sheet for the next pending frame

**Tab bar:** HIDDEN (drill-down from Capture).

**States:**
- All verified (zero pending): empty state with "All caught up" message
- Only AI / only Manual / etc.: list filtered accordingly

---

### 7.6 Verification Sheet (modal)

**Purpose:** A guided questionnaire for confirming or rejecting an AI detection. Replaces "edit the bounding box" interfaces — the medtech can't adjust the bounding box, they answer structured questions about it. Submitted answers train the next model version.

**Layout (bottom sheet):**
1. Drag handle (centered, 36×5)
2. Title row: "Verify detection" (22px display 700) + "FRAME 324" (mono pill, surface bg, hairline border)
3. Image preview (170px tall, 18px radius card):
   - Microscope frame
   - Amber bounding box outline (no edit handles — user cannot adjust)
   - Top-right tag: "✨ AI · 96%" (purple)
   - Bottom-left tag: "19:42:18 · Trichuris trichiura" (mono dark pill)
4. **Model prediction summary card** (`.model-pred`):
   - "MODEL PREDICTED" mono uppercase label / Species in italic brand color
   - Confidence bar (flex) + "96%" mono value
5. **Yes/No questions card** (`.q-card`, 3 rows):
   - "Is there a parasitic egg in this bounding box?" → [Yes selected] [No]
   - "Is the bounding box correctly placed?" → [Yes selected] [No]
   - "Did the model miss any eggs in this frame?" → [Yes] [No selected — neutral gray, not brand]
6. **Species row** (sheet-row card):
   - "Species" label / Italic brand value "Trichuris trichiura" with chevron-down (dropdown)
7. **Action row** (2-column grid, 10px gap):
   - Cancel button (red-tinted ghost)
   - Submit button (brand-filled primary)

**Functionality:**
- Yes/No buttons toggle answer
- Species row → opens species picker (sheet within sheet, or push to full list)
- Submit → records answers, updates frame status to Verified (or Rejected internally based on Q1 answer), closes sheet
- Cancel → closes sheet without saving

**Important design notes:**
- The bounding box image is **read-only** — no corner handles, no resize affordance
- The "no" answer on "missed any eggs" uses a neutral gray (not red) because "no" here means "no problem"
- Confidence bar shows the AI's confidence; the user is answering whether they agree, not editing the confidence

**Tab bar:** HIDDEN (modal overlay).

---

### 7.7 Manual Sheet (modal)

**Purpose:** Labeling a manually-captured frame. Simpler than Verification Sheet because there's no AI prediction to verify — just species selection and note.

**Layout (bottom sheet):**
1. Drag handle
2. Title: "Label sample" + "MANUAL · 263" pill (gray-tinted)
3. Image preview (200px tall):
   - Microscope frame (no bounding box)
   - Top-right: "📷 Manual capture" tag (gray-tinted)
   - Bottom-left: "19:31:04 · 4032×3024 · Manual" mono pill
4. Species section:
   - Mono uppercase label "SPECIES"
   - Quick-pick chips row (5 chips, wrap): `A. lumbricoides`, `T. trichiura` (selected — brand-filled), `Hookworm`, `S. mansoni`, `Other…` (last one not italic, marks "open picker")
5. Note section (`.sheet-notes`):
   - "NOTE" mono uppercase label
   - Body text (filled example): "Slight barrel shape, polar plugs visible. Confirmed against atlas plate 14."
6. Action row:
   - Discard (red-tinted ghost)
   - Save label (brand-filled primary)

**Functionality:**
- Chip tap → select species
- "Other…" → opens full species picker
- Note tap → opens text input
- Save → records label, updates frame status, closes sheet
- Discard → closes sheet without saving the label (frame returns to queue unlabeled)

**Design notes:**
- No Stage / Count fields — the app does not collect those
- "Discard" instead of "Cancel" because the user was actively labeling (creating something) and is throwing that work away

**Tab bar:** HIDDEN (modal overlay).

---

### 7.8 Records Dashboard

**Purpose:** Historical browser of all sessions. Search, filter by species, see weekly performance at a glance. Tab root screen.

**Layout:**
1. Status bar, Dynamic Island
2. Nav bar (glass): Left home icon (returns to Today) / Center "Records" / Right download/export icon
3. Overview cards row (grid 1.5fr 1fr 1fr, 8px gap, margin 6 18 12):
   - **Brand-gradient card** (largest): "THIS WEEK" / `42` / "Sessions completed"
   - White card: "SAMPLES" / `3,184` / "+12%" (green delta)
   - White card: "AVG EPG" / `218` / "7-day"
4. Search bar "Search by ID, patient, or species"
5. Filter chips row: All (dark active), A. lumbricoides, T. trichiura, Hookworm
6. Grouped records list (overflow hidden, padding 0 18 95 — bottom 95 to clear tab bar):
   - Day separator: "TODAY · MAY 28" mono uppercase 10px label
   - Record rows:
     - Layout: body (id mono + meta + species tags) | EPG block (right-aligned big mono number + "EPG" label) | sync icon
     - Color coding for EPG: high values red (`#FF3B30`), medium amber (`#FF9F0A`), low/zero green (`--success-deep`)
     - Sync icon: green check-circle if synced, amber refresh-cw if pending, gray if offline
   - Day separator: "YESTERDAY · MAY 27"
   - More records
7. Tab bar (Records active)

**Functionality:**
- Row tap → Session Detail
- Search → filter
- Filter chips → species filter
- Export icon → export sessions
- Tab bar persists

**States:**
- No records: empty illustration + "No sessions yet" message
- Search empty: "No matches"

---

### 7.9 Session Detail

**Purpose:** Detail view of a single session. EPG hero number, session metadata, gallery of all verified samples.

**Layout:**
1. Status bar, Dynamic Island
2. Nav bar (glass): Left "← Records" back / Center "Session #324" / Right download icon
3. **Session hero card** (margin 4 18 14, 22px radius):
   - Background: light brand-tinted gradient
   - Soft radial highlight in top-right corner
   - Top: "EGGS PER GRAM" 10px mono uppercase brand label
   - Hero number: `312` 72px display 800, brand-deep color
   - Inline delta tag: green badge "↑ Heavy" with arrow icon
   - Body: "Total confirmed eggs: 89 · 87 verified samples" 13px body muted
   - Stat row (border-top): Captured 142, Verified 87, Duration 12m, Synced ●
4. **Metadata card** (4 rows, 14 16 vertical padding each):
   - Patient: `23424` (mono)
   - Started: `19:16` (mono)
   - Operator: `M. Santos` (text)
   - Location: `Laguna RHU · Resp. ward` (text)
5. Gallery header: "Samples" h3 + "See all (87)" brand link
6. Gallery grid (3 cols, 4px gap, padding 0 18):
   - Square thumbnails of microscope frames
   - Each tile has a top-left mini badge (AI 94% with purple bg, or MAN with gray bg)
   - White corner indicators on the bbox

**Functionality:**
- Tile tap → Sample Detail
- "See all" → full gallery view
- Download → export PDF report

**Tab bar:** HIDDEN (drill-down).

---

### 7.10 Sample Detail · Image tab

**Purpose:** View the full microscope image of a single sample. First tab of Sample Detail.

**Layout:**
1. Status bar, Dynamic Island
2. Nav bar (transparent — bg shows through): Left chevron-left back / Center "Sample Detail" / Right empty
3. **Top segmented tabs**: Image (active, underlined) / Detections (inactive) / Metadata (inactive)
4. Content area (light gray bg, padding 18px):
   - **Image card** (white, 16px radius, 14px padding):
     - 4:3 aspect-ratio microscope frame, 10px inner radius, inset hairline
     - If AI detection: green bounding box overlay with `Species · 96%` tag above
     - If manual capture: no bounding box
     - Below the image: capture metadata row — "CAPTURED" label / "2026-05-28 · 22:03 · 4032×3024" mono value

**Functionality:**
- Pinch zoom on image
- Tab tap → switch tab
- Back → returns to Session Detail
- Image long-press → save to camera roll

**Tab bar:** HIDDEN (drill-down).

---

### 7.11 Sample Detail · Detections tab

**Purpose:** Per-detection record cards.

**Layout:**
- Same nav bar and top tabs as Image tab
- Content area:
  - **Detection card**(s) — one per detection:
    - Header row: "DETECTION 1" mono uppercase label / provenance badge (AI or MANUAL)
    - Species (display 700 italic, 22px): "Hookworm"
    - Field rows (4 rows, hairline separators):
      - "Confidence" / `100%` mono
      - "Verdict" / `✓ Confirmed` (green, with check icon)
      - "Bounding box" / `None · manual capture` (italic muted)

**Functionality:**
- One card per detection in the sample
- For samples with multiple detections, cards stack vertically
- No interactions besides scrolling

**Tab bar:** HIDDEN.

---

### 7.12 Sample Detail · Metadata tab

**Purpose:** Complete technical metadata for the sample — IDs, timestamps, device info, storage path.

**Layout:**
- Same nav bar and top tabs as Image tab
- Content (no horizontal padding on container, groups handle their own margins):

**Group 1: Identity**
- Sample ID: `3465fc00-85b5-4e12-b39f-0cc87e71c121`
- Session ID: `83359c2f-e17e-4354-a291-571ddac04bc0`
- Device ID: `107af6652ba9549b`

**Group 2: Status & timing**
- Status: `Synced` (green dot with glow)
- Captured at: `2026-05-28 · 22:03`
- Verified at: `2026-05-28 · 22:10`
- User note: `None` (italic muted if empty)

**Group 3: Capture**
- GPS: `Not recorded` (italic muted)
- Model version: `Manual capture`
- Storage path: `1c51605b-af11-422a-9d8d-3c27ad7a9eac/3465fc00-85b5-4e12-b39f-0cc87e71c121.jpg` (smaller font, 11px, word-break: break-all)

Each row spec:
- Padding: 10px vertical, 14px horizontal
- Label: 10px mono uppercase, muted color, 4px margin-bottom
- Value: 12-13px mono, ink color, word-break: break-all
- Bottom hairline (except last)

**Functionality:**
- Tap-and-hold a value (UUID, path) → copy to clipboard (iOS context menu)

**Tab bar:** HIDDEN.

---

### 7.13 Settings

**Purpose:** App configuration. Profile, capture preferences, AI settings, sync, about. Tab root screen.

**Layout:**
1. Status bar, Dynamic Island
2. Nav bar (glass): empty left / Center "Settings" / Right info icon
3. Profile card (white, 18px radius, 16-18px padding):
   - Avatar 56×56 brand-gradient circle, white "MS" initials (display 22px 700)
   - Body: "M. Santos" (17px 700) → email mono small → role pill "🛡 MEDTECH II"
   - Right: chevron
4. **Group: Capture**
   - **Slider row**: "AI confidence threshold" + "75%" brand-colored mono on right; horizontal slider below
   - Toggle row: purple icon (sparkle) + "Live detection overlays" + green toggle ON
   - Disclosure row: teal icon (camera) + "Capture resolution" + "4K · 60fps" mono trailing + chevron
5. **Group: AI Inference**
   - Toggle: indigo icon (cube) + "On-device inference" + ON
   - Toggle: gray icon (×) + "Server fallback" + OFF
   - Disclosure: purple icon (globe) + "Model" + "v2.4 · Helminth" mono + chevron
6. **Group: Sync & Storage** (with footer)
   - Toggle: green icon (refresh) + "Auto sync" + ON
   - Toggle: amber icon (wifi) + "Wi-Fi only" + ON
   - Footer text: "Last synced 47 seconds ago · 2.3 GB cached locally."
7. Tab bar (Settings active)

**Functionality:**
- Toggles change preferences immediately
- Slider drag → updates AI confidence threshold (affects what surfaces in queue)
- Profile row tap → opens account screen (future)
- Disclosure rows → navigate to detail screens

**Tab bar:** Settings tab active.

---

## 8. Content & Voice

### 8.1 Voice principles

- **Direct and clinical.** Medtechs are busy professionals; they want information density, not encouragement.
- **Concise.** "12 frames awaiting review" not "You have twelve detections that need your attention."
- **Trustworthy.** "Model predicted Trichuris trichiura" not "AI thinks this might be...".
- **No emoji.** No marketing fluff. No exclamation points.

### 8.2 Terminology (canonical)

| Use this | Not this |
|---|---|
| Session | Encounter, exam |
| Sample | Image, frame (when stored), capture |
| Frame | Photo, snapshot |
| Detection | Match, hit, result |
| Species | Type, kind, parasite type |
| Verified | Confirmed, validated, approved |
| Pending | Unreviewed, queued |
| Delete | Reject, dismiss, discard (Discard is reserved for unsaved work in Manual Sheet) |
| EPG | Eggs/g, eggs per gram |
| Confidence | Score, certainty, likelihood |
| Medtech | User, operator, technician, lab tech |
| Bounding box | Box, region, ROI |
| Provenance: AI vs Manual | Source, origin |

### 8.3 Microcopy examples

- Empty session list: "Start your first session"
- All verified: "All caught up" (not "No items")
- Sync done: "Synced" (not "Successfully synced")
- Recording: "Recording" (not "Now recording")
- Error: state the constraint, not the failure — "Sign-in requires an internet connection." not "Login failed."

---

## 9. Implementation Notes

### 9.1 iOS technical conventions

- Use **SF Pro Display / Text / Rounded** via `Font.system` or `UIFont.preferredFont(forTextStyle:)`
- **SF Symbols** for all standard icons (search, chevron, ellipsis, settings, etc.). Custom SVG only for logomark and any domain-specific glyphs (microscope, parasite icon if added).
- **UIBlurEffect(style: .systemMaterial)** for glass surfaces; .systemUltraThinMaterial for lighter chrome
- **Dynamic Type** support: scale display sizes; mono fonts can stay fixed for technical data legibility
- **Dark mode**: not currently designed, but tokens should be defined per-mode (e.g., `--bg` dark = `#1C1C1E`); follow iOS HIG for dark mode treatments

### 9.2 Accessibility

- All interactive elements ≥ 44×44pt touch target
- Color contrast: text on `--bg` should be ≥ 4.5:1 (body) and 3:1 (large display)
- Mono fonts can fall below text contrast for purely decorative labels; ensure values are still legible
- VoiceOver labels for every icon button
- Reduce Motion: disable pulse animations on REC dot and live indicator when enabled

### 9.3 Brand asset

The AgarthaVision logomark is a lung-and-eye symbol:
- Two blue lung shapes flanking a central eye
- A small blue trachea (vertical rectangle) at top center
- The eye: white ring outer, dark iris (#0E0E12), small white catchlight upper-left

Three SVG variants exist:
- `agartha-logo.svg` — with light rounded-square background (web favicons, marketing)
- `agartha-logo-transparent.svg` — no background (overlay on any surface)
- `agartha-logo-appicon.svg` — 1024×1024 full-bleed (App Store submission)

Brand color: `#1F66FF` (slightly different from `--brand: #1E40AF` — the logomark uses the more vibrant blue; the app chrome uses the deeper clinical blue).

### 9.4 Where this design system lives

- Source of truth: this document
- Reference mockup gallery: `agartha-redesign.html` (visual prototype of all screens)
- Logo SVGs: `agartha-logo*.svg`

When generating new designs from this document, prioritize matching the patterns and tokens defined here. If an existing screen conflicts with this doc, the doc wins. If a new screen needs a pattern not defined here, extend the doc rather than diverge.

---

## Appendix A: Quick reference card

```
PRIMARY BLUE:    #1E40AF
DEEP BLUE:       #1E3A8A
LOGO BLUE:       #1F66FF
SUCCESS GREEN:   #34C759
WARNING AMBER:   #FF9F0A
DANGER RED:      #FF3B30
AI PURPLE:       #AF52DE
INK:             #0F172A
BODY:            #3C3C43
MUTED:           #6E6E73
BG:              #F2F2F7
SURFACE:         #FFFFFF
HAIRLINE:        rgba(60, 60, 67, 0.12)
GLASS:           rgba(255, 255, 255, 0.72) + blur(40px) saturate(180%)
SHADOW SM:       0 1px 2px rgba(15, 23, 42, 0.05)
SHADOW MD:       0 6px 18px rgba(15, 23, 42, 0.06)
SHADOW BRAND:    0 8-12px 24-32px rgba(30, 64, 175, 0.22-0.32)

PHONE:           390 × 844 (iPhone 15/14/13 Pro)
SCREEN RADIUS:   46px (inner) / 56px (outer bezel)
STATUS BAR:      54px tall
NAV BAR:         56px tall, top: 47px
DYNAMIC ISLAND:  126 × 38 (compact), expands for Live Activities
TAB BAR:         83px tall, includes 28px home indicator safe area
HOME INDICATOR:  134 × 5 pill at bottom: 9px

CARD RADIUS:     14-18px (standard) / 22px (hero)
BUTTON RADIUS:   12-14px
PILL RADIUS:     100px
SHEET RADIUS:    30px (top corners only)

CANONICAL SPACES: 4 · 8 · 12 · 14 · 16 · 18 · 22 · 28 · 36
```