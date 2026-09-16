import { ref } from "vue";
import type { State, Session } from "./types";

export const state = ref<State | null>(null);
export const connected = ref(false);
export const connectionMode = ref("conectando");
export const session = ref<Session | null>(loadSession());
export const failure = ref("");
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
  failure.value = "";
}
export async function api(path: string, body?: unknown, method?: string) {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
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
      data.message || "Não foi possível concluir a ação.",
    ) as Error & { status: number };
    error.status = response.status;
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
      failure.value = "A sala anterior foi encerrada. Vamos começar outra?";
    } else {
      failure.value =
        "Não conseguimos conectar ao servidor. Tentando novamente…";
      connect();
    }
  }
}
function connect() {
  if (!session.value) return;
  const current = ++generation;
  connectionMode.value = "conectando";
  socket = new WebSocket(
    `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`,
  );
  socket.onopen = () => socket?.send(JSON.stringify(session.value));
  socket.onmessage = (event) => {
    if (current !== generation) return;
    try {
      accept(JSON.parse(event.data));
      connectionMode.value = "ao vivo";
    } catch {}
  };
  socket.onerror = () => {
    connectionMode.value = "reconectando";
  };
  socket.onclose = () => {
    if (current !== generation) return;
    connectionMode.value = "reconectando";
    reconnect = setTimeout(connect, 2000);
  };
  if (heartbeat) clearInterval(heartbeat);
  heartbeat = setInterval(async () => {
    if (!session.value) return;
    if (socket?.readyState === WebSocket.OPEN) socket.send('{"type":"ping"}');
    if (Date.now() - lastReceived > 5000) {
      try {
        accept(await api(`/rooms/${session.value.code}`));
        connectionMode.value = "sincronizado";
      } catch (error) {
        connected.value = false;
        failure.value = "Conexão interrompida. Reconectando…";
        if ([401, 404].includes((error as any).status)) {
          forget();
          failure.value = "A sala foi encerrada. Crie ou entre em outra sala.";
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
