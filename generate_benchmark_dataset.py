# -*- coding: utf-8 -*-
"""
Synthetic Invoice Dataset Generator for Benchmark & Deduplication
- XML cấu trúc cha-con + Digital Signature
- Render PDF trực quan với ReportLab (hỗ trợ phân trang, chữ ký điện tử số hóa)
- Sinh file ground_truth.json phục vụ tính điểm Precision / Recall và Exact Match
"""

import os
import json
import random
import copy
from datetime import datetime, timedelta
import xml.etree.ElementTree as ET
from xml.dom import minidom

from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

# -------------------------------------------------------------
# 1. CẤU HÌNH & DỮ LIỆU MẪU 
# -------------------------------------------------------------
OUTPUT_DIR = "dataset"
XML_DIR = os.path.join(OUTPUT_DIR, "xml")
PDF_DIR = os.path.join(OUTPUT_DIR, "pdf")
GROUND_TRUTH_FILE = os.path.join(OUTPUT_DIR, "ground_truth.json")

os.makedirs(XML_DIR, exist_ok=True)
os.makedirs(PDF_DIR, exist_ok=True)

SELLER_NAMES = [
    "CONG TY CO PHAN CONG NGHE VA DICH VU FSA VIET NAM",
    "CONG TY TNHH PHAN MEM VA TICH HOP HE THONG TOAN CAU",
    "TAP DOAN VIEN THONG VA CONG NGHE THONG TIN V-TECH",
    "CONG TY TNHH THUONG MAI DICH VU KINH DOANH SAO MAI",
    "CONG TY CO PHAN DAU TU VA PHAT TRIEN CONG NGHE SO 1",
    "CONG TY TNHH GIAI PHAP DOANH NGHIEP CHAU A - THAI BINH DUONG"
]

BUYER_NAMES = [
    "CONG TY TNHH SAN XUAT THUONG MAI DICH VU AN PHAT",
    "CONG TY CO PHAN DAU TU XAY DUNG VA PHAT TRIEN HA TANG THANG LONG",
    "NGAN HANG TMCP NGOAI THUONG VIET NAM - CHI NHANH HA NOI",
    "CONG TY CO PHAN LOGISTICS VA XUAT NHAP KHAU VIET NAM",
    "CONG TY TNHH THIET BI VAN PHONG VA GIAI PHAP SO HOA"
]

ITEMS_CATALOG = [
    ("Dich vu tu van he thong CNTT", "Goi"),
    ("Ban quyen phan mem quan ly hoa don", "License"),
    ("May chu Dell PowerEdge R750", "Chiec"),
    ("O cung SSD Samsung 1TB NVMe", "Chiec"),
    ("Dich vu bao tri he thong quy", "Thang"),
    ("Cap mang Cat6 AMP/CommScope", "Cuon"),
    ("Man hinh Dell UltraSharp 27 inch", "Chiec"),
    ("Dich vu Cloud Server tieu chuan", "Thang"),
    ("Ban phim co quang hoc chuyen dung", "Chiec"),
    ("Chuot khong day Logitech MX Master", "Chiec"),
    ("Thiet bi can bang tai DrayTek", "Chiec"),
    ("Chi phi setup he thong mang LAN", "Goi")
]

# -------------------------------------------------------------
# 2. HÀM SINH DỮ LIỆU ĐẶC TRƯNG (TAX ID, DATES, ITEMS)
# -------------------------------------------------------------
def random_tax_id(is_branch=False):
    base = "".join([str(random.randint(0, 9)) for _ in range(10)])
    if is_branch:
        return f"{base}-{random.randint(100, 999)}"
    return base

def random_date(start_year=2026):
    start_dt = datetime(start_year, 1, 1)
    random_days = random.randint(0, 300)
    dt = start_dt + timedelta(days=random_days)
    return dt.strftime("%Y-%m-%d")

