package com.sysmap.wellness.utils.datetime;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.*;
import java.util.*;

/**
 * Define a jornada de trabalho e períodos úteis da organização.
 *
 * Lê horários e dias úteis do arquivo config.properties (resources)
 * e feriados do arquivo holidays.json (resources).
 *
 * Inclui métodos auxiliares para ajustar horários de início e fim de cálculo
 * conforme a jornada, e ignorar feriados e finais de semana.
 */
public class WorkSchedule {

    private final List<WorkingPeriod> periods = new ArrayList<>();
    private final Set<DayOfWeek> workingDays = EnumSet.noneOf(DayOfWeek.class);
    private final List<Holiday> holidays;

    public WorkSchedule() {
        Properties props = new Properties();

        // 🔹 Tenta carregar o arquivo config.properties
        try (FileInputStream fis = new FileInputStream("src/main/resources/config.properties")) {
            props.load(fis);
            System.out.println("[WorkSchedule] Configurações carregadas de config.properties");
        } catch (IOException e) {
            System.out.println("[WorkSchedule] Aviso: config.properties não encontrado. Usando valores padrão.");
        }

        // 🔹 Dias úteis (1=Segunda, 7=Domingo)
        String[] days = props.getProperty("workdays", "1,2,3,4,5").split(",");
        for (String d : days) {
            try {
                workingDays.add(DayOfWeek.of(Integer.parseInt(d.trim())));
            } catch (Exception ignored) {
                System.out.println("[WorkSchedule] Dia inválido ignorado: " + d);
            }
        }

        // 🔹 Períodos de trabalho (manhã e tarde)
        LocalTime morningStart = LocalTime.parse(props.getProperty("morning.start", "09:00"));
        LocalTime morningEnd = LocalTime.parse(props.getProperty("morning.end", "11:59"));
        LocalTime afternoonStart = LocalTime.parse(props.getProperty("afternoon.start", "13:00"));
        LocalTime afternoonEnd = LocalTime.parse(props.getProperty("afternoon.end", "18:00"));

        periods.add(new WorkingPeriod(morningStart, morningEnd));
        periods.add(new WorkingPeriod(afternoonStart, afternoonEnd));

        // 🔹 Feriados (carregados do holidays.json via HolidayLoader)
        holidays = HolidayLoader.loadHolidays();
        System.out.println("[WorkSchedule] Feriados carregados: " + holidays.size());
    }

    /**
     * Retorna true se a data for um dia útil (não feriado e dentro dos dias configurados).
     */
    public boolean isWorkingDay(LocalDate date) {
        boolean weekday = workingDays.contains(date.getDayOfWeek());
        boolean holiday = holidays.stream().anyMatch(h -> LocalDate.parse(h.getDate()).equals(date));
        return weekday && !holiday;
    }

    /**
     * Retorna os períodos úteis configurados (manhã e tarde).
     */
    public List<WorkingPeriod> getPeriods() {
        return periods;
    }

    /**
     * Retorna o próximo dia útil após a data informada.
     */
    public LocalDate getNextWorkingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isWorkingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * Retorna o último dia útil anterior à data informada.
     */
    public LocalDate getPreviousWorkingDay(LocalDate date) {
        LocalDate prev = date.minusDays(1);
        while (!isWorkingDay(prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }

    /**
     * Ajusta o horário inicial para o próximo momento válido de expediente.
     * - Se criado antes do expediente → ajusta para o início da jornada.
     * - Se criado durante o almoço → pula para o início do turno da tarde.
     * - Se criado após o expediente → passa para o próximo dia útil.
     * - Se criado em fim de semana ou feriado → passa para o próximo dia útil.
     */
    public LocalDateTime adjustStartDateTime(LocalDateTime start) {
        LocalDate date = start.toLocalDate();
        LocalTime time = start.toLocalTime();

        // Caso o dia não seja útil, pula para o próximo dia útil
        while (!isWorkingDay(date)) {
            date = getNextWorkingDay(date);
            time = periods.get(0).getStart();
        }

        // Antes do expediente → início do expediente
        WorkingPeriod firstPeriod = periods.get(0);
        if (time.isBefore(firstPeriod.getStart())) {
            return LocalDateTime.of(date, firstPeriod.getStart());
        }

        // Durante o almoço → pula para o início do turno da tarde
        if (periods.size() > 1) {
            WorkingPeriod morning = periods.get(0);
            WorkingPeriod afternoon = periods.get(1);
            if (time.isAfter(morning.getEnd()) && time.isBefore(afternoon.getStart())) {
                return LocalDateTime.of(date, afternoon.getStart());
            }
        }

        // Após o expediente → próximo dia útil
        WorkingPeriod lastPeriod = periods.get(periods.size() - 1);
        if (time.isAfter(lastPeriod.getEnd())) {
            LocalDate nextDay = getNextWorkingDay(date);
            return LocalDateTime.of(nextDay, periods.get(0).getStart());
        }

        // Dentro do horário → mantém
        return LocalDateTime.of(date, time);
    }

    /**
     * Ajusta o horário final para o último momento válido de expediente.
     * - Se resolvido antes do expediente → retrocede para o fim do expediente anterior.
     * - Se resolvido durante o almoço → considera o fim do período da manhã.
     * - Se resolvido após o expediente → ajusta para o fim do expediente atual.
     * - Se resolvido em fim de semana ou feriado → retrocede para o último dia útil anterior.
     */
    public LocalDateTime adjustEndDateTime(LocalDateTime end) {
        LocalDate date = end.toLocalDate();
        LocalTime time = end.toLocalTime();

        // Caso o dia não seja útil → volta até o último útil
        while (!isWorkingDay(date)) {
            date = getPreviousWorkingDay(date);
        }

        // Antes do expediente → fim do expediente do dia anterior
        WorkingPeriod firstPeriod = periods.get(0);
        if (time.isBefore(firstPeriod.getStart())) {
            LocalDate previousDay = getPreviousWorkingDay(date);
            WorkingPeriod lastPeriodPrev = periods.get(periods.size() - 1);
            return LocalDateTime.of(previousDay, lastPeriodPrev.getEnd());
        }

        // Durante o almoço → considera o fim da manhã
        if (periods.size() > 1) {
            WorkingPeriod morning = periods.get(0);
            WorkingPeriod afternoon = periods.get(1);
            if (time.isAfter(morning.getEnd()) && time.isBefore(afternoon.getStart())) {
                return LocalDateTime.of(date, morning.getEnd());
            }
        }

        // Após o expediente → ajusta para o fim do expediente do dia
        WorkingPeriod lastPeriod = periods.get(periods.size() - 1);
        if (time.isAfter(lastPeriod.getEnd())) {
            return LocalDateTime.of(date, lastPeriod.getEnd());
        }

        // Dentro do horário → mantém
        return LocalDateTime.of(date, time);
    }
}
