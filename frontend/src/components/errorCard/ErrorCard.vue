<script setup>
import { computed } from 'vue';


const props = defineProps({
  errors: { type: Array, required: true }
})

const showErrors = computed(() => {

    return [...props.errors].filter(error => error.error)
})
</script>

<template>
    <div class="selector-grid succes">
        <div  class="selector-card success" v-if="showErrors.length == 0">
            <h3>{{'there is no errors'}}</h3>
        </div>
    </div>
    
    <div class="selector-grid error" v-if="showErrors.length > 0">
        <div 
            class="selector-card error"  
            :key="error" 
            v-for="error in showErrors"
            
        >
            <h3>{{ error.source}}</h3>
            <span>{{error.error}}</span>
            
        </div>
    </div>
</template>

<style scoped src="./ErrorCard.css"></style>