def generate_line_items(count, has_mixed_tax=False, has_discount=False):
    items = []
    chosen_catalog = random.choices(ITEMS_CATALOG, k=count)
    for idx, (name, unit) in enumerate(chosen_catalog, start=1):
        qty = random.randint(1, 10)
        price = random.choice([50000, 120000, 250000, 500000, 1500000, 12000000, 45000000])
        amount = qty * price

        if has_mixed_tax:
            tax_rate = random.choice([0, 8, 10])
        else:
            tax_rate = random.choice([8, 10])

        items.append({
            "line_no": idx,
            "item_name": name,
            "unit": unit,
            "quantity": qty,
            "unit_price": price,
            "amount": amount,
            "tax_rate": tax_rate
        })

    if has_discount and len(items) > 1:
        # Them dong chiet khau
        disc_amount = -int(items[0]["amount"] * 0.1)
        items.append({
            "line_no": len(items) + 1,
            "item_name": "Chiet khau thuong mai 10% dong hang 1",
            "unit": "Goi",
            "quantity": 1,
            "unit_price": disc_amount,
            "amount": disc_amount,
            "tax_rate": items[0]["tax_rate"]
        })
    return items

def calculate_totals(items):
    total_before_tax = 0
    total_tax_amount = 0
    for it in items:
        amt = it["amount"]
        tax = int(round(amt * (it["tax_rate"] / 100.0)))
        total_before_tax += amt
        total_tax_amount += tax
    total_amount = total_before_tax + total_tax_amount
    return total_before_tax, total_tax_amount, total_amount

