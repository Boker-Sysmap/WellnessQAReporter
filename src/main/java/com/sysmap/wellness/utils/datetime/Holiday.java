package com.sysmap.wellness.utils.datetime;

public class Holiday {
    private String date;
    private String weekday;
    private String name;
    private String type;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getWeekday() { return weekday; }
    public void setWeekday(String weekday) { this.weekday = weekday; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Override
    public String toString() {
        return date + " (" + weekday + "): " + name + " - " + type;
    }
}
