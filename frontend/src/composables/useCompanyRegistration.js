import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useApi } from '@/composables/useApi'
import { useValidation } from '@/composables/useValidation'
import { useAvailabilityCheck } from '@/composables/useAvailabilityCheck'

function isUsernameFormat(val) {
  return /^[a-z0-9_-]{3,50}$/.test(val)
}

function isHandleFormat(val) {
  return /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/.test(val)
}

export function useCompanyRegistration() {
  const { t } = useI18n()
  const router = useRouter()
  const auth = useAuthStore()
  const api = useApi()
  const { validate, required, email: emailRule, minLength, maxLength, pattern, passwordStrength, usernameFormat, errors, invalids } = useValidation()

  const step = ref(1)

  // Paso 1 — organización
  const orgName = ref('')
  const orgHandle = ref('')

  // Paso 2 — empresa
  const activityType = ref(null)
  const companyName = ref('')

  // Paso 3 — cuenta
  const email = ref('')
  const username = ref('')
  const firstName = ref('')
  const lastName = ref('')
  const phone = ref('')
  const password = ref('')

  const error = ref('')
  const loading = ref(false)

  const { checking: usernameChecking, available: usernameAvailable } =
    useAvailabilityCheck(username, {
      endpoint: '/auth/check-username',
      paramName: 'username',
      validate: isUsernameFormat
    })

  const { checking: handleChecking, available: handleAvailable } =
    useAvailabilityCheck(orgHandle, {
      endpoint: '/organizations/check-handle',
      paramName: 'handle',
      validate: isHandleFormat
    })

  // Auto-sugerir handle desde orgName
  watch(orgName, (val) => {
    if (step.value === 1) {
      orgHandle.value = val
        .toLowerCase()
        .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+/, '').replace(/-+$/, '')
    }
  })

  function goToStep2() {
    error.value = ''
    if (!validate({ orgName: [required(orgName.value, 'orgName')] })) return
    if (!orgHandle.value || !isHandleFormat(orgHandle.value)) {
      error.value = t('validation.orgCodeInvalid')
      return
    }
    if (handleAvailable.value === false) {
      error.value = t('validation.orgCodeTaken')
      return
    }
    step.value = 2
  }

  function goToStep3() {
    error.value = ''
    if (!activityType.value) {
      error.value = t('validation.activityTypeRequired')
      return
    }
    if (!validate({ companyName: [required(companyName.value, 'companyName')] })) return
    step.value = 3
  }

  async function submitRegistration() {
    error.value = ''
    const phoneRegex = /^[+0-9\s()-]+$/
    const valid = validate({
      email: [required(email.value, 'email'), emailRule(email.value)],
      username: [
        required(username.value, 'username'),
        minLength(username.value, 3, 'username'),
        maxLength(username.value, 50, 'username'),
        usernameFormat(username.value),
      ],
      firstName: [
        required(firstName.value, 'firstName'),
        maxLength(firstName.value, 100, 'firstName'),
      ],
      lastName: [maxLength(lastName.value, 100, 'lastName')],
      phone: [
        maxLength(phone.value, 20, 'phone'),
        pattern(phone.value, phoneRegex, 'validation.phoneFormat'),
      ],
      password: [required(password.value, 'password'), minLength(password.value, 8, 'password'), passwordStrength(password.value)],
    })
    if (!valid) return

    loading.value = true
    try {
      const res = await api.post('/auth/register/company', {
        email: email.value,
        username: username.value,
        firstName: firstName.value.trim(),
        lastName: lastName.value.trim() || null,
        phone: phone.value || null,
        password: password.value,
        orgName: orgName.value,
        orgHandle: orgHandle.value,
        companyName: companyName.value,
        activityType: activityType.value,
      })
      const isJson = res.headers.get('content-type')?.includes('application/json')
      const data = isJson ? await res.json().catch(() => null) : null
      if (res.ok && data?.token) {
        auth.applyLoginData(data)
        router.push('/onboarding')
      } else {
        error.value = data ? api.translateError(data, 'error.registerFailed') : t('error.registerFailed')
      }
    } catch {
      error.value = t('error.connection')
    } finally {
      loading.value = false
    }
  }

  return {
    step,
    orgName, orgHandle, handleChecking, handleAvailable, isHandleFormat,
    activityType, companyName,
    email, username, firstName, lastName, phone, usernameChecking, usernameAvailable, isUsernameFormat,
    password,
    error, errors, invalids, loading,
    goToStep2, goToStep3, submitRegistration,
  }
}
