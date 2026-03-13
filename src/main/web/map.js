// Drawing airports on a map of the United States.

const SVG = "http://www.w3.org/2000/svg";

export function svg(name, attributes = {}, text) {
  const element = document.createElementNS(SVG, name);
  for (const [key, value] of Object.entries(attributes)) {
    element.setAttribute(key, value);
  }
  if (text !== undefined) {
    element.textContent = text;
  }
  return element;
}

// Albers equal-area conic projection with the usual parallels for the US.
// Returns map coordinates with y growing downward, as SVG expects.
const RAD = Math.PI / 180;
const N = (Math.sin(29.5 * RAD) + Math.sin(45.5 * RAD)) / 2;
const C = Math.cos(29.5 * RAD) ** 2 + 2 * N * Math.sin(29.5 * RAD);
const RHO0 = Math.sqrt(C - 2 * N * Math.sin(37.5 * RAD)) / N;

export function project(lon, lat) {
  // Guam and the far Aleutians are east of the date line; keep them west of Alaska.
  if (lon > 0) {
    lon -= 360;
  }
  const rho = Math.sqrt(C - 2 * N * Math.sin(lat * RAD)) / N;
  const theta = N * (lon + 96) * RAD;
  return [1000 * rho * Math.sin(theta), -1000 * (RHO0 - rho * Math.cos(theta))];
}

function grow(box, x, y) {
  box.left = Math.min(box.left, x);
  box.right = Math.max(box.right, x);
  box.top = Math.min(box.top, y);
  box.bottom = Math.max(box.bottom, y);
}

// Turn the state outlines (a TopoJSON file) into one SVG path, and measure
// the lower 48 states so a map can frame them.
export function prepareLand(topology) {
  const [scaleX, scaleY] = topology.transform.scale;
  const [moveX, moveY] = topology.transform.translate;
  const arcs = topology.arcs.map((arc) => {
    let x = 0;
    let y = 0;
    return arc.map(([dx, dy]) => {
      x += dx;
      y += dy;
      return project(x * scaleX + moveX, y * scaleY + moveY);
    });
  });

  const outside = new Set(["02", "15", "60", "66", "69", "72", "78"]); // Alaska, Hawaii, territories
  const lower48 = { left: Infinity, top: Infinity, right: -Infinity, bottom: -Infinity };
  let path = "";
  for (const state of topology.objects.states.geometries) {
    const polygons = state.type === "Polygon" ? [state.arcs] : state.arcs;
    for (const rings of polygons) {
      for (const ring of rings) {
        const points = [];
        for (const index of ring) {
          const arc = index >= 0 ? arcs[index] : [...arcs[~index]].reverse();
          points.push(...(points.length > 0 ? arc.slice(1) : arc));
        }
        path += "M" + points.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join("L") + "Z";
        if (!outside.has(state.id)) {
          points.forEach(([x, y]) => grow(lower48, x, y));
        }
      }
    }
  }
  return { path, lower48 };
}

// One map. The outlines and a dot per airport are built once; show() then
// restyles the dots and redraws whatever lies on top of them.
export class MapView {
  // airports: the airports to draw, each with x and y.
  // land: what prepareLand returned.
  // dotSize(airport): radius in screen pixels.
  // onHover(airport, event), onLeave(), onPick(airport): optional.
  constructor(element, { airports, land, dotSize, onHover, onLeave, onPick }) {
    this.element = element;
    this.land = land;
    this.dotSize = dotSize;
    this.handlers = onPick ? { onHover, onLeave, onPick } : null;
    this.dots = new Map();
    this.picture = {};

    this.dotLayer = svg("g");
    this.overlay = svg("g");
    const located = airports.filter((airport) => airport.located);
    // Largest first, so the small ones end up on top and stay reachable.
    for (const airport of [...located].sort((a, b) => dotSize(b) - dotSize(a))) {
      const dot = svg("circle", { cx: airport.x, cy: airport.y });
      this.listen(dot, airport);
      this.dots.set(airport.code, { airport, dot, style: null });
      this.dotLayer.append(dot);
    }
    element.replaceChildren(svg("path", { class: "land", d: land.path }), this.dotLayer, this.overlay);
    new ResizeObserver(() => this.show(this.picture)).observe(element);
  }

  // Hovering or selecting a dot tells whoever asked to hear about it.
  listen(element, airport) {
    if (this.handlers) {
      element.addEventListener("pointermove", (event) => this.handlers.onHover(airport, event));
      element.addEventListener("pointerleave", this.handlers.onLeave);
      element.addEventListener("click", () => this.handlers.onPick(airport));
    }
  }

  // Draw a picture:
  //   frame: airports the view must take in, beyond the lower 48 states
  //   styleOf(code): class for an airport's dot (default "airport")
  //   lines: [{ className, pairs: [[airport, airport], ...] }]
  //   marks: [{ airport, className, label }] drawn large, on top
  show(picture) {
    this.picture = picture;
    const { frame = [], styleOf, lines = [], marks = [] } = picture;

    const view = { ...this.land.lower48 };
    frame.filter((airport) => airport.located).forEach((airport) => grow(view, airport.x, airport.y));
    const pad = 0.06 * (view.right - view.left);
    const width = view.right - view.left + 2 * pad;
    const height = view.bottom - view.top + 2 * pad;
    this.element.setAttribute("viewBox", `${view.left - pad} ${view.top - pad} ${width} ${height}`);

    // One screen pixel, in map coordinates, so dots and labels keep their size.
    const px = width / (this.element.getBoundingClientRect().width || 800);

    for (const entry of this.dots.values()) {
      const style = (styleOf && styleOf(entry.airport.code)) || "airport";
      const radius = this.dotSize(entry.airport) * px;
      if (entry.style !== style || entry.radius !== radius) {
        entry.dot.setAttribute("class", style);
        entry.dot.setAttribute("r", radius);
        entry.style = style;
        entry.radius = radius;
      }
    }

    const layers = [];
    for (const { className, pairs } of lines) {
      const d = pairs
        .filter(([a, b]) => a.located && b.located)
        .map(([a, b]) => `M${a.x.toFixed(1)},${a.y.toFixed(1)}L${b.x.toFixed(1)},${b.y.toFixed(1)}`)
        .join("");
      if (d) {
        layers.push(svg("path", { class: className, d }));
      }
    }
    const shown = marks.filter((mark) => mark.airport.located);
    for (const { airport, className } of shown) {
      const mark = svg("circle", { class: className, cx: airport.x, cy: airport.y, r: 5 * px });
      this.listen(mark, airport);
      layers.push(mark);
    }
    for (const { airport, label } of shown) {
      if (label) {
        layers.push(
          svg("text", { x: airport.x + 8 * px, y: airport.y - 7 * px, "font-size": 13 * px, "stroke-width": 3 * px }, label)
        );
      }
    }
    this.overlay.replaceChildren(...layers);
  }
}
