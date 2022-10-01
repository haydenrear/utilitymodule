package com.hayden.utilitymodule.scaling;

import lombok.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ScalingSlidingConcurrentMap<K extends Number,V extends Number> extends ConcurrentHashMap<K,V> implements ConcurrentMap<K,V> {

    private final ConcurrentHashMap<K,V> scaledMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K,V> scaledKeyMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K,V> scaledValueMap = new ConcurrentHashMap<>();

    AtomicReference<V> max;
    AtomicReference<V> min;

    AtomicBoolean isDirty;

    public void putWithScaler(K k, V v) {
        super.put(k, v);
    }

    @Override
    public boolean remove(Object key, Object value) {
        isDirty.set(true);
        return super.remove(key, value);
    }

    public V putIfAbsent(K key, V value) {
        if(max.get().doubleValue() < value.doubleValue()) {
            max.set(value);
        } else if (min.get().doubleValue() > value.doubleValue()) {
            min.set(value);
        }
        isDirty.set(true);
        return super.putIfAbsent(key, value);
    }

    public boolean replace(K key, V oldValue, V newValue) {
        isDirty.set(true);
        return super.replace(key, oldValue, newValue);
    }

    public V replace(K key, V value) {
        isDirty.set(true);
        return super.replace(key, value);
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        isDirty.set(true);
        super.replaceAll(function);
    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        V v = super.computeIfAbsent(key, mappingFunction);
        if(v != null)
            isDirty.set(true);
        return v;
    }

    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V v = super.computeIfPresent(key, remappingFunction);
        if(v != null)
            isDirty.set(true);
        return v;
    }

    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        isDirty.set(true);
        return super.compute(key, remappingFunction);
    }

    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        isDirty.set(true);
        return super.merge(key, value, remappingFunction);
    }
}
