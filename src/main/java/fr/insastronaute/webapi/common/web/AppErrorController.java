package fr.insastronaute.webapi.common.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class AppErrorController implements org.springframework.boot.webmvc.error.ErrorController {

    @RequestMapping(path = "/error")
    @ResponseBody
    public ResponseEntity<?> error(HttpServletRequest request, HttpServletResponse response) {
        // Une panne apres le premier octet : Tomcat inclut cette page dans la reponse deja
        // commencee avant de couper la connexion. Un corps d'erreur s'ajouterait au fichier
        // (ou echouerait contre son type, en doublant l'erreur au journal) : rien a ecrire.
        if (response.isCommitted()) {
            return null;
        }
        HttpStatus status = resolveStatus(request);
        String path = resolvePath(request);

        if (isBrowserRequest(request)) {
            return htmlError(status, path);
        }

        return jsonError(status, path);
    }

    static ResponseEntity<String> htmlError(HttpStatus status, String path) {
        String title = resolveTitle(status);
        String detail = resolveDetail(status);

        return ResponseEntity
                .status(status)
                .contentType(MediaType.TEXT_HTML)
                .body(renderHtml(status, title, detail, path));
    }

    static ResponseEntity<Map<String, Object>> jsonError(HttpStatus status, String path) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", fr.insastronaute.webapi.common.timetravel.TimeTravelContext.offsetDateTimeNow().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("path", path);

        return ResponseEntity.status(status).body(body);
    }

    static boolean isBrowserRequest(HttpServletRequest request) {
        String fetchMode = request.getHeader("Sec-Fetch-Mode");

        if (fetchMode != null) {
            return "navigate".equalsIgnoreCase(fetchMode);
        }

        String accept = request.getHeader(HttpHeaders.ACCEPT);

        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private static String resolveTitle(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return "Connexion requise";
        }

        if (status == HttpStatus.FORBIDDEN) {
            return "Accès refusé";
        }

        if (status == HttpStatus.NOT_FOUND) {
            return "Page introuvable";
        }

        return status.is4xxClientError() ? "Requête refusée" : "Service indisponible";
    }

    private static String resolveDetail(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return "Vous devez être connecté pour accéder à cette ressource.";
        }

        if (status == HttpStatus.FORBIDDEN) {
            return "Votre compte ne dispose pas des droits nécessaires.";
        }

        if (status == HttpStatus.NOT_FOUND) {
            return "Cette route n'existe pas sur l'API INSA'Stronaute.";
        }

        return status.is4xxClientError()
                ? "La requête ne peut pas être traitée par l'API."
                : "Le backend a rencontre une erreur inattendue.";
    }

    private static HttpStatus resolveStatus(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (statusCode instanceof Integer code) {
            HttpStatus status = HttpStatus.resolve(code);

            if (status != null) {
                return status;
            }
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String resolvePath(HttpServletRequest request) {
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        if (path instanceof String value && !value.isBlank()) {
            return value;
        }

        return request.getRequestURI();
    }

    private static String renderHtml(HttpStatus status, String title, String detail, String path) {
        return """
                <!doctype html>
                <html lang="fr">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s - INSA'Stronaute API</title>
                    <style>
                        :root {
                            color-scheme: dark;
                            font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                            background: #09090b;
                            color: #f8fafc;
                        }

                        * {
                            box-sizing: border-box;
                        }

                        body {
                            min-height: 100vh;
                            margin: 0;
                            display: grid;
                            place-items: center;
                            padding: 32px;
                            background:
                                linear-gradient(90deg, rgba(255, 255, 255, 0.045) 1px, transparent 1px),
                                linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
                                linear-gradient(135deg, #09090b 0%%, #141416 56%%, #101827 100%%);
                            background-size: 44px 44px, 44px 44px, auto;
                        }

                        main {
                            width: min(720px, 100%%);
                            border: 1px solid rgba(148, 163, 184, 0.28);
                            border-radius: 8px;
                            padding: clamp(28px, 6vw, 56px);
                            background: rgba(18, 18, 22, 0.9);
                            box-shadow: 0 28px 90px rgba(0, 0, 0, 0.42);
                        }

                        .eyebrow {
                            margin: 0 0 18px;
                            color: #38bdf8;
                            font-size: 0.78rem;
                            font-weight: 800;
                            letter-spacing: 0.16em;
                            text-transform: uppercase;
                        }

                        h1 {
                            margin: 0;
                            font-size: clamp(2.6rem, 8vw, 5.6rem);
                            line-height: 0.95;
                            letter-spacing: 0;
                        }

                        h2 {
                            margin: 18px 0 0;
                            font-size: clamp(1.4rem, 3vw, 2rem);
                            line-height: 1.15;
                        }

                        p {
                            max-width: 58ch;
                            margin: 14px 0 0;
                            color: #cbd5e1;
                            font-size: 1rem;
                            line-height: 1.65;
                        }

                        code {
                            display: inline-block;
                            max-width: 100%%;
                            margin-top: 24px;
                            padding: 10px 12px;
                            overflow-wrap: anywhere;
                            border: 1px solid rgba(148, 163, 184, 0.24);
                            border-radius: 6px;
                            color: #dbeafe;
                            background: rgba(2, 6, 23, 0.72);
                            font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
                            font-size: 0.9rem;
                        }
                    </style>
                </head>
                <body>
                    <main>
                        <p class="eyebrow">INSA'Stronaute API</p>
                        <h1>%d</h1>
                        <h2>%s</h2>
                        <p>%s</p>
                        <code>%s</code>
                    </main>
                </body>
                </html>
                """.formatted(
                status.value(),
                status.value(),
                escapeHtml(title),
                escapeHtml(detail),
                escapeHtml(path)
        );
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
