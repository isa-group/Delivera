<script setup>
import { ref } from 'vue'

/**
 * Sección con cabecera clicable que abre y cierra su contenido.
 * Empieza cerrada salvo que se pida lo contrario.
 */
const props = defineProps({
    title: { type: String, required: true },
    initiallyOpen: { type: Boolean, default: false },
})

const open = ref(props.initiallyOpen)
</script>

<template>
    <section class="collapsible-section" :class="{ open }">
        <button
            type="button"
            class="collapsible-header"
            :aria-expanded="open ? 'true' : 'false'"
            @click="open = !open"
        >
            <i class="pi" :class="open ? 'pi-chevron-down' : 'pi-chevron-right'" />
            <span class="collapsible-title">{{ title }}</span>
            <slot name="badge" />
        </button>
        <div v-if="open" class="collapsible-body">
            <slot />
        </div>
    </section>
</template>

<style scoped src="./CollapsibleSection.css" />
