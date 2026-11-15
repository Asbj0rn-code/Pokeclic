/**
 * Modelo de estados del tutorial explicativo del juego: máquina
 * **pura e independiente** (sin Android ni ViewModel, testeable en JVM).
 *
 * Reglas del diseño:
 * - Las transiciones **solo** ocurren por acciones reales del jugador
 *     ([TutorialAction]); cualquier acción inesperada es un **no-op** (nunca
 *     lanza), mismo patrón que los guards del ViewModel.
 * - Cada estado declara las interacciones de juego permitidas
 *     ([TutorialInteraction]); desde «espera de experiencia» todo queda
 *     disponible ([TutorialInteraction.ALL]) y el tutorial solo observa.
 * - El progreso es persistible por diseño: el nombre del enum es su clave
 *     estable (ver [tutorialPhaseFromName] para el respaldo de guardados
 *     antiguos).
 * - Los tutoriales de gimnasio (`GymIntroPhase`) son un sistema aparte y no
 *     participan de este modelo.
 */
package com.dsrs.pokeclic.model

/**
 * Estados del tutorial, en orden de recorrido. Convención: cada paso
 * **espera** la acción que lo cierra (`OPEN_MAP` espera `MAP_OPENED`).
 */
enum class TutorialPhase {
    /** Pendiente de ofrecerse: Oak aún no terminó (o partida heredada). */
    PENDING,

    /** Pregunta inicial «¿Quieres un tutorial?» (Sí/No). Capa modal. */
    OFFER,

    /**
     * Segunda confirmación «Empezar tutorial»/«Omitir»: evita que un «Sí»
     * accidental fuerce el recorrido obligatorio.
     */
    CONFIRM,

    /** Paso 1: desplegar la Pokéball (arrastre hacia arriba). */
    DEPLOY_POKEBALL,

    /** Paso 2: se espera que el jugador abra el mapa. */
    OPEN_MAP,

    /** Paso 2: se espera el regreso del mapa (toque en la Pokéball). */
    RETURN_MAP,

    /** Paso 3: se espera que el jugador abra la Pokédex. */
    OPEN_POKEDEX,

    /** Paso 3: se espera el regreso de la Pokédex. */
    RETURN_POKEDEX,

    /** Despedida de Oak antes de soltar al jugador. Capa modal. */
    FAREWELL,

    /**
     * Tutorial básico terminado: todas las funcionalidades disponibles; solo
     * se observa la barra de la zona.
     */
    EXP_WAIT,

    /** Aviso de avance: la barra se llenó y se muestra «pulsa Avanzar». */
    EXP_NOTICE,

    /** Aviso ya visto: se espera el primer avance real de zona. */
    ADVANCE_WAIT,

    /** Final alternativo: el jugador omitió el tutorial. */
    SKIPPED,

    /** Final normal: el tutorial se completó. */
    COMPLETED
}

/** Acciones reales del jugador (o del flujo de la app) que mueven la máquina. */
enum class TutorialAction {
    /** Oak terminó la introducción (nombre y starter fijados). */
    OAK_FINISHED,

    /** «Sí» en la pregunta inicial. */
    ACCEPT_OFFER,

    /** «No» en la pregunta inicial: omite el tutorial. */
    DECLINE_OFFER,

    /** «Empezar tutorial» en la segunda confirmación. */
    BEGIN_TUTORIAL,

    /** «Omitir» en la segunda confirmación. */
    SKIP_TUTORIAL,

    /** Arrastre de la Pokéball hacia arriba completado (bola expandida). */
    POKEBALL_EXPANDED,

    /** El jugador abrió el mapa. */
    MAP_OPENED,

    /** El jugador regresó del mapa (toque en la Pokéball → Aventura). */
    MAP_CLOSED,

    /** El jugador abrió la Pokédex. */
    POKEDEX_OPENED,

    /** El jugador regresó de la Pokédex. */
    POKEDEX_CLOSED,

    /** El jugador cerró el diálogo de despedida. */
    FAREWELL_DISMISSED,

    /** La barra de la zona se llenó (`canAdvanceZone` pasó a `true`). */
    EXP_BAR_FILLED,

    /** El jugador cerró el aviso de avance. */
    EXP_NOTICE_DISMISSED,

    /** Primer avance de zona ejecutado (`onAdvanceZone` válido). */
    ADVANCE_ZONE_EXECUTED
}

/**
 * Interacciones de juego que un estado permite. Todo lo que no esté en el
 * conjunto del estado actual debe ser ignorado por el ViewModel.
 */
enum class TutorialInteraction {
    /** Arrastre de la Pokéball hacia arriba (despliegue). */
    POKEBALL_DRAG,

    /** Toque simple en la Pokéball (vuelta a la aventura desde el mapa). */
    POKEBALL_TAP,

    /** Botón Mapa del menú inferior. */
    MENU_MAP,

    /** Botón Pokédex del menú inferior (abre la hoja de opciones). */
    MENU_POKEDEX,

    /** Fila Pokédex de la hoja de opciones (abre la pantalla de la Dex). */
    POKEDEX_OPEN,

