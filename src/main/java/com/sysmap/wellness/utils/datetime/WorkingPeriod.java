package com.sysmap.wellness.utils.datetime;

import java.time.LocalTime;

public class WorkingPeriod {
    private final LocalTime start;
    private final LocalTime end;

    public WorkingPeriod(LocalTime start, LocalTime end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("O horário final deve ser após o horário inicial");
        }
        this.start = start;
        this.end = end;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    public long getDurationInMinutes() {
        return java.time.Duration.between(start, end).toMinutes();
    }

    @Override
    public String toString() {
        return start + " - " + end;
    }
}
