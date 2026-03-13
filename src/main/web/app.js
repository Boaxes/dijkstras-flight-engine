// The routes page: two airport pickers, the route between them, and a map.
// All routing and airport lookup happen in the Java module
// (flight-engine.wasm); explain.js replays what that code did.

import { $, city, fetchText, loadApp, number, place, setUpPicker, shortLabel, span } from "./core.js";
import { createExplanation } from "./explain.js";
import { MapView, prepareLand } from "./map.js";

const state = { from: null, to: null, mode: "miles", route: null };
let app; // the Java methods and the airports
let map;
let explanation;

start().catch((error) => {
  console.error(error);
  $("result").textContent = "The flight network could not be loaded: " + error.message;
});

async function start() {
  const [loaded, topology, paths, parser, avl] = await Promise.all([
    loadApp(),
    fetchText("data/states-10m.json").then(JSON.parse),
    fetchText("source/graph/ShortestPaths.java"),
    fetchText("source/graph/DB1BParser.java"),
    fetchText("source/avl/AVL.java"),
  ]);
  app = loaded;
  const land = prepareLand(topology);

  const most = Math.max(...app.list.map((airport) => airport.departures));
  map = new MapView($("map"), {
    airports: app.list,
    land,
    dotSize: (airport) => 2 + 4 * Math.sqrt(airport.departures / most),
    onHover: showTooltip,
    onLeave: () => {
      $("tooltip").hidden = true;
    },
    onPick(airport) {
      $("tooltip").hidden = true;
      choose(state.from ? { to: airport.code } : { from: airport.code });
    },
  });
  explanation = createExplanation({ app, land, sources: { paths, parser, avl } });

  for (const which of ["from", "to"]) {
    setUpPicker({
      input: $(which),
      options: $(which + "-options"),
      java: app.java,
      airports: app.airports,
      chosen: () => state[which],
      onPick(code) {
        // Typing over a chosen airport un-chooses it without touching the box.
        if (code === null) {
          state[which] = null;
          update();
        } else {
          choose({ [which]: code });
        }
      },
      onLookup(found) {
        if (found.tree) {
          explanation.tree.show(found.tree);
        }
      },
    });
  }
  $("swap").disabled = false;
  $("swap").addEventListener("click", () => choose({ from: state.to, to: state.from }));
  for (const radio of document.querySelectorAll('input[name="mode"]')) {
    radio.addEventListener("change", () => {
      state.mode = radio.value;
      update();
    });
  }

  // For anyone who would rather not type: Bellingham to Burlington, and
  // the lookup that typing the first of them would have run.
  $("example").disabled = false;
  $("example").addEventListener("click", () => {
    choose({ from: "BLI", to: "BTV" });
    explanation.tree.show(JSON.parse(app.java.suggest("BLI", 8)).tree);
  });

  update();
}

function choose(change) {
  Object.assign(state, change);
  $("from").value = state.from ? shortLabel(app.airports.get(state.from)) : "";
  $("to").value = state.to ? shortLabel(app.airports.get(state.to)) : "";
  update();
}

function update() {
  state.route = null;
  if (state.from && state.to && state.from !== state.to) {
    state.route = JSON.parse(app.java.route(state.from, state.to, state.mode === "flights"));
    explanation.search.show(state.route, state.from, state.to, state.mode);
  } else {
    explanation.search.clear();
  }
  showResult();
  drawMap();
}

const WORDS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"];

function showResult() {
  const result = $("result");
  if (!state.from || !state.to || state.from === state.to) {
    result.textContent = "";
    return;
  }
  const from = city(app.airports.get(state.from));
  const to = city(app.airports.get(state.to));
  const route = state.route;
  if (!route.found) {
    result.textContent = `There is no route from ${from} to ${to}.`;
    return;
  }

  const flights = route.legs.length;
  const sentence = document.createElement("p");
  sentence.textContent =
    `The ${state.mode === "flights" ? "fewest-flights" : "lowest mileage"} trip from ${from} to ${to} is ` +
    `${number.format(route.miles)} miles over the course of ${WORDS[flights] || flights} ${flights === 1 ? "flight" : "flights"}.`;
  const legs = document.createElement("ol");
  for (const leg of route.legs) {
    const item = document.createElement("li");
    item.append(span("leg", `${leg.from} → ${leg.to}`), span("miles", `${number.format(leg.miles)} mi`));
    legs.append(item);
  }
  result.replaceChildren(sentence, legs);
}

function drawMap() {
  // The airports to mark: the route's stops, or whichever ends are chosen.
  const found = state.route && state.route.found;
  const stops = found
    ? [state.route.legs[0].from, ...state.route.legs.map((leg) => leg.to)]
    : [state.from, state.to].filter(Boolean);
  const marked = stops.map((code) => app.airports.get(code));

  map.show({
    frame: marked,
    lines: found ? [{ className: "route-line", pairs: marked.slice(1).map((airport, i) => [marked[i], airport]) }] : [],
    marks: marked.map((airport) => ({ airport, className: "stop", label: airport.code })),
  });
}

function showTooltip(airport, event) {
  const tooltip = $("tooltip");
  const panel = tooltip.parentElement.getBoundingClientRect();
  tooltip.replaceChildren(
    `${airport.code} · ${place(airport)}`,
    span("muted", airport.name),
    span("muted", `${number.format(airport.departures)} tickets departing`)
  );
  tooltip.hidden = false;
  const left = Math.min(event.clientX - panel.left + 14, panel.width - tooltip.offsetWidth - 4);
  tooltip.style.left = `${Math.max(4, left)}px`;
  tooltip.style.top = `${event.clientY - panel.top + 14}px`;
}
