import os
import json
import base64
import logging
from typing import Optional
import requests
from dotenv import load_dotenv
from app.schemas import InvoiceExtractionResponse, LineItemSchema, ExpenseCategoryEnum
from app.mock_data import get_smart_fallback

load_dotenv()

logger = logging.getLogger(__name__)

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "").strip()
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")

SYSTEM_PROMPT = """Bạn là một chuyên gia AI Kế toán và Kiểm toán Hóa đơn Doanh nghiệp tại Việt Nam.
Nhiệm vụ của bạn là đọc và trích xuất dữ liệu từ hình ảnh hoặc tài liệu hóa đơn (Hóa đơn GTGT, Hóa đơn điện tử, Hóa đơn bán lẻ, Vé máy bay, Biên lai thanh toán).

Yêu cầu xuất ra JSON chính xác theo cấu trúc sau:
{
  "invoice_number": "Số hóa đơn (nếu không có thì null)",
  "invoice_date": "Ngày lập hóa đơn định dạng YYYY-MM-DD",
  "vendor_name": "Tên đầy đủ của đơn vị bán hàng / nhà cung cấp",
  "tax_code": "Mã số thuế của đơn vị bán hàng (MST)",
  "line_items": [
    {
      "item_name": "Tên hàng hóa / dịch vụ",
      "quantity": 1.0,
      "unit_price": 100000.0,
      "amount": 100000.0,
      "vat_rate": "10%"
    }
  ],
  "subtotal_amount": 100000.0,
  "vat_amount": 10000.0,
  "total_amount": 110000.0,
  "category": "Một trong 5 danh mục sau: 'Chi phí Công tác', 'Tiếp khách / Ăn uống', 'Mua sắm Vật tư / Thiết bị', 'Đào tạo / Hội thảo', 'Chi phí Dịch vụ / Khác'",
  "confidence_score": 0.95,
  "notes": "Nhận xét tóm tắt về hóa đơn"
}

Quy tắc phân loại danh mục (category):
- 'Chi phí Công tác': Vé máy bay, khách sạn, tàu xe, taxi di chuyển công tác.
- 'Tiếp khách / Ăn uống': Nhà hàng, quán cafe, teabreak, ăn uống tiếp khách.
- 'Mua sắm Vật tư / Thiết bị': Văn phòng phẩm, máy tính, linh kiện, thiết bị phần cứng, bàn ghế.
- 'Đào tạo / Hội thảo': Vé tham dự hội thảo, khóa đào tạo chuyên môn, chứng chỉ.
- 'Chi phí Dịch vụ / Khác': Dịch vụ đám mây (AWS, GCP, Azure), phần mềm SaaS, cước viễn thông, dịch vụ khác.

Đảm bảo:
- Các số tiền đều là số thực (number/float), không chứa dấu phẩy hay ký hiệu tiền tệ.
- Logic số học: Tổng thành tiền các mặt hàng + tiền thuế VAT phải khớp với tổng tiền thanh toán.
- Chỉ trả về duy nhất chuỗi JSON hợp lệ, không bọc markdown hay text giải thích ngoài JSON.
"""

def extract_invoice_with_gemini(
    file_bytes: bytes,
    filename: str,
    mime_type: str
) -> InvoiceExtractionResponse:
    """
    Extracts invoice data using Gemini 1.5 Flash Vision or smart fallback.
    """
    api_key = os.getenv("GEMINI_API_KEY", GEMINI_API_KEY)
    
    if not api_key:
        logger.warning("GEMINI_API_KEY is not set. Using smart fallback extractor.")
        fallback = get_smart_fallback(filename, file_bytes)
        fallback.notes = "Trích xuất mô phỏng AI (Chưa cấu hình GEMINI_API_KEY)"
        return fallback

    configured_model = os.getenv("GEMINI_MODEL", "gemini-3.6-flash").strip()
    candidate_models = [configured_model]
    for fallback_m in ["gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.8-flash"]:
        if fallback_m not in candidate_models:
            candidate_models.append(fallback_m)

    b64_data = base64.b64encode(file_bytes).decode("utf-8")
    
    # Normalize mime type for Gemini
    if "pdf" in mime_type.lower():
        target_mime = "application/pdf"
    elif "png" in mime_type.lower():
        target_mime = "image/png"
    elif "webp" in mime_type.lower():
        target_mime = "image/webp"
    else:
        target_mime = "image/jpeg"

    payload = {
        "contents": [
            {
                "role": "user",
                "parts": [
                    {"text": SYSTEM_PROMPT},
                    {
                        "inline_data": {
                            "mime_type": target_mime,
                            "data": b64_data
                        }
                    }
                ]
            }
        ],
        "generationConfig": {
            "response_mime_type": "application/json",
            "temperature": 0.1
        }
    }

    last_error = None
    for model_name in candidate_models:
        try:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"
            resp = requests.post(url, json=payload, timeout=30)
            if resp.status_code == 200:
                result_json = resp.json()
                content_text = (
                    result_json.get("candidates", [{}])[0]
                    .get("content", {})
                    .get("parts", [{}])[0]
                    .get("text", "")
                )
                
                # Clean up any potential markdown fences
                clean_text = content_text.strip()
                if clean_text.startswith("```json"):
                    clean_text = clean_text[7:]
                elif clean_text.startswith("```"):
                    clean_text = clean_text[3:]
                if clean_text.endswith("```"):
                    clean_text = clean_text[:-3]
                clean_text = clean_text.strip()

                parsed_data = json.loads(clean_text)
                response = InvoiceExtractionResponse(**parsed_data)
                response.notes = f"Trích xuất thành công bằng {model_name}"
                return response
            else:
                last_error = f"Model {model_name} trả về mã {resp.status_code}: {resp.text[:120]}"
                logger.warning(f"Thử {model_name} thất bại: {last_error}. Thử model tiếp theo...")
        except Exception as e:
            last_error = str(e)
            logger.warning(f"Lỗi khi gọi model {model_name}: {e}. Đang chuyển model tiếp theo...")

    logger.error(f"Tất cả model Gemini đều không khả dụng. Lỗi cuối: {last_error}")
    fallback = get_smart_fallback(filename, file_bytes)
    fallback.notes = f"Fallback do lỗi Gemini API ({str(last_error)[:100]})"
    return fallback
