package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.mock.web.MockHttpSession;
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

    @Test
    void invalidCredentialsAndUnconfiguredRoleDoNotCreateASession() {
        UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthRestController controller = new AuthRestController(usuarioDAO, passwordEncoder);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.iniciarSesion(null, request).getStatusCode());
        verifyNoInteractions(usuarioDAO, passwordEncoder);

        UsuarioVO usuario = new UsuarioVO(
                7L, "Ana", "Lopez", "ana@example.com", "bcrypt-hash",
                "Auditor", "Activo", null);
        when(usuarioDAO.buscarPorCorreo("ana@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("correcta", "bcrypt-hash")).thenReturn(true);

        ResponseEntity<?> response = controller.iniciarSesion(
                new AuthRestController.LoginRequest("ana@example.com", "correcta"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(request.getSession(false) == null);
    }

    @Test
    void usuarioActualReturnsDataFromSession() {
        AuthenticatedUser user = new AuthenticatedUser(
                9L, "Eva", "Diaz", "eva@example.com", "Administrador", "csrf-token");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUser.SESSION_ATTRIBUTE, user);
        AuthRestController controller = new AuthRestController(
                mock(UsuarioDAO.class), mock(PasswordEncoder.class));

        AuthRestController.AuthResponse response = controller.usuarioActual(session);

        assertEquals(9L, response.idUsuario());
        assertEquals("Eva Diaz", response.nombreCompleto());
        assertEquals("Administrador", response.rol());
        assertEquals("csrf-token", response.csrfToken());
    }

    @Test
    void logoutInvalidatesExistingSessionAndSucceedsWithoutOne() {
        AuthRestController controller = new AuthRestController(
                mock(UsuarioDAO.class), mock(PasswordEncoder.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        assertEquals(HttpStatus.NO_CONTENT, controller.cerrarSesion(request).getStatusCode());
        assertTrue(session.isInvalid());

        assertEquals(HttpStatus.NO_CONTENT,
                controller.cerrarSesion(new MockHttpServletRequest()).getStatusCode());
    }
}
