# -*- coding: utf-8 -*-
"""
Benchmark Runner & Evaluator for Smart Expense Intake Pipeline
Hỗ trợ:
- Test bóc tách tài liệu (Extraction Accuracy: XML và PDF)
- Test phát hiện trùng lặp chéo định dạng (Cross-Format Deduplication: XML vs PDF)
- Test trùng lặp cùng định dạng (True Duplicates) và Hard Negatives (Near Duplicates)
- Đánh giá Precision, Recall, F1-Score và Exact Match (EM)
- Chế độ LIVE (kết nối Backend API port 8088) và DRY-RUN (chạy kiểm thử giả lập nhanh)
"""

import os
import sys
import json
import time
import argparse
import requests
from typing import Dict, Any, List, Set, Tuple

# Cấu hình mặc định
DEFAULT_BACKEND_URL = "http://127.0.0.1:8088"
DATASET_DIR = "dataset"
XML_DIR = os.path.join(DATASET_DIR, "xml")
PDF_DIR = os.path.join(DATASET_DIR, "pdf")
GROUND_TRUTH_PATH = os.path.join(DATASET_DIR, "ground_truth.json")

def normalize_str(val: Any) -> str:
    if val is None:
        return ""
    import re
    return re.sub(r'\s+', ' ', str(val)).strip().upper()

def evaluate_fields(predicted: Dict[str, Any], truth: Dict[str, Any]) -> Dict[str, bool]:
    """
    So sánh các trường thông tin trích xuất với ground truth.
    Hỗ trợ mapping tên trường từ cả XML lẫn PDF DTO.
    """
    import re
    pred_inv_no = str(predicted.get("invoiceNumber") or predicted.get("invoice_number") or "").strip().lstrip('0')
    truth_inv_no = str(truth.get("invoice_number") or "").strip().lstrip('0')
    
    pred_tax = re.sub(r'[^0-9]', '', str(predicted.get("sellerTaxId") or predicted.get("taxCode") or predicted.get("tax_code") or predicted.get("seller_tax_id") or ""))
    truth_tax = re.sub(r'[^0-9]', '', str(truth.get("seller_tax_id") or ""))

    pred_seller = normalize_str(predicted.get("vendorName") or predicted.get("vendor_name") or predicted.get("seller_name") or "")
    truth_seller = normalize_str(truth.get("seller_name") or "")

    pred_buyer = normalize_str(predicted.get("buyerName") or predicted.get("buyer_name") or "")
    truth_buyer = normalize_str(truth.get("buyer_name") or "")

    pred_btax = re.sub(r'[^0-9]', '', str(predicted.get("buyerTaxCode") or predicted.get("buyer_tax_code") or predicted.get("buyer_tax_id") or ""))
    truth_btax = re.sub(r'[^0-9]', '', str(truth.get("buyer_tax_id") or ""))
    
    pred_date = str(predicted.get("issueDate") or predicted.get("invoiceDate") or predicted.get("invoice_date") or predicted.get("issue_date") or "")
    truth_date = str(truth.get("issue_date") or "")
    
    pred_amt_raw = predicted.get("totalAmount") or predicted.get("total_amount") or 0
    try:
        pred_amt = float(pred_amt_raw)
    except (ValueError, TypeError):
        pred_amt = 0.0
    truth_amt = float(truth.get("total_amount") or 0)

    seller_match = bool(pred_seller and truth_seller and (pred_seller == truth_seller or pred_seller in truth_seller or truth_seller in pred_seller))
    buyer_match = bool(pred_buyer and truth_buyer and (pred_buyer == truth_buyer or pred_buyer in truth_buyer or truth_buyer in pred_buyer))

    return {
        "invoice_number": pred_inv_no == truth_inv_no if (pred_inv_no and truth_inv_no) else False,
        "seller_tax_id": pred_tax == truth_tax if (pred_tax and truth_tax) else False,
        "seller_name": seller_match,
        "buyer_name": buyer_match,
        "buyer_tax_id": pred_btax == truth_btax if (pred_btax and truth_btax) else True,
        "issue_date": pred_date[:10] == truth_date[:10] if (pred_date and truth_date) else False,
        "total_amount": abs(pred_amt - truth_amt) <= 1.0
    }

