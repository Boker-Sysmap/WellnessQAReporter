package com.sysmap.wellness.release;

import java.util.Map;

/**
 * <h1>IdentifierBuilder – Montagem Dinâmica do Identificador de Release</h1>
 *
 * <p>
 * Esta classe é responsável por montar o identificador de release com base em:
 * </p>
 *
 * <ul>
 *     <li>Um <b>formato dinâmico</b> definido no config.properties;</li>
 *     <li>Um <b>mapa de mnemônicos</b> e seus valores (identifierValues), vindo do JSON consolidado;</li>
 *     <li>Substituição explícita com o padrão <code>${mnemônico}</code>;</li>
 * </ul>
 *
 * <p>
 * O formato pode conter qualquer texto literal entre mnemônicos,
 * como: <code>_</code>, <code>-</code>, <code>.</code>, espaços, etc.
 * </p>
 *
 * <p>
 * Exemplo:
 * </p>
 *
 * <pre>
 *   Formato:
 *      ${sprint}_${version}_${environment}_${platform}_${language}_${testType}
 *
 *   identifierValues:
 *      sprint = S7
 *      version = 1.2.3
 *      environment = prod
 *      platform = IOS
 *      language = PT
 *      testType = funcional_manual
 *
 *   Resultado:
 *      S7_1.2.3_prod_IOS_PT_funcional_manual
 * </pre>
 *
 * <p>
 * A classe é 100% estável: caso um mnemônico esteja presente no formato,
 * mas ausente no mapa de valores, uma IllegalArgumentException será lançada.
 * </p>
 */
public class IdentifierBuilder {

    /** Mapa contendo mnemônico → valor. */
    private final Map<String, String> values;

    /** Formato dinâmico contendo placeholders ${token}. */
    private final String format;

    /** Regex do padrão ${token}. */
    private static final java.util.regex.Pattern TOKEN_PATTERN =
        java.util.regex.Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    /**
     * Construtor padrão.
     *
     * @param values mapa contendo mnemônicos e valores correspondentes
     * @param format formato no padrão ${mnemônico}
     */
    public IdentifierBuilder(Map<String, String> values, String format) {
        this.values = values;
        this.format = format;
    }

    /**
     * <h2>Método principal de construção do identificador.</h2>
     *
     * <p>
     * Identifica todos os padrões <code>${mnemônico}</code> presentes no formato,
     * valida a existência dos mnemônicos no mapa de valores e substitui cada um.
     * </p>
     *
     * @return identificador final gerado
     * @throws IllegalArgumentException caso algum mnemônico esteja ausente
     */
    public String build() {

        java.util.regex.Matcher matcher = TOKEN_PATTERN.matcher(format);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String token = matcher.group(1);

            if (!values.containsKey(token)) {
                throw new IllegalArgumentException(
                    "Mnemônico '" + token + "' não encontrado em identifierValues. " +
                        "Formato: " + format
                );
            }

            String value = values.get(token);

            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value));
        }

        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Método utilitário estático para uso rápido.
     *
     * @param format formato contendo ${tokens}
     * @param values mapa contendo mnemônicos e valores
     * @return identificador montado
     */
    public static String build(String format, Map<String, String> values) {
        return new IdentifierBuilder(values, format).build();
    }
}
