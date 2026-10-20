import React, { useState, useEffect } from 'react';
import {
  DollarSign,
  FileCheck,
  Clock,
  Sparkles,
  PieChart,
  BarChart3,
  TrendingUp,
  Building,
  RefreshCw,
  FileSpreadsheet
} from 'lucide-react';
import { api, type AnalyticsSummary } from '../services/api';

export const AnalyticsDashboard: React.FC = () => {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchSummary = async () => {
    try {
      setLoading(true);
      const data = await api.getAnalyticsSummary();
      setSummary(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSummary();
  }, []);

  const formatVND = (num: number | null | undefined) => {
    if (num == null) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(num);
  };

  const handleDownloadExcel = () => {
    window.open(api.exportExcelUrl, '_blank');
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto py-6">
      {/* Header and Excel Export Trigger */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl">
        <div>
          <h2 className="text-2xl font-extrabold text-white flex items-center space-x-2">
            <BarChart3 className="w-6 h-6 text-indigo-400" />
            <span>Báo Cáo Kế Toán & Phân Tích Quyết Toán Chi Phí</span>
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Tổng hợp ngân sách chi tiêu, thống kê nhà cung cấp và xuất file Excel chuẩn mực cho Kế toán trưởng
          </p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={fetchSummary}
            className="p-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition"
            title="Làm mới số liệu"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>

          <button
            onClick={handleDownloadExcel}
            className="flex items-center space-x-2 px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold shadow-lg shadow-emerald-600/30 hover:scale-[1.02] active:scale-[0.98] transition-all"
          >
            <FileSpreadsheet className="w-4 h-4" />
            <span>Xuất Excel "Bảng Kê Chi Phí"</span>
          </button>
        </div>
      </div>

      {/* 4 Executive Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Spend */}
        <div className="bg-slate-900/80 border border-slate-800 p-5 rounded-2xl relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400">Tổng Chi Tiêu Đã Duyệt</span>
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-extrabold text-white font-mono">
            {formatVND(summary?.totalApprovedSpend)}
          </div>
          <div className="mt-1 flex items-center text-[11px] text-emerald-400">
            <TrendingUp className="w-3 h-3 mr-1" />
            <span>{summary?.approvedCount || 0} hóa đơn đã hoàn tất</span>
          </div>
        </div>

        {/* Pending Review Queue */}
        <div className="bg-slate-900/80 border border-slate-800 p-5 rounded-2xl relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400">Chờ Kế Toán Đối Soát</span>
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-extrabold text-amber-400 font-mono">
            {summary?.pendingReviewCount || 0}
          </div>
          <div className="mt-1 text-[11px] text-slate-400">
            Hồ sơ trong hàng chờ Split-Screen
          </div>
        </div>

        {/* Total Invoices */}
        <div className="bg-slate-900/80 border border-slate-800 p-5 rounded-2xl relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400">Tổng Chứng Từ Tiếp Nhận</span>
            <div className="w-8 h-8 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
              <FileCheck className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-extrabold text-slate-100 font-mono">
            {summary?.totalInvoicesCount || 0}
          </div>
          <div className="mt-1 text-[11px] text-slate-400">
            Đã đồng bộ cơ sở dữ liệu quan hệ
          </div>
        </div>

        {/* AI Accuracy */}
        <div className="bg-slate-900/80 border border-slate-800 p-5 rounded-2xl relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-400">Độ Chính Xác Trích Xuất AI</span>
            <div className="w-8 h-8 rounded-lg bg-purple-500/10 border border-purple-500/20 flex items-center justify-center text-purple-400">
              <Sparkles className="w-4 h-4" />
            </div>
          </div>
          <div className="mt-3 text-2xl font-extrabold text-purple-300 font-mono">
            {summary?.aiAccuracyRate ?? 100}%
          </div>
          <div className="mt-1 text-[11px] text-slate-400">
            Dựa trên {summary?.totalErrorLogsCount || 0} lần ghi nhận sửa đổi
          </div>
        </div>
      </div>

      {/* Visual Analytics Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Category Spending Breakdown */}
        <div className="bg-slate-900/90 border border-slate-800 p-6 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center space-x-2">
              <PieChart className="w-5 h-5 text-indigo-400" />
              <h3 className="text-sm font-bold text-white">Phân Bổ Chi Phí Theo Danh Mục</h3>
            </div>
            <span className="text-xs text-slate-400 font-mono">Tỷ trọng %</span>
          </div>

          <div className="space-y-4">
            {summary?.categorySpends && summary.categorySpends.length > 0 ? (
              summary.categorySpends.map((cat, idx) => (
                <div key={idx} className="space-y-1.5">
                  <div className="flex justify-between text-xs">
                    <span className="font-medium text-slate-200">{cat.category}</span>
                    <span className="font-mono text-slate-400">
                      {formatVND(cat.amount)} ({cat.percentage}%)
                    </span>
                  </div>
                  {/* Progress Bar */}
                  <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${
                        idx === 0
                          ? 'bg-gradient-to-r from-indigo-500 to-indigo-400'
                          : idx === 1
                          ? 'bg-gradient-to-r from-emerald-500 to-teal-400'
                          : idx === 2
                          ? 'bg-gradient-to-r from-amber-500 to-yellow-400'
                          : 'bg-gradient-to-r from-purple-500 to-pink-400'
                      }`}
                      style={{ width: `${Math.max(cat.percentage, 5)}%` }}
                    />
                  </div>
                </div>
              ))
            ) : (
              <div className="py-12 text-center text-xs text-slate-500">
                Chưa có dữ liệu danh mục chi tiêu đã phê duyệt.
              </div>
            )}
          </div>
        </div>

        {/* Top Spend Vendors */}
        <div className="bg-slate-900/90 border border-slate-800 p-6 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center space-x-2">
              <Building className="w-5 h-5 text-cyan-400" />
              <h3 className="text-sm font-bold text-white">Top Nhà Cung Cấp Chiếm Tỷ Trọng Lớn Nhất</h3>
            </div>
            <span className="text-xs text-slate-400 font-mono">Top 5</span>
          </div>

          <div className="space-y-3">
            {summary?.topVendors && summary.topVendors.length > 0 ? (
              summary.topVendors.map((vendor, idx) => (
                <div
                  key={idx}
                  className="p-3 bg-slate-950/60 border border-slate-800/80 rounded-xl flex items-center justify-between text-xs hover:border-slate-700 transition"
                >
                  <div className="flex items-center space-x-3">
                    <div className="w-6 h-6 rounded-full bg-indigo-500/10 text-indigo-400 font-mono font-bold flex items-center justify-center text-[10px]">
                      {idx + 1}
                    </div>
                    <div>
                      <div className="font-semibold text-slate-200 line-clamp-1">{vendor.vendorName}</div>
                      <div className="text-[10px] text-slate-500">{vendor.invoiceCount} lần thanh toán</div>
                    </div>
                  </div>
                  <div className="text-right font-mono font-bold text-emerald-400">
                    {formatVND(vendor.amount)}
                  </div>
                </div>
              ))
            ) : (
              <div className="py-12 text-center text-xs text-slate-500">
                Chưa có dữ liệu nhà cung cấp đã phê duyệt.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
