import React, { useState, useRef } from 'react';
import { Upload, FileText, CheckCircle2, AlertTriangle, ArrowRight, Sparkles, RefreshCw, Layers, CreditCard } from 'lucide-react';
import { api, type Invoice } from '../services/api';

interface UploadSectionProps {
  onInvoiceUploaded: (invoice: Invoice) => void;
  onSelectInvoiceForReview: (invoice: Invoice) => void;
  invoices: Invoice[];
  isLoading: boolean;
  onRefresh: () => void;
}

export const UploadSection: React.FC<UploadSectionProps> = ({
  onInvoiceUploaded,
  onSelectInvoiceForReview,
  invoices,
  isLoading,
  onRefresh
}) => {
  const [isDragging, setIsDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [submittedBy, setSubmittedBy] = useState('Nhân viên Nguyễn Văn A');
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = async (file: File) => {
    try {
      setUploading(true);
      setUploadError(null);
      const newInvoice = await api.uploadInvoice(file, submittedBy);
      onInvoiceUploaded(newInvoice);
    } catch (err: any) {
      console.error(err);
      setUploadError(err.response?.data?.message || 'Có lỗi xảy ra khi tải lên và trích xuất hóa đơn.');
    } finally {
      setUploading(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleFileChange(e.dataTransfer.files[0]);
    }
  };

  // Helper to create synthetic demo files for 1-click test
  const triggerDemoUpload = (sampleType: string) => {
    let filename = 'hoa_don_vietnam_airlines.jpg';
    let content = 'Demo invoice Vietnam Airlines';
    if (sampleType === 'meals') {
      filename = 'hoa_don_coffee_house.png';
      content = 'Demo invoice The Coffee House';
    } else if (sampleType === 'equipment') {
      filename = 'hoa_don_fpt_thiet_bi.jpg';
      content = 'Demo invoice FPT Shop Equipment';
    } else if (sampleType === 'duplicate') {
      filename = 'hoa_don_air_duplicate.pdf';
      content = 'Demo duplicate ticket';
    }

    const blob = new Blob([content], { type: sampleType === 'duplicate' ? 'application/pdf' : 'image/jpeg' });
    const file = new File([blob], filename, { type: blob.type });
    handleFileChange(file);
  };

  const formatVND = (num: number | null) => {
    if (num == null) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(num);
  };

  return (
    <div className="space-y-8 max-w-7xl mx-auto py-6">
      {/* Hero Banner */}
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 border border-slate-800 p-8 shadow-xl">
        <div className="absolute top-0 right-0 -mr-16 -mt-16 w-64 h-64 rounded-full bg-indigo-500/10 blur-3xl pointer-events-none" />
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 text-xs font-semibold uppercase tracking-wider mb-4">
            <Sparkles className="w-3.5 h-3.5" />
            <span>AI Core Vision Processing</span>
          </div>
          <h1 className="text-3xl font-extrabold text-white tracking-tight sm:text-4xl">
            Tiếp Nhận & Xử Lý Hóa Đơn Tự Động
          </h1>
          <p className="mt-3 text-slate-300 text-sm sm:text-base leading-relaxed">
            Hệ thống tự động đọc 7 trường thông tin chính (Số HĐ, Ngày lập, Đối tác, MST, Chi tiết dòng, VAT, Tổng tiền),
            phân loại chi phí và kiểm toán số học & trùng lặp trong thời gian thực.
          </p>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            <div className="flex items-center bg-slate-950/70 border border-slate-800 rounded-lg px-3 py-1.5 text-xs text-slate-300">
              <span className="text-slate-400 mr-2">Người nộp:</span>
              <input
                type="text"
                value={submittedBy}
                onChange={(e) => setSubmittedBy(e.target.value)}
                className="bg-transparent font-medium text-indigo-300 focus:outline-none"
              />
            </div>
          </div>
        </div>
      </div>

      {/* Upload Zone & Quick Sample Triggers */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Drag-Drop Box */}
        <div className="lg:col-span-2">
          <div
            onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={handleDrop}
            onClick={() => fileInputRef.current?.click()}
            className={`cursor-pointer rounded-2xl border-2 border-dashed p-10 flex flex-col items-center justify-center text-center transition-all ${
              isDragging
                ? 'border-indigo-500 bg-indigo-500/10 scale-[1.01]'
                : 'border-slate-700 bg-slate-900/40 hover:border-indigo-500/50 hover:bg-slate-900/80'
            }`}
          >
            <input
              type="file"
              ref={fileInputRef}
              className="hidden"
              accept="image/*,application/pdf"
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) {
                  handleFileChange(e.target.files[0]);
                }
              }}
            />

            <div className="w-16 h-16 rounded-2xl bg-indigo-600/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 mb-4 group-hover:scale-110 transition-transform">
              {uploading ? (
                <RefreshCw className="w-8 h-8 animate-spin" />
              ) : (
                <Upload className="w-8 h-8" />
              )}
            </div>

            <h3 className="text-lg font-bold text-white mb-1">
              {uploading ? 'Đang phân tích chứng từ bằng AI...' : 'Kéo thả hoặc Bấm để tải lên hóa đơn'}
            </h3>
            <p className="text-xs text-slate-400 max-w-sm mb-4">
              Hỗ trợ định dạng file ảnh (JPEG, PNG, WEBP) hoặc tài liệu PDF. Dung lượng tối đa 15MB.
            </p>

            {uploadError && (
              <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-xs text-red-400 flex items-center space-x-2">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span>{uploadError}</span>
              </div>
            )}
          </div>
        </div>

        {/* Quick Demo Templates */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-2xl p-6 flex flex-col justify-between">
          <div>
            <div className="flex items-center space-x-2 text-indigo-400 text-sm font-semibold mb-3">
              <Layers className="w-4 h-4" />
              <span>Hóa Đơn Mẫu Trực Quan (1-Click Test)</span>
            </div>
            <p className="text-xs text-slate-400 mb-4">
              Dùng nhanh các bộ dữ liệu hóa đơn thực tế để kiểm tra quy trình AI và cơ chế Audit:
            </p>

            <div className="space-y-2.5">
              <button
                disabled={uploading}
                onClick={() => triggerDemoUpload('travel')}
                className="w-full text-left p-3 rounded-xl bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 hover:border-indigo-500/40 transition-all flex items-center justify-between"
              >
                <div>
                  <div className="text-xs font-semibold text-slate-200">Vé Máy Bay Vietnam Airlines</div>
                  <div className="text-[11px] text-slate-400">Chi phí Công tác • 3.850.000 ₫</div>
                </div>
                <span className="text-[10px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 font-mono">Hợp lệ</span>
              </button>

              <button
                disabled={uploading}
                onClick={() => triggerDemoUpload('meals')}
                className="w-full text-left p-3 rounded-xl bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 hover:border-indigo-500/40 transition-all flex items-center justify-between"
              >
                <div>
                  <div className="text-xs font-semibold text-slate-200">The Coffee House - Tiếp khách</div>
                  <div className="text-[11px] text-slate-400">Ăn uống tiếp đối tác • 637.200 ₫</div>
                </div>
                <span className="text-[10px] px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 font-mono">Hợp lệ</span>
              </button>

              <button
                disabled={uploading}
                onClick={() => triggerDemoUpload('equipment')}
                className="w-full text-left p-3 rounded-xl bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 hover:border-indigo-500/40 transition-all flex items-center justify-between"
              >
                <div>
                  <div className="text-xs font-semibold text-slate-200">FPT Retail - Mua ổ cứng SSD</div>
                  <div className="text-[11px] text-slate-400">Mua sắm thiết bị • 6.479.000 ₫</div>
                </div>
                <span className="text-[10px] px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-400 font-mono">Vật tư</span>
              </button>

              <button
                disabled={uploading}
                onClick={() => triggerDemoUpload('duplicate')}
                className="w-full text-left p-3 rounded-xl bg-slate-800/60 hover:bg-slate-800 border border-slate-700/60 hover:border-amber-500/40 transition-all flex items-center justify-between"
              >
                <div>
                  <div className="text-xs font-semibold text-slate-200">Test Trùng Lặp Chứng Từ</div>
                  <div className="text-[11px] text-slate-400">Kiểm tra bộ lọc Duplicate Audit</div>
                </div>
                <span className="text-[10px] px-2 py-0.5 rounded bg-amber-500/10 text-amber-400 font-mono">Test Audit</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Processing & Verification Queue Table */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="p-5 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <FileText className="w-5 h-5 text-indigo-400" />
            <h2 className="text-base font-bold text-white">Danh Sách Chứng Từ Trong Hệ Thống</h2>
            <span className="text-xs bg-slate-800 text-slate-400 px-2 py-0.5 rounded-full">
              {invoices.length} chứng từ
            </span>
          </div>

          <button
            onClick={onRefresh}
            className="flex items-center space-x-1.5 text-xs text-slate-400 hover:text-white px-3 py-1.5 rounded-lg bg-slate-800/60 hover:bg-slate-800 transition"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            <span>Làm mới</span>
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950/60 text-slate-400 border-b border-slate-800 font-semibold uppercase tracking-wider text-[11px]">
              <tr>
                <th className="py-3.5 px-4">Số HĐ / Ngày</th>
                <th className="py-3.5 px-4">Đơn Vị Bán Hàng</th>
                <th className="py-3.5 px-4">Danh Mục Chi Phí</th>
                <th className="py-3.5 px-4">Tổng Tiền</th>
                <th className="py-3.5 px-4">Kiểm Toán AI (Audit)</th>
                <th className="py-3.5 px-4">Trạng Thái</th>
                <th className="py-3.5 px-4 text-right">Thao Tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {invoices.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500">
                    Chưa có hóa đơn nào trong hệ thống. Hãy kéo thả file hoặc chọn mẫu bên trên để bắt đầu!
                  </td>
                </tr>
              ) : (
                invoices.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-800/30 transition">
                    <td className="py-3 px-4 font-mono">
                      <div className="font-bold text-slate-200">{inv.invoiceNumber || 'Chưa nhận diện'}</div>
                      <div className="text-[11px] text-slate-500">{inv.invoiceDate || 'N/A'}</div>
                    </td>

                    <td className="py-3 px-4">
                      <div className="font-medium text-slate-200 line-clamp-1">{inv.vendorName || 'N/A'}</div>
                      <div className="text-[11px] text-slate-500">MST: {inv.taxCode || 'N/A'}</div>
                    </td>

                    <td className="py-3 px-4">
                      <div className="flex flex-col space-y-1">
                        <span className="inline-block px-2.5 py-1 rounded-full text-[11px] font-medium bg-slate-800 text-indigo-300 border border-slate-700/60">
                          {inv.category}
                        </span>
                        <div className="flex items-center space-x-1">
                          {inv.extractionMethod === 'PYMUPDF_VECTOR_TEXT' ? (
                            <span className="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-300 font-mono border border-amber-500/30">
                              ⚡ PyMuPDF
                            </span>
                          ) : (
                            <span className="text-[9px] px-1.5 py-0.5 rounded bg-indigo-500/15 text-indigo-300 font-mono border border-indigo-500/30">
                              🤖 VLM Vision
                            </span>
                          )}
                          <span className="text-[9px] text-slate-400 font-mono">
                            Nợ {inv.debitAccount || '6427'} • Có {inv.creditAccount || '1111'}
                          </span>
                        </div>
                      </div>
                    </td>

                    <td className="py-3 px-4 font-mono font-bold text-slate-100">
                      {formatVND(inv.totalAmount)}
                    </td>

                    <td className="py-3 px-4">
                      <div className="flex flex-col space-y-1">
                        {inv.arithmeticValid ? (
                          <span className="inline-flex items-center text-[10px] text-emerald-400">
                            <CheckCircle2 className="w-3 h-3 mr-1" /> Số học khớp
                          </span>
                        ) : (
                          <span className="inline-flex items-center text-[10px] text-red-400" title={inv.arithmeticErrorMessage || ''}>
                            <AlertTriangle className="w-3 h-3 mr-1" /> Lệch số học
                          </span>
                        )}

                        {inv.duplicateSuspected && (
                          <span className="inline-flex items-center text-[10px] text-amber-400" title={inv.duplicateWarningMessage || ''}>
                            <AlertTriangle className="w-3 h-3 mr-1" /> Trùng Số HĐ/MST
                          </span>
                        )}

                        {inv.isTechnicalDuplicate && (
                          <span className="inline-flex items-center text-[10px] text-rose-400" title={inv.technicalDuplicateMessage || ''}>
                            <AlertTriangle className="w-3 h-3 mr-1" /> Trùng hash file
                          </span>
                        )}

                        {inv.cashlessRequired && (
                          <span className="inline-flex items-center text-[10px] text-orange-400" title={inv.cashlessWarningMessage || ''}>
                            <CreditCard className="w-3 h-3 mr-1" /> ≥ 20M (Chuyển khoản)
                          </span>
                        )}
                      </div>
                    </td>

                    <td className="py-3 px-4">
                      <span
                        className={`inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold ${
                          inv.status === 'APPROVED'
                            ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                            : inv.status === 'REJECTED'
                            ? 'bg-red-500/10 text-red-400 border border-red-500/30'
                            : 'bg-amber-500/10 text-amber-400 border border-amber-500/30 animate-pulse'
                        }`}
                      >
                        {inv.status === 'APPROVED' ? 'ĐÃ DUYỆT' : inv.status === 'REJECTED' ? 'TỪ CHỐI' : 'CHỜ ĐỐI SOÁT'}
                      </span>
                    </td>

                    <td className="py-3 px-4 text-right">
                      <button
                        onClick={() => onSelectInvoiceForReview(inv)}
                        className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-indigo-600/80 hover:bg-indigo-600 text-white font-medium shadow-sm transition"
                      >
                        <span>Đối Soát</span>
                        <ArrowRight className="w-3 h-3" />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
