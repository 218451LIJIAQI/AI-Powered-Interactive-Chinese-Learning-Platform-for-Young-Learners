# Phase 1 Setup Guide

## Overview
This document explains how to run the Phase 1 foundation of the AI-Powered Interactive Chinese Learning Platform locally.

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

## 2. Run the Backend
From the `backend` folder:

```powershell
.\mvnw.cmd spring-boot:run
```

Health check:

```text
GET http://localhost:8080/api/v1/health
```

## 3. Run the Frontend
From the `frontend` folder:

```powershell
npm install
npm run dev
```

Default frontend URL:
- `http://localhost:5173`

## 4. Environment Files
- Frontend example: `frontend/.env.example`
- Backend example: `backend/.env.example`
- Backend application config example: `backend/src/main/resources/application-example.yml`

## 5. What Exists In Phase 1
- Frontend app scaffold
- Role-based layouts
- Public and dashboard placeholder pages
- Vue Router setup
- Pinia setup
- Axios base client
- Vue I18n placeholder translations for English, Bahasa Melayu, and Chinese
- Theme structure for light and dark mode
- Spring Boot backend scaffold
- `/api/v1/health` endpoint
- API response wrapper
- Global exception handler skeleton
- Basic CORS placeholder
- MySQL Docker Compose setup

Phase 2 note:
- The backend now expects the local Docker MySQL instance on port `3307` by default.

## 6. What Is Intentionally Deferred
- Authentication and JWT
- Database entities and repositories
- Teacher verification
- Content management
- AI provider integration
- Payment and subscriptions
- Notifications, analytics, and reports