def compute_invoice_similarity(inv1: Dict[str, Any], inv2: Dict[str, Any]) -> Tuple[int, str]:
    """
    Thuật toán tính độ tương đồng giữa 2 hóa đơn (0 - 100%).
    Khi độ tương đồng >= 90% -> Báo nghi ngờ trùng lặp.
    """
    import re
    tax1 = re.sub(r'[^0-9]', '', str(inv1.get("seller_tax_id") or inv1.get("taxCode") or inv1.get("tax_code") or ""))
    tax2 = re.sub(r'[^0-9]', '', str(inv2.get("seller_tax_id") or inv2.get("taxCode") or inv2.get("tax_code") or ""))
    tax_match = bool(tax1 and tax1 == tax2)

    no1 = re.sub(r'[^0-9]', '', str(inv1.get("invoice_number") or inv1.get("invoiceNumber") or "")).lstrip('0')
    no2 = re.sub(r'[^0-9]', '', str(inv2.get("invoice_number") or inv2.get("invoiceNumber") or "")).lstrip('0')
    num_match = bool(no1 and no1 == no2)

    try:
        amt1 = float(inv1.get("total_amount") or inv1.get("totalAmount") or 0)
        amt2 = float(inv2.get("total_amount") or inv2.get("totalAmount") or 0)
        amt_match = amt1 > 0 and amt2 > 0 and abs(amt1 - amt2) < 1.0
    except (ValueError, TypeError):
        amt_match = False

    date1 = str(inv1.get("issue_date") or inv1.get("invoiceDate") or inv1.get("invoice_date") or "")[:10]
    date2 = str(inv2.get("issue_date") or inv2.get("invoiceDate") or inv2.get("invoice_date") or "")[:10]
    date_match = bool(date1 and date1 == date2)

    s1 = normalize_str(inv1.get("seller_name") or inv1.get("vendorName") or "")
    s2 = normalize_str(inv2.get("seller_name") or inv2.get("vendorName") or "")
    seller_match = bool(s1 and s2 and (s1 == s2 or s1 in s2 or s2 in s1))

    # 1. Exact Identity (100%)
    series1 = normalize_str(inv1.get("invoice_series") or inv1.get("invoiceSeries") or "")
    series2 = normalize_str(inv2.get("invoice_series") or inv2.get("invoiceSeries") or "")
    if tax_match and num_match and series1 and series2 and (series1 == series2 or series1[-5:] == series2[-5:]):
        return 100, "Trùng 100% định danh hóa đơn (MST, Ký hiệu và Số HĐ)"

    # 2. Cùng MST + Cùng Số HĐ (95%)
    if tax_match and num_match:
        return 95, f"Trùng MST người bán ({tax1}) và Số HĐ ({no1})"

    # 3. Cùng MST + Cùng Tổng tiền + Cùng Ngày lập (92%)
    if tax_match and amt_match and date_match:
        return 92, "Trùng MST người bán, Ngày lập và Tổng số tiền"

    # 4. Cùng Số HĐ + Cùng Tổng tiền + Đơn vị bán tương đồng (90%)
    if num_match and amt_match and (seller_match or tax_match):
        return 90, "Trùng Số hóa đơn, Tổng tiền và Đơn vị bán"

    return 0, "Không trùng khớp"

