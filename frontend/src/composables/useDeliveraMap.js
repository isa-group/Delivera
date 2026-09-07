// Composable unificado para todos los mapas de la aplicación.
// Encapsula: tiles base, iconos (unidad propia / ajena / cliente),
// clustering con número en círculo morado, rutas (OSRM continua o discontinua),
// y los handlers de click / dblclick en marcadores y rutas.
//
// Paleta:
//   - Morado (propio):     #7c3aed
//   - Lila (otros):        #a78bfa
//   - Texto cluster:       #fff

import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'

// --- Ajuste marker images por defecto (aunque no se usen, evita warnings en build) ---
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({ iconUrl: markerIcon, iconRetinaUrl: markerIcon2x, shadowUrl: markerShadow })

// Zoom en el que los elementos se compactan en clusters — por debajo, rutas también se ocultan.
export const CLUSTER_DISABLE_ZOOM = 10
export const COLORS = { own: '#7c3aed', other: '#a78bfa', white: '#ffffff' }
export const ROUTE_COLOR = '#7c3aed'
// Colores de ruta por estado del pedido (DSI-22.1)
export const ROUTE_STATUS_COLORS = {
  PENDING:   '#f59e0b',
  IN_TRANSIT:'#7c3aed',
  DELIVERED: '#16a34a',
  CANCELLED: '#94a3b8',
}
export function routeColorFor(status) {
  return ROUTE_STATUS_COLORS[status] || ROUTE_COLOR
}

// Devuelve { lat, lon } parseado a partir de los campos currentLat/currentLon del pedido,
// o null si alguno es nulo. Centralizado para reutilizar en todas las vistas y testear.
export function currentLocationOf(order) {
  if (order?.currentLat == null || order?.currentLon == null) return null
  return { lat: Number.parseFloat(order.currentLat), lon: Number.parseFloat(order.currentLon) }
}

// True para pedidos en estado activo (PENDING o IN_TRANSIT).
export function isActiveOrder(o) {
  return !!o && (o.status === 'PENDING' || o.status === 'IN_TRANSIT')
}

// True si el pedido tiene coordenadas de origen utilizables para pintar el mapa.
export function hasOriginCoords(o) {
  return !!o && o.originLat != null && o.originLon != null
}

const TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const TILE_ATTR = '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

// ---------------------------------------------------------------------------
// Icons
// ---------------------------------------------------------------------------

function _divIcon(bg, piClass, size = 32) {
  return L.divIcon({
    html: `<div style="width:${size}px;height:${size}px;border-radius:50%;background:${bg};border:2.5px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center"><i class="${piClass}" style="color:#fff;font-size:${Math.round(size * 0.5)}px"></i></div>`,
    className: 'delivera-marker',
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2 - 2],
  })
}

function _divIconStop(bg, stop, size = 32) {
  return L.divIcon({
    html: `<div style="width:${size}px;height:${size}px;border-radius:50%;background:${bg};border:2.5px solid #fff;box-shadow:0 2px 6px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center"><p style="color:#fff;font-size:${Math.round(size * 0.5)}px">${stop}</p></div>`,
    className: 'delivera-marker',
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2 - 2],
  })
}

export function ownUnitIcon(customColor = COLORS.own) { return _divIcon(customColor, 'pi pi-building') }
export function otherUnitIcon(customColor = COLORS.other) { return _divIcon(customColor, 'pi pi-building') }
export function customerIcon(customColor = COLORS.other) { return _divIcon(customColor, 'pi pi-user') }
export function selfIcon(customColor = COLORS.own) { return _divIcon(customColor, 'pi pi-user') }
export function stopIcon(stop, customColor = COLORS.other) { return _divIconStop(customColor,stop)}

// ---------------------------------------------------------------------------
// Cluster options — número en blanco sobre círculo morado.
// ---------------------------------------------------------------------------
export function clusterOptions() {
  return {
    disableClusteringAtZoom: CLUSTER_DISABLE_ZOOM,
    maxClusterRadius: 60,
    spiderfyOnMaxZoom: false,
    showCoverageOnHover: false,
    iconCreateFunction: (cluster) => L.divIcon({
      html: `<div style="width:40px;height:40px;border-radius:50%;background:${COLORS.own};border:3px solid rgba(124,58,237,0.25);display:flex;align-items:center;justify-content:center;color:#fff;font-size:13px;font-weight:700">${cluster.getChildCount()}</div>`,
      className: 'delivera-cluster',
      iconSize: [40, 40],
      iconAnchor: [20, 20],
    }),
  }
}

// ---------------------------------------------------------------------------
// Map creation
// ---------------------------------------------------------------------------
export function createMap(el, options = {}) {
  const map = L.map(el, {
    zoomControl: true,
    scrollWheelZoom: true,
    doubleClickZoom: false, // evita zoom al hacer dblclick en markers
    ...options,
  })
  L.tileLayer(TILE_URL, { attribution: TILE_ATTR }).addTo(map)
  return map
}

