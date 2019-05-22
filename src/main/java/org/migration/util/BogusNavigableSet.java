package org.migration.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;

/**
 * A lie. This class implements NavigableSet but does not follow its contract. This class is used as a holder for elements whose ordering
 * may not be accessible in the current context.
 * 
 * @param <E>
 *            The element type of the set
 */
public class BogusNavigableSet<E> implements NavigableSet<E> {
    private final ArrayList<E> theBacking = new ArrayList<>();

    @Override
    public Comparator<? super E> comparator() {
        return null;
    }

    @Override
    public E first() {
        return null;
    }

    @Override
    public E last() {
        return null;
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
    public boolean contains(Object o) {
        return theBacking.contains(o);
    }

    @Override
    public Object[] toArray() {
        return theBacking.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return theBacking.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return theBacking.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return theBacking.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return theBacking.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return theBacking.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return theBacking.retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return theBacking.removeAll(c);
    }

    @Override
    public void clear() {
        theBacking.clear();
    }

    @Override
    public E lower(E e) {
        return null;
    }

    @Override
    public E floor(E e) {
        return null;
    }

    @Override
    public E ceiling(E e) {
        return null;
    }

    @Override
    public E higher(E e) {
        return null;
    }

    @Override
    public E pollFirst() {
        return null;
    }

    @Override
    public E pollLast() {
        return null;
    }

    @Override
    public Iterator<E> iterator() {
        return theBacking.iterator();
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return null;
    }

    @Override
    public Iterator<E> descendingIterator() {
        return null;
    }

    @Override
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        return null;
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        return null;
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        return null;
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        return null;
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return null;
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return null;
    }
}
