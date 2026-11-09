<script setup>
import {
    provide,
    reactive,
    ref
} from 'vue'



const panels = reactive([])

const panelPositions = reactive({})


function registerPanel(panel) {
    panelPositions[panel.id] = panels.length

    panels.push({

        ...panel,
        
        minimized: false

    })
}





function minimize(
    panelId
) {

    const panel =
        panels.find(
            p => p.id === panelId
        )

    if (!panel) {
        return
    }

    panel.minimized = true

}

function restore(
    panelId
) {

    const panel =
        panels.find(
            p => p.id === panelId
        )

    if (!panel) {
        return
    }

    panel.minimized = false

}


// zIndexManager.js


const globalTopZIndex = ref(100)

function getNextZIndex() {
    globalTopZIndex.value++
    return globalTopZIndex.value
}

provide(
    'getNextZIndex',
    getNextZIndex
)


provide(
    'minimizePanel',
    minimize
)

provide(
    'registerPanel',
    registerPanel
)

provide(    
    'getDockPosition', (panelId) => panelPositions[panelId]
)


provide('panels', panels)






</script>

<template>

    <div class="dock-manager">

        <slot />

        <div class="dock-bar">

            <div
                v-for="panel in panels"
                :key="panel.id"
                :id="`dock-${panel.id}`"
                :class="!panel.minimized ?'dock-slot' : ''"
            >   
                <h4
                    v-if="!panel.minimized"
                >
                    {{panel.displayName}}
                </h4>
                
                <button
                    v-if="panel.minimized"
                    class="dock-item"
                    
                    @click="restore(panel.id)"
                >

                    <i :class="panel.icon" />

                </button>

            </div>

        </div>

    </div>

</template>

<style scoped src="./DockBar.css"/>