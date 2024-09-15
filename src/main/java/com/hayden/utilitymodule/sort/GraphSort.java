package com.hayden.utilitymodule.sort;

import com.hayden.utilitymodule.MapFunctions;
import com.hayden.utilitymodule.proxies.ProxyUtil;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

@UtilityClass
public class GraphSort {

    public interface GraphSortable<SELF extends GraphSortable<SELF>> {

        default List<Class<? extends SELF>> dependsOn() {
            return new ArrayList<>();
        }

        default List<SELF> parseAllDeps(Map<Class<? extends SELF>, SELF> values) {
            return retrieve(new HashSet<>(), values);
        }

        default List<SELF> retrieve(Set<String> prev,
                                    Map<Class<? extends SELF>, SELF> r) {
            List<SELF> out = new ArrayList<>();
            prev.add(this.getClass().getName());
            var notSorted = retrieveRecursive((SELF) this, prev, r, out);
            var newSorted = new ArrayList<>(notSorted);
            newSorted.add((SELF) this);
            return newSorted;
        }

        private @NotNull List<SELF> retrieveRecursive(SELF selfG,
                                                      Set<String> prev,
                                                      Map<Class<? extends SELF>, SELF> r,
                                                      List<SELF> out) {
            return selfG.dependsOn()
                    .stream()
                    .peek(s -> {
                        if (prev.contains(s.getName())) {
                            throw new RuntimeException("Found cycle.");
                        }
                        prev.add(s.getName());
                    })
                    .map(r::get)
                    .flatMap(s -> s.retrieve(prev, r).stream())
                    .collect(Collectors.toCollection(() -> out));
        }

    }

    public class GraphSortAlgo<T extends GraphSortable<T>> {
        Map<Class<? extends T>, T> mappings;
        List<T> graphs;

        public GraphSortAlgo(List<T> mappings) {
            this.mappings = MapFunctions.CollectMap(mappings.stream()
                    .map(e -> Map.entry(getGraphClazz(e), e)));
            this.graphs = mappings;
        }


        public List<T> sort() {
            // Create a map to store the dependencies of each graph
            Map<Class<? extends T>, List<Class<? extends T>>> dependencies = new HashMap<>();
            // Create a set to keep track of visited graphs during DFS
            Set<Class<? extends T>> visited = new HashSet<>();
            // Create a set to keep track of graphs that are currently being visited (i.e., in the recursion stack)
            Set<Class<? extends T>> recursionStack = new HashSet<>();
            // Create a list to store the sorted graphs
            List<Class<? extends T>> sortedGraphs = new ArrayList<>();

            // Initialize the dependencies map
            for (T graph : graphs) {
                dependencies.put((Class<? extends T>) graph.getClass(), graph.dependsOn());
            }

            // Perform DFS on each unvisited graph
            for (T graph : graphs) {
                if (!visited.contains(graph.getClass())) {
                    // If a cycle is detected, throw an exception
                    if (dfs((Class<? extends T>) graph.getClass(), dependencies, visited, recursionStack, sortedGraphs)) {
                        throw new RuntimeException("Cycle detected in the graph");
                    }
                }
            }

            return sortedGraphs.stream().map(mappings::get).toList();
        }


        /**
         * Performs a depth-first search on the given graph.
         *
         * @param graph         Current graph being visited
         * @param dependencies  Map of graph dependencies
         * @param visited       Set of visited graphs
         * @param recursionStack Set of graphs in the recursion stack
         * @param sortedGraphs  List of sorted graphs
         * @return True if no cycle is detected, false otherwise
         */
        private boolean dfs(Class<? extends T> graph, Map<Class<? extends T>, List<Class<? extends T>>> dependencies,
                            Set<Class<? extends T>> visited, Set<Class<? extends T>> recursionStack, List<Class<? extends T>> sortedGraphs) {
            // Mark the current graph as visited and add it to the recursion stack
            visited.add(graph);
            recursionStack.add(graph);

            // Visit each dependency of the current graph
            for (Class<? extends T> dependency : dependencies.get(graph)) {
                // If the dependency is not visited, visit it
                if (!visited.contains(dependency)) {
                    if (!dfs(dependency, dependencies, visited, recursionStack, sortedGraphs)) {
                        return true;
                    }
                }
                // If the dependency is in the recursion stack, a cycle is detected
                else if (recursionStack.contains(dependency)) {
                    return true;
                }
            }

            // Remove the current graph from the recursion stack and add it to the sorted list
            recursionStack.remove(graph);
            sortedGraphs.add(graph);

            return false;
        }

        private static <T extends GraphSortable<T>> @NotNull Class<? extends T> getGraphClazz(T graph) {
            return (Class<? extends T>) graph.getClass();
        }

    }

    public static <T extends GraphSortable<T>> List<T> sort(List<T> graphs) {
        var evilProxy = graphs.stream().filter(ProxyUtil::isProxy).findAny();
        if (evilProxy.isPresent()) {
            throw new RuntimeException("Detected proxy in graph: %s."
                    .formatted(evilProxy.map(t -> t.getClass()).map(Object::toString).orElse(null)));
        }
        GraphSortAlgo<T> algo = new GraphSortAlgo<>(graphs);
        return algo.sort();
    }

}
