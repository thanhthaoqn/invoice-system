import io
import re
import time
import logging
from typing import Optional, List, Tuple
import pymupdf  # PyMuPDF
from app.schemas import InvoiceExtractionResponse, LineItemSchema, ExpenseCategoryEnum
from app.image_utils import preprocess_image
from app.gemini_client import extract_invoice_with_gemini

logger = logging.getLogger(__name__)

class InvoiceProcessor:
    """
    Bộ xử lý hóa đơn đa tầng:
    Tầng 1: Input Router kiểm tra text layer trong PDF bằng PyMuPDF.
    Tầng 2 (PDF Vector): Bóc tách siêu tốc trong vài chục ms trực tiếp từ Text Layer không cần OCR.
    Tầng 3 (PDF Scan / Ảnh): Tiền xử lý ảnh (Deskew, Enhance) và chuyển tiếp tới VLM (Gemini 1.5 Flash Vision).
    Tầng 4: Động cơ KIE & Tự động định khoản Kế toán (Thông tư 200/133) và Thẩm định ngưỡng thanh toán không dùng tiền mặt (20 triệu VNĐ).
    """

    @staticmethod
    def map_accounting_accounts(category: ExpenseCategoryEnum, total_amount: Optional[float]) -> Tuple[str, str, str, bool]:
        """
        Tự động định khoản kế toán theo Thông tư 200/133 và thẩm định ngưỡng 20 triệu:
        Trả về (debit_account, vat_account, credit_account, cashless_required)
        """
        total = total_amount or 0.0
        cashless_required = total >= 20000000.0

        # Tài khoản Nợ theo nhóm chi phí
        if category == ExpenseCategoryEnum.TRAVEL:
            debit_account = "6427"  # Chi phí công tác
        elif category == ExpenseCategoryEnum.MEALS:
            debit_account = "6428"  # Chi phí tiếp khách
        elif category == ExpenseCategoryEnum.EQUIPMENT:
            debit_account = "211" if total >= 30000000.0 else "153"  # TSCĐ hoặc Công cụ dụng cụ
        elif category == ExpenseCategoryEnum.TRAINING:
            debit_account = "6427"  # Chi phí đào tạo
        else:
            debit_account = "6427"  # Chi phí dịch vụ mua ngoài

        # Tài khoản Thuế GTGT đầu vào
        vat_account = "1331"

        # Tài khoản Có: Nếu >= 20 triệu thì bắt buộc chuyển khoản qua ngân hàng (1121) hoặc công nợ (331)
        if cashless_required:
            credit_account = "1121"  # Tiền gửi ngân hàng
        else:
            credit_account = "1111"  # Tiền mặt

        return debit_account, vat_account, credit_account, cashless_required

    @classmethod
    def process_invoice(cls, file_bytes: bytes, filename: str, mime_type: str) -> InvoiceExtractionResponse:
        start_time = time.time()
        is_pdf = "pdf" in mime_type.lower() or filename.lower().endswith(".pdf")

        if is_pdf:
            has_text_layer, page_text = cls.inspect_pdf_text_layer(file_bytes)
            if has_text_layer:
                logger.info(f"⚡ [Input Router] Detected Digital PDF with vector text layer ({len(page_text)} chars). Processing with PyMuPDF Extractor...")
                try:
                    result = cls.process_pdf_text_layer(file_bytes, page_text, filename)
                    elapsed_ms = int((time.time() - start_time) * 1000)
                    result.notes = f"Bóc tách siêu tốc bằng PyMuPDF Extractor ({elapsed_ms}ms)"
                    return result
                except Exception as e:
                    logger.warning(f"PyMuPDF vector extraction encountered exception: {e}. Falling back to Vision pipeline.")

        # PDF Scan hoặc Ảnh chụp (JPG, PNG, WEBP)
        logger.info(f"📷 [Input Router] Processing non-vector/image document ({filename}). Running Image Preprocessing & Deskew...")
        preprocessed_bytes = file_bytes
        try:
            if not is_pdf:
                preprocessed_bytes = preprocess_image(file_bytes)
        except Exception as e:
            logger.warning(f"Preprocessing skipped due to error: {e}")

        # Chuyển tiếp tới VLM Vision
        result = extract_invoice_with_gemini(
            file_bytes=preprocessed_bytes,
            filename=filename,
            mime_type=mime_type
        )
        
        # Bổ sung tài khoản kế toán và ngưỡng không dùng tiền mặt cho kết quả VLM
        debit, vat, credit, cashless = cls.map_accounting_accounts(result.category, result.total_amount)
        result.debit_account = debit
        result.vat_account = vat
        result.credit_account = credit
        result.cashless_required = cashless
        result.text_layer_detected = False
        if not result.extraction_method or result.extraction_method == "PYMUPDF_VECTOR_TEXT":
            result.extraction_method = "VLM_GEMINI_VISION"

        return result

    @classmethod
    def inspect_pdf_text_layer(cls, pdf_bytes: bytes) -> Tuple[bool, str]:
        """
        Kiểm tra nhanh xem PDF có chứa text layer (Vector PDF) hay là Scan ảnh.
        """
        try:
            doc = pymupdf.open(stream=pdf_bytes, filetype="pdf")
            total_text = []
            for page in doc:
                text = page.get_text("text")
                if text:
                    total_text.append(text)
            doc.close()
            combined_text = "\n".join(total_text).strip()
            
            # Nếu có trên 40 ký tự có nghĩa, coi là vector PDF có text layer
            if len(combined_text) > 40:
                return True, combined_text
            return False, combined_text
        except Exception as e:
            logger.warning(f"Failed to inspect PDF text layer: {e}")
            return False, ""

    @classmethod
    def process_pdf_text_layer(cls, pdf_bytes: bytes, full_text: str, filename: str) -> InvoiceExtractionResponse:
        """
        Bóc tách siêu tốc thông tin hóa đơn từ text layer của PyMuPDF kết hợp Regex & KIE.
        """
        doc = pymupdf.open(stream=pdf_bytes, filetype="pdf")
        
        # 1. Trích xuất Mã số thuế (MST) - 10 hoặc 13 số
        tax_code = None
        mst_match = re.search(r"(?:Mã\s*số\s*thuế|MST|Tax\s*Code)[:\s]*([0-9]{10}(?:-[0-9]{3})?)", full_text, re.IGNORECASE)
        if mst_match:
            tax_code = mst_match.group(1).strip()
        else:
            # Tìm dãy số 10 hoặc 13 chữ số thông dụng cho MST
            generic_mst = re.search(r"\b([0-9]{10}(?:-[0-9]{3})?)\b", full_text)
            if generic_mst:
                tax_code = generic_mst.group(1).strip()

        # 2. Trích xuất Số hóa đơn
        invoice_number = None
        inv_no_match = re.search(r"(?:Số\s*hóa\s*đơn|Số\s*HĐ|Số|Invoice\s*No\.?)[:\s]*([A-Z0-9\-\/]{4,15})", full_text, re.IGNORECASE)
        if inv_no_match:
            invoice_number = inv_no_match.group(1).strip()
        else:
            # Mẫu hóa đơn số dạng 0001234
            num_match = re.search(r"\b(00[0-9]{4,6})\b", full_text)
            if num_match:
                invoice_number = num_match.group(1).strip()

        # 3. Trích xuất Ngày lập hóa đơn
        invoice_date = None
        date_vn_match = re.search(r"Ng[àa.·]*y[^\d\n]*(\d{1,2})[^\d\n]*th[áa.·]*ng[^\d\n]*(\d{1,2})[^\d\n]*n[ăa.·]*m[^\d\n]*(\d{4})", full_text, re.IGNORECASE)
        if date_vn_match:
            day, month, year = date_vn_match.groups()
            invoice_date = f"{year}-{int(month):02d}-{int(day):02d}"
        else:
            date_slash_match = re.search(r"\b(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{4})\b", full_text)
            if date_slash_match:
                day, month, year = date_slash_match.groups()
                invoice_date = f"{year}-{int(month):02d}-{int(day):02d}"

        # 4. Trích xuất Tên đơn vị bán (Vendor Name)
        vendor_name = None
        for line in full_text.splitlines()[:20]:
            clean_l = line.strip()
            up = clean_l.upper()
            if any(k in up for k in ["CÔNG TY", "CONG TY", "VIETNAM AIRLINES", "FPT", "THE COFFEE HOUSE", "TNHH", "CỔ PHẦN"]):
                # Loại bỏ tiền tố nhãn nếu có (ví dụ: "Đơn vị bán: ...")
                clean_name = re.sub(r"^[^:]*[:\s]+", "", clean_l).strip()
                if len(clean_name) > 5 and not any(k in clean_name.upper() for k in ["MÃ SỐ THUẾ", "MST"]):
                    vendor_name = clean_name
                    break
                elif len(clean_l) > 5 and not any(k in up for k in ["MÃ SỐ THUẾ", "MST"]):
                    vendor_name = clean_l
                    break

        # 5. Trích xuất Số tiền (Cộng tiền hàng, Tiền thuế VAT, Tổng tiền thanh toán)
        def parse_amount(val_str: str) -> Optional[float]:
            try:
                clean = re.sub(r"[^\d,\.]", "", val_str)
                if "." in clean and "," in clean:
                    if clean.rfind(",") > clean.rfind("."):
                        clean = clean.replace(".", "").replace(",", ".")
                    else:
                        clean = clean.replace(",", "")
                elif "." in clean:
                    parts = clean.split(".")
                    if len(parts) > 1 and len(parts[-1]) == 3:
                        clean = clean.replace(".", "")
                elif "," in clean:
                    parts = clean.split(",")
                    if len(parts) > 1 and len(parts[-1]) == 3:
                        clean = clean.replace(",", "")
                    else:
                        clean = clean.replace(",", ".")
                val = float(clean)
                return val if val > 0 else None
            except Exception:
                return None

        subtotal_amount = None
        vat_amount = None
        total_amount = None

        # Tổng thanh toán (hỗ trợ cả dấu tiếng Việt, không dấu, và ký tự encode)
        total_match = re.search(r"(?:T[ổo.·]*ng|Total)[^\d\n]*(?:c[ộo.·]*ng|ti[ềe.·]*n|thanh\s*to[áa.·]*n)?[^\d\n]*[:\s]*([\d\.,]{4,})", full_text, re.IGNORECASE)
        if total_match:
            total_amount = parse_amount(total_match.group(1))

        # Tiền thuế GTGT (tránh nhầm với "Mã số thuế")
        vat_match = re.search(r"(?:Ti[ềe.·]*n\s*thu[ếe.·]*|Thu[ếe.·]*\s*GTGT|Thu[ếe.·]*\s*su[ấa.·]*t|VAT\s*amount)[^\d\n]*[:\s]*([\d\.,]{4,})", full_text, re.IGNORECASE)
        if vat_match:
            vat_amount = parse_amount(vat_match.group(1))

        # Tiền hàng trước thuế
        subtotal_match = re.search(r"(?:C[ộo.·]*ng|Ti[ềe.·]*n\s*h[àa.·]*ng|Subtotal)[^\d\n]*(?:ti[ềe.·]*n\s*h[àa.·]*ng|h[àa.·]*ng)?[^\d\n]*[:\s]*([\d\.,]{4,})", full_text, re.IGNORECASE)
        if subtotal_match:
            subtotal_amount = parse_amount(subtotal_match.group(1))

        # Logic suy luận số học nếu thiếu 1 trong 3 trường
        if total_amount and subtotal_amount and not vat_amount:
            vat_amount = max(0.0, total_amount - subtotal_amount)
        elif total_amount and vat_amount and not subtotal_amount:
            subtotal_amount = max(0.0, total_amount - vat_amount)
        elif subtotal_amount and vat_amount and not total_amount:
            total_amount = subtotal_amount + vat_amount

        # Nếu không trích xuất được tổng tiền từ text layer, ném exception để Fallback VLM
        if not total_amount and not subtotal_amount:
            raise ValueError("Không trích xuất được tổng tiền từ text layer; kích hoạt Fallback VLM.")

        # 6. Bóc tách danh sách chi tiết mặt hàng (Line Items) từ bảng
        line_items = []
        for page in doc:
            # Thử extract table qua pymupdf
            try:
                tables = page.find_tables()
                if tables and len(tables.tables) > 0:
                    for table in tables:
                        df_rows = table.extract()
                        for row in df_rows[1:]: # Bỏ header
                            row_clean = [str(c).strip() if c else "" for c in row]
                            # Tìm cột tên hàng và cột thành tiền
                            if len(row_clean) >= 3 and any(row_clean):
                                name = row_clean[1] if len(row_clean) > 1 and len(row_clean[1]) > 2 else row_clean[0]
                                amount_candidate = None
                                for col in reversed(row_clean):
                                    amt = parse_amount(col)
                                    if amt and amt > 1000:
                                        amount_candidate = amt
                                        break
                                if amount_candidate and len(name) > 2:
                                    line_items.append(LineItemSchema(
                                        item_name=name,
                                        quantity=1.0,
                                        unit_price=amount_candidate,
                                        amount=amount_candidate,
                                        vat_rate="10%"
                                    ))
            except Exception as e:
                logger.debug(f"PyMuPDF table extraction note: {e}")

        # Nếu không trích xuất được mặt hàng qua bảng, tạo mặt hàng đại diện từ tiền hàng
        if not line_items:
            item_name_fallback = "Dịch vụ / Hàng hóa theo hóa đơn"
            if vendor_name and "AIRLINES" in vendor_name.upper():
                item_name_fallback = "Vé máy bay công tác"
            elif vendor_name and ("COFFEE" in vendor_name.upper() or "TRÀ" in vendor_name.upper()):
                item_name_fallback = "Tiếp khách ăn uống"
            elif vendor_name and ("FPT" in vendor_name.upper() or "THIẾT BỊ" in vendor_name.upper()):
                item_name_fallback = "Mua sắm thiết bị vật tư"

            item_amount = subtotal_amount or (total_amount - (vat_amount or 0.0) if total_amount else 1000000.0)
            line_items.append(LineItemSchema(
                item_name=item_name_fallback,
                quantity=1.0,
                unit_price=max(item_amount, 0.0),
                amount=max(item_amount, 0.0),
                vat_rate="10%"
            ))

        doc.close()

        # 7. Tự động phân loại chi phí (Cost Classification)
        upper_text = full_text.upper()
        if any(k in upper_text for k in ["AIRLINES", "VÉ MÁY BAY", "CHUYẾN BAY", "KHÁCH SẠN", "TÀU XE", "TAXI", "FLIGHT"]):
            category = ExpenseCategoryEnum.TRAVEL
        elif any(k in upper_text for k in ["CÀ PHÊ", "COFFEE", "NHÀ HÀNG", "ĂN UỐNG", "TIẾP KHÁCH", "TEA BREAK", "BÁNH"]):
            category = ExpenseCategoryEnum.MEALS
        elif any(k in upper_text for k in ["FPT", "MÁY TÍNH", "LINH KIỆN", "Ổ CỨNG", "CHUỘT", "BÀN PHÍM", "VẬT TƯ", "THIẾT BỊ"]):
            category = ExpenseCategoryEnum.EQUIPMENT
        elif any(k in upper_text for k in ["ĐÀO TẠO", "HỘI THẢO", "KHOÁ HỌC", "TRAINING", "SEMINAR"]):
            category = ExpenseCategoryEnum.TRAINING
        else:
            category = ExpenseCategoryEnum.SERVICES

        # 8. Tự động định khoản Kế toán & Thẩm định ngưỡng không dùng tiền mặt
        debit_acc, vat_acc, credit_acc, cashless = cls.map_accounting_accounts(category, total_amount)

        return InvoiceExtractionResponse(
            invoice_number=invoice_number,
            invoice_date=invoice_date,
            vendor_name=vendor_name,
            tax_code=tax_code,
            line_items=line_items,
            subtotal_amount=subtotal_amount,
            vat_amount=vat_amount,
            total_amount=total_amount,
            category=category,
            confidence_score=0.97,
            notes="Bóc tách siêu tốc bằng PyMuPDF Vector Text Layer (<50ms)",
            raw_text=full_text[:500],
            extraction_method="PYMUPDF_VECTOR_TEXT",
            text_layer_detected=True,
            debit_account=debit_acc,
            vat_account=vat_acc,
            credit_account=credit_acc,
            cashless_required=cashless
        )
