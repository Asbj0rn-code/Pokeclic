package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.model.TutorialPhase
import com.dsrs.pokeclic.model.isFreePlay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El mundo de ruta se congela mientras hay un encuentro en curso: el clic de la
 * Pokébola no avanza el fondo.
 */
class AppRootTest {

    // ───────────────── Tutorial: menú y capas (Fase 5.2) ─────────────────

    @Test
    fun tutorialQuestions_andFarewell_hideTheMenu() {
        // Capas modales del tutorial: el menú no se compone (la Pokéball ignora
        // el consumo de toques; issue #21).
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.OFFER,
            com.dsrs.pokeclic.model.TutorialPhase.CONFIRM,
            com.dsrs.pokeclic.model.TutorialPhase.FAREWELL
        ).forEach { phase ->
            assertTrue("$phase oculta el menú", tutorialHidesMenu(phase))
        }
    }

    @Test
    fun guidedSteps_andSilentPhases_keepTheMenuComposed() {
        // En los pasos guiados la Pokéball y los botones del menú son la
        // interacción del propio paso: el menú sigue compuesto.
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.DEPLOY_POKEBALL,
            com.dsrs.pokeclic.model.TutorialPhase.OPEN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.OPEN_POKEDEX,
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_POKEDEX,
            com.dsrs.pokeclic.model.TutorialPhase.EXP_NOTICE
        ).forEach { phase ->
            assertFalse("$phase deja el menú compuesto", tutorialHidesMenu(phase))
        }
        // Fuera del tutorial: sin cambios.
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.PENDING,
            com.dsrs.pokeclic.model.TutorialPhase.EXP_WAIT,
            com.dsrs.pokeclic.model.TutorialPhase.ADVANCE_WAIT,
            com.dsrs.pokeclic.model.TutorialPhase.SKIPPED,
            com.dsrs.pokeclic.model.TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertFalse("$phase no toca el menú", tutorialHidesMenu(phase))
        }
    }

    // ───────────────── Tutorial: gesto de despliegue (Fase 6.1) ─────────────────

    @Test
    fun deployStep_withVisibleMessage_disablesThePokeBall() {
        // Mensaje visible: la bola queda completamente inerte.
        assertTrue(
            tutorialPokeBallEnabled(
                phase = com.dsrs.pokeclic.model.TutorialPhase.DEPLOY_POKEBALL,
                messageClosed = false
            ).not()
        )
    }

    @Test
    fun deployStep_withClosedMessage_enablesTheDrag() {
        // Cerrado el mensaje, el deslizamiento real queda habilitado y es lo
        // que completa el paso.
        assertTrue(
            tutorialPokeBallEnabled(
                phase = com.dsrs.pokeclic.model.TutorialPhase.DEPLOY_POKEBALL,
                messageClosed = true
            )
        )
    }

    @Test
    fun openMap_andOpenPokedex_disableThePokeBall_evenWithTheMessageClosed() {
        // En la apertura de Mapa y Pokédex la bola no es la interacción del
        // paso: inactiva aunque el mensaje ya se haya cerrado.
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.OPEN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.OPEN_POKEDEX
        ).forEach { phase ->
            assertFalse(
                "$phase deja la bola inerte",
                tutorialPokeBallEnabled(phase = phase, messageClosed = true)
            )
        }
    }

    @Test
    fun returnSteps_enableThePokeBall_onlyAfterClosingTheMessage() {
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_POKEDEX
        ).forEach { phase ->
            assertFalse(
                "$phase con mensaje visible: bola inerte",
                tutorialPokeBallEnabled(phase = phase, messageClosed = false)
            )
            assertTrue(
                "$phase con mensaje cerrado: bola para volver",
                tutorialPokeBallEnabled(phase = phase, messageClosed = true)
            )
        }
    }

    @Test
    fun outsideTheTutorial_thePokeBallWorksAsAlways() {
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.PENDING,
            com.dsrs.pokeclic.model.TutorialPhase.EXP_WAIT,
            com.dsrs.pokeclic.model.TutorialPhase.ADVANCE_WAIT,
            com.dsrs.pokeclic.model.TutorialPhase.SKIPPED,
            com.dsrs.pokeclic.model.TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertTrue(
                "$phase: gesto normal",
                tutorialPokeBallEnabled(phase = phase, messageClosed = false)
            )
        }
    }

    @Test
    fun pokeBallGameClick_onlyGeneratesProgressInFreePlay() {
        // Regresos y pasos: la bola vuelve, nunca suma experiencia.
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_POKEDEX,
            com.dsrs.pokeclic.model.TutorialPhase.OPEN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.OFFER,
            com.dsrs.pokeclic.model.TutorialPhase.EXP_NOTICE,
            com.dsrs.pokeclic.model.TutorialPhase.DEPLOY_POKEBALL
        ).forEach { phase ->
            assertFalse(
                "$phase no genera experiencia",
                tutorialPokeBallClickEnabled(phase)
            )
        }
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.PENDING,
            com.dsrs.pokeclic.model.TutorialPhase.EXP_WAIT,
            com.dsrs.pokeclic.model.TutorialPhase.ADVANCE_WAIT,
            com.dsrs.pokeclic.model.TutorialPhase.SKIPPED,
            com.dsrs.pokeclic.model.TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertTrue(
                "$phase es juego libre",
                tutorialPokeBallClickEnabled(phase)
            )
        }
    }

    // ───────────────── Tutorial: destinos de la Pokédex (regresión) ─────────────────

    @Test
    fun dexDestinations_inertDuringTutorialSteps_freeInGamePlay() {
        // En el regreso de la Pokédex (y en cualquier fase del tutorial) los
        // destinos del menú quedan inertes: el regreso es exclusivo de la
        // Pokéball, que es a su vez el botón Home.
        TutorialPhase.entries
            .filter { !it.isFreePlay }
            .forEach { phase ->
                assertFalse(
                    "$phase deja el botón Mapa inerte en la Pokédex",
                    tutorialDexDestinationsEnabled(phase)
                )
            }
        listOf(
            TutorialPhase.PENDING,
            TutorialPhase.EXP_WAIT,
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.SKIPPED,
            TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertTrue(
                "$phase navega con normalidad en la Pokédex",
                tutorialDexDestinationsEnabled(phase)
            )
        }
    }

    // ───────────────── Tutorial: capa modal unificada (QA) ─────────────────

    @Test
    fun dialogVisible_whileAnyMessageIsOnScreen() {
        // Preguntas, despedida y aviso: visibles siempre (no se descartan por
        // toque ni la flag las apaga).
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.OFFER,
            com.dsrs.pokeclic.model.TutorialPhase.CONFIRM,
            com.dsrs.pokeclic.model.TutorialPhase.FAREWELL,
            com.dsrs.pokeclic.model.TutorialPhase.EXP_NOTICE
        ).forEach { phase ->
            assertTrue(
                "$phase muestra capa modal",
                tutorialDialogVisible(phase = phase, messageClosed = true)
            )
            assertTrue(
                "$phase muestra capa modal (sin cerrar)",
                tutorialDialogVisible(phase = phase, messageClosed = false)
            )
        }
        // Pasos guiados: la capa dura hasta que se cierra el mensaje.
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.DEPLOY_POKEBALL,
            com.dsrs.pokeclic.model.TutorialPhase.OPEN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.OPEN_POKEDEX,
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_POKEDEX
        ).forEach { phase ->
            assertTrue(
                "$phase con mensaje abierto muestra la capa",
                tutorialDialogVisible(phase = phase, messageClosed = false)
            )
        }
    }

    @Test
    fun dialogLayer_retractsWhenTheMessageCloses() {
        // Cerrado el mensaje del paso, la capa se retira inmediatamente y las
        // interacciones autorizadas vuelven (la puerta del modelo es la misma
        // que consulta el ViewModel).
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.DEPLOY_POKEBALL,
            com.dsrs.pokeclic.model.TutorialPhase.OPEN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_MAP,
            com.dsrs.pokeclic.model.TutorialPhase.OPEN_POKEDEX,
            com.dsrs.pokeclic.model.TutorialPhase.RETURN_POKEDEX
        ).forEach { phase ->
            assertFalse(
                "$phase con mensaje cerrado retira la capa",
                tutorialDialogVisible(phase = phase, messageClosed = true)
            )
        }
        // Cerrada la despedida y el aviso, juego libre sin capa.
        listOf(
            com.dsrs.pokeclic.model.TutorialPhase.EXP_WAIT,
            com.dsrs.pokeclic.model.TutorialPhase.ADVANCE_WAIT,
            com.dsrs.pokeclic.model.TutorialPhase.SKIPPED,
            com.dsrs.pokeclic.model.TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertFalse(
                "$phase sin capa",
                tutorialDialogVisible(phase = phase, messageClosed = false)
            )
        }
    }
}
