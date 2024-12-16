package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.MapFunctions;
import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.agg.Responses;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
public abstract class StreamWrapper<C extends CachableStream<ST, C>, ST> implements Stream<ST> {

    private volatile boolean cached;

    private final ConcurrentHashMap<Class<? extends StreamWrapper.CachedOperation>, StreamWrapper.StreamCacheResult> CACHED_RESULTS = new ConcurrentHashMap<>();

    private StreamResultOptions options;

    @Delegate
    private Stream<ST> underlying;

    protected Class<? extends StreamWrapper.StreamCacheOperation> provider;

    public StreamWrapper(StreamResultOptions options, Stream<ST> underlying,
                         Class<? extends StreamWrapper.StreamCacheOperation> provider,
                         StreamWrapper<C, ST> other) {
        this.options = options;
        this.underlying = underlying;
        this.provider = provider;
        Optional.ofNullable(other)
                .ifPresent(this::setCachedResultsFrom);
    }

    public StreamWrapper(StreamResultOptions options, Stream<ST> underlying,
                         Class<? extends StreamWrapper.StreamCacheOperation> provider) {
        this(options, underlying, provider, null);
    }

    public synchronized void swap(List<ST> to) {
        underlying = to.stream();
        if (options.cache())
            this.resetCache();
    }

    public void setCachedResultsFrom(StreamWrapper<C, ST> from) {
        CACHED_RESULTS.putAll(
            MapFunctions.CollectMap(
                    from.CACHED_RESULTS.entrySet()
                            .stream()
                            .filter(co -> co.getValue().predicateType instanceof PersistentCacheResult)));
    }

    <T extends StreamWrapper.StreamCacheOperation<RE, V>, RE, V> V get(Class<T> t) {
        return (V) CACHED_RESULTS.get(t).cachedResult;
    }

    <T extends StreamWrapper.StreamCacheOperation<RE, V>, RE, V> V get(TypeReferenceDelegate<T> t) {
        return (V) CACHED_RESULTS.get(t.underlying()).cachedResult;
    }

    synchronized void resetCache() {
        this.cached = false;
    }

    synchronized void cacheResults(C streamResult) {
        var resultList = streamResult.stream().toList();
        var swapped = streamResult.swap(resultList.stream());
        var created = cacheFilter(swapped);
        created.forEach(sp -> {
            switch (sp) {
                case StreamWrapper.StreamCachePredicate.Any n ->
                        CACHED_RESULTS.computeIfAbsent(n.getClass(), k -> new StreamWrapper.StreamCacheResult<>(n, false));
                case StreamWrapper.StreamCachePredicate.All p ->
                        CACHED_RESULTS.computeIfAbsent(p.getClass(), k -> new StreamWrapper.StreamCacheResult<>(p, true));
                default ->
                        throw new IllegalStateException("Unexpected value: " + sp);
            }
        });
        cached = true;
    }

    private @NotNull List<StreamWrapper.StreamCacheOperation> cacheFilter(C streamed) {
        if (options.empty() || options.isNonEmpty()) {
            var e = new StreamWrapper.IsCompletelyEmpty();
            CACHED_RESULTS.computeIfAbsent(e.getClass(),
                    k -> new StreamWrapper.StreamCacheResult<>(e, options.empty()));
        }

        if (options.hasRes()) {
            var e = new StreamWrapper.HasResult<>();
            CACHED_RESULTS.computeIfAbsent(e.getClass(),
                    k -> new StreamWrapper.StreamCacheResult<>(e, options.empty()));
        }

        if (options.hasErr()) {
            var e = new StreamWrapper.HasErr<>();
            CACHED_RESULTS.computeIfAbsent(e.getClass(),
                    k -> new StreamWrapper.StreamCacheResult<>(e, options.empty()));
        }

        var streamCacheOperations = new AtomicReference<>(
                predicateTypes(provider).stream()
                        .filter(Predicate.not(s -> CACHED_RESULTS.containsKey(s.getClass())))
                        .toList());

        if (options.isInfinite()) {
//            underlying = underlying.peek(c -> doOps(c, streamCacheOperations));
            throw new RuntimeException("");
        }

        streamed.stream().forEach(res -> doOps(res, streamCacheOperations));

        return streamCacheOperations.get();
    }