    /** Cierre de la pantalla de Pokédex (botón volver o menú de la Dex). */
    POKEDEX_CLOSE,

    /** Botón «Avanzar» de zona. */
    ADVANCE_ZONE,

    /** Todo disponible: tutorial terminado u omitido; el tutorial solo observa. */
    ALL
}

/**
 * Transición del tutorial ante una acción del jugador.
 *
 * Función pura: devuelve el estado siguiente, o el mismo estado si la acción
 * no aplica (no-op, nunca lanza). Los finales ([TutorialPhase.SKIPPED] y
 * [TutorialPhase.COMPLETED]) absorben cualquier acción: su destino ya está
 * sellado y lo desbloquea el guardado, no una acción tardía.
 */
fun TutorialPhase.advance(action: TutorialAction): TutorialPhase {
    if (isFinal) return this
    return when (this) {
        TutorialPhase.PENDING ->
            if (action == TutorialAction.OAK_FINISHED) TutorialPhase.OFFER else this
        TutorialPhase.OFFER -> when (action) {
            TutorialAction.ACCEPT_OFFER -> TutorialPhase.CONFIRM
            TutorialAction.DECLINE_OFFER -> TutorialPhase.SKIPPED
            else -> this
        }
        TutorialPhase.CONFIRM -> when (action) {
            TutorialAction.BEGIN_TUTORIAL -> TutorialPhase.DEPLOY_POKEBALL
            TutorialAction.SKIP_TUTORIAL -> TutorialPhase.SKIPPED
            else -> this
        }
        TutorialPhase.DEPLOY_POKEBALL ->
            if (action == TutorialAction.POKEBALL_EXPANDED) TutorialPhase.OPEN_MAP else this
        TutorialPhase.OPEN_MAP ->
            if (action == TutorialAction.MAP_OPENED) TutorialPhase.RETURN_MAP else this
        TutorialPhase.RETURN_MAP ->
            if (action == TutorialAction.MAP_CLOSED) TutorialPhase.OPEN_POKEDEX else this
        TutorialPhase.OPEN_POKEDEX ->
            if (action == TutorialAction.POKEDEX_OPENED) TutorialPhase.RETURN_POKEDEX else this
        TutorialPhase.RETURN_POKEDEX ->
            if (action == TutorialAction.POKEDEX_CLOSED) TutorialPhase.FAREWELL else this
        TutorialPhase.FAREWELL ->
            if (action == TutorialAction.FAREWELL_DISMISSED) TutorialPhase.EXP_WAIT else this
        TutorialPhase.EXP_WAIT ->
            if (action == TutorialAction.EXP_BAR_FILLED) TutorialPhase.EXP_NOTICE else this
        TutorialPhase.EXP_NOTICE ->
            if (action == TutorialAction.EXP_NOTICE_DISMISSED) TutorialPhase.ADVANCE_WAIT else this
        TutorialPhase.ADVANCE_WAIT ->
            if (action == TutorialAction.ADVANCE_ZONE_EXECUTED) TutorialPhase.COMPLETED else this
        TutorialPhase.SKIPPED, TutorialPhase.COMPLETED -> this
    }
}

/**
 * Interacciones de juego permitidas en este estado; el ViewModel ignora
 * (no-op) todo control fuera de él. Desde [TutorialPhase.EXP_WAIT] —y en los
 * finales— el conjunto es [TutorialInteraction.ALL].
 *
 * Nota: la entrada de `EXP_NOTICE` es defensiva y no es alcanzable vía
 * [tutorialInteractionAllowed], porque esa función corta antes con
 * `isMessageVisible` (el aviso de avance siempre tiene el mensaje visible).
 * Se mantiene en la tabla para que cualquier consumidor futuro que la
 * consulte directamente reciba un conjunto coherente con la fase.
 */
val TutorialPhase.allowedInteractions: Set<TutorialInteraction>
    get() = when (this) {
        TutorialPhase.PENDING, TutorialPhase.OFFER, TutorialPhase.CONFIRM,
        TutorialPhase.FAREWELL -> emptySet()
        TutorialPhase.DEPLOY_POKEBALL -> setOf(TutorialInteraction.POKEBALL_DRAG)
        TutorialPhase.OPEN_MAP -> setOf(TutorialInteraction.MENU_MAP)
        TutorialPhase.RETURN_MAP -> setOf(TutorialInteraction.POKEBALL_TAP)
        TutorialPhase.OPEN_POKEDEX ->
            setOf(TutorialInteraction.MENU_POKEDEX, TutorialInteraction.POKEDEX_OPEN)
        TutorialPhase.RETURN_POKEDEX -> setOf(TutorialInteraction.POKEDEX_CLOSE)
        TutorialPhase.EXP_WAIT, TutorialPhase.EXP_NOTICE, TutorialPhase.ADVANCE_WAIT,
        TutorialPhase.SKIPPED, TutorialPhase.COMPLETED -> setOf(TutorialInteraction.ALL)
    }

