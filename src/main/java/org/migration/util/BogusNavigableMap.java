package org.migration.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;

/**
 * A lie. This class implements NavigableMap but does not follow the contract. This class is used as a holder for entries whose ordering may
 * not be accessible in the current context.
 * 
 * @param <K>
 *            The key type for the map
 * @param <V>
 *            The value type for the map
 */
public class BogusNavigableMap<K, V> implements NavigableMap<K, V> {
    private final Map<K, V> theBacking = new LinkedHashMap<>();

    @Override
    public Comparator<? super K> comparator() {
        return null;
    }

    @Override
    public K firstKey() {
        return null;
    }

    @Override
    public K lastKey() {
        return null;
    }

    @Override
    public Set<K> keySet() {
        return null;
    }

    @Override
    public Collection<V> values() {
        return theBacking.values();
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        return theBacking.entrySet();
    }

    @Override
    public int size() {
        return theBacking.size();
    }

    @Override
    public boolean isEmpty() {
        return theBacking.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return theBacking.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return theBacking.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return theBacking.get(key);
    }

    @Override
    public V put(K key, V value) {
        return theBacking.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return theBacking.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        theBacking.putAll(m);
    }

    @Override
    public void clear() {
        theBacking.clear();
    }

    @Override
    public java.util.Map.Entry<K, V> lowerEntry(K key) {
        return null;
    }

    @Override
    public K lowerKey(K key) {
        return null;
    }

    @Override
    public java.util.Map.Entry<K, V> floorEntry(K key) {
        return null;
    }

    @Override
    public K floorKey(K key) {
        return null;
    }

    @Override
    public java.util.Map.Entry<K, V> ceilingEntry(K key) {
        return null;
    }

    @Override
    public K ceilingKey(K key) {
        return null;
    }

    @Override
    public java.util.Map.Entry<K, V> higherEntry(K key) {
        return null;
    }

    @Override
    public K higherKey(K key) {
        return null;
    }

    @Override
    public java.util.Map.Entry<K, V> firstEntry() {
        return null;
    }

    @Override
    public java.util.Map.Entry<K, V> lastEntry() {
        return null;
    }

    @Override
    public java.util.Map.Entry<K, V> pollFirstEntry() {
        return null;
    }

    @Override
    public java.util.Map.Entry<K, V> pollLastEntry() {
        return null;
    }

    @Override
    public NavigableMap<K, V> descendingMap() {
        return null;
    }

    @Override
    public NavigableSet<K> navigableKeySet() {
        return null;
    }

    @Override
    public NavigableSet<K> descendingKeySet() {
        return null;
    }

    @Override
    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return null;
    }

    @Override
    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return null;
    }

    @Override
    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return null;
    }

    @Override
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        return null;
    }

    @Override
    public SortedMap<K, V> headMap(K toKey) {
        return null;
    }

    @Override
    public SortedMap<K, V> tailMap(K fromKey) {
        return null;
    }
}
