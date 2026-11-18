import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import {
  X,
  GitCompare,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Download,
  ExternalLink,
  ShieldCheck
} from 'lucide-react';
import { api, type Invoice, type IntakeItem, intakeApi } from '../services/api';
import { XmlInvoicePreview } from './XmlInvoicePreview';

interface Props {
  item: IntakeItem;
  onClose: () => void;
  onOpenOriginalInvoice: (invoiceId: number) => void;
}

const formatVND = (num: number | null | undefined): string => {
  if (num == null) return '—';
  return new Intl.NumberFormat('vi-VN').format(Number(num) || 0) + ' đ';
};

interface ExtractedData {
  invoiceNumber?: string;
  invoiceSeries?: string;
  invoiceTemplate?: string;
  invoiceDate?: string;
  vendorName?: string;
  taxCode?: string;
  buyerName?: string;
  buyerTaxCode?: string;
  totalAmount?: number;
  vatAmount?: number;
  subtotalAmount?: number;
  lineItemsCount?: number;
  currency?: string;
}

function parseXmlData(source: string): ExtractedData | null {
  try {
    const doc = new DOMParser().parseFromString(source, 'application/xml');
    if (doc.getElementsByTagNameNS('*', 'parsererror').length) return null;
    const data = doc.getElementsByTagNameNS('*', 'DLHDon')[0] || doc.documentElement;
    if (!data) return null;

    const find = (parent: Element | null, tag: string) => {
      if (!parent) return '';
      const el = Array.from(parent.children).find(c => c.localName.toLowerCase() === tag.toLowerCase());
      return el?.textContent?.trim() || '';
    };

    const common = Array.from(data.children).find(c => c.localName.toLowerCase() === 'ttchung') || null;
    const body = Array.from(data.children).find(c => c.localName.toLowerCase() === 'ndhdon') || null;
    const seller = body ? Array.from(body.children).find(c => c.localName.toLowerCase() === 'nban') || null : null;
    const buyer = body ? Array.from(body.children).find(c => c.localName.toLowerCase() === 'nmua') || null : null;
    const payment = body ? Array.from(body.children).find(c => c.localName.toLowerCase() === 'ttoan') || null : null;
    const goods = body ? Array.from(body.children).find(c => ['dshhdvu', 'dshhang'].includes(c.localName.toLowerCase())) || null : null;

    const lines = goods ? Array.from(goods.children).filter(c => c.localName.toLowerCase() === 'hhdvu') : [];

    return {
      invoiceNumber: find(common, 'shdon'),
      invoiceSeries: find(common, 'khhdon'),
      invoiceTemplate: find(common, 'khmshdon'),
      invoiceDate: find(common, 'nlap'),
      vendorName: find(seller, 'ten'),
      taxCode: find(seller, 'mst'),
      buyerName: find(buyer, 'ten'),
      buyerTaxCode: find(buyer, 'mst'),
      totalAmount: Number(find(payment, 'tgtttbso')) || undefined,
      subtotalAmount: Number(find(payment, 'tgtcthue')) || undefined,
      vatAmount: Number(find(payment, 'tgtthue')) || undefined,
      lineItemsCount: lines.length,
      currency: find(common, 'dvtte') || 'VND'
    };
  } catch (e) {
    return null;
  }
}

