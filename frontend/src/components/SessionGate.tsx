import { useEffect, useState, type ReactNode, type FormEvent } from 'react';
import { authApi, errorMessage, type Session } from '../services/api';
import { Building2, LogOut, UserRound } from 'lucide-react';

export function SessionGate({ children }: { children: (session: Session) => ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  useEffect(() => {
    let mounted = true;
    authApi.me().then(value => { if (mounted) setSession(value); }).catch(() => {}).finally(() => { if (mounted) setReady(true); });
    const expired = () => { setSession(null); setError('Phiên đã hết hạn. Vui lòng đăng nhập lại.'); };
    window.addEventListener('session-expired', expired);
    return () => { mounted = false; window.removeEventListener('session-expired', expired); };
  }, []);
  const login = async (event: FormEvent) => {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError('');
    try { setSession(await authApi.login(username.trim(), password)); setPassword(''); }
    catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  };
  const select = async (id: number) => {
    if (busy) return; setBusy(true); setError('');
    // Unmount tenant data and selected files before switching the server session.
    const previous = session;
    setSession(null); setReady(false);
    try { setSession(await authApi.selectTenant(id)); }
    catch (err) { setSession(previous); setError(errorMessage(err)); }
    finally { setReady(true); setBusy(false); }
  };
  if (!ready) return <p className="p-12 text-center">Đang kiểm tra phiên làm việc…</p>;
  if (!session) return <main className="min-h-screen flex items-center justify-center bg-slate-50 p-6">
    <form onSubmit={login} className="w-full max-w-md space-y-5 rounded-2xl bg-white p-8 border border-slate-200 shadow-sm">
      <h1 className="text-2xl font-bold">Đăng nhập SmartExpense</h1>
      <p className="text-sm text-slate-500">Sử dụng tài khoản đã được cấp cho doanh nghiệp.</p>
      <label className="block">Tài khoản / email<input autoComplete="username" required maxLength={255} value={username} onChange={e => setUsername(e.target.value)} className="mt-2 block w-full border rounded-lg p-3" /></label>
      <label className="block">Mật khẩu<input type="password" autoComplete="current-password" required maxLength={256} value={password} onChange={e => setPassword(e.target.value)} className="mt-2 block w-full border rounded-lg p-3" /></label>
      {error && <p role="alert" className="text-rose-700">{error}</p>}
      <button disabled={busy || !username.trim() || !password} className="w-full bg-indigo-600 text-white rounded-lg p-3 disabled:opacity-50">{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button>
    </form>
  </main>;
  return <>
    <div className="border-b border-slate-200 bg-slate-50">
      <div className="max-w-[1700px] mx-auto flex flex-wrap items-center justify-between gap-x-6 gap-y-3 px-4 sm:px-6 lg:px-8 py-2.5">
        <label className="flex min-w-0 max-w-full items-center gap-2.5 text-xs font-medium text-slate-500">
          <Building2 className="w-4 h-4 shrink-0 text-slate-400" />
          <span className="hidden sm:inline shrink-0">Doanh nghiệp</span>
          <select aria-label="Doanh nghiệp hiện tại" disabled={busy} value={session.tenantId ?? ''} onChange={e => void select(Number(e.target.value))} className="min-w-0 max-w-full w-72 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 shadow-xs focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-100 disabled:opacity-50">
            <option value="" disabled>Chọn doanh nghiệp</option>
            {session.tenants.map(tenant => <option key={tenant.id} value={tenant.id}>{tenant.name} — {tenant.taxCode}</option>)}
          </select>
        </label>
        <div className="flex min-w-0 items-center gap-4 sm:gap-5">
          <span className="flex min-w-0 items-center gap-2 text-sm font-medium text-slate-600"><span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-200/70 text-slate-500"><UserRound className="w-4 h-4" /></span><span className="truncate max-w-48" title={session.username}>{session.username}</span></span>
          <span aria-hidden="true" className="h-5 w-px bg-slate-200" />
          <button disabled={busy} onClick={async () => { setBusy(true); try { await authApi.logout(); setSession(null); } catch (err) { setError(errorMessage(err)); } finally { setBusy(false); } }} className="flex shrink-0 items-center gap-2 rounded-lg px-2 py-1.5 text-xs font-semibold text-slate-500 transition-colors hover:bg-slate-200/60 hover:text-slate-900 disabled:opacity-50"><LogOut className="w-3.5 h-3.5" />Đăng xuất</button>
        </div>
        {error && <p role="alert" className="w-full text-sm text-rose-700">{error}</p>}
      </div>
    </div>
    {session.tenantId ? children(session) : <p className="text-center p-16">Chọn doanh nghiệp để tiếp tục. Phiên này chưa được phép truy cập hóa đơn.</p>}
  </>;
}
