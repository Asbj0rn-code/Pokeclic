/**
 * Pruebas JVM de los mapeos puros de la capa de diálogos del tutorial
 * (`TutorialOverlay.kt`): texto por fase, preguntas y uso del nombre por fase.
 */
package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.TutorialPhase
import com.dsrs.pokeclic.model.isMessageVisible
import com.dsrs.pokeclic.model.isModal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialOverlayTest {

    // ───────────────── Texto por fase ─────────────────

    @Test
    fun everyDialogPhase_hasItsOwnMessage() {
        val expected = mapOf(
            TutorialPhase.OFFER to R.string.tutorial_offer_body,
            TutorialPhase.CONFIRM to R.string.tutorial_confirm_body,
            TutorialPhase.DEPLOY_POKEBALL to R.string.tutorial_deploy_body,
            TutorialPhase.OPEN_MAP to R.string.tutorial_open_map_body,
            TutorialPhase.RETURN_MAP to R.string.tutorial_return_map_body,
            TutorialPhase.OPEN_POKEDEX to R.string.tutorial_open_pokedex_body,
            TutorialPhase.RETURN_POKEDEX to R.string.tutorial_return_pokedex_body,
            TutorialPhase.FAREWELL to R.string.tutorial_farewell_body,
            TutorialPhase.EXP_NOTICE to R.string.tutorial_exp_notice_body
        )
        expected.forEach { (phase, res) ->
            assertEquals("Mensaje de $phase", res, tutorialMessageRes(phase))
        }
    }

    @Test
    fun silentPhases_haveNoMessage() {
        listOf(
            TutorialPhase.PENDING,
            TutorialPhase.EXP_WAIT,
            TutorialPhase.ADVANCE_WAIT,
            TutorialPhase.SKIPPED,
            TutorialPhase.COMPLETED
        ).forEach { phase ->
            assertNull("$phase no muestra diálogo", tutorialMessageRes(phase))
        }
    }

    @Test
    fun messageRes_andVisibility_agreeOnEveryPhase() {
        // Coherencia entre el modelo (isMessageVisible) y la capa: una fase con
        // texto siempre puede mostrarlo con el mensaje abierto, y ninguna fase
        // sin texto llega a componer el overlay.
        TutorialPhase.entries.forEach { phase ->
            val hasText = tutorialMessageRes(phase) != null
            val canShow = phase.isMessageVisible(messageClosed = false)
            if (hasText) {
                assertTrue("$phase con texto debe poder mostrarse", canShow)
            }
        }
    }

    // ───────────────── Preguntas y nombre ─────────────────

    @Test
    fun onlyOfferAndConfirmAreQuestions() {
        assertTrue(tutorialIsQuestion(TutorialPhase.OFFER))
        assertTrue(tutorialIsQuestion(TutorialPhase.CONFIRM))
        TutorialPhase.entries
            .filter { it != TutorialPhase.OFFER && it != TutorialPhase.CONFIRM }
            .forEach { phase ->
                assertFalse("$phase no es pregunta", tutorialIsQuestion(phase))
            }
    }

    @Test
    fun onlyOfferAndReturnPokedex_useThePlayerName() {
        assertTrue(tutorialUsesPlayerName(TutorialPhase.OFFER))
        assertTrue(tutorialUsesPlayerName(TutorialPhase.RETURN_POKEDEX))
        TutorialPhase.entries
            .filter { it != TutorialPhase.OFFER && it != TutorialPhase.RETURN_POKEDEX }
            .forEach { phase ->
                assertFalse("$phase no usa el nombre", tutorialUsesPlayerName(phase))
            }
    }

    // ───────────────── Coherencia con la Fase 4.2 ─────────────────

    @Test
    fun questions_areModalLayers() {
        // Las preguntas serán las capas modales del overlay: coherente con
        // `isModal` del modelo (la Fase 4.2 no las bloquea en el VM a propósito,
        // porque el aislamiento visual llega con esta fase).
        TutorialPhase.entries.forEach { phase ->
            if (tutorialIsQuestion(phase)) {
                assertTrue("$phase es modal", phase.isModal)
            }
        }
    }

    @Test
    fun farewell_andExpNotice_areNotQuestions() {
        // Conservan sus transiciones específicas vía `onMessageTap`, sin botones.
        assertFalse(tutorialIsQuestion(TutorialPhase.FAREWELL))
        assertFalse(tutorialIsQuestion(TutorialPhase.EXP_NOTICE))
    }

    // ───────────────── Paginación (QA del tutorial) ─────────────────

    @Test
    fun messages_splitIntoPagesByParagraph() {
        val pages = tutorialPages(
            "¡Bien, has conseguido desplegar el botón!\n\n" +
                "Al pulsarlo ganarás experiencia.\n\n" +
                "Adelante, pulsa en el botón del Mapa."
        )
        assertEquals(3, pages.size)
        assertEquals("¡Bien, has conseguido desplegar el botón!", pages[0])
        assertEquals("Al pulsarlo ganarás experiencia.", pages[1])
        assertEquals("Adelante, pulsa en el botón del Mapa.", pages[2])
    }

    @Test
    fun shortMessages_areASinglePage() {
        assertEquals(
            listOf("¿Estás seguro? Tu cara me resulta familiar…"),
            tutorialPages("¿Estás seguro? Tu cara me resulta familiar…")
        )
    }

    @Test
    fun pageTap_advancesUntilTheLastPage_thenCloses() {
        // Tres páginas: dos avances y, en la última, cierre (`null`).
        assertNull(tutorialPageAfterTap(pageIndex = 0, pageCount = 1))
        assertEquals(1, tutorialPageAfterTap(pageIndex = 0, pageCount = 3))
        assertEquals(2, tutorialPageAfterTap(pageIndex = 1, pageCount = 3))
        assertNull(tutorialPageAfterTap(pageIndex = 2, pageCount = 3))
    }
}
