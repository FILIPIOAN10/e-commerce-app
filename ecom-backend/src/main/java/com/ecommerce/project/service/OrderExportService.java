package com.ecommerce.project.service;

public interface OrderExportService {

    byte[] exportOrdersToCsv();

    byte[] exportOrdersToPdf();
}
