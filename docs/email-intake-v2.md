# Thu thập hóa đơn từ email — triển khai hiện tại

Triển khai dựa trên tài liệu nghiệp vụ Inbot, tích hợp vào Spring Boot/React và
PostgreSQL của SmartExpense. Không chuyển dữ liệu hiện tại sang MongoDB, không
đổi tài khoản hoặc database của người dùng.

## Thao tác trên giao diện

1. Đăng nhập, chọn **Email → Kết nối email**.
2. Điền email, máy chủ IMAP, mật khẩu ứng dụng và whitelist người gửi.
3. Lưu kết nối, bấm **Đồng bộ ngay**. Chỉ nhận thư chưa đọc trong INBOX.
4. Xem trạng thái email, chọn **Xem tài liệu** để xem file gốc hoặc đối soát PDF.
5. Email FAILED/PARTIAL/PAUSE/REJECTED_SENDER có thể **Xử lý lại** từ bản gốc.
   Sau khi sửa whitelist, có thể tiếp nhận lại thư từng bị từ chối.

Whitelist trống từ chối mọi người gửi của OWN_IMAP. Webhook và SYSTEM_MAILBOX
không áp dụng whitelist theo tài liệu nghiệp vụ mới.

## Cấu hình máy chủ

Sinh khóa mã hóa AES-256-GCM một lần, lưu trong secret manager hoặc môi trường
dịch vụ. Giữ nguyên khóa qua các lần khởi động; đổi khóa cần mã hóa lại dữ liệu.

```powershell
$keyBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($keyBytes)
$env:EMAIL_ENCRYPTION_KEY = [Convert]::ToBase64String($keyBytes)
```

