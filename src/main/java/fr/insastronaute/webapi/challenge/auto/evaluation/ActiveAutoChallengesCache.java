package fr.insastronaute.webapi.challenge.auto.evaluation;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.repository.ChallengeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Tient la liste des défis automatiques actifs, que chaque déclencheur doit lire
 * avant de pouvoir conclure qu'il n'y a rien à faire. Elle ne change que lorsqu'un
 * organisateur l'édite, ce qui est rare, alors que la lecture arrive à chaque
 * carte obtenue et à chaque pixel posé.
 *
 * <p>Hors de l'abstraction de cache de Spring à dessein : l'application ne
 * déclare aucun gestionnaire de cache, et un instantané derrière une courte
 * péremption n'en demande pas.
 *
 * <p>C'est la péremption qui borne la fraîcheur plutôt qu'une invalidation : un
 * organisateur qui édite depuis une autre instance ne peut pas atteindre cet
 * instantané-ci, et la seule garantie qui vaille est que son changement arrive
 * dans le délai annoncé.
 */
@Component
public class ActiveAutoChallengesCache {

    /** Même fenêtre que le balayage : un défi clos depuis peu reste évaluable. */
    private static final Duration RECENTLY_ENDED = Duration.ofHours(3);

    private final ChallengeRepository repository;
    private final long ttlNanos;
    private final LongSupplier clock;

    private volatile Snapshot snapshot;

    @Autowired
    public ActiveAutoChallengesCache(
            ChallengeRepository repository,
            @Value("${app.auto-challenges.cache.ttl-ms:10000}") long ttlMillis
    ) {
        this(repository, ttlMillis, System::nanoTime);
    }

    ActiveAutoChallengesCache(ChallengeRepository repository, long ttlMillis, LongSupplier clock) {
        this.repository = repository;
        this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, ttlMillis));
        this.clock = clock;
    }

    public List<Challenge> current() {
        Snapshot current = snapshot;
        long now = clock.getAsLong();
        if (current != null && now - current.takenAt() < ttlNanos) {
            return current.challenges();
        }
        // La vraie horloge, jamais celle du voyage dans le temps : l'instantané est
        // partagé par tous les joueurs pendant sa péremption, et celui pris sous le
        // voyage d'un membre du staff leur aurait présenté les défis de sa date.
        Instant instant = Instant.now();
        List<Challenge> fresh = List.copyOf(
                repository.findActiveAutomaticChallenges(instant, instant.minus(RECENTLY_ENDED))
        );
        snapshot = new Snapshot(fresh, now);
        return fresh;
    }

    private record Snapshot(List<Challenge> challenges, long takenAt) {
    }
}
