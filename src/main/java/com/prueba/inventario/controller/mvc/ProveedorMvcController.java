package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProveedorMvcController {

    @GetMapping("/proveedores")
    public String proveedores(Model model) {
        model.addAttribute("initialView", "proveedores");
        return "index";
    }
}
