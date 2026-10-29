# MandarinSpark design system

The reference for every page and component in the frontend. It documents only what exists in the code.

Source of truth:

- `src/styles/tokens.css`: every token below, imported first by `main.css`.
- `src/styles/main.css`, foundation block (top of the file, up to the banner
  `Page sections (page-prefixed classes only)`): base elements, the app shell, focus mode and all shared primitives
  (overlays, callout, toast, hanzi tile and seal included). Shell-wide page sections follow, with page-prefixed classes
  only.
- Module stylesheets, imported by the pages and components of one area only: `learn.css`, `learn-practice.css`,
  `learn-progress.css`, `learn-admin.css` (Learn, Practice, Progress, admin Learning path / Practice library),
  `plan.css` (Home, setup, level check and study plan), `classes.css` (teacher console, student classes, live
  sessions), `ai.css` (AI Helper), `ops-public.css`, `ops-account.css`, `ops-admin.css` (public site, account,
  notifications and support, admin console including admin AI and content review). A component used by more than one area belongs in `components/common` with its CSS in `main.css`, never in
  a module file.
- Shared components:
  - `components/sections`: `PageHeaderBlock`;
  - `components/common`: `AppOverlay`, `ConfirmDialog`, `AppToast`, `AppBreadcrumb`, `RouteTabs`, `SegmentedControl`,
    `StatePanel`, `DataNotice`, `StatusPill`, `PaginationBar`, `ProgressBar`, `ProgressRing`, `PortraitAvatar`,
    `MasteryChip`, `TrendChart`, `LanguageSwitcher`, `IconGlyph`;
  - `components/layout`: the shell (`DashboardSidebar`, `DashboardTopbar`, `PublicTopNav`,
    `AppBrand`) and `FocusTopBar`.
- Shared composables: `useResource` / `useServerPage` (loading, error, paging), `useActionFeedback`, `useToast`,
  `useOverlayFocus`, `useLocalMessages`.
- Default texts of the shared components: `plugins/commonMessages.ts`.

Change a primitive or a token here, in the foundation block or `tokens.css`, never from a page section.

---

## 0. Creative direction

MandarinSpark is a calm study desk, not a game arcade and not an admin template. The layout and type are drawn from
the materials of learning Chinese by hand; the colours are the brand's own purple and yellow.

- **Page and sheets.** The page is a cool, light indigo grey (`--surface-page`). White sheets (`.panel`) are laid
  on it only where content needs its own edge. Open sections (a serif heading, a hairline, content) are the default;
  a wall of cards is not.
- **Spark purple** (`--color-primary-*`) is the brand and the interactive colour: primary buttons, links, focus,
  selection, the current item, default progress, and the marks of anything earned (`.seal`).
- **Sunny yellow** (`--color-accent-*`) is the highlight: the one hero action of a page (`.btn-accent`, at most one
  per page) and count badges. It always carries ink text (`--text-on-accent`) and is never a text colour.
- **Ink** (`--color-ink-900`, indigo) is the text. Hierarchy comes from type, size and space, not from colour fills.
- **The wordmark** keeps its two colours: "Mandarin" in `--color-brand-orange`, "Spark" in purple, beside the 火 seal.
- **The 田字格 square** (`.hanzi-tile`, `.state-icon`): the practice grid every character is learnt in, with a dashed
  purple cross (`--grid-line`). It frames characters, empty states and the word of the day. It is the product's
  signature motif; do not decorate with it where no character or learning moment is shown.
- **Type.** Fraunces (`--font-display`) for page titles, section titles, card titles and big numbers; Plus Jakarta
  Sans (`--font-body`) for everything you scan; Noto Serif SC (`--font-hanzi`, class `.hanzi`) for a character shown
  as something to learn. Chinese running text next to pinyin stays `.cjk-text`.
- **No effects.** No gradients on surfaces, no glass, no glow shadows, no emoji as decoration. A page must read well
  in greyscale: structure first, colour second.

### 0.1 One system, several personalities

Every module uses the same tokens and primitives, but composes them for its job. Each has a pigment
(`--module-*`) used for its accents (progress fills via `--progress-color`, markers, small labels), never for large
fills.

| Module               | Pigment                     | Personality and composition                                                                                                                                                                     |
| -------------------- | --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Home                 | purple + yellow             | A desk for today: one next step, today's plan as a timeline, the streak as stamped days. Nothing else competes with "continue".                                                                 |
| Learn                | `--module-learn` (purple)   | A journey: a path of units and lessons that unlock, with the current one obvious, finished ones stamped and locked ones quiet. Lessons run in focus mode.                                       |
| Practice             | one pigment per type        | An open practice room: each type (flashcards, quizzes, listening, tracing, drag & drop, matching, story) has its own pigment and glyph so they are told apart at a glance; feedback is instant. |
| AI Helper (Xiaomei)  | `--module-ai` (plum)        | Presence and conversation: Xiaomei is a person you talk to, the conversation is the page, controls stay out of the way.                                                                         |
| Classes              | `--module-classes` (teal)   | Teacher-led: the teacher's name and face lead every class, schedules read like a timetable. Clearly different from the platform's own Learn and Practice.                                       |
| Progress / Plan      | `--module-progress` (ochre) | Growth and momentum: the level, the streak calendar and earned seals tell a story; numbers are big and few.                                                                                     |
| Teacher console      | teal accents on purple      | A working desk: denser, list- and timetable-led, the same type and paper.                                                                                                                       |
| Admin console        | purple                      | Professional and efficient: queues, tables and filters first; colour only for status.                                                                                                           |
| Public site and auth | purple + yellow             | A product showcase: editorial type, real product moments (a lesson tile, Xiaomei, a class), not feature-card grids.                                                                             |

### 0.2 Composition rules

