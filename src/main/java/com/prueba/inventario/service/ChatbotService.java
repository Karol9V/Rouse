package com.prueba.inventario.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.inventario.model.dao.DetalleVentaDAO;
import com.prueba.inventario.model.dao.ProductoDAO;
import com.prueba.inventario.model.dao.ProveedorDAO;
import com.prueba.inventario.model.dao.VentaDAO;
import com.prueba.inventario.model.vo.ChatMessageVO;
import com.prueba.inventario.model.vo.DetalleVentaVO;
import com.prueba.inventario.model.vo.ProductoVO;
import com.prueba.inventario.model.vo.ProveedorVO;
import com.prueba.inventario.model.vo.VentaVO;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Analiza las consultas del usuario, recupera datos reales del sistema y prepara un contexto
 * estructurado para Gemini.
 */
@Service
public class ChatbotService {

    private static final int MAX_MENSAJE_LENGTH = 1000;
    private final Map<String, ConversationMemory> memories = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProductoDAO productoDAO;
    private final VentaDAO ventaDAO;
    private final DetalleVentaDAO detalleVentaDAO;
    private final ProveedorDAO proveedorDAO;
    private final GeminiService geminiService;

    public ChatbotService(
            ProductoDAO productoDAO,
            VentaDAO ventaDAO,
            DetalleVentaDAO detalleVentaDAO,
            ProveedorDAO proveedorDAO,
            GeminiService geminiService) {
        this.productoDAO = productoDAO;
        this.ventaDAO = ventaDAO;
        this.detalleVentaDAO = detalleVentaDAO;
        this.proveedorDAO = proveedorDAO;
        this.geminiService = geminiService;
    }

    public String responder(String mensaje) {
        return responder(mensaje, (HttpSession) null);
    }

    public String responder(String mensaje, HttpSession session) {
        String key = session == null ? "default" : "session:" + session.getId();
        ConversationMemory memory = memories.computeIfAbsent(key, ignored -> new ConversationMemory());
        return responder(mensaje, memory, List.of());
    }

    public String responder(
            String mensaje,
            List<ChatMessageVO> historial) {
        ConversationMemory memory = reconstruirMemoria(historial);
        return responder(mensaje, memory, historial);
    }

    private String responder(
            String mensaje,
            ConversationMemory memory,
            List<ChatMessageVO> historial) {
        String texto = normalizarMensaje(mensaje);
        if (texto.isBlank()) {
            throw new IllegalArgumentException("Debes escribir una pregunta antes de enviar el mensaje.");
        }
        if (texto.length() > MAX_MENSAJE_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo. Usa máximo 1000 caracteres.");
        }

        QueryContext queryContext = inferirConsulta(texto, memory);
        String prompt = construirPrompt(texto, queryContext);
        String respuestaGemini = geminiService.generateResponse(prompt);
        if (respuestaGemini != null && !respuestaGemini.isBlank()) {
            memory.setUltimoProducto(productoPrincipal(queryContext));
            memory.setUltimosProductos(productosParaMemoria(queryContext));
            return respuestaGemini.trim();
        }

        String respuestaLocal = respuestaLocal(queryContext, memory);
        memory.setUltimoProducto(productoPrincipal(queryContext));
        memory.setUltimosProductos(productosParaMemoria(queryContext));
        return respuestaLocal;
    }

    private List<ProductoVO> productosParaMemoria(QueryContext context) {
        return context.productosParaMemoria.isEmpty()
                ? context.productos : context.productosParaMemoria;
    }

    private ConversationMemory reconstruirMemoria(List<ChatMessageVO> historial) {
        ConversationMemory memory = new ConversationMemory();
        if (historial == null || historial.isEmpty()) {
            return memory;
        }
        List<ProductoVO> productos = productoDAO.listar();
        for (ChatMessageVO message : historial) {
            if ("USUARIO".equals(message.emisor())) {
                QueryContext previous = inferirConsulta(message.contenido(), memory);
                if (!previous.productos.isEmpty()) {
                    memory.setUltimosProductos(productosParaMemoria(previous));
                    memory.setUltimoProducto(productoPrincipal(previous));
                }
            } else if ("ASISTENTE".equals(message.emisor())) {
                List<ProductoVO> mentioned = productosMencionados(
                        normalizarBusqueda(message.contenido()), productos);
                if (mentioned.size() == 1) {
                    memory.setUltimoProducto(mentioned.get(0));
                }
            }
        }
        return memory;
    }

    private ProductoVO productoPrincipal(QueryContext context) {
        if (context.productoReferencia != null) {
            return context.productoReferencia;
        }
        return context.productos.isEmpty() ? null : context.productos.get(0);
    }

