package com.prueba.inventario.model.dao;

import com.prueba.inventario.model.vo.ProductoVO;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProductoDAO {

    private static final String SELECT_COLUMNAS = """
            SELECT id_producto, nombre, descripcion, categoria, precio_venta, costo, stock,
                   stock_minimo, unidad_medida, id_proveedor, estado
            FROM productos
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ProductoVO> rowMapper = (rs, rowNum) -> new ProductoVO(
            rs.getLong("id_producto"),
            rs.getString("nombre"),
            rs.getString("descripcion"),
            rs.getString("categoria"),
            rs.getBigDecimal("precio_venta"),
            rs.getBigDecimal("costo"),
            rs.getBigDecimal("stock"),
            rs.getBigDecimal("stock_minimo"),
            rs.getString("unidad_medida"),
            rs.getObject("id_proveedor", Long.class),
            rs.getString("estado")
    );

    public ProductoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProductoVO> listar() {
        return jdbcTemplate.query(SELECT_COLUMNAS + " ORDER BY id_producto", rowMapper);
    }

    public Optional<ProductoVO> buscarPorId(Long idProducto) {
        List<ProductoVO> productos = jdbcTemplate.query(
                SELECT_COLUMNAS + " WHERE id_producto = ?", rowMapper, idProducto);
        return productos.stream().findFirst();
    }

    public Long insertar(ProductoVO producto) {
        String sql = """
                INSERT INTO productos
                    (nombre, descripcion, categoria, precio_venta, costo, stock, stock_minimo,
                     unidad_medida, id_proveedor, estado)
                VALUES (?, ?, ?, ?, COALESCE(?, 0.00), COALESCE(?, 0.00), COALESCE(?, 0.00),
                        COALESCE(?, 'unidad'), ?, COALESCE(?, 'Disponible'))
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, producto.getNombre());
            statement.setString(2, producto.getDescripcion());
            statement.setString(3, producto.getCategoria());
            statement.setBigDecimal(4, producto.getPrecioVenta());
            statement.setBigDecimal(5, producto.getCosto());
            statement.setBigDecimal(6, producto.getStock());
            statement.setBigDecimal(7, producto.getStockMinimo());
            statement.setString(8, producto.getUnidadMedida());
            if (producto.getIdProveedor() == null) {
                statement.setNull(9, java.sql.Types.BIGINT);
            } else {
                statement.setLong(9, producto.getIdProveedor());
            }
            statement.setString(10, producto.getEstado());
            return statement;
        }, keyHolder);
        return obtenerIdGenerado(keyHolder);
    }

    public int actualizar(ProductoVO producto) {
        String sql = """
                UPDATE productos
                SET nombre = ?, descripcion = ?, categoria = ?, precio_venta = ?, costo = ?, stock = ?,
                    stock_minimo = ?, unidad_medida = ?, id_proveedor = ?, estado = ?
                WHERE id_producto = ?
                """;
        return jdbcTemplate.update(sql,
                producto.getNombre(),
                producto.getDescripcion(),
                producto.getCategoria(),
                producto.getPrecioVenta(),
                producto.getCosto(),
                producto.getStock(),
                producto.getStockMinimo(),
                producto.getUnidadMedida(),
                producto.getIdProveedor(),
                producto.getEstado(),
                producto.getIdProducto());
    }

    public int eliminar(Long idProducto) {
        return jdbcTemplate.update("DELETE FROM productos WHERE id_producto = ?", idProducto);
    }

    public int descontarStock(Long idProducto, BigDecimal cantidad) {
        return jdbcTemplate.update(
                "UPDATE productos SET stock = stock - ? WHERE id_producto = ? AND stock >= ?",
                cantidad,
                idProducto,
                cantidad);
    }

    private Long obtenerIdGenerado(KeyHolder keyHolder) {
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("MySQL no devolvió el id generado para producto");
        }
        return id.longValue();
    }
}
