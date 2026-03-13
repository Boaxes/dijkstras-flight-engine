// Playback controls for a replay: play and pause, step either way, and a
// scrubber. Every replay runs at the same speed. A replay starts paused and
// plays only when asked to; it pauses again if its card leaves the screen.

const STEPS_PER_SECOND = 10;

function button(label, title) {
  const element = document.createElement("button");
  element.type = "button";
  element.textContent = label;
  element.title = title;
  element.setAttribute("aria-label", title);
  return element;
}

export class Player {
  // card: the element that pauses the replay when it leaves the screen.
  // controls: where to put the buttons.
  // render(index): draw step index.
  constructor(card, controls, { render }) {
    this.render = render;
    this.count = 0;
    this.index = 0;
    this.playing = false;

    this.restart = button("⏮", "Back to the start");
    this.back = button("◀", "One step back");
    this.toggle = button("▶", "Play");
    this.forward = button("▶❘", "One step forward");
    this.scrubber = document.createElement("input");
    this.scrubber.type = "range";
    this.scrubber.min = "0";
    this.scrubber.setAttribute("aria-label", "Step");
    this.readout = document.createElement("span");
    this.readout.className = "readout";
    controls.classList.add("player");
    controls.replaceChildren(this.restart, this.back, this.toggle, this.forward, this.scrubber, this.readout);

    this.restart.addEventListener("click", () => this.seek(0, true));
    this.back.addEventListener("click", () => this.seek(this.index - 1, true));
    this.forward.addEventListener("click", () => this.seek(this.index + 1, true));
    this.toggle.addEventListener("click", () => {
      if (this.playing) {
        this.pause();
      } else {
        if (this.index >= this.count - 1) {
          this.seek(0);
        }
        this.play();
      }
    });
    this.scrubber.addEventListener("input", () => this.seek(Number(this.scrubber.value), true));

    new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting && this.playing) {
          this.pause();
        }
      },
      { threshold: 0.2 }
    ).observe(card);
    this.load(0);
  }

  // Start over with a replay of count steps.
  load(count) {
    this.pause();
    this.count = count;
    this.scrubber.max = String(Math.max(0, count - 1));
    for (const control of [this.restart, this.back, this.toggle, this.forward, this.scrubber]) {
      control.disabled = count < 2;
    }
    if (count === 0) {
      this.readout.textContent = "";
      return;
    }
    this.seek(0);
  }

  seek(index, byHand = false) {
    if (byHand) {
      this.pause();
    }
    if (this.count === 0) {
      return;
    }
    this.index = Math.max(0, Math.min(this.count - 1, index));
    this.scrubber.value = String(this.index);
    this.readout.textContent = `${(this.index + 1).toLocaleString("en-US")} / ${this.count.toLocaleString("en-US")}`;
    this.render(this.index);
  }

  play() {
    this.pause();
    this.playing = true;
    this.toggle.textContent = "⏸";
    this.toggle.title = "Pause";
    this.toggle.setAttribute("aria-label", "Pause");

    // Advance by the clock, so the speed is the same however often the
    // browser redraws.
    let last = performance.now();
    let owed = 0;
    const tick = (now) => {
      if (!this.playing) {
        return;
      }
      owed += ((now - last) / 1000) * STEPS_PER_SECOND;
      last = now;
      const steps = Math.floor(owed);
      if (steps > 0) {
        owed -= steps;
        this.seek(this.index + steps);
      }
      if (this.index >= this.count - 1) {
        this.pause();
      } else {
        requestAnimationFrame(tick);
      }
    };
    requestAnimationFrame(tick);
  }

  pause() {
    this.playing = false;
    this.toggle.textContent = "▶";
    this.toggle.title = "Play";
    this.toggle.setAttribute("aria-label", "Play");
  }
}
