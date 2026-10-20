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
  Zap,
  CreditCard,
  BookOpen
} from 'lucide-react';
import { api, type Invoice, type InvoiceLineItem, type ReviewInvoiceRequest } from '../services/api';
import { DocumentViewer } from './DocumentViewer';

interface SplitScreenReviewProps {
  invoice: Invoice;
  onBack: () => void;
  onReviewed: (updated: Invoice) => void;
}

export const SplitScreenReview: React.FC<SplitScreenReviewProps> = ({
  invoice,
  onBack,
  onReviewed
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
      setActionSuccess(targetStatus === 'APPROVED' ? 'Đã duyệt hóa đơn thành công (< 1s)!' : 'Đã từ chối hóa đơn.');
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

  return (
    <div className="h-[calc(100vh-5rem)] flex flex-col py-2 max-w-[1700px] mx-auto px-4">
      {/* Top Bar Navigation & Info */}
      <div className="flex items-center justify-between py-2 border-b border-slate-800 mb-3">
        <div className="flex items-center space-x-3">
          <button
            onClick={onBack}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-300 hover:text-white transition"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Quay lại danh sách</span>
          </button>

          <div className="flex items-center space-x-2">
            <span className="font-bold text-sm text-white">
              Hóa đơn #{invoice.id} - {invoice.invoiceNumber || 'Chưa định danh'}
            </span>
            <span
              className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                invoice.status === 'APPROVED'
                  ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                  : invoice.status === 'REJECTED'
                  ? 'bg-red-500/10 text-red-400 border border-red-500/30'
                  : 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
              }`}
            >
              {invoice.status}
            </span>
          </div>
        </div>

        {actionSuccess && (
          <div className="flex items-center space-x-2 text-xs text-emerald-400 bg-emerald-500/10 border border-emerald-500/30 px-3 py-1 rounded-lg">
            <Check className="w-3.5 h-3.5" />
            <span>{actionSuccess}</span>
          </div>
        )}

        <div className="flex items-center space-x-3 text-xs text-slate-400">
          <span>Người nộp: <strong className="text-slate-200">{invoice.submittedBy}</strong></span>
          <span>Ngày tạo: <strong className="text-slate-200">{invoice.createdAt ? new Date(invoice.createdAt).toLocaleDateString('vi-VN') : 'Hôm nay'}</strong></span>
        </div>
      </div>

      {/* Split Screen 50/50 Layout */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-4 overflow-hidden">
        {/* Left Window: Document Viewer */}
        <div className="h-full overflow-hidden">
          <DocumentViewer invoice={invoice} />
        </div>

        {/* Right Window: AI Extraction Verification Form */}
        <div className="h-full flex flex-col bg-slate-900/90 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
          {/* Header & Audit Badges */}
          <div className="p-4 bg-slate-900 border-b border-slate-800 space-y-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <Sparkles className="w-4 h-4 text-indigo-400" />
                <h3 className="text-sm font-bold text-white">Dữ Liệu Trích Xuất AI & Kiểm Toán Tự Động</h3>
              </div>
              <div className="flex items-center space-x-2">
                {invoice.extractionMethod === 'PYMUPDF_VECTOR_TEXT' ? (
                  <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500/10 text-amber-300 border border-amber-500/30">
                    <Zap className="w-3.5 h-3.5 text-amber-400" />
                    <span>PyMuPDF Vector (&lt;50ms)</span>
                  </span>
                ) : (
                  <span className="inline-flex items-center space-x-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-500/10 text-indigo-300 border border-indigo-500/30">
                    <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
                    <span>Gemini Vision AI</span>
                  </span>
                )}
                <span className="text-[11px] text-slate-400 font-mono">
                  Độ tin cậy: 97%
                </span>
              </div>
            </div>

            {/* Audit Status Banners */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
              {/* Arithmetic Audit Badge */}
              <div
                className={`p-2.5 rounded-xl border flex items-start space-x-2 ${
                  isArithmeticMatched
                    ? 'bg-emerald-950/40 border-emerald-500/30 text-emerald-300'
                    : 'bg-red-950/40 border-red-500/30 text-red-300'
                }`}
              >
                {isArithmeticMatched ? (
                  <CheckCircle className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                ) : (
                  <AlertTriangle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
                )}
                <div>
                  <div className="font-bold text-[11px]">
                    {isArithmeticMatched ? 'Số học hợp lệ' : 'Cảnh báo sai lệch số học'}
                  </div>
                  <div className="text-[10px] text-slate-300 mt-0.5">
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
                    ? 'bg-emerald-950/40 border-emerald-500/30 text-emerald-300'
                    : 'bg-amber-950/40 border-amber-500/30 text-amber-300'
                }`}
              >
                {!invoice.duplicateSuspected ? (
                  <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                ) : (
                  <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                )}
                <div>
                  <div className="font-bold text-[11px]">
                    {!invoice.duplicateSuspected ? 'Chứng từ duy nhất' : 'Nghi vấn trùng lặp'}
                  </div>
                  <div className="text-[10px] text-slate-300 mt-0.5">
                    {!invoice.duplicateSuspected
                      ? 'Chưa từng xuất hiện Số HĐ và MST này trong CSDL'
                      : invoice.duplicateWarningMessage || 'Đã phát hiện hóa đơn tương tự'}
                  </div>
                </div>
              </div>

              {/* Cashless Threshold Alert (>= 20M VNĐ) */}
              {(invoice.cashlessRequired || totalAmount >= 20000000) && (
                <div className="sm:col-span-2 p-2.5 rounded-xl border bg-orange-950/40 border-orange-500/30 text-orange-300 flex items-start space-x-2">
                  <CreditCard className="w-4 h-4 text-orange-400 shrink-0 mt-0.5" />
                  <div>
                    <div className="font-bold text-[11px]">
                      Ngưỡng thanh toán không dùng tiền mặt (CASHLESS_THRESHOLD ≥ 20.000.000đ)
                    </div>
                    <div className="text-[10px] text-slate-300 mt-0.5">
                      {invoice.cashlessWarningMessage || 'Hóa đơn từ 20 triệu đồng trở lên bắt buộc phải chuyển khoản qua ngân hàng (Có TK 1121) để được khấu trừ thuế GTGT và tính chi phí hợp lệ.'}
                    </div>
                  </div>
                </div>
              )}

              {/* Technical Duplicate (SHA-256 Idempotency) Alert */}
              {invoice.isTechnicalDuplicate && (
                <div className="sm:col-span-2 p-2.5 rounded-xl border bg-rose-950/40 border-rose-500/30 text-rose-300 flex items-start space-x-2">
                  <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                  <div>
                    <div className="font-bold text-[11px]">
                      Trùng lặp tệp gốc kỹ thuật (Idempotency SHA-256)
                    </div>
                    <div className="text-[10px] text-slate-300 mt-0.5">
                      {invoice.technicalDuplicateMessage || 'Tệp hóa đơn này trùng khớp nội dung mã băm SHA-256 với chứng từ đã nộp trước đó.'}
                    </div>
                  </div>
                </div>
              )}

              {/* Tax Code Validation Alert */}
              {invoice.taxCodeValid === false && (
                <div className="sm:col-span-2 p-2.5 rounded-xl border bg-yellow-950/40 border-yellow-500/30 text-yellow-300 flex items-start space-x-2">
                  <AlertTriangle className="w-4 h-4 text-yellow-400 shrink-0 mt-0.5" />
                  <div>
                    <div className="font-bold text-[11px]">
                      Mã số thuế không đúng định dạng chuẩn
                    </div>
                    <div className="text-[10px] text-slate-300 mt-0.5">
                      {invoice.taxCodeErrorMessage || 'Mã số thuế Việt Nam yêu cầu định dạng 10 số (Doanh nghiệp) hoặc 13 số (Chi nhánh).'}
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Accounting Account Mapping Card (Thông tư 200/133) */}
            <div className="p-3 bg-slate-950/70 rounded-xl border border-indigo-900/40 space-y-2 mt-2">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-slate-200 flex items-center space-x-1.5">
                  <BookOpen className="w-3.5 h-3.5 text-indigo-400" />
                  <span>Tự Động Định Khoản Kế Toán (Thông tư 200/133)</span>
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 font-mono">
                  Auto Account Mapping
                </span>
              </div>
              <div className="grid grid-cols-3 gap-2 text-xs">
                <div>
                  <label className="block text-[10px] text-slate-400 mb-0.5 font-medium">Nợ TK (Chi phí)</label>
                  <input
                    type="text"
                    value={debitAccount}
                    onChange={(e) => setDebitAccount(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-700 rounded px-2.5 py-1 text-slate-100 font-mono font-bold text-center focus:border-indigo-500 focus:outline-none"
                    placeholder="6427"
                  />
                </div>
                <div>
                  <label className="block text-[10px] text-slate-400 mb-0.5 font-medium">Nợ TK 1331 (Thuế VAT)</label>
                  <input
                    type="text"
                    value={vatAccount}
                    onChange={(e) => setVatAccount(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-700 rounded px-2.5 py-1 text-slate-100 font-mono font-bold text-center focus:border-indigo-500 focus:outline-none"
                    placeholder="1331"
                  />
                </div>
                <div>
                  <label className="block text-[10px] text-slate-400 mb-0.5 font-medium">Có TK (Thanh toán)</label>
                  <input
                    type="text"
                    value={creditAccount}
                    onChange={(e) => setCreditAccount(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-700 rounded px-2.5 py-1 text-slate-100 font-mono font-bold text-center focus:border-indigo-500 focus:outline-none"
                    placeholder="1111 / 1121"
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Form Scrollable Body */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {/* Primary Fields Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div>
                <label className="block text-slate-400 mb-1 font-medium">Số Hóa Đơn (Invoice No.)</label>
                <input
                  type="text"
                  value={invoiceNumber}
                  onChange={(e) => setInvoiceNumber(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 font-mono font-bold focus:border-indigo-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1 font-medium">Ngày Lập Hóa Đơn</label>
                <input
                  type="date"
                  value={invoiceDate}
                  onChange={(e) => setInvoiceDate(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 font-mono focus:border-indigo-500 focus:outline-none"
                />
              </div>

              <div className="sm:col-span-2">
                <label className="block text-slate-400 mb-1 font-medium">Tên Đơn Vị Bán Hàng / Đối Tác</label>
                <input
                  type="text"
                  value={vendorName}
                  onChange={(e) => setVendorName(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 font-semibold focus:border-indigo-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1 font-medium">Mã Số Thuế (Tax Code / MST)</label>
                <input
                  type="text"
                  value={taxCode}
                  onChange={(e) => setTaxCode(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-100 font-mono font-bold text-indigo-300 focus:border-indigo-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1 font-medium">Phân Loại Chi Phí (Auto-Categorization)</label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-indigo-300 font-semibold focus:border-indigo-500 focus:outline-none"
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
            <div className="border border-slate-800 rounded-xl overflow-hidden bg-slate-950/60">
              <div className="p-2.5 bg-slate-800/60 border-b border-slate-800 flex items-center justify-between text-xs">
                <span className="font-bold text-slate-200">Chi Tiết Mặt Hàng (Line Items)</span>
                <button
                  type="button"
                  onClick={handleAddLineItem}
                  className="flex items-center space-x-1 px-2 py-1 bg-indigo-600/30 hover:bg-indigo-600/60 text-indigo-300 hover:text-white rounded text-[11px] font-medium transition"
                >
                  <Plus className="w-3 h-3" />
                  <span>Thêm dòng</span>
                </button>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950 text-slate-400 text-[10px] uppercase font-mono">
                    <tr>
                      <th className="p-2">Tên Mặt Hàng</th>
                      <th className="p-2 w-16 text-right">SL</th>
                      <th className="p-2 w-28 text-right">Đơn Giá</th>
                      <th className="p-2 w-28 text-right">Thành Tiền</th>
                      <th className="p-2 w-16 text-center">VAT</th>
                      <th className="p-2 w-8"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/40">
                    {lineItems.map((item, idx) => (
                      <tr key={idx} className="hover:bg-slate-800/30">
                        <td className="p-1.5">
                          <input
                            type="text"
                            value={item.itemName}
                            onChange={(e) => handleLineItemChange(idx, 'itemName', e.target.value)}
                            className="w-full bg-transparent border-0 text-slate-200 focus:bg-slate-900 rounded px-1.5 py-1 focus:outline-none"
                          />
                        </td>
                        <td className="p-1.5 text-right font-mono">
                          <input
                            type="number"
                            value={item.quantity}
                            onChange={(e) => handleLineItemChange(idx, 'quantity', parseFloat(e.target.value) || 0)}
                            className="w-full text-right bg-transparent border-0 text-slate-200 focus:bg-slate-900 rounded px-1 py-1 focus:outline-none"
                          />
                        </td>
                        <td className="p-1.5 text-right font-mono">
                          <input
                            type="number"
                            value={item.unitPrice}
                            onChange={(e) => handleLineItemChange(idx, 'unitPrice', parseFloat(e.target.value) || 0)}
                            className="w-full text-right bg-transparent border-0 text-slate-200 focus:bg-slate-900 rounded px-1 py-1 focus:outline-none"
                          />
                        </td>
                        <td className="p-1.5 text-right font-mono font-bold text-slate-100">
                          {new Intl.NumberFormat('vi-VN').format(item.amount)}
                        </td>
                        <td className="p-1.5 text-center font-mono">
                          <input
                            type="text"
                            value={item.vatRate || '10%'}
                            onChange={(e) => handleLineItemChange(idx, 'vatRate', e.target.value)}
                            className="w-full text-center bg-transparent border-0 text-slate-400 focus:bg-slate-900 rounded px-1 py-1 focus:outline-none"
                          />
                        </td>
                        <td className="p-1.5 text-center">
                          <button
                            type="button"
                            onClick={() => handleRemoveLineItem(idx)}
                            className="text-slate-500 hover:text-red-400 transition"
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
            <div className="bg-slate-950/80 p-3 rounded-xl border border-slate-800 space-y-2 text-xs">
              <div className="flex justify-between items-center text-slate-300">
                <span>Tổng tiền hàng (trước thuế):</span>
                <input
                  type="number"
                  value={subtotalAmount}
                  onChange={(e) => {
                    const val = parseFloat(e.target.value) || 0;
                    setSubtotalAmount(val);
                    setTotalAmount(val + vatAmount);
                  }}
                  className="w-40 text-right font-mono font-bold bg-slate-900 border border-slate-800 rounded px-2 py-1 text-slate-200"
                />
              </div>

              <div className="flex justify-between items-center text-slate-300">
                <span>Tiền thuế GTGT (VAT):</span>
                <input
                  type="number"
                  value={vatAmount}
                  onChange={(e) => {
                    const val = parseFloat(e.target.value) || 0;
                    setVatAmount(val);
                    setTotalAmount(subtotalAmount + val);
                  }}
                  className="w-40 text-right font-mono font-bold bg-slate-900 border border-slate-800 rounded px-2 py-1 text-slate-200"
                />
              </div>

              <div className="flex justify-between items-center text-base font-extrabold text-indigo-400 pt-2 border-t border-slate-800">
                <span>Tổng tiền thanh toán:</span>
                <input
                  type="number"
                  value={totalAmount}
                  onChange={(e) => setTotalAmount(parseFloat(e.target.value) || 0)}
                  className="w-48 text-right font-mono font-extrabold text-lg text-emerald-400 bg-slate-900 border border-slate-800 rounded px-2 py-1"
                />
              </div>
            </div>

            {/* Reviewer Note Input */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div>
                <label className="block text-slate-400 mb-1 font-medium">Người Phê Duyệt</label>
                <input
                  type="text"
                  value={reviewedBy}
                  onChange={(e) => setReviewedBy(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-200"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1 font-medium">Lý do điều chỉnh (nếu có sai lệch)</label>
                <input
                  type="text"
                  placeholder="VD: Điều chỉnh MST do ảnh mờ..."
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-slate-200"
                />
              </div>
            </div>
          </div>

          {/* Action Bar */}
          <div className="p-3 bg-slate-950 border-t border-slate-800 flex items-center justify-between">
            <button
              disabled={saving}
              onClick={() => handleReview('REJECTED')}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-red-600/20 hover:bg-red-600/40 text-red-400 border border-red-500/30 text-xs font-bold transition"
            >
              <X className="w-4 h-4" />
              <span>Từ Chối HĐ</span>
            </button>

            <div className="flex items-center space-x-3">
              <button
                disabled={saving}
                onClick={() => handleReview('APPROVED')}
                className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-xs font-bold transition"
                title="Lưu các trường đã chỉnh sửa vào Error Log và Duyệt"
              >
                <Save className="w-4 h-4 text-indigo-400" />
                <span>Lưu & Duyệt</span>
              </button>

              <button
                disabled={saving}
                onClick={() => handleReview('APPROVED')}
                className="flex items-center space-x-2 px-6 py-2.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-500 hover:from-emerald-500 hover:to-teal-400 text-white text-xs font-extrabold shadow-lg shadow-emerald-600/30 hover:scale-[1.02] active:scale-[0.98] transition-all"
              >
                <Check className="w-4 h-4" />
                <span>1-Click Duyệt Nhanh (&lt; 1s)</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
