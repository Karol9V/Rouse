package com.prueba.inventario.controller.res;

import com.prueba.inventario.model.dao.UsuarioDAO;
import com.prueba.inventario.model.vo.UsuarioVO;
import java.time.LocalDateTime;
import java.net.URI;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * Expone operaciones REST para usuarios y roles del sistema.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioRestController {

    private final UsuarioDAO usuarioDAO;
    private final PasswordEncoder passwordEncoder;

    public UsuarioRestController(UsuarioDAO usuarioDAO, PasswordEncoder passwordEncoder) {
        this.usuarioDAO = usuarioDAO;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Lista usuarios sin exponer hashes de contraseña.
     *
     * @return respuestas públicas de los usuarios
     */
    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioDAO.listar().stream().map(UsuarioResponse::from).toList();
    }

    /**
     * Lista los roles existentes en el sistema.
     *
     * @return nombres de rol disponibles
     */
    @GetMapping("/roles")
    public List<String> listarRoles() {
        return usuarioDAO.listarRoles();
    }

    /**
     * Busca un usuario por identificador.
     *
     * @param id identificador del usuario
     * @return usuario o {@code 404 Not Found}
     */
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> buscarPorId(@PathVariable Long id) {
        return usuarioDAO.buscarPorId(id)
                .map(UsuarioResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Valida y registra un usuario con contraseña codificada.
     *
     * @param request datos del usuario nuevo
     * @return usuario creado o {@code 400 Bad Request}
     */
    @PostMapping
    public ResponseEntity<UsuarioResponse> insertar(@RequestBody(required = false) UsuarioRequest request) {
        if (request == null || !request.isValidForCreate()
                || !usuarioDAO.listarRoles().contains(request.rol().trim())) {
            return ResponseEntity.badRequest().build();
        }
        UsuarioVO usuario = request.toUsuario(passwordEncoder.encode(request.contrasena()));
        Long id = usuarioDAO.insertar(usuario);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(id).toUri();
        UsuarioResponse response = usuarioDAO.buscarPorId(id)
                .map(UsuarioResponse::from)
                .orElseThrow();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Actualiza un usuario; si la contraseña se omite conserva el hash existente.
     *
     * @param id identificador del usuario
     * @param request datos actualizados
     * @return {@code 204 No Content}, {@code 404 Not Found} o {@code 400 Bad Request}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> actualizar(@PathVariable Long id, @RequestBody(required = false) UsuarioRequest request) {
        if (request == null || request.nombre() == null || request.nombre().isBlank()
                || request.apellido() == null || request.apellido().isBlank()
                || request.correo() == null || request.correo().isBlank()
                || request.rol() == null || !usuarioDAO.listarRoles().contains(request.rol().trim())) {
            return ResponseEntity.badRequest().build();
        }
        UsuarioVO usuarioActual = usuarioDAO.buscarPorId(id).orElse(null);
        if (usuarioActual == null) {
            return ResponseEntity.notFound().build();
        }
        UsuarioVO usuario = request.toUsuario(
                request.contrasena() == null || request.contrasena().isBlank()
                        ? usuarioActual.getContrasenaHash()
                        : passwordEncoder.encode(request.contrasena()));
        usuario.setIdUsuario(id);
        usuarioDAO.actualizar(usuario);
        return ResponseEntity.noContent().build();
    }

    /**
     * Elimina un usuario.
     *
     * @param id identificador del usuario
     * @return {@code 204 No Content} o {@code 404 Not Found}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        return usuarioDAO.eliminar(id) == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.noContent().build();
    }

    /** Datos de entrada para crear o actualizar un usuario. */
    public record UsuarioRequest(
            String nombre,
            String apellido,
            String correo,
            String contrasena,
            String rol,
            String estado) {

        private boolean isValidForCreate() {
            return nombre != null && !nombre.isBlank()
                    && apellido != null && !apellido.isBlank()
                    && correo != null && !correo.isBlank()
                    && contrasena != null && !contrasena.isBlank()
                    && rol != null && !rol.isBlank();
        }

        private UsuarioVO toUsuario(String contrasenaHash) {
            return new UsuarioVO(null, nombre, apellido, correo, contrasenaHash, rol, estado, LocalDateTime.now());
        }
    }

    /** Campos del usuario que se exponen en la API. */
    public record UsuarioResponse(
            Long idUsuario,
            String nombre,
            String apellido,
            String correo,
            String rol,
            String estado,
            LocalDateTime fechaRegistro) {

        private static UsuarioResponse from(UsuarioVO usuario) {
            return new UsuarioResponse(
                    usuario.getIdUsuario(),
                    usuario.getNombre(),
                    usuario.getApellido(),
                    usuario.getCorreo(),
                    usuario.getRol(),
                    usuario.getEstado(),
                    usuario.getFechaRegistro());
        }
    }
}
