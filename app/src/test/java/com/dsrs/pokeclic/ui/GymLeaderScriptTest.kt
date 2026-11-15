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
        // Lt. Surge todavía no tiene retrato ni discurso: no se inventa contenido,
        // se usa la bienvenida de siempre.
        assertNull("surge no debería tener guion todavía", leaderScriptFor("surge"))
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
        assertEquals(setOf("brock", "misty"), scriptedLeaderGymIds)
    }

    @Test
    fun everyScriptedLeader_startsWithAWelcomeAndEndsOfferingTheTutorial() {
        // La última línea es la que la UI cierra con los botones, así que tiene que
        // ser la pregunta del tutorial; la primera, la presentación.
        val welcomes = setOf(
            R.string.gym_leader_line_welcome,
            R.string.gym_leader_line_misty_welcome
        )
        val tutorials = setOf(
            R.string.gym_leader_line_tutorial,
            R.string.gym_leader_line_misty_tutorial
        )
        scriptedLeaderGymIds.forEach { id ->
            val lines = leaderScriptFor(id)!!.lines
            assertTrue("$id no tiene líneas", lines.isNotEmpty())
            assertTrue(
                "$id no empieza presentándose",
                lines.first().textRes in welcomes
            )
            assertTrue(
                "$id no termina ofreciendo el tutorial",
                lines.last().textRes in tutorials
            )
        }
    }

    @Test
    fun misty_opensBoredAndFlapsHerMouthWhileSheTalks() {
        val misty = leaderScriptFor("misty")!!
        val first = misty.lines.first()
        assertEquals(R.string.gym_leader_line_misty_welcome, first.textRes)
        assertEquals(R.drawable.misty_happy, first.portraitRes)
        assertEquals(
            "la boca cerrada acompaña a la cara de siempre",
            R.drawable.misty_happy2,
            first.altPortraitRes
        )
        // Solo su cara de siempre lleva la segunda cara: la boca se mueve en esas
        // líneas, no en las de sorpresa ni en la media sonrisa.
        misty.lines.filter { it.portraitRes == R.drawable.misty_happy }.forEach { line ->
            assertEquals(
                "una línea de boca abierta sin su boca cerrada no se movería",
                R.drawable.misty_happy2,
                line.altPortraitRes
            )
        }
        misty.lines.filter { it.portraitRes != R.drawable.misty_happy }.forEach { line ->
            assertNull("solo la cara de siempre alterna boca", line.altPortraitRes)
        }
    }

    @Test
    fun misty_listensToTheRocketStoryWithTheSurprisedFace() {
        val misty = leaderScriptFor("misty")!!
        val listenLines = misty.lines.filter { it.textRes == R.string.gym_leader_line_listen }
        assertTrue("debería haber varios silencios", listenLines.size >= 2)
        listenLines.forEach { line ->
            assertEquals(
                "los «...» se escuchan con la cara de sorpresa",
                R.drawable.misty_surprised,
                line.portraitRes
            )
        }
        // La mención al Team Rocket también la pilla sorprendida.
        val rocket = misty.lines.first { it.textRes == R.string.gym_leader_line_misty_rocket }
        assertEquals(R.drawable.misty_surprised, rocket.portraitRes)
    }

    @Test
    fun misty_onlySmilesWhenSheRefusesToGiveTheBadgeAway() {
        val misty = leaderScriptFor("misty")!!
        val smiling = misty.lines.filter { it.portraitRes == R.drawable.misty_smiling }
        assertEquals(
            "la media sonrisa es para el reto, ni antes ni después",
            listOf(
                R.string.gym_leader_line_misty_badge,
                R.string.gym_leader_line_misty_duel
            ),
            smiling.map { it.textRes }
        )
        // Y vuelve a su cara de siempre para ofrecer el tutorial.
        assertEquals(
            R.drawable.misty_happy,
            misty.lines.last { it.textRes == R.string.gym_leader_line_misty_tutorial }.portraitRes
        )
    }
}
