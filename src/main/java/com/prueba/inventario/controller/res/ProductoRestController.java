package com.prueba.inventario.controller.res;

import com.prueba.inventario.model.dao.ProductoDAO;
import com.prueba.inventario.model.vo.ProductoVO;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Expone operaciones REST de consulta y mantenimiento de productos.
 */
@RestController
@RequestMapping("/api/productos")
public class ProductoRestController {

    private final ProductoDAO productoDAO;

    public ProductoRestController(ProductoDAO productoDAO) {
        this.productoDAO = productoDAO;
    }

    /**
     * Lista los productos registrados.
     *
     * @return productos disponibles
     */
    @GetMapping
    public List<ProductoVO> listar() {
        return productoDAO.listar();
    }

    /**
     * Busca un producto por identificador.
     *
     * @param id identificador del producto
     * @return el producto o {@code 404 Not Found}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductoVO> buscarPorId(@PathVariable Long id) {
        return productoDAO.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el producto " + id + "."));
    }

    /**
     * Registra un producto y devuelve su URI.
     *
     * @param producto datos del producto
     * @return producto creado o {@code 400 Bad Request}
     */
    @PostMapping
    public ResponseEntity<ProductoVO> insertar(@RequestBody(required = false) ProductoVO producto) {
        if (producto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes enviar los datos del producto.");
        }
        Long id = productoDAO.insertar(producto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(productoDAO.buscarPorId(id).orElseThrow());
    }

    /**
     * Actualiza un producto existente.
     *
     * @param id identificador del producto
     * @param producto datos actualizados
     * @return {@code 204 No Content}, {@code 404 Not Found} o {@code 400 Bad Request}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Long id, @RequestBody(required = false) ProductoVO producto) {
        if (producto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes enviar los datos del producto.");
        }
        producto.setIdProducto(id);
        if (productoDAO.actualizar(producto) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el producto " + id + ".");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Elimina un producto.
     *
     * @param id identificador del producto
     * @return {@code 204 No Content} o {@code 404 Not Found}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (productoDAO.eliminar(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el producto " + id + ".");
        }
        return ResponseEntity.noContent().build();
    }
}
