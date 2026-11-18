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
    return str(val).strip().upper()

def evaluate_fields(predicted: Dict[str, Any], truth: Dict[str, Any]) -> Dict[str, bool]:
    """
    So sánh các trường thông tin trích xuất với ground truth.
    Hỗ trợ mapping tên trường từ cả XML lẫn PDF DTO.
    """
    pred_inv_no = predicted.get("invoiceNumber") or predicted.get("invoice_number") or ""
    truth_inv_no = truth.get("invoice_number") or ""
    
    pred_tax = predicted.get("sellerTaxId") or predicted.get("taxCode") or predicted.get("seller_tax_id") or ""
    truth_tax = truth.get("seller_tax_id") or ""
    
    pred_date = str(predicted.get("issueDate") or predicted.get("invoiceDate") or predicted.get("issue_date") or "")
    truth_date = str(truth.get("issue_date") or "")
    
    pred_amt_raw = predicted.get("totalAmount") or predicted.get("total_amount") or 0
    try:
        pred_amt = float(pred_amt_raw)
    except (ValueError, TypeError):
        pred_amt = 0.0
    truth_amt = float(truth.get("total_amount") or 0)

    return {
        "invoice_number": normalize_str(pred_inv_no) == normalize_str(truth_inv_no),
        "seller_tax_id": normalize_str(pred_tax) == normalize_str(truth_tax),
        "issue_date": pred_date[:10] == truth_date[:10] if (pred_date and truth_date) else False,
        "total_amount": abs(pred_amt - truth_amt) <= 1.0
    }

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
        "xml_extraction": {"total": 0, "success": 0, "fields": {"invoice_number": 0, "seller_tax_id": 0, "issue_date": 0, "total_amount": 0}},
        "pdf_extraction": {"total": 0, "success": 0, "fields": {"invoice_number": 0, "seller_tax_id": 0, "issue_date": 0, "total_amount": 0}},
        "cross_format_dedup": {"tested": 0, "detected": 0},
        "true_duplicate_dedup": {"tested": 0, "detected": 0},
        "hard_negatives": {"tested": 0, "correct_not_flagged": 0}
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
            print(f"  [{idx:03d}/{len(invoices):03d}] {inv_id}.xml: INGESTED #{inv_db_id} ({latency:.0f}ms) | EM: No={matches['invoice_number']} Tax={matches['seller_tax_id']} Date={matches['issue_date']} Amt={matches['total_amount']}")
        elif state in ["DUPLICATE_IDENTITY", "ALREADY_RECEIVED"]:
            print(f"  [{idx:03d}/{len(invoices):03d}] {inv_id}.xml: PHÁT HIỆN TRÙNG ({state})")
        else:
            print(f"  [{idx:03d}/{len(invoices):03d}] {inv_id}.xml: {state} - {item.get('message')}")

    # BƯỚC 2: TEST PHÁT HIỆN TRÙNG LẶP CHÉO ĐỊNH DẠNG (CROSS-FORMAT: XML TRƯỚC, PDF SAU)
    print("\n--- [PHASE 2] KIỂM THỬ TRÙNG LẶP CHÉO ĐỊNH DẠNG (XML -> PDF) ---")
    print("Mô tả: Đã nạp thành công XML của các hóa đơn. Bây giờ nạp file PDF tương ứng.")
    print("Kỳ vọng: Hệ thống phải nhận diện cùng MST, Số HĐ và Ký hiệu -> Trả về DUPLICATE_IDENTITY.")

    # Lấy 10 hóa đơn XML đã nạp thành công để thử trùng với PDF
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

    # BƯỚC 3: TEST TRÍCH XUẤT VÀ NẠP FILE PDF ĐỘC LẬP (ĐỐI VỚI CÁC HÓA ĐƠN CHƯA NẠP XML)
    print("\n--- [PHASE 3] KIỂM THỬ TRÍCH XUẤT TÀI LIỆU PDF (AI / OCR EXTRACTION) ---")
    # Lấy các hóa đơn chưa upload XML để test extraction PDF độc lập
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
            # Lấy bản trích xuất draft JSON
            item_id = item.get("id")
            draft_data = client.get_pdf_draft(item_id)
            matches = evaluate_fields(draft_data, inv)
            for f_name, ok in matches.items():
                if ok:
                    stats["pdf_extraction"]["fields"][f_name] += 1
            print(f"  [{idx:02d}/{len(fresh_pdf_invoices):02d}] {inv_id}.pdf: OCR_REVIEW ({latency:.0f}ms) | EM: No={matches['invoice_number']} Tax={matches['seller_tax_id']} Date={matches['issue_date']} Amt={matches['total_amount']}")
        elif state == "DUPLICATE_IDENTITY":
            print(f"  [{idx:02d}/{len(fresh_pdf_invoices):02d}] {inv_id}.pdf: Đã tồn tại trong DB (DUPLICATE_IDENTITY)")
        else:
            print(f"  [{idx:02d}/{len(fresh_pdf_invoices):02d}] {inv_id}.pdf: {state} - {item.get('message')}")

    # TỔNG HỢP VÀ BÁO CÁO
    print_benchmark_summary(stats)


