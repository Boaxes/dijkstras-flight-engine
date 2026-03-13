// "Explanation" on the routes page: the code that just ran, with a bar on
// the running line, beside a picture of the data it is working on. Every
// replay is drawn from a record the Java code filled in as it ran.

import { CodeView } from "./codeview.js";
import { $, number, span } from "./core.js";
import { MapView, svg } from "./map.js";
import { Player } from "./player.js";

// Fill a strip with the current values of some variables.
function showVariables(strip, values) {
  strip.replaceChildren(
    ...Object.entries(values)
      .filter(([, value]) => value !== null && value !== undefined)
      .map(([name, value]) => {
        const item = span("variable", "");
        item.append(span("name", name), ` ${value}`);
        return item;
      })
  );
}

// app: what loadApp returned. land: the state outlines. sources: the text
// of each Java file shown.
export function createExplanation({ app, land, sources }) {
  graphCard(app.edges, sources.parser);
  return {
    tree: treeCard(app.tree, sources.avl),
    search: searchCard(app, land, sources.paths),
  };
}

// ------------------------------------------------------- 1. building the graph

const CSV_ROWS = 8;

function graphCard(edges, source) {
  const code = new CodeView($("graph-code"), source, "private void parseFlight");
  const statements = code.between(1, code.lines.length);
  const rows = edges.split("\n").slice(0, CSV_ROWS + 1);
  const csv = $("graph-csv");
  csv.replaceChildren(...rows.map((row) => span("", row)));

  const player = new Player($("card-graph"), $("graph-controls"), {
    render(k) {
      const row = 1 + Math.floor(k / statements.length);
      [...csv.children].forEach((line, i) => line.classList.toggle("running", i === row));
      code.mark(statements[k % statements.length]);
      const [origin, dest, miles] = rows[row].split(",");
      showVariables($("graph-variables"), { origCode: origin, destCode: dest, miles });
    },
  });
  player.load(CSV_ROWS * statements.length);
}

// ------------------------------------------------------------- 2. the AVL tree

const VISITS_SHOWN = 60; // nodes of a search drawn, beyond the top of the tree
const COLUMN = 64; // room across for one node and its word
const LEVEL = 48; // room down for one level

