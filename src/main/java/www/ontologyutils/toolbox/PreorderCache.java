package www.ontologyutils.toolbox;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/**
 * Implements a simple cache for a preorder, i.e., a reflexive and transitive
 * relation.
 *
 * Loosely inspired by the approach presented in Shearer, R., & Horrocks, I.
 * (2009). Exploiting partial information in taxonomy construction. In The
 * Semantic Web-ISWC 2009: 8th International Semantic Web Conference, ISWC 2009,
 * Chantilly, VA, USA, October 25-29, 2009. Proceedings 8 (pp. 569-584).
 * Springer Berlin Heidelberg.
 */
public class PreorderCache<T> {
    private Map<T, Set<T>> knownSuccessors;
    private Map<T, Set<T>> knownPredecessors;
    private Map<T, Set<T>> possibleSuccessors;
    private Map<T, Set<T>> possiblePredecessors;

    /**
     * Create a new empty cache.
     */
    public PreorderCache() {
        knownSuccessors = new HashMap<>();
        knownPredecessors = new HashMap<>();
        possibleSuccessors = new HashMap<>();
        possiblePredecessors = new HashMap<>();
    }

    private void assureExistence(T elem) {
        if (!knownSuccessors.containsKey(elem)) {
            var existing = new HashSet<>(knownSuccessors.keySet());
            knownSuccessors.put(elem, new HashSet<>(Set.of(elem)));
            knownPredecessors.put(elem, new HashSet<>(Set.of(elem)));
            for (var other : existing) {
                possiblePredecessors.get(other).add(elem);
                possibleSuccessors.get(other).add(elem);
            }
            possibleSuccessors.put(elem, new HashSet<>(existing));
            possiblePredecessors.put(elem, existing);
        }
    }

    private void removePossibleSuccessors(T pred, T succ) {
        if (possibleSuccessors.get(pred).remove(succ)) {
            possiblePredecessors.get(succ).remove(pred);
            for (var pred2 : Utils.toArray(knownSuccessors.get(pred))) {
                for (var succ2 : Utils.toArray(knownPredecessors.get(succ))) {
                    if (possibleSuccessors.get(pred2).remove(succ2)) {
                        possiblePredecessors.get(succ2).remove(pred2);
                    }
                }
            }
        }
    }

