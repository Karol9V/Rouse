package com.prueba.inventario.model.dao;

import com.prueba.inventario.model.vo.ChatConversationVO;
import com.prueba.inventario.model.vo.ChatMessageVO;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Acceso JDBC a conversaciones y mensajes persistidos del chatbot.
 */
@Repository
public class ChatConversationDAO {

    private final JdbcTemplate jdbcTemplate;

    public ChatConversationDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatConversationVO crear(Long idUsuario, String titulo) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO chat_conversations (id_usuario, titulo) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, idUsuario);
            statement.setString(2, titulo);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("MySQL no devolvió el id generado para la conversación.");
        }
        return buscarPorId(id.longValue(), idUsuario)
                .orElseThrow(() -> new IllegalStateException("No se pudo recuperar la conversación recién creada."));
    }

    public List<ChatConversationVO> listarPorUsuario(Long idUsuario) {
        return jdbcTemplate.query("""
                SELECT id_conversacion, id_usuario, titulo, fecha_creacion, fecha_actualizacion
                FROM chat_conversations
                WHERE id_usuario = ?
                ORDER BY fecha_actualizacion DESC, id_conversacion DESC
                """, (rs, rowNum) -> new ChatConversationVO(
                rs.getLong("id_conversacion"),
                rs.getLong("id_usuario"),
                rs.getString("titulo"),
                toLocalDateTime(rs.getTimestamp("fecha_creacion")),
                toLocalDateTime(rs.getTimestamp("fecha_actualizacion"))), idUsuario);
    }

    public Optional<ChatConversationVO> buscarPorId(Long idConversacion, Long idUsuario) {
        List<ChatConversationVO> result = jdbcTemplate.query("""
                SELECT id_conversacion, id_usuario, titulo, fecha_creacion, fecha_actualizacion
                FROM chat_conversations
                WHERE id_conversacion = ? AND id_usuario = ?
                """, (rs, rowNum) -> new ChatConversationVO(
                rs.getLong("id_conversacion"),
                rs.getLong("id_usuario"),
                rs.getString("titulo"),
                toLocalDateTime(rs.getTimestamp("fecha_creacion")),
                toLocalDateTime(rs.getTimestamp("fecha_actualizacion"))), idConversacion, idUsuario);
        return result.stream().findFirst();
    }

    public List<ChatMessageVO> listarMensajes(Long idConversacion) {
        return jdbcTemplate.query("""
                SELECT m.id_mensaje, m.id_conversacion, m.emisor, m.contenido, m.fecha_creacion
                FROM chat_messages m
                INNER JOIN chat_conversations c ON c.id_conversacion = m.id_conversacion
                WHERE c.id_conversacion = ?
                ORDER BY m.fecha_creacion, m.id_mensaje
                """, (rs, rowNum) -> new ChatMessageVO(
                rs.getLong("id_mensaje"),
                rs.getLong("id_conversacion"),
                rs.getString("emisor"),
                rs.getString("contenido"),
                toLocalDateTime(rs.getTimestamp("fecha_creacion"))), idConversacion);
    }

    public List<ChatMessageVO> listarHistorialReciente(Long idConversacion, int limite) {
        return jdbcTemplate.query("""
                SELECT m.id_mensaje, m.id_conversacion, m.emisor, m.contenido, m.fecha_creacion
                FROM (
                    SELECT id_mensaje, id_conversacion, emisor, contenido, fecha_creacion
                    FROM chat_messages
                    WHERE id_conversacion = ?
                    ORDER BY fecha_creacion DESC, id_mensaje DESC
                    LIMIT ?
                ) m
                ORDER BY m.fecha_creacion, m.id_mensaje
                """, (rs, rowNum) -> new ChatMessageVO(
                rs.getLong("id_mensaje"),
                rs.getLong("id_conversacion"),
                rs.getString("emisor"),
                rs.getString("contenido"),
                toLocalDateTime(rs.getTimestamp("fecha_creacion"))), idConversacion, limite);
    }

    public void agregarMensaje(Long idConversacion, String emisor, String contenido) {
        jdbcTemplate.update("""
                INSERT INTO chat_messages (id_conversacion, emisor, contenido)
                VALUES (?, ?, ?)
                """, idConversacion, emisor, contenido);
        jdbcTemplate.update("""
                UPDATE chat_conversations
                SET fecha_actualizacion = CURRENT_TIMESTAMP
                WHERE id_conversacion = ?
                """, idConversacion);
    }

    public void actualizarTitulo(Long idConversacion, Long idUsuario, String titulo) {
        jdbcTemplate.update("""
                UPDATE chat_conversations
                SET titulo = ?
                WHERE id_conversacion = ? AND id_usuario = ? AND titulo = 'Nueva conversación'
                """, titulo, idConversacion, idUsuario);
    }

    public void limpiarMensajes(Long idConversacion, Long idUsuario) {
        jdbcTemplate.update("""
                DELETE m FROM chat_messages m
                INNER JOIN chat_conversations c ON c.id_conversacion = m.id_conversacion
                WHERE c.id_conversacion = ? AND c.id_usuario = ?
                """, idConversacion, idUsuario);
        jdbcTemplate.update("""
                UPDATE chat_conversations
                SET titulo = 'Nueva conversación', fecha_actualizacion = CURRENT_TIMESTAMP
                WHERE id_conversacion = ? AND id_usuario = ?
                """, idConversacion, idUsuario);
    }

    public boolean eliminar(Long idConversacion, Long idUsuario) {
        return jdbcTemplate.update("""
                DELETE FROM chat_conversations
                WHERE id_conversacion = ? AND id_usuario = ?
                """, idConversacion, idUsuario) > 0;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
