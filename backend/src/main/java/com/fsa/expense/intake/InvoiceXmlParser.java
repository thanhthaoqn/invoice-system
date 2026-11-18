package com.fsa.expense.intake;

import com.fsa.expense.entity.*;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Vietnamese e-invoice XML. No network, DTD, entity expansion or signature claims. */
@Component
public class InvoiceXmlParser {
    public Invoice parse(byte[] bytes) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "100");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            Element root = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            Element data = "DLHDon".equalsIgnoreCase(root.getLocalName()) ? root : descendant(root, "DLHDon");
            if (data == null) throw new IllegalArgumentException("Không tìm thấy DLHDon trong XML hóa đơn");
            Element header = child(data, "TTChung");
            Element body = child(data, "NDHDon");
            Element seller = child(body, "NBan");
            if(seller==null)seller=child(body,"nbanccthue");
            Element buyer = child(body, "NMua");
            Element payment = child(body, "TToan");
            String bName = text(buyer, "Ten", 255);
            if (bName.isEmpty()) {
                bName = text(buyer, "HVTNMHang", 255);
            }
            Invoice invoice = Invoice.builder()
                .invoiceNumber(required(header, "SHDon", 50))
                .invoiceTemplate(required(header, "KHMSHDon", 50))
                .invoiceSeries(required(header, "KHHDon", 50))
                .invoiceDate(LocalDate.parse(required(header, "NLap", 10)))
                .vendorName(required(seller, "Ten", 255))
                .taxCode(required(seller, "MST", 50))
                .buyerTaxCode(text(buyer, "MST", 50))
                .buyerName(bName)
                .buyerAddress(text(buyer, "DChi", 1000))
                .sellerAddress(text(seller, "DChi", 1000))
                .currency(text(header, "DVTTe", 10)).source("XML_IMPORT")
                .subtotalAmount(amount(payment, "TgTCThue", true))
                .vatAmount(amount(payment, "TgTThue", false))
                .totalAmount(amount(payment, "TgTTTBSo", true))
                .category("Chi phí Dịch vụ / Khác")
                .status(InvoiceStatus.PENDING_REVIEW)
                .fileType("XML").extractionMethod("XML_IMPORT").validationState("NOT_CHECKED")
                .build();
            Element goods = child(body, "DSHHDVu");
            if (goods != null) {
                for (Node n = goods.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n instanceof Element item && "HHDVu".equalsIgnoreCase(item.getLocalName())) {
                        // Notes, discounts and commercial descriptions are not ordinary goods lines.
                        String nature = text(item, "TChat", 10);
                        if (!nature.isEmpty() && !"1".equals(nature)) continue;
                        BigDecimal quantity=amount(item, "SLuong", false);
                        if (quantity != null && quantity.precision()-quantity.scale() > 8) throw new IllegalArgumentException("Số lượng vượt giới hạn lưu trữ");
                        invoice.getLineItems().add(InvoiceLineItem.builder().invoice(invoice)
                            .itemName(required(item, "THHDVu", 255))
                            .quantity(quantity).unitPrice(amount(item, "DGia", false))
                            .amount(amount(item, "ThTien", true)).vatRate(text(item, "TSuat", 20))
                            .lineNumber(invoice.getLineItems().size() + 1).build());
                    }
                }
            }
            invoice.setIdentityKey(com.fsa.expense.service.InvoiceIdentity.key(invoice.getTaxCode(), invoice.getInvoiceTemplate(), invoice.getInvoiceSeries(), invoice.getInvoiceNumber()));
            invoice.setSignatureState(signatureState(root));
            return invoice;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new IllegalArgumentException("XML không hợp lệ hoặc ngày lập không đúng định dạng", e);
        }
    }
    private Element descendant(Element root, String name) {
        NodeList list = root.getElementsByTagName("*");Element found=null;
        for(int i=0;i<list.getLength();i++)if(list.item(i) instanceof Element e && name.equalsIgnoreCase(e.getLocalName())){
            if(found!=null)return null;found=e;
        }
        return found;
    }
    private Element child(Element parent, String name) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e && name.equalsIgnoreCase(e.getLocalName())) return e;
        return null;
    }
    private String text(Element parent, String name, int limit) {
        Element e = child(parent, name);
        String value = e == null ? "" : e.getTextContent().trim();
        if (value.length() > limit) throw new IllegalArgumentException("Trường " + name + " vượt giới hạn ký tự");
        return value;
    }
    private String required(Element parent, String name, int limit) {
        String value = text(parent, name, limit);
        if (value.isBlank()) throw new IllegalArgumentException("Thiếu trường " + name);
        return value;
    }
    private BigDecimal amount(Element parent, String name, boolean required) {
        String value = text(parent, name, 40);
        if (value.isEmpty() && !required) return null;
        try {
            BigDecimal result = new BigDecimal(value);
            if (result.precision() - result.scale() > 13 || result.scale() > 2) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Số tiền/số lượng " + name + " không hợp lệ"); }
    }
    private String signatureState(Element root){
        NodeList signatures=root.getElementsByTagNameNS(javax.xml.crypto.dsig.XMLSignature.XMLNS,"Signature");
        if(signatures.getLength()==0)return "MISSING";
        try {
            var factory=javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM");
            for(int i=0;i<signatures.getLength();i++){
                var context=new javax.xml.crypto.dsig.dom.DOMValidateContext(new javax.xml.crypto.KeySelector(){
                    public javax.xml.crypto.KeySelectorResult select(javax.xml.crypto.dsig.keyinfo.KeyInfo keyInfo,Purpose purpose,javax.xml.crypto.AlgorithmMethod method,javax.xml.crypto.XMLCryptoContext context){return () -> null;}
                },signatures.item(i));
                factory.unmarshalXMLSignature(context); // Structure only; deliberately no validate() or trust claim.
            }
            return "PRESENT_UNVERIFIED";
        }catch(Exception e){return "INVALID_STRUCTURE";}
    }
}