    private void doOps(ST res, AtomicReference<List<StreamCacheOperation>> streamCacheOperations) {
        var nextOps = streamCacheOperations.get();
        streamCacheOperations.set(
                nextOps.stream().filter(op -> switch (op) {
                            case StreamCachePredicate.Any n -> {
                                if (n.test(res)) {
                                    CACHED_RESULTS.computeIfAbsent(n.getClass(),
                                            k -> new StreamCacheResult<>(n, true));
                                    yield false;
                                }
                                yield true;
                            }
                            case StreamCachePredicate.All p -> {
                                if (p.test(res)) {
                                    yield true;
                                } else {
                                    CACHED_RESULTS.computeIfAbsent(p.getClass(),
                                            k -> new StreamCacheResult<>(p, false));
                                    yield false;
                                }

                            }
                            case StreamWrapper.StreamCacheFunction fun ->
                                    Optional.ofNullable(fun.apply(res))
                                            .map(app -> {
                                                CACHED_RESULTS.compute(fun.getClass(),
                                                        (key, prev) -> new StreamCacheResult(fun, app));
                                                return false;
                                            })
                                            .orElse(true);
                        })
                        .toList()
        );
    }

    private Stream<Class<? extends StreamWrapper.StreamCacheOperation>> predicateTypesHierarchy(Class<? extends StreamWrapper.StreamCacheOperation> anyClass) {
        return Optional.ofNullable(anyClass.getPermittedSubclasses())
                .stream()
                .flatMap(Arrays::stream)
                .flatMap(spc -> {
                    var permittedSubclasses = Optional.ofNullable(spc.getPermittedSubclasses())
                            .stream()
                            .flatMap(Arrays::stream)
                            .map(c -> (Class<? extends StreamCacheOperation>) c)
                            .toList();
                    return Stream.concat(
                            Stream.concat(Stream.of((Class<? extends StreamCacheOperation>) spc, anyClass), permittedSubclasses.stream()),
                            //
                            permittedSubclasses
                                    .stream()
                                    .flatMap(this::predicateTypesHierarchy)
                    );
                });
    }

