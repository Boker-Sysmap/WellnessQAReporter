package com.sysmap.wellness.report;

import com.sysmap.wellness.report.service.*;
import com.sysmap.wellness.report.service.model.KPIData;
import com.sysmap.wellness.report.sheet.*;
import com.sysmap.wellness.utils.LoggerUtils;
import com.sysmap.wellness.utils.MetricsCollector;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Responsável por orquestrar e coordenar todo o processo de geração do relatório
 * consolidado do WellnessQAReporter. Esta classe atua como o componente central da
 * camada de relatórios, integrando serviços especializados, consolidando informações
 * provenientes do Qase e gerando uma visão completa, estruturada e auditável dos
 * resultados de teste, defeitos e métricas de qualidade.
 *
 * <p>O {@code ReportGenerator} funciona como o “pipeline de montagem” do relatório,
 * executando uma sequência de etapas bem definidas que abrangem desde a preparação
 * dos dados até a geração do arquivo final. Entre suas principais responsabilidades,
 * destacam-se:</p>
 *
 * <h2>1. Preparação e organização dos dados</h2>
 * <ul>
 *   <li>Validação e preparação do caminho de saída do relatório;</li>
 *   <li>Interpretação do identificador de release com base no nome do arquivo;</li>
 *   <li>Coordenação entre múltiplos projetos, cada qual com seu próprio conjunto
 *       de casos, execuções e defeitos.</li>
 * </ul>
 *
 * <h2>2. Processamento de KPIs por meio do motor de indicadores</h2>
 * <p>O {@link KPIEngine} é acionado para detectar releases, filtrar os dados por
 * release e calcular indicadores de negócio e qualidade. Além disso:</p>
 * <ul>
 *   <li>Os KPIs por release são enriquecidos com o contexto de agrupamento
 *       (multi-release);</li>
 *   <li>O histórico de indicadores é persistido no disco, permitindo análises
 *       temporais e comparativas ao longo de diferentes execuções;</li>
 *   <li>O sistema automaticamente determina a release “ativa” ou “principal” de
 *       cada projeto, utilizada em abas executivas e comparativas.</li>
 * </ul>
 *
 * <h2>3. Geração das visões e abas do relatório Excel</h2>
 *
 * <p>O {@code ReportGenerator} é responsável pela criação coordenada das seguintes
 * abas, cada uma gerada por um componente especializado:</p>
 *
 * <ul>
 *   <li><b>Painel Consolidado:</b> visão unificada dos KPIs de todas as releases
 *       e projetos, facilitando a análise de progresso e regressões;</li>
 *
 *   <li><b>Resumo Executivo por Projeto:</b> visão de alto nível dos KPIs
 *       prioritários, considerando a release principal detectada pelo sistema;</li>
 *
 *   <li><b>Resumo Funcional:</b> geração por meio do
 *       {@link FunctionalSummaryService}, analisando suítes, testes executados,
 *       resultados e distribuição funcional de defeitos;</li>
 *
 *   <li><b>Defeitos Analítico:</b> criado pelo {@link DefectAnalyticalService},
 *       correlacionando defeitos a casos, suites, usuários, datas e severidades,
 *       além de resolver links entre execuções e defeitos;</li>
 *
 *   <li><b>Dashboard de Defeitos:</b> camada de visualização simples e direta,
 *       destacando volume, severidade e comportamento dos defeitos por projeto;</li>
 *
 *   <li><b>Defeitos Sintético:</b> resumo numérico e tabelado dos defeitos, ideal
 *       para apresentações e comunicação executiva.</li>
 * </ul>
 *
 * <h2>4. Formatação, ajustes e consistência visual</h2>
 * <ul>
 *   <li>Ajuste automático de largura das colunas;</li>
 *   <li>Padronização de estilos e cabeçalhos;</li>
 *   <li>Ordenação das abas e nomenclaturas consistentes;</li>
 *   <li>Criação de workbooks e gerenciamento de streams de escrita.</li>
 * </ul>
 *
 * <h2>5. Geração do histórico RUN-BASED por release</h2>
 * <p>O {@code ReportGenerator} também é responsável por persistir snapshots
 * por release, permitindo reconstruções históricas de execução, auditorias e
 * acompanhamento temporal. Este processo inclui:</p>
 * <ul>
 *   <li>Filtragem do consolidate.json por release;</li>
 *   <li>Organização dos snapshots por projeto, ano e release;</li>
 *   <li>Geração de metadados contendo data, release id, arquivo gerado e timestamp;</li>
 *   <li>Persistência de estruturas JSON padronizadas em diretórios de histórico.</li>
 * </ul>
 *
 * <h2>6. Extensibilidade e arquitetura modular</h2>
 * <p>A classe foi projetada para suportar novas fontes de dados, novos KPIs,
 * novas abas e novas regras de enriquecimento. Como o processamento é distribuído
 * entre serviços independentes (KPIEngine, FunctionalSummaryService,
 * DefectAnalyticalService, etc.), extensões podem ser adicionadas sem impacto
 * estrutural no pipeline principal.</p>
 *
 * <p>Em resumo, o {@code ReportGenerator} é o núcleo da geração de relatórios do
 * WellnessQAReporter, sendo responsável por transformar dados brutos consolidados
 * em uma saída analítica completa, organizada por múltiplas perspectivas
 * (funcional, executiva, histórica e operacional), oferecendo insumos essenciais
 * para diagnóstico de qualidade, auditoria, planejamento e tomada de decisão.</p>
 */


