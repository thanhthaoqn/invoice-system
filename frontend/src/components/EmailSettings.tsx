import { useEffect, useState } from 'react';
import { Settings2, RefreshCw } from 'lucide-react';
import { emailApi, errorMessage, type MailboxSettings, type SystemMailbox } from '../services/api';

export function EmailSettings({ canCreate, onSynced }: { canCreate: boolean; onSynced: () => void }) {
  const [settings, setSettings] = useState<MailboxSettings | null>(null);
  const [system, setSystem] = useState<SystemMailbox | null>(null);
  const [form, setForm] = useState({ email: '', host: 'imap.gmail.com', port: 993, password: '', active: true, allowedSenders: '' });
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let mounted = true;
    Promise.all([emailApi.mailbox(), emailApi.systemMailbox()]).then(([value, route]) => {
      if (!mounted) return;
      setSettings(value); setSystem(route);
      if (value.account) setForm({ email: value.account.email, host: value.account.host, port: value.account.port, password: '', active: value.account.active, allowedSenders: value.account.allowedSenders });
    }).catch(err => { if (mounted) setError(errorMessage(err)); }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, []);
  const run = async (action: () => Promise<void>) => {
    if (busy) return;
    setBusy(true); setError(''); setNotice('');
    try { await action(); } catch (err) { setError(errorMessage(err)); } finally { setBusy(false); }
  };
  const inputClass = 'mt-2 w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm disabled:bg-slate-50';
  return <details className="rounded-2xl border border-slate-200 bg-white p-6">
    <summary className="cursor-pointer font-semibold"><Settings2 className="mr-2 inline h-5 w-5 text-indigo-600" />Kết nối email</summary>
    {error && <p role="alert" className="mt-4 rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
    {notice && <p role="status" className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-700">{notice}</p>}
    {loading ? <p className="mt-4 text-sm text-slate-500">Đang tải cấu hình…</p> : <div className="mt-6 grid gap-8 xl:grid-cols-2">
      <form onSubmit={event => { event.preventDefault(); void run(async () => {
        const account = await emailApi.saveMailbox(form);
        setSettings(previous => previous ? { ...previous, account } : previous);
        setForm(previous => ({ ...previous, password: '' })); setNotice('Đã lưu kết nối. Bấm Đồng bộ ngay để lấy thư chưa đọc.');
      }); }}>
        <h2 className="font-semibold">Hộp thư của doanh nghiệp</h2>
        <p className="mt-2 text-sm text-slate-500">Đọc email chưa đọc qua IMAPS mỗi 15 phút. Dùng mật khẩu ứng dụng của nhà cung cấp email.</p>
        {!settings?.encryptionConfigured && <p className="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">Quản trị viên cần cấu hình khóa mã hóa email trên máy chủ trước khi lưu kết nối.</p>}
        <fieldset disabled={busy || !canCreate || !settings?.encryptionConfigured} className="mt-4 space-y-4 disabled:opacity-60">
          <label className="block text-sm font-medium">Địa chỉ email<input type="email" required maxLength={254} autoComplete="off" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} className={inputClass} /></label>
          <label className="block text-sm font-medium">Máy chủ IMAP<select value={form.host} onChange={event => setForm({ ...form, host: event.target.value })} className={inputClass}>{(settings?.allowedHosts ?? ['imap.gmail.com']).map(host => <option key={host}>{host}</option>)}</select></label>
          <label className="block text-sm font-medium">Mật khẩu ứng dụng<input type="password" autoComplete="new-password" maxLength={500} required={!settings?.account} value={form.password} onChange={event => setForm({ ...form, password: event.target.value })} className={inputClass} /><span className="mt-1 block text-xs text-slate-500">Để trống để giữ mật khẩu đã lưu khi không đổi email hoặc máy chủ.</span></label>
          <label className="block text-sm font-medium">Người gửi được phép<textarea rows={3} maxLength={4000} value={form.allowedSenders} onChange={event => setForm({ ...form, allowedSenders: event.target.value })} placeholder="hoadon@nhacungcap.vn, billing@doitac.vn" className={inputClass} /><span className="mt-1 block text-xs text-slate-500">Phân cách bằng dấu phẩy hoặc xuống dòng. Danh sách trống sẽ từ chối mọi người gửi.</span></label>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.active} onChange={event => setForm({ ...form, active: event.target.checked })} />Bật tự động nhận email</label>
          <button type="submit" className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white">{busy ? 'Đang thực hiện…' : 'Lưu kết nối'}</button>
        </fieldset>
        <button type="button" disabled={busy || !canCreate || !settings?.account?.active} onClick={() => void run(async () => {
          const result = await emailApi.sync(); setNotice(`Đã nhận ${result.received} thư, ${result.rejected} thư ngoài whitelist.`);
          setSettings(await emailApi.mailbox()); onSynced();
        })} className="mt-4 flex items-center gap-2 rounded-lg border px-4 py-2.5 text-sm font-semibold text-indigo-700 disabled:opacity-50"><RefreshCw className={`h-4 w-4 ${busy ? 'animate-spin' : ''}`} />Đồng bộ ngay</button>
        {settings?.account?.lastPolledAt && <p className="mt-3 text-xs text-slate-500">Lần đồng bộ gần nhất: {new Date(settings.account.lastPolledAt).toLocaleString('vi-VN')}</p>}
        {settings?.account?.lastError && <p className="mt-2 text-sm text-rose-700">{settings.account.lastError}</p>}
      </form>
      <section className="space-y-3"><h2 className="font-semibold">Hộp thư hệ thống</h2><p className="text-sm text-slate-500">Chuyển tiếp email hóa đơn tới địa chỉ riêng của doanh nghiệp. Hệ thống kiểm tra thư mới mỗi 2 phút.</p>
        {system?.mailbox ? <p className="break-all rounded-xl bg-indigo-50 p-4 font-semibold text-indigo-700">{system.mailbox.address}</p> : system?.configured ? <button disabled={!canCreate || busy} onClick={() => void run(async () => { const route = await emailApi.provision(); setSystem({ configured: true, mailbox: route }); setNotice('Đã cấp địa chỉ nhận hóa đơn.'); })} className="rounded-lg border px-4 py-2 text-sm font-semibold text-indigo-700 disabled:opacity-50">Cấp địa chỉ nhận hóa đơn</button> : <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">Hộp thư hệ thống chưa được cấu hình. Bạn có thể kết nối hộp thư riêng ở bên cạnh.</p>}
      </section>
    </div>}
  </details>;
}
