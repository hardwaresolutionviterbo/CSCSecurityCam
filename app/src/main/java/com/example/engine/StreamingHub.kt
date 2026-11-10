package com.example.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Server LAN condiviso a livello di processo.
 *
 * Prima viveva nel `viewModelScope`: distruggendo l'Activity (uscita dall'app) il ViewModel
 * veniva liberato, `onCleared()` chiamava `stop()` e il server HTTP moriva mentre il servizio in
 * primo piano teneva ancora la fotocamera. Riaprendo l'app il server veniva ricreato, per questo
 * lo streaming funzionava solo con l'app aperta. Ora il ciclo di vita del server è quello del
 * servizio di streaming, che sopravvive a schermo spento e app in background.
 */
object StreamingHub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val server: LanStreamingServer = LanStreamingServer(port = 8765, scope = scope)
}
