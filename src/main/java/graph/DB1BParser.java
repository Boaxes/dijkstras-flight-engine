package graph;

import java.util.Scanner;
import java.util.HashMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ListIterator;

public class DB1BParser extends GraphParser {

    private Scanner sc; // a scanner reading the CSV file

    // map csv headers to column indices
    private HashMap<String,Integer> fieldKey;

    private Graph graph;

    /** Constructor: create parser. */
    public DB1BParser() {
        graph = new Graph();
    }

    /** Open the given file and prepare to parse it. */
    @Override
    public void open(File f) throws FileNotFoundException {
        sc = new Scanner(f);
        parseHeaders(sc.nextLine());
    }

    /** Parse an opened file and return a Graph representing the data in the
     * file. Precondition: open() has been successfully called. */
    @Override
    public Graph parse() {
        graph = new Graph();
        while (sc.hasNextLine()) {
            parseFlight(sc.nextLine().split(","));
        }
        return graph;
    }

    /** Added for the flight engine: parse CSV text that is already in memory and
     * return a Graph representing it. A browser has no file to open, so the
     * page fetches the file and hands its text over. The first line must be
     * the header line. */
    public Graph parseText(String csv) {
        String[] lines = csv.split("\n");
        parseHeaders(lines[0].trim());
        graph = new Graph();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                parseFlight(line.split(","));
            }
        }
        return graph;
    }

    /* Parse a line of the DB1B CSV file and construct a Flight object (edge)
     * representing the data given in that line.  If the origin or destination
     * airport is not a node in the graph, add it to the graph. Add this Flight
     * to the list of departures from the origin. */
    private void parseFlight(String[] fields) {
        String origCode = fields[fieldKey.get("origin")].replace("\"","");;
        String destCode = fields[fieldKey.get("dest")].replace("\"","");;

        // if origin is not in graph, parse and create the new Airport

        Node orig = graph.getNode(origCode);
        Node dest = graph.getNode(destCode);

        double miles = Double.parseDouble(fields[fieldKey.get("distance")]);
        graph.addEdge(orig, dest, miles);
    }

    /* create fieldKey from the header line of the csv file.
     * fieldKey maps a field's name to its column index in the csv file. */
    private void parseHeaders(String headerLine) {
        fieldKey = new HashMap<String,Integer>();
        String[] headers = headerLine.split(",");
        for (int i = 0; i < headers.length; i++) {
            fieldKey.put(headers[i].replace("\"","").toLowerCase(), i);
            //System.out.println(headers[i] + ": " + i);
        }
    }

    /** Main method: open a csv file and parse a graph.
     *  If no command line arguments are given, parse the entire file.
     *  If one command line is given, it specifies the maximum number of lines
     *  to parse before ignoring the rest of the file. */
    public static void main(String[] args) {
        DB1BParser parser = new DB1BParser();
        try {
            parser.open(new File(args[0]));
        } catch (FileNotFoundException e) {
            System.out.println("Could not find file " + args[0]);
            return;
        }
        int maxLines = -1;
        if (args.length > 1) {
            maxLines = Integer.parseInt(args[1]);
        }

        Graph g = parser.parse();
        g.report();
    }


}
