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

  return (
    <section className="h-full min-h-0 flex flex-col bg-white overflow-hidden">
      <header className="flex items-center justify-between gap-3 px-4 py-2.5 border-b border-slate-200 bg-slate-50/80 shrink-0">
        <div className="flex items-center gap-2 min-w-0">
          <FileText className="w-4 h-4 shrink-0 text-sky-600" />
          <span className="text-xs font-semibold text-slate-700 truncate">{invoice.fileName}</span>
        </div>
        <a href={fileUrl} download className="text-slate-500 hover:text-sky-700 p-1 rounded transition" title="Tải tài liệu gốc">
          <Download className="w-4 h-4" />
        </a>
      </header>

      {isXml && (
        <div className="flex gap-2 px-4 py-1.5 border-b border-slate-200 bg-white shrink-0" role="group" aria-label="Chế độ xem XML">
          <button
            aria-pressed={view === 'preview'}
            onClick={() => setView('preview')}
            className={`px-2.5 py-1 rounded text-xs transition ${
              view === 'preview'
                ? 'bg-sky-100 text-sky-800 font-semibold'
                : 'text-slate-600 hover:bg-slate-100 font-medium'
            }`}
          >
            Bản đối chiếu
          </button>
          <button
            aria-pressed={view === 'source'}
            onClick={() => setView('source')}
            className={`px-2.5 py-1 rounded text-xs transition ${
              view === 'source'
                ? 'bg-sky-100 text-sky-800 font-semibold'
                : 'text-slate-600 hover:bg-slate-100 font-medium'
            }`}
          >
            Mã XML
          </button>
        </div>
      )}

      <div className="flex-1 min-h-0 overflow-auto bg-slate-100/50">
        {current?.error ? (
          <p role="alert" className="p-6 text-rose-700 text-xs">
            Không tải được tài liệu gốc. Kiểm tra kho lưu trữ và thử lại.
          </p>
        ) : isXml ? (
          !current ? (
            <p className="p-6 text-slate-500 text-xs">Đang tải XML gốc…</p>
          ) : view === 'preview' ? (
            <XmlInvoicePreview source={current.text} />
          ) : (
            <pre className="p-4 whitespace-pre-wrap break-all text-xs leading-relaxed font-mono text-slate-800 bg-white">
              {current.text}
            </pre>
          )
        ) : invoice.fileType === 'PDF' ? (
          <iframe
            title="Tài liệu gốc"
            src={fileUrl}
            className="w-full h-full min-h-[500px] border-0"
            onError={() => setDocument({ id: invoice.id, text: '', error: true })}
          />
        ) : (
          <img
            alt="Tài liệu gốc"
            src={fileUrl}
            className="object-contain w-full p-2"
            onError={() => setDocument({ id: invoice.id, text: '', error: true })}
          />
        )}
      </div>
    </section>
  );
}
