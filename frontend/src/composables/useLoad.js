import { useI18n } from "vue-i18n";

export function useLoad() {
    const { t } = useI18n()
    

    async function executeLoad(_api,_url ,_ref, _refLoadError,  _refLoaded = null, _transformationFunction = null) {
        if (_refLoaded != null && _refLoaded.value) return;
        
        if (_api == null || _url == null || _ref == null || _refLoadError == null) return;
        try {
          const response = await _api.get(_url)
          if (response.ok) {
            const data = await response.json()
            _ref.value = _transformationFunction == null ?
              data : _transformationFunction(data)
    
            if(_refLoaded != null) _refLoaded.value = true
          }else {
            const error = await response.json().catch(()=> null)
            _refLoadError.value = _api.translateError(error, 'error.connection')
          }
        } catch {
          _refLoadError.value = t('error.connection')
        }
    
    }

    async function post(_api,_url, _body ,_ref, _refLoadError,  _refLoaded = null, _transformationFunction = null) {
        if (_refLoaded != null && _refLoaded.value) return;
        
        if (_api == null || _url == null || _ref == null || _refLoadError == null) return;
        try {
          const response = await _api.post(_url,_body)
          if (response.ok) {
            const data = await response.json()
            _ref.value = _transformationFunction == null ?
              data : _transformationFunction(data)
    
            if(_refLoaded != null) _refLoaded.value = true
          }else {
            const error = await response.json().catch(()=> null)
            _refLoadError.value = _api.translateError(error, 'error.connection')
          }
        } catch {
          _refLoadError.value = t('error.connection')
        }
    
    }

    return {
        executeLoad,
        post
    }
}