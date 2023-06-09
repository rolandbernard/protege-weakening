package www.ontologyutils.toolbox;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.*;

/**
 * Contains a collection of utilities for computing minimal subsets that satisfy
 * a monotone predicate. Note that in contrast to
 * {@code MaximalConsistentSubsets} this does not aim to generate all minimal
 * subsets.
 *
 * The implementation is based on
 * Marques-Silva, Joao, Mikoláš Janota, and Anton Belov. "Minimal sets over
 * monotone predicates in boolean formulae." Computer Aided Verification: 25th
 * International Conference, CAV 2013, Saint Petersburg, Russia, July 13-19,
 * 2013. Proceedings 25. Springer Berlin Heidelberg, 2013.
 * and
 * Shchekotykhin, Kostyantyn, Dietmar Jannach, and Thomas Schmitz. "MergeXplain:
 * Fast computation of multiple conflicts for diagnosis." Twenty-Fourth
 * International Joint Conference on Artificial Intelligence. 2015.
 * and
 * Kalyanpur, A., Parsia, B., Horridge, M., &amp; Sirin, E. (2007). Finding all
 * justifications of OWL DL entailments. ISWC/ASWC, 4825, 267-280.
 */
public final class MinimalSubsets {
    private static class MinimalSubsetsResult<T> {
        public Set<T> invalid;
        public Set<Set<T>> results;

        public MinimalSubsetsResult(Set<T> invalid, Set<Set<T>> results) {
            this.invalid = invalid;
            this.results = results;
        }
    }

    /**
     * Prevents instantiation.
     */
    private MinimalSubsets() {
    }

    @SafeVarargs
    private static <T> Set<T> getUnion(Collection<T>... parts) {
        return Utils.toSet(Arrays.stream(parts).flatMap(c -> c.stream()));
    }

    private static <T> Set<T> getDifference(Collection<T> base, Collection<T> remove) {
        return Utils.toSet(base.stream().filter(c -> !remove.contains(c)));
    }

    private static <T> boolean isValidWithoutFirst(Collection<T> contained, List<T> search, int n,
            Predicate<Set<T>> isValid) {
        return isValid.test(getUnion(contained, search.subList(n + 1, search.size())));
    }

    private static <T> int firstRequiredForValidity(Collection<T> contained, List<T> search, int l, int r,
            Predicate<Set<T>> isValid) {
        while (l < r - 1) {
            int m = (l + r) / 2;
            if (isValidWithoutFirst(contained, search, m, isValid)) {
                l = m;
            } else {
                r = m;
            }
        }
        return r;
    }

    /**
     * Computes a single minimal subset of {@code set} that satisfies the
     * predicate together with {@code contained}. The predicate must be monotone.
     *
     * @param <T>
     *            The type of the set elements.
     * @param contained
     *            The set of axioms that must be included before testing.
     * @param set
     *            The set of axioms for which to find a minimal subset.
     * @param isValid
     *            The monotone predicate that should be satisfied by the subset.
     * @return A minimal subset that satisfies {@code isValid} or null if no such
     *         set exists.
     */
    public static <T> Set<T> getMinimalSubset(Collection<T> contained, Collection<T> set, Predicate<Set<T>> isValid) {
        if (!isValid.test(getUnion(contained, set))) {
            return null;
        }
        var all = List.copyOf(set);
        var fixed = new HashSet<T>(contained);
        var subset = new HashSet<T>();
        int position = 0;
        int size = 1;
        while (position < all.size()) {
            if (isValidWithoutFirst(fixed, all, position + size - 1, isValid)) {
                position += size;
                size = Integer.min(size * 2, all.size() - position);
            } else {
                int first = firstRequiredForValidity(fixed, all, position - 1, position + size - 1, isValid);
                fixed.add(all.get(first));
                subset.add(all.get(first));
                position = first + 1;
                size = 1;
            }
        }
        return subset;
    }

