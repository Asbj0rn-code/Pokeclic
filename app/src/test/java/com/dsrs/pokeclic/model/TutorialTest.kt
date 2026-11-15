package com.dsrs.pokeclic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas JVM de la máquina de estados del tutorial general ([TutorialPhase],
 * [TutorialAction], [TutorialInteraction]): transiciones válidas, acciones
 * fuera de sitio (no-op), estados finales, puerta de interacciones y
 * utilidades de presentación/persistencia.
 */
class TutorialTest {

    // ───────────────── Transiciones válidas (camino completo) ─────────────────

    @Test
    fun oakFinished_movesPendingToOffer() {
        assertEquals(TutorialPhase.OFFER, TutorialPhase.PENDING.advance(TutorialAction.OAK_FINISHED))
    }

    @Test
    fun acceptOffer_movesOfferToConfirm() {
        assertEquals(TutorialPhase.CONFIRM, TutorialPhase.OFFER.advance(TutorialAction.ACCEPT_OFFER))
    }

    @Test
    fun declineOffer_skipsTheTutorial() {
        assertEquals(TutorialPhase.SKIPPED, TutorialPhase.OFFER.advance(TutorialAction.DECLINE_OFFER))
    }

    @Test
    fun beginTutorial_movesConfirmToDeployPokeball() {
        assertEquals(
            TutorialPhase.DEPLOY_POKEBALL,
            TutorialPhase.CONFIRM.advance(TutorialAction.BEGIN_TUTORIAL)
        )
    }

    @Test
    fun skipTutorial_movesConfirmToSkipped() {
        assertEquals(TutorialPhase.SKIPPED, TutorialPhase.CONFIRM.advance(TutorialAction.SKIP_TUTORIAL))
    }

    @Test
    fun pokeballExpanded_movesDeployPokeballToOpenMap() {
        assertEquals(
            TutorialPhase.OPEN_MAP,
            TutorialPhase.DEPLOY_POKEBALL.advance(TutorialAction.POKEBALL_EXPANDED)
        )
    }

    @Test
    fun mapOpened_movesOpenMapToReturnMap() {
        assertEquals(TutorialPhase.RETURN_MAP, TutorialPhase.OPEN_MAP.advance(TutorialAction.MAP_OPENED))
    }

    @Test
    fun mapClosed_movesReturnMapToOpenPokedex() {
        assertEquals(
            TutorialPhase.OPEN_POKEDEX,
            TutorialPhase.RETURN_MAP.advance(TutorialAction.MAP_CLOSED)
        )
    }

    @Test
    fun pokedexOpened_movesOpenPokedexToReturnPokedex() {
        assertEquals(
            TutorialPhase.RETURN_POKEDEX,
            TutorialPhase.OPEN_POKEDEX.advance(TutorialAction.POKEDEX_OPENED)
        )
    }

    @Test
    fun pokedexClosed_movesReturnPokedexToFarewell() {
        assertEquals(
            TutorialPhase.FAREWELL,
            TutorialPhase.RETURN_POKEDEX.advance(TutorialAction.POKEDEX_CLOSED)
        )
    }

    @Test
    fun farewellDismissed_movesFarewellToExpWait() {
        assertEquals(
            TutorialPhase.EXP_WAIT,
            TutorialPhase.FAREWELL.advance(TutorialAction.FAREWELL_DISMISSED)
        )
    }

    @Test
    fun expBarFilled_movesExpWaitToExpNotice() {
        assertEquals(
            TutorialPhase.EXP_NOTICE,
            TutorialPhase.EXP_WAIT.advance(TutorialAction.EXP_BAR_FILLED)
        )
    }

    @Test
    fun expNoticeDismissed_movesExpNoticeToAdvanceWait() {
        assertEquals(
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.EXP_NOTICE.advance(TutorialAction.EXP_NOTICE_DISMISSED)
        )
    }

    @Test
    fun advanceZoneExecuted_movesAdvanceWaitToCompleted() {
        assertEquals(
            TutorialPhase.COMPLETED,
            TutorialPhase.ADVANCE_WAIT.advance(TutorialAction.ADVANCE_ZONE_EXECUTED)
        )
    }

