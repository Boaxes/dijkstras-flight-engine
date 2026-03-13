package graph;

import heap.Heap;

/** Written for the flight engine: someone who wants to follow a shortest-paths
 * search step by step. Every number passed is a heap priority: miles in the
 * ordinary search, and flights and miles combined in flight mode. */
public interface SearchListener {

    /** The search is about to begin. frontier holds only origin. */
    void started(Node origin, Heap<Node,Double> frontier);

    /** node has just left the frontier: its shortest path is final. */
    void settled(Node node, double priority, Heap<Node,Double> frontier);

    /** The search is looking at the edge from one node to a neighbor, which
     * would give the neighbor this priority. */
    void considered(Node from, Node node, double priority);

    /** The path just considered beats any known so far. node is new to the
     * frontier, or has moved up in it. */
    void reached(Node from, Node node, double priority, Heap<Node,Double> frontier);
}
