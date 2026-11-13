package fr.insastronaute.webapi.department;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La liste des departements decrit l'ecole, pas les audiences.
 *
 * <p>Neuf ecrans s'en servent -- Place, cibles de defis, classement, effectifs,
 * legendes -- et un seul veut PERF. Le defaut les sert tous sauf celui-la, qui
 * demande explicitement l'audience en plus.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentRepository departmentRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void theDepartmentListLeavesOutThePerfAudience() throws Exception {
        when(departmentRepository.findAllByVirtualFalse()).thenReturn(List.of(department("GC", false)));

        mockMvc.perform(get("/api/departments").with(player()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shortName").value("GC"));
    }

    @Test
    void askingForTheVirtualDepartmentsAddsPerfToTheList() throws Exception {
        when(departmentRepository.findAll()).thenReturn(List.of(department("GC", false), department("PERF", true)));

        mockMvc.perform(get("/api/departments").param("includeVirtual", "true").with(player()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].shortName").value("PERF"))
                .andExpect(jsonPath("$[1].virtual").value(true));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor player() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))
                .idToken(token -> token.subject("player-sub"));
    }

    private static Department department(String shortName, boolean virtual) {
        Department department = new Department();
        department.setId(UUID.randomUUID());
        department.setName("Departement " + shortName);
        department.setShortName(shortName);
        department.setVirtual(virtual);
        return department;
    }
}
