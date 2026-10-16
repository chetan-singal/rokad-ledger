# ROKAD — Daily Ledger

Offline, single-user desktop ledger application.

- `frontend/` — Electron + React (Vite)
- `backend/` — Spring Boot REST API
- Database — local MySQL (`rokad_db`)

## Phase 1 status

This is the Phase 1 skeleton: it proves the frontend, backend, and database can
talk to each other. No ledger logic exists yet — that starts in Phase 2
onward (see the Development Roadmap).

## Getting started

### 1. Database
```sql
CREATE DATABASE rokad_db;
```
Then edit `backend/src/main/resources/application.properties` with your
MySQL username/password.

### 2. Backend
```bash
cd backend
mvn spring-boot:run
```
Visit http://localhost:8080/api/health — it should return
`OK - ROKAD backend is running`.

### 3. Frontend
```bash
cd frontend
npm install
npm run dev
```
In a second terminal, once Vite is running on http://localhost:5173:
```bash
npm run electron:dev
```
The Electron window should open and show the backend health status.

## Layout

```
rokad-ledger/
├── frontend/                        # Electron + React (Vite)
│   ├── electron/main.js             # Electron main process
│   └── src/                         # React app
└── backend/
    └── src/main/java/com/rokad/
        ├── controller/              # REST endpoints (Presentation boundary)
        ├── service/
        │   ├── calculation/         # Financial Calculation Service — Phase 4
        │   └── lifecycle/           # Ledger Lifecycle Service — Phase 5
        ├── repository/              # Data Access layer
        ├── entity/
        └── dto/
```
