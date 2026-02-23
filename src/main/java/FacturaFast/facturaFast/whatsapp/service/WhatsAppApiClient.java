package FacturaFast.facturaFast.whatsapp.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class WhatsAppApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppApiClient.class);

    @Value("${meta.access_token:}")
    private String accessToken;

    @Value("${meta.phone_number_id:}")
    private String phoneNumberId;

    @Value("${meta.graph_version:v19.0}")
    private String graphVersion;

    private final RestTemplate restTemplate = new RestTemplate();

    public Boolean sendText(String waId, String text) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", waId,
                "type", "text",
                "text", Map.of("body", text)
        );
        return post(body);
    }

    public Boolean sendButtons(String waId, String bodyText, String footer, Map<String, String> buttons) {
        List<Map<String, Object>> actionButtons = buttons.entrySet().stream()
                .limit(3)
                .map(entry -> Map.of(
                        "type", "reply",
                        "reply", Map.of("id", entry.getKey(), "title", entry.getValue())
                )).toList();

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", waId,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", bodyText),
                        "footer", Map.of("text", footer),
                        "action", Map.of("buttons", actionButtons)
                )
        );
        return post(body);
    }

    private boolean post(Map<String, Object> payload) {
        String normalizedToken = normalizeToken(accessToken);
        String normalizedPhoneNumberId = phoneNumberId == null ? null : phoneNumberId.trim();

        if (normalizedToken == null || normalizedToken.isBlank() || normalizedPhoneNumberId == null || normalizedPhoneNumberId.isBlank()) {
            log.warn("WhatsApp API no configurada: faltan meta.access_token o meta.phone_number_id");
            return false;
        }
        String url = "https://graph.facebook.com/" + graphVersion + "/" + normalizedPhoneNumberId + "/messages";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(normalizedToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
            return true;
        } catch (HttpStatusCodeException ex) {
            if (HttpStatus.UNAUTHORIZED.equals(ex.getStatusCode())) {
                log.error("Meta devolvió 401 UNAUTHORIZED. Revisa token (expirado/app incorrecta/permisos faltantes). url={} tokenLength={} body={}",
                        url, normalizedToken.length(), ex.getResponseBodyAsString());
            } else {
                log.error("Error al enviar mensaje a Meta WhatsApp. status={} body={} url={} tokenLength={}",
                        ex.getStatusCode(), ex.getResponseBodyAsString(), url, normalizedToken.length());
            }
            return false;
        } catch (RuntimeException ex) {
            log.error("Error inesperado enviando mensaje a Meta WhatsApp", ex);
            return false;
        }
    }

    private String normalizeToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }

        String token = rawToken.trim();

        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }

        if ((token.startsWith("\"") && token.endsWith("\""))
                || (token.startsWith("'") && token.endsWith("'"))) {
            token = token.substring(1, token.length() - 1).trim();
        }

        token = token.replace("\\n", "")
                .replace("\\r", "")
                .replace("\\t", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .replace(" ", "");

        return token;
    }
}
