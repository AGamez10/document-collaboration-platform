package com.officeplatform.exception;

/**
 * Operación que el estado del recurso no admite.
 *
 * <p>Distinta de un permiso que falta y distinta de un dato mal escrito: acá quien pide tiene
 * derecho a pedirlo y lo que pide está bien formado, pero aplicarlo sobre <i>este</i> recurso
 * produciría un resultado inaceptable —mover una carpeta dentro de sí misma, sacar del espacio
 * compartido un documento del que depende todo un equipo—. Es un 409, no un 403 ni un 500.
 *
 * <p>Antes estos casos viajaban como {@code StorageException}, que responde 500: un error de
 * operación se veía como una falla del servidor, y el usuario recibía "error interno" frente a
 * algo que él podía corregir.
 */
public class InvalidOperationException extends RuntimeException {

    public InvalidOperationException(String message) {
        super(message);
    }

}
