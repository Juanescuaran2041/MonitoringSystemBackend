# Frontend — Dashboard de observabilidad (React)

Guía de implementación del dashboard que consume este backend. Vive en un repositorio
aparte (`monitoring-system-frontend`) y se despliega en Vercel.

> Todo el código va **en inglés**: nombres de componentes, hooks, variables, funciones y
> comentarios. Esta documentación está en español; el código no.

---

## 1. Stack

| Pieza | Elección | Por qué |
|---|---|---|
| Framework | React 18 + Vite | Arranque y build rápidos, soporte nativo en Vercel |
| Gráficas | Recharts | Componentes declarativos de React, suficiente para una línea por servicio |
| HTTP | `fetch` nativo encapsulado en `apiClient` | Sin dependencia extra para 5 llamadas |
| Estado | `useState` + custom hooks | El estado es local a la pantalla; Redux/Zustand sería sobreingeniería |
| Estilos | CSS Modules | Estilos con alcance por componente, sin librería adicional |

Sin TypeScript para no alargar el taller; los contratos se documentan con **JSDoc**
(`src/api/types.js`), lo que da autocompletado en el editor sin configurar un compilador.

---

## 2. Estructura de carpetas

Separación por responsabilidad, no por tipo de archivo suelto:

```
src/
├── api/
│   ├── apiClient.js          # fetch + manejo de errores + base URL (única capa que conoce HTTP)
│   ├── metricsApi.js         # getSummary, getLogs, simulateLoad
│   ├── servicesApi.js        # callService(serviceId, operation, params)
│   └── types.js              # typedefs JSDoc del contrato del backend
├── hooks/
│   ├── usePolling.js         # ejecuta una función cada N ms, con limpieza
│   ├── useMetricsSummary.js  # resumen + estados loading/error
│   └── useServiceLogs.js     # logs + filtros + paginación
├── components/
│   ├── dashboard/ServiceCard.jsx, ServiceCardList.jsx
│   ├── charts/ResponseTimeChart.jsx
│   ├── logs/LogsTable.jsx, LogRow.jsx, LogDetails.jsx, LogFilters.jsx
│   └── common/StatusBadge.jsx, ErrorMessage.jsx, Spinner.jsx, Button.jsx
├── pages/DashboardPage.jsx   # compone la pantalla, no contiene lógica de datos
├── utils/formatters.js       # formatDuration, formatPercentage, formatTimestamp
├── constants.js              # DEGRADED_ERROR_RATE, POLLING_INTERVAL_MS, SERVICE_IDS
└── main.jsx
```

**Regla de dependencias (de arriba hacia abajo, nunca al revés):**
`pages → components → hooks → api`. Un componente nunca llama a `fetch` directamente ni
conoce una URL; un hook nunca renderiza JSX.

---

## 3. Buenas prácticas aplicadas

| Práctica | Cómo se cumple |
|---|---|
| **Responsabilidad única** | `ServiceCard` solo pinta una tarjeta; quien trae los datos es `useMetricsSummary` |
| **Componentes de presentación vs. contenedores** | `DashboardPage` (contenedor) orquesta hooks y pasa props; el resto son puros y testeables |
| **Capa de API aislada** | Cambiar de `fetch` a axios o cambiar una ruta toca un solo archivo |
| **Sin valores mágicos** | `15%`, `3000 ms`, `20` llamadas viven en `constants.js` |
| **Configuración por entorno** | `VITE_API_URL`, nunca una URL escrita en el código |
| **Estados explícitos** | Cada vista maneja `loading`, `error` y `empty`, no solo el camino feliz |
| **Limpieza de efectos** | `usePolling` limpia el `setInterval` y aborta la petición en curso al desmontar |
| **Inmutabilidad** | El estado se reemplaza (`setLogs(next)`), nunca se muta |
| **Listas con `key` estable** | `key={log.requestId}`, que es un UUID del backend |
| **Nomenclatura** | `PascalCase` componentes · `camelCase` funciones y variables · `useX` hooks · `UPPER_SNAKE_CASE` constantes |
| **Accesibilidad** | `<table>` semántica, `aria-expanded` en la fila expandible, foco visible, color nunca como único indicador (el estado también se lee como texto) |
| **Rendimiento** | `useCallback` en las funciones que se pasan a hijos y `useMemo` para los datos derivados de la gráfica |

---

## 4. Requisitos del taller → componente

| Requisito | Dónde se implementa |
|---|---|
| Dashboard con 3 tarjetas: total de llamadas, tasa de éxito, tiempo promedio | `ServiceCardList` + `ServiceCard` ← `useMetricsSummary` |
| Gráfica de líneas con las últimas 20 llamadas por servicio y su tiempo de respuesta | `ResponseTimeChart` ← `getLogs({ service, size: 20 })` |
| Tabla de logs en tiempo real con polling cada 3 segundos | `LogsTable` ← `useServiceLogs` + `usePolling(3000)` |
| Filtros por servicio, estado y rango de fechas | `LogFilters` → parámetros `service`, `status`, `from`, `to` |
| Botón "Simular carga" que dispara el endpoint y actualiza el dashboard | `SimulateLoadButton` → `simulateLoad()` y luego `refresh()` |
| Al hacer clic en un log, expandir detalles (parámetros y respuesta completa) | `LogRow` + `LogDetails` (el backend ya envía `params` y `result`) |
| Indicador rojo si la tasa de error supera el 15% | `ServiceCard` usando `metrics.degraded` que calcula el backend |

