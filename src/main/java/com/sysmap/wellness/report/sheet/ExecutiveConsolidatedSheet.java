package com.sysmap.wellness.report.sheet;

import com.sysmap.wellness.report.kpi.history.KPIHistoryRecord;
import com.sysmap.wellness.report.kpi.history.KPIHistoryRepository;
import com.sysmap.wellness.report.service.model.KPIData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.*;

/**
 * Gera o <b>Painel Consolidado</b>, aba executiva que apresenta uma visão
 * resumida e histórica dos principais KPIs de cada projeto.
 *
 * <p>Seu objetivo é fornecer uma linha por release, exibindo KPIs consolidados
 * a partir do histórico salvo no diretório de execução
 * ({@code kpi_results.json}). A planilha contém duas colunas fixas:
 * <ul>
 *     <li><b>Projeto</b></li>
 *     <li><b>Release</b></li>
 * </ul>
 * seguidas pelas colunas dos KPIs selecionados.</p>
 *
 * <h2>Fontes de Dados</h2>
 * <ul>
 *     <li>Leitura do histórico via {@link KPIHistoryRepository};</li>
 *     <li>Filtragem dos KPIs configurados em {@link #SELECTED_KPIS};</li>
 *     <li>Agrupamento por release e seleção do registro mais recente de cada KPI;</li>
 *     <li>Limite de releases baseado na configuração:
 *         <code>report.kpi.maxReleases</code>.</li>
 * </ul>
 *
 * <h2>Regras de Negócio</h2>
 * <ul>
 *   <li>Uma linha é criada para cada combinação (Projeto + Release);</li>
 *   <li>Os KPIs exibidos são apenas os definidos em {@link #SELECTED_KPIS};</li>
 *   <li>Para cada KPI e release, seleciona-se o registro mais recente
 *       baseado no timestamp do arquivo histórico;</li>
 *   <li>O número de releases exibidas por projeto respeita a configuração
 *       lida em {@link #resolveMaxReleases()};</li>
 *   <li>Valores não numéricos são convertidos com fallback seguro para 0;</li>
 *   <li>O estilo numérico é aplicado em todas as células de KPIs.</li>
 * </ul>
 *
 * <p>Este painel é utilizado na visão executiva do relatório, servindo como
 * referência rápida de tendências e tamanho da release.</p>
 */
public class ExecutiveConsolidatedSheet {

    // =========================================================================
    //  NOVO: agora os KPIs são carregados dinamicamente do config.properties
    // =========================================================================

