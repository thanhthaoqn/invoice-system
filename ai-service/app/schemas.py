from typing import List, Optional
from pydantic import BaseModel, Field
from enum import Enum


class ExpenseCategoryEnum(str, Enum):
    TRAVEL = "Chi phí Công tác"
    MEALS = "Tiếp khách / Ăn uống"
    EQUIPMENT = "Mua sắm Vật tư / Thiết bị"
    TRAINING = "Đào tạo / Hội thảo"
    SERVICES = "Chi phí Dịch vụ / Khác"


class LineItemSchema(BaseModel):
    item_name: str = Field(..., description="Tên mặt hàng, dịch vụ hoặc vé")
    quantity: Optional[float] = Field(default=None, description="Số lượng")
    unit_price: Optional[float] = Field(default=None, description="Đơn giá")
    amount: Optional[float] = Field(default=None, description="Thành tiền trước thuế của mặt hàng")
    vat_rate: Optional[str] = Field(default=None, description="Thuế suất GTGT")


class InvoiceExtractionResponse(BaseModel):
    invoice_template: Optional[str] = None
    invoice_series: Optional[str] = None
    seller_address: Optional[str] = None
    buyer_name: Optional[str] = None
    buyer_tax_code: Optional[str] = None
    buyer_address: Optional[str] = None
    currency: Optional[str] = None
    invoice_number: Optional[str] = Field(None, description="Số hóa đơn (VD: '001289', 'HD-2024-001')")
    invoice_date: Optional[str] = Field(None, description="Ngày lập hóa đơn định dạng YYYY-MM-DD")
    vendor_name: Optional[str] = Field(None, description="Tên đơn vị bán lẻ, nhà cung cấp, hoặc đối tác")
    tax_code: Optional[str] = Field(None, description="Mã số thuế bên bán (Tax Code / MST)")
    line_items: List[LineItemSchema] = Field(default_factory=list, description="Danh sách các dòng chi tiết hàng hóa / dịch vụ")
    subtotal_amount: Optional[float] = Field(None, description="Tổng tiền trước thuế")
    vat_amount: Optional[float] = Field(None, description="Tổng tiền thuế GTGT (VAT)")
    total_amount: Optional[float] = Field(None, description="Tổng tiền thanh toán bằng số")
    category: ExpenseCategoryEnum = Field(default=ExpenseCategoryEnum.SERVICES, description="Danh mục chi phí tự động phân loại")
    confidence_score: Optional[float] = Field(default=None, ge=0.0, le=1.0, description="Độ tin cậy nếu engine cung cấp")
    notes: Optional[str] = Field(default=None, description="Ghi chú thêm hoặc lưu ý từ AI")
    raw_text: Optional[str] = Field(default=None, description="Văn bản tóm tắt trích xuất")
    extraction_method: Optional[str] = Field(default="VLM_GEMINI_VISION", description="Phương thức bóc tách: PYMUPDF_VECTOR_TEXT hoặc VLM_GEMINI_VISION")
    text_layer_detected: Optional[bool] = Field(default=False, description="Đã phát hiện text layer vector gốc trong PDF")
    debit_account: Optional[str] = Field(default="6427", description="Tài khoản Nợ kế toán đề xuất")
    vat_account: Optional[str] = Field(default="1331", description="Tài khoản Thuế GTGT kế toán đề xuất")
    credit_account: Optional[str] = Field(default="1111", description="Tài khoản Có kế toán đề xuất")
    cashless_required: Optional[bool] = Field(default=False, description="Yêu cầu thanh toán không dùng tiền mặt (>= 20 triệu VNĐ)")
