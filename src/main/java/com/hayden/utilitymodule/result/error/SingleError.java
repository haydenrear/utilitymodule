package com.hayden.utilitymodule.result.error;

import java.util.Arrays;
import java.util.stream.Collectors;

public interface SingleError {

    static String parseStackTraceToString(Throwable e) {
        return Arrays.stream(e.getStackTrace())
                .map(StackTraceElement::toString).collect(Collectors.joining(System.lineSeparator()));
    }

    default boolean isError() {
        return true;
    }

    static SingleError fromMessage(String error) {
        return new StandardError(error);
    }

    static SingleError fromE(Throwable error) {
        return new StandardError(error);
    }

    static SingleError fromE(Throwable error, String cause) {
        return new StandardError(cause, error);
    }

    String getMessage();

    record StandardError(String error, Throwable throwable) implements SingleError {
        public StandardError(Throwable throwable) {
            this(throwable.getMessage(), throwable);
        }
        public StandardError(String throwable) {
            this(throwable, null);
        }

        @Override
        public String getMessage() {
            return error;
        }
    }

}
