package run.halo.app.service;

import java.util.Set;
import org.springframework.lang.NonNull;
import run.halo.app.model.dto.UrlReplaceResult;
import run.halo.app.model.enums.ReplaceableModule;

/**
 * Service for scanning and executing URL replacements across modules.
 *
 * @author halo-dev
 */
public interface UrlReplaceScanService {

    /**
     * Scan modules for occurrences of oldUrl without modifying any data.
     *
     * @param oldUrl the old URL to search for
     * @param newUrl the new URL (used for warning detection)
     * @param modules the modules to scan
     * @return scan result with per-module statistics
     */
    UrlReplaceResult scan(@NonNull String oldUrl, @NonNull String newUrl,
        @NonNull Set<ReplaceableModule> modules);

    /**
     * Scan first, then execute URL replacement on the selected modules.
     * Returns the pre-execution scan statistics.
     *
     * @param oldUrl the old URL to replace
     * @param newUrl the new URL to replace with
     * @param modules the modules to apply replacement on
     * @return pre-execution scan result
     */
    UrlReplaceResult execute(@NonNull String oldUrl, @NonNull String newUrl,
        @NonNull Set<ReplaceableModule> modules);
}
