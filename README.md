# AI-Powered Interactive Chinese Learning Platform for Young Learners

## Project Overview
This project is a full-stack final-year-project web application for young learners aged 7-12. It is designed as an AI-centered, interactive, and gamified Chinese learning platform for the Malaysian context, with support for student, teacher, and admin roles.

Phase 1 focuses only on the professional monorepo foundation:
- Vue 3 frontend scaffold with routing, state management, i18n, theme support, and role-based layouts
- Spring Boot backend scaffold with a versioned API base convention and health endpoint
- MySQL 8.4 development database via Docker Compose
- Initial project documentation for local setup

## Tech Stack
- Frontend: Vue 3, Vite, TypeScript, Element Plus, Vue Router, Pinia, Axios, Vue I18n, npm
- Backend: Java 21, Spring Boot 3.5.14, Maven Wrapper, Spring Web, Spring Data JPA, Bean Validation
- Database: MySQL 8.4 LTS
- Tooling: Docker Compose

## Monorepo Structure
```text
ai-chinese-learning-platform/
├── frontend/
├── backend/
├── docs/
├── docker-compose.yml
├── README.md
└── .gitignore
```

## Current Phase 1 Status
Completed in this phase:
- Monorepo scaffold created
- Vue frontend created with required packages and folder structure
- Public, student, teacher, and admin layouts added
- Placeholder pages added for requested routes
- Vue Router, Pinia, Axios, Element Plus, and Vue I18n configured
- Basic light/dark theme structure added
- Spring Boot backend scaffolded with `com.example.aichinese`
- `/api/v1/health` endpoint added
- `ApiResponse` wrapper and `GlobalExceptionHandler` skeleton added
- Basic CORS placeholder added
- Docker Compose MySQL setup added
- Setup documentation added

Not yet implemented:
- Authentication and JWT
- Database entities and business modules
- AI integration
- Payment integration
- Role-based backend authorization

## How To Start MySQL
From the project root:

```powershell
docker compose up -d
```

To stop MySQL:

```powershell
docker compose down
```

## How To Run Backend
From the `backend` folder:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend default URL:
- `http://localhost:8080`

Health endpoint:
- `GET http://localhost:8080/api/v1/health`

Example response:

```json
{
  "success": true,
  "message": "Service is running",
  "data": {
    "status": "UP",
    "service": "backend",
    "timestamp": "2026-05-08T00:00:00Z"
  },
  "errors": []
}
```

## How To Run Frontend
From the `frontend` folder:

```powershell
npm install
npm run dev
```

Frontend default URL:
- `http://localhost:5173`

## Environment Notes
- Frontend example environment file: `frontend/.env.example`
- Backend example environment file: `backend/.env.example`
- Backend configuration example: `backend/src/main/resources/application-example.yml`

## Additional Documentation
- Setup guide: `docs/setup.md`
