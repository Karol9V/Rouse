package com.prueba.inventario.service;

import com.prueba.inventario.dto.ChatConversationMessage;
import com.prueba.inventario.dto.ChatConversationSummary;
import com.prueba.inventario.dto.ChatMessageResponse;
import com.prueba.inventario.model.dao.ChatConversationDAO;
import com.prueba.inventario.model.vo.ChatConversationVO;
import com.prueba.inventario.model.vo.ChatMessageVO;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coordina la persistencia del historial y el uso de contexto por conversación.
 */
@Service
public class ChatbotConversationService {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_HISTORY_MESSAGES = 20;

    private final ChatConversationDAO conversationDAO;
    private final ChatbotService chatbotService;

    public ChatbotConversationService(
            ChatConversationDAO conversationDAO,
            ChatbotService chatbotService) {
        this.conversationDAO = conversationDAO;
        this.chatbotService = chatbotService;
    }

    @Transactional
    public ChatConversationSummary crear(Long idUsuario) {
        return ChatConversationSummary.from(conversationDAO.crear(idUsuario, "Nueva conversación"));
    }

    public List<ChatConversationSummary> listar(Long idUsuario) {
        return conversationDAO.listarPorUsuario(idUsuario).stream()
                .map(ChatConversationSummary::from)
                .toList();
    }

    public List<ChatConversationMessage> listarMensajes(Long idUsuario, Long idConversacion) {
        requireConversation(idUsuario, idConversacion);
        return conversationDAO.listarMensajes(idConversacion).stream()
                .map(ChatConversationMessage::from)
                .toList();
    }

    public ChatMessageResponse enviar(Long idUsuario, Long idConversacion, String mensaje) {
        String content = mensaje == null ? "" : mensaje.trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un mensaje antes de enviarlo.");
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El mensaje supera el límite permitido de 1000 caracteres.");
        }

        ChatConversationVO conversation = idConversacion == null
                ? conversationDAO.crear(idUsuario, "Nueva conversación")
                : requireConversation(idUsuario, idConversacion);
        List<ChatMessageVO> history = conversationDAO.listarHistorialReciente(
                conversation.idConversacion(), MAX_HISTORY_MESSAGES);
        if (history.isEmpty()) {
            conversationDAO.actualizarTitulo(
                    conversation.idConversacion(), idUsuario, crearTitulo(content));
        }

        conversationDAO.agregarMensaje(conversation.idConversacion(), "USUARIO", content);
        String answer = chatbotService.responder(
                content, history);
        conversationDAO.agregarMensaje(conversation.idConversacion(), "ASISTENTE", answer);

        ChatMessageResponse response = new ChatMessageResponse();
        response.setRespuesta(answer);
        response.setConversacionId(conversation.idConversacion());
        response.setOk(true);
        return response;
    }

    @Transactional
    public void limpiar(Long idUsuario, Long idConversacion) {
        requireConversation(idUsuario, idConversacion);
        conversationDAO.limpiarMensajes(idConversacion, idUsuario);
    }

    @Transactional
    public void eliminar(Long idUsuario, Long idConversacion) {
        if (!conversationDAO.eliminar(idConversacion, idUsuario)) {
            throw conversationNotFound();
        }
    }

    private ChatConversationVO requireConversation(Long idUsuario, Long idConversacion) {
        return conversationDAO.buscarPorId(idConversacion, idUsuario)
                .orElseThrow(ChatbotConversationService::conversationNotFound);
    }

    private String crearTitulo(String firstMessage) {
        String title = firstMessage.replaceAll("\\s+", " ").trim();
        if (title.length() > 80) {
            title = title.substring(0, 77).stripTrailing() + "...";
        }
        return title.isEmpty() ? "Nueva conversación" : title;
    }

    private static ResponseStatusException conversationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró esa conversación.");
    }
}
