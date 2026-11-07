package com.prueba.inventario.controller.res;

import com.prueba.inventario.model.dao.ProductoDAO;
import com.prueba.inventario.model.vo.ProductoVO;
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

@RestController
@RequestMapping("/api/productos")
public class ProductoRestController {

    private final ProductoDAO productoDAO;

    public ProductoRestController(ProductoDAO productoDAO) {
        this.productoDAO = productoDAO;
    }

    @GetMapping
    public List<ProductoVO> listar() {
        return productoDAO.listar();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoVO> buscarPorId(@PathVariable Long id) {
        return productoDAO.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProductoVO> insertar(@RequestBody(required = false) ProductoVO producto) {
        if (producto == null) {
            return ResponseEntity.badRequest().build();
        }
        Long id = productoDAO.insertar(producto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(productoDAO.buscarPorId(id).orElseThrow());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Long id, @RequestBody(required = false) ProductoVO producto) {
        if (producto == null) {
            return ResponseEntity.badRequest().build();
        }
        producto.setIdProducto(id);
        return productoDAO.actualizar(producto) == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return productoDAO.eliminar(id) == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }
}
