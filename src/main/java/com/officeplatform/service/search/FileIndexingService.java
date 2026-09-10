package com.officeplatform.service.search;

import java.io.InputStream;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.officeplatform.entity.FileEntity;
import com.officeplatform.repository.FileRepository;
import com.officeplatform.service.storage.StorageService;

import lombok.extern.slf4j.Slf4j;

/**
 * Llena el texto buscable de un archivo, fuera del camino de la subida.
 *
 * <p>Asíncrono a propósito: leer y recorrer un documento de varios megabytes tarda, y quien acaba
 * de subir un archivo no tiene por qué esperar a que se indexe para ver que su subida terminó. El
 * archivo queda disponible de inmediato y su contenido se vuelve buscable un instante después.
 *
 * <p>Ningún fallo acá afecta al archivo: si la indexación no puede completarse, el documento
 * simplemente se sigue encontrando por su nombre, que es como funcionó siempre.
 */
@Service
@Slf4j
public class FileIndexingService {

    private final FileRepository fileRepository;
    private final StorageService storageService;
    private final DocumentTextExtractor extractor;

    public FileIndexingService(FileRepository fileRepository,
                               StorageService storageService,
                               DocumentTextExtractor extractor) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.extractor = extractor;
    }

    /**
     * Extrae e indexa el texto de un archivo ya almacenado.
     *
     * <p>Se le pasa el id y no la entidad porque corre en otro hilo: una entidad JPA cruzada entre
     * hilos arrastra su contexto de persistencia y termina en errores difíciles de leer.
     */
    @Async("indexingExecutor")
    @Transactional
    public void indexAsync(Long fileId) {
        index(fileId);
    }

    /** Misma indexación, en el hilo del llamador. Existe para poder probarla sin asincronía. */
    @Transactional
    public void index(Long fileId) {
        FileEntity file = fileRepository.findById(fileId).orElse(null);
        if (file == null || file.getObjectName() == null || file.getObjectName().isBlank()) {
            return;
        }
        try {
            byte[] content;
            try (InputStream in = storageService.retrieve(file.getObjectName())) {
                content = in.readAllBytes();
            }
            String text = extractor.extract(content, file.getOriginalFileName());
            file.setSearchContent(text);
            fileRepository.save(file);
            if (text != null) {
                log.debug("Indexado '{}': {} caracteres", file.getOriginalFileName(), text.length());
            }
        } catch (Exception e) {
            // El archivo existe y se descarga igual; solo no se lo va a encontrar por su texto.
            log.warn("No se pudo indexar el contenido de '{}': {}",
                    file.getOriginalFileName(), e.getMessage());
        }
    }

}
