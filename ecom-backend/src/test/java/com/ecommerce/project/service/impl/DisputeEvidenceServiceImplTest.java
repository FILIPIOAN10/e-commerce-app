package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Dispute;
import com.ecommerce.project.model.DisputeEvidenceFile;
import com.ecommerce.project.payload.DisputeEvidenceFileDTO;
import com.ecommerce.project.repository.DisputeEvidenceFileRepository;
import com.ecommerce.project.repository.DisputeRepository;
import com.ecommerce.project.service.DisputeEvidenceService;
import com.ecommerce.project.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeEvidenceServiceImplTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private DisputeEvidenceFileRepository evidenceFileRepository;
    @Mock private FileService fileService;

    @InjectMocks private DisputeEvidenceServiceImpl service;

    private Dispute openDispute;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "evidenceDir", "./target/test-evidence");
        ReflectionTestUtils.setField(service, "maxBytes", 1024L);

        openDispute = Dispute.openedFrom("dp_1", "pi_1", "ch_1", 9L,
                new BigDecimal("50.00"), "USD", "fraudulent", "needs_response", null);
        openDispute.setId(1L);
        when(disputeRepository.findById(1L)).thenReturn(Optional.of(openDispute));
        when(evidenceFileRepository.save(any(DisputeEvidenceFile.class))).thenAnswer(inv -> {
            DisputeEvidenceFile f = inv.getArgument(0);
            f.setId(7L);
            return f;
        });
    }

    @Test
    void attach_storesTheFileAndRecordsMetadata() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "receipt.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(fileService.uploadImage(eq("./target/test-evidence"), any())).thenReturn("stored-uuid.pdf");

        DisputeEvidenceFileDTO dto = service.attach(1L, pdf, "admin@example.com");

        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.originalName()).isEqualTo("receipt.pdf");
        assertThat(dto.uploadedBy()).isEqualTo("admin@example.com");
        verify(evidenceFileRepository).save(any(DisputeEvidenceFile.class));
    }

    @Test
    void attach_rejectsAnUnsupportedType() {
        MockMultipartFile exe = new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> service.attach(1L, exe, "admin@example.com"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void attach_rejectsAFileOverTheSizeLimit() {
        MockMultipartFile big = new MockMultipartFile("file", "big.pdf", "application/pdf", new byte[2048]);

        assertThatThrownBy(() -> service.attach(1L, big, "admin@example.com"))
                .isInstanceOf(APIException.class);
    }

    @Test
    void attach_refusesEvidenceOnAClosedDispute() throws Exception {
        Dispute won = Dispute.openedFrom("dp_2", "pi_2", "ch_2", 9L,
                new BigDecimal("50.00"), "USD", "fraudulent", "won", null);
        won.setId(2L);
        when(disputeRepository.findById(2L)).thenReturn(Optional.of(won));
        MockMultipartFile pdf = new MockMultipartFile("file", "r.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.attach(2L, pdf, "admin@example.com"))
                .isInstanceOf(APIException.class);
        verify(fileService, never()).uploadImage(any(), any());
    }

    @Test
    void download_returnsTheBytesForAFileThatBelongsToTheDispute() throws Exception {
        DisputeEvidenceFile meta = DisputeEvidenceFile.of(1L, "stored.pdf", "receipt.pdf",
                "application/pdf", 3, "admin@example.com");
        meta.setId(7L);
        when(evidenceFileRepository.findById(7L)).thenReturn(Optional.of(meta));
        when(fileService.read("./target/test-evidence", "stored.pdf")).thenReturn(new byte[]{9, 8, 7});

        DisputeEvidenceService.EvidenceDownload dl = service.download(1L, 7L);

        assertThat(dl.filename()).isEqualTo("receipt.pdf");
        assertThat(dl.contentType()).isEqualTo("application/pdf");
        assertThat(dl.bytes()).containsExactly(9, 8, 7);
    }

    @Test
    void download_refusesAFileFromAnotherDispute() {
        DisputeEvidenceFile meta = DisputeEvidenceFile.of(999L, "stored.pdf", "r.pdf", "application/pdf", 3, "a");
        meta.setId(7L);
        when(evidenceFileRepository.findById(7L)).thenReturn(Optional.of(meta));

        assertThatThrownBy(() -> service.download(1L, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
