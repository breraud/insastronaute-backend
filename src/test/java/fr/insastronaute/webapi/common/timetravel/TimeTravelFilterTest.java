package fr.insastronaute.webapi.common.timetravel;

import fr.insastronaute.webapi.common.config.JacksonConfig;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Le portail du voyage dans le temps.
 *
 * <p>Ce qui est eprouve ici n'est pas seulement qu'une bonne valeur passe,
 * mais qu'une mauvaise <strong>s'arrete</strong>. C'est le coeur du sujet : le
 * filtre ignorait autrefois en silence une date illisible et un departement
 * introuvable, et une page repondait alors avec les donnees reelles sans que
 * rien ne signale que le reglage n'avait pas pris. Un en-tete de debug muet
 * quand on le nourrit mal est pire qu'un en-tete absent.</p>
 */
@ExtendWith(MockitoExtension.class)
class TimeTravelFilterTest {

    private static final UUID MECA_ID = UUID.fromString("ae0601fe-cf5b-4ca7-8d0f-d2b9c11da3bc");

    @Mock
    private DepartmentRepository departmentRepository;

    private TimeTravelFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // Le mapper de l'application, et pas un ObjectMapper nu : le corps d'erreur
        // porte un OffsetDateTime, que seul le mapper configure sait ecrire.
        filter = new TimeTravelFilter(departmentRepository, new JacksonConfig().objectMapper());
        request = new MockHttpServletRequest("GET", "/api/events");
        response = new MockHttpServletResponse();
        staff();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TimeTravelContext.clear();
    }

    /**
     * Le cas qui a coute une soiree : le panneau du front envoyait le libelle
     * de l'option - « MECA () » - au lieu du departement, parce qu'il lisait un
     * champ que l'API ne renvoie pas. Le filtre ne trouvait rien et laissait
     * passer la requete telle quelle.
     */
    @Test
    void anUnknownDepartmentIsRefusedInsteadOfIgnored() throws Exception {
        lenient().when(departmentRepository.findByKeycloakGroupNameIgnoreCase("MECA ()")).thenReturn(Optional.empty());
        request.addHeader("X-Time-Travel-Dept", "MECA ()");
        AtomicReference<Boolean> chainRan = new AtomicReference<>(false);

        filter.doFilter(request, response, (req, res) -> chainRan.set(true));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("TIME_TRAVEL_UNKNOWN_DEPARTMENT").contains("MECA ()");
        assertThat(chainRan.get()).isFalse();
    }

    /** Ce que le panneau envoie desormais : l'identifiant, que /api/departments publie. */
    @Test
    void aDepartmentIsAcceptedByItsIdentifier() throws Exception {
        when(departmentRepository.findById(MECA_ID)).thenReturn(Optional.of(department(MECA_ID)));
        request.addHeader("X-Time-Travel-Dept", MECA_ID.toString());

        assertThat(forcedDepartmentSeenBy(filter)).isEqualTo(MECA_ID);
    }

    /** Le nom de groupe Keycloak reste accepte : il a circule, et il s'utilise a la main. */
    @Test
    void aDepartmentIsStillAcceptedByItsKeycloakGroupName() throws Exception {
        when(departmentRepository.findByKeycloakGroupNameIgnoreCase("meca")).thenReturn(Optional.of(department(MECA_ID)));
        request.addHeader("X-Time-Travel-Dept", "meca");

        assertThat(forcedDepartmentSeenBy(filter)).isEqualTo(MECA_ID);
    }

    /** Meme regle pour la date : illisible, donc refusee, et non avalee par un catch vide. */
    @Test
    void anUnparsableDateIsRefusedInsteadOfIgnored() throws Exception {
        request.addHeader("X-Time-Travel-Date", "30/08/2026");
        AtomicReference<Boolean> chainRan = new AtomicReference<>(false);

        filter.doFilter(request, response, (req, res) -> chainRan.set(true));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("TIME_TRAVEL_INVALID_DATE");
        assertThat(chainRan.get()).isFalse();
    }

    /**
     * Le voyage reste reserve au staff. Un joueur qui pose les en-tetes lui-meme
     * ne doit ni voyager ni etre refuse : pour lui, ils n'existent pas.
     */
    @Test
    void aPlayerWithoutStaffRoleTravelsNowhereAndIsNotRefused() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("player", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        request.addHeader("X-Time-Travel-Dept", "MECA ()");
        request.addHeader("X-Time-Travel-Date", "n'importe quoi");

        assertThat(forcedDepartmentSeenBy(filter)).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    /** Le contexte est vide en sortie : il ne doit pas fuir sur la requete suivante du meme fil. */
    @Test
    void theContextDoesNotSurviveTheRequest() throws Exception {
        when(departmentRepository.findById(MECA_ID)).thenReturn(Optional.of(department(MECA_ID)));
        request.addHeader("X-Time-Travel-Dept", MECA_ID.toString());

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(TimeTravelContext.getForcedDepartmentId()).isNull();
    }

    /**
     * La porte de sortie. Le panneau de reglage n'est affiche qu'a un membre du
     * staff reconnu, et cette reconnaissance vient de /api/session : la refuser
     * pour cause de reglage fautif ferait disparaitre le panneau, donc le
     * bouton qui efface le reglage, et il ne resterait qu'a vider le stockage
     * local a la main.
     */
    @Test
    void aFaultySettingNeverLocksTheStaffOutOfTheSession() throws Exception {
        lenient().when(departmentRepository.findByKeycloakGroupNameIgnoreCase("MECA ()")).thenReturn(Optional.empty());
        request = new MockHttpServletRequest("GET", "/api/session");
        request.addHeader("X-Time-Travel-Dept", "MECA ()");
        request.addHeader("X-Time-Travel-Date", "30/08/2026");
        AtomicReference<Boolean> chainRan = new AtomicReference<>(false);

        filter.doFilter(request, response, (req, res) -> chainRan.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chainRan.get()).isTrue();
    }

    /** Le departement vu par la suite de la chaine, la ou le contexte est encore rempli. */
    private UUID forcedDepartmentSeenBy(TimeTravelFilter filter) throws Exception {
        AtomicReference<UUID> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(TimeTravelContext.getForcedDepartmentId());
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    private void staff() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("staff", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }

    private static Department department(UUID id) {
        Department department = new Department();
        department.setId(id);
        department.setName("Mécanique");
        department.setShortName("MECA");
        department.setKeycloakGroupName("MECA");
        return department;
    }
}
