package router;

import avl.AVL;
import graph.DB1BParser;
import graph.Graph;
import graph.Node;
import graph.SearchListener;
import graph.ShortestPaths;
import heap.Heap;
import heap.HeapInspector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sort.Sorts;

/** Answers questions about a network of flights: the route between two
 * airports, which airports match what someone has typed, and the busiest
 * routes out of an airport.
 *
 * It does none of the algorithmic work itself. Routes come from
 * ShortestPaths, typed-prefix lookup from the AVL tree and ranking from
 * Sorts. What it adds is a record of what each of them did, for the page to
 * replay. */
public final class RoutePlanner {

    /** How much of the front of the heap a snapshot records. Fifteen is the
     * top four levels of the tree. */
    static final int HEAP_SHOWN = 15;

    /** One airport in the network. */
    public static final class Airport {
        public final String code;
        public String name = "";
        public String city = "";
        public String region = "";
        public double lat;
        public double lon;
        public boolean located; // true if lat and lon are known
        public int departures; // sampled tickets flying out of this airport

        Airport(String code) {
            this.code = code;
        }
    }

    /** One flight between two airports. */
    public static final class Leg {
        public final String from;
        public final String to;
        public final double miles;
        public final int count; // sampled tickets that flew this leg

        Leg(String from, String to, double miles, int count) {
            this.from = from;
            this.to = to;
            this.miles = miles;
            this.count = count;
        }
    }

    /** A sequence of flights from one airport to another. */
    public static final class Route {
        public final List<Leg> legs;
        public final double miles;
        public final Trace.Search search; // how it was found, if that was recorded

        Route(List<Leg> legs, Trace.Search search) {
            this.legs = legs;
            this.search = search;
            double total = 0;
            for (Leg leg : legs) {
                total += leg.miles;
            }
            this.miles = total;
        }
    }

    /** The airports matching what someone typed. */
    public static final class Suggestions {
        public final List<String> codes = new ArrayList<String>();
        public Trace.TreeSearch tree; // how the candidates were found
    }

    /** The flights out of an airport, busiest first. */
    public static final class Busiest {
        public final List<Leg> flights = new ArrayList<Leg>();
        public Trace.Sort sort; // how they were ranked
    }

    private final Graph graph;
    private final String[] codes; // every airport code, in alphabetical order
    private final Map<String, Airport> airports = new HashMap<String, Airport>();
    private final Map<String, Integer> counts = new HashMap<String, Integer>();
    private final AVL searchIndex = new AVL();
    private int routeCount;

    /** Build a planner from the collapsed flight file (Origin,Dest,Distance and
     * an optional Count column) and the airport file (tab-separated code,
     * name, city, region, latitude, longitude). The airport file may be
     * empty, and may leave airports out. */
    public RoutePlanner(String edgesCsv, String airportsTsv) {
        graph = new DB1BParser().parseText(edgesCsv);

        // An in-order walk of an AVL tree hands the codes back sorted.
        AVL sorted = new AVL();
        for (String code : graph.getNodes().keySet()) {
            sorted.avlInsert(code);
        }
        codes = sorted.withPrefix("", Integer.MAX_VALUE).toArray(new String[0]);
        for (String code : codes) {
            airports.put(code, new Airport(code));
        }

        readCounts(edgesCsv);
        readAirports(airportsTsv);
        buildSearchIndex();
    }

    /** Return every airport, in alphabetical order of code. */
    public List<Airport> airports() {
        List<Airport> all = new ArrayList<Airport>();
        for (String code : codes) {
            all.add(airports.get(code));
        }
        return all;
    }

    /** Return the airport with this code, or null if there is none. */
    public Airport airport(String code) {
        return airports.get(code);
    }

    /** Return the number of one-way routes in the network. */
    public int routeCount() {
        return routeCount;
    }

    /** Return the number of entries in the tree that typed text is looked
     * up in. */
    public int searchTerms() {
        return searchIndex.getSize();
    }

