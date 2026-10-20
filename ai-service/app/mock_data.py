import re
from typing import Optional
from app.schemas import InvoiceExtractionResponse, LineItemSchema, ExpenseCategoryEnum

MOCK_INVOICES = [
    InvoiceExtractionResponse(
        invoice_number="VN-2024-08912",
        invoice_date="2024-05-12",
        vendor_name="CÔNG TY CỔ PHẦN HÀNG KHÔNG VIETNAM AIRLINES",
        tax_code="0100107518",
        line_items=[
            LineItemSchema(
                item_name="Vé máy bay khứ hồi Hà Nội - TP. Hồ Chí Minh",
                quantity=1.0,
                unit_price=3200000.0,
                amount=3200000.0,
                vat_rate="10%"
            ),
            LineItemSchema(
                item_name="Phí dịch vụ hành lý ký gửi 23kg",
                quantity=1.0,
                unit_price=300000.0,
                amount=300000.0,
                vat_rate="10%"
            )
        ],
        subtotal_amount=3500000.0,
        vat_amount=350000.0,
        total_amount=3850000.0,
        category=ExpenseCategoryEnum.TRAVEL,
        confidence_score=0.96,
        notes="Hóa đơn điện tử hợp lệ - Vé máy bay công tác",
        raw_text="HÓA ĐƠN GIÁ TRỊ GIA TĂNG VIETNAM AIRLINES..."
    ),
    InvoiceExtractionResponse(
        invoice_number="TCH-2024-4410",
        invoice_date="2024-05-14",
        vendor_name="CÔNG TY CỔ PHẦN THƯƠNG MẠI DỊCH VỤ TRÀ CÀ PHÊ VIỆT NAM (THE COFFEE HOUSE)",
        tax_code="0312867172",
        line_items=[
            LineItemSchema(
                item_name="Combo Teabreak & Cà phê tiếp khách đối tác",
                quantity=4.0,
                unit_price=125000.0,
                amount=500000.0,
                vat_rate="8%"
            ),
            LineItemSchema(
                item_name="Bánh ngọt phô mai nướng",
                quantity=2.0,
                unit_price=45000.0,
                amount=90000.0,
                vat_rate="8%"
            )
        ],
        subtotal_amount=590000.0,
        vat_amount=47200.0,
        total_amount=637200.0,
        category=ExpenseCategoryEnum.MEALS,
        confidence_score=0.93,
        notes="Hóa đơn tiếp khách ăn uống",
        raw_text="THE COFFEE HOUSE - HÓA ĐƠN BÁN HÀNG TIẾP KHÁCH..."
    ),
    InvoiceExtractionResponse(
        invoice_number="FPT-2024-78192",
        invoice_date="2024-05-16",
        vendor_name="CÔNG TY CỔ PHẦN BÁN LẺ KỸ THUẬT SỐ FPT (FPT RETAIL)",
        tax_code="0311609355",
        line_items=[
            LineItemSchema(
                item_name="Ổ cứng SSD di động Kingston XS1000 1TB USB 3.2 Gen 2",
                quantity=2.0,
                unit_price=1850000.0,
                amount=3700000.0,
                vat_rate="10%"
            ),
            LineItemSchema(
                item_name="Chuột không dây Logitech MX Master 3S",
                quantity=1.0,
                unit_price=2190000.0,
                amount=2190000.0,
                vat_rate="10%"
            )
        ],
        subtotal_amount=5890000.0,
        vat_amount=589000.0,
        total_amount=6479000.0,
        category=ExpenseCategoryEnum.EQUIPMENT,
        confidence_score=0.97,
        notes="Trang thiết bị phụ vụ dự án",
        raw_text="FPT SHOP - HÓA ĐƠN GTGT MUA SẮM THIẾT BỊ..."
    ),
    InvoiceExtractionResponse(
        invoice_number="AWS-VN-2024-910",
        invoice_date="2024-05-18",
        vendor_name="AMAZON WEB SERVICES VIETNAM LLC",
        tax_code="0109823412",
        line_items=[
            LineItemSchema(
                item_name="Dịch vụ hạ tầng điện toán đám mây Cloud Hosting & EC2 Tháng 5/2024",
                quantity=1.0,
                unit_price=4200000.0,
                amount=4200000.0,
                vat_rate="10%"
            )
        ],
        subtotal_amount=4200000.0,
        vat_amount=420000.0,
        total_amount=4620000.0,
        category=ExpenseCategoryEnum.SERVICES,
        confidence_score=0.98,
        notes="Hóa đơn dịch vụ phần mềm",
        raw_text="AMAZON WEB SERVICES - CLOUD INVOICE..."
    )
]

def get_smart_fallback(filename: str, file_bytes: bytes) -> InvoiceExtractionResponse:
    """Return a realistic invoice based on filename hints or round-robin."""
    lower_name = filename.lower()
    if any(k in lower_name for k in ["air", "flight", "bay", "ve", "travel", "ticket"]):
        return MOCK_INVOICES[0]
    elif any(k in lower_name for k in ["coffee", "an", "uong", "meal", "food", "nha-hang"]):
        return MOCK_INVOICES[1]
    elif any(k in lower_name for k in ["fpt", "thiet-bi", "gear", "pc", "laptop", "equipment"]):
        return MOCK_INVOICES[2]
    elif any(k in lower_name for k in ["aws", "cloud", "service", "dich-vu"]):
        return MOCK_INVOICES[3]
    
    # Hash of bytes to pick deterministically
    idx = sum(file_bytes[:100]) % len(MOCK_INVOICES) if file_bytes else 0
    return MOCK_INVOICES[idx]
