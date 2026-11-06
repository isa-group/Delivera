import { useI18n } from "vue-i18n";
import {SemanticRole, SemanticToken, ROLE_MAP} from "../utils/SemanticDictionary"


export interface SmartTranslationUtils {

    getDisplayName: (
        name: string
    ) => string

    splitCamelCase: (
        text: string
    ) => string[]

    prettifyName: (
        text: string
    ) => string
}

export type SemanticBuilder = (
        tokens: SemanticToken[]
    ) => string | null




export  function useSmartTranslation(): SmartTranslationUtils {
    const { 
        t, te, locale 
    } = useI18n()
    
    
    function classifyWord(
        word: string
    ): SemanticRole {
    
        return (
            ROLE_MAP.get(
                word.toLowerCase()
            )
            ??
            SemanticRole.UNKNOWN
        )
    
    }

    function tokenize(
        text: string
    ): SemanticToken[] {
    
        return splitCamelCase(text)
            .map(word => ({
                word,
                role: classifyWord(word)
            }))
    
    }
    
    function splitCamelCase(
        text: string
    ): string[] {
    
        return text.split(
            /(?=[A-Z])/
        )
    
    }

    function translate(
        word: string
    ): string {
    
        const key =
            `vocabulary.${word.toLowerCase()}`
    
        return te(key)
            ? t(key)
            : word.toLowerCase()
    
    }

    function agreeAdjective(
        adjective: string,
        resource: string
    ): string {
    
        if (
            resource.endsWith('os')
            &&
            adjective.endsWith('as')
        ) {
            return adjective.replace(
                /as$/,
                'os'
            )
        }
    
        if (
            resource.endsWith('o')
            &&
            adjective.endsWith('a')
        ) {
            return adjective.replace(
                /a$/,
                'o'
            )
        }
    
        return adjective
    
    }
    


    function hasRoles(
        tokens: SemanticToken[],
        roles: SemanticRole[]
    ): boolean {
    
        if (tokens.length !== roles.length) {
            return false
        }
    
        return roles.every(
            (role, index) =>
                tokens[index].role === role
        )
    
    }

    function buildQuantifierTechnologyResourceAction(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUANTIFIER,
                    SemanticRole.TECHNOLOGY,
                    SemanticRole.RESOURCE,
                    SemanticRole.ACTION
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[0].word)} `
            +
            `de `
            +
            `${translate(tokens[3].word)} `
            +
            `del `
            +
            `${translate(tokens[2].word)} `
            +
            `${translate(tokens[1].word)}`
        )
    
    }

    function buildQuantifierResource(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUANTIFIER,
                    SemanticRole.RESOURCE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[0].word)} `
            +
            `de `
            +
            `${translate(tokens[1].word)}`
        )
    
    }

    function buildQuantifierQualifierResource(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUANTIFIER,
                    SemanticRole.QUALIFIER,
                    SemanticRole.RESOURCE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[0].word)} `
            +
            `de `
            +
            `${translate(tokens[2].word)} `
            +
            `${agreeAdjective(
                translate(tokens[1].word),
                translate(tokens[2].word)
            )}`
        )
    
    }

    function buildStateResource(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.STATE,
                    SemanticRole.RESOURCE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[1].word)} `
            +
            `${agreeAdjective(
                translate(tokens[0].word),
                translate(tokens[1].word)
            )}`
        )
    }

    function buildUsageAction(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.USAGE,
                    SemanticRole.ACTION
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[1].word)} `
            +
            `${translate(tokens[0].word)}`
        )
    
    }

    function buildQuantifierStateResource(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUANTIFIER,
                    SemanticRole.STATE,
                    SemanticRole.RESOURCE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[0].word)} `
            +
            `de `
            +
            `${translate(tokens[2].word)} `
            +
            `${agreeAdjective(
                translate(tokens[1].word),
                translate(tokens[2].word)
            )}`
        )
    
    }

    function buildParameterMeasure(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.PARAMETER,
                    SemanticRole.MEASURE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[1].word)} `
            +
            `de `
            +
            `${translate(tokens[0].word)}`
        )
    
    }

    function buildQuantifierParameter(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUANTIFIER,
                    SemanticRole.PARAMETER
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[0].word)} `
            +
            `de `
            +
            `${translate(tokens[1].word)}`
        )
    
    }

    function buildQualifierParameterMeasure(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUALIFIER,
                    SemanticRole.PARAMETER,
                    SemanticRole.MEASURE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[2].word)} `
            +
            `de `
            +
            `${translate(tokens[1].word)} `
            +
            `${translate(tokens[0].word)}`
        )
    
    }

    function buildQuantifierParameterMeasure(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUANTIFIER,
                    SemanticRole.PARAMETER,
                    SemanticRole.MEASURE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[2].word)} `
            +
            `${translate(tokens[0].word)} `
            +
            `de `
            +
            `${translate(tokens[1].word)}`
        )
    
    }

    function buildParameterParameter(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.PARAMETER,
                    SemanticRole.PARAMETER
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[1].word)} `
            +
            `de `
            +
            `${translate(tokens[0].word)}`
        )
    
    }

    function buildParameterParameterMeasure(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.PARAMETER,
                    SemanticRole.PARAMETER,
                    SemanticRole.MEASURE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[2].word)} `
            +
            `de `
            +
            `${translate(tokens[1].word)}`
        )
    
    }

    function buildQualifierParameter(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUALIFIER,
                    SemanticRole.PARAMETER
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[1].word)} `
            +
            `${translate(tokens[0].word)}`
        )
    
    }

    function buildStateAction(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.STATE,
                    SemanticRole.ACTION
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[1].word)} `
            +
            `${translate(tokens[0].word)}`
        )
    
    }

    function buildQualifierMeasureMeasure(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUALIFIER,
                    SemanticRole.MEASURE,
                    SemanticRole.MEASURE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[2].word)} `
            +
            `de `
            +
            `${translate(tokens[1].word)} `
            +
            `${translate(tokens[0].word)}`
        )
    
    }

    function buildResourceParameterParameter(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.RESOURCE,
                    SemanticRole.PARAMETER,
                    SemanticRole.PARAMETER
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[2].word)} `
            +
            `de `
            +
            `${translate(tokens[1].word)} `
            +
            `de `
            +
            `${translate(tokens[0].word)}`
        )
    
    }

    function buildQuantifierResourceMeasureMeasure(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.QUANTIFIER,
                    SemanticRole.RESOURCE,
                    SemanticRole.MEASURE,
                    SemanticRole.MEASURE
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[2].word)} `
            +
            `${translate(tokens[0].word)} `
            +
            `de `
            +
            `${translate(tokens[1].word)} `
            +
            `${translate(tokens[3].word)}`
        )
    
    }

    

    function buildDefaultName(
        tokens: SemanticToken[]
    ): string {
    
        return prettifyName(
            tokens
                .map(
                    token =>
                        translate(token.word)
                )
                .join(" ")
        )
    
    }

    function buildResourceParameter(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            !hasRoles(
                tokens,
                [
                    SemanticRole.RESOURCE,
                    SemanticRole.PARAMETER
                ]
            )
        ) {
            return null
        }
    
        return (
            `${translate(tokens[1].word)} `
            +
            `de `
            +
            `${translate(tokens[0].word)}`
        )
    
    }
 
    const builders: SemanticBuilder[] = [

        // ===== Solver quotas =====
    
        buildQuantifierTechnologyResourceAction,
    
        // ===== Params case =====
    
        buildQualifierParameterMeasure,
    
        buildParameterParameterMeasure,
    
        buildQuantifierParameterMeasure,

        buildResourceParameterParameter,
    
        // =====  =====
    
        buildParameterMeasure,
    
        buildParameterParameter,
    
        buildQualifierParameter,
    
        buildQuantifierParameter,

        buildQualifierMeasureMeasure,

        buildResourceParameter,

        buildQuantifierResourceMeasureMeasure,
    
        // ===== Business case =====
    
        buildQuantifierQualifierResource,
    
        buildQuantifierStateResource,
    
        buildQuantifierResource,
    
        buildStateResource,

        buildStateAction,
    
        buildUsageAction
    
    ]

    function buildSpanishSemanticName(
        tokens: SemanticToken[]
    ): string | null {
    
        for (const builder of builders) {
    
            const result = builder(tokens)
    
            if (result) {
                return result
            }
    
        }
    
        return null
    
    }

    function buildSemanticName(
        tokens: SemanticToken[]
    ): string | null {
    
        if (
            locale.value !== 'es'
        ) {
            return buildDefaultName(tokens)
        }
    
        return buildSpanishSemanticName(
            tokens
        )
    
    }

    function prettifyName(
        name: string
    ): string {
    
        return name
            .replace(
                /([A-Z])/g,
                ' $1'
            )
            .trim()
            .toLowerCase()
            .replace(
                /^./,
                c => c.toUpperCase()
            ).trim()
    
    }

    function getDisplayName(
        name: string
    ): string {
    
        const specificKey =
            `features.${name}`
    
        if (
            te(specificKey)
        ) {
    
            return t(specificKey)
    
        }
    
        const tokens =
            tokenize(name)
    
        const semantic =
            buildSemanticName(
                tokens
            )
    
        if (semantic) {
    
            return prettifyName(semantic)
    
        }
    
        return prettifyName(
            tokens
                .map(
                    t => translate(t.word)
                )
                .join(" ")
        )
    
    }

    


    return {
        getDisplayName: getDisplayName,
        splitCamelCase: splitCamelCase,
        prettifyName: prettifyName

    }


}