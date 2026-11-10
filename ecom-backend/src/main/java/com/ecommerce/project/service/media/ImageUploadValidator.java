package com.ecommerce.project.service.media;

import com.ecommerce.project.exception.APIException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Validates and normalises an image upload before anything writes it to disk or
 * saves a reference to it. Two callers historically diverged: the avatar path
 * checked extension + magic bytes; the product-image path went straight into
 * {@code FileServiceImpl.uploadImage}, which read the multipart's declared
 * filename and never looked at what was inside — a caller could upload
 * {@code xss.svg} carrying inline JavaScript and the API would serve it back
 * from {@code /images/**}, same origin as authenticated calls.
 *
 * <p>The same class also fixes the second half of the old
 * {@code FileServiceImpl}: {@code originalFilename.substring(lastIndexOf('.'))}
 * NPE'd on a null filename and threw {@code StringIndexOutOfBoundsException} on
 * a dotless one, turning bad uploads into 500s with stack traces. Extension is
 * derived here defensively.
 */
@Component
public class ImageUploadValidator {

    /**
     * Raster formats the app accepts. Kept in sync with the {@link ImageSignature}
     * enum below: adding a format is one entry here and one enum constant.
     */
    private static final List<String> ALLOWED_EXTENSIONS =
            List.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    /**
     * A validated upload: bytes read once, extension normalised to the lowercase
     * dot form ({@code .png}). Callers use both — bytes go to disk, extension
     * goes into the stored filename.
     */
    public record Validated(byte[] bytes, String extension) {}

    public Validated validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new APIException("No image provided.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new APIException("Invalid file type. Allowed: jpg, jpeg, png, gif, webp.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            // Real I/O failure reading the multipart part — not the caller's
            // fault, but not a 500 either: they'll retry.
            throw new APIException("Could not read the uploaded file. Please try again.");
        }

        if (!ImageSignature.contentMatchesExtension(bytes, extension)) {
            throw new APIException(
                    "Invalid file content. The file does not match the declared image type.");
        }

        return new Validated(bytes, extension);
    }

    /**
     * Returns the lowercase dot-prefixed extension, or {@code ""} when the
     * filename is missing or dotless. The original code called
     * {@code lastIndexOf('.')} without checking its {@code -1} return and NPE'd
     * on a null filename — this is the fix.
     */
    private static String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return originalFilename.substring(dot).toLowerCase(Locale.ROOT);
    }
}
