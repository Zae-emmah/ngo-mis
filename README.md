# NGO-MIS
Frontend: React + TypeScript (Vite). Backend: Spring Boot 3 (Java 17, H2 database).
Run backend: `cd backend && mvn spring-boot:run` (port 8080)
Run frontend: `cd frontend && npm install && npm run dev` (port 5173)
Demo login: admin@ngo.org / admin123
Notes: demo-grade auth (no token enforcement on data routes); AI reports are rule-based on live data - swap `Api.report` for an LLM call.
