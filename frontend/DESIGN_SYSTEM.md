# MandarinSpark design system

The reference for every page and component in the frontend. It documents only what exists in the code.

Source of truth:

- `src/styles/tokens.css`: every token below, imported first by `main.css`.
- `src/styles/main.css`, block "Foundation" (top of the file, up to the banner
  `Page sections (owned by page agents; page-prefixed classes only)`): base elements, the app shell and all shared
  primitives. Page sections follow, with page-prefixed classes only.
- Shared components:
  - `components/sections`: `PageHeaderBlock`;
  - `components/common`: `StatePanel`, `DataNotice`, `StatusPill`, `PaginationBar`, `ProgressBar`, `ProgressRing`,
    `PortraitAvatar`, `MasteryChip`, `TrendChart`, `IconGlyph`;
  - `components/dashboard`: `ResourceSection`, `InfoCard`, `ListInfoItem`, `ActionFormCard`;
  - `components/content`: `ContentStatusBadge`, `LearningStageTag`.
- Helper: `utils/cssToken.ts` (`readCssToken`) for code that must read a token in JavaScript (canvas drawing).

Change a primitive or a token here, in the foundation block or `tokens.css`, never from a page section.

---

## 1. Rules in one minute

1. **Only tokens.** No raw hex, `rgba()`, px font sizes, radii or shadows in your rules when a token exists.
   Spacing uses `--space-*`. Dynamic, data-driven inline styles (a progress width) are the only inline styles allowed.
   Illustration art (`PandaIllustration`, `CourseSceneThumb`) keeps its own palette.
2. **One page header.** Every dashboard page starts with `<PageHeaderBlock>`; public content pages use the same
   classes (see §7). Exactly one `h1` per page, including while the page is loading or failed.
3. **Shared primitives first.** Buttons are `.btn` + a variant; status is `<StatusPill>`; notices are `<DataNotice>`;
   loading, empty and error areas are `<StatePanel>` (or `<ResourceSection>`, which wraps it); paging is
   `<PaginationBar>`.
4. **Breakpoints are 1200px, 900px and 640px** (`max-width`). Do not add new ones.
5. **Never colour-only.** Status carries text (and optionally an icon); colour reinforces it.
6. **Motion** uses `--duration-*` and `--ease-*`. Everything already collapses under `prefers-reduced-motion`.
7. Keep page selectors prefixed (`.teacher-content-*`, `.admin-users-*`). Do not restyle `.btn`, `.panel`, `.input`
   etc. globally from a page section; scope with the page class if you truly need a local variant. Never reach into a
   shared component with `:deep()`; add a prop or a modifier to the component instead.
8. **No dead CSS.** A class that no template or script uses is deleted, primitives included. Add a primitive when the
   first page needs it.

---

## 2. Tokens

### 2.1 Colour palette

