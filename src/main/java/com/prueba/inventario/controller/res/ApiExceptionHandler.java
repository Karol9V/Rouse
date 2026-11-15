package com.prueba.inventario.controller.res;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Convierte excepciones de controladores REST en respuestas JSON consistentes.
 */
@RestControllerAdvice(basePackages = "com.prueba.inventario.controller.res")
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Informa conflictos ocasionados por restricciones de integridad.
     *
     * @param exception error de integridad de datos
     * @return respuesta {@code 409 Conflict}
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        LOGGER.warn("La operación fue rechazada por una restricción de la base de datos", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "No se pudo guardar: revisa los datos relacionados y los campos obligatorios."));
    }

    /**
     * Distingue los errores del esquema de una caída de MySQL.
     *
     * @param exception error SQL de tabla o columna
     * @return respuesta con una indicación para revisar el esquema
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<Map<String, String>> handleSchemaError(BadSqlGrammarException exception) {
        LOGGER.error("MySQL respondió, pero una tabla o columna del esquema no está disponible", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "MySQL respondió, pero falta una tabla o columna requerida. "
                        + "Revisa que se haya aplicado el esquema del chatbot y consulta el registro del servidor."));
    }

    /**
     * Informa fallas reales de conexión con el origen de datos.
     *
     * @param exception error al establecer o utilizar la conexión
     * @return respuesta {@code 503 Service Unavailable}
     */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Map<String, String>> handleDatabaseConnectionError(
            DataAccessResourceFailureException exception) {
        LOGGER.error("No se pudo conectar con MySQL", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "No se pudo conectar con MySQL. Verifica el host, puerto, credenciales y que el servidor esté activo."));
    }

    /**
     * Informa otros errores de acceso a datos sin atribuirlos automáticamente a una caída de MySQL.
     *
     * @param exception error de acceso a datos
     * @return respuesta {@code 503 Service Unavailable}
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDatabaseError(DataAccessException exception) {
        LOGGER.error("Falló una operación de acceso a datos", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "No se pudo completar la operación con la base de datos. Revisa los permisos, el esquema y el registro del servidor."));
    }

    /**
     * Informa cuerpos de solicitud que no se pueden interpretar.
     *
     * @param exception error de lectura del mensaje HTTP
     * @return respuesta {@code 400 Bad Request}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(HttpMessageNotReadableException exception) {
        LOGGER.debug("La API recibió un cuerpo JSON inválido", exception);
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Los datos enviados no tienen el formato esperado."));
    }

    /**
     * Conserva el estado HTTP de errores declarados por la aplicación.
     *
     * @param exception excepción con estado HTTP y motivo
     * @return respuesta con el estado y mensaje originales
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleRequestConflict(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", exception.getReason() == null
                        ? "No se pudo completar la operación."
                        : exception.getReason()));
    }
}