    /** Return the top levels of that tree: each node above the given depth,
     * parents before children. */
    public List<Trace.TreeNode> treeTop(int levels) {
        List<Trace.TreeNode> top = new ArrayList<Trace.TreeNode>();
        collect(searchIndex.root, 0, -1, levels, top);
        return top;
    }

    /** Return the route from one airport to another: the fewest flights if
     * fewestFlights is true (and the fewest miles among those), otherwise
     * the fewest miles. Return null if no route exists.
     * @throws IllegalArgumentException if either code is not an airport. */
    public Route route(String from, String to, boolean fewestFlights) {
        return route(from, to, fewestFlights, false);
    }

    /** As route, and if traced is true the route carries a record of the
     * search that found it. */
    public Route route(String from, String to, boolean fewestFlights, boolean traced) {
        requireAirport(from);
        requireAirport(to);

        Node origin = graph.getNode(from);
        ShortestPaths paths = new ShortestPaths();
        Trace.Search search = traced ? new Trace.Search() : null;
        if (traced) {
            paths.setListener(recorder(search));
        }
        if (fewestFlights) {
            paths.computeFlightMode(origin);
        } else {
            paths.compute(origin);
        }

        LinkedList<Node> stops = paths.shortestPath(graph.getNode(to));
        if (stops == null) {
            return null;
        }

        List<Leg> legs = new ArrayList<Leg>();
        Node previous = null;
        for (Node stop : stops) {
            if (previous != null) {
                legs.add(leg(previous, stop));
            }
            previous = stop;
        }
        return new Route(legs, search);
    }

    /** Return the search that route would run from an airport, whether or
     * not it reaches anywhere in particular.
     * @throws IllegalArgumentException if from is not an airport. */
    public Trace.Search search(String from, boolean fewestFlights) {
        requireAirport(from);
        Trace.Search search = new Trace.Search();
        ShortestPaths paths = new ShortestPaths();
        paths.setListener(recorder(search));
        if (fewestFlights) {
            paths.computeFlightMode(graph.getNode(from));
        } else {
            paths.compute(graph.getNode(from));
        }
        return search;
    }

    /** Return up to limit airports matching what someone has typed: the
     * start of a code, a city, or a word in the airport's name. An exact
     * code comes first, then the busiest airports. */
    public Suggestions suggest(String typed, int limit) {
        Suggestions suggestions = new Suggestions();
        String prefix = typed.trim().toLowerCase();
        if (prefix.isEmpty()) {
            return suggestions;
        }

        // Index entries are "term<tab>CODE"; several terms can lead to one airport.
        List<AVL.Node> visited = new ArrayList<AVL.Node>();
        LinkedHashSet<String> found = new LinkedHashSet<String>();
        for (String entry : searchIndex.withPrefix(prefix, Integer.MAX_VALUE, visited)) {
            found.add(entry.substring(entry.indexOf('\t') + 1));
        }
        suggestions.tree = describe(prefix, visited);

        String[] candidates = found.toArray(new String[0]);
        int[] departures = new int[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            departures[i] = airports.get(candidates[i]).departures;
        }

        String exact = typed.trim().toUpperCase();
        if (found.contains(exact)) {
            suggestions.codes.add(exact);
        }
        for (int i : orderByDescending(departures, null)) {
            if (suggestions.codes.size() >= limit) {
                break;
            }
            if (!candidates[i].equals(exact)) {
                suggestions.codes.add(candidates[i]);
            }
        }
        return suggestions;
    }

    /** Return every direct flight out of an airport, busiest first, with a
     * record of the sort that ranked them.
     * @throws IllegalArgumentException if code is not an airport. */
    public Busiest busiestFrom(String code) {
        requireAirport(code);
        List<Leg> direct = new ArrayList<Leg>();
        Node origin = graph.getNode(code);
        for (Node neighbor : origin.getNeighbors().keySet()) {
            direct.add(leg(origin, neighbor));
        }

        int[] tickets = new int[direct.size()];
        String[] labels = new String[direct.size()];
        for (int i = 0; i < tickets.length; i++) {
            tickets[i] = direct.get(i).count;
            labels[i] = direct.get(i).to;
        }

        Busiest busiest = new Busiest();
        busiest.sort = new Trace.Sort();
        busiest.sort.labels = labels;
        for (int i : orderByDescending(tickets, busiest.sort)) {
            busiest.flights.add(direct.get(i));
        }
        return busiest;
    }

