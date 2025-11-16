package com.sysmap.wellness.utils.datetime;

import java.time.*;
import java.time.temporal.ChronoUnit;

/**
 * <h1>BusinessTimeCalculator</h1>
 *
 * <p>
 * Classe responsável por calcular o <b>tempo útil</b> (tempo realmente trabalhado)
 * entre duas datas/horários, considerando:
 * </p>
 *
 * <ul>
 *   <li>Dias úteis configurados em {@link WorkSchedule};</li>
 *   <li>Períodos de expediente (ex.: manhã e tarde);</li>
 *   <li>Intervalo de almoço;</li>
 *   <li>Feriados definidos no arquivo {@code holidays.json};</li>
 *   <li>Ajustes automáticos de horários que caem fora do expediente;</li>
 *   <li>Avanço automático para o próximo dia útil quando necessário.</li>
 * </ul>
 *
 * <p>
 * O cálculo é realizado minuto a minuto dentro de períodos válidos,
 * retornando um tempo total no formato <b>HH:mm</b>.
 * </p>
 *
 * <h2>Exemplo de uso:</h2>
 * <pre>
 * WorkSchedule schedule = new WorkSchedule();
 * BusinessTimeCalculator btc = new BusinessTimeCalculator(schedule);
 *
 * String t = btc.calculateBusinessTime(
 *     LocalDateTime.of(2025, 1, 10, 10, 30),
 *     LocalDateTime.of(2025, 1, 12, 16, 00)
 * );
 * // Saída: "10:30" (depende dos períodos configurados)
 * </pre>
 */
public class BusinessTimeCalculator {

    /** Configuração completa de expediente, períodos e feriados. */
    private final WorkSchedule schedule;

    /**
     * Constrói um novo calculador de tempo útil baseado em um {@link WorkSchedule}.
     *
     * @param schedule Configuração contendo períodos, dias úteis e feriados.
     */
    public BusinessTimeCalculator(WorkSchedule schedule) {
        this.schedule = schedule;
    }

    /**
     * <h2>Calcula o tempo útil entre duas datas.</h2>
     *
     * <p>
     * O cálculo respeita:
     * </p>
     * <ul>
     *   <li>Feriados (não contam tempo);</li>
     *   <li>Fins de semana (não contam tempo);</li>
     *   <li>Períodos definidos em {@link WorkSchedule#getPeriods()};</li>
     *   <li>Ajuste automático do horário inicial/final caso estejam fora do expediente.</li>
     * </ul>
     *
     * <p>
     * Se a data final é anterior à inicial, retorna <code>"00:00"</code>.
     * </p>
     *
     * @param start Data/hora inicial
     * @param end   Data/hora final
     * @return Tempo útil total no formato HH:mm
     */
    public String calculateBusinessTime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return "00:00";
        }

        // Ajusta horários fora do expediente para os limites válidos
        LocalDateTime current = schedule.adjustStartDateTime(start);
        LocalDateTime finish  = schedule.adjustEndDateTime(end);
        long totalMinutes = 0;

        // Percorre minuto a minuto dentro dos períodos úteis
        while (current.isBefore(finish)) {
            LocalDate currentDate = current.toLocalDate();

            // Se for feriado ou final de semana, pula para o próximo dia útil
            if (!schedule.isWorkingDay(currentDate)) {
                current = LocalDateTime.of(
                    schedule.getNextWorkingDay(currentDate),
                    schedule.getPeriods().get(0).getStart());
                continue;
            }

            boolean moved = false;

            // Varre todos os períodos do dia (manhã e tarde, por exemplo)
            for (WorkingPeriod period : schedule.getPeriods()) {
                LocalDateTime periodStart = LocalDateTime.of(currentDate, period.getStart());
                LocalDateTime periodEnd   = LocalDateTime.of(currentDate, period.getEnd());

                // Antes do expediente → move para o início do período
                if (current.isBefore(periodStart)) {
                    current = periodStart;
                }

                // Depois do expediente → ignora período
                if (current.isAfter(periodEnd)) {
                    continue;
                }

                // Determina o fim deste segmento com base no horário final geral
                LocalDateTime segmentEnd = finish.isBefore(periodEnd) ? finish : periodEnd;

                if (segmentEnd.isAfter(current)) {
                    totalMinutes += ChronoUnit.MINUTES.between(current, segmentEnd);
                    current = segmentEnd;
                    moved = true;
                }

                if (!current.isBefore(finish)) break;
            }

            // Não conseguiu avançar dentro de nenhum período → pula para próximo dia útil
            if (!moved) {
                current = LocalDateTime.of(
                    schedule.getNextWorkingDay(currentDate),
                    schedule.getPeriods().get(0).getStart());
            }
        }

        return formatTime(totalMinutes);
    }

    /**
     * Converte minutos totais para o formato <b>HH:mm</b>.
     *
     * @param totalMinutes Minutos acumulados
     * @return String formatada no padrão HH:mm
     */
    private String formatTime(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }
}
