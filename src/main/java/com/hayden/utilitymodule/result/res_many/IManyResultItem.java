package com.hayden.utilitymodule.result.res_many;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public interface IManyResultItem<R> extends IResultItem<R> {

    Logger log = LoggerFactory.getLogger(IManyResultItem.class);

    default List<R> toList() {
        return stream().toList();
    }

    <V> IManyResultItem<V> flatMap(Function<R, IResultItem<V>> toMap);

    @Override
    default Optional<R> firstOptional(boolean keepAll) {
        return getFirst(keepAll);
    }

    /**
     * Forfeits operations
     * @param r
     * @return
     */
    IManyResultItem<R> add(R r);

    /**
     * Forfeits operations
     * @param r
     * @return
     */
    IManyResultItem<R> concat(IManyResultItem<R> r);

    // TODO: remove into res_single?
    @Override
    default Optional<R> firstOptional() {
        return getFirst(true);
    }

    private @NotNull Optional<R> getFirst(boolean keepAll) {
        var l = Lists.newArrayList(toList());
        if (l.size() > 1) {
            log.error("Called optional on stream result with more than one value. Returning first.");
        }

        if (keepAll)
            swap(l);

        if (l.size() != 1 && !l.isEmpty())
            log.warn("Called get first on many result and discarded {} results.", l.size() - 1);

        return !l.isEmpty() ? Optional.of(l.getFirst()) : Optional.empty();
    }

    boolean has(Predicate<R> e);

    void swap(List<R> toSwap);

}