    /**
     * Computes a single minimal subset of {@code set} that satisfies the
     * predicate. The predicate must be monotone.
     *
     * @param <T>
     *            The type of the set elements.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return A minimal subset that satisfies {@code isValid} or null if no such
     *         set exists.
     */
    public static <T> Set<T> getMinimalSubset(Collection<T> set, Predicate<Set<T>> isValid) {
        return getMinimalSubset(Set.of(), set, isValid);
    }

    /**
     * Computes a single minimal subset of {@code set} that satisfies the
     * predicate. The predicate must be monotone. In contrast to
     * {@code getMinimalSubset} this method randomly shuffles the input set before
     * searching.
     *
     * @param <T>
     *            The type of the set elements.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return A minimal subset that satisfies {@code isValid}.
     */
    public static <T> Set<T> getRandomizedMinimalSubset(Collection<T> set, Predicate<Set<T>> isValid) {
        return getMinimalSubset(Utils.randomOrder(set), isValid);
    }

    private static <T> MinimalSubsetsResult<T> getMinimalSubsetsHelper(Set<T> contained, List<T> set,
            Predicate<Set<T>> isValid) {
        if (!isValid.test(getUnion(contained, set))) {
            return new MinimalSubsetsResult<T>(new HashSet<>(set), new HashSet<>());
        } else if (set.size() <= 1) {
            return new MinimalSubsetsResult<T>(new HashSet<>(), new HashSet<>(Set.of(new HashSet<>(set))));
        } else {
            var set1 = set.subList(0, set.size() / 2);
            var set2 = set.subList(set.size() / 2, set.size());
            var result1 = getMinimalSubsetsHelper(contained, set1, isValid);
            var result2 = getMinimalSubsetsHelper(contained, set2, isValid);
            while (isValid.test(getUnion(contained, result1.invalid, result2.invalid))) {
                var valid1 = getMinimalSubset(getUnion(contained, result2.invalid), result1.invalid, isValid);
                var valid2 = getMinimalSubset(getUnion(contained, valid1), result2.invalid, isValid);
                result1.results.add(getUnion(valid1, valid2));
                result1.invalid.remove(valid1.stream().findAny().get());
            }
            result1.invalid.addAll(result2.invalid);
            result1.results.addAll(result2.results);
            return result1;
        }
    }

    /**
     * Find a number of minimal subsets from {@code set} that together with
     * {@code contained} satisfy the monotone predicate {@code isValid}. Note that
     * this implementation is not designed to find all minimal sets.
     *
     * @param <T>
     *            The type of the set elements.
     * @param contained
     *            The set of axioms that must be included before testing.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return A set containing minimal sets.
     */
    public static <T> Set<Set<T>> getMinimalSubsets(Collection<T> contained, Collection<T> set,
            Predicate<Set<T>> isValid) {
        if (isValid.test(Set.copyOf(contained))) {
            return Set.of(Set.of());
        } else {
            return getMinimalSubsetsHelper(Set.copyOf(contained), List.copyOf(set), isValid).results;
        }
    }

    /**
     * Find a number of minimal subsets from {@code set} that satisfy the monotone
     * predicate {@code isValid}. Note that this implementation is not designed to
     * find all minimal sets.
     *
     * @param <T>
     *            The type of the set elements.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return A set containing minimal sets.
     */
    public static <T> Set<Set<T>> getMinimalSubsets(Collection<T> set, Predicate<Set<T>> isValid) {
        return getMinimalSubsets(Set.of(), set, isValid);
    }

    /**
     * @param <T>
     *            The type of the set elements.
     * @param set
     *            The set to find a subset for.
     * @param tries
     *            The number of times to reorder and compute subsets.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return A set containing minimal sets.
     */
    public static <T> Stream<Set<T>> randomizedMinimalSubsets(Collection<T> set, int tries, Predicate<Set<T>> isValid) {
        return IntStream.range(0, tries).mapToObj(i -> getMinimalSubsets(Utils.randomOrder(set), isValid))
                .flatMap(sets -> sets.stream()).distinct();
    }