public class ReportGenerator {

    /**
     * Gera todo o relatório PREMIUM, incluindo:
     * <ul>
     *   <li>KPIs multi-release (via KPIEngine)</li>
     *   <li>Resumo funcional</li>
     *   <li>Defeitos analíticos</li>
     *   <li>Dashboard de defeitos</li>
     *   <li>Defeitos sintético</li>
     *   <li>Histórico RUN-BASED (multi-release)</li>
     * </ul>
     *
     * @param consolidatedData Dados consolidados do Qase por projeto.
     * @param outputPath Caminho final do arquivo de saída (.xlsx).
     */
    public void generateReport(
        Map<String, JSONObject> consolidatedData,
        Path outputPath
    ) {
        LoggerUtils.section("📘 GERAÇÃO DE RELATÓRIO (PREMIUM)");

        long start = System.nanoTime();

        try {
            // -----------------------------------------------------
            // 1) Preparar caminho final do relatório
            // -----------------------------------------------------
            Path finalPath = prepareOutputPath(outputPath);

            String fileBasedReleaseId =
                stripExt(finalPath.getFileName().toString());

            LoggerUtils.info("🔖 ReleaseId (fallback) via nome do arquivo: " + fileBasedReleaseId);

            // Serviços auxiliares
            FunctionalSummaryService summaryService =
                new FunctionalSummaryService();
            DefectAnalyticalService defectService =
                new DefectAnalyticalService();

            // -----------------------------------------------------
            // 2) KPIs via KPIEngine (inclui histórico)
            // -----------------------------------------------------
            LoggerUtils.section("📊 KPIs via KPIEngine");

            KPIEngine kpiEngine = new KPIEngine();

            Map<String, List<KPIData>> kpisByProject =
                kpiEngine.calculateForAllProjects(consolidatedData, fileBasedReleaseId);

            LoggerUtils.success("✔ KPIs calculados com histórico gravado");

            // release "principal" por projeto (usada apenas nas abas executivas)
            Map<String, String> releaseByProject =
                buildReleaseByProjectMap(kpisByProject, fileBasedReleaseId);

            // -----------------------------------------------------
            // 3) Resumo Funcional
            // -----------------------------------------------------
            LoggerUtils.section("📘 Resumo Funcional");

            Map<String, JSONObject> functionalSummaries =
                summaryService.prepareData(consolidatedData);

            // -----------------------------------------------------
            // 4) Defeitos Analítico (enriquecido)
            // -----------------------------------------------------
            LoggerUtils.section("🐞 Defeitos (RUN-BASED)");

            Map<String, JSONArray> enrichedDefects =
                defectService.prepareData(consolidatedData);

            // -----------------------------------------------------
            // 5) Gerar Excel completo
            // -----------------------------------------------------
            try (XSSFWorkbook wb = new XSSFWorkbook()) {

                // Painel Consolidado
                ExecutiveConsolidatedSheet.create(
                    wb,
                    kpisByProject,
                    releaseByProject
                );
                wb.setSheetOrder("Painel Consolidado", 0);

                // Resumos Executivos (1 por projeto)
                for (String project : kpisByProject.keySet()) {

                    String releaseId = releaseByProject.get(project);

                    ExecutiveKPISheet.create(
                        wb,
                        kpisByProject.get(project),
                        project + " – Resumo Executivo",
                        releaseId
                    );
                }

                // Resumo Funcional
                for (String project : functionalSummaries.keySet()) {

                    JSONObject summary = functionalSummaries.get(project);
                    Map<String, JSONObject> map = new LinkedHashMap<>();
                    map.put(project, summary);

                    new FunctionalSummarySheet().create(
                        wb,
                        map,
                        project + " – Resumo Funcional"
                    );
                }

                // Defeitos Analítico
                for (String project : enrichedDefects.keySet()) {

                    Map<String, JSONArray> map = new LinkedHashMap<>();
                    map.put(project, enrichedDefects.get(project));

                    new DefectAnalyticalReportSheet().create(
                        wb,
                        map,
                        project + " – Defeitos Analítico"
                    );
                }

                // Dashboard
                for (String project : enrichedDefects.keySet()) {

                    JSONObject d = new JSONObject();
                    d.put("defects", enrichedDefects.get(project));

                    DefectsDashboardSheet.create(
                        wb,
                        d,
                        project + " – Defeitos Dashboard"
                    );
                }

                // Sintético
                for (String project : enrichedDefects.keySet()) {

                    JSONObject d = new JSONObject();
                    d.put("defects", enrichedDefects.get(project));

                    DefectsSyntheticSheet.create(
                        wb,
                        d,
                        project + " – Defeitos Sintético"
                    );
                }

                adjustAllColumns(wb);
                saveWorkbook(wb, finalPath);

                // -----------------------------------------------------
                // 6) Histórico RUN-BASED (agora multi-release)
                // -----------------------------------------------------
                generateRunBasedHistory(
                    consolidatedData,
                    enrichedDefects,
                    functionalSummaries,
                    finalPath,
                    kpisByProject
                );
            }

            long end = System.nanoTime();
            LoggerUtils.success("🏁 Relatório gerado: " + finalPath);

            MetricsCollector.timing(
                "report.totalMs",
                (end - start) / 1_000_000
            );

        } catch (Exception e) {
            LoggerUtils.error("💥 Erro crítico no ReportGenerator", e);
            MetricsCollector.increment("reportErrors");
        }
    }

