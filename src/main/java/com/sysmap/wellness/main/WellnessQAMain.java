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
 * Classe principal do projeto WellnessQA.
 *
 * Fluxo de execução:
 * 1. Carrega configurações (config.properties e endpoints.properties)
 * 2. Consulta dados da API Qase (ou lê do cache JSON local)
 * 3. Consolida dados e gera relatórios Excel
 */
public class WellnessQAMain {

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

                    // Busca da API
                    JSONArray arr = qaseClient.fetchEndpoint(project, endpoint);

                    // Cache local
                    jsonHandler.saveJsonArray(project, endpoint, arr);

                    projectData.put(endpoint, arr);
                }

                allData.put(project, projectData);
                LoggerUtils.success("✅ Projeto " + project + " concluído.");
                LoggerUtils.divider();
            }

            // === 3️⃣ Consolidação de dados dos JSONs locais ===
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
