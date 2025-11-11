package com.sysmap.wellness.utils.datetime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class HolidayLoader {

    public static List<Holiday> loadHolidays() {
        try (InputStream input = HolidayLoader.class.getClassLoader().getResourceAsStream("holidays.json")) {
            if (input == null) {
                throw new IllegalStateException("Arquivo holidays.json não encontrado em resources/");
            }

            ObjectMapper mapper = new ObjectMapper();
            List<Holiday> holidays = mapper.readValue(input, new TypeReference<List<Holiday>>() {});

            // Garante que o dia da semana está correto mesmo que o JSON seja antigo
            for (Holiday h : holidays) {
                LocalDate date = LocalDate.parse(h.getDate());
                DayOfWeek dow = date.getDayOfWeek();
                String weekday = dow.getDisplayName(TextStyle.FULL, new Locale("pt", "BR"));
                h.setWeekday(weekday.substring(0, 1).toUpperCase() + weekday.substring(1));
            }

            return holidays;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar holidays.json", e);
        }
    }
}

