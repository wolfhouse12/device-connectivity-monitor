const STATE_LABEL = { ONLINE: "Online", OFFLINE: "Offline", UNAUTHORIZED: "Unauthorized" };
const POLL_MS = 5000;

function formatTime(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleTimeString();
}

function deviceCard(d) {
  const state = d.connectionState || "OFFLINE";
  return `
    <div class="device" data-serial="${d.serial}">
      <div class="device-top">
        <div>
          <div class="device-name">${d.model}</div>
          <div class="device-serial">${d.serial}</div>
        </div>
        <span class="pill pill--${state.toLowerCase()}">${STATE_LABEL[state] ?? state}</span>
      </div>
      <div class="device-meta">
        <span>Battery: <strong>${d.batteryLevel != null ? d.batteryLevel + "%" : "—"}</strong></span>
        <span>Screen: <strong>${d.screenState ?? "—"}</strong></span>
        <span>Checked: <strong>${formatTime(d.lastChecked)}</strong></span>
      </div>
      <div class="device-actions">
        <button class="btn" data-action="screenshot" ${state !== "ONLINE" ? "disabled" : ""}>Screenshot</button>
        <button class="btn" data-action="battery" ${state !== "ONLINE" ? "disabled" : ""}>Battery info</button>
        <button class="btn" data-action="processes" ${state !== "ONLINE" ? "disabled" : ""}>Processes</button>
      </div>
    </div>
  `;
}

async function refresh() {
  const container = document.getElementById("devices");
  try {
    const res = await fetch("/api/devices", { cache: "no-store" });
    const devices = await res.json();
    container.innerHTML = devices.length
      ? devices.map(deviceCard).join("")
      : "<p class='empty-state'>No devices found. Plug in an Android device with USB debugging enabled.</p>";
  } catch (err) {
    container.innerHTML = "<p class='empty-state'>Couldn't reach the monitor service.</p>";
    console.error(err);
  }
}

function openPanel(title) {
  document.getElementById("panel-title").textContent = title;
  document.getElementById("diagnostics-panel").classList.remove("hidden");
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

  if (action === "screenshot") {
    openPanel(`Screenshot — ${serial}`);
    content.innerHTML = "Capturing…";
    const res = await fetch(`/api/devices/${serial}/screenshot`, { method: "POST" });
    if (!res.ok) { content.textContent = "Failed to capture screenshot."; return; }
    const blob = await res.blob();
    content.innerHTML = `<img src="${URL.createObjectURL(blob)}" alt="Device screenshot" />`;
    return;
  }

  openPanel(`${btn.textContent} — ${serial}`);
  content.textContent = "Loading…";
  const res = await fetch(`/api/devices/${serial}/diagnostics/${action}`, { method: "POST" });
  const lines = await res.json();
  content.textContent = lines.join("\n");
});

refresh();
setInterval(refresh, POLL_MS);
