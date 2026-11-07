package com.prueba.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación Spring Boot de inventario.
 */
@SpringBootApplication
public class InventarioApplication {

	/**
	 * Inicia el contexto de Spring y el servidor web embebido.
	 *
	 * @param args argumentos de línea de comandos
	 */
	public static void main(String[] args) {
		SpringApplication.run(InventarioApplication.class, args);
	}

}
 