    private static <T extends Comparable<? super T>> void addToSetOfSets(SetOfSets<T> sets, Set<T> set) {
        // We keep the hitting sets and prefix paths minimal to improve search
        // performance.
        for (var superset : (Iterable<Set<T>>) sets.supersets(set)::iterator) {
            sets.remove(superset);
        }
        sets.add(set);
    }

    private static <T extends Comparable<? super T>> void getAllMinimalSubsetsHelper(Collection<T> contained,
            Collection<T> set, Predicate<Set<T>> isValid, Set<T> path, SetOfSets<T> minimalSets,
            Set<Set<T>> hittingSets, SetOfSets<T> prefixPaths, Map<T, Integer> frequency) {
        if (prefixPaths.containsSubset(path)) {
            return;
        }
        Set<T> minimalSet = minimalSets.getDisjoint(path);
        if (minimalSet == null) {
            minimalSet = getMinimalSubset(contained, set, isValid);
            if (minimalSet == null) {
                var minimalHitting = getMinimalSubset(Set.of(), path,
                        s -> !isValid.test(getUnion(contained, set, getDifference(path, s))));
                addToSetOfSets(prefixPaths, minimalHitting);
                if (hittingSets != null) {
                    hittingSets.add(minimalHitting);
                }
                return;
            }
            minimalSets.add(minimalSet);
            for (var elem : minimalSet) {
                frequency.put(elem, frequency.getOrDefault(elem, 0) + 1);
            }
        }
        var sorted = minimalSet.stream()
                .sorted((a, b) -> frequency.get(b).compareTo(frequency.get(a)));
        for (var elem : (Iterable<T>) sorted::iterator) {
            set.remove(elem);
            path.add(elem);
            getAllMinimalSubsetsHelper(contained, set, isValid, path, minimalSets, hittingSets, prefixPaths, frequency);
            set.add(elem);
            path.remove(elem);
        }
        addToSetOfSets(prefixPaths, path);
    }

