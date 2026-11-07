package com.prueba.inventario.model.dao;

import com.prueba.inventario.model.vo.ProveedorVO;
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
public class ProveedorDAO {

    private static final String SELECT_COLUMNAS = """
            SELECT id_proveedor, nombre_empresa, nombre_contacto, telefono, correo, direccion, estado
            FROM proveedores
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ProveedorVO> rowMapper = (rs, rowNum) -> new ProveedorVO(
            rs.getLong("id_proveedor"),
            rs.getString("nombre_empresa"),
            rs.getString("nombre_contacto"),
            rs.getString("telefono"),
            rs.getString("correo"),
            rs.getString("direccion"),
            rs.getString("estado")
    );

    public ProveedorDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProveedorVO> listar() {
        return jdbcTemplate.query(SELECT_COLUMNAS + " ORDER BY id_proveedor", rowMapper);
    }

    public Optional<ProveedorVO> buscarPorId(Long idProveedor) {
        List<ProveedorVO> proveedores = jdbcTemplate.query(
                SELECT_COLUMNAS + " WHERE id_proveedor = ?", rowMapper, idProveedor);
        return proveedores.stream().findFirst();
    }

    public Long insertar(ProveedorVO proveedor) {
        String sql = """
                INSERT INTO proveedores
                    (nombre_empresa, nombre_contacto, telefono, correo, direccion, estado)
                VALUES (?, ?, ?, ?, ?, COALESCE(?, 'Activo'))
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, proveedor.getNombreEmpresa());
            statement.setString(2, proveedor.getNombreContacto());
            statement.setString(3, proveedor.getTelefono());
            statement.setString(4, proveedor.getCorreo());
            statement.setString(5, proveedor.getDireccion());
            statement.setString(6, proveedor.getEstado());
            return statement;
        }, keyHolder);
        return obtenerIdGenerado(keyHolder);
    }

    public int actualizar(ProveedorVO proveedor) {
        String sql = """
                UPDATE proveedores
                SET nombre_empresa = ?, nombre_contacto = ?, telefono = ?, correo = ?, direccion = ?, estado = ?
                WHERE id_proveedor = ?
                """;
        return jdbcTemplate.update(sql,
                proveedor.getNombreEmpresa(),
                proveedor.getNombreContacto(),
                proveedor.getTelefono(),
                proveedor.getCorreo(),
                proveedor.getDireccion(),
                proveedor.getEstado(),
                proveedor.getIdProveedor());
    }

    public int eliminar(Long idProveedor) {
        return jdbcTemplate.update("DELETE FROM proveedores WHERE id_proveedor = ?", idProveedor);
    }

    private Long obtenerIdGenerado(KeyHolder keyHolder) {
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("MySQL no devolvió el id generado para proveedor");
        }
        return id.longValue();
    }
}