La tasa de error la decide el backend (campo `degraded`), no el frontend: una sola fuente
de verdad para el umbral.

---

## 5. Contrato con el backend

Base URL en `VITE_API_URL` (local: `http://localhost:8080`).

| Uso | Llamada |
|---|---|
| Tarjetas | `GET /api/metrics/summary` |
| Tabla y gráfica | `GET /api/metrics/logs?service=&status=&from=&to=&page=&size=` |
| Botón simular | `POST /api/metrics/simulate-load` con `{}` o `{"calls":100}` |
| Pruebas manuales | `POST /api/services/{inventory\|orders\|payments}/{operation}` |

Las fechas de `from` y `to` van en ISO-8601 UTC (`new Date(value).toISOString()`).
Los errores llegan con `{ timestamp, status, error, message }`; `apiClient` convierte ese
`message` en la excepción que ven los componentes.

---

## 6. Piezas clave

### `src/api/apiClient.js` — único punto que habla HTTP

```js
const BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export async function request(path, { method = "GET", body, signal } = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
    signal,
  });

  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(payload?.message ?? `Request failed with status ${response.status}`);
  }
  return payload;
}
```

### `src/api/metricsApi.js` — el resto de la app no arma URLs

```js
import { request } from "./apiClient";

export const getSummary = (signal) => request("/api/metrics/summary", { signal });

export function getLogs({ service, status, from, to, page = 0, size = 20 }, signal) {
  const params = new URLSearchParams({ page, size });
  if (service) params.set("service", service);
  if (status) params.set("status", status);
  if (from) params.set("from", new Date(from).toISOString());
  if (to) params.set("to", new Date(to).toISOString());
  return request(`/api/metrics/logs?${params}`, { signal });
}

export const simulateLoad = (calls) =>
  request("/api/metrics/simulate-load", { method: "POST", body: { calls } });
```

### `src/hooks/usePolling.js` — refresco cada 3 s, con limpieza

```js
import { useEffect, useRef } from "react";

/** Runs `callback` immediately and then every `intervalMs`, aborting in-flight work on unmount. */
export function usePolling(callback, intervalMs) {
  const savedCallback = useRef(callback);
  savedCallback.current = callback;

  useEffect(() => {
    const controller = new AbortController();
    const tick = () => savedCallback.current(controller.signal);

    tick();
    const timerId = setInterval(tick, intervalMs);
    return () => {
      clearInterval(timerId);
      controller.abort();
    };
  }, [intervalMs]);
}
```

### `src/hooks/useMetricsSummary.js` — datos fuera de los componentes

```js
import { useCallback, useState } from "react";
import { getSummary } from "../api/metricsApi";
import { usePolling } from "./usePolling";
import { POLLING_INTERVAL_MS } from "../constants";

export function useMetricsSummary() {
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  const refresh = useCallback(async (signal) => {
    try {
      setSummary(await getSummary(signal));
      setError(null);
    } catch (fetchError) {
      if (fetchError.name !== "AbortError") setError(fetchError.message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  usePolling(refresh, POLLING_INTERVAL_MS);
  return { summary, isLoading, error, refresh };
}
```

### `src/components/dashboard/ServiceCard.jsx` — presentacional y puro

```jsx
import { formatDuration, formatPercentage } from "../../utils/formatters";
import styles from "./ServiceCard.module.css";

export function ServiceCard({ metrics }) {
  const { serviceId, totalCalls, successRate, averageResponseTimeMs, degraded } = metrics;

  return (
    <article className={degraded ? styles.cardDegraded : styles.card} aria-live="polite">
      <header>
        <h2>{serviceId}</h2>
        {degraded && <span className={styles.alert}>Degraded</span>}
      </header>
      <dl>
        <dt>Total calls</dt><dd>{totalCalls}</dd>
        <dt>Success rate</dt><dd>{formatPercentage(successRate)}</dd>
        <dt>Avg. response</dt><dd>{formatDuration(averageResponseTimeMs)}</dd>
      </dl>
    </article>
  );
}
```

### `src/constants.js`

```js
export const POLLING_INTERVAL_MS = 3000;
export const CHART_SAMPLE_SIZE = 20;
export const SERVICE_IDS = ["inventory", "orders", "payments"];
export const CALL_STATUSES = ["SUCCESS", "ERROR"];
```

---

## 7. Puesta en marcha y despliegue

```bash
npm create vite@latest monitoring-system-frontend -- --template react
cd monitoring-system-frontend
npm install recharts
echo "VITE_API_URL=http://localhost:8080" > .env.local
npm run dev
```

**Vercel:** importar el repositorio (preset Vite, build `npm run build`, salida `dist`) y
definir `VITE_API_URL=https://<dominio-railway>` en *Settings → Environment Variables*.
Después, en Railway, fijar `MONITORING_CORS_ALLOWED_ORIGINS=https://<tu-app>.vercel.app`
para cerrar el CORS a ese origen.

`.env.local` no se versiona; el repositorio solo lleva un `.env.example` con la variable
vacía.
