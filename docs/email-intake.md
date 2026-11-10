# Tiếp nhận email

Module backend bổ sung vào Spring Boot/PostgreSQL hiện có. Đây là luồng webhook
JSON chuẩn hóa; nhà cung cấp cần adapter chuyển payload SendGrid/Postmark sang
hợp đồng bên dưới. Chưa thay thế toàn bộ kiến trúc Inbot trong bản nghiệp vụ.

## Cấu hình

Thiết lập biến môi trường trước khi khởi động backend:

```powershell
$env:EMAIL_WEBHOOK_SECRET = '<secret ngẫu nhiên do đơn vị vận hành cấp>'
$env:EMAIL_TENANT_ID = '1'
$env:EMAIL_ALLOWED_SENDERS = 'billing@supplier.vn,invoices@supplier.vn'
```

Mặc định webhook tắt. Mỗi cấu hình webhook hiện phục vụ một tenant; tenant không
được lấy từ dữ liệu người gửi. Whitelist so sánh địa chỉ đầy đủ, không phân biệt
hoa thường. Địa chỉ sender phải được adapter xác minh dựa trên kết quả xác thực
thư của nhà cung cấp; trường JSON không tự chứng minh được người gửi.

## API

`POST /emails/webhook`, header `X-Inbot-Email-Webhook-Secret`:

```json
{
  "messageId": "<invoice-123@supplier.vn>",
  "sender": "billing@supplier.vn",
  "subject": "Hóa đơn tháng 11",
  "html": "<p>Hóa đơn đính kèm</p>",
  "attachments": [{"name": "invoice.xml", "content": "BASE64_OF_ORIGINAL_BYTES"}]
}
```

`GET /api/emails`: tối đa 100 email của tenant đang đăng nhập.

`POST /api/emails/{id}/reprocess`: dùng session đăng nhập và `X-CSRF-Token` như
API nhập tài liệu. Chấp nhận FAILED/PARTIAL/PAUSE hoặc RECEIVED quá 15 phút;
giới hạn tổng cộng 3 lần xử lý. Các bản ghi đang chạy trả 409.

`sessionId` trong kết quả dẫn tới `GET /api/intake/sessions/{sessionId}`, chứa
trạng thái từng file và liên kết hóa đơn. File gốc truy cập qua API intake có
kiểm tra quyền tenant. PDF ghép XML mang trạng thái `PDF_ATTACHED`.

## Đảm bảo đã triển khai

- Secret so sánh bằng `MessageDigest.isEqual`; whitelist mặc định từ chối.
- Lưu nguyên payload và byte đính kèm bằng kho file hiện tại trước khi parse.
  Lỗi lưu bản gốc làm rollback claim và trả lỗi để provider giao lại thư.
- Claim theo tenant + hash Message-ID; thiếu Message-ID thì hash payload JSON.
  Unique constraint và khóa hàng tenant PostgreSQL bảo vệ giữa các instance.
  Khi nhận lại email đã claim, trả bản ghi hiện có, không chạy pipeline lần nữa.
- Reprocess đọc payload đã lưu; hóa đơn thành công trước đó không tạo lại.
- XML được ưu tiên trước PDF; nhận diện nội dung XML với BOM UTF-8/whitespace.
  PDF ghép theo tên, hoặc XML duy nhất, không gọi OCR. PDF không ghép được dùng
  luồng OCR có sẵn và chờ xác nhận của kế toán.
- XML dùng parser chặn DTD/XXE hiện có; ZIP dùng giới hạn số entry, dung lượng,
  tỷ lệ nén và kiểm tra đường dẫn hiện có.
- Email bị sai MST mua bị `REJECTED_ROUTING`, không tạo Invoice. Bảng
  `tenant_aliases` cho phép thêm MST chi nhánh theo tenant. Cả xác nhận PDF từ
  email cũng áp dụng quy tắc này. Chặn hóa đơn đầu ra và chống trùng danh tính
  tiếp tục dùng pipeline hiện có.

## Phạm vi còn thiếu so với đặc tả

- IMAP 15 phút, quản lý tài khoản và AES-GCM; chưa có thao tác SEEN.
- Redis/ShedLock, tự động retry exponential backoff, Redis DLQ.
- RabbitMQ/outbox và worker OCR bất đồng bộ; OCR hiện gọi HTTP dịch vụ sẵn có.
- Adapter tải link portal với DNS pinning/chặn SSRF. URL hiện chỉ được giữ lại;
  email chỉ có URL nhận FAILED để không báo thành công giả.
- Danh sách doanh nghiệp rủi ro và kiểm tra cấu trúc chữ ký số; parser hiện
  không xác thực chữ ký hay chuỗi chứng thư NEAC.
- Màn hình quản lý email, cấu hình mailbox, quản trị whitelist và tenant aliases.
- REJECTED_SENDER trả HTTP 403, chưa ghi bản ghi audit email bị từ chối.

Kho raw dùng filesystem, cần volume bền vững dùng chung giữa các instance và
chính sách backup. Chưa thể tuyên bố zero data loss khi hạ tầng chưa đáp ứng.
Đặt giới hạn HTTP body ở ingress (ví dụ 36 MiB cho JSON base64) trước backend.
Không tự động dọn raw payload: cần chính sách lưu trữ riêng của đơn vị vận hành.
