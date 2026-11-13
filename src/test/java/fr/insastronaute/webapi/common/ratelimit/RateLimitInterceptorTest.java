package fr.insastronaute.webapi.common.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitInterceptorTest {

    private final RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(rateLimiter);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appliesCheckpointPolicyToAuthenticatedSubject() throws Exception {
        authenticate("player-sub");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/treasure-hunt/checkpoints/validate"
        );

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(
                eq("player-sub"),
                argThat(policy -> policy.id().equals("checkpoint-validation")
                        && policy.limit() == 30)
        );
    }

    @Test
    void appliesFriendRequestPolicyButNotAcceptRoute() throws Exception {
        authenticate("player-sub");
        MockHttpServletRequest send = new MockHttpServletRequest(
                "POST",
                "/api/friends/requests/9f8d2ced-173d-453c-b3f4-9cc8edfd35f6"
        );
        MockHttpServletRequest accept = new MockHttpServletRequest(
                "POST",
                "/api/friends/requests/9f8d2ced-173d-453c-b3f4-9cc8edfd35f6/accept"
        );

        interceptor.preHandle(send, new MockHttpServletResponse(), new Object());
        interceptor.preHandle(accept, new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(
                eq("player-sub"),
                argThat(policy -> policy.id().equals("friend-request"))
        );
    }

    @Test
    void limitsExpensiveDatasetSheetGenerationForEachAdmin() throws Exception {
        authenticate("admin-sub");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/admin/batches/dataset/sheets"
        );

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(
                eq("admin-sub"),
                argThat(policy -> policy.id().equals("dataset-sheet-generation")
                        && policy.limit() == 10
                        && policy.window().equals(java.time.Duration.ofHours(1)))
        );
    }

    // ── Les codes qu'on devine ────────────────────────────────────────────────
    // Un code se casse en essayant. Ce qui protege ces routes n'est pas la longueur du
    // secret mais le nombre d'essais qu'on laisse faire.

    /**
     * Le passage prioritaire se freine par adresse, faute de compte a qui l'imputer.
     *
     * <p>C'est le seul secret partage de l'application, et il s'essaie sans etre
     * authentifie : le limiteur par sujet ne le voyait donc jamais. Sans frein, un code
     * court se casse a la vitesse du lien.</p>
     */
    @Test
    void throttlesTheAnonymousFastPassByClientAddress() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/gate/fast-pass");
        request.setRemoteAddr("203.0.113.7");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(
                eq("ip:203.0.113.7"),
                argThat(policy -> policy.id().equals("fast-pass-attempt"))
        );
    }

    /** Un joueur connecte qui essaie le code reste compte sur son adresse, comme les autres. */
    @Test
    void theFastPassIsCountedOnTheAddressEvenWhenSignedIn() throws Exception {
        authenticate("player-sub");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/gate/fast-pass");
        request.setRemoteAddr("203.0.113.7");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(eq("ip:203.0.113.7"), argThat(policy -> policy.id().equals("fast-pass-attempt")));
        verify(rateLimiter, never()).requireAllowed(eq("player-sub"), org.mockito.ArgumentMatchers.any());
    }

    /** Une route par sujet reste muette pour qui n'est pas authentifie : rien a compter. */
    @Test
    void aSubjectKeyedRouteStaysOpenToAnonymousCallers() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/cards/instances/claim");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter, never()).requireAllowed(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(RateLimitPolicy.class)
        );
    }

    @Test
    void throttlesPhysicalCardClaims() throws Exception {
        authenticate("player-sub");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/cards/instances/claim");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(eq("player-sub"), argThat(policy -> policy.id().equals("card-claim")));
    }

    /** Le coffre prend un verrou pessimiste sur le joueur : l'essayer en boucle coute cher. */
    @Test
    void throttlesVaultUnlockAttempts() throws Exception {
        authenticate("player-sub");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/treasure-hunt/vault/unlock");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(eq("player-sub"), argThat(policy -> policy.id().equals("vault-unlock")));
    }

    @Test
    void throttlesChallengeSubmissions() throws Exception {
        authenticate("player-sub");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/challenges/9f8d2ced-173d-453c-b3f4-9cc8edfd35f6/submit");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(eq("player-sub"), argThat(policy -> policy.id().equals("challenge-submission")));
    }

    /** Le panier se manipule au bouton « plus » : la limite laisse de quoi cliquer. */
    @Test
    void throttlesCartChangesWithRoomToClick() throws Exception {
        authenticate("player-sub");
        MockHttpServletRequest put = new MockHttpServletRequest(
                "PUT", "/api/shop/cart/items/9f8d2ced-173d-453c-b3f4-9cc8edfd35f6");
        MockHttpServletRequest delete = new MockHttpServletRequest(
                "DELETE", "/api/shop/cart/items/9f8d2ced-173d-453c-b3f4-9cc8edfd35f6");

        interceptor.preHandle(put, new MockHttpServletResponse(), new Object());
        interceptor.preHandle(delete, new MockHttpServletResponse(), new Object());

        verify(rateLimiter, org.mockito.Mockito.times(2)).requireAllowed(
                eq("player-sub"),
                argThat(policy -> policy.id().equals("cart-change") && policy.limit() >= 60)
        );
    }

    /** Le balayage complet interroge Keycloak une fois par compte : il ne se martele pas. */
    @Test
    void throttlesTheFullDepartmentSweep() throws Exception {
        authenticate("admin-sub");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/users/department-resync");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(eq("admin-sub"),
                argThat(policy -> policy.id().equals("department-resync-sweep") && policy.limit() == 2));
    }

    /** La relecture d'un seul joueur ne coute qu'un appel : elle reste libre. */
    @Test
    void leavesTheSinglePlayerResyncAlone() throws Exception {
        authenticate("admin-sub");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/admin/users/9f8d2ced-173d-453c-b3f4-9cc8edfd35f6/department-resync");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter, never()).requireAllowed(eq("admin-sub"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresUnlistedRoute() throws Exception {
        authenticate("player-sub");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/shop/products");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        verify(rateLimiter, never()).requireAllowed(
                eq("player-sub"),
                org.mockito.ArgumentMatchers.any(RateLimitPolicy.class)
        );
    }

    @Test
    void limitsMediaLibraryReadsPerSubject() throws Exception {
        authenticate("staff-sub");
        String id = "9f8d2ced-173d-453c-b3f4-9cc8edfd35f6";

        interceptor.preHandle(new MockHttpServletRequest("GET", "/api/media-library/media/" + id + "/0/thumbnail"),
                new MockHttpServletResponse(), new Object());
        interceptor.preHandle(new MockHttpServletRequest("GET", "/api/media-library/media/" + id + "/0/content"),
                new MockHttpServletResponse(), new Object());

        verify(rateLimiter).requireAllowed(eq("staff-sub"),
                argThat(policy -> policy.id().equals("media-library-thumbnail") && policy.limit() == 600));
        verify(rateLimiter).requireAllowed(eq("staff-sub"),
                argThat(policy -> policy.id().equals("media-library-content") && policy.limit() == 120));
    }

    private void authenticate(String subject) {
        OidcUser user = mock(OidcUser.class);
        when(user.getSubject()).thenReturn(subject);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(user, null, "ROLE_USER")
        );
    }
}
