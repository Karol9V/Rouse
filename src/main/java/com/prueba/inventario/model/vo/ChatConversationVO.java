package com.prueba.inventario.model.vo;

import java.time.LocalDateTime;

/**
 * Conversación del chatbot perteneciente a un usuario autenticado.
 */
public record ChatConversationVO(
        Long idConversacion,
        Long idUsuario,
        String titulo,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion) {
}
