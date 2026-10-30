`Ambiguities found and Semantics selected`:
    * Who are the users? We work only with cart_id and order_id as authentication and authorization is not required.
    * What happens when all items removed from cart? Cart Deletion is approach taken.
    * Do you have seperate cart creation and add to cart api? In cart creation we also pass in an item to add
        initially.
    * What happens to cart after checkout and order placed? Cart is deleted.
    * How do you design order and cart tables which can have multiple products? Seperate table for order product mapping
        is used. Similarly for cart. Helps for foreign key associations.
    * Prices can change. How do you record order details for book keeping? Order detail has a purchased_unit_price column.

`Design decision and Alternatives considered`:
    * We could have seperate cart creation and add to cart api. But it would add latency so better to pass in an item
        in create cart.
    * order and card tables could have had a column which stored an array of products and quantities. But it would be
        more complex to do joins on product and inventory tables and slower.

`Transaction, Concurrency, and Idempotency strategy`:
    * Transaction is needed so that an API either performs all the updates or none.
      A checkout sequence involves multiple database operations: reducing inventory, creating an order,
      moving items from cart_items to order_items, and marking a coupon as used. If application crashes
      halfway through, it could end up with inventory deducted but no order created.

      How Spring Handles It: Spring provides declarative transaction management via the @Transactional annotation.
      The Implementation: place @Transactional at the Service layer (e.g., on your OrderCheckoutService.checkout() method).

      Hard to simulate Transaction bugs as JPA is very good at preventing it. Just removing @Transaction is not sufficient.
      Run `python3 test_transaction_rollback.py` to run the test for transaction.

    * Concurrency Control(Prevent Overselling):
      The Problem: Imagine only 1 item left in stock. Two different users (User A and User B) click
      "Checkout" at the exact same time. Without concurrency control, both requests might read "Stock = 1",
      both proceed, and both place an order—resulting in an oversold inventory bug.

      How Spring Boot & JPA Handle It: You handle race conditions using Database Locking. For an e-commerce
      checkout where inventory counts are critical, Pessimistic Locking is the gold standard.

      The Implementation: You tell PostgreSQL to lock the specific inventory row the moment a transaction reads
      it, forcing other concurrent transactions to wait until the first one finishes.

      e.g. @Lock(LockModeType.PESSIMISTIC_WRITE) is used in InventoryRepository.

      HOW TO SIMULATE IT:
        * Change `Inventory inventory = inventoryRepository.findByIdForUpdate(productId)` to `Inventory inventory = inventoryRepository.findById(productId)`
          in OrderCheckoutService.checkout() method. findByIdForUpdate uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` which prevents concurrency bugs.
          `findById` does not use it. Next startup docker compose. Once all containers are running, run `python3 test_concurrency_locks.py`. It should fail,
          simulating the concurrency bug. Now repeat the same without the changes, test should succeed.

    * Idempotency (Preventing Duplicate Checkouts):
      The Problem: Network lag happens. A user clicks "Checkout", the request takes 3 seconds, and the user impatiently
      clicks the button a second time. This can result in two identical orders being placed and two charges being processed.

      HOW TO SIMULATE IT:
        * Comment out `if (cart.getOrderId() != null) {` check in OrderCheckoutService.checkout() method. This is an Idempotency check to prevent idempotency
          related bugs. Next startup docker compose. Once all containers are running, run `python3 test_idempotency.py`. It should fail,
          simulating the idempotency bug. Now repeat the same without the changes, test should succeed.