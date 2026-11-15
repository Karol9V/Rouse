package com.prueba.inventario.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.inventario.model.dao.DetalleVentaDAO;
import com.prueba.inventario.model.dao.ProductoDAO;
import com.prueba.inventario.model.dao.ProveedorDAO;
import com.prueba.inventario.model.dao.UsuarioDAO;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Analiza las consultas del usuario, recupera datos reales del sistema y prepara un contexto
 * estructurado para Gemini.
 */
@Service
public class ChatbotService {

    private static final int MAX_MENSAJE_LENGTH = 1000;
    private static final Pattern STOCK_EXACTO_PATTERN = Pattern.compile(
            "(?:\\btiene\\s+|\\bcon\\s+|\\bstock(?:\\s+(?:de|es|en))?\\s*)(\\d+(?:[.,]\\d+)?)");
    private final Map<String, ConversationMemory> memories = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProductoDAO productoDAO;
    private final VentaDAO ventaDAO;
    private final DetalleVentaDAO detalleVentaDAO;
    private final ProveedorDAO proveedorDAO;
    private final UsuarioDAO usuarioDAO;
    private final GeminiService geminiService;

    public ChatbotService(
            ProductoDAO productoDAO,
            VentaDAO ventaDAO,
            DetalleVentaDAO detalleVentaDAO,
            ProveedorDAO proveedorDAO,
            UsuarioDAO usuarioDAO,
            GeminiService geminiService) {
        this.productoDAO = productoDAO;
        this.ventaDAO = ventaDAO;
        this.detalleVentaDAO = detalleVentaDAO;
        this.proveedorDAO = proveedorDAO;
        this.usuarioDAO = usuarioDAO;
        this.geminiService = geminiService;
    }

    public String responder(String mensaje) {
        return responder(mensaje, (HttpSession) null);
    }

    public String responder(String mensaje, HttpSession session) {
        String key = session == null ? "default" : "session:" + session.getId();
        ConversationMemory memory = memories.computeIfAbsent(key, ignored -> new ConversationMemory());
        return responder(mensaje, memory, List.of(), new QueryData());
    }

    public String responder(
            String mensaje,
            List<ChatMessageVO> historial) {
        QueryData data = new QueryData();
        ConversationMemory memory = reconstruirMemoria(historial, data);
        return responder(mensaje, memory, historial, data);
    }

    private String responder(
            String mensaje,
            ConversationMemory memory,
            List<ChatMessageVO> historial,
            QueryData data) {
        String texto = normalizarMensaje(mensaje);
        if (texto.isBlank()) {
            throw new IllegalArgumentException("Debes escribir una pregunta antes de enviar el mensaje.");
        }
        if (texto.length() > MAX_MENSAJE_LENGTH) {
            throw new IllegalArgumentException("El mensaje es demasiado largo. Usa máximo 1000 caracteres.");
        }

        String respuestaConversacional = respuestaConversacional(texto);
        if (respuestaConversacional != null) {
            return respuestaConversacional;
        }

        QueryContext queryContext = inferirConsulta(texto, memory, data);
        if (esConsultaDeterminista(queryContext)) {
            String respuesta = respuestaLocal(queryContext, memory);
            actualizarMemoria(memory, queryContext);
            return respuesta;
        }
        String prompt = construirPrompt(texto, queryContext);
        String respuestaGemini = geminiService.generateResponse(prompt, historial);
        if (respuestaGemini != null && !respuestaGemini.isBlank()) {
            actualizarMemoria(memory, queryContext);
            return respuestaGemini.trim();
        }

        String respuestaLocal = respuestaLocal(queryContext, memory);
        actualizarMemoria(memory, queryContext);
        return respuestaLocal;
    }

    private void actualizarMemoria(ConversationMemory memory, QueryContext context) {
        memory.setUltimoProducto(productoPrincipal(context));
        memory.setUltimosProductos(productosParaMemoria(context));
        actualizarDimensionComparacion(memory, context);
    }

    private void actualizarDimensionComparacion(ConversationMemory memory, QueryContext context) {
        switch (context.tipoConsulta == null ? "" : context.tipoConsulta) {
            case "COMPARAR_STOCK", "CONSULTA_STOCK", "BUSCAR_PRODUCTOS_POR_STOCK",
                    "LISTAR_PRODUCTOS_STOCK_BAJO", "LISTAR_PRODUCTOS_AGOTADOS" ->
                memory.setDimensionComparacion(ComparisonDimension.STOCK);
            case "COMPARAR_PRODUCTOS", "CONSULTA_PRECIO" ->
                memory.setDimensionComparacion(ComparisonDimension.PRECIO);
            case "SOLICITAR_ACLARACION" -> {
            }
            default -> memory.setDimensionComparacion(null);
        }
    }

