# 📋 Architectural & Design Decisions

---

## 🔍 Ambiguities Found and Semantics Selected

* **Who are the users?**  
  We work only with `cart_id` and `order_id` as authentication and authorization are not required.

* **What happens when all items removed from cart?**  
  Cart Deletion is the approach taken.

* **Do you have separate cart creation and add to cart API?**  
  In cart creation, we also pass in an item to add initially.

* **What happens to cart after checkout and order placed?**  
  Cart is deleted.

* **How do you design order and cart tables which can have multiple products?**  
  Separate table for order-product mapping is used. Similarly for cart. Helps for foreign key associations.

* **Prices can change. How do you record order details for bookkeeping?**  
  Order detail has a `purchased_unit_price` column.

---

## ⚙️ Transaction, Concurrency, and Idempotency Strategy

### 1. Transaction Management

A transaction is needed so that an API either performs all the updates or none.

A checkout sequence involves multiple database operations: reducing inventory, creating an order, moving items from `cart_items` to `order_items`, and marking a coupon as used. If the application crashes halfway through, it could end up with inventory deducted but no order created.

* **How Spring Handles It:**  
  Spring provides declarative transaction management via the `@Transactional` annotation.
* **The Implementation:**  
  Place `@Transactional` at the Service layer (e.g., on your `OrderCheckoutService.checkout()` method).

> [!NOTE]
> Hard to simulate Transaction bugs as JPA is very good at preventing it. Just removing `@Transactional` is not sufficient.  
> Run `python3 test_transaction_rollback.py` to run the test for transaction.

---

### 2. Concurrency Control (Prevent Overselling)

* **The Problem:**  
  Imagine only 1 item left in stock. Two different users (User A and User B) click "Checkout" at the exact same time. Without concurrency control, both requests might read "Stock = 1", both proceed, and both place an order—resulting in an oversold inventory bug.

* **How Spring Boot & JPA Handle It:**  
  You handle race conditions using Database Locking. For an e-commerce checkout where inventory counts are critical, Pessimistic Locking is the gold standard.

* **The Implementation:**  
  You tell PostgreSQL to lock the specific inventory row the moment a transaction reads it, forcing other concurrent transactions to wait until the first one finishes.  
  e.g., `@Lock(LockModeType.PESSIMISTIC_WRITE)` is used in `InventoryRepository`.

#### How to Simulate It:
1. Change:
   ```java
   Inventory inventory = inventoryRepository.findByIdForUpdate(productId)
   ```
   to:
   ```java
   Inventory inventory = inventoryRepository.findById(productId)
   ```
   in `OrderCheckoutService.checkout()` method. `findByIdForUpdate` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` which prevents concurrency bugs. `findById` does not use it.
2. Startup Docker Compose (`docker compose up --build`).
3. Once all containers are running, run:
   ```bash
   python3 test_concurrency_locks.py
   ```
   It should fail, simulating the concurrency bug.
4. Now repeat the same without the changes, test should succeed.

---

### 3. Idempotency (Preventing Duplicate Checkouts)

* **The Problem:**  
  Network lag happens. A user clicks "Checkout", the request takes 3 seconds, and the user impatiently clicks the button a second time. This can result in two identical orders being placed and two charges being processed.

#### How to Simulate It:
1. Comment out the `if (cart.getOrderId() != null) {` check in `OrderCheckoutService.checkout()` method. This is an Idempotency check to prevent idempotency-related bugs.
2. Startup Docker Compose (`docker compose up --build`).
3. Once all containers are running, run:
   ```bash
   python3 test_idempotency.py
   ```
   It should fail, simulating the idempotency bug.
4. Now repeat the same without the changes, test should succeed.

---

## 💡 Design Decisions and Alternatives Considered

* **Cart Creation Flow:**  
  We could have separate cart creation and add to cart APIs. But it would add latency, so it is better to pass in an item in create cart.

* **Order and Cart Schemas:**  
  Order and cart tables could have had a column which stored an array of products and quantities. But it would be more complex to do joins on product and inventory tables and slower.

---

## 💰 Money and Rounding Rules

* **Why `BigDecimal` over `float`/`double`?** Floating-point types cause binary precision loss (e.g. `1.00 - 0.90 = 0.09999999999999998`). `BigDecimal` and PostgreSQL `decimal(10,2)` ensure exact monetary calculations.
* **Rounding:** All currency calculations enforce 2 decimal places with `RoundingMode.HALF_UP` (standard commercial rounding) to prevent fractional penny errors.
* **Initialization:** Instantiated via `new BigDecimal("...")` or `BigDecimal.valueOf(...)`, never from raw `double` literals.

---

## ⚖️ What Was Implemented Versus Intentionally Deferred

* **Authentication & Authorization:** User auth was not required, so it was deferred.
* **Inventory Restocking:** APIs to add to inventory were deferred as they were out of scope. Only APIs to view and decrement inventory were added.
* **Database Indexes:** Indexes were deferred as they are not needed for an initial MVP.
* **Product Catalog:** Detailed product metadata/catalog information was deferred.

---

## 🚀 How the Design Would Evolve for Multiple Service Instances and Production Scale

* **State & Concurrency:** All state lives in Postgres. Transaction management prevents atomicity bugs, and pessimistic locking prevents concurrency race conditions. The core architecture will operate safely across multiple service instances.
* **Performance Optimizations:** Under heavy production load, query performance would need enhancements like database indexes on foreign keys, connection pooling tuning, and caching (e.g. Redis).
* **Nth-Order Reward Generation:** Currently evaluated via an order count query. At scale, concurrent orders could trigger duplicate coupon issuance; this would be solved by using a dedicated atomic counter table or Postgres sequence read with locks.

---

## 🤖 How AI Tools Were Used (Redirects and Corrections)

* **Architecture & Boilerplate:** Used AI tools to scaffold boilerplate code after designing the required API contracts and directing AI on the exact endpoints to code.
* **Idempotency Enforcement:** AI did not add idempotency checks initially; I redirected it by specifying the exact logic and checkout checks to prevent duplicate orders on repeated cart requests.
* **Verification:** Audited all generated code to verify `@Transactional` boundaries and pessimistic row locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`).
* **Automated Bug Simulation:** Directed AI to generate automated test scripts to validate transaction rollback, concurrency, and idempotency. I also verified these by simulating the bugs directly (removing locks/checks) to confirm the tests successfully caught the failures.

---

## ⏱️ What to Examine First If Given Another Two Hours

* Run exploratory end-to-end edge-case testing against all service APIs to identify unhandled corner cases.
* Write additional granular integration tests focused on negative scenarios (e.g., malformed payloads, non-existent coupon IDs, extreme cart quantities).
* Profile slow queries and add optimal database indexing on foreign keys and lookup columns.
