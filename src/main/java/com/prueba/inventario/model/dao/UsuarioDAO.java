package com.prueba.inventario.model.dao;

import com.prueba.inventario.model.vo.UsuarioVO;
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

/**
 * Ejecuta consultas y modificaciones JDBC para usuarios y roles.
 */
@Repository
public class UsuarioDAO {

    private static final String SELECT_COLUMNAS = """
            SELECT id_usuario, nombre, apellido, correo, contrasena_hash, rol, estado, fecha_registro
            FROM usuarios
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UsuarioVO> rowMapper = (rs, rowNum) -> {
        Timestamp fechaRegistro = rs.getTimestamp("fecha_registro");
        return new UsuarioVO(
                rs.getLong("id_usuario"),
                rs.getString("nombre"),
                rs.getString("apellido"),
                rs.getString("correo"),
                rs.getString("contrasena_hash"),
                rs.getString("rol"),
                rs.getString("estado"),
                fechaRegistro == null ? null : fechaRegistro.toLocalDateTime()
        );
    };

    public UsuarioDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return todos los usuarios ordenados por identificador */
    public List<UsuarioVO> listar() {
        return jdbcTemplate.query(SELECT_COLUMNAS + " ORDER BY id_usuario", rowMapper);
    }

    /**
     * Busca un usuario por identificador.
     *
     * @param idUsuario identificador del usuario
     * @return usuario si existe
     */
    public Optional<UsuarioVO> buscarPorId(Long idUsuario) {
        List<UsuarioVO> usuarios = jdbcTemplate.query(
                SELECT_COLUMNAS + " WHERE id_usuario = ?", rowMapper, idUsuario);
        return usuarios.stream().findFirst();
    }

    /**
     * Busca un usuario por correo sin distinguir mayúsculas.
     *
     * @param correo correo que se desea consultar
     * @return usuario si existe
     */
    public Optional<UsuarioVO> buscarPorCorreo(String correo) {
        List<UsuarioVO> usuarios = jdbcTemplate.query(
                SELECT_COLUMNAS + " WHERE LOWER(correo) = LOWER(?)", rowMapper, correo);
        return usuarios.stream().findFirst();
    }

    /** @return roles no vacíos registrados en el sistema */
    public List<String> listarRoles() {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT TRIM(rol)
                FROM usuarios
                WHERE rol IS NOT NULL AND TRIM(rol) <> ''
                ORDER BY TRIM(rol)
                """, String.class);
    }

    /**
     * Inserta un usuario y devuelve la clave generada.
     *
     * @param usuario datos que se insertarán
     * @return identificador generado
     */
    public Long insertar(UsuarioVO usuario) {
        String sql = """
                INSERT INTO usuarios (nombre, apellido, correo, contrasena_hash, rol, estado)
                VALUES (?, ?, ?, ?, COALESCE(?, 'Vendedor'), COALESCE(?, 'Activo'))
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, usuario.getNombre());
            statement.setString(2, usuario.getApellido());
            statement.setString(3, usuario.getCorreo());
            statement.setString(4, usuario.getContrasenaHash());
            statement.setString(5, usuario.getRol());
            statement.setString(6, usuario.getEstado());
            return statement;
        }, keyHolder);
        return obtenerIdGenerado(keyHolder, "usuario");
    }

    /**
     * Actualiza un usuario.
     *
     * @param usuario datos actualizados, incluido su identificador
     * @return número de filas afectadas
     */
    public int actualizar(UsuarioVO usuario) {
        String sql = """
                UPDATE usuarios
                SET nombre = ?, apellido = ?, correo = ?, contrasena_hash = ?, rol = ?, estado = ?
                WHERE id_usuario = ?
                """;
        return jdbcTemplate.update(sql,
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.getContrasenaHash(),
                usuario.getRol(),
                usuario.getEstado(),
                usuario.getIdUsuario());
    }

    /**
     * Elimina el usuario indicado.
     *
     * @param idUsuario identificador del usuario
     * @return número de filas eliminadas
     */
    public int eliminar(Long idUsuario) {
        return jdbcTemplate.update("DELETE FROM usuarios WHERE id_usuario = ?", idUsuario);
    }

    private Long obtenerIdGenerado(KeyHolder keyHolder, String entidad) {
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("MySQL no devolvió el id generado para " + entidad);
        }
        return id.longValue();
    }
}
