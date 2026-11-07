package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Atiende la ruta de la interfaz web para el módulo de productos.
 */
@Controller
public class ProductoMvcController {

    /**
     * Selecciona la vista inicial de productos.
     *
     * @param model modelo de la vista
     * @return nombre de la plantilla
     */
    @GetMapping("/productos")
    public String productos(Model model) {
        model.addAttribute("initialView", "productos");
        return "index";
    }
}
