package com.prueba.inventario.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prueba.inventario.model.dao.DetalleVentaDAO;
import com.prueba.inventario.model.dao.ProductoDAO;
import com.prueba.inventario.model.dao.ProveedorDAO;
import com.prueba.inventario.model.dao.VentaDAO;
import com.prueba.inventario.model.vo.ChatMessageVO;
import com.prueba.inventario.model.vo.DetalleVentaVO;
import com.prueba.inventario.model.vo.ProductoVO;
import com.prueba.inventario.model.vo.ProveedorVO;
import com.prueba.inventario.model.vo.VentaVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatbotServiceTest {

    @Test
    void listaProductosDeCategoriaSinReducirLaRespuestaAUnConteo() {
        ChatbotService service = crearServicio();

        String respuesta = service.responder("¿Qué pasteles tenemos?");

        assertTrue(respuesta.contains("Torta de chocolate"));
        assertFalse(respuesta.contains("Pan integral"));
    }

    @Test
    void respondeConteosYResuelveElPrimeroDeLaListaEnElSeguimiento() {
        ChatbotService service = crearServicio();

        String conteo = service.responder("¿Cuántos productos tenemos?");
        String precio = service.responder("¿Y el precio del primero?");

        assertTrue(conteo.contains("Hay 2 registros"));
        assertTrue(precio.contains("Torta de chocolate cuesta 15000 COP."));
    }

    @Test
    void relacionaElProductoConSuProveedor() {
        ChatbotService service = crearServicio();

        String respuesta = service.responder("¿Qué proveedor suministra Torta de chocolate?");

        assertTrue(respuesta.contains("Dulces del Valle"));
    }

    @Test
    void listaProductosAsociadosAlProveedorMencionado() {
        ChatbotService service = crearServicio();

        String respuesta = service.responder("¿Qué productos suministra Dulces del Valle?");

        assertTrue(respuesta.contains("Torta de chocolate"));
        assertFalse(respuesta.contains("Pan integral"));
    }

    @Test
    void separaCantidadDeVentasDeIngresosYListaLasTransaccionesIndividuales() {
        List<VentaVO> ventas = List.of(
                venta(1L, "Completada", "30000"),
                venta(2L, "Completada", "10000"),
                venta(3L, "Anulada", "90000"));
        List<DetalleVentaVO> detalles = List.of(
                detalle(1L, 1L, 1L, "2"),
                detalle(2L, 2L, 1L, "1"),
                detalle(3L, 3L, 2L, "8"));
        ChatbotService service = crearServicio(productos(), ventas, detalles);

        String conteo = service.responder("¿Cuántas ventas se realizaron hoy?");
        String ingresos = service.responder("¿Cuánto dinero se obtuvo hoy?");
        String listado = service.responder("Muéstrame las ventas de hoy");

        assertTrue(conteo.contains("2 ventas"));
        assertFalse(conteo.contains("40000"));
        assertTrue(ingresos.contains("40000.00 COP"));
        assertTrue(ingresos.contains("2 ventas"));
        assertTrue(listado.contains("Venta 1"));
        assertTrue(listado.contains("Venta 2"));
        assertFalse(listado.contains("Venta 3"));
    }

    @Test
    void consultaProductosVendidosYUnidadesSinContarVentasAnuladas() {
        List<VentaVO> ventas = List.of(
                venta(1L, "Completada", "30000"),
                venta(2L, "Anulada", "90000"));
        List<DetalleVentaVO> detalles = List.of(
                detalle(1L, 1L, 1L, "2"),
                detalle(2L, 2L, 1L, "8"));
        ChatbotService service = crearServicio(productos(), ventas, detalles);

        String productosVendidos = service.responder("¿Qué productos se vendieron hoy?");
        String unidades = service.responder("¿Cuántas unidades se vendieron hoy?");

        assertTrue(productosVendidos.contains("Torta de chocolate: 2 unidades"));
        assertFalse(productosVendidos.contains("8 unidades"));
        assertTrue(unidades.contains("2 unidades"));
    }

    @Test
    void resuelveComparacionYSeguimientoDeStockEnLaMismaConversacion() {
        ChatbotService service = crearServicio();

        service.responder("¿Qué productos tenemos disponibles?");
        String comparacion = service.responder("¿Cuál es el más costoso?");
        String stock = service.responder("¿Y cuántos quedan de ese producto?");

        assertTrue(comparacion.contains("Torta de chocolate"));
        assertTrue(stock.contains("Torta de chocolate: 4 en stock."));
    }

    @Test
    void puedeMostrarLosOtrosProductosDeLaListaComparada() {
        ChatbotService service = crearServicio();

        service.responder("¿Qué productos tenemos disponibles?");
        service.responder("¿Cuál es el más costoso?");
        String respuesta = service.responder("¿Y los otros?");

        assertTrue(respuesta.contains("Pan integral"));
        assertFalse(respuesta.contains("Torta de chocolate"));
    }

    @Test
    void reconstruyeContextoTrasRecuperarUnaConversacionYNoLoComparteConOtra() {
        ChatbotService service = crearServicio();
        List<ChatMessageVO> history = List.of(
                mensaje(1L, "USUARIO", "¿Qué productos tenemos disponibles?"),
                mensaje(2L, "ASISTENTE", "Tenemos Torta de chocolate y Pan integral."),
                mensaje(3L, "USUARIO", "¿Cuál es el más costoso?"),
                mensaje(4L, "ASISTENTE", "La Torta de chocolate es la más costosa."));

        String stock = service.responder("¿Cuántos quedan?", history);
        String sinContexto = service.responder("¿Cuántos quedan?", List.of());

        assertTrue(stock.contains("Torta de chocolate: 4 en stock."));
        assertTrue(sinContexto.contains("¿Qué producto"));
    }

    private ChatbotService crearServicio() {
        return crearServicio(productos(), List.of(), List.of());
    }

    private ChatbotService crearServicio(
            List<ProductoVO> productos,
            List<VentaVO> ventas,
            List<DetalleVentaVO> detalles) {
        ProductoDAO productoDAO = mock(ProductoDAO.class);
        VentaDAO ventaDAO = mock(VentaDAO.class);
        DetalleVentaDAO detalleVentaDAO = mock(DetalleVentaDAO.class);
        ProveedorDAO proveedorDAO = mock(ProveedorDAO.class);
        GeminiService geminiService = mock(GeminiService.class);

        ProveedorVO proveedor = new ProveedorVO(7L, "Dulces del Valle", null, null, null, null, "Activo");

        when(productoDAO.listar()).thenReturn(productos);
        when(proveedorDAO.listar()).thenReturn(List.of(proveedor));
        when(ventaDAO.listar()).thenReturn(ventas);
        when(detalleVentaDAO.listar()).thenReturn(detalles);
        return new ChatbotService(productoDAO, ventaDAO, detalleVentaDAO, proveedorDAO, geminiService);
    }

    private List<ProductoVO> productos() {
        ProductoVO torta = new ProductoVO(1L, "Torta de chocolate", null, "Pasteles",
                new BigDecimal("15000"), BigDecimal.ZERO, new BigDecimal("4"),
                BigDecimal.ONE, "unidad", 7L, "Disponible");
        ProductoVO pan = new ProductoVO(2L, "Pan integral", null, "Panadería",
                new BigDecimal("5000"), BigDecimal.ZERO, new BigDecimal("8"),
                BigDecimal.ONE, "unidad", null, "Disponible");
        return List.of(torta, pan);
    }

    private VentaVO venta(Long id, String estado, String total) {
        VentaVO venta = new VentaVO(id, 1L, LocalDateTime.now(), new BigDecimal(total),
                BigDecimal.ZERO, new BigDecimal(total), "Efectivo", estado);
        return venta;
    }

    private DetalleVentaVO detalle(Long id, Long idVenta, Long idProducto, String cantidad) {
        return new DetalleVentaVO(id, idVenta, idProducto,
                new BigDecimal(cantidad), BigDecimal.ONE, new BigDecimal(cantidad));
    }

    private ChatMessageVO mensaje(Long id, String role, String content) {
        return new ChatMessageVO(id, 81L, role, content, LocalDateTime.now());
    }
}
