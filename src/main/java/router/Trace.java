package router;

import java.util.ArrayList;
import java.util.List;

/** Records of what the algorithms did while answering a question, so the
 * page can replay them. Each is filled in as the real code runs. */
public final class Trace {

    private Trace() { }

    /** A merge sort that ranked some items by a number. */
    public static final class Sort {
        public String[] labels; // the items, in the order they started in
        public int[] keys; // the number each item was ranked by
        public int comparisons; // comparisons the sort made
        /** What happened, in order. A call to mergeSort is {0, start, end}.
         * A merge is {1, start, end} followed by which item sits in each
         * slot of the whole array afterwards. */
        public final List<int[]> events = new ArrayList<int[]>();
    }

    /** A search of the AVL tree for the words that start with a prefix. */
    public static final class TreeSearch {
        public String prefix;
        public final List<TreeNode> visits = new ArrayList<TreeNode>(); // each node looked at, once
        /** What happened, in order, as positions in visits. The first time a
         * node appears the search has arrived at it; a second time means its
         * word has just joined the result. */
        public final List<Integer> events = new ArrayList<Integer>();
    }

    /** One node of the AVL tree. */
    public static final class TreeNode {
        public String word;
        public int depth; // 0 for the root
        public int parent; // position of the node above in the same list, or -1
    }

    /** A shortest-paths search from one airport. */
    public static final class Search {
        public final List<Step> steps = new ArrayList<Step>();
        /** The frontier at the start, after each airport leaves it, and
         * after each change to it, in that order. */
        public final List<Snapshot> snapshots = new ArrayList<Snapshot>();
    }

    /** One airport leaving the frontier, and each neighbor then looked at. */
    public static final class Step {
        public String code;
        public double priority; // the priority it left the heap with
        public final List<String> neighbors = new ArrayList<String>();
        public final List<Double> priorities = new ArrayList<Double>(); // what each neighbor would get
        /** One character per neighbor: '0' no better than what was known,
         * '1' new to the frontier, '2' already there and moved up. */
        public final StringBuilder outcomes = new StringBuilder();
    }

    /** The frontier at one moment. */
    public static final class Snapshot {
        public List<String> heap; // the front of the heap's array
        public List<Double> priorities;
        public int size; // how many are in the heap altogether
        public int[] chains; // entries in each bucket of the heap's hash table
    }
}
