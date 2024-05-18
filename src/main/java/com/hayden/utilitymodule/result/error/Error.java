package com.hayden.utilitymodule.result.error;

public interface Error {
    static Error fromMessage(String error) {
        return new StandardError(error);
    }

    static Error fromE(Throwable error) {
        return new StandardError(error);
    }

    static Error fromE(Throwable error, String cause) {
        return new StandardError(cause, error);
    }

    String getMessage();

    record StandardError(String error, Throwable throwable) implements Error {
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
