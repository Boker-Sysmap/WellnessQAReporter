package com.sysmap.wellness.utils.datetime;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.*;
import java.util.*;

/**
 * Representa a configuração oficial de jornada de trabalho da organização,
 * incluindo:
 *
 * <ul>
 *     <li>Dias úteis da semana (ex.: segunda a sexta);</li>
 *     <li>Períodos de trabalho (ex.: turno da manhã e da tarde);</li>
 *     <li>Feriados nacionais/regionais carregados de {@code holidays.json};</li>
 *     <li>Regras de ajuste para horários de início e fim de cálculo de SLA;</li>
 * </ul>
 *
 * <p>
 * Esta classe é usada diretamente pelo {@link BusinessTimeCalculator} para
 * determinar intervalos válidos de trabalho e aplicar corretamente exceções
 * como feriados, finais de semana e horários fora do expediente.
 * </p>
 *
 * <h2>Origem dos dados:</h2>
 * <ul>
 *     <li><b>config.properties:</b> define workdays, morning.start, afternoon.end, etc.</li>
 *     <li><b>holidays.json:</b> lista de feriados carregada por {@link HolidayLoader}.</li>
 * </ul>
 *
 * <h2>Comportamento governado por regras</h2>
 * O WorkSchedule garante que:
 * <ul>
 *     <li>Qualquer hora fora do expediente é ajustada para o próximo horário útil;</li>
 *     <li>Fins de semana e feriados são ignorados automaticamente;</li>
 *     <li>Horários dentro de intervalos de almoço são realocados corretamente;</li>
 *     <li>O cálculo nunca considera minutos não úteis.</li>
 * </ul>
 */
public class WorkSchedule {

    /** Períodos de trabalho configurados (ex.: manhã, tarde). */
    private final List<WorkingPeriod> periods = new ArrayList<>();

    /** Conjunto de dias da semana considerados úteis (ex.: MON–FRI). */
    private final Set<DayOfWeek> workingDays = EnumSet.noneOf(DayOfWeek.class);

    /** Lista completa de feriados carregados de holidays.json. */
    private final List<Holiday> holidays;

    /**
     * Constrói o WorkSchedule carregando:
     *
     * <ul>
     *     <li>dias úteis e horários do arquivo {@code config.properties};</li>
     *     <li>feriados via {@code HolidayLoader};</li>
     * </ul>
     *
     * Em caso de ausência do arquivo de configuração, valores padrão seguros são usados.
     */
    public WorkSchedule() {
        Properties props = new Properties();

        // ---- Carregamento de config.properties ----
        try (FileInputStream fis = new FileInputStream("src/main/resources/config.properties")) {
            props.load(fis);
            System.out.println("[WorkSchedule] Configurações carregadas de config.properties");
        } catch (IOException e) {
            System.out.println("[WorkSchedule] Aviso: config.properties não encontrado. Usando valores padrão.");
        }

        // ---------------- Dias úteis ----------------
        String[] days = props.getProperty("workdays", "1,2,3,4,5").split(",");
        for (String d : days) {
            try {
                workingDays.add(DayOfWeek.of(Integer.parseInt(d.trim())));
            } catch (Exception ignored) {
                System.out.println("[WorkSchedule] Dia inválido ignorado: " + d);
            }
        }

        // --------------- Períodos úteis --------------
        LocalTime morningStart = LocalTime.parse(props.getProperty("morning.start", "09:00"));
        LocalTime morningEnd   = LocalTime.parse(props.getProperty("morning.end", "11:59"));
        LocalTime afternoonStart = LocalTime.parse(props.getProperty("afternoon.start", "13:00"));
        LocalTime afternoonEnd   = LocalTime.parse(props.getProperty("afternoon.end", "18:00"));

        periods.add(new WorkingPeriod(morningStart, morningEnd));
        periods.add(new WorkingPeriod(afternoonStart, afternoonEnd));

        // ------------------ Feriados -----------------
        holidays = HolidayLoader.loadHolidays();
        System.out.println("[WorkSchedule] Feriados carregados: " + holidays.size());
    }

    /**
     * Verifica se a data informada é um dia útil.
     *
     * <p>Um dia é considerado útil se:</p>
     * <ul>
     *     <li>pertencer ao conjunto configurado de dias úteis;</li>
     *     <li>não for feriado;</li>
     * </ul>
     *
     * @param date data a ser verificada
     * @return {@code true} se a data for útil, caso contrário {@code false}
     */
    public boolean isWorkingDay(LocalDate date) {
        boolean weekday = workingDays.contains(date.getDayOfWeek());
        boolean holiday = holidays.stream()
            .anyMatch(h -> LocalDate.parse(h.getDate()).equals(date));

        return weekday && !holiday;
    }

