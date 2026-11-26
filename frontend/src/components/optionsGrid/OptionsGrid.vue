<script setup>
defineProps({
  items: { type: Array, required: true },
  selectedIds: {type: Object, required: true},
  onSelect: {type: Function, required: true}
})

</script>
<template>
    <div class="selector-grid">
        <div
            v-for="item in items"
            :key="item.id"
            class="selector-card"
            :class="{
                selected: selectedIds.has(item.id)
            }"
            @click="onSelect(item.id)"
        >
            <div class="selector-header">
                <i :class="item.icon"/>
                <h3>{{ item.name}}</h3>
            </div>
            <p>{{item.description}}</p>
        </div>
    </div>
</template>
<style scoped src="./OptionsGrid.css"></style>

