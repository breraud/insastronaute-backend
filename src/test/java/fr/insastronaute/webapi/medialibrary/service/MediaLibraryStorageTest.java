package fr.insastronaute.webapi.medialibrary.service;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.common.web.StorageUnavailableException;
import fr.insastronaute.webapi.proof.service.ProofStorageService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaLibraryStorageTest {

    private final ProofStorageService proofStorage = mock(ProofStorageService.class);
    private final MediaLibraryStorage storage = new MediaLibraryStorage(proofStorage);

    @Test
    void aMissingObjectIsNotFoundAndAFailingStoreIsUnavailable() {
        when(proofStorage.openProofMedia("gone", null))
                .thenThrow((Throwable) NoSuchKeyException.builder().statusCode(404).message("absent").build());
        when(proofStorage.openProofMedia("broken", null))
                .thenThrow((Throwable) S3Exception.builder().statusCode(500).message("panne").build());

        assertThatThrownBy(() -> storage.open("gone", null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> storage.open("broken", null))
                .isInstanceOf(StorageUnavailableException.class)
                .hasMessageNotContaining("broken");
        assertThat(storage.openIfPresent("gone")).isEmpty();
    }

    @Test
    void anUnknownSizeIsZero() {
        when(proofStorage.proofObjectSize(any()))
                .thenThrow((Throwable) NoSuchKeyException.builder().statusCode(404).message("absent").build());

        assertThat(storage.sizeOrZero("gone")).isZero();
    }
}
