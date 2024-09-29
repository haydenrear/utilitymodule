package com.hayden.utilitymodule.stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.stream.Stream;

public class StreamUtil {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Slf4j
    public static class StreamBuilderDelegate<T> {

        @Delegate
        Stream.Builder<T> builder = Stream.builder();

        public static <T> StreamBuilderDelegate<T> builder() {
            return new StreamBuilderDelegate<T>();
        }

        public StreamBuilderDelegate<T> addAllStreams(Stream ... streams) {
            Arrays.stream(streams)
                    .flatMap(s -> s)
                    .forEach(t -> {
                        try {
                            builder.add((T) t);
                        } catch (ClassCastException c) {
                            log.error("Could not add builder in stream builder delegate: {}.", c.getMessage());
                            throw c;
                        }
                    });
            return this;
        }

        public StreamBuilderDelegate<T> add(T ... streams) {
            Arrays.stream(streams).forEach(builder::add);
            return this;
        }

        public StreamBuilderDelegate<T> addStream(Stream<T> streams) {
            streams.forEach(builder::add);
            return this;
        }

        public StreamBuilderDelegate<T> addAll(Stream<T> stream) {
            stream.forEach(builder::add);
            return this;
        }


    }

}
