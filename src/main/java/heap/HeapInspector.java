package heap;

import java.util.ArrayList;
import java.util.List;

/** Written for the flight engine: looks inside the course's heap so the page can
 * draw it. The heap keeps its array and its hash table protected, which
 * means visible to other classes in this package, so this class lives here.
 * It only reads. */
public final class HeapInspector {

    private HeapInspector() { }

    /** Return up to limit values from the front of the heap's array. The
     * first is the root, the next two are its children, and so on. */
    public static <V, P extends Comparable<P>> List<V> values(Heap<V, P> heap, int limit) {
        List<V> values = new ArrayList<V>();
        for (int i = 0; i < heap.c.size() && i < limit; i++) {
            values.add(heap.c.get(i).value);
        }
        return values;
    }

    /** Return the priorities of the same entries values returns. */
    public static <V, P extends Comparable<P>> List<P> priorities(Heap<V, P> heap, int limit) {
        List<P> priorities = new ArrayList<P>();
        for (int i = 0; i < heap.c.size() && i < limit; i++) {
            priorities.add(heap.c.get(i).priority);
        }
        return priorities;
    }

    /** Return how many entries sit in each bucket of the hash table the heap
     * uses to find where a value is in its array. */
    public static <V, P extends Comparable<P>> int[] chainLengths(Heap<V, P> heap) {
        int[] lengths = new int[heap.map.buckets.length];
        for (int i = 0; i < lengths.length; i++) {
            for (HashTable<V, Integer>.Pair pair = heap.map.buckets[i]; pair != null; pair = pair.next) {
                lengths[i]++;
            }
        }
        return lengths;
    }
}
