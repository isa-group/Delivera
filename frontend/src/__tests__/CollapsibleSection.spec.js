import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CollapsibleSection from '@/components/collapsibleSection/CollapsibleSection.vue'

function mountSection(props = {}) {
  return mount(CollapsibleSection, {
    props: { title: 'Genético', ...props },
    slots: { default: '<p class="contenido">Parámetros</p>' },
  })
}

describe('CollapsibleSection', () => {
  it('empieza cerrada y muestra solo la cabecera', () => {
    const wrapper = mountSection()

    expect(wrapper.find('button').text()).toContain('Genético')
    expect(wrapper.find('button').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.contenido').exists()).toBe(false)
  })

  it('se abre y se cierra al pulsar la cabecera', async () => {
    const wrapper = mountSection()

    await wrapper.find('button').trigger('click')
    expect(wrapper.find('.contenido').exists()).toBe(true)
    expect(wrapper.find('button').attributes('aria-expanded')).toBe('true')

    await wrapper.find('button').trigger('click')
    expect(wrapper.find('.contenido').exists()).toBe(false)
  })

  it('puede empezar abierta si se pide', () => {
    expect(mountSection({ initiallyOpen: true }).find('.contenido').exists()).toBe(true)
  })
})
