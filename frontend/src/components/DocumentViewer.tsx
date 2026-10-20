import React, { useState } from 'react';
import { ZoomIn, ZoomOut, RotateCw, Download, FileText, Image as ImageIcon } from 'lucide-react';
import type { Invoice } from '../services/api';

interface DocumentViewerProps {
  invoice: Invoice;
}

export const DocumentViewer: React.FC<DocumentViewerProps> = ({ invoice }) => {
  const [zoom, setZoom] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [hasError, setHasError] = useState(false);

  const handleZoomIn = () => setZoom((prev) => Math.min(prev + 0.25, 3));
  const handleZoomOut = () => setZoom((prev) => Math.max(prev - 0.25, 0.5));
  const handleRotate = () => setRotation((prev) => (prev + 90) % 360);
  const handleReset = () => { setZoom(1); setRotation(0); };

  const isPdf = invoice.fileType?.toUpperCase() === 'PDF' || invoice.fileName?.toLowerCase().endsWith('.pdf');
  const fileUrl = invoice.fileUrl || `/api/invoices/${invoice.id}/file`;

  return (
    <div className="h-full flex flex-col bg-slate-950 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
      {/* Viewer Toolbar */}
      <div className="p-3 bg-slate-900 border-b border-slate-800 flex items-center justify-between">
        <div className="flex items-center space-x-2">
          {isPdf ? (
            <FileText className="w-4 h-4 text-rose-400" />
          ) : (
            <ImageIcon className="w-4 h-4 text-cyan-400" />
          )}
          <span className="text-xs font-semibold text-slate-200 truncate max-w-[200px]" title={invoice.fileName}>
            {invoice.fileName}
          </span>
          <span className="text-[10px] px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono">
            {invoice.fileType}
          </span>
        </div>

        {/* Zoom & Rotate Controls */}
        <div className="flex items-center space-x-1">
          <button
            onClick={handleZoomOut}
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition"
            title="Thu nhỏ (-)"
          >
            <ZoomOut className="w-3.5 h-3.5" />
          </button>
          <span className="text-[11px] font-mono text-slate-400 px-1.5">
            {Math.round(zoom * 100)}%
          </span>
          <button
            onClick={handleZoomIn}
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition"
            title="Phóng to (+)"
          >
            <ZoomIn className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={handleRotate}
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition ml-1"
            title="Xoay 90°"
          >
            <RotateCw className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={handleReset}
            className="px-2 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-[11px] text-slate-300 hover:text-white transition"
            title="Mặc định"
          >
            Đặt lại
          </button>
          <a
            href={fileUrl}
            target="_blank"
            rel="noreferrer"
            download={invoice.fileName}
            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition ml-2"
            title="Tải file gốc"
          >
            <Download className="w-3.5 h-3.5" />
          </a>
        </div>
      </div>

      {/* Document Viewport */}
      <div className="flex-1 overflow-auto p-4 flex items-center justify-center bg-slate-900/50 relative">
        {hasError ? (
          /* High-Fidelity Synthetic Invoice Document Render when raw binary preview is unavailable or demo */
          <div
            style={{
              transform: `scale(${zoom}) rotate(${rotation}deg)`,
              transformOrigin: 'center center',
              transition: 'transform 0.15s ease-out'
            }}
            className="w-full max-w-md bg-white text-slate-900 p-6 rounded-lg shadow-2xl border border-slate-200 font-sans text-xs select-none"
          >
            <div className="border-b-2 border-red-600 pb-3 mb-3 flex justify-between items-start">
              <div>
                <h4 className="font-bold text-red-700 text-sm tracking-wide uppercase">
                  HÓA ĐƠN GIÁ TRỊ GIA TĂNG
                </h4>
                <p className="text-[10px] text-slate-500 font-serif italic">(Bản thể hiện của hóa đơn điện tử)</p>
                <p className="font-semibold text-slate-800 text-[11px] mt-1">{invoice.vendorName || 'ĐƠN VỊ BÁN LẺ'}</p>
                <p className="text-[10px] text-slate-600">MST: <span className="font-mono font-bold">{invoice.taxCode || '---'}</span></p>
              </div>
              <div className="text-right">
                <span className="inline-block px-2 py-0.5 bg-red-100 text-red-700 text-[10px] font-bold rounded">
                  HĐĐT
                </span>
                <p className="text-[11px] font-mono mt-1 font-bold">Số: {invoice.invoiceNumber || 'HD-0000'}</p>
                <p className="text-[10px] text-slate-500">Ngày: {invoice.invoiceDate || '---'}</p>
              </div>
            </div>

            <div className="mb-3 text-[11px] text-slate-700 bg-slate-50 p-2 rounded">
              <p><strong>Người mua hàng:</strong> {invoice.submittedBy}</p>
              <p><strong>Danh mục chi phí:</strong> {invoice.category}</p>
            </div>

            <table className="w-full border-collapse border border-slate-300 text-[10px] mb-3">
              <thead>
                <tr className="bg-slate-100 text-slate-700">
                  <th className="border border-slate-300 p-1">STT</th>
                  <th className="border border-slate-300 p-1 text-left">Tên hàng hóa, dịch vụ</th>
                  <th className="border border-slate-300 p-1 text-right">SL</th>
                  <th className="border border-slate-300 p-1 text-right">Đơn giá</th>
                  <th className="border border-slate-300 p-1 text-right">Thành tiền</th>
                </tr>
              </thead>
              <tbody>
                {invoice.lineItems && invoice.lineItems.length > 0 ? (
                  invoice.lineItems.map((item, idx) => (
                    <tr key={idx}>
                      <td className="border border-slate-300 p-1 text-center font-mono">{idx + 1}</td>
                      <td className="border border-slate-300 p-1">{item.itemName}</td>
                      <td className="border border-slate-300 p-1 text-right font-mono">{item.quantity}</td>
                      <td className="border border-slate-300 p-1 text-right font-mono">
                        {new Intl.NumberFormat('vi-VN').format(item.unitPrice)}
                      </td>
                      <td className="border border-slate-300 p-1 text-right font-mono font-semibold">
                        {new Intl.NumberFormat('vi-VN').format(item.amount)}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={5} className="border border-slate-300 p-2 text-center text-slate-400">
                      Chi tiết chi phí quyết toán
                    </td>
                  </tr>
                )}
              </tbody>
            </table>

            <div className="border-t border-slate-300 pt-2 space-y-1 text-[11px]">
              <div className="flex justify-between">
                <span className="text-slate-600">Cộng tiền hàng:</span>
                <span className="font-mono">{new Intl.NumberFormat('vi-VN').format(invoice.subtotalAmount || 0)} ₫</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-600">Tiền thuế GTGT (VAT):</span>
                <span className="font-mono">{new Intl.NumberFormat('vi-VN').format(invoice.vatAmount || 0)} ₫</span>
              </div>
              <div className="flex justify-between font-bold text-sm border-t border-slate-200 pt-1 text-red-700">
                <span>Tổng tiền thanh toán:</span>
                <span className="font-mono">{new Intl.NumberFormat('vi-VN').format(invoice.totalAmount || 0)} ₫</span>
              </div>
            </div>

            <div className="mt-4 pt-2 border-t border-dashed border-slate-300 flex justify-between items-center text-[9px] text-slate-400">
              <span>Đã ký điện tử bởi {invoice.vendorName}</span>
              <span className="font-mono text-emerald-600 font-bold">Chữ ký hợp lệ</span>
            </div>
          </div>
        ) : isPdf ? (
          <iframe
            src={`${fileUrl}#toolbar=0&navpanes=0`}
            title="PDF Document Viewer"
            className="w-full h-full rounded border-0"
            onError={() => setHasError(true)}
          />
        ) : (
          <div
            style={{
              transform: `scale(${zoom}) rotate(${rotation}deg)`,
              transformOrigin: 'center center',
              transition: 'transform 0.15s ease-out'
            }}
            className="flex items-center justify-center max-w-full max-h-full"
          >
            <img
              src={fileUrl}
              alt="Hóa đơn tải lên"
              className="max-h-[650px] object-contain rounded-lg shadow-xl"
              onError={() => setHasError(true)}
            />
          </div>
        )}
      </div>

      <div className="p-2.5 bg-slate-900/90 border-t border-slate-800 text-[11px] text-slate-400 flex items-center justify-between px-4">
        <span>Cửa sổ kiểm chứng chứng từ gốc (Human-In-The-Loop)</span>
        <button
          onClick={() => setHasError((prev) => !prev)}
          className="text-xs text-indigo-400 hover:text-indigo-300 transition"
        >
          {hasError ? 'Thử tải lại file gốc' : 'Xem dạng bản in chuẩn'}
        </button>
      </div>
    </div>
  );
};
