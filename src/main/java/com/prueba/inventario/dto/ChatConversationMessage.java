package com.prueba.inventario.dto;

import com.prueba.inventario.model.vo.ChatMessageVO;
import java.time.LocalDateTime;

/**
 * Mensaje de conversación expuesto al cliente.
 */
public record ChatConversationMessage(
        Long idMensaje,
        String emisor,
        String contenido,
        LocalDateTime fechaCreacion) {

    public static ChatConversationMessage from(ChatMessageVO message) {
        return new ChatConversationMessage(
                message.idMensaje(),
                message.emisor(),
                message.contenido(),
                message.fechaCreacion());
    }
}
