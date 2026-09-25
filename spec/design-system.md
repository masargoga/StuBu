# Design System

> Theme, component usage, and visual standards. Reference this when building or reviewing UI.

---

## 1. Theme

- **Base theme:** Vaadin Aura
- **Custom CSS:** `src/main/resources/META-INF/resources/styles.css`

**Aura and Lumo are two different, incompatible design systems.** This project uses **Aura**. Do not use `--lumo-*` CSS variables — they belong to the Lumo theme and must not be mixed with Aura. Use `--aura-*` variables for Aura-specific properties (typography, shadows) and `--vaadin-*` variables for base properties shared across all themes (spacing, radius, colors).

**Always use Aura theme variables instead of hard-coded values** (e.g., `--aura-font-size-xs` through `--aura-font-size-xl` for font sizes). Do not use hardcoded `px`, `rem`, or `em` values when an Aura variable exists. This ensures consistency with the Vaadin Aura theme and allows global adjustments through theme customization.

---

## 2. Color Palette

Aura computes all color variations automatically from a small set of base properties. Override these instead of hard-coding hex values.

| Token | Default | Usage |
|-------|---------|-------|
| `--aura-accent-color-light` | Blue | Primary actions, focus rings, selection highlights (light mode) |
| `--aura-accent-color-dark` | Blue | Primary actions, focus rings, selection highlights (dark mode) |
| `--aura-neutral` / `-light` / `-dark` | Dark gray / off-white | Text, borders, default UI chrome |
| `--aura-red` | Red | Error states, destructive actions |
| `--aura-orange` | Orange | Warnings |
| `--aura-green` | Green | Success states, confirmations |
| `--aura-blue` | Blue | Informational, links |
| `--aura-yellow` | Yellow | Caution, highlights |
| `--aura-purple` | Purple | Decorative accents |

Derived read-only tokens (do not override directly):
- `--aura-accent-contrast-color` — high-contrast text on accent backgrounds
- `--aura-accent-text-color` — accent-derived text color with good contrast
- `--aura-accent-border-color` — border tinted with accent color
- `--aura-accent-surface` — surface tinted with accent color
- `--aura-red-text`, `--aura-green-text`, etc. — palette text variants with better contrast

Base style tokens (shared across all themes):
- `--vaadin-text-color` — main text color
- `--vaadin-text-color-secondary` — secondary/muted text
- `--vaadin-text-color-disabled` — disabled state text
- `--vaadin-border-color` — prominent borders (3:1 contrast)
- `--vaadin-border-color-secondary` — subtle, non-essential borders
- `--vaadin-background-color` — base content background
- `--vaadin-background-container` — buttons, toolbars, highlighted areas
- `--vaadin-background-container-strong` — more prominent container background

Use accent class names (e.g. `.aura-accent-purple`) on `<html>` or individual components to swap accent color contextually.

---

## 3. Typography

Aura uses the **Instrument Sans** web font by default (`--aura-font-family-instrument-sans`), falling back to the system font stack.

| Token | Purpose |
|-------|---------|
| `--aura-font-family` | App-wide font family (set on `<body>`) |
| `--aura-base-font-size` | Base size (unitless number, represents M size in px) |
| `--aura-font-size-xs` through `-xl` | Computed font sizes (rem, rounded to nearest px) |
| `--aura-base-line-height` | Base line height (unitless, relative to font size) |
| `--aura-line-height-xs` through `-xl` | Computed line heights (rem, rounded to nearest 2px) |
| `--aura-font-weight-regular` | Normal body text |
| `--aura-font-weight-medium` | Emphasis, subheadings |
| `--aura-font-weight-semibold` | Headings, strong emphasis |
| `--aura-font-smoothing` | Set to `auto` to disable grayscale anti-aliasing |

Use Aura font-size tokens (`--aura-font-size-s`, etc.) instead of hard-coded `px`/`rem` values.

---

## 4. Spacing & Layout

Aura computes gap and padding from `--aura-base-size` (unitless, range 12–24). Use the resulting base style tokens:

| Token | Purpose |
|-------|---------|
| `--vaadin-gap-xs` through `-xl` | Space between elements in flex/grid layouts |
| `--vaadin-padding-xs` through `-xl` | Internal padding for containers and content areas |
| `--vaadin-padding-inline-container` | Horizontal padding for single-line containers (buttons, inputs) |
| `--vaadin-padding-block-container` | Vertical padding for single-line containers |

**Border radius** (computed from `--aura-base-radius`, unitless, range 0–10):

