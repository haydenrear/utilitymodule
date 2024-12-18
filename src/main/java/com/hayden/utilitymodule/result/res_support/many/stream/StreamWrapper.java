package com.hayden.utilitymodule.result.res_support.many.stream;

import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachableStream;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachingOperations;
import lombok.RequiredArgsConstructor;
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


    protected final Class<? extends CachingOperations.StreamCacheOperation> provider;

    protected final StreamResultOptions options;

    protected final StreamCache<? extends CachingOperations.CachedOperation, C, ST> cached;

    protected void setUnderlying(Stream<ST> underlying) {
        this.underlying = underlying;
    }

    @Delegate
    protected Stream<ST> underlying;


    protected C res;


    protected interface StreamCache<T extends CachingOperations.CachedOperation<ST, ?>, C extends CachableStream<ST, C>, ST> {

        ConcurrentHashMap<Class<? extends T>, CachingOperations.StreamCacheResult> CACHED_RESULTS();

        Class<? extends CachingOperations.StreamCacheOperation> provider();

        boolean isCached();

        default void doCache(C c) {
            doCache(c, st -> {});
        }

        void doCache(C c, Consumer<? super ST> terminalOp);

        List<ST> cacheToList(C c, Consumer<? super ST> terminalOp);

        default List<ST> cacheToList(C c) {
            return this.cacheToList(c, cst -> {});
        }

        void resetCache();

        static Stream<Class<? extends CachingOperations.StreamCacheOperation>> predicateTypesHierarchy(Class<? extends CachingOperations.StreamCacheOperation> anyClass) {
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
                                        .flatMap(StreamCache::predicateTypesHierarchy)
                        );
                    });
        }

        static List<CachingOperations.StreamCacheOperation> predicateTypes(Class<? extends CachingOperations.StreamCacheOperation> anyClass) {
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

        record CacheFilterResult<ST>(List<CachingOperations.StreamCacheOperation<ST, ?>> res,
                                     List<ST> results) {}

        default @NotNull CacheFilterResult<ST> cacheFilter(C streamed,
                                                           StreamResultOptions opts,
                                                           Consumer<? super ST> terminalOp) {
            if (opts.empty()) {
                var e = new CachingOperations.IsCompletelyEmpty();
                CACHED_RESULTS().computeIfAbsent((Class<? extends T>) e.getClass(),
                        k -> new CachingOperations.StreamCacheResult<>(e, true));
            }

            if (opts.hasRes()) {
                var e = new CachingOperations.HasResult<>();
                CACHED_RESULTS().computeIfAbsent((Class<? extends T>) e.getClass(),
                        k -> new CachingOperations.StreamCacheResult<>(e, true));
            }

            if (opts.hasErr()) {
                var e = new CachingOperations.HasErr<>();
                CACHED_RESULTS().computeIfAbsent((Class<? extends T>) e.getClass(),
                        k -> new CachingOperations.StreamCacheResult<>(e, true));
            }

            var streamCacheOperations = new AtomicReference<>(
                    predicateTypes(provider()).stream()
                            .filter(scop -> !opts.isInfinite() || scop instanceof CachingOperations.InfiniteOperation<?, ?>)
                            .filter(Predicate.not(s -> CACHED_RESULTS().containsKey(s.getClass())))
                            .toList());

            if (opts.isInfinite()) {
                streamed.swap(streamed.stream().peek(c -> doOps(c, streamCacheOperations)));
                return new CacheFilterResult<>(new ArrayList<>(), new ArrayList<>());
            }

            var resultList = streamed.stream().peek(res -> { terminalOp.accept(res); doOps(res, streamCacheOperations); })
                    .toList();

            return new CacheFilterResult<>((List) streamCacheOperations.get(), resultList);
        }

        private void doOps(ST res, AtomicReference<List<CachingOperations.StreamCacheOperation>> streamCacheOperations) {
            var nextOps = streamCacheOperations.get();
            streamCacheOperations.set(
                    nextOps.stream().filter(op -> switch (op) {
                                case CachingOperations.StreamCachePredicate.Any n -> {
                                    if (n.test(res)) {
                                        CACHED_RESULTS().computeIfAbsent((Class<? extends T>) n.getClass(),
                                                k -> new CachingOperations.StreamCacheResult<>(n, true));
                                        yield false;
                                    }
                                    yield true;
                                }
                                case CachingOperations.StreamCachePredicate.All p -> {
                                    if (p.test(res)) {
                                        yield true;
                                    } else {
                                        CACHED_RESULTS().computeIfAbsent((Class<? extends T>) p.getClass(),
                                                k -> new CachingOperations.StreamCacheResult<>(p, false));
                                        yield false;
                                    }
                                }
                                case CachingOperations.StreamCacheFunction fun ->
                                        Optional.ofNullable(fun.apply(res))
                                                .map(app -> {
                                                    CACHED_RESULTS().compute((Class<? extends T>) fun.getClass(),
                                                            (key, prev) -> new CachingOperations.StreamCacheResult(fun, app));
                                                    return false;
                                                })
                                                .orElse(true);
                            })
                            .toList()
            );
        }

        default <W extends CachingOperations.CachedOperation<U, V>, U, V> OneResult<V, SingleError.StandardError> get(Class<W> clazz) {
            return Result.fromOpt(
                            TypeReferenceDelegate.<W>create(clazz),
                            new SingleError.StandardError("Failed to cast to type ref for %s".formatted(clazz.getName()))
                    )
                    .flatMapResult(this::get)
                    .one();
        }

        default <W extends CachingOperations.CachedOperation<U, V>, U, V> OneResult<V, SingleError.StandardError> get(TypeReferenceDelegate<W> clazz) {
            try {
                return Optional.ofNullable(CACHED_RESULTS().get(clazz.underlying()))
                        .map(cachedRes -> Result.<V, SingleError.StandardError>ok((V) cachedRes.cachedResult()))
                        .orElseGet(() -> Result.err(new SingleError.StandardError("Operation did not exist from %s.".formatted(clazz.getName()))));
            } catch (ClassCastException castingException) {
                return Result.err(new SingleError.StandardError(castingException));
            }
        }

    }

    @RequiredArgsConstructor
    protected class InfiniCache implements StreamCache<CachingOperations.InfiniteOperation<ST, ?>, C, ST> {

        private final ConcurrentHashMap<Class<? extends CachingOperations.InfiniteOperation<ST, ?>>, CachingOperations.StreamCacheResult> CACHED_RESULTS
                = new ConcurrentHashMap<>();

        protected final Class<? extends CachingOperations.StreamCacheOperation> provider;

        private volatile boolean cached = false;


        @Override
        public synchronized void doCache(C c, Consumer<? super ST> st) {
            if (this.isCached())
                return;


            cacheFilter(c, options, st);

            this.cached = true;
        }

        @Override
        public List<ST> cacheToList(C c, Consumer<? super ST> st) {
            if (this.isCached())
                throw new IllegalStateException();


            var cachedL = cacheFilter(c, options, st);

            this.cached = true;

            return cachedL.results;
        }

        @Override
        public synchronized void resetCache() {
            cached = false;
        }

        @Override
        public ConcurrentHashMap<Class<? extends CachingOperations.InfiniteOperation<ST, ?>>, CachingOperations.StreamCacheResult> CACHED_RESULTS() {
            return CACHED_RESULTS;
        }

        @Override
        public Class<? extends CachingOperations.StreamCacheOperation> provider() {
            return provider;
        }

        @Override
        public boolean isCached() {
            return cached;
        }

    }

    @RequiredArgsConstructor
    protected class StandardCache implements StreamCache<CachingOperations.CachedOperation<ST, ?>, C, ST> {

        private final ConcurrentHashMap<Class<? extends CachingOperations.CachedOperation<ST, ?>>, CachingOperations.StreamCacheResult> CACHED_RESULTS
                = new ConcurrentHashMap<>();

        protected final Class<? extends CachingOperations.StreamCacheOperation> provider;

        private volatile boolean cached = false;

        public synchronized void doCache(C c) {

        }

        public synchronized void doCache(C c, Consumer<? super ST> terminalOp) {
            doCaching(c, terminalOp);
        }

         @Override
        public List<ST> cacheToList(C c, Consumer<? super ST> terminalOp) {
             var created = doCaching(c, terminalOp);

             return created.results;
        }

        private @NotNull CacheFilterResult<ST> doCaching(C c, Consumer<? super ST> terminalOp) {
            if (this.isCached())
                throw new IllegalStateException();

            var created = cacheFilter(c, options, terminalOp);
            finalizeOps(created);
            this.cached = true;
            return created;
        }

        private void finalizeOps(CacheFilterResult<ST> created) {
            created.res.forEach(sp -> {
                switch (sp) {
                    case CachingOperations.StreamCachePredicate.Any n ->
                            CACHED_RESULTS.computeIfAbsent((Class<? extends CachingOperations.CachedOperation<ST, ?>>) n.getClass(), k -> new CachingOperations.StreamCacheResult<>(n, false));
                    case CachingOperations.StreamCachePredicate.All p ->
                            CACHED_RESULTS.computeIfAbsent((Class<? extends CachingOperations.CachedOperation<ST, ?>>) p.getClass(), k -> new CachingOperations.StreamCacheResult<>(p, true));
                    default ->
                            log.debug("Skipped stream result: {}", sp);
                }
            });
        }

        @Override
        public void resetCache() {
            cached = false;
        }

        @Override
        public ConcurrentHashMap<Class<? extends CachingOperations.CachedOperation<ST, ?>>, CachingOperations.StreamCacheResult> CACHED_RESULTS() {
            return CACHED_RESULTS;
        }

        @Override
        public Class<? extends CachingOperations.StreamCacheOperation> provider() {
            return provider;
        }

        @Override
        public boolean isCached() {
            return cached;
        }



    }

    public StreamWrapper(StreamResultOptions options, Stream<ST> underlying,
                         Class<? extends CachingOperations.StreamCacheOperation> provider,
                         StreamWrapper<C, ST> other,
                         C res) {
        this.res = res;
        this.options = options;
        this.underlying = underlying;
        this.provider = provider;
        if (this.options.isInfinite())
            this.cached = new InfiniCache(provider);
        else this.cached = new StandardCache(provider);

        if (other != null)
            throw new RuntimeException("Not implemented yet");
    }

    public StreamWrapper(StreamResultOptions options, Stream<ST> underlying,
                         Class<? extends CachingOperations.StreamCacheOperation> provider,
                         C c) {
        this(options, underlying, provider, null, c);
    }

    public synchronized void swap(List<ST> to) {
        underlying = to.stream();
    }

    protected <T extends CachingOperations.CachedOperation<RE, V>, RE, V> OneResult<V, SingleError.StandardError> get(Class<T> t) {
        return cached.get(t);
    }

    protected <T extends CachingOperations.CachedOperation<RE, V>, RE, V> Result<V, SingleError.StandardError> get(TypeReferenceDelegate<T> t) {
        return cached.get(t);
    }

    public synchronized void resetCache() {
        this.cached.resetCache();
    }

     public synchronized boolean isAnyNonNull(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.IsAnyNonNull.class)
                .mapError(se -> {log.error("{}", se); return se;})
                .one()
                .get();
    }

    public synchronized boolean isCompletelyEmpty() {
        return isCompletelyEmpty(this.res);
    }

    public synchronized boolean isCompletelyEmpty(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.IsCompletelyEmpty.class)
                .mapError(se -> {log.error("{}", se); return se;})
                .one()
                .get();
    }

    public synchronized boolean hasAnyResult() {
        return this.hasAnyResult(this.res);
    }

    public synchronized boolean isAnyNonNull() {
        return this.isAnyNonNull(this.res);
    }

    public synchronized boolean hasAnyResult(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.HasResult.class)
                .mapError(se -> {log.error("{}", se); return se;})
                .one()
                .orElseRes(false);
    }

    public synchronized boolean hasAnyError(C streamResult) {
        Assert.notNull(streamResult, "streamResult must not be null");
        cacheResultsIfNotCached();

        return (boolean) get(CachingOperations.HasErr.class)
                .mapError(se -> {log.error("{}", se); return se;})
                .one()
                .orElseRes(false);
    }

    protected void cacheResultsIfNotCached() {
        cacheResultsIfNotCached(c -> {});
    }

    protected void cacheResultsIfNotCached(Consumer<? super ST> consumer) {
        if (!cached.isCached())
            this.cached.doCache(this.res, consumer);
    }

    public List<ST> cacheResultsIfNotCachedWithList(Consumer<? super ST> consumer) {
        if (!cached.isCached())
            return this.cached.cacheToList(this.res, consumer);

        throw new RuntimeException("Multiple terminal operations have been called.");
    }

    public synchronized List<ST> throwIfCachedOrCacheWithList(boolean infinite, Consumer<? super ST> consumer) {
        if (this.cached.isCached())
            throw new RuntimeException("Already cached!");

        return cacheResultsIfNotCachedWithList(consumer);
    }

    public synchronized List<ST> throwIfCachedOrCacheWithList(Consumer<? super ST> consumer) {
        return throwIfCachedOrCacheWithList(false, consumer);
    }

    public synchronized List<ST> throwIfCachedOrCacheWithList() {
        return cacheResultsIfNotCachedWithList(st -> {});
    }

    public synchronized void throwIfCachedOrCache(boolean infinite, Consumer<? super ST> consumer) {
        if (this.cached.isCached())
            throw new RuntimeException("Already cached!");

        cacheResultsIfNotCached(consumer);
    }

    public synchronized void throwIfCachedOrCache() {
        throwIfCachedOrCache(false, st -> {});
    }

    public synchronized void throwIfCachedOrCache(Consumer<? super ST> consumer) {
        throwIfCachedOrCache(false, consumer);
    }

    @Override
    public void close() {
        underlying.close();
    }

    @Override
    public boolean isParallel() {
        return underlying.isParallel();
    }

    @Override
    public Stream<ST> dropWhile(Predicate<? super ST> predicate) {
        return underlying.dropWhile(predicate);
    }

    @Override
    public List<ST> toList() {
        return throwIfCachedOrCacheWithList();
    }
    @Override
    public void forEachOrdered(Consumer<? super ST> action) {
        throwIfCachedOrCache(action);
    }

    @Override
    public void forEach(Consumer<? super ST> action) {
        throwIfCachedOrCache(action);
    }

}
