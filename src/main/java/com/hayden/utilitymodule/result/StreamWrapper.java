package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.MapFunctions;
import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.stream_cache.CachingOperations;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.Assert;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.*;

@Slf4j
public abstract class StreamWrapper<C extends CachableStream<ST, C>, ST> implements Stream<ST> {


    private final ConcurrentHashMap<Class<? extends CachingOperations.CachedOperation>, CachingOperations.StreamCacheResult> CACHED_RESULTS = new ConcurrentHashMap<>();

    private StreamResultOptions options;

    @Delegate
    protected Stream<ST> underlying;

    protected volatile boolean cached = false;

    protected Class<? extends CachingOperations.StreamCacheOperation> provider;

    protected C res;

    public StreamWrapper(StreamResultOptions options, Stream<ST> underlying,
                         Class<? extends CachingOperations.StreamCacheOperation> provider,
                         StreamWrapper<C, ST> other,
                         C res) {
        this.res = res;
        this.options = options;
        this.underlying = underlying;
        this.provider = provider;
        Optional.ofNullable(other)
                .ifPresent(this::setCachedResultsFrom);
    }

    public StreamWrapper(StreamResultOptions options, Stream<ST> underlying,
                         Class<? extends CachingOperations.StreamCacheOperation> provider,
                         C c) {
        this(options, underlying, provider, null, c);
    }

    public synchronized void swap(List<ST> to) {
        underlying = to.stream();
        this.cached = false;
    }

    public void setCachedResultsFrom(StreamWrapper<C, ST> from) {
        CACHED_RESULTS.putAll(
            MapFunctions.CollectMap(
                    from.CACHED_RESULTS.entrySet()
                            .stream()
                            .filter(co -> co.getValue().predicateType() instanceof CachingOperations.PersistentCacheResult)));
    }

    <T extends CachingOperations.StreamCacheOperation<RE, V>, RE, V> V get(Class<T> t) {
        return (V) CACHED_RESULTS.get(t).cachedResult();
    }

    protected <T extends CachingOperations.StreamCacheOperation<RE, V>, RE, V> V get(TypeReferenceDelegate<T> t) {
        return (V) CACHED_RESULTS.get(t.underlying()).cachedResult();
    }

    synchronized void resetCache() {
        this.cached = false;
    }

    protected synchronized void cacheResultsIfNotCached() {
        if (this.cached)
            return;

        var resultList = this.underlying.toList();
        this.underlying = resultList.stream();
        var swapped = this.res.swap(resultList.stream());
        var created = cacheFilter(swapped);

        created.forEach(sp -> {
            switch (sp) {
                case CachingOperations.StreamCachePredicate.Any n ->
                        CACHED_RESULTS.computeIfAbsent(n.getClass(), k -> new CachingOperations.StreamCacheResult<>(n, false));
                case CachingOperations.StreamCachePredicate.All p ->
                        CACHED_RESULTS.computeIfAbsent(p.getClass(), k -> new CachingOperations.StreamCacheResult<>(p, true));
                default ->
                       log.debug("Skipped stream result: {}", sp);
            }
        });
        this.cached = true;
    }