// ---------------------------------------------------------------------------
// Popup helpers
// ---------------------------------------------------------------------------
function escape(s) {
  return String(s ?? '').replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}

function popupHtml({ title, subtitle, actionLabel, actionId }) {
  const sub = subtitle ? `<br><small style="color:#64748b">${escape(subtitle)}</small>` : ''
  const act = actionLabel && actionId
    ? `<br><a href="#" data-delivera-action="${escape(actionId)}" style="color:${COLORS.own};font-size:12px;margin-top:4px;display:inline-block;font-weight:500">${escape(actionLabel)}</a>`
    : ''
  return `<div style="min-width:160px"><strong>${escape(title)}</strong>${sub}${act}</div>`
}

function bindPopupWithAction(marker, htmlBuilder, actionId, onAction) {
  marker.bindPopup(htmlBuilder)
  marker.on('popupopen', () => {
    const link = document.querySelector(`[data-delivera-action="${actionId}"]`)
    if (link && onAction) {
      link.onclick = (e) => { e.preventDefault(); onAction() }
    }
  })
}

// ---------------------------------------------------------------------------
// Marker factory
// - kind: 'OWN_UNIT' | 'OTHER_UNIT' | 'CUSTOMER'
// - navigateTo: url | null  (null = no dblclick nav, p.e. unidad ajena / cliente no fidelizado)
// ---------------------------------------------------------------------------
export function addMarker(map, {
  id, lat, lon, kind, title, subtitle, actionLabel, navigateTo, router, stop, customColor
}) {
  let icon
  if (kind === 'OWN_UNIT') icon = ownUnitIcon(customColor)
  else if (kind === 'OTHER_UNIT') icon = otherUnitIcon(customColor)
  else if (kind === 'SELF') icon = selfIcon(customColor)
  else if (kind === 'STOP') icon = stopIcon(stop, customColor)
  else icon = customerIcon(customColor)

  const marker = L.marker([lat, lon], { icon })
  const actionId = `m-${kind}-${id ?? Math.random().toString(36).slice(2)}`

  bindPopupWithAction(
    marker,
    popupHtml({
      title,
      subtitle,
      actionLabel: navigateTo ? actionLabel : null,
      actionId,
    }),
    actionId,
    navigateTo ? () => router.push(navigateTo) : null,
  )

  if (navigateTo) {
    marker.on('dblclick', (ev) => {
      L.DomEvent.stopPropagation(ev)
      marker.closePopup()
      router.push(navigateTo)
    })
  }

  return marker
}
// ---------------------------------------------------------------------------
// Route factory — intenta OSRM (polilínea continua morada). Si falla,
// usa una línea recta discontinua entre ambos puntos.
//
// Devuelve una referencia con el layer creado para poder ocultar en zoom out.
// ---------------------------------------------------------------------------


export function routesOverlays(map, layersBySolverRef) {
  return L.control.layers(null, layersBySolverRef.value).addTo(map);
}

export function initOverlays(map) {
 return  L.control.layers(null, {}).addTo(map);
}

export function addlayer(layerOverlay, layer, name) {
  layerOverlay.addOverlay(layer, name)
}


export function initSolverLayer(solver, layersBySolverRef) {
  layersBySolverRef.value = { 
    ...layersBySolverRef.value, 
    [solver] : {
      root:L.layerGroup(),
      routes: []

    }
  }
  console.log(layersBySolverRef.value)
}

export function initLayer() {
  return L.layerGroup()
}



function interpolateColor(start, end, factor) {
  return {
      r: Math.round(start.r + factor * (end.r - start.r)),
      g: Math.round(start.g + factor * (end.g - start.g)),
      b: Math.round(start.b + factor * (end.b - start.b))
  };
}
function hexToRgb(hex) {
  const value = hex.replace('#', '');

  return {
      r: parseInt(value.substring(0, 2), 16),
      g: parseInt(value.substring(2, 4), 16),
      b: parseInt(value.substring(4, 6), 16)
  };
}

function lightenColor(hex, factor = 0.6) {
  const rgb = hexToRgb(hex);

  return {
      r: Math.round(rgb.r + (255 - rgb.r) * factor),
      g: Math.round(rgb.g + (255 - rgb.g) * factor),
      b: Math.round(rgb.b + (255 - rgb.b) * factor)
  };
}



