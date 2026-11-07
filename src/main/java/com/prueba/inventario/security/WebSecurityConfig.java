package com.prueba.inventario.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configura el codificador de contraseñas y el interceptor de autenticación.
 */
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    private final AuthenticatedUserInterceptor authenticatedUserInterceptor;

    public WebSecurityConfig(AuthenticatedUserInterceptor authenticatedUserInterceptor) {
        this.authenticatedUserInterceptor = authenticatedUserInterceptor;
    }

    /**
     * Crea el codificador BCrypt usado para contraseñas.
     *
     * @return codificador BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Registra el interceptor y excluye las rutas públicas y recursos estáticos.
     *
     * @param registry registro de interceptores MVC
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticatedUserInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/api/auth/login",
                        "/css/**",
                        "/js/**",
                        "/img/**",
                        "/favicon.ico",
                        "/error");
    }
}
