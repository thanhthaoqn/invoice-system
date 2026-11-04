import unittest
from unittest.mock import patch, Mock
import pymupdf
from app.pdf_extraction import extract_pdf, text_fields, number, PdfExtractionError


TEXT = '''Seller: Example Supplier
Tax code: 0300000001
Address: 1 Example Street
Buyer: Example Buyer
Tax code: 0100000001
Address: 2 Example Street
Invoice number: 00027515
Invoice template: 1
Series: C26TAA
Invoice date: 2026-11-04
Currency: VND
Description | Quantity | Unit price | Amount | VAT rate
Consulting | 2 | 50,000 | 100,000 | 10%
Subtotal: 100,000
VAT amount: 10,000
Total payment: 110,000
'''


def pdf(text=TEXT, pages=1):
    with pymupdf.open() as document:
        for _ in range(pages):
            page = document.new_page()
            if text:
                page.insert_text((40, 50), text, fontsize=10)
        return document.tobytes()


class PdfExtractionTest(unittest.TestCase):
    def test_digital_pdf_extracts_xml_equivalent_fields_without_network(self):
        with patch('app.pdf_extraction.vision_pdf') as vision:
            result = extract_pdf(pdf())
            vision.assert_not_called()
        self.assertEqual(result['invoice_number'], '00027515')
        self.assertEqual(result['invoice_date'], '2026-11-04')
        self.assertEqual(result['invoice_template'], '1')
        self.assertEqual(result['invoice_series'], 'C26TAA')
        self.assertEqual(result['tax_code'], '0300000001')
        self.assertEqual(result['buyer_tax_code'], '0100000001')
        self.assertEqual(result['buyer_name'], 'Example Buyer')
        self.assertEqual(result['seller_address'], '1 Example Street')
        self.assertEqual(result['total_amount'], 110000)
        self.assertEqual(result['line_items'][0]['quantity'], 2)
        self.assertEqual(result['line_items'][0]['unit_price'], 50000)
        self.assertEqual(result['line_items'][0]['amount'], 100000)
        self.assertIsNone(result['confidence_score'])

    def test_vietnamese_labels_and_zero_tax(self):
        result = text_fields('Đơn vị bán: CÔNG TY ABC\nMST: 0300000001\nĐơn vị mua: CÔNG TY XYZ\nMST: 0100000001\nSố hóa đơn: 00001\nMẫu số: 1\nKý hiệu: C26TAA\nNgày 04 tháng 11 năm 2026\nCộng tiền hàng: 1.000.000\nTiền thuế GTGT: 0\nTổng tiền thanh toán: 1.000.000')
        self.assertEqual(result['vendor_name'], 'CÔNG TY ABC')
        self.assertEqual(result['buyer_name'], 'CÔNG TY XYZ')
        self.assertEqual(result['vat_amount'], 0)
        self.assertEqual(result['total_amount'], 1000000)
        self.assertEqual(result['invoice_date'], '2026-11-04')

    def test_missing_values_and_rows_are_not_invented(self):
        result = text_fields('Seller: Example Supplier\nInvoice number: 0001')
        self.assertIsNone(result['vat_amount'])
        self.assertIsNone(result['buyer_tax_code'])
        self.assertEqual(result['line_items'], [])

    def test_page_boundary_and_corrupt_file(self):
        extract_pdf(pdf(pages=2), max_pages=2)
        for content in [pdf(pages=3), b'not pdf', b'%PDF-corrupt']:
            with self.assertRaises(PdfExtractionError):
                extract_pdf(content, max_pages=2)

    def test_scanned_pdf_calls_real_vision_path(self):
        expected = {'invoice_number': '0001', 'line_items': []}
        with patch('app.pdf_extraction.vision_pdf', return_value=expected) as vision:
            self.assertEqual(extract_pdf(pdf(text='')), expected)
            vision.assert_called_once()

    def test_vision_missing_config_and_failure_never_fabricate_data(self):
        with patch.dict('os.environ', {'GEMINI_API_KEY': '', 'GEMINI_MODEL': ''}):
            with self.assertRaises(PdfExtractionError):
                extract_pdf(pdf(text=''))
        with patch.dict('os.environ', {'GEMINI_API_KEY': 'test', 'GEMINI_MODEL': 'test-model'}), patch('app.pdf_extraction.requests.post', return_value=Mock(status_code=503)):
            with self.assertRaises(PdfExtractionError):
                extract_pdf(pdf(text=''))

    def test_localized_numbers(self):
        self.assertEqual(number('1.250.000,50'), 1250000.5)
        self.assertEqual(number('1,250,000.50'), 1250000.5)
        self.assertEqual(number('0'), 0)
        self.assertIsNone(number('not a number'))


if __name__ == '__main__':
    unittest.main()
