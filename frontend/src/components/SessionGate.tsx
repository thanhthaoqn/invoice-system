import { useEffect, useState, type ReactNode, type FormEvent } from 'react';
import { authApi, errorMessage, type Session } from '../services/api';
import { Building2, LogOut, UserRound, FileSpreadsheet, ChevronLeft, ChevronRight } from 'lucide-react';

export function SessionGate({ children }: { children: (session: Session, collapsed: boolean) => ReactNode }) {
  const [collapsed, setCollapsed] = useState(() => {
    try { return localStorage.getItem('smartexpense.sidebar-collapsed') === 'true'; } catch { return false; }
  });
  const toggleSidebar = () => setCollapsed(previous => {
    const next = !previous;
    try { localStorage.setItem('smartexpense.sidebar-collapsed', String(next)); } catch { /* Layout still works without storage. */ }
    return next;
  });
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
    <header className="flex border-b border-slate-200 bg-white">
      <div className={`sidebar-rail relative flex shrink-0 items-center border-r border-slate-200 px-4 py-4 ${collapsed ? 'is-collapsed' : ''}`}>
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-sm shadow-indigo-200"><FileSpreadsheet className="h-6 w-6" strokeWidth={1.8} /></span>
        <span className="sidebar-label ml-3 min-w-0 overflow-hidden whitespace-nowrap">
          <span className="block text-xl font-bold tracking-tight text-slate-900">SmartExpense</span>
          <span className="mt-0.5 block text-[11px] text-slate-500">Quản lý hóa đơn & chi phí</span>
        </span>
        <button onClick={toggleSidebar} aria-label={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'} aria-expanded={!collapsed} aria-controls="workspace-navigation" title={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'} className="absolute -right-3.5 top-1/2 z-50 hidden h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-500 shadow-sm transition-colors hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-600 focus-visible:outline-2 focus-visible:outline-indigo-500 md:flex">
          {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
        </button>
      </div>
      <div className="min-w-0 flex-1 flex flex-wrap items-center justify-between gap-x-6 gap-y-3 px-4 sm:px-6 lg:px-8 py-2.5">
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
    </header>
    {session.tenantId ? children(session, collapsed) : <p className="text-center p-16">Chọn doanh nghiệp để tiếp tục. Phiên này chưa được phép truy cập hóa đơn.</p>}
  </>;
}
