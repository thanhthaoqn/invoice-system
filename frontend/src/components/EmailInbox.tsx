import { useEffect, useState } from 'react';
import { Mail, RefreshCw } from 'lucide-react';
import { EmailSettings } from './EmailSettings';
import { EmailDocuments } from './EmailDocuments';
import { emailApi, errorMessage, type EmailMessage, type Invoice } from '../services/api';

const states: Record<string, string> = { SUCCESS: 'Thành công', PARTIAL: 'Thành công một phần', FAILED: 'Thất bại', PAUSE: 'Chờ thử lại', NO_XML: 'Không có hóa đơn', NEEDS_REVIEW: 'Chờ đối soát PDF', RECEIVED: 'Đã tiếp nhận', REJECTED_SENDER: 'Từ chối người gửi' };

export function EmailInbox({ canCreate, onChanged, onOpenInvoice }: { canCreate: boolean; onChanged: () => void; onOpenInvoice: (invoice: Invoice) => void }) {
  const [messages, setMessages] = useState<EmailMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [selectedSession, setSelectedSession] = useState<number | null>(null);
  useEffect(() => {
    let mounted = true;
    emailApi.history().then(data => { if (mounted) setMessages(data); })
      .catch(err => { if (mounted) setError(errorMessage(err)); })
      .finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, []);
  const refresh = async () => {
    setLoading(true); setError('');
    try { setMessages(await emailApi.history()); } catch (err) { setError(errorMessage(err)); }
    finally { setLoading(false); }
  };
  const retry = async (id: number) => {
    if (busy !== null) return;
    setBusy(id); setError('');
    try {
      const updated = await emailApi.reprocess(id);
      setMessages(previous => previous.map(message => message.id === id ? updated : message));
      onChanged();
    } catch (err) { setError(errorMessage(err)); }
    finally { setBusy(null); }
  };
  return <div className="mx-auto max-w-7xl space-y-6 py-8">
    <header className="flex items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-white p-6">
      <div><h1 className="flex items-center gap-3 text-2xl font-bold"><Mail className="h-6 w-6 text-indigo-600" />Email</h1><p className="mt-2 text-sm text-slate-500">Theo dõi hóa đơn tiếp nhận từ email và xử lý lại thư có lỗi.</p></div>
      <button disabled={loading || busy !== null} onClick={() => void refresh()} className="flex items-center gap-2 rounded-xl border px-4 py-2 text-sm text-indigo-700 disabled:opacity-50"><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />Làm mới</button>
    </header>
    <EmailSettings canCreate={canCreate} onSynced={() => { void refresh(); onChanged(); }} />
    {error && <p role="alert" className="rounded-xl bg-rose-50 p-4 text-rose-700">{error}</p>}
    <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
      {loading ? <p role="status" className="p-8 text-slate-500">Đang tải email…</p> : messages.length === 0 ? <div className="p-12 text-center"><Mail className="mx-auto mb-4 h-10 w-10 text-slate-300" /><h2 className="font-semibold">Chưa có email được tiếp nhận</h2><p className="mt-2 text-sm text-slate-500">Mở Kết nối email để cấu hình hộp thư, sau đó bấm Đồng bộ ngay.</p></div> :
        <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="bg-slate-50 text-slate-500"><tr><th className="p-4">Email</th><th className="p-4">Thời gian nhận</th><th className="p-4">Trạng thái</th><th className="p-4">Ghi chú</th><th className="p-4">Thao tác</th></tr></thead>
          <tbody>{messages.map(message => <tr key={message.id} className="border-t border-slate-100 align-top">
            <td className="max-w-xs break-words p-4"><p className="font-semibold">{message.subject || '(Không có tiêu đề)'}</p><p className="mt-1 text-xs text-slate-500">{message.sender}</p></td>
            <td className="whitespace-nowrap p-4 text-slate-500">{new Date(message.createdAt).toLocaleString('vi-VN')}</td>
            <td className="p-4"><span className={`inline-block rounded-lg px-2.5 py-1 text-xs font-semibold ${message.state === 'SUCCESS' ? 'bg-emerald-50 text-emerald-700' : message.state === 'FAILED' ? 'bg-rose-50 text-rose-700' : 'bg-amber-50 text-amber-700'}`}>{states[message.state] || message.state}</span><p className="mt-2 text-xs text-slate-400">{message.attempts} lần xử lý · {message.channel || 'WEBHOOK'}</p>{message.deadLetter && <p className="mt-1 text-xs font-semibold text-rose-600">Cần kiểm tra thủ công</p>}{message.nextRetryAt && <p className="mt-1 text-xs text-slate-500">Thử lại: {new Date(message.nextRetryAt).toLocaleString('vi-VN')}</p>}</td>
            <td className="max-w-sm p-4 text-slate-500">{message.note}</td>
            <td className="space-y-2 p-4">{message.sessionId && <button onClick={() => setSelectedSession(message.sessionId)} className="block whitespace-nowrap text-xs font-semibold text-indigo-700">Xem tài liệu</button>}{canCreate && (['FAILED', 'PARTIAL', 'PAUSE', 'REJECTED_SENDER'].includes(message.state) || (message.state === 'RECEIVED' && Date.now() - new Date(message.updatedAt).getTime() > 900000)) && <button disabled={busy !== null} onClick={() => void retry(message.id)} className="whitespace-nowrap rounded-lg bg-indigo-600 px-3 py-2 text-xs font-semibold text-white disabled:opacity-50">{busy === message.id ? 'Đang xử lý…' : 'Xử lý lại'}</button>}</td>
          </tr>)}</tbody></table></div>}
    </section>
    {selectedSession !== null && <EmailDocuments key={selectedSession} sessionId={selectedSession} canEdit={canCreate} onChanged={onChanged} onClose={() => setSelectedSession(null)} onOpenInvoice={onOpenInvoice} />}
  </div>;
}