function treeCard(tree, source) {
  const code = new CodeView($("tree-code"), source, "private void withPrefix");
  const startsWith = code.find("n.word.startsWith(prefix)");
  const add = code.find("found.add(n.word)");
  const elseIf = code.find("else if (n.word.compareTo(prefix) < 0)");
  const otherwise = code.find("else {", elseIf + 1);
  const line = {
    guard: code.find("if (n == null"),
    startsWith,
    left: code.find("withPrefix(n.left", startsWith),
    sizeCheck: code.find("if (found.size() < limit)"),
    add,
    right: code.find("withPrefix(n.right", add),
    elseIf,
    rightOnly: code.find("withPrefix(n.right", elseIf),
    otherwise,
    leftOnly: code.find("withPrefix(n.left", otherwise),
  };

  const drawing = $("tree-drawing");
  const scroller = $("tree-scroller");
  let frames = [];
  let parts = new Map(); // word -> its pieces, where it is, and when the search arrives at and adds it
  let prefix = "";

  // Draw some nodes of the tree, each with its word beneath it. nodes maps a
  // word to its depth and the word above it.
  function draw(nodes) {
    // A search tree read left to right is in sorted order, so sorting the
    // nodes by word gives each its own column.
    const words = [...nodes.keys()].sort((a, b) => (a < b ? -1 : 1));
    words.forEach((word, rank) => {
      nodes.get(word).x = COLUMN / 2 + rank * COLUMN;
    });
    const y = (node) => 14 + node.depth * LEVEL;
    const deepest = Math.max(...[...nodes.values()].map((node) => node.depth));
    const width = words.length * COLUMN;
    const height = y({ depth: deepest }) + 38;
    drawing.setAttribute("viewBox", `0 0 ${width} ${height}`);
    drawing.setAttribute("width", width);
    drawing.setAttribute("height", height);

    parts = new Map();
    const edges = [];
    const rest = [];
    for (const [word, node] of nodes) {
      let edge = null;
      if (node.parent !== null) {
        const above = nodes.get(node.parent);
        edge = svg("line", { x1: above.x, y1: y(above), x2: node.x, y2: y(node) });
        edges.push(edge);
      }
      const [term, airport] = word.split("\t");
      const dot = svg("circle", { cx: node.x, cy: y(node), r: 5 });
      const label = svg("text", { x: node.x, y: y(node) + 17 }, term.length > 10 ? term.slice(0, 9) + "…" : term);
      label.append(svg("title", {}, term));
      const sub = svg("text", { class: "sub", x: node.x, y: y(node) + 30 }, airport);
      rest.push(dot, label, sub);
      parts.set(word, { edge, dot, label, x: node.x, arrive: Infinity, added: Infinity, className: null });
    }
    drawing.replaceChildren(...edges, ...rest);
  }

  function paint(k, current) {
    let found = 0;
    for (const [word, part] of parts) {
      const state = part.added <= k ? "match" : part.arrive <= k ? "seen" : "untouched";
      if (part.added <= k) {
        found++;
      }
      const className = `${state}${word === current ? " current" : ""}`;
      if (part.className !== className) {
        part.dot.setAttribute("class", className);
        part.label.setAttribute("class", className);
        if (part.edge) {
          part.edge.setAttribute("class", state);
        }
        part.className = className;
      }
    }
    return found;
  }

  const top = () =>
    new Map(tree.top.map((node) => [node.word, { depth: node.depth, parent: node.parent < 0 ? null : tree.top[node.parent].word }]));

  const player = new Player($("card-tree"), $("tree-controls"), {
    render(k) {
      const frame = frames[k];
      code.mark(frame.line);
      const found = paint(k, frame.word);
      // Follow the search across a drawing wider than the card.
      scroller.scrollLeft = parts.get(frame.word).x - scroller.clientWidth / 2;
      const [term, airport] = frame.word.split("\t");
      showVariables($("tree-variables"), { prefix: `"${prefix}"`, "n.word": `"${term}" (${airport})`, found });
    },
  });

  // Until something is typed, show the top of the tree at rest.
  draw(top());
  paint(-1, null);

  return {
    // record: what the search did. Draws the top of the tree plus every node
    // the search went to, and steps through the code that took it there.
    show(record) {
      prefix = record.prefix;
      const visits = record.visits.slice(0, VISITS_SHOWN);
      const nodes = top();
      for (const visit of visits) {
        nodes.set(visit.word, { depth: visit.depth, parent: visit.parent < 0 ? null : record.visits[visit.parent].word });
      }
      draw(nodes);

      // The lines each event runs, in order.
      frames = [];
      const push = (which, word) => frames.push({ line: line[which], word });
      const arrived = new Set();
      for (const event of record.events) {
        if (event >= visits.length) {
          continue;
        }
        const word = visits[event].word;
        const part = parts.get(word);
        if (!arrived.has(event)) {
          arrived.add(event);
          part.arrive = frames.length;
          push("guard", word);
          push("startsWith", word);
          if (word.startsWith(prefix)) {
            push("left", word);
          } else {
            push("elseIf", word);
            if (word < prefix) {
              push("rightOnly", word);
            } else {
              push("otherwise", word);
              push("leftOnly", word);
            }
          }
        } else {
          push("sizeCheck", word);
          part.added = frames.length;
          push("add", word);
          push("right", word);
        }
      }
      player.load(frames.length);
    },
  };
}

// ----------------------------------- 3. Dijkstra, its priority queue, its hash table

