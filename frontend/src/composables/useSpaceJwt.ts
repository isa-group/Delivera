import { computed, ComputedRef, onMounted, Ref, ref } from "vue"
import { useLoad } from "./useLoad"
import { useServices } from "./useServices"
import { useSmartTranslation } from "./useSmartTranslation"



export interface FeatureEvaluation {
    eval: boolean

    limit: Record<string, number> | null

    used: Record<string, number> | null

    error: string | null
}



export interface PricingContext {
    features: Record<string, boolean | string | number>

    usageLimits: Record<string, number>
}

export interface SpaceJwtRequest {
    pricingToken: string

}

export interface SpaceJwtPayload {
    features: Record<string, FeatureEvaluation>

    pricingContext: PricingContext

    subscriptionContext: Record<string, number>

    iat: number

    exp: number

    sub: string
}

export interface FeatureContext {    
    name: string

    displayName: string

    usage: number

    limit: number

    remaining: number

    percentage: number

    severity: string
}

export interface SpaceContext {
    payload: Ref<SpaceJwtPayload | null>

    featureContexts: ComputedRef<FeatureContext[]>
}


export function useSpaceJwt(): SpaceContext {
    

    const jwtPayload:  Ref<SpaceJwtPayload | null> =  ref(null)
    const errorLoadingJwt: Ref<string | null>  = ref(null)

    const featureContexts: ComputedRef<FeatureContext[]> = computed(getFeaturesAndUsages)

    const { getDisplayName } = useSmartTranslation()
    const { executeLoad} = useLoad()
    const dataApi = useServices('data-service')

    const prefix = 'delivera-'

    onMounted(() => {

        executeLoad(
            dataApi, 
            "/space/jwt", 
            jwtPayload, 
            errorLoadingJwt,
            null,
            decodeJwt
        )
    })

    function decodeJwt(request: SpaceJwtRequest): SpaceJwtPayload | null {
        const jwt: string = request.pricingToken

        try {
            return JSON.parse(atob(jwt.split('.')[1])) as SpaceJwtPayload
        } catch {
            return null
        }
    }

    function getSeverity(
        percentage: number
    ): string {
    
        if (percentage >= 85) {
            return 'danger'
        }
    
        if (percentage >= 70) {
            return 'warning'
        }
    
        return 'success'
    
    }

   


    function getFeaturesAndUsages(): FeatureContext[] {
        const context: SpaceJwtPayload | null = jwtPayload.value

        if (!context && errorLoadingJwt.value) {
            console.error(
                'There is no sapce context'
            )
            return []
        }
        
        const subscriptionContextArray = Object.entries(context?.subscriptionContext ?? []) 

        const featureContexts: FeatureContext[] = []
        
        let y = 0
        for (const [name, usage] of subscriptionContextArray) {
            const limit: number | undefined =  context.pricingContext.usageLimits[name]
            const parsedName: string = name.replace(prefix, '')

            const remaining: number =  (limit ?? 0) - usage
            const percentage: number = limit > 0
                ? usage / limit * 100
                : 0

           
            featureContexts.push(
                {
                    name: parsedName, usage,
                    displayName: getDisplayName(parsedName), 
                    limit, remaining, percentage: percentage , severity: getSeverity(percentage)
                }
            )
            y += 1
            
        }
        
        return featureContexts

    }

    return {
        payload: jwtPayload,
        featureContexts: featureContexts
    }
}