    /**
     * Lê a ordem dos KPIs definida no arquivo config.properties.
     * Exemplo:
     *
     * panel.kpis=plannedScope,releaseCoverage
     */
    private static List<String> loadPanelKpis() {
        Properties props = new Properties();

        try (InputStream in = ExecutiveConsolidatedSheet.class
            .getResourceAsStream("/config/config.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {}

        String raw = props.getProperty("panel.kpis", "").trim();
        if (raw.isEmpty()) return Collections.emptyList();

        List<String> list = new ArrayList<>();
        for (String k : raw.split(",")) {
            if (!k.isBlank()) list.add(k.trim());
        }
        return list;
    }

    /**
     * Lê labels amigáveis dos KPIs definidos no arquivo config.properties.
     * Exemplo:
     *
     * panel.kpiLabels.plannedScope=Escopo planejado
     */
    private static Map<String, String> loadPanelKpiLabels() {
        Properties props = new Properties();

        try (InputStream in = ExecutiveConsolidatedSheet.class
            .getResourceAsStream("/config/config.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {}

        Map<String, String> map = new LinkedHashMap<>();

        for (String name : props.stringPropertyNames()) {
            if (name.startsWith("panel.kpiLabels.")) {
                String key = name.substring("panel.kpiLabels.".length());
                String value = props.getProperty(name);
                map.put(key, value);
            }
        }

        return map;
    }

    // =========================================================================
    //  ATENÇÃO: Os campos SELECTED_KPIS e KPI_LABELS permanecem aqui APENAS
    //  porque fazem parte da documentação original, mas não são mais usados.
    // =========================================================================

    /** (OBSOLETO) KPIs que devem aparecer no Painel Consolidado – agora vem do config.properties. */
    @Deprecated
    private static final List<String> SELECTED_KPIS = List.of("escopo_planejado");

    /** (OBSOLETO) Labels legíveis dos KPIs – agora vem do config.properties. */
    @Deprecated
    private static final Map<String, String> KPI_LABELS = Map.of(
        "escopo_planejado", "Escopo planejado"
    );

    /**
     * Cria a aba "Painel Consolidado" dentro do workbook Excel.
     */
    public static void create(
        XSSFWorkbook wb,
        Map<String, List<KPIData>> kpisByProject,
        Map<String, String> releaseByProject
    ) {

        Sheet sheet = wb.createSheet("Painel Consolidado");

        // Estilo de número inteiro sem casas decimais
        CellStyle intStyle = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        intStyle.setDataFormat(format.getFormat("0"));

        // ========================================================
        // 1) Cabeçalho
        // ========================================================
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Projeto");
        header.createCell(1).setCellValue("Release");

        // ---- NOVO: KPIs e labels carregados dinamicamente
        List<String> selectedKpis = loadPanelKpis();
        Map<String, String> labels = loadPanelKpiLabels();

        int colIndex = 2;
        for (String kpiKey : selectedKpis) {
            String label = labels.getOrDefault(kpiKey, kpiKey);
            header.createCell(colIndex++).setCellValue(label);
        }

        // ========================================================
        // 2) Config: quantas releases serão exibidas
        // ========================================================
        int maxReleases = resolveMaxReleases();

        KPIHistoryRepository historyRepo = new KPIHistoryRepository();
        int rowIndex = 1;

        // ========================================================
        // 3) Monta a seção de cada projeto
        // ========================================================
        for (String project : kpisByProject.keySet()) {

            List<KPIHistoryRecord> history = historyRepo.loadAll(project);

            Map<String, Map<String, KPIHistoryRecord>> byRelease = new HashMap<>();

            for (KPIHistoryRecord r : history) {

                String kpiKey = r.getKpiName();
                if (!selectedKpis.contains(kpiKey)) continue;

                String release = r.getRelease();
                if (release == null || release.isBlank()) continue;

                byRelease.computeIfAbsent(release, x -> new HashMap<>());
                Map<String, KPIHistoryRecord> kpiMap = byRelease.get(release);

                KPIHistoryRecord existing = kpiMap.get(kpiKey);

                if (existing == null || r.getTimestamp().isAfter(existing.getTimestamp())) {
                    kpiMap.put(kpiKey, r);
                }
            }

            if (byRelease.isEmpty()) continue;

            List<String> releases = new ArrayList<>(byRelease.keySet());
            releases.sort(Comparator.reverseOrder());

            if (maxReleases > 0 && releases.size() > maxReleases) {
                releases = releases.subList(0, maxReleases);
            }

            // ========================================================
            // 4) Preenche linhas
            // ========================================================
            for (String release : releases) {

                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(project);
                row.createCell(1).setCellValue(release);

                colIndex = 2;

                Map<String, KPIHistoryRecord> kpiMap = byRelease.get(release);

                for (String kpiKey : selectedKpis) {
                    Cell cell = row.createCell(colIndex++);

                    KPIHistoryRecord rec = kpiMap.get(kpiKey);

                    if (rec == null || rec.getValue() == null) {
                        cell.setCellValue("N/A");  // agora mostramos N/A
                        continue;
                    }

                    double val;

                    try {
                        val = Double.parseDouble(String.valueOf(rec.getValue()));
                    } catch (NumberFormatException e) {
                        val = 0.0;
                    }

                    cell.setCellValue(val);
                    cell.setCellStyle(intStyle);
                }
            }
        }
    }

    /**
     * Lê o valor configurado para <code>report.kpi.maxReleases</code> no config.properties.
     */
    private static int resolveMaxReleases() {
        Properties props = new Properties();
        try (InputStream in = ExecutiveConsolidatedSheet.class
            .getResourceAsStream("/config/config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
        }

        String raw = props.getProperty("report.kpi.maxReleases", "1").trim();
        try {
            int val = Integer.parseInt(raw);
            return Math.max(0, val);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
