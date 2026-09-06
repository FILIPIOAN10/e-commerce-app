package com.ecommerce.project.service.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSignatureTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 1, 2, 3, 4};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 9, 9, 9};
    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0};
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 4, 4, 4, 4, 0x57, 0x45, 0x42, 0x50};
    private static final byte[] RIFF_NOT_WEBP = {0x52, 0x49, 0x46, 0x46, 4, 4, 4, 4, 0x41, 0x56, 0x49, 0x20};

    @Test
    void acceptsEachFormatWhenTheExtensionMatchesTheBytes() {
        assertThat(ImageSignature.contentMatchesExtension(JPEG, ".jpg")).isTrue();
        assertThat(ImageSignature.contentMatchesExtension(JPEG, ".jpeg")).isTrue();
        assertThat(ImageSignature.contentMatchesExtension(PNG, ".png")).isTrue();
        assertThat(ImageSignature.contentMatchesExtension(GIF, ".gif")).isTrue();
        assertThat(ImageSignature.contentMatchesExtension(WEBP, ".webp")).isTrue();
    }

    @Test
    void rejectsAContentTypeMismatch() {
        assertThat(ImageSignature.contentMatchesExtension(PNG, ".jpg")).isFalse();
        assertThat(ImageSignature.contentMatchesExtension(JPEG, ".png")).isFalse();
    }

    @Test
    void rejectsARiffContainerThatIsNotWebp() {
        assertThat(ImageSignature.contentMatchesExtension(RIFF_NOT_WEBP, ".webp")).isFalse();
    }

    @Test
    void rejectsUnknownExtensions() {
        assertThat(ImageSignature.contentMatchesExtension(JPEG, ".bmp")).isFalse();
        assertThat(ImageSignature.contentMatchesExtension(JPEG, ".svg")).isFalse();
    }

    @Test
    void rejectsTruncatedOrNullInput() {
        assertThat(ImageSignature.contentMatchesExtension(null, ".png")).isFalse();
        assertThat(ImageSignature.contentMatchesExtension(new byte[]{(byte) 0x89, 0x50}, ".png")).isFalse();
    }
}
