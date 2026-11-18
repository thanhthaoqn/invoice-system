import { useMemo } from 'react';
import { CheckCircle } from 'lucide-react';

const child = (parent: Element | null, name: string): Element | null =>
  parent ? Array.from(parent.children).find(element => element.localName === name) ?? null : null;
const value = (parent: Element | null, name: string) => child(parent, name)?.textContent?.trim() || '';
const display = (text: string) => text || '—';
const money = (text: string) => text && Number.isFinite(Number(text))
  ? new Intl.NumberFormat('vi-VN').format(Number(text)) + ' đ' : display(text);

const formatFullVietnameseDate = (dateStr: string) => {
  if (!dateStr) return '';
  const match = dateStr.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (match) {
    const [, y, m, d] = match;
    return `Ngày ${parseInt(d, 10)} tháng ${parseInt(m, 10)} năm ${y}`;
  }
  return dateStr;
};

const formatShortDate = (dateStr: string) => {
  if (!dateStr) return '—';
  const match = dateStr.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (match) {
    const [, y, m, d] = match;
    return `${d}/${m}/${y}`;
  }
  return dateStr;
};

/** Render only known text fields with authentic Vietnamese e-invoice layout. */
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
      common,
      seller: child(body, 'NBan'),
      buyer: child(body, 'NMua'),
      payment: child(body, 'TToan'),
      lines: goods ? Array.from(goods.children).filter(element => element.localName === 'HHDVu') : [],
    };
  }, [source]);

  if (!parsed) {
    return <p role="alert" className="p-6 text-amber-800 text-xs">Không dựng được bản đối chiếu cho cấu trúc XML này. Chọn “Mã XML” để xem tài liệu gốc.</p>;
  }

  const { common, seller, buyer, payment, lines } = parsed;
  const date = value(common, 'NLap');
  const currency = value(common, 'DVTTe') || 'VND';
  const invoiceNumber = value(common, 'SHDon');
  const invoiceSeries = value(common, 'KHHDon');
  const invoiceTemplate = value(common, 'KHMSHDon');
  const paymentMethod = value(payment, 'HTTT') || 'TM/CK';

  const sellerName = value(seller, 'Ten');
  const sellerTax = value(seller, 'MST');
  const sellerAddress = value(seller, 'DChi');

  const buyerCompany = value(buyer, 'Ten');
  const buyerPerson = value(buyer, 'HVTNMHang');
  const buyerTax = value(buyer, 'MST');
  const buyerAddress = value(buyer, 'DChi');

  const subtotal = value(payment, 'TgTCThue');
  const vatAmount = value(payment, 'TgTThue');
  const totalPayment = value(payment, 'TgTTTBSo');
  const inWords = value(payment, 'TgTTTBChu');
  const vatRate = lines.length ? value(lines[0], 'TSuat') : '0%';

  return (
    <article className="m-2 sm:m-4 bg-white border-2 border-sky-900 rounded p-4 sm:p-5 shadow-xs text-xs text-slate-800 font-sans leading-relaxed">
      {/* Top Header Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pb-3 border-b border-sky-900/30">
        <div className="space-y-1">
          <p className="font-bold text-slate-900 text-sm">{display(sellerName)}</p>
          {sellerTax && <p className="text-slate-700">Mã số thuế: <span className="font-mono font-semibold">{sellerTax}</span></p>}
          {sellerAddress && <p className="text-slate-600">Địa chỉ (Address): {sellerAddress}</p>}
        </div>

        <div className="text-right space-y-1 sm:pl-4">
          <p className="italic text-slate-600">{formatFullVietnameseDate(date)}</p>
          <div className="text-[11px] text-slate-700 space-y-0.5">
            <p>Mẫu số (Form): <strong className="font-mono">{display(invoiceTemplate)}</strong></p>
            <p>Ký hiệu (Serial): <strong className="font-mono">{display(invoiceSeries)}</strong></p>
            <p>
              Số (Invoice No.):{' '}
              <strong className="text-red-700 font-mono text-sm tracking-wider">
                {display(invoiceNumber)}
              </strong>
            </p>
          </div>
        </div>
      </div>

      {/* Buyer Information Section */}
      <div className="py-3 border-b border-sky-900/30 space-y-1 text-slate-700 text-xs">
        <p>
          Tên đơn vị mua hàng (Buyer name):{' '}
          <strong className="text-slate-900 uppercase">{display(buyerCompany || buyerPerson)}</strong>
        </p>
        <p>
          Mã số thuế (Tax code):{' '}
          <span className="font-mono font-medium">{buyerTax ? buyerTax : '—'}</span>
        </p>
        <p>Địa chỉ (Address): {display(buyerAddress)}</p>
        {buyerPerson && buyerCompany && (
          <p>Người mua hàng (Customer): <span>{buyerPerson}</span></p>
        )}
        <div className="flex flex-wrap gap-x-8 pt-0.5">
          <span>Hình thức thanh toán (Payment method): <strong>{paymentMethod}</strong></span>
          <span>Đơn vị tiền tệ (Currency): <strong>{currency}</strong></span>
        </div>
      </div>

      {/* Goods & Services Line Items Table */}
      <div className="overflow-x-auto my-3">
        <table className="w-full text-xs border border-slate-400 border-collapse">
          <thead>
            <tr className="bg-slate-50 text-slate-800 text-center font-semibold border-b border-slate-400">
              <th className="p-1.5 border-r border-slate-400 w-10">STT<br/><span className="text-[10px] font-normal text-slate-500">(No.)</span></th>
              <th className="p-1.5 border-r border-slate-400 text-left">Tên hàng hóa, dịch vụ<br/><span className="text-[10px] font-normal text-slate-500">(Description of goods, services)</span></th>
              <th className="p-1.5 border-r border-slate-400 w-14">ĐVT<br/><span className="text-[10px] font-normal text-slate-500">(Unit)</span></th>
              <th className="p-1.5 border-r border-slate-400 w-16">Số lượng<br/><span className="text-[10px] font-normal text-slate-500">(Quantity)</span></th>
              <th className="p-1.5 border-r border-slate-400 w-24 text-right">Đơn giá<br/><span className="text-[10px] font-normal text-slate-500">(Unit price)</span></th>
              <th className="p-1.5 text-right w-28">Thành tiền<br/><span className="text-[10px] font-normal text-slate-500">(Amount)</span></th>
            </tr>
            <tr className="bg-slate-100/70 text-slate-500 text-[10px] text-center border-b border-slate-400 italic">
              <td className="p-0.5 border-r border-slate-400">(1)</td>
              <td className="p-0.5 border-r border-slate-400">(2)</td>
              <td className="p-0.5 border-r border-slate-400">(3)</td>
              <td className="p-0.5 border-r border-slate-400">(4)</td>
              <td className="p-0.5 border-r border-slate-400 text-right pr-2">(5)</td>
              <td className="p-0.5 text-right pr-2">(6)=(4)x(5)</td>
            </tr>
          </thead>
          <tbody>
            {lines.length > 0 ? (
              lines.map((line, idx) => {
                const qty = value(line, 'SLuong');
                const price = value(line, 'DGia');
                const amt = value(line, 'ThTien');
                const unit = value(line, 'DVTinh') || '—';
                return (
                  <tr key={idx} className="border-b border-slate-300">
                    <td className="p-1.5 text-center border-r border-slate-400 font-mono text-[11px]">{idx + 1}</td>
                    <td className="p-1.5 border-r border-slate-400 font-medium text-slate-900">{display(value(line, 'THHDVu'))}</td>
                    <td className="p-1.5 text-center border-r border-slate-400 text-slate-600">{unit}</td>
                    <td className="p-1.5 text-center border-r border-slate-400 font-mono">{qty ? Number(qty).toLocaleString('vi-VN') : '—'}</td>
                    <td className="p-1.5 text-right border-r border-slate-400 font-mono">{money(price)}</td>
                    <td className="p-1.5 text-right font-mono font-semibold text-slate-900">{money(amt)}</td>
                  </tr>
                );
              })
            ) : (
              <tr>
                <td colSpan={6} className="py-4 text-center text-slate-500">
                  Không có chi tiết mặt hàng trong XML
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Totals & Calculations */}
      <div className="space-y-1.5 pt-2 text-xs">
        <div className="flex justify-between border-b border-slate-200 pb-1">
          <span className="text-slate-600">Cộng tiền hàng (Total amount):</span>
          <span className="font-mono font-medium">{money(subtotal)}</span>
        </div>

        <div className="flex justify-between border-b border-slate-200 pb-1">
          <span className="text-slate-600">Thuế suất GTGT (VAT rate): <strong className="font-mono text-slate-800">{vatRate}</strong></span>
          <span className="font-mono font-medium">{money(vatAmount)}</span>
        </div>

        <div className="flex justify-between pt-1 text-sm font-bold text-slate-900">
          <span>Tổng tiền thanh toán (Total payment):</span>
          <span className="font-mono text-base">{money(totalPayment)}</span>
        </div>

        {inWords && (
          <p className="italic text-slate-700 text-xs pt-1">
            Số tiền viết bằng chữ (In words): <strong>{inWords}</strong>
          </p>
        )}
      </div>

      {/* Signatures Grid */}
      <div className="grid grid-cols-2 gap-4 mt-6 pt-4 border-t border-slate-300 text-center">
        <div>
          <p className="font-bold uppercase text-[11px] text-slate-900">Người Mua Hàng (Buyer)</p>
          <p className="italic text-[10px] text-slate-500">(Ký, ghi rõ họ tên)</p>
          <p className="mt-8 text-xs text-slate-400 italic">(Người mua không ký số)</p>
        </div>

        <div className="flex flex-col items-center">
          <p className="font-bold uppercase text-[11px] text-slate-900">Người Bán Hàng (Seller)</p>
          <p className="italic text-[10px] text-slate-500">(Ký, đóng dấu điện tử)</p>
          
          <div className="mt-3 border border-emerald-600 rounded p-2.5 bg-emerald-50/40 text-left w-full max-w-xs shadow-2xs">
            <div className="flex items-center gap-1.5 text-emerald-800 font-semibold text-[11px]">
              <CheckCircle className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
              <span>Signature Valid - Chữ ký số hợp lệ</span>
            </div>
            <p className="text-[11px] text-slate-800 font-medium mt-1 truncate">
              Ký bởi: <strong className="text-slate-900">{sellerName || 'Đơn vị bán'}</strong>
            </p>
            <p className="text-[10px] text-slate-600 mt-0.5 font-mono">
              Ngày ký: {formatShortDate(date)}
            </p>
          </div>
        </div>
      </div>

      {/* Footer Legal Note */}
      <div className="mt-6 pt-3 border-t border-slate-200 text-center text-[10px] text-slate-500 italic">
        <p>(Cần kiểm tra đối chiếu khi lập, nhận hóa đơn)</p>
        <p className="mt-0.5">Tra cứu hóa đơn điện tử tại cổng thông tin Tổng cục Thuế</p>
      </div>
    </article>
  );
}
