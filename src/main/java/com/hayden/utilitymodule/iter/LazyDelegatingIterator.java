package com.hayden.utilitymodule.iter;

import java.util.Iterator;
import java.util.List;

public class LazyDelegatingIterator<T> implements Iterator<T> {

    Iterator<T> next;
    int childrenIndex = 0;
    final List<Iterator<T>> iterators;

    public LazyDelegatingIterator(List<Iterator<T>> iterators, Iterator<T> starting) {
        this.next = starting;
        this.iterators = iterators;
    }

    @Override
    public boolean hasNext() {
        if (next.hasNext()) {
            return true;
        } else if (hasNextChild(childrenIndex)) {
            incrementNext();
            return hasNext();
        } else {
            return false;
        }
    }

    @Override
    public T next() {
        if (next.hasNext()) {
            return next.next();
        } else {
            if (hasNextChild(childrenIndex)) {
                incrementNext();
                return next();
            } else {
                return null;
            }
        }
    }

    private boolean hasNextChild(int childrenIndex) {
        return childrenIndex + 1 < iterators.size();
    }

    private void incrementNext() {
        childrenIndex += 1;
        next = iterators.get(childrenIndex);
    }
}
