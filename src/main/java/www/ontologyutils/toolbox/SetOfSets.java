package www.ontologyutils.toolbox;

import java.util.*;
import java.util.stream.Stream;

/**
 * This class implements a set of sets that can be queried based on subset and
 * superset relationships. This is a simple convenience wrapper around
 * MapOfSets.
 */
public class SetOfSets<K extends Comparable<? super K>> extends AbstractSet<Set<K>> {
    private MapOfSets<K, Boolean> map;

    /**
     * Creates a new empty set.
     */
    public SetOfSets() {
        map = new MapOfSets<>();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean contains(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean add(Set<K> key) {
        return map.put(key, true) == null;
    }

    @Override
    public boolean remove(Object key) {
        return map.remove(key) != null;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Iterator<Set<K>> iterator() {
        return map.entrySet().stream().map(entry -> entry.getKey()).iterator();
    }

    /**
     * @param key
     * @return True iff the any element in this set is a subset of {@code key}.
     */
    public boolean containsSubset(Set<K> key) {
        return map.containsSubset(key);
    }

    /**
     * @param key
     * @return True iff the any element in this set is disjoint with {@code key}.
     */
    public boolean containsDisjoint(Set<K> key) {
        return map.containsDisjoint(key);
    }

    /**
     * @param key
     * @return True iff the any element in this set is a superset of {@code key}.
     */
    public boolean containsSuperset(Set<K> key) {
        return map.containsSuperset(key);
    }

    /**
     * @param key
     * @return A stream of all element in this set that are subsets of {@code key}.
     */
    public Stream<Set<K>> subsets(Set<K> key) {
        return map.entrySetForSubsets(key).stream().map(entry -> entry.getKey());
    }

    /**
     * @param key
     * @return A stream of all element in this set that are supersets of
     *         {@code key}.
     */
    public Stream<Set<K>> supersets(Set<K> key) {
        return map.entrySetForSupersets(key).stream().map(entry -> entry.getKey());
    }

    /**
     * @param key
     * @return Some entry which is a subset of {@code key} or null if no such entry
     *         exists.
     */
    public Set<K> getSubset(Set<K> key) {
        var result = map.getSubset(key);
        if (result != null) {
            return result.getKey();
        } else {
            return null;
        }
    }

    /**
     * @param key
     * @return Some entry which is disjoint with {@code key} or null if no such
     *         entry exists.
     */
    public Set<K> getDisjoint(Set<K> key) {
        var result = map.getDisjoint(key);
        if (result != null) {
            return result.getKey();
        } else {
            return null;
        }
    }

    /**
     * @param key
     * @return Some entry which is a superset of {@code key} or null if no such
     *         entry exists.
     */
    public Set<K> getSuperset(Set<K> key) {
        var result = map.getSuperset(key);
        if (result != null) {
            return result.getKey();
        } else {
            return null;
        }
    }
}
