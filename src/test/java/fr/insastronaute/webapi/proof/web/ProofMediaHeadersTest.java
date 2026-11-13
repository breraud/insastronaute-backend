package fr.insastronaute.webapi.proof.web;

import fr.insastronaute.webapi.proof.service.ProofMediaStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProofMediaHeadersTest {

    @Test
    void aPartialVideoCarriesItsRangeAndTheSecurityHeaders() {
        ProofMediaStream partial = new ProofMediaStream("video/mp4", 1000, 100, 199, true, new ByteArrayInputStream(new byte[0]));

        HttpHeaders headers = ProofMediaHeaders.forMedia(partial);

        assertThat(headers.getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 100-199/1000");
        assertThat(headers.getContentLength()).isEqualTo(100);
        assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store, private, max-age=0");
        assertThat(headers.getFirst("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void headersAreCopiedOntoAServletResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProofMediaHeaders.writeTo(ProofMediaHeaders.forBytes("image/jpeg", 42), response);

        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("42");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store, private, max-age=0");
    }
}