export function drawGradientRoute({
  map,
  latLngs,
  baseColor,
  startColor,
  endColor,
  lightenFactor = 0.6,
  weight = 5,
  dashed = false
}) {
  if (baseColor) {
    startColor = lightenColor(baseColor, lightenFactor);
    endColor = hexToRgb(baseColor);
  }
  for (let i = 0; i < latLngs.length - 1; i++) {
      const factor =
          i / Math.max(1, latLngs.length - 2);

      const color =
          interpolateColor(
              startColor,
              endColor,
              factor
          );
      
      const line = !dashed? L.polyline(
          [
              latLngs[i],
              latLngs[i + 1]
          ],
          {
              color: `rgb(${color.r},${color.g},${color.b})`,
              weight
          }
      ): L.polyline([
              latLngs[i],
              latLngs[i + 1]
          ],
          {
              color: `rgb(${color.r},${color.g},${color.b})`,
              weight,
              dashArray: '2,10'
          })
      /*bindPopupWithAction(
        line,
        popupHtml({
          title: "HOLA",
          subtitle: "HOLA",
          actionLabel: "HOLA"
        }),
        null,
        null,
      )*/
      line.addTo(map)

  }
}

function addClientIndexs(map,latLngs) {
  const bounds = L.latLngBounds(latLngs);
  const center = bounds.getCenter();
  let index = 0
  L.divIcon({
    html: `
        <div class="route-step">
            ${index}
        </div>
    `,
    className: '',
    iconSize: [30, 30]
  })
  

}
function addDirectionMarkers(map, latLngs, color) {

  const every = 20;

  for (let i = every; i < latLngs.length - every; i += every) {

      const p1 = latLngs[i];
      const p2 = latLngs[i + 1];

      const angle =
          Math.atan2(
              p2[0] - p1[0],
              p2[1] - p1[1]
          ) * 180 / Math.PI;

      L.marker(p1, {
          icon: L.divIcon({
              html: `
                  <div style="
                      color:${color};
                      transform:rotate(${angle}deg);
                      font-size:12px;
                  ">
                      ➤
                  </div>
              `,
              className: '',
              iconSize: [12,12]
          })
      }).addTo(map);
  }
}


export async function  addFmsRoute(map, {
  depotId, stops, depotsById, customersById,
  timeoutMs = 6000, markers ,popupTitle, popupSubtitle, actionLabel, router,
  color, useDataFunction = null, dashed
}) {
  const route = [depotId, ...stops,depotId ]
  const coords = route.map(
      (id,indx)=> {
        if (indx == 0 || indx == route.length-1){
          return depotsById[id]
        } else {
          return customersById[id]
        }
      }
    ).map(coord => {
      return `${coord.lon},${coord.lat}`
    })
  const coordinates = coords.join(";")
  //console.table(coords)
  const url = `https://router.project-osrm.org/route/v1/driving/${coordinates}?geometries=geojson&overview=full`
  const res = await fetch(url, { signal: AbortSignal.timeout(timeoutMs) })
  if (!res.ok) return null
  const data = await res.json()
  if (useDataFunction != null) {
    useDataFunction(data)
  }
  const geometry =
    data.routes[0].geometry.coordinates

  //console.log(data)
  const latLngs =
      geometry.map(([lng, lat]) => [lat, lng])


  drawGradientRoute({
      map: map,
      latLngs: latLngs,
      baseColor: color,
      lightenFactor: 0.7,
      dashed
  });

  
}


// ---------------------------------------------------------------------------
// Route factory — intenta OSRM (polilínea continua morada). Si falla,
// usa una línea recta discontinua entre ambos puntos.
//
// Devuelve una referencia con el layer creado para poder ocultar en zoom out.
// ---------------------------------------------------------------------------
export async function addRoute(map, {
  orderId, origin, dest, popupTitle, popupSubtitle, actionLabel, router,
  timeoutMs = 6000, originMarker = null, destMarker = null, status = null,
  currentLocation = null, customColor = null, useDataFunction = null
}) {
  const entry = { layer: null, solid: true, originMarker, destMarker, currentMarker: null }
  const color = customColor==null? routeColorFor(status) : customColor

  async function fetchOSRM() {
    const url = `https://router.project-osrm.org/route/v1/driving/${origin.lon},${origin.lat};${dest.lon},${dest.lat}?geometries=geojson&overview=simplified`
    const res = await fetch(url, { signal: AbortSignal.timeout(timeoutMs) })
    if (!res.ok) return null
    const data = await res.json()
    if (useDataFunction != null) {
      useDataFunction(data)
    }
    const coords = data.routes?.[0]?.geometry?.coordinates
    if (!coords || coords.length < 2) return null
    return coords.map(([lon, lat]) => [lat, lon])
  }

  function buildSolid(latlngs) {
    // Trazo morado grueso de alta opacidad — visible a corta y larga distancia.
    return L.polyline(latlngs, {
      color, weight: 5, opacity: 0.95,
      lineCap: 'round', lineJoin: 'round',
    })
  }
  function buildDashed() {
    return L.polyline(
      [[origin.lat, origin.lon], [dest.lat, dest.lon]],
      { color, weight: 4, opacity: 0.9, dashArray: '10,10', lineCap: 'round' },
    )
  }

  let line
  try {
    const path = await fetchOSRM()
    if (path) {
      // OSRM snaps endpoints to the nearest road; prepend/append the exact marker
      // coordinates so the polyline always starts and ends at the marker position.
      const anchored = [[origin.lat, origin.lon], ...path, [dest.lat, dest.lon]]
      line = buildSolid(anchored); entry.solid = true
    } else { line = buildDashed(); entry.solid = false }
  } catch {
    line = buildDashed(); entry.solid = false
  }

  const actionId = `r-${orderId}-${Math.random().toString(36).slice(2, 6)}`
  bindPopupWithAction(
    line,
    popupHtml({
      title: popupTitle,
      subtitle: popupSubtitle,
      actionLabel: orderId ? actionLabel : null,
      actionId,
    }),
    actionId,
    orderId ? () => router.push(`/orders/${orderId}`) : null,
  )

  if (orderId) {
    line.on('dblclick', (ev) => {
      L.DomEvent.stopPropagation(ev)
      line.closePopup()
      router.push(`/orders/${orderId}`)
    })
  }

  if (map) { line.addTo(map); entry.layer = line }

  entry.currentMarker = addCurrentLocationMarker(map, currentLocation, color, popupTitle, popupSubtitle)
  return entry
}

