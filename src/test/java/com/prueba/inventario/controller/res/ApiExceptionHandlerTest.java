package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleRequestConflictReturnsJsonMessageAndOriginalStatus() {
        var response = handler.handleRequestConflict(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan datos obligatorios."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("message", "Faltan datos obligatorios."), response.getBody());
    }
}
