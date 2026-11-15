package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.GymCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La despedida del líder es la parte comprobable sin emulador: qué dice, con qué
 * cara y cuándo aparece la medalla.
 */
class GymVictoryScriptTest {

    private val brock = victoryScriptFor("brock")!!

    @Test
    fun brock_goesFromAngryToHappyAndHandsOverTheBadge() {
        assertEquals(
            listOf(
                // La rabia por el resultado, con la cara de enojo.
                Triple(R.string.gym_victory_line_almost, R.drawable.brock_angry, false),
                // El disfrute del combate y la entrega, ya sonriendo.
                Triple(R.string.gym_victory_line_enjoyed, R.drawable.brock_happy, false),
                Triple(R.string.gym_victory_line_badge, R.drawable.brock_happy, true),
                Triple(R.string.gym_victory_line_farewell, R.drawable.brock_happy, true)
            ),
            brock.lines.map { Triple(it.textRes, it.portraitRes, it.badge) }
        )
    }

    @Test
    fun theBadgeAppearsFromTheLineThatHandsItOver() {
        val badgeLines = brock.lines.mapIndexedNotNull { index, line -> index.takeIf { line.badge } }
        // Solo las dos últimas líneas muestran la medalla en el centro.
        assertEquals(listOf(2, 3), badgeLines)
        assertEquals(R.drawable.medalla_roca, brock.badgeRes)
    }

    @Test
    fun everyLine_hasATextAndAPortrait() {
        brock.lines.forEach { line ->
            assertTrue("línea sin texto", line.textRes != 0)
            assertTrue("línea sin retrato", line.portraitRes != 0)
        }
    }

    @Test
    fun gymsWithoutAVictoryScript_closeTheCombatAtOnce() {
        // Lt. Surge aún no tiene retratos ni despedida: su combate termina en el
        // acto, como siempre.
        assertNull("surge no debería tener despedida todavía", victoryScriptFor("surge"))
        assertNull(victoryScriptFor(null))
        assertNull(victoryScriptFor("gimnasio_inexistente"))
    }

    @Test
    fun configuredVictories_pointToRealGyms() {
        val gymIds = GymCatalog.all.map { it.id }.toSet()
        victorySpeechGymIds.forEach { id ->
            assertTrue("'$id' no es un gimnasio del catálogo", id in gymIds)
        }
        assertEquals(setOf("brock", "misty"), victorySpeechGymIds)
    }

    @Test
    fun misty_goesFromSadToHappyAndHandsOverTheCascadeBadge() {
        val misty = victoryScriptFor("misty")!!
        assertEquals(R.drawable.medalla_cascada, misty.badgeRes)
        assertEquals(R.string.gym_victory_badge_cascada_desc, misty.badgeDescRes)
        assertEquals(
            listOf(
                // La tristeza por haber perdido, con su cara de pena.
                Triple(R.string.gym_victory_line_misty_almost, R.drawable.misty_sad, false),
                Triple(R.string.gym_victory_line_misty_enjoyed, R.drawable.misty_sad, false),
                // Se rehace, da la enhorabuena y entrega la medalla.
                Triple(R.string.gym_victory_line_misty_badge, R.drawable.misty_happy, true),
                Triple(R.string.gym_victory_line_misty_farewell, R.drawable.misty_happy, true)
            ),
            misty.lines.map { Triple(it.textRes, it.portraitRes, it.badge) }
        )
    }

    @Test
    fun misty_flapsHerMouthOnlyOnTheHappyLines() {
        val misty = victoryScriptFor("misty")!!
        misty.lines.filter { it.portraitRes == R.drawable.misty_happy }.forEach { line ->
            assertEquals(
                "la cara alegre habla con la boca abierta y cerrada",
                R.drawable.misty_happy2,
                line.altPortraitRes
            )
        }
        misty.lines.filter { it.portraitRes != R.drawable.misty_happy }.forEach { line ->
            assertNull("la cara triste no alterna", line.altPortraitRes)
        }
    }

    @Test
    fun everyVictory_endsWithTheGymAlreadyHandedOver() {
        victorySpeechGymIds.forEach { id ->
            val script = victoryScriptFor(id)
            assertNotNull(script)
            assertTrue("$id no tiene líneas", script!!.lines.isNotEmpty())
            assertTrue(
                "$id debería terminar entregando la medalla",
                script.lines.last().badge
            )
            assertFalse(
                "$id no puede empezar mostrando la medalla",
                script.lines.first().badge
            )
        }
    }
}
