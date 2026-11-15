package com.prueba.inventario.controller.res;

import com.prueba.inventario.dto.ChatConversationMessage;
import com.prueba.inventario.dto.ChatConversationSummary;
import com.prueba.inventario.dto.ChatMessageRequest;
import com.prueba.inventario.dto.ChatMessageResponse;
import com.prueba.inventario.security.AuthenticatedUser;
import com.prueba.inventario.service.ChatbotConversationService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints protegidos para conversar con el asistente y administrar el historial.
 */
@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotConversationService conversationService;

    public ChatbotController(ChatbotConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping({"", "/mensaje"})
    public ResponseEntity<ChatMessageResponse> enviarMensaje(
            @RequestBody(required = false) ChatMessageRequest request,
            HttpSession session) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes enviar un mensaje.");
        }
        ChatMessageResponse response = conversationService.enviar(
                usuario(session).idUsuario(), request.getConversacionId(), request.getMensaje());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/conversations")
    public List<ChatConversationSummary> listarConversaciones(HttpSession session) {
        return conversationService.listar(usuario(session).idUsuario());
    }

    @PostMapping("/conversations")
    public ResponseEntity<ChatConversationSummary> crearConversacion(HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.crear(usuario(session).idUsuario()));
    }

    @GetMapping("/conversations/{idConversacion}/messages")
    public List<ChatConversationMessage> listarMensajes(
            @PathVariable Long idConversacion,
            HttpSession session) {
        return conversationService.listarMensajes(usuario(session).idUsuario(), idConversacion);
    }

    @DeleteMapping("/conversations/{idConversacion}/messages")
    public ResponseEntity<Void> limpiarConversacion(
            @PathVariable Long idConversacion,
            HttpSession session) {
        conversationService.limpiar(usuario(session).idUsuario(), idConversacion);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conversations/{idConversacion}")
    public ResponseEntity<Void> eliminarConversacion(
            @PathVariable Long idConversacion,
            HttpSession session) {
        conversationService.eliminar(usuario(session).idUsuario(), idConversacion);
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser usuario(HttpSession session) {
        AuthenticatedUser user = session == null ? null
                : (AuthenticatedUser) session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión.");
        }
        return user;
    }
}
