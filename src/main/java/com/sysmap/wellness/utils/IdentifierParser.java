package com.sysmap.wellness.utils;

import com.sysmap.wellness.config.ConfigManager;

import java.util.*;

/**
 * IdentifierParser – versão final, POSICIONAL.
 *
 * Regras confirmadas:
 * -------------------------------------------------------
 * ✔ Usa release.identifier.format exatamente como está.
 * ✔ Segmentação POSICIONAL por "_".
 * ✔ Somente version e environment são obrigatórios.
 * ✔ Campos opcionais → null se valor faltar ou não estiver na lista.
 * ✔ Colchetes permitidos: [PT] → PT.
 * ✔ Case-insensitive.
 * ✔ Tokens EXCEDENTES → ignorados (não afetam validade).
 * ✔ officialId = version + "_" + environment
 */
public final class IdentifierParser {

    // =============================================================
    // Resultado do parse
    // =============================================================
    public static final class ParsedIdentifier {

        private final String raw;
        private final String officialId;
        private final Map<String, Object> values;

        public ParsedIdentifier(String raw, String officialId, Map<String, Object> values) {
            this.raw = raw;
            this.officialId = officialId;
            this.values = values;
        }

        public String getRawIdentifier() {
            return raw;
        }

        /** Nome antigo ainda usado em alguns pontos do código */
        public String getReleaseIdentifier() {
            return officialId;
        }

        /** 🔥 NOVO MÉTODO — compatível com o pipeline atualizado */
        public String getOfficialId() {
            return officialId;
        }

        public Map<String, Object> getValues() {
            return values;
        }

        @Override
        public String toString() {
            return "ParsedIdentifier{raw='" + raw + "', officialId='" + officialId + "', values=" + values + "}";
        }
    }

    // =============================================================
    // Estrutura do formato lida do config
    // =============================================================
    private static final class FormatDefinition {
        final List<String> tokens;
        final int versionIndex;
        final int environmentIndex;

        FormatDefinition(List<String> tokens, int versionIndex, int environmentIndex) {
            this.tokens = tokens;
            this.versionIndex = versionIndex;
            this.environmentIndex = environmentIndex;
        }
    }

    private static volatile FormatDefinition CACHED;

    private IdentifierParser() {
        // uso estático.
    }

    // =============================================================
    // API PRINCIPAL
    // =============================================================
    public static ParsedIdentifier parse(String text) {

        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;

        FormatDefinition def = getOrBuildFormat();
        if (def == null) return null;

        String[] parts = trimmed.split("_");

        Map<String, Object> values = new LinkedHashMap<>();

        for (int pos = 0; pos < def.tokens.size(); pos++) {

            String token = def.tokens.get(pos);
            String rawValue = (pos < parts.length ? parts[pos] : null);

            String normalized = normalize(rawValue);

            switch (token) {

                case "version":
                    if (normalized == null) return null;
                    if (!normalized.matches(ConfigManager.getVersionPattern())) return null;
                    values.put("version", normalized);
                    break;

                case "environment":
                    if (normalized == null) return null;
                    if (!ConfigManager.isAllowedEnvironment(normalized)) return null;
                    values.put("environment", normalized);
                    break;

                case "platform":
                    values.put("platform",
                        (normalized != null && ConfigManager.isAllowedPlatform(normalized))
                            ? normalized : null
                    );
                    break;

                case "language":
                    values.put("language",
                        (normalized != null && ConfigManager.isAllowedLanguage(normalized))
                            ? normalized : null
                    );
                    break;

                case "testType":
                    values.put("testType",
                        (normalized != null && ConfigManager.isAllowedTestType(normalized))
                            ? normalized : null
                    );
                    break;

                case "date":
                    values.put("date", normalized);
                    break;

                default:
                    // Qualquer mnemônico extra definido no config
                    values.put(token, normalized);
            }
        }

        // ---------------------------------------------------------
        // Campos obrigatórios
        // ---------------------------------------------------------
        String version = (String) values.get("version");
        String env = (String) values.get("environment");

        if (version == null || env == null) return null;

        String official = version + "_" + env;

        return new ParsedIdentifier(trimmed, official, values);
    }

    // =============================================================
    // Build format from config
    // =============================================================
    private static FormatDefinition getOrBuildFormat() {
        if (CACHED != null) return CACHED;

        synchronized (IdentifierParser.class) {
            if (CACHED != null) return CACHED;

            CACHED = buildFormat();
            return CACHED;
        }
    }

    private static FormatDefinition buildFormat() {

        String fmt = ConfigManager.getReleaseIdentifierFormat();
        if (fmt == null) return null;

        String[] segments = fmt.trim().split("_");

        List<String> tokens = new ArrayList<>();
        int versionIdx = -1;
        int envIdx = -1;

        for (int i = 0; i < segments.length; i++) {
            String s = segments[i].trim();

            if (s.startsWith("${") && s.endsWith("}")) {
                String token = s.substring(2, s.length() - 1).trim();
                tokens.add(token);

                if (token.equals("version")) versionIdx = i;
                if (token.equals("environment")) envIdx = i;

            } else {
                // suportando literais no formato (muito raro, mas permitido)
                tokens.add(s);
            }
        }

        if (versionIdx < 0 || envIdx < 0) return null;

        return new FormatDefinition(tokens, versionIdx, envIdx);
    }

    // =============================================================
    // Normalização
    // =============================================================
    private static String normalize(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;

        // remove colchetes → [PT] vira PT
        if (v.startsWith("[") && v.endsWith("]") && v.length() > 2)
            v = v.substring(1, v.length() - 1);

        if (v.isEmpty()) return null;

        return v.toUpperCase(Locale.ROOT);
    }
}
