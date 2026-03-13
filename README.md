# Backend API - Expense Tracker (Spring Boot)

Production-ready REST API for the Expense Tracker platform, built with Spring Boot and Java 21.

## Overview

This service provides:
- Expense, budget, goal, and analytics APIs
- Authentication/authorization with Spring Security
- PostgreSQL persistence via Spring Data JPA
- Firebase Admin integration for token verification and identity support
- AI integration through OpenRouter
- Realtime relay token issuance and media upload support (ImageKit)

## Tech Stack

- Java 21
- Spring Boot 3.x
- Spring Web, Spring Security, Spring Data JPA, Validation
- PostgreSQL
- Firebase Admin SDK
- Maven Wrapper (`mvnw`, `mvnw.cmd`)

## Project Structure

```text
src/main/java/com/wing/backendapiexpensespringboot
├─ controller/
├─ service/
├─ repository/
├─ model/
├─ dto/
├─ config/
└─ security/

src/main/resources
├─ application.properties
├─ application-dev.properties
└─ application-prod.properties
```

## Prerequisites

- Java 21 installed
- PostgreSQL-compatible database (Supabase/PostgreSQL)
- Maven (optional, wrapper is included)

## Getting Started

### 1) Install dependencies

No manual dependency installation is required. Maven Wrapper handles this during build.

### 2) Configure environment

Set environment variables for your active profile (recommended: `dev` for local).

Minimum required for production:
- `SPRING_PROFILES_ACTIVE=prod`
- `DATABASE_JDBC_URL` (or `SUPABASE_POOLER_JDBC_URL` / `SUPABASE_POOLER_URL` / `DATABASE_URL`)
- `DB_USERNAME`
- `DB_PASSWORD`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SERVICE_ACCOUNT_JSON` or `FIREBASE_SERVICE_ACCOUNT_PATH`
- `OPENROUTER_API_KEY`

Optional but commonly used:
- `CORS_ALLOW_ORIGINS`
- `REALTIME_RELAY_URL`
- `REALTIME_PUBLIC_SOCKET_URL`
- `REALTIME_RELAY_SECRET`
- `IMAGEKIT_PUBLIC_KEY`
- `IMAGEKIT_PRIVATE_KEY`
- `IMAGEKIT_URL_ENDPOINT`

### 3) Run locally

Windows:
- `./mvnw.cmd spring-boot:run`

macOS/Linux:
- `./mvnw spring-boot:run`

Default base URL (dev):
- `http://localhost:8080/api`

## Build and Test

- Run tests: `./mvnw.cmd test`
- Verify package: `./mvnw.cmd clean verify`
- Create artifact: `./mvnw.cmd clean package`

## Configuration Profiles

- `application.properties` selects profile via `SPRING_PROFILES_ACTIVE` (default: `dev`)
- `application-dev.properties` for local development behavior
- `application-prod.properties` for production behavior and stricter defaults

## Security Notes

- Never commit secrets (DB passwords, API keys, private keys).
- Use environment variables for all credentials.
- Keep production CORS origins restricted.
- Avoid embedding service-account JSON directly in source-controlled files.

## Deployment

Any Java 21-compatible environment works (Render, Docker, VM, Kubernetes).

Suggested deployment flow:
1. Set required environment variables.
2. Run `clean verify` in CI.
3. Deploy packaged artifact or Docker image.
4. Validate health endpoint and key API paths.

## License

Internal project documentation only. Add your license policy if needed.
