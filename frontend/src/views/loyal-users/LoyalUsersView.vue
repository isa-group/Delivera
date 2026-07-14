<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useFormatDate } from '@/composables/useFormatDate'
import { useResourceList } from '@/composables/useResourceList'
import { useApi } from '@/composables/useApi'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const { formatDate } = useFormatDate()
const router = useRouter()
const api = useApi()
const { items: loyalUsers, loading, error } = useResourceList('/loyal-users')

const showAdd = ref(false)
const addEmail = ref('')
const addName = ref('')
const addPhone = ref('')
const adding = ref(false)
const addError = ref('')
const addSuccess = ref(false)
const emailInvalid = ref(false)

async function addLoyalUser() {
  emailInvalid.value = !addEmail.value.trim()
  if (emailInvalid.value) return
  adding.value = true
  addError.value = ''
  addSuccess.value = false
  try {
    const res = await api.post('/loyal-users', {
      email: addEmail.value.trim(),
      name: addName.value.trim() || null,
      phone: addPhone.value.trim() || null,
    })
    if (res.ok) {
      const created = await res.json()
      loyalUsers.value.unshift(created)
      addEmail.value = ''
      addName.value = ''
      addPhone.value = ''
      showAdd.value = false
      addSuccess.value = true
      setTimeout(() => { addSuccess.value = false }, 3000)
    } else {
      const data = await res.json()
      addError.value = api.translateError(data, 'error.saveFailed')
    }
  } catch {
    addError.value = t('error.connection')
  } finally {
    adding.value = false
  }
}
</script>

<template>
  <div class="surface-card card-full">
    <div class="list-header">
      <h1>{{ t('loyalUsers.title') }}</h1>
      <PButton v-if="auth.isCompanyAdmin" :label="t('loyalUsers.new')" icon="pi pi-plus" @click="showAdd = !showAdd; addError = ''" />
    </div>

    <PMessage v-if="addSuccess" severity="success" :closable="false" class="form-message">{{ t('loyalUsers.added') }}</PMessage>

    <div v-if="showAdd" class="add-form">
      <div class="form-row">
        <InputText v-model="addEmail" type="email" :placeholder="t('fields.emailPersonalPlaceholder')" :invalid="emailInvalid" @input="emailInvalid = false" fluid />
        <InputText v-model="addName" :placeholder="t('loyalUsers.name')" fluid />
        <InputText v-model="addPhone" :placeholder="t('loyalUsers.phone')" fluid />
      </div>
      <div class="form-row">
        <PButton :label="t('common.cancel')" severity="secondary" outlined icon="pi pi-times" @click="showAdd = false" />
        <PButton :label="adding ? t('common.loading') : t('loyalUsers.new')" icon="pi pi-plus" :loading="adding" @click="addLoyalUser" />
      </div>
      <PMessage v-if="addError" severity="error" :closable="false" class="form-message">{{ addError }}</PMessage>
    </div>

    <PMessage v-if="error" severity="error" :closable="false">{{ error }}</PMessage>

    <div class="list-scroll">
    <DataTable :value="loyalUsers" :loading="loading" paginator :rows="10" striped-rows row-hover @row-click="e => router.push(`/loyal-users/${e.data.id}`)">
      <template #empty>
        <EmptyState icon="pi-users" :message="t('loyalUsers.empty')" />
      </template>
      <Column field="email" :header="t('loyalUsers.email')" />
      <Column :header="t('loyalUsers.name')" style="width:180px">
        <template #body="{ data }">{{ data.name || '—' }}</template>
      </Column>
      <Column :header="t('loyalUsers.orders')" style="width:130px">
        <template #body="{ data }">
          <PTag :value="String(data.orderCount)" severity="info" />
        </template>
      </Column>
      <Column :header="t('loyalUsers.registered')" style="width:140px">
        <template #body="{ data }">
          <PTag
            :value="data.registered ? t('loyalUsers.registered') : t('loyalUsers.notRegistered')"
            :severity="data.registered ? 'success' : 'secondary'"
          />
        </template>
      </Column>
      <Column :header="t('loyalUsers.since')" style="width:130px">
        <template #body="{ data }">
          {{ formatDate(data.createdAt) }}
        </template>
      </Column>
    </DataTable>
    </div>
  </div>
</template>

<style scoped src="./LoyalUsersView.css"></style>

