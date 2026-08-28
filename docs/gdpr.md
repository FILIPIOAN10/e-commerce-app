# GDPR: data export and erasure

Two rights, implemented self-service: **Art. 15** (get a copy of your data) and
**Art. 17** (be forgotten). This document is the record of what each one actually
does, and — more importantly — what it deliberately does *not* do.

Code lives in `com.ecommerce.project.service.gdpr`; schema in
`V21__gdpr_export_and_erasure.sql`.

---

## Art. 15 — export

| Step | Endpoint | Notes |
|---|---|---|
| 1. Request | `POST /api/users/gdpr/export` | Authenticated. Records a `gdpr_export` row and publishes `GDPR_EXPORT_REQUESTED` to the outbox. Returns `202`. |
| 2. Build | *(outbox handler)* | `GdprExportHandler` assembles every domain, writes a ZIP into `gdpr_export.payload`, and emails a single-use link. |
| 3. Download | `GET /api/public/gdpr/export/download?token=…` | Public, token-gated. Streams the ZIP, stamps `downloaded_at`. |
| 4. Purge | *(scheduled)* | `GdprExportPurgeJob` drops the bytes once `expires_at` passes; the row survives as `EXPIRED`. |

The archive contains one JSON file per area, plus a `manifest.json`:

```
account.json  addresses.json  orders.json  reviews.json  questions.json
wishlist.json carts.json      notifications.json  activity-log.json
subscriptions.json  returns.json
```

**Why the outbox.** Assembling an account is unbounded work — an old customer
with hundreds of orders should not hold an HTTP thread, and a request lost to a
restart is a legal obligation we silently dropped. The outbox row makes the
request durable and the build retryable; the handler is idempotent, so a
redelivery re-sends the link rather than rebuilding.

**Why one live archive per user.** Requesting again while a build is pending is
answered, not queued — otherwise anyone could make the server assemble their
entire account in a loop. Requesting again while one is *ready* re-sends a link
for the archive already built, which is also how someone recovers a link they
lost.

### Accepted trade-offs

- **The archive sits unencrypted in the database for its TTL (7 days default).**
  It is a complete copy of one person's data behind a signed, single-use,
  purpose-scoped token. The mitigation is retention, not encryption: it exists
  for days, not forever, and the purge job is what keeps that promise. Storing
  it in the database rather than object storage keeps it inside the same backup
  and access story as the data it was built from — one fewer place to secure,
  and one fewer place to forget to purge.
- **The download token is single-use.** A flaky download costs a new link, not
  the archive. That is the price of a link that cannot be replayed from an
  inbox, a proxy log, or a browser history.
- **The link is emailed rather than returned.** The requester must still control
  the mailbox, so a hijacked session alone cannot walk off with the data.

---

## Art. 17 — erasure

Two steps, and both are required:

| Step | Endpoint | Proves |
|---|---|---|
| 1. Request | `POST /api/users/gdpr/erase` with `{"password": "…"}` | You know the password. |
| 2. Confirm | `POST /api/public/gdpr/erase/confirm?token=…` | You control the mailbox. |

The confirmation token lives 60 minutes. Nothing is deleted until it is spent.

An account created through OAuth has no password to re-enter; for those the
emailed link is the only factor — the same assurance the identity provider gives
us at sign-in.

### What is deleted outright

Cart and cart items, abandoned-cart reminders, wishlist, reviews, product
questions, notifications, activity log, recently-viewed (Redis), every session
and refresh token, and any address not attached to a retained order.

Reviews and questions go rather than being anonymised: they carry the customer's
own words, and `reviews.user_id` is `NOT NULL`, so there is nothing to anonymise
them *to*.

### What is anonymised instead

**This is the central compromise, and it is the legally standard one.** Orders,
order lines, payments and invoices are retained — fiscal law requires the
transaction record for years, and Art. 17(3)(b) explicitly carves out processing
required by law. What the law does *not* require is knowing who made the
purchase. So the rows stay and the identifiers go:

| Table | Before | After |
|---|---|---|
| `orders.email` | `ana@example.com` | `deleted-a1b2c3d4e5f6@anonymised.invalid` |
| `addresses.*` (order-linked) | real address | `REDACTED`, `user_id` nulled |
| `payments.pg_response_message` | gateway free text | `''` |
| `return_requests.user_email`, `user_subscriptions.email` | real email | pseudonym address |
| `stock_movement.created_by` | real username | pseudonym |
| `users.username` / `email` | real | pseudonym |
| `users.password` | hash | hash of a random value nobody holds |
| `users.phone` / `avatar_url` / `two_factor_secret` / `provider_id` | set | `NULL` |
| `users.erased` | `false` | `true` (+ `erased_at`) |

Amounts, dates and line items are left intact — detached from a person they are
not personal data, and they are exactly what the books need.

**The user row is a tombstone, not a deletion.** Retained orders hold foreign
keys into it. `erased = true` is what closes the account: `UserDetailsImpl`
reports `isEnabled() == false`, so *every* authentication path — form login,
refresh, OAuth2 — is shut by one flag rather than by a check each of them has to
remember.

**The pseudonym is deterministic** (`SHA-256("gdpr-erasure:" + userId)`,
truncated). Retained orders, returns and subscriptions keep pointing at the same
handle, so accounting can still tell that these records belong together without
being able to tell whose they were. It is not reversible without the user id,
and the user id no longer identifies anybody.

**One transaction.** A half-erased account — cart gone, email still on the
orders — is the only outcome worse than not starting. Erasure is also
idempotent, so a double-submitted confirmation link does not fail at the
customer.

### The audit trail names nobody

`admin_audit_logs` gets a `GDPR_ERASURE` row, and it records only the user id and
the pseudonym. An audit entry naming the person we just promised to forget would
undo the erasure it is meant to evidence.

---

## Configuration

```properties
app.gdpr.export-ttl-days=7
app.gdpr.erasure-token-ttl-minutes=60
app.gdpr.purge-enabled=true
app.gdpr.purge-interval-ms=3600000
```

## Known gaps

- **Admin-initiated erasure.** Support staff cannot run this on a customer's
  behalf; the customer must ask for it themselves while able to sign in. A user
  who has lost access to their account has no route today.
- **Backups.** Erasure rewrites the live database. Existing backups still hold
  the pre-erasure rows until they age out of their own retention window — the
  standard position, but it should be stated in the privacy policy rather than
  left implied.
- **Outgoing email already sent** (order confirmations, invoices attached to
  them) is beyond our reach once delivered.
