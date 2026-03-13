package router;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import avl.AVL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import router.RoutePlanner.Leg;
import router.RoutePlanner.Route;

class RoutePlannerTest {

    // SEA to MIA is one long direct flight, or two shorter ones through DEN
    // that add up to fewer miles. ANC and FAI only reach each other. HNL can
    // be flown to but not out of.
    private static final String EDGES = String.join("\n",
        "Origin,Dest,Distance,Count",
        "SEA,MIA,3000,40",
        "MIA,SEA,3000,35",
        "SEA,DEN,1000,500",
        "DEN,SEA,1000,480",
        "DEN,MIA,1700,300",
        "MIA,DEN,1700,290",
        "SEA,PDX,130,900",
        "PDX,SEA,130,880",
        "SEA,HNL,2680,60",
        "ANC,FAI,260,20",
        "FAI,ANC,260,25",
        "");

    private static final String AIRPORTS = String.join("\n",
        "SEA\tSeattle Tacoma International Airport\tSeattle\tWA\t47.449\t-122.309",
        "MIA\tMiami International Airport\tMiami\tFL\t25.793\t-80.291",
        "DEN\tDenver International Airport\tDenver\tCO\t39.862\t-104.673",
        "PDX\tPortland International Airport\tPortland\tOR\t45.589\t-122.597",
        "");

    private final RoutePlanner planner = new RoutePlanner(EDGES, AIRPORTS);

    private static List<String> stops(Route route) {
        List<String> stops = new ArrayList<String>();
        stops.add(route.legs.get(0).from);
        for (Leg leg : route.legs) {
            stops.add(leg.to);
        }
        return stops;
    }

    @Test
    void fewestMilesGoesThroughDenver() {
        Route route = planner.route("SEA", "MIA", false);
        assertEquals(Arrays.asList("SEA", "DEN", "MIA"), stops(route));
        assertEquals(2700.0, route.miles);
        assertEquals(500, route.legs.get(0).count);
    }

    @Test
    void fewestFlightsGoesDirect() {
        Route route = planner.route("SEA", "MIA", true);
        assertEquals(Arrays.asList("SEA", "MIA"), stops(route));
        assertEquals(3000.0, route.miles);
    }

    // Three ways from AAA to DDD in two flights, and none in one. Whatever
    // order the search meets them in, it should settle on the shortest.
    @Test
    void fewestFlightsBreaksTiesOnMiles() {
        String edges = String.join("\n",
            "Origin,Dest,Distance,Count",
            "AAA,BBB,900,10", "BBB,DDD,900,10",
            "AAA,CCC,100,10", "CCC,DDD,150,10",
            "AAA,EEE,500,10", "EEE,DDD,500,10",
            "AAA,FFF,10,10", "FFF,GGG,10,10", "GGG,DDD,10,10",
            "");
        RoutePlanner ties = new RoutePlanner(edges, "");
        assertEquals(Arrays.asList("AAA", "CCC", "DDD"), stops(ties.route("AAA", "DDD", true)));
        assertEquals(250.0, ties.route("AAA", "DDD", true).miles);
        // three short flights still beat two long ones on miles alone
        assertEquals(Arrays.asList("AAA", "FFF", "GGG", "DDD"), stops(ties.route("AAA", "DDD", false)));
    }

    @Test
    void routeToTheSameAirportHasNoFlights() {
        Route route = planner.route("SEA", "SEA", false);
        assertTrue(route.legs.isEmpty());
        assertEquals(0.0, route.miles);
    }

    @Test
    void noRouteBetweenAirportsNothingConnects() {
        assertNull(planner.route("SEA", "ANC", false));
    }

    @Test
    void noRouteOutOfAnAirportWithNoDepartures() {
        assertNull(planner.route("HNL", "SEA", false));
    }

