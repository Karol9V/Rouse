package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prueba.inventario.model.dao.DetalleVentaDAO;
import com.prueba.inventario.model.dao.ProductoDAO;
import com.prueba.inventario.model.dao.VentaDAO;
import com.prueba.inventario.model.vo.DetalleVentaVO;
import com.prueba.inventario.model.vo.VentaVO;
import com.prueba.inventario.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

class VentaRestControllerTest {

    private final VentaDAO ventaDAO = mock(VentaDAO.class);
    private final DetalleVentaDAO detalleDAO = mock(DetalleVentaDAO.class);
    private final ProductoDAO productoDAO = mock(ProductoDAO.class);
    private final VentaRestController controller = new VentaRestController(ventaDAO, detalleDAO, productoDAO);
    private final AuthenticatedUser vendedor =
            new AuthenticatedUser(7L, "Ana", "Lopez", "ana@example.com", "Vendedor", "token");

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listarYBuscarPorIdDeleganAlDao() {
        VentaVO venta = new VentaVO();
        List<VentaVO> ventas = List.of(venta);
        when(ventaDAO.listar()).thenReturn(ventas);
        when(ventaDAO.buscarPorId(1L)).thenReturn(Optional.of(venta));
        when(ventaDAO.buscarPorId(2L)).thenReturn(Optional.empty());

        assertSame(ventas, controller.listar());
        assertSame(venta, controller.buscarPorId(1L).getBody());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> controller.buscarPorId(2L)).getStatusCode());
    }

    @Test
    void insertarCalculaImportesYAsignaLaVentaAlVendedorAutenticado() {
        VentaVO ventaPersistida = new VentaVO();
        when(productoDAO.descontarStock(3L, new BigDecimal("2"))).thenReturn(1);
        when(ventaDAO.insertar(any(VentaVO.class))).thenReturn(25L);
        when(ventaDAO.buscarPorId(25L)).thenReturn(Optional.of(ventaPersistida));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/api/ventas")));
        VentaRestController.VentaRequest request = request(
                null, List.of(new VentaRestController.DetalleVentaInput(
                        3L, new BigDecimal("2"), new BigDecimal("12.50"))));

        var response = controller.insertar(request, vendedor);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/ventas/25", response.getHeaders().getLocation().getPath());
        assertSame(ventaPersistida, response.getBody());
        verify(ventaDAO).insertar(org.mockito.ArgumentMatchers.argThat(venta ->
                venta.getIdUsuario().equals(7L)
                        && venta.getSubtotal().compareTo(new BigDecimal("25.00")) == 0
                        && venta.getDescuento().compareTo(BigDecimal.ZERO) == 0
                        && venta.getTotal().compareTo(new BigDecimal("25.00")) == 0));
        verify(detalleDAO).insertar(any(DetalleVentaVO.class));
    }

    @Test
    void insertarRechazaDatosInvalidosAntesDeAccederALosDaos() {
        var emptyDetails = request(null, List.of());
        var noPayment = new VentaRestController.VentaRequest(null, null, null, null, null,
                " ", null, List.of(new VentaRestController.DetalleVentaInput(
                        3L, BigDecimal.ONE, BigDecimal.ONE)));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.insertar(null, vendedor)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.insertar(emptyDetails, vendedor)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.insertar(noPayment, vendedor)).getStatusCode());
        verifyNoInteractions(ventaDAO, detalleDAO, productoDAO);
    }

    @Test
    void insertarDevuelveConflictCuandoNoHayStock() {
        when(productoDAO.descontarStock(3L, BigDecimal.ONE)).thenReturn(0);
        var request = request(null, List.of(new VentaRestController.DetalleVentaInput(
                3L, BigDecimal.ONE, BigDecimal.ONE)));

        when(productoDAO.buscarPorId(3L)).thenReturn(Optional.of(new com.prueba.inventario.model.vo.ProductoVO()));
        ResponseStatusException insufficientStock = assertThrows(
                ResponseStatusException.class, () -> controller.insertar(request, vendedor));
        assertEquals(HttpStatus.CONFLICT, insufficientStock.getStatusCode());

        when(productoDAO.buscarPorId(3L)).thenReturn(Optional.empty());
        ResponseStatusException missingProduct = assertThrows(
                ResponseStatusException.class, () -> controller.insertar(request, vendedor));
        assertEquals(HttpStatus.CONFLICT, missingProduct.getStatusCode());
        verifyNoInteractions(ventaDAO, detalleDAO);
    }

    @Test
    void administradorDebeIndicarUsuarioYVentaActualizaConservandoCamposOmisos() {
        AuthenticatedUser admin =
                new AuthenticatedUser(1L, "Luis", "Perez", "luis@example.com", "Administrador", "token");
        var request = request(null, List.of(new VentaRestController.DetalleVentaInput(
                3L, BigDecimal.ONE, BigDecimal.ONE)));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.insertar(request, admin)).getStatusCode());

        VentaVO actual = new VentaVO(8L, 7L, null, new BigDecimal("10"),
                BigDecimal.ZERO, new BigDecimal("10"), "Efectivo", "Completada");
        when(ventaDAO.buscarPorId(8L)).thenReturn(Optional.of(actual));
        when(ventaDAO.actualizar(any(VentaVO.class))).thenReturn(1);
        VentaVO cambios = new VentaVO();

        assertEquals(HttpStatus.NO_CONTENT, controller.actualizar(8L, cambios).getStatusCode());
        assertEquals(8L, cambios.getIdVenta());
        assertEquals(7L, cambios.getIdUsuario());
        assertEquals("Efectivo", cambios.getMetodoPago());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> controller.actualizar(8L, null)).getStatusCode());

        when(ventaDAO.buscarPorId(9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.actualizar(9L, new VentaVO())).getStatusCode());
    }

    @Test
    void eliminarDevuelveNoContentONotFound() {
        when(ventaDAO.eliminar(1L)).thenReturn(1);
        when(ventaDAO.eliminar(2L)).thenReturn(0);

        assertEquals(HttpStatus.NO_CONTENT, controller.eliminar(1L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> controller.eliminar(2L)).getStatusCode());
    }

    private VentaRestController.VentaRequest request(
            Long idUsuario, List<VentaRestController.DetalleVentaInput> detalles) {
        return new VentaRestController.VentaRequest(
                idUsuario, null, null, null, null, "Efectivo", "Completada", detalles);
    }
}
