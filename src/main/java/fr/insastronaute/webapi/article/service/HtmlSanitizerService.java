package fr.insastronaute.webapi.article.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class HtmlSanitizerService {

    private static final Set<String> ALLOWED_IFRAME_HOSTS = Set.of(
            "youtube.com",
            "youtube-nocookie.com",
            "player.vimeo.com"
    );
    private static final Set<String> ALLOWED_STYLE_PROPERTIES = Set.of(
            "text-align",
            "color",
            "background-color",
            "font-weight",
            "font-style",
            "text-decoration"
    );
    private static final Pattern SAFE_COLOR_VALUE = Pattern.compile(
            "(?i)^(#[0-9a-f]{3,8}|rgb\\([0-9\\s,%.]+\\)|rgba\\([0-9\\s,%.]+\\)|hsl\\([0-9\\s,%.]+\\)|hsla\\([0-9\\s,%.]+\\)|[a-z]+)$"
    );
    private static final Pattern SAFE_FONT_WEIGHT_VALUE = Pattern.compile("(?i)^(normal|bold|bolder|lighter|[1-9]00)$");
    private static final Pattern SAFE_FONT_STYLE_VALUE = Pattern.compile("(?i)^(normal|italic)$");
    private static final Pattern SAFE_TEXT_ALIGN_VALUE = Pattern.compile("(?i)^(left|right|center|justify|start|end)$");
    private static final Pattern SAFE_TEXT_DECORATION_VALUE = Pattern.compile("(?i)^(none|underline|line-through)$");

    private final Safelist safelist;
    private final Document.OutputSettings outputSettings;

    public HtmlSanitizerService() {
        this.safelist = Safelist.relaxed()
                .addAttributes(":all", "class", "style")
                .addTags("video", "source", "iframe", "mark")
                .addAttributes("video", "src", "width", "height", "controls", "poster", "preload", "playsinline")
                .addAttributes("source", "src", "type", "media")
                .addAttributes("iframe", "src", "width", "height", "allow", "allowfullscreen", "frameborder", "referrerpolicy", "title")
                .addProtocols("img", "src", "http", "https")
                .addProtocols("video", "src", "http", "https")
                .addProtocols("video", "poster", "http", "https")
                .addProtocols("source", "src", "http", "https")
                .addProtocols("iframe", "src", "http", "https");
        this.outputSettings = new Document.OutputSettings()
                .prettyPrint(false);
    }

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String cleanedHtml = Jsoup.clean(html, "", safelist, outputSettings);
        Document document = Jsoup.parseBodyFragment(cleanedHtml);
        document.outputSettings(outputSettings);
        sanitizeInlineStyles(document);
        removeUnsafeIframes(document);

        return document.body().html();
    }

    private void sanitizeInlineStyles(Document document) {
        for (Element element : document.select("[style]")) {
            String safeStyle = safeStyle(element.attr("style"));

            if (safeStyle.isBlank()) {
                element.removeAttr("style");
            } else {
                element.attr("style", safeStyle);
            }
        }
    }

    private String safeStyle(String style) {
        StringBuilder safeDeclarations = new StringBuilder();

        for (String declaration : style.split(";")) {
            int separatorIndex = declaration.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }

            String property = declaration.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(separatorIndex + 1).trim();

            if (!ALLOWED_STYLE_PROPERTIES.contains(property) || !isSafeStyleValue(property, value)) {
                continue;
            }

            if (!safeDeclarations.isEmpty()) {
                safeDeclarations.append(' ');
            }
            safeDeclarations.append(property).append(": ").append(value).append(';');
        }

        return safeDeclarations.toString();
    }

    private boolean isSafeStyleValue(String property, String value) {
        return switch (property) {
            case "text-align" -> SAFE_TEXT_ALIGN_VALUE.matcher(value).matches();
            case "color", "background-color" -> SAFE_COLOR_VALUE.matcher(value).matches();
            case "font-weight" -> SAFE_FONT_WEIGHT_VALUE.matcher(value).matches();
            case "font-style" -> SAFE_FONT_STYLE_VALUE.matcher(value).matches();
            case "text-decoration" -> SAFE_TEXT_DECORATION_VALUE.matcher(value).matches();
            default -> false;
        };
    }

    private void removeUnsafeIframes(Document document) {
        for (Element iframe : document.select("iframe")) {
            if (!isAllowedIframeSource(iframe.attr("src"))) {
                iframe.remove();
            }
        }
    }

    private boolean isAllowedIframeSource(String src) {
        try {
            URI uri = URI.create(src);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (host == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                return false;
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return ALLOWED_IFRAME_HOSTS.stream()
                    .anyMatch(allowedHost -> normalizedHost.equals(allowedHost) || normalizedHost.endsWith("." + allowedHost));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
