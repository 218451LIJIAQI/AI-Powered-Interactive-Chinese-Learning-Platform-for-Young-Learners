# UI/UX quality pass: report (19 October 2026)

This pass covered the whole frontend of `FYP_Source_Code_Final`. The design system it produced is documented in `frontend/DESIGN_SYSTEM.md`.

## Method

1. **Inventory.**
   - Router, pages, layouts and components were inventoried, covering 46 screens (the full list is below).
   - Every screen was screenshotted at desktop 1440, tablet 834 and mobile 390 in preview (demo-data) mode, giving 138 baseline images.
2. **Foundation.** Built first, so every page could use it:
   - **Design tokens** in `src/styles/tokens.css`, with the brand kept (purple / yellow / panda / Fredoka + Nunito):
     - colour scales, with every text pair verified ≥ 4.5:1;
     - type scale and roles;
     - spacing (4px);
     - radius, shadows, motion, z-index and control heights.
   - **Normalisation** of hard-coded colours, sizes, radii and shadows across every stylesheet and `<style>` block.
   - **Shared primitives:** buttons, form fields, cards, page header, tabs, badges, tables, notices, and empty/loading/error states.
   - **New app shell:**
     - full sidebar above 1200px, and an icon rail from 901 to 1200px;
     - a drawer with focus trap and Escape at 900px and below;
     - a user menu in the topbar;
     - a public mobile menu;
     - a skip link on both layouts.
3. **Page-by-page passes.** Six partitions ran in parallel, merged with a three-way merge plus a semantic CSS merge, and every added rule was verified:
   - public/auth/system;
   - student core;
   - student other plus shared notifications and account pages;
   - teacher authoring;
   - teacher people;
   - admin.

   Each page was checked for:
   - layout and hierarchy;
   - buttons and forms;
   - tables and lists;
   - loading, empty, error and success states;
   - responsiveness and accessibility.

4. **Second-round cross-page review.**
   - Unified headers, links, buttons, icons, notices and states across all pages.
   - Fixed shared-component issues: the sidebar column height, hero contrast, the phone form-action row, field spacing and the TrendChart points.
   - Removed dead CSS.
   - Fixed contrast failures: 57 before, 0 after.
5. **Cleanup.**
   - Added loading and error states where missing, including catalog and course detail.
   - Created one shared `PaginationBar` to replace ten copies.
   - TracingPad colours now come from tokens.
   - Unused primitives were removed.
6. **Independent acceptance audit**, against the full checklist. An agent that wrote none of the code checked:
   - 46 routes at 6 widths (1440, 1280, 1024, 834, 390, 360);
   - Malay and Chinese at 4 widths;
   - forced loading and error states on every route;
   - keyboard focus order, focus rings, accessible names and tap targets;
   - form validation;
   - dead CSS and code hygiene.

   It fixed what it found:
   - the page-level error shown twice on 22 pages;
   - tap targets under 44px on phones;
   - overflow at 360px in Malay;
   - avatar, language and user-menu accessible names;
   - missing field errors on reset password and the content builders.

## Screens covered (all checked at every viewport)

| Area               | Screens                                                                                                                                                                                   |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Public and system  | Landing, Sign in / Register, Forgot password, Reset password, Terms, Privacy, Responsible AI, Course catalog (public and signed-in), Course detail (course and lesson), 404, Unauthorized |
| Student            | Home, Learn, Practice (all six players), Quiz, Classes, Achievements, Rewards, AI Helper, Profile, My Progress, Speaking & Writing, Notifications, Account & Security                     |
| Teacher            | Dashboard, Content (all builders and tabs), Practice Activities, Classes, Students, Feedback Center, Profile & Verification, Notifications, Account & Security                            |
| Admin              | Dashboard, Users, Teacher Verification, Content, Content Review, Activity Library, Reports & Moderation, Analytics, Settings, Notifications, Account & Security                           |
| Shell and overlays | Sidebar, topbar, user menu, notification bell, language switcher, mobile drawer, public nav and mobile menu, confirms, tabs, dropdowns                                                    |

## Results in numbers (compared with the start of this pass)

- **Distinct hex colours:** 591 → 103 (the rest are illustration art such as the panda and scenes).
- **Distinct font-size values:** 144 → 27 (type scale tokens).
- **Global stylesheets:** 16,713 → 6,309 lines; page styles now live with their pages.
- **Contrast failures (automated audit, all routes):** 0. Horizontal overflow: 0. Console errors: 0.

## Verification (final project state)

- **Static checks:** `npm run lint`, `npm run format:check`, `npm run typecheck` and `npm run build` all pass.
- **Unit tests:** 107 files / 472 tests pass. The new tests cover PaginationBar and the CSS token helper.
- **End-to-end:** Playwright passes 39/39 with system Chrome.
- **Screenshots:** 138 final shots (46 routes × 3 viewports) show no overflow or errors.
- **Functionality:** APIs, routing, guards, RBAC, form submission and validation, state management, i18n keys and test ids are unchanged. The backend was not modified.

## Small behaviour additions (safety and UX, no new features)

- Confirmations before cancelling a live class, suspending a user and rejecting a teacher.
- The moderation page selects a report from the queue; its duplicate drop-down was removed.
- Validation messages appear next to their fields.

## Known limitations (left as they are)

- **Destructive confirmations** use the browser's native dialog. A custom dialog would be new behaviour.
- **AI Helper error state:** when everything fails it shows two error panels, because availability and reports load and retry separately.
- **Feedback composer:** server-side errors appear in the page notice, not under the field.
- **No dark mode.** The product never had a theme switch.
- **Visual checks used preview data.** Live-data pages use the same components and states.
