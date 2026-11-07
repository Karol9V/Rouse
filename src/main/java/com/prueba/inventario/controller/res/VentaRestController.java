package com.prueba.inventario.controller.res;

import com.prueba.inventario.model.dao.DetalleVentaDAO;
import com.prueba.inventario.model.dao.ProductoDAO;
import com.prueba.inventario.model.dao.VentaDAO;
import com.prueba.inventario.model.vo.DetalleVentaVO;
import com.prueba.inventario.model.vo.VentaVO;
import com.prueba.inventario.security.AuthenticatedUser;
import com.prueba.inventario.security.RolePermissions;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Expone operaciones REST de ventas y coordina el registro de sus detalles.
 */
@RestController
@RequestMapping("/api/ventas")
public class VentaRestController {

    private final VentaDAO ventaDAO;
    private final DetalleVentaDAO detalleVentaDAO;
    private final ProductoDAO productoDAO;

    public VentaRestController(VentaDAO ventaDAO, DetalleVentaDAO detalleVentaDAO, ProductoDAO productoDAO) {
        this.ventaDAO = ventaDAO;
        this.detalleVentaDAO = detalleVentaDAO;
        this.productoDAO = productoDAO;
    }

    /**
     * Lista las ventas registradas.
     *
     * @return ventas disponibles
     */
    @GetMapping
    public List<VentaVO> listar() {
        return ventaDAO.listar();
    }

    /**
     * Busca una venta por identificador.
     *
     * @param id identificador de la venta
     * @return venta o {@code 404 Not Found}
     */
    @GetMapping("/{id}")
    public ResponseEntity<VentaVO> buscarPorId(@PathVariable Long id) {
        return ventaDAO.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Valida la venta, descuenta inventario y registra sus detalles.
     *
     * @param request datos y líneas de la venta
     * @param authenticatedUser usuario asociado a la sesión
     * @return venta creada o error de validación/inventario
     */
    @PostMapping
    @Transactional
    public ResponseEntity<VentaVO> insertar(
            @RequestBody(required = false) VentaRequest request,
            @SessionAttribute(AuthenticatedUser.SESSION_ATTRIBUTE) AuthenticatedUser authenticatedUser) {
        if (request == null || (request.idUsuario() == null
                && !RolePermissions.isVendor(authenticatedUser.rol()))) {
            return ResponseEntity.badRequest().build();
        }
        List<DetalleVentaInput> detalles = request.detalles() == null ? List.of() : request.detalles();
        if (detalles.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        BigDecimal descuento = request.descuento() == null ? BigDecimal.ZERO : request.descuento();
        if (detalles.stream().anyMatch(detalle -> detalle == null || detalle.idProducto() == null
                || detalle.cantidad() == null || detalle.precioUnitario() == null
                || detalle.cantidad().signum() <= 0 || detalle.precioUnitario().signum() < 0)) {
            return ResponseEntity.badRequest().build();
        }
        BigDecimal subtotal = detalles.stream()
                .map(detalle -> detalle.cantidad().multiply(detalle.precioUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = subtotal.subtract(descuento);
        if (descuento.signum() < 0 || total.signum() < 0
                || request.metodoPago() == null || request.metodoPago().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        for (DetalleVentaInput detalle : detalles) {
            if (productoDAO.descontarStock(detalle.idProducto(), detalle.cantidad()) == 0) {
                boolean productoExiste = productoDAO.buscarPorId(detalle.idProducto()).isPresent();
                String mensaje = productoExiste
                        ? "Stock insuficiente para el producto " + detalle.idProducto() + "."
                        : "No existe el producto " + detalle.idProducto() + ".";
                throw new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
            }
        }
        Long idUsuario = RolePermissions.isVendor(authenticatedUser.rol())
                ? authenticatedUser.idUsuario()
                : request.idUsuario();
        VentaVO venta = new VentaVO(
                null,
                idUsuario,
                request.fechaVenta(),
                subtotal,
                descuento,
                total,
                request.metodoPago(),
                request.estado());
        Long id = ventaDAO.insertar(venta);
        for (DetalleVentaInput detalle : detalles) {
            detalleVentaDAO.insertar(new DetalleVentaVO(
                    null,
                    id,
                    detalle.idProducto(),
                    detalle.cantidad(),
                    detalle.precioUnitario(),
                    null));
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(ventaDAO.buscarPorId(id).orElseThrow());
    }

    /**
     * Actualiza los campos proporcionados y conserva los valores omitidos.
     *
     * @param id identificador de la venta
     * @param venta campos que se desean actualizar
     * @return {@code 204 No Content}, {@code 404 Not Found} o {@code 400 Bad Request}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Long id, @RequestBody(required = false) VentaVO venta) {
        if (venta == null) {
            return ResponseEntity.badRequest().build();
        }
        VentaVO ventaActual = ventaDAO.buscarPorId(id).orElse(null);
        if (ventaActual == null) {
            return ResponseEntity.notFound().build();
        }
        venta.setIdVenta(id);
        if (venta.getIdUsuario() == null) {
            venta.setIdUsuario(ventaActual.getIdUsuario());
        }
        if (venta.getSubtotal() == null) {
            venta.setSubtotal(ventaActual.getSubtotal());
        }
        if (venta.getDescuento() == null) {
            venta.setDescuento(ventaActual.getDescuento());
        }
        if (venta.getTotal() == null) {
            venta.setTotal(ventaActual.getTotal());
        }
        if (venta.getMetodoPago() == null) {
            venta.setMetodoPago(ventaActual.getMetodoPago());
        }
        if (venta.getEstado() == null) {
            venta.setEstado(ventaActual.getEstado());
        }
        return ventaDAO.actualizar(venta) == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

    /**
     * Elimina una venta.
     *
     * @param id identificador de la venta
     * @return {@code 204 No Content} o {@code 404 Not Found}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return ventaDAO.eliminar(id) == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

    /** Datos de cabecera y líneas requeridos para registrar una venta. */
    public record VentaRequest(
            Long idUsuario,
            LocalDateTime fechaVenta,
            BigDecimal subtotal,
            BigDecimal descuento,
            BigDecimal total,
            String metodoPago,
            String estado,
            List<DetalleVentaInput> detalles) {
    }

    /** Producto, cantidad y precio de una línea de venta. */
    public record DetalleVentaInput(
            Long idProducto,
            BigDecimal cantidad,
            BigDecimal precioUnitario) {
    }
}