    @Test
    fun fullHappyPath_isOfferToCompletedInThirteenSteps() {
        var phase = TutorialPhase.PENDING
        val actions = listOf(
            TutorialAction.OAK_FINISHED,
            TutorialAction.ACCEPT_OFFER,
            TutorialAction.BEGIN_TUTORIAL,
            TutorialAction.POKEBALL_EXPANDED,
            TutorialAction.MAP_OPENED,
            TutorialAction.MAP_CLOSED,
            TutorialAction.POKEDEX_OPENED,
            TutorialAction.POKEDEX_CLOSED,
            TutorialAction.FAREWELL_DISMISSED,
            TutorialAction.EXP_BAR_FILLED,
            TutorialAction.EXP_NOTICE_DISMISSED,
            TutorialAction.ADVANCE_ZONE_EXECUTED
        )
        actions.forEach { phase = phase.advance(it) }
        assertEquals(TutorialPhase.COMPLETED, phase)
    }

    // ───────────────── Acciones incorrectas: no-op ─────────────────

    @Test
    fun pending_ignoresEveryActionExceptOakFinished() {
        TutorialAction.entries
            .filter { it != TutorialAction.OAK_FINISHED }
            .forEach { action ->
                assertEquals("PENDING debe ignorar $action", TutorialPhase.PENDING, TutorialPhase.PENDING.advance(action))
            }
    }

    @Test
    fun offer_ignoresActionItDoesNotExpect() {
        assertEquals(TutorialPhase.OFFER, TutorialPhase.OFFER.advance(TutorialAction.MAP_OPENED))
        assertEquals(TutorialPhase.OFFER, TutorialPhase.OFFER.advance(TutorialAction.OAK_FINISHED))
    }

    @Test
    fun confirm_ignoresPokeballAction() {
        assertEquals(TutorialPhase.CONFIRM, TutorialPhase.CONFIRM.advance(TutorialAction.POKEBALL_EXPANDED))
    }

    @Test
    fun steps_ignoreActionsOfOtherSteps() {
        // Cada paso ignora la acción que cerraría a otro paso.
        assertEquals(
            TutorialPhase.DEPLOY_POKEBALL,
            TutorialPhase.DEPLOY_POKEBALL.advance(TutorialAction.MAP_OPENED)
        )
        assertEquals(TutorialPhase.OPEN_MAP, TutorialPhase.OPEN_MAP.advance(TutorialAction.POKEBALL_EXPANDED))
        assertEquals(TutorialPhase.RETURN_MAP, TutorialPhase.RETURN_MAP.advance(TutorialAction.POKEDEX_OPENED))
        assertEquals(
            TutorialPhase.OPEN_POKEDEX,
            TutorialPhase.OPEN_POKEDEX.advance(TutorialAction.POKEDEX_CLOSED)
        )
        assertEquals(
            TutorialPhase.RETURN_POKEDEX,
            TutorialPhase.RETURN_POKEDEX.advance(TutorialAction.FAREWELL_DISMISSED)
        )
        assertEquals(TutorialPhase.FAREWELL, TutorialPhase.FAREWELL.advance(TutorialAction.OAK_FINISHED))
    }

    @Test
    fun expPhases_ignoreActionsThatDoNotBelongToThem() {
        assertEquals(TutorialPhase.EXP_WAIT, TutorialPhase.EXP_WAIT.advance(TutorialAction.ADVANCE_ZONE_EXECUTED))
        assertEquals(TutorialPhase.EXP_NOTICE, TutorialPhase.EXP_NOTICE.advance(TutorialAction.EXP_BAR_FILLED))
        assertEquals(
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.ADVANCE_WAIT.advance(TutorialAction.EXP_NOTICE_DISMISSED)
        )
    }

    // ───────────────── Estados finales ─────────────────

    @Test
    fun finalPhases_absorbEveryAction() {
        listOf(TutorialPhase.SKIPPED, TutorialPhase.COMPLETED).forEach { phase ->
            TutorialAction.entries.forEach { action ->
                assertEquals("$phase debe absorber $action", phase, phase.advance(action))
            }
        }
    }

    @Test
    fun finalPhases_areFlaggedAsFinal() {
        assertTrue(TutorialPhase.SKIPPED.isFinal)
        assertTrue(TutorialPhase.COMPLETED.isFinal)
    }

    @Test
    fun intermediatePhases_areNotFinal() {
        listOf(
            TutorialPhase.PENDING,
            TutorialPhase.OFFER,
            TutorialPhase.CONFIRM,
            TutorialPhase.DEPLOY_POKEBALL,
            TutorialPhase.OPEN_MAP,
            TutorialPhase.RETURN_MAP,
            TutorialPhase.OPEN_POKEDEX,
            TutorialPhase.RETURN_POKEDEX,
            TutorialPhase.FAREWELL,
            TutorialPhase.EXP_WAIT,
            TutorialPhase.EXP_NOTICE,
            TutorialPhase.ADVANCE_WAIT
        ).forEach { phase ->
            assertFalse("$phase no es final", phase.isFinal)
        }
    }

