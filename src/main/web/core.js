// What both pages share: loading the Java module and the data, naming
// airports, and the box you type an airport into.

import { project } from "./map.js";

export const $ = (id) => document.getElementById(id);
export const number = new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 });

export function span(className, text) {
  const element = document.createElement("span");
  element.className = className;
  element.textContent = text;
  return element;
}

export async function fetchText(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${url} returned ${response.status}`);
  }
  return response.text();
}

// Load the Java module and build the flight network in it. Returns the
// exported Java methods, the airports (by code, and as an alphabetical
// list), the text of the flight file, and the top of the search tree.
export async function loadApp() {
  const [teavm, edges, airportList] = await Promise.all([
    TeaVM.wasmGC.load("flight-engine.wasm"),
    fetchText("data/edges.csv"),
    fetchText("data/airports.tsv"),
  ]);
  const java = teavm.exports;
  const network = JSON.parse(java.load(edges, airportList));
  const airports = new Map();
  for (const airport of network.airports) {
    if (airport.located) {
      [airport.x, airport.y] = project(airport.lon, airport.lat);
    }
    airports.set(airport.code, airport);
  }
  return { java, airports, list: network.airports, edges, tree: network.tree };
}

export function city(airport) {
  return airport.city || airport.code;
}

export function place(airport) {
  if (!airport.city) {
    return airport.code;
  }
  return airport.region ? `${airport.city}, ${airport.region}` : airport.city;
}

export function shortLabel(airport) {
  return airport.city ? `${airport.code} · ${place(airport)}` : airport.code;
}

// Make a text box into an airport picker with suggestions under it.
//   chosen(): the code currently chosen, or null
//   onPick(code): an airport was chosen, or un-chosen (null) by typing over it
//   onLookup(found, typed): Java was asked what matches the typed text
export function setUpPicker({ input, options, java, airports, chosen, onPick, onLookup }) {
  let codes = [];
  let active = -1;

  input.disabled = false;

  function close() {
    options.hidden = true;
    input.setAttribute("aria-expanded", "false");
    input.removeAttribute("aria-activedescendant");
    active = -1;
  }

  function setActive(index) {
    active = index;
    [...options.children].forEach((option, i) => option.setAttribute("aria-selected", String(i === active)));
    if (active >= 0) {
      input.setAttribute("aria-activedescendant", options.children[active].id);
      options.children[active].scrollIntoView({ block: "nearest" });
    }
  }

  function pick(code) {
    close();
    onPick(code);
  }

  input.addEventListener("input", () => {
    if (chosen()) {
      onPick(null);
    }
    const found = JSON.parse(java.suggest(input.value, 8));
    if (onLookup) {
      onLookup(found, input.value.trim());
    }
    codes = found.codes;
    options.replaceChildren(
      ...codes.map((code, i) => {
        const airport = airports.get(code);
        const option = document.createElement("li");
        option.id = `${input.id}-option-${i}`;
        option.setAttribute("role", "option");
        option.append(span("code", code), ` ${airport.city ? place(airport) : ""}`, span("name", airport.name));
        // mousedown, not click: it fires before the input loses focus
        option.addEventListener("mousedown", (event) => {
          event.preventDefault();
          pick(code);
        });
        return option;
      })
    );
    options.hidden = codes.length === 0;
    input.setAttribute("aria-expanded", String(codes.length > 0));
    setActive(codes.length > 0 ? 0 : -1);
  });

  input.addEventListener("keydown", (event) => {
    if (options.hidden) {
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((active + 1) % codes.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((active - 1 + codes.length) % codes.length);
    } else if (event.key === "Enter") {
      event.preventDefault();
      pick(codes[Math.max(active, 0)]);
    } else if (event.key === "Escape") {
      close();
    }
  });

  input.addEventListener("focus", () => input.select());
  input.addEventListener("blur", () => {
    close();
    // Leaving the box without choosing puts back whatever was chosen before.
    input.value = chosen() ? shortLabel(airports.get(chosen())) : "";
  });
}
