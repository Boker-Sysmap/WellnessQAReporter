package com.sysmap.wellness.history;

import com.sysmap.wellness.utils.LoggerUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Gerenciador PREMIUM da estrutura de histórico do WellnessQAReporter.
 *
 * Melhorias:
 *  - Compatível com Java 8+
 *  - Métodos pequenos e claros
 *  - Normalização padronizada de nomes
 *  - Criação segura de diretórios
 *  - Verificação de permissões
 *  - Logs padronizados
 */
public class HistoryDirectoryManager {

    private static final String BASE_DIR = "historico";

    // Diretórios internos usados pelo Reporter para KPIs e histórico
    private static final List<String> SUBDIRS = Arrays.asList(
            "releases",
            "mensal",
            "produtividade",
            "estabilidade",
            "curva_execucao",
            "defeitos",
            "snapshots"
    );

    private final Properties props;

    public HistoryDirectoryManager(Properties props) {
        this.props = props;
    }

    /**
     * Cria toda a estrutura de histórico necessária.
     */
    public void initializeHistoryStructure() {
        LoggerUtils.step("📚 Preparando estrutura de histórico...");

        // pasta raiz
        Path base = Paths.get(BASE_DIR);
        createDirectory(base);
        validateWritePermissions(base);

        // cria pastas por projeto
        List<String> projects = loadProjects();
        for (String proj : projects) {
            createProjectStructure(proj);
        }

        // pasta de meta-informações
        createDirectory(Paths.get(BASE_DIR, "meta"));

        LoggerUtils.success("📁 Estrutura de histórico criada com sucesso.");
    }

    // -------------------------------------------------------------
    //  PROJETOS
    // -------------------------------------------------------------
    private List<String> loadProjects() {
        String raw = props.getProperty("projects", "").trim();

        if (raw.isEmpty()) {
            throw new IllegalStateException("Nenhum projeto encontrado nas propriedades (chave: 'projects').");
        }

        // Compatível com Java 8 (sem Stream.toList)
        List<String> list = new ArrayList<>();
        for (String part : raw.split(",")) {
            String val = part.trim();
            if (!val.isEmpty()) list.add(val);
        }

        LoggerUtils.step("📌 Projetos detectados: " + String.join(", ", list));
        return list;
    }

    private String normalizeProjectName(String s) {
        return s.toLowerCase()
                .replace(" ", "_")
                .replaceAll("[^a-z0-9_]", "");
    }

    // -------------------------------------------------------------
    //  CRIAÇÃO DE ESTRUTURA POR PROJETO
    // -------------------------------------------------------------
    private void createProjectStructure(String project) {
        String normalized = normalizeProjectName(project);

        for (String sub : SUBDIRS) {
            Path p = Paths.get(BASE_DIR, sub, normalized);
            createDirectory(p);
        }
    }

    // -------------------------------------------------------------
    //  UTILITÁRIOS DE DIRETÓRIO
    // -------------------------------------------------------------
    private void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
            LoggerUtils.info("📂 Diretório OK: " + path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Falha ao criar diretório: " + path, e);
        }
    }

    private void validateWritePermissions(Path path) {
        if (!Files.isWritable(path)) {
            throw new IllegalStateException("Sem permissão de escrita no diretório: " + path.toAbsolutePath());
        }
    }
}
