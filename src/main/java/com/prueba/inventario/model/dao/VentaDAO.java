package com.prueba.inventario.model.dao;

import com.prueba.inventario.model.vo.VentaVO;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class VentaDAO {

    private static final String SELECT_COLUMNAS = """
            SELECT v.id_venta, v.id_usuario, v.fecha_venta, v.subtotal, v.descuento,
                   v.total, v.metodo_pago, v.estado,
                   CONCAT_WS(' ', u.nombre, u.apellido) AS nombre_vendedor
            FROM ventas v
            LEFT JOIN usuarios u ON u.id_usuario = v.id_usuario
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<VentaVO> rowMapper = (rs, rowNum) -> {
        Timestamp fechaVenta = rs.getTimestamp("fecha_venta");
        VentaVO venta = new VentaVO(
                rs.getLong("id_venta"),
                rs.getLong("id_usuario"),
                fechaVenta == null ? null : fechaVenta.toLocalDateTime(),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("descuento"),
                rs.getBigDecimal("total"),
                rs.getString("metodo_pago"),
                rs.getString("estado"));
        venta.setNombreVendedor(rs.getString("nombre_vendedor"));
        return venta;
    };

    public VentaDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VentaVO> listar() {
        return jdbcTemplate.query(SELECT_COLUMNAS + " ORDER BY fecha_venta DESC", rowMapper);
    }

    public Optional<VentaVO> buscarPorId(Long idVenta) {
        List<VentaVO> ventas = jdbcTemplate.query(
                SELECT_COLUMNAS + " WHERE v.id_venta = ?", rowMapper, idVenta);
        return ventas.stream().findFirst();
    }

    public Long insertar(VentaVO venta) {
        String sql = """
                INSERT INTO ventas
                    (id_usuario, fecha_venta, subtotal, descuento, total, metodo_pago, estado)
                VALUES (?, COALESCE(?, CURRENT_TIMESTAMP), ?, COALESCE(?, 0.00), ?, ?, COALESCE(?, 'Completada'))
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, venta.getIdUsuario());
            if (venta.getFechaVenta() == null) {
                statement.setTimestamp(2, null);
            } else {
                statement.setTimestamp(2, Timestamp.valueOf(venta.getFechaVenta()));
            }
            statement.setBigDecimal(3, venta.getSubtotal());
            statement.setBigDecimal(4, venta.getDescuento());
            statement.setBigDecimal(5, venta.getTotal());
            statement.setString(6, venta.getMetodoPago());
            statement.setString(7, venta.getEstado());
            return statement;
        }, keyHolder);
        return obtenerIdGenerado(keyHolder);
    }

    public int actualizar(VentaVO venta) {
        String sql = """
                UPDATE ventas
                SET id_usuario = ?, subtotal = ?, descuento = ?, total = ?, metodo_pago = ?, estado = ?
                WHERE id_venta = ?
                """;
        return jdbcTemplate.update(sql,
                venta.getIdUsuario(),
                venta.getSubtotal(),
                venta.getDescuento(),
                venta.getTotal(),
                venta.getMetodoPago(),
                venta.getEstado(),
                venta.getIdVenta());
    }

    public int eliminar(Long idVenta) {
        return jdbcTemplate.update("DELETE FROM ventas WHERE id_venta = ?", idVenta);
    }

    private Long obtenerIdGenerado(KeyHolder keyHolder) {
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("MySQL no devolvió el id generado para venta");
        }
        return id.longValue();
    }
}
