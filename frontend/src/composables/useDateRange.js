import { computed, ref } from "vue"
import { useI18n } from "vue-i18n"

export function useDateRange() {


    const fromDate = ref(null)
    const toDate = ref(null)

    const selectedInstants = computed(() => {
        const instants = { from: null, to: null}
            instants.from =  fromDate.value?.toISOString()
            instants.to = toDate.value?.toISOString()
        return instants
            
    })


    return {
        fromDate,
        toDate,
        selectedInstants
    }
}

export function getLocaleFormat() {
    const { locale } = useI18n() 
    const format = {'es': 'dd/mm/yy' , 'en': 'mm/dd/yy' }

    
    return format[locale.value] ?? format['en']
}