    /**
     * @param <T>
     *            The type of the set elements.
     * @param contained
     *            The set of axioms that must be included before testing.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return All minimal subsets that together with {@code contained} satisfy the
     *         monotone predicate {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Set<Set<T>> getAllMinimalSubsets(Collection<T> contained,
            Collection<T> set, Predicate<Set<T>> isValid) {
        if (isValid.test(Set.copyOf(contained))) {
            return Set.of(Set.of());
        } else {
            var minimalSets = new SetOfSets<T>();
            var prefixPaths = new SetOfSets<T>();
            var frequency = new HashMap<T, Integer>();
            getAllMinimalSubsetsHelper(Set.copyOf(contained), new HashSet<>(set), isValid, new HashSet<>(), minimalSets,
                    null, prefixPaths, frequency);
            return minimalSets;
        }
    }

    /**
     * @param <T>
     *            The type of the set elements.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return All minimal subsets that satisfy the monotone predicate
     *         {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Set<Set<T>> getAllMinimalSubsets(Collection<T> set,
            Predicate<Set<T>> isValid) {
        return getAllMinimalSubsets(Set.of(), set, isValid);
    }

    /**
     * @param <T>
     *            The type of the set elements.
     * @param contained
     *            The set of axioms that must be included before testing.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return All minimal hitting sets for the justifications of {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Set<Set<T>> getAllMinimalHittingSets(Collection<T> contained,
            Collection<T> set, Predicate<Set<T>> isValid) {
        if (isValid.test(Set.copyOf(contained))) {
            return Set.of(Set.of());
        } else {
            var minimalSets = new SetOfSets<T>();
            var prefixPaths = new SetOfSets<T>();
            var hittingSets = new HashSet<Set<T>>();
            var frequency = new HashMap<T, Integer>();
            getAllMinimalSubsetsHelper(Set.copyOf(contained), new HashSet<>(set), isValid, new HashSet<>(), minimalSets,
                    hittingSets, prefixPaths, frequency);
            return hittingSets;
        }
    }

    /**
     * @param <T>
     *            The type of the set elements.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return All minimal hitting sets for the justifications of {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Set<Set<T>> getAllMinimalHittingSets(Collection<T> set,
            Predicate<Set<T>> isValid) {
        return getAllMinimalHittingSets(Set.of(), set, isValid);
    }

    private static <T extends Comparable<? super T>> Set<T> allMinimalSubsetsHelper(Collection<T> contained,
            Collection<T> set, Predicate<Set<T>> isValid, Set<T> path, ArrayDeque<Map.Entry<T, Boolean>> queue,
            SetOfSets<T> minimalSets,
            SetOfSets<T> prefixPaths, Map<T, Integer> frequency) {
        Set<T> result = null;
        while (result == null && !queue.isEmpty()) {
            var frame = queue.removeLast();
            if (frame.getValue() != null) {
                if (frame.getValue()) {
                    addToSetOfSets(prefixPaths, path);
                    set.add(frame.getKey());
                    path.remove(frame.getKey());
                    continue;
                } else {
                    set.remove(frame.getKey());
                    path.add(frame.getKey());
                }
            }
            if (prefixPaths.containsSubset(path)) {
                continue;
            }
            Set<T> minimalSet = minimalSets.getDisjoint(path);
            if (minimalSet == null) {
                minimalSet = getMinimalSubset(contained, set, isValid);
                if (minimalSet == null) {
                    var minimalHitting = getMinimalSubset(Set.of(), path,
                            s -> !isValid.test(getUnion(contained, set, getDifference(path, s))));
                    addToSetOfSets(prefixPaths, minimalHitting);
                    continue;
                }
                minimalSets.add(minimalSet);
                for (var elem : minimalSet) {
                    frequency.put(elem, frequency.getOrDefault(elem, 0) + 1);
                }
                result = minimalSet;
            }
            var sorted = minimalSet.stream()
                    .sorted((a, b) -> frequency.get(b).compareTo(frequency.get(a)));
            for (var elem : (Iterable<T>) sorted::iterator) {
                queue.addLast(new AbstractMap.SimpleEntry<>(elem, true));
                queue.addLast(new AbstractMap.SimpleEntry<>(elem, false));
            }
        }
        return result;
    }

    /**
     * @param <T>
     *            The type of the set elements.
     * @param contained
     *            The set of axioms that must be included before testing.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return All minimal subsets that together with {@code contained} satisfy the
     *         monotone predicate {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Stream<Set<T>> allMinimalSubsets(Collection<T> contained,
            Collection<T> set, Predicate<Set<T>> isValid) {
        if (isValid.test(Set.copyOf(contained))) {
            return Stream.of(Set.of());
        } else {
            var minimalSets = new SetOfSets<T>();
            var prefixPaths = new SetOfSets<T>();
            var frequency = new HashMap<T, Integer>();
            var queue = new ArrayDeque<Map.Entry<T, Boolean>>();
            queue.add(new AbstractMap.SimpleEntry<>(null, null));
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<Set<T>>() {
                private Set<T> result;

                public boolean hasNext() {
                    if (result == null) {
                        result = allMinimalSubsetsHelper(Set.copyOf(contained), new HashSet<>(set), isValid,
                                new HashSet<>(), queue, minimalSets, prefixPaths, frequency);
                    }
                    return result != null;
                }

                @Override
                public Set<T> next() {
                    hasNext();
                    var next = result;
                    result = null;
                    return next;
                }
            }, Spliterator.NONNULL), false);
        }
    }

    /**
     * @param <T>
     *            The type of the set elements.
     * @param set
     *            The set to find a subset for.
     * @param isValid
     *            The monotone predicate that must be satisfied.
     * @return All minimal subsets that satisfy the monotone predicate
     *         {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Stream<Set<T>> allMinimalSubsets(Collection<T> set,
            Predicate<Set<T>> isValid) {
        return allMinimalSubsets(Set.of(), set, isValid);
    }
}