    private @NotNull List<CachingOperations.StreamCacheOperation> cacheFilter(C streamed) {
        if (options.empty() || options.isNonEmpty()) {
            var e = new CachingOperations.IsCompletelyEmpty();
            CACHED_RESULTS.computeIfAbsent(e.getClass(),
                    k -> new CachingOperations.StreamCacheResult<>(e, options.empty()));
        }

        if (options.hasRes()) {
            var e = new CachingOperations.HasResult<>();
            CACHED_RESULTS.computeIfAbsent(e.getClass(),
                    k -> new CachingOperations.StreamCacheResult<>(e, options.empty()));
        }

        if (options.hasErr()) {
            var e = new CachingOperations.HasErr<>();
            CACHED_RESULTS.computeIfAbsent(e.getClass(),
                    k -> new CachingOperations.StreamCacheResult<>(e, options.empty()));
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

    private void doOps(ST res, AtomicReference<List<CachingOperations.StreamCacheOperation>> streamCacheOperations) {
        var nextOps = streamCacheOperations.get();
        streamCacheOperations.set(
                nextOps.stream().filter(op -> switch (op) {
                            case CachingOperations.StreamCachePredicate.Any n -> {
                                if (n.test(res)) {
                                    CACHED_RESULTS.computeIfAbsent(n.getClass(),
                                            k -> new CachingOperations.StreamCacheResult<>(n, true));
                                    yield false;
                                }
                                yield true;
                            }
                            case CachingOperations.StreamCachePredicate.All p -> {
                                if (p.test(res)) {
                                    yield true;
                                } else {
                                    CACHED_RESULTS.computeIfAbsent(p.getClass(),
                                            k -> new CachingOperations.StreamCacheResult<>(p, false));
                                    yield false;
                                }

                            }
                            case CachingOperations.StreamCacheFunction fun ->
                                    Optional.ofNullable(fun.apply(res))
                                            .map(app -> {
                                                CACHED_RESULTS.compute(fun.getClass(),
                                                        (key, prev) -> new CachingOperations.StreamCacheResult(fun, app));
                                                return false;
                                            })
                                            .orElse(true);
                        })
                        .toList()
        );
    }

    private Stream<Class<? extends CachingOperations.StreamCacheOperation>> predicateTypesHierarchy(Class<? extends CachingOperations.StreamCacheOperation> anyClass) {
        return Optional.ofNullable(anyClass.getPermittedSubclasses())
                .stream()
                .flatMap(Arrays::stream)
                .flatMap(spc -> {
                    var permittedSubclasses = Optional.ofNullable(spc.getPermittedSubclasses())
                            .stream()
                            .flatMap(Arrays::stream)
                            .map(c -> (Class<? extends CachingOperations.StreamCacheOperation>) c)
                            .toList();
                    return Stream.concat(
                            Stream.concat(Stream.of((Class<? extends CachingOperations.StreamCacheOperation>) spc, anyClass), permittedSubclasses.stream()),
                            //
                            permittedSubclasses
                                    .stream()
                                    .flatMap(this::predicateTypesHierarchy)
                    );
                });
    }

    private List<CachingOperations.StreamCacheOperation> predicateTypes(Class<? extends CachingOperations.StreamCacheOperation> anyClass) {
        return predicateTypesHierarchy(anyClass)
                .filter(Predicate.not(Class::isInterface))
                .map(c -> {
                    try {
                        return (CachingOperations.StreamCacheOperation) c.getDeclaredConstructor().newInstance();
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
            cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.IsAnyNonNull.class);
    }

    synchronized boolean isCompletelyEmpty(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        if (!cached)
            cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.IsCompletelyEmpty.class);
    }

    synchronized boolean hasAnyResult(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        if (!cached)
            cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.HasResult.class);
    }

    synchronized boolean hasAnyError(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        if (!cached)
            cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.HasErr.class);
    }

    public synchronized void throwIfCachedOrCache() {
        if (this.cached)
            throw new RuntimeException("Already cached!");

        cacheResultsIfNotCached();
    }

    @Override
    public void close() {
        underlying.close();
    }

    @Override
    public boolean isParallel() {
        throwIfCachedOrCache();
        return underlying.isParallel();
    }

    @Override
    public @NotNull Spliterator<ST> spliterator() {
        throwIfCachedOrCache();
        return underlying.spliterator();
    }

    @Override
    public @NotNull Iterator<ST> iterator() {
        throwIfCachedOrCache();
        return underlying.iterator();
    }

    @Override
    public boolean noneMatch(Predicate<? super ST> predicate) {
        throwIfCachedOrCache();
        return underlying.noneMatch(predicate);
    }

    @Override
    public boolean allMatch(Predicate<? super ST> predicate) {
        throwIfCachedOrCache();
        return underlying.allMatch(predicate);
    }

    @Override
    public boolean anyMatch(Predicate<? super ST> predicate) {
        throwIfCachedOrCache();
        return underlying.anyMatch(predicate);
    }

    @Override
    public long count() {
        throwIfCachedOrCache();
        return underlying.count();
    }

    @Override
    public @NotNull Optional<ST> max(Comparator<? super ST> comparator) {
        throwIfCachedOrCache();
        return underlying.max(comparator);
    }

    @Override
    public @NotNull Optional<ST> min(Comparator<? super ST> comparator) {
        throwIfCachedOrCache();
        return underlying.min(comparator);
    }

    @Override
    public List<ST> toList() {
        throwIfCachedOrCache();
        return underlying.toList();
    }

    @Override
    public <R, A> R collect(Collector<? super ST, A, R> collector) {
        throwIfCachedOrCache();
        return underlying.collect(collector);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super ST> accumulator, BiConsumer<R, R> combiner) {
        throwIfCachedOrCache();
        return underlying.collect(supplier, accumulator, combiner);
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super ST, U> accumulator, BinaryOperator<U> combiner) {
        throwIfCachedOrCache();
        return underlying.reduce(identity, accumulator, combiner);
    }

    @Override
    public @NotNull Optional<ST> reduce(BinaryOperator<ST> accumulator) {
        throwIfCachedOrCache();
        return underlying.reduce(accumulator);
    }

    @Override
    public ST reduce(ST identity, BinaryOperator<ST> accumulator) {
        throwIfCachedOrCache();
        return underlying.reduce(identity, accumulator);
    }

    @Override
    public @NotNull <A> A[] toArray(IntFunction<A[]> generator) {
        throwIfCachedOrCache();
        return underlying.toArray(generator);
    }

    @Override
    public @NotNull Object[] toArray() {
        throwIfCachedOrCache();
        return underlying.toArray();
    }

    @Override
    public void forEachOrdered(Consumer<? super ST> action) {
        throwIfCachedOrCache();
        underlying.forEachOrdered(action);
    }

    @Override
    public void forEach(Consumer<? super ST> action) {
        throwIfCachedOrCache();
        underlying.forEach(action);
    }

    @Override
    public Stream<ST> dropWhile(Predicate<? super ST> predicate) {
        return underlying.dropWhile(predicate);
    }

}
