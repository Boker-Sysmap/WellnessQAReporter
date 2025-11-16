package com.sysmap.wellness.utils;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitário centralizado para normalização, extração e comparação de
 * identificadores de release e títulos de Test Plans do Qase.
 *
 * Evita duplicação de lógica e garante consistência em todos os serviços
 * (KPIEngine, KPIService, ScopeKPIService, histórico e dashboards).
 */
public final class ReleaseUtils {

    private ReleaseUtils() {}

    /**
     * Regex oficial para detectar releases no padrão:
     *   PROJ-YYYY-MM-RNN
     * Ex.:
     *   FULLYREPO-2025-02-R02
     */
    private static final Pattern RELEASE_PATTERN =
        Pattern.compile("([A-Z0-9_]+-[0-9]{4}-[0-9]{2}-R[0-9]{2})");

    /**
     * Normalização ULTRA-ROBUSTA:
     *  - remove acentos
     *  - converte hífens unicode (2013, 2014, 2212)
     *  - remove non-breaking spaces (00A0)
     *  - remove zero-width spaces (200B)
     *  - remove espaços comuns
     *  - uppercase
     */
    public static String normalize(String text) {
        if (text == null) return "";

        // Remove acentos
        String n = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");

        return n
            .replace("\u2013", "-")  // EN DASH
            .replace("\u2014", "-")  // EM DASH
            .replace("\u2212", "-")  // MINUS SIGN
            .replace("\u00A0", "")   // NBSP
            .replace("\u200B", "")   // Zero-width space
            .replace(" ", "")
            .trim()
            .toUpperCase();
    }

    /**
     * Extrai o identificador de release de um título de Test Plan.
     * Se não encontrar, retorna null.
     */
    public static String extractReleaseIdFromTitle(String title) {
        if (title == null) return null;

        String normalized = normalize(title);
        Matcher matcher = RELEASE_PATTERN.matcher(normalized);

        if (matcher.find()) {
            return matcher.group(1); // Identificador completo da release
        }

        return null;
    }

    /**
     * Verifica se um Test Plan pertence à release desejada.
     */
    public static boolean isPlanFromRelease(String planTitle, String releaseId) {
        if (planTitle == null || releaseId == null) return false;

        String t = normalize(planTitle);
        String r = normalize(releaseId);

        return t.contains(r);
    }
}
