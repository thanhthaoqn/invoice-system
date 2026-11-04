import { useEffect, useState } from 'react';
import axios from 'axios';
import { Download, FileText } from 'lucide-react';
import type { Invoice } from '../services/api';
import { XmlInvoicePreview } from './XmlInvoicePreview';
export function DocumentViewer({ invoice }: { invoice: Invoice }) {
  const [document, setDocument] = useState<{ id: number; text: string; error: boolean } | null>(null);
  const [view, setView] = useState<'preview' | 'source'>('preview');
  const isXml = invoice.fileType === 'XML';
  const fileUrl = `/api/invoices/${invoice.id}/file`;
  useEffect(() => {
    if (!isXml) return;
    const controller = new AbortController();
    axios.get<string>(fileUrl, { responseType: 'text', signal: controller.signal })
      .then(response => setDocument({ id: invoice.id, text: response.data, error: false }))
      .catch(err => { if (!axios.isCancel(err)) setDocument({ id: invoice.id, text: '', error: true }); });
    return () => controller.abort();
  }, [fileUrl, isXml, invoice.id]);
  const current = document?.id === invoice.id ? document : null;
  return <section className="h-full min-h-0 flex flex-col bg-white border rounded-2xl overflow-hidden">
    <header className="flex items-center justify-between gap-3 p-4 border-b bg-slate-50"><FileText className="w-5 h-5 shrink-0 text-indigo-600" /><span className="text-sm font-semibold break-all">{invoice.fileName}</span><a href={fileUrl} download className="text-indigo-700" title="Tải tài liệu gốc"><Download className="w-5 h-5" /></a></header>
    {isXml && <div className="flex gap-2 px-4 py-2 border-b bg-white" role="group" aria-label="Chế độ xem XML">
      <button aria-pressed={view === 'preview'} onClick={() => setView('preview')} className={`px-3 py-1.5 rounded-lg text-xs font-semibold ${view === 'preview' ? 'bg-indigo-100 text-indigo-800' : 'text-slate-600'}`}>Bản đối chiếu</button>
      <button aria-pressed={view === 'source'} onClick={() => setView('source')} className={`px-3 py-1.5 rounded-lg text-xs font-semibold ${view === 'source' ? 'bg-indigo-100 text-indigo-800' : 'text-slate-600'}`}>Mã XML</button>
    </div>}
    <div className="flex-1 min-h-0 overflow-auto bg-slate-50">
      {current?.error ? <p role="alert" className="p-6 text-rose-700">Không tải được tài liệu gốc. Kiểm tra kho lưu trữ và thử lại.</p>
        : isXml ? !current ? <p className="p-6 text-slate-500">Đang tải XML gốc…</p>
          : view === 'preview' ? <XmlInvoicePreview source={current.text} /> : <pre className="p-4 whitespace-pre-wrap break-all text-xs leading-relaxed">{current.text}</pre>
        : invoice.fileType === 'PDF' ? <iframe title="Tài liệu gốc" src={fileUrl} className="w-full h-full min-h-[500px]" onError={() => setDocument({ id: invoice.id, text: '', error: true })} />
          : <img alt="Tài liệu gốc" src={fileUrl} className="object-contain w-full" onError={() => setDocument({ id: invoice.id, text: '', error: true })} />}
    </div>
  </section>;
}
