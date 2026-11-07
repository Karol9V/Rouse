package com.prueba.inventario.security;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.http.HttpMethod;

/**
 * Evalúa los permisos de acceso a rutas según el rol autenticado.
 */
public final class RolePermissions {

    private RolePermissions() {
    }

    /**
     * Indica si el rol corresponde a un administrador.
     *
     * @param role nombre del rol
     * @return {@code true} para Administrador o Admin
     */
    public static boolean isAdministrator(String role) {
        String normalized = normalize(role);
        return normalized.equals("administrador") || normalized.equals("admin");
    }

    /**
     * Indica si el rol corresponde a un vendedor.
     *
     * @param role nombre del rol
     * @return {@code true} para Vendedor
     */
    public static boolean isVendor(String role) {
        return normalize(role).equals("vendedor");
    }

    /**
     * Determina si el usuario puede acceder a la ruta con el método indicado.
     *
     * @param user identidad autenticada
     * @param path ruta solicitada
     * @param method método HTTP
     * @return {@code true} si la operación está permitida
     */
    public static boolean canAccess(AuthenticatedUser user, String path, String method) {
        if (isAdministrator(user.rol())) {
            return true;
        }
        if (!isVendor(user.rol())) {
            return false;
        }

        if (HttpMethod.GET.matches(method)) {
            return path.equals("/")
                    || path.equals("/productos")
                    || path.equals("/ventas")
                    || isApiResource(path, "/api/productos")
                    || isApiResource(path, "/api/ventas")
                    || isApiResource(path, "/api/detalles-venta")
                    || path.equals("/api/auth/me");
        }
        if (path.equals("/api/ventas")) {
            return HttpMethod.POST.matches(method);
        }
        return isSale(path) && (HttpMethod.PUT.matches(method) || HttpMethod.DELETE.matches(method));
    }

    private static boolean isApiResource(String path, String resource) {
        return path.equals(resource) || path.startsWith(resource + "/");
    }

    private static boolean isSale(String path) {
        return path.matches("/api/ventas/[^/]+");
    }

    private static String normalize(String role) {
        if (role == null) {
            return "";
        }
        return Normalizer.normalize(role.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
