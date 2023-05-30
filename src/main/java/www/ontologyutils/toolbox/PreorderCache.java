package www.ontologyutils.toolbox;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/**
 * Implements a simple cache for a preorder, i.e., a reflexive and transitive
 * relation.
 *
 * Loosely inspired by the approach presented in Shearer, R., &amp; Horrocks, I.
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

    private synchronized void assureExistence(T elem) {
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

    /**
     * @param pred
     *            The element known to not be a predecessor of {@code succ}.
     * @param succ
     *            The element known to not be a successor of {@code pred}.
     */
    protected synchronized void removePossibleSuccessors(T pred, T succ) {
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

    /**
     * @param pred
     *            The element known to be a predecessor of {@code succ}.
     * @param succ
     *            The element known to be a successor of {@code pred}.
     */
    protected synchronized void addKnownSuccessors(T pred, T succ) {
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
     *            The collection of all elements for which to setup the cache.
     */
    public void setupDomain(Collection<T> domain) {
        for (var elem : domain) {
            assureExistence(elem);
        }
    }

    /**
     * @param pred
     *            The element for which to find successors.
     * @return A stream of all known successors of {@code pred}.
     */
    public synchronized List<T> knownStrictSuccessors(T pred) {
        assureExistence(pred);
        return Utils.toList(knownSuccessors.get(pred).stream()
                .filter(succ -> !knownSuccessors.get(succ).contains(pred)
                        && !possibleSuccessors.get(succ).contains(pred)));
    }

    /**
     * @param pred
     *            The element for which to find successors.
     * @return A stream of all possible, but not known, successors of {@code pred}.
     */
    public synchronized List<T> possibleStrictSuccessors(T pred) {
        assureExistence(pred);
        return Utils.toList(Stream.concat(
                knownSuccessors.get(pred).stream()
                        .filter(succ -> !knownSuccessors.get(succ).contains(pred)
                                && possibleSuccessors.get(succ).contains(pred)),
                possibleSuccessors.get(pred).stream()
                        .filter(succ -> !knownSuccessors.get(succ).contains(pred))));
    }

    /**
     * @param succ
     *            The element for which to find predecessors.
     * @return A stream of all known predecessors of {@code succ}.
     */
    public synchronized List<T> knownStrictPredecessors(T succ) {
        assureExistence(succ);
        return Utils.toList(knownPredecessors.get(succ).stream()
                .filter(pred -> !knownPredecessors.get(pred).contains(succ)
                        && !possiblePredecessors.get(pred).contains(succ)));
    }

    /**
     * @param succ
     *            The element for which to find predecessors.
     * @return A stream of all possible, but not known, predecessors of
     *         {@code succ}.
     */
    public synchronized List<T> possibleStrictPredecessors(T succ) {
        assureExistence(succ);
        return Utils.toList(Stream.concat(
                knownPredecessors.get(succ).stream()
                        .filter(pred -> !knownPredecessors.get(pred).contains(succ)
                                && possiblePredecessors.get(pred).contains(succ)),
                possiblePredecessors.get(succ).stream()
                        .filter(pred -> !knownPredecessors.get(pred).contains(succ))));
    }

    /**
     * @param pred
     *            The possible predecessor.
     * @param succ
     *            The possible successor.
     * @return True if {@code pred} is a predecessor of {@code succ}.
     */
    public synchronized boolean isKnownSuccessor(T pred, T succ) {
        var set = knownSuccessors.get(pred);
        if (set == null) {
            return false;
        }
        return set.contains(succ);
    }

    /**
     * @param pred
     *            The possible predecessor.
     * @param succ
     *            The possible successor.
     * @return False if {@code pred} is not a predecessor of {@code succ}.
     */
    public synchronized boolean isPossibleSuccessor(T pred, T succ) {
        var set = possibleSuccessors.get(pred);
        if (set == null || !possibleSuccessors.containsKey(succ)) {
            return true;
        }
        return set.contains(succ);
    }

    /**
     * @param pred
     *            The possible predecessor.
     * @param succ
     *            The possible successor.
     * @param order
     *            The predicate to test with.
     * @return True if {@code pred} is a predecessor of {@code succ}.
     */
    protected boolean compute(T pred, T succ, BiPredicate<T, T> order) {
        return order.test(pred, succ);
    }

    /**
     * Get whether the relation contains the pair ({@code pred}, {@code succ}). If
     * the result is already known form the cached values it is returned
     * immediately, other wise {@code order} is called to find the result.
     *
     * @param pred
     *            The possible predecessor of {@code succ}.
     * @param succ
     *            The possible successor of {@code pred}.
     * @param order
     *            A function defining the relation to cache.
     * @return True iff the relation contains a connection from {@code pred} to
     *         {@code succ}.
     */
    public boolean computeIfAbsent(T pred, T succ, BiPredicate<T, T> order) {
        assureExistence(pred);
        assureExistence(succ);
        if (isKnownSuccessor(pred, succ)) {
            return true;
        } else if (!isPossibleSuccessor(pred, succ)) {
            return false;
        } else if (compute(pred, succ, order)) {
            addKnownSuccessors(pred, succ);
            return true;
        } else {
            removePossibleSuccessors(pred, succ);
            return false;
        }
    }

    /**
     * @param pred
     *            The predecessor of {@code succ}.
     * @param succ
     *            The successor of {@code pred}.
     * @return True iff the new information is consistent with what is already
     *         known.
     */
    public boolean assertSuccessor(T pred, T succ) {
        assureExistence(pred);
        assureExistence(succ);
        if (isKnownSuccessor(pred, succ)) {
            return true;
        } else if (!isPossibleSuccessor(pred, succ)) {
            return false;
        } else {
            addKnownSuccessors(pred, succ);
            return true;
        }
    }

    /**
     * @param pred
     *            The non-predecessor of {@code succ}.
     * @param succ
     *            The non-successor of {@code pred}.
     * @return True iff the new information is consistent with what is already
     *         known.
     */
    public boolean denySuccessor(T pred, T succ) {
        assureExistence(pred);
        assureExistence(succ);
        if (isKnownSuccessor(pred, succ)) {
            return false;
        } else if (!isPossibleSuccessor(pred, succ)) {
            return true;
        } else {
            removePossibleSuccessors(pred, succ);
            return true;
        }
    }

    /**
     * Wrap the given preorder {@code preorder} using a {@code PreorderCache}.
     *
     * @param <T>
     *            The domain over which the preorder is defined.
     * @param preorder
     *            The preorder that should be wrapped.
     * @return The wrapped preorder.
     */
    public static <T> BiPredicate<T, T> wrapPreorder(BiPredicate<T, T> preorder) {
        var cache = new PreorderCache<T>();
        return (pred, succ) -> {
            return cache.computeIfAbsent(pred, succ, preorder);
        };
    }
}
