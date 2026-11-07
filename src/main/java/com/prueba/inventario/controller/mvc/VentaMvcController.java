package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Atiende la ruta de la interfaz web para el módulo de ventas.
 */
@Controller
public class VentaMvcController {

    /**
     * Selecciona la vista inicial de ventas.
     *
     * @param model modelo de la vista
     * @return nombre de la plantilla
     */
    @GetMapping("/ventas")
    public String ventas(Model model) {
        model.addAttribute("initialView", "ventas");
        return "index";
    }
}
