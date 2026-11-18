# Sprint 1 manual/browser QA plan

Environment: disposable PostgreSQL database and upload directory, backend port
8088, frontend port 5173. Provision two companies and two separate accounts, plus
one account linked to both companies. Never use real confidential invoices.

1. Correct/wrong/missing credentials, inactive account, 10 failed logins and
   expiry of the 15-minute window. Failures must never create a usable session.
2. Multi-company login must require selection. Select files in A, switch to B:
   pending files, invoice selection and all A results must disappear.
3. Direct API calls with no session, no selected company, no creation permission,
   missing CSRF, another company's session/item/invoice/file IDs: no read/write.
4. Upload valid XML, malformed XML, wrong-buyer XML, outbound XML. Verify originals
   and outcomes; only inbound parsed XML creates pending invoices.
5. ZIP with two good XMLs and one malformed XML: 1 uploaded file, 3 XMLs, 2 created,
   1 parse failure. Upload again: 2 already-received, no extra canonical totals.
6. Different bytes with the same seller/template/series/number: identity duplicate.
   Two concurrent uploads must link the same canonical invoice.
7. Empty/no-XML ZIP, nested ZIP, traversal path, entry-count/expanded-byte/ratio
   limits and exact file/lô boundaries. Retain earlier successful entries when
   later entries fail. PDF/PNG must receive explicit unsupported guidance.
8. Refresh/sign out/in and inspect real history. Retry only eligible failed
   documents and reach the configured maximum. Successful items cannot retry.
9. Simulate unavailable storage and unavailable audit separately. Storage failure
   must not claim durable acceptance; audit failure must show NOT_CHECKED, with
   recheck available and no implicit approval.
10. Review/approve through the existing workflow; deny users without review
    permission. Original XML display must never claim a verified signature.
11. Session expiry/logout must remove the workspace. No automatic upload replay.
    Restart the backend after durable reception and inspect incomplete items.
12. Analytics, export and discrepancy logs must contain only the selected tenant.

Browser E2E execution is pending. Backend integration tests are run independently;
their passing results do not mark manual/browser acceptance complete.
