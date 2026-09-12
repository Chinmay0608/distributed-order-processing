# Developer Onboarding & Verification Guide — 7-Day Hands-On Track

Goal: by Day 7, you will have verified, load-tested, and extended every critical path in this
distributed architecture. Each day has a **concept focus**, a **codebase inspection task**, and a
**hands-on verification exercise** to build operational fluency. Budget roughly 1–2 hours/day.

---

## Day 1 — Trace the Request, End to End

**Concept focus:** What actually happens, in order, when someone clicks "Place Order."

**Reading task:**
Open `OrderController.java` → `OrderService.java` in that order. Do NOT read
`InventoryLockService.java` or `IdempotencyService.java` yet — just get the shape of
the main flow first. Write down, in your own words (paper or a notes app), the 10
steps as you understand them, before checking against `HLD.md` Section 7.

**Hands-on exercise:**
1. Run the whole stack (`docker-compose up`).
2. Open your browser's Network tab (F12 → Network).
3. Place a single order through the frontend.
4. Find the `POST /api/orders` request in the Network tab. Look at:
   - The exact request headers (find `Idempotency-Key`)
   - The exact request body
   - The exact response body and status code
5. Now do it again with the **same** Idempotency-Key (don't click "New UUID"). Confirm
   you get back the exact same order ID as a cached response.

**Checkpoint question (answer out loud, no notes):**
*"What are the first three things that happen after the controller receives the
request, before any database is touched?"*

---

## Day 2 — The Lock, In Isolation

**Concept focus:** What a distributed lock actually is, mechanically — not the theory,
the literal mechanism.

**Reading task:**
Open `InventoryLockService.java`. Read every line. For each line, ask "what would
happen if this line didn't exist?" Also open `docker-compose.yml` and find the Redis
service definition — note the port it exposes.

**Hands-on exercise:**
1. With the stack running, connect to Redis directly:
   ```bash
   docker exec -it <redis-container-name> redis-cli
   ```
2. Run `KEYS *` — you'll likely see nothing (locks are short-lived and self-clean).
3. In one terminal, keep running `KEYS lock:*` in a fast loop while, in your browser,
   you place an order for a low-stock item. Try to actually *catch* the lock key
   existing for a brief moment:
   ```bash
   while true; do docker exec <redis-container-name> redis-cli KEYS "lock:*"; sleep 0.05; done
   ```
4. Now manually create a fake lock yourself in `redis-cli`:
   ```
   SET lock:inventory:FAKE_ID "manual-lock" NX PX 30000
   GET lock:inventory:FAKE_ID
   TTL lock:inventory:FAKE_ID
   ```
   Watch the TTL count down. This is *exactly* what Redisson does under the hood.

**Checkpoint question:**
*"If I manually set a lock like above and never delete it, what happens to real orders
for that product ID within the next 30 seconds? What happens after?"*

---

## Day 3 — Break It On Purpose (The Most Important Day)

**Concept focus:** Proving to yourself, empirically, that the lock is load-bearing —
not decorative.

**Hands-on exercise (do this exactly in order):**
1. Make a copy of `InventoryLockService.java` somewhere safe (or just use git — commit
   current working state first: `git add -A && git commit -m "before breaking lock"`).
2. Modify `InventoryLockService.java` so `acquireLock()` always returns `true`
   immediately, without ever actually calling Redisson's `tryLock()`. Effectively:
   the lock is now a no-op.
3. Re-run `OrderServiceConcurrencyTest.java`.
4. **Watch it fail.** Read the failure output carefully — how many orders got
   created? What's the final stock value? Is it negative? Is it more than 1 order
   for 1 unit of stock?
5. Revert your change (`git checkout -- InventoryLockService.java` or restore from
   your copy). Re-run the test. Confirm it passes again.

**Write down (system behavior under contention, from direct observation):**
- What exact number of orders got created when the lock was disabled?
- What was the final stock value — negative, zero, or something else?
- In one sentence: why did removing ONLY the lock (keeping the Mongo atomic check)
  still cause a problem, if Mongo's atomic check is supposed to be a safety net?

*(Hint for that last one: think about the gap between when a thread reads stock
and when it writes the decrement — without the lock serializing access, are you sure
your Mongo query is actually using an atomic `find-and-update`, or could it be doing
a separate read-then-write? Check the actual code in the decrement step and confirm
which one it is.)*

---

## Day 4 — Watch the Async Pipeline With Your Own Eyes

**Concept focus:** Kafka isn't magic — it's just messages sitting in a queue that
something else reads later. Prove this to yourself directly.

**Reading task:**
Open `PaymentConsumer.java` → `PaymentProcessor.java` → `ShippingConsumer.java` →
`ShippingProcessor.java`. Note the topic names each one produces/consumes.

**Hands-on exercise:**
1. Get a shell into your Kafka container:
   ```bash
   docker exec -it <kafka-container-name> bash
   ```
2. Watch a topic live, raw:
   ```bash
   kafka-console-consumer --bootstrap-server localhost:9092 \
     --topic order-placed --from-beginning
   ```
3. In another terminal/browser, place a new order. Watch the raw JSON event appear
   in your consumer terminal in real time.
4. Repeat for `order-payment-processed` and `order-shipped` — watch the same order
   flow through all three topics over ~3 seconds.
5. Now place an order that triggers the deterministic payment failure (check
   `PaymentProcessor.java` for the exact trigger — likely a specific product ID or
   `quantity == 99`). Watch `order-payment-failed` fire instead of
   `order-payment-processed`.

**Checkpoint question:**
*"If I killed the Processing Service right after Kafka received the `order-placed`
event, but before Payment Consumer processed it, what would happen to that order?
Would it be lost? Why or why not?"*
(Think about what Kafka guarantees about message persistence vs. what happens if a
consumer isn't running.)

---

## Day 5 — The Compensation Saga, Live

**Concept focus:** How the system "undoes" a mistake without a traditional database
transaction spanning multiple services.

**Reading task:**
Open `CompensationConsumer.java` → `InventoryCompensationService.java`. Compare this
lock-acquire/release pattern to `InventoryLockService.java` from Day 2 — notice it's
the *same* lock key format, reused for a different purpose (restoring stock instead
of reserving it).

**Hands-on exercise:**
1. Note a product's current stock (e.g. the "Simulated Payment Fail Item").
2. Place an order that triggers the deterministic payment failure.
3. Watch (in Product List / by refreshing) the stock count: it should first drop
   (reserved), then — after the compensation runs — go back up to its original value.
4. Check the order's `statusHistory` via `GET /api/orders/{orderId}` — confirm you can
   see: `PLACED → FAILED → (compensation entry)`.
5. Now do this same order TWICE in quick succession (two different Idempotency-Keys,
   same failing product) — watch that both compensations correctly restore stock
   without interfering with each other (this proves the lock is doing its job here too).

**Checkpoint question:**
*"Why does the compensation step need to acquire the same lock key as a normal order
placement, instead of just directly incrementing stock without any lock at all?"*

---

## Day 6 — Extend It Yourself (No AI Help)

**Concept focus:** Can you actually build within this architecture, not just read it?

**Task:** Implement a `POST /api/orders/{orderId}/cancel` endpoint, entirely by
yourself — no Antigravity, no asking me for code. You're allowed to reference the
existing code as a pattern, but you write every line yourself.

**Requirements for your implementation:**
1. Only allow cancellation if the order's current status is `PLACED` or
   `PAYMENT_PROCESSED` (not `SHIPPED` — can't cancel a shipped order in this design).
2. Must acquire the same `lock:inventory:{productId}` lock before touching stock.
3. Must restore the reserved quantity back to the product's stock atomically.
4. Must update the order's status to `CANCELLED` and append a `statusHistory` entry.
5. Must release the lock in a `finally` block, exactly like the existing code does.

**If you get stuck:** re-read `InventoryCompensationService.java` from Day 5 — your
cancel logic will look almost identical to the compensation logic, which is itself a
useful realization (cancellation and payment-failure compensation are the *same
underlying operation* — releasing reserved stock — triggered by different events).

**Checkpoint:** write a quick manual test — place an order, cancel it, confirm stock
is restored and status is `CANCELLED`.

---

## Day 7 — Architectural Defense & System Review

**Concept focus:** Fluently synthesize system behavior, concurrency guarantees, and trade-offs.

**Exercise (system validation checklist):**
1. **Architecture Walkthrough:** Sketch or describe the complete architecture from memory — Order
   Service, Processing Service, Redis, MongoDB, Kafka, and the end-to-end data flow.
2. **Failure Modes & Trade-offs:** Review the core edge cases:
   - Why Redis lock AND Mongo atomic check, not just one?
   - What happens if Redis goes down mid-transaction?
   - Why Kafka instead of Order Service calling Payment Service directly?
   - What is a compensation saga, and where does one execute in this system?
   - Production readiness: rate limiting and backpressure controls needed for burst loads.

**Final checkpoint:** You now possess end-to-end command over the system's concurrency guarantees,
failure recovery mechanisms, asynchronous event streaming pipeline, and operational trade-offs.

---

## A Note on Pacing

If any single day takes you 2-3 hours instead of 1-2, that's fine — depth matters more
than speed here. If Day 3 (breaking the lock) or Day 6 (building cancel yourself) feels
hard, that's actually the signal you're at the edge of real understanding, which is
exactly where you want to be pushing.
