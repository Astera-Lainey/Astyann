# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Astyann is a full-stack platform for AI-driven software development lifecycle automation. The monorepo has an Angular 21 frontend, a Spring Cloud Gateway, and Spring Boot microservices.

## Commands

### Frontend (`frontend/`)
```bash
npm run start    # dev server on http://localhost:4200
npm run build    # production build
npm run test     # Karma/Jasmine unit tests
npm run watch    # incremental build
```

### Backend — Auth Service (`backend/AuthService/`)
```bash
mvn spring-boot:run   # runs on :8081
mvn clean package     # build JAR
```

### Backend — API Gateway (`backend/APIGateway/`)
```bash
mvn spring-boot:run   # runs on :8080
docker compose up --build   # starts MySQL + Auth Service + Gateway together
```

## Architecture

### Frontend
Angular 21 **zoneless** standalone app with signal-based state. No NgModules.

```
src/app/
  core/
    guards/         # auth.guard.ts (requires token), guest.guard.ts (redirects if logged in)
    interceptors/   # jwt.interceptor.ts — attaches Bearer token from localStorage to every request
    models/         # auth.models.ts, project.models.ts, requirement.models.ts — API contracts
    services/       # AuthService (signals + computed), ProjectService, RequirementsService
  features/
    auth/           # login, signup, verify-email, reset-password, forgot-password
    dashboard/      # app-shell (nav), dashboard-page, new-project, project-workspace
    landing/        # public landing page
  shared/
    components/     # form-field, logo, theme-toggle, toast, decorative elements
```

**Key patterns:**
- All HTTP responses follow `{ status, message, data: T }` — services `.pipe(map(res => res.data))` before returning to components.
- Use `inject()` for service dependencies that are needed in class field initializers (e.g., `FormBuilder` used to initialize a `readonly form = this.fb.group(...)`). Constructor injection assigns `this.fb` *after* class fields initialize, causing a TS2729 error.
- `ChangeDetectionStrategy.OnPush` on every component.
- Route guards are functional (`CanActivateFn`), not class-based.

**Naming convention:** Components follow `feature-name.component.ts/html/scss`. Template and style URLs must match the actual file names exactly.

### Backend

**API Gateway (port 8080)** — Spring Cloud Gateway (reactive/Netty). Validates JWT on every route except the public auth endpoints, then proxies to downstream services. JWT secret is shared with AuthService via env var `JWT_SECRET`.

**Auth Service (port 8081)** — Spring Boot 3.3.6, MySQL on localhost:3307 (non-standard port). All auth endpoints under `/api/v1/auth`. DDL is `update` (auto-creates schema).

**Planned microservices** (not yet implemented):
- Project Service :8082, Requirement Service :8083, UML :8084, Document :8085, Code Gen :8086, Version :8087, Deployment :8088, AI Orchestrator :8089

### API base URL
Frontend calls go to `environment.apiBaseUrl` (configured in `src/environments/`), which in dev points to `http://localhost:8080`. The gateway strips the `/api/v1/` prefix and routes by path segment.

### Environment setup
Copy `backend/APIGateway/.env.example` to `.env` and fill in `JWT_SECRET` and service URLs before running Docker Compose. MySQL listens on host port **3307** (not 3306).
