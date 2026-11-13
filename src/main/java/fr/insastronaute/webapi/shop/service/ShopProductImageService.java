package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.shop.domain.ShopProduct;
import fr.insastronaute.webapi.shop.domain.ShopProductPhoto;
import fr.insastronaute.webapi.shop.dto.ShopProductPhotoDto;
import fr.insastronaute.webapi.shop.repository.ShopProductPhotoRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductRepository;
import fr.insastronaute.webapi.storage.S3Properties;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ShopProductImageService {
    private static final long MAX_SIZE = 15L * 1024L * 1024L;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Tika TIKA = new Tika();
    private final ShopProductRepository productRepository; private final ShopProductPhotoRepository photoRepository; private final S3Client s3Client; private final S3Properties s3Properties;
    public ShopProductImageService(ShopProductRepository productRepository, ShopProductPhotoRepository photoRepository, S3Client s3Client, S3Properties s3Properties) { this.productRepository = productRepository; this.photoRepository = photoRepository; this.s3Client = s3Client; this.s3Properties = s3Properties; }
    @Transactional public ShopProductPhotoDto upload(UUID productId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) throw new BadRequestException("Format d'image non autorisé.");
        byte[] bytes;
        String contentType;
        try {
            bytes = file.getBytes();
            contentType = TIKA.detect(bytes);
        } catch (IOException exception) {
            throw new BadRequestException("Impossible de lire l'image.");
        }
        if (!TYPES.contains(contentType)) throw new BadRequestException("Format d'image non autorisé.");
        ShopProduct product = productRepository.findById(productId).orElseThrow(() -> new NotFoundException("Produit introuvable."));
        String extension = switch (contentType) { case "image/jpeg" -> "jpg"; case "image/png" -> "png"; default -> "webp"; };
        String key = "shop/products/" + productId + "/" + UUID.randomUUID() + "." + extension;
        String url = publicUrl(key);
        try { s3Client.putObject(PutObjectRequest.builder().bucket(s3Properties.getPublicBucket()).key(key).contentType(contentType).acl(ObjectCannedACL.PUBLIC_READ).build(), RequestBody.fromBytes(bytes)); }
        catch (RuntimeException exception) { throw new BadRequestException("Impossible d'enregistrer l'image."); }
        ShopProductPhoto photo = new ShopProductPhoto(); photo.setProduct(product); photo.setObjectKey(key); photo.setUrl(url); photo.setDisplayOrder(photoRepository.findByProductIdOrderByDisplayOrderAsc(productId).size());
        ShopProductPhoto saved = photoRepository.save(photo); return new ShopProductPhotoDto(saved.getId(), saved.getUrl(), saved.getDisplayOrder());
    }
    @Transactional
    public List<ShopProductPhotoDto> reorder(UUID productId, List<UUID> photoIds) {
        if (!productRepository.existsById(productId)) throw new NotFoundException("Produit introuvable.");
        List<ShopProductPhoto> photos = photoRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        if (photoIds == null || photoIds.size() != photos.size() || new HashSet<>(photoIds).size() != photoIds.size()) throw new BadRequestException("La liste des photos est invalide.");
        java.util.Map<UUID, ShopProductPhoto> byId = photos.stream().collect(java.util.stream.Collectors.toMap(ShopProductPhoto::getId, photo -> photo));
        if (!byId.keySet().equals(new HashSet<>(photoIds))) throw new BadRequestException("La liste des photos est invalide.");
        int temporaryOffset = photos.size() + 1;
        for (int index = 0; index < photoIds.size(); index++) byId.get(photoIds.get(index)).setDisplayOrder(temporaryOffset + index);
        photoRepository.saveAllAndFlush(photos);
        for (int index = 0; index < photoIds.size(); index++) byId.get(photoIds.get(index)).setDisplayOrder(index);
        photoRepository.saveAll(photos);
        return photoIds.stream().map(byId::get).map(this::toDto).toList();
    }
    @Transactional
    public void delete(UUID productId, UUID photoId) {
        ShopProductPhoto photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null) return;
        if (!photo.getProduct().getId().equals(productId)) throw new NotFoundException("Photo introuvable.");
        deleteObjectQuietly(photo.getObjectKey());
        photoRepository.delete(photo);
        List<ShopProductPhoto> remaining = photoRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        for (int index = 0; index < remaining.size(); index++) remaining.get(index).setDisplayOrder(index);
        photoRepository.saveAll(remaining);
    }
    @Transactional(readOnly = true)
    public void deleteProductImagesQuietly(UUID productId) {
        photoRepository.findByProductIdOrderByDisplayOrderAsc(productId).forEach(photo -> deleteObjectQuietly(photo.getObjectKey()));
    }
    private void deleteObjectQuietly(String key) {
        if (key == null || key.isBlank()) return;
        try { s3Client.deleteObject(DeleteObjectRequest.builder().bucket(s3Properties.getPublicBucket()).key(key).build()); }
        catch (RuntimeException ignored) { }
    }
    private ShopProductPhotoDto toDto(ShopProductPhoto photo) { return new ShopProductPhotoDto(photo.getId(), photo.getUrl(), photo.getDisplayOrder()); }
    private String publicUrl(String key) {
        String endpoint = s3Properties.getPublicEndpoint();
        if (endpoint == null || endpoint.isBlank()) endpoint = s3Properties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) throw new BadRequestException("Le point d'accès public S3 est requis pour publier une image de produit.");
        String baseUrl = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(baseUrl + "/" + s3Properties.getPublicBucket() + "/" + key).toString();
    }
}
