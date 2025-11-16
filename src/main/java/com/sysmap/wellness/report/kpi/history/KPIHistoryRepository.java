package com.sysmap.wellness.report.kpi.history;

import com.sysmap.wellness.config.ConfigManager;
import com.sysmap.wellness.report.service.model.KPIData;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repositório responsável por persistir e recuperar o histórico de KPIs
 * calculados por projeto e release. Atua como camada de armazenamento de
 * baixo nível, manipulando exclusivamente o formato físico dos arquivos e
 * diretórios onde os KPIs históricos são gravados.
 *
 * <p>O layout dos arquivos segue a estrutura:</p>
 *
 * <pre>
 *   {history.kpi.baseDir}/{project}/{release}/kpi_results.json
 * </pre>
 *
 * <p>Cada arquivo {@code kpi_results.json} contém um array de objetos JSON,
 * onde cada objeto representa um registro histórico ({@link KPIHistoryRecord})
 * contendo:</p>
 *
 * <ul>
 *   <li>Nome do KPI (kpiName);</li>
 *   <li>Nome do projeto;</li>
 *   <li>Nome da release;</li>
 *   <li>Timestamp da coleta;</li>
 *   <li>Valor bruto do KPI;</li>
 *   <li>Detalhes adicionais (nome, formatação, tendência, etc.).</li>
 * </ul>
 *
 * <p>A classe foi projetada para:</p>
 * <ul>
 *   <li>Ser resiliente a arquivos inválidos (ignora silenciosamente);</li>
 *   <li>Permitir crescimento incremental das releases sem bloqueios;</li>
 *   <li>Integrar-se facilmente ao {@link com.sysmap.wellness.report.service.KPIHistoryService};</li>
 *   <li>Garantir isolamento das responsabilidades de persistência.</li>
 * </ul>
 */
public class KPIHistoryRepository {

    /**
     * Construtor padrão. Não possui dependências diretas, pois todas as
     * configurações são obtidas via {@link ConfigManager}.
     */
    public KPIHistoryRepository() {
        // Nenhuma dependência direta — tudo configurado pelo ConfigManager
    }

    /**
     * Resolve o diretório de histórico para um determinado projeto e release,
     * garantindo sua existência. O formato resultante é:
     *
     * <pre>
     *   {history.kpi.baseDir}/{project}/{release}/
     * </pre>
     *
     * @param project Nome do projeto.
     * @param release Nome da release.
     * @return Caminho do diretório correspondente.
     * @throws RuntimeException Se houver falha ao criar o diretório.
     */
    private Path getKPIDirectory(String project, String release) {

        String baseDir = ConfigManager.getKPIHistoryBaseDir();
        Path dir = Paths.get(baseDir, project, release);

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao criar diretório de histórico de KPIs: " + dir, e);
        }

        return dir;
    }

    /**
     * Persiste os KPIs calculados de um projeto e release em um arquivo
     * {@code kpi_results.json}. Cada KPI é convertido em um
     * {@link KPIHistoryRecord} que captura os valores relevantes e o timestamp
     * do momento da gravação.
     *
     * <p>Regras de gravação:</p>
     * <ul>
     *   <li>O arquivo é sobrescrito a cada chamada (TRUNCATE_EXISTING);</li>
     *   <li>O formato escrito é um array JSON com identação 2;</li>
     *   <li>Diretórios necessários são criados automaticamente.</li>
     * </ul>
     *
     * @param project     Nome do projeto.
     * @param releaseName Nome da release.
     * @param results     Lista de KPIs calculados.
     */
    public void save(String project, String releaseName, List<KPIData> results) {

        Path file = getKPIDirectory(project, releaseName).resolve("kpi_results.json");
        JSONArray arr = new JSONArray();

        for (KPIData data : results) {

            JSONObject jsonDetails = data.toJson();

            KPIHistoryRecord record = new KPIHistoryRecord(
                data.getKey(),
                project,
                releaseName,
                LocalDateTime.now(),
                data.getValue(),
                jsonDetails
            );

            arr.put(record.toJson());
        }

        try {
            Files.writeString(
                file,
                arr.toString(2),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            throw new RuntimeException("Falha ao gravar histórico de KPIs no arquivo: " + file, e);
        }
    }

    /**
     * Localiza todos os arquivos {@code kpi_results.json} pertencentes a um projeto.
     * A busca percorre todas as subpastas, permitindo múltiplas releases.
     *
     * @param project Nome do projeto.
     * @return Lista de caminhos para os arquivos encontrados.
     */
    private List<Path> listAllProjectFiles(String project) {

        String baseDir = ConfigManager.getKPIHistoryBaseDir();
        Path base = Paths.get(baseDir, project);

        if (!Files.exists(base)) {
            return Collections.emptyList();
        }

        try {
            List<Path> files = new ArrayList<>();

            Files.walk(base)
                .filter(f -> f.getFileName().toString().equals("kpi_results.json"))
                .forEach(files::add);

            return files;

        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Carrega todos os registros históricos de um projeto, agregando dados
     * provenientes de todas as releases existentes.
     *
     * <p>Regras:</p>
     * <ul>
     *   <li>Arquivos inválidos ou corrompidos são ignorados silenciosamente;</li>
     *   <li>Todos os registros válidos de todas as releases são retornados;</li>
     *   <li>A ordenação não é garantida aqui, mas pode ser aplicada no serviço.</li>
     * </ul>
     *
     * @param project Nome do projeto.
     * @return Lista de {@link KPIHistoryRecord}.
     */
    public List<KPIHistoryRecord> loadAll(String project) {

        List<Path> files = listAllProjectFiles(project);
        List<KPIHistoryRecord> result = new ArrayList<>();

        for (Path f : files) {
            try {
                String json = Files.readString(f);
                JSONArray arr = new JSONArray(json);

                for (int i = 0; i < arr.length(); i++) {
                    result.add(KPIHistoryRecord.fromJson(arr.getJSONObject(i)));
                }

            } catch (Exception ignored) {
                // arquivos inválidos são ignorados silenciosamente
            }
        }

        return result;
    }

    /**
     * Carrega todos os registros históricos de um KPI específico, ordenando-os
     * por timestamp (do mais antigo ao mais recente).
     *
     * @param project Nome do projeto.
     * @param kpiKey  Nome do KPI desejado.
     * @return Lista temporal ordenada de registros.
     */
    public List<KPIHistoryRecord> loadByKPI(String project, String kpiKey) {
        return loadAll(project).stream()
            .filter(r -> kpiKey.equals(r.getKpiName()))
            .sorted(Comparator.comparing(KPIHistoryRecord::getTimestamp))
            .collect(Collectors.toList());
    }

    /**
     * Recupera o registro histórico mais recente de um KPI específico.
     *
     * @param project Nome do projeto.
     * @param kpiKey  Identificador do KPI.
     * @return Optional contendo o último registro, se existir.
     */
    public Optional<KPIHistoryRecord> loadLast(String project, String kpiKey) {
        List<KPIHistoryRecord> list = loadByKPI(project, kpiKey);
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(list.size() - 1));
    }
}
