import React, { useEffect, useState } from 'react';
import { Mail, RefreshCw, Unplug } from 'lucide-react';
import { errorMessage, gmailApi, type GmailStatus } from '../services/api';

export const GmailIntegrationPanel: React.FC = () => {
  const [status, setStatus] = useState<GmailStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const load = async () => { try { setStatus(await gmailApi.status()); } catch (error) { setNotice(errorMessage(error)); } };
  useEffect(() => { load(); }, []);

  const connect = async () => {
    try { setBusy(true); window.location.assign(await gmailApi.authorizationUrl()); }
    catch (error) { setNotice(errorMessage(error)); setBusy(false); }
  };
  const sync = async () => {
    try { setBusy(true); const result=await gmailApi.sync(); setNotice(`Đã kiểm tra ${result.discovered} email, xử lý ${result.processed}, lỗi ${result.failed}.`); await load(); }
    catch (error) { setNotice(errorMessage(error)); } finally { setBusy(false); }
  };
  const disconnect = async () => {
    if (!window.confirm('Ngắt kết nối Gmail của doanh nghiệp?')) return;
    try { setBusy(true); await gmailApi.disconnect(); setNotice('Đã ngắt kết nối Gmail.'); await load(); }
    catch (error) { setNotice(errorMessage(error)); } finally { setBusy(false); }
  };

  return <section className="max-w-3xl mx-auto py-10 space-y-6">
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-start gap-4">
        <span className="rounded-xl bg-red-50 p-3 text-red-600"><Mail className="h-6 w-6" /></span>
        <div className="flex-1">
          <h1 className="text-xl font-bold text-slate-900">Gmail hóa đơn</h1>
          <p className="mt-1 text-sm text-slate-600">Chỉ đọc email có nhãn <strong>HOA_DON</strong>. Hệ thống không đánh dấu đã đọc, sửa nhãn hoặc xóa email.</p>
          {status && <div className="mt-5 rounded-xl bg-slate-50 p-4 text-sm">
            <p><strong>Trạng thái:</strong> {status.connected ? 'Đã kết nối' : 'Chưa kết nối'}</p>
            {status.emailAddress && <p className="mt-1"><strong>Tài khoản:</strong> {status.emailAddress}</p>}
            {status.lastSyncAt && <p className="mt-1"><strong>Đồng bộ gần nhất:</strong> {new Date(status.lastSyncAt).toLocaleString('vi-VN')}</p>}
            {status.lastError && <p className="mt-2 text-red-700">{status.lastError}</p>}
          </div>}
          {notice && <p className="mt-4 text-sm text-indigo-700">{notice}</p>}
          {status?.admin ? <div className="mt-5 flex flex-wrap gap-3">
            {!status.connected && <button disabled={busy || !status.configured} onClick={connect} className="rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-bold text-white disabled:opacity-50">Kết nối Gmail</button>}
            {status.connected && <button disabled={busy} onClick={sync} className="flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-bold text-white disabled:opacity-50"><RefreshCw className="h-4 w-4" /> Đồng bộ ngay</button>}
            {status.connected && <button disabled={busy} onClick={disconnect} className="flex items-center gap-2 rounded-xl border border-red-200 px-4 py-2.5 text-sm font-bold text-red-700 disabled:opacity-50"><Unplug className="h-4 w-4" /> Ngắt kết nối</button>}
          </div> : status && <p className="mt-5 text-sm text-amber-700">Bạn không có quyền quản trị kết nối Gmail của doanh nghiệp.</p>}
          {status && !status.configured && <p className="mt-4 text-sm text-amber-700">Máy chủ chưa được cấu hình Google OAuth.</p>}
        </div>
      </div>
    </div>
  </section>;
};
