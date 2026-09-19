import { t, locale, hasMessage } from "./i18n";
import { ref, computed } from "vue";
import type { State, Session } from "./types";
import { csrfHeaders } from "./product";

export const state = ref<State | null>(null);
export const connected = ref(false);
const connectionKey = ref("ui.connecting");
export const connectionMode = computed(() => t(connectionKey.value));
export const session = ref<Session | null>(loadSession());
const failureKey = ref("");
export const failure = computed(() =>
  failureKey.value ? t(failureKey.value) : "",
);
export const clockOffset = ref(0);
let socket: WebSocket | null = null;
let reconnect: ReturnType<typeof setTimeout> | undefined;
let heartbeat: ReturnType<typeof setInterval> | undefined;
let generation = 0;
let lastReceived = 0;

function loadSession(): Session | null {
  try {
    const s = JSON.parse(sessionStorage.getItem("quizmosh-session") || "null");
    return s?.token && s?.code ? s : null;
  } catch {
    return null;
  }
}
export function accept(next: State) {
  if (
    state.value &&
    state.value.code === next.code &&
    next.revision < state.value.revision
  )
    return;
  clockOffset.value = Date.parse(next.serverTime) - Date.now();
  state.value = next;
  lastReceived = Date.now();
  connected.value = true;
  failureKey.value = "";
}
export async function api(path: string, body?: unknown, method?: string) {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "Accept-Language": locale.value,
    ...csrfHeaders(),
  };
  if (session.value) headers.Authorization = `Bearer ${session.value.token}`;
  const response = await fetch("/api" + path, {
    method: method || (body === undefined ? "GET" : "POST"),
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(10000),
  });
  const data = await response.json();
  if (!response.ok) {
    const error = new Error(
      data.message || t("ui.weCouldnTCompleteTheAction"),
    ) as Error & {
      status: number;
      code?: string;
      arguments?: Record<string, unknown>;
    };
    error.status = response.status;
    if (typeof data.code === "string" && hasMessage(data.code)) {
      error.code = data.code;
      error.arguments = data.arguments || {};
    }
    throw error;
  }
  return data;
}
export function enter(data: Session & { state: State }) {
  stop();
  session.value = {
    token: data.token,
    code: data.code,
    playerId: data.playerId,
  };
  sessionStorage.setItem("quizmosh-session", JSON.stringify(session.value));
  accept(data.state);
  connect();
}
export async function resume() {
  if (!session.value) return;
  try {
    accept(await api(`/rooms/${session.value.code}`));
    connect();
  } catch (error) {
    if ([401, 404].includes((error as any).status)) {
      forget();
      failureKey.value = "ui.yourPreviousRoomHasClosedStartA";
    } else {
      failureKey.value = "ui.weCouldnTConnectToTheServer";
      connect();
    }
  }
}
function connect() {
  if (!session.value) return;
  const current = ++generation;
  connectionKey.value = "ui.connecting";
  socket = new WebSocket(
    `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`,
  );
  socket.onopen = () => socket?.send(JSON.stringify(session.value));
  socket.onmessage = (event) => {
    if (current !== generation) return;
    try {
      accept(JSON.parse(event.data));
      connectionKey.value = "ui.live";
    } catch {}
  };
  socket.onerror = () => {
    connectionKey.value = "ui.reconnecting2";
  };
  socket.onclose = () => {
    if (current !== generation) return;
    connectionKey.value = "ui.reconnecting2";
    reconnect = setTimeout(connect, 2000);
  };
  if (heartbeat) clearInterval(heartbeat);
  heartbeat = setInterval(async () => {
    if (!session.value) return;
    if (socket?.readyState === WebSocket.OPEN) socket.send('{"type":"ping"}');
    if (Date.now() - lastReceived > 5000) {
      try {
        accept(await api(`/rooms/${session.value.code}`));
        connectionKey.value = "ui.synced";
      } catch (error) {
        connected.value = false;
        failureKey.value = "ui.connectionInterruptedReconnecting";
        if ([401, 404].includes((error as any).status)) {
          forget();
          failureKey.value = "ui.thisRoomHasClosedCreateOrJoin";
        }
      }
    }
  }, 3000);
}
function stop() {
  generation++;
  clearTimeout(reconnect);
  clearInterval(heartbeat);
  socket?.close();
  socket = null;
}
export function forget() {
  stop();
  session.value = null;
  state.value = null;
  connected.value = false;
  sessionStorage.removeItem("quizmosh-session");
}