    /** Return the positions of keys ordered from the largest key to the
     * smallest. Equal keys keep their original order. If trace is not null,
     * record in it what the sort did.
     *
     * Sorts works on plain ints, so each key is packed together with its
     * position into one int, the ints are sorted, and the positions are read
     * back out.
     * Precondition: keys are not negative.
     * @throws ArithmeticException if a key is too large to pack. */
    static int[] orderByDescending(int[] keys, final Trace.Sort trace) {
        final int n = keys.length;
        int[] packed = new int[n];
        for (int i = 0; i < n; i++) {
            long value = (long) keys[i] * n + (n - 1 - i);
            if (value > Integer.MAX_VALUE) {
                throw new ArithmeticException("Key too large to rank: " + keys[i]);
            }
            packed[i] = (int) value;
        }

        Sorts sorts = new Sorts();
        if (trace != null) {
            trace.keys = keys.clone();
            // The same sort, noting each call and where everything is after each merge.
            sorts = new Sorts() {
                @Override
                public void mergeSort(int[] A, int start, int end) {
                    trace.events.add(new int[] { 0, start, end });
                    super.mergeSort(A, start, end);
                }

                @Override
                public void merge(int[] A, int start, int mid, int end) {
                    super.merge(A, start, mid, end);
                    int[] merge = new int[n + 3];
                    merge[0] = 1;
                    merge[1] = start;
                    merge[2] = end;
                    for (int i = 0; i < n; i++) {
                        merge[i + 3] = n - 1 - (A[i] % n);
                    }
                    trace.events.add(merge);
                }
            };
        }
        sorts.mergeSort(packed, 0, n);
        if (trace != null) {
            trace.comparisons = sorts.getComparisonCount();
        }

        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = n - 1 - (packed[n - 1 - i] % n);
        }
        return order;
    }

    private void requireAirport(String code) {
        if (!airports.containsKey(code)) {
            throw new IllegalArgumentException("Unknown airport: " + code);
        }
    }

    /* Return the flight from one airport to a neighboring one. */
    private Leg leg(Node from, Node to) {
        double miles = from.getNeighbors().get(to);
        Integer count = counts.get(from.getId() + ">" + to.getId());
        return new Leg(from.getId(), to.getId(), miles, count == null ? 0 : count);
    }

    /* Return a listener that writes each step of a search into search. */
    private static SearchListener recorder(final Trace.Search search) {
        return new SearchListener() {
            private Trace.Step step;
            private final Set<String> reached = new HashSet<String>();

            @Override
            public void started(Node origin, Heap<Node, Double> frontier) {
                reached.add(origin.getId());
                search.snapshots.add(snapshot(frontier));
            }

            @Override
            public void settled(Node node, double priority, Heap<Node, Double> frontier) {
                step = new Trace.Step();
                step.code = node.getId();
                step.priority = priority;
                search.steps.add(step);
                search.snapshots.add(snapshot(frontier));
            }

            @Override
            public void considered(Node from, Node node, double priority) {
                step.neighbors.add(node.getId());
                step.priorities.add(priority);
                step.outcomes.append('0');
            }

            @Override
            public void reached(Node from, Node node, double priority, Heap<Node, Double> frontier) {
                boolean isNew = reached.add(node.getId());
                step.outcomes.setCharAt(step.outcomes.length() - 1, isNew ? '1' : '2');
                search.snapshots.add(snapshot(frontier));
            }
        };
    }

    private static Trace.Snapshot snapshot(Heap<Node, Double> frontier) {
        Trace.Snapshot snapshot = new Trace.Snapshot();
        snapshot.heap = new ArrayList<String>();
        for (Node waiting : HeapInspector.values(frontier, HEAP_SHOWN)) {
            snapshot.heap.add(waiting.getId());
        }
        snapshot.priorities = HeapInspector.priorities(frontier, HEAP_SHOWN);
        snapshot.size = frontier.size();
        snapshot.chains = HeapInspector.chainLengths(frontier);
        return snapshot;
    }

    /* Describe what a tree search did. visited lists each node on arrival,
     * and a second time when its word joined the result. */
    private static Trace.TreeSearch describe(String prefix, List<AVL.Node> visited) {
        Trace.TreeSearch tree = new Trace.TreeSearch();
        tree.prefix = prefix;

        Map<AVL.Node, Integer> position = new HashMap<AVL.Node, Integer>();
        for (AVL.Node node : visited) {
            Integer seen = position.get(node);
            if (seen == null) {
                Trace.TreeNode visit = new Trace.TreeNode();
                visit.word = node.word;
                Integer above = node.parent == null ? null : position.get(node.parent);
                visit.parent = above == null ? -1 : above;
                visit.depth = above == null ? 0 : tree.visits.get(above).depth + 1;
                seen = tree.visits.size();
                position.put(node, seen);
                tree.visits.add(visit);
            }
            tree.events.add(seen);
        }
        return tree;
    }

    /* Add node and everything below it, down to but not including the given
     * depth, to nodes. */
    private static void collect(AVL.Node node, int depth, int parent, int levels, List<Trace.TreeNode> nodes) {
        if (node == null || depth >= levels) {
            return;
        }
        Trace.TreeNode here = new Trace.TreeNode();
        here.word = node.word;
        here.depth = depth;
        here.parent = parent;
        int position = nodes.size();
        nodes.add(here);
        collect(node.left, depth + 1, position, levels, nodes);
        collect(node.right, depth + 1, position, levels, nodes);
    }

    /* Read how many sampled tickets flew each route, and add them up into
     * each airport's departures. A file with no Count column counts every
     * route once. */
    private void readCounts(String edgesCsv) {
        String[] lines = edgesCsv.split("\n");
        String[] headers = lines[0].trim().toLowerCase().split(",");
        int origin = indexOf(headers, "origin");
        int dest = indexOf(headers, "dest");
        int count = indexOf(headers, "count");

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.split(",");
            String from = fields[origin].replace("\"", "");
            String to = fields[dest].replace("\"", "");
            int tickets = count < 0 ? 1 : Integer.parseInt(fields[count].trim());

            String key = from + ">" + to;
            Integer already = counts.get(key);
            if (already == null) {
                routeCount++;
                already = 0;
            }
            counts.put(key, already + tickets);
            airports.get(from).departures += tickets;
        }
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].replace("\"", "").equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /* Fill in names and positions for the airports the file knows about. */
    private void readAirports(String airportsTsv) {
        for (String line : airportsTsv.split("\n")) {
            String[] fields = line.split("\t", -1);
            if (fields.length < 6) {
                continue;
            }
            Airport airport = airports.get(fields[0].trim());
            if (airport == null) {
                continue;
            }
            airport.name = fields[1].trim();
            airport.city = fields[2].trim();
            airport.region = fields[3].trim();
            try {
                airport.lat = Double.parseDouble(fields[4].trim());
                airport.lon = Double.parseDouble(fields[5].trim());
                airport.located = true;
            } catch (NumberFormatException e) {
                airport.located = false;
            }
        }
    }

    /* Index every airport under its code, its city, and each word of its
     * name and city, so that typing the start of any of them finds it. */
    private void buildSearchIndex() {
        for (String code : codes) {
            Airport airport = airports.get(code);
            searchIndex.avlInsert(code.toLowerCase() + "\t" + code);
            if (!airport.city.isEmpty()) {
                searchIndex.avlInsert(airport.city.toLowerCase() + "\t" + code);
            }
            for (String word : (airport.name + " " + airport.city).toLowerCase().split("[^a-z0-9]+")) {
                if (word.length() >= 3) {
                    searchIndex.avlInsert(word + "\t" + code);
                }
            }
        }
    }
}
