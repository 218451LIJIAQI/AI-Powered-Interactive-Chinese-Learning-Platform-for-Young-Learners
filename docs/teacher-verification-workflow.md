# Teacher Verification Workflow

## Overview
Phase 5 adds a real teacher verification workflow built on the existing authentication, teacher profile, upload metadata, notification, and audit log foundations.

The workflow keeps teacher verification documents private while allowing admins to review and approve or reject teacher accounts safely.

## Teacher Status Values
- `PENDING`
  - default status after teacher registration
  - teacher can log in
  - teacher can update profile and upload verification files
  - teacher is not publicly visible
- `APPROVED`
  - set only by an admin
  - teacher can keep using the teacher dashboard
  - teacher becomes publicly visible through the approved teacher public profile API
- `REJECTED`
  - set only by an admin with a required reason
  - teacher can see the rejection reason
  - teacher can upload new documents and resubmit verification
  - teacher is not publicly visible

## Backend Tables Used
- `teacher_profiles`
- `teacher_verification_documents`
- `teacher_verification_reviews`
- `uploaded_files`
- `notifications`
- `audit_logs`

## Teacher Workflow
1. Teacher registers publicly through `POST /api/v1/auth/register/teacher`.
2. The backend creates:
   - a `users` record
   - a `teacher_profiles` record
   - `teacher_status = PENDING`
3. Teacher signs in and opens:
   - `/teacher/profile`
   - `/teacher/verification-status`
   - `/teacher/verification-documents`
4. Teacher updates their profile details.
5. Teacher uploads a verification document with:
   - `POST /api/v1/teachers/verification-documents`
6. The backend:
   - validates file type and size
   - stores the file in `UPLOAD_DIR`
   - stores metadata in `uploaded_files`
   - creates a `teacher_verification_documents` record
   - creates notification and audit log entries
7. If the teacher was rejected before, they can call:
   - `POST /api/v1/teachers/resubmit-verification`
8. The backend changes status from `REJECTED` back to `PENDING`.

## Admin Workflow
1. Admin signs in with the seeded admin account.
2. Admin reviews pending teachers at:
   - `/admin/teacher-approval`
3. Admin opens a teacher detail page:
   - `/admin/teacher-approval/{id}`
4. Admin can view protected teacher verification documents.
5. Admin approves with:
   - `POST /api/v1/admin/teachers/{id}/approve`
6. Or rejects with:
   - `POST /api/v1/admin/teachers/{id}/reject`
7. The backend then:
   - updates `teacher_status`
   - writes a `teacher_verification_reviews` record
   - writes an `audit_logs` record
   - creates an in-app `notifications` record
   - calls the no-op-friendly email service safely if no real SMTP service exists

## Public Teacher Profile
Public teacher profile API:
- `GET /api/v1/public/teachers/{id}`

Rules:
- only `APPROVED` teachers are visible
- `PENDING` teachers return `404`
- `REJECTED` teachers return `404`
- verification documents are never exposed publicly
- teacher email is not exposed publicly

Frontend public page:
- `/teachers/:id`

## File Upload Storage
Environment variables:
- `UPLOAD_DIR`
- `MAX_FILE_SIZE`

Allowed file types:
- `application/pdf`
- `image/jpeg`
- `image/png`

Storage behavior:
- files are stored in a backend-managed directory
- the database stores metadata only
- stored filenames are generated uniquely
- uploads reject unsafe extensions, unsupported content types, empty files, and oversized files

## Protected APIs
Teacher-only:
- `GET /api/v1/teachers/me`
- `PUT /api/v1/teachers/me/profile`
- `GET /api/v1/teachers/verification-status`
- `POST /api/v1/teachers/verification-documents`
- `POST /api/v1/teachers/resubmit-verification`
- `GET /api/v1/teachers/verification-documents/{id}`

Admin-only:
- `GET /api/v1/admin/teachers`
- `GET /api/v1/admin/teachers/{id}`
- `GET /api/v1/admin/teachers/pending`
- `POST /api/v1/admin/teachers/{id}/approve`
- `POST /api/v1/admin/teachers/{id}/reject`
- `GET /api/v1/admin/teacher-documents/{id}`
- `GET /api/v1/admin/users`
- `GET /api/v1/admin/students`

Public:
- `GET /api/v1/public/teachers/{id}`

## Manual Test Checklist
Teacher:
- register teacher
- log in as teacher
- open `/teacher/profile`
- update full name, bio, and categories
- open `/teacher/verification-documents`
- upload a PDF or PNG document
- confirm status remains `PENDING`

Admin:
- log in as admin
- open `/admin/teacher-approval`
- open teacher detail page
- view uploaded document
- approve one teacher
- reject another teacher with a required reason

Rejected teacher:
- log in as rejected teacher
- confirm rejection reason is visible
- upload a new document
- resubmit verification
- confirm status becomes `PENDING`

Public:
- open `/teachers/{id}` for an approved teacher
- confirm verified badge appears
- confirm pending or rejected teacher IDs are not publicly visible
