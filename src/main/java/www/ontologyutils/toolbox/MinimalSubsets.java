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
 * Kalyanpur, A., Parsia, B., Horridge, M., & Sirin, E. (2007). Finding all
 * justifications of OWL DL entailments. ISWC/ASWC, 4825, 267-280.
 */
public final class MinimalSubsets {
    private static class MinimalSubsetsResult<T> {
        public Set<T> invalid;
        public Set<Set<T>> results;

        public MinimalSubsetsResult(final Set<T> invalid, final Set<Set<T>> results) {
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
    private static <T> Set<T> getUnion(final Collection<T>... parts) {
        return Arrays.stream(parts).flatMap(c -> c.stream()).collect(Collectors.toSet());
    }

    private static <T> Set<T> getDifference(final Collection<T> base, final Collection<T> remove) {
        return base.stream().filter(c -> !remove.contains(c)).collect(Collectors.toSet());
    }

    private static <T> boolean isValidWithoutFirst(final Collection<T> contained, final List<T> search, int n,
            final Predicate<Set<T>> isValid) {
        return isValid.test(getUnion(contained, search.subList(n + 1, search.size())));
    }

    private static <T> int firstRequiredForValidity(final Collection<T> contained, final List<T> search, int l, int r,
            final Predicate<Set<T>> isValid) {
        while (l < r - 1) {
            final int m = (l + r) / 2;
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
     * @param contained
     * @param set
     * @param isValid
     * @return A minimal subset that satisfies {@code isValid} or null if no such
     *         set exists.
     */
    public static <T> Set<T> getMinimalSubset(final Collection<T> contained, final Collection<T> set,
            final Predicate<Set<T>> isValid) {
        if (!isValid.test(getUnion(contained, set))) {
            return null;
        }
        final var all = List.copyOf(set);
        final var fixed = new HashSet<T>(contained);
        final var subset = new HashSet<T>();
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
     * @param set
     * @param isValid
     * @return A minimal subset that satisfies {@code isValid} or null if no such
     *         set exists.
     */
    public static <T> Set<T> getMinimalSubset(final Collection<T> set, final Predicate<Set<T>> isValid) {
        return getMinimalSubset(Set.of(), set, isValid);
    }

    /**
     * Computes a single minimal subset of {@code set} that satisfies the
     * predicate. The predicate must be monotone. In contrast to
     * {@code getMinimalSubset} this method randomly shuffles the input set before
     * searching.
     *
     * @param set
     * @param isValid
     * @return A minimal subset that satisfies {@code isValid}.
     */
    public static <T> Set<T> getRandomizedMinimalSubset(final Collection<T> set, final Predicate<Set<T>> isValid) {
        return getMinimalSubset(Utils.randomOrder(set), isValid);
    }

    private static <T> MinimalSubsetsResult<T> getMinimalSubsetsHelper(final Set<T> contained, final List<T> set,
            final Predicate<Set<T>> isValid) {
        if (!isValid.test(getUnion(contained, set))) {
            return new MinimalSubsetsResult<T>(new HashSet<>(set), new HashSet<>());
        } else if (set.size() <= 1) {
            return new MinimalSubsetsResult<T>(new HashSet<>(), new HashSet<>(Set.of(new HashSet<>(set))));
        } else {
            final var set1 = set.subList(0, set.size() / 2);
            final var set2 = set.subList(set.size() / 2, set.size());
            final var result1 = getMinimalSubsetsHelper(contained, set1, isValid);
            final var result2 = getMinimalSubsetsHelper(contained, set2, isValid);
            while (isValid.test(getUnion(contained, result1.invalid, result2.invalid))) {
                final var valid1 = getMinimalSubset(getUnion(contained, result2.invalid), result1.invalid, isValid);
                final var valid2 = getMinimalSubset(getUnion(contained, valid1), result2.invalid, isValid);
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
     * @param contained
     * @param set
     * @param isValid
     * @return A set containing minimal sets.
     */
    public static <T> Set<Set<T>> getMinimalSubsets(final Collection<T> contained, final Collection<T> set,
            final Predicate<Set<T>> isValid) {
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
     * @param set
     * @param isValid
     * @return A set containing minimal sets.
     */
    public static <T> Set<Set<T>> getMinimalSubsets(final Collection<T> set, final Predicate<Set<T>> isValid) {
        return getMinimalSubsets(Set.of(), set, isValid);
    }

    /**
     * @param <T>
     * @param set
     * @param tries
     * @param isValid
     * @return A set containing minimal sets.
     */
    public static <T> Stream<Set<T>> randomizedMinimalSubsets(final Collection<T> set, final int tries,
            final Predicate<Set<T>> isValid) {
        return IntStream.range(0, tries).mapToObj(i -> getMinimalSubsets(Utils.randomOrder(set), isValid))
                .flatMap(sets -> sets.stream()).distinct();
    }

    private static <T extends Comparable<? super T>> void addToSetOfSets(final SetOfSets<T> sets, final Set<T> set) {
        // We keep the hitting sets and prefix paths minimal to improve search
        // performance.
        for (final var superset : (Iterable<Set<T>>) sets.supersets(set)::iterator) {
            sets.remove(superset);
        }
        sets.add(set);
    }

    private static <T extends Comparable<? super T>> void getAllMinimalSubsetsHelper(final Set<T> contained,
            final Collection<T> set, final Predicate<Set<T>> isValid, final Set<T> path, final SetOfSets<T> minimalSets,
            final Set<Set<T>> hittingSets, final SetOfSets<T> prefixPaths, final Map<T, Integer> frequency) {
        if (prefixPaths.containsSubset(path)) {
            return;
        }
        Set<T> minimalSet = minimalSets.getDisjoint(path);
        if (minimalSet == null) {
            minimalSet = getMinimalSubset(contained, List.copyOf(set), isValid);
            if (minimalSet == null) {
                final var minimalHitting = getMinimalSubset(Set.of(), List.copyOf(path),
                        s -> !isValid.test(getUnion(contained, set, getDifference(path, s))));
                addToSetOfSets(prefixPaths, minimalHitting);
                if (hittingSets != null) {
                    hittingSets.add(minimalHitting);
                }
                return;
            }
            minimalSets.add(minimalSet);
            for (final var elem : minimalSet) {
                frequency.put(elem, frequency.getOrDefault(elem, 0) + 1);
            }
        }
        final var sorted = minimalSet.stream()
                .sorted((a, b) -> frequency.get(b).compareTo(frequency.get(a)));
        for (final var elem : (Iterable<T>) sorted::iterator) {
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
     * @param contained
     * @param set
     * @param isValid
     * @return All minimal subsets that together with {@code contained} satisfy the
     *         monotone predicate {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Set<Set<T>> getAllMinimalSubsets(final Collection<T> contained,
            final Collection<T> set, final Predicate<Set<T>> isValid) {
        if (isValid.test(Set.copyOf(contained))) {
            return Set.of(Set.of());
        } else {
            final var minimalSets = new SetOfSets<T>();
            final var prefixPaths = new SetOfSets<T>();
            final var frequency = new HashMap<T, Integer>();
            getAllMinimalSubsetsHelper(Set.copyOf(contained), new HashSet<>(set), isValid, new HashSet<>(), minimalSets,
                    null, prefixPaths, frequency);
            return minimalSets;
        }
    }

    /**
     * @param <T>
     * @param set
     * @param isValid
     * @return All minimal subsets that satisfy the monotone predicate
     *         {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Set<Set<T>> getAllMinimalSubsets(final Collection<T> set,
            final Predicate<Set<T>> isValid) {
        return getAllMinimalSubsets(Set.of(), set, isValid);
    }

    /**
     * @param <T>
     * @param contained
     * @param set
     * @param isValid
     * @return All minimal hitting sets for the justifications of {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Set<Set<T>> getAllMinimalHittingSets(final Collection<T> contained,
            final Collection<T> set, final Predicate<Set<T>> isValid) {
        if (isValid.test(Set.copyOf(contained))) {
            return Set.of(Set.of());
        } else {
            final var minimalSets = new SetOfSets<T>();
            final var prefixPaths = new SetOfSets<T>();
            final var hittingSets = new HashSet<Set<T>>();
            final var frequency = new HashMap<T, Integer>();
            getAllMinimalSubsetsHelper(Set.copyOf(contained), new HashSet<>(set), isValid, new HashSet<>(), minimalSets,
                    hittingSets, prefixPaths, frequency);
            return hittingSets;
        }
    }

    /**
     * @param <T>
     * @param set
     * @param isValid
     * @return All minimal hitting sets for the justifications of {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Set<Set<T>> getAllMinimalHittingSets(final Collection<T> set,
            final Predicate<Set<T>> isValid) {
        return getAllMinimalHittingSets(Set.of(), set, isValid);
    }

    private static <T extends Comparable<? super T>> Set<T> allMinimalSubsetsHelper(final Collection<T> contained,
            final Collection<T> set, final Predicate<Set<T>> isValid, final ArrayDeque<Set<T>> queue,
            final SetOfSets<T> minimalSets, final SetOfSets<T> hittingSets, final Map<T, Integer> frequency) {
        Set<T> result = null;
        while (result == null && !queue.isEmpty()) {
            final var path = queue.pop();
            if (hittingSets.containsSubset(path)) {
                continue;
            }
            Set<T> minimalSet = minimalSets.getDisjoint(path);
            if (minimalSet == null) {
                minimalSet = getMinimalSubset(contained, getDifference(set, path), isValid);
                if (minimalSet == null) {
                    final var minimalHitting = getMinimalSubset(Set.of(), path,
                            s -> !isValid.test(getUnion(contained, getDifference(set, s))));
                    hittingSets.add(minimalHitting);
                    continue;
                }
                minimalSets.add(minimalSet);
                for (final var elem : minimalSet) {
                    frequency.put(elem, frequency.getOrDefault(elem, 0) + 1);
                }
                result = minimalSet;
            }
            final var sorted = minimalSet.stream()
                    .sorted((a, b) -> frequency.get(b).compareTo(frequency.get(a)));
            for (final var elem : (Iterable<T>) sorted::iterator) {
                queue.push(getUnion(path, Set.of(elem)));
            }
        }
        return result;
    }

    /**
     * @param <T>
     * @param contained
     * @param set
     * @param isValid
     * @return All minimal subsets that together with {@code contained} satisfy the
     *         monotone predicate {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Stream<Set<T>> allMinimalSubsets(final Collection<T> contained,
            final Collection<T> set, final Predicate<Set<T>> isValid) {
        if (isValid.test(Set.copyOf(contained))) {
            return Stream.of(Set.of());
        } else {
            final var minimalSets = new SetOfSets<T>();
            final var hittingSets = new SetOfSets<T>();
            final var frequency = new HashMap<T, Integer>();
            final var queue = new ArrayDeque<Set<T>>();
            queue.add(Set.of());
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<Set<T>>() {
                private Set<T> result;

                public boolean hasNext() {
                    if (result == null) {
                        result = allMinimalSubsetsHelper(contained, set, isValid, queue, minimalSets, hittingSets,
                                frequency);
                    }
                    return result != null;
                }

                @Override
                public Set<T> next() {
                    hasNext();
                    final var next = result;
                    result = null;
                    return next;
                }
            }, Spliterator.NONNULL), false);
        }
    }

    /**
     * @param <T>
     * @param set
     * @param isValid
     * @return All minimal subsets that satisfy the monotone predicate
     *         {@code isValid}.
     */
    public static <T extends Comparable<? super T>> Stream<Set<T>> allMinimalSubsets(final Collection<T> set,
            final Predicate<Set<T>> isValid) {
        return allMinimalSubsets(Set.of(), set, isValid);
    }
}
