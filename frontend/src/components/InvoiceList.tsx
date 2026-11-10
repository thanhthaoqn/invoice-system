import { useState } from 'react';
import { api, errorMessage, type Invoice, type Session } from '../services/api';

export function InvoiceList({ session, invoices, isLoading, onRefresh, onSelectInvoiceForReview }: {
  session: Session; invoices: Invoice[]; isLoading: boolean; onRefresh: () => void;
  onSelectInvoiceForReview: (invoice: Invoice) => void;
}) {
  const [error, setError] = useState('');
  const busy = isLoading;
  return <div className="max-w-7xl mx-auto py-8 space-y-6">
    <header className="flex items-center justify-between gap-4"><h1 className="text-2xl font-bold">Danh sách hóa đơn</h1><button onClick={onRefresh} disabled={isLoading} className="rounded-xl border bg-white px-4 py-2 text-sm text-indigo-700 disabled:opacity-50">Làm mới</button></header>
    {error && <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-700">{error}</p>}
    <section className="bg-white border rounded-2xl overflow-hidden"><h2 className="p-5 font-bold border-b">Hóa đơn của doanh nghiệp · {invoices.length}</h2>
      {isLoading ? <p className="p-6">Đang tải hóa đơn…</p> : invoices.length === 0 ? <p className="p-8 text-slate-500">Chưa có hóa đơn.</p> : <div className="overflow-x-auto"><table className="w-full text-left text-sm">
        <thead className="bg-slate-50"><tr><th className="p-4">Số / Ngày</th><th className="p-4">Đơn vị bán</th><th className="p-4">Tổng tiền</th><th className="p-4">Kiểm tra / Phê duyệt</th><th className="p-4">Thao tác</th></tr></thead>
        <tbody>{invoices.map(invoice => <tr key={invoice.id} className="border-t"><td className="p-4">{invoice.invoiceNumber || 'Chưa xác định'}<br /><span className="text-xs text-slate-500">{invoice.invoiceDate}</span></td><td className="p-4">{invoice.vendorName}<br /><span className="text-xs text-slate-500">{invoice.taxCode}</span></td>
          <td className="p-4">{invoice.totalAmount == null ? 'Chưa xác định' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(invoice.totalAmount)}</td>
          <td className="p-4"><div>{invoice.validationState === 'NOT_CHECKED' ? 'Chưa kiểm tra' : invoice.validationState === 'BUYER_MISMATCH' ? 'Sai/thiếu MST người mua' : invoice.arithmeticValid === true ? 'Số học khớp' : invoice.arithmeticValid === false ? 'Lệch số học' : 'Chưa kiểm tra'}</div><span className="text-xs text-slate-500">{invoice.status === 'APPROVED' ? 'Đã duyệt' : invoice.status === 'REJECTED' ? 'Từ chối' : 'Chờ đối soát'}</span></td>
          <td className="p-4">{session.invoiceCreate && invoice.validationState === 'NOT_CHECKED' && <button disabled={busy} className="block text-amber-700 mb-2" onClick={async () => { try { await api.recheckInvoice(invoice.id); onRefresh(); } catch (err) { setError(errorMessage(err)); } }}>Chạy lại kiểm tra</button>}<button onClick={() => onSelectInvoiceForReview(invoice)} className="text-indigo-700">Xem / đối soát</button></td></tr>)}</tbody>
      </table></div>}
    </section>
  </div>;
}