| Token | Purpose |
|-------|---------|
| `--vaadin-radius-s` | Small controls (should not become circles) |
| `--vaadin-radius-m` | Default component radius |
| `--vaadin-radius-l` | Large containers, cards, dialogs |

**Shadows** (Aura-specific):

| Token | Purpose |
|-------|---------|
| `--aura-shadow-xs` | Subtle elevation — buttons, inputs, checkboxes |
| `--aura-shadow-s` | Slight elevation — primary buttons, selected controls, cards |
| `--aura-shadow-m` | Clear elevation — overlays, notifications, dialogs |

**Surface colors** for visual hierarchy (read-only, computed):
- `--aura-surface-color` — semi-transparent elevated background
- `--aura-surface-color-solid` — opaque version
- Control with `--aura-surface-level` (number, higher = more elevation) and `--aura-surface-opacity` (default 0.5)

**Layout approach:** Use Vaadin `VerticalLayout` / `HorizontalLayout` (Flow) or flexbox/grid with `--vaadin-gap-*` / `--vaadin-padding-*` tokens. No hard-coded spacing values.

---

## 5. Component Standards

| Component | When to use | Notes |
|-----------|-------------|-------|
| `Button` | Actions | One primary (green) button per area; the action that is not possible right now steps back (outlined). Never smaller than 44px, the two time actions are 64px |
| Cards (`.time-hero`, `.time-card`, `.timesheet-status`, `.approval-row`) | Group related content | White surface, 1px soft border, small shadow (`--stubu-card-shadow`), large radius. No card inside a card |
| Summary tiles (`.time-totals` > `.time-total`) | Key numbers | Small label above a large value; label and value are one readable text ("Total hours today: 5h 34m") |
| `Badge` | Status | Always with text, colour only supports it |
| `Avatar` | Signed-in user in the header | Initials on the soft green tint |
| `SideNav` | Navigation drawer | The current page has the soft green tint |
| Responsive div table (`.approval-head/.approval-row`) | Lists of people, timesheets, holidays, audit entries | Columns on wide screens, one card per row on narrow ones (`data-label` is shown in front of the value) |
| `Dialog` | Corrections, confirmations | Large radius, errors inside the dialog (`DialogError`) |
| `MessageBox`, `LoadErrorBox` | Feedback and load errors | Icon and text, so state is never only a colour |

---

## 6. Responsive Behavior

- **Mobile** (< 640px): Single column, stacked layouts, full-width cards
- **Tablet** (640–1024px): Two-column grid, side-by-side content
- **Desktop** (> 1024px): Multi-column grid, admin grid+form side by side

---

## 7. Brand and look (design refresh)

Light and airy: soft surfaces, generous spacing, one accent colour.

- **Accent:** green `#0e7a4b` (`--stubu-green`, set as `--aura-accent-color-light`; white text on it has a contrast of about 5.4:1). The tints `--stubu-green-soft` (selected item, hover, hints) and `--stubu-green-line` (borders) are mixed from it with `color-mix`. Green also means "working now": the status dot, the elapsed time and the open period. Red and orange are only for errors, delete and holidays.
- **Icon mark:** `icons/stubu-mark.svg`, a clock face with a progress arc on a green rounded tile. It is used in the header, on the login card and as the favicon (`AppLogo`, `Application.configurePage`). It has no text of its own; the name "Employee Time Tracking" is beside it.
- **Spacing and shape:** `--aura-base-size: 18` from 800px width up (the Aura default below), base radius 8, cards use `--vaadin-radius-l`. Page background is a very light green-grey gradient.
- **Type:** Aura's Instrument Sans; headings semibold with slightly tighter letter spacing; tabular numbers for times. The sizes stay large (accessibility): body text at least `--aura-font-size-l`.
- **Time display:** the Today page has a status card (state, current time, elapsed time and the two actions), three summary tiles (total, break, first check-in) and the day card with the timeline. Timeline: solid green blocks on a soft track, an open period is striped, breaks are the gaps. The visible time span is 06:00 to 20:00 and widens in whole hours to fit every period (`TimelineWindow`); the month view uses one shared span so its rows can be compared.
- **Login:** card with the icon mark and the provider buttons with the provider's logo (Google, Microsoft; drawn by `styles.css` from `data-provider`).
- **Not in this round:** dark mode (the accent has a dark value already, `--aura-accent-color-dark`), an overflow menu for row actions (Edit, Details and Deactivate stay visible buttons: fewer clicks and large targets for the users).
- **Guard rails:** the browser tests check contrast (WCAG AA), no horizontal scrolling, large buttons and the layout at 1920x1080, 768x1024 and 375x812.
