import axios from 'axios';

const API_BASE = '/api';

let csrfToken = '';
axios.interceptors.request.use(config => {
  if (config.url?.startsWith('/api/') && config.method && !['get', 'head', 'options'].includes(config.method)) {
    config.headers.set('X-CSRF-Token', csrfToken);
  }
  return config;
});
axios.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401 && !['/api/auth/login', '/api/auth/me'].includes(error.config?.url)) {
    csrfToken = '';
    window.dispatchEvent(new Event('session-expired'));
  }
  return Promise.reject(error);
});

export interface Tenant { id: number; name: string; taxCode: string; status: string; subscriptionStatus: string }
export interface Session { username: string; tenants: Tenant[]; tenantId: number | null; invoiceCreate: boolean; invoiceReview: boolean; csrfToken: string }
export interface IntakeLimits { maxFiles: number; maxFileBytes: number; maxBatchBytes: number; maxEntries: number; maxExpandedBytes: number; maxCompressionRatio: number; maxAttempts: number; maxPdfPages: number }
export interface IntakeItem { id: number; fileName: string; fileSize: number; xmlDocument: boolean; documentType?: string; retryable: boolean; state: string; message: string; invoiceId: number | null; originalItemId: number | null; attempts: number }
export interface UploadResult { session: { id: number; tenantId: number; submittedBy: string; createdAt: string; uploadedFiles: number; state: string }; items: IntakeItem[]; counts: Record<string, number> }
export const errorMessage = (error: unknown): string => axios.isAxiosError(error) ? error.response?.data?.message || 'Không kết nối được máy chủ. Kiểm tra lịch sử trước khi gửi lại.' : 'Thao tác chưa hoàn tất.';
export interface PdfLine { item_name: string; quantity: number | null; unit_price: number | null; amount: number | null; vat_rate: string | null }
export interface PdfData {
  invoice_number: string | null; invoice_date: string | null; invoice_template: string | null; invoice_series: string | null;
  vendor_name: string | null; tax_code: string | null; seller_address: string | null;
  buyer_name: string | null; buyer_tax_code: string | null; buyer_address: string | null; currency: string | null;
  subtotal_amount: number | null; vat_amount: number | null; total_amount: number | null; line_items: PdfLine[];
  extraction_method?: string;
}
export interface PdfDraft { version: number; data: PdfData }
async function saveSession(request: Promise<{ data: Session }>): Promise<Session> {
  const { data } = await request; csrfToken = data.csrfToken; return data;
}
export const authApi = {
  me: () => saveSession(axios.get<Session>('/api/auth/me')),
  login: (username: string, password: string) => saveSession(axios.post<Session>('/api/auth/login', { username, password })),
  selectTenant: (tenantId: number) => saveSession(axios.post<Session>('/api/auth/tenant', { tenantId })),
  logout: async () => { await axios.post('/api/auth/logout'); csrfToken = ''; },
};
export const intakeApi = {
  pdfDraft: async (id: number) => (await axios.get<PdfDraft>(`/api/intake/items/${id}/draft`)).data,
  savePdfDraft: async (id: number, draft: PdfDraft) => (await axios.put<PdfDraft>(`/api/intake/items/${id}/draft`, draft)).data,
  confirmPdf: async (id: number, draft: PdfDraft) => (await axios.post<IntakeItem>(`/api/intake/items/${id}/confirm`, draft)).data,
  limits: async () => (await axios.get<IntakeLimits>('/api/intake/limits')).data,
  history: async () => (await axios.get<UploadResult[]>('/api/intake/sessions')).data,
  upload: async (files: File[], onProgress: (percent: number) => void) => {
    const data = new FormData(); files.forEach(file => data.append('files', file));
    return (await axios.post<UploadResult>('/api/intake/sessions', data, {
      onUploadProgress: event => onProgress(event.total ? Math.round(event.loaded / event.total * 100) : 0),
    })).data;
  },
  retry: async (sessionId: number, itemId: number) => (await axios.post<UploadResult>(`/api/intake/sessions/${sessionId}/items/${itemId}/retry`)).data,
};

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
  validationState?: string | null;
  buyerTaxCode?: string | null;
  buyerName?: string | null;
  sellerAddress?: string | null;
  buyerAddress?: string | null;
  currency?: string | null;
  source?: string | null;
  invoiceTemplate?: string | null;
  invoiceSeries?: string | null;
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
  recheckInvoice: async (id: number): Promise<Invoice> => (await axios.post<Invoice>(`/api/invoices/${id}/recheck`)).data,
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
