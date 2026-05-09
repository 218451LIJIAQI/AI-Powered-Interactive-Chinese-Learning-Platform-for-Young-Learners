# Phase 10 Live, Recorded, And Hybrid Learning

## Overview
Phase 10 adds live classes, recorded lesson support, hybrid lesson delivery, protected meeting-link access, video progress tracking, and simple schedule views for student, teacher, and admin roles.

## Live Class Feature
Teacher APIs:
- `POST /api/v1/teachers/live-classes`
- `GET /api/v1/teachers/live-classes`
- `GET /api/v1/teachers/live-classes/{id}`
- `PUT /api/v1/teachers/live-classes/{id}`
- `PATCH /api/v1/teachers/live-classes/{id}/status`
- `PATCH /api/v1/teachers/live-classes/{id}/cancel`
- `PATCH /api/v1/teachers/live-classes/{id}/reschedule`
- `GET /api/v1/teachers/live-classes/{id}/enrolments`

Student APIs:
- `GET /api/v1/students/live-classes`
- `GET /api/v1/students/live-classes/{id}`
- `POST /api/v1/students/live-classes/{id}/enrol`
- `POST /api/v1/students/live-classes/{id}/cancel-enrolment`
- `GET /api/v1/students/live-classes/{id}/join`

Admin APIs:
- `GET /api/v1/admin/live-classes`
- `GET /api/v1/admin/live-classes/{id}`

Live class values:
- meeting platforms: `ZOOM`, `GOOGLE_MEET`, `MICROSOFT_TEAMS`, `OTHER`
- class status: `SCHEDULED`, `ONGOING`, `COMPLETED`, `CANCELLED`, `RESCHEDULED`

## Meeting Link Security Rule
- Meeting links are never exposed through public course or lesson APIs.
- Student live class list and detail APIs do not include the raw meeting link.
- Only the protected join endpoint returns the meeting link.
- Student join requires:
  - authenticated `STUDENT`
  - active enrolment
  - class status that allows joining
  - published linked content
  - required paid entitlement or access when content is paid
- Teacher owner and admin responses may include the meeting link when needed for management.

## Live Class Enrolment
- Enrolment states: `ENROLLED`, `CANCELLED`, `ATTENDED`, `MISSED`
- Duplicate active enrolment is blocked.
- Enrolment beyond `maxParticipants` is blocked.
- Re-enrolment after cancellation is allowed through the same record being reactivated.

## Recorded Lesson Feature
Teacher APIs:
- `POST /api/v1/teachers/lessons/{lessonId}/recorded-videos`
- `GET /api/v1/teachers/lessons/{lessonId}/recorded-videos`
- `PUT /api/v1/teachers/lessons/{lessonId}/recorded-videos/{videoId}`
- `PATCH /api/v1/teachers/lessons/{lessonId}/recorded-videos/{videoId}/archive`
- `GET /api/v1/teachers/lessons/{lessonId}/video-progress`

Student APIs:
- `GET /api/v1/students/lessons/{lessonId}/recorded-videos`
- `GET /api/v1/students/lessons/{lessonId}/recorded-videos/{videoId}`
- `GET /api/v1/students/recorded-videos/{videoId}/stream`

Recorded video source types:
- `UPLOADED_FILE`
- `EXTERNAL_URL`

## Recorded Video Access Rules
- Uploaded recorded videos are stored through backend-managed file handling.
- Allowed upload content types:
  - `video/mp4`
  - `video/webm`
  - `video/quicktime`
- File path traversal is prevented through normalized path checks.
- The frontend only receives a protected stream URL, never the private storage path.
- Students must pass lesson or package access checks before viewing or streaming a recorded video.

## Video Progress Tracking
Student APIs:
- `GET /api/v1/students/video-progress/{videoId}`
- `POST /api/v1/students/video-progress/{videoId}`
- `PATCH /api/v1/students/video-progress/{videoId}`
- `POST /api/v1/students/video-progress/{videoId}/complete`

Rules:
- progress percentage must stay between `0` and `100`
- watched seconds and total seconds must not be negative
- mark complete sets the final state to complete and progress to `100`
- students can only update their own progress

## Hybrid Learning Support
- Lesson player responses now include:
  - `learningType`
  - `liveClasses`
  - `recordedVideos`
- `LIVE` lessons emphasize live class scheduling.
- `RECORDED` lessons emphasize recorded video playback.
- `HYBRID` lessons show live class, recorded video, and existing Phase 7 activity sections together.
- Phase 9 paid entitlement checks still apply.

## Schedule Feature
Student:
- `GET /api/v1/students/schedule`
- upcoming enrolled live classes
- learning tasks
- recorded videos to continue

Teacher:
- `GET /api/v1/teachers/schedule`
- upcoming teaching sessions
- recorded lesson updates

Admin:
- `GET /api/v1/admin/schedule`
- platform-wide live class overview

The schedule UI is intentionally simple and list-based for FYP scope.

## Frontend Routes
Student:
- `/student/live-classes`
- `/student/live-classes/:id`
- `/student/schedule`
- `/student/recorded-lessons/:id`

Teacher:
- `/teacher/live-classes`
- `/teacher/live-classes/create`
- `/teacher/live-classes/:id/edit`
- `/teacher/schedule`
- `/teacher/recorded-lessons`

Admin:
- `/admin/live-classes`
- `/admin/schedule`

## How Paid Access Interacts With Live And Recorded Learning
- Free content can be accessed without Phase 9 entitlement records.
- Paid live classes require lesson or package entitlement before enrol or join succeeds.
- Paid recorded videos require lesson or package entitlement before list, detail, or stream access succeeds.
- Backend access checks remain authoritative.

## How To Test Teacher Live Class Creation
1. Log in as an approved teacher.
2. Open `/teacher/live-classes`.
3. Create a live class linked to an owned lesson or package.
4. Confirm the class appears in the teacher list and schedule.

## How To Test Student Enrolment
1. Log in as a student.
2. Open `/student/live-classes`.
3. Enrol in an available class.
4. Confirm the status changes to enrolled.

## How To Test Join Live Class Flow
1. Stay logged in as an enrolled student.
2. Open the live class detail page or lesson player live section.
3. Click `Join Class`.
4. Confirm the frontend only receives the link after the backend join endpoint approves access.

## How To Test Recorded Video Progress
1. Log in as an approved teacher and add a recorded video to a published lesson.
2. Log in as a student with access to that lesson.
3. Open the lesson player or `/student/recorded-lessons/{id}`.
4. Save progress and mark the video complete.
5. Confirm teacher video progress view reflects the updated state.

## Known Limitations
- external meeting links only
- no internal video conferencing
- no WebRTC
- no Zoom, Google Meet, or Teams clone
- no advanced streaming, CDN, or transcoding
- recorded video upload and playback are prototype-friendly rather than production media infrastructure
