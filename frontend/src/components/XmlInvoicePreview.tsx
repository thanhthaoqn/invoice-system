import { useMemo } from 'react';

const child = (parent: Element | null, name: string): Element | null =>
  parent ? Array.from(parent.children).find(element => element.localName === name) ?? null : null;
const value = (parent: Element | null, name: string) => child(parent, name)?.textContent?.trim() || '';
const display = (text: string) => text || 'Không có trong XML';
const money = (text: string) => text && Number.isFinite(Number(text))
  ? new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 6 }).format(Number(text)) : display(text);

/** Render only known text fields. Never execute the XML's linked stylesheet or HTML. */
export function XmlInvoicePreview({ source }: { source: string }) {
  const parsed = useMemo(() => {
    if (/<!DOCTYPE/i.test(source)) return null;
    const document = new DOMParser().parseFromString(source, 'application/xml');
    if (document.getElementsByTagNameNS('*', 'parsererror').length) return null;
    const data = document.getElementsByTagNameNS('*', 'DLHDon');
    if (data.length !== 1) return null;
    const common = child(data[0], 'TTChung');
    const body = child(data[0], 'NDHDon');
    const goods = child(body, 'DSHHDVu');
    return {
      common, seller: child(body, 'NBan'), buyer: child(body, 'NMua'), payment: child(body, 'TToan'),
      lines: goods ? Array.from(goods.children).filter(element => element.localName === 'HHDVu') : [],
    };
  }, [source]);
  if (!parsed) return <p role="alert" className="p-6 text-amber-800">Không dựng được bản đối chiếu cho cấu trúc XML này. Chọn “Mã XML” để xem tài liệu gốc.</p>;
  const { common, seller, buyer, payment, lines } = parsed;
  const date = value(common, 'NLap');
  const currency = value(common, 'DVTTe');
  return <article className="m-3 sm:m-5 bg-white border border-slate-200 rounded-lg p-4 sm:p-6 shadow-sm text-sm text-slate-800">
    <div className="text-center border-b border-slate-200 pb-5 mb-5">
      <p className="text-xs text-indigo-600 font-semibold uppercase tracking-wider">Dữ liệu từ XML gốc</p>
      <h2 className="mt-2 text-xl font-bold">{value(common, 'THDon') || 'Hóa đơn điện tử'}</h2>
      <p className="mt-2">Số hóa đơn: <strong>{display(value(common, 'SHDon'))}</strong></p>
      <div className="flex flex-wrap justify-center gap-x-5 gap-y-1 mt-2 text-xs text-slate-500">
        <span>Mẫu số: {display(value(common, 'KHMSHDon'))}</span>
        <span>Ký hiệu: {display(value(common, 'KHHDon'))}</span>
        <span>Ngày lập: {/^\d{4}-\d{2}-\d{2}$/.test(date) ? date.split('-').reverse().join('/') : display(date)}</span>
      </div>
    </div>
    {[{ title: 'Đơn vị bán', party: seller }, { title: 'Đơn vị mua', party: buyer }].map(({ title, party }) =>
      <section key={title} className="mb-4 pb-4 border-b border-slate-100 space-y-1.5 break-words">
        <h3 className="text-xs uppercase tracking-wide text-slate-500 font-semibold">{title}</h3>
        <p className="font-semibold">{display(value(party, 'Ten'))}</p>
        <p>Mã số thuế: <span className="font-mono">{display(value(party, 'MST'))}</span></p>
        {value(party, 'DChi') && <p className="text-slate-600">Địa chỉ: {value(party, 'DChi')}</p>}
        {value(party, 'HVTNMHang') && <p>Người mua hàng: {value(party, 'HVTNMHang')}</p>}
      </section>)}
    <div className="overflow-x-auto my-5">
      <table className="w-full text-xs border-collapse">
        <thead><tr className="bg-slate-50 border-y border-slate-200"><th className="p-2 text-left">Hàng hóa / dịch vụ</th><th className="p-2 text-right">SL</th><th className="p-2 text-right">Đơn giá</th><th className="p-2 text-right">Thành tiền</th><th className="p-2 text-right">Thuế suất</th></tr></thead>
        <tbody>{lines.length ? lines.map((line, index) => <tr key={index} className="border-b border-slate-100 align-top">
          <td className="p-2 min-w-32 break-words">{display(value(line, 'THHDVu'))}</td>
          <td className="p-2 text-right tabular-nums">{money(value(line, 'SLuong'))}</td>
          <td className="p-2 text-right tabular-nums whitespace-nowrap">{money(value(line, 'DGia'))}</td>
          <td className="p-2 text-right tabular-nums whitespace-nowrap">{money(value(line, 'ThTien'))}</td>
          <td className="p-2 text-right">{display(value(line, 'TSuat'))}</td>
        </tr>) : <tr><td colSpan={5} className="py-5 text-center text-slate-500">XML không có chi tiết hàng hóa.</td></tr>}</tbody>
      </table>
    </div>
    <dl className="space-y-3">
      <div className="flex justify-between gap-4"><dt>Tổng tiền trước thuế</dt><dd className="font-medium tabular-nums">{money(value(payment, 'TgTCThue'))}</dd></div>
      <div className="flex justify-between gap-4"><dt>Tiền thuế GTGT</dt><dd className="font-medium tabular-nums">{money(value(payment, 'TgTThue'))}</dd></div>
      <div className="flex justify-between gap-4 border-t pt-3 text-base font-bold"><dt>Tổng thanh toán {currency && `(${currency})`}</dt><dd className="tabular-nums">{money(value(payment, 'TgTTTBSo'))}</dd></div>
    </dl>
    {value(payment, 'TgTTTBChu') && <p className="mt-4 text-xs italic text-slate-600">Bằng chữ: {value(payment, 'TgTTTBChu')}</p>}
    <p className="mt-6 border-t pt-3 text-xs text-slate-500">Bản đối chiếu được dựng từ nội dung XML gốc, độc lập với dữ liệu chỉnh sửa bên phải. Chưa xác minh chữ ký điện tử.</p>
  </article>;
}