    private List<StreamWrapper.StreamCacheOperation> predicateTypes(Class<? extends StreamWrapper.StreamCacheOperation> anyClass) {
        return predicateTypesHierarchy(anyClass)
                .filter(Predicate.not(Class::isInterface))
                .map(c -> {
                    try {
                        return (StreamCacheOperation) c.getDeclaredConstructor().newInstance();
                    } catch (
                            InstantiationException |
                            IllegalAccessException |
                            InvocationTargetException |
                            NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
    }

     synchronized boolean isAnyNonNull(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        if (!cached)
            cacheResults(streamResult);

        return (boolean) get(StreamWrapper.IsAnyNonNull.class);
    }

    synchronized boolean isCompletelyEmpty(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        if (!cached)
            cacheResults(streamResult);

        return (boolean) get(StreamWrapper.IsCompletelyEmpty.class);
    }

    synchronized boolean hasAnyResult(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        if (!cached)
            cacheResults(streamResult);

        return (boolean) get(StreamWrapper.HasResult.class);
    }

    synchronized boolean hasAnyError(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        if (!cached)
            cacheResults(streamResult);

        return (boolean) get(StreamWrapper.HasErr.class);
    }

    private interface CachedOperation<T, U> extends Function<T, U> {}

    public sealed interface StreamCacheOperation<T, U> extends Function<T, U>
            permits
                ResultTyStreamWrapperOperation,
                ResultStreamCacheOperation { }

    public sealed interface ResultTyStreamWrapperOperation<T, U> extends StreamCacheOperation<T, U>
            permits ResultTyPredicate, ResultTyStreamCacheFunction {
    }

    private interface StreamCacheFunction<T, U> extends CachedOperation<T, U> {}

    sealed interface ResultStreamCacheOperation<T, U> extends StreamCacheOperation<T, U>
            permits ResultStreamCacheFunction, ResultStreamCachePredicate{ }

    private sealed interface ResultStreamCacheFunction<T, U> extends StreamCacheFunction<T, U>, ResultStreamCacheOperation<T, U>
            permits
            RetrieveError,
            RetrieveRes { }

    private sealed interface ResultTyStreamCacheFunction<T, U> extends StreamCacheFunction<T, U>, ResultTyStreamWrapperOperation<T, U>
            permits RetrieveFirstTy { }

    record RetrieveFirstTy<T>() implements ResultTyStreamCacheFunction<IResultTy<T>, T> {
        @Override
        public T apply(IResultTy<T> teResult) {
            return null;
        }
    }

    record RetrieveError<T, E>() implements ResultStreamCacheFunction<Result<T, E>, Err<E>> {
        @Override
        public Err<E> apply(Result<T, E> teResult) {
            return null;
        }
    }

    record RetrieveRes<T, E>() implements ResultStreamCacheFunction<Result<T, E>, Responses.Ok<T>>{
        @Override
        public Responses.Ok<T> apply(Result<T, E> teResult) {
            return null;
        }
    }

    private interface StreamCachePredicate<T> extends Predicate<T>, CachedOperation<T, Boolean>{

        non-sealed interface Any<T> extends ResultTyPredicate<T>, ResultStreamCachePredicate<T> {
            @Override
            default boolean test(T t) {
                return false;
            }

            @Override
            default Boolean apply(T t) {
                return this.test(t);
            }
        }

        non-sealed interface All<T> extends ResultTyPredicate<T>, ResultStreamCachePredicate<T> {
            @Override
            default boolean test(T t) {
                return false;
            }

            @Override
            default Boolean apply(T t) {
                return this.test(t);
            }
        }
    }

    private sealed interface ResultTyPredicate<T> extends StreamCachePredicate<T>, ResultTyStreamWrapperOperation<T, Boolean>
            permits IsAnyNonNull, IsCompletelyEmpty, StreamCachePredicate.All, StreamCachePredicate.Any { }

    private sealed interface ResultStreamCachePredicate<T> extends StreamCachePredicate<T>, ResultStreamCacheOperation<T, Boolean>
            permits HasErr, HasResult, IsAnyNonNull, IsCompletelyEmpty, StreamCachePredicate.All, StreamCachePredicate.Any { }

    private record StreamCacheResult<T, U>(CachedOperation<T, U> predicateType,
                                           U cachedResult) {}

    public interface PersistentCacheResult  {}

    record IsAnyNonNull() implements StreamCachePredicate.Any, ResultStreamCachePredicate, ResultTyPredicate, PersistentCacheResult {
        @Override
        public boolean test(Object o) {
            return o != null;
        }
    }

    record IsCompletelyEmpty() implements StreamCachePredicate.All, ResultStreamCachePredicate, ResultTyPredicate, PersistentCacheResult {
        @Override
        public boolean test(Object o) {
            return false;
        }
    }

    record HasResult<R, E>() implements StreamCachePredicate.Any<Result<R, E>>, ResultStreamCachePredicate<Result<R, E>>, PersistentCacheResult {
        @Override
        public boolean test(Result<R, E> o) {
            return false;
        }
    }

    record HasErr<R, E>() implements StreamCachePredicate.Any<Result<R, E>>, ResultStreamCachePredicate<Result<R, E>>, PersistentCacheResult {
        @Override
        public boolean test(Result<R, E> o) {

            return false;
        }
    }

}
