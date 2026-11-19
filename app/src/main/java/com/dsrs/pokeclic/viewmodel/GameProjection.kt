package com.dsrs.pokeclic.viewmodel

import com.dsrs.pokeclic.model.GymCatalog
import com.dsrs.pokeclic.model.GymMechanic
import com.dsrs.pokeclic.model.LeadOption
import com.dsrs.pokeclic.model.PokedexProjector
import com.dsrs.pokeclic.model.SpeciesCatalog
import com.dsrs.pokeclic.model.StarterOption
import com.dsrs.pokeclic.model.TutorialPhase
import com.dsrs.pokeclic.model.Zone
import com.dsrs.pokeclic.model.isGymPending
import kotlin.math.roundToLong

/** Cada especie en posesión suma este % a la potencia de clic. */
private const val PER_OWNED_SPECIES_BONUS = 0.05

/** Extra de % de clic por nivel de vínculo del starter, cuando va equipado. */
private const val STARTER_BOND_CLICK_BONUS = 0.02

/** HP base del entrenador antes de la mejora de vida. */
private const val BASE_MAX_HP = 5

/**
 * Proyección del estado: los campos que se **derivan** de catálogos y de la run.
 *
 * Extraída de [MainViewModel] porque es el último bloque con sustancia que no es
 * superficie de eventos ni persistencia. Es pura salvo por sus dos cachés
 * internas (opciones del selector y filas de la Pokédex), que existen porque la
 * proyección corre en cada tick.
 *
 * @param dexLoadFailed se arrastra al estado para que la UI avise del fallo.
 */
internal class GameProjection(
    private val pokedex: PokedexProjector,
    private val dexLoadFailed: Boolean
) {

    private var leadOptionsKey: LeadOptionsKey? = null
    private var leadOptionsCache: List<LeadOption> = emptyList()
    private val starterOptionsCache: List<StarterOption> by lazy {
        SpeciesCatalog.starters.map { StarterOption(id = it.id, name = it.name, type = it.type) }
    }

    private data class LeadOptionsKey(val owned: Set<String>, val starter: String?)

    /**
     * Recalcula los campos que dependen de catálogos (zona, costes, principal…).
     *
     * @param zone zona actual, ya resuelta por el llamador.
     * @param lastZoneIndex índice de la última zona: en ella no hay «Avanzar».
     */
    fun project(state: GameUiState, zone: Zone, lastZoneIndex: Int): GameUiState {
        val starterId = state.starterSpeciesId
        val ownedCount = state.ownedSpecies.size + if (starterId != null) 1 else 0
        val bondBonus = if (starterId != null && state.leadSpeciesId == starterId) {
            state.starterBond * STARTER_BOND_CLICK_BONUS
        } else {
            0.0
        }
        val bonus = ownedCount * PER_OWNED_SPECIES_BONUS + bondBonus
        val clickPower = (1.0 + bonus).roundToLong().coerceAtLeast(1L)

        val lead = (state.leadSpeciesId ?: starterId)?.let { SpeciesCatalog.byId(it) }

        // Opciones ya resueltas: la UI no conoce el catálogo ni ordena especies.
        // Memoizadas: la proyección corre en cada tick y solo cambian al capturar.
        val leadOptions = leadOptionsFor(state.ownedSpecies, starterId)
        val starterOptions = starterOptionsCache

        val gymPending = isGymPending(zone, state.gymClearedRun)
        val isGymReady = gymPending && state.zoneClicks >= zone.clickRequirement
        // La mecánica también se resuelve antes de crear `GymState`: la capa de
        // entrada (bienvenida/tutorial) se pinta con `gym == null` y necesita
        // saber qué minijuego explicar.
        val gymMechanic = zone.gymId?.let { GymCatalog.byId(it)?.mechanic }
            ?: GymMechanic.PROJECTILES

        // Botón Avanzar: barra llena y, en zonas con gimnasio, ganado en la run
        // (lo excluye `gymPending`). En la última zona no hay botón.
        val canAdvanceZone = state.zoneIndex < lastZoneIndex &&
            state.zoneClicks >= zone.clickRequirement &&
            !gymPending

        // Normalización del tutorial: dos transiciones que dependen del estado del
        // juego y no de un control concreto, así que viven aquí y no en un evento
        // de UI:
        // - `PENDING` + starter: Oak ya terminó (partida recién cerrada tras la
        //   intro o reset que conservó el starter) → se ofrece el tutorial sin
        //   repetir la introducción.
        // - `EXP_WAIT` + barra llena: el aviso de avance sustituye a la espera.
        // El resto de fases se respeta tal cual: la reapcripción conserva
        // exactamente lo guardado.
        val tutorialPhase = when {
            state.tutorialPhase == TutorialPhase.PENDING && starterId != null ->
                TutorialPhase.OFFER

            state.tutorialPhase == TutorialPhase.EXP_WAIT && canAdvanceZone ->
                TutorialPhase.EXP_NOTICE

            else -> state.tutorialPhase
        }

        // Pokédex: el proyector memoiza por firma (posesión de la run + historia
        // permanente), así que el resto de ticks reutiliza las 151 filas.
        val dex = pokedex.project(
            ownedSpecies = state.ownedSpecies,
            starterSpeciesId = state.starterSpeciesId,
            seenSpecies = state.seenSpecies,
            caughtSpecies = state.caughtSpecies
        )

        return state.copy(
            zoneName = zone.name,
            zoneId = zone.id,
            zoneRequirement = zone.clickRequirement,
            ownedCount = ownedCount,
            clickPower = clickPower,
            maxHp = BASE_MAX_HP,
            leadName = lead?.name,
            leadType = lead?.type,
            isGymReady = isGymReady,
            hasWilds = zone.wildSpeciesIds.isNotEmpty(),
            gymLeaderName = if (isGymReady) {
                zone.gymId?.let { GymCatalog.byId(it)?.leaderName }
            } else {
                null
            },
            gymId = if (isGymReady) zone.gymId else null,
            gymMechanic = gymMechanic,
            canAdvanceZone = canAdvanceZone,
            needsStarter = starterId == null,
            leadOptions = leadOptions,
            starterOptions = starterOptions,
            tutorialPhase = tutorialPhase,
            dexEntries = dex.entries,
            dexSeenCount = dex.seenCount,
            dexCaughtCount = dex.caughtCount,
            dexLoadFailed = dexLoadFailed
        )
    }

    /**
     * Opciones del selector de principal, recalculadas solo cuando cambia la
     * posesión o el inicial. La clave se compara por contenido, así que un `copy`
     * con el mismo contenido reutiliza la lista.
     */
    private fun leadOptionsFor(owned: Set<String>, starterId: String?): List<LeadOption> {
        val key = LeadOptionsKey(owned, starterId)
        if (key != leadOptionsKey) {
            leadOptionsKey = key
            leadOptionsCache = (owned + setOfNotNull(starterId))
                .mapNotNull { SpeciesCatalog.byId(it) }
                .sortedBy { it.name }
                .map { LeadOption(id = it.id, name = it.name) }
        }
        return leadOptionsCache
    }
}
