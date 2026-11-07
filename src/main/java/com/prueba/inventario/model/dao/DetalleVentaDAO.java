package com.prueba.inventario.model.dao;

import com.prueba.inventario.model.vo.DetalleVentaVO;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Ejecuta consultas y modificaciones JDBC para detalles de venta.
 */
@Repository
public class DetalleVentaDAO {

    private static final String SELECT_COLUMNAS = """
            SELECT id_detalle, id_venta, id_producto, cantidad, precio_unitario, subtotal
            FROM detalle_venta
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DetalleVentaVO> rowMapper = (rs, rowNum) -> new DetalleVentaVO(
            rs.getLong("id_detalle"),
            rs.getLong("id_venta"),
            rs.getLong("id_producto"),
            rs.getBigDecimal("cantidad"),
            rs.getBigDecimal("precio_unitario"),
            rs.getBigDecimal("subtotal")
    );

    public DetalleVentaDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return todos los detalles ordenados por identificador */
    public List<DetalleVentaVO> listar() {
        return jdbcTemplate.query(SELECT_COLUMNAS + " ORDER BY id_detalle", rowMapper);
    }

    /**
     * Lista los detalles de una venta.
     *
     * @param idVenta identificador de la venta
     * @return detalles asociados
     */
    public List<DetalleVentaVO> listarPorVenta(Long idVenta) {
        return jdbcTemplate.query(
                SELECT_COLUMNAS + " WHERE id_venta = ? ORDER BY id_detalle", rowMapper, idVenta);
    }

    /**
     * Busca un detalle por identificador.
     *
     * @param idDetalle identificador del detalle
     * @return detalle si existe
     */
    public Optional<DetalleVentaVO> buscarPorId(Long idDetalle) {
        List<DetalleVentaVO> detalles = jdbcTemplate.query(
                SELECT_COLUMNAS + " WHERE id_detalle = ?", rowMapper, idDetalle);
        return detalles.stream().findFirst();
    }

    /**
     * Inserta un detalle y devuelve la clave generada.
     *
     * @param detalle datos que se insertarán
     * @return identificador generado
     */
    public Long insertar(DetalleVentaVO detalle) {
        String sql = """
                INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario)
                VALUES (?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, detalle.getIdVenta());
            statement.setLong(2, detalle.getIdProducto());
            statement.setBigDecimal(3, detalle.getCantidad());
            statement.setBigDecimal(4, detalle.getPrecioUnitario());
            return statement;
        }, keyHolder);
        return obtenerIdGenerado(keyHolder);
    }

    /**
     * Actualiza un detalle de venta.
     *
     * @param detalle datos actualizados, incluido su identificador
     * @return número de filas afectadas
     */
    public int actualizar(DetalleVentaVO detalle) {
        String sql = """
                UPDATE detalle_venta
                SET id_venta = ?, id_producto = ?, cantidad = ?, precio_unitario = ?
                WHERE id_detalle = ?
                """;
        return jdbcTemplate.update(sql,
                detalle.getIdVenta(),
                detalle.getIdProducto(),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getIdDetalle());
    }

    /**
     * Elimina un detalle.
     *
     * @param idDetalle identificador del detalle
     * @return número de filas eliminadas
     */
    public int eliminar(Long idDetalle) {
        return jdbcTemplate.update("DELETE FROM detalle_venta WHERE id_detalle = ?", idDetalle);
    }

    /**
     * Elimina todos los detalles asociados a una venta.
     *
     * @param idVenta identificador de la venta
     * @return número de filas eliminadas
     */
    public int eliminarPorVenta(Long idVenta) {
        return jdbcTemplate.update("DELETE FROM detalle_venta WHERE id_venta = ?", idVenta);
    }

    private Long obtenerIdGenerado(KeyHolder keyHolder) {
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("MySQL no devolvió el id generado para detalle de venta");
        }
        return id.longValue();
    }
}
