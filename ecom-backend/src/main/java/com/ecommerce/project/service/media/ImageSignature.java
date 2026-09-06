package com.ecommerce.project.service.media;

import java.util.Arrays;
import java.util.Set;

/**
 * The raster formats an avatar upload may declare, each paired with the magic
 * bytes that prove the file really is that format. Replaces a parallel if-else
 * chain and a {@code switch} that re-listed the same signatures: the pairing now
 * lives in exactly one place, and a new format is one enum constant.
 */
public enum ImageSignature {

    JPEG(Set.of(".jpg", ".jpeg"), (byte) 0xFF, (byte) 0xD8, (byte) 0xFF),
    PNG(Set.of(".png"), (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
    GIF(Set.of(".gif"), 0x47, 0x49, 0x46, 0x38),
    /** RIFF container; the {@code WEBP} marker at offset 8 is what distinguishes it. */
    WEBP(Set.of(".webp"), 0x52, 0x49, 0x46, 0x46) {
        @Override
        boolean matches(byte[] bytes) {
            return super.matches(bytes)
                    && bytes.length >= 12
                    && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
        }
    };

    private final Set<String> extensions;
    private final byte[] magic;

    ImageSignature(Set<String> extensions, int... magic) {
        this.extensions = extensions;
        this.magic = new byte[magic.length];
        for (int i = 0; i < magic.length; i++) {
            this.magic[i] = (byte) magic[i];
        }
    }

    boolean matches(byte[] bytes) {
        if (bytes == null || bytes.length < magic.length) {
            return false;
        }
        return Arrays.equals(bytes, 0, magic.length, magic, 0, magic.length);
    }

    /**
     * True when {@code bytes} carry the magic of the format that {@code extension}
     * names — i.e. the declared type and the actual content agree. An unknown
     * extension is rejected.
     */
    public static boolean contentMatchesExtension(byte[] bytes, String extension) {
        for (ImageSignature signature : values()) {
            if (signature.extensions.contains(extension)) {
                return signature.matches(bytes);
            }
        }
        return false;
    }
}
