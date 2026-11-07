package com.prueba.inventario.security;

import com.prueba.inventario.model.dao.UsuarioDAO;
import com.prueba.inventario.model.vo.UsuarioVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Valida sesión, permisos por rol y token CSRF antes de procesar solicitudes.
 */
@Component
public class AuthenticatedUserInterceptor implements HandlerInterceptor {

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";
    private final UsuarioDAO usuarioDAO;

    public AuthenticatedUserInterceptor(UsuarioDAO usuarioDAO) {
        this.usuarioDAO = usuarioDAO;
    }

    /**
     * Comprueba que la solicitud pueda continuar hacia el controlador.
     *
     * @param request solicitud recibida
     * @param response respuesta HTTP que puede completarse ante un rechazo
     * @param handler controlador que atendería la solicitud
     * @return {@code true} si la solicitud está autenticada y autorizada
     * @throws IOException si ocurre un error al escribir la respuesta de error
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        HttpSession session = request.getSession(false);
        AuthenticatedUser user = session == null
                ? null
                : (AuthenticatedUser) session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE);
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (path.equals("/api/auth/logout")) {
            if (user != null && !hasValidCsrfToken(request, user)) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "Token de seguridad inválido.");
                return false;
            }
            return true;
        }

        if (user != null) {
            UsuarioVO currentUser = usuarioDAO.buscarPorId(user.idUsuario()).orElse(null);
            if (currentUser == null || currentUser.getEstado() == null
                    || !currentUser.getEstado().equalsIgnoreCase("Activo")) {
                session.invalidate();
                user = null;
            } else {
                user = new AuthenticatedUser(
                        currentUser.getIdUsuario(),
                        currentUser.getNombre(),
                        currentUser.getApellido(),
                        currentUser.getCorreo(),
                        currentUser.getRol(),
                        user.csrfToken());
                session.setAttribute(AuthenticatedUser.SESSION_ATTRIBUTE, user);
            }
        }

        if (user == null) {
            if (path.startsWith("/api/")) {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Debes iniciar sesión.");
            } else {
                response.sendRedirect("/login");
            }
            return false;
        }

        if (!RolePermissions.canAccess(user, path, request.getMethod())) {
            if (path.startsWith("/api/")) {
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "No tienes permiso para esta operación.");
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
            return false;
        }

        if (requiresCsrfToken(request) && !hasValidCsrfToken(request, user)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Token de seguridad inválido.");
            return false;
        }
        return true;
    }

    private boolean requiresCsrfToken(HttpServletRequest request) {
        return !request.getMethod().equals("GET")
                && !request.getMethod().equals("HEAD")
                && !request.getMethod().equals("OPTIONS")
                && !request.getMethod().equals("TRACE");
    }

    private boolean hasValidCsrfToken(HttpServletRequest request, AuthenticatedUser user) {
        String suppliedToken = request.getHeader(CSRF_HEADER);
        return suppliedToken != null
                && MessageDigest.isEqual(
                        suppliedToken.getBytes(StandardCharsets.UTF_8),
                        user.csrfToken().getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
