// The busiest-flights page: pick an airport and how many to show, and see
// its busiest flights as a chart, with the merge sort that ranked them.

import { CodeView } from "./codeview.js";
import { $, city, fetchText, loadApp, number, place, span } from "./core.js";
import { svg } from "./map.js";
import { Player } from "./player.js";

const STATES = {
  AL: "Alabama", AK: "Alaska", AZ: "Arizona", AR: "Arkansas", CA: "California", CO: "Colorado",
  CT: "Connecticut", DE: "Delaware", DC: "District of Columbia", FL: "Florida", GA: "Georgia", HI: "Hawaii",
  ID: "Idaho", IL: "Illinois", IN: "Indiana", IA: "Iowa", KS: "Kansas", KY: "Kentucky", LA: "Louisiana",
  ME: "Maine", MD: "Maryland", MA: "Massachusetts", MI: "Michigan", MN: "Minnesota", MS: "Mississippi",
  MO: "Missouri", MT: "Montana", NE: "Nebraska", NV: "Nevada", NH: "New Hampshire", NJ: "New Jersey",
  NM: "New Mexico", NY: "New York", NC: "North Carolina", ND: "North Dakota", OH: "Ohio", OK: "Oklahoma",
  OR: "Oregon", PA: "Pennsylvania", RI: "Rhode Island", SC: "South Carolina", SD: "South Dakota",
  TN: "Tennessee", TX: "Texas", UT: "Utah", VT: "Vermont", VA: "Virginia", WA: "Washington",
  WV: "West Virginia", WI: "Wisconsin", WY: "Wyoming",
  AS: "American Samoa", GU: "Guam", MP: "Northern Mariana Islands", PR: "Puerto Rico", VI: "US Virgin Islands",
};

const state = { airport: null, show: "10" };
let app; // the Java methods and the airports
let flights = []; // every flight out of the chosen airport, busiest first
let sortReplay;


start().catch((error) => {
  console.error(error);
  $("sentence").textContent = "The flight network could not be loaded: " + error.message;
});

async function start() {
  const [loaded, sorts] = await Promise.all([loadApp(), fetchText("source/sort/Sorts.java")]);
  app = loaded;
  sortReplay = sortCard(sorts);
  setUpAirportMenu();
  $("show").addEventListener("change", () => {
    state.show = $("show").value;
    draw();
  });
}

// The airport dropdown: a folder for each state, opening to its airports.
function setUpAirportMenu() {
  const button = $("airport");
  const menu = $("airport-menu");

  const byState = new Map();
  for (const airport of app.list) {
    const name = STATES[airport.region] || "Other";
    if (!byState.has(name)) {
      byState.set(name, []);
    }
    byState.get(name).push(airport);
  }

  function close() {
    menu.hidden = true;
    button.setAttribute("aria-expanded", "false");
  }

  menu.replaceChildren(
    ...[...byState.keys()].sort().map((name) => {
      const folder = document.createElement("details");
      const summary = document.createElement("summary");
      summary.append(name, span("count", byState.get(name).length));
      const airports = document.createElement("ul");
      for (const airport of byState.get(name).sort((a, b) => (city(a) < city(b) ? -1 : 1))) {
        const item = document.createElement("li");
        const choice = document.createElement("button");
        choice.type = "button";
        choice.append(span("code", airport.code), ` ${city(airport)}`);
        choice.title = airport.name;
        choice.addEventListener("click", () => {
          close();
          button.textContent = `${airport.code} · ${place(airport)}`;
          button.focus();
          choose(airport.code);
        });
        item.append(choice);
        airports.append(item);
      }
      folder.append(summary, airports);
      return folder;
    })
  );

  button.disabled = false;
  button.addEventListener("click", () => {
    menu.hidden = !menu.hidden;
    button.setAttribute("aria-expanded", String(!menu.hidden));
  });
  document.addEventListener("click", (event) => {
    if (!menu.hidden && !menu.contains(event.target) && event.target !== button) {
      close();
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !menu.hidden) {
      close();
      button.focus();
    }
  });
}

function choose(code) {
  state.airport = code;
  const busiest = JSON.parse(app.java.busiestFrom(code));
  flights = busiest.flights;
  draw();
  sortReplay.show(busiest.sort);
}

function band(miles) {
  return miles < 500 ? "short" : miles < 1500 ? "medium" : "long";
}

