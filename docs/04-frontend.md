# Frontend

The TicketFlow frontend exists for one specific reason: a distributed system only
demonstrates what it can do when somebody can *see* the immediate response, the order
changing status on its own, and the ticket appearing. Without a screen, all of that is
just terminal output.

It consumes three APIs — Order, Notification and one authenticated read on Payment.
That last one exists only so Stripe Elements can confirm a card; everything else the
Payment Service does is driven by events.

## Stack

| Piece | Choice | Why |
|---|---|---|
| Build | Vite | Fast dev server and a built-in proxy |
| UI | React 18 + strict TypeScript | Types mirror the OpenAPI contract |
| Styling | CSS custom properties | Light/dark comes for free; zero runtime |
| Type | Self-hosted woff2, one set per skin | No CDN and no third party in the render path |
| Routing | Own router (~60 lines) | See [Decisions](#decisions) |
| State | `useState` + `sessionStorage` | There is not enough global state to justify a library |

No dependency beyond `react` and `react-dom`.

## Screens

```
/                    Home: search hero, highlights, upcoming events
/events              Discover: filters by city, price and text
/events/:id          Event: description, venue and ticket selector
/checkout            Payment: method and summary
/orders              My orders, filtered by status
/orders/:id          Order: stub, deadline, tickets and timeline
/signin              Sign in
```

### Sign in

The screen has two columns: a dark panel carrying the hero artwork, which answers "what
happens after I sign in", and the card with the sign-in mechanism.

The mechanism depends on whether a provider is configured, and it is the only difference
between the two environments:

- **`VITE_GOOGLE_CLIENT_ID` set** — the Google button. The provider returns a signed ID
  token straight to the browser, which sends it in `Authorization`. No code exchange, no
  session and no cookie: the services stay pure resource servers. The token lasts an
  hour and there is no refresh token — after that the API answers 401, the frontend
  drops the session and the person signs in again.
- **No client id** — the development form, which talks to a local issuer that verifies
  nothing. It is what allows the whole project to run without an account at any
  provider. That issuer is turned off in every published environment.

The frontend reads the token's claims to write the name on screen, **and only for that**.
Anyone can build a JWT with any name in it; the part that cannot be forged is the
signature, and checking signatures is the job of whoever holds the data.

## Four skins, chosen by the visitor

The site ships **four complete designs**, and a control switches between them live, with
no reload:

| Skin | What it is |
|---|---|
| `boxoffice` | Default. Bone paper, brass, signage lettering, event card shaped like a ticket with a tear-off stub |
| `stage` | 1a "Palco". Geist, big imagery, every number in Geist Mono, amber reserved for urgency alone |
| `poster` | 1b "Cartaz". Instrument Serif headings, warm near-white paper, hairline instead of box — the catalogue reads as an index |
| `classic` | The original design: brand blue, Inter, rounded cards |

`stage` and `poster` come from the two directions in the *TicketFlow Redesign* canvas.
They are redesigns of the original layout — same routes, components remade — so they
inherit its markup and change only their clothes.

The control sits in the header above 1140px and in the footer always. The footer alone was
where it started, and that was wrong: a preference nobody can find without scrolling to the
bottom of the page is a preference nobody has. It is a `<select>` rather than a row of
buttons — with two skins the buttons fitted, with four they want some 380px, which the
header line does not have.

Because it renders twice, the component holds **no state of its own**. With a `useState` per
instance, clicking the one in the header changed the whole site while the one in the footer
went on showing the previous choice — two controls disagreeing. Both read `useSkin()`, which
is the same attribute the CSS reads, so the copies cannot drift from each other or from the
screen.

### How the four share one stylesheet

All skins share the **token names**, so a file of values is most of a skin. Beyond that:

- `skins/flat.css` holds the rectangular layout — hero, pitch strip, card with a calendar
  badge — shared by `classic`, `stage` and `poster`. It is scoped
  `:not([data-skin='boxoffice'])` rather than a list of skins, so a new flat skin works
  without touching it; forgetting to add it to a list would be a screen with no styling at all.
- `skins/classic.css`, `skins/stage.css`, `skins/poster.css` hold each skin's tokens and
  the handful of decisions no token carries.

**Specificity is the trap here.** `flat.css` selectors carry two attributes, so
`[data-skin]:not([data-skin='boxoffice']) h1` scores (0,3,0). A per-skin override written
as `[data-skin='poster'] h1` scores (0,2,0) and loses **silently** — that is exactly how
the Cartaz headings stayed in the grotesque and its date badge kept showing on a grid that
is not supposed to have one. Per-skin overrides are written `:root[data-skin='…']` to tie,
and import order decides.

The same trap bit the responsive layout from the other side: `flat.css` set
`.discover` and `.trending` to multi-column at (0,3,0), which outranks the
`@media (max-width: 1000px)` collapse in `views.css` at (0,1,0). On a phone the filter rail
stayed 240px wide and the whole page scrolled sideways. Those grids now sit inside a
`min-width` guard.

Exactly **two** places change markup rather than styling, and both branch in React on
`usesFlatLayout()`: the event card and the top of the home page. The generative poster
palette follows the skin too — a box office poster inside the blue rounded card, or a blue
one on warm paper, does not look like the same site.

## Design system

Everything comes from `src/styles/tokens.css`. **No component writes a hex value** — if
one does, the dark theme never reaches that part of the screen, and that is always how a
dark mode ends up half-finished.

The direction is a **box office**: bone paper, near-black ink, brass fittings, and the
red of the curtain kept for the things that go wrong. There is no product blue anywhere,
because the subject is a theatre and a stadium, not a dashboard.

- **Brass** — the hardware. Two values, not one: `--brand-500` fills buttons and markers,
  and `--brand-ink` is the darker tone for whenever brass has to be *text* on paper. One
  value cannot serve both sides: the shade light enough for ink to sit on top of fails as
  text on the page, and the compromise fails at both ends
- **Curtain `--accent-500`** — urgency, sold out, decline, cancellation. Used sparingly:
  if it shows up everywhere it stops meaning anything
- **Paper** — the ground is bone with a green cast, and a card is *lighter* than it. Cards
  rise by luminance, not by shadow. Shadow is left for the few things that genuinely
  float: a sticky panel, the header, the order stub
- **Ink rules** — the header, the section heads and the totals close with a 1px line at
  full text strength. That line, not a border radius, is what separates things here

Three typefaces, one job each:

- **Big Shoulders Display** for headings and event names. It was drawn for street
  signage — narrow, tall, meant to be read from across a road. It is the marquee lettering
- **IBM Plex Sans** for running text
- **IBM Plex Mono** for every number — price, time, countdown, ticket code, order id,
  quantity — and for the small uppercase labels. Close to half of a box office screen is
  numbers, and a column of tabular figures is easier to compare than proportional ones

The files are served by the site itself, not by a font CDN. It is the same decision
already taken for the venue photographs: `font-src 'self'`, and a page that changes
typeface because a third party went down is a failure that need not exist. Only the two
`latin` subsets are preloaded; `latin-ext` is declared and fetched only if some character
needs it.

The theme has **three states**: light, dark, and "follow the system". Without the third,
whoever chose once is stuck — and most people never go back to the button to fix it. The
default is the third, and in that case the `data-theme` attribute is not in the DOM at
all: `prefers-color-scheme` decides.

In dark mode the palette is not the light one inverted. Brass has to lighten so that ink
still reads on top of it, and the background is not pure black, because no shadow is
visible over `#000` and the depth hierarchy disappears with it.

Four tokens deliberately do **not** follow the theme: `--marquee`, `--marquee-text`,
`--marquee-dim` and `--marquee-rule`. The panel at the top of the home page, the event
header and the sign-in artwork are dark in both themes, because a marquee is dark and a
photograph goes on top of it. A marquee that lightens with the theme is white text on a
white ground for half the visits. Same case as the white backing of the QR code: fixed
context, fixed colour.

## The signature: a card is a ticket

An event card is not a rounded rectangle with a photo in it. It is a ticket — body on the
left, tear-off stub on the right, a dashed perforation between them and a punched notch at
each end. The stub carries the date and nothing else: day, month, and the weekday printed
along the counterfoil, the way a real one prints it. It is `aria-hidden`, because the full
date is already written out in the body and a screen reader should not hear it twice.

The shape already existed in this project. It was locked inside the order screen, where
only somebody who had already bought could ever see it. Promoting it to the unit the whole
catalogue is built from costs nothing, and it is the one thing on the page that could not
belong to any other product.

The notch is a full circle with a border, offset half its own width outside the card;
`overflow: hidden` eats the outer half and what remains is an arc — which is what makes a
hole look like a hole. Without the border the bite is invisible: the page, the card and
the stub are three neighbouring tones of bone on purpose, and three neighbouring tones
draw no edge at all. Because the notch has to be the colour of whatever sits *behind* the
card, it reads a `--ground` custom property that any section with a different background
can override.

## Decisions

**Own router instead of React Router.** It is not about saving a dependency: a frontend
this size uses a fraction of the library, and the fraction it uses fits in fifty
readable lines over the History API. What does not fit — nested routes, per-route code
splitting, guards — is not needed here either. The non-negotiable part was having real
URLs: a working back button, an order openable by direct link, and a reload that does
not land on the home page.

**The cart lives in the client, not the server.** Nothing is reserved while the person
is choosing. The reservation happens in `POST /orders`, in a single transaction. A
server-side cart would have to hold seats for an indefinite time for everyone who opened
the page — the kind of thing that looks like care and turns into stuck inventory. It
lives in `sessionStorage` so it survives a refresh mid-checkout.

**Real photographs of the venues, with a drawn poster as the fallback.** The events are
fictional, so there is no photo of *that* show — but the venues they name are real
places, and those are photographable. The images are freely licensed shots from Wikimedia
Commons, served by the site itself rather than hotlinked: the CSP closes `img-src` to
`'self'`, and a card losing its image because a third party went down is a failure that
need not exist.

Two rules decide which photo is usable. The Creative Commons licence settles the
photographer's rights, and the credit line on the event page pays that. **It does not
settle the rights of people who appear in the frame** — that belongs to each person and
is not resolved by attribution. So an empty venue qualifies and a shot full of faces does
not; where only crowd photos existed, the event keeps the drawn poster.

The generative SVG stayed for exactly that reason, plus one more: any event added after
the seed has no photo, and it is also what covers a file that fails to load.

**Card data is collected by Stripe, not by us.** The card field is an iframe belonging to
Stripe; the number goes straight to them. Our code only ever touches the `client_secret`,
which authorises that one charge and nothing else. The system stores only the brand and
the last four digits.

**City filtering on the backend, price and text on the client.** City is a real catalogue
parameter. Price and text are filtered over the loaded page, because the endpoint has no
such parameters. With the current catalogue everything fits in one page; when it stops
fitting, client-side filtering starts lying — and the right answer then is to move both
to the backend, not to paginate more cleverly in the frontend.

**Polling, not WebSocket.** It is what the contract defines: `POST` answers `202` and the
client polls `GET /orders/{id}`. The interval stops on its own once the order reaches a
final state — and pauses while the tab is hidden. An abandoned order sits `PENDING` for
fifteen minutes; at two seconds a request that is some 450 calls nobody will read, which
on a free instance is CPU taken from someone actually using the site. Returning to the
tab polls immediately, so nobody sees a stale status.

**Cancelling asks for confirmation, and treats `409` as success.** The button only exists
while the order is `PENDING` — a paid order is refunded, which is a different flow. The
confirmation is there because the action is irreversible and the tickets may be gone by
the time the person changes their mind. A `409` means the order had already finished,
almost always because the payment landed while they were deciding; showing an error there
would blame the customer for a race inside the system, so the screen just refreshes into
the new state.

**The cancelled order says what happened to the money.** Reading the status alone would
tell someone who was charged that nothing was charged: when a cancellation crosses a
charge in flight, the money goes out and comes back and the payment stays `CANCELLED` —
because that is what happened to the *order*. The screen asks the Payment Service
directly and distinguishes three outcomes: nothing was charged, the amount came back, or
the refund is still in flight. The proof is `refund_id`, exposed to the browser as a
boolean; the receipt decides, not the label.

## Running

```bash
npm install --prefix frontend
npm run dev --prefix frontend
```

Vite proxies `/api/v1/tickets` to the Notification Service (8083), `/api/v1/payments` to
the Payment Service (8082), and the rest of `/api` to the Order Service (8081). Rule
order matters: the most specific first, otherwise `/api` swallows everything.

A proxy instead of CORS on the backend because, in production, the frontend is served
from the same origin — and opening CORS only for the development environment is the kind
of configuration that leaks into production by being forgotten.

## What comes next

See [05-roadmap-produto.md](05-roadmap-produto.md).
