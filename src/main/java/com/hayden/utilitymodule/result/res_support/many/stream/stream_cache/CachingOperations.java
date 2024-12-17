package com.hayden.utilitymodule.result.res_support.many.stream.stream_cache;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.ok.Ok;

import java.util.function.Function;
import java.util.function.Predicate;

public interface CachingOperations {

    interface CachedOperation<T, U> extends Function<T, U> {}

    interface InfiniteOperation<T, U> extends CachedOperation<T, U> {}

    record StreamCacheResult<T, U>(CachedOperation<T, U> predicateType,
                                   U cachedResult) {}

    sealed interface StreamCacheOperation<T, U> extends Function<T, U>
            permits
                CachingOperations.ResultTyStreamWrapperOperation,
                CachingOperations.ResultStreamCacheOperation { }

    sealed interface ResultTyStreamWrapperOperation<T, U> extends StreamCacheOperation<T, U>
                        permits CachingOperations.ResultTyPredicate, CachingOperations.ResultTyStreamCacheFunction { }

    interface StreamCacheFunction<T, U> extends CachedOperation<T, U> {}

    sealed interface ResultStreamCacheOperation<T, U> extends StreamCacheOperation<T, U>
            permits CachingOperations.ResultStreamCacheFunction, CachingOperations.ResultStreamCachePredicate { }

    sealed interface ResultStreamCacheFunction<T, U> extends StreamCacheFunction<T, U>, ResultStreamCacheOperation<T, U>
            permits
            CachingOperations.RetrieveError,
            CachingOperations.RetrieveRes,
            CachingOperations.RetrieveFirstRes{ }

    sealed interface ResultTyStreamCacheFunction<T, U> extends StreamCacheFunction<T, U>, ResultTyStreamWrapperOperation<T, U>
            permits CachingOperations.RetrieveFirstTy { }

    record RetrieveFirstTy<T>() implements ResultTyStreamCacheFunction<T, T> {
        @Override
        public T apply(T teResult) {
            return teResult;
        }
    }

    record RetrieveError<T, E>() implements ResultStreamCacheFunction<Result<T, E>, Err<E>> {
        @Override
        public Err<E> apply(Result<T, E> teResult) {
            return teResult.e();
        }
    }

    record RetrieveRes<T, E>() implements ResultStreamCacheFunction<Result<T, E>, Ok<T>>{
        @Override
        public Ok<T> apply(Result<T, E> teResult) {
            return teResult.r();
        }
    }

    record RetrieveFirstRes<T, E>() implements ResultStreamCacheFunction<Result<T, E>, Result<T, E>>{
        @Override
        public Result<T, E> apply(Result<T, E> teResult) {
            return teResult;
        }
    }

    interface StreamCachePredicate<T> extends Predicate<T>, CachedOperation<T, Boolean>{

        non-sealed interface Any<T> extends CachingOperations.ResultTyPredicate<T>, CachingOperations.ResultStreamCachePredicate<T> {
            @Override
            default boolean test(T t) {
                return false;
            }

            @Override
            default Boolean apply(T t) {
                return this.test(t);
            }
        }

        non-sealed interface All<T> extends CachingOperations.ResultTyPredicate<T>, CachingOperations.ResultStreamCachePredicate<T> {
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

    sealed interface ResultTyPredicate<T> extends StreamCachePredicate<T>, ResultTyStreamWrapperOperation<T, Boolean>
            permits CachingOperations.IsAnyNonNull, CachingOperations.IsCompletelyEmpty, StreamCachePredicate.All, StreamCachePredicate.Any { }

    sealed interface ResultStreamCachePredicate<T> extends StreamCachePredicate<T>, ResultStreamCacheOperation<T, Boolean>
            permits CachingOperations.HasErr, CachingOperations.HasResult, CachingOperations.IsAnyNonNull, CachingOperations.IsCompletelyEmpty, StreamCachePredicate.All, StreamCachePredicate.Any { }

    interface PersistentCacheResult  {}


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
            if (o.r().isStream())
                throw new RuntimeException("Cannot call HasResult on IResultTy when IResultTy is StreamResult.");

            return o.isOk();
        }
    }

    record HasErr<R, E>() implements StreamCachePredicate.Any<Result<R, E>>, ResultStreamCachePredicate<Result<R, E>>, PersistentCacheResult {
        @Override
        public boolean test(Result<R, E> o) {
            if (o.e().isStream())
                throw new RuntimeException("Cannot call HasResult on IResultTy when IResultTy is StreamResult.");
            return o.isError();
        }
    }
}
