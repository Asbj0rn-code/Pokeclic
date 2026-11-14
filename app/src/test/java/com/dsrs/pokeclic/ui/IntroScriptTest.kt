package com.dsrs.pokeclic.ui

import com.dsrs.pokeclic.R
import com.dsrs.pokeclic.model.PokemonType
import com.dsrs.pokeclic.model.StarterOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El guion de la intro es la única parte comprobable sin emulador: qué línea se
 * dice, con qué emoción y cuál espera a un widget. El retrato, el avance por
 * toques y los pasos a los que vuelve un "No" se apoyan en esas mismas funciones
 * puras, así que se prueban aquí.
 */
class IntroScriptTest {

    private val charmander = StarterOption("charmander", "Charmander", PokemonType.FIRE)
    private val script = introScript(name = "Rojo", starter = charmander)

    @Test
    fun script_keepsEachEmotionOnTheLinesTheStoryNeeds() {
        assertEquals(
            listOf(
                // Presentación y nombre: los frames de hablar.
                R.string.intro_line_welcome to OakMood.TALKING,
                R.string.intro_ask_name to OakMood.TALKING,
                R.string.intro_confirm_name to OakMood.TALKING,
                R.string.intro_greet to OakMood.TALKING,
                // El Team Rocket: los frames de enfado (la primera frase aún es de hablar).
                R.string.intro_line_danger to OakMood.TALKING,
                R.string.intro_line_rocket_power to OakMood.ANGRY,
                R.string.intro_line_loop to OakMood.ANGRY,
                R.string.intro_line_trapped to OakMood.ANGRY,
                // La Pokédex: vuelve a hablar.
                R.string.intro_line_pokedex to OakMood.TALKING,
                R.string.intro_line_charge to OakMood.TALKING,
                R.string.intro_line_needs to OakMood.TALKING,
                // Green: la caída en la advertencia sobre su carácter.
                R.string.intro_line_green to OakMood.TALKING,
                R.string.intro_line_green_impulsive to OakMood.DROP,
                R.string.intro_line_green_risk to OakMood.TALKING,
                // La oferta de la Pokédex, el starter y la descripción del elegido.
                R.string.intro_line_ask_help to OakMood.OFFERING,
                R.string.intro_ask_starter to OakMood.OFFERING,
                R.string.intro_starter_brief_charmander to OakMood.OFFERING,
                R.string.intro_goodbye to OakMood.OFFERING
            ),
            script.map { it.textRes to it.mood }
        )
    }

    @Test
    fun script_asksTheNameAndTheStarterEachOnItsOwnLine() {
        val nameLine = script.single { it.widget == IntroWidget.NAME }
        val nameConfirm = script.single { it.widget == IntroWidget.CONFIRM_NAME }
        val starterLine = script.single { it.widget == IntroWidget.STARTER }
        val starterConfirm = script.single { it.widget == IntroWidget.CONFIRM_STARTER }

        assertEquals(R.string.intro_ask_name, nameLine.textRes)
        assertEquals(R.string.intro_confirm_name, nameConfirm.textRes)
        assertEquals(R.string.intro_ask_starter, starterLine.textRes)
        assertEquals(R.string.intro_starter_brief_charmander, starterConfirm.textRes)
        // Cada confirmación va justo después del paso que la abre.
        assertEquals(script.indexOf(nameLine) + 1, script.indexOf(nameConfirm))
        assertEquals(script.indexOf(starterLine) + 1, script.indexOf(starterConfirm))
        // El selector y su confirmación van antes de la despedida.
        assertEquals(script.indexOf(starterConfirm), script.lastIndex - 1)
    }

    @Test
    fun script_confirmLineRepeatsTheTypedName() {
        val confirm = script.single { it.widget == IntroWidget.CONFIRM_NAME }
        assertEquals(listOf("Rojo"), confirm.formatArgs)
    }

