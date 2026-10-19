const STATE_LABEL = { ONLINE: "Online", OFFLINE: "Offline", UNAUTHORIZED: "Unauthorized" };
const POLL_MS = 5000;

// Model names and serials are reported by the device itself, so they are escaped before being
// put into HTML.
function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[c]);
}

function formatTime(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString();
}

function deviceCard(d) {
  const state = STATE_LABEL[d.connectionState] ? d.connectionState : "OFFLINE";
  const disabled = state !== "ONLINE" ? "disabled" : "";
  return `
    <div class="device" data-serial="${escapeHtml(d.serial)}">
      <div class="device-top">
        <div>
          <div class="device-name">${escapeHtml(d.model)}</div>
          <div class="device-serial">${escapeHtml(d.serial)}</div>
        </div>
        <span class="pill pill--${state.toLowerCase()}">${STATE_LABEL[state]}</span>
      </div>
      <div class="device-meta">
        <span>Battery: <strong>${d.batteryLevel != null ? escapeHtml(d.batteryLevel) + "%" : "—"}</strong></span>
        <span>Screen: <strong>${escapeHtml(d.screenState ?? "—")}</strong></span>
        <span>Checked: <strong>${formatTime(d.lastChecked)}</strong></span>
      </div>
      <div class="device-actions">
        <button class="btn" type="button" data-action="screenshot" ${disabled}>Screenshot</button>
        <button class="btn" type="button" data-action="battery" ${disabled}>Battery info</button>
        <button class="btn" type="button" data-action="processes" ${disabled}>Processes</button>
      </div>
    </div>
  `;
}

let lastRendered = null;

async function refresh() {
  const container = document.getElementById("devices");
  let html;
  try {
    const res = await fetch("/api/devices", { cache: "no-store" });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const devices = await res.json();
    html = devices.length
      ? devices.map(deviceCard).join("")
      : "<p class='empty-state'>No devices found. Plug in an Android device with USB debugging enabled.</p>";
  } catch (err) {
    html = "<p class='empty-state'>Couldn't reach the monitor service. Retrying…</p>";
    console.error(err);
  }
  // Re-rendering replaces the buttons, which would drop keyboard focus every few seconds, so
  // only touch the DOM when something actually changed.
  if (html !== lastRendered) {
    container.innerHTML = html;
    lastRendered = html;
  }
}

function openPanel(title) {
  document.getElementById("panel-title").textContent = title;
  document.getElementById("diagnostics-panel").classList.remove("hidden");
}

function errorMessage(status) {
  if (status === 404) return "This device is no longer connected.";
  if (status === 409) return "This device is not online.";
  return "The device did not respond. Check the USB connection and try again.";
}

document.getElementById("panel-close").addEventListener("click", () => {
  document.getElementById("diagnostics-panel").classList.add("hidden");
});

document.getElementById("devices").addEventListener("click", async (e) => {
  const btn = e.target.closest("button[data-action]");
  if (!btn) return;
  const serial = btn.closest(".device").dataset.serial;
  const action = btn.dataset.action;
  const content = document.getElementById("panel-content");
  const url = `/api/devices/${encodeURIComponent(serial)}`;

  btn.disabled = true;
  try {
    if (action === "screenshot") {
      openPanel(`Screenshot — ${serial}`);
      content.textContent = "Capturing…";
      const res = await fetch(`${url}/screenshot`, { method: "POST" });
      if (!res.ok) { content.textContent = errorMessage(res.status); return; }
      const img = document.createElement("img");
      img.alt = `Screenshot of ${serial}`;
      img.src = URL.createObjectURL(await res.blob());
      content.replaceChildren(img);
      return;
    }

    openPanel(`${btn.textContent} — ${serial}`);
    content.textContent = "Loading…";
    const res = await fetch(`${url}/diagnostics/${encodeURIComponent(action)}`, { method: "POST" });
    if (!res.ok) { content.textContent = errorMessage(res.status); return; }
    const lines = await res.json();
    content.textContent = lines.length ? lines.join("\n") : "No output.";
  } catch (err) {
    content.textContent = "Couldn't reach the monitor service.";
    console.error(err);
  } finally {
    btn.disabled = false;
  }
});

refresh();
setInterval(refresh, POLL_MS);
