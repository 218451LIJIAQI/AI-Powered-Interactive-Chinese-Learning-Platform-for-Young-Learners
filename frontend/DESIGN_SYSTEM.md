# MandarinSpark design system

The reference for every page and component in the frontend. It documents only what exists in the code.

The design foundation is the low-fidelity Figma prototype (file `7zEAygN3F4redhguyJLq6A`, frame "Library · UI rules"). The
prototype fixes structure, hierarchy, flows, sizes and spacing. This system adds what a greyscale wireframe leaves open:
the brand colours, semantic status colours, interaction states and accessibility.

Source of truth:

- `src/styles/tokens.css`: every token below, imported first by `main.css`.
- `src/styles/main.css`, foundation block (top of the file, up to the banner `Page sections (page-prefixed classes only)`):
  base elements, the app shell, focus mode and all shared primitives.
- Module stylesheets, imported by the pages and components of one area only: `learn.css`, `learn-practice.css`,
  `learn-progress.css`, `learn-admin.css`, `plan.css`, `classes.css` (student classes, catalogue, shared class pieces),
  `classes-teacher.css` (teacher console), `ai.css`, `ops-public.css`, `ops-account.css`, `ops-admin.css`. A component used
  by more than one area belongs in `components/common` with its CSS in `main.css`, never in a module file.
- Shared components: `components/sections/PageHeaderBlock`; `components/common`: `AppOverlay`, `ConfirmDialog`, `AppToast`,
  `AppBreadcrumb`, `RouteTabs`, `FilterChips`, `SegmentedControl`, `StatePanel`, `DataNotice`, `StatusPill`,
  `PaginationBar`, `ProgressBar`, `PortraitAvatar`, `TrendChart`, `LanguageSwitcher`, `IconGlyph`;
  `components/layout`: `DashboardSidebar`, `DashboardTopbar`, `PublicTopNav`, `AppBrand`, `FocusTopBar`, `FocusFooter`.
- Shared composables: `useResource` / `useServerPage`, `useActionFeedback`, `useToast`, `useOverlayFocus`,
  `useLocalMessages`. Default texts of the shared components: `plugins/commonMessages.ts`.

Change a primitive or a token here, in the foundation block or `tokens.css`, never from a page section.

---

## 0. Direction

Modern, clean, friendly and professional, for young adults learning Mandarin in Malaysia. Not a game arcade, not an admin
template, not childish.

- **Page and sheets.** Sidebar and header are white with a hairline. Content sits on a cool light page colour
  (`--surface-page`) in white cards (`.panel`) with a hairline edge and no shadow. Focus flows are a white page.
- **Spark purple** (`--color-primary-*`) is the brand and the interactive colour: primary buttons, the active sidebar
  item, selected chips and options, focus, progress. Where the wireframe shows an ink-filled element, the product shows
  purple.
- **Sunny yellow** (`--color-accent-*`) is the highlight: the one hero or next-step action of a page (`.btn-accent`, at
  most one per page) and counts of waiting work. It always carries ink text.
- **Ink** (`--color-ink-*`) is the text. Hierarchy comes from size, weight and space, not from colour fills.
- **The wordmark** keeps its two colours beside the 火 seal.
- **Type.** Nunito for the whole interface. Chinese: Noto Serif SC Bold at 20px and above (`--font-hanzi`, `.hanzi`), Noto
  Sans SC below 20px (`--font-cjk`, `.cjk-text`).
- **Hanzi are learning content, never icons.** They always come with pinyin and meaning. Only the 火 brand seal is an
  exception. Use line icons (`IconGlyph`, Lucide) for meaning, and only where they add meaning.
- **No effects.** No gradients on surfaces, no glass, no glow, no decorative illustration. A page must read well in
  greyscale.

### 0.1 Module personality: shared components, different compositions

