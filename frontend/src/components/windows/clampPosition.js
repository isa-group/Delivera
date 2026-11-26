/**
 * Recoloca un panel para que no se salga del contenedor por ningún lado.
 *
 * Si el panel es más grande que el contenedor, lo pega arriba a la izquierda:
 * así la cabecera, que es por donde se arrastra, siempre se puede agarrar.
 */
export function clampPosition({ x, y }, panel, container) {
    const maxX = Math.max(0, container.width - panel.width)
    const maxY = Math.max(0, container.height - panel.height)
    return {
        x: Math.min(Math.max(0, x), maxX),
        y: Math.min(Math.max(0, y), maxY),
    }
}
