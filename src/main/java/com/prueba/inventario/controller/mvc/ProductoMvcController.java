package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProductoMvcController {

    @GetMapping("/productos")
    public String productos(Model model) {
        model.addAttribute("initialView", "productos");
        return "index";
    }
}
