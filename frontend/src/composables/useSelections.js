import { ref } from "vue"

export function useSelections() {
    const selections = ref(new Set())

    function toggleSelection(id, _ref) {
        const copy = new Set(_ref.value);
    
        if (copy.has(id)) {
            copy.delete(id);
        } else {
            copy.add(id);
        }
    
        _ref.value = copy;
    }

    function selectOnlyOne(id,_ref) {
        if (_ref.value.has(id)) {
            _ref.value = new Set();
            return false
        }else {
            _ref.value = new Set([id]);
            return true
           
        } 
    }



    return {
        selections,
        toggleSelection,
        selectOnlyOne
    }
}