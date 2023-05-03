package www.ontologyutils.toolbox;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class implements a map of sets that can be queried based on subset and
 * superset relationships. This is a useful data structure for some caches.
 */
public class MapOfSets<K extends Comparable<? super K>, V> extends AbstractMap<Set<K>, V> {
    private static class TrieNode<K, V> {
        private Map<K, TrieNode<K, V>> children;
        public int size;
        public V data;

        /**
         * Remove all children of the node. This is only safe to be called on the root
         * node, or when the node contains no actual children.
         */
        public void clear() {
            children.clear();
            size = 0;
            data = null;
        }

        /**
         * @return The set of all children of this node.
         */
        public Set<Entry<K, TrieNode<K, V>>> children() {
            if (children != null) {
                return children.entrySet();
            } else {
                return Set.of();
            }
        }

        /**
         * Remove the child with the given key.
         *
         * @param key
         */
        public void removeChild(Object key) {
            if (children != null) {
                if (children.size() == 1) {
                    children = null;
                } else {
                    children.remove(key);
                }
            }
        }

        /**
         * @param key
         * @return The child at {@code key} in this node.
         */
        public TrieNode<K, V> getChild(Object key) {
            if (children != null) {
                return children.get(key);
            } else {
                return null;
            }
        }

        /**
         * Get the child node for {@code key}. If the child does not exist, create a new
         * child node.
         *
         * @param key
         * @return The child at {@code key}.
         */
        public TrieNode<K, V> getOrCreateChild(K key) {
            if (children == null) {
                children = new HashMap<>(1);
            }
            if (!children.containsKey(key)) {
                children.put(key, new TrieNode<>());
            }
            return children.get(key);
        }
    }

    private TrieNode<K, V> root;

    /**
     * Create a new empty map.
     */
    public MapOfSets() {
        root = new TrieNode<>();
    }

    @Override
    public void clear() {
        root.clear();
    }

    @Override
    public V get(Object key) {
        if (key instanceof Set) {
            var sorted = ((Set<?>) key).stream().sorted().iterator();
            var current = root;
            while (current != null && sorted.hasNext()) {
                current = current.getChild(sorted.next());
            }
            return current != null ? current.data : null;
        } else {
            return null;
        }
    }

    @Override
    public V put(Set<K> key, V value) {
        var sorted = key.stream().sorted().iterator();
        var path = new ArrayList<TrieNode<K, V>>();
        var current = root;
        while (sorted.hasNext()) {
            path.add(current);
            current = current.getOrCreateChild(sorted.next());
        }
        V oldData = current.data;
        current.data = value;
        if (oldData == null) {
            current.size += 1;
            for (var node : path) {
                node.size += 1;
            }
        }
        return oldData;
    }

