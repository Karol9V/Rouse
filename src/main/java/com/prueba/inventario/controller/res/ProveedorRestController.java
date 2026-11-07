package com.prueba.inventario.controller.res;

import com.prueba.inventario.model.dao.ProveedorDAO;
import com.prueba.inventario.model.vo.ProveedorVO;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Expone operaciones REST de consulta y mantenimiento de proveedores.
 */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorRestController {

    private final ProveedorDAO proveedorDAO;

    public ProveedorRestController(ProveedorDAO proveedorDAO) {
        this.proveedorDAO = proveedorDAO;
    }

    /**
     * Lista los proveedores registrados.
     *
     * @return proveedores disponibles
     */
    @GetMapping
    public List<ProveedorVO> listar() {
        return proveedorDAO.listar();
    }

    /**
     * Busca un proveedor por identificador.
     *
     * @param id identificador del proveedor
     * @return el proveedor o {@code 404 Not Found}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProveedorVO> buscarPorId(@PathVariable Long id) {
        return proveedorDAO.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Registra un proveedor y devuelve su URI.
     *
     * @param proveedor datos del proveedor
     * @return proveedor creado o {@code 400 Bad Request}
     */
    @PostMapping
    public ResponseEntity<ProveedorVO> insertar(@RequestBody(required = false) ProveedorVO proveedor) {
        if (proveedor == null) {
            return ResponseEntity.badRequest().build();
        }
        Long id = proveedorDAO.insertar(proveedor);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(proveedorDAO.buscarPorId(id).orElseThrow());
    }

    /**
     * Actualiza un proveedor existente.
     *
     * @param id identificador del proveedor
     * @param proveedor datos actualizados
     * @return {@code 204 No Content}, {@code 404 Not Found} o {@code 400 Bad Request}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Long id, @RequestBody(required = false) ProveedorVO proveedor) {
        if (proveedor == null) {
            return ResponseEntity.badRequest().build();
        }
        proveedor.setIdProveedor(id);
        return proveedorDAO.actualizar(proveedor) == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

    /**
     * Elimina un proveedor.
     *
     * @param id identificador del proveedor
     * @return {@code 204 No Content} o {@code 404 Not Found}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return proveedorDAO.eliminar(id) == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }
}
