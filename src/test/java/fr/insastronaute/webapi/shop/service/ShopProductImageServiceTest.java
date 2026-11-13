package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.shop.repository.ShopProductPhotoRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductRepository;
import fr.insastronaute.webapi.shop.domain.ShopProduct;
import fr.insastronaute.webapi.shop.domain.ShopProductPhoto;
import fr.insastronaute.webapi.shop.dto.ShopProductPhotoDto;
import fr.insastronaute.webapi.storage.S3Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ShopProductImageServiceTest {
    @Mock private ShopProductRepository productRepository;
    @Mock private ShopProductPhotoRepository photoRepository;
    @Mock private S3Client s3Client;
    @Mock private S3Properties s3Properties;

    @Test
    void uploadRejectsNonImageFiles() {
        ShopProductImageService service = new ShopProductImageService(productRepository, photoRepository, s3Client, s3Properties);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Format d'image non autorisé.");
    }

    @Test
    void uploadRejectsFileWithoutContentType() {
        ShopProductImageService service = new ShopProductImageService(productRepository, photoRepository, s3Client, s3Properties);
        MockMultipartFile file = new MockMultipartFile("file", "image", null, new byte[] {1});

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Format d'image non autorisé.");
    }

    @Test
    void uploadStoresPhotoInPublicS3BucketAndReturnsItsPublicUrl() {
        UUID productId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        ShopProduct product = new ShopProduct();
        product.setId(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(photoRepository.findByProductIdOrderByDisplayOrderAsc(productId)).thenReturn(java.util.List.of());
        when(photoRepository.save(any(ShopProductPhoto.class))).thenAnswer(invocation -> {
            ShopProductPhoto photo = invocation.getArgument(0);
            photo.setId(photoId);
            return photo;
        });
        when(s3Properties.getPublicBucket()).thenReturn("public");
        when(s3Properties.getPublicEndpoint()).thenReturn("https://s3.example.test");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes());

        ShopProductPhotoDto result = new ShopProductImageService(productRepository, photoRepository, s3Client, s3Properties)
                .upload(productId, file);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("public");
        assertThat(request.key()).startsWith("shop/products/" + productId + "/").endsWith(".png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.acl()).isEqualTo(ObjectCannedACL.PUBLIC_READ);
        assertThat(result.id()).isEqualTo(photoId);
        assertThat(result.url()).isEqualTo("https://s3.example.test/public/" + request.key());
    }

    @Test
    void uploadDetectsAValidImageWhenTheBrowserUsesAGenericContentType() {
        UUID productId = UUID.randomUUID();
        ShopProduct product = new ShopProduct();
        product.setId(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(photoRepository.findByProductIdOrderByDisplayOrderAsc(productId)).thenReturn(java.util.List.of());
        when(photoRepository.save(any(ShopProductPhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(s3Properties.getPublicBucket()).thenReturn("public");
        when(s3Properties.getPublicEndpoint()).thenReturn("https://s3.example.test");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "application/octet-stream", pngBytes());

        new ShopProductImageService(productRepository, photoRepository, s3Client, s3Properties)
                .upload(productId, file);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("image/png");
        assertThat(requestCaptor.getValue().key()).endsWith(".png");
    }

    @Test
    void deleteRemovesPhotoFromStorageAndDatabase() {
        UUID productId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        ShopProduct product = new ShopProduct();
        product.setId(productId);
        ShopProductPhoto photo = new ShopProductPhoto();
        photo.setId(photoId);
        photo.setProduct(product);
        photo.setObjectKey("shop/products/" + productId + "/photo.png");
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(s3Properties.getPublicBucket()).thenReturn("public");

        new ShopProductImageService(productRepository, photoRepository, s3Client, s3Properties).delete(productId, photoId);

        verify(s3Client).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
        verify(photoRepository).delete(photo);
    }

    @Test
    void deletingAnAlreadyMissingPhotoIsIdempotent() {
        UUID productId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        when(photoRepository.findById(photoId)).thenReturn(Optional.empty());

        ShopProductImageService service = new ShopProductImageService(productRepository, photoRepository, s3Client, s3Properties);

        assertThatCode(() -> service.delete(productId, photoId)).doesNotThrowAnyException();
    }

    @Test
    void uploadRejectsMissingPublicEndpoint() {
        UUID productId = UUID.randomUUID();
        ShopProduct product = new ShopProduct();
        product.setId(productId);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", pngBytes());

        ShopProductImageService service = new ShopProductImageService(productRepository, photoRepository, s3Client, s3Properties);

        assertThatThrownBy(() -> service.upload(productId, file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le point d'accès public S3 est requis pour publier une image de produit.");
    }

    private static byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52
        };
    }
}
