# Content Management Workflow

## Phase 6 Purpose
Phase 6 implements the learning content management foundation for the platform. It focuses on teacher-authored content, admin review, and public discovery. It does not implement AI features, payment workflows, interactive gameplay, live class scheduling, or recorded lesson playback.

## Content Structure
The Phase 6 learning structure is:

`Level -> Module -> Lesson -> Activity -> Quiz`

Implemented in Phase 6:
- learning levels
- learning categories
- modules
- lessons
- lesson activity placeholders
- learning packages
- package items

Deferred to later phases:
- quiz gameplay and quiz question workflows
- AI lesson help or generation logic
- payment access enforcement
- live class scheduling
- recorded video playback

## Content Status Values
Phase 6 uses the existing `ContentStatus` workflow:
- `DRAFT`
- `SUBMITTED`
- `APPROVED`
- `REJECTED`
- `PUBLISHED`
- `ARCHIVED`

Rules:
- Teachers create `DRAFT`
- Teachers submit `DRAFT` or `REJECTED`
- Admin approves or rejects `SUBMITTED`
- Admin publishes `APPROVED`
- Admin archives content where needed
- Public APIs only return `PUBLISHED`

## Teacher Content Creation Flow
1. Teacher logs in with `TEACHER` role.
2. Teacher must already have `APPROVED` teacher status.
3. Teacher creates a module, lesson, or package as `DRAFT`.
4. Teacher updates only their own `DRAFT` or `REJECTED` content.
5. Teacher adds lesson activity placeholders or package items where applicable.
6. Teacher submits content for admin review.

Important restrictions:
- `PENDING` and `REJECTED` teachers cannot create or submit content.
- Teachers cannot manage another teacher's content.
- Teachers cannot directly set content to `APPROVED` or `PUBLISHED`.

## Admin Approval, Rejection, Publish, and Archive Flow
Admin workflows:
- review `SUBMITTED` content
- approve submitted content
- reject submitted content with required reason
- publish approved content
- archive content

Admin review surfaces:
- submission list
- submission detail page
- moderation list for published, rejected, archived, and approved content

## Public Discovery
Public content routes:
- `GET /api/v1/public/levels`
- `GET /api/v1/public/categories`
- `GET /api/v1/public/courses`
- `GET /api/v1/public/courses/{id}`

Public discovery behavior:
- only `PUBLISHED` lessons and packages appear
- draft, submitted, approved-but-not-published, rejected, and archived content stay hidden
- paid content appears locked
- no checkout or entitlement logic is implemented
- public detail pages show safe metadata only

## Free vs Paid Metadata
Phase 6 stores `FREE` and `PAID` as metadata only.

What is implemented:
- price can be stored on lessons and packages
- paid content appears locked in public discovery
- public detail pages show locked status

What is not implemented:
- cart
- checkout
- payment confirmation
- student entitlement
- automatic unlock

## Lesson Activity Placeholder Structure
Activity placeholders are metadata only.

Supported activity types:
- `FLASHCARD`
- `QUIZ`
- `MATCHING`
- `LISTENING`
- `TRACING`
- `DRAG_DROP`
- `STORY`
- `AI_HELP`
- `TEXT_CONTENT`
- `VIDEO_PLACEHOLDER`

Phase 6 does not implement real gameplay or AI behavior for these activity types.

## Package Structure
Packages can contain:
- modules
- lessons

Stored package item metadata includes:
- item type
- item id
- display order
- title snapshot

## Role Permissions
Public:
- can browse published content only
- cannot manage content

Student:
- can browse published content only
- cannot access teacher or admin content APIs

Teacher:
- must be authenticated
- must have `TEACHER` role
- must be `APPROVED` to create or submit content
- can manage only owned content

Admin:
- must be authenticated
- must have `ADMIN` role
- can review, approve, reject, publish, and archive content

## Protected APIs
Teacher content APIs are protected under:
- `/api/v1/teachers/**`

Admin moderation APIs are protected under:
- `/api/v1/admin/**`

Public discovery APIs are exposed under:
- `/api/v1/public/**`

## What Is Intentionally Not Implemented In Phase 6
- AI tutor logic
- AI-generated lessons
- payment checkout
- cart
- subscription access
- quiz gameplay
- flashcard gameplay
- matching gameplay
- character tracing canvas
- student progress tracking
- rewards or badges logic
- live class scheduling
- recorded video player

## Manual Test Guide
### Teacher content creation
1. Log in as an approved teacher.
2. Create a module draft from `/teacher/modules`.
3. Create a lesson draft from `/teacher/lessons`.
4. Add one or more lesson activity placeholders.
5. Create a package draft from `/teacher/packages`.
6. Add lesson or module items to the package.
7. Submit the content for review.

### Admin moderation
1. Log in as admin.
2. Open `/admin/content/approvals`.
3. Review submitted content.
4. Approve one content item.
5. Reject another content item with a reason.
6. Publish approved content.
7. Archive content if needed.

### Public discovery
1. Open `/courses`.
2. Search or filter the public catalog.
3. Confirm only published content appears.
4. Open a course or package detail page.
5. Confirm paid content remains locked and metadata-only.