    private void addKnownSuccessors(T pred, T succ) {
        if (knownSuccessors.get(pred).add(succ)) {
            knownPredecessors.get(succ).add(pred);
            possibleSuccessors.get(pred).remove(succ);
            possiblePredecessors.get(succ).remove(pred);
            for (var pred2 : Utils.toArray(knownPredecessors.get(pred))) {
                for (var succ2 : Utils.toArray(knownSuccessors.get(succ))) {
                    if (knownSuccessors.get(pred2).add(succ2)) {
                        knownPredecessors.get(succ2).add(pred2);
                        possibleSuccessors.get(pred2).remove(succ2);
                        possiblePredecessors.get(succ2).remove(pred2);
                    }
                }
            }
            for (var succ2 : Utils.toArray(possibleSuccessors.get(succ))) {
                if (possibleSuccessors.get(succ).contains(succ2)) {
                    for (var succ3 : Utils.toArray(knownSuccessors.get(succ2))) {
                        if (!possibleSuccessors.get(pred).contains(succ3)
                                && !knownSuccessors.get(pred).contains(succ3)) {
                            possibleSuccessors.get(succ).remove(succ2);
                            possiblePredecessors.get(succ2).remove(succ);
                            break;
                        }
                    }
                }
            }
            for (var pred2 : Utils.toArray(possiblePredecessors.get(pred))) {
                if (possibleSuccessors.get(pred2).contains(pred)) {
                    for (var pred3 : Utils.toArray(knownPredecessors.get(pred2))) {
                        if (!possibleSuccessors.get(pred3).contains(succ)
                                && !knownSuccessors.get(pred3).contains(succ)) {
                            possibleSuccessors.get(pred2).remove(pred);
                            possiblePredecessors.get(pred).remove(pred2);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Create all entries for the elements in the given collection. It is valid to
     * not call this method beforehand, but then the possible successors and
     * possible predecessors will not contain these unseen elements.
     *
     * @param domain
     */
    public void setupDomain(Collection<T> domain) {
        for (var elem : domain) {
            assureExistence(elem);
        }
    }

    /**
     * Precompute the complete preorder for the given domain and relation.
     *
     * @param domain
     *            The domain for which to compute the order.
     * @param order
     *            The relation to be computed.
     */
    public void precomputeFor(Collection<T> domain, BiPredicate<T, T> order) {
        setupDomain(domain);
        for (var element : domain) {
            possibleSuccessors.get(element).stream()
                    .sorted((a, b) -> Integer.compare(knownPredecessors.get(b).size(), knownPredecessors.get(a).size()))
                    .forEach(elem -> {
                        computeIfAbsent(element, elem, order);
                    });
        }
    }

    /**
     * @param pred
     * @return A stream of all known successors of {@code pred}.
     */
    public Set<T> getKnownSuccessors(T pred) {
        assureExistence(pred);
        return knownSuccessors.get(pred);
    }

    /**
     * @param pred
     * @return A stream of all possible, but not known, successors of {@code pred}.
     */
    public Set<T> getPossibleSuccessors(T pred) {
        assureExistence(pred);
        return possibleSuccessors.get(pred);
    }

    /**
     * @param succ
     * @return A stream of all known predecessors of {@code succ}.
     */
    public Set<T> getKnownPredecessors(T succ) {
        assureExistence(succ);
        return knownPredecessors.get(succ);
    }

    /**
     * @param succ
     * @return A stream of all possible, but not known, predecessors of
     *         {@code succ}.
     */
    public Set<T> getPossiblePredecessors(T succ) {
        assureExistence(succ);
        return possiblePredecessors.get(succ);
    }

    /**
     * @param pred
     * @return A stream of all known successors of {@code pred}.
     */
    public Stream<T> knownStrictSuccessors(T pred) {
        assureExistence(pred);
        return knownSuccessors.get(pred).stream()
                .filter(succ -> !knownSuccessors.get(succ).contains(pred)
                        && !possibleSuccessors.get(succ).contains(pred));
    }

    /**
     * @param pred
     * @return A stream of all possible, but not known, successors of {@code pred}.
     */
    public Stream<T> possibleStrictSuccessors(T pred) {
        assureExistence(pred);
        return Stream.concat(
                knownSuccessors.get(pred).stream()
                        .filter(succ -> !knownSuccessors.get(succ).contains(pred)
                                && possibleSuccessors.get(succ).contains(pred)),
                possibleSuccessors.get(pred).stream()
                        .filter(succ -> !knownSuccessors.get(succ).contains(pred)));
    }

    /**
     * @param succ
     * @return A stream of all known predecessors of {@code succ}.
     */
    public Stream<T> knownStrictPredecessors(T succ) {
        assureExistence(succ);
        return knownPredecessors.get(succ).stream()
                .filter(pred -> !knownPredecessors.get(pred).contains(succ)
                        && !possiblePredecessors.get(pred).contains(succ));
    }

    /**
     * @param succ
     * @return A stream of all possible, but not known, predecessors of
     *         {@code succ}.
     */
    public Stream<T> possibleStrictPredecessors(T succ) {
        assureExistence(succ);
        return Stream.concat(
                knownPredecessors.get(succ).stream()
                        .filter(pred -> !knownPredecessors.get(pred).contains(succ)
                                && possiblePredecessors.get(pred).contains(succ)),
                possiblePredecessors.get(succ).stream()
                        .filter(pred -> !knownPredecessors.get(pred).contains(succ)));
    }

    /**
     * Get whether the relation contains the pair ({@code pred}, {@code succ}). If
     * the result is already known form the cached values it is returned
     * immediately, other wise {@code order} is called to find the result.
     *
     * @param pred
     * @param succ
     * @param order
     *            A function defining the relation to cache.
     * @return True iff the relation contains a connection from {@code pred} to
     *         {@code succ}.
     */
    public boolean computeIfAbsent(T pred, T succ, BiPredicate<T, T> order) {
        assureExistence(pred);
        assureExistence(succ);
        if (knownSuccessors.get(pred).contains(succ)) {
            return true;
        } else if (!possibleSuccessors.get(pred).contains(succ)) {
            return false;
        } else if (order.test(pred, succ)) {
            addKnownSuccessors(pred, succ);
            return true;
        } else {
            removePossibleSuccessors(pred, succ);
            return false;
        }
    }

    /**
     * Get whether the relation contains the pair ({@code pred}, {@code succ}). If
     * the result is already known form the cached values it is returned
     * immediately, other wise {@code order} is called to find the result. Unlike
     * {@code computeIfAbsent}, this method will not cache the result of the
     * computation.
     *
     * @param pred
     * @param succ
     * @param order
     *            A function defining the relation to cache.
     * @return True iff the relation contains a connection from {@code pred} to
     *         {@code succ}.
     */
    public boolean computeIfAbsentNoCache(T pred, T succ, BiPredicate<T, T> order) {
        if (knownSuccessors.containsKey(pred) && knownSuccessors.get(pred).contains(succ)) {
            return true;
        } else if (possibleSuccessors.containsKey(pred) && !possibleSuccessors.get(pred).contains(succ)) {
            return false;
        } else {
            return order.test(pred, succ);
        }
    }

    /**
     * Wrap the given preorder {@code preorder} using a {@code PreorderCache}.
     *
     * @param <K>
     * @param <V>
     * @param preorder
     * @return The wrapped preorder.
     */
    public static <T> BiPredicate<T, T> wrapPreorder(BiPredicate<T, T> preorder) {
        var cache = new PreorderCache<T>();
        return (pred, succ) -> {
            return cache.computeIfAbsent(pred, succ, preorder);
        };
    }
}