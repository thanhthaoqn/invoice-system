import React, { useState, useEffect } from 'react';
import {
  CheckCircle,
  AlertTriangle,
  Save,
  Check,
  X,
  Plus,
  Trash2,
  Sparkles,
  ArrowLeft,
  ShieldCheck,
  CreditCard,
  BookOpen
} from 'lucide-react';
import { api, type Invoice, type InvoiceLineItem, type ReviewInvoiceRequest } from '../services/api';
import { DocumentViewer } from './DocumentViewer';

interface SplitScreenReviewProps {
  invoice: Invoice;
  onBack: () => void;
  onReviewed: (updated: Invoice) => void;
  canReview: boolean;
}

export const SplitScreenReview: React.FC<SplitScreenReviewProps> = ({
  invoice,
  onBack,
  onReviewed,
  canReview
}) => {
  // Form state initialized from invoice (AI-extracted)
  const [invoiceNumber, setInvoiceNumber] = useState(invoice.invoiceNumber || '');
  const [invoiceDate, setInvoiceDate] = useState(invoice.invoiceDate || '');
  const [vendorName, setVendorName] = useState(invoice.vendorName || '');
  const [taxCode, setTaxCode] = useState(invoice.taxCode || '');
  const [subtotalAmount, setSubtotalAmount] = useState<number>(invoice.subtotalAmount || 0);
  const [vatAmount, setVatAmount] = useState<number>(invoice.vatAmount || 0);
  const [totalAmount, setTotalAmount] = useState<number>(invoice.totalAmount || 0);
  const [category, setCategory] = useState(invoice.category || 'Chi phí Dịch vụ / Khác');
  const [debitAccount, setDebitAccount] = useState(invoice.debitAccount || '6427');
  const [vatAccount, setVatAccount] = useState(invoice.vatAccount || '1331');
  const [creditAccount, setCreditAccount] = useState(invoice.creditAccount || ((invoice.totalAmount || 0) >= 20000000 ? '1121' : '1111'));
  const [reviewedBy, setReviewedBy] = useState('Kế toán viên Trần Thị B');
  const [reason, setReason] = useState('');
  const [lineItems, setLineItems] = useState<InvoiceLineItem[]>(
    invoice.lineItems && invoice.lineItems.length > 0 ? [...invoice.lineItems] : []
  );

  const [saving, setSaving] = useState(false);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  useEffect(() => {
    setInvoiceNumber(invoice.invoiceNumber || '');
    setInvoiceDate(invoice.invoiceDate || '');
    setVendorName(invoice.vendorName || '');
    setTaxCode(invoice.taxCode || '');
    setSubtotalAmount(invoice.subtotalAmount || 0);
    setVatAmount(invoice.vatAmount || 0);
    setTotalAmount(invoice.totalAmount || 0);
    setCategory(invoice.category || 'Chi phí Dịch vụ / Khác');
    setDebitAccount(invoice.debitAccount || '6427');
    setVatAccount(invoice.vatAccount || '1331');
    setCreditAccount(invoice.creditAccount || ((invoice.totalAmount || 0) >= 20000000 ? '1121' : '1111'));
    setLineItems(invoice.lineItems && invoice.lineItems.length > 0 ? [...invoice.lineItems] : []);
    setActionSuccess(null);
  }, [invoice]);

  // Recalculate totals from line items if requested
  const handleLineItemChange = (index: number, field: keyof InvoiceLineItem, value: any) => {
    const updated = [...lineItems];
    updated[index] = { ...updated[index], [field]: value };
    
    // Auto compute amount if qty and price change
    if (field === 'quantity' || field === 'unitPrice') {
      const q = Number(updated[index].quantity) || 0;
      const p = Number(updated[index].unitPrice) || 0;
      updated[index].amount = q * p;
    }
    setLineItems(updated);

    // Auto-update subtotal
    const newSubtotal = updated.reduce((sum, item) => sum + (Number(item.amount) || 0), 0);
    setSubtotalAmount(newSubtotal);
    setTotalAmount(newSubtotal + vatAmount);
  };

  const handleAddLineItem = () => {
    setLineItems([
      ...lineItems,
      {
        itemName: 'Hàng hóa / Dịch vụ mới',
        quantity: 1,
        unitPrice: 0,
        amount: 0,
        vatRate: '10%',
        lineNumber: lineItems.length + 1
      }
    ]);
  };

  const handleRemoveLineItem = (index: number) => {
    const filtered = lineItems.filter((_, i) => i !== index);
    setLineItems(filtered);
    const newSubtotal = filtered.reduce((sum, item) => sum + (Number(item.amount) || 0), 0);
    setSubtotalAmount(newSubtotal);
    setTotalAmount(newSubtotal + vatAmount);
  };

  // Submit Review Actions
  const handleReview = async (targetStatus: 'APPROVED' | 'REJECTED') => {
    if (saving || !canReview) return;
    try {
      setSaving(true);
      const payload: ReviewInvoiceRequest = {
        invoiceNumber,
        invoiceDate,
        vendorName,
        taxCode,
        subtotalAmount,
        vatAmount,
        totalAmount,
        category,
        debitAccount,
        vatAccount,
        creditAccount,
        status: targetStatus,
        reviewedBy,
        reason: reason || (targetStatus === 'APPROVED' ? 'Đối soát chuẩn xác' : 'Từ chối chứng từ'),
        lineItems
      };

      const updated = await api.reviewInvoice(invoice.id, payload);
      setActionSuccess(targetStatus === 'APPROVED' ? 'Đã duyệt hóa đơn thành công.' : 'Đã từ chối hóa đơn.');
      onReviewed(updated);
    } catch (err) {
      console.error(err);
      alert('Lỗi khi lưu đối soát hóa đơn.');
    } finally {
      setSaving(false);
    }
  };

  // Check live arithmetic equality
  const computedSum = subtotalAmount + vatAmount;
  const isArithmeticMatched = Math.abs(computedSum - totalAmount) <= 1000;
  const isXml = invoice.fileType === 'XML' || invoice.extractionMethod === 'XML_IMPORT';
  const extractionLabel = isXml ? 'Nhập XML' : invoice.extractionMethod === 'PYMUPDF_VECTOR_TEXT' ? 'Đọc văn bản PDF' : invoice.extractionMethod || 'Trích xuất chứng từ';

  return (
    <div className="lg:h-[calc(100dvh-9rem)] min-h-[36rem] flex flex-col py-2 max-w-[1700px] mx-auto px-2 sm:px-4">
      {/* Top Bar Navigation & Info */}
      <div className="flex flex-wrap gap-3 items-center justify-between py-2 border-b border-slate-200 mb-3 shrink-0">
        <div className="flex items-center space-x-3">
          <button
            onClick={onBack}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-white hover:bg-slate-50 border border-slate-200 text-xs font-semibold text-slate-700 hover:text-slate-900 shadow-xs transition"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Quay lại danh sách</span>
          </button>

          <div className="flex items-center space-x-2">
            <span className="font-extrabold text-sm text-slate-900">
              Hóa đơn #{invoice.id} - {invoice.invoiceNumber || 'Chưa định danh'}
            </span>
            <span
              className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
                invoice.status === 'APPROVED'
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                  : invoice.status === 'REJECTED'
                  ? 'bg-rose-50 text-rose-700 border border-rose-200'
                  : 'bg-amber-50 text-amber-800 border border-amber-200'
              }`}
            >
              {invoice.status}
            </span>
          </div>
        </div>

        {actionSuccess && (
          <div className="flex items-center space-x-2 text-xs text-emerald-800 bg-emerald-50 border border-emerald-200 px-3 py-1 rounded-xl shadow-xs">
            <Check className="w-3.5 h-3.5 text-emerald-600" />
            <span>{actionSuccess}</span>
          </div>
        )}

        <div className="flex items-center space-x-3 text-xs text-slate-500">
          <span>Người nộp: <strong className="text-slate-800 font-semibold">{invoice.submittedBy}</strong></span>
          <span>Ngày tạo: <strong className="text-slate-800 font-semibold">{invoice.createdAt ? new Date(invoice.createdAt).toLocaleDateString('vi-VN') : 'Hôm nay'}</strong></span>
        </div>
      </div>

      {/* Split Screen 50/50 Layout */}
      <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-2 gap-4 lg:overflow-hidden">
        {/* Left Window: Document Viewer */}
        <div className="h-[65vh] lg:h-full min-h-0 min-w-0 overflow-hidden">
          <DocumentViewer key={invoice.id} invoice={invoice} />
        </div>

        {/* Right Window: AI Extraction Verification Form */}
        <div className="lg:h-full min-h-0 min-w-0 flex flex-col bg-white border border-slate-200/90 rounded-2xl overflow-hidden shadow-sm">
          {/* Header & Audit Badges */}
          <div className="p-4 bg-slate-50/80 border-b border-slate-200 space-y-2 shrink-0">
            <div className="flex flex-wrap gap-2 items-center justify-between">
              <div className="flex items-center space-x-2">
                <Sparkles className="w-4 h-4 text-indigo-600" />
                <h3 className="text-sm font-bold text-slate-900">{isXml ? 'Dữ liệu XML & Đối soát' : 'Dữ liệu trích xuất & Đối soát'}</h3>
              </div>
              <div className="flex items-center space-x-2">
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">{extractionLabel}</span>
              </div>
            </div>

            {invoice.validationState === 'NOT_CHECKED' && <p role="status" className="p-2.5 rounded-xl bg-amber-50 text-amber-800 text-xs">Kiểm tra tự động chưa hoàn tất. Các phép cộng bên dưới chỉ đối chiếu các trường đang hiển thị.</p>}
            {invoice.validationState === 'BUYER_MISMATCH' && <p role="status" className="p-2.5 rounded-xl bg-amber-50 text-amber-800 text-xs">MST người mua thiếu hoặc khác doanh nghiệp nhận. Cần kiểm tra người mua trong XML gốc trước khi duyệt.</p>}

            {/* Audit Status Banners */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
              {/* Arithmetic Audit Badge */}
              <div
                className={`p-2.5 rounded-xl border flex items-start space-x-2 ${
                  isArithmeticMatched
                    ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                    : 'bg-rose-50 border-rose-200 text-rose-900'
                }`}
              >
                {isArithmeticMatched ? (
                  <CheckCircle className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                ) : (
                  <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                )}
                <div>
                  <div className="font-bold text-[11px]">
                    {isArithmeticMatched ? 'Tổng tiền đang khớp' : 'Cảnh báo sai lệch số học'}
                  </div>
                  <div className="text-[10px] text-slate-600 mt-0.5">
                    {isArithmeticMatched
                      ? 'Tổng tiền hàng + VAT khớp với Tổng thanh toán'
                      : `Tổng hàng (${new Intl.NumberFormat('vi-VN').format(subtotalAmount)}) + VAT (${new Intl.NumberFormat('vi-VN').format(vatAmount)}) ≠ Tổng thanh toán (${new Intl.NumberFormat('vi-VN').format(totalAmount)})`}
                  </div>
                </div>
              </div>

              {/* Duplicate Audit Badge */}
              <div
                className={`p-2.5 rounded-xl border flex items-start space-x-2 ${
                  !invoice.duplicateSuspected
                    ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                    : 'bg-amber-50 border-amber-200 text-amber-900'
                }`}
              >
                {!invoice.duplicateSuspected ? (
                  <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                ) : (
                  <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                )}
                <div>
                  <div className="font-bold text-[11px]">
                    {!invoice.duplicateSuspected ? 'Chưa phát hiện trùng' : 'Nghi vấn trùng lặp'}
                  </div>
                  <div className="text-[10px] text-slate-600 mt-0.5">
                    {!invoice.duplicateSuspected
                      ? 'Không phát hiện bản trùng trong doanh nghiệp hiện tại'
                      : invoice.duplicateWarningMessage || 'Đã phát hiện hóa đơn tương tự'}
                  </div>
                </div>
              </div>

              {/* Cashless Threshold Alert (>= 20M VNĐ) */}
              {(invoice.cashlessRequired || totalAmount >= 20000000) && (
                <div className="sm:col-span-2 p-2.5 rounded-xl border bg-orange-50 border-orange-200 text-orange-900 flex items-start space-x-2">
                  <CreditCard className="w-4 h-4 text-orange-600 shrink-0 mt-0.5" />
                  <div>
                    <div className="font-bold text-[11px]">
                      Ngưỡng thanh toán không dùng tiền mặt (CASHLESS_THRESHOLD ≥ 20.000.000đ)
                    </div>
                    <div className="text-[10px] text-slate-600 mt-0.5">
                      {invoice.cashlessWarningMessage || 'Hóa đơn từ 20 triệu đồng trở lên bắt buộc phải chuyển khoản qua ngân hàng (Có TK 1121) để được khấu trừ thuế GTGT và tính chi phí hợp lệ.'}
                    </div>
                  </div>
                </div>
              )}

              {/* Technical Duplicate (SHA-256 Idempotency) Alert */}
              {invoice.isTechnicalDuplicate && (
                <div className="sm:col-span-2 p-2.5 rounded-xl border bg-rose-50 border-rose-200 text-rose-900 flex items-start space-x-2">
                  <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  <div>
                    <div className="font-bold text-[11px]">
                      Trùng lặp tệp gốc kỹ thuật (Idempotency SHA-256)
                    </div>
                    <div className="text-[10px] text-slate-600 mt-0.5">
                      {invoice.technicalDuplicateMessage || 'Tệp hóa đơn này trùng khớp nội dung mã băm SHA-256 với chứng từ đã nộp trước đó.'}
                    </div>
                  </div>
                </div>
              )}

              {/* Tax Code Validation Alert */}
              {invoice.taxCodeValid === false && (
                <div className="sm:col-span-2 p-2.5 rounded-xl border bg-amber-50 border-amber-200 text-amber-900 flex items-start space-x-2">
                  <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                  <div>
                    <div className="font-bold text-[11px]">
                      Mã số thuế không đúng định dạng chuẩn
                    </div>
                    <div className="text-[10px] text-slate-600 mt-0.5">
                      {invoice.taxCodeErrorMessage || 'Mã số thuế Việt Nam yêu cầu định dạng 10 số (Doanh nghiệp) hoặc 13 số (Chi nhánh).'}
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Accounting Account Mapping Card (Thông tư 200/133) */}
            <div className="p-3 bg-indigo-50/60 rounded-xl border border-indigo-100 space-y-2 mt-2 shadow-xs">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-slate-900 flex items-center space-x-1.5">
                  <BookOpen className="w-3.5 h-3.5 text-indigo-600" />
                  <span>Tự Động Định Khoản Kế Toán (Thông tư 200/133)</span>
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded bg-indigo-100 text-indigo-800 font-mono font-bold">
                  Auto Account Mapping
                </span>
              </div>
              <div className="grid grid-cols-3 gap-2 text-xs">
                <div>
                  <label className="block text-[10px] text-slate-600 mb-0.5 font-medium">Nợ TK (Chi phí)</label>
                  <input
                    type="text"
                    value={debitAccount}
                    onChange={(e) => setDebitAccount(e.target.value)}
                    className="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1 text-slate-900 font-mono font-bold text-center focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100 focus:outline-none shadow-xs"
                    placeholder="6427"
                  />
                </div>
                <div>
                  <label className="block text-[10px] text-slate-600 mb-0.5 font-medium">Nợ TK 1331 (Thuế VAT)</label>
                  <input
                    type="text"
                    value={vatAccount}
                    onChange={(e) => setVatAccount(e.target.value)}
                    className="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1 text-slate-900 font-mono font-bold text-center focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100 focus:outline-none shadow-xs"
                    placeholder="1331"
                  />
                </div>
                <div>
                  <label className="block text-[10px] text-slate-600 mb-0.5 font-medium">Có TK (Thanh toán)</label>
                  <input
                    type="text"
                    value={creditAccount}
                    onChange={(e) => setCreditAccount(e.target.value)}
                    className="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1 text-slate-900 font-mono font-bold text-center focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100 focus:outline-none shadow-xs"
                    placeholder="1111 / 1121"
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Form Scrollable Body */}
          <div className="flex-1 min-h-0 overflow-y-auto p-4 space-y-4">
            <section className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs space-y-2">
              <div className="flex flex-wrap gap-4"><span>Mẫu số: <strong>{invoice.invoiceTemplate || 'Chưa xác định'}</strong></span><span>Ký hiệu: <strong>{invoice.invoiceSeries || 'Chưa xác định'}</strong></span><span>Tiền tệ: <strong>{invoice.currency || 'Chưa xác định'}</strong></span></div>
              {invoice.sellerAddress && <p>Địa chỉ bên bán: {invoice.sellerAddress}</p>}
              <p>Bên mua: <strong>{invoice.buyerName || 'Chưa xác định'}</strong> · MST: <strong>{invoice.buyerTaxCode || 'Chưa xác định'}</strong></p>
              {invoice.buyerAddress && <p>Địa chỉ bên mua: {invoice.buyerAddress}</p>}
              {invoice.source === 'OCR_CONFIRMED' && <p className="text-indigo-700">Nguồn: PDF đã xác nhận dữ liệu. Chưa xác minh chữ ký điện tử.</p>}
            </section>
            {/* Primary Fields Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div>
                <label className="block text-slate-600 mb-1 font-medium">Số Hóa Đơn (Invoice No.)</label>
                <input
                  type="text"
                  value={invoiceNumber}
                  onChange={(e) => setInvoiceNumber(e.target.value)}
                  className="w-full bg-slate-50/60 hover:bg-white focus:bg-white border border-slate-300 rounded-xl px-3 py-2 text-slate-900 font-mono font-bold focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100 focus:outline-none shadow-xs transition-colors"
                />
              </div>

              <div>
                <label className="block text-slate-600 mb-1 font-medium">Ngày Lập Hóa Đơn</label>
                <input
                  type="date"
                  value={invoiceDate}
                  onChange={(e) => setInvoiceDate(e.target.value)}
                  className="w-full bg-slate-50/60 hover:bg-white focus:bg-white border border-slate-300 rounded-xl px-3 py-2 text-slate-900 font-mono focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100 focus:outline-none shadow-xs transition-colors"
                />
              </div>

              <div className="sm:col-span-2">
                <label className="block text-slate-600 mb-1 font-medium">Tên Đơn Vị Bán Hàng / Đối Tác</label>
                <input
                  type="text"
                  value={vendorName}
                  onChange={(e) => setVendorName(e.target.value)}
                  className="w-full bg-slate-50/60 hover:bg-white focus:bg-white border border-slate-300 rounded-xl px-3 py-2 text-slate-900 font-semibold focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100 focus:outline-none shadow-xs transition-colors"
                />
              </div>

              <div>
                <label className="block text-slate-600 mb-1 font-medium">Mã Số Thuế (Tax Code / MST)</label>
                <input
                  type="text"
                  value={taxCode}
                  onChange={(e) => setTaxCode(e.target.value)}
                  className="w-full bg-slate-50/60 hover:bg-white focus:bg-white border border-slate-300 rounded-xl px-3 py-2 text-indigo-700 font-mono font-bold focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100 focus:outline-none shadow-xs transition-colors"
                />
              </div>

              <div>
                <label className="block text-slate-600 mb-1 font-medium">Phân Loại Chi Phí (Auto-Categorization)</label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="w-full bg-white border border-slate-300 rounded-xl px-3 py-2 text-indigo-900 font-semibold focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100 focus:outline-none shadow-xs transition-colors"
                >
                  <option value="Chi phí Công tác">Chi phí Công tác (Vé máy bay, khách sạn)</option>
                  <option value="Tiếp khách / Ăn uống">Tiếp khách / Ăn uống (Nhà hàng, cafe)</option>
                  <option value="Mua sắm Vật tư / Thiết bị">Mua sắm Vật tư / Thiết bị (Máy tính, VPP)</option>
                  <option value="Đào tạo / Hội thảo">Đào tạo / Hội thảo (Khóa học, vé sự kiện)</option>
                  <option value="Chi phí Dịch vụ / Khác">Chi phí Dịch vụ / Khác (Cloud, phần mềm)</option>
                </select>
              </div>
            </div>

            {/* Line Items Table */}
            <div className="border border-slate-200 rounded-xl overflow-hidden bg-white shadow-xs">
              <div className="p-2.5 bg-slate-50/90 border-b border-slate-200 flex items-center justify-between text-xs">
                <span className="font-bold text-slate-800">Chi Tiết Mặt Hàng (Line Items)</span>
                <button
                  type="button"
                  onClick={handleAddLineItem}
                  className="flex items-center space-x-1 px-2.5 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-lg text-[11px] font-semibold transition"
                >
                  <Plus className="w-3 h-3" />
                  <span>Thêm dòng</span>
                </button>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 text-slate-600 text-[10px] uppercase font-mono border-b border-slate-200">
                    <tr>
                      <th className="p-2">Tên Mặt Hàng</th>
                      <th className="p-2 w-16 text-right">SL</th>
                      <th className="p-2 w-28 text-right">Đơn Giá</th>
                      <th className="p-2 w-28 text-right">Thành Tiền</th>
                      <th className="p-2 w-16 text-center">VAT</th>
                      <th className="p-2 w-8"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {lineItems.map((item, idx) => (
                      <tr key={idx} className="hover:bg-slate-50/80 transition-colors">
                        <td className="p-1.5">
                          <input
                            type="text"
                            value={item.itemName}
                            onChange={(e) => handleLineItemChange(idx, 'itemName', e.target.value)}
                            className="w-full bg-transparent border-0 text-slate-800 focus:bg-white rounded px-1.5 py-1 focus:ring-1 focus:ring-indigo-300 focus:outline-none"
                          />
                        </td>
                        <td className="p-1.5 text-right font-mono">
                          <input
                            type="number"
                            value={item.quantity}
                            onChange={(e) => handleLineItemChange(idx, 'quantity', parseFloat(e.target.value) || 0)}
                            className="w-full text-right bg-transparent border-0 text-slate-800 focus:bg-white rounded px-1 py-1 focus:ring-1 focus:ring-indigo-300 focus:outline-none"
                          />
                        </td>
                        <td className="p-1.5 text-right font-mono">
                          <input
                            type="number"
                            value={item.unitPrice}
                            onChange={(e) => handleLineItemChange(idx, 'unitPrice', parseFloat(e.target.value) || 0)}
                            className="w-full text-right bg-transparent border-0 text-slate-800 focus:bg-white rounded px-1 py-1 focus:ring-1 focus:ring-indigo-300 focus:outline-none"
                          />
                        </td>
                        <td className="p-1.5 text-right font-mono font-bold text-slate-900">
                          {new Intl.NumberFormat('vi-VN').format(item.amount)}
                        </td>
                        <td className="p-1.5 text-center font-mono">
                          <input
                            type="text"
                            value={item.vatRate || '10%'}
                            onChange={(e) => handleLineItemChange(idx, 'vatRate', e.target.value)}
                            className="w-full text-center bg-transparent border-0 text-slate-600 focus:bg-white rounded px-1 py-1 focus:ring-1 focus:ring-indigo-300 focus:outline-none font-medium"
                          />
                        </td>
                        <td className="p-1.5 text-center">
                          <button
                            type="button"
                            onClick={() => handleRemoveLineItem(idx)}
                            className="text-slate-400 hover:text-rose-600 transition"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Financial Totals */}
            <div className="bg-slate-50/80 p-3.5 rounded-xl border border-slate-200 space-y-2 text-xs shadow-xs">
              <div className="flex justify-between items-center text-slate-700">
                <span className="font-medium">Tổng tiền hàng (trước thuế):</span>
                <input
                  type="number"
                  value={subtotalAmount}
                  onChange={(e) => {
                    const val = parseFloat(e.target.value) || 0;
                    setSubtotalAmount(val);
                    setTotalAmount(val + vatAmount);
                  }}
                  className="w-40 text-right font-mono font-bold bg-white border border-slate-200 rounded-lg px-2.5 py-1 text-slate-900 shadow-xs"
                />
              </div>

              <div className="flex justify-between items-center text-slate-700">
                <span className="font-medium">Tiền thuế GTGT (VAT):</span>
                <input
                  type="number"
                  value={vatAmount}
                  onChange={(e) => {
                    const val = parseFloat(e.target.value) || 0;
                    setVatAmount(val);
                    setTotalAmount(subtotalAmount + val);
                  }}
                  className="w-40 text-right font-mono font-bold bg-white border border-slate-200 rounded-lg px-2.5 py-1 text-slate-900 shadow-xs"
                />
              </div>

              <div className="flex justify-between items-center text-base font-extrabold text-slate-900 pt-2 border-t border-slate-200">
                <span>Tổng tiền thanh toán:</span>
                <input
                  type="number"
                  value={totalAmount}
                  onChange={(e) => setTotalAmount(parseFloat(e.target.value) || 0)}
                  className="w-48 text-right font-mono font-extrabold text-lg text-emerald-700 bg-white border border-emerald-300 rounded-lg px-2.5 py-1 shadow-xs"
                />
              </div>
            </div>

            {/* Reviewer Note Input */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div>
                <label className="block text-slate-600 mb-1 font-medium">Người Phê Duyệt</label>
                <input
                  type="text"
                  value={reviewedBy}
                  onChange={(e) => setReviewedBy(e.target.value)}
                  className="w-full bg-white border border-slate-300 rounded-xl px-3 py-2 text-slate-900 shadow-xs focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100"
                />
              </div>

              <div>
                <label className="block text-slate-600 mb-1 font-medium">Lý do điều chỉnh (nếu có sai lệch)</label>
                <input
                  type="text"
                  placeholder="VD: Điều chỉnh MST do ảnh mờ..."
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="w-full bg-white border border-slate-300 rounded-xl px-3 py-2 text-slate-900 shadow-xs focus:border-indigo-600 focus:ring-2 focus:ring-indigo-100"
                />
              </div>
            </div>
          </div>

          {/* Action Bar */}
          <div className="p-3.5 bg-slate-50/90 border-t border-slate-200 flex flex-wrap gap-2 items-center justify-between shrink-0">
            <button
              disabled={saving || !canReview}
              onClick={() => handleReview('REJECTED')}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-xs font-bold transition shadow-xs"
            >
              <X className="w-4 h-4" />
              <span>Từ Chối HĐ</span>
            </button>

            <div className="flex items-center space-x-3">
              <button
                disabled={saving || !canReview}
                onClick={() => handleReview('APPROVED')}
                className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-white hover:bg-slate-100 border border-slate-300 text-slate-700 hover:text-slate-900 text-xs font-bold transition shadow-xs"
                title="Lưu các trường đã chỉnh sửa vào Error Log và Duyệt"
              >
                <Save className="w-4 h-4 text-indigo-600" />
                <span>Lưu & Duyệt</span>
              </button>

              <button
                disabled={saving || !canReview}
                onClick={() => handleReview('APPROVED')}
                className="flex items-center space-x-2 px-6 py-2.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white text-xs font-extrabold shadow-md shadow-emerald-600/25 hover:scale-[1.02] active:scale-[0.98] transition-all"
              >
                <Check className="w-4 h-4" />
                <span>Duyệt hóa đơn</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
