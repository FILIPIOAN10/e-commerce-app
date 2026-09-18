package com.ecommerce.project.service.impl;

import com.ecommerce.project.service.FileService;
import com.ecommerce.project.service.media.ImageUploadValidator;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final ImageUploadValidator imageValidator;

    @Autowired
    public FileServiceImpl(ImageUploadValidator imageValidator) {
        this.imageValidator = imageValidator;
    }

    /**
     * Every image upload the app takes on the product path lands here. The old
     * implementation read {@code file.getOriginalFilename()} and copied the
     * stream to disk with no content check, so an admin (or anyone with that
     * permission) could upload {@code xss.svg} carrying inline JavaScript and
     * the server would serve it back from {@code /images/**}, executing it in
     * the API origin against authenticated cookies. The old code also called
     * {@code substring(lastIndexOf('.'))} without checking the {@code -1}
     * return, turning a filename without a dot into a 500. Both are handled by
     * {@link ImageUploadValidator#validate} now — extension whitelist plus a
     * magic-byte check, and a defensive extension parser.
     */
    @Override
    public String uploadImage(String path, MultipartFile file) throws IOException {
        ImageUploadValidator.Validated validated = imageValidator.validate(file);

        String fileName = UUID.randomUUID() + validated.extension();
        String filePath = path + File.separator + fileName;

        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdir();
        }

        Files.write(Paths.get(filePath), validated.bytes());
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
