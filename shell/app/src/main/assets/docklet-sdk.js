/**
 * Docklet module SDK, served by the shell at /__docklet/sdk.js.
 *
 * If no native bridge is injected (browser preview), the SDK enters mock mode
 * so module UI can be developed without Android. Mock mode is never a data
 * store: modules that need real persistence must run inside Docklet.
 */
(function () {
  "use strict";

  const native = window.DockletNative || null;
  const mock = !native;
  let readySent = false;
  let lifecycleHandler = null;
  const networkListeners = new Map();

  function parseResponse(raw) {
    if (!raw) throw new Error("EMPTY_NATIVE_RESPONSE");
    const response = typeof raw === "string" ? JSON.parse(raw) : raw;
    if (!response || response.ok !== true) {
      const error =
        (response && response.error) || {
          code: "NATIVE_ERROR",
          message: "原生桥返回失败",
        };
      const wrapped = new Error(error.message || error.code || "NATIVE_ERROR");
      wrapped.code = error.code || "NATIVE_ERROR";
      wrapped.detail = error;
      throw wrapped;
    }
    return response.data;
  }

  async function request(method, params) {
    if (mock) {
      if (method === "shell.info") {
        return {
          moduleId: "demo",
          platformVersion: "1.0.0",
          shellVersion: "0.1.0",
          mock: true,
        };
      }
      if (method === "storage.open") {
        return { database: "mock://demo", schemaVersion: 0, mock: true };
      }
      if (method === "storage.query") return [];
      if (method === "storage.exec") return { changes: 0, lastInsertRowId: 0 };
      if (method === "storage.transaction") return { applied: 0 };
      if (method === "storage.backup") return { path: "mock://backup", mock: true };
      if (method === "notify.show") return { shown: false, mock: true };
      if (method === "backgroundMessages.subscribe") {
        return { subscribed: true, moduleId: "demo", mock: true };
      }
      if (method === "backgroundMessages.unsubscribe") {
        return { subscribed: false, mock: true };
      }
      if (method === "backgroundMessages.setActiveSession") {
        return { activeSession: (params && params.session) || "", mock: true };
      }
      if (method === "backgroundMessages.getState") {
        return {
          subscribed: false,
          activeSession: "",
          state: "offline",
          lastError: "",
          lastEventAt: 0,
          mock: true,
        };
      }
      if (method === "display.set") {
        return {
          mode: (params && params.mode) || "safe",
          statusBarIcons: (params && params.statusBarIcons) || "auto",
          safeContent: true,
          mock: true,
        };
      }
      if (method === "display.getState") {
        return {
          mode: "safe",
          statusBarIcons: "auto",
          safeContent: true,
          mock: true,
        };
      }
      if (method === "network.getState") {
        return {
          online: true,
          type: "wifi",
          metered: false,
          validated: true,
          changedAt: Date.now(),
          mock: true,
        };
      }
      if (method === "network.watch") {
        return {
          online: true,
          type: "wifi",
          metered: false,
          validated: true,
          changedAt: Date.now(),
          mock: true,
        };
      }
      if (method === "network.unwatch") {
        return { watching: false, mock: true };
      }
      if (method === "clipboard.read") return { text: "", chars: 0, mock: true };
      if (method === "clipboard.write") return { ok: true, chars: 0, mock: true };
      if (method === "openExternal") return { opened: true, mock: true };
      if (method === "files.write") {
        const p = params.path;
        const body = params.data || "";
        try {
          localStorage.setItem("docklet.mock.files." + p, body);
        } catch (_) {
          /* preview storage may be disabled */
        }
        return { path: p, bytes: body.length, encoding: params.encoding || "utf8" };
      }
      if (method === "files.read") {
        const p = params.path;
        let body = "";
        try {
          body = localStorage.getItem("docklet.mock.files." + p) || "";
        } catch (_) {
          /* ignore */
        }
        return {
          path: p,
          data: body,
          bytes: body.length,
          encoding: params.encoding || "utf8",
        };
      }
      if (method === "files.list") {
        const root = "docklet.mock.files.";
        const base = params.path ? String(params.path).replace(/\/+$/, "") + "/" : "";
        const entries = [];
        try {
          for (let i = 0; i < localStorage.length; i += 1) {
            const key = localStorage.key(i) || "";
            if (!key.startsWith(root)) continue;
            const relative = key.slice(root.length);
            if (!relative.startsWith(base)) continue;
            const rest = relative.slice(base.length);
            const name = rest.split("/")[0];
            if (!name || entries.some((entry) => entry.name === name)) continue;
            entries.push({
              name,
              path: base + name,
              dir: rest.includes("/"),
              size: 0,
              modified: 0,
            });
          }
        } catch (_) {
          /* ignore */
        }
        return entries;
      }
      if (method === "files.delete") {
        try {
          localStorage.removeItem("docklet.mock.files." + params.path);
        } catch (_) {
          /* ignore */
        }
        return { path: params.path, deleted: true, mock: true };
      }
      if (method === "files.pick") {
        return {
          path: "picked/mock.txt",
          name: "mock.txt",
          bytes: 0,
          mime: "text/plain",
          mock: true,
        };
      }
      if (method === "files.share") {
        return { shared: true, path: params.path, mock: true };
      }
      if (method === "files.download") {
        return { downloaded: true, path: params.path, mock: true };
      }
      if (method === "download.save") {
        return { started: true, name: params.filename || "download.bin", mock: true };
      }
      if (method === "files.exists") {
        let exists = false;
        try {
          exists = localStorage.getItem("docklet.mock.files." + params.path) !== null;
        } catch (_) {
          /* ignore */
        }
        return { path: params.path, exists, mock: true };
      }
      throw Object.assign(new Error("UNKNOWN_METHOD"), { code: "UNKNOWN_METHOD" });
    }

    const raw = native.request(
      JSON.stringify({ method: method, params: params || {} })
    );
    return parseResponse(raw);
  }

  const storage = {
    open: () => request("storage.open"),
    query: (sql, params) => request("storage.query", { sql, params: params || [] }),
    exec: (sql, params) => request("storage.exec", { sql, params: params || [] }),
    transaction: (statements) =>
      request("storage.transaction", { statements: statements || [] }),
    backup: () => request("storage.backup"),
  };

  const notify = {
    show: (options) => request("notify.show", options || {}),
  };

  const network = {
    getState: () => request("network.getState"),
    watch: (callback) => {
      if (typeof callback !== "function") return () => {};
      const id =
        "n" +
        Date.now().toString(36) +
        Math.random().toString(36).slice(2, 8);
      networkListeners.set(id, callback);
      request("network.watch", { id }).catch(() => {
        networkListeners.delete(id);
      });
      return () => {
        if (!networkListeners.delete(id)) return;
        request("network.unwatch", { id }).catch(() => {});
      };
    },
  };

  window.__dockletNetworkEvent = function (id, state) {
    const callback = networkListeners.get(id);
    if (!callback) return;
    try {
      callback(state);
    } catch (_) {
      /* a broken listener must not break the bridge */
    }
  };

  const display = {
    set: (options) => {
      const config = options || {};
      return request("display.set", {
        mode: config.mode || "",
        statusBarIcons: config.statusBarIcons || "",
      });
    },
    getState: () => request("display.getState"),
  };

  const backgroundMessages = {
    subscribe: (options) => {
      const config = options || {};
      return request("backgroundMessages.subscribe", {
        endpoint: config.endpoint,
        transport: config.transport || "sse",
        headers: config.headers || {},
        eventTypes: config.eventTypes || ["turn.end"],
        foreground: config.foreground || "silent",
        sessionField: config.sessionField || "session",
        dedupeBy: config.dedupeBy || "eventId",
        persist: config.persist !== false,
      });
    },
    unsubscribe: () => request("backgroundMessages.unsubscribe"),
    setActiveSession: (session) =>
      request("backgroundMessages.setActiveSession", {
        session: session == null ? "" : String(session),
      }),
    getState: () => request("backgroundMessages.getState"),
  };

  const files = {
    write: (path, data, options) =>
      request("files.write", {
        path,
        data: data == null ? "" : String(data),
        encoding: (options && options.encoding) || "utf8",
      }),
    read: (path, options) =>
      request("files.read", {
        path,
        encoding: (options && options.encoding) || "utf8",
      }).then((result) => result.data),
    list: (path) => request("files.list", { path: path || "" }),
    delete: (path, options) =>
      request("files.delete", {
        path,
        recursive: Boolean(options && options.recursive),
      }),
    exists: (path) => request("files.exists", { path }).then((result) => result.exists),
    pick: (options) =>
      request("files.pick", {
        mime: (options && options.mime) || "*/*",
        path: (options && options.path) || "",
      }),
    share: (path, options) =>
      request("files.share", {
        path,
        mime: (options && options.mime) || "",
      }),
    download: (path, options) =>
      request("files.download", {
        path,
        mime: (options && options.mime) || "",
      }),
  };
  files.remove = files.delete;

  const clipboard = {
    read: () => request("clipboard.read").then((result) => result.text),
    write: (text) => request("clipboard.write", { text: text == null ? "" : String(text) }),
  };

  async function openExternal(url) {
    return request("openExternal", { url });
  }

  function download(url, filename, options) {
    return request("download.save", {
      url,
      filename: filename == null ? "" : String(filename),
      headers: (options && options.headers) || {},
    });
  }

  const lifecycle = {
    setHandler(fn) {
      lifecycleHandler = typeof fn === "function" ? fn : null;
    },
  };

  const backHandlers = [];

  function onBack(handler) {
    if (typeof handler !== "function") return () => {};
    backHandlers.push(handler);
    return () => {
      const index = backHandlers.indexOf(handler);
      if (index >= 0) backHandlers.splice(index, 1);
    };
  }

  // Handlers return "handled" (the page consumed this back) or "exit" (let the
  // host close the module). Anything else is treated as "exit".
  window.__dockletBack = function () {
    for (let i = backHandlers.length - 1; i >= 0; i -= 1) {
      try {
        const result = backHandlers[i]();
        if (result === "handled") return "handled";
        if (result === "exit") return "exit";
      } catch (_) {
        /* a broken handler must not swallow back */
      }
    }
    return "exit";
  };

  function markReady() {
    if (readySent) return;
    readySent = true;
    if (native && typeof native.ready === "function") {
      try {
        native.ready();
      } catch (_) {
        /* WebView may have been destroyed. */
      }
    }
    window.dispatchEvent(new CustomEvent("docklet:ready"));
  }

  window.dockletLifecycle = async function (action, requestId) {
    try {
      if (lifecycleHandler) await lifecycleHandler(action);
      if (requestId && native) native.lifecycleResult(requestId, true, "");
    } catch (error) {
      if (requestId && native) {
        native.lifecycleResult(
          requestId,
          false,
          (error && error.message) || "LIFECYCLE_FAILED"
        );
      }
    }
  };

  window.docklet = {
    version: "1.0",
    mock,
    request,
    storage,
    files,
    clipboard,
    download,
    openExternal,
    notify,
    network,
    display,
    backgroundMessages,
    lifecycle,
    onBack,
    ready: markReady,
    shellInfo: () => request("shell.info"),
  };

  document.documentElement.classList.add(mock ? "docklet-mock" : "docklet-native");
})();
