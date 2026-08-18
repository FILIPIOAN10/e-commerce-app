package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Order;
import com.ecommerce.project.repository.OrderRepository;
import com.ecommerce.project.service.OrderExportService;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class OrderExportServiceImpl implements OrderExportService {

    private final OrderRepository orderRepository;

    public OrderExportServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public byte[] exportOrdersToCsv() {
        List<Order> orders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderDate"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos));

        writer.println("Order ID,Email,Order Date,Total Amount,Status,Payment Method");

        for (Order order : orders) {
            String paymentMethod = order.getPayment() != null ? order.getPayment().getPaymentMethod() : "N/A";
            writer.printf("%d,%s,%s,%.2f,%s,%s%n",
                    order.getId(),
                    escapeCsv(order.getEmail()),
                    order.getOrderDate(),
                    order.getTotalAmount(),
                    order.getOrderStatus(),
                    escapeCsv(paymentMethod));
        }

        writer.flush();
        return baos.toByteArray();
    }

    @Override
    public byte[] exportOrdersToPdf() {
        List<Order> orders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderDate"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, baos);
            document.open();

            document.add(new Paragraph("Orders Report"));
            document.add(new Paragraph("Generated: " + LocalDate.now()));
            document.add(new Paragraph("Total Orders: " + orders.size()));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);

            String[] headers = {"Order ID", "Email", "Date", "Total ($)", "Status", "Payment"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header));
                cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
                table.addCell(cell);
            }

            for (Order order : orders) {
                table.addCell(String.valueOf(order.getId()));
                table.addCell(order.getEmail() != null ? order.getEmail() : "");
                table.addCell(order.getOrderDate() != null ? order.getOrderDate().toString() : "");
                table.addCell(String.format("%.2f", order.getTotalAmount()));
                table.addCell(order.getOrderStatus() != null ? order.getOrderStatus() : "");
                table.addCell(order.getPayment() != null && order.getPayment().getPaymentMethod() != null
                        ? order.getPayment().getPaymentMethod() : "N/A");
            }

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (Exception e) {
            throw new APIException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
