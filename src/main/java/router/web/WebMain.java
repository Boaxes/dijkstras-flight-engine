package router.web;

import java.util.List;
import org.teavm.jso.JSExport;
import router.RoutePlanner;
import router.RoutePlanner.Airport;
import router.RoutePlanner.Busiest;
import router.RoutePlanner.Leg;
import router.RoutePlanner.Route;
import router.RoutePlanner.Suggestions;
import router.Trace;

/** What the web pages can call. Each method takes and returns plain strings
 * and numbers; anything structured goes back as JSON text for the page to
 * parse. */
public final class WebMain {

    /** How many levels of the search tree load describes. */
    private static final int TREE_LEVELS = 3;

    private static RoutePlanner planner;
    private static Trace.Search lastSearch; // the search behind the last route, for searchState

    private WebMain() { }

    public static void main(String[] args) { }

    /** Build the network from the text of the two data files. Return every
     * airport, and the top of the tree that typed text is looked up in. */
    @JSExport
    public static String load(String edgesCsv, String airportsTsv) {
        planner = new RoutePlanner(edgesCsv, airportsTsv);

        StringBuilder json = new StringBuilder("{\"airports\":[");
        boolean first = true;
        for (Airport airport : planner.airports()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"code\":").append(quote(airport.code))
                .append(",\"name\":").append(quote(airport.name))
                .append(",\"city\":").append(quote(airport.city))
                .append(",\"region\":").append(quote(airport.region))
                .append(",\"located\":").append(airport.located)
                .append(",\"lat\":").append(airport.lat)
                .append(",\"lon\":").append(airport.lon)
                .append(",\"departures\":").append(airport.departures)
                .append('}');
        }
        json.append("],\"tree\":{\"size\":").append(planner.searchTerms())
            .append(",\"top\":").append(treeNodes(planner.treeTop(TREE_LEVELS)))
            .append("}}");
        return json.toString();
    }

    /** Return the airports matching what someone has typed, with what the
     * tree search did to find them. */
    @JSExport
    public static String suggest(String typed, int limit) {
        Suggestions suggestions = planner.suggest(typed, limit);
        StringBuilder json = new StringBuilder("{\"codes\":").append(strings(suggestions.codes));
        if (suggestions.tree != null) {
            Trace.TreeSearch tree = suggestions.tree;
            json.append(",\"tree\":{\"prefix\":").append(quote(tree.prefix))
                .append(",\"visits\":").append(treeNodes(tree.visits))
                .append(",\"events\":[");
            for (int i = 0; i < tree.events.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(tree.events.get(i).intValue());
            }
            json.append("]}");
        }
        return json.append('}').toString();
    }

    /** Return the route between two airports and every step of the search
     * for it. The search is there even when no route is found. What the
     * frontier held at each moment is fetched separately, with searchState. */
    @JSExport
    public static String route(String from, String to, boolean fewestFlights) {
        Route route = planner.route(from, to, fewestFlights, true);
        if (route == null) {
            lastSearch = planner.search(from, fewestFlights);
            return "{\"found\":false,\"search\":" + steps(lastSearch) + "}";
        }
        lastSearch = route.search;
        return "{\"found\":true,\"miles\":" + route.miles
            + ",\"legs\":" + legs(route.legs)
            + ",\"search\":" + steps(route.search) + "}";
    }

    /** Return what the frontier held at one moment of the last search:
     * moment 0 is the start, and one more follows each airport leaving the
     * frontier and each change made to it. */
    @JSExport
    public static String searchState(int moment) {
        Trace.Snapshot snapshot = lastSearch.snapshots.get(moment);
        StringBuilder json = new StringBuilder("{\"heap\":").append(strings(snapshot.heap))
            .append(",\"priorities\":").append(doubles(snapshot.priorities))
            .append(",\"size\":").append(snapshot.size);
        // One digit per bucket; no chain gets near ten long.
        json.append(",\"chains\":\"");
        for (int length : snapshot.chains) {
            json.append(Math.min(length, 9));
        }
        return json.append("\"}").toString();
    }

    /** Return every direct flight out of an airport, busiest first, with
     * what the sort did to rank them. */
    @JSExport
    public static String busiestFrom(String code) {
        Busiest busiest = planner.busiestFrom(code);
        Trace.Sort sort = busiest.sort;

        StringBuilder json = new StringBuilder("{\"flights\":").append(legs(busiest.flights))
            .append(",\"sort\":{\"labels\":[");
        for (int i = 0; i < sort.labels.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(quote(sort.labels[i]));
        }
        json.append("],\"keys\":").append(ints(sort.keys))
            .append(",\"comparisons\":").append(sort.comparisons)
            .append(",\"events\":[");
        for (int i = 0; i < sort.events.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(ints(sort.events.get(i)));
        }
        return json.append("]}}").toString();
    }

    private static String steps(Trace.Search search) {
        StringBuilder json = new StringBuilder("[");
        for (Trace.Step step : search.steps) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("{\"code\":").append(quote(step.code))
                .append(",\"priority\":").append(step.priority)
                .append(",\"neighbors\":").append(strings(step.neighbors))
                .append(",\"priorities\":").append(doubles(step.priorities))
                .append(",\"outcomes\":\"").append(step.outcomes)
                .append("\"}");
        }
        return json.append(']').toString();
    }

    private static String treeNodes(List<Trace.TreeNode> nodes) {
        StringBuilder json = new StringBuilder("[");
        for (Trace.TreeNode node : nodes) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("{\"word\":").append(quote(node.word))
                .append(",\"depth\":").append(node.depth)
                .append(",\"parent\":").append(node.parent)
                .append('}');
        }
        return json.append(']').toString();
    }

    private static String legs(List<Leg> legs) {
        StringBuilder json = new StringBuilder("[");
        for (Leg leg : legs) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append("{\"from\":").append(quote(leg.from))
                .append(",\"to\":").append(quote(leg.to))
                .append(",\"miles\":").append(leg.miles)
                .append(",\"count\":").append(leg.count)
                .append('}');
        }
        return json.append(']').toString();
    }

    private static String strings(List<String> strings) {
        StringBuilder json = new StringBuilder("[");
        for (String string : strings) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append(quote(string));
        }
        return json.append(']').toString();
    }

    private static String doubles(List<Double> numbers) {
        StringBuilder json = new StringBuilder("[");
        for (Double number : numbers) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append(number.doubleValue());
        }
        return json.append(']').toString();
    }

    private static String ints(int[] numbers) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < numbers.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(numbers[i]);
        }
        return json.append(']').toString();
    }

    /* Return text as a JSON string. */
    private static String quote(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                quoted.append('\\').append(c);
            } else if (c == '\t') {
                quoted.append("\\t");
            } else if (c < ' ') {
                quoted.append(' ');
            } else {
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }
}
