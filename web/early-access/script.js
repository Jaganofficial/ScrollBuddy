"use strict";

// Where sign-ups are sent: the web app address of the Google Apps Script that fills the
// sign-up sheet (web/signup-sheet/Code.gs), which ends in /exec. While it is empty the form
// sends nothing and says so.
const FORM_ENDPOINT =
  "https://script.google.com/macros/s/AKfycbx6a-a_vLUBbrdO4HY9LcFhivYfF5bjL6cxDwAUkoDM2V4gSeS3IJPdIEC-8E6nhXc1sg/exec";

// Adding ?demo to the page's address makes the form behave as if a sign-up had gone through,
// without sending anything, so the success message and confetti can be seen before a form
// service is connected.
const DEMO = new URLSearchParams(location.search).has("demo");

const REDUCED_MOTION = matchMedia("(prefers-reduced-motion: reduce)").matches;
const PALETTE = ["#ffb4a8", "#e2574a", "#b7c4ff", "#ecebf4"];

document.documentElement.classList.add("js");

/* ---------- Sections appearing as they scroll into view ---------- */

const appear = new IntersectionObserver(
  (entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      entry.target.classList.add("in");
      appear.unobserve(entry.target);
    }
  },
  { threshold: 0.15 },
);

for (const element of document.querySelectorAll(".reveal")) {
  // Cards in one row arrive one after another instead of all at once
  const position = [...element.parentElement.children].indexOf(element);
  element.style.setProperty("--delay", `${Math.min(position, 7) * 70}ms`);
  appear.observe(element);
}

/* ---------- Drifting particles ---------- */

// Small rounded cards and dots rising slowly, like reels going by. Each canvas runs only while
// it is on screen and the tab is in front.
class Drift {
  constructor(canvas) {
    this.canvas = canvas;
    this.context = canvas.getContext("2d");
    this.pointer = { x: 0, y: 0 };
    this.running = false;
    this.visible = false;
    this.resize();
    new ResizeObserver(() => this.resize()).observe(canvas);
    new IntersectionObserver(([entry]) => {
      this.visible = entry.isIntersecting;
      this.sync();
    }).observe(canvas);
    document.addEventListener("visibilitychange", () => this.sync());
    canvas.parentElement.addEventListener("pointermove", (event) => {
      const box = canvas.getBoundingClientRect();
      this.pointer.x = (event.clientX - box.left) / box.width - 0.5;
      this.pointer.y = (event.clientY - box.top) / box.height - 0.5;
    });
  }

  resize() {
    const ratio = Math.min(devicePixelRatio || 1, 2);
    this.width = this.canvas.clientWidth;
    this.height = this.canvas.clientHeight;
    this.canvas.width = this.width * ratio;
    this.canvas.height = this.height * ratio;
    this.context.setTransform(ratio, 0, 0, ratio, 0, 0);
    // About one particle for every 16,000 square pixels, within reason
    const count = Math.max(24, Math.min(90, Math.round((this.width * this.height) / 16000)));
    this.particles = Array.from({ length: count }, () => this.particle(true));
  }

  particle(anywhere) {
    const card = Math.random() < 0.45;
    return {
      x: Math.random() * this.width,
      y: anywhere ? Math.random() * this.height : this.height + 20,
      size: card ? 6 + Math.random() * 10 : 1 + Math.random() * 2.2,
      speed: 0.15 + Math.random() * 0.5,
      sway: Math.random() * Math.PI * 2,
      spin: (Math.random() - 0.5) * 0.01,
      angle: Math.random() * Math.PI,
      // Nearer particles move more with the pointer
      depth: 0.3 + Math.random() * 0.7,
      alpha: card ? 0.08 + Math.random() * 0.14 : 0.25 + Math.random() * 0.5,
      colour: PALETTE[Math.floor(Math.random() * PALETTE.length)],
      card,
    };
  }

  sync() {
    const shouldRun = this.visible && !document.hidden;
    if (shouldRun && !this.running) {
      this.running = true;
      requestAnimationFrame(() => this.frame());
    } else if (!shouldRun) {
      this.running = false;
    }
  }

  frame() {
    if (!this.running) return;
    const { context, width, height, pointer } = this;
    context.clearRect(0, 0, width, height);
    this.particles.forEach((particle, index) => {
      particle.y -= particle.speed;
      particle.sway += 0.01;
      particle.angle += particle.spin;
      if (particle.y < -30) this.particles[index] = particle = this.particle(false);
      const x = particle.x + Math.sin(particle.sway) * 12 - pointer.x * 40 * particle.depth;
      const y = particle.y - pointer.y * 26 * particle.depth;
      context.globalAlpha = particle.alpha;
      context.fillStyle = particle.colour;
      if (particle.card) {
        context.save();
        context.translate(x, y);
        context.rotate(particle.angle);
        context.beginPath();
        context.roundRect(-particle.size * 0.56, -particle.size, particle.size * 1.12, particle.size * 2, 3);
        context.fill();
        context.restore();
      } else {
        context.beginPath();
        context.arc(x, y, particle.size, 0, Math.PI * 2);
        context.fill();
      }
    });
    context.globalAlpha = 1;
    requestAnimationFrame(() => this.frame());
  }
}

if (!REDUCED_MOTION) {
  for (const canvas of document.querySelectorAll(".particles")) new Drift(canvas);
}

/* ---------- The light that follows the pointer across a card ---------- */