    @Test
    fun nameStep_isTheLineThatOpensTheNameField() {
        // "No" en la confirmación vuelve a este paso con el nombre ya escrito.
        assertEquals(R.string.intro_ask_name, script[introNameStep(script)].textRes)
    }

    @Test
    fun starterStep_isTheLineThatReopensTheStarterPicker() {
        // "No" en la descripción devuelve el starter a su sitio y permite elegir otro.
        assertEquals(R.string.intro_ask_starter, script[introStarterStep(script)].textRes)
    }

    @Test
    fun starterBrief_hasItsOwnTextForEachStarterAndAFallback() {
        assertEquals(
            R.string.intro_starter_brief_bulbasaur,
            starterBriefRes("bulbasaur")
        )
        assertEquals(
            R.string.intro_starter_brief_squirtle,
            starterBriefRes("squirtle")
        )
        // Sin starter elegido (o con una especie sin ficha) se pregunta igual.
        assertEquals(R.string.intro_starter_brief_default, starterBriefRes(null))
        assertEquals(R.string.intro_starter_brief_default, starterBriefRes("pikachu"))
    }

    @Test
    fun script_greetsAndSaysGoodbyeWithTheGivenNames() {
        assertEquals(emptyList<String>(), script.first().formatArgs)
        assertEquals(listOf("Rojo"), script.single { it.textRes == R.string.intro_greet }.formatArgs)
        assertEquals(listOf("Charmander"), script.last().formatArgs)
    }

    @Test
    fun tap_revealsTheCurrentLineFirstAndAdvancesOnlyWhenItIsComplete() {
        assertEquals(IntroTap.REVEAL_LINE, introTap(lineComplete = false))
        assertEquals(IntroTap.ADVANCE, introTap(lineComplete = true))
    }

    @Test
    fun advance_onlyLinesWithoutAWidgetAdvanceOnATap() {
        assertTrue(introCanAdvance(IntroWidget.NONE))
        // El nombre, sus confirmaciones y el starter los confirma el widget.
        assertFalse(introCanAdvance(IntroWidget.NAME))
        assertFalse(introCanAdvance(IntroWidget.CONFIRM_NAME))
        assertFalse(introCanAdvance(IntroWidget.STARTER))
        assertFalse(introCanAdvance(IntroWidget.CONFIRM_STARTER))
    }

    @Test
    fun portrait_pinsTheFramePairOfEachMood() {
        assertEquals(
            OakPortrait(R.drawable.profesor_oak, R.drawable.profesor_oak_talk),
            OakMood.TALKING.portrait
        )
        assertEquals(
            OakPortrait(R.drawable.profesor_oak_angry_closed, R.drawable.profesor_oak_angry),
            OakMood.ANGRY.portrait
        )
        assertEquals(OakPortrait(R.drawable.profesor_oak_drop), OakMood.DROP.portrait)
        assertEquals(OakPortrait(R.drawable.profesor_oak_offering), OakMood.OFFERING.portrait)
    }

    @Test
    fun portrait_animatedMoodsAlternateTwoDifferentFrames() {
        listOf(OakMood.TALKING, OakMood.ANGRY).forEach { mood ->
            val portrait = mood.portrait
            val open = portrait.open
            assertNotNull("$mood no tiene frame de boca abierta", open)
            assertTrue("$mood repite el mismo PNG", open != portrait.closed)
        }
        // Las emociones sin pareja dibujada son retratos fijos.
        assertNull(OakMood.DROP.portrait.open)
        assertNull(OakMood.OFFERING.portrait.open)
    }

    @Test
    fun script_staysTheSameShapeWithOrWithoutAChosenStarter() {
        val empty = introScript(name = "", starter = null)
        // La ficha del starter cambia, pero ni el número de pasos ni los widgets.
        assertEquals(script.size, empty.size)
        assertEquals(
            script.map { it.mood to it.widget },
            empty.map { it.mood to it.widget }
        )
    }
}
