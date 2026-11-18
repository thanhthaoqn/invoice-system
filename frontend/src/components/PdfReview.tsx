import { useEffect, useState } from 'react';
import { intakeApi, errorMessage, type PdfData, type PdfDraft, type PdfLine, type IntakeItem } from '../services/api';

interface Props { itemId: number; canEdit: boolean; onClose: () => void; onConfirmed: (item: IntakeItem) => void }

export function parseMoney(val: string | number | null | undefined): number | null {
  if (val === null || val === undefined) return null;
  if (typeof val === 'number') return isNaN(val) ? null : val;
  const str = String(val).trim();
  if (!str) return null;

  const lower = str.toLowerCase();
  if (/^(kct|không chịu thuế|khong chiu thue|miễn thuế|mien thue|0%)$/i.test(lower)) {
    return 0;
  }

  let s = str.replace(/[đ₫VNDvnd\s]/g, '');
  const dotCount = (s.match(/\./g) || []).length;
  const commaCount = (s.match(/,/g) || []).length;

  if (dotCount > 0 && commaCount === 0) {
    if (dotCount > 1 || /\.\d{3}$/.test(s)) {
      s = s.replace(/\./g, '');
    }
  } else if (commaCount > 0 && dotCount === 0) {
    if (commaCount > 1 || /,\d{3}$/.test(s)) {
      s = s.replace(/,/g, '');
    } else {
      s = s.replace(',', '.');
    }
  } else if (dotCount > 0 && commaCount > 0) {
    if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
      s = s.replace(/\./g, '').replace(',', '.');
    } else {
      s = s.replace(/,/g, '');
    }
  }

  const num = Number(s);
  return isNaN(num) ? null : num;
}

interface MoneyInputProps {
  value: number | null | undefined;
  onChange: (val: number | null) => void;
  disabled?: boolean;
  required?: boolean;
  className?: string;
  placeholder?: string;
}

function MoneyInput({ value, onChange, disabled, required, className, placeholder }: MoneyInputProps) {
  const [text, setText] = useState<string>(() => {
    if (value === null || value === undefined) return '';
    return String(value);
  });
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    if (!focused) {
      if (value === null || value === undefined) {
        setText('');
      } else if (value === 0 && (text.toLowerCase().includes('kct') || text.toLowerCase().includes('khong') || text.toLowerCase().includes('không'))) {
        setText('KCT (0 đ)');
      } else {
        setText(value.toLocaleString('vi-VN'));
      }
    }
  }, [value, focused]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value;
    setText(raw);
    const parsed = parseMoney(raw);
    onChange(parsed);
  };

  const handleBlur = () => {
    setFocused(false);
    const parsed = parseMoney(text);
    if (parsed !== null) {
      if (parsed === 0 && (text.toLowerCase().includes('kct') || text.toLowerCase().includes('khong') || text.toLowerCase().includes('không') || text.toLowerCase().includes('miễn'))) {
        setText('KCT (0 đ)');
      } else {
        setText(parsed.toLocaleString('vi-VN'));
      }
    }
  };

  const handleFocus = () => {
    setFocused(true);
    if (text === 'KCT (0 đ)') {
      setText('KCT');
    }
  };

  return (
    <input
      type="text"
      disabled={disabled}
      required={required}
      className={className}
      value={text}
      onChange={handleChange}
      onFocus={handleFocus}
      onBlur={handleBlur}
      placeholder={placeholder || 'Nhập số tiền hoặc KCT'}
    />
  );
}

function detectVatOption(subtotal: number | null | undefined, vat: number | null | undefined, rawText?: string | null): string {
  const normText = (rawText || '').toLowerCase();
  const sub = Number(subtotal) || 0;
  const v = Number(vat) || 0;

  if (sub > 0 && v > 0) {
    const ratio = v / sub;
    if (Math.abs(ratio - 0.08) < 0.005) return '8%';
    if (Math.abs(ratio - 0.10) < 0.005) return '10%';
    if (Math.abs(ratio - 0.05) < 0.005) return '5%';
    if (ratio === 0) return '0%';
  }
  if (normText.includes('kct') || normText.includes('không chịu thuế') || normText.includes('khong chiu thue')) {
    return 'KCT';
  }
  if (normText.includes('0%') || normText.includes('thuế suất: 0%')) {
    return '0%';
  }
  if (v === 0) return 'KCT';
  return '8%';
}