Với Windows PowerShell cũ không có `Fill`, dùng:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$rng.Dispose()
$env:EMAIL_ENCRYPTION_KEY = [Convert]::ToBase64String($keyBytes)
```

Không có khóa thì giao diện vẫn xem lịch sử được, nhưng không lưu kết nối mới.
Khóa phải là Base64 của đúng 32 byte. Mỗi lần mã hóa dùng nonce ngẫu nhiên 12
byte và tenantId làm AAD, tránh hoán đổi ciphertext giữa doanh nghiệp.

Các biến môi trường tùy chọn:

| Biến | Ý nghĩa |
|---|---|
| `EMAIL_IMAP_HOSTS` | Danh sách máy chủ quản trị cho phép; mặc định Gmail, Outlook, Yahoo |
| `EMAIL_DOWNLOAD_DOMAINS` | Domain tải HTTPS được phép; mặc định các domain HĐĐT phổ biến trong application.yml |
| `EMAIL_MONTHLY_INVOICE_LIMIT` | Hạn mức hóa đơn email mặc định theo tenant/tháng; 0 là không giới hạn |
| `EMAIL_WEBHOOK_SECRET` | Secret webhook; rỗng thì webhook tắt |
| `EMAIL_TENANT_ID` | Tenant nhận của webhook hiện tại; không nhận tenantId từ người gửi |
| `EMAIL_SYSTEM_DOMAIN` | Domain catch-all do đơn vị vận hành sở hữu |
| `EMAIL_SYSTEM_HOST` | Máy chủ IMAPS hộp catch-all, mặc định imap.gmail.com |
| `EMAIL_SYSTEM_USER` | Tài khoản hộp catch-all |
| `EMAIL_SYSTEM_PASSWORD` | Mật khẩu ứng dụng catch-all từ secret môi trường |
| `EMAIL_REDIS_ENABLED` | Bật Redis lock và Redis DLQ; mặc định false |
| `EMAIL_OCR_ASYNC_ENABLED` | Bật OCR qua RabbitMQ/outbox; mặc định false |

Redis dùng `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`,
`SPRING_DATA_REDIS_PASSWORD`. RabbitMQ dùng `SPRING_RABBITMQ_HOST`,
`SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD`.

## Ba kênh tiếp nhận

**OWN_IMAP:** Scheduler 15 phút hoặc nút đồng bộ. IMAPS port 993, kiểm tra
chứng thư/hostname, peek để không tự đánh dấu SEEN khi đọc. Toàn bộ MIME gốc và
payload/file đính kèm được lưu trước SEEN. Nếu lưu hoặc commit lỗi, thư còn chưa
đọc để nhận lại. Mỗi chu kỳ tối đa 25 thư; MIME tối đa 36 MiB, độ sâu 16,
200 phần, 20 đính kèm, 15 MiB/file, 24 MiB tổng payload.

**SYSTEM_MAILBOX:** Khi có cấu hình catch-all, người dùng bấm cấp địa chỉ nhận;
địa chỉ do server sinh `tenant-<id>@<domain>` để không chiếm địa chỉ tenant khác.
Quét 2 phút, ưu tiên `Delivered-To`, rồi `X-Original-To`, rồi `To`. Không xác
định được đúng một tenant thì giữ MIME vào `system_inbox_unmatched`; không
đánh dấu SEEN nếu chưa lưu thành công. Domain/MX/catch-all phải được thiết lập
thực tế ở nhà cung cấp email; ứng dụng không tự mua domain hoặc tạo Google Workspace.

**WEBHOOK:** `POST /emails/webhook`, secret constant-time như bản trước. Đây là
hợp đồng JSON chuẩn hóa; adapter nhà cung cấp phải xác minh nguồn thư và chuẩn
hóa sender/attachments trước khi chuyển tiếp. Webhook hiện cấu hình một tenant
ở máy chủ, chưa định tuyến đa tenant theo payload.

## Pipeline và khôi phục

- Claim unique `(tenantId, messageKey)` và khóa hàng tenant PostgreSQL trước khi
  lưu nguyên bản. Message-ID là ưu tiên; thiếu thì hash payload chuẩn hóa.
- XML ưu tiên trước PDF; ZIP có giới hạn 50 MiB, 200 entry và tỷ lệ 100:1.
  PDF ghép được với XML thì không chạy OCR.
- XML chặn DTD/XXE, hỗ trợ tên thẻ không phân biệt hoa thường và `nbanccthue`.
- Tải link HTTPS trực tiếp XML/ZIP/PDF trong allowlist; chặn mạng riêng,
  loopback, link-local, shared-address và IPv6 tunnel. Apache HTTP client dùng
  DNS resolver ghim IP đã xác minh, kiểm tra lại từng redirect, tối đa 5 redirect.
  Không bỏ kiểm tra chứng thư và không gửi credentials tới website tải hóa đơn.
- MST mua khớp tenant/aliases, chặn đầu ra, unique hash và danh tính hóa đơn.
  Hóa đơn email có source `EMAIL_POLLER`.
- `risk_enterprises` chứa danh sách rủi ro được đơn vị vận hành nhập từ nguồn
  đáng tin cậy. Gắn `taxRiskState=HIGH_TAX_RISK`, hiển thị cảnh báo đỏ; giữ trạng
  thái chờ đối soát để kế toán quyết định. Không giả lập dữ liệu CQT.
- XML có `signatureState=MISSING`, `INVALID_STRUCTURE` hoặc
  `PRESENT_UNVERIFIED`. Unmarshal cấu trúc XMLDSig; chưa xác minh mật mã, CA/NEAC,
  thu hồi chứng thư hoặc trạng thái hủy hóa đơn.
- Quota bảng `tenant_email_quota`, tính tháng theo Asia/Ho_Chi_Minh. Trừ quota
  trong transaction tạo invoice; trùng nội dung/danh tính không trừ lần nữa.
- Lỗi mạng/429/5xx chuyển PAUSE; tự thử lại 3 lần sau 10s, 60s, 15 phút.
  Hết lượt gắn FAILED + deadLetter, lưu bền trong PostgreSQL và mirror sang
  Redis `email:dlq:<tenantId>:<emailId>` nếu bật. Mốc 30/60 phút trong tài liệu
  không dùng vì tài liệu đồng thời quy định chỉ 3 lần retry.
- Reprocess thủ công có bộ đếm riêng, không bị khóa vĩnh viễn vì đã hết lượt
  auto retry. Dùng raw payload đã lưu; không yêu cầu nhà cung cấp gửi lại.
- Khóa liên instance dùng lease PostgreSQL có owner token, thêm Redis lock nếu
  bật. Không thêm ShedLock vì lease hiện có đảm nhiệm cùng chức năng trong
  kiến trúc PostgreSQL này. Khi bật Redis mà Redis lỗi, thao tác không tiếp tục.

## Chống nhập trùng PDF / XML / email

Trong cùng doanh nghiệp, đối chiếu **MST người bán + Ký hiệu + Số hóa đơn**.
Bỏ khoảng trắng đầu/cuối và không phân biệt hoa thường; mẫu số không tham gia
khóa. Giữ chuẩn hóa OCR: bỏ số 0 đầu số hóa đơn và bỏ mẫu số bị OCR ghép vào
đầu ký hiệu. Không suy đoán danh tính khi thiếu một trong ba trường.

- File giống hệt đã nhập: `ALREADY_RECEIVED`.
- Nội dung hoặc định dạng khác nhưng trùng ba trường: `DUPLICATE_IDENTITY`.
  Giữ tài liệu gốc và liên kết tới hóa đơn đã có; không tạo hóa đơn thứ hai,
  không trừ quota thêm và không cộng tiền lần hai.
- PDF được kiểm tra sau OCR, khi trích xuất lại và ngay trước xác nhận. Nếu XML
  được nhập trong lúc PDF chờ đối soát, xác nhận PDF sẽ báo trùng lặp.
- Đối chiếu cả dữ liệu cũ dùng khóa có mẫu số. Duyệt hóa đơn kiểm tra lại danh
  tính và trả HTTP 409 khi trùng; nhập/xác nhận/duyệt khóa theo doanh nghiệp.
- Cùng số nhưng khác ký hiệu, MST bán hoặc doanh nghiệp nhận không bị coi là
  trùng. Không tự gộp/xóa bản ghi lịch sử đã có trước thay đổi.

Test tay: nhập XML, sau đó gửi PDF cùng ba trường qua email và thử thứ tự ngược
lại. Trong **Xem tài liệu**, bản sau hiển thị trùng lặp và mở về hóa đơn ban đầu.
Danh sách chỉ có một hóa đơn; duyệt bản đó rồi kiểm tra tổng chi tăng một lần.
Đổi riêng ký hiệu để kiểm tra vẫn nhận hóa đơn mới.

## Xuất bảng kê chi phí Excel

Nút **Xuất Excel hóa đơn đã duyệt** chỉ lấy hóa đơn `APPROVED` của doanh nghiệp
đang chọn, gồm cả hóa đơn nhận từ email. Hóa đơn đang xử lý, chờ đối soát hoặc
bị từ chối không xuất hiện và không được cộng vào tổng tiền. Tổng thanh toán
trong file tương ứng với tổng chi tiêu đã duyệt trên dashboard tại thời điểm xuất.
Chưa có hóa đơn đã duyệt thì vẫn tải được file với thông báo và tổng tiền bằng 0.
Sau khi duyệt hoặc đổi trạng thái hóa đơn, tải file mới để nhận dữ liệu mới nhất.

## PDF bất đồng bộ

Khi `EMAIL_OCR_ASYNC_ENABLED=true`, PDF email không ghép được với XML tạo
`OCR_PENDING` và outbox `email_ocr_outbox` cùng transaction. Dispatcher publish
vào queue bền `email.ocr`, chờ publisher confirm và kiểm tra mandatory return
rồi mới đánh dấu PUBLISHED. Job chưa gửi vẫn nằm PENDING khi broker lỗi.

Worker nhận job id, thiết lập tenant context, gọi AI HTTP hiện có, lưu
OCR_REVIEW/OCR_FAILED. Delivery lặp không trích xuất lại job đã xong. Queue lỗi
có `email.ocr.dlq`. Người dùng đối soát PDF trong mục Email trước khi tạo invoice.

Khi chưa bật RabbitMQ, PDF dùng HTTP OCR hiện có; email mang NEEDS_REVIEW và
không tạo invoice trước xác nhận. Đây là fallback có chủ đích để chạy dự án
cục bộ khi chưa có broker.

## API cấu hình mới

Các API `/api` dùng session, tenant membership, quyền tạo hóa đơn và CSRF hiện có.
Mật khẩu IMAP không bao giờ được trả về trong JSON.

| API | Chức năng |
|---|---|
| GET `/api/emails/mailbox` | Cấu hình hộp riêng, trạng thái mã hóa và host được phép |
| PUT `/api/emails/mailbox` | email, host, port=993, password, active, allowedSenders |
| POST `/api/emails/sync` | Đồng bộ ngay hộp riêng |
| GET `/api/emails/system-mailbox` | Địa chỉ hệ thống và khả năng cấp |
| POST `/api/emails/system-mailbox` | Cấp địa chỉ do server sinh |
| GET `/api/emails` | Lịch sử gồm channel, retryCount, reprocessCount, nextRetryAt, deadLetter |
| POST `/api/emails/{id}/reprocess` | Xử lý lại dữ liệu đã giữ |

## Giới hạn cần vận hành hoặc phát triển tiếp

- Chưa tự động bấm link xác nhận chuyển tiếp Gmail: không thể tin sender/subject
  đơn thuần để xác thực thư Google. Cần kiểm tra DKIM hoặc metadata được relay
  tin cậy xác minh trước khi tự động thay đổi forwarding; hiện xác nhận thủ công.
- Link HTML portal có CAPTCHA, đăng nhập hoặc JavaScript cần adapter riêng;
  downloader hiện chỉ nhận URL tải file trực tiếp, không giả lập trình duyệt.
- Không tự phê duyệt hóa đơn khi chưa có chính sách được cấu hình; rủi ro/chữ ký
  luôn hiển thị để kế toán xem xét. Chưa có feed CQT hoặc xác minh chuỗi NEAC.
- Chưa có UI quản trị quarantine hệ thống, danh sách CQT và quota. Dữ liệu được
  quản lý ở database bởi đơn vị vận hành.
- Lưu trữ gốc cần volume bền dùng chung giữa các node và backup. Lease dùng TTL
  30 phút, quét tối đa 10 phút trước thư kế tiếp; không nên chạy từng tài liệu
  vượt TTL. Cần giới hạn tài nguyên và giám sát thực tế trước triển khai lớn.
- Không tự bật IMAP khi chưa có tài khoản/mật khẩu thực tế, hoặc tự bật Redis/
  RabbitMQ khi chưa có hạ tầng. Kiểm thử tích hợp không thay thế kiểm tra kết nối
  mailbox/relay/broker thật của đơn vị vận hành.
