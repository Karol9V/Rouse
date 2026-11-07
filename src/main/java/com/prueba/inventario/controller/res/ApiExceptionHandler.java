package com.prueba.inventario.controller.res;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
     * Informa errores de conexión o acceso a datos.
     *
     * @param exception error de acceso a datos
     * @return respuesta {@code 503 Service Unavailable}
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDatabaseError(DataAccessException exception) {
        LOGGER.error("Error al consultar la base de datos", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "No se pudo acceder a la base de datos. Verifica que MySQL esté activo, que exista bd_pasteleria y que DB_HOST, DB_PORT, DB_USER y DB_PASSWORD sean correctos."));
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