| Module          | Composition                                                                                                                                                           |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Home            | One next step in a large card; today's plan on a sunken rail; the goal strip above.                                                                                   |
| Learn           | A path of pinyin, characters, grammar and words: current position, completed, locked, next lesson. Only the path scrolls; the unit banner and side column stay.       |
| Practice        | Free choice: activity tabs (Flashcards · Quizzes · Listening · Tracing · Drag & Drop · Matching), a daily-goal strip, one large stage.                                |
| AI Helper       | Conversation first: a face-to-face stage that keeps one height in every state, so the reply dock never moves; transcript rail.                                        |
| Classes         | Teacher-led classes, one Theme each: 2 units, 4 to 6 lessons, live sessions. Each lesson = 1 video + key phrases + Flashcards, Tracing, Matching + a 5-question quiz. |
| Progress        | Reflection: plan status and skills with their weekly change come first; XP level, streak and badges stay secondary.                                                   |
| Teacher / Admin | Efficiency and control: to-do lists, tables, filters, list-detail, confirmations.                                                                                     |
| Public and auth | A small product showcase; one centred auth card.                                                                                                                      |

### 0.2 Content rules

Every element earns its place.

- No subtitle that repeats a title, no tip that a clear label already covers. Banners say what happened in one line.
- Never add a card, sentence or icon only to fill space. Fix sparse pages with layout: width, grouping, alignment.
- Show a number only if it changes a decision. Analytics live on Analytics, not on every dashboard.
- Pinyin, meanings, grammar, exercise instructions and feedback always stay.
- One "AI can make mistakes" note per AI screen; every AI answer can be reported.
- Validation, errors, consequences of destructive actions and safety notes are never removed; shorten them instead.

---

## 1. Rules in one minute

1. **Only tokens.** No raw hex, `rgba()`, px font sizes, radii or shadows when a token exists. Spacing uses `--space-*`
   on the 4-pt scale. Dynamic inline styles (a progress width) are the only inline styles allowed.
2. **One h1 per page, always.** Section roots have no visible H1 (`<PageHeaderBlock :title hide-title />` keeps it for
   assistive technology); their tabs or content start at the top of the column. Nested pages: breadcrumb, H1, meta.
3. **Shared primitives first.** Never build a second version of a primitive inside a module.
4. **One width breakpoint: `max-width: 1200px`** (small laptops: side rails move under the main column). Do not add others.
5. **Never colour-only.** Status carries text; colour reinforces it.
6. **Motion** uses `--duration-*` and `--ease-*`. Everything collapses under `prefers-reduced-motion`.
7. Keep page selectors prefixed. Do not restyle `.btn`, `.panel`, `.input` from a page section. Never `:deep()` into a
   shared component; add a prop or modifier instead. No `!important` outside the reduced-motion block.
8. **No dead CSS.** A class that no template or script uses is deleted, primitives included.

---

## 2. Tokens

### 2.1 Colour

