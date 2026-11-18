import { useState, useMemo } from 'react';
import {
  Search,
  RefreshCw,
  CheckCircle2,
  Clock,
  XCircle,
  X,
  FileText
} from 'lucide-react';
import { api, errorMessage, type Invoice, type Session } from '../services/api';

type StatusFilter = 'all' | 'pending' | 'approved' | 'rejected';

export function InvoiceList({
  session,
  invoices,
  isLoading,
  onRefresh,
  onSelectInvoiceForReview
}: {
  session: Session;
  invoices: Invoice[];
  isLoading: boolean;
  onRefresh: () => void;
  onSelectInvoiceForReview: (invoice: Invoice) => void;
}) {
  const [error, setError] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const busy = isLoading;

  const counts = useMemo(() => {
    return {
      all: invoices.length,
      pending: invoices.filter(i => i.status === 'PENDING_REVIEW' || i.status === 'PROCESSING').length,
      approved: invoices.filter(i => i.status === 'APPROVED').length,
      rejected: invoices.filter(i => i.status === 'REJECTED').length,
    };
  }, [invoices]);

  const filteredInvoices = useMemo(() => {
    return invoices.filter(invoice => {
      // 1. Lọc theo trạng thái phê duyệt
      if (statusFilter === 'pending' && invoice.status !== 'PENDING_REVIEW' && invoice.status !== 'PROCESSING') {
        return false;
      }
      if (statusFilter === 'approved' && invoice.status !== 'APPROVED') {
        return false;
      }
      if (statusFilter === 'rejected' && invoice.status !== 'REJECTED') {
        return false;
      }

      // 2. Lọc theo từ khóa tìm kiếm (Số HĐ, Tên đơn vị bán, MST)
      if (searchTerm.trim()) {
        const term = searchTerm.toLowerCase().trim();
        const matchNumber = (invoice.invoiceNumber || '').toLowerCase().includes(term);
        const matchVendor = (invoice.vendorName || '').toLowerCase().includes(term);
        const matchTaxCode = (invoice.taxCode || '').toLowerCase().includes(term);
        const matchSeries = (invoice.invoiceSeries || '').toLowerCase().includes(term);
        if (!matchNumber && !matchVendor && !matchTaxCode && !matchSeries) {
          return false;
        }
      }

      return true;
    });
  }, [invoices, statusFilter, searchTerm]);

  const formatVND = (num: number | null | undefined): string => {
    if (num == null) return 'Chưa xác định';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(num);
  };

  const renderStatusBadge = (status: string) => {
    switch (status) {
      case 'APPROVED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
            Đã duyệt
          </span>
        );
      case 'REJECTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
            <XCircle className="w-3.5 h-3.5 text-rose-600" />
            Từ chối
          </span>
        );
      case 'PENDING_REVIEW':
      case 'PROCESSING':
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
            <Clock className="w-3.5 h-3.5 text-amber-600" />
            Chưa duyệt
          </span>
        );
    }
  };

  const isFiltered = statusFilter !== 'all' || searchTerm.trim().length > 0;

  return (
    <div className="max-w-7xl mx-auto py-8 space-y-6">
      {/* Header */}
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Danh sách hóa đơn</h1>
          <p className="text-sm text-slate-500 mt-1">Quản lý, tìm kiếm và đối soát hóa đơn điện tử</p>
        </div>
        <button
          onClick={onRefresh}
          disabled={isLoading}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-indigo-700 hover:bg-slate-50 disabled:opacity-50 transition shadow-2xs"
        >
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          Làm mới
        </button>
      </header>

      {error && <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-700 border border-rose-200">{error}</p>}

      {/* Main Container */}
      <section className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-2xs">
        {/* Filter Bar */}
        <div className="p-5 border-b border-slate-200 bg-slate-50/50 space-y-4">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            {/* Status Tabs */}
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => setStatusFilter('all')}
                className={`inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-sm font-semibold transition ${
                  statusFilter === 'all'
                    ? 'bg-indigo-600 text-white shadow-2xs'
                    : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                }`}
              >
                <FileText className="w-4 h-4" />
                <span>Tất cả</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-xs font-bold ${
                    statusFilter === 'all' ? 'bg-indigo-700/60 text-white' : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {counts.all}
                </span>
              </button>

              <button
                type="button"
                onClick={() => setStatusFilter('pending')}
                className={`inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-sm font-semibold transition ${
                  statusFilter === 'pending'
                    ? 'bg-amber-600 text-white shadow-2xs'
                    : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                }`}
              >
                <Clock className="w-4 h-4 text-amber-500 group-hover:text-amber-600" />
                <span>Chưa duyệt</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-xs font-bold ${
                    statusFilter === 'pending' ? 'bg-amber-700/60 text-white' : 'bg-amber-100 text-amber-800'
                  }`}
                >
                  {counts.pending}
                </span>
              </button>

              <button
                type="button"
                onClick={() => setStatusFilter('approved')}
                className={`inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-sm font-semibold transition ${
                  statusFilter === 'approved'
                    ? 'bg-emerald-600 text-white shadow-2xs'
                    : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                }`}
              >
                <CheckCircle2 className="w-4 h-4 text-emerald-500" />
                <span>Đã duyệt</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-xs font-bold ${
                    statusFilter === 'approved' ? 'bg-emerald-700/60 text-white' : 'bg-emerald-100 text-emerald-800'
                  }`}
                >
                  {counts.approved}
                </span>
              </button>

              <button
                type="button"
                onClick={() => setStatusFilter('rejected')}
                className={`inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-sm font-semibold transition ${
                  statusFilter === 'rejected'
                    ? 'bg-rose-600 text-white shadow-2xs'
                    : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
                }`}
              >
                <XCircle className="w-4 h-4 text-rose-500" />
                <span>Từ chối</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-xs font-bold ${
                    statusFilter === 'rejected' ? 'bg-rose-700/60 text-white' : 'bg-rose-100 text-rose-800'
                  }`}
                >
                  {counts.rejected}
                </span>
              </button>
            </div>

            {/* Search Box */}
            <div className="relative w-full md:w-80">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
                placeholder="Tìm số HĐ, đơn vị bán, MST..."
                className="w-full rounded-xl border border-slate-200 bg-white py-2 pl-9 pr-9 text-sm text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
              {searchTerm && (
                <button
                  type="button"
                  onClick={() => setSearchTerm('')}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-0.5 rounded-full"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>

          {/* Subheader info & Reset Filter */}
          <div className="flex items-center justify-between text-xs text-slate-500 pt-1">
            <div>
              Hiển thị <strong>{filteredInvoices.length}</strong> {isFiltered ? `trên tổng số ${invoices.length}` : ''} hóa đơn
            </div>
            {isFiltered && (
              <button
                type="button"
                onClick={() => {
                  setStatusFilter('all');
                  setSearchTerm('');
                }}
                className="text-indigo-600 hover:text-indigo-800 font-medium underline"
              >
                Đặt lại bộ lọc
              </button>
            )}
          </div>
        </div>

        {/* Invoice Table or Empty State */}
        {isLoading ? (
          <div className="p-12 text-center text-slate-500">
            <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-3 text-indigo-600" />
            Đang tải dữ liệu hóa đơn…
          </div>
        ) : filteredInvoices.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <FileText className="w-10 h-10 mx-auto text-slate-300" />
            <p className="font-semibold text-slate-700">
              {invoices.length === 0
                ? 'Chưa có hóa đơn nào trong hệ thống.'
                : 'Không tìm thấy hóa đơn nào khớp với bộ lọc.'}
            </p>
            {isFiltered && (
              <button
                type="button"
                onClick={() => {
                  setStatusFilter('all');
                  setSearchTerm('');
                }}
                className="px-4 py-2 rounded-xl bg-indigo-50 text-indigo-700 font-semibold text-xs hover:bg-indigo-100 transition"
              >
                Xóa bộ lọc để xem tất cả
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 border-b border-slate-200 text-xs font-semibold uppercase text-slate-500 tracking-wider">
                <tr>
                  <th className="p-4">Số / Ngày</th>
                  <th className="p-4">Đơn vị bán</th>
                  <th className="p-4">Tổng tiền</th>
                  <th className="p-4">Kiểm tra & Trạng thái</th>
                  <th className="p-4 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredInvoices.map(invoice => (
                  <tr key={invoice.id} className="hover:bg-slate-50/80 transition-colors">
                    <td className="p-4 align-top">
                      <span className="font-semibold text-slate-900">{invoice.invoiceNumber || 'Chưa xác định'}</span>
                      {invoice.invoiceSeries && (
                        <span className="ml-2 text-xs px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 font-mono">
                          {invoice.invoiceSeries}
                        </span>
                      )}
                      <br />
                      <span className="text-xs text-slate-500">{invoice.invoiceDate}</span>
                    </td>
                    <td className="p-4 align-top max-w-sm">
                      <div className="font-medium text-slate-800 line-clamp-2">{invoice.vendorName}</div>
                      <span className="text-xs text-slate-500 font-mono">MST: {invoice.taxCode}</span>
                    </td>
                    <td className="p-4 align-top font-semibold text-slate-900 whitespace-nowrap">
                      {formatVND(invoice.totalAmount)}
                    </td>
                    <td className="p-4 align-top space-y-1.5">
                      <div>{renderStatusBadge(invoice.status)}</div>
                      {invoice.taxRiskState === 'HIGH_TAX_RISK' && (
                        <p className="font-semibold text-rose-700 text-xs">⚠️ Rủi ro thuế cao</p>
                      )}
                      <div className="text-xs text-slate-600">
                        {invoice.validationState === 'NOT_CHECKED'
                          ? 'Chưa kiểm tra'
                          : invoice.validationState === 'BUYER_MISMATCH'
                          ? 'Sai/thiếu MST người mua'
                          : invoice.arithmeticValid === true
                          ? 'Số học khớp'
                          : invoice.arithmeticValid === false
                          ? 'Lệch số học'
                          : 'Chưa kiểm tra'}
                      </div>
                      {invoice.signatureState && (
                        <p className="text-xs text-amber-700">
                          {invoice.signatureState === 'MISSING'
                            ? 'Thiếu chữ ký số'
                            : invoice.signatureState === 'INVALID_STRUCTURE'
                            ? 'Cấu trúc chữ ký không hợp lệ'
                            : 'Có khối chữ ký; chưa xác minh chứng thư'}
                        </p>
                      )}
                    </td>
                    <td className="p-4 align-top text-right whitespace-nowrap space-y-2">
                      {session.invoiceCreate && invoice.validationState === 'NOT_CHECKED' && (
                        <button
                          disabled={busy}
                          className="block ml-auto text-xs text-amber-700 hover:text-amber-800 font-medium"
                          onClick={async () => {
                            try {
                              await api.recheckInvoice(invoice.id);
                              onRefresh();
                            } catch (err) {
                              setError(errorMessage(err));
                            }
                          }}
                        >
                          Chạy lại kiểm tra
                        </button>
                      )}
                      <button
                        onClick={() => onSelectInvoiceForReview(invoice)}
                        className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-semibold text-xs transition"
                      >
                        Xem / đối soát
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
