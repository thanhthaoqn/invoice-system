import { useEffect, useState } from 'react';
import { intakeApi, errorMessage, type PdfData, type PdfDraft, type PdfLine, type IntakeItem } from '../services/api';

interface Props { itemId: number; canEdit: boolean; onClose: () => void; onConfirmed: (item: IntakeItem) => void }
const fields: { key: keyof PdfData; label: string; type?: string; required?: boolean }[] = [
  { key: 'invoice_number', label: 'Số hóa đơn', required: true }, { key: 'invoice_date', label: 'Ngày lập', type: 'date', required: true },
  { key: 'invoice_template', label: 'Mẫu số', required: true }, { key: 'invoice_series', label: 'Ký hiệu', required: true },
  { key: 'vendor_name', label: 'Tên bên bán', required: true }, { key: 'tax_code', label: 'MST bên bán', required: true },
  { key: 'seller_address', label: 'Địa chỉ bên bán' }, { key: 'buyer_name', label: 'Tên bên mua' },
  { key: 'buyer_tax_code', label: 'MST bên mua' }, { key: 'buyer_address', label: 'Địa chỉ bên mua' },
  { key: 'currency', label: 'Đơn vị tiền tệ' }, { key: 'subtotal_amount', label: 'Tổng tiền trước thuế', type: 'number', required: true },
  { key: 'vat_amount', label: 'Tiền thuế GTGT', type: 'number', required: true }, { key: 'total_amount', label: 'Tổng thanh toán', type: 'number', required: true },
];
const lineFields: { key: keyof PdfLine; label: string; number?: boolean; required?: boolean }[] = [
  { key: 'item_name', label: 'Tên hàng', required: true }, { key: 'quantity', label: 'Số lượng', number: true },
  { key: 'unit_price', label: 'Đơn giá', number: true }, { key: 'amount', label: 'Thành tiền', number: true, required: true }, { key: 'vat_rate', label: 'Thuế suất' },
];
const inputClass = 'mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100 disabled:bg-slate-50';
export function PdfReview({ itemId, canEdit, onClose, onConfirmed }: Props) {
  const [draft, setDraft] = useState<PdfDraft | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  useEffect(() => {
    let mounted = true;
    intakeApi.pdfDraft(itemId).then(value => { if (mounted) setDraft(value); }).catch(err => { if (mounted) setError(errorMessage(err)); });
    return () => { mounted = false; };
  }, [itemId]);
  const update = (key: keyof PdfData, value: string | number | null) => {
    setSaved(false); setDraft(previous => previous ? { ...previous, data: { ...previous.data, [key]: value } } : previous);
  };
  const save = async (confirm: boolean) => {
    if (!draft || busy || !canEdit) return;
    setBusy(true); setError(''); setSaved(false);
    try {
      if (confirm) onConfirmed(await intakeApi.confirmPdf(itemId, draft));
      else { setDraft(await intakeApi.savePdfDraft(itemId, draft)); setSaved(true); }
    } catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  };
  const data = draft?.data;
  return <div className="fixed inset-0 z-[60] bg-slate-900/40 p-2 sm:p-5" role="dialog" aria-modal="true" aria-labelledby="pdf-review-title">
    <section className="h-full max-w-[1700px] mx-auto flex flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
      <header className="flex flex-wrap justify-between items-center gap-3 border-b px-5 py-3 shrink-0">
        <div><h2 id="pdf-review-title" className="text-lg font-bold">Kiểm tra dữ liệu tài liệu</h2><p className="text-xs text-slate-500 mt-1">Xác nhận dữ liệu tạo hóa đơn chờ đối soát; chưa phê duyệt hay xác minh chữ ký.</p></div>
        <button disabled={busy} onClick={onClose} className="rounded-lg border px-3 py-2 text-sm">Đóng</button>
      </header>
      <div className="flex-1 min-h-0 overflow-auto lg:overflow-hidden grid grid-cols-1 lg:grid-cols-2">
        <iframe title="Tài liệu gốc để kiểm tra" src={`/api/intake/items/${itemId}/file`} className="w-full h-[60vh] lg:h-full border-0 bg-slate-100" />
        <form onSubmit={event => { event.preventDefault(); void save(true); }} className="min-h-0 flex flex-col border-l">
          <div className="flex-1 min-h-0 overflow-y-auto p-5 space-y-5">
            {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
            {!data ? <p className="text-slate-500">Đang tải dữ liệu tài liệu…</p> : <>
              <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">Trường trống chưa được nhận diện. Đối chiếu bản gốc và bổ sung các trường có dấu * trước khi xác nhận.</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">{fields.map(field => <label key={field.key} className="text-xs font-medium text-slate-600">
                {field.label}{field.required && ' *'}
                <input className={`${inputClass} ${data[field.key] == null || data[field.key] === '' ? 'border-amber-300 bg-amber-50/30' : ''}`} disabled={!canEdit || busy} type={field.type || 'text'} required={field.required} step={field.type === 'number' ? '0.01' : undefined}
                  value={(data[field.key] as string | number | null) ?? ''} onChange={event => update(field.key, event.target.value === '' ? null : field.type === 'number' ? Number(event.target.value) : event.target.value)} />
              </label>)}</div>
              <div className="flex justify-between items-center"><h3 className="font-semibold">Chi tiết hàng hóa / dịch vụ</h3>
                <button type="button" disabled={!canEdit || busy} className="text-sm text-indigo-700" onClick={() => { setSaved(false); setDraft(previous => previous ? { ...previous, data: { ...previous.data, line_items: [...(previous.data.line_items || []), { item_name: '', quantity: null, unit_price: null, amount: null, vat_rate: null }] } } : previous); }}>+ Thêm dòng</button></div>
              {!data.line_items?.length && <p className="text-sm text-amber-800">Chưa nhận diện được dòng hàng. Có thể bổ sung từ PDF gốc.</p>}
              {data.line_items?.map((line, index) => <div key={index} className="border rounded-xl p-3 space-y-3">
                <div className="flex justify-between text-xs"><strong>Dòng {index + 1}</strong><button type="button" disabled={!canEdit || busy} className="text-rose-700" onClick={() => { setSaved(false); setDraft(previous => previous ? { ...previous, data: { ...previous.data, line_items: previous.data.line_items.filter((_, i) => i !== index) } } : previous); }}>Xóa</button></div>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">{lineFields.map(field => <label key={field.key} className="text-xs text-slate-500">{field.label}{field.required && ' *'}
                  <input className={inputClass} disabled={!canEdit || busy} type={field.number ? 'number' : 'text'} step="0.01" required={field.required} value={line[field.key] ?? ''}
                    onChange={event => { setSaved(false); setDraft(previous => previous ? { ...previous, data: { ...previous.data, line_items: previous.data.line_items.map((item, i) => i === index ? { ...item, [field.key]: event.target.value === '' ? null : field.number ? Number(event.target.value) : event.target.value } : item) } } : previous); }} />
                </label>)}</div>
              </div>)}
            </>}
          </div>
          <footer className="flex flex-wrap items-center justify-end gap-3 border-t bg-slate-50 p-4 shrink-0">
            {saved && <span role="status" className="text-sm text-emerald-700">Đã lưu nháp.</span>}
            <button type="button" disabled={!draft || busy || !canEdit} onClick={() => void save(false)} className="rounded-lg border bg-white px-4 py-2 text-sm disabled:opacity-50">Lưu nháp</button>
            <button disabled={!draft || busy || !canEdit} className="rounded-lg bg-indigo-600 text-white px-4 py-2 text-sm font-semibold disabled:opacity-50">{busy ? 'Đang lưu…' : 'Xác nhận dữ liệu & tạo hóa đơn'}</button>
          </footer>
        </form>
      </div>
    </section>
  </div>;
}
