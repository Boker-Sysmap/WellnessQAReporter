package com.sysmap.wellness.utils.datetime;

import java.time.*;
import java.time.temporal.ChronoUnit;

/**
 * Calcula o tempo útil entre duas datas, considerando:
 * - Dias úteis configurados em WorkSchedule
 * - Feriados de holidays.json
 * - Horários de expediente
 * - Intervalo de almoço
 */
public class BusinessTimeCalculator {

    private final WorkSchedule schedule;

    public BusinessTimeCalculator(WorkSchedule schedule) {
        this.schedule = schedule;
    }

    /**
     * Calcula o tempo útil entre duas datas no formato HH:mm.
     * @param start Data/hora inicial
     * @param end Data/hora final
     * @return Tempo útil em HH:mm
     */
    public String calculateBusinessTime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return "00:00";
        }

        LocalDateTime current = schedule.adjustStartDateTime(start);
        LocalDateTime finish = schedule.adjustEndDateTime(end);
        long totalMinutes = 0;

        // percorre cronologicamente, somando apenas minutos úteis
        while (current.isBefore(finish)) {
            LocalDate currentDate = current.toLocalDate();

            // pula fins de semana e feriados
            if (!schedule.isWorkingDay(currentDate)) {
                current = LocalDateTime.of(
                        schedule.getNextWorkingDay(currentDate),
                        schedule.getPeriods().get(0).getStart());
                continue;
            }

            boolean moved = false;

            for (WorkingPeriod period : schedule.getPeriods()) {
                LocalDateTime periodStart = LocalDateTime.of(currentDate, period.getStart());
                LocalDateTime periodEnd = LocalDateTime.of(currentDate, period.getEnd());

                // se ainda não começou o expediente, avança para o início
                if (current.isBefore(periodStart)) {
                    current = periodStart;
                }

                // se já passou do expediente, pula para o próximo período
                if (current.isAfter(periodEnd)) continue;

                // calcula o fim efetivo dentro deste período
                LocalDateTime segmentEnd = finish.isBefore(periodEnd) ? finish : periodEnd;

                if (segmentEnd.isAfter(current)) {
                    totalMinutes += ChronoUnit.MINUTES.between(current, segmentEnd);
                    current = segmentEnd;
                    moved = true;
                }

                // se chegou no horário final, encerra
                if (!current.isBefore(finish)) break;
            }

            // se não se moveu dentro de nenhum período, avança para o próximo dia útil
            if (!moved) {
                current = LocalDateTime.of(
                        schedule.getNextWorkingDay(currentDate),
                        schedule.getPeriods().get(0).getStart());
            }
        }

        return formatTime(totalMinutes);
    }

    private String formatTime(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }
}