    /**
     * Retorna a lista completa dos períodos de trabalho configurados.
     *
     * @return lista imutável contendo os períodos ativos
     */
    public List<WorkingPeriod> getPeriods() {
        return periods;
    }

    /**
     * Calcula o próximo dia útil após a data informada.
     *
     * @param date data base
     * @return primeiro {@link LocalDate} considerado útil após a data
     */
    public LocalDate getNextWorkingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isWorkingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * Retorna o dia útil imediatamente anterior à data informada.
     *
     * @param date data base
     * @return último dia útil conhecido antes da data
     */
    public LocalDate getPreviousWorkingDay(LocalDate date) {
        LocalDate prev = date.minusDays(1);
        while (!isWorkingDay(prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }

    /**
     * Ajusta a data/hora inicial para o próximo momento válido de expediente.
     *
     * <p>Regras aplicadas:</p>
     * <ul>
     *     <li>Se cair em fim de semana/feriado → avança para o próximo dia útil;</li>
     *     <li>Se antes do expediente → ajusta para o início da manhã;</li>
     *     <li>Se durante o almoço → ajusta para o início do turno da tarde;</li>
     *     <li>Se após o expediente → avança para o próximo dia útil;</li>
     * </ul>
     *
     * @param start data/hora inicial
     * @return data/hora ajustada para início do expediente útil
     */
    public LocalDateTime adjustStartDateTime(LocalDateTime start) {
        LocalDate date = start.toLocalDate();
        LocalTime time = start.toLocalTime();

        // Feriados e fins de semana → avança para próximo útil
        while (!isWorkingDay(date)) {
            date = getNextWorkingDay(date);
            time = periods.get(0).getStart();
        }

        // Antes do expediente
        WorkingPeriod firstPeriod = periods.get(0);
        if (time.isBefore(firstPeriod.getStart())) {
            return LocalDateTime.of(date, firstPeriod.getStart());
        }

        // Durante almoço
        if (periods.size() > 1) {
            WorkingPeriod morning = periods.get(0);
            WorkingPeriod afternoon = periods.get(1);

            if (time.isAfter(morning.getEnd()) && time.isBefore(afternoon.getStart())) {
                return LocalDateTime.of(date, afternoon.getStart());
            }
        }

        // Após expediente
        WorkingPeriod lastPeriod = periods.get(periods.size() - 1);
        if (time.isAfter(lastPeriod.getEnd())) {
            LocalDate nextDay = getNextWorkingDay(date);
            return LocalDateTime.of(nextDay, periods.get(0).getStart());
        }

        // Caso esteja dentro do expediente
        return LocalDateTime.of(date, time);
    }

    /**
     * Ajusta a data/hora final para o último momento útil possível.
     *
     * <p>Regras aplicadas:</p>
     * <ul>
     *     <li>Se ocorrer em fim de semana/feriado → retrocede para último dia útil;</li>
     *     <li>Se antes do expediente → volta para o fim do expediente anterior;</li>
     *     <li>Se durante almoço → considera fim do turno da manhã;</li>
     *     <li>Se após expediente → fixa no fim do expediente atual;</li>
     * </ul>
     *
     * @param end data/hora final original
     * @return data/hora ajustada ao último horário trabalhado
     */
    public LocalDateTime adjustEndDateTime(LocalDateTime end) {
        LocalDate date = end.toLocalDate();
        LocalTime time = end.toLocalTime();

        // Feriado ou final de semana → retroceder
        while (!isWorkingDay(date)) {
            date = getPreviousWorkingDay(date);
        }

        WorkingPeriod first = periods.get(0);

        // Antes do expediente
        if (time.isBefore(first.getStart())) {
            LocalDate prev = getPreviousWorkingDay(date);
            WorkingPeriod lastPrev = periods.get(periods.size() - 1);
            return LocalDateTime.of(prev, lastPrev.getEnd());
        }

        // Durante almoço
        if (periods.size() > 1) {
            WorkingPeriod morning = periods.get(0);
            WorkingPeriod afternoon = periods.get(1);

            if (time.isAfter(morning.getEnd()) && time.isBefore(afternoon.getStart())) {
                return LocalDateTime.of(date, morning.getEnd());
            }
        }

        // Após expediente
        WorkingPeriod last = periods.get(periods.size() - 1);
        if (time.isAfter(last.getEnd())) {
            return LocalDateTime.of(date, last.getEnd());
        }

        // Dentro do expediente
        return LocalDateTime.of(date, time);
    }
}
