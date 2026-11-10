package com.ecommerce.project.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileService.safeExtension")
class FileServiceSafeExtensionTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(value = {
            "receipt.pdf        | .pdf",
            "RECEIPT.PDF        | .pdf",
            "photo.final.jpeg   | .jpeg",
            "no-dot             | ''",
            "trailing.          | ''",
            "x.toolongext       | ''",
            "evil.p/df          | ''",
            "../../etc/passwd   | ''",
            "a.b%00c            | ''",
    }, delimiter = '|')
    void keepsOnlyAShortAlphanumericExtension(String name, String expected) {
        assertThat(FileService.safeExtension(name)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void nullNameHasNoExtension() {
        assertThat(FileService.safeExtension(null)).isEmpty();
    }
}
