package com.ecommerce.project.service;

import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.repository.OrderRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final OrderRepository orderRepository;

    public byte[] generateInvoicePdf(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderId", orderId));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD);
            Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font smallFont = new Font(Font.HELVETICA, 8, Font.NORMAL);

            // Header
            Paragraph title = new Paragraph("EcommerceHub", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph invoiceTitle = new Paragraph("INVOICE #" + order.getId(), headerFont);
            invoiceTitle.setAlignment(Element.ALIGN_CENTER);
            invoiceTitle.setSpacingAfter(10f);
            document.add(invoiceTitle);

            // Invoice meta
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingBefore(10f);
            metaTable.setSpacingAfter(10f);

            addMetaCell(metaTable, "Invoice Date:", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), normalFont);
            addMetaCell(metaTable, "Order Date:", order.getOrderDate() != null ? order.getOrderDate().toString() : "N/A", normalFont);
            addMetaCell(metaTable, "Customer Email:", order.getEmail(), normalFont);
            addMetaCell(metaTable, "Payment Method:", order.getPayment() != null ? order.getPayment().getPaymentMethod() : "N/A", normalFont);
            addMetaCell(metaTable, "Payment Status:", order.getPayment() != null ? order.getPayment().getPgStatus() : "N/A", normalFont);
            addMetaCell(metaTable, "Order Status:", order.getOrderStatus(), normalFont);

            document.add(metaTable);

            // Shipping address
            if (order.getAddress() != null) {
                Address addr = order.getAddress();
                document.add(new Paragraph("Shipping Address:", headerFont));
                document.add(new Paragraph(
                        addr.getStreet() + ", " + addr.getBuildingName() + "\n" +
                        addr.getCity() + ", " + addr.getState() + "\n" +
                        addr.getCountry() + " - " + addr.getPincode(),
                        normalFont));
                document.add(new Paragraph(" "));
            }

            // Items table
            PdfPTable itemTable = new PdfPTable(5);
            itemTable.setWidthPercentage(100);
            itemTable.setSpacingBefore(10f);
            itemTable.setWidths(new float[]{40f, 10f, 15f, 15f, 20f});

            String[] headers = {"Product", "Qty", "Unit Price", "Discount", "Total"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(new Color(240, 240, 240));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5f);
                itemTable.addCell(cell);
            }

            for (OrderItem item : order.getOrderItems()) {
                String productName = item.getProduct() != null ? item.getProduct().getProductName() : "N/A";
                int qty = item.getQuantity();
                double unitPrice = item.getOrderedProductPrice();
                double discount = item.getDiscount();
                double lineTotal = unitPrice * qty;

                itemTable.addCell(new Phrase(productName, normalFont));
                itemTable.addCell(new Phrase(String.valueOf(qty), normalFont));
                itemTable.addCell(new Phrase(String.format("$%.2f", unitPrice), normalFont));
                itemTable.addCell(new Phrase(String.format("%.1f%%", discount), normalFont));
                itemTable.addCell(new Phrase(String.format("$%.2f", lineTotal), normalFont));
            }

            document.add(itemTable);

            // Total
            document.add(new Paragraph(" "));
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(40);
            totalTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalTable.setSpacingBefore(10f);

            PdfPCell totalLabel = new PdfPCell(new Phrase("Total Amount:", headerFont));
            totalLabel.setBackgroundColor(new Color(240, 240, 240));
            totalLabel.setPadding(5f);
            totalTable.addCell(totalLabel);

            PdfPCell totalValue = new PdfPCell(new Phrase(String.format("$%.2f", order.getTotalAmount()), headerFont));
            totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValue.setPadding(5f);
            totalTable.addCell(totalValue);

            document.add(totalTable);

            // Footer
            document.add(new Paragraph(" "));
            Paragraph footer = new Paragraph(
                    "Thank you for your purchase!\n" +
                    "This is a computer-generated invoice and does not require a signature.",
                    smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate invoice for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to generate invoice: " + e.getMessage());
        }
    }

    private void addMetaCell(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBackgroundColor(new Color(245, 245, 245));
        labelCell.setPadding(5f);
        labelCell.setBorderWidth(0.5f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setPadding(5f);
        valueCell.setBorderWidth(0.5f);
        table.addCell(valueCell);
    }
}
