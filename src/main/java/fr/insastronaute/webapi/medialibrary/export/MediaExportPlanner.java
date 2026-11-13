package fr.insastronaute.webapi.medialibrary.export;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * L'ordre d'un export et son decoupage en parties.
 *
 * <p>Fige une fois, a l'instantane : par titre de defi, puis date, soumission et
 * position. Les parties se remplissent dans cet ordre sans depasser leur limite. Un
 * fichier plus gros que la limite (impossible a 40 Mio par preuve) aurait sa propre
 * partie plutot que de casser le decoupage.</p>
 */
public final class MediaExportPlanner {

    public static final long PART_LIMIT_BYTES = 2L * 1024 * 1024 * 1024;

    public record Candidate(UUID submissionId, int mediaIndex, String challengeTitle, Instant submittedAt, long sizeBytes) {
    }

    public record PlannedItem(Candidate candidate, int position, int partNumber) {
    }

    private MediaExportPlanner() {
    }

    public static List<PlannedItem> plan(List<Candidate> candidates, long partLimitBytes) {
        Collator titles = Collator.getInstance(Locale.FRENCH);
        titles.setStrength(Collator.SECONDARY);
        List<Candidate> ordered = candidates.stream()
                .sorted(Comparator
                        .comparing((Candidate candidate) -> candidate.challengeTitle() == null ? "" : candidate.challengeTitle(), titles)
                        .thenComparing(Candidate::submittedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Candidate::submissionId)
                        .thenComparingInt(Candidate::mediaIndex))
                .toList();

        List<PlannedItem> planned = new ArrayList<>(ordered.size());
        int part = 1;
        long used = 0;
        for (int position = 0; position < ordered.size(); position++) {
            Candidate candidate = ordered.get(position);
            if (used > 0 && used + candidate.sizeBytes() > partLimitBytes) {
                part++;
                used = 0;
            }
            used += candidate.sizeBytes();
            planned.add(new PlannedItem(candidate, position, part));
        }
        return planned;
    }

    public static int partCount(List<PlannedItem> planned) {
        return planned.isEmpty() ? 0 : planned.getLast().partNumber();
    }
}
