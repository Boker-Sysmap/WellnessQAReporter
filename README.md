# 🧩 **WellnessQAReporter**

**WellnessQAReporter** é uma plataforma Java completa para **coleta inteligente de dados do Qase.io**, **consolidação RUN-BASED** e **geração de relatórios executivos em Excel** com KPIs, histórico multi-release e dashboards analíticos.

O foco é entregar análises profissionais, métricas de qualidade e visão completa do ciclo de testes — tudo de forma automatizada.

---

# 🚀 **Principais Recursos**

### 🔗 **Integração Avançada com Qase.io**

* Suporte a paginação, timeout, retry exponencial e controle de duplicidade.
* Coleta completa de:

  * **Cases**
  * **Suites**
  * **Runs**
  * **Run Results**
  * **Results via Hash** (referenciados dentro de Defects)
  * **Defects**
  * **Users**, **Milestones**, **Plans**, **Config**, **Environment** (configuráveis)

### 🧠 **Consolidação RUN-BASED Inteligente**

O `DataConsolidator` reconstrói a relação completa entre:

```
defect → result.hash → run_results → case → suite
```

Permitindo identificar **funcionalidade real** afetada por cada defeito.

### 📊 **Geração de Relatórios Excel**

* Resumo Funcional por Projeto
* KPIs por release
* Painel Consolidado multi-release
* Dashboards de defeitos (tendências, distribuições, gráficos)
* Resumo Sintético de Defeitos
* Execução formatada com estilos globais via `ReportStyleManager`

### ⏱ **Métricas e Telemetria**

* Estatísticas de tempo (min/max/avg) por operação
* Contadores automáticos
* Exportação de métricas em JSON

### 🕓 **Cálculo de Tempo Útil de Resolução**

Utilizando:

* Dias úteis configuráveis (workdays)
* Horário comercial (manhã + tarde)
* Feriados automáticos via `holidays.json`
* Ajustes inteligentes de horários (WorkSchedule + BusinessTimeCalculator)

### 📂 **Histórico Multi-Release**

Grava KPIs e releases em:

```
historico/kpis/
```

Com suporte a:

* KPIEngine multi-release
* Histórico por projeto
* KPIs agrupados por release (`withGroup()`)

---

# 🏗️ **Estrutura do Projeto**

```
src/
 └── main/
     ├── java/com/sysmap/wellness/
     │   ├── main/                 # Entry point
     │   ├── api/                  # QaseClient e integrações
     │   ├── service/              # DataConsolidator, KPIEngine, KPIService
     │   ├── report/               # ReportGenerator + planilhas
     │   ├── history/              # Histórico de releases e KPIs
     │   ├── utils/                # LoggerUtils, MetricsCollector, FileUtils
     │   └── utils/datetime/       # WorkSchedule, BusinessTimeCalculator
     └── resources/
         ├── config.properties     # Configuração principal
         ├── endpoints.properties  # Endpoints Qase ativos
         ├── holidays.json         # Feriados nacionais/regionais
         └── templates/            # Arquivos auxiliares
```

Relatórios são gerados em:

```
output/reports/
```

JSONs coletados ficam em:

```
output/json/
```

---

# ⚙️ **Configuração**

O sistema usa `ConfigManager`, que lê automaticamente:

* `config.properties`
* `endpoints.properties` (opcional)
* `holidays.json`

### 📌 Exemplo resumido de `config.properties`

```properties
# API Qase
qase.api.token=SEU_TOKEN_AQUI
qase.api.baseUrl=https://api.qase.io/v1

# Projetos Qase (CSV)
qase.projects=FULLY,CHUBB

# Dias úteis
workdays=1,2,3,4,5

# Períodos de trabalho
morning.start=09:00
morning.end=11:59
afternoon.start=13:00
afternoon.end=18:00

# Releases exibidas no Painel Consolidado
report.kpi.maxReleases=2
```

---

# 🧠 **Principais Classes e Responsabilidades**

| Classe                                    | Descrição                                                                                    |
| ----------------------------------------- | -------------------------------------------------------------------------------------------- |
| **QaseClient**                            | Coleta robusta da API Qase com paginação, retry, timeout, busca por hash e result por run_id |
| **DataConsolidator**                      | Reconstrói e unifica todos os dados do projeto (RUN-BASED)                                   |
| **KPIEngine / KPIService**                | Processa KPIs multi-release, produzindo datasets históricos                                  |
| **ReportGenerator**                       | Gera relatório Excel com todas as abas                                                       |
| **FunctionalSummarySheet**                | Resumo funcional (casos, execução, falhas, bugs)                                             |
| **ExecutiveKPISheet**                     | KPIs da release atual                                                                        |
| **ExecutiveConsolidatedSheet**            | Painel consolidado multi-release usando histórico                                            |
| **DefectsDashboardSheet**                 | Dashboard completo com gráficos e tendências                                                 |
| **DefectsSyntheticSheet**                 | Visão sintética tabular dos defeitos                                                         |
| **WorkSchedule + BusinessTimeCalculator** | Cálculo avançado de tempo útil de resolução                                                  |
| **LoggerUtils**                           | Logs enriquecidos com timers, seções e cores                                                 |
| **MetricsCollector**                      | Telemetria, estatísticas de tempo e exportação JSON                                          |

---

# 🏃‍♂️ Execução

### 💻 Via Maven + Java

1️⃣ Compile:

```bash
mvn clean package
```

2️⃣ Execute:

```bash
java -jar target/WellnessQAReporter.jar
```

3️⃣ O relatório aparecerá em:

```
output/reports/WellnessQAReport_<data>.xlsx
```

---

# 📘 Gerando JavaDoc

```bash
mvn javadoc:javadoc
```

Saída em:

```
target/site/apidocs/index.html
```

---

# 🔍 Exemplo de Saída do Excel

Inclui abas como:

* **Resumo Funcional**
* **Painel Consolidado**
* **KPI da Release Atual**
* **Defeitos — Dashboard Executivo**
* **Defeitos — Resumo Sintético**
* **Apoio e tabelas auxiliares**
* **KPIs históricos (multi-release)**

---

# 🛠️ Desenvolvimento

### Requisitos

* Java **11+**
* Maven **3.8+**
* Git

### Commits

```bash
git add .
git commit -m "Implementa novo KPI multi-release"
git push origin main
```

---

# 🛡️ Licença

Projeto de uso interno — propriedade Sysmap Solutions.
Distribuição externa não autorizada.

---

# 👨‍💻 Autor

**Roberto Boker**
QA Automation & Reporting – Sysmap Solutions (2025)

---

