package com.prueba.inventario.controller.res;

import com.prueba.inventario.model.dao.UsuarioDAO;
import com.prueba.inventario.model.vo.UsuarioVO;
import com.prueba.inventario.security.AuthenticatedUser;
import com.prueba.inventario.security.RolePermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthRestController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UsuarioDAO usuarioDAO;
    private final PasswordEncoder passwordEncoder;

    public AuthRestController(UsuarioDAO usuarioDAO, PasswordEncoder passwordEncoder) {
        this.usuarioDAO = usuarioDAO;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> iniciarSesion(
            @RequestBody(required = false) LoginRequest request,
            HttpServletRequest httpRequest) {
        if (request == null || request.correo() == null || request.correo().isBlank()
                || request.contrasena() == null || request.contrasena().isBlank()) {
            return credencialesInvalidas();
        }

        UsuarioVO usuario = usuarioDAO.buscarPorCorreo(request.correo().trim()).orElse(null);
        if (usuario == null || usuario.getContrasenaHash() == null
                || usuario.getEstado() == null || !usuario.getEstado().equalsIgnoreCase("Activo")
                || !passwordEncoder.matches(request.contrasena(), usuario.getContrasenaHash())) {
            return credencialesInvalidas();
        }
        if (!RolePermissions.isAdministrator(usuario.getRol())
                && !RolePermissions.isVendor(usuario.getRol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "El rol de este usuario no tiene permisos configurados."));
        }

        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        String csrfToken = crearTokenCsrf();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.getRol(),
                csrfToken);
        httpRequest.getSession(true).setAttribute(
                AuthenticatedUser.SESSION_ATTRIBUTE, authenticatedUser);
        return ResponseEntity.ok(AuthResponse.from(authenticatedUser));
    }

    @GetMapping("/me")
    public AuthResponse usuarioActual(HttpSession session) {
        AuthenticatedUser user = (AuthenticatedUser) session.getAttribute(
                AuthenticatedUser.SESSION_ATTRIBUTE);
        return AuthResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Map<String, String>> credencialesInvalidas() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Correo o contraseña incorrectos."));
    }

    private String crearTokenCsrf() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public record LoginRequest(String correo, String contrasena) {
    }

    public record AuthResponse(
            Long idUsuario,
            String nombre,
            String apellido,
            String nombreCompleto,
            String correo,
            String rol,
            String csrfToken) {

        private static AuthResponse from(AuthenticatedUser user) {
            return new AuthResponse(
                    user.idUsuario(),
                    user.nombre(),
                    user.apellido(),
                    user.nombreCompleto(),
                    user.correo(),
                    user.rol(),
                    user.csrfToken());
        }
    }
}
