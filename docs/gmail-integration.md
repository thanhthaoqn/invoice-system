# Gmail Invoice Integration

The backend can connect one Gmail mailbox per tenant, read messages carrying the
user label `HOA_DON`, store supported attachments and pass them to the existing
tenant-scoped intake pipeline. It requests only `gmail.readonly`; it never changes
labels, marks messages read or deletes mail.

## Google Cloud setup

1. Create or select a Google Cloud project and enable **Gmail API**.
2. Configure the OAuth consent screen/audience and add test users while the app is
   in testing mode.
3. Add `https://www.googleapis.com/auth/gmail.readonly` to Data Access.
4. Create a **Web application** OAuth client.
5. Add the exact authorized redirect URI used by the backend, for example
   `http://127.0.0.1:8088/oauth/gmail/callback` for local development. Production
   must use HTTPS and the deployed host.
6. Configure the environment variables documented in `backend/.env.example`.

`gmail.readonly` is a restricted scope. Public/production use can require Google
OAuth verification and a security assessment when restricted Gmail data is stored
or transmitted by the server. Cloud Console work must be completed by an operator
who has permission on the Google project.

## Grant an application administrator

Only users listed in `gmail_admin_grant` can connect, disconnect or manually sync
Gmail. Provision the row through controlled database administration after checking
the user and tenant IDs:

```sql
insert into gmail_admin_grant (user_id, tenant_id)
values (<app_user_id>, <tenant_id>);
```

Do not infer this permission from invoice creation/review permissions. Hibernate's
current `ddl-auto: update` setting creates the new development tables; production
deployments should review and apply an explicit schema migration and backup first.

## Runtime behavior

- OAuth state is random, hashed in the database, expires after ten minutes and is
  accepted only once.
- Refresh tokens are encrypted with AES-256-GCM. The encryption key and Google
  client secret never belong in source control.
- The worker resolves the `HOA_DON` label ID and follows Gmail `nextPageToken`
  pagination.
- MIME parts are traversed recursively. Inline attachment data and attachment IDs
  are supported.
- PDF, XML, PNG and JPEG are accepted only after content sniffing. Originals are
  stored before parser/OCR processing and receive the existing SHA-256 protection.
- Gmail message and MIME-part unique keys make synchronization resumable. Errors
  are retried with bounded backoff and no token/message content is written to logs.
- XML is handled by the existing parser. PDF and image attachments enter the OCR
  review flow and are not converted to an invoice until a user confirms the data.

## API

- `GET /api/gmail/status`
- `POST /api/gmail/oauth/authorization-url`
- `GET /oauth/gmail/callback` (Google callback)
- `POST /api/gmail/sync`
- `POST /api/gmail/disconnect`

All `/api/gmail` mutations retain the application's session CSRF protection and
also require an explicit Gmail administrator grant.
