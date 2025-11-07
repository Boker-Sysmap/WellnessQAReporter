package com.sysmap.wellness.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Classe utilitária para registro de logs coloridos e legíveis no console.
 *
 * <p>Fornece métodos estáticos para exibição padronizada de mensagens de log
 * em diferentes níveis (informação, sucesso, aviso, erro, etc.), incluindo
 * carimbo de hora e cores ANSI para fácil leitura.</p>
 *
 * <p>Este utilitário é utilizado em praticamente todas as classes do projeto
 * <b>WellnessQA</b>, garantindo consistência na saída de logs durante
 * a execução e geração de relatórios.</p>
 *
 * <h3>Recursos principais:</h3>
 * <ul>
 *   <li>Colorização de mensagens com códigos ANSI (compatível com terminais modernos)</li>
 *   <li>Timestamp automático no formato <code>HH:mm:ss</code></li>
 *   <li>Métodos separados para cada nível de log (INFO, STEP, OK, WARN, ERROR, METRIC)</li>
 *   <li>Impressão de exceções (stack trace) quando aplicável</li>
 * </ul>
 *
 * <h3>Exemplo de uso:</h3>
 * <pre>{@code
 * LoggerUtils.info("Iniciando coleta de dados...");
 * LoggerUtils.success("Processo concluído com sucesso!");
 * LoggerUtils.warn("Arquivo de configuração não encontrado.");
 * LoggerUtils.error("Erro ao conectar à API", e);
 * }</pre>
 *
 * @author
 * @version 1.0
 */
public class LoggerUtils {

    // === Códigos ANSI para cores no terminal ===
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[35m";

    /** Formato padrão para exibição do horário nos logs (HH:mm:ss). */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Retorna o horário atual formatado para exibição no início de cada mensagem de log.
     *
     * @return String contendo o horário atual entre colchetes (ex: [14:23:05])
     */
    private static String now() {
        return "[" + LocalDateTime.now().format(TIME_FMT) + "]";
    }

    /**
     * Imprime uma linha divisória no console para separar blocos de logs.
     */
    public static void divider() {
        System.out.println(CYAN + "-------------------------------------------------------------" + RESET);
    }

    /**
     * Exibe uma mensagem informativa (nível INFO).
     *
     * @param msg Mensagem a ser exibida
     */
    public static void info(String msg) {
        System.out.println(now() + " " + BLUE + "[INFO] " + RESET + msg);
    }

    /**
     * Exibe uma mensagem indicando o andamento de uma etapa (nível STEP).
     *
     * @param msg Descrição da etapa atual
     */
    public static void step(String msg) {
        System.out.println(now() + " " + MAGENTA + "[STEP] " + RESET + msg);
    }

    /**
     * Exibe uma mensagem de sucesso (nível OK).
     *
     * @param msg Mensagem de confirmação de sucesso
     */
    public static void success(String msg) {
        System.out.println(now() + " " + GREEN + "[OK] " + RESET + msg);
    }

    /**
     * Exibe uma mensagem de aviso (nível WARN).
     *
     * @param msg Mensagem de alerta ou atenção
     */
    public static void warn(String msg) {
        System.out.println(now() + " " + YELLOW + "[WARN] " + RESET + msg);
    }

    /**
     * Exibe uma mensagem de erro (nível ERROR) com exceção associada.
     *
     * @param msg Mensagem de erro a ser exibida
     * @param t   Exceção relacionada (pode ser {@code null})
     */
    public static void error(String msg, Throwable t) {
        System.out.println(now() + " " + RED + "[ERROR] " + RESET + msg);
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }

    /**
     * Exibe uma mensagem de erro simples, sem exceção associada.
     *
     * @param msg Mensagem de erro
     */
    public static void error(String msg) {
        System.out.println(now() + " " + RED + "[ERROR] " + RESET + msg);
    }

    /**
     * Exibe uma métrica ou estatística no console.
     *
     * <p>Utilizado para fins de monitoramento de desempenho e coleta
     * de métricas durante a execução.</p>
     *
     * @param key   Nome ou chave da métrica
     * @param value Valor associado à métrica
     */
    public static void metric(String key, Object value) {
        System.out.println(now() + " " + CYAN + "[METRIC] " + RESET + key + " = " + value);
    }
}