| Scale                                                       | 50      | 100     | 200     | 300     | 400     | 500         | 600     | 700     | 800     | 900     |
| ----------------------------------------------------------- | ------- | ------- | ------- | ------- | ------- | ----------- | ------- | ------- | ------- | ------- |
| `--color-primary-*`                                         | #f6f3ff | #ede8ff | #dcd2ff | #c2b2ff | #9d85ff | **#6f4cff** | #5a35ec | #4a28c9 | #3a1f9c | #2a1870 |
| `--color-accent-*`                                          | #fffaeb | #fff2c7 | #ffe696 | #ffdb66 | #ffd247 | **#ffcc2f** | #f0b400 | #c28d00 | #8f6700 | #5f4500 |
| `--color-ink-*` (indigo-tinted neutral; also `-25` #fafbff) | #f5f6fc | #eceff8 | #e0e4f1 | #cbd1e3 | #a2aac4 | #7a84a3     | #5c6687 | #434d6d | #2c3656 | #152042 |

`--color-white` (#ffffff). `--color-brand-orange` (#f97316) is reserved for the "Mandarin" half of the wordmark.

Semantic families (each has a fill, a soft background, a border, an AA text colour and a strong solid):

| Family            | fill    | `-soft` | `-border` | `-text` | `-strong`                                      |
| ----------------- | ------- | ------- | --------- | ------- | ---------------------------------------------- |
| `--color-success` | #2f9e44 | #eaf8ec | #bfe5c6   | #1e7a34 | #1e7a34                                        |
| `--color-warning` | #f5a524 | #fff5e2 | #fbd99a   | #8a5100 | #a35f00                                        |
| `--color-danger`  | #e5484d | #fff0f0 | #f7c3c5   | #c0262d | #c0262d (hover `--color-danger-hover` #a51f26) |
| `--color-info`    | #2f8fff | #ecf5ff | #bcd8ff   | #1a5fc0 | #1a5fc0                                        |

- **fill**: icons, dots, progress fills, chart marks. Never body text (warning fill is only 2.0:1 on white).
- **soft**: tinted backgrounds of pills, notices and states.
- **text**: text or icons on soft or white backgrounds.
- **strong**: solid backgrounds that carry white text (danger button).

Translucent tokens (composited over whatever is underneath): `--color-primary-a04/a08/a12/a16/a24/a32/a48`,
`--color-white-a08/a16/a24/a32/a48/a64/a80`, `--color-ink-a04/a08/a12/a16/a24/a48`, `--color-accent-a12/a24/a48`,
`--color-{success,warning,danger,info}-a12/a24`. Use them for overlays on coloured hero art, hover tints and scrims.

Brand gradients and on-brand surfaces:

| Token                                             | Value                               | Use                                                                                                                                 |
| ------------------------------------------------- | ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `--gradient-brand`                                | 135° primary-700 → 600 → 500        | `.hero-card` and every purple hero. Every stop keeps white text ≥ 5:1.                                                              |
| `--gradient-brand-soft`                           | 135° primary-50 → white → accent-50 | `.hero-card.light` and soft brand cards                                                                                             |
| `--surface-on-brand` / `--surface-on-brand-hover` | ink at 20 % / 30 %                  | Inset tiles and chips on a `--gradient-brand` hero; white text on them stays ≥ 4.5:1. Do not use translucent white tiles on purple. |

### 2.2 Semantic colour roles (prefer these over raw scale steps)

| Role                                             | Token                                                       | Value                                                  |
| ------------------------------------------------ | ----------------------------------------------------------- | ------------------------------------------------------ |
| Page background                                  | `--surface-page`                                            | ink-50                                                 |
| Card / panel                                     | `--surface-card`                                            | white                                                  |
| Raised (menus)                                   | `--surface-raised`                                          | white + `--shadow-overlay`                             |
| Sunken (inset areas, disabled inputs)            | `--surface-sunken`                                          | ink-50                                                 |
| Subtle (empty states, sidebar footer)            | `--surface-subtle`                                          | ink-25                                                 |
| Hover tint / selected                            | `--surface-hover` / `--surface-selected`                    | primary-50 / primary-100                               |
| Dark surface (tooltip)                           | `--surface-inverse`                                         | ink-900                                                |
| Drawer scrim                                     | `--surface-scrim`                                           | ink 48 %                                               |
| Border subtle (cards, dividers)                  | `--border-subtle`                                           | #e8ebf5                                                |
| Border default (controls at rest, tags)          | `--border-default`                                          | ink-200                                                |
| Border strong (inputs)                           | `--border-strong`                                           | ink-300                                                |
| Border interactive (hover of secondary controls) | `--border-interactive`                                      | primary-300                                            |
| Text primary                                     | `--text-primary`                                            | ink-900                                                |
| Text secondary (muted copy)                      | `--text-secondary`                                          | ink-600                                                |
| Text disabled                                    | `--text-disabled`                                           | ink-400 (exempt from contrast; never for real content) |
| Text on primary / on accent / on dark            | `--text-on-primary` / `--text-on-accent` / `--text-on-dark` | white / ink-900 / white                                |
| Link / link hover                                | `--text-link` / `--text-link-hover`                         | primary-600 / primary-700                              |
| Brand text (eyebrows, badges)                    | `--text-brand`                                              | primary-600                                            |

There are no legacy alias tokens (`--primary`, `--text-soft`, `--shadow`, `--text-muted`, … were deleted). Use the
names above.

### 2.3 Contrast (WCAG 2.1, computed from the token values)

| Foreground             | Background                            | Ratio    |
| ---------------------- | ------------------------------------- | -------- |
| `--text-primary`       | `--surface-card`                      | 15.94:1  |
| `--text-primary`       | `--surface-page`                      | 14.78:1  |
| `--text-secondary`     | `--surface-card`                      | 5.67:1   |
| `--text-secondary`     | `--surface-page` / `--surface-sunken` | 5.25:1   |
| `--text-secondary`     | `--color-primary-50`                  | 5.18:1   |
| `--text-secondary`     | `--color-accent-50`                   | 5.43:1   |
| `--text-link`          | `--surface-card`                      | 6.66:1   |
| `--text-link`          | `--surface-page`                      | 6.17:1   |
| `--text-link-hover`    | `--surface-card`                      | 8.61:1   |
| `--text-brand`         | `--color-primary-50`                  | 6.08:1   |
| `--color-primary-700`  | `--color-primary-50`                  | 7.87:1   |
| `--color-primary-700`  | `--color-primary-100`                 | 7.21:1   |
| `--color-primary-700`  | `--color-ink-100`                     | 7.49:1   |
| `--text-on-primary`    | `--color-primary-500`                 | 5.06:1   |
| `--text-on-primary`    | `--color-primary-600`                 | 6.66:1   |
| `--text-on-accent`     | `--color-accent-500`                  | 10.58:1  |
| `--text-on-accent`     | `--color-accent-400`                  | 11.06:1  |
| white                  | `--color-danger-strong`               | 5.90:1   |
| white                  | `--color-danger-hover`                | 7.44:1   |
| white                  | `--color-success-strong`              | 5.40:1   |
| white                  | `--color-warning-strong`              | 5.01:1   |
| white                  | `--color-info-strong`                 | 6.10:1   |
| `--color-success-text` | `--color-success-soft`                | 4.92:1   |
| `--color-success-text` | `--surface-card`                      | 5.40:1   |
| `--color-warning-text` | `--color-warning-soft`                | 5.96:1   |
| `--color-warning-text` | `--surface-card`                      | 6.45:1   |
| `--color-danger-text`  | `--color-danger-soft`                 | 5.34:1   |
| `--color-danger-text`  | `--surface-card`                      | 5.90:1   |
| `--color-info-text`    | `--color-info-soft`                   | 5.54:1   |
| `--color-info-text`    | `--surface-card`                      | 6.10:1   |
| `--color-accent-900`   | `--color-accent-100`                  | 8.03:1   |
| `--color-accent-800`   | `--color-accent-50`                   | 4.90:1   |
| `--color-ink-700`      | `--color-ink-100`                     | 7.25:1   |
| `--text-on-dark`       | `--surface-inverse`                   | 15.94:1  |
| white                  | every stop of `--gradient-brand`      | ≥ 5.06:1 |

Non-text (3:1 needed for icons, focus indicators, meaningful UI marks): `--color-primary-500` 5.06:1, focus ring
5.06:1, `--color-danger` 3.91:1, `--color-success` 3.45:1, `--color-info` 3.23:1 on white.

**Fails, by design (do not use for text):** `--color-ink-500` 3.71:1 (placeholders only), `--text-disabled` 2.31:1,
`--color-warning` fill 2.04:1 (always pair with a text label), `--border-strong` 1.53:1 (input outlines are
supplemented by the label, the focus ring and the error text). The wordmark's `--color-brand-orange` is a logotype and
exempt.

### 2.4 Typography

Families: `--font-display` (Fredoka; headings, card titles, big numbers), `--font-body` (Nunito; everything else),
`--font-cjk` (Chinese text next to pinyin; class `.cjk-text`).

Size scale (rem): `--font-size-2xs` 0.6875 (11) · `-xs` 0.75 (12) · `-sm` 0.8125 (13) · `-md` 0.875 (14) ·
`-base` 1 (16) · `-lg` 1.125 (18) · `-xl` 1.25 (20) · `-2xl` 1.5 (24) · `-3xl` 1.75 (28) · `-4xl` 2 (32) ·
`-5xl` 2.5 (40) · `-6xl` 3 (48) · `-7xl` 3.75 (60) · `-8xl` 4.5 (72).

Weights: `--font-weight-regular` 400 · `-medium` 500 · `-semibold` 600 · `-bold` 700 · `-extrabold` 800.
Line heights: `--line-height-tight` 1.15 · `-snug` 1.3 · `-normal` 1.5 · `-relaxed` 1.65.
Letter spacing: `--letter-spacing-tight` -0.02em (display headings) · `--letter-spacing-wide` 0.06em (overlines).

Semantic roles. Headings are styled by the element or component that renders them, not by helper classes:

| Role               | Size token                             | Font / weight              | Rendered by                              | Use                             |
| ------------------ | -------------------------------------- | -------------------------- | ---------------------------------------- | ------------------------------- |
| Display            | `--type-display-size` (36–60 px fluid) | display 700                | landing hero (`landing.css`)             | Landing headline only           |
| H1 page title      | `--type-h1-size` (26–36 px fluid)      | display 600                | `PageHeaderBlock` (`.page-title h1`)     | One per page                    |
| H2 section         | `--type-h2-size` (20)                  | display 600                | `.section-title`                         | Section headers                 |
| H3 card title      | `--font-size-lg` (18)                  | display 600                | `.card-title`                            | Card and panel titles           |
| Body               | `--type-body-size` (16)                | body 400                   | `body`                                   | Paragraphs                      |
| Body small         | `--type-body-sm-size` (14)             | body 400                   | `.card-subtitle`, `.section-description` | Dense UI, descriptions in cards |
| Label              | `--type-label-size` (14)               | body 700                   | `.field-label`                           | Form labels                     |
| Caption            | `--type-caption-size` (12)             | body 400, secondary colour | `.type-caption`                          | Meta, timestamps                |
| Overline / eyebrow | `--type-overline-size` (12)            | body 800, uppercase, wide  | `.page-eyebrow`                          | Above public page titles        |

### 2.5 Spacing (4 px base)

`--space-0` 0 · `-0-5` 2 · `-1` 4 · `-1-5` 6 · `-2` 8 · `-2-5` 10 · `-3` 12 · `-3-5` 14 · `-4` 16 · `-5` 20 ·
`-6` 24 · `-7` 28 · `-8` 32 · `-10` 40 · `-12` 48 · `-16` 64 · `-20` 80.

Layout rhythm tokens (they already change per breakpoint, so use them instead of media queries):

| Token                                                        | ≥ 900 px | 641–900 px | ≤ 640 px |
| ------------------------------------------------------------ | -------- | ---------- | -------- |
| `--page-gutter` (main padding left/right)                    | 24       | 16         | 12       |
| `--page-gap` (gap between top-level blocks of `.page-stack`) | 24       | 20         | 16       |
| `--section-gap` (gap inside a section, header → content)     | 20       | 16         | 16       |
| `--card-padding` (`.panel`, `.hero-card`)                    | 24       | 20         | 16       |
| `--grid-gap` (card grids)                                    | 16       | 16         | 12       |

Other layout tokens: `--page-max` 1600px, `--content-max` 1280px, `--reading-max` 72ch, `--sidebar-width` (248px;
76px icon rail at 901–1200px; 0 below 900px where it is a drawer), `--topbar-height` 64px.

Content width you can count on inside `main` (after gutters): 1440 viewport ≈ 1144px, 1280 ≈ 984px, 1024 ≈ 900px,
834 ≈ 802px, 390 ≈ 366px.

### 2.6 Radius, borders, shadows, z-index, motion, sizes

- Radius: `--radius-xs` 6 · `-sm` 10 (inputs, small chips, menu items) · `-md` 14 (icon tiles, tabs) · `-lg` 18
  (list items, notices, inner cards) · `-xl` 24 (panels, cards) · `-2xl` 32 (hero art) · `-pill` 999px (buttons,
  pills) · `-round` 50%.
- Borders: `--border-width-thin` 1px · `-thick` 2px · `--border-width-focus` 2px.
- Shadows: `--shadow-xs` (pressed tabs, avatars) · `-sm` (cards at rest) · `-md` (sticky bars) · `-lg` (lifted / hover
  cards) · `-overlay` (menus, the drawer) · `-primary` / `-accent` (brand glow, use sparingly).
- Focus: `--focus-ring-color`, `--focus-ring-offset`, `--focus-halo` (soft halo used by inputs). The global rule
  `:focus-visible { outline: 2px solid primary; outline-offset: 2px }` already applies to links, buttons, inputs and
  `[tabindex]`. **Never remove an outline without replacing it.**
- Z-index: `--z-base` 0 · `--z-raised` 1 · `--z-sticky` 20 (topbars) · `--z-sidebar` 30 · `--z-dropdown` 40 ·
  `--z-scrim` 50 · `--z-drawer` 60 (mobile sidebar) · `--z-tooltip` 90 · `--z-skip-link` 100.
- Motion: `--duration-instant` 80ms · `-fast` 140ms (hover, colour) · `-base` 200ms · `-slow` 300ms (drawer);
  `--ease-standard`, `--ease-out` (entering), `--ease-in` (leaving). All durations become 0.01ms under
  `prefers-reduced-motion`.
- Control heights: `--control-height-sm` 32 · `-md` 40 (default) · `-lg` 48; `--tap-target-min` 44 (applied
  automatically at ≤ 640px to every `.btn` (`.btn-sm` included), `.btn-icon`, `.btn-link`, `.input` (file inputs
  included), `.select`, `.tab-button`, `.segmented-option`, the language switcher and the topbar user button). Icon sizes are the `size` prop
  of `IconGlyph` (14 / 16 inside small buttons, 16–20 in text, 20–24 in tiles).

### 2.7 Breakpoints (documented; CSS variables cannot be used in media queries)

| Name   | Query                        | What changes globally                                                                                                                                                 |
| ------ | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| wide   | `@media (max-width: 1200px)` | from 901 to 1200px the sidebar collapses to a 76px icon rail (labels as tooltips), the brand moves into the topbar, topbar subtitle hidden                            |
| drawer | `@media (max-width: 900px)`  | sidebar becomes an off-canvas drawer opened from the topbar menu button; public nav collapses into a menu                                                             |
| phone  | `@media (max-width: 640px)`  | language switcher moves into the drawer, touch-size controls, `.form-grid.two` stacks, `.form-actions` stack full width, `PaginationBar` hides its middle status text |

### 2.8 Tokens in JavaScript

Canvas drawing cannot use `var()`. Read the token once the element is mounted, with a literal fallback that matches
the token (used in SSR and unit tests):

```ts
import { readCssToken } from '@/utils/cssToken'

const ink = readCssToken(canvas, '--color-primary-800', '#3a1f9c')
```

`TracingPad` reads its ink (`--color-primary-800`), guide lines (`--color-primary-a24`), reference glyph
(`--color-ink-a12`) and keyboard cursor (`--focus-ring-color`) this way.

---

## 3. Which class or component for each element

| Element                                   | Use                                                                                                                                                                   | Notes                                                                                                                                                                                           |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Page header                               | `<PageHeaderBlock title description icon? eyebrow?>` with `#before`, `#meta` and `#actions` slots                                                                     | Classes `.page-header`, `.page-header-before`, `.page-title`, `.page-title-icon`, `.page-title-copy`, `.page-eyebrow`, `.page-header-meta`, `.page-header-actions`. See the header rules in §7. |
| Section header (inside a page or a panel) | `<div class="section-header"><div><h2 class="section-title">…</h2><p class="section-description">…</p></div><div class="section-header-actions">…</div></div>`        |                                                                                                                                                                                                 |
| Card / panel                              | `.panel`                                                                                                                                                              | radius-xl, border-subtle, shadow-sm, `--card-padding`                                                                                                                                           |
| Card with icon heading                    | `<InfoCard title subtitle icon>` or `.card-section-head` + `.card-section-title` + `.media-badge.small` + `.card-section-copy`                                        |                                                                                                                                                                                                 |
| Card parts                                | `.card-header`, `.card-title`, `.card-subtitle`, `.card-footer`                                                                                                       | `.card-footer` has a top divider and right-aligned actions                                                                                                                                      |
| Hero                                      | `.hero-card` (`--gradient-brand`, white text) or `.hero-card.light` (`--gradient-brand-soft`)                                                                         | inset tiles on the purple hero use `--surface-on-brand`                                                                                                                                         |
| Primary action                            | `.btn.btn-primary`                                                                                                                                                    | one per view                                                                                                                                                                                    |
| Brand CTA (marketing, "start learning")   | `.btn.btn-accent`                                                                                                                                                     | yellow, dark text                                                                                                                                                                               |
| Secondary action                          | `.btn.btn-secondary`                                                                                                                                                  | white, purple text, border                                                                                                                                                                      |
| Tertiary / outline                        | `.btn.btn-outline`                                                                                                                                                    | transparent, purple border                                                                                                                                                                      |
| Quiet action (toolbar, "Cancel", "Back")  | `.btn.btn-ghost`                                                                                                                                                      | no border; hover tint                                                                                                                                                                           |
| Destructive                               | `.btn.btn-danger` (solid) or `.btn.btn-danger-soft`                                                                                                                   | confirm before irreversible actions                                                                                                                                                             |
| Inline text action ("Open", "View all")   | `.btn.btn-link`                                                                                                                                                       | one fixed size everywhere: `--font-size-md`, no padding, 32px hit area (44px on phones). `.btn-sm` has no effect on it; do not add it.                                                          |
| Icon-only button                          | `.btn.btn-ghost.btn-icon` + `aria-label`                                                                                                                              | square 40 (`.btn-sm` 32); always 44 × 44 on phones                                                                                                                                              |
| Sizes / width                             | `.btn-sm` (32), default (40), `.btn-lg` (48); `.btn-block`                                                                                                            | page-header, card-footer and state-panel buttons go full width on phones automatically                                                                                                          |
| Icons in buttons                          | `IconGlyph` before the label: `plus` (create / add), `archive`, `trash` (remove / delete), `flag` (report), `chevronLeft` / `chevronRight` (paging, back, "view all") |                                                                                                                                                                                                 |
| Loading button                            | set `:aria-busy="saving"` and `:disabled="saving"`                                                                                                                    | a spinner appears automatically                                                                                                                                                                 |
| Form field                                | `.field` > `label` (`.field-label`) + `.input`/`.select`/`.textarea` + `.field-hint` + `.field-error`                                                                 | see §5                                                                                                                                                                                          |
| Field grid                                | `.form-grid` (1 col) / `.form-grid.two` (2 col, stacks ≤ 640)                                                                                                         |                                                                                                                                                                                                 |
| Form grouping                             | `<fieldset class="form-section"><legend class="form-section-title">`                                                                                                  |                                                                                                                                                                                                 |
| Submit / cancel row                       | `.form-actions` (right-aligned; stacks full width on phones; primary last in DOM)                                                                                     |                                                                                                                                                                                                 |
| Form card with a submit                   | `<ActionFormCard title subtitle? icon? submitLabel submitting? disabled? submitVariant?>`                                                                             | `submitVariant="secondary"` when the view already has a primary action                                                                                                                          |
| Checkbox / radio                          | `<label class="choice"><input type="checkbox">…</label>`                                                                                                              | brand accent colour automatically                                                                                                                                                               |
| Switch                                    | `<input type="checkbox" role="switch" class="switch">` inside a `<label>`                                                                                             |                                                                                                                                                                                                 |
| Search / input with icon                  | `.input-with-icon` > `IconGlyph.input-icon` + `.input`                                                                                                                |                                                                                                                                                                                                 |
| File upload                               | `<input type="file" class="input">`                                                                                                                                   | styled file button                                                                                                                                                                              |
| Tabs (in-page views)                      | `.tabs` > `button.tab-button` with `role="tab"` / `aria-selected` (or `.is-active`)                                                                                   | segmented style; `.tabs--underline` for page-level tabs; `.tabs--block` for equal-width full-width tabs (auth)                                                                                  |
| Segmented toggle                          | `.segmented` > `button.segmented-option[aria-pressed]`                                                                                                                | same look as the language switcher                                                                                                                                                              |
| Status                                    | `<StatusPill :label tone="success/warning/danger/info/primary/neutral" icon?>`                                                                                        | dot or icon + text                                                                                                                                                                              |
| Content status                            | `<ContentStatusBadge>`; lesson mastery `<MasteryChip>`; stage `<LearningStageTag>`                                                                                    |                                                                                                                                                                                                 |
| Tag / chip / count badge                  | `.tag` (neutral outlined), `.chip` (brand soft), `.badge` (count)                                                                                                     | tags and chips never wrap inside `.data-table`                                                                                                                                                  |
| Notice / banner                           | `<DataNotice>`: `using-fallback` (preview, yellow), `info`, `success`, `warning`, `error`; `#actions` slot                                                            | all preview banners must be DataNotice                                                                                                                                                          |
| Light inline banner                       | `.alert` + `.alert-success/-warning/-danger`                                                                                                                          | short messages inside a form or card                                                                                                                                                            |
| Loading / empty / error area              | `<StatePanel tone="loading/empty/error" title message eyebrow? icon? compact? :skeleton>` + `#actions` slot                                                           | or `<ResourceSection :loading :error @retry>`; see §4                                                                                                                                           |
| Spinner / skeleton                        | `.spinner` (`.is-sm`), `.skeleton` + `.skeleton-line` (`.is-short`)                                                                                                   | StatePanel renders the skeleton lines for you                                                                                                                                                   |
| List rows                                 | `<ListInfoItem title description? icon?>` (card row) or `.list-divided` for flat rows                                                                                 | `.list-action-row` for row actions                                                                                                                                                              |
| Table                                     | `.data-table-wrap` > `table.data-table`; `.col-actions`, `.col-numeric`                                                                                               | see §6                                                                                                                                                                                          |
| Pagination                                | `<PaginationBar>`                                                                                                                                                     | see §4; never hand-write `.pagination` markup                                                                                                                                                   |
| Breadcrumb                                | `<nav aria-label><ol class="breadcrumb"><li><a>…` ; current item `aria-current="page"`                                                                                | learning pages use `LearningBreadcrumb` in the header's `#before` slot                                                                                                                          |
| Key-value                                 | `<dl class="kv-list">`                                                                                                                                                |                                                                                                                                                                                                 |
| Divider                                   | `<hr class="divider">`                                                                                                                                                |                                                                                                                                                                                                 |
| Avatar                                    | `<PortraitAvatar :size="32/40/56" :name tone?>`                                                                                                                       | `tone`: `primary` (default) / `info` / `success` / `warning` / `accent`, each a fill + initials pair that meets AA (type `AvatarTone` in `avatarTone.ts`)                                       |
| Progress                                  | `<ProgressBar :progress tone? label>` (`tone` primary / success / warning / info; `class="is-sm/is-lg"`)                                                              |                                                                                                                                                                                                 |
| Progress ring                             | `<ProgressRing :progress title subtitle? tone? size?>`                                                                                                                | `tone` primary (default) / success / warning; `size` `md` (88px, default) or `sm` (56px dial, tighter copy, for side panels)                                                                    |
| Trend line                                | `<TrendChart :values label stroke?>`                                                                                                                                  | `stroke` accepts any CSS colour, including a token: `stroke="var(--color-success)"`                                                                                                             |
| Icons                                     | `<IconGlyph :name :size>` (Lucide; decorative, `aria-hidden`)                                                                                                         | the button or link carries the accessible name                                                                                                                                                  |
| Dropdown menu                             | `.menu` > `.menu-item` (`.is-danger`), `.menu-divider`                                                                                                                | position the `.menu` yourself; reuse `useOverlayFocus`                                                                                                                                          |
| Tooltip                                   | `data-tooltip="…"` on a focusable element, plus an `aria-label`                                                                                                       | decorative only                                                                                                                                                                                 |
| Inline feedback                           | `.inline-feedback` (`.is-error`) next to a button, `role="status"`                                                                                                    |                                                                                                                                                                                                 |

There are no modal, drawer, toast, layout-grid (`.two-col` …) or typography helper (`.type-h1` …) classes. The mobile
sidebar drawer is part of the app shell (§10). Build a page layout with its own page-prefixed grid using
`--grid-gap` (§7).

---

## 4. Snippets

### Page header

```vue
<PageHeaderBlock :title="t('users.title')" :description="t('users.subtitle')" icon="users">
  <template #meta><StatusPill :label="t('users.live')" tone="success" /></template>
  <template #actions>
    <button class="btn btn-secondary" type="button">{{ t('users.export') }}</button>
    <button class="btn btn-primary" type="button">{{ t('users.invite') }}</button>
  </template>
</PageHeaderBlock>
```

### Card with header and footer

```html
<section class="panel">
  <div class="card-header">
    <div>
      <h2 class="card-title">{{ t('x.title') }}</h2>
      <p class="card-subtitle">{{ t('x.subtitle') }}</p>
    </div>
    <button class="btn btn-link" type="button">{{ t('x.viewAll') }}</button>
  </div>
  …content…
  <div class="card-footer">
    <button class="btn btn-ghost" type="button">{{ t('common.cancel') }}</button>
    <button class="btn btn-primary" type="submit">{{ t('common.save') }}</button>
  </div>
</section>
```

### Form field with hint and error

```html
<div class="field">
  <label class="field-label" for="title"
    >{{ t('x.titleLabel') }}<span class="required-mark" aria-hidden="true">*</span></label
  >
  <input
    id="title"
    v-model="title"
    class="input"
    required
    :aria-invalid="Boolean(titleError)"
    :aria-describedby="titleError ? 'title-error' : 'title-hint'"
  />
  <p id="title-hint" class="field-hint">{{ t('x.titleHint') }}</p>
  <p v-if="titleError" id="title-error" class="field-error" role="alert">{{ titleError }}</p>
</div>
```

### Data states

Every data area has a loading, an empty and an error state. `useResource` sets `loading` only while there is no data
yet (later reloads keep the current data on screen), so the loading panel never flashes over content.

A whole page (or every block that depends on the same requests): wrap the blocks in `ResourceSection`. Add
`class="page-stack"` when it wraps several top-level blocks so they keep the page rhythm:

```vue
<DataNotice :using-fallback="usingFallback" :error="noticeError" :success="actionSuccess" />
<ResourceSection class="page-stack" :loading="pageLoading" :error="pageError" @retry="reloadAll">
  <section class="admin-kpi-grid">…</section>
  <section class="admin-split">…</section>
</ResourceSection>
```

One area inside a card (a list, a queue, a side panel): chain the states in the template, with the page's own copy:

```vue
<StatePanel
  v-if="resource.loading.value"
  tone="loading"
  icon="progress"
  compact
  :title="t('x.loading')"
  :message="t('x.loadingHint')"
/>
<StatePanel
  v-else-if="resource.error.value && !items.length"
  tone="error"
  icon="alert"
  compact
  :title="t('x.loadErrorTitle')"
  :message="resource.error.value"
>
  <template #actions><button class="btn btn-primary" type="button" @click="resource.reload">{{ t('x.retry') }}</button></template>
</StatePanel>
<ul v-else-if="items.length">…</ul>
<StatePanel v-else tone="empty" icon="search" compact :title="t('x.emptyTitle')" :message="t('x.emptyHint')">
  <template #actions><button class="btn btn-secondary" type="button" @click="clearFilters">{{ t('x.clear') }}</button></template>
</StatePanel>
```

- The error panel always offers a retry. When an area shows its own error panel, leave that error out of the page's
  `DataNotice` so the message is not shown twice (the catalog and course detail pages do this).
- A page whose title comes from the data (course detail) keeps an `sr-only` `h1` while it is loading or failed.

### Pagination

```vue
<PaginationBar
  v-if="totalPages > 1"
  :label="t('x.pagination')"
  :info="t('x.pageOf', { page: page + 1, total: totalPages })"
  :previous-label="t('x.previous')"
  :next-label="t('x.next')"
  :previous-disabled="page === 0"
  :next-disabled="page >= totalPages - 1"
  @previous="goToPage(page - 1)"
  @next="goToPage(page + 1)"
/>
```

| Prop / slot                                                        | Meaning                                                                                                                                           |
| ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `info` (required)                                                  | Summary text in `.pagination-info` ("Page 2 of 5", "1–10 of 42")                                                                                  |
| `label`                                                            | Accessible name; when set the bar renders as a `<nav>` landmark, otherwise a `<div>`                                                              |
| `previousLabel` / `nextLabel`, `previousDisabled` / `nextDisabled` | Button text and state; the parent decides when paging is disabled (first / last page, busy)                                                       |
| `iconOnly`                                                         | Chevron-only `.btn-icon` buttons; the labels become their `aria-label` (admin tables)                                                             |
| `status`                                                           | Short text between the two buttons (`.pagination-status`), hidden on phones                                                                       |
| `pageCount`, `currentPage` (1-based), `pageLabel(page)`            | Numbered mode: one button per page instead of previous / next; emits `select(page)`; the current page is `btn-primary` with `aria-current="page"` |
| `testIdPrefix`                                                     | Adds `<prefix>-pagination`, `-page-label`, `-previous-page`, `-next-page` test ids                                                                |
| `#start` slot                                                      | Controls before the summary, e.g. a rows-per-page select                                                                                          |
| Events                                                             | `previous`, `next`, `select(page)`                                                                                                                |

### Preview / feedback notices

```vue
<DataNotice :using-fallback="usingFallback" :error="error" :success="success" :fallback-title="t('x.previewTitle')" />
<DataNotice :info="t('x.reviewInfo')" />
<DataNotice :warning="t('x.unsavedWarning')">
  <template #actions><button class="btn btn-secondary btn-sm" type="button">{{ t('x.review') }}</button></template>
</DataNotice>
```

---

## 5. Forms

- Every control has a visible `<label for>`; placeholders are examples, never the label.
- Required: native `required` + `<span class="required-mark" aria-hidden="true">*</span>`; explain once at the top of
  long forms.
- Errors: `aria-invalid="true"` on the control (red border + red halo) and a `.field-error` element referenced by
  `aria-describedby`. Hints use `.field-hint`, also referenced. Both have no margin; `.field` spaces them.
- Heights: inputs and selects are 40px (44px on phones). Do not override heights per page.
- Long forms: group with `.form-section` fieldsets; put `.form-actions` at the end (secondary first, primary last).
  On phones `.form-actions` stacks full width with the primary on top.

## 6. Tables

```html
<div class="data-table-wrap" role="region" :aria-label="t('users.tableLabel')" tabindex="0">
  <table class="data-table">
    <thead>
      <tr>
        <th scope="col">{{ t('users.name') }}</th>
        …
        <th scope="col" class="col-actions"><span class="sr-only">{{ t('users.actions') }}</span></th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.id">
        <td>…</td>
        <td class="col-numeric">{{ row.count }}</td>
        <td class="col-actions">
          <button class="btn btn-ghost btn-icon btn-sm" :aria-label="t('users.more', { name: row.name })">…</button>
        </td>
      </tr>
    </tbody>
  </table>
</div>
```

- The wrap scrolls horizontally with edge shadows as the affordance; the table keeps `min-width: 640px` (override with
  your own page class if you need more). Never let a column be clipped by `overflow: hidden` on the card.
- Below 640px, tables with more than 4 columns should switch to stacked cards (`<ListInfoItem>`), or keep the scroll
  wrapper.
- The actions column is always last, right-aligned, `white-space: nowrap`, using `.btn-sm`/`.btn-icon`.
- Status cells use `<StatusPill>`; dates and numbers use `.col-numeric` for alignment.
- Paging under a table is a `<PaginationBar>`.

## 7. Page templates and rhythm

All dashboard pages render inside `<main class="page-stack dashboard-main">`: padding `--space-6` top, `--page-gutter`
sides, max width `--page-max`, vertical gap `--page-gap`. The page root is **a single element** with the page class
and `page-stack` (flex column, `--page-gap`).

**Header rules**

- Dashboard pages (student, teacher, admin): `PageHeaderBlock` with an icon, title, one- or two-sentence description,
  then `#meta` and `#actions`. **No eyebrow**: the sidebar group already names the area.
- Public and marketing pages (catalog, legal pages) may use an eyebrow above the title.
- Context that belongs above the title (the learning breadcrumb) goes in the `#before` slot, never in a separate card.
- The preview / error `DataNotice` sits directly under the header.

**Dashboard page**

```
PageHeaderBlock
DataNotice (preview / error), if any
ResourceSection class="page-stack" (loading / error with retry)
  KPI row:   .your-kpis { display:grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: var(--grid-gap) }
  Main grid: your page-prefixed grid, e.g. grid-template-columns: minmax(0, 1.4fr) minmax(0, 0.8fr); gap: var(--grid-gap);
             one column at ≤ 1200px
    .panel blocks with .card-header / .section-header
```

**List + detail page** (users, content review, verification)

```
PageHeaderBlock (primary action in #actions)
DataNotice
.your-layout { display:grid; grid-template-columns: minmax(0, 1fr) minmax(320px, 0.42fr); gap: var(--grid-gap) }
  list panel: .tabs (filters) → toolbar (.input-with-icon search + .select filters) → list / .data-table-wrap
              with its loading / error / empty StatePanel → <PaginationBar>
  detail panel: .panel with .card-header, .kv-list, .card-footer actions
@media (max-width: 1200px) → single column (detail below the list)
```

**Form page** (account, settings, profile)

```
PageHeaderBlock
.panel (max-width: 880px when standalone) > form.form-grid
  fieldset.form-section × n (legend.form-section-title)
  .form-actions
```

**Public content page** (terms, privacy, responsible AI, catalog)

```
.public-shell (already provides width and the top nav)
<header class="page-header"> with .page-title > .page-title-copy > .page-eyebrow + h1 + p   (or PageHeaderBlock)
content in .panel blocks; long text constrained to --reading-max
```

## 8. Responsive rules

- Use the three breakpoints only. Prefer intrinsic layouts (`repeat(auto-fit, minmax(…, 1fr))`, `minmax(0, 1fr)`,
  `flex-wrap`) so that most components need no media query at all.
- Every grid/flex child that holds text gets `min-width: 0`; long words use `overflow-wrap: anywhere` (Malay and
  Chinese strings are long or unbreakable).
- At ≤ 640px: one column, actions full width (`.page-header-actions > .btn`, `.form-actions`, `.card-footer > .btn`
  and `.state-actions > .btn` do it automatically), tap targets ≥ 44px, no horizontal page scroll.
- Never hide information on mobile that is needed to complete a task; move it below instead.

## 9. Do and don't

**Do**

- Use `PageHeaderBlock` for the title; keep descriptions to one or two sentences.
- Keep one `.btn-primary` per view; pair it with secondary/ghost buttons.
- Use `.btn-link` for inline "Open" / "View all" actions.
- Use `StatusPill` for any status, with a text label.
- Show loading, empty and error states with `StatePanel` / `ResourceSection` (with a retry action for errors).
- Use `PaginationBar` for every pager, and the component props (`ProgressRing size`, `PortraitAvatar tone`, …) instead
  of page overrides.
- Use `--gradient-brand` / `--surface-on-brand` on purple heroes and the translucent tokens instead of new `rgba()`.
- Check `en`, `ms` and `zh-CN`; Malay strings are ~30 % longer.

**Don't**

- Don't hard-code colours, font sizes, radii or shadows; don't add `!important` (only the reduced-motion block has it).
- Don't restyle global classes (`.btn`, `.panel`, `.input`, `.tabs`, `.sidebar-*`, `.topbar-*`, `.page-header`) from a
  page section without a page-class prefix, never change their size or colour globally, and never use `:deep()` on a
  shared component.
- Don't set `overflow: hidden` on a card that contains a table or a dropdown.
- Don't use colour alone for meaning, or text below 12px.
- Don't add hover lift (`translateY`) to non-interactive cards. Interactive cards may lift by `-2px` with
  `--shadow-lg`.
- Don't add new breakpoints, z-index numbers or animation timings.
- Don't leave unused classes or tokens behind.

## 10. App shell reference

- `DashboardLayout`: skip link → `.dashboard-shell` (`.dashboard-shell--student|teacher|admin`, which also paints the
  sidebar column for the full page height) → `DashboardSidebar` (`#app-sidebar`) + `.content-shell` →
  `DashboardTopbar` + `<main id="main-content" tabindex="-1">`.
- Sidebar > 1200px: sticky full-height column (brand, role intro with the panda, grouped navigation with
  `aria-current="page"`, footer card). 901–1200px: 76px icon rail with tooltips. ≤ 900px: off-canvas drawer
  (`role="dialog"`, `--z-drawer` over the `--surface-scrim` scrim, focus trapped by `useOverlayFocus`, Escape and the
  scrim close it, body scroll locked, focus returns to the menu button, closes on navigation).
- Topbar: menu button + brand (< 900px), role badge + context (≥ 900px), language switcher (≥ 640px; inside the
  drawer below), streak, notifications (`data-testid="topbar-notifications"`), user menu (Profile, Account & security
  `data-testid="topbar-account"`, Sign out). Sticky at the top of the content column; the page scrolls under it.
- `PublicLayout`: skip link, `PublicTopNav` (sticky pill bar; < 900px a menu button reveals links, language switcher
  and sign-in buttons), `#main-content`.
- System pages (404 / 403) are standalone: `.system-page` > `.system-card` with quick links (`.system-links` >
  `.system-link`, four static links on two pages, kept as page markup).
- A page must not add its own sticky headers above `--z-sticky` or fixed elements that cover the topbar.

## 11. Changes since the first version of this document

- **Tokens added:** `--gradient-brand`, `--gradient-brand-soft`, `--surface-on-brand`, `--surface-on-brand-hover`.
- **Tokens removed:** all legacy aliases (`--primary`, `--primary-soft`, `--text`, `--text-soft`, `--surface`,
  `--border`, `--shadow`, `--shadow-soft`, `--shadow-lift`, `--shadow-focus`, `--success`, `--danger`, …),
  `--text-muted` (use `--text-secondary`), `--type-h3-size`, `--shadow-inset`, `--focus-ring`, `--z-modal`,
  `--z-toast`, `--icon-size-sm/md/lg` (none had a user).
- **Primitives removed (no users):** `.type-display`, `.type-h1`, `.type-h2`, `.type-h3`, `.type-body`,
  `.type-body-sm`, `.type-label`, `.type-overline`; `.two-col`, `.three-col`, `.split-grid`; `.surface-card`;
  `.card-body`; `.soft-banner`; `.skeleton-block`; `.kv-list--stacked`; `.divider-vertical`; `.modal-backdrop`,
  `.modal`, `.modal-header`, `.modal-title`, `.modal-body`, `.modal-footer`; `.drawer`; `.toast-region`, `.toast`;
  `.btn-block-mobile`; the `.input/.select/.textarea.is-invalid` aliases (use `aria-invalid="true"`).
- **New components and props:** `PaginationBar` (replaces ten hand-written `.pagination` blocks) with
  `.pagination-status`; `ProgressRing` `size="sm"` and `tone="warning"` (replaces the Feedback page's `:deep(.ring)`
  override); `PageHeaderBlock` `#before` slot; `ActionFormCard` `submitVariant`; `PortraitAvatar` `tone` (the hex
  `accent` prop is gone); `TrendChart` `stroke` accepts tokens; `IconGlyph` `plus`, `archive`, `trash`, `flag`;
  `.tabs--block`; `readCssToken` for canvas colours.
- **Behaviour of primitives:** `.btn-link` has one size (32px hit area, 44px on phones, `btn-sm` ignored);
  `.btn-icon` is 44 × 44 on phones at every size; `.form-actions` stacks without wrapping on phones; `.field-hint` has no
  margin; tags and chips do not wrap in tables.
- **Header rules:** dashboard headers have no eyebrow; learning breadcrumbs live in `#before`.
- **States:** catalog, course detail, landing featured courses, moderation, teacher verification, content, content
  review, activity library, teacher content and AI helper reports now have loading and error-with-retry states.
- **Acceptance pass (A1):** a page notice never repeats a load error that a `ResourceSection` or an area `StatePanel`
  already shows (pass only action errors, plus errors of requests that have no panel of their own); every control is
  at least 44px tall on phones; `.tabs` may shrink inside flex and grid parents (`min-width: 0`); `PortraitAvatar` is
  decorative (`aria-hidden`) unless an `ariaLabel` is passed; language-switcher names start with the visible label
  ("BM, Switch to Bahasa Melayu").