1. **Consistent, never repetitive.** Do not build pages as title → subtitle → three or four equal cards → section →
   more cards. Choose the composition that fits the content: a timeline, a path, a list with hairlines, a split
   layout, one featured item and a quiet list.
2. **One focus per screen.** Each page has one primary thing (the next lesson, the conversation, the queue). Secondary
   material sits beside or below it, smaller and quieter.
3. **Rhythm.** 8 inside a group, 16 between groups, 24–32 between blocks, 48–64 between sections (`--page-gap` is 40
   on desktop). Generous margins beat extra boxes.
4. **Hairlines over boxes.** Separate rows with `--border-subtle` hairlines. Nest a panel inside a panel only for a
   genuinely separate object (a quoted message, a preview).
5. **Numbers are type.** Show figures with `.stat-figure` (Fraunces, tabular) and a short caption, not in tinted
   tiles.
6. **States are designed.** Every list has its loading, empty, error, completed and locked look (§3).
7. **Desktop only, still fluid.** MandarinSpark is a desktop web app for laptop and desktop browsers. There is one
   layout and one navigation; it flexes between a 1024px window and a large monitor, and narrower windows scroll
   sideways (`--page-min-width`). Do not add phone or tablet layouts.

---

## 1. Rules in one minute

1. **Only tokens.** No raw hex, `rgba()`, px font sizes, radii or shadows in your rules when a token exists.
   Spacing uses `--space-*`. Dynamic, data-driven inline styles (a progress width) are the only inline styles allowed.
   Illustration art (`XiaomeiAvatar`) keeps its own palette.
2. **One page header.** Every dashboard page starts with `<PageHeaderBlock>` (optional eyebrow, serif title, short
   lede) or, where a module needs its own hero, an equivalent block with exactly one `h1`; a focus-mode task starts
   with `<FocusTopBar>` instead. Exactly one `h1` per page, including while the page is loading or failed.
3. **Shared primitives first.** Buttons are `.btn` + a variant; status is `<StatusPill>`; notices are `<DataNotice>`;
   loading, empty and error areas are `<StatePanel>`; paging is `<PaginationBar>`; every dialog and drawer is
   `<AppOverlay>` or `<ConfirmDialog>`; a same-page confirmation is `<AppToast>`. Never build a second version of one
   of these inside a module.
4. **One width breakpoint: `max-width: 1200px`** (small laptops: side rails move under the main column). Do not add
   new ones; the one height exception is listed in §2.7.
5. **Never colour-only.** Status carries text (and optionally an icon); colour reinforces it.
6. **Motion** uses `--duration-*` and `--ease-*`. Everything already collapses under `prefers-reduced-motion`.
7. Keep page selectors prefixed (`.lesson-editor-*`, `.admin-users-*`). Do not restyle `.btn`, `.panel`, `.input`
   etc. globally from a page section; scope with the page class if you truly need a local variant. Never reach into a
   shared component with `:deep()`; add a prop or a modifier to the component instead.
8. **No dead CSS.** A class that no template or script uses is deleted, primitives included. Add a primitive when the
   first page needs it.

---

## 2. Tokens

### 2.1 Colour palette

