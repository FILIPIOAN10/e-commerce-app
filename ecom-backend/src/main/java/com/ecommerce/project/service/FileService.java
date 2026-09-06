package com.ecommerce.project.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {
    String uploadImage(String path, MultipartFile file) throws IOException;

    void deleteImage(String path, String imageName) throws IOException;

    /**
     * Read a stored file back as bytes. {@code storedName} is whatever
     * {@link #uploadImage} returned for it — a bare filename for the local
     * provider, an absolute URL for S3. For files that are not publicly served
     * (dispute evidence), this is the only way back to the content.
     */
    byte[] read(String path, String storedName) throws IOException;
}
