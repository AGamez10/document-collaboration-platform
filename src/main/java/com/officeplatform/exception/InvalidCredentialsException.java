package com.officeplatform.exception;

/**
 * Credenciales que no corresponden a ninguna cuenta.
 *
 * <p>Existe para separar dos situaciones que HTTP distingue y que el portal estaba mezclando.
 * {@link ShareAccessDeniedException} responde 403 y significa "sé quién sos y no te alcanza";
 * un login fallido es lo contrario, "no sé quién sos", que es un 401. Reusar el 403 para el
 * login dejaba al navegador y a cualquier cliente sin forma de distinguir una contraseña
 * equivocada de un permiso que falta, y la respuesta se confundía con el 403 que Spring Security
 * devuelve cuando una ruta no está permitida.
 *
 * <p>El mensaje nunca dice cuál de los dos datos falló: diferenciarlos convertiría al login en
 * una forma de averiguar qué cédulas están registradas.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }

}
