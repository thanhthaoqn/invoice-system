import React, { useState, useEffect } from 'react';
import { Navbar } from './components/Navbar';
import { UploadSection } from './components/UploadSection';
import { SplitScreenReview } from './components/SplitScreenReview';
import { AnalyticsDashboard } from './components/AnalyticsDashboard';
import { ErrorLogViewer } from './components/ErrorLogViewer';
import { api, type Invoice } from './services/api';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'upload' | 'review' | 'analytics' | 'logs'>('upload');
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchInvoices = async () => {
    try {
      setLoading(true);
      const data = await api.getInvoices();
      setInvoices(data);
      if (!selectedInvoice && data.length > 0) {
        // Default select first pending invoice
        const pending = data.find((inv) => inv.status === 'PENDING_REVIEW') || data[0];
        setSelectedInvoice(pending);
      }
    } catch (err) {
      console.error('Failed to load invoices', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchInvoices();
  }, []);

  const handleInvoiceUploaded = (newInvoice: Invoice) => {
    setInvoices((prev) => [newInvoice, ...prev]);
    setSelectedInvoice(newInvoice);
    setActiveTab('review'); // Automatically guide user directly into split-screen verification!
  };

  const handleSelectInvoiceForReview = (inv: Invoice) => {
    setSelectedInvoice(inv);
    setActiveTab('review');
  };

  const handleReviewed = (updated: Invoice) => {
    setInvoices((prev) => prev.map((item) => (item.id === updated.id ? updated : item)));
    setSelectedInvoice(updated);
  };

  const pendingCount = invoices.filter((i) => i.status === 'PENDING_REVIEW').length;

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col selection:bg-indigo-600 selection:text-white">
      <Navbar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        pendingCount={pendingCount}
      />

      <main className="flex-1 px-4 sm:px-6 lg:px-8">
        {activeTab === 'upload' && (
          <UploadSection
            onInvoiceUploaded={handleInvoiceUploaded}
            onSelectInvoiceForReview={handleSelectInvoiceForReview}
            invoices={invoices}
            isLoading={loading}
            onRefresh={fetchInvoices}
          />
        )}

        {activeTab === 'review' && (
          selectedInvoice ? (
            <SplitScreenReview
              invoice={selectedInvoice}
              onBack={() => setActiveTab('upload')}
              onReviewed={handleReviewed}
            />
          ) : (
            <div className="text-center py-24 space-y-4">
              <p className="text-slate-500 font-medium">Chưa chọn hóa đơn nào để đối soát.</p>
              <button
                onClick={() => setActiveTab('upload')}
                className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 rounded-xl text-sm font-bold text-white shadow-md shadow-indigo-600/20 transition-all hover:scale-[1.02]"
              >
                Tải lên hoặc chọn hóa đơn
              </button>
            </div>
          )
        )}

        {activeTab === 'analytics' && <AnalyticsDashboard />}

        {activeTab === 'logs' && <ErrorLogViewer />}
      </main>
    </div>
  );
};

export default App;
