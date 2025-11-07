package com.sysmap.wellness.main;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.report.ReportGenerator;
import com.sysmap.wellness.service.*;
import com.sysmap.wellness.util.LoggerUtils;
import com.sysmap.wellness.util.MetricsCollector;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Classe principal do projeto <b>Wellness QA Reporter</b>.
 *
 * <p>Responsável por orquestrar toda a execução do processo automatizado:
 * desde o carregamento das configurações até a geração do relatório final
 * em formato Excel. O fluxo de execução é dividido em etapas bem definidas,
 * garantindo rastreabilidade e isolamento de responsabilidades.</p>
 *
 * <h2>Fluxo de execução:</h2>
 * <ol>
 *     <li><b>Carrega as configurações</b> a partir dos arquivos
 *         {@code config.properties} e {@code endpoints.properties}.</li>
 *     <li><b>Consulta os dados</b> na API Qase para cada projeto e endpoint configurado.</li>
 *     <li><b>Armazena os resultados</b> em cache local (JSONs em disco).</li>
 *     <li><b>Consolida os dados</b> em uma estrutura unificada usando {@link DataConsolidator}.</li>
 *     <li><b>Gera o relatório Excel</b> com base nos dados consolidados usando {@link ReportGenerator}.</li>
 * </ol>
 *
 * <p>Logs estruturados e métricas de execução são gerenciados via
 * {@link LoggerUtils} e {@link MetricsCollector}, permitindo auditoria e
 * diagnóstico de performance.</p>
 *
 * @author Roberto
 * @version 1.1
 * @since 1.0
 */
public class WellnessQAMain {

    /**
     * Ponto de entrada principal do sistema.
     *
     * <p>Executa todo o pipeline de automação de geração de relatórios Qase,
     * incluindo as seguintes etapas:</p>
     * <ul>
     *     <li>Leitura de configurações</li>
     *     <li>Consulta de dados na API Qase</li>
     *     <li>Persistência de JSONs localmente</li>
     *     <li>Consolidação dos dados</li>
     *     <li>Geração do relatório Excel</li>
     * </ul>
     *
     * <p>Em caso de falha crítica, o erro é capturado e exibido de forma amigável
     * no console.</p>
     *
     * @param args argumentos opcionais passados via linha de comando (não utilizados).
     */
    public static void main(String[] args) {
        LoggerUtils.divider();
        LoggerUtils.success("🚀 Iniciando execução do Wellness QA Report");
        LoggerUtils.divider();

        LocalDateTime start = LocalDateTime.now();
        MetricsCollector.reset();

        try {
            // === 1️⃣ Carrega configurações ===
            List<String> projects = ConfigManager.getProjects();
            List<String> endpoints = ConfigManager.getEndpoints();

            if (projects.isEmpty()) {
                LoggerUtils.error("Nenhum projeto configurado em config.properties (chave qase.projects).", null);
                return;
            }

            if (endpoints.isEmpty()) {
                LoggerUtils.error("Nenhum endpoint configurado (ver endpoints.properties ou qase.endpoints).", null);
                return;
            }

            LoggerUtils.step("Projetos configurados: " + String.join(", ", projects));
            LoggerUtils.step("Endpoints configurados: " + String.join(", ", endpoints));

            // === 2️⃣ Consulta API Qase e salva JSONs localmente ===
            QaseClient qaseClient = new QaseClient();
            JsonHandler jsonHandler = new JsonHandler();

            Map<String, Map<String, JSONArray>> allData = new LinkedHashMap<>();

            for (String project : projects) {
                Map<String, JSONArray> projectData = new LinkedHashMap<>();

                for (String endpoint : endpoints) {
                    LoggerUtils.step("🔍 Processando [" + project + "] endpoint: " + endpoint);

                    // Busca os dados da API
                    JSONArray arr = qaseClient.fetchEndpoint(project, endpoint);

                    // Armazena em cache local
                    jsonHandler.saveJsonArray(project, endpoint, arr);

                    projectData.put(endpoint, arr);
                }

                allData.put(project, projectData);
                LoggerUtils.success("✅ Projeto " + project + " concluído.");
                LoggerUtils.divider();
            }

            // === 3️⃣ Consolidação de dados ===
            LoggerUtils.step("📦 Consolidando dados a partir dos arquivos JSON locais...");
            DataConsolidator consolidator = new DataConsolidator();
            Map<String, JSONObject> consolidatedData = consolidator.consolidateAll();

            // === 4️⃣ Geração do relatório Excel ===
            ReportGenerator reportGenerator = new ReportGenerator();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path outputPath = Path.of("output", "reports", "WellnessQA_Report_" + timestamp + ".xlsx");

            reportGenerator.generateReport(consolidatedData, outputPath);

            // === 5️⃣ Finalização ===
            LocalDateTime end = LocalDateTime.now();
            LoggerUtils.success("🏁 Execução concluída com sucesso!");
            LoggerUtils.step("Duração total: " + java.time.Duration.between(start, end).toSeconds() + " segundos");
            LoggerUtils.step("Relatório final em: " + outputPath);

            MetricsCollector.printSummary();

        } catch (Exception e) {
            LoggerUtils.error("Erro fatal durante a execução do WellnessQA", e);
        }
    }
}
