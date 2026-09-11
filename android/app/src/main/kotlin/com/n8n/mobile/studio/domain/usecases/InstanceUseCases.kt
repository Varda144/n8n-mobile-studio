package com.n8n.mobile.studio.domain.usecases

import com.n8n.mobile.studio.data.preferences.PreferencesStore
import com.n8n.mobile.studio.domain.models.N8nInstance
import com.n8n.mobile.studio.security.SecureStorage
import kotlinx.coroutines.flow.first

class SaveInstancesUseCase(
    private val store: PreferencesStore,
    private val secure: SecureStorage,
) {
    suspend operator fun invoke(instance: N8nInstance, apiKey: String) {
        secure.put("n8n_api_${instance.id}", apiKey)
        store.save(store.instances.first() + instance)
    }
}