const fields: { key: keyof PdfData; label: string; type?: string; isMoney?: boolean; isVat?: boolean; required?: boolean }[] = [
  { key: 'invoice_number', label: 'Số hóa đơn', required: true }, { key: 'invoice_date', label: 'Ngày lập', type: 'date', required: true },
  { key: 'invoice_template', label: 'Mẫu số', required: true }, { key: 'invoice_series', label: 'Ký hiệu', required: true },
  { key: 'vendor_name', label: 'Tên bên bán', required: true }, { key: 'tax_code', label: 'MST bên bán', required: true },
  { key: 'seller_address', label: 'Địa chỉ bên bán' }, { key: 'buyer_name', label: 'Tên bên mua' },
  { key: 'buyer_tax_code', label: 'MST bên mua' }, { key: 'buyer_address', label: 'Địa chỉ bên mua' },
  { key: 'currency', label: 'Đơn vị tiền tệ' },
  { key: 'subtotal_amount', label: 'Tổng tiền trước thuế', isMoney: true, required: true },
  { key: 'vat_amount', label: 'Thuế GTGT (VAT)', isVat: true, required: true },
  { key: 'total_amount', label: 'Tổng thanh toán', isMoney: true, required: true },
];

const lineFields: { key: keyof PdfLine; label: string; number?: boolean; isMoney?: boolean; required?: boolean }[] = [
  { key: 'item_name', label: 'Tên hàng', required: true },
  { key: 'quantity', label: 'Số lượng', number: true },
  { key: 'unit_price', label: 'Đơn giá', isMoney: true },
  { key: 'amount', label: 'Thành tiền', isMoney: true, required: true },
  { key: 'vat_rate', label: 'Thuế suất' },
];

const inputClass = 'mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-100 disabled:bg-slate-50';

