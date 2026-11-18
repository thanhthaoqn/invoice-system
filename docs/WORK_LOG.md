# Work log — SmartExpense Sprint 1

2026-11-04: Added real digital-PDF extraction and configured Gemini scan OCR,
matching XML fields. Added stored PDF drafts, versioned edits, explicit confirmation,
pending invoice creation, XML/PDF dedup and authorized original preview. Removed
Java's simulated extraction fallback. Verified 18 backend tests, 7 Python tests,
frontend build and two live synthetic PDFs (text and scanned image). See pdf-intake.md.

2026-11-03: User selected implementation of Sprint 1 in the current SmartExpense
repository. The supplied inbot backlog is used as requirements; referenced Vue,
Redis, intake and multi-tenant services did not exist in this repository.

Added account/company membership and password hashing, session/CSRF protection,
active-company authorization, scoped invoice/analytics/export access, XML intake,
bounded ZIP expansion, durable originals, session/item history and retry attempts.
Connected React login/company selector, multiple-file picker, actual results,
history, retries, original XML display and audit recheck. Removed synthetic demo
uploads and fabricated signature display.

Verification: backend integration suite reports 15 tests, zero failures/errors;
frontend production build passes. Oxlint reports warnings in existing React
effects, with no errors. No browser E2E or live PostgreSQL acceptance was run.

Adaptations and remaining acceptance boundaries are recorded in
`sprint-1-smart-expense.md`; manual/browser scenarios in `sprint-1-qa-plan.md`.
No commit, MR, merge or production deployment is performed.

2026-11-03 follow-up: XML review now defaults to a readable representation of the
original source (parties, identity, goods, totals), with separate raw-XML viewing
and download. Embedded XML stylesheet references are never executed. The preview
stays independent of form edits. Fixed panel scrolling/responsive height and
removed hardcoded Gemini/confidence/speed claims from XML review. PDF intake is
still outside the active Sprint 1 flow; the legacy PDF service contains simulated
fallbacks and has not been re-enabled as production OCR.