// Work out which lines of a search method run for each part of a step.
function program(code) {
  const loop = code.find("while (F.size() > 0)");
  const forLine = code.find("for (Map.Entry");
  const ifLine = code.find("if (!paths.containsKey(w))");
  const elseIf = code.find("else if (F.contains(w)");
  let condition = elseIf; // the else-if's condition may run over two lines
  while (!code.lines[condition].text.split("//")[0].trimEnd().endsWith("{")) {
    condition++;
  }
  let close = condition + 1;
  while (code.lines[close].text.trim() !== "}") {
    close++;
  }
  return {
    init: code.between(code.find("paths = new HashMap"), code.find("F.add(origin") + 1),
    loop,
    poll: [code.find("Node f = F.poll()"), code.find("PathData fData")],
    neighbor: code.between(forLine, ifLine),
    ifLine,
    fresh: code.between(ifLine + 1, elseIf),
    elseIf: code.between(elseIf, condition + 1),
    better: code.between(condition + 1, close),
  };
}

function searchCard(app, land, source) {
  const { java, airports, list } = app;
  const map = new MapView($("search-map"), { airports: list, land, dotSize: () => 2.5 });
  const queueDrawing = $("queue-drawing");
  const cells = $("table-cells");
  const views = {
    miles: new CodeView($("search-code-miles"), source, "public void compute("),
    flights: new CodeView($("search-code-flights"), source, "public void computeFlightMode("),
    path: new CodeView($("search-code-path"), source, "public LinkedList<Node> shortestPath("),
  };
  const programs = { miles: program(views.miles), flights: program(views.flights) };
  const pathLine = {
    check: views.path.find("if (!paths.containsKey(destination))"),
    none: views.path.find("return null"),
    result: views.path.find("LinkedList<Node> result"),
    current: views.path.find("Node current = destination"),
    loop: views.path.find("while (current != null)"),
    addFirst: views.path.find("result.addFirst(current)"),
    back: views.path.find("current = paths.get(current).bp"),
    done: views.path.find("return result"),
  };

  let run = null; // everything about the search being replayed
  let drawn = {}; // what the map and queue last showed, to skip needless redraws

  const short = (priority) => {
    if (run.mode !== "flights") {
      return number.format(priority);
    }
    // Flight mode packs both into one number: a million per flight, plus the miles.
    const flights = Math.floor(priority / 1e6);
    return `${flights}f ${number.format(priority - flights * 1e6)}`;
  };

  // Say what is happening at frame k: which line is running, in which code,
  // which airport f and neighbor w are in hand, how much of the route has
  // been read back, and which snapshot of the frontier is current.
  function locate(k) {
    const { steps, plan } = run;
    if (k < plan.init.length) {
      return { view: run.mode, line: plan.init[k], snap: k === plan.init.length - 1 ? 0 : -1 };
    }
    k -= plan.init.length;

    if (k < run.searchFrames) {
      // Find the step this frame falls in.
      let low = 0;
      let high = steps.length - 1;
      while (low < high) {
        const middle = (low + high + 1) >> 1;
        if (run.stepStart[middle] <= k) {
          low = middle;
        } else {
          high = middle - 1;
        }
      }
      const s = low;
      const step = steps[s];
      let r = k - run.stepStart[s];
      const at = { view: run.mode, s, f: null, w: null, snap: run.stepSnap[s] };
      if (r === 0) {
        return { ...at, line: plan.loop, snap: run.stepSnap[s] - 1 };
      }
      at.f = step.code;
      if (r <= 2) {
        return { ...at, line: plan.poll[r - 1] };
      }
      r -= 3;
      let done = 0; // changes to the frontier made so far in this step
      for (let j = 0; j < step.neighbors.length; j++) {
        const outcome = step.outcomes[j];
        const tail = outcome === "1" ? plan.fresh : outcome === "2" ? [...plan.elseIf, ...plan.better] : plan.elseIf;
        const lines = [...plan.neighbor, plan.ifLine, ...tail];
        if (r < lines.length) {
          const finished = outcome !== "0" && r === lines.length - 1;
          return {
            ...at,
            line: lines[r],
            w: step.neighbors[j],
            newDistance: step.priorities[j],
            snap: run.stepSnap[s] + done + (finished ? 1 : 0),
          };
        }
        r -= lines.length;
        if (outcome !== "0") {
          done++;
        }
      }
    }
    k -= run.searchFrames;
    const lastSnap = run.snapshots - 1;
    if (k === 0) {
      return { view: run.mode, line: plan.loop, snap: lastSnap }; // the loop's last test fails
    }
    k -= 1;

    // Reading the route back through the back-pointers.
    if (run.path.length === 0) {
      return { view: "path", line: k === 0 ? pathLine.check : pathLine.none, snap: lastSnap, walked: 0 };
    }
    if (k < 3) {
      return { view: "path", line: [pathLine.check, pathLine.result, pathLine.current][k], snap: lastSnap, walked: 0 };
    }
    k -= 3;
    const turn = Math.floor(k / 3);
    if (turn < run.path.length) {
      const which = k % 3;
      return {
        view: "path",
        line: [pathLine.loop, pathLine.addFirst, pathLine.back][which],
        snap: lastSnap,
        walked: turn + (which >= 1 ? 1 : 0),
      };
    }
    const after = k - run.path.length * 3;
    return { view: "path", line: after === 0 ? pathLine.loop : pathLine.done, snap: lastSnap, walked: run.path.length };
  }

  function drawQueue(snap) {
    const state = snap < 0 ? { heap: [], priorities: [], size: 0, chains: "" } : JSON.parse(java.searchState(snap));
    const width = 360;
    const at = (i) => {
      const level = Math.floor(Math.log2(i + 1));
      const inLevel = i + 1 - 2 ** level;
      return [(width * (inLevel + 0.5)) / 2 ** level, 22 + level * 54];
    };
    const pieces = [];
    state.heap.forEach((code, i) => {
      if (i > 0) {
        const [px, py] = at((i - 1) >> 1);
        const [x, y] = at(i);
        pieces.push(svg("line", { x1: px, y1: py, x2: x, y2: y }));
      }
    });
    state.heap.forEach((code, i) => {
      const [x, y] = at(i);
      pieces.push(
        svg("circle", { class: i === 0 ? "root" : "", cx: x, cy: y, r: 15 }),
        svg("text", { class: "code-label", x, y: y + 3.5 }, code),
        svg("text", { class: "priority", x, y: y + 26 }, short(state.priorities[i]))
      );
    });
    queueDrawing.replaceChildren(...pieces);
    $("queue-size").textContent = state.size > state.heap.length ? `${number.format(state.size)} entries, top 15 shown` : "";

    if (cells.children.length !== state.chains.length) {
      cells.replaceChildren(...[...state.chains].map(() => document.createElement("span")));
    }
    [...state.chains].forEach((digit, i) => {
      const className = `c${Math.min(Number(digit), 3)}`;
      if (cells.children[i].className !== className) {
        cells.children[i].className = className;
      }
    });
  }

  function drawMap(at) {
    const settled = (code) => run.settledSnap.has(code) && run.settledSnap.get(code) <= at.snap;
    const tree = [];
    for (const step of run.steps) {
      const parent = run.parent.get(step.code);
      if (parent && settled(step.code)) {
        tree.push([airports.get(parent), airports.get(step.code)]);
      }
    }
    const lines = [{ className: "tree", pairs: tree }];
    const marks = [];
    const mark = (code, className) => marks.push({ airport: airports.get(code), className, label: code });

    if (at.walked !== undefined) {
      // The route appears from the destination backwards.
      const walked = run.path.slice(run.path.length - at.walked);
      lines.push({
        className: "route-line",
        pairs: walked.slice(1).map((code, i) => [airports.get(walked[i]), airports.get(code)]),
      });
      new Set([run.from, run.to, ...walked]).forEach((code) => mark(code, "mark end"));
    } else {
      if (at.f && at.w) {
        lines.push({ className: "relax", pairs: [[airports.get(at.f), airports.get(at.w)]] });
      }
      new Set([run.from, run.to]).forEach((code) => mark(code, "mark end"));
      if (at.f && at.f !== run.from && at.f !== run.to) {
        mark(at.f, "mark current");
      }
      if (at.w && at.w !== run.from && at.w !== run.to) {
        mark(at.w, "mark neighbor");
      }
    }
    map.show({
      frame: run.frame,
      styleOf(code) {
        if (settled(code)) {
          return "dot settled";
        }
        return run.addedSnap.has(code) && run.addedSnap.get(code) <= at.snap ? "dot frontier" : "dot";
      },
      lines,
      marks,
    });
  }

  const player = new Player($("card-search"), $("search-controls"), {
    render(k) {
      const at = locate(k);
      for (const [name, view] of Object.entries(views)) {
        view.container.hidden = name !== at.view;
        view.mark(name === at.view ? at.line : null);
      }
      const key = `${at.snap}|${at.f}|${at.w}|${at.walked}`;
      if (drawn.key !== key) {
        drawMap(at);
        drawn.key = key;
      }
      if (drawn.snap !== at.snap) {
        drawQueue(at.snap);
        drawn.snap = at.snap;
      }
      showVariables(
        $("search-variables"),
        at.view === "path"
          ? { destination: run.to, result: at.walked > 0 ? run.path.slice(run.path.length - at.walked).join(" ") : "empty" }
          : { f: at.f, w: at.w, newDistance: at.w ? short(at.newDistance) : null }
      );
    },
  });

  return {
    // result: what route returned. from, to: airport codes. mode: "miles" or "flights".
    show(result, from, to, mode) {
      const steps = result.search;
      const plan = programs[mode];

      // Number the moments the frontier changes, the same way Java numbered
      // its snapshots: the start, each airport leaving, each add or move.
      const settledSnap = new Map();
      const addedSnap = new Map([[from, 0]]);
      const parent = new Map();
      const stepStart = [];
      const stepSnap = [];
      let frames = 0;
      let snap = 0;
      for (const step of steps) {
        stepStart.push(frames);
        snap++;
        stepSnap.push(snap);
        settledSnap.set(step.code, snap);
        frames += 3;
        step.neighbors.forEach((code, j) => {
          const outcome = step.outcomes[j];
          frames += plan.neighbor.length + 1;
          if (outcome === "0") {
            frames += plan.elseIf.length;
            return;
          }
          frames += outcome === "1" ? plan.fresh.length : plan.elseIf.length + plan.better.length;
          snap++;
          if (!addedSnap.has(code)) {
            addedSnap.set(code, snap);
          }
          parent.set(code, step.code); // the last to improve on it is the one it keeps
        });
      }
      const path = result.found ? [from, ...result.legs.map((leg) => leg.to)] : [];
      run = {
        steps,
        plan,
        mode,
        from,
        to,
        path,
        settledSnap,
        addedSnap,
        parent,
        stepStart,
        stepSnap,
        searchFrames: frames,
        snapshots: snap + 1,
        frame: (path.length > 0 ? path : [from, to]).map((code) => airports.get(code)),
      };
      drawn = {};
      const tail = path.length > 0 ? 3 + 3 * path.length + 2 : 2;
      player.load(plan.init.length + frames + 1 + tail);
    },
    clear() {
      run = null;
      drawn = {};
      player.load(0);
      map.show({});
      queueDrawing.replaceChildren();
      cells.replaceChildren();
      $("queue-size").textContent = "";
      $("search-variables").replaceChildren();
      Object.values(views).forEach((view) => view.mark(null));
    },
  };
}
