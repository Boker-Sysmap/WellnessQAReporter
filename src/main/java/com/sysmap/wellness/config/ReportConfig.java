package com.sysmap.wellness.config;

import com.sysmap.wellness.utils.LoggerUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configurações específicas do relatório (nível de apresentação).
 *
 * Atualmente:
 *   - report.releases.max:
 *       0 = todas as releases (atual + histórico)
 *       1 = apenas a release atual (padrão)
 *       2 = atual + 1 anterior
 *       3 = atual + 2 anteriores
 *       ...
 */
public final class ReportConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = ReportConfig.class.getResourceAsStream("/config/config.properties")) {
            if (in != null) {
                PROPS.load(in);
                LoggerUtils.info("✔ ReportConfig carregado de /config/config.properties");
            } else {
                LoggerUtils.warn("⚠ ReportConfig: /config/config.properties não encontrado no classpath. Usando defaults.");
            }
        } catch (IOException e) {
            LoggerUtils.error("⚠ ReportConfig: erro ao carregar /config/config.properties. Usando defaults.", e);
        }
    }

    private ReportConfig() {
        // utilitário estático
    }

    /**
     * Número máximo de releases que devem aparecer no Painel Consolidado.
     *
     *  0 = todas as releases (atual + histórico)
     *  1 = apenas a release mais recente (default se não configurado ou inválido)
     *  N = N releases mais recentes
     */
    public static int getReportReleasesMax() {
        String raw = PROPS.getProperty("report.releases.max", "1").trim();

        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                LoggerUtils.warn("⚠ report.releases.max < 0. Forçando para 0 (todas as releases).");
                return 0;
            }
            return value;
        } catch (NumberFormatException e) {
            LoggerUtils.warn("⚠ report.releases.max inválido (" + raw + "). Usando default = 1.");
            return 1;
        }
    }
}
