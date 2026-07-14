<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useServices } from '@/composables/useServices'
import { useValidation } from '@/composables/useValidation'
import BaseLayout from '@/components/BaseLayout.vue'
import { startAuthRefresh } from '@/composables/useRefreshToken'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const api = useServices("auth-service")
const { validate, required, errors, invalids } = useValidation()

const identifier = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  if (!validate({
    identifier: [required(identifier.value, 'loginIdentifier')],
    password: [required(password.value, 'password')],
  })) return

  loading.value = true
  try {
    const res = await api.post('/auth/login', { identifier: identifier.value, password: password.value })
    if (res.ok) {
      const data = await res.json()
      auth.applyLoginData(data)
      startAuthRefresh()
      router.push(auth.isWorker ? '/home' : '/profile')
    } else {
      const data = await res.json()
      error.value = api.translateError(data, 'error.invalidCredentials')
    }
  } catch {
    error.value = t('error.connection')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <BaseLayout>
    <form class="card" @submit.prevent="handleLogin">
      <h1>{{ t('app.name') }}</h1>
      <p class="subtitle">{{ t('auth.signIn') }}</p>

      <div class="form-field">
        <label for="login-identifier">{{ t('fields.loginIdentifier') }}</label>
        <InputText
          id="login-identifier"
          v-model="identifier"
          type="text"
          :placeholder="t('fields.loginIdentifierPlaceholder')"
          :invalid="!!invalids.identifier"
          autocomplete="username"
          fluid
        />
        <small v-if="errors.identifier" class="field-error">{{ errors.identifier }}</small>
      </div>

      <div class="form-field">
        <label for="login-password">{{ t('fields.password') }}</label>
        <PPassword
          id="login-password"
          v-model="password"
          :feedback="false"
          toggle-mask
          :placeholder="t('fields.password')"
          :invalid="!!invalids.password"
          :pt="{ pcinput: { root: { autocomplete: 'current-password' } } }"
          fluid
        />
        <small v-if="errors.password" class="field-error">{{ errors.password }}</small>
      </div>

      <PMessage v-if="error" severity="error" :closable="false" class="form-message">{{ error }}</PMessage>

      <PButton
        type="submit"
        :label="loading ? t('common.loading') : t('auth.login')"
        :loading="loading"
        fluid
        class="submit-btn"
      />

      <p class="form-link">
        {{ t('auth.noAccount') }} <router-link to="/register">{{ t('auth.signUp') }}</router-link>
      </p>
    </form>
  </BaseLayout>
</template>
