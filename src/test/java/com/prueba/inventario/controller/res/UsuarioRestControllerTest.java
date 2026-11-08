package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prueba.inventario.model.dao.UsuarioDAO;
import com.prueba.inventario.model.vo.UsuarioVO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

class UsuarioRestControllerTest {

    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UsuarioRestController controller = new UsuarioRestController(usuarioDAO, passwordEncoder);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listarRolesYBuscarPorIdMapeanLosDatosDelDao() {
        UsuarioVO usuario = new UsuarioVO(4L, "Ana", "Lopez", "ana@example.com",
                "hash", "Vendedor", "Activo", null);
        when(usuarioDAO.listar()).thenReturn(List.of(usuario));
        when(usuarioDAO.listarRoles()).thenReturn(List.of("Vendedor"));
        when(usuarioDAO.buscarPorId(4L)).thenReturn(Optional.of(usuario));
        when(usuarioDAO.buscarPorId(5L)).thenReturn(Optional.empty());

        assertEquals("Ana", controller.listar().get(0).nombre());
        assertEquals(List.of("Vendedor"), controller.listarRoles());
        assertEquals("ana@example.com", controller.buscarPorId(4L).getBody().correo());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class, () -> controller.buscarPorId(5L)).getStatusCode());
    }

    @Test
    void insertarGuardaContrasenaCodificadaYDevuelveCreated() {
        UsuarioVO usuario = new UsuarioVO(10L, "Ana", "Lopez", "ana@example.com",
                "hash-seguro", "Vendedor", "Activo", null);
        when(usuarioDAO.listarRoles()).thenReturn(List.of("Vendedor"));
        when(passwordEncoder.encode("secreta")).thenReturn("hash-seguro");
        when(usuarioDAO.insertar(any(UsuarioVO.class))).thenReturn(10L);
        when(usuarioDAO.buscarPorId(10L)).thenReturn(Optional.of(usuario));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/api/usuarios")));

        var response = controller.insertar(new UsuarioRestController.UsuarioRequest(
                "Ana", "Lopez", "ana@example.com", "secreta", "Vendedor", "Activo"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/usuarios/10", response.getHeaders().getLocation().getPath());
        assertEquals("Ana", response.getBody().nombre());
        verify(passwordEncoder).encode("secreta");
    }

    @Test
    void insertarRechazaDatosInvalidosYSinRolRegistrado() {
        when(usuarioDAO.listarRoles()).thenReturn(List.of("Vendedor"));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(
                ResponseStatusException.class, () -> controller.insertar(null)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(
                ResponseStatusException.class, () -> controller.insertar(new UsuarioRestController.UsuarioRequest(
                        "Ana", "Lopez", "ana@example.com", "", "Vendedor", "Activo"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(
                ResponseStatusException.class, () -> controller.insertar(new UsuarioRestController.UsuarioRequest(
                        "Ana", "Lopez", "ana@example.com", "secreta", "Administrador", "Activo"))).getStatusCode());
        verify(usuarioDAO, never()).insertar(any(UsuarioVO.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void actualizarConservaHashCuandoNoSeCambiaContrasena() {
        UsuarioVO actual = new UsuarioVO(6L, "Ana", "Lopez", "ana@example.com",
                "hash-actual", "Vendedor", "Activo", null);
        when(usuarioDAO.listarRoles()).thenReturn(List.of("Vendedor"));
        when(usuarioDAO.buscarPorId(6L)).thenReturn(Optional.of(actual));
        when(usuarioDAO.actualizar(any(UsuarioVO.class))).thenReturn(1);

        var response = controller.actualizar(6L, new UsuarioRestController.UsuarioRequest(
                "Ana María", "Lopez", "ana@example.com", " ", "Vendedor", "Activo"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(usuarioDAO).actualizar(org.mockito.ArgumentMatchers.argThat(
                usuario -> usuario.getIdUsuario().equals(6L)
                        && usuario.getContrasenaHash().equals("hash-actual")));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void actualizarRechazaRolInvalidoONoEncontrado() {
        when(usuarioDAO.listarRoles()).thenReturn(List.of("Vendedor"));
        UsuarioRestController.UsuarioRequest request = new UsuarioRestController.UsuarioRequest(
                "Ana", "Lopez", "ana@example.com", null, "Vendedor", "Activo");
        when(usuarioDAO.buscarPorId(99L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(
                ResponseStatusException.class, () -> controller.actualizar(1L,
                new UsuarioRestController.UsuarioRequest("Ana", "Lopez", "ana@example.com",
                        null, "Administrador", "Activo"))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class, () -> controller.actualizar(99L, request)).getStatusCode());
        verify(usuarioDAO, never()).actualizar(any(UsuarioVO.class));
    }

    @Test
    void eliminarDevuelveNoContentONotFound() {
        when(usuarioDAO.eliminar(1L)).thenReturn(1);
        when(usuarioDAO.eliminar(2L)).thenReturn(0);

        assertEquals(HttpStatus.NO_CONTENT, controller.eliminar(1L).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(
                ResponseStatusException.class, () -> controller.eliminar(2L)).getStatusCode());
    }
}
