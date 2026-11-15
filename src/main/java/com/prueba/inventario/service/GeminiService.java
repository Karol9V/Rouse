package com.prueba.inventario.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Gestiona la integración con la API de Gemini para responder consultas en español.
 */
@Service
public class GeminiService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String apiKey;

    @Value("${gemini.model:${GEMINI_MODEL:gemini-4-hydrogen}}")
    private String model;

    /**
     * Genera una respuesta usando Gemini para un intent, o devuelve {@code null} si la API no está configurada.
     *
     * @param prompt consulta del usuario con el contexto del sistema
     * @return texto generado por Gemini, o {@code null} si no hay servicio disponible
     */
    public String generateResponse(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.debug("Gemini no está configurado; se utilizará la respuesta local del chatbot.");
            return null;
        }
        String body = buildRequestBody(prompt);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Gemini devolvió el estado HTTP {}.", response.statusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return null;
            }
            JsonNode textNode = candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                return null;
            }
            return textNode.asText().trim();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("La solicitud a Gemini fue interrumpida.", ex);
            return null;
        } catch (IOException ex) {
            LOGGER.warn("No se pudo completar o interpretar la respuesta de Gemini.", ex);
            return null;
        }
    }

    private String buildRequestBody(String prompt) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    )),
                    "generationConfig", Map.of(
                            "temperature", 0.6,
                            "topP", 0.9,
                            "maxOutputTokens", 800)
            ));
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo serializar la solicitud de Gemini.", ex);
        }
    }
}
