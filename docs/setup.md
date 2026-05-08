# Setup Guide

## Overview
This document explains how to run the project locally through Phase 3.

## Prerequisites
- Node.js and npm
- Java 21
- Docker Desktop with Docker Compose

Note:
- Maven does not need to be installed globally because the backend includes the Maven Wrapper.

## 1. Start MySQL
From the project root:

```powershell
docker compose up -d
```

This starts:
- MySQL 8.4
- Database name: `ai_chinese_learning`
- Host port: `3307`

## 2. Configure Backend Environment Variables
Use:
- [backend/.env.example](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/backend/.env.example)
- [backend/src/main/resources/application-example.yml](/C:/Users/leeji/OneDrive/Desktop/AI-Powered%20Interactive%20Chinese%20Learning%20Platform%20for%20Young%20Learners/backend/src/main/resources/application-example.yml)

Important Phase 3 variables:
- `JWT_SECRET`
- `JWT_EXPIRATION`
- `DEFAULT_ADMIN_EMAIL`
- `DEFAULT_ADMIN_PASSWORD`

Example:

```env
DB_HOST=localhost
DB_PORT=3307
DB_NAME=ai_chinese_learning
DB_USERNAME=root
DB_PASSWORD=root
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
JWT_SECRET=replace-with-a-32-character-minimum-secret
JWT_EXPIRATION=3600000
DEFAULT_ADMIN_EMAIL=admin@example.com
DEFAULT_ADMIN_PASSWORD=SuperSecure123!
```

Notes:
- Do not use real production secrets in local demo setup files.
- If `DEFAULT_ADMIN_EMAIL` or `DEFAULT_ADMIN_PASSWORD` is missing, the backend still starts safely and skips admin creation.

## 3. Run the Backend
From the `backend` folder:

```powershell
.\mvnw.cmd spring-boot:run
```

Health check:

```text
GET http://localhost:8080/api/v1/health
```

## 4. Run the Frontend
From the `frontend` folder:

```powershell
npm install
npm run dev
```

Default frontend URL:
- `http://localhost:5173`

## 5. Phase 3 Authentication Flows
Implemented auth endpoints:
- `POST /api/v1/auth/register/student`
- `POST /api/v1/auth/register/teacher`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

### Student registration
- Public route
- Creates `STUDENT` role user
- Creates `student_profiles` record

### Teacher registration
- Public route
- Creates `TEACHER` role user
- Creates `teacher_profiles` record
- Default status is `PENDING`

### Admin login
- Admin cannot register publicly
- Admin account is seeded only from:
  - `DEFAULT_ADMIN_EMAIL`
  - `DEFAULT_ADMIN_PASSWORD`

### Frontend auth behavior
- JWT token is stored in local storage
- Axios automatically sends `Authorization: Bearer <token>`
- Vue Router guards protect role pages
- Role redirects after login:
  - `STUDENT -> /student/dashboard`
  - `TEACHER -> /teacher/dashboard`
  - `ADMIN -> /admin/dashboard`

## 6. Protected Route Testing
Without token:
- `GET /api/v1/students/dashboard` should return `401`

With a student token:
- `GET /api/v1/students/dashboard` should return `200`
- `GET /api/v1/admin/dashboard` should return `403`

With a teacher token:
- `GET /api/v1/teachers/dashboard` should return `200`

With an admin token:
- `GET /api/v1/admin/dashboard` should return `200`

## 7. What Exists Through Phase 3
- Frontend app scaffold
- Role-based layouts
- Real login page
- Real student and teacher registration page
- Auth store, JWT handling, and route guards
- Student, teacher, and admin dashboard placeholders with authenticated user info
- Spring Boot backend
- `/api/v1/health`
- Database foundation and seed data
- Spring Security and JWT authentication
- Current user API
- Role-based backend protection
- Backend auth integration tests

## 8. What Is Intentionally Deferred
- AI integration
- Payment and subscription workflows
- Learning content management
- Teacher verification document upload workflow
- Course, package, lesson, quiz, and business feature workflows
- Full email delivery system
