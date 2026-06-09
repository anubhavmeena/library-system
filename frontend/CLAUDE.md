# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm install          # install dependencies
npm run dev          # dev server on :3000 (proxies /api → :8080)
npm run build        # production build to dist/
npm run preview      # serve production build locally
```

The Vite dev server proxies all `/api/*` requests to `http://localhost:8080` (the API Gateway). In production, Nginx handles the same proxy — see `nginx.conf`.

## Architecture

### Entry point
`main.jsx` mounts `<Provider store>` → `<BrowserRouter>` → `<App>`. The Redux store is initialized before routing, so `localStorage`-seeded auth state is available on the very first render.

### Route structure (`App.jsx`)
Two protected subtrees, each wrapped in a layout component that renders `<Outlet />`:
- `/student/*` — requires `user.role === "STUDENT"`, rendered inside `StudentLayout`
- `/admin/*` — requires `user.role === "ADMIN"`, rendered inside `AdminLayout`

`ProtectedRoute` reads `state.auth.{token, user}` from Redux. A missing token redirects to `/login`; a wrong role redirects to `/`.

### Layouts
`StudentLayout` and `AdminLayout` are shell components with a fixed sidebar and a mobile hamburger. They own the sidebar open/close state locally (`useState`). `StudentLayout` dispatches `logout()` from `authSlice` on the logout button. Neither layout fetches data.

## Redux Store

Three slices in `src/store/slices/`. All async actions use `createAsyncThunk` pointing to `src/services/api.js`.

### `authSlice`
Bootstrapped from `localStorage` (`token`, `user`) on module load. Owns the full OTP login sequence:
- `otpSent` → `otpVerified` + `sessionToken` + `isNewUser` → JWT issued via `registerUser` or `loginUser`

`loginUser.fulfilled` and `registerUser.fulfilled` write both to Redux state **and** `localStorage`. `logout` clears both.

The Axios 401 interceptor in `api.js` bypasses Redux and directly wipes `localStorage` + hard-redirects — this handles token expiry without needing the slice.

### `membershipSlice`
Owns: `current` (active membership), `plans` (all active plans), `order` (pending Razorpay order).
- `verifyPayment.fulfilled` replaces `current` with the newly activated membership.
- `createPaymentOrder.fulfilled` stores the order response in `order` — used by `BookingPage` to open Razorpay checkout.

### `seatSlice`
Owns: `seats` (full availability array for a shift/date), `selectedSeat` (the seat the user clicked in the grid).
- `selectSeat` / `clearSelectedSeat` are synchronous reducers — no API call.
- `selectedSeat` is written to Redux (not local state) so `BookingPage` can read it in step 3 after navigating between steps.

## Page Patterns

### Redux-connected pages (student)
`BookingPage`, `DashboardPage`, `ProfilePage` read from and dispatch to Redux slices. `BookingPage` coordinates `seatSlice` and `membershipSlice` simultaneously across a 3-step wizard (local `step` state) but keeps the seat selection in Redux.

### Direct-API pages (admin + some student)
All four admin pages (`AdminDashboardPage`, `AdminStudentsPage`, `AdminSeatsPage`, `AdminRemindersPage`) and `MembershipPage`'s history/payment sections use `api.get/post` directly into local `useState`. These are display-only views that don't share data between pages, so Redux is intentionally bypassed.

### Dev mode payment bypass
`BookingPage` detects `order.orderId.startsWith('dev_')` and skips the Razorpay widget entirely, calling `verifyPayment` directly with dummy IDs. This mirrors the backend's `dev_order_*` logic.

## Styling

Tailwind with a custom `primary` color scale (navy blues, `#0d1b4b` background) and `amber` accent. Custom component classes are defined in `src/index.css` via `@layer components`:

| Class | Description |
|---|---|
| `.btn-primary` | Amber filled button with hover lift |
| `.btn-outline` | Ghost border button |
| `.btn-ghost` | Text-only button |
| `.card` | Semi-transparent dark panel with border |
| `.card-hover` | `card` + lift animation on hover |
| `.input` | Dark rounded text input |
| `.label` | Form label |
| `.badge`, `.badge-active`, `.badge-expired`, `.badge-pending` | Status pills |
| `.page-header` | Playfair Display 3xl heading |
| `.section-title` | Playfair Display xl heading |
| `.shimmer` | Animated skeleton loading placeholder |
| `.seat-selected` | Pulsing amber glow on selected seat button |

Font families: `font-display` → Playfair Display (headings), body → DM Sans, `font-mono` → JetBrains Mono.

## API Layer

`src/services/api.js` exports a single Axios instance. All services and slices import from it — never create a second instance.
- Request interceptor: attaches `Authorization: Bearer <token>` from `localStorage` if present.
- Response interceptor: on 401, clears `localStorage` and redirects to `/login`.
- Base URL: `VITE_API_BASE_URL/api` (defaults to empty string in dev, so Vite proxy handles it).

## Seat Grid

`SeatGrid` in `BookingPage.jsx` renders 110 seats across 4 rows (A: 28, B: 28, C: 28, D: 26), split visually into left/right halves per row with an aisle gap. Seat status (`available` / `booked`) is derived from the `seats` array in `seatSlice` by matching `seatNumber`. Clicking an available seat dispatches `selectSeat({ seatNumber, row })` — the `id` field is intentionally absent here because the backend looks up seats by `seatNumber` string.
