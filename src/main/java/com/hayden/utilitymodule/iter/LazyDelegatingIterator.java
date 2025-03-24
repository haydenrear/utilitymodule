package com.hayden.utilitymodule.iter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class LazyDelegatingIterator<T> implements Iterator<T> {

    Iterator<T> next;
    int childrenIndex = 0;
    final List<? extends Iterator<T>> iterators;
    volatile boolean didIterate = false;

    public LazyDelegatingIterator(List<? extends Iterator<T>> iterators, Iterator<T> starting) {
        this.next = starting;
        this.iterators = iterators;
    }

    public LazyDelegatingIterator(Iterator<T> starting) {
        this(new ArrayList<>(), starting);
    }

    public void doOverAll(Consumer<T> c) {
        throwIfAlreadyIterated();
        while (next.hasNext()) {
            c.accept(next.next());
        }
        didIterate = true;
    }

    private void throwIfAlreadyIterated() {
        if (didIterate)
            throw new RuntimeException("Already iterated!");
    }

    @Override
    public boolean hasNext() {
        throwIfAlreadyIterated();
        if (next.hasNext()) {
            return true;
        } else if (hasNextChild(childrenIndex)) {
            incrementNext();
            return hasNext();
        } else {
            this.didIterate = true;
            return false;
        }
    }

    @Override
    public T next() {
        throwIfAlreadyIterated();
        if (next.hasNext()) {
            return next.next();
        } else {
            if (hasNextChild(childrenIndex)) {
                incrementNext();
                return next();
            } else {
                this.didIterate = true;
                return null;
            }
        }
    }

    private boolean hasNextChild(int childrenIndex) {
        if (iterators.isEmpty())
            return false;
        return childrenIndex < iterators.size();
    }

    private void incrementNext() {
        next = iterators.get(childrenIndex);
        childrenIndex += 1;
    }

    public boolean shortCircuitThisIterator() {
        if (hasNextChild(childrenIndex)) {
            incrementNext();
            return true;
        }

        return false;
    }
}
