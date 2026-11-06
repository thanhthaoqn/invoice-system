import { useEffect, useRef, useState } from 'react';
import { Upload, RefreshCw, X } from 'lucide-react';
import { PdfReview } from './PdfReview';
import { api, intakeApi, errorMessage, type Invoice, type Session, type IntakeLimits, type UploadResult } from '../services/api';

interface Props {
  session: Session;
  onInvoiceUploaded: (invoice: Invoice) => void;
  onSelectInvoiceForReview: (invoice: Invoice) => void;
  invoices: Invoice[];
  isLoading: boolean;
  onRefresh: () => void;
}
const labels: Record<string, string> = {
  RECEIVED: 'Đã lưu, chờ xử lý', INGESTED: 'Đã tạo hóa đơn', ALREADY_RECEIVED: 'Đã nhận trước đó',
  DUPLICATE_IDENTITY: 'Trùng danh tính hóa đơn', PARSE_FAILED: 'Không đọc được XML', REJECTED_OUTBOUND: 'Từ chối hóa đơn đầu ra',
  PROCESSING_FAILED: 'Xử lý chưa hoàn tất', STORAGE_FAILED: 'Chưa lưu được bản gốc', UNSUPPORTED: 'Không hỗ trợ định dạng',
  ARCHIVE_RECEIVED: 'Đã lưu ZIP', ARCHIVE_PROCESSED: 'Đã đọc ZIP', ARCHIVE_EMPTY: 'ZIP không có XML',
  ARCHIVE_FAILED: 'ZIP có lỗi', INVALID_ARCHIVE: 'Không phải ZIP hợp lệ',
  OCR_REVIEW: 'PDF chờ kiểm tra & xác nhận', OCR_FAILED: 'Chưa trích xuất được PDF',
};
const size = (value: number) => `${(value / 1024 / 1024).toFixed(2)} MB`;
export function UploadSection({ session, onInvoiceUploaded, onSelectInvoiceForReview, invoices, isLoading, onRefresh }: Props) {
  const [files, setFiles] = useState<File[]>([]);
  const [limits, setLimits] = useState<IntakeLimits | null>(null);
  const [history, setHistory] = useState<UploadResult[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [pdfItem, setPdfItem] = useState<number | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const lock = useRef(false);
  const tenant = session.tenants.find(value => value.id === session.tenantId);
  useEffect(() => {
    let mounted = true;
    Promise.all([intakeApi.limits(), intakeApi.history()]).then(([config, sessions]) => {
      if (mounted) { setLimits(config); setHistory(sessions); }
    }).catch(err => { if (mounted) setError(errorMessage(err)); }).finally(() => { if (mounted) setLoadingHistory(false); });
    return () => { mounted = false; };
  }, []);
  const add = (selected: FileList | null) => {
    if (!selected || lock.current || !limits || !session.invoiceCreate) return;
    const next = [...files]; const issues: string[] = [];
    for (const file of Array.from(selected)) {
      if (!/\.(xml|zip|pdf)$/i.test(file.name)) { issues.push(`${file.name}: chỉ nhận XML, PDF hoặc ZIP.`); continue; }
      if (!file.size || file.size > limits.maxFileBytes) { issues.push(`${file.name}: file trống hoặc vượt ${size(limits.maxFileBytes)}.`); continue; }
      if (next.some(existing => existing.name === file.name && existing.size === file.size && existing.lastModified === file.lastModified)) continue;
      if (next.length >= limits.maxFiles || next.reduce((total, value) => total + value.size, 0) + file.size > limits.maxBatchBytes) { issues.push(`${file.name}: vượt giới hạn lô.`); continue; }
      next.push(file);
    }
    setFiles(next); setError(issues.join(' ')); if (input.current) input.current.value = '';
  };
  const refresh = async () => {
    setLoadingHistory(true); setError('');
    try { setHistory(await intakeApi.history()); onRefresh(); } catch (err) { setError(errorMessage(err)); } finally { setLoadingHistory(false); }
  };
  const run = async (operation: () => Promise<UploadResult>, clearFiles = false) => {
    if (lock.current || !session.invoiceCreate) return;
    lock.current = true; setBusy(true); setError(''); setProgress(0);
    try {
      const result = await operation();
      setHistory(previous => [result, ...previous.filter(value => value.session.id !== result.session.id)]);
      if (clearFiles) setFiles([]);
      onRefresh();

      // Tự động mở modal xác nhận dữ liệu PDF hoặc mở thẳng màn hình đối soát
      const pendingPdf = result.items.find(i => i.state === 'OCR_REVIEW' || (i.documentType === 'PDF' && !i.invoiceId));
      if (pendingPdf) {
        setPdfItem(pendingPdf.originalItemId || pendingPdf.id);
      } else {
        const firstInvoice = result.items.find(i => i.invoiceId);
        if (firstInvoice?.invoiceId) {
          void open(firstInvoice.invoiceId);
        }
      }
    } catch (err) { setError(errorMessage(err)); } finally { lock.current = false; setBusy(false); }
  };
  const open = async (id: number) => { try { onInvoiceUploaded(await api.getInvoice(id)); } catch (err) { setError(errorMessage(err)); } };
  return <div className="max-w-7xl mx-auto py-8 space-y-6">
    {pdfItem !== null && <PdfReview key={pdfItem} itemId={pdfItem} canEdit={session.invoiceCreate} onClose={() => setPdfItem(null)} onConfirmed={item => {
      setPdfItem(null); void refresh();
      if (item.invoiceId) void open(item.invoiceId);
      else setError(item.message);
    }} />}
    <header className="rounded-2xl border border-indigo-100 bg-gradient-to-r from-indigo-50 to-white p-8">
      <span className="text-xs font-bold uppercase tracking-wider text-indigo-600">Tiếp nhận hóa đơn · XML / PDF / ZIP</span>
      <h1 className="text-3xl font-bold mt-3">Nạp chứng từ, theo dõi từng kết quả</h1>
      <p className="mt-3 text-slate-600">Doanh nghiệp nhận: <strong>{tenant?.name}</strong> · MST {tenant?.taxCode}</p>
      <p className="mt-2 text-sm text-slate-500">Tiếp nhận, tạo hóa đơn, kiểm tra và phê duyệt là các bước riêng biệt. Dữ liệu nhập luôn chờ đối soát.</p>
    </header>
    {!session.invoiceCreate && <p className="rounded-xl bg-amber-50 p-4 text-amber-800">Phiên chỉ có quyền xem; không được tải lên hoặc xử lý lại.</p>}
    {error && <p role="alert" className="p-4 border border-rose-200 bg-rose-50 text-rose-700 rounded-xl">{error}</p>}
    <section className="bg-white border border-slate-200 rounded-2xl p-6 space-y-4">
      <button disabled={busy || !limits || !session.invoiceCreate} onClick={() => input.current?.click()}
        onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); add(event.dataTransfer.files); }}
        className="w-full rounded-xl border-2 border-dashed border-slate-300 p-10 text-center hover:border-indigo-400 disabled:opacity-50">
        <Upload className="mx-auto w-10 h-10 text-indigo-600 mb-3" /><span className="block font-semibold">Chọn nhiều XML, PDF hoặc ZIP — hoặc kéo thả vào đây</span>
        <span className="block text-sm text-slate-500 mt-2">{limits ? `Tối đa ${limits.maxFiles} file · ${size(limits.maxFileBytes)}/file · ${size(limits.maxBatchBytes)}/lô` : 'Đang đọc giới hạn từ máy chủ…'}</span>
        <span className="block text-xs text-slate-500 mt-2">PDF tối đa {limits?.maxPdfPages ?? 20} trang, được trích xuất cùng bộ trường XML; cần kiểm tra và xác nhận trước khi tạo hóa đơn. PDF scan sử dụng dịch vụ OCR Gemini đã cấu hình.</span>
      </button>
      <input ref={input} type="file" multiple accept=".xml,.pdf,.zip,application/pdf,application/xml,text/xml,application/zip" className="hidden" onChange={event => add(event.target.files)} />
      <ul className="divide-y divide-slate-100">{files.map((file, index) => <li key={`${file.name}-${index}`} className="flex items-center gap-3 py-3">
        <span className="flex-1 break-all text-sm">{file.name}</span><span className="text-xs text-slate-500">{size(file.size)}</span>
        <button aria-label={`Xóa ${file.name}`} disabled={busy} onClick={() => setFiles(previous => previous.filter((_, position) => position !== index))}><X className="w-4 h-4" /></button>
      </li>)}</ul>
      <div className="flex flex-wrap justify-between items-center gap-3"><span className="text-sm text-slate-500">{files.length} file ? {size(files.reduce((total, file) => total + file.size, 0))}</span>
        <button disabled={busy || !limits || !files.length || !session.invoiceCreate} onClick={() => void run(() => intakeApi.upload(files, setProgress), true)} className="rounded-xl bg-indigo-600 px-6 py-3 text-white font-semibold disabled:opacity-50">{busy ? 'Đang xử lý…' : 'Gửi tiếp nhận'}</button></div>
      {busy && <div role="status"><progress aria-label="Tiến độ truyền tệp" value={progress} max={100} className="w-full" /><p className="text-sm text-slate-500">Đã truyền {progress}%. Đợi máy chủ trả kết quả; chưa xác nhận tiếp nhận hoàn tất.</p></div>}
    </section>
    <section className="bg-white border border-slate-200 rounded-2xl overflow-hidden">
      <div className="flex justify-between items-center p-5 border-b"><h2 className="font-bold text-lg">Lịch sử tiếp nhận</h2><button disabled={busy || loadingHistory} onClick={() => void refresh()} className="flex items-center gap-2 text-sm text-indigo-700"><RefreshCw className={`w-4 h-4 ${loadingHistory ? 'animate-spin' : ''}`} />Làm mới</button></div>
      {loadingHistory && <p className="p-6 text-slate-500">Đang tải lịch sử…</p>}
      {!loadingHistory && history.length === 0 && <p className="p-10 text-center text-slate-500">Chưa có phiên tải lên cho doanh nghiệp này.</p>}
      <div className="divide-y divide-slate-200">{history.map(result => <details key={result.session.id} open={history[0]?.session.id === result.session.id} className="p-5">
        <summary className="cursor-pointer space-y-2"><span className="font-semibold">Phiên #{result.session.id} · {new Date(result.session.createdAt).toLocaleString('vi-VN')} · {result.session.submittedBy}</span>
          {(result.counts.pdfDocuments || 0) > 0 && <p className="text-sm text-indigo-700">{result.counts.pdfDocuments} PDF · {result.counts.OCR_REVIEW || 0} chờ xác nhận · {result.counts.OCR_FAILED || 0} lỗi trích xuất</p>}
          <p className="text-sm text-slate-500">{result.counts.uploadedFiles} file tải lên · {result.counts.xmlDocuments} XML · {result.counts.INGESTED} tạo mới · {result.counts.ALREADY_RECEIVED} nhận lại · {result.counts.DUPLICATE_IDENTITY} trùng · {(result.counts.PARSE_FAILED || 0) + (result.counts.PROCESSING_FAILED || 0) + (result.counts.STORAGE_FAILED || 0) + (result.counts.ARCHIVE_FAILED || 0) + (result.counts.INVALID_ARCHIVE || 0) + (result.counts.UNSUPPORTED || 0)} lỗi · {result.counts.REJECTED_OUTBOUND || 0} đầu ra</p>
          <p className="text-xs text-slate-500">{result.session.state === 'COMPLETED' ? 'Đã kết thúc xử lý lô. Xem kết quả từng tài liệu.' : 'Phiên chưa hoàn tất; cần kiểm tra kết quả.'}</p></summary>
        <ul className="mt-4 divide-y divide-slate-100">{result.items.map(item => <li key={item.id} className="py-4 space-y-2">
          <div className="flex flex-wrap justify-between gap-2"><span className="font-medium text-sm break-all">{item.fileName}</span><span className="text-xs font-semibold text-indigo-700">{labels[item.state] || item.state}</span></div>
          <p className="text-sm text-slate-500">{item.message}</p>
          <div className="flex flex-wrap items-center gap-3 text-xs">
            {item.invoiceId && (
              <button
                onClick={() => void open(item.invoiceId!)}
                className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs shadow-2xs transition"
              >
                Mở đối soát #{item.invoiceId}
              </button>
            )}
            {(item.state === 'OCR_REVIEW' || (item.documentType === 'PDF' && item.originalItemId && !item.invoiceId)) && (
              <button
                onClick={() => setPdfItem(item.originalItemId || item.id)}
                className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs shadow-2xs transition"
              >
                Kiểm tra & Tạo hóa đơn đối soát
              </button>
            )}
            {item.fileSize > 0 && item.state !== 'STORAGE_FAILED' && (
              <a
                href={`/api/intake/items/${item.id}/file`}
                target="_blank"
                rel="noreferrer"
                className="px-3 py-1.5 rounded-lg bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 font-medium text-xs transition"
              >
                Tải bản gốc
              </a>
            )}
            {session.invoiceCreate && limits && item.retryable && item.attempts < limits.maxAttempts && (
              <button
                disabled={busy}
                onClick={() => void run(() => intakeApi.retry(result.session.id, item.id))}
                className="px-3 py-1.5 rounded-lg bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 font-medium text-xs transition disabled:opacity-50"
              >
                Xử lý lại ({item.attempts}/{limits.maxAttempts})
              </button>
            )}
          </div></li>)}</ul>
      </details>)}</div>
    </section>
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