for (const card of document.querySelectorAll(".card")) {
  card.addEventListener("pointermove", (event) => {
    const box = card.getBoundingClientRect();
    card.style.setProperty("--mx", `${event.clientX - box.left}px`);
    card.style.setProperty("--my", `${event.clientY - box.top}px`);
  });
}

/* ---------- What Vaathi says, one line after another ---------- */

const LINES = [
  "Podhum, Nanba. Phone-a keezha vai.",
  "Class mudinjiduchu. Phone-a ulla vai.",
  "Life is very short, Nanba. Adha reels-la tholaikkaadha.",
  "Yaarum paakala-nu nenachiya? Vaathi inga dhaan irukken.",
  "I am waiting… andha phone keezha pora varaikkum.",
];
const says = document.querySelector("[data-says]");
const saysText = document.querySelector("[data-says-text]");

if (says && !REDUCED_MOTION) {
  let line = 0;
  setInterval(() => {
    says.classList.add("is-changing");
    setTimeout(() => {
      line = (line + 1) % LINES.length;
      saysText.textContent = LINES[line];
      says.classList.remove("is-changing");
    }, 350);
  }, 3800);
}

/* ---------- The count bubble: counting up, then warning, as in the app ---------- */

const chip = document.querySelector("[data-chip]");
const chipText = document.querySelector("[data-chip-text]");
const CHIP_LIMIT = 20;
const CHIP_WARN_LEFT = 3;

if (chip && !REDUCED_MOTION) {
  let count = 11;
  setInterval(() => {
    count = count >= CHIP_LIMIT ? 12 : count + 1;
    const left = CHIP_LIMIT - count;
    const warning = left <= CHIP_WARN_LEFT;
    chip.classList.toggle("is-warning", warning);
    chipText.textContent = warning ? `Vaathi coming… ${left} left` : `${count} reels today`;
  }, 900);
} else if (chipText) {
  chipText.textContent = "12 reels today";
}

/* ---------- Confetti for a sign-up that went through ---------- */

const confettiCanvas = document.querySelector(".confetti");

function celebrate(fromX, fromY) {
  if (REDUCED_MOTION || !confettiCanvas) return;
  const context = confettiCanvas.getContext("2d");
  const ratio = Math.min(devicePixelRatio || 1, 2);
  confettiCanvas.width = innerWidth * ratio;
  confettiCanvas.height = innerHeight * ratio;
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  const pieces = Array.from({ length: 150 }, () => {
    const angle = -Math.PI / 2 + (Math.random() - 0.5) * 2.2;
    const speed = 6 + Math.random() * 12;
    return {
      x: fromX,
      y: fromY,
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      size: 5 + Math.random() * 7,
      angle: Math.random() * Math.PI,
      spin: (Math.random() - 0.5) * 0.4,
      colour: PALETTE[Math.floor(Math.random() * PALETTE.length)],
      life: 1,
    };
  });
  const step = () => {
    context.clearRect(0, 0, innerWidth, innerHeight);
    let alive = false;
    for (const piece of pieces) {
      piece.vy += 0.32;
      piece.vx *= 0.99;
      piece.x += piece.vx;
      piece.y += piece.vy;
      piece.angle += piece.spin;
      piece.life -= 0.008;
      if (piece.life <= 0 || piece.y > innerHeight + 40) continue;
      alive = true;
      context.save();
      context.globalAlpha = Math.min(1, piece.life * 2);
      context.translate(piece.x, piece.y);
      context.rotate(piece.angle);
      context.fillStyle = piece.colour;
      context.fillRect(-piece.size / 2, -piece.size / 3, piece.size, piece.size * 0.66);
      context.restore();
    }
    if (alive) requestAnimationFrame(step);
    else context.clearRect(0, 0, innerWidth, innerHeight);
  };
  requestAnimationFrame(step);
}

/* ---------- The sign-up forms ---------- */

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

async function send(email) {
  if (DEMO) return true;
  // The JSON goes as plain text on purpose. A JSON content type makes the browser ask the
  // script's permission before posting, and an Apps Script web app can't answer that question
  const response = await fetch(FORM_ENDPOINT, {
    method: "POST",
    headers: { "Content-Type": "text/plain;charset=utf-8" },
    body: JSON.stringify({ email }),
  });
  if (!response.ok) return false;
  // A script that fails still answers 200, with an error page: only its own "ok" counts
  const answer = await response.json();
  return answer.ok === true;
}

for (const form of document.querySelectorAll(".signup")) {
  const input = form.querySelector("input");
  const button = form.querySelector("button");
  const status = form.querySelector(".signup-status");
  const say = (message, kind) => {
    status.textContent = message;
    status.className = `signup-status ${kind}`;
  };

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const email = input.value.trim();
    if (!EMAIL.test(email)) {
      say("That doesn't look like an email address, Nanba.", "warn");
      input.focus();
      return;
    }
    if (!FORM_ENDPOINT && !DEMO) {
      say("Sign-ups aren't switched on yet: this form isn't connected to a list.", "warn");
      return;
    }
    button.disabled = true;
    try {
      if (!(await send(email))) throw new Error("The sign-up sheet refused the address");
      say("You're on the list, Nanba! Vaathi will come find you.", "ok");
      input.value = "";
      const box = button.getBoundingClientRect();
      celebrate(box.left + box.width / 2, box.top);
    } catch (error) {
      say("That didn't go through. Please try again in a moment.", "warn");
    } finally {
      button.disabled = false;
    }
  });
}
