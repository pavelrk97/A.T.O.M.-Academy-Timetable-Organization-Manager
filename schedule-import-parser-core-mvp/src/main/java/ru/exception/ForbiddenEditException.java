package ru.exception;

public class ForbiddenEditException extends RuntimeException {
    public ForbiddenEditException(String message) {
        super(message);
    }
}
