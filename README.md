# EastWest Bank (EWB) Standing Order Processor

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Architecture](https://img.shields.io/badge/Architecture-Microservices-blue.svg)](#architecture-overview)
[![Docker](https://img.shields.io/badge/Docker-Compose%20Ready-2496ED.svg)](docker-compose.yml)
[![Build Status](https://img.shields.io/badge/Tests-100%25%20Passing-success.svg)](#acceptance-scenarios--automated-testing)

An enterprise-grade, microservices-based **Standing Order Processor** for EastWest Bank (EWB) that enables bank customers to schedule recurring transfers (e.g., **₱5,000 monthly from salary account `EWB-SAL-1001` to savings account `EWB-SAV-2001` on the 25th of every month**).

The system executes transfers automatically, guarantees deduplication, performs atomic double-entry ledger bookkeeping, handles technical and business failures, provides uncertain outcome recovery, and delivers customer notifications via a transactional outbox.

---

## Architecture Overview

Built as a **Maven Multi-Module Microservices Architecture** from Day 1. Each microservice strictly owns its own data and communicates via REST and domain events rather than querying shared database tables.

```text
                               ┌──────────────────────────────────────────────┐
                               │  Browser (Glassmorphic SPA on Port 8080)     │
                               └──────────────────────┬───────────────────────┘
                                                      │
                                                      ▼
                                       ┌──────────────────────────────┐
                                       │    ewb-gateway (Port 8080)   │
                                       └──────────────┬───────────────┘
                                                      │ Reverse Proxy (/api/**)
                     ┌───────────────────┬────────────┴───────┬───────────────────┐
                     │                   │                    │                   │
                     ▼                   ▼                    ▼                   ▼
           ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
           │   Standing Order │ │    Execution     │ │     Payment      │ │   Notification   │
           │     Service      │ │     Service      │ │  (Mock Core Bank)│ │     Service      │
           │   (Port 8081)    │ │   (Port 8082)    │ │   (Port 8083)    │ │   (Port 8084)    │
           └─────────┬────────┘ └─────────┬────────┘ └─────────┬────────┘ └─────────┬────────┘
                     │                    │                    │                    │
                     ▼                    ▼                    ▼                    ▼
               [standing_order_db]   [execution_db]       [payment_db]       [notification_db]
```

---

## Service Breakdown

| Service Module | Port | Owned Database | Responsibilities |
|---|---|---|---|
| **`ewb-gateway`** | `8080` | None | Unified API reverse proxy, CORS/security filters, and hosts the glassmorphic SPA web console. |
| **`ewb-standing-order-service`** | `8081` | `standing_order_db` | Instruction CRUD, pause/resume/cancel lifecycle, smart recurrence math (with month-end clamping), and immutable version auditing. |
| **`ewb-execution-service`** | `8082` | `execution_db` | Discovery of due instructions, duplicate prevention, worker lease claiming, transfer orchestration, and transactional outbox. |
| **`ewb-payment-service`** | `8083` | `payment_db` | Mock core banking engine, account validation, atomic double-entry ledger (`DEBIT`/`CREDIT`), idempotency cache, and lost response simulation. |
| **`ewb-notification-service`** | `8084` | `notification_db` | Event consumption, deduplication by `event_id`, customer SMS & Email formatting, and outage resilience. |
| **`ewb-common`** | — | — | Shared DTOs, Enums, and Domain Event schemas. |

---

## Tech Stack

* **Language**: Java 21 (LTS)
* **Framework**: Spring Boot 3.3.4
* **Data Access**: Spring Data JPA & Hibernate 6
* **Databases**:
  * **H2 In-Memory**: Default development profile (zero external setup needed)
  * **PostgreSQL 16**: Production/Docker profile with separate logical databases
* **Security**: Spring Security with role-based personas (`CUSTOMER`, `OPERATIONS`, `AUDITOR`)
* **Build System**: Maven (Multi-Module with compiler `-parameters` enabled)
* **Frontend**: Vanilla HTML5, modern CSS3 (Glassmorphism & dark palette), and JavaScript
* **Orchestration**: Docker & Docker Compose

---

## Quick Start

### Prerequisites
* Java 21 JDK installed
* Maven 3.9+ installed
* Docker & Docker Compose (optional, for containerized deployment)

---

### Option 1: Run Locally (Fastest, Zero Dependencies)

Each service defaults to an in-memory database with pre-seeded mock accounts (`EWB-SAL-1001`, `EWB-SAV-2001`, etc.).

1. **Build and package all modules:**
   ```powershell
   mvn clean package -DskipTests
   ```

2. **Launch all services simultaneously with the PowerShell runner:**
   ```powershell
   .\run-all.ps1
   ```

   *(Alternatively, run each JAR in separate terminals:)*
   ```powershell
   java -jar ewb-payment-service/target/ewb-payment-service-1.0.0-SNAPSHOT.jar
   java -jar ewb-standing-order-service/target/ewb-standing-order-service-1.0.0-SNAPSHOT.jar
   java -jar ewb-notification-service/target/ewb-notification-service-1.0.0-SNAPSHOT.jar
   java -jar ewb-execution-service/target/ewb-execution-service-1.0.0-SNAPSHOT.jar
   java -jar ewb-gateway/target/ewb-gateway-1.0.0-SNAPSHOT.jar
   ```

3. **Access the Web Console:**
   Open your browser and navigate to: **`http://localhost:8080`**

---

### Option 2: Run with Docker Compose (PostgreSQL 16)

Spins up PostgreSQL with 4 independent databases alongside containerized microservices:

```bash
docker compose up --build
```

Access the Web Console at: **`http://localhost:8080`**

---

## Acceptance Scenarios & Automated Testing

All 8 business scenarios from Section 9 of `CaseStudy.md` are covered by automated JUnit 5 / Spring Boot integration tests:

| Scenario | Condition | Verified Behavior |
|---|---|---|
| **1. Successful Transfer** | Source: ₱20,000; Dest: ₱1,000; Transfer: ₱5,000 | Source: ₱15,000; Dest: ₱6,000; 2 ledger entries; Notification sent |
| **2. Insufficient Funds** | Source: ₱2,000 (`EWB-SAL-1002`); Transfer: ₱5,000 | Transfer fails (`INSUFFICIENT_FUNDS`); balances unchanged; recurring schedule preserved |
| **3. Frozen Account** | Source is frozen (`EWB-SAL-FROZEN`); Transfer: ₱5,000 | Payment rejected (`ACCOUNT_FROZEN`); zero ledger movement; attempt logged |
| **4. Duplicate Request** | Identical idempotency key replayed | Cached response returned; exactly 1 debit and 1 credit created |
| **5. Lost Response & Recovery** | Simulated timeout commits payment | Outcome marked `UNCERTAIN`; recovery query discovers confirmed payment without second debit |
| **6. Paused Instruction** | Pause order before cutoff | Order skipped by execution engine; no financial movement |
| **7. Month-End Schedule (Day 31)** | Monthly instruction on day 31 | Automatically clamps to Feb 28/29 or Apr 30 in shorter months |
| **8. Notification Outage** | Downstream notification service failure | Payment remains committed; transactional outbox safely retries delivery |

### Run the Test Suite:
```powershell
mvn test
```

---

## Interactive Web Console

When you open `http://localhost:8080`, you can:
* **Switch Personas**: Toggle between **Maria Santos (Customer)**, **Operations Lead (Ops)**, and **Compliance Auditor (Audit)**.
* **Customer Portal**: View live account balances, create recurring standing orders, pause/resume/cancel orders, and review customer notifications.
* **Operations Center**: Trigger discovery cycles manually, monitor executions, resolve uncertain transactions, and toggle notification outages.
* **Ledger & Audit Log**: View atomic double-entry bookkeeping (`pay_ledger`) and inspect immutable version diffs (`so_versions`).
* **1-Click Scenario Runner**: Execute and verify any of the 8 acceptance scenarios live with real-time terminal output.

---

## Postman API Collection

A complete Postman collection is included in the root directory:
* File: [`ewb-standing-order.postman_collection.json`](ewb-standing-order.postman_collection.json)
* Import into Postman to test all endpoints across Standing Orders, Execution Engine, Core Banking, and Notifications.

---

## Team Breakdown (4-Member Group Project)

The codebase is modularized to allow clean division among team members:

| Member | Focus Area | Primary Modules & Responsibilities |
|---|---|---|
| **Member 1** | **Standing Orders & Versioning** | `ewb-standing-order-service`<br>• Refine instruction CRUD & pagination.<br>• Expand recurrence rules (bi-weekly, cut-off logic).<br>• Add version comparison diff viewer. |
| **Member 2** | **Execution Engine & Scheduler** | `ewb-execution-service`<br>• Implement distributed locks (Redis / ShedLock).<br>• Enhance worker lease recovery.<br>• Circuit breaker integration with Resilience4j. |
| **Member 3** | **Core Banking & Ledger** | `ewb-payment-service`<br>• Core banking accounting rules and transaction limits.<br>• Optimistic locking retries on concurrent account access.<br>• External banking ISO 20022 adapter stub. |
| **Member 4** | **Gateway, Security & DevOps** | `ewb-gateway` & `ewb-notification-service`<br>• Spring Security JWT / OAuth2 integration.<br>• Real SMS/Email gateway adapters (Twilio / SendGrid mock).<br>• Docker production tuning & UI enhancements. |

---

## License

This project was developed for the EastWest Bank (EWB) Standing Order Processor Case Study.