    // ───────────────── Interacciones permitidas por estado ─────────────────

    @Test
    fun dialogPhases_allowNoGameInteraction() {
        listOf(
            TutorialPhase.PENDING,
            TutorialPhase.OFFER,
            TutorialPhase.CONFIRM,
            TutorialPhase.FAREWELL
        ).forEach { phase ->
            assertTrue(
                "$phase no debe permitir interacciones de juego",
                phase.allowedInteractions.isEmpty()
            )
        }
    }

    @Test
    fun stepPhases_allowOnlyTheirOwnInteraction() {
        assertEquals(
            setOf(TutorialInteraction.POKEBALL_DRAG),
            TutorialPhase.DEPLOY_POKEBALL.allowedInteractions
        )
        assertEquals(setOf(TutorialInteraction.MENU_MAP), TutorialPhase.OPEN_MAP.allowedInteractions)
        assertEquals(setOf(TutorialInteraction.POKEBALL_TAP), TutorialPhase.RETURN_MAP.allowedInteractions)
        assertEquals(
            setOf(TutorialInteraction.MENU_POKEDEX, TutorialInteraction.POKEDEX_OPEN),
            TutorialPhase.OPEN_POKEDEX.allowedInteractions
        )
        assertEquals(setOf(TutorialInteraction.POKEDEX_CLOSE), TutorialPhase.RETURN_POKEDEX.allowedInteractions)
    }

    @Test
    fun fromExpWaitOnwards_everythingIsAvailable() {
        // Requisito: tras el tutorial básico no se bloquea ninguna funcionalidad
        // mientras se espera la barra y el primer avance.
        listOf(
            TutorialPhase.EXP_WAIT,
            TutorialPhase.EXP_NOTICE,
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.SKIPPED,
            TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertEquals(
                "$phase debe permitir todo",
                setOf(TutorialInteraction.ALL),
                phase.allowedInteractions
            )
        }
    }

    // ───────────────── Presentación (capa de diálogo) ─────────────────

    @Test
    fun onlyQuestionsAndFarewellAreModal() {
        listOf(TutorialPhase.OFFER, TutorialPhase.CONFIRM, TutorialPhase.FAREWELL).forEach { phase ->
            assertTrue("$phase es modal", phase.isModal)
        }
        listOf(
            TutorialPhase.PENDING,
            TutorialPhase.DEPLOY_POKEBALL,
            TutorialPhase.OPEN_MAP,
            TutorialPhase.RETURN_MAP,
            TutorialPhase.OPEN_POKEDEX,
            TutorialPhase.RETURN_POKEDEX,
            TutorialPhase.EXP_WAIT,
            TutorialPhase.EXP_NOTICE,
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.SKIPPED,
            TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertFalse("$phase no es modal", phase.isModal)
        }
    }

    // ───────────────── Persistencia ─────────────────

    @Test
    fun everyPhaseRoundTripsThroughItsName() {
        TutorialPhase.entries.forEach { phase ->
            assertEquals(phase, tutorialPhaseFromName(phase.name, TutorialPhase.PENDING))
        }
    }

    @Test
    fun unknownName_fallsBackToProvidedPhase() {
        assertEquals(TutorialPhase.PENDING, tutorialPhaseFromName("no_existe", TutorialPhase.PENDING))
        assertEquals(TutorialPhase.COMPLETED, tutorialPhaseFromName("no_existe", TutorialPhase.COMPLETED))
    }

    @Test
    fun legacySaveWithoutKey_fallsBackToProvidedPhase() {
        assertNull(null) // el `null` de la clave ausente se resuelve en el fallback del llamante.
        assertEquals(TutorialPhase.COMPLETED, tutorialPhaseFromName(null, TutorialPhase.COMPLETED))
        assertEquals(TutorialPhase.PENDING, tutorialPhaseFromName(null, TutorialPhase.PENDING))
    }

    // ───────────────── Juego libre y puerta de interacciones (QA) ─────────────────

    @Test
    fun freePlay_isPendingWaitStatesAndFinals() {
        listOf(
            TutorialPhase.PENDING,
            TutorialPhase.EXP_WAIT,
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.SKIPPED,
            TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertTrue("$phase es juego libre", phase.isFreePlay)
        }
        listOf(
            TutorialPhase.OFFER,
            TutorialPhase.CONFIRM,
            TutorialPhase.DEPLOY_POKEBALL,
            TutorialPhase.OPEN_MAP,
            TutorialPhase.RETURN_MAP,
            TutorialPhase.OPEN_POKEDEX,
            TutorialPhase.RETURN_POKEDEX,
            TutorialPhase.FAREWELL,
            TutorialPhase.EXP_NOTICE
        ).forEach { phase ->
            assertFalse("$phase no es juego libre", phase.isFreePlay)
        }
    }

