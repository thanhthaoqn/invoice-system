import React, { useState, useEffect } from 'react';
import {
  Check,
  X,
  Search,
  Package,
  Pencil,
  AlertTriangle,
  Ban,
  ArrowLeft,
  Plus,
  Trash2,
  Save,
  CreditCard
} from 'lucide-react';
import { api, type Invoice, type InvoiceLineItem, type ReviewInvoiceRequest } from '../services/api';
import { DocumentViewer } from './DocumentViewer';

interface SplitScreenReviewProps {
  invoice: Invoice;
  onBack: () => void;
  onReviewed: (updated: Invoice) => void;
  canReview: boolean;
}

const formatVND = (num: number | null | undefined): string => {
  return new Intl.NumberFormat('vi-VN').format(Number(num) || 0) + ' đ';
};

export const SplitScreenReview: React.FC<SplitScreenReviewProps> = ({
  invoice,
  onBack,
  onReviewed,
  canReview
}) => {
  // Form / Data state
  const [invoiceNumber, setInvoiceNumber] = useState(invoice.invoiceNumber || '');
  const [invoiceDate, setInvoiceDate] = useState(invoice.invoiceDate || '');
  const [vendorName, setVendorName] = useState(invoice.vendorName || '');
  const [taxCode, setTaxCode] = useState(invoice.taxCode || '');
  const [buyerName, setBuyerName] = useState(invoice.buyerName || '');
  const [buyerTaxCode, setBuyerTaxCode] = useState(invoice.buyerTaxCode || '');
  const [buyerAddress, setBuyerAddress] = useState(invoice.buyerAddress || '');
  const [sellerAddress, setSellerAddress] = useState(invoice.sellerAddress || '');
  const [subtotalAmount, setSubtotalAmount] = useState<number>(invoice.subtotalAmount || 0);
  const [vatAmount, setVatAmount] = useState<number>(invoice.vatAmount || 0);
  const [totalAmount, setTotalAmount] = useState<number>(invoice.totalAmount || 0);
  const [category, setCategory] = useState(invoice.category || 'Chi phí Dịch vụ / Khác');
  const [debitAccount, setDebitAccount] = useState(invoice.debitAccount || '6427');
  const [vatAccount, setVatAccount] = useState(invoice.vatAccount || '1331');
  const [creditAccount, setCreditAccount] = useState(
    invoice.creditAccount || ((invoice.totalAmount || 0) >= 20000000 ? '1121' : '1111')
  );
  const [reviewedBy, setReviewedBy] = useState('Kế toán viên');
  const [reason, setReason] = useState('');
  const [lineItems, setLineItems] = useState<InvoiceLineItem[]>(
    invoice.lineItems && invoice.lineItems.length > 0 ? [...invoice.lineItems] : []
  );

  // UI state: View mode vs Edit mode
  const [isEditing, setIsEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  useEffect(() => {
    setInvoiceNumber(invoice.invoiceNumber || '');
    setInvoiceDate(invoice.invoiceDate || '');
    setVendorName(invoice.vendorName || '');
    setTaxCode(invoice.taxCode || '');
    setBuyerName(invoice.buyerName || '');
    setBuyerTaxCode(invoice.buyerTaxCode || '');
    setBuyerAddress(invoice.buyerAddress || '');
    setSellerAddress(invoice.sellerAddress || '');
    setSubtotalAmount(invoice.subtotalAmount || 0);
    setVatAmount(invoice.vatAmount || 0);
    setTotalAmount(invoice.totalAmount || 0);
    setCategory(invoice.category || 'Chi phí Dịch vụ / Khác');
    setDebitAccount(invoice.debitAccount || '6427');
    setVatAccount(invoice.vatAccount || '1331');
    setCreditAccount(invoice.creditAccount || ((invoice.totalAmount || 0) >= 20000000 ? '1121' : '1111'));
    setLineItems(invoice.lineItems && invoice.lineItems.length > 0 ? [...invoice.lineItems] : []);
    setIsEditing(false);
    setActionSuccess(null);
  }, [invoice]);

  // Recalculate totals from line items during editing
  const handleLineItemChange = (index: number, field: keyof InvoiceLineItem, value: any) => {
    const updated = [...lineItems];
    updated[index] = { ...updated[index], [field]: value };

    if (field === 'quantity' || field === 'unitPrice') {
      const q = Number(updated[index].quantity) || 0;
      const p = Number(updated[index].unitPrice) || 0;
      updated[index].amount = q * p;
    }
    setLineItems(updated);

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
        vatRate: '0%',
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
        buyerName,
        buyerTaxCode,
        buyerAddress,
        sellerAddress,
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
      setIsEditing(false);
      onReviewed(updated);
    } catch (err) {
      console.error(err);
      alert('Lỗi khi lưu đối soát hóa đơn.');
    } finally {
      setSaving(false);
    }
  };

  const computedSum = subtotalAmount + vatAmount;
  const isArithmeticMatched = Math.abs(computedSum - totalAmount) <= 1000;

  // Compute VAT rate display string
  const getVatRateDisplay = (): string => {
    if (lineItems.length > 0 && lineItems[0].vatRate) {
      return lineItems[0].vatRate;
    }
    if (subtotalAmount > 0 && vatAmount > 0) {
      const pct = Math.round((vatAmount / subtotalAmount) * 100);
      return `${pct} %`;
    }
    return '0 %';
  };

  const buyerNameVal = buyerName || invoice.buyerName || '—';
  const buyerAddressVal = buyerAddress || invoice.buyerAddress || '';
  const buyerTaxVal = buyerTaxCode || invoice.buyerTaxCode || '—';

  return (
    <div className="lg:h-[calc(100dvh-7.5rem)] min-h-[38rem] flex flex-col py-2 max-w-[1700px] mx-auto px-2 sm:px-4">
      {/* Top Bar Navigation & Info */}
      <div className="flex flex-wrap gap-3 items-center justify-between py-2 border-b border-slate-200 mb-2 shrink-0">
        <div className="flex items-center space-x-3">
          <button
            onClick={onBack}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 border border-slate-300 text-xs font-medium text-slate-700 hover:text-slate-900 shadow-2xs transition"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Quay lại danh sách</span>
          </button>

          <div className="flex items-center space-x-2">
            <span className="font-bold text-sm text-slate-900">
              Hóa đơn #{invoice.id} {invoiceNumber ? `(${invoiceNumber})` : ''}
            </span>
            <span
              className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                invoice.status === 'APPROVED'
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                  : invoice.status === 'REJECTED'
                  ? 'bg-rose-50 text-rose-700 border border-rose-200'
                  : 'bg-amber-50 text-amber-800 border border-amber-200'
              }`}
            >
              {invoice.status === 'APPROVED' ? 'Đã duyệt' : invoice.status === 'REJECTED' ? 'Từ chối' : 'Chờ đối soát'}
            </span>
          </div>
        </div>

        {actionSuccess && (
          <div className="flex items-center space-x-2 text-xs text-emerald-800 bg-emerald-50 border border-emerald-200 px-3 py-1 rounded-lg">
            <Check className="w-3.5 h-3.5 text-emerald-600" />
            <span>{actionSuccess}</span>
          </div>
        )}

        <div className="flex items-center space-x-4 text-xs text-slate-500">
          <span>Người nộp: <strong className="text-slate-700 font-medium">{invoice.submittedBy}</strong></span>
          <span>Ngày nộp: <strong className="text-slate-700 font-medium">{invoice.createdAt ? new Date(invoice.createdAt).toLocaleDateString('vi-VN') : '—'}</strong></span>
        </div>
      </div>

      {/* Main Container: Split 50/50 Card */}
      <div className="flex-1 min-h-0 bg-white border border-slate-300/80 rounded-xl overflow-hidden shadow-2xs flex flex-col">
        {/* Split Body */}
        <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-2 divide-y lg:divide-y-0 lg:divide-x divide-slate-200 overflow-hidden">
          {/* LEFT PANEL: Chi tiết & So khớp Hóa đơn */}
          <div className="flex flex-col min-h-0 overflow-y-auto p-4 sm:p-6 bg-white">
            {/* Header */}
            <div className="flex items-center justify-between pb-3 mb-4 border-b border-slate-100">
              <div className="flex items-center space-x-2">
                <Search className="w-4 h-4 text-sky-600" />
                <h2 className="text-base font-bold text-slate-800 tracking-tight">
                  {isEditing ? 'Chỉnh sửa thông tin hóa đơn' : 'Chi tiết & So khớp Hóa đơn'}
                </h2>
              </div>
              {isEditing && (
                <span className="text-[11px] font-medium text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded">
                  Chế độ chỉnh sửa
                </span>
              )}
            </div>

            {/* Subtle Alert Banners (only shown if there are actual issues) */}
            {invoice.validationState === 'BUYER_MISMATCH' && (
              <div className="mb-3 p-2.5 bg-amber-50/90 border border-amber-200/80 rounded-lg text-amber-900 text-xs flex items-start space-x-2">
                <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                <span>MST người mua thiếu hoặc khác doanh nghiệp nhận. Cần kiểm tra người mua trong XML gốc trước khi duyệt.</span>
              </div>
            )}

            {!isArithmeticMatched && (
              <div className="mb-3 p-2.5 bg-rose-50/90 border border-rose-200/80 rounded-lg text-rose-900 text-xs flex items-start space-x-2">
                <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                <span>Cảnh báo sai lệch số học: Tổng hàng + VAT ({formatVND(computedSum)}) khác Tổng thanh toán ({formatVND(totalAmount)}).</span>
              </div>
            )}

            {invoice.duplicateSuspected && (
              <div className="mb-3 p-2.5 bg-amber-50/90 border border-amber-200/80 rounded-lg text-amber-900 text-xs flex items-start space-x-2">
                <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                <span>Nghi vấn trùng lặp: {invoice.duplicateWarningMessage || 'Đã phát hiện hóa đơn tương tự trong hệ thống.'}</span>
              </div>
            )}

            {(invoice.cashlessRequired || totalAmount >= 20000000) && (
              <div className="mb-3 p-2.5 bg-sky-50/80 border border-sky-200/80 rounded-lg text-sky-900 text-xs flex items-start space-x-2">
                <CreditCard className="w-4 h-4 text-sky-600 shrink-0 mt-0.5" />
                <span>Ngưỡng thanh toán không dùng tiền mặt (≥ 20.000.000đ): Bắt buộc chuyển khoản qua ngân hàng (Có TK 1121).</span>
              </div>
            )}

            {/* ================= VIEW MODE (Exactly like Image 2) ================= */}
            {!isEditing ? (
              <div className="space-y-4">
                {/* Key-Value Summary List */}
                <div className="text-xs space-y-2.5 divide-y divide-slate-100">
                  {/* Người mua */}
                  <div className="flex items-start pt-1">
                    <span className="w-36 sm:w-44 shrink-0 text-slate-500 font-normal">Người mua:</span>
                    <div className="flex-1 min-w-0">
                      <p className="font-semibold text-slate-900 text-sm">{buyerNameVal}</p>
                      {buyerAddressVal && (
                        <p className="text-slate-500 text-[11px] mt-0.5 leading-relaxed">{buyerAddressVal}</p>
                      )}
                    </div>
                  </div>

                  {/* MST Nhận */}
                  <div className="flex items-center pt-2.5">
                    <span className="w-36 sm:w-44 shrink-0 text-slate-500 font-normal">MST Nhận:</span>
                    <span className="font-mono text-slate-800">{buyerTaxVal}</span>
                  </div>

                  {/* Đơn vị bán */}
                  {vendorName && (
                    <div className="flex items-start pt-2.5">
                      <span className="w-36 sm:w-44 shrink-0 text-slate-500 font-normal">Đơn vị bán:</span>
                      <div className="flex-1 min-w-0">
                        <span className="font-medium text-slate-800">
                          {vendorName} {taxCode ? `(MST: ${taxCode})` : ''}
                        </span>
                        {(sellerAddress || invoice.sellerAddress) && (
                          <p className="text-slate-500 text-[11px] mt-0.5 leading-relaxed">
                            {sellerAddress || invoice.sellerAddress}
                          </p>
                        )}
                      </div>
                    </div>
                  )}

                  {/* Số HĐ / Ngày */}
                  {(invoiceNumber || invoiceDate) && (
                    <div className="flex items-center pt-2.5">
                      <span className="w-36 sm:w-44 shrink-0 text-slate-500 font-normal">Số HĐ / Ngày lập:</span>
                      <span className="font-mono text-slate-800">
                        {invoiceNumber || '—'} {invoiceDate ? `· ${invoiceDate}` : ''}
                      </span>
                    </div>
                  )}

                  {/* Tổng tiền chưa thuế */}
                  <div className="flex items-center pt-2.5">
                    <span className="w-36 sm:w-44 shrink-0 text-slate-500 font-normal">Tổng tiền chưa thuế:</span>
                    <span className="font-medium text-slate-900 font-mono">{formatVND(subtotalAmount)}</span>
                  </div>

                  {/* Thuế suất */}
                  <div className="flex items-center pt-2.5">
                    <span className="w-36 sm:w-44 shrink-0 text-slate-500 font-normal">Thuế suất:</span>
                    <span className="font-medium text-slate-900 font-mono">{getVatRateDisplay()}</span>
                  </div>

                  {/* Tiền thuế VAT */}
                  <div className="flex items-center pt-2.5">
                    <span className="w-36 sm:w-44 shrink-0 text-slate-500 font-normal">Tiền thuế VAT:</span>
                    <span className="font-medium text-slate-900 font-mono">{formatVND(vatAmount)}</span>
                  </div>

                  {/* Tổng thanh toán */}
                  <div className="flex items-center pt-2.5">
                    <span className="w-36 sm:w-44 shrink-0 text-slate-500 font-normal">Tổng thanh toán:</span>
                    <span className="font-bold text-emerald-600 text-base font-mono">{formatVND(totalAmount)}</span>
                  </div>

                  {/* Định khoản kế toán (Thông tư 200/133) */}
                  <div className="flex items-center pt-2.5">
                    <span className="w-36 sm:w-44 shrink-0 text-slate-500 font-normal">Định khoản:</span>
                    <span className="text-slate-700 font-mono text-[11px] bg-slate-50 px-2 py-0.5 rounded border border-slate-200">
                      Nợ TK {debitAccount} / Nợ TK {vatAccount} / Có TK {creditAccount}
                    </span>
                  </div>
                </div>

                {/* Section: Chi tiết hàng hóa dịch vụ */}
                <div className="pt-2">
                  <div className="flex items-center space-x-1.5 mb-2">
                    <Package className="w-4 h-4 text-amber-700" />
                    <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wide">
                      Chi tiết hàng hóa dịch vụ
                    </h3>
                  </div>

                  <div className="overflow-x-auto border border-slate-200 rounded">
                    <table className="w-full text-xs border-collapse">
                      <thead>
                        <tr className="bg-slate-100 text-slate-700 text-[11px] font-bold uppercase border-b border-slate-200">
                          <th className="p-2 text-left border-r border-slate-200 font-semibold">TÊN HÀNG HÓA, DỊCH VỤ</th>
                          <th className="p-2 text-center w-24 border-r border-slate-200 font-semibold">SỐ LƯỢNG</th>
                          <th className="p-2 text-right w-28 border-r border-slate-200 font-semibold">ĐƠN GIÁ</th>
                          <th className="p-2 text-right w-28 font-semibold">THÀNH TIỀN</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100 bg-white">
                        {lineItems.length > 0 ? (
                          lineItems.map((item, idx) => (
                            <tr key={idx} className="hover:bg-slate-50/50">
                              <td className="p-2 border-r border-slate-200 text-slate-800 font-medium">
                                {item.itemName || 'Hàng hóa / Dịch vụ'}
                              </td>
                              <td className="p-2 text-center border-r border-slate-200 text-slate-700 font-mono">
                                {item.quantity || 1}
                              </td>
                              <td className="p-2 text-right border-r border-slate-200 text-slate-700 font-mono">
                                {formatVND(item.unitPrice)}
                              </td>
                              <td className="p-2 text-right text-slate-900 font-semibold font-mono">
                                {formatVND(item.amount)}
                              </td>
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan={4} className="p-4 text-center text-slate-400">
                              Không có dòng mặt hàng nào
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            ) : (
              /* ================= EDIT MODE (Professional Accounting Form) ================= */
              <div className="space-y-4 text-xs">
                {/* Basic Fields Grid */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-slate-50/70 p-3 rounded-lg border border-slate-200">
                  <div>
                    <label className="block text-slate-600 mb-1 font-medium">Số Hóa Đơn</label>
                    <input
                      type="text"
                      value={invoiceNumber}
                      onChange={(e) => setInvoiceNumber(e.target.value)}
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5 text-slate-900 font-mono font-medium focus:border-sky-600 focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-slate-600 mb-1 font-medium">Ngày Lập</label>
                    <input
                      type="date"
                      value={invoiceDate}
                      onChange={(e) => setInvoiceDate(e.target.value)}
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5 text-slate-900 font-mono focus:border-sky-600 focus:outline-none"
                    />
                  </div>

                  <div className="sm:col-span-2">
                    <label className="block text-slate-600 mb-1 font-medium">Đơn Vị Bán Hàng</label>
                    <input
                      type="text"
                      value={vendorName}
                      onChange={(e) => setVendorName(e.target.value)}
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5 text-slate-900 font-medium focus:border-sky-600 focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-slate-600 mb-1 font-medium">Mã Số Thuế Bên Bán</label>
                    <input
                      type="text"
                      value={taxCode}
                      onChange={(e) => setTaxCode(e.target.value)}
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5 text-slate-900 font-mono font-medium focus:border-sky-600 focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-slate-600 mb-1 font-medium">Phân Loại Chi Phí</label>
                    <select
                      value={category}
                      onChange={(e) => setCategory(e.target.value)}
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5 text-slate-900 font-medium focus:border-sky-600 focus:outline-none"
                    >
                      <option value="Chi phí Công tác">Chi phí Công tác (Vé máy bay, khách sạn)</option>
                      <option value="Tiếp khách / Ăn uống">Tiếp khách / Ăn uống (Nhà hàng, cafe)</option>
                      <option value="Mua sắm Vật tư / Thiết bị">Mua sắm Vật tư / Thiết bị (Máy tính, VPP)</option>
                      <option value="Đào tạo / Hội thảo">Đào tạo / Hội thảo (Khóa học, vé sự kiện)</option>
                      <option value="Chi phí Dịch vụ / Khác">Chi phí Dịch vụ / Khác (Cloud, phần mềm)</option>
                    </select>
                  </div>

                  <div className="sm:col-span-2">
                    <label className="block text-slate-600 mb-1 font-medium">Địa Chỉ Bên Bán</label>
                    <input
                      type="text"
                      value={sellerAddress}
                      onChange={(e) => setSellerAddress(e.target.value)}
                      placeholder="Số 46, Ngõ 10, Đường Nguyễn Văn Huyên..."
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5 text-slate-900 focus:border-sky-600 focus:outline-none"
                    />
                  </div>

                  {/* Buyer Fields */}
                  <div className="sm:col-span-2 pt-2 border-t border-slate-200">
                    <label className="block text-slate-600 mb-1 font-medium">Tên Người Mua / Đơn Vị Nhận</label>
                    <input
                      type="text"
                      value={buyerName}
                      onChange={(e) => setBuyerName(e.target.value)}
                      placeholder="Tên công ty hoặc họ tên cá nhân"
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5 text-slate-900 font-medium focus:border-sky-600 focus:outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-slate-600 mb-1 font-medium">Mã Số Thuế Bên Mua</label>
                    <input
                      type="text"
                      value={buyerTaxCode}
                      onChange={(e) => setBuyerTaxCode(e.target.value)}
                      placeholder="0100892628"
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5 text-slate-900 font-mono focus:border-sky-600 focus:outline-none"
                    />
                  </div>

                  <div className="sm:col-span-2">
                    <label className="block text-slate-600 mb-1 font-medium">Địa Chỉ Bên Mua</label>
                    <input
                      type="text"
                      value={buyerAddress}
                      onChange={(e) => setBuyerAddress(e.target.value)}
                      placeholder="Địa chỉ nhận hàng / đơn vị mua..."
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5 text-slate-900 focus:border-sky-600 focus:outline-none"
                    />
                  </div>
                </div>

                {/* Accounting Mapping */}
                <div className="bg-slate-50/70 p-3 rounded-lg border border-slate-200 space-y-2">
                  <span className="font-semibold text-slate-800">Định Khoản Kế Toán</span>
                  <div className="grid grid-cols-3 gap-2">
                    <div>
                      <label className="block text-[11px] text-slate-600 mb-0.5">Nợ TK (Chi phí)</label>
                      <input
                        type="text"
                        value={debitAccount}
                        onChange={(e) => setDebitAccount(e.target.value)}
                        className="w-full bg-white border border-slate-300 rounded px-2 py-1 font-mono text-center"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] text-slate-600 mb-0.5">Nợ TK 1331 (VAT)</label>
                      <input
                        type="text"
                        value={vatAccount}
                        onChange={(e) => setVatAccount(e.target.value)}
                        className="w-full bg-white border border-slate-300 rounded px-2 py-1 font-mono text-center"
                      />
                    </div>
                    <div>
                      <label className="block text-[11px] text-slate-600 mb-0.5">Có TK (Thanh toán)</label>
                      <input
                        type="text"
                        value={creditAccount}
                        onChange={(e) => setCreditAccount(e.target.value)}
                        className="w-full bg-white border border-slate-300 rounded px-2 py-1 font-mono text-center"
                      />
                    </div>
                  </div>
                </div>

                {/* Editable Line Items */}
                <div className="border border-slate-200 rounded overflow-hidden">
                  <div className="p-2 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
                    <span className="font-bold text-slate-800">Chi Tiết Mặt Hàng</span>
                    <button
                      type="button"
                      onClick={handleAddLineItem}
                      className="flex items-center space-x-1 px-2 py-0.5 bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 rounded text-xs"
                    >
                      <Plus className="w-3 h-3" />
                      <span>Thêm dòng</span>
                    </button>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs">
                      <thead className="bg-slate-50 text-slate-600 uppercase border-b border-slate-200">
                        <tr>
                          <th className="p-1.5 text-left">Tên Mặt Hàng</th>
                          <th className="p-1.5 w-16 text-right">SL</th>
                          <th className="p-1.5 w-24 text-right">Đơn Giá</th>
                          <th className="p-1.5 w-28 text-right">Thành Tiền</th>
                          <th className="p-1.5 w-8"></th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {lineItems.map((item, idx) => (
                          <tr key={idx}>
                            <td className="p-1">
                              <input
                                type="text"
                                value={item.itemName}
                                onChange={(e) => handleLineItemChange(idx, 'itemName', e.target.value)}
                                className="w-full bg-white border border-slate-200 rounded px-1.5 py-1"
                              />
                            </td>
                            <td className="p-1 text-right">
                              <input
                                type="number"
                                value={item.quantity}
                                onChange={(e) => handleLineItemChange(idx, 'quantity', parseFloat(e.target.value) || 0)}
                                className="w-full text-right bg-white border border-slate-200 rounded px-1 py-1 font-mono"
                              />
                            </td>
                            <td className="p-1 text-right">
                              <input
                                type="number"
                                value={item.unitPrice}
                                onChange={(e) => handleLineItemChange(idx, 'unitPrice', parseFloat(e.target.value) || 0)}
                                className="w-full text-right bg-white border border-slate-200 rounded px-1 py-1 font-mono"
                              />
                            </td>
                            <td className="p-1 text-right font-mono font-bold text-slate-800">
                              {formatVND(item.amount)}
                            </td>
                            <td className="p-1 text-center">
                              <button
                                type="button"
                                onClick={() => handleRemoveLineItem(idx)}
                                className="text-slate-400 hover:text-rose-600"
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

                {/* Amount Totals */}
                <div className="bg-slate-50/70 p-3 rounded-lg border border-slate-200 space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="text-slate-600">Tổng tiền chưa thuế:</span>
                    <input
                      type="number"
                      value={subtotalAmount}
                      onChange={(e) => {
                        const val = parseFloat(e.target.value) || 0;
                        setSubtotalAmount(val);
                        setTotalAmount(val + vatAmount);
                      }}
                      className="w-36 text-right font-mono bg-white border border-slate-300 rounded px-2 py-1 font-medium"
                    />
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-slate-600">Tiền thuế VAT:</span>
                    <input
                      type="number"
                      value={vatAmount}
                      onChange={(e) => {
                        const val = parseFloat(e.target.value) || 0;
                        setVatAmount(val);
                        setTotalAmount(subtotalAmount + val);
                      }}
                      className="w-36 text-right font-mono bg-white border border-slate-300 rounded px-2 py-1 font-medium"
                    />
                  </div>
                  <div className="flex justify-between items-center pt-1 border-t border-slate-200 font-bold">
                    <span className="text-slate-900">Tổng thanh toán:</span>
                    <input
                      type="number"
                      value={totalAmount}
                      onChange={(e) => setTotalAmount(parseFloat(e.target.value) || 0)}
                      className="w-36 text-right font-mono text-emerald-700 bg-white border border-emerald-300 rounded px-2 py-1 font-bold"
                    />
                  </div>
                </div>

                {/* Reason & Reviewer */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-slate-600 mb-1 font-medium">Người Phê Duyệt</label>
                    <input
                      type="text"
                      value={reviewedBy}
                      onChange={(e) => setReviewedBy(e.target.value)}
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5"
                    />
                  </div>
                  <div>
                    <label className="block text-slate-600 mb-1 font-medium">Lý do điều chỉnh (Error Log)</label>
                    <input
                      type="text"
                      placeholder="VD: Điều chỉnh MST do ảnh mờ..."
                      value={reason}
                      onChange={(e) => setReason(e.target.value)}
                      className="w-full bg-white border border-slate-300 rounded px-2.5 py-1.5"
                    />
                  </div>
                </div>

                {/* Edit Mode Buttons */}
                <div className="flex items-center justify-end space-x-2 pt-2 border-t border-slate-200">
                  <button
                    type="button"
                    onClick={() => setIsEditing(false)}
                    className="px-3 py-1.5 bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 rounded text-xs font-medium"
                  >
                    Hủy sửa
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setIsEditing(false);
                      setActionSuccess('Đã cập nhật dữ liệu tạm thời.');
                    }}
                    className="px-3 py-1.5 bg-slate-800 hover:bg-slate-900 text-white rounded text-xs font-medium flex items-center space-x-1"
                  >
                    <Save className="w-3.5 h-3.5" />
                    <span>Lưu thay đổi</span>
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* RIGHT PANEL: Document Viewer */}
          <div className="flex flex-col min-h-0 overflow-hidden bg-slate-50/50">
            <DocumentViewer key={invoice.id} invoice={invoice} />
          </div>
        </div>

        {/* BOTTOM ACTION BAR (Matching Image 2 exactly) */}
        <div className="p-3 bg-white border-t border-slate-200 flex flex-wrap gap-2 items-center justify-between shrink-0">
          {/* Left: Sửa thông tin (Correction) */}
          <div>
            {!isEditing ? (
              <button
                type="button"
                onClick={() => setIsEditing(true)}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-sky-50 hover:bg-sky-100 text-sky-700 border border-sky-200 text-xs font-medium transition"
              >
                <Pencil className="w-3.5 h-3.5 text-sky-600" />
                <span>Sửa thông tin (Correction)</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={() => setIsEditing(false)}
                className="flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 text-xs font-medium transition"
              >
                <X className="w-3.5 h-3.5" />
                <span>Thoát chỉnh sửa</span>
              </button>
            )}
          </div>

          {/* Right Action Buttons */}
          <div className="flex items-center space-x-2">
            {/* Đóng */}
            <button
              type="button"
              onClick={onBack}
              className="px-4 py-2 rounded-lg bg-white hover:bg-slate-50 border border-slate-300 text-slate-700 text-xs font-medium transition shadow-2xs"
            >
              Đóng
            </button>

            {/* Hủy hóa đơn */}
            <button
              type="button"
              disabled={saving || !canReview}
              onClick={() => {
                if (window.confirm('Bạn có chắc chắn muốn hủy bỏ hóa đơn này?')) {
                  handleReview('REJECTED');
                }
              }}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-lg bg-white hover:bg-slate-50 border border-slate-300 text-slate-700 text-xs font-medium transition shadow-2xs"
            >
              <Ban className="w-3.5 h-3.5 text-slate-500" />
              <span>Hủy hóa đơn</span>
            </button>

            {/* Từ chối */}
            <button
              type="button"
              disabled={saving || !canReview}
              onClick={() => handleReview('REJECTED')}
              className="flex items-center space-x-1.5 px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-white text-xs font-semibold transition shadow-2xs"
            >
              <X className="w-3.5 h-3.5" />
              <span>Từ chối</span>
            </button>

            {/* Duyệt hóa đơn */}
            <button
              type="button"
              disabled={saving || !canReview}
              onClick={() => handleReview('APPROVED')}
              className="flex items-center space-x-1.5 px-5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold transition shadow-2xs"
            >
              <Check className="w-4 h-4" />
              <span>Duyệt hóa đơn</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