    private String normalizarMensaje(String mensaje) {
        return mensaje == null ? "" : mensaje.replaceAll("\\s+", " ").trim();
    }

    private String construirPrompt(String mensaje, QueryContext context) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tipoConsulta", context.tipoConsulta);
            payload.put("totalResultados", context.totalResultados);
            payload.put("pregunta", mensaje);
            payload.put("registros", context.registros);
            payload.put("referenciaAnterior", context.productoReferencia == null ? null : context.productoReferencia.getNombre());

            String json = objectMapper.writeValueAsString(payload);
            return "Eres un asistente administrativo amable, natural y profesional de una pastelería. "
                    + "Responde en español claro y cercano, con variación natural y sin repetir un saludo en cada turno. "
                    + "Los registros del contexto son la única fuente de datos administrativos; no inventes ni calcules "
                    + "cantidades de ventas a partir del dinero ni dinero a partir del número de ventas. "
                    + "Distingue explícitamente cantidad de transacciones, ingresos monetarios y unidades vendidas. "
                    + "El contexto conversacional relevante ya fue resuelto por el backend; no inventes referencias. "
                    + "Si hay más de una interpretación "
                    + "posible, pide una aclaración. No generes ni ejecutes SQL y no sugieras cambios de datos. "
                    + "Ajusta el formato a la pregunta: listados en viñetas, comparaciones explicando el criterio y "
                    + "ventas con identificador, fecha y valor cuando estén disponibles. "
                    + "Contexto JSON: " + json;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo preparar el contexto seguro para Gemini.", ex);
        }
    }

    private QueryContext inferirConsulta(String texto, ConversationMemory memory) {
        String textoLower = normalizarBusqueda(texto);
        List<ProductoVO> productos = productoDAO.listar();
        List<ProveedorVO> proveedores = proveedorDAO.listar();
        List<VentaVO> ventas = ventaDAO.listar();
        List<DetalleVentaVO> detalles = detalleVentaDAO.listar();
        QueryContext context = new QueryContext();
        context.pregunta = textoLower;

        ProductoVO productoReferencia = resolverProductoReferencia(textoLower, memory, productos);
        context.productoReferencia = productoReferencia;
        List<ProductoVO> coincidenciasProducto = productosMencionados(textoLower, productos);
        List<ProductoVO> productosMencionados = coincidenciasProducto.isEmpty() && productoReferencia != null
                ? List.of(productoReferencia) : coincidenciasProducto;
        List<ProductoVO> productosFiltrados = filtrarPorCategoria(textoLower, productos);
        if (!productosMencionados.isEmpty()) {
            productosFiltrados = productosMencionados;
        }

        if (textoLower.contains("otro") && !memory.getUltimosProductos().isEmpty()) {
            List<ProductoVO> otros = memory.getUltimosProductos().stream()
                    .filter(producto -> memory.getUltimoProducto() == null
                            || !Objects.equals(producto.getIdProducto(), memory.getUltimoProducto().getIdProducto()))
                    .toList();
            if (!otros.isEmpty()) {
                context.tipoConsulta = "LISTAR_PRODUCTOS";
                context.productos = otros;
                context.registros = otros.stream().map(this::toProductoMap).collect(Collectors.toList());
                context.totalResultados = context.registros.size();
                return context;
            }
        }

        if (esReferenciaConversacional(textoLower) && productoReferencia == null) {
            context.tipoConsulta = "SOLICITAR_ACLARACION";
            return context;
        }

        if (esConsultaVentas(textoLower)) {
            List<VentaVO> ventasPeriodo = ventas.stream()
                    .filter(this::esVentaValida)
                    .filter(venta -> dentroDelPeriodo(venta.getFechaVenta() == null
                            ? null : venta.getFechaVenta().toLocalDate(), textoLower))
                    .toList();
            List<Long> idsVenta = ventasPeriodo.stream().map(VentaVO::getIdVenta).toList();
            List<DetalleVentaVO> detallesPeriodo = detalles.stream()
                    .filter(detalle -> idsVenta.contains(detalle.getIdVenta()))
                    .toList();
            boolean consultaProductosVendidos = textoLower.contains("producto")
                    && (textoLower.contains("vend") || textoLower.contains("se vendio"));
            if (textoLower.contains("mas vendido") || textoLower.contains("mas se vende")) {
                context.tipoConsulta = "PRODUCTOS_MAS_VENDIDOS";
                context.registros = productosVendidos(detallesPeriodo, productos, 5);
            } else if (consultaProductosVendidos) {
                context.tipoConsulta = "CONSULTAR_PRODUCTOS_VENDIDOS";
                context.registros = productosVendidos(detallesPeriodo, productos, Integer.MAX_VALUE);
            } else if (textoLower.contains("unidad") && (textoLower.contains("cuanto")
                    || textoLower.contains("cuantas") || textoLower.contains("cantidad"))) {
                BigDecimal unidades = detallesPeriodo.stream()
                        .filter(detalle -> productosMencionados.isEmpty()
                                || productosMencionados.stream().anyMatch(producto ->
                                Objects.equals(producto.getIdProducto(), detalle.getIdProducto())))
                        .map(DetalleVentaVO::getCantidad)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                context.tipoConsulta = "CONTAR_UNIDADES_VENDIDAS";
                context.registros = List.of(Map.of("unidadesVendidas", unidades));
            } else if (textoLower.contains("promedio")) {
                BigDecimal ingresos = sumarVentas(ventasPeriodo);
                BigDecimal promedio = ventasPeriodo.isEmpty() ? BigDecimal.ZERO
                        : ingresos.divide(BigDecimal.valueOf(ventasPeriodo.size()), 2, RoundingMode.HALF_UP);
                context.tipoConsulta = "CONSULTAR_PROMEDIO_VENTAS";
                context.registros = List.of(Map.of(
                        "promedioVenta", promedio,
                        "cantidadVentas", ventasPeriodo.size()));
            } else if (esConsultaConteo(textoLower)) {
                context.tipoConsulta = "CONTAR_VENTAS";
                context.registros = List.of(Map.of("cantidadVentas", ventasPeriodo.size()));
            } else if (textoLower.contains("dinero") || textoLower.contains("ingreso")
                    || textoLower.contains("recaud") || textoLower.contains("obtuvo")
                    || textoLower.contains("total")) {
                context.tipoConsulta = "CONSULTAR_TOTAL_VENTAS";
                context.registros = List.of(Map.of(
                        "ingresosTotales", sumarVentas(ventasPeriodo),
                        "cantidadVentas", ventasPeriodo.size()));
            } else {
                context.tipoConsulta = "LISTAR_VENTAS";
                context.registros = ventasPeriodo.stream().map(this::toVentaMap).collect(Collectors.toList());
            }
            context.totalResultados = context.registros.size();
            return context;
        }

        boolean consultaProveedor = textoLower.contains("proveedor") || textoLower.contains("suministra")
                || textoLower.contains("suministrador");
        if (consultaProveedor) {
            List<ProveedorVO> proveedorMencionado = proveedores.stream()
                    .filter(proveedor -> proveedor.getNombreEmpresa() != null
                            && textoLower.contains(normalizarBusqueda(proveedor.getNombreEmpresa())))
                    .toList();
            if (!proveedorMencionado.isEmpty()) {
                context.tipoConsulta = "PRODUCTOS_POR_PROVEEDOR";
                List<Long> idsProveedor = proveedorMencionado.stream()
                        .map(ProveedorVO::getIdProveedor).toList();
                List<ProductoVO> productosProveedor = productos.stream()
                        .filter(producto -> idsProveedor.contains(producto.getIdProveedor()))
                        .toList();
                context.productos = productosProveedor;
                context.registros = productosProveedor.stream()
                        .map(producto -> toProductoConProveedorMap(producto, proveedorMencionado))
                        .collect(Collectors.toList());
            } else if (!productosFiltrados.isEmpty() && (textoLower.contains("producto")
                    || textoLower.contains("pastel") || textoLower.contains("pan") || textoLower.contains("cafe")
                    || !productosMencionados.isEmpty())) {
                context.tipoConsulta = "PROVEEDOR_DE_PRODUCTO";
                context.productos = productosFiltrados;
                context.registros = productosFiltrados.stream()
                        .map(producto -> toProductoConProveedorMap(producto, proveedores))
                        .collect(Collectors.toList());
            } else {
                context.tipoConsulta = "LISTAR_PROVEEDORES";
                context.registros = proveedores.stream()
                        .map(this::toProveedorMap).collect(Collectors.toList());
            }
            if (esConsultaConteo(textoLower)) {
                context.tipoConsulta = "CONTAR_REGISTROS";
            }
            context.totalResultados = context.registros.size();
            return context;
        }

        boolean esComparacionPrecio = textoLower.contains("mas caro") || textoLower.contains("mas costoso")
                || textoLower.contains("mas economico") || textoLower.contains("mas barato")
                || textoLower.contains("menor precio") || textoLower.contains("mayor precio");
        if (!esComparacionPrecio && (textoLower.contains("precio")
                || textoLower.contains("cuesta") || textoLower.contains("vale"))) {
            context.tipoConsulta = "CONSULTA_PRECIO";
            context.productos = productosFiltrados;
            context.registros = productosFiltrados.stream().map(producto -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("idProducto", producto.getIdProducto());
                item.put("nombre", producto.getNombre());
                item.put("precioVenta", producto.getPrecioVenta());
                item.put("categoria", producto.getCategoria());
                return item;
            }).collect(Collectors.toList());
            context.totalResultados = context.registros.size();
            return context;
        }

        if (esComparacionPrecio) {
            boolean maximo = textoLower.contains("mas caro") || textoLower.contains("mas costoso")
                    || textoLower.contains("mayor precio");
            List<ProductoVO> candidatos = productosMencionados.isEmpty()
                    ? memory.getUltimosProductos() : productosMencionados;
            if (candidatos.isEmpty()) {
                context.tipoConsulta = "SOLICITAR_ACLARACION";
                return context;
            }
            ProductoVO elegido = candidatos.stream()
                    .filter(producto -> producto.getPrecioVenta() != null)
                    .max(maximo
                            ? Comparator.comparing(ProductoVO::getPrecioVenta)
                            : Comparator.comparing(ProductoVO::getPrecioVenta).reversed())
                    .orElse(null);
            context.tipoConsulta = "COMPARAR_PRODUCTOS";
            if (elegido != null) {
                context.productoReferencia = elegido;
                context.productos = List.of(elegido);
                context.productosParaMemoria = candidatos;
                context.registros = List.of(toProductoMap(elegido));
                context.compararMaximo = maximo;
            }
            context.totalResultados = context.registros.size();
            return context;
        }

        if (textoLower.contains("agotad") || textoLower.contains("sin stock") || textoLower.contains("sin existencias")) {
            context.tipoConsulta = "LISTAR_PRODUCTOS_AGOTADOS";
            List<ProductoVO> agotados = productos.stream()
                    .filter(producto -> producto.getStock() != null && producto.getStock().compareTo(BigDecimal.ZERO) <= 0)
                    .toList();
            context.productos = agotados;
            context.registros = agotados.stream().map(this::toProductoMap).collect(Collectors.toList());
            context.totalResultados = context.registros.size();
            return context;
        }

        if (textoLower.contains("stock bajo") || textoLower.contains("pocas existencias") || textoLower.contains("bajo stock") || textoLower.contains("crítico") || textoLower.contains("critico")) {
            context.tipoConsulta = "LISTAR_PRODUCTOS_STOCK_BAJO";
            List<ProductoVO> bajoStock = productos.stream()
                    .filter(producto -> producto.getStock() != null && producto.getStockMinimo() != null
                            && producto.getStock().compareTo(producto.getStockMinimo()) <= 0)
                    .toList();
            context.productos = bajoStock;
            context.registros = bajoStock.stream().map(this::toProductoMap).collect(Collectors.toList());
            context.totalResultados = context.registros.size();
            return context;
        }

        if (textoLower.contains("disponible") || textoLower.contains("que productos tenemos")
                || textoLower.contains("que tenemos") || textoLower.contains("que pasteles tenemos")) {
            List<ProductoVO> disponibles = productosFiltrados.stream().filter(this::estaDisponible).toList();
            context.productos = disponibles;
            context.registros = disponibles.stream().map(this::toProductoMap).collect(Collectors.toList());
            context.tipoConsulta = esConsultaConteo(textoLower)
                    ? "CONTAR_REGISTROS" : "LISTAR_PRODUCTOS_DISPONIBLES";
            context.totalResultados = context.registros.size();
            return context;
        }

        if (textoLower.contains("stock") || textoLower.contains("existencia") || textoLower.contains("queda")) {
            context.tipoConsulta = "CONSULTA_STOCK";
            context.productos = productosFiltrados;
            context.registros = productosFiltrados.stream().map(this::toProductoMap).collect(Collectors.toList());
            context.totalResultados = context.registros.size();
            return context;
        }

        if (esConsultaConteo(textoLower)) {
            context.tipoConsulta = "CONTAR_REGISTROS";
            if (esConsultaProducto(textoLower)) {
                context.productos = productosFiltrados;
                context.registros = productosFiltrados.stream().map(this::toProductoMap).collect(Collectors.toList());
            } else if (textoLower.contains("venta")) {
                context.registros = ventas.stream().map(this::toVentaMap).collect(Collectors.toList());
            } else if (textoLower.contains("proveedor")) {
                context.registros = proveedores.stream().map(this::toProveedorMap).collect(Collectors.toList());
            } else {
                context.registros = productos.stream().map(this::toProductoMap).collect(Collectors.toList());
            }
            context.totalResultados = context.registros.size();
            return context;
        }

        context.tipoConsulta = "LISTAR_PRODUCTOS";
        context.productos = productosFiltrados;
        context.registros = productosFiltrados.stream().map(this::toProductoMap).collect(Collectors.toList());
        context.totalResultados = context.registros.size();
        return context;
    }

    private String normalizarBusqueda(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private List<ProductoVO> productosMencionados(String texto, List<ProductoVO> productos) {
        return productos.stream()
                .filter(producto -> producto.getNombre() != null
                        && texto.contains(normalizarBusqueda(producto.getNombre())))
                .sorted(Comparator.comparingInt(
                        (ProductoVO producto) -> -normalizarBusqueda(producto.getNombre()).length()))
                .toList();
    }

    private List<ProductoVO> filtrarPorCategoria(String texto, List<ProductoVO> productos) {
        String categoria = null;
        if (texto.contains("pastel") || texto.contains("torta")) {
            categoria = "pastel";
        } else if (texto.contains("pan")) {
            categoria = "pan";
        } else if (texto.contains("cafe")) {
            categoria = "cafe";
        }
        if (categoria == null) {
            return productos;
        }
        String filtro = categoria;
        return productos.stream()
                .filter(producto -> normalizarBusqueda(producto.getCategoria()).contains(filtro)
                        || normalizarBusqueda(producto.getNombre()).contains(filtro))
                .toList();
    }

    private boolean esConsultaProducto(String texto) {
        return texto.contains("producto") || texto.contains("pastel") || texto.contains("torta")
                || texto.contains("pan") || texto.contains("cafe");
    }

    private boolean esConsultaConteo(String texto) {
        return texto.contains("cuantos") || texto.contains("cuantas")
                || texto.contains("cantidad de") || texto.contains("total de");
    }

    private boolean esReferenciaConversacional(String texto) {
        return texto.contains("ese producto") || texto.contains("este producto")
                || texto.contains("el primero") || texto.contains("la primera")
                || texto.contains("el segundo") || texto.contains("el tercero")
                || texto.contains("su precio") || texto.contains("cuanto cuesta")
                || texto.contains("cuanto queda") || texto.contains("cuantos quedan")
                || texto.contains("cuantas quedan") || texto.contains("de ese")
                || texto.contains("de la lista") || texto.contains("los anteriores")
                || texto.contains("los otros") || texto.contains("las otras");
    }

    private boolean esConsultaVentas(String texto) {
        return texto.contains("venta") || texto.contains("vend") || texto.contains("ingreso")
                || texto.contains("dinero") || texto.contains("recaud") || texto.contains("obtuvo");
    }

    private boolean dentroDelPeriodo(LocalDate fecha, String texto) {
        if (fecha == null) {
            return false;
        }
        LocalDate hoy = LocalDate.now();
        if (texto.contains("hoy") || texto.contains("diaria") || texto.contains("diario")
                || texto.contains("del dia") || texto.contains("de hoy")) {
            return fecha.equals(hoy);
        }
        if (texto.contains("ayer")) {
            return fecha.equals(hoy.minusDays(1));
        }
        if (texto.contains("semana") || texto.contains("semanal")) {
            return !fecha.isBefore(hoy.minusDays(6)) && !fecha.isAfter(hoy);
        }
        if (texto.contains("mes") || texto.contains("mensual")) {
            return !fecha.isBefore(hoy.withDayOfMonth(1)) && !fecha.isAfter(hoy);
        }
        return true;
    }

    private boolean esVentaValida(VentaVO venta) {
        String estado = normalizarBusqueda(venta.getEstado());
        return !estado.contains("anulad") && !estado.contains("cancelad")
                && !estado.contains("rechazad") && !estado.contains("pendient");
    }

    private BigDecimal sumarVentas(List<VentaVO> ventas) {
        return ventas.stream()
                .map(VentaVO::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Map<String, Object>> productosVendidos(
            List<DetalleVentaVO> detalles,
            List<ProductoVO> productos,
            int limite) {
        Map<Long, BigDecimal> unidadesPorProducto = detalles.stream()
                .filter(detalle -> detalle.getIdProducto() != null && detalle.getCantidad() != null)
                .collect(Collectors.groupingBy(DetalleVentaVO::getIdProducto,
                        Collectors.reducing(BigDecimal.ZERO, DetalleVentaVO::getCantidad, BigDecimal::add)));
        return unidadesPorProducto.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .limit(limite)
                .map(entry -> {
                    ProductoVO producto = buscarProductoPorId(productos, entry.getKey());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("idProducto", entry.getKey());
                    item.put("nombre", producto == null
                            ? "Producto " + entry.getKey() : producto.getNombre());
                    item.put("unidadesVendidas", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private ProductoVO buscarProductoPorId(List<ProductoVO> productos, Long idProducto) {
        return productos.stream()
                .filter(producto -> Objects.equals(producto.getIdProducto(), idProducto))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> toProductoConProveedorMap(ProductoVO producto, List<ProveedorVO> proveedores) {
        Map<String, Object> item = toProductoMap(producto);
        ProveedorVO proveedor = proveedores.stream()
                .filter(candidato -> Objects.equals(candidato.getIdProveedor(), producto.getIdProveedor()))
                .findFirst()
                .orElse(null);
        item.put("proveedor", proveedor == null ? null : proveedor.getNombreEmpresa());
        return item;
    }

    private ProductoVO resolverProductoReferencia(String texto, ConversationMemory memory, List<ProductoVO> productos) {
        ProductoVO mencionado = productosMencionados(texto, productos).stream().findFirst().orElse(null);
        if (mencionado != null) {
            return mencionado;
        }
        if (memory.getUltimosProductos() != null && !memory.getUltimosProductos().isEmpty()) {
            int indice = texto.contains("segundo") ? 1
                    : texto.contains("tercero") ? 2
                    : texto.contains("ultimo") ? memory.getUltimosProductos().size() - 1
                    : texto.contains("primero") || texto.contains("primer producto") || texto.contains("primera")
                            ? 0 : -1;
            if (indice >= 0 && indice < memory.getUltimosProductos().size()) {
                return memory.getUltimosProductos().get(indice);
            }
        }
        if (texto.contains("ese producto") || texto.contains("este producto") || texto.contains("anterior")
                || texto.contains("de la lista") || texto.contains("su precio") || texto.contains("y su")) {
            return memory.getUltimoProducto();
        }
        if (texto.contains("precio") || texto.contains("stock") || texto.contains("existencia")
                || texto.contains("queda") || texto.contains("cuesta")) {
            return memory.getUltimoProducto();
        }
        return null;
    }

    private boolean estaDisponible(ProductoVO producto) {
        if (producto.getEstado() != null && producto.getEstado().equalsIgnoreCase("Disponible")) {
            return true;
        }
        return producto.getStock() != null && producto.getStock().compareTo(BigDecimal.ZERO) > 0;
    }

    private String respuestaLocal(QueryContext context, ConversationMemory memory) {
        String tipo = context.tipoConsulta;
        if (tipo == null) {
            return "Mi especialidad es ayudar con la gestión de la pastelería. Consulta inventario, productos, proveedores o ventas con datos reales del sistema.";
        }

        switch (tipo) {
            case "CONSULTA_PRECIO" -> {
                if (context.registros == null || context.registros.isEmpty()) {
                    return "No encontré un producto con ese nombre para consultar el precio.";
                }
                return context.registros.stream()
                        .map(item -> {
                            Map<?, ?> registro = (Map<?, ?>) item;
                            Object nombre = registro.get("nombre");
                            Object precio = registro.get("precioVenta");
                            return nombre + " cuesta " + precio + " COP.";
                        })
                        .collect(Collectors.joining(" "));
            }
            case "LISTAR_PRODUCTOS_DISPONIBLES" -> {
                if (context.registros == null || context.registros.isEmpty()) {
                    return "No hay productos disponibles actualmente.";
                }
                return "Los productos disponibles son: " + context.registros.stream()
                        .map(item -> {
                            Map<?, ?> registro = (Map<?, ?>) item;
                            return registro.get("nombre") + " (" + registro.get("stock") + " "
                                    + unidadMedida(registro) + ")";
                        })
                        .collect(Collectors.joining(", ")) + ".";
            }
            case "LISTAR_PRODUCTOS_AGOTADOS" -> {
                if (context.registros == null || context.registros.isEmpty()) {
                    return "No hay productos agotados en esta base de datos.";
                }
                return "Los productos agotados son: " + context.registros.stream()
                        .map(item -> (String) ((Map<?, ?>) item).get("nombre"))
                        .collect(Collectors.joining(", ")) + ".";
            }
            case "LISTAR_PRODUCTOS_STOCK_BAJO" -> {
                if (context.registros == null || context.registros.isEmpty()) {
                    return "No hay productos con stock bajo o crítico en este momento.";
                }
                return "Los productos con existencias bajas son: " + context.registros.stream()
                        .map(item -> {
                            Map<?, ?> registro = (Map<?, ?>) item;
                            return registro.get("nombre") + ": " + registro.get("stock") + " "
                                    + unidadMedida(registro) + " (mínimo " + registro.get("stockMinimo") + ")";
                        })
                        .collect(Collectors.joining(", ")) + ".";
            }
            case "LISTAR_PROVEEDORES" -> {
                if (context.registros == null || context.registros.isEmpty()) {
                    return "Actualmente no hay proveedores registrados.";
                }
                return "Los proveedores registrados son: " + context.registros.stream()
                        .map(item -> (String) ((Map<?, ?>) item).get("nombreEmpresa"))
                        .collect(Collectors.joining(", ")) + ".";
            }
            case "CONTAR_VENTAS" -> {
                int count = numero(context, "cantidadVentas");
                return etiquetaPeriodo(context) + " se registraron " + count + (count == 1 ? " venta." : " ventas.");
            }
            case "CONSULTAR_TOTAL_VENTAS" -> {
                BigDecimal total = decimal(context, "ingresosTotales");
                return "Los ingresos " + etiquetaPeriodo(context) + " fueron "
                        + formatoDinero(total) + " COP en " + numero(context, "cantidadVentas")
                        + (numero(context, "cantidadVentas") == 1 ? " venta." : " ventas.");
            }
            case "CONSULTAR_PROMEDIO_VENTAS" -> {
                return "El valor promedio de las ventas " + etiquetaPeriodo(context) + " fue "
                        + formatoDinero(decimal(context, "promedioVenta")) + " COP, calculado sobre "
                        + numero(context, "cantidadVentas")
                        + (numero(context, "cantidadVentas") == 1 ? " venta." : " ventas.");
            }
            case "LISTAR_VENTAS" -> {
                if (context.registros.isEmpty()) {
                    return "No encontré ventas válidas " + etiquetaPeriodo(context) + ".";
                }
                return "Ventas " + etiquetaPeriodo(context) + ":\n" + context.registros.stream()
                        .map(item -> {
                            Map<?, ?> registro = (Map<?, ?>) item;
                            return "• Venta " + registro.get("idVenta") + " — "
                                    + registro.get("fechaVenta") + " — "
                                    + formatoDinero(decimal(registro.get("total"))) + " COP";
                        })
                        .collect(Collectors.joining("\n"));
            }
            case "CONTAR_UNIDADES_VENDIDAS" -> {
                BigDecimal units = decimal(context, "unidadesVendidas");
                return "Se vendieron " + units.stripTrailingZeros().toPlainString()
                        + " unidades " + etiquetaPeriodo(context) + ".";
            }
            case "CONSULTAR_PRODUCTOS_VENDIDOS", "PRODUCTOS_MAS_VENDIDOS" -> {
                if (context.registros.isEmpty()) {
                    return "No encontré productos vendidos " + etiquetaPeriodo(context) + ".";
                }
                String prefix = "PRODUCTOS_MAS_VENDIDOS".equals(tipo)
                        ? "Los productos más vendidos " : "Los productos vendidos ";
                return prefix + etiquetaPeriodo(context) + " son:\n" + context.registros.stream()
                        .map(item -> {
                            Map<?, ?> registro = (Map<?, ?>) item;
                            return "• " + registro.get("nombre") + ": "
                                    + registro.get("unidadesVendidas") + " unidades";
                        })
                        .collect(Collectors.joining("\n"));
            }
            case "COMPARAR_PRODUCTOS" -> {
                if (context.registros.isEmpty()) {
                    return "No pude comparar los productos porque no tengo una lista previa con precios.";
                }
                Map<?, ?> producto = (Map<?, ?>) context.registros.get(0);
                return "El producto " + producto.get("nombre") + " tiene el precio "
                        + (context.compararMaximo ? "más alto: " : "más bajo: ")
                        + formatoDinero(decimal(producto.get("precioVenta"))) + " COP.";
            }
            case "CONSULTA_STOCK" -> {
                if (context.registros == null || context.registros.isEmpty()) {
                    return "No encontré productos que coincidan con esa consulta de existencias.";
                }
                return context.registros.stream()
                        .map(item -> {
                            Map<?, ?> registro = (Map<?, ?>) item;
                            return registro.get("nombre") + ": " + registro.get("stock") + " en stock.";
                        })
                        .collect(Collectors.joining(" "));
            }
            case "PROVEEDOR_DE_PRODUCTO", "PRODUCTOS_POR_PROVEEDOR" -> {
                if (context.registros == null || context.registros.isEmpty()) {
                    return "No encontré productos o proveedores relacionados con esa consulta.";
                }
                return context.registros.stream()
                        .map(item -> {
                            Map<?, ?> registro = (Map<?, ?>) item;
                            return registro.get("nombre") + " — proveedor: "
                                    + (registro.get("proveedor") == null ? "no registrado" : registro.get("proveedor"));
                        })
                        .collect(Collectors.joining(". ", "", "."));
            }
            case "CONTAR_REGISTROS" -> {
                int total = context.registros == null ? 0 : context.registros.size();
                return "Hay " + total + " registros en esta consulta.";
            }
            case "SOLICITAR_ACLARACION" -> {
                return "¿Qué productos quieres comparar? Puedo revisar los precios de los que me indiques.";
            }
            default -> {
                if (context.registros == null || context.registros.isEmpty()) {
                    return "No encontré registros para esa consulta.";
                }
                return "He encontrado " + context.registros.size() + " resultados relevantes: " + context.registros.stream()
                        .limit(10)
                        .map(item -> String.valueOf(((Map<?, ?>) item).get("nombre") != null ? ((Map<?, ?>) item).get("nombre") : ((Map<?, ?>) item).get("nombreEmpresa")))
                        .collect(Collectors.joining(", ")) + ".";
            }
        }
    }

    private Map<String, Object> toProductoMap(ProductoVO producto) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idProducto", producto.getIdProducto());
        item.put("nombre", producto.getNombre());
        item.put("categoria", producto.getCategoria());
        item.put("precioVenta", producto.getPrecioVenta());
        item.put("stock", producto.getStock());
        item.put("stockMinimo", producto.getStockMinimo());
        item.put("estado", producto.getEstado());
        item.put("unidadMedida", producto.getUnidadMedida());
        return item;
    }

    private Map<String, Object> toProveedorMap(ProveedorVO proveedor) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idProveedor", proveedor.getIdProveedor());
        item.put("nombreEmpresa", proveedor.getNombreEmpresa());
        item.put("estado", proveedor.getEstado());
        return item;
    }

    private Map<String, Object> toVentaMap(VentaVO venta) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("idVenta", venta.getIdVenta());
        item.put("fechaVenta", venta.getFechaVenta());
        item.put("metodoPago", venta.getMetodoPago());
        item.put("estado", venta.getEstado());
        item.put("total", venta.getTotal());
        return item;
    }

    private int numero(QueryContext context, String key) {
        Object value = context.registros.isEmpty()
                ? null : ((Map<?, ?>) context.registros.get(0)).get(key);
        return value == null ? 0 : Integer.parseInt(value.toString());
    }

    private BigDecimal decimal(QueryContext context, String key) {
        Object value = context.registros.isEmpty()
                ? null : ((Map<?, ?>) context.registros.get(0)).get(key);
        return decimal(value);
    }

    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private String formatoDinero(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String unidadMedida(Map<?, ?> registro) {
        Object unit = registro.get("unidadMedida");
        return unit == null ? "unidades" : unit.toString();
    }

    private String etiquetaPeriodo(QueryContext context) {
        String question = context.pregunta;
        if (question.contains("hoy") || question.contains("del dia")) {
            return "hoy";
        }
        if (question.contains("ayer")) {
            return "ayer";
        }
        if (question.contains("semana")) {
            return "esta semana";
        }
        if (question.contains("mes")) {
            return "este mes";
        }
        return "en el periodo consultado";
    }

    private static final class ConversationMemory {
        private ProductoVO ultimoProducto;
        private List<ProductoVO> ultimosProductos = new ArrayList<>();

        public ProductoVO getUltimoProducto() {
            return ultimoProducto;
        }

        public void setUltimoProducto(ProductoVO ultimoProducto) {
            this.ultimoProducto = ultimoProducto;
        }

        public List<ProductoVO> getUltimosProductos() {
            return ultimosProductos;
        }

        public void setUltimosProductos(List<ProductoVO> ultimosProductos) {
            this.ultimosProductos = ultimosProductos == null ? new ArrayList<>() : new ArrayList<>(ultimosProductos);
        }
    }

    private static final class QueryContext {
        private String tipoConsulta;
        private String pregunta = "";
        private List<Map<String, Object>> registros = new ArrayList<>();
        private int totalResultados;
        private ProductoVO productoReferencia;
        private List<ProductoVO> productos = new ArrayList<>();
        private List<ProductoVO> productosParaMemoria = new ArrayList<>();
        private boolean compararMaximo;
    }
}
