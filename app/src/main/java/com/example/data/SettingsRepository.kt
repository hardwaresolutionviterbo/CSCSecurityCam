package com.example.data

import com.example.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: SettingsDao) {

    val settings: Flow<AppSettings> = dao.observe().map { entity ->
        entity?.toAppSettings() ?: AppSettings()
    }

    suspend fun get(): AppSettings = dao.get()?.toAppSettings() ?: AppSettings()

    suspend fun save(settings: AppSettings) {
        dao.upsert(settings.toEntity())
    }
}
