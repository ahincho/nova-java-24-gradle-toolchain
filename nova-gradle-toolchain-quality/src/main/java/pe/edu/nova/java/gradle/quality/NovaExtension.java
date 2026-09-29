package pe.edu.nova.java.gradle.quality;

import java.math.BigDecimal;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Lo que un repositorio puede ajustar del toolchain, en el bloque {@code nova { }}.
 *
 * <p>Todo tiene un valor por defecto. Bajar el mínimo de cobertura o apagar los hooks se puede, pero
 * queda escrito en el {@code build.gradle.kts} del repositorio, a la vista de quien revisa.
 */
public abstract class NovaExtension {

    /** El nombre del bloque en el build. */
    public static final String NAME = "nova";

    /** Crea la extensión; la instancia Gradle. */
    public NovaExtension() {}

    /**
     * La cobertura mínima de líneas, entre 0 y 1. Por defecto 0.80, el mismo número que el preset de
     * Vitest en NestJS.
     *
     * @return la propiedad
     */
    public abstract Property<BigDecimal> getCoverageMinimum();

    /**
     * Los patrones de clases que no cuentan para la cobertura, como {@code **}{@code /*Application.class}.
     *
     * @return la propiedad
     */
    public abstract ListProperty<String> getCoverageExcludes();

    /**
     * Si el build local instala el hook {@code commit-msg}. Por defecto sí; en CI nunca.
     *
     * @return la propiedad
     */
    public abstract Property<Boolean> getGitHooks();
}
