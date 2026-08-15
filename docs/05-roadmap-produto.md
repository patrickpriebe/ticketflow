# Roadmap — product and frontend

What exists today covers the whole path: discover, choose, pay, receive the ticket and
follow the order. What is listed here is what **does not exist yet**, organised by how
much it changes the system — not by how long it takes.

Every item states what it demands from the backend. That matters: half of the pretty
frontend ideas die the moment somebody discovers they need a new table.

---

## Level 1 — Closing gaps in what is already built

Things the system almost does, left out by a conscious choice.

### Pagination and sorting in the catalogue
The endpoint accepts `page` and `size`; the frontend asks for one page and stops there.
With nine events it makes no difference. It starts to matter the moment the catalogue
grows — and the symptom is treacherous, because the screen does not break: it simply
stops showing things.
**Backend:** nothing. **Frontend:** pagination or infinite scroll, plus sorting by date
or price.

### Server-side search
Today the text filters the already-loaded page. That is honest while everything fits in
one page, and stops being honest a minute later.
**Backend:** a `q` parameter on `GET /events`, in the same shape as the city filter. To
be genuinely worth it, a `pg_trgm` index in PostgreSQL.

### Event genre and image
Filtering by "concert", "theatre" or "sports" is the first thing anyone tries. And the
generated poster solves the problem of having no image, but it does not replace a photo
of the artist.
**Backend:** `category` and `image_url` columns on `events`, new fields in the contract.
**Frontend:** a genre filter and an `<img>` with the poster as a fallback.

### Field-level errors
`ProblemDetail` already carries `errors[]` when validation fails. The frontend only
shows the general text. With two fields in a form nobody notices; with a real form,
everybody does.

### Cancelling an order
`CANCELLED` exists as a status and there is no way to reach it from the screen. It is
the most requested button in any purchase system.
**Backend:** `POST /orders/{id}/cancel`, releasing inventory in the same transaction and
publishing the event. The hard part is not the endpoint — it is the race where a
cancellation and an approval cross, which is a compensation problem (refund), not a
locking one.

---

## Level 2 — Changing what the product can do

### A cart across events
Today the cart holds one event, because `POST /orders` holds one event. Buying tickets
for two shows in the same purchase means several orders in one transaction — or one
order with several events, which changes the whole model. Worth discussing before
implementing: most ticketing sites do **not** do this, and not out of laziness.

### Numbered seats
The most expensive jump on the list. "Available quantity" stops existing and a seat map
takes its place, with reservation of a specific seat and the classic problem of two
people clicking the same chair in the same millisecond.
**Backend:** a `seats` table, per-seat locking, the map in the contract.
**Frontend:** an interactive map — in SVG, which handles thousands of elements better
than the DOM.

### Coupons and concession tickets
Price stops coming straight from the catalogue and starts going through a rule. The care
needed is to never let the discount be computed on the client: the charged amount must
still come from the server, otherwise the customer chooses how much to pay.

### A waiting room for high-demand events
For launches where ten thousand people click in the same second. A queue with a visible
position, a turn token and a per-person purchase window.
**Backend:** Redis for the queue; it is the first case where a new piece of
infrastructure genuinely justifies itself.

### Refunds
A refund is a payment flow of its own, with its own states and the same idempotency
requirement as the charge. It is where `PaymentStrategy` shows whether it was well
designed: every method refunds differently.

---

## Level 3 — Experience

### Push updates instead of polling
Today the order screen polls the API every two seconds. It works and it is enough, but
SSE in the Order Service would remove both the interval and the wait between a payment
being approved and the screen noticing.
**Backend:** an SSE endpoint fed by the payment-result consumer.

### Tickets in Apple Wallet and Google Wallet
The ticket already has a code, a holder and an event. What is missing is the signed
`.pkpass`. It is the kind of detail that makes a project look like a product rather than
an exercise.

### PWA and offline use
The ticket has to open at the venue door, where mobile signal is always bad. A service
worker caching issued tickets solves the worst possible moment to lose the network.

### Real email
The Notification Service already builds the notification and records it. What is missing
is the provider.

### Audited accessibility
The groundwork is done — visible focus, large targets, accessible names,
`prefers-reduced-motion`, contrast checked in both themes. What is missing is what only
shows up in real testing: going through the entire purchase with a screen reader and
with the keyboard alone.

### Internationalisation
The interface text is Portuguese, inline in the code. Before translating, extract — and
then decide whether it is worth it. Currency and dates already go through `Intl`, which
is the hard half.

---

## Level 4 — Platform

- **Organiser panel**: create an event, define price tiers, follow sales. It is
  practically a second product, with role-based authorisation.
- **Gate validation**: an app that reads the QR code and marks the ticket as used, with
  idempotency — the same ticket does not get in twice.
- **Reports**: revenue per event, decline rate per payment method, time between order and
  approval. The data already exists; what is missing is the reading side.
- **End-to-end frontend tests**: Playwright walking through the purchase against the
  compose stack. The screenshot script in `scripts/screenshots/` is already most of the
  way there — it drives the real purchase flow and fails if the pipeline breaks. Turning
  it into an assertion suite is a small step.

---

## What stays out, and why

**A server-side cart.** Holding seats for an indefinite time for everyone who opened the
page looks like care and turns into stuck inventory.

**Microfrontends.** There are seven screens.

**A third-party component library.** The design system here is four CSS files and no
runtime. Pulling in a whole library to reuse a button and a modal would cost more than
the two components.

**GraphQL.** The REST contract is the source of truth and it is well designed. Switching
for the sake of switching would bring the N+1 problem and the caching problem with no
real pain relieved in return.
