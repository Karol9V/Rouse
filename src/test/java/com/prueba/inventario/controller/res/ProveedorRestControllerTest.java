package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prueba.inventario.model.dao.ProveedorDAO;
import com.prueba.inventario.model.vo.ProveedorVO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

class ProveedorRestControllerTest {

    private final ProveedorDAO proveedorDAO = mock(ProveedorDAO.class);
    private final ProveedorRestController controller = new ProveedorRestController(proveedorDAO);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listarYBuscarPorIdDeleganAlDao() {
        List<ProveedorVO> proveedores = List.of(new ProveedorVO());
        ProveedorVO proveedor = new ProveedorVO();
        when(proveedorDAO.listar()).thenReturn(proveedores);
        when(proveedorDAO.buscarPorId(1L)).thenReturn(Optional.of(proveedor));
        when(proveedorDAO.buscarPorId(2L)).thenReturn(Optional.empty());

        assertSame(proveedores, controller.listar());
        assertSame(proveedor, controller.buscarPorId(1L).getBody());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class, () -> controller.buscarPorId(2L)).getStatusCode());
    }

    @Test
    void insertarValidaYDevuelveCreated() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(
                ResponseStatusException.class, () -> controller.insertar(null)).getStatusCode());
        verifyNoInteractions(proveedorDAO);

        ProveedorVO proveedor = new ProveedorVO(null, "Empresa", "Contacto", null, "contacto@example.com",
                null, null);
        when(proveedorDAO.insertar(proveedor)).thenReturn(22L);
        when(proveedorDAO.buscarPorId(22L)).thenReturn(Optional.of(proveedor));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/api/proveedores")));

        var response = controller.insertar(proveedor);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/proveedores/22", response.getHeaders().getLocation().getPath());
        assertSame(proveedor, response.getBody());
    }

    @Test
    void insertarExigeEmpresaContactoYCorreoPeroPermiteNulosEnOtrosCampos() {
        ProveedorVO incompleto = new ProveedorVO(null, "Empresa", null, null, "contacto@example.com", null, null);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(
                ResponseStatusException.class, () -> controller.insertar(incompleto)).getStatusCode());
        verifyNoInteractions(proveedorDAO);
    }

    @Test
    void actualizarAsignaIdYDevuelveElResultadoDelDao() {
        ProveedorVO proveedor = new ProveedorVO(null, "Empresa", "Contacto", null, "contacto@example.com",
                null, null);
        when(proveedorDAO.actualizar(proveedor)).thenReturn(1, 0);

        assertEquals(HttpStatus.NO_CONTENT, controller.actualizar(3L, proveedor).getStatusCode());
        assertEquals(3L, proveedor.getIdProveedor());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class, () -> controller.actualizar(3L, proveedor)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(
                ResponseStatusException.class, () -> controller.actualizar(3L, null)).getStatusCode());
    }

    @Test
    void eliminarDevuelveNoContentONotFound() {
        when(proveedorDAO.eliminar(1L)).thenReturn(1);
        when(proveedorDAO.eliminar(2L)).thenReturn(0);

        assertEquals(HttpStatus.NO_CONTENT, controller.eliminar(1L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class, () -> controller.eliminar(2L)).getStatusCode());
    }
}
