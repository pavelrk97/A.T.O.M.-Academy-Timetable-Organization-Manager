package ru.schedule.exception;

public class ScheduleParseException extends RuntimeException {

    public ScheduleParseException(String message) {
        super(message);
    }

    public ScheduleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
