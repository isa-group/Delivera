<script setup lang="ts">
import  { FeatureContext } from "@/composables/useSpaceJwt"
import UsageCard from "./UsageCard.vue";

const props = defineProps({
    features: {type: Array, required: true}
})

</script>

<template>
    <div class="quota-grid">
        <UsageCard
            v-for="feature in props.features"
            :key="feature.name"
            :feature="feature"
        />
    </div>

</template>

<style scoped src="./QuotaUsage.css" />