    private List<ProductoVO> productosParaMemoria(QueryContext context) {
        return context.productosParaMemoria.isEmpty()
                ? context.productos : context.productosParaMemoria;
    }

    private ConversationMemory reconstruirMemoria(List<ChatMessageVO> historial, QueryData data) {
        ConversationMemory memory = new ConversationMemory();
        if (historial == null || historial.isEmpty()) {
            return memory;
        }
        for (ChatMessageVO message : historial) {
            if ("USUARIO".equals(message.emisor())) {
                if (respuestaConversacional(message.contenido()) != null) {
                    continue;
                }
                QueryContext previous = inferirConsulta(message.contenido(), memory, data);
                actualizarDimensionComparacion(memory, previous);
                if (!previous.productos.isEmpty()) {
                    memory.setUltimosProductos(productosParaMemoria(previous));
                    memory.setUltimoProducto(productoPrincipal(previous));
                }
            } else if ("ASISTENTE".equals(message.emisor())
                    && !memory.getUltimosProductos().isEmpty()) {
                List<ProductoVO> mentioned = productosMencionados(
                        normalizarBusqueda(message.contenido()), memory.getUltimosProductos());
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
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo preparar el contexto seguro para Gemini.", ex);
        }
    }

    private QueryContext inferirConsulta(
            String texto,
            ConversationMemory memory,
            QueryData data) {
        String textoLower = normalizarBusqueda(texto);
        QueryContext context = new QueryContext();
        context.pregunta = textoLower;
        if (textoLower.contains("vendedor") && !esConsultaVentas(textoLower)) {
            if (esConsultaConteo(textoLower)) {
                context.tipoConsulta = "CONTAR_VENDEDORES";
                context.registros = List.of(Map.of(
                        "cantidadVendedores", usuarioDAO.contarVendedores()));
            } else {
                context.tipoConsulta = "LISTAR_VENDEDORES";
                context.registros = usuarioDAO.listarVendedores().stream()
                        .map(nombre -> Map.<String, Object>of("nombre", nombre))
                        .collect(Collectors.toList());
            }
            context.totalResultados = context.registros.size();
            return context;
        }
        List<ProductoVO> productos = data.productos();

        ProductoVO productoReferencia = resolverProductoReferencia(textoLower, memory, productos);
        context.productoReferencia = productoReferencia;
        List<ProductoVO> coincidenciasProducto = productosMencionados(textoLower, productos);
        List<ProductoVO> productosMencionados = coincidenciasProducto.isEmpty() && productoReferencia != null
                ? List.of(productoReferencia) : coincidenciasProducto;
        CategoryFilter categoryFilter = filtroCategoria(textoLower, productos);
        List<ProductoVO> productosFiltrados = filtrarPorCategoria(categoryFilter, productos);
        if (!productosMencionados.isEmpty()) {
            productosFiltrados = productosMencionados;
        } else if (productoReferencia != null && esReferenciaConversacional(textoLower)) {
            productosFiltrados = List.of(productoReferencia);
        }
        boolean comparacionContextual = esComparacionContextual(textoLower)
                && memory.getDimensionComparacion() != null;

        if (!memory.getUltimosProductos().isEmpty()
                && (textoLower.startsWith("cuales son") || textoLower.startsWith("y cuales son"))
                && !esConsultaVentas(textoLower)) {
            List<ProductoVO> productosRecordados = memory.getUltimosProductos();
            context.tipoConsulta = "LISTAR_PRODUCTOS";
            context.productos = productosRecordados;
            context.registros = productosRecordados.stream()
                    .map(this::toProductoMap)
                    .collect(Collectors.toList());
            context.totalResultados = context.registros.size();
            return context;
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

        if (esReferenciaConversacional(textoLower) && productoReferencia == null
                && !comparacionContextual) {
            context.tipoConsulta = "SOLICITAR_ACLARACION";
            return context;
        }

        if (esConsultaVentas(textoLower)) {
            List<VentaVO> ventas = data.ventas();
            List<VentaVO> ventasPeriodo = ventas.stream()
                    .filter(this::esVentaValida)
                    .filter(venta -> dentroDelPeriodo(venta.getFechaVenta() == null
                            ? null : venta.getFechaVenta().toLocalDate(), textoLower))
                    .toList();
            List<Long> idsVenta = ventasPeriodo.stream().map(VentaVO::getIdVenta).toList();
            Optional<List<ProductoVO>> seleccionProductos = seleccionProductosVenta(
                    coincidenciasProducto, categoryFilter, productosFiltrados,
                    productoReferencia, textoLower);
            boolean consultaProductosVendidos = esConsultaProducto(textoLower)
                    && (textoLower.contains("vend") || textoLower.contains("se vendio"));
            boolean consultaProductosMasVendidos = textoLower.contains("mas vendido")
                    || textoLower.contains("mas se vende");
            boolean consultaUnidades = textoLower.contains("unidad")
                    && (textoLower.contains("cuanto") || textoLower.contains("cuantas")
                    || textoLower.contains("cantidad"));
            List<DetalleVentaVO> detallesPeriodo = List.of();
            if (seleccionProductos.isPresent() || consultaProductosVendidos
                    || consultaProductosMasVendidos || consultaUnidades) {
                detallesPeriodo = data.detalles().stream()
                        .filter(detalle -> idsVenta.contains(detalle.getIdVenta()))
                        .toList();
            }
            if (seleccionProductos.isPresent()) {
                Set<Long> idsProducto = seleccionProductos.get().stream()
                        .map(ProductoVO::getIdProducto)
                        .collect(Collectors.toSet());
                detallesPeriodo = detallesPeriodo.stream()
                        .filter(detalle -> idsProducto.contains(detalle.getIdProducto()))
                        .toList();
                Set<Long> ventasConProducto = detallesPeriodo.stream()
                        .map(DetalleVentaVO::getIdVenta)
                        .collect(Collectors.toSet());
                ventasPeriodo = ventasPeriodo.stream()
                        .filter(venta -> ventasConProducto.contains(venta.getIdVenta()))
                        .toList();
            }
            if (consultaProductosMasVendidos) {
                context.tipoConsulta = "PRODUCTOS_MAS_VENDIDOS";
                context.registros = productosVendidos(detallesPeriodo, productos, 5);
            } else if (consultaProductosVendidos) {
                context.tipoConsulta = "CONSULTAR_PRODUCTOS_VENDIDOS";
                context.registros = productosVendidos(detallesPeriodo, productos, Integer.MAX_VALUE);
            } else if (consultaUnidades) {
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
                BigDecimal ingresos = seleccionProductos.isPresent()
                        ? sumarDetalles(detallesPeriodo) : sumarVentas(ventasPeriodo);
                BigDecimal promedio = ventasPeriodo.isEmpty() ? BigDecimal.ZERO
                        : ingresos.divide(BigDecimal.valueOf(ventasPeriodo.size()), 2, RoundingMode.HALF_UP);
                context.tipoConsulta = "CONSULTAR_PROMEDIO_VENTAS";
                context.registros = List.of(Map.of(
                        "promedioVenta", promedio,
                        "cantidadVentas", ventasPeriodo.size()));
            } else if (esConsultaConteoVentas(textoLower)) {
                context.tipoConsulta = "CONTAR_VENTAS";
                context.registros = List.of(Map.of("cantidadVentas", ventasPeriodo.size()));
            } else if (esConsultaMontoVentas(textoLower)) {
                context.tipoConsulta = "CONSULTAR_TOTAL_VENTAS";
                BigDecimal ingresos = seleccionProductos.isPresent()
                        ? sumarDetalles(detallesPeriodo) : sumarVentas(ventasPeriodo);
                context.registros = List.of(Map.of(
                        "ingresosTotales", ingresos,
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
            List<ProveedorVO> proveedores = data.proveedores();
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

        boolean esComparacionPrecio = esComparacionPrecio(textoLower)
                || (comparacionContextual
                && memory.getDimensionComparacion() == ComparisonDimension.PRECIO
                && !esComparacionStock(textoLower));
        if (!esComparacionPrecio && (textoLower.contains("precio")
                || textoLower.contains("cuesta") || textoLower.contains("vale"))) {
            if (coincidenciasProducto.isEmpty() && !categoryFilter.tieneFiltros()
                    && productoReferencia == null
                    && !textoLower.contains("productos") && !textoLower.contains("precios")
                    && !textoLower.contains("todos") && !textoLower.contains("lista")) {
                context.tipoConsulta = "SOLICITAR_ACLARACION";
                return context;
            }
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
            boolean maximo = esPrecioMaximo(textoLower)
                    || (comparacionContextual
                    && memory.getDimensionComparacion() == ComparisonDimension.PRECIO
                    && esComparacionMaximoContextual(textoLower));
            List<ProductoVO> candidatos = candidatosComparacion(
                    coincidenciasProducto, productosFiltrados, memory, textoLower);
            if (candidatos.isEmpty()) {
                context.tipoConsulta = "SOLICITAR_ACLARACION";
                return context;
            }
            BigDecimal precioElegido = candidatos.stream()
                    .filter(producto -> producto.getPrecioVenta() != null)
                    .map(ProductoVO::getPrecioVenta)
                    .reduce(maximo ? BigDecimal::max : BigDecimal::min)
                    .orElse(null);
            context.tipoConsulta = "COMPARAR_PRODUCTOS";
            if (precioElegido != null) {
                List<ProductoVO> elegidos = candidatos.stream()
                        .filter(producto -> producto.getPrecioVenta() != null
                                && producto.getPrecioVenta().compareTo(precioElegido) == 0)
                        .toList();
                context.productoReferencia = elegidos.get(0);
                context.productos = elegidos;
                context.productosParaMemoria = candidatos;
                context.registros = elegidos.stream()
                        .map(this::toProductoMap)
                        .collect(Collectors.toList());
                context.compararMaximo = maximo;
            }
            context.totalResultados = context.registros.size();
            return context;
        }

        if (esComparacionStock(textoLower)
                || (comparacionContextual
                && memory.getDimensionComparacion() == ComparisonDimension.STOCK)) {
            boolean maximo = esStockMaximo(textoLower)
                    || (comparacionContextual && esComparacionMaximoContextual(textoLower));
            List<ProductoVO> candidatos = candidatosComparacion(
                    coincidenciasProducto, productosFiltrados, memory, textoLower);
            BigDecimal stockElegido = candidatos.stream()
                    .map(ProductoVO::getStock)
                    .filter(Objects::nonNull)
                    .reduce(maximo ? BigDecimal::max : BigDecimal::min)
                    .orElse(null);
            context.tipoConsulta = "COMPARAR_STOCK";
            if (stockElegido != null) {
                List<ProductoVO> elegidos = candidatos.stream()
                        .filter(producto -> producto.getStock() != null
                                && producto.getStock().compareTo(stockElegido) == 0)
                        .toList();
                context.productoReferencia = elegidos.get(0);
                context.productos = elegidos;
                context.productosParaMemoria = candidatos;
                context.registros = elegidos.stream()
                        .map(this::toProductoMap)
                        .collect(Collectors.toList());
                context.compararStockMaximo = maximo;
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

        if (textoLower.contains("stock") || textoLower.contains("existencia")
                || textoLower.contains("queda") || textoLower.contains("quedan")) {
            BigDecimal stockSolicitado = extraerStockSolicitado(textoLower);
            if (stockSolicitado != null) {
                List<ProductoVO> coincidencias = productosFiltrados.stream()
                        .filter(producto -> producto.getStock() != null
                                && producto.getStock().compareTo(stockSolicitado) == 0)
                        .toList();
                context.tipoConsulta = "BUSCAR_PRODUCTOS_POR_STOCK";
                context.stockSolicitado = stockSolicitado;
                context.productos = coincidencias;
                context.registros = coincidencias.stream()
                        .map(this::toProductoMap)
                        .collect(Collectors.toList());
                context.totalResultados = context.registros.size();
                return context;
            }
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
                List<VentaVO> ventas = data.ventas();
                context.registros = ventas.stream().map(this::toVentaMap).collect(Collectors.toList());
            } else if (textoLower.contains("proveedor")) {
                List<ProveedorVO> proveedores = data.proveedores();
                context.registros = proveedores.stream().map(this::toProveedorMap).collect(Collectors.toList());
            } else {
                context.registros = productos.stream().map(this::toProductoMap).collect(Collectors.toList());
            }
            context.totalResultados = context.registros.size();
            return context;
        }

        if (esConsultaProducto(textoLower) || categoryFilter.tieneFiltros()) {
            context.tipoConsulta = "LISTAR_PRODUCTOS";
            context.productos = productosFiltrados;
            context.registros = productosFiltrados.stream()
                    .map(this::toProductoMap)
                    .collect(Collectors.toList());
            context.totalResultados = context.registros.size();
            return context;
        }

        context.tipoConsulta = "CONSULTA_AMBIGUA";
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

    private CategoryFilter filtroCategoria(String texto, List<ProductoVO> productos) {
        List<String> categorias = productos.stream()
                .map(ProductoVO::getCategoria)
                .filter(Objects::nonNull)
                .map(this::normalizarBusqueda)
                .distinct()
                .toList();
        List<String> incluidas = new ArrayList<>();
        List<String> excluidas = new ArrayList<>();
        for (String categoria : categorias) {
            List<String> alias = aliasCategoria(categoria);
            List<String> mencionados = alias.stream().filter(texto::contains).toList();
            if (mencionados.isEmpty()) {
                continue;
            }
            if (categoriaNegada(texto, mencionados)) {
                excluidas.add(categoria);
            } else {
                incluidas.add(categoria);
            }
        }
        return new CategoryFilter(incluidas, excluidas);
    }

    private List<ProductoVO> filtrarPorCategoria(CategoryFilter filtro, List<ProductoVO> productos) {
        return productos.stream()
                .filter(producto -> {
                    String categoria = normalizarBusqueda(producto.getCategoria());
                    boolean incluida = filtro.incluidas().isEmpty()
                            || filtro.incluidas().contains(categoria);
                    return incluida && !filtro.excluidas().contains(categoria);
                })
                .toList();
    }

    private List<String> aliasCategoria(String categoria) {
        LinkedHashSet<String> alias = new LinkedHashSet<>();
        alias.add(categoria);
        if (categoria.endsWith("es") && categoria.length() > 5) {
            alias.add(categoria.substring(0, categoria.length() - 2));
        }
        if (categoria.endsWith("s") && categoria.length() > 4) {
            alias.add(categoria.substring(0, categoria.length() - 1));
        }
        if (categoria.contains("pastel") || categoria.contains("torta")) {
            alias.addAll(List.of("pastel", "pasteles", "torta", "tortas"));
        }
        if (categoria.contains("pan")) {
            alias.addAll(List.of("pan", "panes", "panaderia"));
        }
        if (categoria.contains("cafe")) {
            alias.addAll(List.of("cafe", "cafes"));
        }
        return List.copyOf(alias);
    }

    private boolean categoriaNegada(String texto, List<String> aliasMencionados) {
        return aliasMencionados.stream().anyMatch(alias -> {
            int posicion = texto.indexOf(alias);
            if (posicion < 0) {
                return false;
            }
            String prefijo = texto.substring(0, posicion).stripTrailing();
            return prefijo.matches("(?s).*\\b(?:no|excepto|menos|sin)(?:\\s+\\w+){0,2}$");
        });
    }

    private List<ProductoVO> candidatosComparacion(
            List<ProductoVO> coincidenciasProducto,
            List<ProductoVO> productosFiltrados,
            ConversationMemory memory,
            String texto) {
        if (!coincidenciasProducto.isEmpty()) {
            return coincidenciasProducto;
        }
        if (esComparacionContextual(texto) && memory.getUltimosProductos().size() > 1) {
            return memory.getUltimosProductos();
        }
        if (texto.contains("de esos") || texto.contains("de estas")
                || texto.contains("de estos") || texto.contains("entre esos")
                || texto.contains("entre estos") || texto.contains("de la lista")
                || texto.contains("los anteriores") || texto.contains("y cual")) {
            if (!memory.getUltimosProductos().isEmpty()) {
                return memory.getUltimosProductos();
            }
        }
        if ((texto.contains("ese producto") || texto.contains("este producto")
                || texto.contains("esa") || texto.contains("ese")) && memory.getUltimoProducto() != null) {
            return List.of(memory.getUltimoProducto());
        }
        return productosFiltrados;
    }

    private boolean esConsultaProducto(String texto) {
        return texto.contains("producto") || texto.contains("pastel") || texto.contains("torta")
                || texto.contains("pan") || texto.contains("cafe");
    }

    private boolean esConsultaConteo(String texto) {
        return texto.contains("cuantos") || texto.contains("cuantas")
                || texto.contains("cantidad de") || texto.contains("total de")
                || texto.contains("numero de");
    }

    private boolean esComparacionPrecio(String texto) {
        return texto.contains("mas caro") || texto.contains("mas costoso")
                || texto.contains("menos caro") || texto.contains("menos costoso")
                || texto.contains("mas economico") || texto.contains("mas barato")
                || texto.contains("menor precio") || texto.contains("mayor precio")
                || texto.contains("menor costo") || texto.contains("mayor costo");
    }

    private boolean esPrecioMaximo(String texto) {
        return texto.contains("mas caro") || texto.contains("mas costoso")
                || texto.contains("mayor precio") || texto.contains("mayor costo");
    }

    private boolean esComparacionStock(String texto) {
        return texto.contains("menos stock") || texto.contains("menor stock")
                || texto.contains("mas stock") || texto.contains("mayor stock")
                || texto.contains("menos existencia") || texto.contains("menor existencia")
                || texto.contains("mas existencia") || texto.contains("mayor existencia")
                || texto.contains("menos existencias") || texto.contains("menor cantidad de stock")
                || texto.contains("mayor cantidad de stock") || texto.contains("mas cantidad de stock");
    }

    private boolean esStockMaximo(String texto) {
        return texto.contains("mas stock") || texto.contains("mayor stock")
                || texto.contains("mas existencia") || texto.contains("mayor existencia")
                || texto.contains("mayor cantidad de stock") || texto.contains("mas cantidad de stock");
    }

    private boolean esComparacionContextual(String texto) {
        boolean direccionComparativa = texto.contains("mas") || texto.contains("mayor")
                || texto.contains("menos") || texto.contains("menor");
        boolean referencia = texto.contains("cual") || texto.contains("que producto")
                || texto.contains("que tiene")
                || texto.contains("el que") || texto.contains("la que");
        return direccionComparativa && referencia;
    }

    private boolean esComparacionMaximoContextual(String texto) {
        return texto.contains("mas") || texto.contains("mayor");
    }

    private boolean esReferenciaConversacional(String texto) {
        return texto.contains("ese producto") || texto.contains("este producto")
                || texto.contains("el primero") || texto.contains("la primera")
                || texto.contains("el segundo") || texto.contains("el tercero")
                || texto.contains("su precio") || texto.contains("cuanto cuesta")
                || texto.contains("cuanto queda") || texto.contains("cuantos quedan")
                || texto.contains("cuantas quedan") || texto.contains("de ese")
                || texto.contains("de la lista") || texto.contains("los anteriores")
                || texto.contains("los otros") || texto.contains("las otras")
                || texto.contains("y el precio") || texto.contains("y ese")
                || texto.contains("y esa") || texto.contains("y cual")
                || texto.contains("de estos") || texto.contains("de esos")
                || texto.contains("entre estos") || texto.contains("entre esos");
    }

    private boolean esConsultaVentas(String texto) {
        return texto.contains("venta") || texto.contains("vendio") || texto.contains("vendieron")
                || texto.contains("vendido") || texto.contains("vendidos") || texto.contains("vendidas")
                || texto.contains("vender") || texto.contains("ingreso") || texto.contains("dinero")
                || texto.contains("recaud") || texto.contains("obtuvo") || texto.contains("pedido")
                || texto.contains("transaccion");
    }

    private boolean esConsultaConteoVentas(String texto) {
        return texto.contains("cuantos") || texto.contains("cuantas")
                || texto.contains("cantidad de venta") || texto.contains("numero de venta")
                || texto.contains("cantidad de transaccion") || texto.contains("numero de transaccion");
    }

    private boolean esConsultaMontoVentas(String texto) {
        return texto.contains("dinero") || texto.contains("ingreso") || texto.contains("recaud")
                || texto.contains("obtuvo") || texto.contains("total")
                || (texto.contains("cuanto") && !texto.contains("unidad"));
    }

    private Optional<List<ProductoVO>> seleccionProductosVenta(
            List<ProductoVO> coincidenciasProducto,
            CategoryFilter categoryFilter,
            List<ProductoVO> productosFiltrados,
            ProductoVO productoReferencia,
            String texto) {
        if (!coincidenciasProducto.isEmpty()) {
            return Optional.of(coincidenciasProducto);
        }
        if (categoryFilter.tieneFiltros()) {
            return Optional.of(productosFiltrados);
        }
        if (productoReferencia != null && esReferenciaConversacional(texto)) {
            return Optional.of(List.of(productoReferencia));
        }
        return Optional.empty();
    }

    private BigDecimal extraerStockSolicitado(String texto) {
        Matcher matcher = STOCK_EXACTO_PATTERN.matcher(texto);
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group(1).replace(',', '.'));
    }

    private boolean esConsultaDeterminista(QueryContext context) {
        return context.tipoConsulta != null && !"CONSULTA_AMBIGUA".equals(context.tipoConsulta);
    }

    private String respuestaConversacional(String texto) {
        String mensaje = normalizarBusqueda(texto).toLowerCase(Locale.ROOT)
                .replaceAll("[.!?¡¿,]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (mensaje.matches("(hola|holi|hey|hi|buenas|buen dia|buenos dias|buenas tardes|buenas noches)")) {
            return "¡Hola! ¿En qué te gustaría que te ayude hoy con el inventario o las ventas?";
        }
        if (mensaje.matches("(gracias|muchas gracias|te agradezco|listo gracias)")) {
            return "¡Con gusto! Si necesitas revisar algo más, aquí estoy.";
        }
        if (mensaje.matches("(adios|hasta luego|nos vemos|chao)")) {
            return "¡Hasta luego! Cuando necesites consultar el inventario o las ventas, aquí estaré.";
        }
        if (mensaje.matches("(como estas|como te va)")) {
            return "¡Muy bien, gracias por preguntar! ¿Qué te gustaría revisar?";
        }
        return null;
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

    private BigDecimal sumarDetalles(List<DetalleVentaVO> detalles) {
        return detalles.stream()
                .map(detalle -> detalle.getSubtotal() != null
                        ? detalle.getSubtotal()
                        : detalle.getPrecioUnitario() == null || detalle.getCantidad() == null
                                ? BigDecimal.ZERO
                                : detalle.getPrecioUnitario().multiply(detalle.getCantidad()))
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
            case "LISTAR_PRODUCTOS" -> {
                if (context.registros.isEmpty()) {
                    return "No encontré productos que coincidan con esa consulta.";
                }
                return "Encontré estos productos: " + context.registros.stream()
                        .map(item -> String.valueOf(((Map<?, ?>) item).get("nombre")))
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
            case "CONTAR_VENDEDORES" -> {
                int count = numero(context, "cantidadVendedores");
                return "Hay " + count + (count == 1
                        ? " vendedor registrado." : " vendedores registrados.");
            }
            case "LISTAR_VENDEDORES" -> {
                if (context.registros.isEmpty()) {
                    return "No hay vendedores registrados.";
                }
                return "Los vendedores registrados son: " + context.registros.stream()
                        .map(item -> String.valueOf(((Map<?, ?>) item).get("nombre")))
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
                            String vendedor = context.pregunta.contains("vendedor")
                                    || context.pregunta.contains("quien")
                                    ? " — Vendedor: " + Objects.toString(registro.get("nombreVendedor"),
                                            "no identificado")
                                    : "";
                            return "• Venta " + registro.get("idVenta") + vendedor + " — "
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
                String nombres = context.registros.stream()
                        .map(item -> String.valueOf(((Map<?, ?>) item).get("nombre")))
                        .collect(Collectors.joining(", "));
                return (context.registros.size() == 1
                        ? "El producto " + nombres + " tiene el precio "
                        : "Los productos " + nombres + " tienen el precio ")
                        + (context.compararMaximo ? "más alto: " : "más bajo: ")
                        + formatoDinero(decimal(producto.get("precioVenta"))) + " COP.";
            }
            case "COMPARAR_STOCK" -> {
                if (context.registros.isEmpty()) {
                    return "No encontré productos con una cantidad de stock registrada para comparar.";
                }
                Map<?, ?> producto = (Map<?, ?>) context.registros.get(0);
                String nombres = context.registros.stream()
                        .map(item -> String.valueOf(((Map<?, ?>) item).get("nombre")))
                        .collect(Collectors.joining(", "));
                String comparacion = context.compararStockMaximo ? "mayor" : "menor";
                return (context.registros.size() == 1
                        ? "El " + comparacion + " stock lo tiene "
                        : "El " + comparacion + " stock lo tienen ")
                        + nombres + ": " + producto.get("stock") + " "
                        + unidadMedida(producto) + ".";
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
            case "BUSCAR_PRODUCTOS_POR_STOCK" -> {
                BigDecimal stock = context.stockSolicitado;
                String cantidad = stock.stripTrailingZeros().toPlainString();
                if (context.registros.isEmpty()) {
                    return "No encontré productos con exactamente " + cantidad + " unidades en stock.";
                }
                String nombres = context.registros.stream()
                        .map(item -> String.valueOf(((Map<?, ?>) item).get("nombre")))
                        .collect(Collectors.joining(", "));
                return (context.registros.size() == 1
                        ? "El producto con " + cantidad + " unidades en stock es: "
                        : "Los productos con " + cantidad + " unidades en stock son: ")
                        + nombres + ".";
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
                String entidad = entidadContada(context.pregunta);
                return "Hay " + total + " " + (total == 1 ? entidad : pluralizar(entidad)) + ".";
            }
            case "SOLICITAR_ACLARACION" -> {
                if (context.pregunta.contains("stock") || context.pregunta.contains("existencia")
                        || context.pregunta.contains("queda")) {
                    return "¿De qué producto quieres revisar las existencias? Indícame su nombre.";
                }
                if (context.pregunta.contains("precio") || context.pregunta.contains("cuesta")
                        || context.pregunta.contains("vale") || context.pregunta.contains("barato")
                        || context.pregunta.contains("caro")) {
                    return "¿De qué producto quieres consultar el precio? Indícame su nombre o categoría.";
                }
                return "¿Qué productos quieres comparar? Indícame sus nombres o la categoría.";
            }
            case "CONSULTA_AMBIGUA" -> {
                return "¿Qué te gustaría consultar: productos, existencias, proveedores o ventas? "
                        + "Si tienes otra pregunta, cuéntame un poco más para orientarte.";
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
        item.put("fechaVenta", venta.getFechaVenta() == null ? null : venta.getFechaVenta().toString());
        item.put("nombreVendedor", venta.getNombreVendedor());
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

    private String entidadContada(String pregunta) {
        if (pregunta.contains("proveedor")) {
            return pregunta.contains("disponible") ? "proveedor disponible" : "proveedor";
        }
        if (pregunta.contains("producto") || pregunta.contains("pastel")
                || pregunta.contains("torta") || pregunta.contains("pan")
                || pregunta.contains("cafe")) {
            return pregunta.contains("disponible") ? "producto disponible" : "producto";
        }
        if (pregunta.contains("venta") || pregunta.contains("pedido")) {
            return "venta";
        }
        return "registro";
    }

    private String pluralizar(String entidad) {
        return switch (entidad) {
            case "proveedor" -> "proveedores";
            case "producto disponible" -> "productos disponibles";
            case "proveedor disponible" -> "proveedores disponibles";
            case "venta" -> "ventas";
            case "registro" -> "registros";
            default -> entidad + "s";
        };
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
        private ComparisonDimension dimensionComparacion;

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

        public ComparisonDimension getDimensionComparacion() {
            return dimensionComparacion;
        }

        public void setDimensionComparacion(ComparisonDimension dimensionComparacion) {
            this.dimensionComparacion = dimensionComparacion;
        }
    }

    private enum ComparisonDimension {
        STOCK,
        PRECIO
    }

    private final class QueryData {
        private List<ProductoVO> productos;
        private List<ProveedorVO> proveedores;
        private List<VentaVO> ventas;
        private List<DetalleVentaVO> detalles;

        private List<ProductoVO> productos() {
            if (productos == null) {
                productos = productoDAO.listar();
            }
            return productos;
        }

        private List<ProveedorVO> proveedores() {
            if (proveedores == null) {
                proveedores = proveedorDAO.listar();
            }
            return proveedores;
        }

        private List<VentaVO> ventas() {
            if (ventas == null) {
                ventas = ventaDAO.listar();
            }
            return ventas;
        }

        private List<DetalleVentaVO> detalles() {
            if (detalles == null) {
                detalles = detalleVentaDAO.listar();
            }
            return detalles;
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
        private boolean compararStockMaximo;
        private BigDecimal stockSolicitado;
    }

    private record CategoryFilter(List<String> incluidas, List<String> excluidas) {
        private boolean tieneFiltros() {
            return !incluidas.isEmpty() || !excluidas.isEmpty();
        }
    }
}
