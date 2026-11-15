package com.prueba.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prueba.inventario.model.dao.ChatConversationDAO;
import com.prueba.inventario.model.vo.ChatConversationVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ChatbotConversationServiceTest {

    @Test
    void noPermiteLeerUnaConversacionDeOtroUsuario() {
        ChatConversationDAO dao = mock(ChatConversationDAO.class);
        ChatbotService chatbotService = mock(ChatbotService.class);
        when(dao.buscarPorId(41L, 7L)).thenReturn(Optional.empty());
        ChatbotConversationService service = new ChatbotConversationService(dao, chatbotService);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.listarMensajes(7L, 41L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(dao, never()).listarMensajes(41L);
    }

    @Test
    void guardaElTurnoEnLaConversacionPropietaria() {
        ChatConversationDAO dao = mock(ChatConversationDAO.class);
        ChatbotService chatbotService = mock(ChatbotService.class);
        ChatConversationVO conversation = new ChatConversationVO(
                41L, 7L, "Nueva conversación", LocalDateTime.now(), LocalDateTime.now());
        when(dao.crear(7L, "Nueva conversación")).thenReturn(conversation);
        when(dao.listarHistorialReciente(41L, 20)).thenReturn(List.of());
        when(chatbotService.responder("¿Cuántas ventas hay?", List.of()))
                .thenReturn("Hay 3 ventas.");
        ChatbotConversationService service = new ChatbotConversationService(dao, chatbotService);

        var response = service.enviar(7L, null, "  ¿Cuántas ventas hay?  ");

        assertEquals(41L, response.getConversacionId());
        assertEquals("Hay 3 ventas.", response.getRespuesta());
        verify(dao).actualizarTitulo(41L, 7L, "¿Cuántas ventas hay?");
        verify(dao).agregarMensaje(41L, "USUARIO", "¿Cuántas ventas hay?");
        verify(dao).agregarMensaje(41L, "ASISTENTE", "Hay 3 ventas.");
    }
}
