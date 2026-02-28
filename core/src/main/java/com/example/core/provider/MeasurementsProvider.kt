package com.example.core.provider

/**
 * DÉSACTIVÉ.
 *
 * Le ContentProvider doit vivre dans l'app capteur (:app) afin d'exposer la DB de :app.
 * Si on le laisse dans :core, il peut être empaqueté aussi dans :appmap et pointer sur une DB vide.
 *
 * Provider actif: `app/src/main/java/com/example/myapplication/provider/MeasurementsProvider.kt`
 */
@Deprecated(
    message = "Ne pas utiliser. Utiliser le provider dans :app.",
    level = DeprecationLevel.ERROR
)
class MeasurementsProvider
