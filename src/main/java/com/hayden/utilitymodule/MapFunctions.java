package com.hayden.utilitymodule;

import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MapFunctions {

    public static <
            W,
            T extends Collection<? extends W>,
            M extends ConcurrentNavigableMap<K,T>,
            K extends Comparable<K>,
            CN extends ConcurrentNavigableMap<K,? super W>,
            EL extends List<CN>
            > EL ExpandMapEx(
            M starting,
            EL map,
            Supplier<CN> mapSupplier
    )
    {

        int counter = 0;
        for(var entry : starting.entrySet()){
            for (W w : entry.getValue()) {
                CN foundMap;
                if(counter >= map.size()) {
                    foundMap = mapSupplier.get();
                    map.add(foundMap);
                } else {
                    foundMap = map.get(counter);
                }
                foundMap.put(entry.getKey(), w);
                ++counter;
            }
            counter = 0;
        }
        return map;
    }


    public static <T,U,V extends Map<T,U>,L extends Collection<U>,F extends Map<T,L>> F Flatten(Collection<V> toFlatten, L finalCollection, F finalMap)
    {
        return toFlatten.stream()
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                                               () -> finalMap,
                                               Collectors.mapping(
                                                       Map.Entry::getValue,
                                                       Collectors.toCollection(() -> finalCollection)
                                               )
                         )
                );
    }

    public static <U,T,K extends Comparable<K>> Map<T, ConcurrentSkipListMap<K, U>> CollectMaps(List<Map<T, ConcurrentSkipListMap<K, U>>> values)
    {
        return values.stream()
                .reduce(new HashMap<>(), (a, b) -> {
                    if(b != null) {
                        b.forEach((key, val) -> {
                            a.compute(key, (mapKey, mapVal) -> {
                                if (mapVal == null) {
                                    mapVal = new ConcurrentSkipListMap<>();
                                }
                                mapVal.putAll(val);
                                return mapVal;
                            });
                        });
                    }
                    return a;
                });
    }

    private static <T, U> Map<String, ConcurrentSkipListMap<Date, U>> GetMap(
            Map.Entry<String, Collection<T>> assets,
            Function<T, Map<String, ConcurrentSkipListMap<Date, U>>> getText
    )
    {
        return CollectMaps(assets.getValue()
                .stream()
                .map(getText)
                .collect(Collectors.toList()));
    }

    private <T extends ConcurrentNavigableMap<Date,Collection<Collection<U>>>, U> List<Map<String, ConcurrentNavigableMap<Date,Collection<U>>>> Expand(Collection<Map<String, T>> values)
    {

        return values.stream()
                .map(map -> map.entrySet()
                        .stream()
                        .map(e1 -> Map.entry(e1.getKey(), e1.getValue().entrySet()
                                .stream()
                                .map(e2 -> Map.entry(e2.getKey(), (Collection<U>) CollectionFunctions.flattenCollection(e2.getValue())))
                                .collect(Collectors.toConcurrentMap(Map.Entry::getKey,Map.Entry::getValue, (v1,v2) -> v1, () -> {
                                    ConcurrentNavigableMap<Date,Collection<U>> col = new ConcurrentSkipListMap<>();
                                    return col;
                                }))
                        ))
                        .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue))
                ).collect(Collectors.toList());
    }

    public static <T,U,V extends Map<T,Z>, Z> Map<T,U> transformValue(V mapToTransform, Function<Map.Entry<T,Z>,Map.Entry<T,U>> transformation)
    {
        Map<T, U> tuConcurrentSkipListMap = CollectConcurrent(
                mapToTransform.entrySet()
                        .stream()
                        .map(entry ->transformation.apply(entry))
        );
        return tuConcurrentSkipListMap;
    }

    public static <
            OUTERKEY,
            INPUTMAP extends Map<OUTERKEY,INNERMAPCHANGE>,
            INNERMAPCHANGE extends Map<INNERKEY,TOCHANGE>,
            INNERKEY,
            TOCHANGE,
            CHANGETO,
            FINALMAP extends Map<INNERKEY,CHANGETO>,
            INNERMAPCHANGED extends Map<OUTERKEY, FINALMAP>
            >  INNERMAPCHANGED TransformInnerMap(INPUTMAP in, Function<Map.Entry<OUTERKEY,INNERMAPCHANGE>, Function<Map.Entry<INNERKEY,TOCHANGE>,Map.Entry<INNERKEY, CHANGETO>>> mapper
    )
    {
        Map<OUTERKEY, Map<INNERKEY, CHANGETO>> collect = in.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), transformValue(entry.getValue(), mapper.apply(entry))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return (INNERMAPCHANGED) collect;
    }


    public static <T,U,KeyBefore,ValueBefore> ConcurrentSkipListMap<T,U> CollectConcurrent(
            Stream<Map.Entry<KeyBefore,ValueBefore>> entryStream,
            Function<Map.Entry<KeyBefore, ValueBefore>,T> keyMapper,
            Function<Map.Entry<KeyBefore, ValueBefore>,U> valueMapper
    )
    {
        return entryStream.collect(Collectors.toConcurrentMap(keyMapper, valueMapper, (v1,v2) -> v1, ConcurrentSkipListMap::new));
    }

    public static <T,U> ConcurrentSkipListMap<T,U> CollectConcurrent(
            Stream<Map.Entry<T,U>> entryStream
    )
    {
        return CollectConcurrent(entryStream, new ConcurrentSkipListMap<>());
    }

    public static <T,U, MAP extends ConcurrentNavigableMap<T,U>> MAP CollectConcurrent(
            Stream<Map.Entry<T,U>> entryStream, MAP map
    )
    {
        return entryStream.collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue, (v1,v2) -> v1, () -> map));
    }

    public static <T,U> Map<T,U> CollectMap(
            Stream<Map.Entry<T,U>> entryStream
    )
    {
        return entryStream.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static <
            W,
            T extends Collection<? extends W>,
            M extends ConcurrentNavigableMap<K,T>,
            K extends Comparable<K>,
            CN extends ConcurrentNavigableMap<K,? super W>,
            EL extends List<CN>
            > EL ExpandMap(
            M starting,
            EL map,
            Supplier<CN> mapSupplier
    )
    {

        int counter = 0;
        for(var entry : starting.entrySet()){
            for (W w : entry.getValue()) {
                CN foundMap;
                if(counter >= map.size()) {
                    foundMap = mapSupplier.get();
                    map.add(foundMap);
                } else {
                    foundMap = map.get(counter);
                }
                foundMap.put(entry.getKey(), w);
                ++counter;
            }
            counter = 0;
        }
        return map;
    }

    public static <K,V> BiFunction<K, List<V>, List<V>> remapToList(V toAdd){
        return (key, oldValue) -> {
            if (oldValue == null) {
                oldValue = new ArrayList<>();
            }
            oldValue.add(toAdd);
            return oldValue;
        };
    }

    public static <K,V> BiFunction<K, Set<V>, Set<V>> remapToSet(V toAdd) {
        return (key, oldValue) -> {
            if (oldValue == null) {
                oldValue = new HashSet<V>();
            }
            oldValue.add(toAdd);
            return oldValue;
        };
    }


    /**
     * helper function to reduce in above - it reduces a map of items to collection to
     * the items that have the most quantity in collection
     * @param toReduce
     * @param <U>
     * @param <V>
     * @return
     */
    public static <U, V> Flux<V> reduceBy(Map<U, Collection<V>> toReduce){
        Map<U, Integer> magMap = new HashMap<>();
        toReduce.forEach((key, val) -> magMap.put(key, val.size()));
        int max = magMap.values().stream().max(Integer::compareTo).orElse(0);
        var toReturn = toReduce.entrySet().stream().filter(entry -> entry.getValue().size() == max).findFirst();
        return toReturn.map(uCollectionEntry -> Flux.fromIterable(uCollectionEntry.getValue())).orElseGet(Flux::empty);
    }

}
