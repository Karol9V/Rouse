package com.prueba.inventario.dto;

/**
 * Mensaje enviado por el cliente al chatbot.
 */
public class ChatMessageRequest {

    private String mensaje;
    private Long conversacionId;

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Long getConversacionId() {
        return conversacionId;
    }

    public void setConversacionId(Long conversacionId) {
        this.conversacionId = conversacionId;
    }
}
