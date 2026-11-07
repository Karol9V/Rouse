package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VentaMvcController {

    @GetMapping("/ventas")
    public String ventas(Model model) {
        model.addAttribute("initialView", "ventas");
        return "index";
    }
}
