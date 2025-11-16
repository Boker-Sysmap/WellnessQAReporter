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

    /** KPIs que devem aparecer no Painel Consolidado. */
    private static final List<String> SELECTED_KPIS = List.of("escopo_planejado");

    /** Labels legíveis para os KPIs exibidos no painel. */
    private static final Map<String, String> KPI_LABELS = Map.of(
        "escopo_planejado", "Escopo planejado"
    );

    /**
     * Cria a aba "Painel Consolidado" dentro do workbook Excel.
     *
     * <p>Fluxo de execução:</p>
     * <ol>
     *     <li>Cria a planilha e define o cabeçalho;</li>
     *     <li>Lê o número máximo de releases permitido pela configuração;</li>
     *     <li>Para cada projeto, carrega todo o histórico de KPIs;</li>
     *     <li>Agrupa registros por release e seleciona os mais recentes;</li>
     *     <li>Escreve as linhas do painel com Projeto | Release | KPIs;</li>
     *     <li>Aplica formatação numérica básica.</li>
     * </ol>
     *
     * @param wb                Workbook principal onde a aba será criada.
     * @param kpisByProject     KPIs calculados por projeto (não utilizados diretamente aqui,
     *                          mas mantidos por compatibilidade futura).
     * @param releaseByProject  Mapa auxiliar (não mais utilizado como fonte principal).
     */
    public static void create(
        XSSFWorkbook wb,
        Map<String, List<KPIData>> kpisByProject,
        Map<String, String> releaseByProject // hoje não usamos mais, mantido por compat.
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

        int colIndex = 2;
        for (String kpiKey : SELECTED_KPIS) {
            String label = KPI_LABELS.getOrDefault(kpiKey, kpiKey);
            header.createCell(colIndex++).setCellValue(label);
        }

        // ========================================================
        // 2) Config: quantas releases serão exibidas no painel
        // ========================================================
        int maxReleases = resolveMaxReleases();

        KPIHistoryRepository historyRepo = new KPIHistoryRepository();
        int rowIndex = 1;

        // ========================================================
        // 3) Monta a seção de cada projeto
        // ========================================================
        for (String project : kpisByProject.keySet()) {

            // Carrega todo o histórico salvo do projeto
            List<KPIHistoryRecord> history = historyRepo.loadAll(project);

            // release → (kpiKey → registro mais recente)
            Map<String, Map<String, KPIHistoryRecord>> byRelease = new HashMap<>();

            for (KPIHistoryRecord r : history) {

                String kpiKey = r.getKpiName();
                if (!SELECTED_KPIS.contains(kpiKey)) continue;

                String release = r.getRelease();
                if (release == null || release.isBlank()) continue;

                byRelease.computeIfAbsent(release, x -> new HashMap<>());
                Map<String, KPIHistoryRecord> kpiMap = byRelease.get(release);

                KPIHistoryRecord existing = kpiMap.get(kpiKey);
                if (existing == null || r.getTimestamp().isAfter(existing.getTimestamp())) {
                    kpiMap.put(kpiKey, r);
                }
            }

            if (byRelease.isEmpty()) {
                // Nenhum histórico disponível (provável primeira execução)
                continue;
            }

            // Releases ordenadas da mais recente para a mais antiga
            List<String> releases = new ArrayList<>(byRelease.keySet());
            releases.sort(Comparator.reverseOrder());

            // Limita conforme configuração
            if (maxReleases > 0 && releases.size() > maxReleases) {
                releases = releases.subList(0, maxReleases);
            }

            // ========================================================
            // 4) Preenche linhas do painel
            // ========================================================
            for (String release : releases) {

                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(project);
                row.createCell(1).setCellValue(release);

                colIndex = 2;

                Map<String, KPIHistoryRecord> kpiMap = byRelease.get(release);

                for (String kpiKey : SELECTED_KPIS) {
                    Cell cell = row.createCell(colIndex++);

                    KPIHistoryRecord rec = kpiMap.get(kpiKey);
                    if (rec == null || rec.getValue() == null) {
                        cell.setBlank();
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
     * Lê o valor configurado para <code>report.kpi.maxReleases</code> no arquivo
     * <code>config.properties</code>.
     *
     * <p>Regras:</p>
     * <ul>
     *     <li><b>1</b> (default) → exibe apenas a release atual;</li>
     *     <li><b>0</b> → exibe todas as releases disponíveis;</li>
     *     <li><b>N</b> → exibe apenas as N releases mais recentes;</li>
     *     <li>Valores inválidos → retorna 1.</li>
     * </ul>
     *
     * @return Quantidade máxima de releases por projeto.
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
