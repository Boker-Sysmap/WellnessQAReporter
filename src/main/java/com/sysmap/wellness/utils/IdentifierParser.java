package com.sysmap.wellness.utils;

import com.sysmap.wellness.config.ConfigManager;
import java.util.*;
import java.util.regex.*;

/**
 * IdentifierParser – versão final
 * Baseado 100% no formato definido no config.properties
 * release.identifier.format=${sprint}_${version}_${environment}_${platform}_${language}_${testType}
 *
 * Regras:
 *  - version e environment são obrigatórios
 *  - demais campos opcionais (podem ser null)
 *  - identifier oficial da release = version + "_" + environment
 *  - valores entre [] podem conter qualquer coisa
 *  - valores simples: [A-Za-z0-9.]+
 *  - sem busca por substring arbitrária
 */
public final class IdentifierParser {

    // --------------------------------------------------------------------
    // Resultado final do parse
    // --------------------------------------------------------------------
    public static final class ParsedIdentifier {
        private final String rawIdentifier;
        private final String releaseIdentifier;
        private final Map<String, Object> values;

        public ParsedIdentifier(String rawIdentifier,
                                String releaseIdentifier,
                                Map<String, Object> values) {
            this.rawIdentifier = rawIdentifier;
            this.releaseIdentifier = releaseIdentifier;
            this.values = values;
        }

        /** Identificador bruto extraído do título */
        public String getRawIdentifier() { return rawIdentifier; }

        /** Identificador OFICIAL → version_environment */
        public String getReleaseIdentifier() { return releaseIdentifier; }

        /** Mnemônicos → valores capturados */
        public Map<String, Object> getValues() { return values; }

        @Override
        public String toString() {
            return "ParsedIdentifier{raw='" + rawIdentifier +
                "', official='" + releaseIdentifier +
                "', values=" + values + "}";
        }
    }

    // --------------------------------------------------------------------
    // Estrutura interna para regex preparado
    // --------------------------------------------------------------------
    private static class FormatRegex {
        final Pattern pattern;
        final List<String> tokens;

        FormatRegex(Pattern p, List<String> t) {
            this.pattern = p;
            this.tokens = t;
        }
    }

    private static volatile FormatRegex CACHED;

    private IdentifierParser() {}

    // ====================================================================
    // PUBLIC API
    // ====================================================================

    /**
     * Método principal usado pelo DataConsolidator.
     */
    public static ParsedIdentifier parse(String text) {

        if (text == null || text.isBlank())
            return null;

        FormatRegex fr = getOrBuildRegex();
        if (fr == null) return null;

        Matcher m = fr.pattern.matcher(text.trim());
        if (!m.find()) return null; // regex não casou → UNKNOWN

        // valor bruto (ex.: S6_3.2.0_PROD_AND_PT_MANUAL)
        String raw = m.group(0);

        // capturar campos
        Map<String, Object> values = new LinkedHashMap<>();

        for (int i = 0; i < fr.tokens.size(); i++) {

            String token = fr.tokens.get(i);
            String rawValue = m.group(i + 1);

            if (rawValue == null) {
                values.put(token, null);
                continue;
            }

            String v = rawValue.trim();

            // remover colchetes caso existam
            if (v.startsWith("[") && v.endsWith("]")) {
                v = v.substring(1, v.length() - 1).trim();
            }

            // normalizar para maiúsculas
            v = v.toUpperCase();

            // validar allowed lists
            if (token.equals("environment")) {
                if (!ConfigManager.isAllowedEnvironment(v)) return null;
            }
            if (token.equals("platform")) {
                if (!ConfigManager.isAllowedPlatform(v)) v = null;
            }
            if (token.equals("language")) {
                if (!ConfigManager.isAllowedLanguage(v)) v = null;
            }
            if (token.equals("testType")) {
                if (!ConfigManager.isAllowedTestType(v)) v = null;
            }

            values.put(token, v);
        }

        // -------------------------------
        // VALIDAR OBRIGATÓRIOS
        // -------------------------------
        String version = values.get("version") == null ? null : values.get("version").toString();
        String environment = values.get("environment") == null ? null : values.get("environment").toString();

        if (version == null || environment == null)
            return null;

        // validar regex da versão
        if (!version.matches(ConfigManager.getVersionPattern()))
            return null;

        // construir release oficial
        String official = version + "_" + environment;

        return new ParsedIdentifier(raw, official, values);
    }

    // ====================================================================
    // REGEX BUILDER
    // ====================================================================
    private static FormatRegex getOrBuildRegex() {
        FormatRegex c = CACHED;
        if (c != null) return c;

        synchronized (IdentifierParser.class) {
            if (CACHED != null) return CACHED;
            CACHED = buildRegex();
            return CACHED;
        }
    }

    private static FormatRegex buildRegex() {

        String format = ConfigManager.getReleaseIdentifierFormat();
        if (format == null || format.isBlank()) return null;

        StringBuilder r = new StringBuilder();
        List<String> tokens = new ArrayList<>();

        for (int i = 0; i < format.length();) {
            char c = format.charAt(i);

            if (c == '$' && i + 1 < format.length() && format.charAt(i+1) == '{') {
                int end = format.indexOf("}", i+2);

                String token = format.substring(i+2, end).trim();
                tokens.add(token);

                r.append("(")
                    .append("\\[[^\\]]+\\]") // múltiplo
                    .append("|")
                    .append("[A-Za-z0-9.]+") // simples
                    .append(")");

                i = end + 1;
                continue;
            }

            if (c == '_' || c == '-')
                r.append("[-_]");
            else {
                if ("\\.[]{}()+-*?^$|".indexOf(c) >= 0)
                    r.append("\\");
                r.append(c);
            }
            i++;
        }

        // lookahead para não capturar lixo
        r.append("(?=$|[^A-Za-z0-9\\[])");

        Pattern pattern = Pattern.compile(r.toString(), Pattern.CASE_INSENSITIVE);
        return new FormatRegex(pattern, tokens);
    }
}