    // =====================================================================================
    // 🔧 Helpers
    // =====================================================================================

    /**
     * Garante a criação do diretório de saída e retorna o caminho final
     * onde o relatório será gravado.
     *
     * @param outputPath Caminho indicado pelo usuário.
     * @return Caminho final ajustado dentro de /output/reports.
     * @throws IOException Se não for possível criar diretórios.
     */
    private Path prepareOutputPath(Path outputPath) throws IOException {

        Path dir = Path.of("output", "reports");
        if (!Files.exists(dir)) Files.createDirectories(dir);

        Path finalPath = dir.resolve(outputPath.getFileName());

        LoggerUtils.step("📄 Arquivo final: " + finalPath);
        return finalPath;
    }

    /**
     * Ajusta automaticamente a largura das colunas de todas as abas.
     *
     * @param wb Workbook Excel criado.
     */
    private void adjustAllColumns(Workbook wb) {

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {

            Sheet sheet = wb.getSheetAt(i);
            if (sheet.getRow(0) == null) continue;

            int cols = sheet.getRow(0).getPhysicalNumberOfCells();

            for (int c = 0; c < cols; c++) {
                try {
                    sheet.autoSizeColumn(c);
                    sheet.setColumnWidth(
                        c,
                        Math.min(sheet.getColumnWidth(c) + 1500, 18000)
                    );
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Salva um Workbook Excel no caminho especificado.
     *
     * @param wb Workbook a ser gravado.
     * @param finalPath Caminho destino.
     * @throws IOException Se houver falha ao escrever o arquivo.
     */
    private void saveWorkbook(Workbook wb, Path finalPath) throws IOException {

        try (FileOutputStream fos = new FileOutputStream(finalPath.toFile())) {
            wb.write(fos);
        }

        LoggerUtils.success("💾 Excel salvo em " + finalPath);
    }

    /**
     * Remove a extensão de um nome de arquivo.
     *
     * @param name Nome do arquivo.
     * @return Nome sem extensão.
     */
    private String stripExt(String name) {
        int idx = name.lastIndexOf(".");
        return idx == -1 ? name : name.substring(0, idx);
    }

    /**
     * Normaliza texto para uso em nomes de diretórios.
     *
     * @param s String de entrada.
     * @return Texto normalizado.
     */
    private String normalize(String s) {
        return s.toLowerCase()
            .replace(" ", "_")
            .replaceAll("[^a-z0-9_]", "");
    }

    // =====================================================================================
    // 🧠 Release "principal" por projeto (usado pelas abas executivas)
    // =====================================================================================

    /**
     * Determina qual release deve ser considerada “principal”
     * para cada projeto, utilizada pelo Resumo Executivo.
     *
     * @param kpisByProject KPIs agrupados por projeto.
     * @param fallback Release padrão caso nenhuma seja encontrada.
     * @return Mapa projeto → release principal.
     */
    private Map<String, String> buildReleaseByProjectMap(
        Map<String, List<KPIData>> kpisByProject,
        String fallback
    ) {
        Map<String, String> map = new LinkedHashMap<>();

        for (String project : kpisByProject.keySet()) {

            String release =
                kpisByProject.get(project).stream()
                    .filter(k -> k.getGroup() != null && !k.getGroup().isEmpty())
                    .map(KPIData::getGroup)
                    .findFirst()
                    .orElse(fallback);

            map.put(project, release);
        }

        return map;
    }

    // =====================================================================================
    // 🗂 Histórico RUN-BASED (agora por release)
    // =====================================================================================

    /**
     * Salva o histórico RUN-BASED para cada projeto e release detectada.
     * Inclui:
     * <ul>
     *   <li>consolidated filtrado por release;</li>
     *   <li>snapshot da release;</li>
     *   <li>organização por ano/projeto/release;</li>
     * </ul>
     *
     * @param consolidated Dados completos do consolidate.json.
     * @param defects Defeitos enriquecidos por projeto.
     * @param functional Resumos funcionais por projeto.
     * @param finalPath Caminho do relatório gerado.
     * @param kpisByProject KPIs multi-release calculados.
     */
    private void generateRunBasedHistory(
        Map<String, JSONObject> consolidated,
        Map<String, JSONArray> defects,
        Map<String, JSONObject> functional,
        Path finalPath,
        Map<String, List<KPIData>> kpisByProject
    ) {
        LoggerUtils.section("📚 Salvando histórico RUN-BASED (multi-release)");

        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());

        for (String project : consolidated.keySet()) {

            List<KPIData> projectKpis = kpisByProject.get(project);
            if (projectKpis == null || projectKpis.isEmpty()) {
                LoggerUtils.warn("⚠ Nenhum KPI encontrado para " + project + " ao salvar histórico.");
                continue;
            }

            // releases distintas presentes nos KPIs
            Set<String> releases = new TreeSet<>(Comparator.reverseOrder());
            for (KPIData k : projectKpis) {
                String g = k.getGroup();
                if (g != null && !g.isEmpty()) {
                    releases.add(g);
                }
            }

            if (releases.isEmpty()) {
                LoggerUtils.warn("⚠ Nenhuma release em KPIs para " + project + " ao salvar histórico.");
                continue;
            }

            for (String releaseId : releases) {

                Path relDir =
                    Paths.get("historico", "releases", normalize(project), year, releaseId);

                Path snapDir =
                    Paths.get("historico", "snapshots", normalize(project), year, releaseId);

                try {
                    Files.createDirectories(relDir);
                    Files.createDirectories(snapDir);

                    JSONObject info = new JSONObject();
                    info.put("project", project);
                    info.put("releaseId", releaseId);
                    info.put("year", year);
                    info.put("generatedAt", now.toString());
                    info.put("reportFile", finalPath.getFileName().toString());

                    // consolidated filtrado por release
                    JSONObject fullConsolidated = consolidated.get(project);
                    JSONObject filteredConsolidated =
                        filterConsolidatedByRelease(fullConsolidated, releaseId);

                    writeJson(filteredConsolidated, snapDir.resolve("consolidated.json"));
                    writeJson(info, relDir.resolve("release_snapshot.json"));

                } catch (Exception e) {
                    LoggerUtils.error("⚠ Falha ao salvar histórico para " + project +
                        " / release " + releaseId, e);
                }
            }
        }
    }

    /**
     * Retorna uma versão do consolidated contendo apenas os Test Plans
     * cujo título inclui o ID da release desejada.
     *
     * @param full JSON consolidado completo.
     * @param releaseId Identificador da release.
     * @return JSON filtrado apenas para aquela release.
     */
    private JSONObject filterConsolidatedByRelease(JSONObject full, String releaseId) {

        if (full == null) return null;

        JSONObject filtered = new JSONObject(full.toString()); // deep clone

        JSONArray originalPlans = full.optJSONArray("plan");
        JSONArray filteredPlans = new JSONArray();

        if (originalPlans != null) {
            for (int i = 0; i < originalPlans.length(); i++) {
                JSONObject p = originalPlans.optJSONObject(i);
                if (p == null) continue;

                String title = p.optString("title", "");
                if (title.contains(releaseId)) {
                    filteredPlans.put(p);
                }
            }
        }

        filtered.put("plan", filteredPlans);
        return filtered;
    }

    /**
     * Grava qualquer JSON em disco com indentação 2.
     *
     * @param json Objeto JSON a ser salvo.
     * @param path Caminho destino.
     */
    private void writeJson(JSONObject json, Path path) {
        try (BufferedWriter bw = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            bw.write(json.toString(2));
        } catch (Exception e) {
            LoggerUtils.error("❌ Erro ao salvar JSON em " + path, e);
        }
    }
}
