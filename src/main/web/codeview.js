// Shows one method from a real Java source file, colored by syntax, with a
// bar on the line running.

const KEYWORDS = new Set(
  ("abstract boolean break byte case catch char class continue default do double else extends false final finally " +
    "float for if implements import instanceof int interface long new null package private protected public return " +
    "short static super switch this throw throws true try void while").split(" ")
);

// A comment, a string, a character, a number or a word; anything else is punctuation.
const TOKEN = /(\/\/.*$)|("(?:\\.|[^"\\])*")|('(?:\\.|[^'\\])')|(\b\d+(?:\.\d+)?\b)|([A-Za-z_]\w*)/g;

// Return a line of Java as a span, with its tokens wrapped for coloring.
function highlight(text) {
  const line = document.createElement("span");
  let last = 0;
  for (const match of text.matchAll(TOKEN)) {
    const [token, comment, string, character, number, word] = match;
    let kind = null;
    if (comment) {
      kind = "comment";
    } else if (string || character) {
      kind = "string";
    } else if (number) {
      kind = "number";
    } else if (KEYWORDS.has(word)) {
      kind = "keyword";
    } else if (/^[A-Z]/.test(word)) {
      kind = "type";
    }
    line.append(text.slice(last, match.index));
    if (kind) {
      const piece = document.createElement("span");
      piece.className = kind;
      piece.textContent = token;
      line.append(piece);
    } else {
      line.append(token);
    }
    last = match.index + token.length;
  }
  line.append(text.slice(last) || " ");
  return line;
}

export class CodeView {
  // container: where to draw. source: the text of the whole file.
  // start: text found on the method's first line.
  // hide: lines matching this are left out, along with the block they open.
  // The lines added so the page can follow the code are hidden by default,
  // so what shows is the algorithm itself.
  constructor(container, source, start, hide = /listener|visited != null/) {
    this.container = container;
    this.lines = [];
    this.running = null;

    const all = source.split("\n");
    const first = all.findIndex((line) => line.includes(start));
    const indent = all[first].length - all[first].trimStart().length;
    let depth = 0;
    let hiddenUntil = -1; // while inside a hidden block: the depth it closes at
    for (let i = first; i < all.length; i++) {
      const code = all[i].split("//")[0];
      const opens = (code.match(/{/g) || []).length;
      const closes = (code.match(/}/g) || []).length;
      const hidden = hiddenUntil >= 0 || hide.test(all[i]);
      if (hide.test(all[i]) && opens > closes && hiddenUntil < 0) {
        hiddenUntil = depth;
      }
      depth += opens - closes;
      if (hiddenUntil >= 0 && depth <= hiddenUntil) {
        hiddenUntil = -1;
      }
      if (!hidden) {
        this.lines.push({ number: i + 1, text: all[i].slice(indent).trimEnd() });
      }
      if (depth === 0 && i > first) {
        break;
      }
    }

    container.classList.add("code");
    container.replaceChildren(
      ...this.lines.map((line) => {
        const row = document.createElement("div");
        const number = document.createElement("span");
        number.className = "num";
        number.textContent = line.number;
        row.append(number, highlight(line.text));
        return row;
      })
    );
  }

  // Return the position of the first line at or after from that contains text.
  find(text, from = 0) {
    const index = this.lines.findIndex((line, i) => i >= from && line.text.includes(text));
    if (index < 0) {
      throw new Error(`No line contains: ${text}`);
    }
    return index;
  }

  // Return the positions of the lines of code from one position up to, but
  // not including, another: blank lines and comments are left out.
  between(from, to) {
    const found = [];
    for (let i = from; i < to; i++) {
      const text = this.lines[i].text.trim();
      if (text && !text.startsWith("//") && text !== "}") {
        found.push(i);
      }
    }
    return found;
  }

  // Put the bar on a line, or on none.
  mark(index) {
    if (this.running === index) {
      return;
    }
    if (this.running !== null) {
      this.container.children[this.running].classList.remove("running");
    }
    this.running = index;
    if (index === null) {
      return;
    }
    const row = this.container.children[index];
    row.classList.add("running");
    // Keep the running line in view without moving the page.
    const top = row.offsetTop; // the container is the row's offset parent
    if (top < this.container.scrollTop + 20) {
      this.container.scrollTop = Math.max(0, top - 20);
    } else if (top + row.offsetHeight > this.container.scrollTop + this.container.clientHeight - 20) {
      this.container.scrollTop = top + row.offsetHeight - this.container.clientHeight + 20;
    }
  }
}
