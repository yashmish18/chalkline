# Design System: Faculty Dashboard (IET)

**Design Language:** Swiss Modernism 2.0 / Institutional Precision  
**Audience:** Academic Department Coordinators & Faculty Members  
**Principles:** Legible, rational, authoritative, zero visual fluff, high data density with generous breathing room.

---

## 1. Color Palette & Semantic Tokens

### 1.1 Brand & Neutral Hierarchy
```css
:root {
  /* Brand Primaries */
  --color-primary: #1E3A5F;            /* Deep Institutional Navy */
  --color-primary-hover: #162C48;
  --color-primary-foreground: #FFFFFF;

  --color-secondary: #2563EB;          /* Active Interaction Blue */
  --color-secondary-hover: #1D4ED8;
  --color-secondary-foreground: #FFFFFF;

  --color-accent: #B45309;             /* Academic Gold / Amber */
  --color-accent-subtle: #FEF3C7;
  --color-accent-foreground: #FFFFFF;

  /* Surfaces & Backgrounds */
  --color-background: #F8FAFC;         /* Crisp Canvas */
  --color-surface: #FFFFFF;            /* Cards, Tables, Modals */
  --color-surface-hover: #F1F5F9;
  --color-surface-active: #E2E8F0;

  /* Typography Colors */
  --color-foreground: #0F172A;         /* Primary Headings & Data */
  --color-foreground-muted: #475569;   /* Labels, Secondary Data */
  --color-foreground-subtle: #94A3B8;  /* Timestamps, Placeholders */

  /* Borders & Dividers */
  --color-border: #E2E8F0;
  --color-border-subtle: #F1F5F9;
  --color-border-strong: #CBD5E1;

  /* Status Colors */
  --status-on-track-bg: #DCFCE7;
  --status-on-track-text: #166534;
  --status-on-track-border: #86EFAC;

  --status-watch-bg: #FEF3C7;
  --status-watch-text: #92400E;
  --status-watch-border: #FCD34D;

  --status-critical-bg: #FEE2E2;
  --status-critical-text: #991B1B;
  --status-critical-border: #FCA5A5;

  --status-info-bg: #E0F2FE;
  --status-info-text: #075985;
  --status-info-border: #7DD3FC;
}
```

---

## 2. Typography

* **Primary Sans (UI & Data):** `Inter`, `-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `sans-serif`
* **Editorial / Academic Serif (Headers & Accents):** `EB Garamond`, `Georgia`, `serif`
* **Monospace (Course Codes, Hashes):** `JetBrains Mono`, `SF Mono`, `ui-monospace`, `monospace`

| Style | Size | Weight | Line Height | Tracking | Usage |
|---|---|---|---|---|---|
| Display / Page Title | 24px (1.5rem) | 700 | 1.25 | -0.02em | Main views, dashboard headers |
| Section Heading (h2) | 18px (1.125rem) | 600 | 1.35 | -0.01em | Card groups, table titles |
| Subsection (h3) | 15px (0.9375rem) | 600 | 1.4 | 0 | Modal titles, form group labels |
| Body Text | 14px (0.875rem) | 400 / 500 | 1.5 | 0 | Standard UI, table cells, comments |
| Caption / Metadata | 12px (0.75rem) | 500 | 1.4 | +0.01em | Timestamps, badge labels, subtext |

---

## 3. Spacing & Grid System

* **8px Grid Rhythm:** Standard spacing multiples: `4px (0.25rem)`, `8px (0.5rem)`, `12px (0.75rem)`, `16px (1rem)`, `24px (1.5rem)`, `32px (2rem)`, `48px (3rem)`.
* **Container Max-Widths:**
  * Application Canvas: Fluid with `max-w-7xl` (1280px) centered.
  * Form / Detail Modals: `max-w-xl` (576px) to `max-w-2xl` (672px).
* **Radii:** Subtle, disciplined curves:
  * Buttons & Inputs: `6px` (`rounded-md`)
  * Cards & Containers: `8px` (`rounded-lg`)
  * Badges & Chips: `9999px` (`rounded-full`)

---

## 4. Key UI Components

### 4.1 Metric Cards (Coordinator Dashboard)
* 4 KPI cards in a 4-column responsive grid:
  1. **Submission Rate** (e.g. 92% - On Track)
  2. **On-Time Rate** (e.g. 88%)
  3. **Overdue Submissions** (e.g. 4 pending)
  4. **Faculty at Critical** (e.g. 1 faculty < 60%)
* Crisp 1px border, neutral background, prominent number with status badge.

### 4.2 Status Badges
* Explicit padding: `px-2.5 py-0.5 text-xs font-semibold rounded-full border`
* Combinations:
  * `Pending`: Grey background (`bg-slate-100 text-slate-700 border-slate-200`)
  * `Submitted`: Blue background (`bg-blue-50 text-blue-700 border-blue-200`)
  * `Late`: Orange background (`bg-amber-50 text-amber-700 border-amber-200`)
  * `Returned`: Rose background (`bg-rose-50 text-rose-700 border-rose-200`)
  * `Accepted`: Green background (`bg-emerald-50 text-emerald-700 border-emerald-200`)

### 4.3 Notification Center
* Header Bell Icon with real-time numeric counter badge (active red `#DC2626` when unread > 0).
* Dropdown popover (`w-80` to `w-96`) displaying:
  * Header with "Notifications" title and "Mark all as read" button.
  * Scrollable list of notification items with unread indicator dot, relative timestamp (`2h ago`), title, and preview message.
  * Direct action click navigating straight to the target assignment or submission.

### 4.4 Data Tables & Lists
* Clean bordered table with sticky header (`bg-slate-50`).
* Zebra striping optional; hover highlight on rows (`hover:bg-slate-50/70`).
* Explicit empty states with clear calls to action (no bare empty rectangles).

---

## 5. Accessibility & Interaction Standards

* **Contrast:** Minimum 4.5:1 for body copy; 3:1 for large headers and interface boundaries.
* **Keyboard Navigation:** Clear focus visible ring: `ring-2 ring-offset-2 ring-[#1E3A5F]`.
* **Touch Targets:** Minimum 40x40px clickable hit areas for buttons and icons.
* **Icons:** Standardized SVG icons (Lucide React), strictly no emoji as functional icons.
