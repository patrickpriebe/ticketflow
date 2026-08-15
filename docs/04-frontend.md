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

## Design system

Everything comes from `src/styles/tokens.css`. **No component writes a hex value** — if
one does, the dark theme never reaches that part of the screen, and that is always how a
dark mode ends up half-finished.

- **Primary `#0052ff`** — actions, links and active states
- **Ink `#121212`** — text and inverted surfaces
- **Accent `#bf3003`** — urgency, sold out, decline. Used sparingly: if it shows up
  everywhere it stops meaning anything
- **Neutral `#f8f9fa`** — section background
- **Inter**, with the system stack as a fallback

The theme has **three states**: light, dark, and "follow the system". Without the third,
whoever chose once is stuck — and most people never go back to the button to fix it. The
default is the third, and in that case the `data-theme` attribute is not in the DOM at
all: `prefers-color-scheme` decides.

In dark mode the palette is not the light one inverted. Pure brand blue on a near-black
background falls below the minimum contrast, so it lightens to `#4d84ff`. And the
background is not pure black, because no shadow is visible over `#000` and the depth
hierarchy disappears with it.

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

**Posters are drawn, not photographed.** The catalogue has no images, and adding an
`imageUrl` field to the contract just so the frontend could look better would be the
tail wagging the dog: with nowhere to host and no upload flow, the field would be born
empty. Each event gets an SVG derived from its own id — always the same face, a varied
grid, zero bytes over the network.

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
final state.

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
