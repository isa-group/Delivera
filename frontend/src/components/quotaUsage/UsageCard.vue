<script setup lang="ts">
import { ProgressBar } from "primevue";
import { useI18n } from "vue-i18n";

const props = defineProps({
    feature: {type: Object, required: true}
})

const {t} = useI18n()

</script>

<template>

    <div class="usage-card">
        <h4>
            {{ feature.displayName}}
        </h4>

        <ProgressBar :value="feature.percentage" :class="feature.severity" />

        <div class="usage-card-info">
            <span>
                {{ feature.usage }} / {{ feature.limit }}
            </span>

            <span class="remaining">
                {{t("usageCard.remaining")}} {{ feature.remaining }}
            </span>
        </div>

    </div>


</template>

<style scoped src="./UsageCard.css" />