package pe.edu.nova.java.gradle.quality;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Las versiones que el toolchain fija. Salen del catálogo del repositorio del toolchain, que el
 * build copia a {@code versions.properties}; un consumidor no declara ninguna.
 */
public final class NovaVersions {

    private static final String RESOURCE = "versions.properties";

    private final Properties versions;

    private NovaVersions(Properties versions) {
        this.versions = versions;
    }

    /**
     * Lee las versiones empaquetadas en el plugin.
     *
     * @return las versiones
     */
    public static NovaVersions load() {
        Properties versions = new Properties();
        try (InputStream in = NovaVersions.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The Nova toolchain jar does not contain " + RESOURCE);
            }
            versions.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE + " from the Nova toolchain jar", e);
        }
        return new NovaVersions(versions);
    }

    /**
     * La versión de una herramienta, con el nombre que tiene en el catálogo.
     *
     * @param alias el nombre en el catálogo, como {@code palantir.java.format}
     * @return la versión
     */
    public String of(String alias) {
        String version = versions.getProperty(alias);
        if (version == null) {
            throw new IllegalArgumentException("The Nova toolchain has no version for " + alias);
        }
        return version;
    }
}
