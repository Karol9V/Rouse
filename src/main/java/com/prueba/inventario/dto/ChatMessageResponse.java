package com.prueba.inventario.dto;

/**
 * Respuesta del asistente virtual en formato JSON.
 */
public class ChatMessageResponse {

    private String respuesta;
    private boolean ok = true;
    private Long conversacionId;

    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public Long getConversacionId() {
        return conversacionId;
    }

    public void setConversacionId(Long conversacionId) {
        this.conversacionId = conversacionId;
    }
}
