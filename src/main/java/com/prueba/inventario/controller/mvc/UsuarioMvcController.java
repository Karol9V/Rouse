package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Atiende la ruta de la interfaz web para el módulo de usuarios.
 */
@Controller
public class UsuarioMvcController {

    /**
     * Selecciona la vista inicial de usuarios.
     *
     * @param model modelo de la vista
     * @return nombre de la plantilla
     */
    @GetMapping("/usuarios")
    public String usuarios(Model model) {
        model.addAttribute("initialView", "usuarios");
        return "index";
    }
}
