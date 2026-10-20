import logging
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from app.schemas import InvoiceExtractionResponse
from app.invoice_processor import InvoiceProcessor

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ai_service")

app = FastAPI(
    title="Smart Expense AI Extraction Microservice",
    description="Microservice bóc tách và phân loại hóa đơn đa tầng: PyMuPDF Vector Text (<50ms) + VLM Vision + KIE + Định khoản kế toán",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "ai-extraction-microservice",
        "pipeline": "PyMuPDF Vector Text + Gemini 1.5 Flash Vision Fallback",
        "features": [
            "Smart Input Router (Digital Vector PDF vs Scanned/Image)",
            "PyMuPDF Vector Text Extractor (<50ms)",
            "Image Preprocessing & Deskew",
            "Regex & KIE Extractor",
            "Auto Accounting Account Mapping (TT 200/133)",
            "Cashless Payment Threshold Check (>=20M VNĐ)"
        ]
    }


@app.post("/api/extract", response_model=InvoiceExtractionResponse)
async def extract_invoice(file: UploadFile = File(...)):
    """
    Quy trình bóc tách hóa đơn 4 tầng kỹ thuật:
    1. Input Router kiểm tra text layer trong PDF (PyMuPDF).
    2. Nếu PDF vector có text -> Bóc tách siêu tốc trong 50ms không cần OCR.
    3. Nếu PDF scan / Ảnh -> Tiền xử lý ảnh (Deskew, Enhance) và chuyển sang VLM.
    4. Trích xuất thông tin ngữ cảnh KIE, tự động định khoản Nợ/Có và kiểm tra ngưỡng 20 triệu.
    """
    if not file:
        raise HTTPException(status_code=400, detail="Không tìm thấy file tải lên")

    filename = file.filename or "invoice.pdf"
    mime_type = file.content_type or "application/octet-stream"

    logger.info(f"Processing invoice file: {filename} ({mime_type})")

    file_bytes = await file.read()
    if len(file_bytes) == 0:
        raise HTTPException(status_code=400, detail="File rỗng")

    extraction_result = InvoiceProcessor.process_invoice(
        file_bytes=file_bytes,
        filename=filename,
        mime_type=mime_type
    )

    return extraction_result


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