// Crea un marcador circular con el color del estado sobre la ruta. Aislado para poder
// testearlo sin instanciar el Map completo.
export function addCurrentLocationMarker(map, currentLocation, color, popupTitle, popupSubtitle) {
  if (!map || currentLocation?.lat == null || currentLocation?.lon == null) return null
  const marker = L.circleMarker([currentLocation.lat, currentLocation.lon], {
    radius: 7, color: '#fff', weight: 2, fillColor: color, fillOpacity: 1,
  }).addTo(map)
  if (popupTitle) marker.bindPopup(popupHtml({ title: popupTitle, subtitle: popupSubtitle, actionLabel: null }))
  return marker
}

// ---------------------------------------------------------------------------
// Visibilidad de rutas en función del estado real de clustering.
// - Si el origen O el destino están dentro de un cluster → ruta oculta.
// - En cualquier otro caso → ruta visible (incluyendo zoom lejano cuando
//   los marcadores no compactan por distancia).
// - Si no se pasa clusterGroup, las rutas están siempre visibles.
// ---------------------------------------------------------------------------
function _isClustered(clusterGroup, marker) {
  if (!marker) return false
  if (!clusterGroup?.getVisibleParent) return false
  const parent = clusterGroup.getVisibleParent(marker)
  return parent && parent !== marker
}

function applyVisibility(entry, visible) {
  if (!entry?.layer) return
  const solidOpacity = entry.solid ? 0.95 : 0.9
  const opacity = visible ? solidOpacity : 0
  if (entry.layer.setStyle) entry.layer.setStyle({ opacity })
  const el = entry.layer.getElement?.()
  if (el) el.style.pointerEvents = visible ? '' : 'none'
}

export function attachRouteVisibilityHandler(map, clusterGroup, routeEntriesRef) {
  function update() {
    for (const entry of routeEntriesRef()) {
      if (!entry?.layer) continue
      const hidden = _isClustered(clusterGroup, entry.originMarker)
        || _isClustered(clusterGroup, entry.destMarker)
      applyVisibility(entry, !hidden)
    }
  }

  map.on('zoomend', update)
  map.on('moveend', update)
  if (clusterGroup?.on) {
    clusterGroup.on('animationend', update)
    clusterGroup.on('clusterclick', update)
  }
  update()
  return () => {
    map.off('zoomend', update)
    map.off('moveend', update)
    if (clusterGroup?.off) {
      clusterGroup.off('animationend', update)
      clusterGroup.off('clusterclick', update)
    }
  }
}

// Alias retrocompatible (nombre viejo).
export const attachRouteZoomHandler = attachRouteVisibilityHandler

// ---------------------------------------------------------------------------
// Util: ajustar bounds con padding estándar.
// ---------------------------------------------------------------------------
import { MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION } from '@/constants/map'

export function fitBounds(map, coords, fallback = [MAP_DEFAULT_CENTER, MAP_DEFAULT_ZOOM_REGION]) {
  const filtered = coords.filter(c => c?.[0] != null && c?.[1] != null)
  if (filtered.length === 0) { map.setView(fallback[0], fallback[1]); return }
  if (filtered.length === 1) { map.setView(filtered[0], 13); return }
  map.fitBounds(filtered, { padding: [40, 40] })
}
