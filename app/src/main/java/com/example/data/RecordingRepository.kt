package com.example.data

import com.example.model.RecordingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Elenco registrazioni: vive su Room, non più in memoria. */
class RecordingRepository(private val dao: RecordingDao) {

    val recordings: Flow<List<RecordingItem>> = dao.observeAll().map { entities ->
        entities.map { it.toRecordingItem() }
    }

    suspend fun all(): List<RecordingItem> = dao.getAll().map { it.toRecordingItem() }

    suspend fun find(id: String): RecordingItem? = dao.getById(id)?.toRecordingItem()

    suspend fun save(item: RecordingItem) {
        dao.upsert(item.toEntity())
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
