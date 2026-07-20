# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

C-Blog is a full-stack personal blog system built with Spring Boot 3 (backend) + Vue 3 (frontend), consisting of three sub-projects:

- **blog-backend/** — Spring Boot 3.1.4 + Java 17 backend (Maven)
- **blog-frontend/kuailemao-blog/** — Public-facing blog frontend (Vue 3 + Element Plus)
- **blog-frontend/kuailemao-admin/** — Admin dashboard (Vue 3 + Ant Design Vue, based on Antdv Pro)

## Build & Run Commands

### Backend (blog-backend/)

```bash
# Build
cd blog-backend && mvn clean package -DskipTests

# Run (default profile: dev)
mvn spring-boot:run

# Run with production profile
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# Run tests
mvn test
```

Backend starts on port **8088**. API docs at `http://localhost:8088/doc.html`.

### Blog Frontend (blog-frontend/kuailemao-blog/)

```bash
cd blog-frontend/kuailemao-blog
pnpm install
pnpm dev      # Dev server on port 99
pnpm build    # Production build
```

Vite proxies `/api` to backend (port 8088) and `/wapi` to music service (port 3000).

### Admin Frontend (blog-frontend/kuailemao-admin/)

```bash
cd blog-frontend/kuailemao-admin
pnpm install
pnpm dev          # Dev server on port 6678 (uses MistJS CLI)
pnpm build        # Production build
pnpm lint         # ESLint with fix
pnpm typecheck    # TypeScript check
```

## Architecture

### Backend Package Structure (xyz.kuailemao)

Standard layered architecture: `controller` → `service/impl` → `mapper` → database. Domain objects split into `entity`, `dto`, `vo`, `request`, `response`.

Key cross-cutting concerns:
- **Authentication**: Spring Security + JWT (`filter/JwtAuthorizeFilter`), OAuth via JustAuth (Gitee/GitHub)
- **Authorization**: RBAC model with `@PreAuthorize`, dynamic menus/permissions
- **Rate limiting**: `@AccessLimit` annotation + Redis, enforced per-minute per-endpoint
- **Logging**: `@LogAnnotation` + AOP (`aop/LogAspect`) + RabbitMQ async processing
- **Blacklist**: `@CheckBlacklist` annotation on endpoints

Infrastructure dependencies: MySQL 8.0, Redis 7.2, RabbitMQ (email/log queues), MinIO (file storage), Quartz (scheduled jobs).

MyBatis-Plus with logical delete (`isDeleted` field), auto-increment IDs, and XML mapper files in `resources/mapper/`.

### Frontend Architecture

Both frontends use Vue 3 + Pinia + Vue Router + TypeScript + Vite.

**Blog frontend** (kuailemao-blog): Organized by feature in `src/apis/` (API calls), `src/views/` (page components), `src/store/modules/` (Pinia stores). Uses unplugin-auto-import and unplugin-vue-components for Element Plus auto-imports.

**Admin frontend** (kuailemao-admin): Based on Antdv Pro framework. Uses `@mistjs/cli` as build tool. Pages organized in `src/pages/` with system management (`system/`) and blog management (`blog/`) sections. Custom directives `v-hasRole` and `v-hasPermi` for permission control in templates.

### Deployment

Docker Compose based deployment. Nginx serves both frontends and proxies `/api` to the backend container. See `.sh/docker-restart.sh` for rebuild script. SQL schemas in `sql/` directory.

## Key Configuration

- Backend config: `blog-backend/src/main/resources/application.yml` (active profile controlled by Maven profiles: `dev`/`prod`)
- Blog Vite config: `blog-frontend/kuailemao-blog/vite.config.ts`
- Admin Vite config: `blog-frontend/kuailemao-admin/vite.config.ts`
- Default admin credentials: username `ADMIN`, password `123456`

## Conventions

- Backend uses Lombok extensively — entities use `@Data`, `@Builder`, etc.
- RESTful API design with consistent response wrapper (`ResponseResult`)
- RabbitMQ queues handle async operations: email notifications, login/operation logging
- Frontend API layer in `apis/` (blog) or `api/` (admin) with axios instances
- Package manager is **pnpm** for both frontends (not npm/yarn)
- SQL files in `sql/` are versioned (e.g., `v1.5.0/`, `v1.6.0/`)
