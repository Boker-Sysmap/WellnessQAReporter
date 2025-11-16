package com.sysmap.wellness.utils.datetime;

/**
 * Representa um feriado utilizado pelo sistema de cálculo de tempo útil.
 *
 * <p>
 * Esta classe modela um registro do arquivo {@code holidays.json},
 * contendo informações sobre:
 * </p>
 *
 * <ul>
 *     <li>Data do feriado;</li>
 *     <li>Dia da semana correspondente;</li>
 *     <li>Nome do feriado;</li>
 *     <li>Tipo do feriado (nacional, estadual, municipal, etc.);</li>
 * </ul>
 *
 * <p>
 * A classe é um simples POJO (Plain Old Java Object), utilizado pelo
 * {@code HolidayService}, {@code WorkSchedule} e {@code BusinessTimeCalculator}
 * para determinar se um dia deve ser excluído do cálculo de expediente.
 * </p>
 *
 * <h2>Formato esperado no JSON:</h2>
 * <pre>
 * {
 *   "date": "2025-01-01",
 *   "weekday": "Wednesday",
 *   "name": "Confraternização Universal",
 *   "type": "Nacional"
 * }
 * </pre>
 */
public class Holiday {

    /** Data do feriado em formato ISO (yyyy-MM-dd). */
    private String date;

    /** Nome do dia da semana (ex.: Monday, Tuesday). */
    private String weekday;

    /** Nome descritivo do feriado. */
    private String name;

    /** Tipo do feriado (nacional, estadual, municipal, técnico, etc.). */
    private String type;

    /**
     * Obtém a data do feriado.
     *
     * @return Data no formato ISO (yyyy-MM-dd).
     */
    public String getDate() {
        return date;
    }

    /**
     * Define a data do feriado.
     *
     * @param date Data no formato ISO (yyyy-MM-dd).
     */
    public void setDate(String date) {
        this.date = date;
    }

    /**
     * Obtém o dia da semana referente ao feriado.
     *
     * @return Nome do dia da semana (Monday, Tuesday, ...).
     */
    public String getWeekday() {
        return weekday;
    }

    /**
     * Define o dia da semana referente ao feriado.
     *
     * @param weekday Nome do dia da semana.
     */
    public void setWeekday(String weekday) {
        this.weekday = weekday;
    }

    /**
     * Obtém o nome do feriado.
     *
     * @return Nome descritivo do feriado.
     */
    public String getName() {
        return name;
    }

    /**
     * Define o nome do feriado.
     *
     * @param name Nome do feriado.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Obtém o tipo do feriado.
     *
     * @return Tipo (ex.: Nacional, Estadual, Municipal, Técnico).
     */
    public String getType() {
        return type;
    }

    /**
     * Define o tipo do feriado.
     *
     * @param type Tipo do feriado.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Retorna uma representação amigável do feriado,
     * usada principalmente para logs e depuração.
     *
     * @return String formatada contendo data, weekday, nome e tipo.
     */
    @Override
    public String toString() {
        return date + " (" + weekday + "): " + name + " - " + type;
    }
}
