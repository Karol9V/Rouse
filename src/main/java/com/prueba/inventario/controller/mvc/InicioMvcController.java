package com.prueba.inventario.controller.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;
import com.prueba.inventario.security.AuthenticatedUser;

@Controller
public class InicioMvcController {

    @GetMapping("/login")
    public String login(HttpSession session) {
        return session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE) == null
                ? "login"
                : "redirect:/";
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("initialView", "inicio");
        return "index";
    }
}