| Scale                                               | 50      | 100     | 200     | 300     | 400     | 500         | 600     | 700     | 800     | 900     |
| --------------------------------------------------- | ------- | ------- | ------- | ------- | ------- | ----------- | ------- | ------- | ------- | ------- |
| `--color-primary-*` (Spark purple)                  | #f6f3ff | #ede8ff | #dcd2ff | #c2b2ff | #9d85ff | **#6f4cff** | #5a35ec | #4a28c9 | #3a1f9c | —       |
| `--color-accent-*` (sunny yellow)                   | #fffaeb | #fff2c7 | #ffe696 | #ffdb66 | #ffd247 | **#ffcc2f** | #f0b400 | #c28d00 | #8f6700 | #5f4500 |
| `--color-ink-*` (indigo-tinted grey; `-25` #fafbff) | #f5f6fc | #eceff8 | #e0e4f1 | #cbd1e3 | #a2aac4 | #626b88     | #5c6687 | #434d6d | —       | #152042 |

`--color-white` (#ffffff). `--color-brand-orange` (#f97316) is "Mandarin" in the wordmark.

Pigments (`--pigment-*`, each with `-soft`): cobalt #3554b5, cinnabar (here a deep orange) #c2410c, jade #2c7a5a,
ochre #98620f, plum #7b3f78, teal #1e6f7d, ink (the indigo ink). Module aliases: `--module-learn` (purple),
`--module-classes` (teal), `--module-ai` (plum), `--module-progress` (ochre), each with `-soft`. Every pigment
carries white text (≥ 5.1:1) and reads as text on its own `-soft` (≥ 4.6:1).

Semantic families (each has a fill, a soft background, a border and an AA text colour; warning and danger also have a
strong solid):

| Family            | fill    | `-soft` | `-border` | `-text` | `-strong`                                      |
| ----------------- | ------- | ------- | --------- | ------- | ---------------------------------------------- |
| `--color-success` | #2f9e44 | #eaf8ec | #bfe5c6   | #1e7a34 | —                                              |
| `--color-warning` | #f5a524 | #fff5e2 | #fbd99a   | #8a5100 | #a35f00                                        |
| `--color-danger`  | #e5484d | #fff0f0 | #f7c3c5   | #c0262d | #c0262d (hover `--color-danger-hover` #a51f26) |
| `--color-info`    | #2f8fff | #ecf5ff | #bcd8ff   | #1a5fc0 | —                                              |

- **fill**: icons, dots, progress fills, chart marks. Never body text (the warning fill is 2.0:1 on white).
- **soft**: tinted backgrounds of pills, notices and states.
- **text**: text or icons on soft or white backgrounds.
- **strong**: solid backgrounds that carry white text (danger button, notification badge).

Translucent tokens: `--color-primary-a04/a16/a32`, `--color-white-a80`, `--color-ink-a12/a16/a48`,
`--color-danger-a12`.

Grounds and lines: `--grid-line` (the dashed purple of the 田字格), `--paper-grid` (a faint 28px square grid for
practice surfaces).

### 2.2 Semantic colour roles (prefer these over raw scale steps)

| Role                                             | Token                                                       | Value                                                  |
| ------------------------------------------------ | ----------------------------------------------------------- | ------------------------------------------------------ |
| Page background                                  | `--surface-page`                                            | ink-50                                                 |
| Sheet / panel                                    | `--surface-card`                                            | white                                                  |
| Sunken (inset areas, disabled inputs)            | `--surface-sunken`                                          | ink-100 (one step below the page, visible on it)       |
| Subtle                                           | `--surface-subtle`                                          | ink-25                                                 |
| Hover tint / selected                            | `--surface-hover` / `--surface-selected`                    | primary-50 / primary-100                               |
| Dark surface (tooltip, toast)                    | `--surface-inverse`                                         | ink-900                                                |
| Drawer scrim                                     | `--surface-scrim`                                           | ink 48 %                                               |
| Border subtle (sheets, hairlines)                | `--border-subtle`                                           | #e8ebf5                                                |
| Border default (controls at rest, tags)          | `--border-default`                                          | ink-200                                                |
| Border strong (inputs)                           | `--border-strong`                                           | ink-300                                                |
| Border interactive (hover of secondary controls) | `--border-interactive`                                      | primary-300                                            |
| Text primary (ink)                               | `--text-primary`                                            | ink-900                                                |
| Text secondary (muted copy)                      | `--text-secondary`                                          | ink-600                                                |
| Text disabled                                    | `--text-disabled`                                           | ink-400 (exempt from contrast; never for real content) |
| Text on primary / on accent / on dark            | `--text-on-primary` / `--text-on-accent` / `--text-on-dark` | white / ink-900 / white                                |
| Link / link hover                                | `--text-link` / `--text-link-hover`                         | primary-600 / primary-700                              |
| Brand text (overlines, marks)                    | `--text-brand`                                              | primary-600                                            |

### 2.3 Contrast (WCAG 2.1, computed from the token values)

| Foreground                  | Background                          | Ratio                                        |
| --------------------------- | ----------------------------------- | -------------------------------------------- |
| `--text-primary`            | `--surface-card` / page             | 15.94 / 14.78                                |
| `--text-secondary`          | `--surface-card` / page             | 5.67 / 5.25                                  |
| `--text-secondary`          | `--color-ink-100`                   | 4.93                                         |
| `--color-ink-500`           | `--surface-card` / page             | 5.31 / 4.89                                  |
| `--text-link` (primary-600) | page                                | 6.17                                         |
| `--color-primary-700`       | white / primary-50 / -100           | 8.61 / 7.87 / 7.21                           |
| `--color-primary-500`       | primary-50                          | 4.62                                         |
| white                       | primary-500 / -600                  | 5.06 / 6.66                                  |
| `--text-on-accent` (ink)    | accent-500                          | 10.58                                        |
| `--color-accent-800`        | accent-100                          | 4.57                                         |
| white                       | teal / plum / jade / ochre / orange | 5.79 / 7.44 / 5.20 / 5.14 / 5.18             |
| pigment                     | its `-soft`                         | ≥ 4.65 (cobalt 5.81, plum 6.24, orange 4.76) |
| `--color-success-text`      | `--color-success-soft`              | 4.92                                         |
| `--color-warning-text`      | `--color-warning-soft`              | 5.96                                         |
| `--color-danger-text`       | `--color-danger-soft`               | 5.34                                         |
| `--color-info-text`         | `--color-info-soft`                 | 5.54                                         |
| white                       | danger-strong                       | 5.90                                         |

Non-text (3:1 for icons, focus indicators, meaningful marks): primary-500 5.06 on white; the focus ring is primary-500.

**Fails, by design (do not use for text):** yellow as a text colour (accent-500 is 1.6:1 on white; yellow always
carries ink text), `--color-brand-orange` (a logotype, exempt), `--text-disabled`, the warning fill (2.0:1, always
pair with a text label), `--border-strong` and `--grid-line` (decoration and outlines supplemented by labels).

### 2.4 Typography

Families: `--font-display` (Fraunces; page, section and card titles, big numbers), `--font-body` (Plus Jakarta Sans;
everything else), `--font-hanzi` (Noto Serif SC; characters shown as something to learn, class `.hanzi`),
`--font-cjk` (Chinese running text next to pinyin; class `.cjk-text`).

Size scale (rem): `--font-size-2xs` 0.6875 (11) · `-xs` 0.75 (12) · `-sm` 0.8125 (13) · `-md` 0.875 (14) ·
`-base` 1 (16) · `-lg` 1.125 (18) · `-xl` 1.25 (20) · `-2xl` 1.5 (24) · `-3xl` 1.875 (30) · `-4xl` 2.25 (36) ·
`-5xl` 2.75 (44) · `-6xl` 3.5 (56) · `-7xl` 4.25 (68) · `-8xl` 5.5 (88).

Weights: `--font-weight-regular` 400 · `-medium` 500 · `-semibold` 600 · `-bold` 700.
Line heights: `--line-height-tight` 1.1 · `-snug` 1.3 · `-normal` 1.55 · `-relaxed` 1.7.
Letter spacing: `--letter-spacing-display` -0.025em (serif titles) · `--letter-spacing-wide` 0.08em (overlines).

| Role               | Size token                             | Font / weight              | Rendered by                              | Use                        |
| ------------------ | -------------------------------------- | -------------------------- | ---------------------------------------- | -------------------------- |
| Display            | `--type-display-size` (44–76 px fluid) | display 600                | landing hero, module heroes              | One per page at most       |
| H1 page title      | `--type-h1-size` (30–42 px fluid)      | display 600                | `PageHeaderBlock` (`.page-title h1`)     | One per page               |
| H2 section         | `--type-h2-size` (20–24 px fluid)      | display 600                | `.section-title`                         | Section headers            |
| H3 card title      | `--font-size-lg` (18)                  | display 600                | `.card-title`                            | Card and panel titles      |
| Stat               | `--type-stat-size` (32–44 px fluid)    | display 600, tabular       | `.stat-figure`                           | XP, streak, scores, counts |
| Body               | `--type-body-size` (16)                | body 400                   | `body`                                   | Paragraphs                 |
| Body small         | `--font-size-md` (14)                  | body 400                   | `.card-subtitle`, `.section-description` | Dense UI, descriptions     |
| Label              | `--type-label-size` (14)               | body 700                   | `.field-label`                           | Form labels                |
| Caption            | `--type-caption-size` (13)             | body 400, secondary colour | `.type-caption`                          | Meta, timestamps           |
| Overline / eyebrow | `--type-overline-size` (11)            | body 700, uppercase, wide  | `.page-eyebrow`                          | Above titles, row labels   |

### 2.5 Spacing (4 px base)

`--space-0-5` 2 · `-1` 4 · `-1-5` 6 · `-2` 8 · `-2-5` 10 · `-3` 12 · `-3-5` 14 · `-4` 16 · `-5` 20 ·
`-6` 24 · `-7` 28 · `-8` 32 · `-10` 40 · `-12` 48 · `-16` 64 · `-20` 80 · `-24` 96.

Layout rhythm tokens (the gutter and page gap tighten at ≤ 1200px, so use them instead of media queries):

| Token                                                        | > 1200 px | ≤ 1200 px |
| ------------------------------------------------------------ | --------- | --------- |
| `--page-gutter` (main padding left/right)                    | 40        | 32        |
| `--page-gap` (gap between top-level blocks of `.page-stack`) | 40        | 32        |
| `--section-gap` (gap inside a section, header → content)     | 20        | 20        |
| `--card-padding` (`.panel`, overlays)                        | 24        | 24        |
| `--grid-gap` (grids)                                         | 20        | 20        |

Other layout tokens: `--page-min-width` 1024px (the narrowest window the pages are laid out for), `--page-max`
1440px, `--reading-max` 68ch, `--sidebar-width` 244px, `--topbar-height` 64px, `--topbar-control-size` 44px (the
header's bell and account chip).

### 2.6 Radius, borders, shadows, z-index, motion, sizes

- Radius: `--radius-xs` 4 (tiles, tags, seals) · `-sm` 8 (inputs, small buttons, chips) · `-md` 10 (buttons, nav
  items, tabs) · `-lg` 14 (panels, dialogs, notices) · `-xl` 18 (feature surfaces) · `-2xl` 24 (hero art only) ·
  `-pill` (status pills, counts) · `-round` 50%.
- Borders: plain `1px` hairlines · `--border-width-thick` 2px · `--border-width-focus` 2px.
- Shadows are paper-soft: `--shadow-xs` (sheets at rest) · `-sm` (raised sheets) · `-md` (sticky bars) · `-overlay`
  (menus, dialogs, drawers). No coloured glow shadows.
- Focus: `--focus-ring-color` (primary-500), `--focus-ring-offset`, `--focus-halo` (inputs). The global rule
  `:focus-visible { outline: 2px solid purple; outline-offset: 2px }` applies to links, buttons, inputs and
  `[tabindex]`. **Never remove an outline without replacing it.**
- Z-index: `--z-raised` 1 · `--z-sticky` 20 (topbars) · `--z-sidebar` 30 · `--z-drawer` 60 (dialogs and drawers
  over their scrim) · `--z-tooltip` 90 · `--z-skip-link` 100.
- Motion: `--duration-fast` (hover, colour) · `-base` (enter) · `-slow` (drawer); `--ease-standard`, `--ease-out`,
  `--ease-spring` (a seal landing, a correct answer). All durations collapse under `prefers-reduced-motion`.
- Control heights: `--control-height-sm` 32 · `-md` 42 (default) · `-lg` 52. Icon sizes are the `size` prop of
  `IconGlyph` (14 / 16 in small buttons, 16–20 in text, 20–24 in tiles).

### 2.7 Breakpoints (documented; CSS variables cannot be used in media queries)

The app is laid out for desktop windows from `--page-min-width` (1024px) up; below that the page scrolls sideways.
There is one width breakpoint:

| Name  | Query                        | What changes                                                                                              |
| ----- | ---------------------------- | --------------------------------------------------------------------------------------------------------- |
| small | `@media (max-width: 1200px)` | small laptops: gutters tighten and page rails (Home, Learn, Plan, class pages) move under the main column |

One height query exists on purpose; do not reuse it for new work:

- `@media (max-height: 820px)`: short laptop screens hide the sidebar's word of the day, so the whole navigation
  and the status stay in view.

### 2.8 Tokens in JavaScript

Canvas drawing cannot use `var()`. Read the token once the element is mounted, with a literal fallback that matches
the token (used in SSR and unit tests):

```ts
const ink = window.getComputedStyle(canvas).getPropertyValue('--color-ink-900').trim() || '#1d1b22'
```

### 2.9 Signature primitives

| Primitive     | Markup                                                                   | Use                                                                                                                            |
| ------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| 田字格 tile   | `<span class="hanzi-tile" style="--hanzi-tile-size: 96px">我</span>`     | A character to learn. Size with `--hanzi-tile-size`; the glyph scales with it.                                                 |
| Seal          | `<span class="seal">学</span>` (+ `.is-outline`, `.is-muted`)            | A purple stamp for something earned or finished (`.seal`), upcoming (`.is-outline`), locked (`.is-muted`). Size `--seal-size`. |
| Stat figure   | `<strong class="stat-figure">240</strong>`                               | A headline number with a caption beside or below.                                                                              |
| Hanzi text    | `<span class="hanzi">学</span>`                                          | A character shown large outside a tile (flashcard face, hero).                                                                 |
| Module colour | `style` none; set `--progress-color: var(--module-learn)` on a page root | Fills `ProgressBar` / `ProgressRing` in the module's pigment.                                                                  |

---

## 3. Which class or component for each element

| Element                                   | Use                                                                                                                                                                   | Notes                                                                                                                                                                                        |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Page header                               | `<PageHeaderBlock title description eyebrow?>` with `#before`, `#meta` and `#actions` slots                                                                           | Classes `.page-header`, `.page-header-before`, `.page-title`, `.page-title-copy`, `.page-eyebrow`, `.page-header-meta`, `.page-header-actions`. See the header rules in §7.                  |
| Section header (inside a page or a panel) | `<div class="section-header"><div><h2 class="section-title">…</h2><p class="section-description">…</p></div><div class="section-header-actions">…</div></div>`        |                                                                                                                                                                                              |
| Card / panel                              | `.panel`                                                                                                                                                              | a white sheet: radius-lg, hairline, shadow-xs, `--card-padding`; use only where content needs its own edge                                                                                   |
| Card parts                                | `.section-header`, `.card-title`, `.card-subtitle`, `.card-footer`                                                                                                    | `.card-footer` has a top divider and right-aligned actions                                                                                                                                   |
| Hero                                      | a page-prefixed open section: overline, display-size serif title, one action; no gradient                                                                             |                                                                                                                                                                                              |
| Primary action                            | `.btn.btn-primary`                                                                                                                                                    | purple fill; one per view                                                                                                                                                                    |
| Brand CTA (marketing, "start learning")   | `.btn.btn-accent`                                                                                                                                                     | yellow, ink text; at most one per page                                                                                                                                                       |
| Secondary action                          | `.btn.btn-secondary`                                                                                                                                                  | white sheet, purple text, hairline border                                                                                                                                                    |
| Tertiary / outline                        | `.btn.btn-outline`                                                                                                                                                    | transparent, purple text and border                                                                                                                                                          |
| Quiet action (toolbar, "Cancel", "Back")  | `.btn.btn-ghost`                                                                                                                                                      | no border; hover tint                                                                                                                                                                        |
| Destructive                               | `.btn.btn-danger` (solid) or `.btn.btn-danger-soft`                                                                                                                   | confirm before irreversible actions                                                                                                                                                          |
| Inline text action ("Open", "View all")   | `.btn.btn-link`                                                                                                                                                       | one fixed size everywhere: `--font-size-md`, no padding, 32px hit area. Never combine it with `.btn-sm`.                                                                                     |
| Icon-only button                          | `.btn.btn-ghost.btn-icon` + `aria-label`                                                                                                                              | square 42 (`.btn-sm` 32)                                                                                                                                                                     |
| Sizes / width                             | `.btn-sm` (32), default (42), `.btn-lg` (52); `.btn-block`                                                                                                            |                                                                                                                                                                                              |
| Icons in buttons                          | `IconGlyph` before the label: `plus` (create / add), `archive`, `trash` (remove / delete), `flag` (report), `chevronLeft` / `chevronRight` (paging, back, "view all") |                                                                                                                                                                                              |
| Loading button                            | set `:aria-busy="saving"` and `:disabled="saving"`                                                                                                                    | a spinner appears automatically                                                                                                                                                              |
| Form field                                | `.field` > `label` (`.field-label`) + `.input`/`.select`/`.textarea` + `.field-hint` + `.field-error`                                                                 | see §5                                                                                                                                                                                       |
| Field grid                                | `.form-grid` (1 col) / `.form-grid.two` (2 col)                                                                                                                       |                                                                                                                                                                                              |
| Form grouping                             | `<fieldset class="form-section"><legend class="form-section-title">`                                                                                                  |                                                                                                                                                                                              |
| One field of several controls             | `<fieldset class="field-set"><legend class="field-label">` + the controls (a `SegmentedControl`, choice cards)                                                        | the legend reads and spaces like a `.field` label; `.form-section` is for groups of fields with a section title                                                                              |
| Submit / cancel row                       | `.form-actions` (right-aligned; primary last in DOM)                                                                                                                  |                                                                                                                                                                                              |
| Checkbox                                  | `<label class="choice"><input type="checkbox">…</label>`                                                                                                              | brand accent colour automatically                                                                                                                                                            |
| Switch                                    | `<input type="checkbox" role="switch" class="switch">` inside a `<label>`                                                                                             |                                                                                                                                                                                              |
| Search / input with icon                  | `.input-with-icon` > `IconGlyph.input-icon` + `.input`                                                                                                                |                                                                                                                                                                                              |
| File upload                               | `<input type="file" class="input">`                                                                                                                                   | styled file button                                                                                                                                                                           |
| Tabs (in-page views)                      | `.tabs` > `button.tab-button` with `role="tab"` / `aria-selected`                                                                                                     | segmented style, equal-width and full-width (the auth page). Tabs that are routes use `<RouteTabs>`; filters use `<SegmentedControl>`                                                        |
| Segmented toggle                          | `<SegmentedControl v-model :options="[{ value, label, icon?, testId? }]" :label? :disabled?>`                                                                         | toggle buttons with `aria-pressed`; give `label` unless it sits in a `<fieldset>` whose legend names the group                                                                               |
| Status                                    | `<StatusPill :label tone="success/warning/danger/info/neutral" icon?>`                                                                                                | dot or icon + text                                                                                                                                                                           |
| Content status                            | `<StatusPill>` with the status label; lesson mastery `<MasteryChip>`                                                                                                  |                                                                                                                                                                                              |
| Tag / chip / count badge                  | `.tag` (neutral outlined), `.chip` (brand soft), `.route-tab-count` (count; `.is-attention` yellow)                                                                   | tags never wrap inside `.data-table`                                                                                                                                                         |
| Notice / banner                           | `<DataNotice>`: `error`, `success`, `info`; `#actions` slot                                                                                                           | the outcome of the last action on the page (`useActionFeedback`)                                                                                                                             |
| Light inline banner                       | `.alert` + `.alert-success/-warning/-danger`                                                                                                                          | short messages inside a form or card                                                                                                                                                         |
| Loading / empty / error area              | `<StatePanel tone="loading/empty/error" title message eyebrow? icon? spacious? :skeleton="false"?>` (`icon` for empty/error) + `#actions` slot                        | compact by default (`spacious` for a whole-page state); or `<ResourceSection :loading :error @retry>`; see §4                                                                                |
| Spinner / skeleton                        | `.spinner` (`.is-sm`), `.skeleton` + `.skeleton-line` (`.is-short`)                                                                                                   | StatePanel renders the skeleton lines for you                                                                                                                                                |
| Table                                     | `.data-table-wrap` > `table.data-table`; `.col-actions`, `.col-numeric`                                                                                               | see §6                                                                                                                                                                                       |
| Pagination                                | `<PaginationBar>`                                                                                                                                                     | see §4; never hand-write `.pagination` markup                                                                                                                                                |
| Breadcrumb                                | `<AppBreadcrumb :items="[{ label, to? }]">` in the header's `#before` slot                                                                                            | every item but the last links back up; the last is the page (`aria-current`)                                                                                                                 |
| Key-value                                 | `<dl class="kv-list">`                                                                                                                                                |                                                                                                                                                                                              |
| Avatar                                    | `<PortraitAvatar :size="32/40/56" :name tone?>` (`tone` primary / warning)                                                                                            | `tone`: `primary` (default) / `info` / `success` / `warning` / `accent`, each a fill + initials pair that meets AA (type `AvatarTone` in `avatarTone.ts`); always decorative (`aria-hidden`) |
| Verified teacher                          | `<span class="verified-mark"><IconGlyph name="verified" :size="14" />…</span>`                                                                                        | teal (the Classes colour), beside or under a teacher's name                                                                                                                                  |
| Progress                                  | `<ProgressBar :progress tone? label>` (`tone` primary / success; `class="is-sm/is-lg"`)                                                                               |                                                                                                                                                                                              |
| Progress ring                             | `<ProgressRing :progress title subtitle?>` (a 56px dial)                                                                                                              | `tone` primary (default) / warning; `size` `md` (88px, default) or `sm` (56px dial, tighter copy, for side panels)                                                                           |
| Trend line                                | `<TrendChart :values label>`                                                                                                                                          | drawn in `--color-primary-500`                                                                                                                                                               |
| Icons                                     | `<IconGlyph :name :size>` (Lucide; decorative, `aria-hidden`)                                                                                                         | the button or link carries the accessible name                                                                                                                                               |
| Route tabs                                | `<RouteTabs :tabs="[{ label, to, icon?, count?, attention? }]" :label>`                                                                                               | each tab is its own route (deep-linkable, works with Back); `count` is a quiet total, `attention` makes it a yellow badge for work waiting (hidden at zero)                                  |
| Dialog / drawer                           | `<AppOverlay :open title subtitle? variant="dialog\|drawer" size="sm\|md\|lg" :busy? @close>` + `#footer`                                                             | focus trapped and returned; Escape, × and the scrim close it (not while `busy`); `open` defaults to true for `v-if`                                                                          |
| Confirm step                              | `<ConfirmDialog :open title message confirm-label cancel-label? tone="primary\|danger\|leave" :busy? :error? @confirm @cancel>`                                       | focus starts on the safe choice; `tone="leave"` is the focus-mode "Leave …?" step (outline Leave, primary Keep going)                                                                        |
| Toast                                     | `<AppToast :message="toast">` driven by `useToast()`                                                                                                                  | a short confirmation of a same-page action, gone after 4 s                                                                                                                                   |
| Callout                                   | `<p class="callout">` (`.is-warning`, `.is-danger`, `.is-compact`) > `IconGlyph` + `<span>`                                                                           | what an action will do or why something is so, inside a dialog or a panel                                                                                                                    |
| Chinese character tile                    | `<span class="hanzi-tile">字</span>` (size with `--hanzi-tile-size`)                                                                                                  | the 田字格 frame every character sits in                                                                                                                                                     |

Overlays stack: a confirm step opened from a drawer closes on its own with Escape, and the page stays locked until
the last overlay closes (`useOverlayFocus`). There are no general layout-grid classes, and `.type-caption` (§2.4) is
the only typography helper. Build a page layout with its own page-prefixed grid using `--grid-gap` (§7).

---

## 4. Snippets

### Page header

```vue
<PageHeaderBlock :title="t('users.title')" :description="t('users.subtitle')">
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
  <div class="section-header">
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
<DataNotice :error="noticeError" :success="actionSuccess" />
<ResourceSection class="page-stack" :loading="pageLoading" :error="pageError" @retry="reloadAll">
  <section class="your-page-figures">…</section>
  <section class="your-page-grid">…</section>
</ResourceSection>
```

One area inside a card (a list, a queue, a side panel): chain the states in the template, with the page's own copy:

```vue
<StatePanel v-if="resource.loading.value" tone="loading" :title="t('x.loading')" :message="t('x.loadingHint')" />
<StatePanel
  v-else-if="resource.error.value && !items.length"
  tone="error"
  icon="alert"
  :title="t('x.loadErrorTitle')"
  :message="resource.error.value"
>
  <template #actions><button class="btn btn-primary" type="button" @click="resource.reload">{{ t('x.retry') }}</button></template>
</StatePanel>
<ul v-else-if="items.length">…</ul>
<StatePanel v-else tone="empty" icon="search" :title="t('x.emptyTitle')" :message="t('x.emptyHint')">
  <template #actions><button class="btn btn-secondary" type="button" @click="clearFilters">{{ t('x.clear') }}</button></template>
</StatePanel>
```

- The error panel always offers a retry. When an area shows its own error panel, leave that error out of the page's
  `DataNotice` so the message is not shown twice (the class catalogue pages do this).
- A page whose title comes from the data (class detail) keeps an `sr-only` `h1` while it is loading or failed.

### Pagination

```vue
<!-- 1-based page state -->
<PaginationBar v-if="totalPages > 1" v-model:current-page="page" :total-pages="totalPages" :label="t('x.pagination')" />
<!-- 0-based page state that reloads -->
<PaginationBar
  v-if="totalPages > 1"
  :current-page="page + 1"
  :total-pages="totalPages"
  @update:current-page="goToPage($event - 1)"
/>
```

| Prop / slot                                      | Meaning                                                                                                        |
| ------------------------------------------------ | -------------------------------------------------------------------------------------------------------------- |
| `currentPage` (1-based), `totalPages` (required) | The buttons emit `update:currentPage` with the page to show; first / last page disable previous / next         |
| `info`                                           | Summary text in `.pagination-info`; defaults to "Page {currentPage} of {totalPages}" in the interface language |
| `label`                                          | Accessible name; when set the bar renders as a `<nav>` landmark, otherwise a `<div>`                           |
| `previousLabel` / `nextLabel`                    | Button text; defaults to Previous / Next (`paginationMessages`)                                                |
| `previousDisabled` / `nextDisabled`              | Extra reasons to disable a button (busy); the page bounds already disable it                                   |
| `iconOnly`                                       | Chevron-only `.btn-icon` buttons; the labels become their `aria-label` (admin tables)                          |
| `status`                                         | Short text between the two buttons (`.pagination-status`)                                                      |
| `numbered`, `pageLabel(page)`                    | One button per page instead of previous / next; the current page is `btn-primary` with `aria-current`          |
| `testIdPrefix`                                   | Adds `<prefix>-pagination`, `-page-label`, `-previous-page`, `-next-page` test ids                             |
| `#start` slot                                    | Controls before the summary, e.g. a rows-per-page select                                                       |

### Feedback notices and toasts

```vue
<DataNotice :error="actionError" :success="actionSuccess" />
<DataNotice :info="t('x.reviewInfo')" />
<DataNotice :error="error">
  <template #actions><button class="btn btn-secondary btn-sm" type="button">{{ t('x.tryAgain') }}</button></template>
</DataNotice>
<AppToast :message="toast" />
```

The outcome of a page's last action comes from `useActionFeedback()` (`actionError`, `actionSuccess`, `clearFeedback`,
`showError`, `showSuccess`, `runAction`): one message at a time, cleared before the next action. A write runs through
`runAction(t('x.saveError'), async () => { ...; return t('x.saved') })`, which shows the text it returns or the
request error. A quick confirmation that needs no reading (Marked as read, CSV downloaded) is a toast instead:
`const { toast, showToast } = useToast()`.

### Dialogs

```vue
<ConfirmDialog
  :open="archiving"
  tone="danger"
  :title="t('x.archiveTitle', { title })"
  :message="t('x.archiveMessage')"
  :confirm-label="t('x.archive')"
  :busy="saving"
  :error="dialogError"
  @confirm="archive"
  @cancel="archiving = false"
/>

<AppOverlay :open="editing" variant="drawer" :title="t('x.editTitle')" :busy="saving" @close="editing = false">
  <form id="x-form" class="form-grid" @submit.prevent="save">…</form>
  <template #footer>
    <button class="btn btn-ghost" type="button" :disabled="saving" @click="editing = false">{{ t('x.cancel') }}</button>
    <button class="btn btn-primary" type="submit" form="x-form" :aria-busy="saving">{{ t('x.save') }}</button>
  </template>
</AppOverlay>
```

A dialog says exactly what will happen ("Students stop seeing this unit"), with a `.callout` when there is a
consequence to stress. Put the element that should take the focus first behind `data-autofocus`.

---

## 5. Forms

- Every control has a visible `<label for>`; placeholders are examples, never the label.
- Required: native `required` + `<span class="required-mark" aria-hidden="true">*</span>`; explain once at the top of
  long forms.
- Errors: `aria-invalid="true"` on the control (red border + red halo) and a `.field-error` element referenced by
  `aria-describedby`. Hints use `.field-hint`, also referenced. Both have no margin; `.field` spaces them.
- Character counters: wrap the hint and a `.field-hint.field-counter` in `.field-foot` (hint left, counter right).
- Heights: inputs and selects are 42px. Do not override heights per page.
- Long forms: group with `.form-section` fieldsets; put `.form-actions` at the end (secondary first, primary last).

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
- The actions column is always last, right-aligned, `white-space: nowrap`, using `.btn-sm`/`.btn-icon`.
- Status cells use `<StatusPill>`; dates and numbers use `.col-numeric` for alignment.
- Paging under a table is a `<PaginationBar>`.

## 7. Page templates and rhythm

All dashboard pages render inside `<main class="page-stack dashboard-main">`: padding `--space-8` top, `--page-gutter`
sides, max width `--page-max`, vertical gap `--page-gap`. The page root is **a single element** with the page class
and `page-stack` (flex column, `--page-gap`).

**Header rules**

- Dashboard pages (student, teacher, admin): `PageHeaderBlock` with a serif title, at most a one-sentence lede, then
  `#meta` and `#actions`. Page headers have no icon. An eyebrow (overline) is optional: use it for context the title
  does not carry (a date, a unit, a class name), never to repeat the section name.
- A module may replace the header with its own composed hero (Home's greeting, Learn's journey, Xiaomei's presence)
  as long as it keeps exactly one `h1` and the same type scale.
- Context that belongs above the title (`AppBreadcrumb`) goes in the `#before` slot, never in a separate card.
- The action-outcome `DataNotice` sits directly under the header.

**Dashboard page**

```
PageHeaderBlock
DataNotice (error / success), if any
ResourceSection class="page-stack" (loading / error with retry)
  Figure row: .your-figures — stat-figure + caption pairs separated by hairlines (not tinted KPI tiles)
  Main grid: your page-prefixed grid, e.g. grid-template-columns: minmax(0, 1.4fr) minmax(0, 0.8fr); gap: var(--grid-gap);
             one column at ≤ 1200px
    .panel blocks with a .section-header
```

**Student page with a rail** (Home, Learn, Practice, Progress): each area has its own shape (a path, a choice of
sets, a growth view), so there is no shared workspace class.

```
PageHeaderBlock (Home: the greeting header of the page itself)
.your-layout { display:grid; grid-template-columns: minmax(0, 1fr) minmax(280px, 340px); gap: var(--grid-gap) }
  main column: the page's own content
  aside: rail cards (daily goal, streak, plan, today's word …)
@media (max-width: 1200px) → one column; the rail moves below the main content
```

**Focus-mode task** (lesson player, Mistakes review, setup, level check, class activity, lesson quiz): routes with
`meta.focus` render without the sidebar and topbar, inside `<main class="focus-main">`.

```
FocusTopBar :label :close-label :progress?     close asks first: <ConfirmDialog tone="leave">
                                                 the task root sets --focus-width to its column (720px player, setup width)
the task, one step at a time (.panel or the task's own layout, max width --reading-max for text)
a bottom check bar for answers (Learn: CheckBar)
```

**List + detail page** (users, content review, verification)

```
PageHeaderBlock (primary action in #actions)
DataNotice
.your-layout { display:grid; grid-template-columns: minmax(0, 1fr) minmax(320px, 0.42fr); gap: var(--grid-gap) }
  list panel: <SegmentedControl> (filters) → toolbar (.input-with-icon search + .select filters) → list / .data-table-wrap
              with its loading / error / empty StatePanel → <PaginationBar>
  detail panel: .panel with .section-header, .kv-list, .card-footer actions
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

## 8. Desktop layout rules

- Desktop browsers only (Chrome, Edge, Firefox, Safari on laptops and desktops), from a 1024px window to a large
  monitor. There is one implementation of every page and every piece of navigation: no phone or tablet variants, no
  touch-only gestures, no screen-size feature switches. Mouse and keyboard both work everywhere.
- Fluid, not fixed: use `max-width`, `minmax(0, 1fr)`, `repeat(auto-fit, minmax(…, 1fr))` and `flex-wrap` so pages
  adapt between a small laptop and a wide monitor without media queries. Never position page content absolutely
  or size it for one screen.
- The only width breakpoint is `max-width: 1200px` (§2.7): rails move under the main column on small laptops.
- Every grid/flex child that holds text gets `min-width: 0`; long words use `overflow-wrap: anywhere` (Malay and
  Chinese strings are long or unbreakable). No horizontal page scroll at 1024px and wider.

## 9. Do and don't

These add to the rules in §1.

**Do**

- Use the component props (`ProgressBar tone`, `PortraitAvatar tone`, …) instead of page overrides.
- Use the translucent tokens instead of new `rgba()`.
- Check `en`, `ms` and `zh-CN`; Malay strings are ~30 % longer.

**Don't**

- Don't add `!important` (only the reduced-motion block has it).
- Don't set `overflow: hidden` on a card that contains a table or a dropdown.
- Don't use text below 12px outside badges, overline labels and the brand tagline (`--font-size-2xs`, 11px).
- Don't add hover lift (`translateY`) to non-interactive cards.
- Don't add new z-index numbers or animation timings.

## 10. App shell reference

- `DashboardLayout`: skip link → `.dashboard-shell` (which also draws the sidebar hairline for the full page height) →
  `DashboardSidebar` + `.content-shell` → `DashboardTopbar` + `<main id="main-content" tabindex="-1">`. A route with `meta.focus` skips the shell: `<main id="main-content" class="focus-main">` holds the task and its
  `FocusTopBar`.
- Navigation comes from `config/navigation.ts`: one flat list per role (`roleNavigation`) plus Help & support
  (`roleSupportNav`). An item can carry a count badge (`countKey`, read from `GET /users/me/nav-counts`) and can need
  an approved teacher (`needsApproval`: shown with a lock and routed to the locked page until approval). The route
  guard (`router/guard.ts`) and the backend apply the same role rules.
- Sidebar: sticky full-height column on the page paper at every window width (seal + wordmark, role overline,
  navigation with `aria-current="page"`: the current item is a white sheet with a purple marker and purple label; count
  badges in yellow with ink text; the word of the day in a 田字格 tile; Help & support; a status footnote: level and XP
  for students, approval state for teachers, platform health for admins).
- Topbar: a thin paper strip with a hairline: the current section's name (small and quiet; the page title carries
  the page), language switcher, notifications with a red unread badge
  (`data-testid="topbar-notifications"`) and the account button (name and role; it opens Account, where Sign out
  is). Sticky at the top of the content column; the page scrolls under it.
- `PublicLayout`: skip link, `PublicTopNav` (sticky flat bar on the paper with a hairline; links underline in
  purple; language switcher and sign-in buttons on the right), `<main id="main-content" tabindex="-1">`.
- System pages (404 / 403) are standalone: `pages/system/SystemStatusPage.vue` (route prop `kind`) renders
  `.system-card`: the page's character in a 田字格 tile (迷 not found, 锁 no access), the code and copy, the way back
  (`.system-actions`) and quick links (`.system-link`) from its per-page table.
- A page must not add its own sticky headers above `--z-sticky` or fixed elements that cover the topbar.
