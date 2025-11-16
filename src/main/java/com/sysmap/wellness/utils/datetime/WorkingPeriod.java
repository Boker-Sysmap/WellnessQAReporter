package com.sysmap.wellness.utils.datetime;

import java.time.LocalTime;

/**
 * Representa um período de trabalho dentro de um dia útil, composto por um
 * horário de início e um horário de término.
 *
 * <p>É utilizado pelo {@link WorkSchedule} e pelo {@link BusinessTimeCalculator}
 * para determinar quais horários são considerados válidos para contagem
 * de tempo útil.</p>
 *
 * <h2>Exemplos de períodos válidos:</h2>
 * <ul>
 *     <li>08:00 → 12:00</li>
 *     <li>13:00 → 18:00</li>
 * </ul>
 *
 * <h2>Regras de validação:</h2>
 * <ul>
 *     <li>O horário final <b>não pode</b> ser anterior ao horário inicial;</li>
 *     <li>O período considera o intervalo [start, end] como inclusivo;</li>
 *     <li>A classe é imutável e thread-safe.</li>
 * </ul>
 */
public class WorkingPeriod {

    /** Horário inicial do período de trabalho. */
    private final LocalTime start;

    /** Horário final do período de trabalho. */
    private final LocalTime end;

    /**
     * Cria um novo período de trabalho.
     *
     * @param start horário inicial (inclusive)
     * @param end   horário final (inclusive)
     * @throws IllegalArgumentException caso {@code end} seja anterior a {@code start}
     */
    public WorkingPeriod(LocalTime start, LocalTime end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                "O horário final deve ser posterior ao horário inicial.");
        }
        this.start = start;
        this.end = end;
    }

    /**
     * Retorna o horário inicial do período.
     *
     * @return {@link LocalTime} representando o início
     */
    public LocalTime getStart() {
        return start;
    }

    /**
     * Retorna o horário final do período.
     *
     * @return {@link LocalTime} representando o fim
     */
    public LocalTime getEnd() {
        return end;
    }

    /**
     * Verifica se um determinado horário está dentro deste período.
     *
     * <p>O intervalo é considerado inclusivo: valores iguais a
     * {@code start} ou {@code end} são aceitos.</p>
     *
     * @param time horário a verificar
     * @return {@code true} se {@code time ∈ [start, end]}, caso contrário {@code false}
     */
    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    /**
     * Calcula a duração total do período em minutos.
     *
     * @return número de minutos entre {@code start} e {@code end}
     */
    public long getDurationInMinutes() {
        return java.time.Duration.between(start, end).toMinutes();
    }

    /**
     * Retorna uma representação textual do período no formato:
     *
     * <pre>HH:mm - HH:mm</pre>
     *
     * @return string representando o período
     */
    @Override
    public String toString() {
        return start + " - " + end;
    }
}