# -------------------------------------------------------------
# 3. SINH FILE XML CHUẨN THÔNG TƯ 78 (KÈM DIGITAL SIGNATURE)
# -------------------------------------------------------------
def build_tt78_xml(inv_data):
    root = ET.Element("HDon")
    dldg = ET.SubElement(root, "DLHDon")
    dldg.set("Id", f"HD_{inv_data['invoice_number']}")

    # TTChung
    ttc = ET.SubElement(dldg, "TTChung")
    ET.SubElement(ttc, "PBan").text = "2.0.0"
    ET.SubElement(ttc, "THDon").text = "Hoa don gia tri gia tang"
    ET.SubElement(ttc, "KHMSHDon").text = "1"
    ET.SubElement(ttc, "KHHDon").text = inv_data["invoice_series"]
    ET.SubElement(ttc, "SHDon").text = inv_data["invoice_number"]
    ET.SubElement(ttc, "NLap").text = inv_data["issue_date"]
    ET.SubElement(ttc, "DVTTe").text = inv_data["currency"]

    # NBan
    nb = ET.SubElement(dldg, "NDHDon")
    nban = ET.SubElement(nb, "NBan")
    ET.SubElement(nban, "Ten").text = inv_data["seller_name"]
    ET.SubElement(nban, "MST").text = inv_data["seller_tax_id"]
    ET.SubElement(nban, "DChi").text = inv_data["seller_address"]

    # NMua
    nmua = ET.SubElement(nb, "NMua")
    ET.SubElement(nmua, "Ten").text = inv_data["buyer_name"]
    ET.SubElement(nmua, "MST").text = inv_data["buyer_tax_id"]
    ET.SubElement(nmua, "DChi").text = inv_data["buyer_address"]

    # DSHHDVu (Danh sach hang hoa dich vu theo Thong tu 78)
    dshh = ET.SubElement(nb, "DSHHDVu")
    for it in inv_data["line_items"]:
        hh = ET.SubElement(dshh, "HHDVu")
        ET.SubElement(hh, "TChat").text = "1"
        ET.SubElement(hh, "STT").text = str(it["line_no"])
        ET.SubElement(hh, "THHDVu").text = it["item_name"]
        ET.SubElement(hh, "DVTinh").text = it["unit"]
        ET.SubElement(hh, "SLuong").text = str(it["quantity"])
        ET.SubElement(hh, "DGia").text = str(it["unit_price"])
        ET.SubElement(hh, "ThTien").text = str(it["amount"])
        ET.SubElement(hh, "TSuat").text = f"{it['tax_rate']}%"

    # TToan
    ttoan = ET.SubElement(nb, "TToan")
    ET.SubElement(ttoan, "TgTCThue").text = str(inv_data["total_before_tax"])
    ET.SubElement(ttoan, "TgTThue").text = str(inv_data["total_tax_amount"])
    ET.SubElement(ttoan, "TgTTTBSo").text = str(inv_data["total_amount"])

    # Digital Signature
    if inv_data.get("has_signature", True):
        sig = ET.SubElement(root, "Signature")
        sig.set("xmlns", "http://www.w3.org/2000/09/xmldsig#")
        si = ET.SubElement(sig, "SignedInfo")
        ET.SubElement(si, "CanonicalizationMethod", Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315")
        ET.SubElement(si, "SignatureMethod", Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256")
        sv = ET.SubElement(sig, "SignatureValue")
        sv.text = "MIAGCSqGSIb3DQEHAqCAMIACAQExCzAJBgUrDgMCGgUAMIAGCSqGSIb3DQEHAQAA..."
        ki = ET.SubElement(sig, "KeyInfo")
        xd = ET.SubElement(ki, "X509Data")
        ET.SubElement(xd, "X509SubjectName").text = f"CN={inv_data['seller_name']}, OID.0.9.2342.19200300.100.1.1=MST:{inv_data['seller_tax_id']}"
        ET.SubElement(xd, "SigningTime").text = f"{inv_data['issue_date']}T08:30:00+07:00"

    xml_str = ET.tostring(root, encoding="utf-8")
    parsed = minidom.parseString(xml_str)
    return parsed.toprettyxml(indent="  ", encoding="utf-8")

# -------------------------------------------------------------
# 4. RENDER FILE PDF BẰNG REPORTLAB
# -------------------------------------------------------------
def render_invoice_pdf(inv_data, output_filepath):
    doc = SimpleDocTemplate(
        output_filepath,
        pagesize=A4,
        leftMargin=30,
        rightMargin=30,
        topMargin=30,
        bottomMargin=30
    )
    styles = getSampleStyleSheet()
    story = []

    # Title
    title_style = ParagraphStyle(
        name="InvoiceTitle",
        parent=styles["Heading1"],
        fontSize=16,
        leading=20,
        alignment=1, # Center
        textColor=colors.HexColor("#1A365D"),
        spaceAfter=5
    )
    story.append(Paragraph("HOA DON GIA TRI GIA TANG", title_style))
    story.append(Paragraph("(VAT INVOICE)", ParagraphStyle(name="Sub", parent=styles["Normal"], alignment=1, fontSize=9, textColor=colors.gray)))
    story.append(Spacer(1, 10))

    # Meta Box (Series, No, Date)
    meta_data = [
        [
            Paragraph(f"<b>Ky hieu (Series):</b> {inv_data['invoice_series']}", styles["Normal"]),
            Paragraph(f"<b>So hoa don (No.):</b> <font color='red'><b>{inv_data['invoice_number']}</b></font>", styles["Normal"])
        ],
        [
            Paragraph(f"<b>Ngay lap (Date):</b> {inv_data['issue_date']}", styles["Normal"]),
            Paragraph(f"<b>Tien te (Currency):</b> {inv_data['currency']}", styles["Normal"])
        ]
    ]
    meta_table = Table(meta_data, colWidths=[270, 265])
    meta_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#F7FAFC")),
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#CBD5E0")),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.HexColor("#E2E8F0")),
        ('TOPPADDING', (0,0), (-1,-1), 5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 5),
    ]))
    story.append(meta_table)
    story.append(Spacer(1, 10))

    # Parties (Seller & Buyer)
    party_data = [
        [Paragraph("<b>DON VI BAN HANG (SELLER)</b>", ParagraphStyle(name="SH", parent=styles["Normal"], textColor=colors.HexColor("#2B6CB0")))],
        [Paragraph(f"Ten don vi: <b>{inv_data['seller_name']}</b>", styles["Normal"])],
        [Paragraph(f"Ma so thue: <b>{inv_data['seller_tax_id']}</b>", styles["Normal"])],
        [Paragraph(f"Dia chi: {inv_data['seller_address']}", styles["Normal"])],
        [Spacer(1, 4)],
        [Paragraph("<b>DON VI MUA HANG (BUYER)</b>", ParagraphStyle(name="BH", parent=styles["Normal"], textColor=colors.HexColor("#2B6CB0")))],
        [Paragraph(f"Ten don vi: <b>{inv_data['buyer_name']}</b>", styles["Normal"])],
        [Paragraph(f"Ma so thue: <b>{inv_data['buyer_tax_id']}</b>", styles["Normal"])],
        [Paragraph(f"Dia chi: {inv_data['buyer_address']}", styles["Normal"])],
    ]
    party_table = Table(party_data, colWidths=[535])
    party_table.setStyle(TableStyle([
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#CBD5E0")),
        ('BACKGROUND', (0,0), (0,0), colors.HexColor("#EDF2F7")),
        ('BACKGROUND', (0,5), (0,5), colors.HexColor("#EDF2F7")),
        ('TOPPADDING', (0,0), (-1,-1), 3),
        ('BOTTOMPADDING', (0,0), (-1,-1), 3),
    ]))
    story.append(party_table)
    story.append(Spacer(1, 10))

    # Line Items Table
    headers = ["STT", "Ten hang hoa, dich vu", "DVT", "SL", "Don gia", "Thanh tien", "Thue"]
    table_rows = [headers]
    for it in inv_data["line_items"]:
        table_rows.append([
            str(it["line_no"]),
            Paragraph(it["item_name"], styles["Normal"]),
            it["unit"],
            f"{it['quantity']:,}",
            f"{it['unit_price']:,}",
            f"{it['amount']:,}",
            f"{it['tax_rate']}%"
        ])

    item_table = Table(table_rows, colWidths=[30, 205, 45, 35, 75, 95, 50], repeatRows=1)
    item_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor("#2B6CB0")),
        ('TEXTCOLOR', (0,0), (-1,0), colors.white),
        ('ALIGN', (0,0), (-1,0), 'CENTER'),
        ('ALIGN', (0,1), (0,-1), 'CENTER'),
        ('ALIGN', (2,1), (3,-1), 'CENTER'),
        ('ALIGN', (4,1), (5,-1), 'RIGHT'),
        ('ALIGN', (6,1), (6,-1), 'CENTER'),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor("#CBD5E0")),
        ('FONTSIZE', (0,0), (-1,-1), 8),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
    ]))
    story.append(item_table)
    story.append(Spacer(1, 10))

    # Summary Table
    summary_data = [
        ["Tong tien chua thue (Total before tax):", f"{inv_data['total_before_tax']:,} {inv_data['currency']}"],
        ["Tong tien thue GTGT (Total VAT):", f"{inv_data['total_tax_amount']:,} {inv_data['currency']}"],
        ["TONG CONG THANH TOAN (TOTAL PAYMENT):", f"{inv_data['total_amount']:,} {inv_data['currency']}"]
    ]
    summary_table = Table(summary_data, colWidths=[355, 180])
    summary_table.setStyle(TableStyle([
        ('ALIGN', (0,0), (0,-1), 'RIGHT'),
        ('ALIGN', (1,0), (1,-1), 'RIGHT'),
        ('FONTNAME', (0,2), (-1,2), 'Helvetica-Bold'),
        ('TEXTCOLOR', (1,2), (1,2), colors.HexColor("#C53030")),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor("#E2E8F0")),
        ('BACKGROUND', (0,2), (-1,2), colors.HexColor("#FEFCBF")),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
    ]))
    story.append(summary_table)
    story.append(Spacer(1, 15))

    # Digital Signature Box
    sig_content = [
        [
            Paragraph("<b>NGUOI MUA HANG</b><br/>(Buyer)", ParagraphStyle(name="C1", parent=styles["Normal"], alignment=1)),
            Paragraph(
                f"<b>NGUOI BAN HANG (SELLER)</b><br/>"
                f"<font color='green'>[DA KY DIEN TU / DIGITALLY SIGNED]</font><br/>"
                f"Ky boi: <b>{inv_data['seller_name']}</b><br/>"
                f"Ngay ky: {inv_data['issue_date']}",
                ParagraphStyle(name="C2", parent=styles["Normal"], alignment=1, fontSize=8)
            )
        ]
    ]
    sig_table = Table(sig_content, colWidths=[265, 270])
    sig_table.setStyle(TableStyle([
        ('ALIGN', (0,0), (-1,-1), 'CENTER'),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('BOX', (1,0), (1,0), 1, colors.HexColor("#38A169")),
        ('BACKGROUND', (1,0), (1,0), colors.HexColor("#F0FFF4")),
        ('TOPPADDING', (0,0), (-1,-1), 8),
        ('BOTTOMPADDING', (0,0), (-1,-1), 8),
    ]))
    story.append(KeepTogether(sig_table))

    doc.build(story)