    @Test
    fun interactionGate_blocksEverythingWhileAnyMessageIsVisible() {
        // Regla principal de QA: con un mensaje en pantalla no responde nada.
        val cases = listOf(
            Triple(TutorialPhase.OFFER, false, TutorialInteraction.POKEBALL_TAP),
            Triple(TutorialPhase.CONFIRM, false, TutorialInteraction.MENU_MAP),
            Triple(TutorialPhase.DEPLOY_POKEBALL, false, TutorialInteraction.POKEBALL_DRAG),
            Triple(TutorialPhase.OPEN_MAP, false, TutorialInteraction.MENU_MAP),
            Triple(TutorialPhase.RETURN_MAP, false, TutorialInteraction.POKEBALL_TAP),
            Triple(TutorialPhase.OPEN_POKEDEX, false, TutorialInteraction.MENU_POKEDEX),
            Triple(TutorialPhase.RETURN_POKEDEX, false, TutorialInteraction.POKEDEX_CLOSE),
            Triple(TutorialPhase.FAREWELL, false, TutorialInteraction.POKEBALL_TAP),
            Triple(TutorialPhase.EXP_NOTICE, false, TutorialInteraction.ADVANCE_ZONE),
            Triple(TutorialPhase.EXP_NOTICE, false, TutorialInteraction.POKEBALL_TAP)
        )
        cases.forEach { (phase, closed, interaction) ->
            assertFalse(
                "$phase con mensaje visible bloquea $interaction",
                tutorialInteractionAllowed(phase, closed, interaction)
            )
        }
    }

    @Test
    fun interactionGate_afterClosing_allowsOnlyTheStepInteraction() {
        // Con el mensaje cerrado, la puerta reproduce exactamente el conjunto
        // del paso: nada más responde.
        listOf(
            TutorialPhase.DEPLOY_POKEBALL,
            TutorialPhase.OPEN_MAP,
            TutorialPhase.RETURN_MAP,
            TutorialPhase.OPEN_POKEDEX,
            TutorialPhase.RETURN_POKEDEX
        ).forEach { phase ->
            TutorialInteraction.entries
                .filter { it != TutorialInteraction.ALL }
                .forEach { interaction ->
                    assertEquals(
                        "$phase cerrado: $interaction",
                        interaction in phase.allowedInteractions,
                        tutorialInteractionAllowed(phase, true, interaction)
                    )
                }
        }
    }

    @Test
    fun interactionGate_afterTheFarewell_everythingIsFree() {
        listOf(
            TutorialPhase.PENDING,
            TutorialPhase.EXP_WAIT,
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.SKIPPED,
            TutorialPhase.COMPLETED
        ).forEach { phase ->
            TutorialInteraction.entries.forEach { interaction ->
                assertTrue(
                    "$phase es juego libre para $interaction",
                    tutorialInteractionAllowed(phase, false, interaction)
                )
            }
        }
    }

    // ───────────────── Pasos guiados y diálogo pendiente ─────────────────

    @Test
    fun onlyTheFiveWalkthroughStepsAreGuidedSteps() {
        listOf(
            TutorialPhase.DEPLOY_POKEBALL,
            TutorialPhase.OPEN_MAP,
            TutorialPhase.RETURN_MAP,
            TutorialPhase.OPEN_POKEDEX,
            TutorialPhase.RETURN_POKEDEX
        ).forEach { phase ->
            assertTrue("$phase es paso guiado", phase.isGuidedStep)
        }
        listOf(
            TutorialPhase.PENDING,
            TutorialPhase.OFFER,
            TutorialPhase.CONFIRM,
            TutorialPhase.FAREWELL,
            TutorialPhase.EXP_WAIT,
            TutorialPhase.EXP_NOTICE,
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.SKIPPED,
            TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertFalse("$phase no es paso guiado", phase.isGuidedStep)
        }
    }

    // ───────────────── Independencia del gimnasio ─────────────────

    @Test
    fun tutorialModel_sharesNothingWithGymIntro() {
        // El modelo del tutorial no reutiliza el vocabulario de la capa de
        // entrada al gimnasio: ni un solo nombre de fase coincide, de modo que
        // persistir uno no puede confundirse con el otro.
        val gymNames = GymIntroPhase.entries.map { it.name }.toSet()
        TutorialPhase.entries.forEach { phase ->
            assertFalse(
                "El nombre ${phase.name} no debe coincidir con una fase de gimnasio",
                phase.name in gymNames
            )
        }
    }
}
