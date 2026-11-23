# NGO-MIS (CiviCore)
Frontend: React + TypeScript (Vite) -> deploy on Vercel. Backend: Spring Boot 3, Java 17, PostgreSQL -> deploy on Render (Docker, root dir `backend`).

## Run locally
1. Start Postgres and create a database `ngomis` (or set SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD).
2. Backend: `cd backend && mvn spring-boot:run`
3. Frontend: `cd frontend && npm install && npm run dev`
4. Register at /signup. Verification codes are emailed via Brevo.

## Environment variables
Backend (Render): SPRING_DATASOURCE_URL (jdbc:postgresql://...), SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, BREVO_API_KEY, MAIL_FROM (a sender verified in Brevo).
Frontend (Vercel): VITE_API_URL = your Render URL, no trailing slash. Redeploy after changing it.

## Upgrading an existing database
Drop the old oid avatar column once: `ALTER TABLE app_user DROP COLUMN avatar;` (or wipe the tables and re-register).

## Known gaps
Roles are not enforced on data routes; passwords use SHA-256 (move to BCrypt); sessions are in memory; forgot-password sends nothing.

## v2 changes and extra environment variables
- Backend (Render): ALLOWED_ORIGINS = your Vercel URL (e.g. https://yourapp.vercel.app, comma-separated for several; unset allows all), FRONTEND_URL = same Vercel URL (used in password-reset emails), ALLOW_DEMO_SUBSCRIBE = leave unset (self-service plan activation is OFF until a payment provider is integrated).
- Passwords now use BCrypt; old SHA-256 passwords upgrade automatically at next login. Sessions expire after 12 hours. Five wrong logins lock an account for 10 minutes.
- Roles are enforced on the server per module (Auditor is read-only). Forgot password sends a reset link by email.
- New columns are added automatically; no tables need to be dropped for this upgrade.
