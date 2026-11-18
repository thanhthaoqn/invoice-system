# Chạy và test luồng email cục bộ

Đã khởi chạy trên database riêng `expense_email_manual`, tenant MST `0100000001`.
Đây là thử nghiệm webhook HTTP; không gửi email ra ngoài hay kết nối hộp thư thật.

- Frontend: http://127.0.0.1:5173
- Backend: http://127.0.0.1:8088
- AI service: http://127.0.0.1:8000/docs

Tài khoản test là `email.tester`. Mật khẩu và webhook secret sinh riêng cho máy,
được lưu ở `.local/email-test/config.json` và không đưa vào Git. Đọc mật khẩu để
đăng nhập frontend:

```powershell
(Get-Content -Raw .local/email-test/config.json | ConvertFrom-Json).password
```

Frontend hiện xem được hóa đơn và các phiên nhập, chưa có trang quản lý email.
Test email và reprocess bằng API như dưới đây. Chạy các lệnh từ thư mục gốc dự án.

## Bộ kiểm tra HTTP đang chạy thật

```powershell
& ai-service/venv/Scripts/python.exe scripts/manual_email_test.py
```

Mỗi lần chạy dùng Message-ID và số hóa đơn mới, giữ lại dữ liệu để xem trên UI.
Kết quả lưu tại `.local/email-test/results.json`. Mẫu webhook lưu tại
`.local/email-test/sample-email.json`.

Lần kiểm tra ngày 10/11/2026 đạt 20/20 trường hợp: secret sai, sender ngoài
whitelist, XML hợp lệ, lặp Message-ID, lặp nội dung, MST mua sai, hóa đơn đầu ra,
XXE, partial, reprocess giữ nguyên hóa đơn thành công, giới hạn retry, ZIP nhiều
XML, Zip-Slip, tỷ lệ nén bất thường, PDF ghép XML, tải lại đúng byte gốc, PDF-only
chờ đối soát qua AI service thật, link chưa hỗ trợ, lịch sử cần đăng nhập và
lịch sử email sau đăng nhập.

Đối chiếu PostgreSQL sau lần chạy đầu: 12 email, 5 hóa đơn; 4 email SUCCESS,
1 PARTIAL, 1 NO_XML, 6 FAILED (các trường hợp cố ý lỗi). PDF-only có hồ sơ
OCR_REVIEW, chưa tạo hóa đơn trước khi kế toán xác nhận.

## Tự gửi và kiểm tra

```powershell
$cfg = Get-Content -Raw .local/email-test/config.json | ConvertFrom-Json
$headers = @{ 'X-Inbot-Email-Webhook-Secret' = $cfg.secret }
$body = Get-Content -Raw -Encoding UTF8 .local/email-test/sample-email.json
$message = Invoke-RestMethod http://127.0.0.1:8088/emails/webhook `
  -Method Post -ContentType application/json -Headers $headers `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
$message
```

Gửi lại mẫu này phải trả cùng `id` và `sessionId`. Muốn nhập hóa đơn mới cần
đổi cả Message-ID và số hóa đơn trong XML (trường content là Base64).

Đăng nhập API, xem lịch sử email và phiên nhập:

```powershell
$login = Invoke-RestMethod http://127.0.0.1:8088/api/auth/login `
  -Method Post -ContentType application/json -SessionVariable emailSession `
  -Body (@{ username=$cfg.username; password=$cfg.password } | ConvertTo-Json)
$emails = Invoke-RestMethod http://127.0.0.1:8088/api/emails -WebSession $emailSession
$emails | Format-Table id,subject,state,sessionId,attempts
Invoke-RestMethod "http://127.0.0.1:8088/api/intake/sessions/$($message.sessionId)" `
  -WebSession $emailSession
```

Chọn email lỗi còn lượt retry, rồi xử lý lại:

```powershell
$failed = $emails | Where-Object { $_.state -eq 'FAILED' -and $_.attempts -lt 3 } | Select-Object -First 1
if ($failed) {
  Invoke-RestMethod "http://127.0.0.1:8088/api/emails/$($failed.id)/reprocess" `
    -Method Post -WebSession $emailSession -Headers @{ 'X-CSRF-Token'=$login.csrfToken }
}
```

File XML lỗi cấu trúc vẫn lỗi khi retry: bản gốc không bị thay đổi. Mục tiêu
kiểm tra là đọc lại được dữ liệu đã giữ, tăng attempts và không sinh hóa đơn trùng.

## Runtime

Log nằm trong `.local/email-test/`: `backend.log`, `backend-error.log`,
`ai-error.log`, `frontend.log`, `frontend-error.log`.

Backend local dùng script `.local/email-test/backend.ps1` để nạp cấu hình test
và chạy Maven offline bằng plugin đã cache. Sau khi dừng backend, khởi động lại:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .local/email-test/backend.ps1
```

Frontend và AI service dùng lệnh README. Không dùng đồng thời backend mặc định
và backend test trên cùng port 8088. Các giới hạn nghiệp vụ chưa triển khai được
liệt kê tại [email-intake.md](email-intake.md).
