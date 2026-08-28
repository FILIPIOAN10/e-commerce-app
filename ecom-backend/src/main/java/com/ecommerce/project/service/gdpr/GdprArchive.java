package com.ecommerce.project.service.gdpr;

/**
 * A built export, ready to stream back to the customer.
 *
 * @param fileName what the browser should save it as
 * @param content  the ZIP bytes
 */
public record GdprArchive(String fileName, byte[] content) {
}
