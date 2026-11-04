# PDF intake — 2026-11-04

PDF now uses the same invoice fields as XML: number, date, template/series,
seller name/tax/address, buyer name/tax/address, currency, goods lines
(name, quantity, unit price, amount, VAT rate), subtotal, VAT and total.

## Usage

1. Select PDF alongside XML/ZIP in **Nộp chứng từ**, or include PDFs in a ZIP.
2. The original is stored before extraction. PDF text is read locally with
   PyMuPDF; image-only or mixed PDFs are sent to the configured Gemini OCR model.
3. In intake history choose **Xem / sửa dữ liệu PDF**. The original PDF appears
   beside the extracted fields. Empty fields remain visibly empty; no sample
   invoice, inferred totals or placeholder goods lines are substituted.
4. Correct/complete fields, add/remove goods lines, and optionally **Lưu nháp**.
   Conflicting edits return HTTP 409; close/reopen to load the current version.
5. **Xác nhận dữ liệu & tạo hóa đơn** creates a pending invoice with source
   `OCR_CONFIRMED`. Confirmation does not approve it or verify a signature.
   Repeated confirmation returns the same invoice. An XML/PDF identity collision
   links the existing invoice instead of creating another canonical record.
6. Failed extraction retains the original and supports bounded retry from history.
   An identical upload reuses a pending PDF review or existing invoice.

Permission: `invoice_create` allows draft edits/confirmation; the existing
`invoice_review` permission is required for subsequent invoice approval.
Company-scoped access applies to drafts, original PDF previews and confirmation.

## Services and configuration

Start the existing backend/frontend and, from `ai-service`, run:

```powershell
.\venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Install dependencies from `requirements.txt` if setting up a new environment.
Digital PDF extraction requires no cloud credentials. Image-only/mixed PDFs need
`GEMINI_API_KEY` and `GEMINI_MODEL` in `ai-service/.env` (never committed).
OCR uses the [Gemini generateContent API](https://ai.google.dev/api/generate-content).
The configured model is used directly; errors do not trigger simulated results.

File size uses existing `app.intake` limits. Page count defaults to 20 via backend
`app.intake.max-pdf-pages` and Python `PDF_MAX_PAGES`; the smaller limit wins.
Password-protected/corrupt PDFs and PDFs over the limit produce explicit errors.
One PDF is one review document; split files containing unrelated invoices before
upload. Table extraction depends on readable headers/layout; unsupported columns
are left for user review, and do not receive invented values.

The intake flow remains synchronous and local originals require durable storage.
No queue/background OCR worker is introduced. Unavailable OCR returns `OCR_FAILED`
with a retry action. `OCR_REVIEW` means extracted/persisted but not yet an Invoice.
Draft edits retain the original extraction; confirmation stores actor, time and
the confirmed snapshot. Engine identification is taken from the server result.

API: `GET/PUT /api/intake/items/{id}/draft` exchanges `{version,data}`;
`POST /api/intake/items/{id}/confirm` accepts the same body. Draft data uses the
extractor's snake_case field names. `GET /api/intake/items/{id}/file` serves a
tenant-authorized inline PDF; archives and non-PDF originals remain downloads.

## Verification

- Python: 7 unit tests for actual digital PDFs, Vietnamese labels/zero VAT,
  missing data, page limits/corrupt input, scan routing and engine failures.
- Backend: 18 integration tests, including PDF draft persistence/version conflict,
  required fields, confirmation replay, PDF/XML identity dedup and tenant access.
- TypeScript/Vite production build and lint (only pre-existing React warnings).
- Live extraction of two generated fixtures: digital text and image-only PDF both
  returned the same number/template/series/buyer tax/total and one goods line.
  The scan was processed by the configured Gemini service, without mock fallback.
  No real user invoice was sent as a verification fixture.
