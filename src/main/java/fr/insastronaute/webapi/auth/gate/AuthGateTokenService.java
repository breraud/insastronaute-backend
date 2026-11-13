package fr.insastronaute.webapi.auth.gate;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class AuthGateTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final AuthGateProperties properties;

    public AuthGateTokenService(AuthGateProperties properties) {
        this.properties = properties;
    }

    public String generateToken(String ticketId, String redirectUri, Instant expiresAt) {
        String cleanRedirect = redirectUri == null ? "" : redirectUri;
        String payload = ticketId + "|" + cleanRedirect + "|" + expiresAt.toEpochMilli();
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString((payload + "|" + signature).getBytes(StandardCharsets.UTF_8));
    }

    public Optional<ValidatedGateToken> validateToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 4) {
                return Optional.empty();
            }
            String ticketId = parts[0];
            String redirectUri = parts[1];
            long epochMilli = Long.parseLong(parts[2]);
            String expectedSignature = parts[3];

            String payload = ticketId + "|" + redirectUri + "|" + epochMilli;
            String calculatedSignature = sign(payload);

            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), calculatedSignature.getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }

            Instant expiresAt = Instant.ofEpochMilli(epochMilli);
            if (expiresAt.isBefore(Instant.now())) {
                return Optional.empty();
            }

            return Optional.of(new ValidatedGateToken(ticketId, redirectUri, expiresAt));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    public record ValidatedGateToken(String ticketId, String redirectUri, Instant expiresAt) {}
}
