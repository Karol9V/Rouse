package com.prueba.inventario.dto;

import com.prueba.inventario.model.vo.ChatConversationVO;
import java.time.LocalDateTime;

/**
 * Datos de una conversación visibles para su propietario.
 */
public record ChatConversationSummary(
        Long idConversacion,
        String titulo,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion) {

    public static ChatConversationSummary from(ChatConversationVO conversation) {
        return new ChatConversationSummary(
                conversation.idConversacion(),
                conversation.titulo(),
                conversation.fechaCreacion(),
                conversation.fechaActualizacion());
    }
}
