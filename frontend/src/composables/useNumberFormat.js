import { useI18n } from "vue-i18n"


export function useNumberFormat() {
    const { locale } = useI18n() 
    
    
    function numberI18n({value, minFractionDigits = 2, maxFractionDigits = 2}) {
        if (Number.isNaN(value)) return;

        return new Intl.NumberFormat(
            locale.value,
            {
                minimumFractionDigits: minFractionDigits,
                maximumFractionDigits: maxFractionDigits
            }
        ).format(value)
    }




    return {
        numberI18n
    }
}