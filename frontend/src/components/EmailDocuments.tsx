import { useEffect, useRef, useState } from 'react';
import { api, intakeApi, errorMessage, type Invoice, type UploadResult } from '../services/api';
import { PdfReview } from './PdfReview';

export function EmailDocuments({ sessionId, canEdit, onChanged, onClose, onOpenInvoice }: { sessionId: number; canEdit: boolean; onChanged: () => void; onClose: () => void; onOpenInvoice: (invoice: Invoice) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [opening, setOpening] = useState(false);
  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => element?.close();
  }, []);
  const openInvoice = async (id: number) => {
    setOpening(true); setError('');
    try { onOpenInvoice(await api.getInvoice(id)); }
    catch (err) { setError(errorMessage(err)); }
    finally { setOpening(false); }
  };
  const [result, setResult] = useState<UploadResult | null>(null);
  const [error, setError] = useState('');
  const [pdf, setPdf] = useState<number | null>(null);
  useEffect(() => {
    let mounted = true;
    intakeApi.session(sessionId).then(data => { if (mounted) setResult(data); }).catch(err => { if (mounted) setError(errorMessage(err)); });
    return () => { mounted = false; };
  }, [sessionId]);
  return <dialog ref={dialog} onCancel={onClose} aria-labelledby="email-documents-title" className="fixed inset-0 m-auto max-h-[90dvh] w-[min(64rem,95vw)] overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 backdrop:bg-slate-900/40">
    <header className="mb-4 flex items-center justify-between"><h2 id="email-documents-title" className="font-semibold">Tài liệu email · Phiên #{sessionId}</h2><button onClick={onClose} className="text-sm text-slate-500">Đóng</button></header>
    {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
    {!result && !error && <p className="text-sm text-slate-500">Đang tải tài liệu…</p>}
    <ul className="divide-y divide-slate-100">{result?.items.map(item => <li key={item.id} className="space-y-2 py-4"><div className="flex flex-wrap justify-between gap-2"><span className="break-all font-medium">{item.fileName}</span><span className="text-xs text-indigo-700">{item.state === 'DUPLICATE_IDENTITY' || item.state === 'ALREADY_RECEIVED' ? 'Trùng lặp · Không tính tiền lần hai' : item.state === 'OCR_PENDING' ? 'Đang chờ OCR' : item.state === 'OCR_REVIEW' ? 'Chờ xác nhận PDF' : item.state}</span></div><p className="text-sm text-slate-500">{item.message}</p><div className="flex gap-4 text-sm">
      {item.fileSize > 0 && item.state !== 'STORAGE_FAILED' && <a className="text-indigo-700" href={`/api/intake/items/${item.id}/file`} target="_blank" rel="noreferrer">Xem bản gốc</a>}
      {item.invoiceId != null && <button disabled={opening} onClick={() => void openInvoice(item.invoiceId!)} className="font-semibold text-indigo-700 disabled:opacity-50">Xem hóa đơn / đối soát</button>}
      {item.state === 'OCR_REVIEW' && <button onClick={() => setPdf(item.originalItemId || item.id)} className="font-semibold text-indigo-700">Đối soát PDF</button>}
    </div></li>)}</ul>
    {pdf !== null && <PdfReview key={pdf} itemId={pdf} canEdit={canEdit} onClose={() => setPdf(null)} onConfirmed={() => { setPdf(null); void intakeApi.session(sessionId).then(setResult).catch(err => setError(errorMessage(err))); onChanged(); }} />}
  </dialog>;
}
