<script setup>
import { computed, ref } from 'vue'
import { watch } from 'vue';
import {onMounted, onUnmounted } from 'vue'
import {inject} from 'vue'

const registerPanel = inject('registerPanel')

const minimizePanel = inject('minimizePanel')

const getNextZIndex = inject('getNextZIndex')

const panels = inject('panels')

const minimized = ref(false)

const maximized = ref(false)

let currentZIndex = 10

const zIndex = ref(++currentZIndex)



const isDragging = ref(false)

const dragOffsetX = ref(0)
const dragOffsetY = ref(0)

const props = defineProps({

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

const posX = ref(props.initialX)
const posY = ref(props.initialY)

const dockCoordinates = ref({x: 0, y:0}) 

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

        icon: props.icon

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
            zIndex
        }"
    >
        <i :class="icon" />
    </div>

    <div 
        v-if="!finalMinimized" 
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