    @Override
    public V remove(Object key) {
        if (key instanceof Set) {
            var sorted = ((Set<?>) key).stream().sorted().iterator();
            var path = new ArrayList<TrieNode<K, V>>();
            var current = root;
            while (current != null && sorted.hasNext()) {
                path.add(current);
                current = current.getChild(sorted.next());
            }
            if (current != null && current.data != null) {
                V data = current.data;
                current.size -= 1;
                current.data = null;
                Collections.reverse(path);
                for (var node : path) {
                    if (current.size == 0) {
                        node.removeChild(key);
                    }
                    node.size -= 1;
                    current = node;
                }
                return data;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public int size() {
        return root.size;
    }

    private void entrySetHelper(TrieNode<K, V> node, List<K> path, Set<Entry<Set<K>, V>> into) {
        if (node.data != null) {
            into.add(new SimpleEntry<>(Set.copyOf(path), node.data));
        }
        for (var entry : node.children()) {
            path.add(entry.getKey());
            entrySetHelper(entry.getValue(), path, into);
            path.remove(path.size() - 1);
        }
    }

    @Override
    public Set<Entry<Set<K>, V>> entrySet() {
        var result = new HashSet<Entry<Set<K>, V>>();
        entrySetHelper(root, new ArrayList<>(), result);
        return result;
    }

    /**
     * @param key
     * @return True iff any key in the map is a subset of {@code key}:
     */
    public boolean containsSubset(Set<K> key) {
        return getSubset(key) != null;
    }

    /**
     * @param key
     * @return True iff any key in the map is disjoint with {@code key}:
     */
    public boolean containsDisjoint(Set<K> key) {
        return getDisjoint(key) != null;
    }

    /**
     * @param key
     * @return True iff any key in the map is a superset of {@code key}:
     */
    public boolean containsSuperset(Set<K> key) {
        return getSuperset(key) != null;
    }

    private void entrySetForSubsetHelper(TrieNode<K, V> node, Set<K> key, List<K> path,
            Set<Entry<Set<K>, V>> into) {
        if (node.data != null) {
            into.add(new SimpleEntry<>(Set.copyOf(path), node.data));
        }
        for (var entry : node.children()) {
            if (key.contains(entry.getKey())) {
                path.add(entry.getKey());
                entrySetForSubsetHelper(entry.getValue(), key, path, into);
                path.remove(path.size() - 1);
            }
        }
    }

    /**
     * @param key
     * @return All entries for which the key is a subset of {@code key}.
     */
    public Set<Entry<Set<K>, V>> entrySetForSubsets(Set<K> key) {
        var result = new HashSet<Entry<Set<K>, V>>();
        entrySetForSubsetHelper(root, key, new ArrayList<>(), result);
        return result;
    }

    private void entrySetForSupersetHelper(TrieNode<K, V> node, List<K> key, int depth,
            List<K> path, Set<Entry<Set<K>, V>> into) {
        if (depth == key.size() && node.data != null) {
            into.add(new SimpleEntry<>(Set.copyOf(path), node.data));
        }
        for (var entry : node.children()) {
            int cmp = depth < key.size() ? entry.getKey().compareTo(key.get(depth)) : -1;
            if (cmp <= 0) {
                path.add(entry.getKey());
                entrySetForSupersetHelper(entry.getValue(), key, cmp == 0 ? depth + 1 : depth, path, into);
                path.remove(path.size() - 1);
            }
        }
    }

    /**
     * @param key
     * @return All entries for which the key is a superset of {@code key}.
     */
    public Set<Entry<Set<K>, V>> entrySetForSupersets(Set<K> key) {
        var sorted = key.stream().sorted().collect(Collectors.toList());
        var result = new HashSet<Entry<Set<K>, V>>();
        entrySetForSupersetHelper(root, sorted, 0, new ArrayList<>(), result);
        return result;
    }

    private Entry<Set<K>, V> getSubsetHelper(TrieNode<K, V> node, Set<K> key, List<K> path) {
        if (node.data != null) {
            return new SimpleEntry<>(Set.copyOf(path), node.data);
        }
        for (var entry : node.children()) {
            if (key.contains(entry.getKey())) {
                path.add(entry.getKey());
                var result = getSubsetHelper(entry.getValue(), key, path);
                if (result != null) {
                    return result;
                }
                path.remove(path.size() - 1);
            }
        }
        return null;
    }

    /**
     * @param key
     * @return Some entry for which the key is a subset of {@code key} or null if no
     *         such entry exists.
     */
    public Entry<Set<K>, V> getSubset(Set<K> key) {
        return getSubsetHelper(root, key, new ArrayList<>());
    }

    private Entry<Set<K>, V> getDisjointHelper(TrieNode<K, V> node, Set<K> key, List<K> path) {
        if (node.data != null) {
            return new SimpleEntry<>(Set.copyOf(path), node.data);
        }
        for (var entry : node.children()) {
            if (!key.contains(entry.getKey())) {
                path.add(entry.getKey());
                var result = getDisjointHelper(entry.getValue(), key, path);
                if (result != null) {
                    return result;
                }
                path.remove(path.size() - 1);
            }
        }
        return null;
    }

    /**
     * @param key
     * @return Some entry for which the key is disjoint with {@code key} or null if
     *         no such entry exists.
     */
    public Entry<Set<K>, V> getDisjoint(Set<K> key) {
        return getDisjointHelper(root, key, new ArrayList<>());
    }

    private Entry<Set<K>, V> getSupersetHelper(TrieNode<K, V> node, List<K> key, int depth,
            List<K> path) {
        if (depth == key.size() && node.data != null) {
            return new SimpleEntry<>(Set.copyOf(path), node.data);
        }
        for (var entry : node.children()) {
            int cmp = depth < key.size() ? entry.getKey().compareTo(key.get(depth)) : -1;
            if (cmp <= 0) {
                path.add(entry.getKey());
                var result = getSupersetHelper(entry.getValue(), key, cmp == 0 ? depth + 1 : depth, path);
                if (result != null) {
                    return result;
                }
                path.remove(path.size() - 1);
            }
        }
        return null;
    }

    /**
     * @param key
     * @return Some entry for which the key is a superset of {@code key} or null if
     *         no such entry exists.
     */
    public Entry<Set<K>, V> getSuperset(Set<K> key) {
        var sorted = key.stream().sorted().collect(Collectors.toList());
        return getSupersetHelper(root, sorted, 0, new ArrayList<>());
    }
}