# -------------------------------------------------------------
# 5. TIẾN TRÌNH TẠO 100 HÓA ĐƠN BENCHMARK
# -------------------------------------------------------------
def generate_synthetic_benchmark():
    random.seed(42)
    print(">>> Dang khoi tao 100 bo hoa don synthetic...")

    invoices = []
    
    # 70 hoa don tieu chuan (inv_001 -> inv_070)
    for i in range(1, 71):
        inv_id = f"inv_{i:03d}"
        s_name = random.choice(SELLER_NAMES)
        b_name = random.choice(BUYER_NAMES)
        s_tax = random_tax_id(is_branch=False)
        b_tax = random_tax_id(is_branch=False)
        items = generate_line_items(count=random.randint(1, 4), has_mixed_tax=False, has_discount=False)
        tot_b, tot_t, tot = calculate_totals(items)
        
        invoices.append({
            "invoice_id": inv_id,
            "type": "standard",
            "seller_name": s_name,
            "seller_tax_id": s_tax,
            "seller_address": f"So {random.randint(1, 500)} Duong Giai Phong, Quan Hoang Mai, Ha Noi",
            "buyer_name": b_name,
            "buyer_tax_id": b_tax,
            "buyer_address": f"Toa nha FPT, So {random.randint(1, 50)} Duy Tan, Quan Cau Giay, Ha Noi",
            "invoice_series": f"1C26T{random.choice(['AA', 'BB', 'CC'])}",
            "invoice_number": f"{i:07d}",
            "issue_date": random_date(2026),
            "currency": "VND",
            "line_items": items,
            "total_before_tax": tot_b,
            "total_tax_amount": tot_t,
            "total_amount": tot,
            "has_signature": True,
            "duplicate_with": []
        })

    # 15 hoa don multi-page/multi-item (inv_071 -> inv_085)
    for i in range(71, 86):
        inv_id = f"inv_{i:03d}"
        s_name = random.choice(SELLER_NAMES)
        b_name = random.choice(BUYER_NAMES)
        s_tax = random_tax_id(is_branch=False)
        b_tax = random_tax_id(is_branch=False)
        items = generate_line_items(count=random.randint(15, 25), has_mixed_tax=False, has_discount=False)
        tot_b, tot_t, tot = calculate_totals(items)

        invoices.append({
            "invoice_id": inv_id,
            "type": "multi_page",
            "seller_name": s_name,
            "seller_tax_id": s_tax,
            "seller_address": f"Tang {random.randint(2, 20)}, Toa nha Keangnam Landmark 72, Pham Hung, Nam Tu Liem, Ha Noi",
            "buyer_name": b_name,
            "buyer_tax_id": b_tax,
            "buyer_address": f"Khu Cong nghe cao Hoa Lac, Thach That, Ha Noi",
            "invoice_series": "1C26TDD",
            "invoice_number": f"{i:07d}",
            "issue_date": random_date(2026),
            "currency": "VND",
            "line_items": items,
            "total_before_tax": tot_b,
            "total_tax_amount": tot_t,
            "total_amount": tot,
            "has_signature": True,
            "duplicate_with": []
        })

    # 15 hoa don corner cases (inv_086 -> inv_100)
    for i in range(86, 101):
        inv_id = f"inv_{i:03d}"
        s_tax = random_tax_id(is_branch=(i % 2 == 0)) # MST 13 so
        b_tax = random_tax_id(is_branch=(i % 3 == 0))
        
        # Ten rat dai
        s_name = "CONG TY TNHH PHAT TRIEN VA CUNG UNG DICH VU GIA PHAP CONG NGHE THONG TIN LIEN QUOC GIA KHU VUC DONG NAM A - CHI NHANH BAC NINH" if i % 2 == 0 else random.choice(SELLER_NAMES)
        b_name = "TONG CONG TY DAU KHI VA XAY DUNG CONG TRINH BIEN DONG VIET NAM - TRUNG TAM NGHIEP VU LOGISTICS DU AN" if i % 2 != 0 else random.choice(BUYER_NAMES)

        items = generate_line_items(count=random.randint(3, 6), has_mixed_tax=True, has_discount=(i % 2 == 0))
        
        # Truong hop tien lon hang chuc ty
        if i in [98, 99, 100]:
            items[0]["unit_price"] = 15000000000
            items[0]["amount"] = items[0]["quantity"] * items[0]["unit_price"]

        tot_b, tot_t, tot = calculate_totals(items)

        invoices.append({
            "invoice_id": inv_id,
            "type": "corner_case",
            "seller_name": s_name,
            "seller_tax_id": s_tax,
            "seller_address": f"Lo CN{random.randint(1, 10)}, KCN Yen Phong, Xa Yen Trung, Huyen Yen Phong, Tinh Bac Ninh, Viet Nam",
            "buyer_name": b_name,
            "buyer_tax_id": b_tax,
            "buyer_address": f"So {random.randint(1, 100)} Nguyen Huu Canh, Phuong 22, Quan Binh Thanh, TP Ho Chi Minh",
            "invoice_series": "2C26TYY",
            "invoice_number": f"{i:07d}",
            "issue_date": random_date(2026),
            "currency": "VND",
            "line_items": items,
            "total_before_tax": tot_b,
            "total_tax_amount": tot_t,
            "total_amount": tot,
            "has_signature": True,
            "duplicate_with": []
        })

    # ---------------------------------------------------------
    # THIẾT KẾ CÁC CẶP TRÙNG LẶP & HARD NEGATIVE (DEDUP BENCHMARK)
    # ---------------------------------------------------------
    # 15 True Duplicates: Lấy 15 hóa đơn (ví dụ inv_016 -> inv_030 clone từ inv_001 -> inv_015)
    duplicate_groups = []
    for dup_idx in range(15):
        orig_inv = invoices[dup_idx] # inv_001 -> inv_015
        target_inv = invoices[15 + dup_idx] # inv_016 -> inv_030
        
        # Bien target thanh ban clone hoan toan cua orig
        target_inv["seller_tax_id"] = orig_inv["seller_tax_id"]
        target_inv["seller_name"] = orig_inv["seller_name"]
        target_inv["buyer_tax_id"] = orig_inv["buyer_tax_id"]
        target_inv["buyer_name"] = orig_inv["buyer_name"]
        target_inv["invoice_series"] = orig_inv["invoice_series"]
        target_inv["invoice_number"] = orig_inv["invoice_number"]
        target_inv["issue_date"] = orig_inv["issue_date"]
        target_inv["line_items"] = copy.deepcopy(orig_inv["line_items"])
        target_inv["total_before_tax"] = orig_inv["total_before_tax"]
        target_inv["total_tax_amount"] = orig_inv["total_tax_amount"]
        target_inv["total_amount"] = orig_inv["total_amount"]

        orig_inv["duplicate_with"].append(target_inv["invoice_id"])
        target_inv["duplicate_with"].append(orig_inv["invoice_id"])
        duplicate_groups.append([orig_inv["invoice_id"], target_inv["invoice_id"]])

    # 10 Hard Negative pairs: Giong nguoi ban, ngay, tong tien nhung KHAC so hoa don
    near_duplicate_groups = []
    for hn_idx in range(10):
        src_inv = invoices[30 + hn_idx] # inv_031 -> inv_040
        tgt_inv = invoices[40 + hn_idx] # inv_041 -> inv_050

        tgt_inv["seller_tax_id"] = src_inv["seller_tax_id"]
        tgt_inv["seller_name"] = src_inv["seller_name"]
        tgt_inv["issue_date"] = src_inv["issue_date"]
        tgt_inv["line_items"] = copy.deepcopy(src_inv["line_items"])
        tgt_inv["total_before_tax"] = src_inv["total_before_tax"]
        tgt_inv["total_tax_amount"] = src_inv["total_tax_amount"]
        tgt_inv["total_amount"] = src_inv["total_amount"]
        # Khac so hoa don de thanh Negative
        tgt_inv["invoice_number"] = f"99{tgt_inv['invoice_number'][2:]}"

        near_duplicate_groups.append({
            "doc1": src_inv["invoice_id"],
            "doc2": tgt_inv["invoice_id"],
            "reason": "Same seller, date, total amount, but DIFFERENT invoice_number"
        })

    # ---------------------------------------------------------
    # XUẤT RA FILES (XML, PDF, GROUND TRUTH)
    # ---------------------------------------------------------
    ground_truth_records = []

    for inv in invoices:
        inv_id = inv["invoice_id"]
        xml_filename = f"{inv_id}.xml"
        pdf_filename = f"{inv_id}.pdf"
        
        xml_path = os.path.join(XML_DIR, xml_filename)
        pdf_path = os.path.join(PDF_DIR, pdf_filename)

        # Xuất XML
        xml_content = build_tt78_xml(inv)
        with open(xml_path, "wb") as f:
            f.write(xml_content)

        # Xuất PDF
        render_invoice_pdf(inv, pdf_path)

        # Chuẩn bị bản ghi ground truth
        record = {
            "invoice_id": inv_id,
            "type": inv["type"],
            "xml_file": f"xml/{xml_filename}",
            "pdf_file": f"pdf/{pdf_filename}",
            "seller_tax_id": inv["seller_tax_id"],
            "seller_name": inv["seller_name"],
            "buyer_tax_id": inv["buyer_tax_id"],
            "buyer_name": inv["buyer_name"],
            "invoice_series": inv["invoice_series"],
            "invoice_number": inv["invoice_number"],
            "issue_date": inv["issue_date"],
            "currency": inv["currency"],
            "total_before_tax": inv["total_before_tax"],
            "total_tax_amount": inv["total_tax_amount"],
            "total_amount": inv["total_amount"],
            "line_items": inv["line_items"],
            "has_signature": inv["has_signature"],
            "duplicate_with": inv["duplicate_with"]
        }
        ground_truth_records.append(record)

    # Xuất Ground Truth JSON
    gt_payload = {
        "metadata": {
            "generated_at": datetime.now().isoformat(),
            "total_invoices": len(ground_truth_records),
            "true_duplicate_pairs_count": len(duplicate_groups),
            "hard_negative_pairs_count": len(near_duplicate_groups)
        },
        "duplicate_groups": duplicate_groups,
        "near_duplicate_groups": near_duplicate_groups,
        "invoices": ground_truth_records
    }

    with open(GROUND_TRUTH_FILE, "w", encoding="utf-8") as f:
        json.dump(gt_payload, f, ensure_ascii=False, indent=2)

    print(f"=== HOAN THANH ===")
    print(f"- 100 XML files: {XML_DIR}")
    print(f"- 100 PDF files: {PDF_DIR}")
    print(f"- Ground Truth: {GROUND_TRUTH_FILE}")

if __name__ == "__main__":
    generate_synthetic_benchmark()
