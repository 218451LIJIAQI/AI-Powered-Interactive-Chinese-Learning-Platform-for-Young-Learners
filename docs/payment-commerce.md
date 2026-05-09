# Phase 9 Payment And Commerce

## Overview
Phase 9 adds commerce workflows for cart, buy now, checkout, payment records, subscriptions, entitlements, teacher earnings, commission, payouts, refunds, notifications, and audit logs.

## Payment Provider Configuration
Default provider:
- `PAYMENT_PROVIDER=stripe`

Safe backend-only environment variables:

```env
PAYMENT_PROVIDER=stripe
STRIPE_SECRET_KEY=replace-with-your-stripe-test-secret-key
STRIPE_PUBLIC_KEY=replace-with-your-stripe-test-public-key
STRIPE_WEBHOOK_SECRET=replace-with-your-stripe-webhook-secret
PAYMENT_SUCCESS_URL=http://localhost:5173/payment/success
PAYMENT_CANCEL_URL=http://localhost:5173/payment/cancel
PAYMENT_CURRENCY=MYR
PAYMENT_ENABLE_REAL_PROVIDER=false
PAYMENT_TIMEOUT_SECONDS=30
PAYMENT_MOCK_SUCCESS_ENABLED=true
```

Rules:
- Never place `STRIPE_SECRET_KEY` or `STRIPE_WEBHOOK_SECRET` in frontend files.
- Never commit real Stripe keys.
- The frontend only receives checkout URLs or safe payment status responses.

## Missing Key Behavior
- The backend still boots if Stripe keys are missing.
- If `PAYMENT_ENABLE_REAL_PROVIDER=false`, commerce uses mock or unavailable provider mode.
- If real Stripe checkout is disabled or keys are missing, checkout returns a friendly unavailable response instead of crashing.

## Cart Flow
- Student adds a paid item to `/api/v1/students/cart/items`.
- Backend resolves the item from database content and stores quantity `1`.
- Duplicate paid items are rejected.
- Free items are not added to the paid cart.

## Buy Now And Checkout Flow
- Buy now uses `POST /api/v1/students/checkout/buy-now`.
- Cart checkout uses `POST /api/v1/students/checkout/cart`.
- Subscription checkout uses `POST /api/v1/students/subscription/checkout`.
- Backend calculates totals using `BigDecimal` and ignores frontend price values.
- A local order and pending payment record are created before provider checkout starts.

## Payment Status Flow
- Student payment status uses `GET /api/v1/payments/status/{paymentId}`.
- This route requires authenticated `STUDENT` access.
- The backend checks that the payment belongs to the authenticated student.
- Admin payment listing uses `GET /api/v1/admin/payments`.

## Success And Cancel URLs
- Default success URL: `/payment/success?paymentId=<id>`
- Default cancel URL: `/payment/cancel?paymentId=<id>&cancelled=true`
- Frontend routes render the shared payment result page.
- Reaching the success page alone does not mark payment successful.
- The frontend always fetches backend payment status before showing the final result.

## Subscription Plans
- Public/student plan listing is available through active plans only.
- Admin can create, update, activate, and deactivate plans.
- Free plans can be supported without real Stripe checkout.
- The current prototype uses a simple 30-day subscription window after activation.

## Paid Access And Entitlements
- Payment success creates `payment_entitlements`.
- Entitlements unlock paid lessons, modules, packages, premium AI access structure, and subscriptions where applicable.
- Package purchases can expand into included module or lesson entitlements.
- Refund completion revokes related entitlements where practical.

## Premium AI Access Structure
- No new AI features are added in Phase 9.
- Phase 9 only adds the access structure for premium AI purchase or entitlement checks.
- The default direct-purchase prototype price is `RM 19.90`, defined in backend code as a named constant.

## Teacher Earnings And Commission
- Teacher earnings are created after successful payment for teacher-owned paid content.
- Commission uses a configurable system setting and `BigDecimal`.
- Default currency is `MYR`, displayed as `RM` in the frontend.
- Existing historical earnings are not recalculated automatically when commission changes.

## Payout Flow
- Teacher requests payout through `/api/v1/teachers/payout-requests`.
- Admin approves, rejects, or marks paid through `/api/v1/admin/payout-requests/...`.
- No real bank transfer is performed.
- The current prototype tracks pending and paid payout amounts from payout request records to avoid over-reserving whole earning rows.

## Refund Flow
- Student submits refund request through `/api/v1/students/refund-requests`.
- Admin approves, rejects, or marks refunded through `/api/v1/admin/refund-requests/...`.
- Marking refunded updates order/payment status, revokes entitlements, and marks related teacher earnings as refunded where applicable.
- Full live Stripe refund processing is not required for automated tests.

## Notifications And Audit Logs
- Commerce events create in-app notifications through the existing notification service.
- Important actions are recorded through the existing audit log service.

## Known Limitations
- Stripe sandbox/test mode only.
- Automated tests use mock payment provider behavior and do not call real Stripe.
- No real bank transfer automation.
- Payout is simulated by admin approval and mark-paid actions.
- Refund handling is prototype-friendly if full Stripe refund flow is not configured.
- No real card data is stored by this app.
