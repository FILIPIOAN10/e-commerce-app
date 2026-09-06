package com.ecommerce.project.service.impl;

import com.ecommerce.project.service.FileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;


@Service
@ConditionalOnProperty(name = "file.storage.provider", havingValue = "local", matchIfMissing = true)
public class FileServiceImpl implements FileService {






    @Override
    public String uploadImage(String path, MultipartFile file) throws IOException {

        // File names of current / original file
        String originalFilename = file.getOriginalFilename();

        // Generate a unique file name
        String randomId = UUID.randomUUID().toString();
        // fileName is -> mat.jpg
        // UUID is  1234
        // fileName ->1234.jpg
        String fileName = randomId.concat(originalFilename.substring(originalFilename.lastIndexOf('.')));
        String filePath = path + File.separator + fileName;

        // Check if path exist and create
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdir();
        }

        // Upload to server
        Files.copy(file.getInputStream(), Paths.get(filePath));

        // returning file name
        return fileName;
    }

    @Override
    public void deleteImage(String path, String imageName) throws IOException {
        if (imageName == null || imageName.isBlank() || imageName.startsWith("http://") || imageName.startsWith("https://")) {
            return;
        }
        File file = new File(path + File.separator + imageName);
        if (file.exists() && !file.delete()) {
            throw new IOException("Failed to delete image: " + file.getAbsolutePath());
        }
    }

    @Override
    public byte[] read(String path, String storedName) throws IOException {
        if (storedName == null || storedName.isBlank()) {
            throw new IOException("No file name given");
        }
        // Guard against a stored name that tries to climb out of the directory.
        String name = Paths.get(storedName).getFileName().toString();
        return Files.readAllBytes(Paths.get(path, name));
    }
}
