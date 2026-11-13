package fr.insastronaute.webapi.common.security.csrf;

import fr.insastronaute.webapi.common.security.config.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Security")
public class CsrfController {

    @GetMapping("/api/csrf")
    @Operation(summary = "Get CSRF token", description = "Returns the CSRF token and header name required for browser state-changing requests.")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(
                csrfToken.getParameterName(),
                SecurityConfig.CSRF_HEADER_NAME,
                csrfToken.getToken()
        );
    }
}
