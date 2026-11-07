package com.prueba.inventario.security;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.http.HttpMethod;

public final class RolePermissions {

    private RolePermissions() {
    }

    public static boolean isAdministrator(String role) {
        String normalized = normalize(role);
        return normalized.equals("administrador") || normalized.equals("admin");
    }

    public static boolean isVendor(String role) {
        return normalize(role).equals("vendedor");
    }

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
