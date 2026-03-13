package router.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Prepares the two data files the web page loads. Run it on your own
 * machine whenever the raw data changes:
 *
 *   BuildData DB1BCoupon.csv airports.csv outputDirectory
 *
 * A raw DB1B coupon file has one row per sampled ticket leg, millions of
 * them. This collapses it to one row per pair of airports (edges.csv), and
 * picks the names and positions of just those airports out of the
 * OurAirports list (airports.tsv). */
public final class BuildData {

    private BuildData() { }

    public static void main(String[] args) throws IOException {
        if (args.length < 3 || args[0].isEmpty()) {
            System.out.println("usage: BuildData DB1BCoupon.csv airports.csv outputDirectory");
            return;
        }
        Path out = Paths.get(args[2]);
        Files.createDirectories(out);

        Map<String, double[]> routes = collapse(Paths.get(args[0]));
        writeEdges(routes, out.resolve("edges.csv"));

        TreeMap<String, Boolean> codes = new TreeMap<String, Boolean>();
        for (String route : routes.keySet()) {
            String[] pair = route.split(",");
            codes.put(pair[0], false);
            codes.put(pair[1], false);
        }
        int named = writeAirports(Paths.get(args[1]), codes, out.resolve("airports.tsv"));

        System.out.println(routes.size() + " routes between " + codes.size() + " airports.");
        System.out.println(named + " airports named; " + (codes.size() - named) + " not found:");
        for (Map.Entry<String, Boolean> code : codes.entrySet()) {
            if (!code.getValue()) {
                System.out.print(" " + code.getKey());
            }
        }
        System.out.println();
    }

    /* Read a DB1B coupon file and return, for each "ORIGIN,DEST" pair, its
     * distance and how many rows mentioned it. */
    private static Map<String, double[]> collapse(Path db1b) throws IOException {
        Map<String, double[]> routes = new TreeMap<String, double[]>();
        try (BufferedReader reader = Files.newBufferedReader(db1b, StandardCharsets.UTF_8)) {
            Map<String, Integer> column = columns(parseLine(reader.readLine()));
            int origin = column.get("origin");
            int dest = column.get("dest");
            int distance = column.get("distance");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] fields = line.split(",");
                String key = fields[origin].replace("\"", "") + "," + fields[dest].replace("\"", "");
                double[] route = routes.get(key);
                if (route == null) {
                    route = new double[] { Double.parseDouble(fields[distance]), 0 };
                    routes.put(key, route);
                }
                route[1]++;
            }
        }
        return routes;
    }

    private static void writeEdges(Map<String, double[]> routes, Path file) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            writer.print("Origin,Dest,Distance,Count\n");
            for (Map.Entry<String, double[]> route : routes.entrySet()) {
                writer.print(route.getKey() + "," + (long) route.getValue()[0] + "," + (long) route.getValue()[1] + "\n");
            }
        }
    }

    /* Write code, name, city, region, latitude and longitude for each wanted
     * airport that the OurAirports file lists, marking the ones found.
     * Return how many were found. */
    private static int writeAirports(Path ourAirports, Map<String, Boolean> wanted, Path file) throws IOException {
        Map<String, String[]> best = new HashMap<String, String[]>();
        Map<String, String[]> formerly = new HashMap<String, String[]>(); // matched on an old code
        Map<String, Integer> column;
        try (BufferedReader reader = Files.newBufferedReader(ourAirports, StandardCharsets.UTF_8)) {
            column = columns(parseLine(reader.readLine()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = parseLine(line);
                if (fields.length < column.size()) {
                    continue;
                }
                // DB1B mostly uses IATA codes; a few small US airports only have a local one.
                String code = fields[column.get("iata_code")];
                if (code.isEmpty() && fields[column.get("iso_country")].equals("US")) {
                    code = fields[column.get("local_code")];
                }
                if (wanted.containsKey(code)) {
                    String[] already = best.get(code);
                    if (already == null || rank(fields, column) > rank(already, column)) {
                        best.put(code, fields);
                    }
                }
                // A renamed or closed airport keeps the code it used to have in its keywords.
                for (String keyword : fields[column.get("keywords")].split(",")) {
                    String old = keyword.trim();
                    if (wanted.containsKey(old)) {
                        String[] already = formerly.get(old);
                        if (already == null || rank(fields, column) > rank(already, column)) {
                            formerly.put(old, fields);
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, String[]> old : formerly.entrySet()) {
            if (!best.containsKey(old.getKey())) {
                best.put(old.getKey(), old.getValue());
            }
        }

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            for (String code : new ArrayList<String>(wanted.keySet())) {
                String[] fields = best.get(code);
                if (fields == null) {
                    continue;
                }
                wanted.put(code, true);
                String region = fields[column.get("iso_region")];
                // "US-WA" is Washington; territories look like "PR-U-A".
                if (region.startsWith("US-")) {
                    region = region.substring(3);
                } else if (region.indexOf('-') > 0) {
                    region = region.substring(0, region.indexOf('-'));
                }
                writer.print(code + "\t" + clean(fields[column.get("name")]) + "\t"
                    + clean(fields[column.get("municipality")]) + "\t" + region + "\t"
                    + fields[column.get("latitude_deg")] + "\t" + fields[column.get("longitude_deg")] + "\n");
            }
        }
        return best.size();
    }

    /* When two airports share a code, prefer the open one with scheduled
     * flights, then the larger one. */
    private static int rank(String[] fields, Map<String, Integer> column) {
        String type = fields[column.get("type")];
        int rank = 0;
        if (!type.equals("closed")) {
            rank += 100;
        }
        if (fields[column.get("scheduled_service")].equals("yes")) {
            rank += 10;
        }
        if (type.equals("large_airport")) {
            rank += 3;
        } else if (type.equals("medium_airport")) {
            rank += 2;
        } else if (type.equals("small_airport")) {
            rank += 1;
        }
        return rank;
    }

    private static String clean(String text) {
        return text.replace('\t', ' ').trim();
    }

    private static Map<String, Integer> columns(String[] headers) {
        Map<String, Integer> column = new HashMap<String, Integer>();
        for (int i = 0; i < headers.length; i++) {
            column.put(headers[i].toLowerCase(), i);
        }
        return column;
    }

    /* Split one CSV line into fields. A field in double quotes may contain
     * commas, and a doubled quote inside it stands for one quote. */
    static String[] parseLine(String line) {
        List<String> fields = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }
}
