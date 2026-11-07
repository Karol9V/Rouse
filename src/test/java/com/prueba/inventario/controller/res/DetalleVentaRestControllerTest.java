package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prueba.inventario.model.dao.DetalleVentaDAO;
import com.prueba.inventario.model.vo.DetalleVentaVO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class DetalleVentaRestControllerTest {

    private final DetalleVentaDAO detalleDAO = mock(DetalleVentaDAO.class);
    private final DetalleVentaRestController controller = new DetalleVentaRestController(detalleDAO);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listarFiltraPorVentaSoloCuandoSeIndicaId() {
        List<DetalleVentaVO> todos = List.of(new DetalleVentaVO());
        List<DetalleVentaVO> deVenta = List.of(new DetalleVentaVO());
        when(detalleDAO.listar()).thenReturn(todos);
        when(detalleDAO.listarPorVenta(4L)).thenReturn(deVenta);

        assertSame(todos, controller.listar(null));
        assertSame(deVenta, controller.listar(4L));
    }

    @Test
    void buscarPorIdDevuelveDetalleONotFound() {
        DetalleVentaVO detalle = new DetalleVentaVO();
        when(detalleDAO.buscarPorId(1L)).thenReturn(Optional.of(detalle));
        when(detalleDAO.buscarPorId(2L)).thenReturn(Optional.empty());

        assertSame(detalle, controller.buscarPorId(1L).getBody());
        assertEquals(HttpStatus.NOT_FOUND, controller.buscarPorId(2L).getStatusCode());
    }

    @Test
    void insertarValidaYDevuelveCreated() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.insertar(null).getStatusCode());
        verifyNoInteractions(detalleDAO);

        DetalleVentaVO detalle = new DetalleVentaVO();
        when(detalleDAO.insertar(detalle)).thenReturn(12L);
        when(detalleDAO.buscarPorId(12L)).thenReturn(Optional.of(detalle));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/api/detalles-venta")));

        var response = controller.insertar(detalle);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/detalles-venta/12", response.getHeaders().getLocation().getPath());
        assertSame(detalle, response.getBody());
    }

    @Test
    void actualizarAsignaIdYManejaNoEncontrado() {
        DetalleVentaVO detalle = new DetalleVentaVO();
        when(detalleDAO.actualizar(detalle)).thenReturn(1, 0);

        assertEquals(HttpStatus.NO_CONTENT, controller.actualizar(7L, detalle).getStatusCode());
        assertEquals(7L, detalle.getIdDetalle());
        assertEquals(HttpStatus.NOT_FOUND, controller.actualizar(7L, detalle).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.actualizar(7L, null).getStatusCode());
    }

    @Test
    void eliminarDevuelveNoContentONotFound() {
        when(detalleDAO.eliminar(1L)).thenReturn(1);
        when(detalleDAO.eliminar(2L)).thenReturn(0);

        assertEquals(HttpStatus.NO_CONTENT, controller.eliminar(1L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.eliminar(2L).getStatusCode());
    }
}
