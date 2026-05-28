# AgarthaVision · System Design

A complete design specification for the AgarthaVision clinical microscopy mobile app, derived from the interactive HTML prototype.

---

## Table of contents

1. [Product overview](#1-product-overview)
2. [Design principles](#2-design-principles)
3. [Design tokens](#3-design-tokens)
4. [Information architecture](#4-information-architecture)
5. [Navigation](#5-navigation)
6. [Screen specifications](#6-screen-specifications)
7. [Modal sheets](#7-modal-sheets)
8. [Component library](#8-component-library)
9. [Interaction patterns](#9-interaction-patterns)
10. [Brand](#10-brand)
11. [Implementation notes](#11-implementation-notes)

---

## 1. Product overview

**AgarthaVision** is a mobile clinical microscopy assistant for medical technologists working with parasitic stool samples. It pairs real-time AI parasite detection on the microscope feed with a structured workflow for capturing, verifying, and archiving findings.

**Primary user**: medical technologists in clinical labs and field sites.

**Core jobs to be done**:

1. Capture parasitic egg detections during microscope examination.
2. Verify AI detections (confirm / reject) to build clean clinical data.
3. Manually flag specimens the AI missed.
4. Review past sessions and individual sample details.
5. Generate clinical reports (eggs per gram, species mix).

**Platform**: iOS / Android native. The prototype is a mobile-frame HTML mockup at 390×844 (iPhone 14 reference).

---

## 2. Design principles

1. **Clinical clean, not playful.** Restrained whitespace, hairline borders, no decorative ornament.
2. **Single accent color.** A cobalt blue used sparingly for primary actions, active states, focus rings, and AI bounding boxes. Semantic colors (red, green, amber) only for specific states.
3. **Tabular numerals for all data.** Session IDs, timestamps, GPS coords, confidence scores, and EPG counts align in columns.
4. **Italicized binomial nomenclature.** Proper scientific typography — *Ascaris lumbricoides*, not Ascaris lumbricoides.
5. **Tool mode vs. browse mode.** The Capture screen is dark and immersive (camera tool); every other screen is light and structured (browsing).
6. **No heavy shadows.** Flat design. Hairline borders separate elements. Only modal sheets and the Active Session hero use real shadows.
7. **No charting library.** Small data viz is hand-crafted inline SVG to keep the file self-contained and consistent with the rest of the UI.

---

## 3. Design tokens

### 3.1 Color

```
Brand
  --blue          #1E3FD9   Primary accent — CTAs, active states, focus rings, AI bboxes
  --blue-hover    #1A36BF
  --blue-pressed  #15309F
  --blue-tint     #E6EBFC   Active card backgrounds
  --blue-tint-2   #F1F4FE   Hint/info banner backgrounds

Neutrals
  --white         #FFFFFF
  --off-white     #FAFBFC
  --gray-50       #F7F8FA   Subtle surfaces
  --gray-100      #EEF0F4   Dividers, light borders
  --gray-200      #E2E5EB   Standard borders
  --gray-300      #CBD0DA   Chevrons, disabled icons
  --gray-400      #9CA3AF
  --gray-500      #6B7280   Secondary text
  --gray-700      #374151   Standard body text
  --gray-900      #0F172A   Primary text

Semantic
  --red           #DC2626   Destructive, REC indicator, errors
  --red-tint      #FEE2E2
  --green         #16A34A   Sync OK, confirmed detections
  --green-tint   #DCFCE7
  --amber         #D97706   Manual captures, pending review, sync warning
  --amber-tint    #FEF3C7
```

> The logo uses a slightly brighter blue (`#036BFC`) than the app accent (`#1E3FD9`). Both are kept distinct on purpose — the logo retains its standalone identity, the app uses a deeper, more "clinical" shade for actions.

### 3.2 Typography

**Font**: Inter (Google Fonts), with system fallback.
**Features**: `cv11`, `ss01`, `ss03`. Tabular numerals enabled on all data fields.

| Style | Size / line | Weight | Used for |
|---|---|---|---|
| Display | 32 / 40 | 700 | Hero numbers (EPG count, large session IDs) |
| Headline | 22 / 28 | 700 | App bar titles |
| Subhead | 17 / 22 | 700 | Card titles, greeting |
| Body | 15 / 22 | 500 | Default text |
| Label | 13 / 18 | 500 | Form labels |
| Caption | 12 / 16 | 500 | Metadata, timestamps |
| Micro / eyebrow | 10–11 / 14 | 600 | Uppercase section labels |

- Italic for *binomial species names*.
- Tabular numerals on every data field that displays numbers in a column.

### 3.3 Spacing scale

`4 · 8 · 12 · 16 · 20 · 24 · 32 · 48` (px). 8-px grid.

### 3.4 Radius

| Token | Value | Used for |
|---|---|---|
| `--radius-sm` | 8px | Inputs, tiny tiles |
| `--radius-md` | 12px | Cards, list items |
| `--radius-lg` | 16px | Hero cards, modal sheet top corners |
| `--radius-pill` | 999px | Buttons, badges, chips |

### 3.5 Elevation

| Surface | Treatment |
|---|---|
| Plain card | No shadow; 1px hairline border (`--gray-100`) |
| Active Session hero | `0 10px 28px -10px rgba(30, 63, 217, 0.45)` |
| Modal sheet | `0 -16px 32px -8px rgba(15, 23, 42, 0.12)` |
| Floating glass UI (Capture) | `0 4px 14px -4px rgba(0, 0, 0, 0.35)` |
| Shutter button | `0 6px 22px -2px rgba(30, 63, 217, 0.5), 0 0 0 1.5px rgba(0, 0, 0, 0.5)` |

### 3.6 Motion

| Pattern | Duration / curve |
|---|---|
| Default UI transition | 150ms ease |
| Sheet slide-up | 320ms cubic-bezier(0.32, 0.72, 0, 1) |
| Scrim fade | 250ms ease |
| Toast slide-down | 400ms cubic-bezier(0.32, 0.72, 0, 1) |
| Pulse (REC dot) | 1500ms ease-in-out, infinite |
| Recording halo | 1500ms recPulse keyframes |

---

## 4. Information architecture

### 4.1 Screen list

| # | Screen | Purpose |
|---|---|---|
| 1 | Login | Authentication entry point |
| 2 | Dashboard | Home / today's activity, lands after sign-in |
| 3 | Session Picker | Pick or start a session |
| 4 | Capture | Live microscope feed with AI detection |
| 5 | Verify Queue | List of flagged frames awaiting review |
| 6 | Records | Historical session browser |
| 7 | Session Detail | One session's results gallery |
| 7b | Session Detail (empty) | Variant for sessions with no findings |
| 8 | Sample Detail | One sample image with metadata |
| 9 | Settings | App config, account |

### 4.2 Modal sheets

| Sheet | Trigger | Purpose |
|---|---|---|
| New Session | Sessions tab → "+ New session" | Set label + note before scanning |
| Verify (AI) | Capture toast Review, Verify Queue AI rows | Confirm/reject AI detection, change species |
| Manual | Verify Queue manual rows | Label a manually captured frame |

### 4.3 Primary flow (happy path)

```
Login
  → Dashboard
       Active Session hero  →  Capture
                                 ↓
                          AI detects egg
                                 ↓
                          Toast "Review"  →  Verify sheet  →  Confirm
                                                              ↓
                                                          Sample saved
                                 ↓
                          End session     →  Session Detail
                                              ↓
                                          Sample Detail
```

### 4.4 Browse flow

```
Dashboard  →  Records  →  Session Detail  →  Sample Detail
       ↘
        Records tab (bottom nav)  →  same flow
```

### 4.5 New session flow

```
Sessions tab  →  + New session  →  New Session sheet
                                      ↓
                                Fill label + note  →  Start session  →  Capture
```

---

## 5. Navigation

### 5.1 Bottom tab bar

Persistent on every screen except **Login** (no auth) and **Capture** (immersive tool mode).

```
Home  ·  Sessions  ·  Records  ·  Settings
```

- Active tab: blue icon + blue label.
- Inactive: `--gray-500` icon, `--gray-700` label.
- Bar height: 64px content + 18px iOS home indicator strip.
- iOS home indicator: 134×4 dark pill at the bottom, 55% opacity.

Each tab owns a primary screen + its drill-downs:

| Tab | Owns | Drill-down preserves tab |
|---|---|---|
| Home | Dashboard | n/a |
| Sessions | Session Picker, Capture, Verify Queue | Yes |
| Records | Records, Session Detail, Sample Detail | Yes |
| Settings | Settings | n/a |

### 5.2 Top app bar pattern

```
[← Back?]  [Title]                       [Icon actions]
           [Sub-meta (caption, tabular)]
```

- Back arrow shown on drill-down screens.
- Title: Headline 22/28.
- Sub-meta: Caption 12, `--gray-500`, tabular numerals.

### 5.3 Modal sheet behavior

- Slide up from bottom over the current screen + tab bar.
- Drag handle (gray pill) at the top.
- Scrim: `rgba(15, 23, 42, 0.4)` with 2px backdrop blur.
- Header: title + optional subtitle + close button (× in gray-100 circle).
- Body: scrollable.
- Footer: 1–2 buttons, sticky, with top hairline border.
- Max height: 86% of phone height.

---

## 6. Screen specifications

### 6.1 Login

**Purpose**: authenticate the technologist.

**Layout** (centered vertical stack, 28px horizontal padding):

- Status bar (light, dark text).
- 32px breathing space.
- **App mark**: 64×64, 16px radius, AgarthaVision logo on its cream canvas, soft drop shadow.
- 28px gap.
- **Title**: "AgarthaVision" — display 30, -0.025em letter-spacing.
- **Subtitle**: "Sign in to continue your clinical work." — body, `--gray-500`.
- 32px gap.
- **Email** input (pre-filled `tech@agarthavision.app` for demo).
- **Password** input (masked).
- **Primary CTA**: "Sign in" — pill, full-width.
- Bottom: "Forgot password?" — blue text link.

**Tab bar**: hidden.

**Navigation**: Sign in → Dashboard.

**Future states**: loading state on Sign in; error state on auth failure (red banner above form).

---

### 6.2 Dashboard

**Purpose**: at-a-glance overview of today's activity + entry into the active session.

**Layout** (top → bottom, scrollable):

#### 1. Personalized top bar
- 40×40 blue avatar circle with "MR" initials.
- Greeting: "Good evening, Maria" — subhead 17.
- Date: "Thursday · May 28" — caption, `--gray-500`, tabular.
- Right: bell icon with red unread dot.

#### 2. Active Session hero card *(when a session is active)*
- Full-width card with gradient blue background (`#1E3FD9 → #2A56E8 → #3B68F5`), 16px radius.
- Decorative radial highlights top-right and bottom-left.
- Status badge: pulsing white dot + "LIVE SESSION" (micro caps, 0.12em letter-spacing).
- Session ID: display 32, tabular bold.
- Meta: "**00:12:34** elapsed · **147** frames" with bold values.
- Right: 56×56 white circle "Resume" button with blue play icon.
- Tap → Capture screen.

#### 3. Today's activity KPI grid (2×2)
- **Sessions** · **Eggs found** · **Pending** · **Samples**.
- Each tile: 11px gray-500 label, 28px tabular value, 11px trend caption.
- Up trends in green with ↑ arrow.

#### 4. 7-day activity sparkline card
- Hand-crafted inline SVG: 320×56 viewBox.
- Blue line over soft blue gradient fill (18% → 0% opacity).
- White-bordered start dot, larger filled end dot.
- Weekday axis: Thu · Fri · Sat · Sun · Mon · Tue · Today (Today in gray-700, others in gray-400).
- Header: "7-day activity" + "Eggs found per day" + green "↑ 38%" delta pill.

#### 5. Today's findings card (species mix)
- Single horizontal segmented bar — 10px tall, pill-shaped.
- 3 colored segments proportional to species counts: Ascaris blue (64.3%), Trichuris cyan #0EA5E9 (21.4%), Necator amber (14.3%).
- 2px white separators between segments.
- Legend row: colored dots + italic species names + bold tabular counts.

#### 6. Recent sessions (3 mini-cards)
- Compact rows: session ID + meta on left, big tabular number + "eggs" caption on right.
- "See all" link → Records.

#### 7. Verify queue alert row
- Amber-tinted warning icon, "3 frames awaiting verification", "Oldest pending · 18s ago", chevron right.
- Tap → Verify Queue.

#### 8. Sync status row
- Green-tinted check icon, "All samples synced", "Last sync just now · 87 samples".

**Variants**: no-active-session state (future) — replace the hero with a "Start new session" CTA card that opens the New Session sheet.

---

### 6.3 Session Picker

**Purpose**: pick an existing session or start a new one.

**Layout**:

- **App bar**: "Sessions" title + "3 sessions · 1 active" sub-meta + Records icon + Settings icon.
- **Scrollable list** of session cards. Each card:
  - Session ID (subhead 19, tabular).
  - Meta: "2026-05-28 · 19:16 · PID 23424" (caption, tabular).
  - Right side:
    - Status badge: blue "Active" pill with white pulse dot (for active), or green "14 eggs", or gray "0 eggs" (for completed).
    - Kebab menu on active sessions: **Resume capture · Export · End session** (destructive red).
  - Active card has `--blue-tint-2` background and `--blue-tint` border.
- **Sticky bottom CTA**: "+ New session" (primary pill, full-width, above tab bar).

**Behavior**:
- Tap active card → Capture.
- Tap historical card → Session Detail.
- Tap "+ New session" → New Session sheet.

---

### 6.4 Capture (transparent floating UI)

**Purpose**: live microscope feed with real-time AI detection and capture controls.

**Visual register**: the only dark screen. Camera feed extends edge-to-edge under the status bar. All chrome floats over the feed as glass elements with backdrop blur + saturation.

**Layout** (z-stack, bottom to top):

#### 1. Camera viewport — `position: absolute; inset: 0`
- Background: subtle dark radial gradient (microscope vignette).
- Contents:
  - One faint specimen shape (so the AI box has a subject to detect).
  - Subtle focus reticle at the center (4 thin white lines, opacity 0.25).
  - AI bounding box with **corner brackets** in white and "Ascaris · 94%" label.
  - Live detection toast.

#### 2. Top chrome — floating, transparent background
- Glass-circle back button (40×40).
- **Session pill** (glass): pulsing red REC dot with halo + "Session" eyebrow + "324" tabular ID.
- Right cluster: 3 glass-circle action buttons (Stats, History, Verify queue with blue "8" badge).

#### 3. Bottom chrome — 3-column grid, transparent background
- **Frame count glass pill** (left): "FRAMES" eyebrow + "147" tabular.
- **Shutter** (center): 74×74 blue circle with 4px white ring + soft blue glow + 1.5px dark outline. Active scale: 0.92.
- **End session glass-red pill** (right): stop-square icon + "End session".

#### 4. Live detection toast (when AI hits)
- Slides in from top below the chrome.
- Glass background (rgba 15,23,42,0.78), white text.
- Blue check-circle icon, "*Ascaris lumbricoides* detected", "94% confidence · just now", blue "Review" action.
- Tap Review → Verify sheet opens.

**Status bar**: forced light (white text).
**Tab bar**: hidden.

---

### 6.5 Verify Queue

**Purpose**: review and triage all flagged frames (AI + manual).

**Layout**:

- **App bar**: back arrow + "Verify Queue" + "8 items · 3 pending" sub-meta + filter icon.
- **Filter chips** (horizontal, scrollable, with tabular counts): All (8), Pending (3), AI (6), Manual (2), Confirmed (4), Rejected (1). Active chip: dark `--gray-900` background.
- **Frame list** rows:
  - 52×52 thumbnail with colored bbox:
    - Blue solid = AI detection.
    - Amber dashed = manual capture.
    - Green = confirmed.
    - Red dashed = rejected.
  - Italic species name + small confidence pill (gray, tabular %).
  - Source ("AI" / "Manual") · time ago · status pill (amber / green / red).
  - Chevron right.

**Behavior**:
- Tap pending AI row → Verify sheet.
- Tap pending manual row → Manual sheet.
- Tap confirmed / rejected row → Sample Detail.

---

### 6.6 Records

**Purpose**: browse historical sessions; filter by search, species, status.

**Layout**:

- **App bar**: back arrow + "Records" + "Past 30 days" + Export icon.
- **Search input**: "Search sessions, patient ID, species…" with leading search icon, `--gray-50` background, blue focus glow.
- **3-column stat tiles**:
  - Sessions (42, +8 this week, green).
  - Eggs found (196, +24, green).
  - Accuracy (94%, +2.1%, green).
- **Species filter chips** (horizontal, scrollable): All species, *Ascaris*, *Trichuris*, *Necator*, *Hymenolepis*.
- **Record cards**:
  - Session ID (subhead 17, tabular).
  - Time + PID (caption, tabular).
  - Right: status badge — green "Synced" or amber "Pending sync".
  - Bottom row (above hairline): eggs · species · samples — bold tabular numbers + gray label.

---

### 6.7 Session Detail (populated)

**Purpose**: view one session's results — EPG, gallery, sync status, export.

**Layout**:

- **App bar**: back arrow + "Session 3242" + "2026-05-28 · 19:13 · PID 34343" + Export icon.
- **EPG hero card**:
  - Light gray surface, "EGGS PER GRAM" micro caps label.
  - Display value: "14" — 56px tabular bold.
  - Meta row: "**14** confirmed · **2** species · **38** samples".
  - Soft blue radial accent in top-right.
- **Sync banner**: green-tinted row with check icon, "Synced to cloud · 2026-05-28 19:42 · 38 samples uploaded".
- "Verified samples · 14 of 38" section header.
- **3-column gallery**:
  - Each tile: dark microscope-feel gradient + colored bbox.
  - Bottom-left species badge (blue Ascaris, amber Manual).
  - Top-right confidence chip (e.g. "94%" or "M" for manual).
  - Tap → Sample Detail.

---

### 6.7b Session Detail (empty state)

**Layout**:
- Same app bar.
- EPG hero shows "0" with sub "No confirmed eggs yet".
- **Empty state graphic** below:
  - 56×56 gray-50 circle with thin outline icon.
  - Title: "No verified samples yet".
  - Sub: "Captured frames will appear here once verified."

---

### 6.8 Sample Detail

**Purpose**: zoom into one captured frame; show bounding boxes, confidence, and full metadata.

**Layout**:

- **App bar**: back + "Sample 14" + "Frame 28 of 38" + Share + More.
- **Microscopy image** (square aspect):
  - Dark gradient with two specimen shapes.
  - Two labeled blue AI bounding boxes ("Ascaris · 94%" and "Ascaris · 87%").
  - **Floating glass image toolbar**: zoom · toggle boxes · edit · download (4 buttons in a glass strip at the bottom of the image).
- **Sample metadata**:
  - Species heading: *Ascaris lumbricoides* (italic, 22px) + green "Confirmed" badge.
  - **Confidence bar**: thin progress bar + "94% confidence · 2 detections".
  - **2-column meta grid**:
    | Field | Value |
    |---|---|
    | Captured | 19:14:22 / 2026-05-28 |
    | Verified by | M. Reyes / 19:15:08 |
    | AI model | AgarthaNet v2.3 (monospace) |
    | Magnification | 400× |
    | Location *(full-width)* | 14.5995° N, 120.9842° E — Manila, Philippines · ±5m |

---

### 6.9 Settings

**Purpose**: configure capture, privacy, data, account.

**Layout**:

- **App bar**: back + "Settings".
- **Account row** (own card): blue 40×40 avatar + name + email + chevron.
- **Capture** section:
  - Offline mode (toggle).
  - Auto-flag detections (toggle, default on).
  - AI confidence threshold (slider, 75%, with sub "Lower = more sensitivity, more noise").
- **Privacy** section:
  - GPS tracking (toggle, on).
  - Patient data encryption (toggle, on + disabled + "enforced by policy").
- **Data** section:
  - Sync status — "Up to date" (green), chevron.
  - Storage used — "3.1%", chevron.
  - Export all sessions — chevron.
- **About** section:
  - Version — "2.3.1 · build 4421".
  - AI model — "AgarthaNet v2.3", chevron.
  - Sign out — destructive red text + red logout chevron.
- **Footer**: 36×36 AgarthaVision logo + "AgarthaVision · Clinical Microscopy Assistant".

---

## 7. Modal sheets

### 7.1 New Session sheet

**Trigger**: "+ New session" on Session Picker.

**Title**: "New session"
**Subtitle**: "Set the label and a note before scanning"

**Body**:
- **Label** input — text, **required**. Pill marker "Required".
- **Note** textarea — 3 rows, **required**. Pill marker "Required".
- **Hint banner** (blue-tinted): info icon + "Both fields are required by lab protocol. You can edit them later from Session Detail."
- **Error banner** (hidden by default, red-tinted): "Please fill in both fields to continue." Shown when validation fails.

**Footer**: Cancel (secondary) + Start session (primary with arrow icon).

**Validation**:
- On Start session, both fields trimmed and checked.
- Empty fields get `input-error` red border + light red background.
- Error banner appears.
- First invalid field auto-focuses.
- Typing into a field clears its error state instantly; error banner clears once both fields have content.
- On valid submission: sheet slides down (280ms) then `showScreen('capture')` is called.

---

### 7.2 Verify sheet — AI

**Trigger**: Capture toast "Review" link; Verify Queue pending AI rows.

**Title**: "Verify detection"
**Subtitle**: "Frame 087 · captured 19:14:22" (tabular)

**Body**:
- **Preview**: 16:10 aspect rectangle with dark microscope gradient + blue bounding box overlay + "94% confidence" pill (top-right, glass) + "x: 412, y: 280" coordinate (bottom-left, monospace).
- **AI suggests** section: 2-column grid of species chips. Pre-selected: *Ascaris*. Others: *Trichuris*, *Necator*, *Hymenolepis*. Full-width "Other species · Search the full library" chip at the bottom.
- Selected chip: blue border + blue tint background + blue check-circle in top-right corner.
- **Edit bounding box** dashed-border link button.

**Footer**: Reject (secondary) + Confirm (primary with check icon).

---

### 7.3 Manual sheet

**Trigger**: Verify Queue pending Manual rows.

**Title**: "Label manual capture"
**Subtitle**: "Frame 094 · captured 19:14:48" (tabular)

**Body**:

- **Preview**: same shape as Verify sheet, but the bbox is dashed amber + "Manual" pill in amber.
- **Search input**: "Search species…"
- **Select species** section label with "Required" pill.
- **Species list** (6 items, radio-style, italic names where applicable):
  1. *Ascaris lumbricoides* — "Roundworm · most common at this site" (pre-selected).
  2. *Trichuris trichiura* — "Whipworm"
  3. *Necator americanus* — "Hookworm"
  4. *Hymenolepis nana* — "Dwarf tapeworm"
  5. **Other species** — blue "+" icon, blue label, "Type a custom species name below"
  6. **Unknown / artifact** — "Mark for later review"

- **Custom species input** (revealed when "Other species" is selected):
  - Slides in below the list as a blue-tinted dashed-border panel.
  - Label: "Custom species name · Required".
  - Input: "e.g. *Strongyloides stercoralis*" placeholder.
  - Auto-focuses after 120ms.

- **Note** textarea — 3 rows, **optional**. Italic "Optional" marker. Placeholder: "Notes on the capture, sample quality, or anything else worth flagging…"

**Footer**: Cancel (secondary) + Save label (primary).

**Validation**:
- If "Other species" is selected and custom field is empty → red border on custom field + auto-focus + sheet stays open.
- Otherwise sheet closes.

---

## 8. Component library

### 8.1 Buttons

```
.btn               base — pill, 14/20 padding, 600 weight, 15px font
.btn-primary       --blue background, white text
.btn-secondary     --gray-100 background, --gray-900 text
.btn-destructive   --red background, white text
.btn-block         100% width
.btn-sm            10/16 padding, 13px font
```

### 8.2 Inputs

```
.input             white, --gray-200 border, 12px radius, 13/16 padding
.input:focus       --blue border + 3px blue glow
.input.textarea    resize off, min-height 88, 3 rows
.input.input-error red border, light red tint background, red focus glow
```

**Labels**: 13px, weight 500, `--gray-700`.
**Required marker**: small uppercase pill, gray-100 background.
**Optional marker**: italic caption, `--gray-500`.

### 8.3 Badges

```
.badge              pill, 11px, 600 weight, 4/9 padding
.badge-blue         --blue bg, white text
.badge-blue-tint    --blue-tint bg, --blue text
.badge-gray         --gray-100 bg, --gray-700 text
.badge-green        --green-tint bg, dark green text
.badge-amber        --amber-tint bg, dark amber text
.badge-red          --red-tint bg, --red text
```

### 8.4 Cards

Plain card: white background, `--gray-100` border, 12px radius, 16px padding, no shadow.

**Active variant**: `--blue-tint-2` bg, `--blue-tint` border.

**Hover state**: border → `--gray-300`, bg → `--gray-50`.

### 8.5 List items

- Hairline divider between items, no divider on last child.
- Touch target: ≥ 44px.
- Chevron right (`--gray-300`, 18×18) for navigable rows.

### 8.6 Toggles

- 44×26 pill, `--gray-200` off, `--blue` on.
- 22×22 white thumb with subtle shadow, 2px from edges, 200ms transition.
- Disabled state: 0.5 opacity.

### 8.7 Slider

- 4px-tall `--gray-200` track, `--blue` fill.
- 18×18 white thumb with 2px blue border.

### 8.8 Tab bar item

- Equal flex, ~6px horizontal padding, 6px top padding.
- Inactive: gray-500 icon (22×22), gray-700 label (10px / 600).
- Active: blue icon + blue label.
- Optional badge: small red pill in the top-right of the icon area.

### 8.9 Glass UI (Capture)

```
background:       rgba(20, 28, 42, 0.55)
backdrop-filter:  blur(20px) saturate(160%)
border:           1px solid rgba(255, 255, 255, 0.08)
color:            white
box-shadow:       0 4px 14px -4px rgba(0, 0, 0, 0.35)
```

Used on: top bar icon buttons, session pill, frame count pill, end-session pill, toast, image toolbar.

### 8.10 Sparkline (Dashboard)

Hand-crafted inline SVG, no library:

```html
<svg viewBox="0 0 320 56" preserveAspectRatio="none">
  <defs>
    <linearGradient id="sparkFill" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%"   stop-color="#1E3FD9" stop-opacity="0.18"/>
      <stop offset="100%" stop-color="#1E3FD9" stop-opacity="0"/>
    </linearGradient>
  </defs>
  <path   d="M0,42 L53,30 L107,34 L160,18 L213,22 L267,12 L320,6 L320,56 L0,56 Z"
          fill="url(#sparkFill)"/>
  <polyline points="0,42 53,30 107,34 160,18 213,22 267,12 320,6"
            fill="none" stroke="#1E3FD9" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="0"   cy="42" r="2.5" fill="white" stroke="#1E3FD9" stroke-width="1.5"/>
  <circle cx="320" cy="6"  r="4"   fill="#1E3FD9" stroke="white" stroke-width="2"/>
</svg>
```

### 8.11 Species mix bar

Single pill-shaped div with 3 colored segment divs, widths proportional to counts. 2px white border separators. Legend row below with colored dots + italic species names + bold counts.

### 8.12 Detection bounding box

```
.det-box                  2px solid --blue, 4px radius, soft blue glow
.det-box::before          attr(data-label) chip, blue, top-left
.det-box-corner.tl/tr/bl/br   white L-shaped corner brackets
```

### 8.13 Modal sheet

```
.sheet                    bottom-anchored, slides up via transform
.sheet-handle             36×4 gray pill, centered
.sheet-header             title + close button (× in gray-100 circle)
.sheet-body               scrollable, scrollbar hidden
.sheet-footer             1-2 buttons with top hairline border
.sheet-scrim              rgba(15,23,42,0.4) + 2px blur, fades in/out
```

---

## 9. Interaction patterns

### 9.1 Touch feedback

- Buttons: subtle background darkening on hover; `scale(0.92–0.99)` on press for critical actions (shutter, hero card).
- Cards / rows: border + background change on hover.

### 9.2 Modal sheet behavior

- Tap outside (scrim) → close.
- Drag handle is decorative (no drag in prototype; real app should support).
- Sheet open: scrim fades in (250ms), sheet slides up (320ms cubic-bezier).
- Sheet close: reverse.
- Sheet covers the tab bar.

### 9.3 Empty state pattern

- 56×56 `--gray-50` circle with `--gray-300` outline icon, centered.
- Title: subhead 14, `--gray-700`.
- Sub: caption, `--gray-500`.

### 9.4 Validation pattern

- Error border on inputs (red).
- Optional inline error banner above the form for sheet-wide errors.
- Auto-focus first invalid field.
- Error clears as user types valid input.

### 9.5 Status bar handling

- Default: dark text (`--gray-900`) on light screens.
- Capture screen: forced white text via `.status-bar.dark`.

### 9.6 Navigation transitions

- For the prototype: instant screen swap.
- For the real app: slide-from-right for forward drill-downs, slide-back for back, fade for tab switches.

---

## 10. Brand

### 10.1 Logo

The AgarthaVision logo is a square mark:
- Cream background (`#F8F7F7`).
- A stylized blue "A" composed of two interlocking forms.
- A dark focal element (the "lens" of the A) in `#1D1D1E`.

**Sizes in product**:

| Surface | Size | Radius |
|---|---|---|
| Login app mark | 64×64 | 16px |
| Settings footer | 36×36 | 9px |
| Meta header (above phone, prototype only) | 28×28 | 7px |

Embedded once as an inline SVG `<symbol id="agartha-logo">`, referenced via `<use href="#agartha-logo">` everywhere — keeps the file size flat as the logo gets reused.

### 10.2 Naming

- **App**: AgarthaVision.
- **AI model**: AgarthaNet (versioned, e.g. v2.3).
- **Tagline / sub**: "Clinical Microscopy Assistant".

### 10.3 Voice

- **Clinical** — restrained, never playful.
- **Specifically scientific** where it matters: italic binomials, precise units (× magnification, EPG, GPS coords).
- **Friendly without casual** — "Good evening, Maria" is fine; "Hey there!" is not.
- **Direct prompts** — "Set the label and a note before scanning" beats "Please fill out the following form below".

---

## 11. Implementation notes

### 11.1 File structure (prototype)

A single self-contained HTML file:

```
prototype.html
├─ <head>
│   ├─ Inter font (Google Fonts)
│   └─ <style> — design tokens, components, screens, sheets
└─ <body>
    ├─ <svg> #agartha-logo (hidden symbol)
    ├─ .stage
    │   ├─ .meta-header (outside phone)
    │   ├─ .screen-selector (jump chips for prototype review)
    │   └─ .phone-stage > .phone
    │       ├─ .notch, .status-bar
    │       ├─ .screens > 9 .screen children
    │       ├─ .tab-bar
    │       ├─ .sheet-scrim
    │       └─ 3 .sheet children (newsession, verify, manual)
    └─ <script> — showScreen, openSheet, validation, etc.
```

### 11.2 Navigation logic

```js
const TAB_FOR_SCREEN = {
  dashboard: 'dashboard',
  sessions:  'sessions',
  capture:   'sessions',
  queue:     'sessions',
  records:   'records',
  'session-detail':       'records',
  'session-detail-empty': 'records',
  'sample-detail':        'records',
  settings:  'settings'
};
const NO_TAB_BAR = ['login', 'capture'];

function showScreen(name) {
  // Switch screen, update tab bar visibility, mark active tab,
  // adjust status bar color, reset scroll, close kebabs + sheets.
}
```

### 11.3 Sheets

Sheets are siblings of `.screens` inside `.phone`. They slide up over the tab bar. Scrim closes on tap-outside or close button. `closeSheet()` removes the `.open` class from scrim and every open sheet.

### 11.4 Status bar

Fixed overlay at the top of the phone frame, z-index 90, with `.dark` class added on Capture only.

### 11.5 No external libraries

- **No charting library** — sparkline + species mix bar are inline SVG and CSS.
- **No icon library** — all icons are inline SVG, lucide-style outline at 1.5–1.8 stroke.
- **No JS framework** — vanilla JS handles all state.
- **One external font** — Inter from Google Fonts.

### 11.6 Accessibility

- Touch targets ≥ 44px (iOS HIG).
- Color contrast: body text on white ≥ 4.5:1; large text ≥ 3:1.
- Required fields marked visually + with text label ("Required").
- All icon buttons have `title` attributes.
- Focus rings on inputs (3px blue glow).
- **Future**: ARIA roles, keyboard navigation, `prefers-reduced-motion` support.

### 11.7 Responsive

The phone frame is fixed at 390×844 (iPhone 14 reference). For a native build, the same tokens and components scale to other devices; key layouts (KPI grid, sample grid, tab bar) are already grid-based and will reflow.

### 11.8 Data model sketch (for backend planning)

```
Session
  id, label, note, patientId, technologistId,
  startedAt, endedAt, status (active|ended|synced),
  location { lat, lng, accuracyMeters },
  framesTotal, samplesVerified, eggsPerGram

Sample
  id, sessionId, frameNumber, capturedAt,
  capturedBy (ai|manual), thumbnailUrl, fullImageUrl,
  status (pending|confirmed|rejected),
  species (Ascaris|Trichuris|Necator|Hymenolepis|Other|Unknown),
  customSpeciesName?, note?, confidence?,
  detections: [{ bbox: [x,y,w,h], species, confidence }],
  verifiedBy, verifiedAt, aiModelVersion (e.g. "AgarthaNet v2.3")

User
  id, name, email, avatar, role (tech|admin)
```

### 11.9 Known cleanup items

- The prototype's demo email is still `tech@parascope.app` in two places (Login input, Settings account row); should be `tech@agarthavision.app`.
- The prototype's REC visual treatment was consolidated into the session pill on Capture — the legacy `.rec-pill` CSS may be removable.

---

## Appendix · Screen inventory at a glance

| Screen | Tab bar | Status bar | Has scroll | Has sticky CTA |
|---|---|---|---|---|
| Login | hidden | light | no | no |
| Dashboard | visible (Home active) | light | yes | no |
| Session Picker | visible (Sessions active) | light | yes | yes (New session) |
| Capture | hidden | dark | no | no (floating chrome) |
| Verify Queue | visible (Sessions active) | light | yes | no |
| Records | visible (Records active) | light | yes | no |
| Session Detail | visible (Records active) | light | yes | no |
| Sample Detail | visible (Records active) | light | yes | no |
| Settings | visible (Settings active) | light | yes | no |