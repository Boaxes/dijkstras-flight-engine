/** Author: Brandon Connely
 * Date: 3/12/26
 * Purpose: A4
 *
 * Enhancements:
 * 1. How to Use:
 * Use the program the same way as before via the CLI, but add
 * flightmode=true as one additional argument at the end when giving a
 * destination.
 * i.e. gradle run --args "db1b data/DB1BCoupon_2018_1.csv BLI BTV flightmode=true"
 * "The path with the least amount of flights is: BLI SAN CLT BTV (3 flights)
 * with a distance of: 3980.0 miles. This is 1 less flights than the shortest
 * route, but is 1526.0 miles longer."
 *
 * 2. How I Solved:
 * I believe the writing on the wall is clear that the idea was for us to
 * implement BFS to solve this, which directly addresses the "fewest-hops"
 * problem with flights. But since the assignment states that our
 * implementation is up to us, I decided to do the 'hacky' (but fully functional) approach of
 * just redoing Dijkstra's while treating every edge as the same weight.
 */
package graph;

import heap.Heap;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.io.File;
import java.io.FileNotFoundException;

/** Provides an implementation of Dijkstra's single-source shortest paths
 * algorithm.
 * Sample usage:
 *   Graph g = // create your graph
 *   ShortestPaths sp = new ShortestPaths();
 *   Node a = g.getNode("A");
 *   sp.compute(a);
 *   Node b = g.getNode("B");
 *   LinkedList<Node> abPath = sp.getShortestPath(b);
 *   double abPathLength = sp.getShortestPathLength(b);
 *   */
public class ShortestPaths {
    // stores auxiliary data associated with each node for the shortest
    // paths computation:
    private HashMap<Node,PathData> paths;

    // Added for the flight engine: if set, told about each step of a search so
    // the page can replay it. Null unless someone is watching.
    private SearchListener listener;

    /** Added for the flight engine: report each step of later searches to
     * listener, or to no one if listener is null. */
    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    /* Added for the flight engine: the heap priority of a path in flight mode.
     * One flight outweighs any trip's worth of miles, so the number of
     * flights decides and the miles only break ties. */
    private static double flightPriority(double flights, double miles) {
        return flights * 1000000.0 + miles;
    }

    /** Compute the shortest path to all nodes from origin using Dijkstra's
     * algorithm. Fill in the paths field, which associates each Node with its
     * PathData record, storing total distance from the source, and the
     * backpointer to the previous node on the shortest path.
     * Precondition: origin is a node in the Graph.*/
    public void compute(Node origin) {
        // F = frontier, f = current frontier node, w = neighbor, d = distance, bp = backpointer.
        paths = new HashMap<Node,PathData>(); // S = { }; (implicit per design decision bullet point 3)
        Heap<Node,Double> F = new Heap<Node,Double>();
        paths.put(origin, new PathData(0.0, null)); // v.d = 0; v.bp = null;
        F.add(origin, 0.0); // F = {v};
        if (listener != null) { listener.started(origin, F); } // added for the flight engine

        while (F.size() > 0) { // while (F != {})
            Node f = F.poll(); // f = node in F with min d value;
            PathData fData = paths.get(f);
            if (listener != null) { listener.settled(f, fData.d, F); } // added for the flight engine

            for (Map.Entry<Node,Double> edge : f.getNeighbors().entrySet()) { // for each neighbor w of f
                Node w = edge.getKey();
                double newDistance = fData.d + edge.getValue();
                if (listener != null) { listener.considered(f, w, newDistance); } // added for the flight engine

                if (!paths.containsKey(w)) { // if (w not in S or F)
                    paths.put(w, new PathData(newDistance, f)); // w.d = f.d + weight(f, w); w.bp = f;
                    F.add(w, newDistance); // add w to F;
                    if (listener != null) { listener.reached(f, w, newDistance, F); } // added for the flight engine
                }

                else if (F.contains(w) && newDistance < paths.get(w).d) { // else if (f.d + weight(f,w) < w.d)
                    paths.get(w).d = newDistance; // w.d = f.d + weight(f,w);
                    paths.get(w).bp = f; // w.bp = f;
                    F.changePriority(w, newDistance);
                    if (listener != null) { listener.reached(f, w, newDistance, F); } // added for the flight engine
                }
            }
        }
    }

