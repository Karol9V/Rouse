CREATE TABLE IF NOT EXISTS chat_conversations (
    id_conversacion BIGINT NOT NULL AUTO_INCREMENT,
    id_usuario INT UNSIGNED NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_conversacion),
    INDEX idx_chat_conversations_usuario_actualizacion (id_usuario, fecha_actualizacion),
    CONSTRAINT fk_chat_conversations_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_messages (
    id_mensaje BIGINT NOT NULL AUTO_INCREMENT,
    id_conversacion BIGINT NOT NULL,
    emisor VARCHAR(12) NOT NULL,
    contenido TEXT NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_mensaje),
    INDEX idx_chat_messages_conversacion_fecha (id_conversacion, fecha_creacion, id_mensaje),
    CONSTRAINT fk_chat_messages_conversacion
        FOREIGN KEY (id_conversacion) REFERENCES chat_conversations (id_conversacion) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
