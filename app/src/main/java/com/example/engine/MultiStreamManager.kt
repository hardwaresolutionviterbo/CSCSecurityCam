package com.example.engine

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una telecamera da mostrare in griglia: id stabile del dispositivo più dove raggiungerla. */
data class GridTarget(
    val id: String,
    val ip: String,
    val port: Int
)

data class GridPlan(
    val close: List<String>,
    val open: List<GridTarget>
)

object GridStreams {
    /** I riquadri contemporanei non devono mai superare questo numero. */
    const val MAX_STREAMS = 4

    /** Lato massimo dei frame in griglia: 640 px bastano per un riquadro 16:10. */
    const val GRID_MAX_SIDE = 640

    /** Tetto richiesto al server in modalità mosaico: quattro flussi a 1080p saturano il Wi-Fi. */
    const val GRID_FPS = 12

    /** Differenza tra ciò che è collegato e ciò che deve esserlo, senza riaprire i flussi uguali. */
    fun plan(current: Map<String, GridTarget>, targets: List<GridTarget>): GridPlan {
        val bounded = targets.take(MAX_STREAMS).distinctBy { it.id }
        val wanted = bounded.associateBy { it.id }
        return GridPlan(
            close = current.keys.filter { it !in wanted },
            open = bounded.filter { target ->
                val existing = current[target.id]
                existing == null || existing.ip != target.ip || existing.port != target.port
            }
        )
    }
}

/** Calcoli puri sulle dimensioni, testabili senza un Bitmap reale. */
object GridSizing {
    fun scaledSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        if (maxSide <= 0 || width <= 0 || height <= 0) return width to height
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val ratio = maxSide.toFloat() / longest
        return (width * ratio).toInt().coerceAtLeast(1) to
            (height * ratio).toInt().coerceAtLeast(1)
    }

    /** inSampleSize potenza di due: BitmapFactory decodifica già a bassa risoluzione. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        if (maxSide <= 0 || width <= 0 || height <= 0) return 1
        var sample = 1
        while (maxOf(width / (sample * 2), height / (sample * 2)) >= maxSide) {
            sample *= 2
        }
        return sample
    }
}

/**
 * Più flussi MJPEG insieme per la griglia del centro di controllo.
 *
 * Ogni riquadro ha il suo [LanStreamClient] perché il client LAN tiene una sola connessione:
 * condividere l'istanza significherebbe interrompere la telecamera precedente a ogni selezione.
 * I frame vengono ridotti a [GridStreams.GRID_MAX_SIDE] e i bitmap sostituiti vengono riciclati
 * dopo una breve attesa, per non strappare l'immagine a Compose mentre la disegna.
 */
class MultiStreamManager(private val scope: CoroutineScope) {

    private data class Entry(
        val client: LanStreamClient,
        val jobs: List<Job>,
        var lastFrame: Bitmap?
    )

    private val clients = LinkedHashMap<String, Entry>()
    private var currentTargets: Map<String, GridTarget> = emptyMap()

    private val _frames = MutableStateFlow<Map<String, Bitmap?>>(emptyMap())
    val frames: StateFlow<Map<String, Bitmap?>> = _frames.asStateFlow()

    private val _stats = MutableStateFlow<Map<String, ClientStreamStats>>(emptyMap())
    val stats: StateFlow<Map<String, ClientStreamStats>> = _stats.asStateFlow()

    /** Collega esattamente i riquadri visibili e scollega gli altri; massimo quattro. */
    fun setStreams(targets: List<GridTarget>) {
        val plan = GridStreams.plan(currentTargets, targets)
        plan.close.forEach { closeStream(it) }
        plan.open.forEach { openStream(it) }
        currentTargets = targets.take(GridStreams.MAX_STREAMS)
            .distinctBy { it.id }
            .associateBy { it.id }
    }

    fun reconnect(id: String) {
        clients[id]?.client?.reconnect()
    }

    fun clear() {
        clients.keys.toList().forEach { closeStream(it) }
        currentTargets = emptyMap()
    }

    private fun openStream(target: GridTarget) {
        closeStream(target.id)

        val client = LanStreamClient(
            scope = scope,
            maxDimension = GridStreams.GRID_MAX_SIDE,
            requestFps = GridStreams.GRID_FPS
        )

        val jobs = mutableListOf<Job>()
        jobs += scope.launch {
            client.latestFrame.collect { bitmap -> publishFrame(target.id, bitmap) }
        }
        jobs += scope.launch {
            client.streamStats.collect { stats -> publishStats(target.id, stats) }
        }

        clients[target.id] = Entry(client, jobs, null)
        client.connect(target.ip, target.port)
    }

    private fun publishFrame(id: String, bitmap: Bitmap?) {
        val entry = clients[id] ?: return
        val previous = entry.lastFrame
        entry.lastFrame = bitmap
        _frames.update { it + (id to bitmap) }

        if (previous != null && previous !== bitmap) recycleLater(id, previous)
    }

    private fun publishStats(id: String, stats: ClientStreamStats) {
        if (!clients.containsKey(id)) return
        _stats.update { it + (id to stats) }
    }

    private fun closeStream(id: String) {
        val entry = clients.remove(id) ?: return
        entry.jobs.forEach { it.cancel() }
        entry.client.disconnect()
        _frames.update { it - id }
        _stats.update { it - id }
        entry.lastFrame?.let { recycleLater(id, it) }
    }

    private fun recycleLater(id: String, bitmap: Bitmap) {
        scope.launch {
            // Compose può ancora disegnare il frame precedente: il riciclo avviene dopo.
            delay(800)
            val stillUsed = clients[id]?.lastFrame === bitmap || _frames.value[id] === bitmap
            if (!stillUsed && !bitmap.isRecycled) bitmap.recycle()
        }
    }
}
