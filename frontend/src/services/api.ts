import axios from 'axios';

const API_BASE = '/api';

export interface InvoiceLineItem {
  id?: number;
  itemName: string;
  quantity: number;
  unitPrice: number;
  amount: number;
  vatRate?: string;
  lineNumber?: number;
}

export interface Invoice {
  id: number;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  vendorName: string | null;
  taxCode: string | null;
  subtotalAmount: number | null;
  vatAmount: number | null;
  totalAmount: number | null;
  category: string;
  filePath: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  fileUrl: string;
  status: 'PROCESSING' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
  arithmeticValid: boolean;
  arithmeticErrorMessage: string | null;
  duplicateSuspected: boolean;
  duplicateWarningMessage: string | null;
  contentHash?: string | null;
  isTechnicalDuplicate?: boolean;
  technicalDuplicateMessage?: string | null;
  cashlessRequired?: boolean;
  cashlessWarningMessage?: string | null;
  debitAccount?: string | null;
  vatAccount?: string | null;
  creditAccount?: string | null;
  extractionMethod?: string | null;
  taxCodeValid?: boolean;
  taxCodeErrorMessage?: string | null;
  rawAiResponse: string | null;
  submittedBy: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  lineItems: InvoiceLineItem[];
}

export interface ReviewInvoiceRequest {
  invoiceNumber?: string;
  invoiceDate?: string;
  vendorName?: string;
  taxCode?: string;
  subtotalAmount?: number;
  vatAmount?: number;
  totalAmount?: number;
  category?: string;
  debitAccount?: string;
  vatAccount?: string;
  creditAccount?: string;
  status: 'APPROVED' | 'REJECTED';
  reviewedBy?: string;
  reason?: string;
  lineItems?: InvoiceLineItem[];
}

export interface CategorySpend {
  category: string;
  amount: number;
  invoiceCount: number;
  percentage: number;
}

export interface VendorSpend {
  vendorName: string;
  amount: number;
  invoiceCount: number;
}

export interface AnalyticsSummary {
  totalApprovedSpend: number;
  totalInvoicesCount: number;
  pendingReviewCount: number;
  approvedCount: number;
  rejectedCount: number;
  aiAccuracyRate: number;
  totalErrorLogsCount: number;
  categorySpends: CategorySpend[];
  topVendors: VendorSpend[];
}

export interface ErrorLog {
  id: number;
  invoiceId: number;
  invoiceNumber: string;
  fieldName: string;
  originalAiValue: string | null;
  correctedValue: string | null;
  reason: string | null;
  correctedBy: string | null;
  createdAt: string;
}

export const api = {
  uploadInvoice: async (file: File, submittedBy?: string): Promise<Invoice> => {
    const formData = new FormData();
    formData.append('file', file);
    if (submittedBy) {
      formData.append('submittedBy', submittedBy);
    }
    const response = await axios.post<Invoice>(`${API_BASE}/invoices/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  getInvoices: async (status?: string): Promise<Invoice[]> => {
    const params = status ? { status } : {};
    const response = await axios.get<Invoice[]>(`${API_BASE}/invoices`, { params });
    return response.data;
  },

  getInvoice: async (id: number): Promise<Invoice> => {
    const response = await axios.get<Invoice>(`${API_BASE}/invoices/${id}`);
    return response.data;
  },

  reviewInvoice: async (id: number, data: ReviewInvoiceRequest): Promise<Invoice> => {
    const response = await axios.put<Invoice>(`${API_BASE}/invoices/${id}/review`, data);
    return response.data;
  },

  getAnalyticsSummary: async (): Promise<AnalyticsSummary> => {
    const response = await axios.get<AnalyticsSummary>(`${API_BASE}/analytics/summary`);
    return response.data;
  },

  getErrorLogs: async (): Promise<ErrorLog[]> => {
    const response = await axios.get<ErrorLog[]>(`${API_BASE}/analytics/error-logs`);
    return response.data;
  },

  exportExcelUrl: `${API_BASE}/invoices/export`,
};
