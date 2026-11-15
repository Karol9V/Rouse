package com.prueba.inventario.controller.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prueba.inventario.dto.ChatMessageResponse;
import com.prueba.inventario.dto.ChatMessageRequest;
import com.prueba.inventario.security.AuthenticatedUser;
import com.prueba.inventario.service.ChatbotConversationService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ChatbotControllerTest {

    private final ChatbotConversationService conversationService = mock(ChatbotConversationService.class);
    private final ChatbotController controller = new ChatbotController(conversationService);
    private final HttpSession session = mock(HttpSession.class);
    private final AuthenticatedUser user = new AuthenticatedUser(10L, "Ana", "Pérez",
            "ana@example.test", "Administrador", "csrf");

    ChatbotControllerTest() {
        when(session.getAttribute(AuthenticatedUser.SESSION_ATTRIBUTE)).thenReturn(user);
    }

    @Test
    void enviarMensajeRechazaCuerpoVacío() {
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class,
                        () -> controller.enviarMensaje(null, session)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class,
                        () -> controller.enviarMensaje(new ChatMessageRequest(), session)).getStatusCode());
        verifyNoInteractions(conversationService);
    }

    @Test
    void enviarMensajeDevuelveRespuestaDelServicio() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setMensaje("¿Cuántos productos tenemos?");
        ChatMessageResponse expected = new ChatMessageResponse();
        expected.setRespuesta("Hay 10 productos registrados.");
        expected.setConversacionId(45L);
        when(conversationService.enviar(10L, null, "¿Cuántos productos tenemos?"))
                .thenReturn(expected);

        var response = controller.enviarMensaje(request, session);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Hay 10 productos registrados.", response.getBody().getRespuesta());
        assertEquals(45L, response.getBody().getConversacionId());
        assertTrue(response.getBody().isOk());
        verify(conversationService).enviar(10L, null, "¿Cuántos productos tenemos?");
    }
}
