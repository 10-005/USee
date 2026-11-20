# Simulation event integration scaffold

DTOs and enums: src/services/simulation/events.ts.
GET /simulations/{sessionSeq}/events accepts optional status, page, size and occurredDttm sorting.
FCM EVENT indicates a HOME event. Payload keys, session identifier and page envelope are not specified; no parser is assumed.
On integration, refresh the current session on screen entry and EVENT push. Persist first display by occurrenceSeq at that point. Currently only the development preview is wired.
Display scenario.title and description without choices or the correct action.
Enable the hospital and show cost/red dot only for PENDING with vetVisit.
Later send 18.6 VET_VISIT and apply resolvedEvent on success. RESOLVED/EXPIRED restores the original icon.
No local treatment completion, illness queue, API calls, cost deductions or expiry penalties are implemented.
18.6 care actions and itemSeq requirements are defined in src/services/simulation/actions.ts.
FEED, CLEAN_LITTER, BATHE, SNACK and MEDICINE require itemSeq; PLAY accepts an optional itemSeq; WATER and VET_VISIT omit it.
Required equipment: FEED uses FOOD_BOWL, WATER uses WATER_BOWL, CLEAN_LITTER uses LITTER_BOX, and PLAY requires at least one TOY.
The server validates inventory, consumes supplies and charges the event cost. VET_VISIT without the corresponding PENDING event returns 409 SIMULATION_VET_NOT_NEEDED.
The resolvedEvent response schema still needs specification.
