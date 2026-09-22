import { ref, onMounted, watch, nextTick, onUnmounted, computed } from 'vue'
import 'pricing-renderer';
import { useServices } from '@/composables/useServices';
import { useLoad } from '@/composables/useLoad';
import { useI18n } from 'vue-i18n';

export function usePrincing() {
    const serviceName = "delivera"
    const pricingRenderer = ref()
    const pricingVersion = ref(0)
    const initialCost = ref()
    const contract = ref()
    const selection = ref()
    const iPricing = ref()
    const contractError = ref()
    const updateError = ref()
    const ymlUrl = ref()
    const autoRenew = ref()
    const pricingLocale = computed(() => resolveLocale())
    const phases = new Set([
        "preview",
        "confirmation"
    ])
    const changePhases = {
        "preview": {next: "confirmation"},
        "confirmation": {previous: "preview"}
    }
    const phase = ref("preview")
    const { locale } = useI18n()
    const deliveraApi = useServices("delivera")
    const { executeLoad, post, put } = useLoad()

    let readyHandler = null
    let selectionHandler = null

    const usedPercentage = computed(() => {
        const billing = contract.value?.billingPeriod
    
        if (!billing) return 0
    
        const start = new Date(billing.startDate).getTime()
        const end = new Date(billing.endDate).getTime()
        const now = Date.now()
    
        if (isNaN(start) || isNaN(end) || end <= start) {
            return 0
        }
    
        const percentage = ((now - start) / (end - start)) * 100
    
        return Math.round(
            Math.max(0, Math.min(100, percentage))
        )
    })

    const usedDays = computed(() => {
        const billing = contract.value?.billingPeriod
    
        if (!billing) return 0
    
        const start = new Date(billing.startDate).getTime()
        const end = new Date(billing.endDate).getTime()
        const now = Date.now()
    
        if (isNaN(start) || isNaN(end) || end <= start) {
            return 0
        }
    
        return Math.ceil((now - start) /(1000*60*60*24))
    })

    function getStartPhase() {
        return "preview"
    }

    function getEndPhase() {
        return "confirmation"
    }

    function canLoadPricing() {
        return contract.value && ymlUrl.value?.yamlPath
    }

    function checkPhaseEquals(_phase, namedPhase) {
        if (_phase!= null) {
            return _phase === namedPhase
        }
        return phase.value === namedPhase
    }

    function startPhase(_phase = null) {
        return checkPhaseEquals(_phase, getStartPhase())
    }

    function endPhase(_phase = null) {
        return checkPhaseEquals(_phase, getEndPhase())
    }


    function getPreviousPhase() {
        return changePhases[phase.value]?.previous?? null
    }

    function getNextPhase() {
        return changePhases[phase.value]?.next?? null
    }

    function previousPhase() {
        const _phase = getPreviousPhase()
        if (_phase == null) return;
        phase.value = _phase
        
    }

    function nextPhase() {
        const _phase = getNextPhase()
        if (_phase == null) return; 
        phase.value = _phase
    }

    function resolveLocale() {
        const toMap = {
            "es": "es-ES",
            "en": "en-US"
        }
        return toMap[locale.value] ?? "en-US"
        //return "en-US"

    }

    function pricingUrl() {
        return ymlUrl.value.yamlPath
    }

    function hasChanges() {
        const changes = resume()
        return changes.some( (change) => change?.changed)
    }

    function cancelAllChanges() {
        phase.value = getStartPhase()
        selection.value = defaultSelectionObject()
        pricingVersion.value++
    }

    function loadContract(pricing, contract) {
        if (!pricing || !contract) return;
        pricing.planId = contract.subscriptionPlans[serviceName]
        pricing.addOns = Object.entries({...contract.subscriptionAddOns[serviceName]})
            .reduce((acc,[k,v]) => { 
                acc[k] = {selected: true, quantity: v }
                return acc
            },{})
    }

    function loadContractFromSelection(pricing, contract) {
        if (selection.value) {
            pricing.planId = selection.value.planId
            pricing.addOns = selection.value.addOns
        } else {
            loadContract(pricing, contract)
        }

    }

    function defaultSelectionObject() {
        if (!selection.value) {
            selection.value =  {
                planId: contract.value.subscriptionPlans[serviceName],
                addOns: Object.entries({...contract.value.subscriptionAddOns[serviceName]})
                .reduce((acc,[k,v]) => { 
                    acc[k] = {selected: true, quantity: v }
                    return acc
                },{})
            }
        }
    }

   

    async function confirmChanges() {
        if (!hasChanges() && !selection.value ) return;
        await post(deliveraApi,"/pricing/contract", 
            {plan: selection.value.planId ,newAddOns: selection.value.addOns},
            contract, 
            updateError
        )
        selection.value = null
        iPricing.value = null
        pricingVersion.value++
        phase.value = getStartPhase()

    }

    async function toggleAutoRenew() {
        await put(deliveraApi,"/pricing/contract/autoRenew",{},autoRenew,updateError,null, () => {
            autoRenew.value = !autoRenew.value
            contract.value.billingPeriod.autoRenew = !autoRenew.value
        })
        
        
    }

    function updateChanges(detail, pricing, contractRef) {
        if (! (detail && detail.selection && detail.selection.planId && detail.selection.addOns) ) return;
        updateError.value = null
        selection.value = detail.selection
        iPricing.value = detail.resolved
    }

    function currentAddOns(contract) {
        return  Object.entries({...contract.subscriptionAddOns[serviceName]})
        .reduce((acc,[k,v]) => { 
            acc[k] = {selected: true, quantity: v }
            return acc
        },{})
    }


    function getChangesInAddOns() {
        const beforeAddOns = currentAddOns(contract.value)
        const afterAddOns = selection.value.addOns
        return Object.entries({...iPricing?.value?.addOnPrices || {}}).reduce((acc,[k,v])=>{
            const before = beforeAddOns[k]?? {selected: false, quantity: 0}
            const after = afterAddOns[k]?? {selected: false, quantity: 0}
            const changed = before.selected !== after.selected || before.quantity !== after.quantity
            acc.push({
                name:k,
                before: before,
                after: after,
                changed: changed,
                type: 'addOn'
            })
            return acc
        },[])

    }

    function getPlanChange() {
        const before = contract.value.subscriptionPlans[serviceName]
        const after = selection.value?.planId 
        const changed = before !== after
        return {name: "plan", before: before  , after: after, changed: changed, type: 'plan' }
    }
    function printTypePlan (change) {
        return change
    }

    function printTypeAddOn (change) {
        return change?.selected? change.quantity  : 'NO'
    }

    function printChange(change, type = null) {
        if (type == null) return change
        const functionByType = {
            'plan': printTypePlan,
            'addOn': printTypeAddOn
        }
        const _function = functionByType[type]
        if (_function) {
            return _function(change)
        }

    }


    function resume(onlyChanges = false) {
        defaultSelectionObject()
        return [
            getPlanChange(),
            ...getChangesInAddOns()
        ].filter(element => !onlyChanges || element.changed)
    }

    function resumeCost() {
        return {before: initialCost.value, after: iPricing.value?.subtotal}
    }

    function pricingReadyEvent(el) {
        readyHandler = e => {
            if (startPhase() && contract.value) {
                loadContractFromSelection(
                    el._internalSelection,
                    contract.value
                );
                if (!iPricing.value) {
                    initialCost.value = pricingRenderer.value._viewModel?.()?.resolved?.subtotal?? null
                }
               
            }
        }
        el.addEventListener(
            'pricing-ready',
            readyHandler
        );
    }

    function pricingSelectionEvent(el){
        selectionHandler = async e => {
            updateChanges(e.detail,el._internalSelection, contract)
        }
        el.addEventListener(
            'pricing-selection-change',
            selectionHandler
        );
    }
   

    watch(
        () => pricingRenderer.value,
        (el) => {

            if (!el) return;

            pricingReadyEvent(el)
            pricingSelectionEvent(el)
        }
        //,{ once: true }
    );

    onMounted(async () => {
            await Promise.all([
                executeLoad(deliveraApi,"/pricing/contract", contract, contractError),
                executeLoad(deliveraApi,"/pricing", ymlUrl, contractError)
            ])
            if (contract.value) {
                autoRenew.value = contract.value?.billingPeriod?.autoRenew
            }
           
        }
    );

    onUnmounted(() => {
        if (pricingRenderer.value) {
            pricingRenderer.value.removeEventListener(
                'pricing-ready',
                readyHandler
            )
            pricingRenderer.value.removeEventListener(
                'pricing-selection-change',
                selectionHandler
            )
        }
    })

    return {
        contract,
        autoRenew,
        pricingRenderer,
        pricingVersion,
        contractError,
        updateError,
        phase,
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
        endPhase,
        

    }

}