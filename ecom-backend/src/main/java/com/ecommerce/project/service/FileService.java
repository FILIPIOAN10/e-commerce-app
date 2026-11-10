package com.ecommerce.project.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {
    String uploadImage(String path, MultipartFile file) throws IOException;

    /**
     * Stores a non-image file under a random name and returns that name (or,
     * for S3, its URL), exactly like {@link #uploadImage} but WITHOUT the
     * image-only validation. For files the caller has already validated and
     * that are never served from {@code /images/**} — dispute evidence (PDF,
     * PNG, JPEG), read back only through {@link #read}. Only a short
     * alphanumeric extension of the original name survives.
     */
    String storeFile(String path, MultipartFile file) throws IOException;

    /** ".pdf" for "receipt.PDF"; "" for no dot, a long or non-alphanumeric suffix. */
    static String safeExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return ext.matches("[a-z0-9]{1,5}") ? "." + ext : "";
    }

    void deleteImage(String path, String imageName) throws IOException;

    /**
     * Read a stored file back as bytes. {@code storedName} is whatever
     * {@link #uploadImage} returned for it — a bare filename for the local
     * provider, an absolute URL for S3. For files that are not publicly served
     * (dispute evidence), this is the only way back to the content.
     */
    byte[] read(String path, String storedName) throws IOException;
}
