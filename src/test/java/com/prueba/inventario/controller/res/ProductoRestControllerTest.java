package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prueba.inventario.model.dao.ProductoDAO;
import com.prueba.inventario.model.vo.ProductoVO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

class ProductoRestControllerTest {

    private final ProductoDAO productoDAO = mock(ProductoDAO.class);
    private final ProductoRestController controller = new ProductoRestController(productoDAO);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listarDevuelveProductosDelDao() {
        List<ProductoVO> productos = List.of(new ProductoVO());
        when(productoDAO.listar()).thenReturn(productos);

        assertSame(productos, controller.listar());
    }

    @Test
    void buscarPorIdDevuelveProductoONotFound() {
        ProductoVO producto = new ProductoVO();
        when(productoDAO.buscarPorId(1L)).thenReturn(Optional.of(producto));
        when(productoDAO.buscarPorId(2L)).thenReturn(Optional.empty());

        assertSame(producto, controller.buscarPorId(1L).getBody());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> controller.buscarPorId(2L)).getStatusCode());
    }

    @Test
    void insertarDevuelveBadRequestCuandoElProductoEsNulo() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(
                ResponseStatusException.class, () -> controller.insertar(null)).getStatusCode());
        verifyNoInteractions(productoDAO);
    }

    @Test
    void insertarDevuelveCreatedConLocationYProducto() {
        ProductoVO producto = new ProductoVO();
        when(productoDAO.insertar(producto)).thenReturn(15L);
        when(productoDAO.buscarPorId(15L)).thenReturn(Optional.of(producto));
        bindRequest("POST", "/api/productos");

        var response = controller.insertar(producto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/productos/15", response.getHeaders().getLocation().getPath());
        assertSame(producto, response.getBody());
    }

    @Test
    void actualizarAsignaIdYDevuelveElResultadoDelDao() {
        ProductoVO producto = new ProductoVO();
        when(productoDAO.actualizar(producto)).thenReturn(1, 0);

        assertEquals(HttpStatus.NO_CONTENT, controller.actualizar(8L, producto).getStatusCode());
        assertEquals(8L, producto.getIdProducto());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class, () -> controller.actualizar(8L, producto)).getStatusCode());
    }

    @Test
    void actualizarRechazaCuerpoNulo() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(
                ResponseStatusException.class, () -> controller.actualizar(8L, null)).getStatusCode());
        verifyNoInteractions(productoDAO);
    }

    @Test
    void eliminarDevuelveNoContentONotFound() {
        when(productoDAO.eliminar(1L)).thenReturn(1);
        when(productoDAO.eliminar(2L)).thenReturn(0);

        assertEquals(HttpStatus.NO_CONTENT, controller.eliminar(1L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class, () -> controller.eliminar(2L)).getStatusCode());
    }

    private void bindRequest(String method, String path) {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(method, path)));
    }
}
