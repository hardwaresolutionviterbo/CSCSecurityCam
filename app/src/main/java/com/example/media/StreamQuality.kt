package com.example.media

/**
 * Qualità del flusso MJPEG richiesta dal centro di controllo alla telecamera.
 *
 * AUTO resta sul compromesso 720p e scende a 480p quando il collegamento non regge il passo,
 * così il monitor non prova a trasportare un full HD che la rete non sostiene.
 */
enum class StreamQuality(
    val label: String,
    val shortLabel: String,
    val targetWidth: Int,
    val targetHeight: Int
) {
    AUTO("Auto", "AUTO", 1280, 720),
    FULL_HD("1080p (Full HD)", "FULL HD", 1920, 1080),
    HD("720p (HD)", "HD", 1280, 720),
    SD("480p (SD)", "SD", 640, 480);

    /** Riquadro effettivo del flusso: in AUTO può scendere a 480p. */
    fun dimensions(autoFallback: Boolean): Pair<Int, Int> =
        if (this == AUTO && autoFallback) {
            SD.targetWidth to SD.targetHeight
        } else {
            targetWidth to targetHeight
        }

    companion object {
        fun options(): List<StreamQuality> = values().toList()

        /** Etichette sconosciute (valori vecchi salvati) ricadono su AUTO. */
        fun fromLabel(label: String?): StreamQuality =
            values().firstOrNull { it.label.equals(label?.trim(), ignoreCase = true) } ?: AUTO
    }
}
