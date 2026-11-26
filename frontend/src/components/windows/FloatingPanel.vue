<script setup>
import { computed, ref } from 'vue'
import { watch, nextTick } from 'vue';
import {onMounted, onUnmounted } from 'vue'
import {inject} from 'vue'
import { clampPosition } from './clampPosition'

const registerPanel = inject('registerPanel')

const minimizePanel = inject('minimizePanel')

const restorePanel = inject('restorePanel', null)

const getNextZIndex = inject('getNextZIndex')

const panels = inject('panels')


let currentZIndex = 10

const zIndex = ref(++currentZIndex)



const isDragging = ref(false)

const dragOffsetX = ref(0)
const dragOffsetY = ref(0)

const props = defineProps({

    startMinimized: {
        type: Boolean,
        default: true    
    },

    onDockbar: {
        type: Boolean,
        default: true
    },

    panelId: {
        type: String,
        required: true
    },

    title: String,

    displayName: String,

    icon: {
        type: String,
        default: 'pi pi-cog'
    },

    initialWidth: {
        type: Number,
        default: 500
    },

    initialHeight: {
        type: Number,
        default: 500
    },

    initialX: {
        type: Number,
        default: 16
    },

    initialY: {
        type: Number,
        default: 120
    },

   

    dockPosition: {
        type: Object,
        required: false
    },

    draggable: {
        type: Boolean,
        default: true
    },

    resizable: {
        type: Boolean,
        default: true
    },

    minimizable: {
        type: Boolean,
        default: true
    },

    maximizable: {
        type: Boolean,
        default: true
    }

})


const minimized = ref(props.startMinimized)

const maximized = ref(false)

const posX = ref(props.initialX)
const posY = ref(props.initialY)

const dockCoordinates = ref({x: 0, y:0})

const panelEl = ref(null)

// Mantiene el panel dentro de su contenedor: si no, al arrastrarlo o al encoger la
// ventana se puede perder fuera de la pantalla y ya no hay cabecera que agarrar.
function keepInside() {
    const el = panelEl.value
    if (!el || maximized.value) return
    const parent = el.offsetParent
    const container = parent
        ? { width: parent.clientWidth, height: parent.clientHeight }
        : { width: window.innerWidth, height: window.innerHeight }
    const { x, y } = clampPosition(
        { x: posX.value, y: posY.value },
        { width: el.offsetWidth, height: el.offsetHeight },
        container
    )
    posX.value = x
    posY.value = y
}

function restore() {
    restorePanel?.(props.panelId)
    minimized.value = false
    bringToFront()
    nextTick(keepInside)
}

defineExpose({ restore })

function startDrag(event) {

    isDragging.value = true

    dragOffsetX.value =
        event.clientX - posX.value

    dragOffsetY.value =
        event.clientY - posY.value

}

function onDrag(event) {

    if (!isDragging.value) {
        return
    }

    posX.value =
        event.clientX - dragOffsetX.value

    posY.value =
        event.clientY - dragOffsetY.value

    keepInside()

}

function stopDrag() {

    isDragging.value = false

}

function bringToFront() {
    const newZindex = getNextZIndex?.()
    if (!newZindex) {
        zIndex.value = ++currentZIndex
    }
    zIndex.value = newZindex
}

const emit = defineEmits([
    'minimize',
    'restore',
    'close'
])


function minimize() {

    minimizePanel?.(props.panelId)

    minimized.value = true

    emit(
        'minimize',
        props.panelId,  
    )

    dockCoordinates.value = props.dockPosition

}
const finalMinimized = computed(() => {

    const panel =
        panels?.find?.(
            p => p.id === props.panelId
        )

    if (!panel) {

        return minimized.value

    }

    return panel.minimized

})


onMounted(() => {
    if (!registerPanel) return;

    registerPanel({

        id: props.panelId,

        displayName: props.displayName,

        icon: props.icon,

        startMinimized: props.startMinimized

    })

})




onMounted(() => {

    window.addEventListener(
        'mousemove',
        onDrag
    )

    window.addEventListener(
        'mouseup',
        stopDrag
    )

    window.addEventListener(
        'resize',
        keepInside
    )

    nextTick(keepInside)

})


onUnmounted(() => {

    window.removeEventListener(
        'mousemove',
        onDrag
    )

    window.removeEventListener(
        'mouseup',
        stopDrag
    )

    window.removeEventListener(
        'resize',
        keepInside
    )

})

// Al restaurarlo desde el dock vuelve a aparecer donde estaba, que puede haber
// quedado fuera si la ventana ha cambiado de tamaño mientras estaba minimizado.
watch(finalMinimized, isMinimized => {
    if (!isMinimized) nextTick(keepInside)
})

watch(maximized, isMaximized => {
    if (!isMaximized) nextTick(keepInside)
})

watch(
    dockCoordinates,
    value => {
        console.log(
            props.panelId,
            value
        )
    }
)


</script>

<template>
    <div
        v-if="finalMinimized && !onDockbar"
        class="floating-panel-icon"
        @click="minimized = false"
        :style="{
            left: `${dockCoordinates.x}px`,
            top: `${dockCoordinates.y}px`,
            width: maximized ? undefined : `${width}px`,
            height: maximized ? undefined : `${height}px`,
            zIndex
        }"
    >
        <i :class="icon" />
    </div>

    <div 
        v-if="!finalMinimized"
        ref="panelEl"
        class="floating-panel"
        :class="{
            maximized
        }"
        :style="{
            left: `${posX}px`,
            top: `${posY}px`,
            zIndex
        }"
        @mousedown="bringToFront"
    >
        <div 
            class="floating-panel-header"
            @mousedown="startDrag"
        >

            <span>
                {{ title }}
            </span>

            <div class="floating-panel-actions">

                <button class="floating-panel-action" @click="minimize">
                    <i class="pi pi-minus" />
                </button>

                <button
                    @click="maximized = !maximized"
                    class="floating-panel-action"
                >
                    <i
                        :class="
                            maximized
                            ? 'pi pi-window-minimize'
                            : 'pi pi-window-maximize'
                        "
                    />
                </button>

                <button class="floating-panel-action close" @click="minimize">
                    <i class="pi pi-times" />
                </button>

            </div>

        </div>

        <div class="floating-panel-body">

            <slot />

        </div>

        </div>


</template>

<style scoped src="./FloatingPanel.css" />