function draw() {
  if (!state.airport) {
    return;
  }
  const from = city(app.airports.get(state.airport));
  const all = state.show === "all" || Number(state.show) >= flights.length;
  const shown = all ? flights : flights.slice(0, Number(state.show));
  const count = shown.length;
  $("sentence").textContent = count === 0 ? `There are no flights out of ${from}.` : "";
  $("chart-legend").hidden = count === 0;

  const most = count > 0 ? shown[0].count : 1;
  const tooltip = $("tooltip");
  $("chart").replaceChildren(
    ...shown.map((flight) => {
      const to = app.airports.get(flight.to);
      const row = document.createElement("li");
      const bar = span(`bar ${band(flight.miles)}`, "");
      bar.style.width = `${Math.max(0.5, (100 * flight.count) / most)}%`;
      const track = span("track", "");
      track.append(bar);
      row.append(span("place", `${flight.to} · ${city(to)}`), track, span("count", number.format(flight.count)));

      row.addEventListener("pointermove", (event) => {
        const panel = tooltip.parentElement.getBoundingClientRect();
        tooltip.replaceChildren(
          `${flight.to} · ${place(to)}`,
          span("muted", to.name),
          span("muted", `${number.format(flight.count)} tickets · ${number.format(flight.miles)} mi`)
        );
        tooltip.hidden = false;
        const left = Math.min(event.clientX - panel.left + 14, panel.width - tooltip.offsetWidth - 4);
        tooltip.style.left = `${Math.max(4, left)}px`;
        tooltip.style.top = `${event.clientY - panel.top + 14}px`;
      });
      row.addEventListener("pointerleave", () => {
        tooltip.hidden = true;
      });
      return row;
    })
  );
}

// The merge sort that ranked the flights, replayed against its own code.
function sortCard(source) {
  const code = new CodeView($("sort-code"), source, "public void mergeSort(");
  const check = code.find("if (end - start < 2)");
  const line = {
    check,
    back: code.find("return;", check),
    mid: code.find("int mid = (start + end) / 2"),
    left: code.find("mergeSort(A, start, mid)"),
    right: code.find("mergeSort(A, mid, end)"),
    merge: code.find("merge(A, start, mid, end)"),
  };
  const drawing = $("sort-drawing");
  let sort = null;
  let frames = [];

  const player = new Player($("card-sort"), $("sort-controls"), {
    render(k) {
      const frame = frames[k];
      code.mark(frame.line);

      const n = sort.labels.length;
      const most = Math.max(1, ...sort.keys);
      const width = 720;
      const slot = Math.min(40, (width - 8) / Math.max(1, n));
      const origin = (width - slot * n) / 2;
      const spacer = slot > 6 ? 2 : 0.5;
      drawing.setAttribute("viewBox", `0 0 ${width} 132`);
      drawing.replaceChildren(
        ...frame.order.map((item, position) => {
          const height = Math.max(2, (120 * sort.keys[item]) / most);
          const bar = svg("rect", {
            class: position >= frame.start && position < frame.end ? "bar active" : "bar",
            x: origin + position * slot + spacer / 2,
            y: 126 - height,
            width: slot - spacer,
            height,
          });
          bar.append(svg("title", {}, `${sort.labels[item]}: ${number.format(sort.keys[item])} tickets`));
          return bar;
        })
      );

      const strip = $("sort-variables");
      strip.replaceChildren();
      const values = { start: frame.start, end: frame.end };
      if (frame.end - frame.start >= 2) {
        values.mid = (frame.start + frame.end) >> 1;
      }
      for (const [name, value] of Object.entries(values)) {
        const item = span("variable", "");
        item.append(span("name", name), ` ${value}`);
        strip.append(item);
      }
    },
  });

  return {
    // record: every call to mergeSort and every merge, in the order they happened.
    show(record) {
      sort = record;
      frames = [];
      let order = [...sort.labels.keys()];
      const calls = []; // the calls still running, innermost last
      const push = (which, start, end) => frames.push({ line: line[which], start, end, order });
      for (const event of sort.events) {
        const [kind, start, end] = event;
        if (kind === 0) {
          // A call made from inside another runs that one's recursive line first.
          const caller = calls[calls.length - 1];
          if (caller) {
            push(caller.made === 0 ? "left" : "right", caller.start, caller.end);
            caller.made++;
          }
          push("check", start, end);
          if (end - start < 2) {
            push("back", start, end);
          } else {
            push("mid", start, end);
            calls.push({ start, end, made: 0 });
          }
        } else {
          order = event.slice(3);
          push("merge", start, end);
          calls.pop();
        }
      }
      player.load(frames.length);
    },
  };
}