    /** Compute the path with the fewest flights to all nodes from origin by
     * treating every edge as having the same cost. Fill in the paths field,
     * which associates each Node with its PathData record, storing total
     * flights from the source, and the backpointer to the previous node on the
     * least-flights path.
     * Precondition: origin is a node in the Graph.
     * Postcondition: paths stores least-flights data from origin to every
     * reachable node. */
    public void computeFlightMode(Node origin) {
        // F = frontier, f = current frontier node, w = neighbor, d = flights, bp = backpointer.
        final double flightCost = 1.0; // replacement for every weight in the graph for flightMode
        paths = new HashMap<Node,PathData>(); // S = { }; (implicit per design decision bullet point 3)
        Heap<Node,Double> F = new Heap<Node,Double>();
        paths.put(origin, new PathData(0.0, null)); // v.d = 0; v.bp = null;
        F.add(origin, 0.0); // F = {v};
        if (listener != null) { listener.started(origin, F); } // added for the flight engine

        while (F.size() > 0) { // while (F != {})
            Node f = F.poll(); // f = node in F with min d value;
            PathData fData = paths.get(f);
            if (listener != null) { listener.settled(f, flightPriority(fData.d, fData.miles), F); } // added for the flight engine

            // Changed for the flight engine: among routes with the same number of
            // flights, the one with fewer miles now wins. Before, whichever
            // was found first was kept. d still counts flights; miles rides
            // along beside it and only settles ties.
            for (Map.Entry<Node,Double> edge : f.getNeighbors().entrySet()) { // for each neighbor w of f
                Node w = edge.getKey();
                double newDistance = fData.d + flightCost;
                double newMiles = fData.miles + edge.getValue();
                if (listener != null) { listener.considered(f, w, flightPriority(newDistance, newMiles)); } // added for the flight engine

                if (!paths.containsKey(w)) { // if (w not in S or F)
                    PathData wData = new PathData(newDistance, f); // w.d = f.d + flightCost; w.bp = f;
                    wData.miles = newMiles;
                    paths.put(w, wData);
                    F.add(w, flightPriority(newDistance, newMiles)); // add w to F;
                    if (listener != null) { listener.reached(f, w, flightPriority(newDistance, newMiles), F); } // added for the flight engine
                }

                else if (F.contains(w) && (newDistance < paths.get(w).d // else if (f.d + flightCost < w.d)
                        || (newDistance == paths.get(w).d && newMiles < paths.get(w).miles))) {
                    paths.get(w).d = newDistance; // w.d = f.d + flightCost;
                    paths.get(w).miles = newMiles;
                    paths.get(w).bp = f; // w.bp = f;
                    F.changePriority(w, flightPriority(newDistance, newMiles));
                    if (listener != null) { listener.reached(f, w, flightPriority(newDistance, newMiles), F); } // added for the flight engine
                }
            }
        }
    }

    /** Returns the length of the shortest path from the origin to destination.
     * If no path exists, return Double.POSITIVE_INFINITY.
     * Precondition: destination is a node in the graph, and compute(origin) or
     * computeFlightMode(origin) has been called. */
    public double shortestPathLength(Node destination) {
        if (!paths.containsKey(destination)) {
            return Double.POSITIVE_INFINITY;
        }
        return paths.get(destination).d;
    }

    /** Returns a LinkedList of the nodes along the shortest path from origin
     * to destination. This path includes the origin and destination. If origin
     * and destination are the same node, it is included only once.
     * If no path to it exists, return null.
     * Precondition: destination is a node in the graph, and compute(origin) or
     * computeFlightMode(origin) has been called. */
    public LinkedList<Node> shortestPath(Node destination) {
        if (!paths.containsKey(destination)) {
            return null;
        }

        LinkedList<Node> result = new LinkedList<Node>();
        Node current = destination;

        while (current != null) {
            result.addFirst(current);
            current = paths.get(current).bp;
        }

        return result;
    }

