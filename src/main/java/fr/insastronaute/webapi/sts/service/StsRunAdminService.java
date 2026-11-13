package fr.insastronaute.webapi.sts.service;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.sts.domain.StsRunStatus;
import fr.insastronaute.webapi.sts.dto.StsRunAdminDtos;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que l'administration lit des runs solo, pour savoir quels personnages se jouent et comment
 * leurs runs se terminent.
 *
 * <p>Tout se lit dans les runs déjà en base, comme {@link fr.insastronaute.webapi.sts.multiplayer.service.StsPvpAdminService}
 * pour le multijoueur ; rien n'est écrit.
 */
@Service
public class StsRunAdminService {

    private final StsRunRepository stsRunRepository;
    private final UserRepository userRepository;

    public StsRunAdminService(StsRunRepository stsRunRepository, UserRepository userRepository) {
        this.stsRunRepository = stsRunRepository;
        this.userRepository = userRepository;
    }

    /**
     * L'usage des personnages, et comment leurs runs se sont terminées.
     *
     * @param days la période, en jours ; null ou négatif pour « depuis toujours »
     */
    @Transactional(readOnly = true)
    public StsRunAdminDtos.CharacterUsage characterUsage(Integer days) {
        Instant since = days == null || days <= 0
                ? null
                : TimeTravelContext.instantNow().minus(Duration.ofDays(days));

        Map<String, Map<StsRunStatus, Integer>> byCharacter = new LinkedHashMap<>();
        int retiredTotal = 0;
        int failedTotal = 0;
        int abandonedTotal = 0;
        int activeTotal = 0;
        int runsTotal = 0;

        List<Object[]> usageRows = since == null
            ? stsRunRepository.countByCharacterAndStatus()
            : stsRunRepository.countByCharacterAndStatusSince(since);
        for (Object[] row : usageRows) {
            String character = row[0] == null ? "?" : (String) row[0];
            StsRunStatus status = (StsRunStatus) row[1];
            int count = ((Number) row[2]).intValue();

            byCharacter.computeIfAbsent(character, ignored -> new EnumMap<>(StsRunStatus.class))
                    .merge(status, count, Integer::sum);
            runsTotal += count;
            switch (status) {
                case Retired -> retiredTotal += count;
                case Failed -> failedTotal += count;
                case Abandoned -> abandonedTotal += count;
                case Active -> activeTotal += count;
                default -> { }
            }
        }

        int totalRuns = runsTotal;
        List<StsRunAdminDtos.CharacterUsageRow> characters = byCharacter.entrySet().stream()
                .map(entry -> toUsageRow(entry.getKey(), entry.getValue(), totalRuns))
                .sorted(Comparator.comparingInt(StsRunAdminDtos.CharacterUsageRow::runs).reversed())
                .toList();

        return new StsRunAdminDtos.CharacterUsage(
                since,
                totalRuns,
                new StsRunAdminDtos.StatusSplit(retiredTotal, failedTotal, abandonedTotal, activeTotal),
                characters);
    }

    private StsRunAdminDtos.CharacterUsageRow toUsageRow(
            String character, Map<StsRunStatus, Integer> counts, int totalRuns) {
        int retired = counts.getOrDefault(StsRunStatus.Retired, 0);
        int failed = counts.getOrDefault(StsRunStatus.Failed, 0);
        int abandoned = counts.getOrDefault(StsRunStatus.Abandoned, 0);
        int active = counts.getOrDefault(StsRunStatus.Active, 0);
        int runs = counts.values().stream().mapToInt(Integer::intValue).sum();
        int decided = retired + failed;
        Double retiredRate = decided == 0 ? null : (double) retired / decided;
        double share = totalRuns == 0 ? 0 : (double) runs / totalRuns;
        return new StsRunAdminDtos.CharacterUsageRow(character, runs, share, retired, failed, abandoned, active, retiredRate);
    }

    /**
     * Combien de runs un joueur a lancées avec chaque personnage.
     *
     * @throws NotFoundException quand ce joueur n'existe pas
     */
    @Transactional(readOnly = true)
    public List<StsRunAdminDtos.PlayerCharacterUsageRow> playerCharacterUsage(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Joueur introuvable.");
        }
        return stsRunRepository.countByCharacterForOwner(userId).stream()
                .map(row -> new StsRunAdminDtos.PlayerCharacterUsageRow(
                        row[0] == null ? "?" : (String) row[0], ((Number) row[1]).intValue()))
                .toList();
    }
}
