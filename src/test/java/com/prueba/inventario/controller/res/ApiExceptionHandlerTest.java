package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
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

    @Test
    void schemaErrorDoesNotReportThatMysqlIsUnavailable() {
        var response = handler.handleSchemaError(new BadSqlGrammarException(
                "consultar chat", "SELECT * FROM chat_conversations", new SQLException("table missing")));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().get("message").contains("MySQL respondió"));
    }

    @Test
    void connectionErrorReportsDatabaseUnavailability() {
        var response = handler.handleDatabaseConnectionError(
                new org.springframework.dao.DataAccessResourceFailureException("connection failed"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(response.getBody().get("message").contains("No se pudo conectar con MySQL"));
    }
}
