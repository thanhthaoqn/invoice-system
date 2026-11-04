# Sprint 1 — SmartExpense

Implemented scope: login, company selection, XML/ZIP intake, per-document outcomes,
durable history and bounded retries. OCR/email remain outside this sprint.

## Run and provision

Use Java 21, PostgreSQL, and the existing frontend dependencies. Start the backend
from `backend` with `mvn spring-boot:run`, and the frontend from `frontend` with
`npm run dev`. Vite proxies `/api` to port 8088; deploy the frontend and `/api`
under the same origin. In HTTPS deployments set `SESSION_COOKIE_SECURE=true`.

No account or default password is shipped. Before the first backend start, set:

```powershell
$env:APP_BOOTSTRAP_USERNAME = 'accountant@example.com'
$env:APP_BOOTSTRAP_PASSWORD = '<your unique password, 12–256 characters>'
$env:APP_BOOTSTRAP_TAX_CODE = '<company tax code>'
$env:APP_BOOTSTRAP_TENANT_NAME = '<company name>'
mvn spring-boot:run
```

Remove these variables from the deployment after provisioning. To link another
company to that existing account, bootstrap again with that username and the new
company tax code/name. Existing passwords and permissions are never overwritten.
Provision other users the same way. There is no public registration endpoint.
The `membership` table controls `invoice_create` and `invoice_review` separately.
Administrators can revoke membership, disable an account (`app_user.active`) or
update company/subscription status through controlled database administration.

Existing invoices have no tenant: they stay invisible. Assign legacy records to
the verified tenant through an explicit migration after backup/review; do not
automatically give them to the first company. Hibernate adds the new tables and
columns under the project's existing `ddl-auto: update` policy; review schema
changes and back up the database before deploying to an existing installation.

## Workflow

1. Log in. A single eligible company is selected automatically. Multiple eligible
   companies require a selection before invoice APIs are accessible.
2. The company header always shows the recipient. Switching company unmounts the
   current workspace, discarding selected files and results before loading data.
3. Select/add/remove XML/ZIP files and press **Gửi tiếp nhận**. A progress value of
   100% only means bytes were transmitted. Inspect the server's item results.
4. Open invoices from results for existing review/approval. New imports remain
   `PENDING_REVIEW`; XML signatures are not verified or synthesized.
5. History survives refresh/sign-in and shows the latest 100 sessions. Retry only
   parse/processing errors (or a received item stalled for five minutes), up to
   three attempts including the initial attempt. Retry uses the stored source;
   successful documents cannot be retried into new invoices. A broken source
   remains broken: upload a corrected XML as a new document.
6. `STORAGE_FAILED` means the original was not saved. Restore storage and upload
   again. `NOT_CHECKED` invoices expose **Chạy lại kiểm tra**; checking never
   approves them. A session interrupted during a process restart stays visibly
   incomplete, with recoverable received XML items.

## API and outcomes

- `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/tenant`,
  `POST /api/auth/logout`. Sessions use HttpOnly, SameSite=Strict cookies.
  Login returns a CSRF token; subsequent mutations require `X-CSRF-Token`.
- `GET /api/intake/limits` supplies the exact frontend/backend intake limits.
- `POST /api/intake/sessions`: multipart `files`, one result session per request.
- `GET /api/intake/sessions`, `GET /api/intake/sessions/{id}`: persisted results.
- `POST /api/intake/sessions/{id}/items/{itemId}/retry`.
- `GET /api/intake/items/{id}/file`, `GET /api/intake/items/{id}/attempts`.
- `POST /api/invoices/{id}/recheck` reruns validation without changing approval.

A session response contains `session`, `items`, and `counts`. Each item includes
its source path/name, state, message, invoice/original item linkage and attempts.
`INGESTED` means a new canonical invoice, `ALREADY_RECEIVED` a content duplicate,
`DUPLICATE_IDENTITY` a different file for an existing identity. `PARSE_FAILED`,
`REJECTED_OUTBOUND`, storage/processing errors and ZIP errors are distinct.
Archive rows represent uploaded ZIP sources, not additional invoices/XMLs.
Counts separate uploaded files, discovered XML documents and business outcomes.
Every read/retry/file download checks the active company's membership. Legacy
single-upload and unscoped filename download routes are disabled.

## Adaptations from the inbot backlog

- SmartExpense had no AuthService, Redis, tenant policy, intake pipeline, quota,
  fiscal-period service or auto-approval service. These are not presumed to exist.
- Failed logins are serialized and counted persistently in the database, with
  10 failures per 15-minute account window. This is not Redis parity with LG-01.
- Sessions expire after 30 minutes of inactivity or a backend restart. No refresh
  tokens are added; expiry clears the frontend and requires sign-in. Mutating
  requests are never automatically replayed. LG-03/04 are not claimed complete.
- Eligible company statuses: CONFIGURING/READY/ACTIVE. EXPIRED is blocked;
  GRACE allows read/export only. Password-change-required users are read-only.
- The shared SmartExpense audit is called, and invoice approval remains explicit.
  There is no new quota, fiscal-period assignment or auto-approval subsystem.
- XML supports the Vietnamese `DLHDon/TTChung/NDHDon` structure, with buyer/seller,
  totals and ordinary goods lines. Unsupported XML schemas are retained as parse
  failures. Signed XML is preserved unchanged and never treated as verified.
- ZIP budgets are shared across a batch: 200 entries, 50 MiB expanded and ratio
  100; maximum 20 files, 15 MiB/file, 24 MiB/batch. Configure `app.intake.*` to
  change these initial defaults. The transport ceiling remains 25 MiB.
  Nested ZIPs, unsafe entry paths, encrypted/corrupt ZIPs are rejected explicitly.
- Storage is local disk, with independently committed item processing and
  per-tenant DB locks plus unique canonical keys. Multi-instance deployments
  require shared durable storage and session affinity/shared sessions.
- Receipts are intake item rows per source/session; attempts are separately
  retained. There is no automatic retention deletion, OCR, email or public user
  administration UI in this change.

## Validation

Backend integration tests use H2 and MockMvc, covering authentication, tenant
access, partial ZIP success, duplicates, retry, malicious paths/XXE, storage
failure, audit failure, and concurrent canonical creation. Frontend uses the
TypeScript/Vite production build and oxlint. Browser E2E and live PostgreSQL
deployment verification are separate checks; neither is claimed by these tests.
