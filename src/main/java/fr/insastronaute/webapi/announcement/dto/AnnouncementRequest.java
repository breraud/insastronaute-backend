package fr.insastronaute.webapi.announcement.dto;

import fr.insastronaute.webapi.announcement.domain.AnnouncementKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Ce qu'une annonce demande a l'ecriture.
 *
 * <p>{@code active} est boxe : absent vaut « active », qui est le cas courant —
 * on ecrit une annonce pour qu'elle s'affiche.</p>
 */
public record AnnouncementRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String body,
        AnnouncementKind kind,
        Boolean active,
        Instant startDate,
        Instant endDate,
        @Size(max = 120)
        @Pattern(
                regexp = "|/[A-Za-z0-9][A-Za-z0-9/_-]*",
                message = "Le lien doit être un chemin du site, comme /jeu."
        )
        String link
) {
    /** Sans destination : une annonce n'en a pas toujours une. */
    public AnnouncementRequest(String title, String body, AnnouncementKind kind, Boolean active,
                               Instant startDate, Instant endDate) {
        this(title, body, kind, active, startDate, endDate, null);
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }

    @AssertTrue(message = "La date de fin doit être postérieure à la date de début.")
    @SuppressWarnings("unused") // Lu par la validation Jakarta, par reflexion.
    public boolean isValidWindow() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
