package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;
import com.prueba.inventario.security.AuthenticatedUser;

/**
 * Atiende la página inicial y la ruta de inicio de sesión.
 */
@Controller
public class InicioMvcController {

    /**
     * Muestra el formulario de acceso si no hay una sesión autenticada.
     *
     * @param session sesión HTTP actual
     * @return nombre de la vista o redirección al inicio
     */
    @GetMapping("/login")
    public String login(HttpSession session) {
        return session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE) == null
                ? "login"
                : "redirect:/";
    }

    /**
     * Selecciona la vista inicial de la aplicación.
     *
     * @param model modelo de la vista
     * @return nombre de la plantilla
     */
    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("initialView", "inicio");
        return "index";
    }
}
