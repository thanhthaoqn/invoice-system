# Smart Expense Reimbursement & Invoice Audit
# Script khởi chạy toàn bộ 3 dịch vụ: Python AI Service, Spring Boot Backend, và React Frontend

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " SMART EXPENSE REIMBURSEMENT & INVOICE AUDIT" -ForegroundColor Green
Write-Host " Hệ thống Tự động hóa Quyết toán Chi phí Doanh nghiệp" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

$baseDir = $PSScriptRoot

# 0. Kiểm tra PostgreSQL Database (Docker)
Write-Host "[0/3] Kiểm tra PostgreSQL Database (Port 5432)..." -ForegroundColor Blue
$pgRunning = docker ps --filter "name=coursedesk-postgres" --filter "status=running" -q
if (-not $pgRunning) {
    Write-Host "  -> Đang khởi động container coursedesk-postgres..." -ForegroundColor Yellow
    docker start coursedesk-postgres 2>$null
}

# 1. Khởi động Python AI Microservice (Port 8000)
Write-Host "[1/3] Khởi chạy Python AI Extraction Microservice (Port 8000)..." -ForegroundColor Magenta
$aiDir = Join-Path $baseDir "ai-service"
$pyCmd = "if (Test-Path '.\venv\Scripts\python.exe') { & '.\venv\Scripts\python.exe' -m uvicorn app.main:app --host 127.0.0.1 --port 8000 } else { python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 }"
Start-Process powershell.exe -WorkingDirectory $aiDir -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'AI Microservice (Port 8000)'; $pyCmd"

# 2. Khởi động Java Spring Boot Backend (Port 8088)
Write-Host "[2/3] Khởi chạy Java Spring Boot Backend (Port 8088)..." -ForegroundColor Cyan
$backendDir = Join-Path $baseDir "backend"
Start-Process powershell.exe -WorkingDirectory $backendDir -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'Spring Boot Backend (Port 8088)'; mvn spring-boot:run"

# 3. Khởi động React Vite Frontend (Port 5173)
Write-Host "[3/3] Khởi chạy React 18 Frontend Web App (Port 5173)..." -ForegroundColor Green
$frontendDir = Join-Path $baseDir "frontend"
Start-Process powershell.exe -WorkingDirectory $frontendDir -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'React Frontend (Port 5173)'; npm run dev -- --host 127.0.0.1 --port 5173"

Write-Host "----------------------------------------------------------" -ForegroundColor Gray
Write-Host "Tất cả các dịch vụ đang chạy:" -ForegroundColor White
Write-Host "  • AI Extraction Microservice: http://127.0.0.1:8000/docs" -ForegroundColor Yellow
Write-Host "  • Java Spring Boot Backend:   http://127.0.0.1:8088/api/analytics/summary" -ForegroundColor Yellow
Write-Host "  • React Split-Screen Web App: http://127.0.0.1:5173" -ForegroundColor Green
Write-Host "----------------------------------------------------------" -ForegroundColor Gray
Write-Host "Mở trình duyệt tại http://127.0.0.1:5173 để trải nghiệm hệ thống!" -ForegroundColor Green
