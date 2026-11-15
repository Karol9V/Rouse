package com.prueba.inventario.model.vo;

import java.time.LocalDateTime;

/**
 * Mensaje persistido en una conversación del chatbot.
 */
public record ChatMessageVO(
        Long idMensaje,
        Long idConversacion,
        String emisor,
        String contenido,
        LocalDateTime fechaCreacion) {
}
