<script setup>
import { usePrincing } from '@/composables/usePricing';
import { ProgressBar } from 'primevue';
import { useI18n } from 'vue-i18n';

const { t } = useI18n()

const {
    contract,
    autoRenew,
    pricingRenderer, 
    pricingVersion, 
    contractError,
    updateError,
    usedPercentage,
    usedDays,
    pricingLocale,
    pricingUrl,
    canLoadPricing,
    hasChanges,
    cancelAllChanges,
    confirmChanges,
    toggleAutoRenew,
    resume,
    resumeCost,
    printChange,
    nextPhase,
    previousPhase,
    startPhase,
    endPhase

} = usePrincing()




</script>


<template>
    <div  class="background">
        <PMessage v-if="updateError" severity="error" :closable="false">{{ updateError }}</PMessage>
        <PMessage v-if="contractError" severity="error" :closable="false">{{ contractError }}</PMessage>
        <div  v-if="startPhase()">
            <pricing-renderer
                v-if="canLoadPricing()"
                :key="pricingVersion"
                ref="pricingRenderer"
                :src="pricingUrl()"
                :locale= "pricingLocale"
                pricing-path="/pricing"
                mode="commercial"
                theme="light"
            >
            </pricing-renderer>
        </div>
        <div class="confirmation-background" v-if="endPhase()">
            <h1>{{t('pricing.confirmation')}}</h1>
            <h2>{{t('pricing.resume')}}</h2>
            <table class="resume">
                <thead>
                    <tr>
                        <th>
                            {{t('pricing.concept')}}
                        </th>
                        <th>
                            {{t('pricing.before')}}
                        </th>
                        <th>
                            {{t('pricing.after')}}
                        </th>
                        <th>
                            {{t('pricing.changed')}}
                        </th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="element in resume()" :key="element.name">
                        <td>
                            {{element?.name}}
                        </td>
                        <td>
                            <span v-if="printChange(element.before, element?.type) !== 'NO'" :class="'success'">
                                {{printChange(element.before, element?.type)}}
                            </span>
                            <i v-else
                                :class="'pi pi-times-circle error'"
                            ></i>
                        </td>
                        <td>
                            <span v-if="printChange(element.after, element?.type) !== 'NO'" :class="'success'">
                                {{printChange(element.after, element?.type)}}
                            </span>
                            <i v-else
                                :class="'pi pi-times-circle error'"
                            ></i>
                        </td>
                        <td>
                            <i
                            :class="element.changed
                                ? 'pi pi-check-circle success'
                                : 'pi pi-times-circle error'"
                            ></i>
                        </td>
                    </tr>
                </tbody>

            </table>
            <h2>{{t('pricing.changes')}}</h2>
            <table class="resume">
                <thead>
                    <tr>
                        <th>
                            {{t('pricing.concept')}}
                        </th>
                        <th>
                            {{t('pricing.before')}}
                        </th>
                        <th>
                            {{t('pricing.after')}}
                        </th>
                        <th>
                            {{t('pricing.changed')}}
                        </th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="element in resume(true)" :key="element.name">
                        <td>
                            {{element?.name}}
                        </td>
                        <td>
                            <span v-if="printChange(element.before, element?.type) !== 'NO'" :class="'success'">
                                {{printChange(element.before, element?.type)}}
                            </span>
                            <i v-else
                                :class="'pi pi-times-circle error'"
                            ></i>
                        </td>
                        <td>
                            <span v-if="printChange(element.after, element?.type) !== 'NO'" :class="'success'">
                                {{printChange(element.after, element?.type)}}
                            </span>
                            <i v-else
                                :class="'pi pi-times-circle error'"
                            ></i>
                        </td>
                        <td>
                            <i
                            :class="element.changed
                                ? 'pi pi-check-circle success'
                                : 'pi pi-times-circle error'"
                            ></i>
                        </td>
                    </tr>
                </tbody>
            </table>
            <h2>{{t('pricing.price')}}</h2>
            <div class="price-summary">
                <span class="price-before">{{resumeCost().before}}€</span>
                <span class="price-arrow">→</span>
                <span class="price-after">{{resumeCost().after}}€</span>
            </div>
            <div class="actions">
                <PButton
                    v-if="canLoadPricing() && !startPhase()"
                    :label="t('pricing.previous')"
                    icon="pi pi-arrow-left"
                    :aria-label="t('pricing.previous')"
                    @click="previousPhase()"
                />
                <PButton
                    severity="danger"
                    v-if="canLoadPricing() && hasChanges() && endPhase()"
                    :label="t('pricing.cancel')"
                    icon="pi pi-times"
                    :aria-label="t('pricing.cancel')"
                    @click="cancelAllChanges()"
                />
                <PButton
                    severity="success"
                    v-if="canLoadPricing() && hasChanges() && endPhase()"
                    :label="t('pricing.confirm')"
                    icon="pi pi-check-circle"
                    :aria-label="t('pricing.confirm')"
                    @click="confirmChanges()"
                />
            </div>
        </div>
        <div v-if="canLoadPricing()" class="contract-info">
            <h3>{{ t('pricing.BillingInformation') }}</h3>

            <div class="contract-grid">
                <div class="contract-item">
                    <span class="label">{{ t('pricing.startDate') }}</span>
                    <span>
                        {{ new Date(contract.billingPeriod.startDate).toLocaleDateString() }}
                    </span>
                </div>

                <div class="contract-item">
                    <span class="label">{{ t('pricing.endDate') }}</span>
                    <span>
                        {{ new Date(contract.billingPeriod.endDate).toLocaleDateString() }}
                    </span>
                </div>

                <div class="contract-item">
                    <span class="label">{{ t('pricing.autoRenew') }}</span>
                    <i
                        :class=" autoRenew
                            ? 'pi pi-check-circle success'
                            : 'pi pi-times-circle error'"
                        ></i>
                </div>

                <div class="contract-item">
                    <span class="label">{{ t('pricing.renewalPeriod') }}</span>
                    <span>
                        {{ contract.billingPeriod.renewalDays }}
                        {{ t('pricing.days') }}
                    </span>
                </div>
                <div class="contract-item">
                    <span class="label">{{ t('pricing.usedPercentage') }}</span>
                    
                    <ProgressBar :value="usedPercentage" /> <p>{{t('pricing.days')}}: {{usedDays}} </p>
                </div>
                <div class="contract-item">
                    <span class="label">{{ t( autoRenew? 'pricing.desactivateRenew':'pricing.activateRenew') }}</span>
                    <PButton
                        :severity="!autoRenew ? 'success':'danger'"
                        v-if="canLoadPricing()"
                        :label=" t(!autoRenew ? 'pricing.confirm':'pricing.cancel')"
                        :icon="!autoRenew ? 'pi pi-check-circle' :'pi pi-times'"
                        :aria-label="t(!autoRenew ? 'pricing.confirm':'pricing.cancel')"
                        @click="toggleAutoRenew()"
                    />
                </div>
            </div>
        </div>
        <div class="actions">
            <PButton
                severity="danger"
                v-if="canLoadPricing() && hasChanges() && !endPhase()"
                :label="t('pricing.cancel')"
                icon="pi pi-times"
                :aria-label="t('pricing.cancel')"
                @click="cancelAllChanges()"
            />
            <PButton
                v-if="canLoadPricing() && hasChanges() && !endPhase()"
                :label="t('pricing.next')"
                icon="pi pi-arrow-right"
                :aria-label="t('pricing.next')"
                @click="nextPhase()"
            />
            
        </div>
    </div>
