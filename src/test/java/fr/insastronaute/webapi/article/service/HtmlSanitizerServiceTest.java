package fr.insastronaute.webapi.article.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerServiceTest {

    private final HtmlSanitizerService htmlSanitizerService = new HtmlSanitizerService();

    @Test
    void sanitizeRemovesScriptsButKeepsEditorClassesSafeStylesAndMediaTags() {
        String html = """
                <div class="prose text-lg" style="color: red; position: fixed" onclick="alert(1)">
                    <script>alert('xss')</script>
                    <p class="text-center" style="text-align: center; background-image: url(https://evil.test/x)">Hello</p>
                    <img src="data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+" alt="bad">
                    <video src="https://cdn.example.test/video.mp4" controls width="640" height="360"></video>
                    <source src="https://cdn.example.test/video.webm" type="video/webm">
                    <iframe src="https://www.youtube.com/embed/abc123" width="560" height="315" allowfullscreen></iframe>
                    <iframe src="https://evil.example.test/embed/abc123" width="560" height="315"></iframe>
                </div>
                """;

        String sanitizedHtml = htmlSanitizerService.sanitize(html);

        assertThat(sanitizedHtml).doesNotContain("<script");
        assertThat(sanitizedHtml).doesNotContain("onclick");
        assertThat(sanitizedHtml).doesNotContain("data:image");
        assertThat(sanitizedHtml).doesNotContain("position");
        assertThat(sanitizedHtml).doesNotContain("background-image");
        assertThat(sanitizedHtml).doesNotContain("evil.example.test");
        assertThat(sanitizedHtml).contains("class=\"prose text-lg\"");
        assertThat(sanitizedHtml).contains("style=\"color: red;\"");
        assertThat(sanitizedHtml).contains("<p class=\"text-center\" style=\"text-align: center;\">Hello</p>");
        assertThat(sanitizedHtml).contains("<video");
        assertThat(sanitizedHtml).contains("controls");
        assertThat(sanitizedHtml).contains("<source");
        assertThat(sanitizedHtml).contains("type=\"video/webm\"");
        assertThat(sanitizedHtml).contains("<iframe src=\"https://www.youtube.com/embed/abc123\"");
        assertThat(sanitizedHtml).contains("allowfullscreen");
    }
}
