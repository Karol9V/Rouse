package com.prueba.inventario.security;

/**
 * Identidad y token CSRF conservados en la sesión autenticada.
 *
 * @param idUsuario identificador del usuario
 * @param nombre nombre del usuario
 * @param apellido apellido del usuario
 * @param correo correo del usuario
 * @param rol rol de autorización
 * @param csrfToken token CSRF asociado a la sesión
 */
public record AuthenticatedUser(
        Long idUsuario,
        String nombre,
        String apellido,
        String correo,
        String rol,
        String csrfToken) {

    public static final String SESSION_ATTRIBUTE = "authenticatedUser";

    /**
     * Construye el nombre completo del usuario.
     *
     * @return nombre y apellido separados por un espacio
     */
    public String nombreCompleto() {
        return (nombre + " " + apellido).trim();
    }
}
