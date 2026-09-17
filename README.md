# 🤖 AI-Powered IT Service Desk & Incident Auto-Remediation Engine

> **An AI-assisted IT operations platform that turns incident reports into intelligent diagnosis, governed remediation, health verification, and auditable resolution.**

[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-TypeScript-blue?logo=react)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-336791?logo=postgresql)](https://www.postgresql.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-Event%20Driven-black?logo=apachekafka)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-Caching-red?logo=redis)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Containerized-2496ED?logo=docker)](https://www.docker.com/)

### 🔗 Live Application

**Frontend:** https://ai-it-service-desk.vercel.app/

**Backend:** https://ai-it-service-desk.onrender.com

**Health Check:** https://ai-it-service-desk.onrender.com/actuator/health

**Repository:** https://github.com/vedhajanardhan/AI-IT-Service-Desk

---

## 💡 What is this?

Modern IT teams receive thousands of incidents ranging from application failures and authentication problems to infrastructure issues.

A traditional service desk often requires an engineer to:

1. Read the incident.
2. Classify the problem.
3. Determine severity.
4. Search troubleshooting documentation.
5. Identify a remediation.
6. Obtain authorization.
7. Execute the fix.
8. Verify system health.
9. Resolve or escalate the incident.

This project automates and assists that workflow while keeping **human authorization and policy controls around potentially risky remediation**.

### Core idea

```text
Incident
   ↓
AI Analysis
   ↓
Knowledge Recommendation
   ↓
Remediation Recommendation
   ↓
Policy Validation
   ↓
Approval
   ↓
Safe Remediation
   ↓
Health Verification
   ↓
Resolved / Escalated
```

The key design principle is:

> **AI can recommend an action, but it does not receive unrestricted permission to execute arbitrary commands.**

---

# 🏗️ Architecture

```text
┌───────────────────────────────────────────────────────────────┐
│                         React Frontend                        │
│                 React + TypeScript + Vite                    │
└──────────────────────────────┬────────────────────────────────┘
                               │ REST / JSON
                               ▼
┌───────────────────────────────────────────────────────────────┐
│                       Spring Boot API                         │
│                                                               │
│  Authentication  │  Incident Management  │  AI Analysis      │
│  Knowledge Base  │  Remediation           │  Notifications    │
│  Authorization   │  Audit / Events        │  Health Checks    │
└──────────────┬──────────────────┬──────────────────┬──────────┘
               │                  │                  │
               ▼                  ▼                  ▼
        ┌─────────────┐    ┌─────────────┐   ┌──────────────┐
        │ PostgreSQL  │    │    Redis    │   │ Apache Kafka │
        │             │    │             │   │              │
        │ Users       │    │ Fast access │   │ Async events │
        │ Incidents   │    │ / caching   │   │ Retry / DLT  │
        │ Knowledge   │    │             │   │              │
        │ Remediation │    │             │   │              │
        └─────────────┘    └─────────────┘   └──────────────┘
                                                   │
                                                   ▼
                                           Event-driven workflow
```

---

# 🔄 Incident Lifecycle

The platform models an incident as a controlled lifecycle rather than a simple CRUD record.

```text
                    ┌───────────────┐
                    │     OPEN      │
                    └───────┬───────┘
                            ▼
                    ┌───────────────┐
                    │    TRIAGED    │
                    └───────┬───────┘
                            ▼
                    ┌───────────────┐
                    │   ASSIGNED    │
                    └───────┬───────┘
                            ▼
                    ┌───────────────┐
                    │ AI ANALYZED   │
                    └───────┬───────┘
                            ▼
                 ┌──────────────────────┐
                 │ REMEDIATION REQUEST │
                 └──────────┬───────────┘
                            ▼
                    ┌───────────────┐
                    │    APPROVAL   │
                    └───────┬───────┘
                            ▼
                    ┌───────────────┐
                    │   EXECUTION   │
                    └───────┬───────┘
                            ▼
                    ┌───────────────┐
                    │ HEALTH CHECK  │
                    └───────┬───────┘
                       ┌────┴────┐
                       ▼         ▼
                 ┌──────────┐ ┌───────────┐
                 │ RESOLVED │ │ ESCALATED │
                 └──────────┘ └───────────┘
```

---

# 🧠 AI-Assisted Incident Analysis

The application uses an **AI provider abstraction** so the business logic does not depend directly on one AI vendor.

The analysis layer is designed to provide:

* Incident classification
* Severity assessment
* Root-cause suggestions
* Recommended remediation
* Knowledge-base recommendations
* Structured analysis results

### Provider abstraction

```text
                 ┌──────────────────┐
                 │  AI Analysis     │
                 │    Service       │
                 └────────┬─────────┘
                          │
                    Provider Interface
                          │
                ┌─────────┴──────────┐
                ▼                    ▼
        ┌──────────────┐     ┌──────────────┐
        │  Mock AI     │     │  Anthropic   │
        │  Provider    │     │  Provider    │
        └──────────────┘     └──────────────┘
```

This allows local development and testing without requiring a paid external AI API.

---

# 🛡️ Safe AI Auto-Remediation

The most important architectural decision in this project is **not allowing an AI model to execute arbitrary commands**.

Instead, remediation follows a controlled pipeline:

```text
AI Recommendation
       │
       ▼
Predefined Remediation Action
       │
       ▼
Risk / Policy Validation
       │
       ▼
Role Authorization
       │
       ▼
Approval Requirement
       │
       ▼
Execution
       │
       ▼
Health Check
       │
       ├───────────────┐
       ▼               ▼
   SUCCESS           FAILURE
       │               │
       ▼               ▼
   RESOLVED      RETRY / ESCALATE
```

### Safety controls

* Predefined remediation catalog
* Role-based authorization
* Policy validation
* Approval workflow
* Execution timeout
* Retry limits
* Idempotency considerations
* Health verification
* Failure handling
* Escalation
* Audit trail

This makes the automation model **governed rather than blindly autonomous**.

---

# ⚡ Event-Driven Architecture

Apache Kafka is used to decouple incident processing and remediation workflows.

### Event topics

```text
incident.created
incident.assigned
incident.analyzed

remediation.requested
remediation.started
remediation.completed
remediation.failed

incident.resolved
incident.escalated

servicedesk.dlt
```

### Why Kafka?

Using asynchronous events allows the platform to:

* Decouple services
* Process workflows asynchronously
* Support retries
* Handle failures
* Introduce dead-letter processing
* Scale event consumers independently
* Maintain a clear incident event flow

---

# 🔐 Security

Security is built into the application rather than added as an afterthought.

### Authentication

* JWT access tokens
* Refresh tokens
* Password hashing
* Protected APIs

### Authorization

Role-based access control supports:

```text
EMPLOYEE
ENGINEER
ADMIN
```

Different operations require different levels of authorization.

### Secrets

Production credentials are provided through environment variables.

Sensitive values such as:

* Database passwords
* JWT secrets
* Kafka credentials
* AI API keys
* Kafka CA certificates

are **not stored in the repository**.

---

# 🎫 Incident Management

Users can create and track incidents through the web application.

An incident contains information such as:

* Title
* Description
* Category
* Severity
* Status
* Reporter
* Assigned engineer
* AI analysis
* Remediation information
* Event history
* Comments

Example:

```text
Title:
Checkout API returning intermittent 503 errors

Category:
APPLICATION ERROR

Severity:
MEDIUM

Status:
OPEN
```

The incident can then enter the AI-assisted workflow.

---

# 📚 Knowledge Base

The platform includes a knowledge-base layer designed to help engineers find relevant troubleshooting information.

The AI analysis workflow can use incident information to recommend relevant knowledge articles and troubleshooting guidance.

This reduces repetitive manual investigation and provides engineers with contextual information during incident handling.

---

# 📊 Observability & Health

Spring Boot Actuator provides production health endpoints.

### Health

```text
GET /actuator/health
```

### Liveness

```text
GET /actuator/health/liveness
```

### Readiness

```text
GET /actuator/health/readiness
```

The deployment also uses health checks to help determine whether the backend is ready to serve traffic.

---

# 🧰 Technology Stack

## Backend

| Technology           | Purpose                        |
| -------------------- | ------------------------------ |
| Java 17+             | Backend language               |
| Spring Boot          | Application framework          |
| Spring Security      | Authentication & authorization |
| JWT                  | Stateless authentication       |
| Spring Data JPA      | Persistence                    |
| Hibernate            | ORM                            |
| PostgreSQL           | Relational database            |
| Flyway               | Database migrations            |
| Redis                | Fast-access data / caching     |
| Apache Kafka         | Event-driven processing        |
| Spring Kafka         | Kafka integration              |
| SpringDoc OpenAPI    | API documentation              |
| Spring Boot Actuator | Health & monitoring            |
| Maven                | Build & dependency management  |

## Frontend

| Technology   | Purpose                        |
| ------------ | ------------------------------ |
| React        | UI                             |
| TypeScript   | Type-safe frontend development |
| Vite         | Frontend tooling               |
| Tailwind CSS | Styling                        |
| Axios        | HTTP communication             |
| React Router | Client-side routing            |
| Recharts     | Data visualization             |

## AI

| Component               | Purpose                           |
| ----------------------- | --------------------------------- |
| AI Provider abstraction | Vendor-independent architecture   |
| Mock AI provider        | Local development/testing         |
| Anthropic provider      | Production AI integration support |

## Infrastructure

| Technology     | Purpose              |
| -------------- | -------------------- |
| Docker         | Containerization     |
| Docker Compose | Local infrastructure |
| Render         | Backend deployment   |
| Vercel         | Frontend deployment  |
| Aiven          | Managed Kafka        |

---

# 🗂️ Repository Structure

```text
AI-IT-Service-Desk/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   │
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── vite.config.*
│
├── docs/
│
├── .env.example
├── .gitignore
├── docker-compose.yml
└── README.md
```

---

# 🧪 Testing

The backend includes automated tests covering important application behavior, including:

* Authentication
* AI analysis
* Incident management
* Remediation policy validation
* Remediation execution
* Service-layer behavior

Run the backend verification suite:

```bash
cd backend
mvn clean verify
```

A successful build verifies compilation, tests, packaging, and Maven verification.

---

# 💻 Run Locally

## Prerequisites

Install:

* Java 17+
* Maven
* Node.js
* npm
* Docker Desktop

## Backend

```bash
cd backend
mvn spring-boot:run
```

Backend:

```text
http://localhost:8090
```

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:5173
```

## Docker Compose

```bash
docker compose up --build
```

Environment configuration is documented in:

```text
.env.example
```

---

# 🌐 Production Deployment

## Frontend

Deployed on Vercel:

**https://ai-it-service-desk.vercel.app/**

## Backend

Deployed on Render:

**https://ai-it-service-desk.onrender.com**

## API Health

**https://ai-it-service-desk.onrender.com/actuator/health**

## Database

Managed PostgreSQL is used for production persistence.

## Kafka

Managed Apache Kafka is used for production event processing with:

```text
SASL_SSL
SCRAM-SHA-256
```

The Kafka CA certificate is supplied securely through environment configuration.

---

# 🔌 API Documentation

When the backend is running:

```text
/swagger-ui.html
```

Production:

**https://ai-it-service-desk.onrender.com/swagger-ui/index.html**

OpenAPI specification:

```text
/api-docs
```

---

# 🚦 Current Implementation Status

### Platform

* [x] React frontend
* [x] Spring Boot backend
* [x] PostgreSQL
* [x] Redis
* [x] Kafka
* [x] Docker configuration
* [x] Environment-based configuration

### Security

* [x] JWT authentication
* [x] Role-based authorization
* [x] Protected APIs
* [x] Production secret configuration
* [x] CORS configuration

### Incident Management

* [x] Incident creation
* [x] Incident lifecycle
* [x] Assignment
* [x] Severity
* [x] Categorization
* [x] Comments / event tracking

### AI

* [x] AI provider abstraction
* [x] Mock AI provider
* [x] Anthropic provider support
* [x] Incident analysis architecture
* [x] Knowledge recommendations

### Remediation

* [x] Remediation policy validation
* [x] Role checks
* [x] Approval model
* [x] Retry handling
* [x] Timeout handling
* [x] Health verification
* [x] Failure / escalation flow
* [x] Audit-oriented execution tracking

### Deployment

* [x] GitHub repository
* [x] Render backend deployment
* [x] Vercel frontend deployment
* [x] Managed PostgreSQL
* [x] Managed Kafka
* [x] Production CORS
* [x] Production health endpoint
* [x] End-to-end login
* [x] End-to-end incident creation

---

# 🎯 Engineering Highlights

This project demonstrates practical backend and full-stack engineering beyond basic CRUD:

### 1. Event-driven design

Kafka decouples incident and remediation workflows.

### 2. Secure automation

AI recommendations are constrained by predefined actions and policy validation.

### 3. Role-aware operations

Different user roles receive different capabilities.

### 4. Failure-aware processing

Retries, timeouts, dead-letter handling, health checks, and escalation are considered as part of the workflow.

### 5. Provider abstraction

The AI layer is designed so the application isn't tightly coupled to a single AI provider.

### 6. Production deployment

The system is deployed across:

```text
Vercel
   ↓
Render
   ↓
PostgreSQL + Redis + Kafka
```

### 7. Environment-based configuration

Development and production configuration are separated through environment variables rather than hard-coded credentials.

---
### 📸 Screenshots

**Incident Queue**
![Incident Queue](docs/incident-queue.png)

**Remediation Catalog**
![Remediation Catalog](docs/remediation-catalog.png)


# 📈 Future Improvements

Planned improvements include:

* [ ] Full CI/CD pipeline with GitHub Actions
* [ ] Expanded AI providers
* [ ] More remediation actions
* [ ] Advanced incident analytics
* [ ] Prometheus/Grafana observability
* [ ] More integration tests
* [ ] Infrastructure-as-code
* [ ] Advanced audit reporting
* [ ] Notification integrations
* [ ] Production-grade remediation executors

---

# 👨‍💻 Author

## Vedha Janardhan

**Backend / Full-Stack Developer**

Building production-oriented applications with:

**Java • Spring Boot • React • PostgreSQL • Kafka • Redis • Docker • AI**

### GitHub

https://github.com/vedhajanardhan

---

## ⭐ Why this project?

This project explores a practical intersection of:

**AI + Backend Engineering + Distributed Systems + Security + Automation**

rather than treating AI as a standalone chatbot.

The focus is on building an **auditable, policy-controlled, event-driven system** where AI assists engineers while deterministic application logic remains responsible for authorization, safety, execution, and verification.
