# 🛍️ Neustack E-Commerce Application

[![Java](https://img.shields.io/badge/Java-17-orange.svg?style=flat&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg?style=flat&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg?style=flat&logo=docker)](https://www.docker.com/)

A production-grade, highly-reliable RESTful E-Commerce and Rewards engine built with **Spring Boot** and **PostgreSQL**. Designed to handle real-world challenges including **strict transaction rollbacks**, **concurrency race conditions (pessimistic locking)**, and **idempotent cart checkouts**.

---

## 📑 Table of Contents

- [Key Architecture & Features](#-key-architecture--features)
- [Quick Start](#-quick-start)
  - [Prerequisites](#1-prerequisites)
  - [Start the Stack](#2-start-the-stack)
  - [Access pgAdmin UI & Tables](#3-access-pgadmin-ui--view-database-tables)
  - [Stop the Stack](#4-stop-the-stack)
- [pgAdmin Management](#-pgadmin-management)
- [Database Seed & Reset](#-database-seed--reset)
- [Testing & Quality Assurance](#-testing--quality-assurance)
  - [End-to-End API Test](#1-end-to-end-api-test-suite)
  - [Transaction Rollback Test](#2-transaction-rollback-test)
  - [Concurrency & Pessimistic Locks Test](#3-concurrency--pessimistic-locks-test)
  - [Checkout Idempotency Test](#4-checkout-idempotency-test)
- [Postman API Collection](#-postman-api-collection)
- [API Reference](#-api-reference)

---

## 🚀 Key Architecture & Features

- **Pessimistic Locking & Zero Overselling:** Uses database-level row locks (`PESSIMISTIC_WRITE`) during inventory deductions and cart checkouts to eliminate race conditions.
- **Strict Transactional Rollbacks:** Multi-item checkouts are fully atomic (`@Transactional`). If any single item fails (e.g. stock exhaustion), all previous deductions immediately roll back without leaking inventory.
- **Idempotent Checkouts:** Carts are bound to orders upon checkout completion. Concurrent or repeated checkout requests on the same cart safely return the existing order without duplicate charges or double deductions.
- **Cart Mutation Protection:** Once checked out, carts are locked against further item additions, removals, or edits.
- **Nth-Order Rewards & Discount Tracking:** Dynamic discount system that automatically awards a 10% coupon code on every $n$-th order, with real-time discount reporting and admin duplicate code validation (409 Conflict).

---

## ⚡ Quick Start

### 1. Prerequisites
- [Docker](https://www.docker.com/) & [Docker Compose](https://docs.docker.com/compose/)
- [Python 3](https://www.python.org/) with `requests` library (`pip install requests`)

### 2. Start the Stack
Spin up PostgreSQL, the Spring Boot application, and pgAdmin with pre-seeded data from `init.sql`:

```bash
docker compose up --build
```

The application will be accessible at:
- **API Base URL:** `http://localhost:8080`
- **pgAdmin UI:** `http://localhost:5050`

### 3. Access pgAdmin UI & View Database Tables
1. Open your browser and navigate to **[http://localhost:5050](http://localhost:5050)**.
   > **Note:** Login is bypassed automatically. pgAdmin may take 15–30 seconds to initialize on first launch.
2. In the left sidebar object tree, expand:
   - **Servers** ➔ **Postgres (ecommerce_db)** *(connection is pre-authenticated)*
   - **Databases** ➔ **ecommerce_db**
   - **Schemas** ➔ **public** ➔ **Tables**
3. Right-click any table (e.g., `products`, `orders`, `carts`, `discounts`) and select **View/Edit Data** ➔ **All Rows** to view live records.

### 4. Stop the Stack
To stop and clean up containers and database volumes:

```bash
docker compose down -v
```

---

## 🗄️ pgAdmin Management

The stack includes pre-configured **pgAdmin 4** accessible at:
👉 **[http://localhost:5050](http://localhost:5050)**

- **Zero-Login Setup:** Master password and login prompts are disabled (`PGADMIN_CONFIG_SERVER_MODE: "False"`, `PGADMIN_CONFIG_MASTER_PASSWORD_REQUIRED: "False"`).
- **Auto-Configured Server:** PostgreSQL is pre-registered and authenticated automatically via `servers.json` and `pgpass`.
- *Note:* It may take 15–30 seconds on initial startup for the pgAdmin container to initialize.

---

## 🔄 Database Seed & Reset

To reset all tables back to their clean initial seed state without restarting containers:

```bash
./reset_db.sh
```

> **Note:** This script performs `TRUNCATE` on transactional tables and resets inventory quantities to maintain schema integrity, sequence alignment, and persistent OIDs. If viewing tables in pgAdmin, press **F5** (or the Refresh button) to reload the view.

---

## 🧪 Testing & Quality Assurance

All test suites verify production requirements against live running containers.

### 1. End-to-End API Test Suite
Executes a full lifecycle test including cart deletion, a 20-order cycle with rewards generation/redemption, inventory deductions, admin duplicate coupon rejection (HTTP 409), and admin metrics verification.

```bash
python3 test_api.py
```
> ⚠️ **Important:** Run this against a fresh database (or right after `./reset_db.sh`) before issuing manual API requests.

### 2. Transaction Rollback Test
Verifies mid-flight atomic rollback: validates that if a multi-product order fails halfway through (e.g. out of stock on item 2), all earlier deductions roll back completely without leaking stock.

```bash
python3 test_transaction_rollback.py
```

### 3. Concurrency & Pessimistic Locks Test
Simulates high-concurrency race conditions: fires 20 simultaneous threads attempting to check out the last 10 stock units. Verifies that exactly 10 succeed, 10 fail, and final inventory count is cleanly 0 (no overselling or negative inventory).

```bash
python3 test_concurrency_locks.py
```

### 4. Checkout Idempotency Test
Fires simultaneous duplicate checkout requests across parallel threads on the exact same cart. Verifies deduplication: all requests return the same Order ID with a single inventory deduction.

```bash
python3 test_idempotency.py
```

---

## 📮 Postman API Collection

A complete Postman collection is included in the root directory:
- 📁 **`spring-boot-collection.postman_collection.json`**

To use:
1. Open [Postman](https://www.postman.com/).
2. Click **Import** and select `spring-boot-collection.postman_collection.json`.
3. Test endpoints with pre-configured requests, bodies, and variables.

---

## 📖 API Reference

| Endpoint | Method | Description |
| :--- | :---: | :--- |
| `/products` | `GET` | List all catalog products and available inventory |
| `/carts` | `POST` | Create a new shopping cart |
| `/carts/{id}` | `GET` | Get details and items of a specific cart |
| `/carts/{id}/items` | `POST` | Add an item to the cart |
| `/carts/{id}/items/{itemId}` | `DELETE` | Remove an item from the cart |
| `/orders` | `POST` | Check out a cart into an order (accepts coupon code) |
| `/orders/{id}` | `GET` | Retrieve order details and discount applied |
| `/admin/discount/generate` | `POST` | Generate or configure an Nth-order discount coupon |
| `/admin/summary` | `GET` | View system sales metrics, order counts, and total discounts |