</template>

<!--<style scoped src="./PricingView.css"></style>-->

<style scoped>

.contract-info {
    margin-top: 5%;
    width: 100%;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    padding: 1.5rem;
}

.contract-info h3{
    margin-bottom: 2.5%;
   
}

.contract-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 1rem;
}

.contract-item {
    display: flex;
    flex-direction: column;
    padding: 1rem;
    background: white;
    border-radius: 8px;
}

.label {
    color: #64748b;
    font-size: .9rem;
    margin-bottom: .25rem;
}
.confirmation-background {
    max-width: 1000px;
    margin: 2rem auto;

    padding: 2rem;

    background: #ffffff;
    border-radius: 16px;

    box-shadow:
        0 4px 12px rgba(0,0,0,0.08),
        0 12px 32px rgba(0,0,0,0.08);

    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1.5rem;
}

.confirmation-background {
    max-width: 1200px;
    margin: 2rem auto;
    padding: 2rem 3rem;

    background: #fff;

    border-radius: 20px;

    box-shadow:
        0 4px 12px rgba(0,0,0,.05),
        0 12px 40px rgba(0,0,0,.08);
}

.confirmation-background h1 {
    font-size: 2.2rem;
    font-weight: 700;
    color: #1e293b;
    margin: 0;
}

.confirmation-background h2 {
    font-size: 1.4rem;
    color: #475569;
    margin: 0;
}

