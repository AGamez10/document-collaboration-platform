package com.officeplatform.service.search;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.officeplatform.repository.FileRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Indexa al arrancar lo que quedó sin texto extraído.
 *
 * <p>La indexación ocurre cuando alguien sube o guarda un archivo, y eso deja fuera a todo lo que
 * entra por otra puerta: una restauración de respaldo, una ingesta masiva desde la unidad de red,
 * o los archivos anteriores a que existiera la búsqueda por contenido. Después de restaurar un
 * ZIP el catálogo entero quedaba sin texto, y la búsqueda por contenido —la función que distingue
 * a esta plataforma de una carpeta compartida— devolvía vacío sin que nada explicara por qué.
 *
 * <p>Corre desatendido y en segundo plano: el arranque no espera a que termine. Un servidor que
 * tarda diez minutos en levantar porque está leyendo documentos sería un remedio peor que la
 * enfermedad.
 *
 * <p>Con varias réplicas detrás de un balanceador, cada una encolaría el mismo trabajo. Es
 * redundante pero inofensivo: indexar dos veces el mismo archivo escribe el mismo texto.
 */
@Component
@Slf4j
public class PendingIndexingRunner {

    private final FileRepository fileRepository;
    private final FileIndexingService fileIndexingService;
    private final boolean enabled;

    public PendingIndexingRunner(
            FileRepository fileRepository,
            FileIndexingService fileIndexingService,
            @Value("${office-platform.search.index-on-startup:true}") boolean enabled) {
        this.fileRepository = fileRepository;
        this.fileIndexingService = fileIndexingService;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexPendingOnStartup() {
        if (!enabled) {
            log.info("Indexado al arranque desactivado por configuración.");
            return;
        }
        try {
            List<Long> pendientes = fileRepository.findIdsPendingIndexing();
            if (pendientes.isEmpty()) {
                return;
            }
            log.info("Indexado al arranque: {} archivo(s) sin texto extraído, encolados en segundo plano",
                    pendientes.size());
            fileIndexingService.reindexPendingAsync(pendientes);
        } catch (Exception e) {
            // Nunca puede impedir que la aplicación quede disponible: sin índice la búsqueda por
            // nombre sigue funcionando, sin aplicación no funciona nada.
            log.warn("No se pudo encolar el indexado al arranque: {}", e.getMessage());
        }
    }

}