    /** Inner class representing data used by Dijkstra's algorithm in the
     * process of computing shortest paths from a given source node. */
    class PathData {
        double d; // distance of the shortest path from source
        Node bp; // previous node in the path from the source
        double miles; // added for the flight engine: miles so far, kept in flight mode only

        /** constructor: initialize distance and previous node */
        public PathData(double dist, Node prev) {
            d = dist;
            bp = prev;
        }
    }


    /** Static helper method to open and parse a file containing graph
     * information. Can parse either a basic file or a DB1B CSV file with
     * flight data. See GraphParser, BasicParser, and DB1BParser for more.*/
    protected static Graph parseGraph(String fileType, String fileName) throws
        FileNotFoundException {
        // create an appropriate parser for the given file type
        GraphParser parser;
        if (fileType.equals("basic")) {
            parser = new BasicParser();
        } else if (fileType.equals("db1b")) {
            parser = new DB1BParser();
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + fileType);
        }

        // open the given file
        parser.open(new File(fileName));

        // parse the file and return the graph
        return parser.parse();
    }

    public static void main(String[] args) {
      // read command line args
      String fileType = args[0];
      String fileName = args[1];
      String origCode = args[2];

      String destCode = null;
      boolean flightMode = false;
      for (int i = 3; i < args.length; i++) {
          if (args[i].equalsIgnoreCase("flightmode=true")) {
              flightMode = true;
          }
          else if (args[i].equalsIgnoreCase("flightmode=false")) {
              flightMode = false;
          }
          else if (destCode == null) {
              destCode = args[i];
          }
      }

      // parse a graph with the given type and filename
      Graph graph;
      try {
          graph = parseGraph(fileType, fileName);
      } catch (FileNotFoundException e) {
          System.out.println("Could not open file " + fileName);
          return;
      }
      graph.report();


      // Create a ShortestPaths object, use it to compute shortest paths data from the origin node given by origCode.
      Node origin = graph.getNode(origCode);
      ShortestPaths sp = new ShortestPaths();
      if (flightMode) {
          sp.computeFlightMode(origin);
      }
      else {
          sp.compute(origin);
      }

      // If destCode was not given, print each reachable node followed by the length of the shortest path to it from the origin.
      if (destCode == null) {
          System.out.println();
          if (flightMode) {
              System.out.println("Least-flight paths from " + origCode + ":");
          }
          else {
              System.out.println("Shortest paths from " + origCode + ":");
          }
          for (Node node : graph.getNodes().values()) {
              if (!node.equals(origin) && sp.shortestPathLength(node) != Double.POSITIVE_INFINITY) {
                  System.out.println(node + ": " + sp.shortestPathLength(node));
              }
          }
          System.out.println(origin + ": " + sp.shortestPathLength(origin));
      }

      // If destCode was given, print the nodes in the path from origCode to destCode, followed by the total path length.  If no path exists, print a message saying so.
      else {
          System.out.println();
          Node destination = graph.getNode(destCode);
          LinkedList<Node> path = sp.shortestPath(destination);

          if (path == null) {
              System.out.println("No path from " + origCode + " to " + destCode);
          }
          else if (flightMode) {
              ShortestPaths shortestMiles = new ShortestPaths();
              shortestMiles.compute(origin);
              LinkedList<Node> shortestMilesPath = shortestMiles.shortestPath(destination);
              double flightModeMiles = 0.0;

              for (int i = 0; i < path.size() - 1; i++) {
                  flightModeMiles += path.get(i).getNeighbors().get(path.get(i + 1));
              }

              System.out.print("The path with the least amount of flights is: ");
              for (Node node : path) {
                  System.out.print(node + " ");
              }
              System.out.println("(" + (path.size() - 1) + " flights) with a distance of: "
                      + flightModeMiles + " miles.");
              System.out.println("This is " + (shortestMilesPath.size() - path.size())
                      + " less flights than the shortest route, but is "
                      + (flightModeMiles - shortestMiles.shortestPathLength(destination))
                      + " miles longer.");
          }
          else {
              for (Node node : path) {
                  System.out.print(node + " ");
              }
              System.out.println(sp.shortestPathLength(destination));
          }
      }
    }
}
