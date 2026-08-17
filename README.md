# 📘 OpenEx 3.0 – Simulated Crypto Exchange & AI Trading Terminal

![Build Status](https://img.shields.io/github/actions/workflow/status/thabang56R/Open-Exchange/ci.yml?branch=sprint-history&style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-green?style=for-the-badge)
![Version](https://img.shields.io/badge/version-3.0-blue?style=for-the-badge)
![Docker Ready](https://img.shields.io/badge/docker-ready-blue?style=for-the-badge)
![Made With Kotlin](https://img.shields.io/badge/made%20with-Kotlin-orange?style=for-the-badge)
![Frontend React](https://img.shields.io/badge/frontend-React-61DAFB?style=for-the-badge)
![Backend Spring Boot](https://img.shields.io/badge/backend-SpringBoot-6DB33F?style=for-the-badge)
![Database PostgreSQL](https://img.shields.io/badge/database-PostgreSQL-336791?style=for-the-badge)
![AI Ollama](https://img.shields.io/badge/AI-Ollama-9cf?style=for-the-badge)

---

## 1. 📌 Overview
OpenEx 3.0 is a lightweight crypto exchange simulation built with a fully open-source microservices architecture. It emphasizes **resilience**, **financial integrity**, **real-time streaming**, and **agentic AI integration**. This README serves as a runbook for deploying, configuring, and testing the system.

---

## 2. ⚙️ Core Engineering Standards
- **🔀 Version Control**
  - Feature-branch workflow (e.g., `feature/order-matching`, `fix/wallet-race-condition`).
  - No direct commits to `main`.
  - PRs must include descriptions, test steps, and screenshots for UI changes.
  - Commit messages follow Conventional Commits specification.
- **✅ CI/CD**
  - GitHub Actions pipeline runs unit tests and linting on every PR.
  - PRs cannot be merged if CI fails.
- **🏗️ Architecture Requirements**
  - Double-entry ledger for wallet balances.
  - API idempotency with `Idempotency-Key` headers.
  - Single `docker-compose up` orchestration with healthchecks.
  - Air-gapped AI using Ollama (no external APIs).

---

## 3. 📂 Prerequisites
- 🟠 Kotlin & Spring Boot  
- 🟦 PostgreSQL & Redis  
- ⚛️ React (Vite frontend)  
- 🐍 Python 3.10+ & Flask  
- 🐳 Docker & docker-compose  
- 🤖 LangChain for AI agent integration  

---

## 4. 🚀 Deployment

### 💻 Local Development
```bash
# Clone repository
git clone https://github.com/thabang56R/Open-Exchange.git
cd openex-droid-exchange

# Backend setup
cd backend
./gradlew bootRun

# Frontend setup
cd frontend
npm install
npm run dev

# Python microservice
cd analytics
pip install -r requirements.txt
py app.py

---

## 5.🐳 Docker Orchestration

docker-compose up --build

5. 🔑 Configuration

Create .env files in each service:

Backend .env

PORT=8080
DB_URL=jdbc:postgresql://db:5432/openex
REDIS_URL=redis://redis:6379
JWT_SECRET=your_secret_key

Frontend .env

VITE_API_URL=http://localhost:8080

Python .env

OLLAMA_MODEL=llama3

---

6. 🧪 Testing

---

🛠️ Backend Unit Tests

./gradlew test

---

🔄 Integration Tests

./gradlew integrationTest

---

⚛️ Frontend Tests

npm run test

---

7. 📊 Monitoring & Logs

Backend logs:

docker logs backend

Frontend logs: browser console.

Python service logs

docker logs analytics

---

8. 🛠️ Troubleshooting

⚠️ Database not ready → Ensure healthcheck waits for Postgres.

🔁 Duplicate orders → Verify Idempotency-Key header.

🤖 AI not responding → Confirm Ollama model is installed locally.

---

9. 🤝 Contribution Guidelines

Fork repo → Create feature branch → Submit PR.

Ensure CI/CD passes before requesting review.

Use descriptive commit messages.

---

10. 📜 License

MIT License – free to use and modify.