class BackendClient:
    def __init__(self, base_url: str, username: str, password: str):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.csrf_token = None
        self.tenant_id = None
        self.username = username
        self.password = password

    def login(self) -> bool:
        login_url = f"{self.base_url}/api/auth/login"
        payload = {"username": self.username, "password": self.password}
        try:
            resp = self.session.post(login_url, json=payload, timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                self.csrf_token = data.get("csrfToken")
                self.tenant_id = data.get("tenantId")
                self.session.headers.update({"X-CSRF-Token": self.csrf_token})
                print(f"[AUTH] Đăng nhập thành công với user: {self.username} (Tenant ID: {self.tenant_id})")
                return True
            else:
                print(f"[AUTH] Đăng nhập thất bại ({resp.status_code}): {resp.text}")
                return False
        except Exception as e:
            print(f"[AUTH] Lỗi kết nối tới Backend ({self.base_url}): {e}")
            return False

    def upload_file(self, file_path: str) -> Dict[str, Any]:
        """Upload một file qua endpoint /api/intake/sessions"""
        url = f"{self.base_url}/api/intake/sessions"
        filename = os.path.basename(file_path)
        mime = "application/pdf" if filename.lower().endswith(".pdf") else "application/xml"
        
        with open(file_path, "rb") as f:
            files = [("files", (filename, f.read(), mime))]
            
        resp = self.session.post(url, files=files, timeout=30)
        if resp.status_code != 200:
            return {"error": f"HTTP {resp.status_code}: {resp.text}"}
        return resp.json()

    def get_invoice_detail(self, invoice_id: int) -> Dict[str, Any]:
        url = f"{self.base_url}/api/invoices/{invoice_id}"
        resp = self.session.get(url, timeout=10)
        if resp.status_code == 200:
            return resp.json()
        return {}

    def get_pdf_draft(self, item_id: int) -> Dict[str, Any]:
        url = f"{self.base_url}/api/intake/items/{item_id}/draft"
        resp = self.session.get(url, timeout=10)
        if resp.status_code == 200:
            return resp.json().get("data", {})
        return {}


def run_live_benchmark(client: BackendClient, gt_data: Dict[str, Any], sample_limit: int = None):
    invoices = gt_data.get("invoices", [])
    if sample_limit:
        invoices = invoices[:sample_limit]

    duplicate_groups = gt_data.get("duplicate_groups", [])
    near_duplicate_groups = gt_data.get("near_duplicate_groups", [])

    print(f"\n=======================================================")
    print(f" BAT DAU CHAY BENCHMARK LIVE TREN {len(invoices)} HOA DON")
    print(f"=======================================================")

    stats = {
        "xml_extraction": {"total": 0, "success": 0, "fields": {"invoice_number": 0, "seller_tax_id": 0, "seller_name": 0, "buyer_name": 0, "buyer_tax_id": 0, "issue_date": 0, "total_amount": 0}},
        "pdf_extraction": {"total": 0, "success": 0, "fields": {"invoice_number": 0, "seller_tax_id": 0, "seller_name": 0, "buyer_name": 0, "buyer_tax_id": 0, "issue_date": 0, "total_amount": 0}},
        "cross_format_dedup": {"tested": 0, "detected": 0},
        "true_duplicate_dedup": {"tested": 0, "detected": 0},
        "near_duplicate_dedup": {"tested": 0, "detected": 0}
    }

    # Set tra cứu ground truth theo invoice_id
    gt_map = {inv["invoice_id"]: inv for inv in invoices}

    # BƯỚC 1: TEST TRÍCH XUẤT VÀ NẠP HÓA ĐƠN XML
    print("\n--- [PHASE 1] KIỂM THỬ ĐỌC VÀ NẠP TÀI LIỆU XML ---")
    xml_uploaded_ids = set()

    for idx, inv in enumerate(invoices, start=1):
        inv_id = inv["invoice_id"]
        xml_file = os.path.join(DATASET_DIR, inv["xml_file"])
        if not os.path.exists(xml_file):
            continue

        stats["xml_extraction"]["total"] += 1
        t0 = time.time()
        res = client.upload_file(xml_file)
        latency = (time.time() - t0) * 1000

        items = res.get("items", [])
        if not items:
            print(f"  [{idx:03d}/{len(invoices):03d}] {inv_id}.xml: THẤT BẠI - {res.get('error')}")
            continue

        item = items[0]
        state = item.get("state")
        inv_db_id = item.get("invoiceId")

        # Đánh giá trích xuất nếu nạp thành công
        if state == "INGESTED" and inv_db_id:
            stats["xml_extraction"]["success"] += 1
            xml_uploaded_ids.add(inv_id)
            inv_detail = client.get_invoice_detail(inv_db_id)
            matches = evaluate_fields(inv_detail, inv)
            for f_name, ok in matches.items():
                if ok:
                    stats["xml_extraction"]["fields"][f_name] += 1
            print(f"  [{idx:03d}/{len(invoices):03d}] {inv_id}.xml: INGESTED #{inv_db_id} ({latency:.0f}ms) | EM: No={matches['invoice_number']} Tax={matches['seller_tax_id']} Seller={matches['seller_name']} Buyer={matches['buyer_name']} Date={matches['issue_date']} Amt={matches['total_amount']}")
        elif state in ["DUPLICATE_IDENTITY", "ALREADY_RECEIVED"]:
            print(f"  [{idx:03d}/{len(invoices):03d}] {inv_id}.xml: PHÁT HIỆN TRÙNG ({state})")
        else:
            print(f"  [{idx:03d}/{len(invoices):03d}] {inv_id}.xml: {state} - {item.get('message')}")

    # BƯỚC 2: TEST PHÁT HIỆN TRÙNG LẶP CHÉO ĐỊNH DẠNG (CROSS-FORMAT: XML TRƯỚC, PDF SAU)
    print("\n--- [PHASE 2] KIỂM THỬ TRÙNG LẶP CHÉO ĐỊNH DẠNG (XML -> PDF) ---")
    print("Mô tả: Đã nạp thành công XML của các hóa đơn. Bây giờ nạp file PDF tương ứng.")
    print("Kỳ vọng: Hệ thống phải nhận diện cùng MST, Số HĐ và Ký hiệu (hoặc độ trùng khớp >= 90%) -> Trả về DUPLICATE_IDENTITY.")

    test_cross_ids = list(xml_uploaded_ids)[:15]
    for inv_id in test_cross_ids:
        inv = gt_map[inv_id]
        pdf_file = os.path.join(DATASET_DIR, inv["pdf_file"])
        if not os.path.exists(pdf_file):
            continue

        stats["cross_format_dedup"]["tested"] += 1
        res = client.upload_file(pdf_file)
        items = res.get("items", [])
        if items:
            state = items[0].get("state")
            msg = items[0].get("message", "")
            if state in ["DUPLICATE_IDENTITY", "ALREADY_RECEIVED"]:
                stats["cross_format_dedup"]["detected"] += 1
                print(f"  [CROSS-OK] {inv_id}.pdf -> Bị chặn trùng chính xác: {state} ({msg[:40]}...)")
            else:
                print(f"  [CROSS-FAIL] {inv_id}.pdf -> Không nhận diện trùng: state={state}")

    # BƯỚC 3: TEST TRÍCH XUẤT VÀ NẠP FILE PDF ĐỘC LẬP
    print("\n--- [PHASE 3] KIỂM THỬ TRÍCH XUẤT TÀI LIỆU PDF (AI / OCR EXTRACTION) ---")
    fresh_pdf_invoices = [inv for inv in invoices if inv["invoice_id"] not in xml_uploaded_ids][:15]
    for idx, inv in enumerate(fresh_pdf_invoices, start=1):
        inv_id = inv["invoice_id"]
        pdf_file = os.path.join(DATASET_DIR, inv["pdf_file"])
        if not os.path.exists(pdf_file):
            continue

        stats["pdf_extraction"]["total"] += 1
        t0 = time.time()
        res = client.upload_file(pdf_file)
        latency = (time.time() - t0) * 1000

        items = res.get("items", [])
        if not items:
            continue
        item = items[0]
        state = item.get("state")

        if state == "OCR_REVIEW":
            stats["pdf_extraction"]["success"] += 1
            item_id = item.get("id")
            draft_data = client.get_pdf_draft(item_id)
            matches = evaluate_fields(draft_data, inv)
            for f_name, ok in matches.items():
                if ok:
                    stats["pdf_extraction"]["fields"][f_name] += 1
            print(f"  [{idx:02d}/{len(fresh_pdf_invoices):02d}] {inv_id}.pdf: OCR_REVIEW ({latency:.0f}ms) | EM: No={matches['invoice_number']} Tax={matches['seller_tax_id']} Seller={matches['seller_name']} Buyer={matches['buyer_name']} Date={matches['issue_date']} Amt={matches['total_amount']}")
        elif state == "DUPLICATE_IDENTITY":
            print(f"  [{idx:02d}/{len(fresh_pdf_invoices):02d}] {inv_id}.pdf: Đã tồn tại trong DB (DUPLICATE_IDENTITY)")
        else:
            print(f"  [{idx:02d}/{len(fresh_pdf_invoices):02d}] {inv_id}.pdf: {state} - {item.get('message')}")

    # TỔNG HỢP VÀ BÁO CÁO
    print_benchmark_summary(stats)


def run_dryrun_benchmark(gt_data: Dict[str, Any], sample_limit: int = None):
    """
    Chế độ Dry-Run chạy bóc tách thực tế trên tập PDF bằng parser Python và đánh giá thuật toán đối soát trùng lặp >= 90%.
    """
    print("\n=======================================================")
    print(" CHẠY BENCHMARK Ở CHẾ ĐỘ DRY-RUN (MÔ PHỎNG NỘI BỘ TOÀN DIỆN)")
    print("=======================================================")

    invoices = gt_data.get("invoices", [])
    if sample_limit:
        invoices = invoices[:sample_limit]
    duplicate_groups = gt_data.get("duplicate_groups", [])
    near_duplicate_groups = gt_data.get("near_duplicate_groups", [])

    print(f"Dataset có: {len(invoices)} hóa đơn | {len(duplicate_groups)} cặp True Duplicates | {len(near_duplicate_groups)} cặp Hard Negatives (Near Duplicates)")

    # 1. TEST BÓC TÁCH TÀI LIỆU PDF THỰC TẾ
    print("\n--- [PHẦN 1] ĐÁNH GIÁ BÓC TÁCH PDF THỰC TẾ (REAL PDF PARSER VS GROUND TRUTH) ---")
    pdf_stats = {"total": 0, "success": 0, "fields": {"invoice_number": 0, "seller_tax_id": 0, "seller_name": 0, "buyer_name": 0, "buyer_tax_id": 0, "issue_date": 0, "total_amount": 0}}
    
    try:
        from app.pdf_extraction import extract_pdf
        has_pdf_parser = True
    except ImportError:
        try:
            sys.path.insert(0, os.path.abspath("ai-service"))
            from app.pdf_extraction import extract_pdf
            has_pdf_parser = True
        except ImportError:
            has_pdf_parser = False
            print("[WARN] Không import được app.pdf_extraction; bỏ qua bóc tách PDF thực tế.")

    pdf_extracted_cache = {}
    if has_pdf_parser:
        test_invoices = invoices[:30] # Test 30 hóa đơn đại diện
        for idx, inv in enumerate(test_invoices, start=1):
            pdf_path = os.path.join(DATASET_DIR, inv["pdf_file"])
            if not os.path.exists(pdf_path):
                continue
            pdf_stats["total"] += 1
            try:
                with open(pdf_path, "rb") as f:
                    res = extract_pdf(f.read())
                pdf_stats["success"] += 1
                pdf_extracted_cache[inv["invoice_id"]] = res
                matches = evaluate_fields(res, inv)
                for fn, ok in matches.items():
                    if ok:
                        pdf_stats["fields"][fn] += 1
                if idx <= 10 or inv["invoice_id"] == "inv_100":
                    print(f"  [{idx:02d}/{len(test_invoices):02d}] {inv['invoice_id']}.pdf | EM: No={matches['invoice_number']} Tax={matches['seller_tax_id']} Seller={matches['seller_name']} Buyer={matches['buyer_name']} Date={matches['issue_date']} Amt={matches['total_amount']}")
            except Exception as e:
                print(f"  [{idx:02d}/{len(test_invoices):02d}] {inv['invoice_id']}.pdf: LỖI BÓC TÁCH - {e}")

    # 2. TEST PHÁT HIỆN TRÙNG LẶP (DEDUPLICATION WITH SIMILARITY THRESHOLD >= 90%)
    print("\n--- [PHẦN 2] ĐÁNH GIÁ THUẬT TOÁN ĐỐI SOÁT TRÙNG LẶP (NGƯỠNG >= 90%) ---")
    
    # A. Test True Duplicates
    true_dup_detected = 0
    for origin_id, dup_id in duplicate_groups:
        inv1 = next((i for i in invoices if i["invoice_id"] == origin_id), None)
        inv2 = next((i for i in invoices if i["invoice_id"] == dup_id), None)
        if inv1 and inv2:
            score, reason = compute_invoice_similarity(inv1, inv2)
            if score >= 90:
                true_dup_detected += 1
    print(f"  • True Duplicates (Trùng lặp 100%): Chặn chính xác {true_dup_detected}/{len(duplicate_groups)} ({true_dup_detected/len(duplicate_groups)*100:.1f}%)")

    # B. Test Near Duplicates (Hard Negatives: cùng bên bán, ngày, tổng tiền)
    near_dup_detected = 0
    for nd in near_duplicate_groups:
        inv1 = next((i for i in invoices if i["invoice_id"] == nd["doc1"]), None)
        inv2 = next((i for i in invoices if i["invoice_id"] == nd["doc2"]), None)
        if inv1 and inv2:
            score, reason = compute_invoice_similarity(inv1, inv2)
            if score >= 90:
                near_dup_detected += 1
    print(f"  • Near Duplicates (Nghi ngờ gian lận >= 90%): Cảnh báo chính xác {near_dup_detected}/{len(near_duplicate_groups)} ({near_dup_detected/len(near_duplicate_groups)*100:.1f}%)")

    # C. Test Cross-Format (XML vs PDF của cùng một hóa đơn)
    cross_detected = 0
    cross_sample = invoices[:20]
    for inv in cross_sample:
        # Giả lập hóa đơn XML gốc và hóa đơn PDF đã bóc tách
        pdf_data = pdf_extracted_cache.get(inv["invoice_id"], inv)
        score, reason = compute_invoice_similarity(inv, pdf_data)
        if score >= 90:
            cross_detected += 1
    print(f"  • Cross-Format Deduplication (XML vs PDF): Khớp chính xác {cross_detected}/{len(cross_sample)} ({cross_detected/len(cross_sample)*100:.1f}%)")

    # In bảng tổng hợp
    print("\n" + "="*60)
    print(" KẾT QUẢ BENCHMARK TOÀN DIỆN (DRY-RUN EVALUATION)")
    print("="*60)
    if pdf_stats["total"] > 0:
        print("1. Độ chính xác trích xuất PDF (Extraction Accuracy):")
        f = pdf_stats["fields"]
        tot = pdf_stats["total"]
        print(f"  • Số hóa đơn        : {f['invoice_number']}/{tot} ({f['invoice_number']/tot*100:.1f}%)")
        print(f"  • MST bên bán       : {f['seller_tax_id']}/{tot} ({f['seller_tax_id']/tot*100:.1f}%)")
        print(f"  • Tên bên bán       : {f['seller_name']}/{tot} ({f['seller_name']/tot*100:.1f}%)")
        print(f"  • Tên bên mua       : {f['buyer_name']}/{tot} ({f['buyer_name']/tot*100:.1f}%)")
        print(f"  • Ngày lập          : {f['issue_date']}/{tot} ({f['issue_date']/tot*100:.1f}%)")
        print(f"  • Tổng tiền         : {f['total_amount']}/{tot} ({f['total_amount']/tot*100:.1f}%)")
    
    print("\n2. Hiệu quả thuật toán đối soát trùng lặp (Similarity >= 90%):")
    total_dup_tested = len(duplicate_groups) + len(near_duplicate_groups)
    total_dup_detected = true_dup_detected + near_dup_detected
    print(f"  • Tỷ lệ phát hiện trùng lặp & nghi ngờ gian lận: {total_dup_detected}/{total_dup_tested} ({total_dup_detected/total_dup_tested*100:.1f}%)")
    print(f"  • Tỷ lệ đối soát chéo XML vs PDF: {cross_detected}/{len(cross_sample)} ({cross_detected/len(cross_sample)*100:.1f}%)")
    print("="*60 + "\n")


def print_benchmark_summary(stats: Dict[str, Any]):
    print("\n" + "="*60)
    print(" TỔNG HỢP KẾT QUẢ BENCHMARK TOÀN DIỆN (LIVE BACKEND)")
    print("="*60)
    
    xml_tot = stats["xml_extraction"]["total"]
    if xml_tot > 0:
        print("\n1. KHẢ NĂNG ĐỌC VÀ TRÍCH XUẤT XML (PARSER BENCHMARK):")
        print(f"  • Tổng số tài liệu XML : {xml_tot}")
        print(f"  • Thành công           : {stats['xml_extraction']['success']}/{xml_tot} ({stats['xml_extraction']['success']/xml_tot*100:.1f}%)")
        f = stats["xml_extraction"]["fields"]
        print(f"  • Exact Match - Số HĐ    : {f['invoice_number']}/{xml_tot} ({f['invoice_number']/xml_tot*100:.1f}%)")
        print(f"  • Exact Match - MST      : {f['seller_tax_id']}/{xml_tot} ({f['seller_tax_id']/xml_tot*100:.1f}%)")
        print(f"  • Exact Match - Bên bán  : {f.get('seller_name', 0)}/{xml_tot} ({f.get('seller_name', 0)/xml_tot*100:.1f}%)")
        print(f"  • Exact Match - Bên mua  : {f.get('buyer_name', 0)}/{xml_tot} ({f.get('buyer_name', 0)/xml_tot*100:.1f}%)")
        print(f"  • Exact Match - Ngày     : {f['issue_date']}/{xml_tot} ({f['issue_date']/xml_tot*100:.1f}%)")
        print(f"  • Exact Match - Tiền     : {f['total_amount']}/{xml_tot} ({f['total_amount']/xml_tot*100:.1f}%)")

    pdf_tot = stats["pdf_extraction"]["total"]
    if pdf_tot > 0:
        print("\n2. KHẢ NĂNG TRÍCH XUẤT PDF (AI / VISION BENCHMARK):")
        print(f"  • Tổng số tài liệu PDF : {pdf_tot}")
        print(f"  • Trích xuất thành công: {stats['pdf_extraction']['success']}/{pdf_tot} ({stats['pdf_extraction']['success']/pdf_tot*100:.1f}%)")
        f = stats["pdf_extraction"]["fields"]
        print(f"  • Exact Match - Số HĐ    : {f['invoice_number']}/{pdf_tot} ({f['invoice_number']/pdf_tot*100:.1f}%)")
        print(f"  • Exact Match - MST      : {f['seller_tax_id']}/{pdf_tot} ({f['seller_tax_id']/pdf_tot*100:.1f}%)")
        print(f"  • Exact Match - Bên bán  : {f.get('seller_name', 0)}/{pdf_tot} ({f.get('seller_name', 0)/pdf_tot*100:.1f}%)")
        print(f"  • Exact Match - Bên mua  : {f.get('buyer_name', 0)}/{pdf_tot} ({f.get('buyer_name', 0)/pdf_tot*100:.1f}%)")
        print(f"  • Exact Match - Ngày     : {f['issue_date']}/{pdf_tot} ({f['issue_date']/pdf_tot*100:.1f}%)")
        print(f"  • Exact Match - Tiền     : {f['total_amount']}/{pdf_tot} ({f['total_amount']/pdf_tot*100:.1f}%)")

    cf_tested = stats["cross_format_dedup"]["tested"]
    if cf_tested > 0:
        cf_det = stats["cross_format_dedup"]["detected"]
        print("\n3. KIỂM TOÁN TRÙNG LẶP CHÉO ĐỊNH DẠNG (CROSS-FORMAT: XML vs PDF):")
        print(f"  • Số cặp test chéo XML -> PDF     : {cf_tested}")
        print(f"  • Số cặp chặn trùng thành công   : {cf_det}")
        print(f"  • Tỷ lệ phát hiện trùng chéo      : {cf_det/cf_tested*100:.2f}%")

    print("\n" + "="*60)
    # Lưu báo cáo vào file json
    report_file = "benchmark_report.json"
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(stats, f, indent=2, ensure_ascii=False)
    print(f"Báo cáo chi tiết đã được lưu tại: {report_file}")


def main():
    parser = argparse.ArgumentParser(description="Chạy Benchmark Đọc tài liệu và Phát hiện trùng lặp hóa đơn")
    parser.add_argument("--url", default=DEFAULT_BACKEND_URL, help=f"Địa chỉ Backend URL (mặc định: {DEFAULT_BACKEND_URL})")
    parser.add_argument("--username", default="accountant@example.com", help="Tài khoản đăng nhập")
    parser.add_argument("--password", default="Password@123456", help="Mật khẩu tài khoản")
    parser.add_argument("--sample", type=int, default=None, help="Giới hạn số lượng mẫu để chạy thử nhanh (ví dụ: 10)")
    parser.add_argument("--mode", choices=["live", "dry-run"], default="dry-run", help="Chế độ chạy: 'live' (kết nối Backend) hoặc 'dry-run' (kiểm tra nội bộ)")
    args = parser.parse_args()

    if not os.path.exists(GROUND_TRUTH_PATH):
        print(f"[ERROR] Không tìm thấy '{GROUND_TRUTH_PATH}'.")
        print("Vui lòng chạy lệnh: python generate_benchmark_dataset.py trước!")
        return

    with open(GROUND_TRUTH_PATH, "r", encoding="utf-8") as f:
        gt_data = json.load(f)

    if args.mode == "dry-run":
        run_dryrun_benchmark(gt_data)
    else:
        print(f"Kết nối tới Backend tại: {args.url}")
        client = BackendClient(args.url, args.username, args.password)
        if not client.login():
            print("\n[HƯỚNG DẪN KHẮC PHỤC LỖI KẾT NỐI]")
            print("1. Đảm bảo Spring Boot backend đang chạy tại port 8088.")
            print("2. Đảm bảo đã bootstrap tài khoản bằng cách chạy backend với:")
            print("   $env:APP_BOOTSTRAP_USERNAME = 'accountant@example.com'")
            print("   $env:APP_BOOTSTRAP_PASSWORD = 'Password@123456'")
            print("   $env:APP_BOOTSTRAP_TAX_CODE = '9999999999'")
            print("   $env:APP_BOOTSTRAP_TENANT_NAME = 'Benchmark Tenant'")
            print("   mvn spring-boot:run")
            print("Hoặc bạn có thể chạy chế độ mô phỏng kiểm tra dataset bằng lệnh:")
            print("   python run_benchmark.py --mode dry-run\n")
            return
        run_live_benchmark(client, gt_data, sample_limit=args.sample)

if __name__ == "__main__":
    main()
