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

## 💡 Design Decisions and Alternatives Considered

* **Cart Creation Flow:**  
  We could have separate cart creation and add to cart APIs. But it would add latency, so it is better to pass in an item in create cart.

* **Order and Cart Schemas:**  
  Order and cart tables could have had a column which stored an array of products and quantities. But it would be more complex to do joins on product and inventory tables and slower.

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
