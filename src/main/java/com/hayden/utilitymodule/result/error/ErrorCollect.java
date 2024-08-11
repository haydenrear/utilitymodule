package com.hayden.utilitymodule.result.error;

public interface ErrorCollect {

    default boolean isError() {
        return true;
    }

    static ErrorCollect fromMessage(String error) {
        return new StandardError(error);
    }

    static ErrorCollect fromE(Throwable error) {
        return new StandardError(error);
    }

    static ErrorCollect fromE(Throwable error, String cause) {
        return new StandardError(cause, error);
    }

    String getMessage();

    record StandardError(String error, Throwable throwable) implements ErrorCollect {
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
