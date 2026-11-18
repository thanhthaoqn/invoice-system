# Status — SmartExpense Sprint 1

Implementation is ready for review and environment acceptance. Do not equate the
passing H2 integration suite with all original inbot acceptance criteria being Done.

- LG-01: login, password-only verification, inactive-account refusal, persistent
  10/15-minute throttling implemented. Redis-specific AC remains an adaptation.
- LG-02: company choice, API isolation and workspace reset implemented; requires
  browser verification of switching while a file is selected/uploading.
- UP-01: XML/ZIP picker, permission, shared limits and actual sessions implemented.
- UP-02: originals/intake, secure XML parsing, buyer/outbound routing, canonical
  dedup and existing audit implemented. Source project has no quota/period or
  auto-approval infrastructure to reuse.
- UP-03: individual XML processing, partial results and ZIP budgets implemented.
- UP-04: actual outcomes/history, scoped originals, attempt ledger, bounded retry
  and incomplete/error states implemented.

Pending deployment acceptance: PostgreSQL schema/data migration review, initial
account/company provisioning and browser QA. Old tenantless invoices remain
hidden until explicitly assigned. OCR/email and LG-03/04 are outside this scope.
