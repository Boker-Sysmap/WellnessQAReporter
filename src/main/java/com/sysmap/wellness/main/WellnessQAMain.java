package com.sysmap.wellness.main;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.history.HistoryDirectoryManager;
import com.sysmap.wellness.report.generator.ReportGenerator;
import com.sysmap.wellness.service.*;
import com.sysmap.wellness.service.DataConsolidator;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Classe principal do projeto <b>Wellness QA Reporter</b>.
 * Versão PREMIUM:
 *  - Eventos separados em métodos menores (Clean Architecture)
 *  - Logs padronizados
 *  - Validações robustas
 *  - Preparação de diretórios
 *  - Tratamento específico de falhas
 *  - Isolamento de responsabilidades
 *  - Padronização de timestamps e nomenclaturas
 */
public class WellnessQAMain {

    private static final DateTimeFormatter REPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Path REPORT_DIR = Paths.get("output", "reports");

    public static void main(String[] args) {
        LoggerUtils.divider();
        LoggerUtils.success("🚀 Iniciando execução do Wellness QA Reporter (versão PREMIUM)");
        LoggerUtils.divider();

        ZonedDateTime start = ZonedDateTime.now(ZoneId.systemDefault());
        MetricsCollector.reset();

        try {
            List<String> projects = validateProjects();
            List<String> endpoints = validateEndpoints();
            validateApiToken();

            initializeHistory(projects);
            prepareOutputDirectory();

            Map<String, JSONObject> consolidatedData = executeDataPipeline(projects, endpoints);

            Path outputFile = generateExcelReport(consolidatedData);

            finalizeExecution(start, outputFile);

        } catch (Exception e) {
            LoggerUtils.error("❌ ERRO FATAL no WellnessQAReporter", e);
        }
    }

    // ============================================================
    //  VALIDADORES
    // ============================================================

    private static List<String> validateProjects() {
        List<String> projects = ConfigManager.getProjects();
        if (projects.isEmpty()) {
            throw new IllegalStateException("Nenhum projeto configurado em config.properties (chave 'qase.projects').");
        }
        LoggerUtils.step("📌 Projetos configurados: " + String.join(", ", projects));
        return projects;
    }

    private static List<String> validateEndpoints() {
        List<String> endpoints = ConfigManager.getActiveEndpoints();
        if (endpoints.isEmpty()) {
            throw new IllegalStateException("Nenhum endpoint ativo configurado (endpoints.properties ou qase.endpoints).");
        }
        LoggerUtils.step("📌 Endpoints configurados: " + String.join(", ", endpoints));
        return endpoints;
    }

    private static void validateApiToken() {
        String token = ConfigManager.getApiToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Token da API Qase não configurado (parâmetro qase.api.token).");
        }
        LoggerUtils.step("🔐 Token Qase configurado corretamente.");
    }

    // ============================================================
    //  PREPARAÇÃO DE ESTRUTURA
    // ============================================================

    private static void initializeHistory(List<String> projects) {
        LoggerUtils.divider();
        LoggerUtils.step("📚 Inicializando estrutura de histórico...");

        Properties raw = ConfigManager.getRawProperties();
        raw.setProperty("projects", String.join(",", projects)); // garante coerência

        HistoryDirectoryManager historyManager = new HistoryDirectoryManager(raw);
        historyManager.initializeHistoryStructure();

        LoggerUtils.success("📁 Estrutura de histórico preparada.");
        LoggerUtils.divider();
    }

    private static void prepareOutputDirectory() throws IOException {
        Files.createDirectories(REPORT_DIR);
        LoggerUtils.step("📁 Diretório de output confirmado: " + REPORT_DIR.toAbsolutePath());
    }

    // ============================================================
    //  PIPELINE PRINCIPAL
    // ============================================================

    private static Map<String, JSONObject> executeDataPipeline(List<String> projects, List<String> endpoints) {

        LoggerUtils.step("🌐 Iniciando pipeline de coleta de dados Qase...");

        QaseClient qaseClient = new QaseClient();
        JsonHandler jsonHandler = new JsonHandler();

        for (String project : projects) {
            processProject(project, endpoints, qaseClient, jsonHandler);
        }

        LoggerUtils.step("📦 Consolidando dados a partir dos arquivos locais...");
        DataConsolidator consolidator = new DataConsolidator();
        return consolidator.consolidateAll();
    }

    private static void processProject(String project, List<String> endpoints,
                                       QaseClient qaseClient, JsonHandler jsonHandler) {

        LoggerUtils.divider();
        LoggerUtils.step("▶️ Processando projeto: " + project);

        for (String endpoint : endpoints) {
            LoggerUtils.step("🔍 Endpoint: " + endpoint);

            try {
                JSONArray response = qaseClient.fetchEndpoint(project, endpoint);
                jsonHandler.saveJsonArray(project, endpoint, response);

            } catch (Exception ex) {
                LoggerUtils.error("⚠️ Falha ao processar endpoint " + endpoint + " do projeto " + project, ex);
            }
        }

        LoggerUtils.success("✅ Projeto " + project + " processado.");
    }

    // ============================================================
    //  RELATÓRIO
    // ============================================================

    private static Path generateExcelReport(Map<String, JSONObject> consolidatedData) throws IOException {

        ReportGenerator generator = new ReportGenerator();
        String timestamp = ZonedDateTime.now().format(REPORT_TIMESTAMP);

        Path outputPath = REPORT_DIR.resolve("WellnessQA_Report_" + timestamp + ".xlsx");

        LoggerUtils.step("📊 Gerando relatório Excel...");
        generator.generateReport(consolidatedData, outputPath);

        LoggerUtils.success("📄 Relatório gerado: " + outputPath.toAbsolutePath());
        return outputPath;
    }

    // ============================================================
    //  FINALIZAÇÃO
    // ============================================================

    private static void finalizeExecution(ZonedDateTime start, Path outputPath) {

        ZonedDateTime end = ZonedDateTime.now();
        long seconds = Duration.between(start, end).toSeconds();

        LoggerUtils.divider();
        LoggerUtils.success("🏁 Execução concluída com sucesso!");
        LoggerUtils.step("⏱ Duração total: " + seconds + " segundos");
        LoggerUtils.step("📁 Relatório final: " + outputPath);
        LoggerUtils.divider();

        MetricsCollector.printSummary();
    }
}
