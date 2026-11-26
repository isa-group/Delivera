import { useSmartTranslation } from "./useSmartTranslation"

export enum ParamType {

    INTEGER = 'INTEGER',
    NUMBER = 'NUMBER',
    BOOLEAN = 'BOOLEAN'
}


export interface ParamSpecification {
    name: string

    displayName?: string

    type: ParamType | null

    enum: string[] | null

    default: any | null

    minimum:  Number | null

    maximun: Number | null

    enabled: boolean

    step?: Number

}

export function useParamBuilder() {
    const { getDisplayName }= useSmartTranslation()

    function build(params: ParamSpecification[]) {
        let errors: string[] = []
        const renderedParams = []

        params.forEach(param => {
            const paramErrors = validateParam(param)
            errors = [...errors, ...paramErrors] 

            if (paramErrors.length == 0) {
                renderedParams.push(buildParam(param))
            }   
        })

        return {
            params: renderedParams,
            errors: errors
        }

    }

    function buildParam(param: ParamSpecification) {
        return {
            ...param,
            displayName: param.displayName ?? getDisplayName(param.name)
        }


    }
    
    function validateParam(spec: ParamSpecification): string[] {

        
        const errors: string[] = []

        const hasType = spec.type !== null

        const hasEnum = (
            spec.enum !== null &&
            spec.enum?.length > 0
        )

        if (!hasType && !hasEnum) {

            errors.push(
                `${spec.name}: either type or enum must be defined`
            )

            return errors

        }

        if (hasType && hasEnum) {

            errors.push(
                `${spec.name}: type and enum cannot be used together`
            )

            return errors

        }

        if (
            spec.enum &&
            spec.default !== null &&  spec.default !== undefined &&
            !spec.enum?.includes(spec.default)
        ) {
        
            errors.push(
                `${spec.name}: default value must belong to enum`
            )
        
        }

    
        switch (spec.type) {
    
            case ParamType.INTEGER:
    
                if (
                    spec.minimum !== null && spec.minimum !== undefined &&
                    !Number.isInteger(spec.minimum)
                ) {
                    errors.push(`${spec.name}: minimum must be integer`)
                }
    
                if (
                    spec.maximun !== null &&  spec.maximun !== undefined &&
                    !Number.isInteger(spec.maximun)
                ) {
                    errors.push(`${spec.name}: maximum must be integer`)
                }
    
                if (
                    spec.default !== null &&  spec.default !== undefined &&
                    !Number.isInteger(spec.default)
                ) {
                    errors.push(`${spec.name}: default must be integer`)
                }
    
                break
    
            case ParamType.NUMBER:
    
                if (
                    spec.minimum !== null && spec.minimum !== undefined &&
                    typeof spec.minimum !== 'number'
                ) {
                    errors.push(`${spec.name}: minimum must be number`)
                }
    
                if (
                    spec.maximun !== null && spec.maximun !== undefined &&
                    typeof spec.maximun !== 'number'
                ) {
                    errors.push(`${spec.name}: maximum must be number`)
                }
    
                if (
                    spec.default !== null && spec.default !== undefined &&
                    typeof spec.default !== 'number'
                ) {
                    errors.push(`${spec.name}: default must be number`)
                }
    
                break
    
            case ParamType.BOOLEAN:
    
                if (
                    spec.default !== null && spec.default !== undefined &&
                    typeof spec.default !== 'boolean'
                ) {
                    errors.push(`${spec.name}: default must be boolean`)
                }
    
                break
        }
    
        return errors
    }

    return {
        build,
        validateParam
    }

}