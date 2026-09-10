package com.officeplatform.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Habilita la ejecución asíncrona y le pone límites explícitos.
 *
 * <p>Sin {@code @EnableAsync}, la anotación {@code @Async} es decoración sin efecto: el método
 * corre en el hilo del llamador y nadie se entera de que la asincronía nunca existió.
 *
 * <p>El pool se define acá en lugar de usar el de Spring por omisión, que es ilimitado: la
 * indexación lee documentos enteros en memoria, y una ingesta masiva podría lanzar cientos de
 * extracciones a la vez y tumbar la aplicación por memoria. Cuatro hilos y una cola acotada hacen
 * que una avalancha se procese despacio en lugar de tumbar el servicio.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "indexingExecutor")
    public Executor indexingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("indexado-");
        // Con la cola llena, la tarea la ejecuta quien la encola. Es lento a proposito: frena la
        // ingesta en lugar de descartar en silencio el indexado de un documento.
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
