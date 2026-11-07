package com.prueba.inventario.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RolePermissionsTest {

    private final AuthenticatedUser vendor =
            new AuthenticatedUser(1L, "Ana", "Lopez", "ana@example.com", "Vendedor", "token");
    private final AuthenticatedUser administrator =
            new AuthenticatedUser(2L, "Luis", "Perez", "luis@example.com", "Administrador", "token");

    @Test
    void vendorCanReadDashboardProductsAndSales() {
        assertTrue(RolePermissions.canAccess(vendor, "/", "GET"));
        assertTrue(RolePermissions.canAccess(vendor, "/productos", "GET"));
        assertTrue(RolePermissions.canAccess(vendor, "/api/productos/1", "GET"));
        assertTrue(RolePermissions.canAccess(vendor, "/api/ventas", "GET"));
        assertTrue(RolePermissions.canAccess(vendor, "/api/detalles-venta", "GET"));
    }

    @Test
    void vendorCanCreateUpdateAndDeleteSalesButNotManageOtherModules() {
        assertTrue(RolePermissions.canAccess(vendor, "/api/ventas", "POST"));
        assertTrue(RolePermissions.canAccess(vendor, "/api/ventas/1", "PUT"));
        assertTrue(RolePermissions.canAccess(vendor, "/api/ventas/1", "DELETE"));
        assertFalse(RolePermissions.canAccess(vendor, "/api/productos", "POST"));
        assertFalse(RolePermissions.canAccess(vendor, "/api/usuarios", "GET"));
        assertFalse(RolePermissions.canAccess(vendor, "/api/detalles-venta/1", "DELETE"));
        assertFalse(RolePermissions.canAccess(vendor, "/proveedores", "GET"));
    }

    @Test
    void administratorCanAccessAllModulesAndNormalizeRoleCase() {
        assertTrue(RolePermissions.canAccess(administrator, "/api/usuarios", "DELETE"));
        assertTrue(RolePermissions.canAccess(administrator, "/proveedores", "GET"));
        assertTrue(RolePermissions.isAdministrator(" ADMIN "));
    }

    @Test
    void unknownDatabaseRolesReceiveNoPermissions() {
        AuthenticatedUser customRole =
                new AuthenticatedUser(3L, "Eva", "Diaz", "eva@example.com", "Auditor", "token");
        assertFalse(RolePermissions.canAccess(customRole, "/", "GET"));
    }
}
