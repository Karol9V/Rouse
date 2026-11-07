package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UsuarioMvcController {

    @GetMapping("/usuarios")
    public String usuarios(Model model) {
        model.addAttribute("initialView", "usuarios");
        return "index";
    }
}
