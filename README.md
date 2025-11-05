# 🧩 WellnessQAReporter

**WellnessQAReporter** é uma ferramenta Java para **coleta automatizada e geração de relatórios consolidados** de projetos do [Qase.io](https://qase.io).  
Ela foi desenvolvida para facilitar a análise de resultados de testes, defeitos, métricas e estatísticas de qualidade.

---

## 🚀 Funcionalidades

- 🔗 Conecta-se à API Qase.io para coletar dados de:
  - Test Cases
  - Test Results
  - Test Runs
  - Defects (com enriquecimento de resultados via hash)
  - Suites e Milestones (opcional)
- 📊 Gera relatórios Excel (.xlsx) completos e formatados automaticamente
- ⏱️ Requisições otimizadas com **paginação, retry e controle de timeout**
- ⚙️ Consolidação de dados entre múltiplos endpoints
- 🧠 Busca inteligente de *results* por **run_id** e também por **hash** referenciado em *defects*

---

## 🏗️ Estrutura do Projeto

```
src/
 └── main/
     ├── java/com/sysmap/wellness/
     │   ├── main/                 # Classe principal (WellnessQAMain)
     │   ├── service/              # QaseClient, DataConsolidator e serviços auxiliares
     │   ├── report/               # ReportGenerator e planilhas (FunctionalSummarySheet, etc)
     │   └── util/                 # Utilitários (LoggerUtils, MetricsCollector)
     └── resources/                # Configurações e templates
```

Relatórios são salvos automaticamente em:
```
output/reports/
```

---

## ⚙️ Configuração

O projeto utiliza a classe `ConfigManager` para carregar as informações de configuração da API Qase.

Crie um arquivo `config.properties` dentro de `src/main/resources` com o seguinte conteúdo:

```properties
# Qase API Configuration
qase.api.token=INSIRA_AQUI_O_SEU_TOKEN
qase.api.baseUrl=https://api.qase.io/v1

# Projetos (CSV)
qase.projects=FULLY,CHUBB

# Fallback: endpoints em CSV (usado apenas se endpoints.properties não existir)
qase.endpoints=case,suite,result,defect,milestone
```

---

## 🧠 Principais Classes

| Classe | Responsabilidade |
|--------|------------------|
| `QaseClient` | Comunicação com a API Qase (suporte a paginação, retries, timeout e busca por hash/run_id) |
| `DataConsolidator` | Consolida e enriquece dados de todos os endpoints de um projeto |
| `ReportGenerator` | Gera o relatório final em Excel (.xlsx) |
| `FunctionalSummarySheet` | Cria a aba principal do relatório com métricas funcionais |
| `LoggerUtils` | Utilitário de logs formatados |
| `MetricsCollector` | Coleta métricas de execução |

---

## 🏃‍♂️ Execução

### 💻 Via IntelliJ IDEA ou terminal

1️⃣ Compile o projeto:
```bash
mvn clean package
```

2️⃣ Execute o programa:
```bash
java -jar target/WellnessQAReporter.jar
```

3️⃣ O relatório será gerado automaticamente em:
```
output/reports/WellnessQAReport_<data>.xlsx
```

---

## 🧩 Exemplo de Saída

A ferramenta gera um relatório com múltiplas abas no Excel, incluindo:
- **Resumo Funcional (FunctionalSummary)**
- **Tendência de Execução (ExecutionTrend)** *(opcional)*
- **Defeitos e Resultados Associados**

---

## 🛠️ Desenvolvimento e Versionamento

### Requisitos
- **Java 11+**
- **Maven 3.8+**
- Git (para controle de versão)

### Fluxo de Git
```bash
git pull origin main
# Faz alterações...
git add .
git commit -m "Implementa nova funcionalidade"
git push origin main
```

---

## 🧾 Licença

Este projeto é de uso interno e está sob a licença proprietária da Sysmap Solutions.  
Distribuição externa não autorizada é proibida.

---

## 👨‍💻 Autor

**Roberto Boker**  
Desenvolvimento de QA Automation & Reporting  
Sysmap Solutions — 2025