/**
 * ¿Este estado es una capa **modal** (scrim + `modalBlocking` y menú oculto)?
 * Solo las preguntas y la despedida: los pasos interactivos dejan el juego
 * visible dentro de [TutorialPhase.allowedInteractions].
 */
val TutorialPhase.isModal: Boolean
    get() = this == TutorialPhase.OFFER ||
        this == TutorialPhase.CONFIRM ||
        this == TutorialPhase.FAREWELL

/** ¿Estado final? Los finales absorben cualquier acción posterior. */
val TutorialPhase.isFinal: Boolean
    get() = this == TutorialPhase.SKIPPED || this == TutorialPhase.COMPLETED

/**
 * ¿Es uno de los **pasos guiados** del recorrido? En ellos el ViewModel
 * bloquea toda interacción fuera de [TutorialPhase.allowedInteractions].
 * Las preguntas, la despedida y `PENDING` no lo son: su aislamiento lo hacen
 * las capas visuales (la intro opaca y el overlay del tutorial).
 */
val TutorialPhase.isGuidedStep: Boolean
    get() = this == TutorialPhase.DEPLOY_POKEBALL ||
        this == TutorialPhase.OPEN_MAP ||
        this == TutorialPhase.RETURN_MAP ||
        this == TutorialPhase.OPEN_POKEDEX ||
        this == TutorialPhase.RETURN_POKEDEX

/**
 * ¿Está el mensaje de esta fase **visible en pantalla**?
 *
 * Separa la visibilidad del diálogo de la fase en sí: cada paso guiado tiene
 * dos momentos — mensaje explicativo visible y, tras cerrarlo con una
 * pulsación (como en la introducción de Oak), acción habilitada. Cerrar el
 * mensaje **no** completa el paso: la transición sigue exigiendo la acción
 * real de juego.
 *
 * - `OFFER` y `CONFIRM`: siempre visibles y **no** descartables con un toque;
 *   exigen una respuesta explícita (sus botones).
 * - `FAREWELL` y `EXP_NOTICE`: siempre visibles hasta su transición específica
 *   (`FAREWELL_DISMISSED` / `EXP_NOTICE_DISMISSED`), que el toque dispara.
 * - Pasos guiados: visibles hasta que [messageClosed] valga `true`.
 * - Resto de fases: sin mensaje.
 *
 * @param messageClosed flag persistido junto a la fase; cada transición de
 *   fase lo vuelve a `false` para reabrir el mensaje del paso siguiente.
 */
fun TutorialPhase.isMessageVisible(messageClosed: Boolean): Boolean = when (this) {
    TutorialPhase.OFFER,
    TutorialPhase.CONFIRM,
    TutorialPhase.FAREWELL,
    TutorialPhase.EXP_NOTICE -> true
    TutorialPhase.DEPLOY_POKEBALL,
    TutorialPhase.OPEN_MAP,
    TutorialPhase.RETURN_MAP,
    TutorialPhase.OPEN_POKEDEX,
    TutorialPhase.RETURN_POKEDEX -> !messageClosed
    TutorialPhase.PENDING,
    TutorialPhase.EXP_WAIT,
    TutorialPhase.ADVANCE_WAIT,
    TutorialPhase.SKIPPED,
    TutorialPhase.COMPLETED -> false
}

/**
 * ¿Es **juego libre total**? Sin mensaje visible y sin paso guiado en curso:
 * Pokéball, hierba, gimnasio, opciones y avance funcionan con su comportamiento
 * normal. Válido para `PENDING` (la intro opaca ya aísla), esperas y finales.
 */
val TutorialPhase.isFreePlay: Boolean
    get() = this == TutorialPhase.PENDING ||
        this == TutorialPhase.EXP_WAIT ||
        this == TutorialPhase.ADVANCE_WAIT ||
        this == TutorialPhase.SKIPPED ||
        this == TutorialPhase.COMPLETED

/**
 * ¿Está habilitada esta interacción de juego **en el momento actual**?
 *
 * Regla principal (QA): mientras **cualquier** mensaje esté visible, nada del
 * juego responde — la única interacción es el propio diálogo. Con el mensaje
 * cerrado: paso guiado → solo su interacción; resto → juego libre.
 *
 * Función pura para que el ViewModel y las pruebas la compartan.
 */
fun tutorialInteractionAllowed(
    phase: TutorialPhase,
    messageClosed: Boolean,
    interaction: TutorialInteraction
): Boolean {
    if (phase.isMessageVisible(messageClosed)) return false
    return if (phase.isGuidedStep) {
        interaction in phase.allowedInteractions
    } else {
        phase.isFreePlay
    }
}

/**
 * Recupera el estado guardado a partir de su nombre persistido, con
 * [fallback] ante valores desconocidos o `null` (guardados anteriores). El
 * fallback lo decide el llamante: sin starter suele ser `PENDING`; con starter
 * heredado, `COMPLETED` para no ofrecer el tutorial a jugadores veteranos.
 */
fun tutorialPhaseFromName(raw: String?, fallback: TutorialPhase): TutorialPhase =
    TutorialPhase.entries.firstOrNull { it.name == raw } ?: fallback
