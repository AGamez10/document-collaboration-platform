package com.officeplatform.onlyoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Editor behaviour Document Server only applies when the host explicitly asks for it.
 *
 * <p>Macros are off unless {@code macros} and {@code plugins} arrive inside the signed
 * {@code editorConfig}. Without them the Macros entry never appears in the toolbar, which is why
 * the {@code { }} icon under "Extensiones" opened Code Highlighter instead — a different plugin
 * that happens to share that glyph.
 *
 * <p>Travels inside the JWT payload, so Document Server accepts it as coming from a trusted host
 * rather than from the browser.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeCustomization {

    /** Enables the macros engine. */
    private Boolean macros;

    /** "enable" runs macros without prompting; "warn" asks the user first. */
    private String macrosMode;

    /** Required for the plugins tab, which is where the Macros entry lives. */
    private Boolean plugins;

    private Boolean autosave;

    private Boolean forcesave;

    private Boolean comments;
}
