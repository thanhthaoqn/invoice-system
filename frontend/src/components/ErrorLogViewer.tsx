import React, { useState, useEffect } from 'react';
import { Database, RefreshCw, Sparkles, ShieldCheck } from 'lucide-react';
import { api, type ErrorLog } from '../services/api';

export const ErrorLogViewer: React.FC = () => {
  const [logs, setLogs] = useState<ErrorLog[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchLogs = async () => {
    try {
      setLoading(true);
      const data = await api.getErrorLogs();
      setLogs(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, []);

  // Compute field distribution
  const fieldCounts: Record<string, number> = {};
  logs.forEach((log) => {
    fieldCounts[log.fieldName] = (fieldCounts[log.fieldName] || 0) + 1;
  });

  const getFieldLabel = (field: string) => {
    switch (field) {
      case 'invoiceNumber': return 'Số hóa đơn';
      case 'taxCode': return 'Mã số thuế (MST)';
      case 'vendorName': return 'Tên đơn vị bán lẻ';
      case 'category': return 'Danh mục chi phí';
      case 'totalAmount': return 'Tổng tiền thanh toán';
      case 'vatAmount': return 'Tiền thuế VAT';
      default: return field;
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto py-6">
      {/* Header & Prompt Engineering Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-purple-950/40 to-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-purple-500/10 border border-purple-500/20 text-purple-400 text-xs font-semibold mb-3">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Continuous Improvement & AI Prompt Tuning</span>
            </div>
            <h2 className="text-2xl font-extrabold text-white">
              Bảng Phân Tích Lỗi & Phản Hồi Mô Hình AI
            </h2>
            <p className="text-xs text-slate-300 mt-1 max-w-3xl leading-relaxed">
              Mỗi khi Kế toán viên chỉnh sửa ô dữ liệu sai lệch trên giao diện Split-Screen, hệ thống tự động ghi nhận
              sai số giữa giá trị dự đoán ban đầu của AI và giá trị thực tế vào bảng này để phục vụ tinh chỉnh Prompt Engineering.
            </p>
          </div>

          <button
            onClick={fetchLogs}
            className="self-start sm:self-center flex items-center space-x-2 px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Làm mới Logs</span>
          </button>
        </div>
      </div>

      {/* Field Distribution Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-slate-900/80 border border-slate-800 p-4 rounded-xl">
          <span className="text-xs text-slate-400">Tổng Số Lần Điều Chỉnh</span>
          <div className="text-2xl font-extrabold text-white font-mono mt-1">{logs.length}</div>
          <span className="text-[11px] text-slate-500">Mẫu phản hồi Human-In-The-Loop</span>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 p-4 rounded-xl">
          <span className="text-xs text-slate-400">Trường Bị Điều Chỉnh Nhiều Nhất</span>
          <div className="text-lg font-bold text-amber-400 mt-1 truncate">
            {Object.keys(fieldCounts).length > 0
              ? getFieldLabel(Object.entries(fieldCounts).sort((a, b) => b[1] - a[1])[0][0])
              : 'Chưa có'}
          </div>
          <span className="text-[11px] text-slate-500">Cần bổ sung few-shot prompt</span>
        </div>

        <div className="bg-slate-900/80 border border-slate-800 p-4 rounded-xl">
          <span className="text-xs text-slate-400">Trạng Thái Mô Hình</span>
          <div className="text-lg font-bold text-emerald-400 mt-1 flex items-center space-x-1.5">
            <ShieldCheck className="w-4 h-4" />
            <span>Gemini 1.5 Flash Active</span>
          </div>
          <span className="text-[11px] text-slate-500">Structured JSON Output enabled</span>
        </div>
      </div>

      {/* Error Logs Table */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="p-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Database className="w-4 h-4 text-purple-400" />
            <h3 className="text-sm font-bold text-white">Chi Tiết Sai Lệch Đối Soát (Error Log Table)</h3>
          </div>
          <span className="text-xs text-slate-400 font-mono">{logs.length} bản ghi</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] font-mono border-b border-slate-800">
              <tr>
                <th className="py-3 px-4">Log ID</th>
                <th className="py-3 px-4">Hóa Đơn</th>
                <th className="py-3 px-4">Trường Dữ Liệu</th>
                <th className="py-3 px-4">Giá Trị AI Ban Đầu (Original)</th>
                <th className="py-3 px-4">Giá Trị Kế Toán Sửa (Corrected)</th>
                <th className="py-3 px-4">Lý Do / Ghi Chú</th>
                <th className="py-3 px-4">Người Sửa / Thời Gian</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono">
              {logs.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500 font-sans">
                    Chưa có sai lệch nào được ghi nhận. Khi bạn chỉnh sửa bất kỳ ô dữ liệu nào trong giao diện Split-Screen và bấm Lưu, hệ thống sẽ lưu vết tại đây!
                  </td>
                </tr>
              ) : (
                logs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-800/30 transition font-sans">
                    <td className="py-3 px-4 font-mono font-bold text-slate-400">
                      #{log.id}
                    </td>

                    <td className="py-3 px-4 font-mono">
                      <span className="text-indigo-400 font-bold">HĐ #{log.invoiceId}</span>
                    </td>

                    <td className="py-3 px-4">
                      <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-200 border border-slate-700 text-[11px] font-semibold">
                        {getFieldLabel(log.fieldName)}
                      </span>
                    </td>

                    <td className="py-3 px-4">
                      <span className="inline-block px-2 py-1 rounded bg-red-950/40 text-red-400 border border-red-500/30 font-mono line-through text-[11px]">
                        {log.originalAiValue || '(Rỗng)'}
                      </span>
                    </td>

                    <td className="py-3 px-4">
                      <span className="inline-block px-2 py-1 rounded bg-emerald-950/40 text-emerald-300 border border-emerald-500/30 font-mono font-bold text-[11px]">
                        {log.correctedValue || '(Rỗng)'}
                      </span>
                    </td>

                    <td className="py-3 px-4 text-slate-300 text-xs max-w-xs truncate" title={log.reason || ''}>
                      {log.reason || 'Chỉnh sửa đối soát'}
                    </td>

                    <td className="py-3 px-4 text-[11px] text-slate-400">
                      <div>{log.correctedBy || 'Kế toán viên'}</div>
                      <div className="text-[10px] text-slate-500">
                        {log.createdAt ? new Date(log.createdAt).toLocaleString('vi-VN') : ''}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
