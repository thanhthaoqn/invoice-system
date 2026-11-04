import React from 'react';
import { FileSpreadsheet, Upload, LayoutDashboard, Database, CheckSquare } from 'lucide-react';

interface NavbarProps {
  activeTab: 'upload' | 'review' | 'analytics' | 'logs';
  setActiveTab: (tab: 'upload' | 'review' | 'analytics' | 'logs') => void;
  pendingCount: number;
}

export const Navbar: React.FC<NavbarProps> = ({ activeTab, setActiveTab, pendingCount }) => {
  const tabs = [
    { id: 'upload', label: 'Nộp chứng từ', icon: Upload },
    { id: 'review', label: 'Đối soát', icon: CheckSquare },
    { id: 'analytics', label: 'Báo cáo kế toán', icon: LayoutDashboard },
    { id: 'logs', label: 'Nhật ký đối soát', icon: Database },
  ] as const;
  return (
    <header className="sticky top-0 z-50 bg-white/95 backdrop-blur-md border-b border-slate-200 shadow-xs">
      <div className="max-w-[1700px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col gap-4 py-4 lg:flex-row lg:items-center lg:justify-between lg:gap-8">
          <button onClick={() => setActiveTab('upload')} aria-label="SmartExpense — về trang nộp chứng từ" className="flex shrink-0 items-center gap-3 text-left rounded-xl focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-indigo-500">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-sm shadow-indigo-200">
              <FileSpreadsheet className="h-6 w-6" strokeWidth={1.8} />
            </span>
            <span>
              <span className="block text-xl font-bold tracking-tight text-slate-900">SmartExpense</span>
              <span className="block mt-0.5 text-xs text-slate-500">Quản lý hóa đơn & chi phí doanh nghiệp</span>
            </span>
          </button>
          <nav aria-label="Điều hướng chính" className="flex items-center gap-1 overflow-x-auto pb-1 lg:pb-0">
            {tabs.map(({ id, label, icon: Icon }) => <button key={id} onClick={() => setActiveTab(id)} aria-current={activeTab === id ? 'page' : undefined}
              className={`flex shrink-0 items-center gap-2 whitespace-nowrap rounded-xl px-4 py-2.5 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500 ${activeTab === id ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-200' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'}`}>
              <Icon className="h-4 w-4" />
              <span>{label}</span>
              {id === 'review' && pendingCount > 0 && <span aria-label={`${pendingCount} hóa đơn chờ đối soát`} className={`min-w-5 rounded-full px-1.5 py-0.5 text-center text-[11px] font-bold ${activeTab === id ? 'bg-white/20 text-white' : 'bg-amber-100 text-amber-800'}`}>{pendingCount}</span>}
            </button>)}
          </nav>
        </div>
      </div>
    </header>
  );
};
