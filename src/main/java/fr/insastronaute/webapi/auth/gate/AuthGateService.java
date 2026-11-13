package fr.insastronaute.webapi.auth.gate;

import fr.insastronaute.webapi.auth.gate.dto.GateEnterResponse;
import fr.insastronaute.webapi.auth.gate.dto.GateStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthGateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthGateService.class);
    private static final String QUEUE_KEY = "auth:gate:queue";
    private static final String RATE_BUCKET_KEY_PREFIX = "auth:gate:rate:";
    private static final String TICKET_ADMITTED_PREFIX = "auth:gate:admitted:";

    private final StringRedisTemplate redisTemplate;
    private final AuthGateTokenService tokenService;
    private final AuthGateProperties properties;

    public AuthGateService(StringRedisTemplate redisTemplate, AuthGateTokenService tokenService, AuthGateProperties properties) {
        this.redisTemplate = redisTemplate;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    /**
     * Efface les tickets que plus personne ne réclamera.
     *
     * <p>Un ticket n'entrait dans la file que par une admission. Qui fermait son
     * onglet en salle d'attente y laissait le sien pour toujours, et son cookie
     * expirait au bout de dix minutes sans que l'entrée Redis ne bouge.
     *
     * <p>Le compte affiché mesurait donc l'histoire du serveur, pas ses visiteurs —
     * cinq cents personnes annoncées pour un processeur à deux pour cent.
     *
     * <p>Et l'accumulation s'entretenait toute seule : le chemin rapide de
     * {@link #enter} est contourné dès que la file n'est pas vide, si bien qu'un seul
     * ticket abandonné mettait tous les suivants en attente, dont une part
     * abandonnait à son tour. La file ne pouvait plus jamais redescendre à zéro.
     *
     * <p>Le score d'un membre est l'horodatage de son entrée : la purge est donc une
     * seule opération, et {@code ticketTtl} — déclaré depuis le début, jamais lu —
     * retrouve le sens qu'on lui avait donné.
     */
    private void purgeExpiredTickets() {
        long cutoff = Instant.now().minus(properties.getTicketTtl()).toEpochMilli();
        redisTemplate.opsForZSet().removeRangeByScore(QUEUE_KEY, Double.NEGATIVE_INFINITY, (double) cutoff);
    }

    public GateEnterResponse enter(String existingTicketId, String redirectUri) {
        if (!properties.isEnabled()) {
            String token = tokenService.generateToken(UUID.randomUUID().toString(), redirectUri, Instant.now().plus(properties.getTokenTtl()));
            return new GateEnterResponse(GateStatus.ADMITTED, null, token, buildKeycloakAuthorizeUrl(token, redirectUri), 0, 0L, 0L);
        }

        // If ticket already exists and was admitted:
        if (existingTicketId != null && !existingTicketId.isBlank()) {
            String existingToken = redisTemplate.opsForValue().get(TICKET_ADMITTED_PREFIX + existingTicketId);
            if (existingToken != null) {
                return new GateEnterResponse(GateStatus.ADMITTED, existingTicketId, existingToken, buildKeycloakAuthorizeUrl(existingToken, redirectUri), 0, 0L, 0L);
            }
        }

        purgeExpiredTickets();
        Long queueSize = redisTemplate.opsForZSet().size(QUEUE_KEY);
        if (queueSize == null || queueSize == 0L) {
            long currentSecond = Instant.now().getEpochSecond();
            String bucketKey = RATE_BUCKET_KEY_PREFIX + currentSecond;
            Long admissionsThisSecond = redisTemplate.opsForValue().increment(bucketKey);
            if (admissionsThisSecond != null && admissionsThisSecond == 1L) {
                redisTemplate.expire(bucketKey, Duration.ofSeconds(5));
            }

            if (admissionsThisSecond != null && admissionsThisSecond <= properties.getAdmissionsPerSecond()) {
                String ticketId = UUID.randomUUID().toString();
                String token = tokenService.generateToken(ticketId, redirectUri, Instant.now().plus(properties.getTokenTtl()));
                redisTemplate.opsForValue().set(TICKET_ADMITTED_PREFIX + ticketId, token, properties.getTokenTtl());
                return new GateEnterResponse(GateStatus.ADMITTED, ticketId, token, buildKeycloakAuthorizeUrl(token, redirectUri), 0, 0L, 0L);
            }
        }

        // Must join or stay in queue
        String ticketId = (existingTicketId != null && !existingTicketId.isBlank()) ? existingTicketId : UUID.randomUUID().toString();
        redisTemplate.opsForZSet().add(QUEUE_KEY, ticketId, (double) Instant.now().toEpochMilli());

        return calculateQueuePosition(ticketId, redirectUri);
    }

    public GateStatusResponse getStatus(String ticketId, String redirectUri) {
        if (!properties.isEnabled() || ticketId == null || ticketId.isBlank()) {
            String token = tokenService.generateToken(UUID.randomUUID().toString(), redirectUri, Instant.now().plus(properties.getTokenTtl()));
            return new GateStatusResponse(GateStatus.ADMITTED, ticketId, token, buildKeycloakAuthorizeUrl(token, redirectUri), 0, 0L, 0L);
        }

        String admittedToken = redisTemplate.opsForValue().get(TICKET_ADMITTED_PREFIX + ticketId);
        if (admittedToken != null) {
            return new GateStatusResponse(GateStatus.ADMITTED, ticketId, admittedToken, buildKeycloakAuthorizeUrl(admittedToken, redirectUri), 0, 0L, 0L);
        }

        // Process admissions from queue:
        drainQueueAdmissions(redirectUri);

        admittedToken = redisTemplate.opsForValue().get(TICKET_ADMITTED_PREFIX + ticketId);
        if (admittedToken != null) {
            return new GateStatusResponse(GateStatus.ADMITTED, ticketId, admittedToken, buildKeycloakAuthorizeUrl(admittedToken, redirectUri), 0, 0L, 0L);
        }

        GateEnterResponse position = calculateQueuePosition(ticketId, redirectUri);
        return new GateStatusResponse(position.status(), ticketId, null, null, position.position(), position.totalInQueue(), position.estimatedWaitSeconds());
    }

    private synchronized void drainQueueAdmissions(String redirectUri) {
        purgeExpiredTickets();
        long currentSecond = Instant.now().getEpochSecond();
        String bucketKey = RATE_BUCKET_KEY_PREFIX + currentSecond;
        Long currentCount = redisTemplate.opsForValue().increment(bucketKey, 0);
        int availableSlots = (int) Math.max(0, properties.getAdmissionsPerSecond() - (currentCount == null ? 0 : currentCount));

        if (availableSlots > 0) {
            Set<String> nextTickets = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, availableSlots - 1);
            if (nextTickets != null && !nextTickets.isEmpty()) {
                for (String ticket : nextTickets) {
                    redisTemplate.opsForZSet().remove(QUEUE_KEY, ticket);
                    redisTemplate.opsForValue().increment(bucketKey);
                    String token = tokenService.generateToken(ticket, redirectUri, Instant.now().plus(properties.getTokenTtl()));
                    redisTemplate.opsForValue().set(TICKET_ADMITTED_PREFIX + ticket, token, properties.getTokenTtl());
                }
            }
        }
    }

    /**
     * Admet immediatement le porteur du code, sans passer par la file.
     *
     * Le code ne remplace pas l'authentification : le detenteur arrive devant Keycloak comme
     * tout le monde, et Keycloak decide seul. Ce qu'il achete, c'est de ne pas attendre
     * derriere deux mille etudiants un matin d'inte, quand il y a une urgence a traiter dans
     * le panneau d'administration.
     *
     * <p>La comparaison est a temps constant. Non que le secret vaille une attaque
     * sophistiquee -- il ne donne acces a rien -- mais parce qu'un {@code equals} sur un
     * secret est le genre de detail qu'on recopie ensuite ailleurs, ou il comptera.</p>
     *
     * @return l'admission, ou {@code Optional#empty()} si le code est faux ou desactive.
     */
    public Optional<GateEnterResponse> admitWithFastPass(String code, String redirectUri) {
        String expected = properties.getFastPassCode();
        if (expected == null || expected.isBlank() || code == null) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8))) {
            LOGGER.warn("Code de passage prioritaire refuse");
            return Optional.empty();
        }

        String ticketId = UUID.randomUUID().toString();
        String token = tokenService.generateToken(
                ticketId, redirectUri, Instant.now().plus(properties.getTokenTtl()));
        LOGGER.info("Passage prioritaire accorde, ticket [{}]", ticketId);
        return Optional.of(new GateEnterResponse(
                GateStatus.ADMITTED, ticketId, token,
                buildKeycloakAuthorizeUrl(token, redirectUri), 0, 0L, 0L));
    }

    private GateEnterResponse calculateQueuePosition(String ticketId, String redirectUri) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, ticketId);
        Long total = redisTemplate.opsForZSet().size(QUEUE_KEY);
        if (rank == null) {
            return new GateEnterResponse(GateStatus.QUEUED, ticketId, null, null, 1, total == null ? 1L : total, 1L);
        }
        int position = rank.intValue() + 1;
        long estimatedWaitSeconds = Math.max(1, (long) Math.ceil((double) position / properties.getAdmissionsPerSecond()));
        return new GateEnterResponse(GateStatus.QUEUED, ticketId, null, null, position, total == null ? (long) position : total, estimatedWaitSeconds);
    }

    private String buildKeycloakAuthorizeUrl(String gateToken, String redirectUri) {
        String base = "/oauth2/authorization/keycloak";
        StringBuilder sb = new StringBuilder(base);
        sb.append("?gate_token=").append(gateToken);
        if (redirectUri != null && !redirectUri.isBlank()) {
            sb.append("&redirect_uri=").append(java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
