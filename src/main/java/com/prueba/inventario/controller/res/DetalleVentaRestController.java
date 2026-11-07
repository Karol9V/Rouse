package com.prueba.inventario.controller.res;

import com.prueba.inventario.model.dao.DetalleVentaDAO;
import com.prueba.inventario.model.vo.DetalleVentaVO;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/detalles-venta")
public class DetalleVentaRestController {

    private final DetalleVentaDAO detalleVentaDAO;

    public DetalleVentaRestController(DetalleVentaDAO detalleVentaDAO) {
        this.detalleVentaDAO = detalleVentaDAO;
    }

    @GetMapping
    public List<DetalleVentaVO> listar(@RequestParam(required = false) Long ventaId) {
        return ventaId == null ? detalleVentaDAO.listar() : detalleVentaDAO.listarPorVenta(ventaId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetalleVentaVO> buscarPorId(@PathVariable Long id) {
        return detalleVentaDAO.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DetalleVentaVO> insertar(@RequestBody(required = false) DetalleVentaVO detalle) {
        if (detalle == null) {
            return ResponseEntity.badRequest().build();
        }
        Long id = detalleVentaDAO.insertar(detalle);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(detalleVentaDAO.buscarPorId(id).orElseThrow());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Long id, @RequestBody(required = false) DetalleVentaVO detalle) {
        if (detalle == null) {
            return ResponseEntity.badRequest().build();
        }
        detalle.setIdDetalle(id);
        return detalleVentaDAO.actualizar(detalle) == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return detalleVentaDAO.eliminar(id) == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }
}
