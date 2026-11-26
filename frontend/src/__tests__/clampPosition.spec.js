import { describe, it, expect } from 'vitest'
import { clampPosition } from '@/components/windows/clampPosition'

const container = { width: 1000, height: 800 }
const panel = { width: 500, height: 300 }

describe('clampPosition', () => {
  it('deja igual una posición que ya cabe dentro', () => {
    expect(clampPosition({ x: 100, y: 200 }, panel, container)).toEqual({ x: 100, y: 200 })
  })

  it('no deja salir el panel por la izquierda ni por arriba', () => {
    expect(clampPosition({ x: -50, y: -10 }, panel, container)).toEqual({ x: 0, y: 0 })
  })

  it('no deja salir el panel por la derecha ni por abajo', () => {
    expect(clampPosition({ x: 900, y: 700 }, panel, container)).toEqual({ x: 500, y: 500 })
  })

  it('pega arriba a la izquierda un panel más grande que el contenedor', () => {
    const big = { width: 1200, height: 900 }
    expect(clampPosition({ x: 300, y: 300 }, big, container)).toEqual({ x: 0, y: 0 })
  })
})
