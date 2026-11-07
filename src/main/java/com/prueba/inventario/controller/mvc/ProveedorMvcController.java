package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Atiende la ruta de la interfaz web para el módulo de proveedores.
 */
@Controller
public class ProveedorMvcController {

    /**
     * Selecciona la vista inicial de proveedores.
     *
     * @param model modelo de la vista
     * @return nombre de la plantilla
     */
    @GetMapping("/proveedores")
    public String proveedores(Model model) {
        model.addAttribute("initialView", "proveedores");
        return "index";
    }
}