| Scale                              | 50      | 100     | 200     | 300     | 400     | 500         | 600     | 700     | 800     | 900     |
| ---------------------------------- | ------- | ------- | ------- | ------- | ------- | ----------- | ------- | ------- | ------- | ------- |
| `--color-primary-*` (Spark purple) | #f6f3ff | #ede8ff | #dcd2ff | #c2b2ff | #9d85ff | **#6f4cff** | #5a35ec | #4a28c9 | #3a1f9c | —       |
| `--color-accent-*` (sunny yellow)  | #fffaeb | #fff2c7 | #ffe696 | #ffdb66 | #ffd247 | **#ffcc2f** | #f0b400 | #c28d00 | #8f6700 | #5f4500 |
| `--color-ink-*` (`-25` #fafbff)    | #f5f6fc | #eceff8 | #e0e4f1 | #cbd1e3 | #a2aac4 | #626b88     | #5c6687 | #434d6d | —       | #152042 |

Semantic families (fill, `-soft`, `-border`, `-text`; danger and warning also `-strong`): `--color-success`,
`--color-warning`, `--color-danger`, `--color-info`. Fill is for icons, dots and bars, never body text. Soft is for tinted
backgrounds. Text is AA on soft and white.

| Role                                  | Token                                                                                                                       | Value                           |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------- |
| Page / card                           | `--surface-page` / `--surface-card`                                                                                         | ink-50 / white                  |
| Sunken (rails, callouts, stage floor) | `--surface-sunken`                                                                                                          | ink-100                         |
| Subtle, hover tint, selected          | `--surface-subtle`, `--surface-hover`, `--surface-selected`                                                                 | ink-25, primary-50, primary-100 |
| Toast, tooltip                        | `--surface-inverse`                                                                                                         | ink-900                         |
| Scrim                                 | `--surface-scrim`                                                                                                           | ink at 38%                      |
| Borders                               | `--border-subtle` (cards), `--border-default` (controls, shell), `--border-strong` (inputs), `--border-interactive` (hover) |                                 |
| Text                                  | `--text-primary`, `--text-secondary`, `--text-disabled`, `--text-on-primary`, `--text-on-accent`, `--text-link`             |                                 |

Contrast (WCAG 2.2 AA, computed): text-primary on card 15.94, on page 14.78; text-secondary on card 5.67, on page 5.25, on
ink-100 4.93; white on primary-500 5.06; ink on accent-500 10.58; the semantic `-text` on its `-soft` is at least 4.92.
Yellow is never a text colour; `--text-disabled` and a disabled primary are exempt.

### 2.2 Typography

| Role       | Token                    | Size / line height | Weight | Used by                                                   |
| ---------- | ------------------------ | ------------------ | ------ | --------------------------------------------------------- |
| Display    | `--type-display-size`    | 52 / 60            | 800    | Public home hero only                                     |
| H1 public  | `--type-h1-public-size`  | 40 / 48            | 800    | Classes, class detail, legal, help, safety                |
| H1 focus   | `--type-h1-focus-size`   | 32 / 40            | 800    | Setup and result headlines, Home hero (`.focus-headline`) |
| H1 app     | `--type-h1-size`         | 28 / 36            | 800    | Nested app pages (`.page-title h1`)                       |
| Question   | `--type-question-size`   | 24 / 32            | 800    | Exercise and question titles (`.focus-title`)             |
| Section    | `--type-h2-size`         | 18 / 24            | 800    | Group headings (`.section-title`), overlay titles         |
| Card title | `--type-card-title-size` | 16 / 22            | 800    | Every card and panel heading (`.card-title`)              |
| Body       | `--type-body-size`       | 14 / 20            | 400    | `body`                                                    |
| Label      | `--type-label-size`      | 13 / 20            | 700    | Field labels, S buttons, chips                            |
| Caption    | `--type-caption-size`    | 12 / 18            | 400    | Meta lines, hints                                         |
| Overline   | `--type-overline-size`   | 11 / 16            | 800    | Eyebrows, caps (`.page-eyebrow`), table headers           |

Only these sizes (15 only on L buttons; 64 and 88 only in hanzi tiles). Numbers: `.stat-figure` (tabular).

### 2.3 Spacing and layout

`--space-*`: 2, 4, 6, 8, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 64, 80, 96. Layouts use 4 8 12 16 24 32 48; the odd steps are
for inner tuning of a primitive.

- 4 to 8: icon to label, badge padding, chip groups (8/8 wrap).
- 12 to 16: fields in a group, rows inside a card, button groups (12, or 8 for S).
- 24: card padding, card to card, section to section. Children stay inside the padding box.
- 28 / 32 / 48: page padding top / sides / bottom (`--page-top`, `--page-gutter`, `--page-bottom`).

Other layout tokens: `--page-gap` 24, `--grid-gap` 24, `--section-gap` 16, `--card-padding` 24, `--sidebar-width` 248,
`--topbar-height` 64, `--topbar-control-size` 36, `--page-max` 1320 (content column cap on large monitors),
`--public-max` 1376, `--list-column` 360, `--page-min-width` 1024, `--focus-bar-width` 960, `--focus-column` 600,
`--focus-bar-top` 36, `--focus-footer-height`, `--reading-max` 68ch.

A row with an icon or tile and up to two lines is vertically centred. Multi-line label and value rows stay top-aligned.

### 2.4 Radius, borders, shadows, z-index, motion, sizes

- Radius: `--radius-sm` 8 (S button), `--radius-md` 10 (button, input, inner row, banner), `--radius-btn-lg` 12 (L button),
  `--radius-lg` 14 (card, option row), `--radius-xl` 20 (dialog), `--radius-2xl` 24 (stage, hero), `--radius-pill`,
  `--radius-round`.
- Cards have a hairline and no shadow. `--shadow-overlay` is for dialogs and drawers; `--shadow-md` for floating menus.
- Focus: the global `:focus-visible` rule (2px purple outline, 2px offset). Never remove an outline without replacing it.
- Z-index: `--z-raised`, `--z-sticky`, `--z-sidebar`, `--z-drawer`, `--z-tooltip`, `--z-skip-link`.
- Controls: `--control-height-sm` 32, `-md` 40, `-lg` 48; `--chip-height` 32, `--chip-height-sm` 26, `--tab-height` 42,
  `--badge-height` 24, `--count-size` 18.

### 2.5 Breakpoint

`@media (max-width: 1200px)`: rails move under the main column. Below `--page-min-width` (1024) the page scrolls sideways.

---

## 3. Which class or component for each element

### Buttons

Three sizes, and one primary per area, rightmost in every action group. Only hero and next-step CTAs stand alone on the left.

| Size | Class     | Metrics                               | Use                                                                |
| ---- | --------- | ------------------------------------- | ------------------------------------------------------------------ |
| L    | `.btn-lg` | 48, r12, px24, 15 Bold, min width 120 | Public hero, auth submit, setup wizard and lesson footers, 404/403 |
| M    | default   | 40, r10, px16, 14 Bold                | Page, form, card, dialog and drawer actions                        |
| S    | `.btn-sm` | 32, r8, px12, 13 Bold                 | Actions inside list rows and side rails                            |

| Variant           | Class                             | Use                                                           |
| ----------------- | --------------------------------- | ------------------------------------------------------------- |
| Primary           | `.btn-primary`                    | The one main action of an area. Disabled is grey.             |
| Hero              | `.btn-accent`                     | The one hero or next-step CTA of a page, at most one          |
| Secondary         | `.btn-secondary`                  | Everything else, including every repeated card and row action |
| Ghost             | `.btn-ghost`                      | Low-frequency page actions (Archive, Delete draft), px 8      |
| Sunken            | `.btn-sunken`                     | Quiet action on a white stage                                 |
| Destructive       | `.btn-danger`, `.btn-danger-soft` | Confirmed destructive actions                                 |
| Text link in copy | `.btn-link`                       | Only inside a sentence; never a row action                    |
| Icon only         | `.btn-icon`                       | 40 or 32 square with `aria-label`                             |

Group: `.btn-group` (12 apart), `.btn-group.is-sm` (8). Same height in a row, always. Icons: 16 at S and M, 18 at L. Edit = pen
before the label. Open = chevron after, only when it goes to another page. Save has no icon. Loading: `:aria-busy` and
`:disabled`; a spinner appears.

### Action patterns

Same situation, same placement, on every screen.

| Situation      | Pattern                                                                                                                                                             |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dialog         | `AppOverlay`, 520 (720 `size="lg"`), top at 140. Footer right: [Cancel][Primary]; a destructive extra far left (`.is-far-left`). × top right. Outside click closes. |
| Confirm        | `ConfirmDialog` (`tone` primary / danger / leave). Only destructive or irreversible actions; leave only when progress would be lost.                                |
| Drawer         | `AppOverlay variant="drawer"`, 480, full height; footer right, pinned.                                                                                              |
| Wizard, lesson | `FocusFooter`: [Back / Skip] in `start`, [Continue / Check] in `end`, L buttons.                                                                                    |
| Form footer    | Right-aligned on every card and form: `.form-actions` or `.card-footer`, [Secondary][Primary].                                                                      |
| Stage footer   | Practice, inside the stage card: [Reset / Skip] left, [Check / Done] right.                                                                                         |
| Activity done  | `FocusFooter tone="done"`: the result left, [Practise again][Next activity] right.                                                                                  |
| List row       | Row actions right-aligned; the main one rightmost so it lines up across rows ([Mark as read][Open ›]).                                                              |
| Toolbar        | Page-level primary at the right end of the tab row (default slot of `RouteTabs`) or the H1 row (`#actions` of `PageHeaderBlock`).                                   |

### Badges, tags, chips, counts

- `<StatusPill :label :tone>`: 24 high, fully rounded, 12 Bold, leading dot. One status per row, in the right-hand column.
  `strong` = needs YOUR action now (Unread, Needs support; Changes requested for teachers; In review and open reports for
  admins). `success` = positive (Published, Completed, Approved, Active, Resolved, Verified). `info` / `neutral` = normal (In
  progress, On track, In review for the waiting teacher). `outline` = inactive or not yet (Draft, Not started, Not submitted,
  Cancelled, Deactivated, Archived). `warning` / `danger` = something is actually wrong.
- `.tag`: names a category, not a state (theme, role, type); no dot. `.is-outline`, `.is-strong`, `.is-sm` (20).
- `<FilterChips v-model :options="[{ value, label, count?, icon?, testId? }]" :label? size?>`: filters of one list, "Label · N".
  M 32, S 26 for suggestions and small stage controls. Selected is filled.
- `<SegmentedControl v-model :options :label?>`: only 2 to 3 view modes (Sign in / Create account, Recording / Handwriting,
  Last 7 / 30 days).
- Count: `.count-badge` (and `route-tab-count`), dark 18px after a label, on tabs and card titles (`.title-with-count`), never
  "(15)", zero shows none. `.is-attention` (yellow) marks waiting work.

### Tabs and navigation

- `<RouteTabs :tabs="[{ label, to, icon?, count?, attention?, testId? }]" :label>`: underline tabs for sibling views, each its
  own route. 14 Bold, icon 16, 22 apart, 42 high, 2px underline. Default slot: right-end meta or actions.
- Sidebar: section roots only (`config/navigation.ts`), the active item is filled; Help & support and the status card at the
  bottom. An approved teacher has no status card.
- Header: the active section's name, language switch, bell, account (name only).
- `<AppBreadcrumb :items>` on every nested page (`#before` slot of `PageHeaderBlock`); each crumb links back. Never "← Back to …".

### Page structure

- `<PageHeaderBlock :title description? eyebrow? hide-title?>`, slots `before`, `meta`, `actions`.
- `.panel` with `.card-title`, `.card-subtitle`, `.card-footer`; `.section-header`, `.section-title`;
  `.page-toolbar` for a section root's actions when there is no tab row.
- Lists in cards: `.row-list` with `.row-list-copy`, `.row-list-title`, `.row-list-meta`, `.row-list-actions`. Divider rows with
  no side padding, so their text lines up with the card title.
- List and detail: `.list-detail` (360 + 24 + rest), with `.select-card` items (bordered, r14, selected = 2px purple ring; text
  never shifts).
- Answer options: `.option-row` + `.option-keycap` (60 high, r14; `.is-correct`, `.is-wrong`, selected via `aria-pressed`,
  `aria-checked` or `.is-selected`).

### Forms

- `.field` > `label` (`.field-label`) + `.input` / `.select` / `.textarea` + `.field-hint` + `.field-error`. Inputs are 40 high,
  r10. Fields fill the card width. `.form-grid`, `.form-grid.two`.
- Every control has a visible `<label for>`; placeholders are examples, never the label.
- Required: native `required` + `<span class="required-mark" aria-hidden="true">*</span>` (2px after the label).
- Errors: `aria-invalid="true"` on the control and a `.field-error` referenced by `aria-describedby`. Hints too.
- `.field-set` is one field of several controls; `.form-actions` ends a form.
- `.choice` (checkbox), `.switch` (`role="switch"`), `.input-with-icon` (search).

### Feedback and states

- `<AppToast :message>` + `useToast()`: same-page saves (Saved, Draft saved, Marked as read), no dialog.
- `<DataNotice :error :success :info>` with `#actions`; `.alert` (`-success`, `-warning`, `-danger`) inline; `.callout`
  (`.is-warning`, `.is-danger`, `.is-success`, `.is-compact`) for what an action will do. One line where possible.
- Submissions get their own result state (Sent, Submitted, Approved).
- `<StatePanel tone="loading|empty|error" :title :message :icon? :spacious?>` + `#actions`: empty says why and offers one next
  step (dashed tile, title 16, body 14, no inner box); an error always offers a retry. `ResourceSection` wraps whole pages.
- Locked: dashed outline, lock icon, what unlocks it.
- Every list has loading, empty, error, completed and locked looks.

### Learning primitives

- `.hanzi-tile` (`--hanzi-tile-size`): the 田字格 frame a character is shown in, with pinyin and meaning next to it.
- `.seal`: the brand 火 and earned stamps.
- `SessionDateTile`: 56 x 57, white with a line, weekday + day; the row meta repeats "Mon, May 18 · 4:00 PM".
- `ClassCatalogueCard`: teacher, theme tag, title, meta in the same order on public and student pages.
- `<ProgressBar :progress :label tone?>` (`.is-sm`, `.is-lg`), `<TrendChart>`.
- `<PortraitAvatar :size :name>`: decorative initials.

---

## 4. Page templates

**Shell.** `DashboardLayout`: skip link, `.dashboard-shell` (`DashboardSidebar` + `.content-shell` > `DashboardTopbar` +
`<main class="page-stack dashboard-main">`). A route with `meta.focus` skips the shell.

**Section root** (Learn, Practice, AI Helper, Classes, Progress, Dashboard, Users, Content, AI, Analytics, Audit log)

```
PageHeaderBlock hide-title
RouteTabs (actions or notes in the default slot), if the section has sibling views
content starts here
```

**Nested page** (class, lesson, plan, ticket, editor)

```
PageHeaderBlock: #before AppBreadcrumb, h1, meta, #actions on the H1 row
tabs, if any
content
```

**List and detail** (students, teacher applications, content review, AI reports, support)

```
RouteTabs
.list-detail
  list card: title + count, FilterChips, .select-card items
  detail card: head, content, footer actions right
```

**Table** (users, teacher classes, practice library, audit log)

```
RouteTabs
.panel: filters row (fields fill the width), .data-table-wrap > table.data-table, PaginationBar
```

**Focus task** (lesson player, Mistakes review, setup, level check, class activity, lesson quiz)

```
main.focus-main
  FocusTopBar :label :close-label :progress        close asks first: ConfirmDialog tone="leave" when progress would be lost
                                                   the task root sets --focus-label-width, so the bar keeps one length
  .focus-stage (600 column, stable --focus-stage-top per flow): .focus-title or .focus-headline, the task
  FocusFooter :tone  #start [Back / Skip]  default note  #end [Continue / Check]
```

**Public page.** `PublicLayout`: `PublicTopNav`, `<main class="page-stack public-shell-content">`, `PublicFooter`. Width 1376, H1 40.

**Auth.** `AuthShell`: one centred rounded card, sunken brand panel with 你好 tiles, white form panel, L submit.

---

## 5. Tables

`.data-table-wrap` > `table.data-table`. Header 11 ExtraBold caps; rows split by hairlines; `.col-actions` (last,
right-aligned). Status cells use `StatusPill`. Row actions are `.btn-secondary.btn-sm`. Inside a `.panel` the wrap has no border of
its own. Paging is `PaginationBar`.

## 6. Desktop rules

- Desktop browsers only, from a 1024px window to a large monitor. One implementation of every page, no phone or tablet variants.
- Fluid, not fixed: `max-width`, `minmax(0, 1fr)`, `flex-wrap`. Never position page content absolutely.
- Every grid or flex child that holds text gets `min-width: 0`; long words use `overflow-wrap: anywhere`. No horizontal page scroll at
  1024px and wider. Malay strings are about 30% longer: check `en`, `ms` and `zh-CN`.

## 7. Accessibility

Semantic landmarks, one h1, real buttons and links, visible focus, labelled controls, errors tied to fields, overlays that trap and
return focus and close with Escape, status text never colour-only, tests of keyboard paths for tracing, drag and drop and matching.
