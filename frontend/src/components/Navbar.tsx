import React from 'react';
import { Upload, LayoutDashboard, Database, List, Mail } from 'lucide-react';

interface NavbarProps {
  activeTab: 'upload' | 'review' | 'analytics' | 'logs' | 'invoices' | 'emails';
  setActiveTab: (tab: 'upload' | 'review' | 'analytics' | 'logs' | 'invoices' | 'emails') => void;
  pendingCount: number;
  collapsed: boolean;
}

export const Navbar: React.FC<NavbarProps> = ({ activeTab, setActiveTab, pendingCount, collapsed }) => {
  const tabs = [
    { id: 'analytics', label: 'Báo cáo kế toán', icon: LayoutDashboard },
    { id: 'invoices', label: 'Danh sách hóa đơn', icon: List },
    { id: 'upload', label: 'Nộp hóa đơn', icon: Upload },
    { id: 'emails', label: 'Email', icon: Mail },
    { id: 'logs', label: 'Nhật ký đối soát', icon: Database },
  ] as const;
  return (
    <aside className={`sidebar-rail sticky top-0 z-40 h-dvh shrink-0 self-start overflow-x-hidden overflow-y-auto border-r border-slate-200 bg-white ${collapsed ? 'is-collapsed' : ''}`}>
      <div className="flex h-full flex-col px-3 py-4">
        <nav id="workspace-navigation" aria-label="Điều hướng chính" className="flex flex-col gap-2">
          {tabs.map(({ id, label, icon: Icon }) => <button key={id} onClick={() => setActiveTab(id)} title={label} aria-label={label} aria-current={(activeTab === id || (id === 'invoices' && activeTab === 'review')) ? 'page' : undefined}
            className={`sidebar-item relative flex min-h-12 w-full items-center rounded-xl px-[18px] py-3 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500 ${(activeTab === id || (id === 'invoices' && activeTab === 'review')) ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-200' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'}`}>
            <Icon className="h-5 w-5 shrink-0" />
            <span className="sidebar-label ml-3 whitespace-nowrap">{label}</span>
            {id === 'invoices' && pendingCount > 0 && <span aria-label={`${pendingCount} hóa đơn chờ đối soát`} className={`absolute right-0 top-0 min-w-5 rounded-full px-1.5 py-0.5 text-center text-[11px] font-bold ${(activeTab === id || (id === 'invoices' && activeTab === 'review')) ? 'bg-indigo-500 text-white' : 'bg-amber-100 text-amber-800'}`}>{pendingCount}</span>}
          </button>)}
        </nav>
      </div>
    </aside>
  );
};
