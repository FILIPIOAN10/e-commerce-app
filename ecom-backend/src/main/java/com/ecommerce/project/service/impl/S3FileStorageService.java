package com.ecommerce.project.service.impl;

import com.ecommerce.project.service.FileService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file.storage.provider", havingValue = "s3")
public class S3FileStorageService implements FileService {

    private final MinioClient minioClient;

    @Value("${minio.bucket:images}")
    private String bucketName;

    @Value("${minio.public.url:${minio.url}}")
    private String publicUrl;

    @Override
    public String uploadImage(String path, MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        String folder = path != null && path.endsWith("/") ? path.substring(0, path.length() - 1) : (path != null ? path : "uploads");
        String objectName = folder + "/" + UUID.randomUUID() + extension;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), null)
                            .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                            .build()
            );
        } catch (MinioException e) {
            log.error("Failed to upload image to S3/MinIO: {}", e.getMessage());
            throw new IOException("Image upload failed", e);
        }

        return publicUrl + "/" + bucketName + "/" + objectName;
    }

    @Override
    public void deleteImage(String path, String imageName) throws IOException {
        if (imageName == null || imageName.isBlank()) {
            return;
        }
        String publicUrlPrefix = publicUrl + "/" + bucketName + "/";
        String objectName;
        if (imageName.startsWith(publicUrlPrefix)) {
            objectName = imageName.substring(publicUrlPrefix.length());
        } else if (imageName.startsWith("http://") || imageName.startsWith("https://")) {
            return;
        } else {
            String folder = path != null && path.endsWith("/") ? path.substring(0, path.length() - 1)
                    : (path != null ? path : "uploads");
            objectName = folder + "/" + imageName;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (MinioException e) {
            log.error("Failed to delete image from S3/MinIO: {}", e.getMessage());
            throw new IOException("Image deletion failed", e);
        }
    }
}
