<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useOrderForm } from '@/composables/useOrderForm'

const { t } = useI18n()
const router = useRouter()
const {
  units, loyalUserMatch, loadError,
  orderType, originId, destinationId, b2bOrgId, b2bDestinationId,
  recipientEmail, recipientName,
  recipientAddress, recipientLatitude, recipientLongitude, locating, captureLocation,
  priority, notes, loading, error, errors, invalids,
  destinationOptions, b2bOrganizations, b2bUnitOptions, handleSubmit,
} = useOrderForm()

const typeOptions = computed(() => [
  { label: t('orders.type.INTERNAL'), value: 'INTERNAL' },
  { label: t('orders.type.B2C'),      value: 'B2C' },
  { label: t('orders.type.B2B'),      value: 'B2B' },
])

const priorityOptions = computed(() => [
  { label: t('orders.priority.HIGH'),   value: 'HIGH' },
  { label: t('orders.priority.NORMAL'), value: 'NORMAL' },
  { label: t('orders.priority.LOW'),    value: 'LOW' },
])
</script>

<template>
  <form class="surface-card card-wide" @submit.prevent="handleSubmit">
    <PButton
      type="button"
      text
      severity="secondary"
      icon="pi pi-arrow-left"
      class="form-back-btn"
      @click="router.push('/orders')"
    />

    <h1>{{ t('orders.title') }}</h1>

    <PMessage v-if="loadError" severity="error" :closable="false" class="form-message">{{ loadError }}</PMessage>
    <PMessage v-else-if="units.length < 1 && !loadError" severity="warn" :closable="false" class="form-message">{{ t('orders.noUnits') }}</PMessage>

    <template v-else>
      <!-- Tipo de pedido -->
      <div class="form-field">
        <label for="order-type">{{ t('orders.orderType') }}</label>
        <SelectButton id="order-type" v-model="orderType" :options="typeOptions" option-label="label" option-value="value" />
      </div>

      <!-- Origen (siempre) -->
      <div class="form-field">
        <label for="order-origin">{{ t('orders.origin') }}</label>
        <PSelect
          id="order-origin"
          v-model="originId"
          :options="units"
          option-label="name"
          option-value="id"
          :placeholder="t('orders.originPlaceholder')"
          :invalid="!!invalids.originId"
          fluid
        />
      </div>

      <!-- INTERNAL: unidad de destino -->
      <template v-if="orderType === 'INTERNAL'">
        <div class="form-field">
          <label for="order-destination">{{ t('orders.destination') }}</label>
          <PSelect
            id="order-destination"
            v-model="destinationId"
            :options="destinationOptions"
            option-label="name"
            option-value="id"
            :placeholder="t('orders.destinationPlaceholder')"
            :empty-message="t('orders.noDestinationOptions')"
            :invalid="!!invalids.destinationId"
            fluid
          />
        </div>
      </template>

      <!-- B2C: email + nombre -->
      <template v-else-if="orderType === 'B2C'">
        <div class="form-field">
          <label for="order-email">{{ t('orders.recipientEmail') }}</label>
          <PInputText
            id="order-email"
            v-model="recipientEmail"
            :placeholder="t('orders.recipientEmailPlaceholder')"
            :invalid="!!invalids.recipientEmail"
            type="email"
            fluid
          />
          <small v-if="errors.recipientEmail" class="field-error">{{ errors.recipientEmail }}</small>
          <small v-else-if="loyalUserMatch" class="field-hint">
            <i class="pi pi-check-circle" /> {{ t('orders.loyalUserExists') }}
          </small>
        </div>
        <div class="form-field">
          <label for="order-name">{{ t('orders.recipientName') }}</label>
          <PInputText
            id="order-name"
            v-model="recipientName"
            :placeholder="t('orders.recipientNamePlaceholder')"
            fluid
          />
        </div>
        <div class="form-field">
          <label for="order-address">{{ t('fields.address') }}</label>
          <PInputText
            id="order-address"
            v-model="recipientAddress"
            :placeholder="t('fields.addressPlaceholder')"
            :invalid="!!invalids.recipientAddress"
            maxlength="500"
            fluid
          />
          <small v-if="errors.recipientAddress" class="field-error">{{ errors.recipientAddress }}</small>
          <div class="addr-geo">
            <PButton type="button" :label="t('profile.useCurrentLocation')" icon="pi pi-map-marker" severity="secondary" outlined size="small" :loading="locating" @click="captureLocation" />
            <small v-if="recipientLatitude" class="field-hint">{{ recipientLatitude }}, {{ recipientLongitude }}</small>
          </div>
        </div>
      </template>

      <!-- B2B: organización destino + unidad destino -->
      <template v-else>
        <div class="form-field">
          <label for="order-b2b-org">{{ t('orders.destinationOrg') }}</label>
          <PSelect
            id="order-b2b-org"
            v-model="b2bOrgId"
            :options="b2bOrganizations"
            option-label="name"
            option-value="id"
            :placeholder="t('orders.destinationOrgPlaceholder')"
            :empty-message="t('orders.noB2bOrgs')"
            :invalid="!!invalids.b2bOrgId"
            fluid
          />
        </div>
        <div class="form-field">
          <label for="order-b2b-unit">{{ t('orders.destinationUnit') }}</label>
          <PSelect
            id="order-b2b-unit"
            v-model="b2bDestinationId"
            :options="b2bUnitOptions"
            option-label="displayName"
            option-value="id"
            :placeholder="t('orders.destinationUnitPlaceholder')"
            :empty-message="t('orders.noDestinationOptions')"
            :invalid="!!invalids.b2bDestinationId"
            :disabled="!b2bOrgId"
            fluid
          />
        </div>
      </template>

      <!-- Prioridad -->
      <div class="form-field">
        <label for="order-priority">{{ t('orders.priority.label') }}</label>
        <PSelect
          id="order-priority"
          v-model="priority"
          :options="priorityOptions"
          option-label="label"
          option-value="value"
          fluid
        />
      </div>

      <!-- Notas -->
      <div class="form-field">
        <label for="order-notes">{{ t('orders.notes') }}</label>
        <PTextarea
          id="order-notes"
          v-model="notes"
          :placeholder="t('orders.notesPlaceholder')"
          rows="3"
          fluid
        />
      </div>

      <PMessage v-if="error" severity="error" :closable="false" class="form-message">{{ error }}</PMessage>

      <PButton
        type="submit"
        :label="loading ? t('common.loading') : t('common.save')"
        :loading="loading"
        fluid
        class="submit-btn"
      />
    </template>
  </form>
</template>

<style scoped src="./OrderFormView.css"></style>
