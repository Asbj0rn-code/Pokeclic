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
        // Misty y Lt. Surge aún no tienen retratos ni despedida: su combate
        // termina en el acto, como siempre.
        listOf("misty", "surge").forEach { id ->
            assertNull("$id no debería tener despedida todavía", victoryScriptFor(id))
        }
        assertNull(victoryScriptFor(null))
        assertNull(victoryScriptFor("gimnasio_inexistente"))
    }

    @Test
    fun configuredVictories_pointToRealGyms() {
        val gymIds = GymCatalog.all.map { it.id }.toSet()
        victorySpeechGymIds.forEach { id ->
            assertTrue("'$id' no es un gimnasio del catálogo", id in gymIds)
        }
        assertEquals(setOf("brock"), victorySpeechGymIds)
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
