package com.hayden.utilitymodule.stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

import java.util.Arrays;
import java.util.stream.Stream;

public class StreamUtil {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class StreamBuilderDelegate<T> {

        @Delegate
        Stream.Builder<T> builder = Stream.builder();

        public static <T> StreamBuilderDelegate<T> builder() {
            return new StreamBuilderDelegate<T>();
        }

        public StreamBuilderDelegate<T> addAllStreams(Stream ... streams) {
            Arrays.stream(streams)
                    .flatMap(s -> s)
                    .forEach(t -> builder.add((T) t));
            return this;
        }

        public StreamBuilderDelegate<T> add(T ... streams) {
            Arrays.stream(streams).forEach(builder::add);
            return this;
        }

        public StreamBuilderDelegate<T> addAll(Stream<T> stream) {
            stream.forEach(builder::add);
            return this;
        }


    }

}
