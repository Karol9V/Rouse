package com.prueba.inventario.security;

public record AuthenticatedUser(
        Long idUsuario,
        String nombre,
        String apellido,
        String correo,
        String rol,
        String csrfToken) {

    public static final String SESSION_ATTRIBUTE = "authenticatedUser";

    public String nombreCompleto() {
        return (nombre + " " + apellido).trim();
    }
}
