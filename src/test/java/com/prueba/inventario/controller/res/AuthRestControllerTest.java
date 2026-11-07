package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prueba.inventario.model.dao.UsuarioDAO;
import com.prueba.inventario.model.vo.UsuarioVO;
import com.prueba.inventario.security.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthRestControllerTest {

    @Test
    void loginCreatesSessionOnlyAfterPasswordAndActiveStatusAreVerified() {
        UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UsuarioVO usuario = new UsuarioVO(
                7L, "Ana", "Lopez", "ana@example.com", "bcrypt-hash",
                "Vendedor", "Activo", null);
        when(usuarioDAO.buscarPorCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("correcta", "bcrypt-hash")).thenReturn(true);
        AuthRestController controller = new AuthRestController(usuarioDAO, passwordEncoder);
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = controller.iniciarSesion(
                new AuthRestController.LoginRequest("ana@example.com", "correcta"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        AuthRestController.AuthResponse body = (AuthRestController.AuthResponse) response.getBody();
        assertNotNull(body);
        assertEquals("Vendedor", body.rol());
        assertFalse(body.csrfToken().isBlank());
        HttpSession session = request.getSession(false);
        assertNotNull(session);
        assertEquals(7L, ((AuthenticatedUser) session.getAttribute(
                AuthenticatedUser.SESSION_ATTRIBUTE)).idUsuario());
    }

    @Test
    void inactiveAccountCannotStartASession() {
        UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UsuarioVO usuario = new UsuarioVO(
                7L, "Ana", "Lopez", "ana@example.com", "bcrypt-hash",
                "Vendedor", "Inactivo", null);
        when(usuarioDAO.buscarPorCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
        AuthRestController controller = new AuthRestController(usuarioDAO, passwordEncoder);
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = controller.iniciarSesion(
                new AuthRestController.LoginRequest("ana@example.com", "correcta"), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(request.getSession(false) == null);
    }
}