export const DuplicateComparisonModal: React.FC<Props> = ({
  item,
  onClose,
  onOpenOriginalInvoice
}) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [originalInvoice, setOriginalInvoice] = useState<Invoice | null>(null);

  // Original invoice raw file (if XML)
  const [originalXmlText, setOriginalXmlText] = useState('');

  // New uploaded item content
  const [newXmlText, setNewXmlText] = useState('');
  const [newExtracted, setNewExtracted] = useState<ExtractedData | null>(null);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        if (!item.invoiceId) {
          throw new Error('Tài liệu không có mã hóa đơn gốc để so sánh');
        }

        // 1. Tải hóa đơn gốc đã lưu trong hệ thống
        const orig = await api.getInvoice(item.invoiceId);
        if (!mounted) return;
        setOriginalInvoice(orig);

        // 2. Nếu hóa đơn gốc là XML, tải nội dung XML gốc
        if (orig.fileType === 'XML') {
          try {
            const res = await axios.get<string>(`/api/invoices/${orig.id}/file`, { responseType: 'text' });
            if (mounted) setOriginalXmlText(res.data);
          } catch (e) {
            console.error('Không tải được XML gốc', e);
          }
        }

        // 3. Tải tệp mới nộp lên
        const isNewXml = item.documentType === 'XML' || item.fileName.toLowerCase().endsWith('.xml');
        if (isNewXml) {
          const res = await axios.get<string>(`/api/intake/items/${item.id}/file`, { responseType: 'text' });
          if (mounted) {
            setNewXmlText(res.data);
            const parsed = parseXmlData(res.data);
            setNewExtracted(parsed);
          }
        } else {
          // New file is PDF: cố gắng lấy dữ liệu trích xuất từ draft nếu có
          try {
            const draft = await intakeApi.pdfDraft(item.id);
            if (mounted && draft.data) {
              setNewExtracted({
                invoiceNumber: draft.data.invoice_number || undefined,
                invoiceSeries: draft.data.invoice_series || undefined,
                invoiceTemplate: draft.data.invoice_template || undefined,
                invoiceDate: draft.data.invoice_date || undefined,
                vendorName: draft.data.vendor_name || undefined,
                taxCode: draft.data.tax_code || undefined,
                buyerName: draft.data.buyer_name || undefined,
                buyerTaxCode: draft.data.buyer_tax_code || undefined,
                totalAmount: draft.data.total_amount || undefined,
                vatAmount: draft.data.vat_amount || undefined,
                subtotalAmount: draft.data.subtotal_amount || undefined,
                lineItemsCount: draft.data.line_items?.length || 0,
                currency: draft.data.currency || 'VND'
              });
            }
          } catch {
            // Không có draft (PDF đính kèm hoặc chưa bóc tách)
          }
        }
      } catch (err: unknown) {
        if (mounted) {
          setError(err instanceof Error ? err.message : 'Lỗi khi tải thông tin đối chiếu');
        }
      } finally {
        if (mounted) setLoading(false);
      }
    };

    void load();
    return () => {
      mounted = false;
    };
  }, [item]);

  // So khớp đối chiếu
  const diff = useMemo(() => {
    if (!originalInvoice) return null;

    const origNo = (originalInvoice.invoiceNumber || '').trim();
    const newNo = (newExtracted?.invoiceNumber || '').trim();
    const matchNumber = !newNo || origNo === newNo || Number(origNo) === Number(newNo);

    const origTax = (originalInvoice.taxCode || '').replace(/\D/g, '');
    const newTax = (newExtracted?.taxCode || '').replace(/\D/g, '');
    const matchTax = !newTax || origTax === newTax;

    const origAmount = originalInvoice.totalAmount || 0;
    const newAmount = newExtracted?.totalAmount || 0;
    const matchAmount = !newExtracted?.totalAmount || Math.abs(origAmount - newAmount) < 1;

    const isSameBinary = originalInvoice.contentHash && item.contentHash && originalInvoice.contentHash === item.contentHash;

    return {
      matchNumber,
      matchTax,
      matchAmount,
      isSameBinary
    };
  }, [originalInvoice, newExtracted, item.contentHash]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-900/70 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="relative w-full max-w-[96vw] xl:max-w-7xl h-[94vh] bg-white rounded-2xl shadow-2xl overflow-hidden flex flex-col border border-slate-200">
        
        {/* Header Modal */}
        <header className="px-6 py-4 border-b border-slate-200 bg-gradient-to-r from-amber-50/80 via-white to-indigo-50/50 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-amber-100 text-amber-700 shadow-2xs">
              <GitCompare className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-slate-900">Đối chiếu & So sánh Hóa đơn Trùng lặp</h2>
                <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200">
                  DUPLICATE DETECTED
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-0.5">
                So sánh song song giữa hóa đơn đã lưu trong hệ thống và tệp mới nộp lên bị cảnh báo trùng
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </header>

        {loading ? (
          <div className="flex-1 flex items-center justify-center text-slate-500">
            <Clock className="w-6 h-6 animate-spin mr-2 text-indigo-600" />
            Đang tải dữ liệu đối chiếu 2 bên…
          </div>
        ) : error ? (
          <div className="p-8 text-center text-rose-600 space-y-2">
            <AlertTriangle className="w-10 h-10 mx-auto" />
            <p className="font-semibold">{error}</p>
          </div>
        ) : !originalInvoice ? (
          <div className="p-8 text-center text-slate-500">Không tìm thấy thông tin hóa đơn gốc.</div>
        ) : (
          <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
            {/* Quick Diff Alert & Summary Cards */}
            <div className="px-6 py-3 bg-slate-50 border-b border-slate-200 shrink-0">
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                {/* 1. Mã số thuế */}
                <div className="p-2.5 rounded-xl bg-white border border-slate-200 shadow-2xs">
                  <span className="text-slate-500 block">Mã số thuế bên bán</span>
                  <div className="font-semibold text-slate-900 font-mono mt-0.5 flex items-center gap-1.5">
                    {diff?.matchTax ? (
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                    ) : (
                      <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
                    )}
                    <span>{originalInvoice.taxCode || 'Chưa rõ'}</span>
                  </div>
                </div>

                {/* 2. Số hóa đơn */}
                <div className="p-2.5 rounded-xl bg-white border border-slate-200 shadow-2xs">
                  <span className="text-slate-500 block">Số hóa đơn & Ký hiệu</span>
                  <div className="font-semibold text-slate-900 font-mono mt-0.5 flex items-center gap-1.5">
                    <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                    <span>
                      {originalInvoice.invoiceNumber} · {originalInvoice.invoiceSeries}
                    </span>
                  </div>
                </div>

                {/* 3. Tổng tiền */}
                <div className="p-2.5 rounded-xl bg-white border border-slate-200 shadow-2xs">
                  <span className="text-slate-500 block">Tổng tiền thanh toán</span>
                  <div className="font-semibold text-slate-900 mt-0.5 flex items-center gap-1.5">
                    {diff?.matchAmount ? (
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                    ) : (
                      <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
                    )}
                    <span>{formatVND(originalInvoice.totalAmount)}</span>
                  </div>
                </div>

                {/* 4. Tệp nhị phân SHA-256 */}
                <div className="p-2.5 rounded-xl bg-white border border-slate-200 shadow-2xs">
                  <span className="text-slate-500 block">Kiểm tra nội dung tệp (Hash)</span>
                  <div className="font-semibold mt-0.5 flex items-center gap-1 text-[11px] truncate">
                    {diff?.isSameBinary ? (
                      <span className="text-emerald-700 flex items-center gap-1">
                        <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                        Trùng 100% file gốc
                      </span>
                    ) : (
                      <span className="text-amber-700 flex items-center gap-1" title="Khác định dạng hoặc đã qua chỉnh sửa">
                        <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                        Khác file ({originalInvoice.fileType} vs {item.documentType})
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Side-by-Side 2 Viewers */}
            <div className="flex-1 grid grid-cols-1 md:grid-cols-2 divide-y md:divide-y-0 md:divide-x divide-slate-200 min-h-0 bg-slate-100">
              {/* LEFT COLUMN: HÓA ĐƠN GỐC TRONG HỆ THỐNG */}
              <div className="flex flex-col h-full min-h-0 bg-white">
                <div className="p-3 bg-emerald-50/70 border-b border-emerald-100 flex items-center justify-between shrink-0">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-600 text-white">
                      BÊN 1: HÓA ĐƠN GỐC
                    </span>
                    <span className="font-semibold text-xs text-slate-800 truncate">
                      Hồ sơ #{originalInvoice.id} ({originalInvoice.fileName})
                    </span>
                  </div>
                  <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                    {originalInvoice.status === 'APPROVED' ? 'Đã duyệt' : 'Chờ đối soát'}
                  </span>
                </div>

                {/* Summary Info Header */}
                <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 text-xs grid grid-cols-2 gap-2 shrink-0">
                  <div>
                    <span className="text-slate-500">Đơn vị bán:</span>{' '}
                    <strong className="text-slate-800">{originalInvoice.vendorName}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500">Ngày lập:</span>{' '}
                    <strong>{originalInvoice.invoiceDate}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500">Tổng tiền:</span>{' '}
                    <strong className="text-emerald-700">{formatVND(originalInvoice.totalAmount)}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500">Số dòng hàng:</span>{' '}
                    <strong>{originalInvoice.lineItems.length} mặt hàng</strong>
                  </div>
                </div>

                {/* Left Document Body */}
                <div className="flex-1 min-h-0 overflow-auto bg-slate-50/50">
                  {originalInvoice.fileType === 'XML' && originalXmlText ? (
                    <div className="p-3">
                      <XmlInvoicePreview source={originalXmlText} />
                    </div>
                  ) : originalInvoice.fileType === 'PDF' ? (
                    <iframe
                      src={`/api/invoices/${originalInvoice.id}/file`}
                      className="w-full h-full border-0"
                      title="Original PDF Document"
                    />
                  ) : (
                    <div className="p-8 text-center text-slate-400 text-xs">
                      Không có bản thể hiện trực tiếp cho hóa đơn này.
                    </div>
                  )}
                </div>
              </div>

              {/* RIGHT COLUMN: TỆP MỚI NỘP LÊN (BỊ BÁO TRÙNG) */}
              <div className="flex flex-col h-full min-h-0 bg-white">
                <div className="p-3 bg-amber-50/80 border-b border-amber-200 flex items-center justify-between shrink-0">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-amber-600 text-white">
                      BÊN 2: TỆP MỚI NỘP
                    </span>
                    <span className="font-semibold text-xs text-slate-800 truncate">
                      {item.fileName} ({item.documentType})
                    </span>
                  </div>
                  <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 border border-amber-300">
                    Chặn nộp trùng
                  </span>
                </div>

                {/* Summary Info Header */}
                <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 text-xs grid grid-cols-2 gap-2 shrink-0">
                  <div>
                    <span className="text-slate-500">Đơn vị bán:</span>{' '}
                    <strong className="text-slate-800">{newExtracted?.vendorName || originalInvoice.vendorName}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500">Ngày lập:</span>{' '}
                    <strong>{newExtracted?.invoiceDate || originalInvoice.invoiceDate}</strong>
                  </div>
                  <div>
                    <span className="text-slate-500">Tổng tiền:</span>{' '}
                    <strong className="text-amber-700">
                      {newExtracted?.totalAmount ? formatVND(newExtracted.totalAmount) : formatVND(originalInvoice.totalAmount)}
                    </strong>
                  </div>
                  <div>
                    <span className="text-slate-500">Số dòng hàng:</span>{' '}
                    <strong>
                      {newExtracted?.lineItemsCount != null
                        ? `${newExtracted.lineItemsCount} mặt hàng`
                        : `${originalInvoice.lineItems.length} mặt hàng`}
                    </strong>
                  </div>
                </div>

                {/* Right Document Body */}
                <div className="flex-1 min-h-0 overflow-auto bg-slate-50/50">
                  {(item.documentType === 'XML' || item.fileName.toLowerCase().endsWith('.xml')) && newXmlText ? (
                    <div className="p-3">
                      <XmlInvoicePreview source={newXmlText} />
                    </div>
                  ) : item.documentType === 'PDF' || item.fileName.toLowerCase().endsWith('.pdf') ? (
                    <iframe
                      src={`/api/intake/items/${item.id}/file`}
                      className="w-full h-full border-0"
                      title="New PDF Document"
                    />
                  ) : (
                    <div className="p-8 text-center text-slate-400 text-xs">
                      Không có bản thể hiện trực quan cho tệp mới nộp này.
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Footer Actions */}
        <footer className="px-6 py-3 border-t border-slate-200 bg-slate-50 flex flex-wrap items-center justify-between gap-3 shrink-0">
          <div className="flex items-center gap-2 text-xs text-slate-600">
            <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>
              Hệ thống đã tự động bảo vệ: giữ nguyên hóa đơn gốc #{originalInvoice?.id}, không tạo hóa đơn trùng và không tính 2 lần chi phí.
            </span>
          </div>

          <div className="flex items-center gap-2">
            <a
              href={`/api/intake/items/${item.id}/file`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 font-medium text-xs transition"
            >
              <Download className="w-3.5 h-3.5" />
              Tải tệp mới
            </a>

            {originalInvoice && (
              <button
                type="button"
                onClick={() => onOpenOriginalInvoice(originalInvoice.id)}
                className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs shadow-2xs transition"
              >
                <ExternalLink className="w-3.5 h-3.5" />
                Mở hóa đơn gốc #{originalInvoice.id} để duyệt
              </button>
            )}

            <button
              type="button"
              onClick={onClose}
              className="px-4 py-1.5 rounded-xl bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold text-xs transition"
            >
              Đóng
            </button>
          </div>
        </footer>

      </div>
    </div>
  );
};
