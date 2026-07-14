<script setup>

import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { startAuthRefresh } from './composables/useRefreshToken'



const auth = useAuthStore()

onMounted(() => {
  if (auth.isAuthenticated) {
    startAuthRefresh()
  }
})

</script>

<template>
  <router-view />
  <PConfirmDialog />
</template>
