# Finance Domain — Overview

## What This Domain Owns

- Entry and club payment status operations, including bulk mark-paid and mark-unpaid workflows.
- Invoice generation, invoice state transitions, and finance summary read models.
- Staff-facing finance views for club status, entry status, invoice listing, and invoice detail.

## Boundaries

- It does not own the regatta entities whose billing scope it references.
- It does not own authentication policy beyond consuming protected staff routes.
- It does not own public-facing pages, though finance data can affect staff exports and workflows.

## Key Files

| File | Purpose |
|:-----|:--------|
| `apps/backend/src/main/java/com/regattadesk/finance/api/PaymentStatusResource.java` | Entry, club, and bulk payment endpoints. |
| `apps/backend/src/main/java/com/regattadesk/finance/api/InvoiceResource.java` | Invoice generation, listing, detail, and mark-paid endpoints. |
| `apps/backend/src/main/java/com/regattadesk/finance/service/PaymentStatusService.java` | Finance status business logic. |
| `apps/backend/src/main/java/com/regattadesk/finance/service/InvoiceService.java` | Invoice lifecycle service. |
| `apps/backend/src/main/java/com/regattadesk/finance/event/FinanceProjectionHandler.java` | Finance read-model projection updates. |
| `apps/frontend/src/views/staff/RegattaFinance.vue` | Finance landing view. |
| `apps/frontend/src/views/staff/InvoiceList.vue` | Invoice list UI. |
| `apps/frontend/src/views/staff/InvoiceDetail.vue` | Invoice detail UI. |

## Current State

- The backend already has a substantial finance package split across API, event, model, and service layers.
- The frontend has dedicated staff views for club and entry payment status plus invoice workflows.
- BC08 is implemented as a distinct workflow domain rather than as a small extension to entry CRUD.