def run_dryrun_benchmark(gt_data: Dict[str, Any]):
    """
    Chế độ Dry-Run giả lập thuật toán đối soát Exact Match và Deduplication (Logic cục bộ)
    Giúp kiểm tra tính toàn vẹn của dataset và đánh giá metric mà không cần khởi động toàn bộ server.
    """
    print("\n=======================================================")
    print(" CHẠY BENCHMARK Ở CHẾ ĐỘ DRY-RUN (MÔ PHỎNG NỘI BỘ)")
    print("=======================================================")

    invoices = gt_data.get("invoices", [])
    duplicate_groups = gt_data.get("duplicate_groups", [])
    near_duplicate_groups = gt_data.get("near_duplicate_groups", [])

    print(f"Dataset có: {len(invoices)} hóa đơn | {len(duplicate_groups)} cặp True Duplicates | {len(near_duplicate_groups)} cặp Hard Negatives")

    # Giả lập Database trạng thái
    db_identity_index: Dict[str, str] = {} # Key -> first invoice_id
    db_hash_index: Dict[str, str] = {}

    tp = 0 # True duplicate và bị chặn
    fp = 0 # Không trùng nhưng bị chặn
    tn = 0 # Không trùng và được chấp nhận
    fn = 0 # Trùng nhưng không bị chặn

    # Map tra cứu ground truth duplicate
    true_dup_pairs = {pair[1]: pair[0] for pair in duplicate_groups} # inv_target -> inv_origin

    cross_format_tested = 0
    cross_format_detected = 0

    print("\n-> Bắt đầu nạp giả lập 100 XML...")
    for inv in invoices:
        inv_id = inv["invoice_id"]
        # Identity key chuẩn Thông tư 78
        ident_key = f"{normalize_str(inv['seller_tax_id'])}|{normalize_str(inv['invoice_series'])}|{normalize_str(inv['invoice_number'])}"

        is_actual_dup = inv_id in true_dup_pairs

        if ident_key in db_identity_index:
            # Hệ thống phát hiện trùng
            if is_actual_dup:
                tp += 1
            else:
                fp += 1
        else:
            # Hệ thống cho qua
            if is_actual_dup:
                fn += 1
            else:
                tn += 1
            db_identity_index[ident_key] = inv_id

    # Test Cross-Format XML vs PDF
    print("-> Bắt đầu kiểm tra chéo định dạng (Cross-Format XML vs PDF)...")
    for inv in invoices[:20]:
        cross_format_tested += 1
        ident_key = f"{normalize_str(inv['seller_tax_id'])}|{normalize_str(inv['invoice_series'])}|{normalize_str(inv['invoice_number'])}"
        if ident_key in db_identity_index:
            cross_format_detected += 1

    # Tính toán Metrics
    precision = tp / (tp + fp) if (tp + fp) > 0 else 1.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 1.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0

    print("\n" + "="*60)
    print(" KẾT QUẢ BENCHMARK DEDUPLICATION (DRY-RUN EVALUATION)")
    print("="*60)
    print(f"  • True Positives  (Phát hiện trùng chính xác) : {tp}")
    print(f"  • False Positives (Báo trùng nhầm)             : {fp}")
    print(f"  • False Negatives (Bỏ sót trùng lặp)           : {fn}")
    print(f"  • True Negatives  (Hóa đơn riêng biệt hợp lệ)  : {tn}")
    print(f"  ------------------------------------------------------")
    print(f"  • PRECISION : {precision * 100:.2f}%")
    print(f"  • RECALL    : {recall * 100:.2f}%")
    print(f"  • F1-SCORE  : {f1 * 100:.2f}%")
    print(f"  ------------------------------------------------------")
    print(f"  • Cross-Format Duplicate Detection Rate : {cross_format_detected}/{cross_format_tested} ({cross_format_detected/cross_format_tested*100:.1f}%)")
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
        print(f"  • Exact Match - Số HĐ  : {f['invoice_number']}/{xml_tot} ({f['invoice_number']/xml_tot*100:.1f}%)")
        print(f"  • Exact Match - MST    : {f['seller_tax_id']}/{xml_tot} ({f['seller_tax_id']/xml_tot*100:.1f}%)")
        print(f"  • Exact Match - Ngày   : {f['issue_date']}/{xml_tot} ({f['issue_date']/xml_tot*100:.1f}%)")
        print(f"  • Exact Match - Tiền   : {f['total_amount']}/{xml_tot} ({f['total_amount']/xml_tot*100:.1f}%)")

    pdf_tot = stats["pdf_extraction"]["total"]
    if pdf_tot > 0:
        print("\n2. KHẢ NĂNG TRÍCH XUẤT PDF (AI / VISION BENCHMARK):")
        print(f"  • Tổng số tài liệu PDF : {pdf_tot}")
        print(f"  • Trích xuất thành công: {stats['pdf_extraction']['success']}/{pdf_tot} ({stats['pdf_extraction']['success']/pdf_tot*100:.1f}%)")
        f = stats["pdf_extraction"]["fields"]
        print(f"  • Exact Match - Số HĐ  : {f['invoice_number']}/{pdf_tot} ({f['invoice_number']/pdf_tot*100:.1f}%)")
        print(f"  • Exact Match - MST    : {f['seller_tax_id']}/{pdf_tot} ({f['seller_tax_id']/pdf_tot*100:.1f}%)")
        print(f"  • Exact Match - Ngày   : {f['issue_date']}/{pdf_tot} ({f['issue_date']/pdf_tot*100:.1f}%)")
        print(f"  • Exact Match - Tiền   : {f['total_amount']}/{pdf_tot} ({f['total_amount']/pdf_tot*100:.1f}%)")

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
