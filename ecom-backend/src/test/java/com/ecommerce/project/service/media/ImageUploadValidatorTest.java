package com.ecommerce.project.service.media;

import com.ecommerce.project.exception.APIException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ImageUploadValidator")
class ImageUploadValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 9, 9, 9};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};

    private final ImageUploadValidator validator = new ImageUploadValidator();

    @Test
    @DisplayName("accepts a well-formed PNG and returns its bytes + normalised extension")
    void acceptsWellFormedPng() {
        MultipartFile file = new MockMultipartFile("image", "logo.PNG", "image/png", PNG);

        ImageUploadValidator.Validated result = validator.validate(file);

        assertThat(result.bytes()).isEqualTo(PNG);
        assertThat(result.extension()).as("extension is lowercased").isEqualTo(".png");
    }

    @Test
    @DisplayName("rejects a null filename with 400, not the NPE the old code threw")
    void rejectsNullFilename() {
        MultipartFile file = new MockMultipartFile("image", null, "image/png", PNG);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    @DisplayName("rejects a filename without a dot with 400, not the StringIndexOutOfBoundsException the old code threw")
    void rejectsDotlessFilename() {
        MultipartFile file = new MockMultipartFile("image", "README", "image/png", PNG);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    @DisplayName("rejects .svg — an executable format that the API origin must not serve back")
    void rejectsSvg() {
        MultipartFile file = new MockMultipartFile(
                "image", "malicious.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    @DisplayName("rejects a whitelisted extension whose actual bytes are a different format")
    void rejectsMismatchedContent() {
        // .png declared, but the bytes are actually JPEG. This is the substantive
        // check that stops an attacker renaming a payload to smuggle it past a
        // pure extension filter.
        MultipartFile file = new MockMultipartFile("image", "spoofed.png", "image/png", JPEG);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("does not match the declared image type");
    }

    @Test
    @DisplayName("rejects an empty upload")
    void rejectsEmpty() {
        MultipartFile file = new MockMultipartFile("image", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("No image provided");
    }
}
