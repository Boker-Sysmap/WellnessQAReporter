package com.sysmap.wellness.utils.datetime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Responsável por carregar e normalizar a lista de feriados utilizada pelo
 * {@link BusinessTimeCalculator} e pela configuração de calendário de trabalho
 * do sistema.
 *
 * <p>Os feriados são lidos a partir do arquivo {@code holidays.json}, localizado
 * no classpath (normalmente em {@code src/main/resources}).</p>
 *
 * <h2>Principais responsabilidades:</h2>
 * <ul>
 *     <li>Carregar o arquivo JSON contendo a relação de feriados;</li>
 *     <li>Converter cada item para um objeto {@link Holiday};</li>
 *     <li>Garantir que o campo {@code weekday} refletirá corretamente o
 *         dia da semana calculado a partir da data, mesmo que o JSON esteja
 *         desatualizado;</li>
 *     <li>Lançar erro explícito caso o arquivo não exista ou esteja inválido.</li>
 * </ul>
 *
 * <h2>Formato esperado do arquivo holidays.json:</h2>
 * <pre>
 * [
 *   {
 *     "date": "2025-01-01",
 *     "weekday": "Quarta-feira",
 *     "name": "Confraternização Universal",
 *     "type": "Nacional"
 *   },
 *   {
 *     "date": "2025-02-25",
 *     "weekday": "Terça-feira",
 *     "name": "Carnaval",
 *     "type": "Ponto facultativo"
 *   }
 * ]
 * </pre>
 *
 * <h2>Normalização automática do campo weekday</h2>
 * <p>
 * Mesmo que o JSON tenha um {@code weekday} incorreto (por exemplo, se o arquivo for antigo),
 * o método recalcula o dia da semana com base no campo {@code date} usando
 * {@link LocalDate#getDayOfWeek()}.
 * </p>
 *
 * <h2>Exemplo de uso:</h2>
 * <pre>
 * List&lt;Holiday&gt; holidays = HolidayLoader.loadHolidays();
 * </pre>
 */
public class HolidayLoader {

    /**
     * Carrega e normaliza a lista de feriados definida no arquivo
     * {@code holidays.json} presente no classpath.
     *
     * <p>
     * A lista retornada estará sempre com o campo {@code weekday} consistente
     * com a data informada, garantindo integridade mesmo se o JSON estiver
     * defasado.
     * </p>
     *
     * @return Lista de {@link Holiday} devidamente validada e normalizada.
     * @throws RuntimeException Caso o arquivo não seja encontrado ou ocorra
     *                          qualquer erro ao tentar parsear o JSON.
     */
    public static List<Holiday> loadHolidays() {
        try (InputStream input = HolidayLoader.class
            .getClassLoader()
            .getResourceAsStream("holidays.json")) {

            if (input == null) {
                throw new IllegalStateException("Arquivo holidays.json não encontrado no classpath.");
            }

            ObjectMapper mapper = new ObjectMapper();

            // Lê lista de Holiday diretamente do JSON
            List<Holiday> holidays =
                mapper.readValue(input, new TypeReference<List<Holiday>>() {});

            // Corrige inconsistências no campo weekday
            for (Holiday h : holidays) {
                LocalDate date = LocalDate.parse(h.getDate());
                DayOfWeek dow = date.getDayOfWeek();

                // Nome do dia da semana em português BR, capitalizado corretamente
                String weekday = dow.getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
                weekday = weekday.substring(0, 1).toUpperCase() + weekday.substring(1);

                h.setWeekday(weekday);
            }

            return holidays;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar ou processar holidays.json", e);
        }
    }
}