    @Test
    void unknownAirportIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> planner.route("SEA", "XXX", false));
        assertThrows(IllegalArgumentException.class, () -> planner.busiestFrom("XXX"));
    }

    @Test
    void airportsComeBackInCodeOrder() {
        List<String> codes = new ArrayList<String>();
        for (RoutePlanner.Airport airport : planner.airports()) {
            codes.add(airport.code);
        }
        assertEquals(Arrays.asList("ANC", "DEN", "FAI", "HNL", "MIA", "PDX", "SEA"), codes);
    }

    @Test
    void airportFileFillsInNamesAndDepartures() {
        RoutePlanner.Airport sea = planner.airport("SEA");
        assertEquals("Seattle", sea.city);
        assertTrue(sea.located);
        assertEquals(40 + 500 + 900 + 60, sea.departures);
        assertEquals("", planner.airport("ANC").name);
        assertEquals(11, planner.routeCount());
    }

    @Test
    void suggestMatchesCodeCityAndNameWords() {
        assertEquals(Arrays.asList("SEA"), planner.suggest("sea", 5).codes);
        assertEquals(Arrays.asList("SEA"), planner.suggest("Seat", 5).codes);
        assertEquals(Arrays.asList("SEA"), planner.suggest("tacoma", 5).codes);
        assertEquals(Arrays.asList("PDX"), planner.suggest("portl", 5).codes);
        assertTrue(planner.suggest("zzz", 5).codes.isEmpty());
        assertTrue(planner.suggest("  ", 5).codes.isEmpty());
    }

    @Test
    void suggestPutsBusiestFirstAndRespectsLimit() {
        // every named airport has "international" in its name
        assertEquals(Arrays.asList("SEA", "PDX", "DEN", "MIA"), planner.suggest("inter", 5).codes);
        assertEquals(Arrays.asList("SEA", "PDX"), planner.suggest("inter", 2).codes);
    }

    @Test
    void suggestPutsAnExactCodeFirst() {
        // "den" is also the start of Denver, and "mia" of Miami
        assertEquals("DEN", planner.suggest("den", 5).codes.get(0));
        assertEquals("MIA", planner.suggest("MIA", 5).codes.get(0));
    }

    @Test
    void busiestFromRanksEveryFlightByTickets() {
        List<Leg> busiest = planner.busiestFrom("SEA").flights;
        assertEquals(4, busiest.size());
        assertEquals("PDX", busiest.get(0).to);
        assertEquals("DEN", busiest.get(1).to);
        assertEquals("HNL", busiest.get(2).to);
        assertEquals("MIA", busiest.get(3).to);
        assertTrue(planner.busiestFrom("HNL").flights.isEmpty());
    }

    @Test
    void orderByDescendingKeepsTiesInOrder() {
        assertArrayEquals(new int[] { 2, 0, 3, 1 }, RoutePlanner.orderByDescending(new int[] { 5, 1, 9, 5 }, null));
        assertArrayEquals(new int[0], RoutePlanner.orderByDescending(new int[0], null));
        assertThrows(ArithmeticException.class,
            () -> RoutePlanner.orderByDescending(new int[] { Integer.MAX_VALUE, 1 }, null));
    }

    @Test
    void edgesWithoutCountColumnStillLoad() {
        RoutePlanner plain = new RoutePlanner("Origin,Dest,Distance\nAAA,BBB,10\nBBB,AAA,10\n", "");
        assertEquals(10.0, plain.route("AAA", "BBB", false).miles);
        assertEquals(1, plain.airport("AAA").departures);
    }

    @Test
    void routeTraceFollowsTheSearch() {
        Route route = planner.route("SEA", "MIA", false, true);
        List<Trace.Step> steps = route.search.steps;

        // The search settles every airport SEA can reach, nearest first.
        List<String> settled = new ArrayList<String>();
        for (Trace.Step step : steps) {
            settled.add(step.code);
        }
        assertEquals(Arrays.asList("SEA", "PDX", "DEN", "HNL", "MIA"), settled);
        assertEquals(0.0, steps.get(0).priority);
        assertEquals(2700.0, steps.get(4).priority);

        // From SEA it looks at all four neighbors, and each is new to the frontier.
        assertEquals(4, steps.get(0).neighbors.size());
        assertEquals("1111", steps.get(0).outcomes.toString());

        // PDX only leads back to SEA, which is no improvement.
        assertEquals(Arrays.asList("SEA"), steps.get(1).neighbors);
        assertEquals("0", steps.get(1).outcomes.toString());
        assertEquals(260.0, steps.get(1).priorities.get(0));

        // Denver looks at SEA and MIA, and its way to Miami beats the direct flight.
        Trace.Step denver = steps.get(2);
        assertEquals('2', denver.outcomes.charAt(denver.neighbors.indexOf("MIA")));
        assertEquals('0', denver.outcomes.charAt(denver.neighbors.indexOf("SEA")));

        assertNull(planner.route("SEA", "MIA", false).search);
        assertEquals(2, planner.search("ANC", false).steps.size());
    }

    @Test
    void searchSnapshotsShowTheFrontierAtEachMoment() {
        Trace.Search search = planner.search("SEA", false);

        // One at the start, one per airport settled, one per change to the frontier.
        int changes = 0;
        for (Trace.Step step : search.steps) {
            for (char outcome : step.outcomes.toString().toCharArray()) {
                if (outcome != '0') {
                    changes++;
                }
            }
        }
        assertEquals(1 + search.steps.size() + changes, search.snapshots.size());

        // At the start the frontier is just SEA.
        Trace.Snapshot start = search.snapshots.get(0);
        assertEquals(Arrays.asList("SEA"), start.heap);
        assertEquals(1, start.size);

        // Once SEA has left it is empty, then fills with SEA's four neighbors, nearest at the root.
        assertEquals(0, search.snapshots.get(1).size);
        Trace.Snapshot filled = search.snapshots.get(5);
        assertEquals(4, filled.size);
        assertEquals("PDX", filled.heap.get(0));
        assertEquals(130.0, filled.priorities.get(0));

        // The hash table inside the heap has one entry per airport waiting.
        int entries = 0;
        for (int length : filled.chains) {
            entries += length;
        }
        assertEquals(4, entries);
        assertEquals(0, search.snapshots.get(search.snapshots.size() - 1).size);
    }

    @Test
    void flightModeTracePriorityCombinesFlightsAndMiles() {
        List<Trace.Step> steps = planner.search("SEA", true).steps;
        assertEquals("SEA", steps.get(0).code);
        assertEquals("PDX", steps.get(1).code); // one flight, 130 miles
        assertEquals(1000130.0, steps.get(1).priority);
    }

    @Test
    void suggestTraceShowsTheTreeSearch() {
        Trace.TreeSearch tree = planner.suggest("inter", 5).tree;
        assertEquals("inter", tree.prefix);
        assertEquals(0, tree.visits.get(0).depth);
        assertEquals(-1, tree.visits.get(0).parent);
        for (int i = 1; i < tree.visits.size(); i++) {
            Trace.TreeNode visit = tree.visits.get(i);
            assertTrue(visit.parent >= 0 && visit.parent < i);
            assertEquals(tree.visits.get(visit.parent).depth + 1, visit.depth);
        }
        assertTrue(tree.visits.size() < planner.searchTerms()); // most of the tree was never looked at

        // Every node shows up on arrival; the four matches show up again when they join the result.
        int[] seen = new int[tree.visits.size()];
        for (int event : tree.events) {
            seen[event]++;
        }
        int matches = 0;
        for (int i = 0; i < seen.length; i++) {
            assertTrue(seen[i] == 1 || seen[i] == 2);
            if (seen[i] == 2) {
                matches++;
                assertTrue(tree.visits.get(i).word.startsWith("inter"));
            }
        }
        assertEquals(4, matches);
        assertEquals(tree.visits.size() + 4, tree.events.size());
    }

    @Test
    void treeTopListsTheUpperLevelsParentsFirst() {
        List<Trace.TreeNode> top = planner.treeTop(3);
        assertTrue(top.size() <= 7);
        assertEquals(0, top.get(0).depth);
        assertEquals(-1, top.get(0).parent);
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i).depth < 3);
            assertEquals(top.get(top.get(i).parent).depth + 1, top.get(i).depth);
        }
        assertEquals(1, planner.treeTop(1).size());
        assertEquals(planner.searchTerms(), planner.treeTop(100).size());
    }

    @Test
    void busiestTraceShowsEachCallAndMerge() {
        Trace.Sort sort = planner.busiestFrom("SEA").sort;
        assertEquals(4, sort.labels.length);
        assertTrue(sort.comparisons > 0);

        // Sorting four items: seven calls to mergeSort and three merges.
        int calls = 0;
        int merges = 0;
        for (int[] event : sort.events) {
            if (event[0] == 0) {
                calls++;
            } else {
                merges++;
            }
        }
        assertEquals(7, calls);
        assertEquals(3, merges);
        assertArrayEquals(new int[] { 0, 0, 4 }, sort.events.get(0));

        // The last merge covers everything and leaves it sorted, smallest first.
        int[] last = sort.events.get(sort.events.size() - 1);
        assertEquals(1, last[0]);
        assertEquals(0, last[1]);
        assertEquals(4, last[2]);
        for (int slot = 3; slot < last.length - 1; slot++) {
            assertTrue(sort.keys[last[slot]] <= sort.keys[last[slot + 1]]);
        }
        assertEquals("PDX", sort.labels[last[last.length - 1]]);
    }

    @Test
    void avlPrefixSearchReturnsSortedMatches() {
        AVL tree = new AVL();
        for (String word : new String[] { "pear", "peach", "apple", "plum", "pea", "banana", "peanut" }) {
            tree.avlInsert(word);
        }
        assertEquals(Arrays.asList("pea", "peach", "peanut", "pear"), tree.withPrefix("pea", 10));
        assertEquals(Arrays.asList("pea", "peach"), tree.withPrefix("pea", 2));
        assertEquals(Arrays.asList("apple", "banana", "pea", "peach", "peanut", "pear", "plum"), tree.withPrefix("", 10));
        assertTrue(tree.withPrefix("q", 10).isEmpty());
        assertTrue(new AVL().withPrefix("a", 10).isEmpty());
    }
}
