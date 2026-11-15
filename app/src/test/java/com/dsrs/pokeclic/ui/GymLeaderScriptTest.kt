package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.GymCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El discurso del líder es la parte comprobable sin emulador: qué líneas dice,
 * con qué retrato y qué gimnasios lo tienen. El retrato en pantalla, la
 * cortinilla y el toque se apoyan en esas mismas funciones puras.
 */
class GymLeaderScriptTest {

    private val brock = leaderScriptFor("brock")!!

    @Test
    fun brock_changesHisFaceOnlyWhileHearingAboutTeamRocket() {
        assertEquals(
            listOf(
                // Presentación y la sequía de retadores: cara de siempre.
                R.string.gym_leader_line_welcome to R.drawable.brock,
                R.string.gym_leader_line_quiet to R.drawable.brock,
                // Escucha lo que le cuenta el jugador sin inmutarse todavía.
                R.string.gym_leader_line_listen to R.drawable.brock,
                R.string.gym_leader_line_listen to R.drawable.brock,
                // La sorpresa entra con la mención al Team Rocket.
                R.string.gym_leader_line_rocket_hint to R.drawable.brock_surprised,
                // Otro silencio antes del estallido.
                R.string.gym_leader_line_listen to R.drawable.brock_surprised,
                R.string.gym_leader_line_listen to R.drawable.brock_surprised,
                R.string.gym_leader_line_rocket to R.drawable.brock_surprised,
                // Recuperada la calma hasta el final del discurso.
                R.string.gym_leader_line_badge to R.drawable.brock,
                R.string.gym_leader_line_duel to R.drawable.brock,
                R.string.gym_leader_line_tutorial to R.drawable.brock
            ),
            brock.lines.map { it.textRes to it.portraitRes }
        )
    }

    @Test
    fun brock_usesTwoPortraitsAndComesBackToTheCalmOne() {
        val portraits = brock.lines.map { it.portraitRes }
        assertEquals(
            "deberían ser exactamente sus dos caras",
            listOf(R.drawable.brock, R.drawable.brock_surprised),
            portraits.distinct()
        )
        // La sorpresa empieza pasado el arranque y termina antes del cierre: el
        // discurso abre y cierra con su cara de siempre.
        assertEquals(R.drawable.brock, portraits.first())
        assertEquals(R.drawable.brock, portraits.last())
        assertTrue(
            "la sorpresa debería durar más de una línea",
            portraits.count { it == R.drawable.brock_surprised } > 1
        )
    }

    @Test
    fun everyLine_hasATextAndAPortrait() {
        brock.lines.forEach { line ->
            assertNotEquals("línea sin texto", 0, line.textRes)
            assertNotEquals("línea sin retrato", 0, line.portraitRes)
        }
    }

    @Test
    fun gymsWithoutAScript_fallBackToTheGenericWelcome() {
        // Misty y Lt. Surge todavía no tienen retrato ni discurso: no se inventa
        // contenido, se usa la bienvenida de siempre.
        listOf("misty", "surge").forEach { id ->
            assertNull("$id no debería tener guion todavía", leaderScriptFor(id))
        }
        assertNull(leaderScriptFor(null))
        assertNull(leaderScriptFor("gimnasio_inexistente"))
    }

    @Test
    fun configuredScripts_pointToRealGyms() {
        // Un id mal escrito caería siempre en la bienvenida genérica sin avisar.
        val gymIds = GymCatalog.all.map { it.id }.toSet()
        scriptedLeaderGymIds.forEach { id ->
            assertTrue("'$id' no es un gimnasio del catálogo", id in gymIds)
        }
        assertEquals(setOf("brock"), scriptedLeaderGymIds)
    }

    @Test
    fun everyScriptedLeader_startsWithAWelcomeAndEndsOfferingTheTutorial() {
        scriptedLeaderGymIds.forEach { id ->
            val lines = leaderScriptFor(id)!!.lines
            assertNotNull(lines)
            assertTrue("$id no tiene líneas", lines.isNotEmpty())
            assertEquals(
                "$id no empieza con la bienvenida",
                R.string.gym_leader_line_welcome,
                lines.first().textRes
            )
            assertEquals(
                "$id no termina ofreciendo el tutorial",
                R.string.gym_leader_line_tutorial,
                lines.last().textRes
            )
        }
    }
}