.confirmation-background h3 {
    margin: 0;
}

.resume {
    width: 100%;

    border-collapse: collapse;

    background: white;

    border-radius: 12px;
    overflow: hidden;

    box-shadow: 0 2px 8px rgba(0,0,0,0)
}

.resume thead {
    background: #8b5cf6;
    color: white;
}

.resume th {
    padding: 1rem;
    text-align: center;
    font-weight: 600;
}

.resume td {
    padding: 0.9rem;
    text-align: center;
}

.resume {
    width: 100%;
    border-collapse: separate;
    border-spacing: 0;

    background: white;

    border-radius: 12px;
    overflow: hidden;

    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.resume td {
    padding: 1rem;
    text-align: center;

    border-bottom: 1px solid #e5e7eb;
}

.resume tbody tr:last-child td {
    border-bottom: none;
}

.resume tbody tr:hover {
    background: #f5f3ff;
}

.price-summary {
    display: flex;
    gap: 1rem;
    align-items: center;

    font-size: 2rem;
    font-weight: 700;

    color: #8b5cf6;
}

.price-summary {
    display: flex;
    align-items: center;
    gap: 1rem;

    font-size: 3rem;
    font-weight: 700;

    margin: 2rem 0;
}

.price-before {
    color: #64748b;
}

.price-after {
    color: #7c3aed;
}

.price-arrow {
    color: #94a3b8;
}

.arrow {
    color: #64748b;
}

.actions {
    display: flex;
    gap: 1rem;
    justify-content: center;
}

.success {
    color: #22c55e;
    font-weight: bold;
}

.error {
    color: #ef4444;
    font-weight: bold;
}

.success {
    color: #22c55e;
    font-weight: 700;
}

.error {
    color: #ef4444;
    font-weight: 700;
}

.background {
    box-sizing: border-box;

    width: 100%;
    min-height: 100vh;

    background: linear-gradient(
        180deg,
        #f8fafc,
        #eef2ff
    );

    padding: 2rem;
}
pricing-renderer {
    display: block;
    width: 100%;
    max-width: 1600px;
    margin: 0 auto;
}

pricing-renderer {
  --pr-color-accent: #4f46e5;
  --pr-radius-lg: 16px;
}

pricing-renderer {
  --pr-space-section: 1.5rem;
  --pr-shell-padding: 1rem;
}

:deep([data-pr-part='plan-card']) {
  border-radius: 20px;
}

:deep([data-pr-part="cta"]) {
  display: none;
}

:deep([data-pr-part="plan-highlight"]) {
    display: none;
}

:deep([data-pr-part="plan-inheritance"]) {
    display: none;
}

:deep([data-pr-part="hero"]) {
    display: auto;
}
</style>

