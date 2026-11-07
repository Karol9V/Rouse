package com.prueba.inventario.controller.mvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prueba.inventario.security.AuthenticatedUser;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;

class MvcControllersTest {

    private final Model model = mock(Model.class);

    @Test
    void controladoresDeModuloRenderizanIndexConLaVistaCorrespondiente() {
        assertEquals("index", new VentaMvcController().ventas(model));
        verify(model).addAttribute("initialView", "ventas");

        assertEquals("index", new UsuarioMvcController().usuarios(model));
        verify(model).addAttribute("initialView", "usuarios");

        assertEquals("index", new ProveedorMvcController().proveedores(model));
        verify(model).addAttribute("initialView", "proveedores");

        assertEquals("index", new ProductoMvcController().productos(model));
        verify(model).addAttribute("initialView", "productos");

        assertEquals("index", new InicioMvcController().inicio(model));
        verify(model).addAttribute("initialView", "inicio");
    }

    @Test
    void loginSoloRedirigeSiHayUsuarioAutenticadoEnLaSesion() {
        InicioMvcController controller = new InicioMvcController();
        HttpSession session = mock(HttpSession.class);

        when(session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE)).thenReturn(null);
        assertEquals("login", controller.login(session));

        when(session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE))
                .thenReturn(new AuthenticatedUser(1L, "Ana", "Lopez", "ana@example.com", "Vendedor", "token"));
        assertEquals("redirect:/", controller.login(session));
    }
}