export function PdfReview({ itemId, canEdit, onClose, onConfirmed }: Props) {
  const [draft, setDraft] = useState<PdfDraft | null>(null);
  const [vatOption, setVatOption] = useState<string>('8%');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let mounted = true;
    intakeApi.pdfDraft(itemId).then(value => {
      if (mounted) {
        setDraft(value);
        setVatOption(detectVatOption(value.data?.subtotal_amount, value.data?.vat_amount, value.data?.raw_text));
      }
    }).catch(err => { if (mounted) setError(errorMessage(err)); });
    return () => { mounted = false; };
  }, [itemId]);

  const update = (key: keyof PdfData, value: string | number | null) => {
    setSaved(false);
    setDraft(previous => {
      if (!previous) return previous;
      const nextData = { ...previous.data, [key]: value };
      if (key === 'subtotal_amount') {
        const sub = Number(value) || 0;
        let rate = 0;
        if (vatOption === '8%') rate = 0.08;
        else if (vatOption === '10%') rate = 0.10;
        else if (vatOption === '5%') rate = 0.05;
        const vat = rate === 0 ? 0 : Math.round(sub * rate);
        nextData.vat_amount = vat;
        nextData.total_amount = sub > 0 ? sub + vat : nextData.total_amount;
      }
      return { ...previous, data: nextData };
    });
  };

  const handleVatOptionChange = (newOpt: string) => {
    setVatOption(newOpt);
    setSaved(false);
    const sub = Number(draft?.data?.subtotal_amount) || 0;
    let rate = 0;
    if (newOpt === '8%') rate = 0.08;
    else if (newOpt === '10%') rate = 0.10;
    else if (newOpt === '5%') rate = 0.05;
    else rate = 0; // 'KCT' or '0%'

    const vat = rate === 0 ? 0 : Math.round(sub * rate);
    setDraft(previous => {
      if (!previous) return previous;
      const newTotal = sub > 0 ? sub + vat : previous.data.total_amount;
      return {
        ...previous,
        data: {
          ...previous.data,
          vat_amount: vat,
          total_amount: newTotal,
        },
      };
    });
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

  const handleReExtract = async () => {
    if (busy || !canEdit) return;
    setBusy(true); setError(''); setSaved(false);
    try {
      const fresh = await intakeApi.reExtractPdf(itemId);
      setDraft(fresh);
      setVatOption(detectVatOption(fresh.data?.subtotal_amount, fresh.data?.vat_amount, fresh.data?.raw_text));
      setSaved(true);
    } catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  };

  const data = draft?.data;

  return (
    <div className="fixed inset-0 z-[60] bg-slate-900/40 p-2 sm:p-5" role="dialog" aria-modal="true" aria-labelledby="pdf-review-title">
      <section className="h-full max-w-[1700px] mx-auto flex flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
        <header className="flex flex-wrap justify-between items-center gap-3 border-b px-5 py-3 shrink-0">
          <div>
            <h2 id="pdf-review-title" className="text-lg font-bold">Kiểm tra dữ liệu PDF</h2>
            <p className="text-xs text-slate-500 mt-1">Xác nhận dữ liệu tạo hóa đơn chờ đối soát; chưa phê duyệt hay xác minh chữ ký.</p>
          </div>
          <div className="flex items-center space-x-2">
            {canEdit && (
              <button
                type="button"
                disabled={busy}
                onClick={handleReExtract}
                className="rounded-lg border border-indigo-200 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 px-3 py-2 text-xs font-semibold flex items-center space-x-1.5 transition disabled:opacity-50"
                title="Chạy lại bóc tách OCR từ file PDF gốc"
              >
                <span>🔄 Trích xuất lại từ PDF</span>
              </button>
            )}
            <button disabled={busy} onClick={onClose} className="rounded-lg border px-3 py-2 text-sm hover:bg-slate-50">Đóng</button>
          </div>
        </header>

        <div className="flex-1 min-h-0 overflow-auto lg:overflow-hidden grid grid-cols-1 lg:grid-cols-2">
          <iframe title="PDF gốc để kiểm tra" src={`/api/intake/items/${itemId}/file`} className="w-full h-[60vh] lg:h-full border-0 bg-slate-100" />
          <form onSubmit={event => { event.preventDefault(); void save(true); }} className="min-h-0 flex flex-col border-l">
            <div className="flex-1 min-h-0 overflow-y-auto p-5 space-y-5">
              {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
              {!data ? (
                <p className="text-slate-500">Đang tải dữ liệu PDF…</p>
              ) : (
                <>
                  <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
                    {data.notes || 'Trường trống chưa được nhận diện. Đối chiếu bản PDF và bổ sung các trường có dấu * trước khi xác nhận.'}
                  </p>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {fields.map(field => {
                      const isMissing = data[field.key] == null || data[field.key] === '';
                      return (
                        <div key={field.key} className="text-xs font-medium text-slate-600">
                          <div className="flex justify-between items-center mb-0.5">
                            <label htmlFor={`field-${field.key}`}>
                              {field.label}{field.required && ' *'}
                            </label>
                            {field.isMoney && data[field.key] != null && (
                              <span className="text-[11px] font-normal text-slate-500">
                                {Number(data[field.key]) === 0 ? (
                                  <span className="text-emerald-600 font-semibold">0 đ (KCT / Miễn thuế)</span>
                                ) : (
                                  `≈ ${Number(data[field.key]).toLocaleString('vi-VN')} đ`
                                )}
                              </span>
                            )}
                          </div>

                          {field.isVat ? (
                            <select
                              id={`field-${field.key}`}
                              className={inputClass}
                              disabled={!canEdit || busy}
                              value={vatOption}
                              onChange={e => handleVatOptionChange(e.target.value)}
                            >
                              <option value="KCT">KCT</option>
                              <option value="0%">0%</option>
                              <option value="5%">5%</option>
                              <option value="8%">8%</option>
                              <option value="10%">10%</option>
                            </select>
                          ) : field.isMoney ? (
                            <MoneyInput
                              disabled={!canEdit || busy}
                              required={field.required}
                              className={`${inputClass} ${isMissing ? 'border-amber-300 bg-amber-50/30' : ''}`}
                              value={data[field.key] as number | null}
                              onChange={val => update(field.key, val)}
                              placeholder="Nhập số tiền"
                            />
                          ) : (
                            <input
                              id={`field-${field.key}`}
                              className={`${inputClass} ${isMissing ? 'border-amber-300 bg-amber-50/30' : ''}`}
                              disabled={!canEdit || busy}
                              type={field.type || 'text'}
                              required={field.required}
                              value={(data[field.key] as string | number | null) ?? ''}
                              onChange={event => update(field.key, event.target.value === '' ? null : event.target.value)}
                            />
                          )}
                        </div>
                      );
                    })}
                  </div>

                  <div className="flex justify-between items-center pt-2">
                    <h3 className="font-semibold text-slate-800">Chi tiết hàng hóa / dịch vụ</h3>
                    <button
                      type="button"
                      disabled={!canEdit || busy}
                      className="text-sm font-medium text-indigo-700 hover:text-indigo-800"
                      onClick={() => {
                        setSaved(false);
                        setDraft(previous => previous ? {
                          ...previous,
                          data: {
                            ...previous.data,
                            line_items: [...(previous.data.line_items || []), { item_name: '', quantity: null, unit_price: null, amount: null, vat_rate: null }]
                          }
                        } : previous);
                      }}
                    >
                      + Thêm dòng
                    </button>
                  </div>

                  {!data.line_items?.length && (
                    <p className="text-sm text-amber-800">Chưa nhận diện được dòng hàng. Có thể bổ sung từ PDF gốc.</p>
                  )}

                  {data.line_items?.map((line, index) => (
                    <div key={index} className="border rounded-xl p-3 space-y-3 bg-slate-50/50">
                      <div className="flex justify-between text-xs items-center">
                        <strong className="text-slate-700">Dòng {index + 1}</strong>
                        <button
                          type="button"
                          disabled={!canEdit || busy}
                          className="text-rose-600 hover:text-rose-700 font-medium"
                          onClick={() => {
                            setSaved(false);
                            setDraft(previous => previous ? {
                              ...previous,
                              data: { ...previous.data, line_items: previous.data.line_items.filter((_, i) => i !== index) }
                            } : previous);
                          }}
                        >
                          Xóa dòng
                        </button>
                      </div>

                      <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                        {lineFields.map(field => (
                          <div key={field.key} className="text-xs text-slate-500">
                            <label className="block mb-0.5">
                              {field.label}{field.required && ' *'}
                            </label>
                            {field.isMoney ? (
                              <MoneyInput
                                disabled={!canEdit || busy}
                                required={field.required}
                                className={inputClass}
                                value={line[field.key] as number | null}
                                onChange={val => {
                                  setSaved(false);
                                  setDraft(previous => previous ? {
                                    ...previous,
                                    data: {
                                      ...previous.data,
                                      line_items: previous.data.line_items.map((item, i) => i === index ? { ...item, [field.key]: val } : item)
                                    }
                                  } : previous);
                                }}
                                placeholder="0"
                              />
                            ) : field.number ? (
                              <input
                                className={inputClass}
                                disabled={!canEdit || busy}
                                type="number"
                                step="0.01"
                                required={field.required}
                                value={line[field.key] ?? ''}
                                onChange={event => {
                                  setSaved(false);
                                  setDraft(previous => previous ? {
                                    ...previous,
                                    data: {
                                      ...previous.data,
                                      line_items: previous.data.line_items.map((item, i) => i === index ? { ...item, [field.key]: event.target.value === '' ? null : Number(event.target.value) } : item)
                                    }
                                  } : previous);
                                }}
                              />
                            ) : (
                              <input
                                className={inputClass}
                                disabled={!canEdit || busy}
                                type="text"
                                required={field.required}
                                value={line[field.key] ?? ''}
                                placeholder={field.key === 'vat_rate' ? 'VD: 8%, 10%, KCT' : ''}
                                onChange={event => {
                                  setSaved(false);
                                  setDraft(previous => previous ? {
                                    ...previous,
                                    data: {
                                      ...previous.data,
                                      line_items: previous.data.line_items.map((item, i) => i === index ? { ...item, [field.key]: event.target.value === '' ? null : event.target.value } : item)
                                    }
                                  } : previous);
                                }}
                              />
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </>
              )}
            </div>

            <footer className="flex flex-wrap items-center justify-end gap-3 border-t bg-slate-50 p-4 shrink-0">
              {saved && <span role="status" className="text-sm text-emerald-700">Đã lưu nháp.</span>}
              <button
                type="button"
                disabled={!draft || busy || !canEdit}
                onClick={() => void save(false)}
                className="rounded-lg border bg-white px-4 py-2 text-sm disabled:opacity-50 hover:bg-slate-50 transition"
              >
                Lưu nháp
              </button>
              <button
                disabled={!draft || busy || !canEdit}
                className="rounded-lg bg-indigo-600 text-white px-4 py-2 text-sm font-semibold disabled:opacity-50 hover:bg-indigo-700 transition"
              >
                {busy ? 'Đang lưu…' : 'Xác nhận dữ liệu & tạo hóa đơn'}
              </button>
            </footer>
          </form>
        </div>
      </section>
    </div>
  );
}
