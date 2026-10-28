# Issues y riesgos — Pokeclic

Auditoría estática del proyecto Android `MyApplication2` (nombre visible **Pokeclic**), realizada el 20/10/2026 sobre el estado actual del código.

- **Alcance:** 15 archivos Kotlin de `app/src/main`, recursos, manifiesto, Gradle y documentación.
- **Estado de partida:** la última verificación conocida dejó `assembleDebug`, `testDebugUnitTest` y `lintDebug` en verde. Esta auditoría no modifica código.
- **Naturaleza:** los puntos marcados como **Confirmado** se deducen de la lectura del flujo; no se han ejecutado pruebas manuales específicas de reproducción. Los marcados como **Riesgo** dependen de concurrencia, configuración, tamaño de pantalla o datos corruptos.
- **Prioridades:** `P0` pérdida de datos o bloqueo grave; `P1` fallo funcional importante, inaccesibilidad o riesgo alto; `P2` UX, mantenibilidad o rendimiento relevante; `P3` pulido y deuda menor.

---

## Resumen ejecutivo

Los problemas más importantes no son de compilación, sino de **flujo de interacción y de ciclo de vida**:

1. Varias capas modales no consumen los toques fuera de su panel; esos toques llegan a la pantalla de debajo, donde están **Reset** y **Borrar partida**.
2. **Borrar partida** está siempre visible, no pide confirmación y es irreversible.
3. El **combate de gimnasio sigue corriendo en segundo plano** y mezcla un reloj de ticks con un reloj real, lo que puede acumular daño o ráfagas al volver a la app.
4. El **nombre del entrenador puede quedar vacío** usando el botón “Done” del teclado, aunque el botón visual esté deshabilitado.
5. El estado guardado **no se valida ni tiene versión de esquema**, así que un cambio de contenido o un guardado antiguo puede dejar la partida en un estado incoherente.
6. El gimnasio y la Pokébola del menú **no son operables con TalkBack**.
7. La app no está lista para publicarse: `applicationId` de ejemplo, release sin firma y recursos con propiedad intelectual de Pokémon.

---

## 1. Bugs y flujos funcionales

### FUN-01 · Los overlays no bloquean los toques fuera de su panel
- **Prioridad:** P0 · **Estado:** Resuelto (issue #21, rama `feature-7`)
- **Evidencia:** `ui/Overlays.kt:60-92` (captura), `ui/Overlays.kt:112-155` (principal), `ui/Intro.kt:130-179`, `ui/PokeBallSplash.kt:66-88`, `ui/AppRoot.kt:159-174`.
- **Qué pasa:** las capas de captura y selector de principal usan un `Box` con fondo semitransparente pero sin `clickable`/`pointerInput`. Solo el `Surface` central bloquea los toques dentro de su área. La intro y el splash no tienen ningún consumidor de toques. Los toques en el resto de la pantalla atraviesan la capa visual.
- **Impacto:** durante una captura o el selector de principal se puede pulsar sin verlo el HUD de debajo: **Reset**, **Cambiar principal**, la hierba alta o **Borrar partida**. Durante la intro y el splash ocurre lo mismo con la UI oculta. Es una vía directa a perder la partida sin querer.
- **Propuesta:** añadir un scrim de pantalla completa que consuma los toques (`pointerInput(Unit) { detectTapGestures { ... } }` o `clickable`), decidir si el toque fuera cierra o no, y cubrir con test de UI que ningún botón de debajo recibe eventos. En intro y splash, consumir siempre.
- **Solución aplicada (issue #21):** modificador reutilizable `Modifier.modalBlocking()` (`ui/ModalBlocking.kt`) que consume todos los eventos de puntero de la capa sin afectar a sus controles hijos; aplicado en la raíz de `IntroOverlay`, `LeadOverlay` y `PokeBallSplash`. Además, mientras haya capa modal (`needsStarter`, `isLeadOpen` o splash) `AppRoot` no compone el menú, porque la Pokéball usa un detector que ignora el consumo (`requireUnconsumed = false`); con la intro (opaca) tampoco se compone el contenido. Gimnasio y pesca ya aislaban por sí solos (escena a pantalla completa con `detectTapGestures` + menú oculto) y `OptionsSheet` es una `ModalBottomSheet` en ventana propia, impenetrable para el juego. Verificación manual documentada por capa en la issue #21; pendiente test de UI automatizado.

### FUN-02 · “Borrar partida (test)” está en producción, sin confirmación
- **Prioridad:** P0 · **Estado:** Confirmado
- **Evidencia:** `ui/MainScreen.kt:405-409`, `res/values/strings.xml:63-64`, `viewmodel/MainViewModel.kt:166-171`.
- **Qué pasa:** el botón de borrado total es visible siempre en el HUD y ejecuta `store.clear()` inmediatamente. No hay diálogo, deshacer ni copia de seguridad.
- **Impacto:** un toque accidental destruye toda la partida. Combinado con FUN-01, puede ocurrir mientras el jugador cree estar en otra capa.
- **Propuesta:** retirarlo de release o mostrarlo solo en builds de desarrollo; si se conserva, usar `AlertDialog` con confirmación explícita y texto de consecuencias.

### FUN-03 · El gimnasio continúa en segundo plano y puede causar daño acumulado
- **Prioridad:** P0 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:318-398` (bucle en `viewModelScope`), `viewmodel/MainViewModel.kt:336,344` (ticks de 50 ms), `viewmodel/MainViewModel.kt:347-349` (impactos con reloj real), `ui/GymOverlay.kt:133-139`.
- **Qué pasa:** el bucle del combate no se pausa con el ciclo de vida. Si la app pasa a segundo plano o el hilo principal se bloquea, el temporizador de oleada solo avanza 50 ms por tick, pero `Projectile.hasLanded(nowMs)` usa el reloj real. Al volver, todas las rocas pueden constar como impactadas, aunque la stamina no haya bajado lo equivalente.
- **Impacto:** el jugador puede perder el combate o HP sin haber visto la pantalla; también se desperdicia batería. No hay pausa ni indicación de que el combate siga activo.
- **Propuesta:** pausar el bucle cuando el ciclo de vida esté por debajo de `STARTED` (`repeatOnLifecycle`) o usar un reloj de combate basado en tiempo real con un límite de recuperación (“catch-up” máximo). Al reanudar, reiniciar `nextSpawnMs` y no aplicar impactos de tiempo ausente.

### FUN-04 · Ráfagas de recuperación al acumularse el temporizador de aparición
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:351-360`.
- **Qué pasa:** cuando toca generar, se hace `nextSpawnMs += interval`. Si el bucle se retrasa más de un intervalo, en los siguientes ticks `now >= nextSpawnMs` sigue siendo cierto y se lanza una salva nueva cada 50 ms hasta ponerse al día.
- **Impacto:** ráfagas muy densas e injustas después de un tirón, un cambio de pantalla o una vuelta de segundo plano; puede llenar la lista de proyectiles.
- **Propuesta:** fijar `nextSpawnMs = now + interval`, limitar el número de salvas recuperables y usar el tiempo real transcurrido de forma controlada.

### FUN-05 · El nombre del entrenador puede quedar vacío desde el teclado
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `ui/Intro.kt:223-241` (`KeyboardActions(onDone = { accept() })`), `ui/Intro.kt:240` (solo el botón está deshabilitado si está en blanco), `viewmodel/MainViewModel.kt:178-189`.
- **Qué pasa:** el botón “Aceptar” está deshabilitado con `name.isNotBlank()`, pero la acción `Done` del teclado llama a `accept()` sin comprobar nada. El ViewModel tampoco rechaza un nombre vacío: solo hace `trim()`.
- **Impacto:** se puede completar la intro con nombre vacío; la UI muestra “Entrenador: ” y el saludo “¡Encantado, !”. El guardado queda con un nombre vacío.
- **Propuesta:** validar `playerName.isNotBlank()` en `onFinishIntro` (fuente de verdad) y aplicar la misma condición en la acción del teclado; si no es válido, no avanzar de fase.

### FUN-06 · El botón “Pokédex” abre el menú de Opciones
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `ui/AppRoot.kt:109-114` (`isPokedexSelected = state.isOptionsOpen`, `onPokedexClick = onOpenOptions`), `ui/Overlays.kt:197-220`, `res/values/strings.xml:12`.
- **Qué pasa:** el destino derecho del menú está etiquetado como Pokédex, pero abre una hoja con “Configuración” y “Acerca de”. No existe pantalla de Pokédex.
- **Impacto:** expectativa incumplida y sensación de que el botón no funciona; el estado de selección también se reutiliza para Opciones.
- **Propuesta:** implementar la Pokédex o renombrar el botón a “Menú/Opciones” y separar `isPokedexSelected` de `isOptionsOpen`. Mientras tanto, ocultar el destino o marcarlo como no disponible.

### FUN-07 · No se puede huir del gimnasio hasta perder
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:133-139`, `viewmodel/MainViewModel.kt:237-240`.
- **Qué pasa:** `onLeaveGym()` solo se ofrece en el panel de derrota. Durante el combate no hay pausa, huida ni salida.
- **Impacto:** el jugador queda atrapado si empieza un combate sin querer o necesita salir; la única salida es perder, matar la app o el botón atrás (que además no está gestionado).
- **Propuesta:** añadir un botón de pausa/huir con confirmación, o permitir salir desde el menú de pausa sin recompensa. Documentar la penalización (o su ausencia).

### FUN-08 · Atacar al líder no tiene respuesta visual
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:345-369`.
- **Qué pasa:** el ataque a una roca incrementa `attackNonce` y provoca `lunge` y destello; la rama `onEnemy` solo llama a `onEnemyHit()`. No hay animación de ataque, destello ni cambio de estado visual al golpear al líder expuesto.
- **Impacto:** el jugador no sabe con certeza si el toque ha registrado daño; la respuesta queda limitada al pulso del enemigo.
- **Propuesta:** reutilizar `attackNonce`/`hitPoint` para el golpe al líder y añadir una variante de impacto sobre el sprite enemigo.

### FUN-09 · Las capas modales pueden quedar abiertas a la vez
- **Prioridad:** P2 · **Estado:** Confirmado por estado; alcanzable sobre todo por FUN-01
- **Evidencia:** `viewmodel/MainViewModel.kt:199-218`, `ui/AppRoot.kt:159-174`.
- **Qué pasa:** `isLeadOpen` e `isOptionsOpen` son independientes; abrir una no cierra la otra. La intro se compone por `needsStarter` sin mirar gimnasio u opciones.
- **Impacto:** estados de navegación incoherentes (captura + opciones, principal + opciones, intro + gimnasio) y toques que llegan a capas de debajo.
- **Propuesta:** modelar las capas como una pila o un `sealed` de pantalla modal, con una única capa activa; cerrar las demás al abrir una.

### FUN-10 · Falta `BackHandler` en intro, captura, principal y gimnasio
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** no hay ninguna referencia a `BackHandler` en el código; solo `ModalBottomSheet` gestiona el atrás de Opciones (`ui/Overlays.kt:185-190`).
- **Qué pasa:** el botón atrás del sistema cierra la Activity en lugar de cerrar la capa activa. En el gimnasio, además, se pierde el intento; en la intro, el progreso local.
- **Impacto:** salidas accidentales de la app y pérdida de progreso no guardado; comportamiento incoherente con la hoja de Opciones, que sí responde al atrás.
- **Propuesta:** un `BackHandler` por capa, con prioridad clara: opciones/principal/captura > gimnasio (pausa o confirmación) > intro (confirmación) > app.

### FUN-11 · La captura no tiene salida explícita y persiste al cambiar de pestaña
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/Overlays.kt:55-93`, `ui/AppRoot.kt:150-154`.
- **Qué pasa:** el overlay no ofrece “Huir” y se dibuja mientras `encounter != null`, incluso en la pestaña Mapa. La única forma de resolverlo es acertar, agotar los tres intentos o salir de la hierba (botón oculto detrás del overlay).
- **Impacto:** el jugador puede quedar bloqueado en una captura no deseada y el estado de encuentro contamina otras pantallas.
- **Propuesta:** añadir un botón “Huir” que limpie `encounter` y `walkProgress`, o permitir cerrar con atrás; decidir si el encuentro debe sobrevivir al cambio de pestaña.

### FUN-12 · El botón de derrota dice “Rendirse” después de perder
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:501-507`, `res/values/strings.xml:55`.
- **Qué pasa:** tras la derrota, el botón de salida usa `gym_leave`, cuyo texto es “Rendirse”.
- **Impacto:** mensaje semánticamente incorrecto: ya se ha perdido.
- **Propuesta:** usar “Salir” en el panel de derrota y reservar “Rendirse” para una huida durante el combate.

### FUN-13 · Los proyectiles que impactan justo al acabar la oleada no hacen daño
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `model/Combat.kt:306-320`, `viewmodel/MainViewModel.kt:344-349`.
- **Qué pasa:** `advanceWave` pasa a `EXPOSED` y vacía `projectiles` antes de que el bucle cuente los que ya habían llegado. Los que aterrizan en ese último tick se descartan.
- **Impacto:** inconsistencia menor de dificultad; el jugador recibe menos daño del que la escena sugiere.
- **Propuesta:** contabilizar los impactos pendientes antes de limpiar la lista o documentar explícitamente la indulgencia.

### FUN-14 · `onEnterGym` no comprueba que ya haya un gimnasio activo
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:223-228`, `278-309`.
- **Qué pasa:** si se invoca con un gimnasio ya activo, `startGymCombat` cancela el anterior y empieza de cero sin penalización.
- **Impacto:** hoy la UI oculta la entrada, pero cualquier cambio de UI o test puede reiniciar el combate a voluntad.
- **Propuesta:** `if (state.gym != null) return` o tratar el caso como reintento explícito.

### FUN-15 · La intro puede quedarse atascada si el starter final es inválido
- **Prioridad:** P3 · **Estado:** Riesgo
- **Evidencia:** `ui/Intro.kt:115-123`, `viewmodel/MainViewModel.kt:178-189`.
- **Qué pasa:** en fase 6 se llama `onFinish(name, starterId.orEmpty())`. Si `starterId` es nulo o desconocido, el ViewModel retorna sin cambiar el estado; la fase 6 no tiene otra transición.
- **Impacto:** la intro se queda en la despedida y no responde, especialmente tras una restauración parcial del estado de Compose.
- **Propuesta:** si el starter no es válido, volver a la fase de selección o mostrar un error; validar también en el ViewModel y no dejar una transición sin salida.

### FUN-16 · Las animaciones de asentamiento de la Pokébola pueden solaparse
- **Prioridad:** P3 · **Estado:** Riesgo
- **Evidencia:** `ui/PokemonBottomMenu.kt:330-348`.
- **Qué pasa:** cada `settleTo` lanza una corrutina nueva con su propio `Animatable` y no cancela la anterior. Varios toques rápidos o un arrastre durante la animación dejan varias animaciones escribiendo el mismo `progress`.
- **Impacto:** saltos o vibración visual; el estado final puede depender del orden de finalización.
- **Propuesta:** guardar el `Job` de animación y cancelarlo antes de lanzar otro, o usar un único `Animatable` reutilizable.

### FUN-17 · El HUD “En posesión” no cuenta al starter
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/MainScreen.kt:382-386`, `viewmodel/MainViewModel.kt:581-589`.
- **Qué pasa:** la potencia de clic y las opciones de principal incluyen al starter, pero el HUD muestra solo `ownedSpecies.size`. Tras la intro, el jugador tiene un Pokémon y ve “En posesión: 0”.
- **Impacto:** información contradictoria y confusa sobre el progreso.
- **Propuesta:** mostrar `ownedSpecies.size + (starter != null ? 1 : 0)` o cambiar la etiqueta a “Capturados”.

---

## 2. Persistencia y datos

### DAT-01 · El estado restaurado no se valida
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:543-561` (`restoreState`), `viewmodel/MainViewModel.kt:578-618` (`derive`), `data/GameStateStore.kt:37-52`.
- **Qué pasa:** se aceptan tal cual `starterSpeciesId`, `leadSpeciesId`, `ownedSpecies`, `medals`, `zoneIndex`, `zoneClicks`, `coins` y `starterBond`. Ejemplos concretos:
  - `starterSpeciesId` desconocido → `needsStarter = false`, pero `lead` es nulo y el jugador no puede volver a elegir starter.
  - `leadSpeciesId` desconocido → principal sin nombre ni tipo; daño neutro y HUD “Principal: —”.
  - `coins` o `zoneClicks` negativos → HUD y barras incoherentes.
  - `zoneIndex` fuera de rango → se muestra la última zona mediante `coerceIn`, pero el índice guardado sigue inválido.
  - `ownedSpecies` con IDs desconocidos → cuentan para `clickPower` aunque no existan.
- **Impacto:** partidas bloqueadas o explotables tras cambiar catálogos, restaurar un backup antiguo o manipular preferencias.
- **Propuesta:** una función `sanitize(GameSnapshot): GameSnapshot` en la capa de datos/ViewModel que filtre IDs conocidos, recorte rangos, repare el lead y, si el starter no es válido, fuerce `needsStarter = true`. Guardar el estado saneado.

### DAT-02 · No hay versión de esquema ni migraciones
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `data/GameStateStore.kt:37-52,87-101`, `model/GameData.kt:162-174`.
- **Qué pasa:** el guardado son claves sueltas sin `schema_version`. Renombrar una especie, un gimnasio o un campo deja datos antiguos sin tratamiento.
- **Impacto:** cada cambio de contenido puede romper partidas existentes sin aviso; no hay forma de migrar ni de descartar de forma controlada.
- **Propuesta:** añadir `KEY_SCHEMA_VERSION`, migraciones explícitas y un fallback seguro (nueva partida o saneado) con log.

### DAT-03 · La primera partida se detecta por la existencia de `coins`
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `data/GameStateStore.kt:38`.
- **Qué pasa:** `if (!prefs.contains(KEY_COINS)) return null`. Si el archivo existe pero falta esa clave (guardado parcial, migración o edición manual), se trata como primera partida.
- **Impacto:** puede mostrarse la intro y sobrescribirse un guardado que sí existía.
- **Propuesta:** usar una clave explícita de “partida iniciada” o la versión de esquema como marcador de existencia.

### DAT-04 · Conjuntos serializados con comas
- **Prioridad:** P2 · **Estado:** Riesgo
- **Evidencia:** `data/GameStateStore.kt:78-85,100`.
- **Qué pasa:** `seenSpecies`, `rewardedSpecies`, `medals` y `ownedSpecies` se unen con `,` y se separan con `split(",")`. Hoy los IDs no contienen comas.
- **Impacto:** cualquier ID futuro con coma, espacio o cadena vacía corrompe el conjunto; no hay escape ni validación.
- **Propuesta:** usar `putStringSet`/`getStringSet` o JSON; si se mantiene el formato, validar IDs al decodificar.

### DAT-05 · Se guarda en cada toque de la Pokébola
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:96-129`, especialmente `store.save(updated.toSnapshot())` en la línea 126.
- **Qué pasa:** cada toque, incluso los que solo animan al entrenador o tocan hierba sin cambiar progreso relevante, serializa todo el estado y llama a `SharedPreferences.apply()`.
- **Impacto:** escrituras y serializaciones constantes durante el juego; jank y desgaste innecesario. En un clicker, el ritmo de toques es alto.
- **Propuesta:** guardar con debounce/coalescing (por ejemplo, cada 1-2 s y al pasar a segundo plano), o solo cuando cambien campos persistibles.

### DAT-06 · `allowBackup` está activo con reglas de ejemplo
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `AndroidManifest.xml:5-8`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`.
- **Qué pasa:** el guardado puede entrar en backup de nube/transferencia, pero las reglas son las de plantilla y no incluyen ni excluyen nada explícitamente.
- **Impacto:** tras borrar la partida o reinstalar, un backup puede resucitar el estado anterior; también se traslada entre dispositivos sin control.
- **Propuesta:** decidir conscientemente si el guardado debe respaldarse; si sí, incluir solo `sharedpref` y documentarlo; si no, excluirlo.

### DAT-07 · El borrado total usa `apply()` y no reinicia todo el estado transitorio
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `data/GameStateStore.kt:70-72`, `viewmodel/MainViewModel.kt:166-171`.
- **Qué pasa:** `clear()` usa `apply()` asíncrono y el ViewModel no reinicia `lastWalkTapMs`; tampoco hay confirmación de que el borrado se haya persistido.
- **Impacto:** un cierre inmediato tras borrar podría dejar el guardado antiguo; el temporizador de caminar puede arrastrar un instante del estado anterior.
- **Propuesta:** usar `commit()` (o esperar el resultado) para una operación destructiva y reiniciar los campos transitorios relevantes.

### DAT-08 · `zoneAt` oculta índices inválidos en lugar de corregirlos
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:635`.
- **Qué pasa:** `zones[index.coerceIn(0, zones.lastIndex)]` evita el crash, pero no corrige el `zoneIndex` del estado.
- **Impacto:** la UI muestra una zona mientras el estado conserva otra; el siguiente guardado propaga el índice inválido.
- **Propuesta:** sanear el índice al restaurar y al avanzar; usar el valor corregido también en el estado.

### DAT-09 · Los clics sobrantes se pierden al avanzar de zona
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `model/GameData.kt:128-146`.
- **Qué pasa:** al alcanzar el umbral se hace `ZoneAdvance(currentIndex + 1, 0L)`; el excedente de `total` se descarta.
- **Impacto:** con potencia de clic alta, avanzar puede consumir muchos clics sin aprovecharlos; se siente injusto.
- **Propuesta:** arrastrar el excedente a la siguiente zona o documentar que el progreso no se acumula entre zonas.

---

## 3. Concurrencia, tiempo y ciclo de vida

### TIM-01 · Efectos secundarios dentro de `MutableStateFlow.update`
- **Prioridad:** P2 · **Estado:** Riesgo
- **Evidencia:** `viewmodel/MainViewModel.kt:103-125` (`directionChanged`, `lastWalkTapMs`), `viewmodel/MainViewModel.kt:351-370` (`nextSpawnMs += interval`, `nextProjectileId++`, `random`), `viewmodel/MainViewModel.kt:119`.
- **Qué pasa:** los lambdas de `update`/`updateAndGet` deben ser puros porque la implementación puede reintentar si otro hilo gana la carrera. Aquí contienen incrementos, escrituras de campos y llamadas a `Random`.
- **Impacto:** en cuanto una actualización llegue desde otro dispatcher, un reintento puede duplicar proyectiles, saltarse intervalos o alterar el timestamp de caminar. Hoy la mayoría de llamadas son del hilo principal, pero el patrón es frágil.
- **Propuesta:** calcular los valores fuera del lambda o encapsular el estado del gimnasio en un motor con actualización atómica y efectos posteriores.

### TIM-02 · `onGymEnemyHit` lee, calcula y escribe en pasos separados
- **Prioridad:** P2 · **Estado:** Riesgo
- **Evidencia:** `viewmodel/MainViewModel.kt:247-264`.
- **Qué pasa:** toma `_uiState.value`, calcula el daño y luego llama a `winGym` o `updateGym`. No es una operación atómica sobre el estado del gimnasio.
- **Impacto:** si dos invocaciones se solapan (test, accesibilidad, otro dispatcher o un cambio futuro), se puede llamar a `winGym` dos veces y duplicar monedas, medalla, vínculo y avance de zona. Incluso sin concurrencia, el patrón no protege ante un cambio entre lectura y escritura.
- **Propuesta:** hacer todo dentro de un único `update` que compruebe `outcome`/`phase` y, si la vida llega a 0, devuelva el estado de victoria; separar la persistencia del cálculo.

### TIM-03 · Reloj de ticks frente a reloj real
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:322,336,344` (`delay(50)`, `advanceWave(50)`, `advanceExposure(50)`), `model/Combat.kt:166-172`.
- **Qué pasa:** la stamina y la exposición avanzan por número de ticks, mientras los proyectiles usan `nowMs`. Un tick retrasado no descuenta el tiempo real correspondiente.
- **Impacto:** la duración real de una oleada varía con el rendimiento; los proyectiles y la barra se desincronizan. Es la base del problema de FUN-03.
- **Propuesta:** calcular el delta real entre ticks (`elapsed = now - lastTick`) con un tope, y usar ese delta para oleada, exposición y aparición.

### TIM-04 · `onReset` no cancela el reset de frame pendiente
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:145-159`, `viewmodel/MainViewModel.kt:514-525`.
- **Qué pasa:** `resetJob` puede seguir vivo y actualizar el frame del entrenador después del reset.
- **Impacto:** solo visual; el frame vuelve a 0 un segundo después de un reset, sin pérdida de datos.
- **Propuesta:** cancelar `resetJob` en `onReset` y en cualquier transición que reinicie la animación.

### TIM-05 · El bucle de caminar usa incrementos fijos de 100 ms
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:480-492`, `viewmodel/MainViewModel.kt:642`.
- **Qué pasa:** `walkProgress += WALK_TICK_MS / 1000f / ENCOUNTER_SECONDS` por iteración; si el hilo se retrasa, el progreso real es menor que el tiempo transcurrido.
- **Impacto:** el tiempo para encontrar un salvaje se alarga de forma variable; no es crítico, pero rompe la promesa de “3 segundos”.
- **Propuesta:** basar el progreso en tiempo real desde el último toque o en el delta real de cada tick.

---

## 4. Validación y robustez

### VAL-01 · `spawnEncounter` falla si una zona no tiene especies
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:451-454` (`zone.wildSpeciesIds.random(random)`).
- **Qué pasa:** `random()` lanza `NoSuchElementException` con una lista vacía. El catálogo actual no lo hace, pero `zones` es inyectable en el constructor para tests.
- **Impacto:** crash de la app ante un catálogo incompleto o modificado.
- **Propuesta:** validar el catálogo al construirlo (`require(wildSpeciesIds.isNotEmpty())`) y tener un fallback defensivo.

### VAL-02 · `perSpawn <= 0` rompe el gimnasio
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:354-360` (`random.nextInt(1, perSpawn + 1)`).
- **Qué pasa:** con una configuración de gimnasio con `projectilesPerSpawn` o `rageProjectilesPerSpawn` igual a 0, el rango de `nextInt` es inválido.
- **Impacto:** crash en combate si se añade contenido con una configuración incorrecta; no hay validación de `Gym`.
- **Propuesta:** validar en `Gym`/`GymCatalog` (`perSpawn >= 1`, `columns >= 1`, duraciones positivas) y usar `coerceAtLeast(1)` como defensa.

### VAL-03 · Un catálogo de zonas vacío rompe `zoneAt`
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:635`, constructor `viewmodel/MainViewModel.kt:62-67`.
- **Qué pasa:** `zones.lastIndex` sería `-1`; `coerceIn(0, -1)` lanza `IllegalArgumentException`.
- **Impacto:** crash inmediato con un catálogo vacío (tests o configuración futura).
- **Propuesta:** `require(zones.isNotEmpty())` al construir y evitar catálogos mutables vacíos.

### VAL-04 · La probabilidad de captura no se recorta
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:470-472`.
- **Qué pasa:** `captureChance = (1f - species.captureDifficulty) * BASE_CAPTURE_FACTOR` no se limita a `0f..1f`.
- **Impacto:** una dificultad fuera de rango produce probabilidades negativas o mayores que 1; el resultado sigue funcionando por comparación, pero la UI y el diseño pierden sentido.
- **Propuesta:** `coerceIn(0f, 1f)` y validar `captureDifficulty in 0f..1f` en el catálogo.

### VAL-05 · Los catálogos no validan sus invariantes
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `model/Catalogs.kt:18-37,47-63,70-95`, `model/GameData.kt:46-59,102-108`, `model/Combat.kt:107-122`.
- **Qué pasa:** no hay `require` para umbrales de zona positivos, listas de especies no vacías, dificultad válida, columnas/duraciones de gimnasio o recompensas no negativas.
- **Impacto:** los errores de contenido aparecen como crashes o comportamientos raros en tiempo de ejecución, no al arrancar.
- **Propuesta:** validar en `init` de los catálogos o con una comprobación de integridad al inicio; añadir tests de catálogo.

### VAL-06 · `frameCount` puede ser 0 y provocar división por cero
- **Prioridad:** P3 · **Estado:** Riesgo
- **Evidencia:** `viewmodel/MainViewModel.kt:108-109`.
- **Qué pasa:** `(currentFrame + 1) % total` con `total = sprites.frameCount(direction)`. La implementación actual devuelve 4, pero la interfaz permite un repositorio con 0.
- **Impacto:** `ArithmeticException` con un doble de test o un repositorio alternativo.
- **Propuesta:** validar `frameCount > 0` o tratar 0 como “sin animación”.

### VAL-07 · La longitud del nombre solo se limita en la UI
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/Intro.kt:65-66,163`, `viewmodel/MainViewModel.kt:178-188`.
- **Qué pasa:** `MAX_NAME_LENGTH = 12` se aplica en `onNameChange`, pero el ViewModel acepta cualquier longitud y un guardado manipulado puede contener un nombre largo.
- **Impacto:** HUD e intro con textos desbordados; inconsistencia entre fuente de verdad y vista.
- **Propuesta:** normalizar (trim, límite, no vacío) en `onFinishIntro` y usar la misma constante en la capa de estado.

---

## 5. UX, layout y accesibilidad

### UX-01 · El gimnasio no es operable con TalkBack ni teclado
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:313-373` (una única capa `pointerInput` sin semántica), `ui/GymOverlay.kt:217-229` (rocas con `contentDescription = null`), `ui/GymOverlay.kt:243-275` (sprites sin acción).
- **Qué pasa:** toda la interacción del combate es geométrica y táctil. No hay acciones semánticas para “atacar al líder” ni para romper una roca, y los objetivos se mueven.
- **Impacto:** el contenido principal del juego es inaccesible para usuarios de lector de pantalla o controles alternativos.
- **Propuesta:** ofrecer una alternativa accesible (botón “atacar”, lista de rocas con acción, o modo de juego asistido) y añadir semántica/acciones a la capa de toque.

### UX-02 · La Pokébola del menú no tiene acción de clic semántica
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `ui/PokemonBottomMenu.kt:633-636`.
- **Qué pasa:** el `pointerInput` detecta toques y arrastres, y la semántica declara `contentDescription` y `Role.Button`, pero no un `onClick`. TalkBack anuncia un botón que no puede activar.
- **Impacto:** el control principal de la app queda inutilizable con accesibilidad.
- **Propuesta:** añadir `semantics { onClick(label) { ...; true } }` o una acción equivalente, y probar con TalkBack. Mantener el gesto táctil como complemento.

### UX-03 · El HUD puede desbordar en pantallas pequeñas o con fuente grande
- **Prioridad:** P1 · **Estado:** Riesgo
- **Evidencia:** `ui/MainScreen.kt:107-145`, `ui/MainScreen.kt:325-410`.
- **Qué pasa:** `MainScreen` es una `Column` con un HUD de altura intrínseca (nombre, monedas, zona, progreso, estado, principal, estadísticas, gimnasio, reset/lead, borrado) y el resto con `weight(1f)`. No hay scroll ni modo compacto.
- **Impacto:** con `fontScale` alto, pantallas pequeñas o idioma más largo, el HUD puede ocupar toda la altura, recortar botones o dejar al entrenador sin espacio.
- **Propuesta:** HUD desplazable o plegable, tamaños mínimos, `LazyColumn` para el contenido y pruebas con `fontScale` 1.3/2.0 y anchos de 320 dp.

### UX-04 · El gimnasio se solapa en horizontal o alturas pequeñas
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:209-215,521-529`.
- **Qué pasa:** enemigo, jugador y punto de impacto usan posiciones fijas en dp. En landscape, el enemigo (arriba) y el jugador (abajo) pueden solaparse; `impactY` se fuerza a un mínimo que puede quedar fuera de la pantalla.
- **Impacto:** escena ilegible o imposible de jugar en landscape y en móviles bajos.
- **Propuesta:** calcular posiciones con proporciones de la altura disponible, con mínimos/máximos; bloquear orientación o diseñar un layout específico.

### UX-05 · La intro y el selector de starter desbordan en pantallas estrechas
- **Prioridad:** P2 · **Estado:** Riesgo
- **Evidencia:** `ui/Intro.kt:130-178,252-274`.
- **Qué pasa:** la `Row` de starters tiene tres tarjetas de 88 dp más padding y separación; no hay scroll ni wrap. La columna general no es desplazable y `imePadding` reduce el espacio.
- **Impacto:** en 320 dp de ancho o landscape, la fila puede salirse; con teclado abierto, el campo y la caja de diálogo pueden quedar comprimidos.
- **Propuesta:** `FlowRow` o `LazyRow`, contenido desplazable y `BringIntoViewRequester` para el campo de nombre.

### UX-06 · Descripciones duplicadas y estado de la hierba alta
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/MainScreen.kt:192-201` (capas decorativas con `tall_grass_desc`), `ui/MainScreen.kt:212-219` (botón con la misma descripción).
- **Qué pasa:** las dos capas visuales de hierba y el botón anuncian “Hierba alta”. El botón no indica si entra o sale del modo ni su estado.
- **Impacto:** el lector de pantalla recorre elementos decorativos y no distingue la acción real.
- **Propuesta:** `contentDescription = null` en las capas decorativas y una descripción/estado dinámico en el botón (“Entrar en la hierba alta” / “Salir de la hierba alta”).

### UX-07 · Texto blanco sobre imagen sin fondo en el gimnasio
- **Prioridad:** P2 · **Estado:** Riesgo
- **Evidencia:** `ui/GymOverlay.kt:297-309` (stamina), `ui/GymOverlay.kt:471-475` (HP), `ui/GymOverlay.kt:410-449` (HUD con fondo).
- **Qué pasa:** la stamina y la vida del jugador se dibujan en blanco directamente sobre el fondo del gimnasio; solo el HUD superior tiene un fondo negro al 50 %.
- **Impacto:** contraste insuficiente según la zona de la imagen y el brillo; difícil de leer en exteriores.
- **Propuesta:** añadir un scrim local o una superficie semitransparente detrás de cada barra/etiqueta; verificar con herramienta de contraste.

### UX-08 · Objetivos táctiles de 44 dp en el menú
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/PokemonBottomMenu.kt:478-489`.
- **Qué pasa:** el área clicable de Mapa/Pokédex es de 44 dp, por debajo de los 48 dp recomendados; la etiqueta queda fuera del área.
- **Impacto:** toques fallidos, especialmente con movilidad reducida.
- **Propuesta:** 48 dp mínimos y ampliar el área clicable para incluir la etiqueta.

### UX-09 · El principal seleccionado se muestra como botón deshabilitado
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/Overlays.kt:157-164`, `viewmodel/MainViewModel.kt:192-194`.
- **Qué pasa:** la opción actual usa `Button(onClick = {}, enabled = false)`, sin marca de selección ni semántica de “seleccionado”. Elegir no cierra el selector.
- **Impacto:** parece una opción rota y no se anuncia el estado; el usuario debe cerrar manualmente.
- **Propuesta:** usar una fila con `selected`/check y semántica, o cerrar al elegir si se prefiere un flujo de un toque.

### UX-10 · El estado del menú inferior no sobrevive a la rotación
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/PokemonBottomMenu.kt:304-313`.
- **Qué pasa:** `isExpanded` y `progress` usan `remember`; al recrear la Activity vuelven al estado de reposo aunque el contenido siga en Home.
- **Impacto:** la Pokébola se pliega sola y se pierde la posición visual; no afecta a la partida.
- **Propuesta:** `rememberSaveable` con un `Saver` para el progreso o sincronizar con el ViewModel.

### UX-11 · El splash no se puede saltar y se repite si se rota durante la animación
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/PokeBallSplash.kt:48-62`, `MainActivity.kt:56-58`.
- **Qué pasa:** la animación dura 800 ms de espera + 1000 ms de salida y no tiene acción de salto. `rememberSaveable` evita repetirla en rotaciones posteriores, pero no durante la propia animación; el `LaunchedEffect` reinicia el temporizador.
- **Impacto:** arranque en frío de casi 2 s añadido al tiempo real (se observaron unos 4 s en el emulador), sin forma de omitirlo.
- **Propuesta:** usar la Splash Screen API para el arranque y dejar la animación como transición breve; añadir toque para saltar y guardar el progreso de la animación.

### UX-12 · El teclado puede tapar la intro
- **Prioridad:** P3 · **Estado:** Riesgo
- **Evidencia:** `ui/Intro.kt:130-135`.
- **Qué pasa:** `imePadding()` reduce la columna, pero no hay desplazamiento ni petición de visibilidad para el campo de nombre.
- **Impacto:** en pantallas pequeñas o landscape, el campo o el botón pueden quedar bajo el teclado.
- **Propuesta:** contenido desplazable y `BringIntoViewRequester` al enfocar el campo.

### UX-13 · Iconos de navegación blancos sobre barra blanca
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `res/values/themes.xml:12` (`windowLightNavigationBar = false`), `ui/PokemonBottomMenu.kt:422-428` (`Surface` con `Color.White`).
- **Qué pasa:** la barra de navegación es transparente y el fondo inferior es blanco; los iconos/botones del sistema se configuran como claros.
- **Impacto:** con navegación por botones, los iconos pueden quedar casi invisibles sobre el menú blanco.
- **Propuesta:** `windowLightNavigationBar = true` para el tema claro, o un color de navegación que garantice contraste; probar con 2 y 3 botones.

### UX-14 · La barra de estado se oculta siempre y los cutouts laterales no se tratan
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `res/values/themes.xml:13-14`, `ui/Layout.kt:94-106`.
- **Qué pasa:** `windowFullscreen = true` oculta la barra de estado; `PercentageLayout` aplica insets superiores e inferior de barras, pero no insets horizontales del cutout en landscape.
- **Impacto:** se pierde información del sistema (hora, batería) y el contenido puede quedar bajo el notch lateral al girar.
- **Propuesta:** decidir si el fullscreen es requisito; si no, edge-to-edge normal; añadir insets horizontales (`displayCutout`) al layout.

### UX-15 · Las etiquetas del menú inferior no son clicables
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/PokemonBottomMenu.kt:474-506`.
- **Qué pasa:** el `clickable` está solo en el `Box` del icono; la etiqueta “Mapa/Pokédex” queda fuera.
- **Impacto:** tocar el texto no hace nada, aunque visualmente forma parte del botón.
- **Propuesta:** aplicar el `clickable` a toda la `Column` y fusionar la semántica.

### UX-16 · El efecto máquina de escribir no se anuncia como región viva
- **Prioridad:** P3 · **Estado:** Riesgo
- **Evidencia:** `ui/Intro.kt:199-203`.
- **Qué pasa:** el texto del diálogo se actualiza carácter a carácter, pero no tiene `liveRegion` ni semántica de progreso.
- **Impacto:** los usuarios de lector de pantalla no reciben el texto de forma natural y pueden perderse la introducción.
- **Propuesta:** añadir `liveRegion = LiveRegionMode.Polite` o mostrar de golpe el texto completo cuando la accesibilidad está activa.

### UX-17 · Tema Compose por defecto, sin modo oscuro real y con blancos fijos
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `MainActivity.kt:52-54` (`MaterialTheme {}`), `ui/PokemonBottomMenu.kt:427` (`Color.White`), `res/values/themes.xml`, `res/values-night/themes.xml`.
- **Qué pasa:** la app declara `Theme.MaterialComponents.DayNight`, pero Compose usa el `MaterialTheme` por defecto (claro, colores púrpura), y la barra del menú es blanca fija. El modo oscuro del sistema no se refleja en la UI Compose.
- **Impacto:** identidad visual genérica, sin tema Pokémon, y experiencia nocturna inconsistente.
- **Propuesta:** crear `PokeclicTheme` con `lightColorScheme`/`darkColorScheme`, tipografía y colores propios; sustituir los colores fijos por tokens del tema.

### UX-18 · La captura no tiene animación ni feedback de lanzamiento
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/Overlays.kt:55-93`, `viewmodel/MainViewModel.kt:416-431`.
- **Qué pasa:** el overlay muestra texto y el resultado se aplica al tocar la Pokébola; no hay sprite del salvaje, animación de lanzamiento, éxito o huida.
- **Impacto:** la mecánica principal de colección se siente plana y no comunica bien el resultado.
- **Propuesta:** añadir un estado visual (lanzamiento, captura, fallo) y sprite del encuentro; mantener la lógica en el ViewModel.

### UX-19 · Localización mezclada y solo en español
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `res/values/strings.xml:6-8,29,50`, `ui/Intro.kt:206`.
- **Qué pasa:** conviven “Trainer”, “Poké Ball”, “Reset” y “Stamina” con textos en español; no hay `values-en` ni otros idiomas. El símbolo “▼” está en código.
- **Impacto:** inconsistencia y deuda para internacionalizar.
- **Propuesta:** unificar el idioma base, mover símbolos a recursos y preparar locales si se va a distribuir.

---

## 6. Rendimiento y recursos

### PERF-01 · `rock_gym.jpg` pesa 3,5 MB
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `app/src/main/res/drawable/rock_gym.jpg` (3.509.494 bytes), usado en `ui/AppRoot.kt:93-100`.
- **Qué pasa:** un JPEG de fondo grande se decodifica completo para una pantalla de móvil.
- **Impacto:** APK más grande, pico de memoria al entrar al gimnasio y posible jank/OOM en dispositivos modestos.
- **Propuesta:** redimensionar a la resolución necesaria, convertir a WebP y usar `BitmapFactory` con `inSampleSize` o `ImageRequest` si se añade carga asíncrona.

### PERF-02 · La escena del gimnasio se recompone en cada frame
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:156-165` (estado `clock` leído en composición), `ui/GymOverlay.kt:217-229` (imágenes de proyectiles).
- **Qué pasa:** `clock.longValue = nowMs()` se actualiza en cada frame y `val now = clock.longValue` se lee en el cuerpo del composable. Eso invalida y recompone `GymScene` a 60 fps, incluso sin proyectiles, en lugar de limitarse a redibujar capas.
- **Impacto:** trabajo de composición constante durante todo el combate; batería y jank en dispositivos lentos. El comentario del código sugiere que solo se repinta la escena, pero la lectura del estado provoca recomposición.
- **Propuesta:** mover la animación a `graphicsLayer`/`Canvas` con estado diferido (`derivedStateOf`) o animar cada proyectil por separado; evitar leer un estado que cambia cada frame en el cuerpo del composable.

### PERF-03 · `material-icons-extended` y APK de debug de ~69 MB
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `gradle/libs.versions.toml:25`, `app/build/outputs/apk/debug/app-debug.apk` (69.488.159 bytes; `classes.dex` ~40,8 MB).
- **Qué pasa:** se depende de la librería completa de iconos extendidos para usar unos pocos iconos (`Settings`, `Info` y los del menú). El APK de debug medido es muy grande.
- **Impacto:** instalación/actualización lenta, más almacenamiento y tiempos de build; en release R8 puede reducirlo, pero conviene verificar el APK final.
- **Propuesta:** usar `material-icons-core` o vectores propios; comprobar el tamaño de `assembleRelease` y activar `shrinkResources` si no está cubierto por la optimización actual.

### PERF-04 · Los GIF se decodifican en el hilo principal y no se liberan
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/Intro.kt:299-331` (`ImageDecoder.decodeDrawable` dentro de `remember`), usado también en el gimnasio (`GymOverlay.kt:381-398`).
- **Qué pasa:** la decodificación ocurre durante la composición; los `AnimatedImageDrawable` se inician, pero no se detienen ni se liberan al salir de composición.
- **Impacto:** jank al abrir la intro o el gimnasio y animaciones que pueden seguir consumiendo recursos hasta que el GC actúe.
- **Propuesta:** decodificar de forma asíncrona, cachear los drawables y usar `DisposableEffect` para detenerlos/liberarlos.

### PERF-05 · Bitmaps en carpetas sin densidad y sin formatos modernos
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `res/drawable/*.png`, `res/drawable/*.gif`; lint reporta `IconLocation` en todos los bitmaps.
- **Qué pasa:** los recursos están en `drawable/` en lugar de `drawable-nodpi/` o buckets de densidad; Android puede escalarlos y gastar memoria de forma innecesaria. Los PNG grandes no están en WebP.
- **Impacto:** memoria y nitidez variables según el dispositivo; APK inflado.
- **Propuesta:** decidir por recurso: `nodpi` para pixel-art, buckets para arte escalable, WebP para fondos; medir memoria con Android Studio Profiler.

### PERF-06 · Animación de pulso y bucles permanentes
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:195-201` (pulso infinito), `viewmodel/MainViewModel.kt:84-87,480-510` (bucles de caminar y CPS).
- **Qué pasa:** el pulso se ejecuta durante todo el combate aunque el líder no esté expuesto; el bucle de caminar despierta cada 100 ms y el de CPS cada segundo aunque `clicksPerSecond` sea 0.
- **Impacto:** consumo de batería y trabajo de fondo innecesario; el de CPS es hoy código muerto.
- **Propuesta:** activar el pulso solo en fase expuesta y modelar los bucles con `Flow`/`repeatOnLifecycle`, desactivando el CPS si vale 0.

---

## 7. Arquitectura y mantenibilidad

### ARCH-01 · La detección de impactos del gimnasio vive en la UI
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:313-373`.
- **Qué pasa:** la UI calcula el rectángulo del enemigo, la posición de cada proyectil, el radio de acierto y el más cercano; el ViewModel solo recibe un id o “enemigo”. Las constantes de geometría y las reglas de “qué es un impacto” están en la vista.
- **Impacto:** la mecánica no se puede probar en JVM, depende del layout y es fácil que UI y modelo se desincronicen al cambiar tamaños o tiempos.
- **Propuesta:** extraer un `GymHitTester` puro que reciba posiciones normalizadas y devuelva el objetivo; o mover la resolución de impactos al ViewModel con coordenadas ya transformadas.

### ARCH-02 · Los mapeos de sprites están repartidos y codificados en la UI
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:565-595` (`enemyGifFor`, `backGifFor`), `ui/Intro.kt:277-282` (`starterGif`), `model/Catalogs.kt:110-122` (`DefaultSpriteRepository` solo para el trainer).
- **Qué pasa:** solo el trainer usa `SpriteRepository`; los starters y los sprites de gimnasio se resuelven con `when` en la UI y con fallback silencioso a Bulbasaur/transparente.
- **Impacto:** añadir una especie o un gimnasio exige tocar la UI; un ID desconocido muestra arte incorrecto en lugar de un error visible.
- **Propuesta:** unificar un `SpriteRepository`/`AssetCatalog` para todos los sprites y un fallback explícito con placeholder.

### ARCH-03 · El modelo depende de `R`
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `model/Catalogs.kt:110-117`.
- **Qué pasa:** `DefaultSpriteRepository` vive en `model` y referencia `R.drawable.*`, por lo que la capa de modelo conoce recursos Android.
- **Impacto:** rompe la regla de dependencia documentada y dificulta tests JVM puros del repositorio; la interfaz está bien, pero la implementación no.
- **Propuesta:** mover `DefaultSpriteRepository` a `data/` o `ui/` y dejar el modelo con la interfaz y los datos puros.

### ARCH-04 · `GameUiState` y `MainViewModel` concentran demasiado
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/GameUiState.kt` (86 líneas, estado de juego + navegación + derivados), `viewmodel/MainViewModel.kt` (668 líneas: toque, caminar, captura, gimnasio, persistencia, navegación y animación).
- **Qué pasa:** un único estado y un único ViewModel mezclan dominios y hacen que cada nueva mecánica aumente el acoplamiento.
- **Impacto:** más riesgo de regresiones y difícil aislar tests; el gimnasio ya ocupa una parte importante del archivo.
- **Propuesta:** separar `GymEngine`/`GymStateHolder`, `ProgressRepository` y un `NavigationState` de UI; mantener `MainViewModel` como orquestador.

### ARCH-05 · Constantes de diseño de juego dentro de `derive`
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:589,609-610,653-660`.
- **Qué pasa:** `clicksPerSecond = 0L` y `maxHp = BASE_MAX_HP` se imponen en cada derivación, y la fórmula de potencia está hardcodeada.
- **Impacto:** el sistema de mejoras futuro obligará a tocar `derive` y puede pisar valores si se añaden campos; el CPS actual es código muerto.
- **Propuesta:** extraer una `ProgressionRules`/`UpgradeState` que calcule potencia, CPS y HP, y eliminar el CPS muerto hasta que exista.

### ARCH-06 · Contenido hardcodeado y acoplado al build
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `model/Catalogs.kt:18-95`, `model/GameData.kt:46-108`.
- **Qué pasa:** especies, zonas, gimnasios y tipos viven en objetos Kotlin; no hay carga desde JSON/assets ni validación de contenido.
- **Impacto:** añadir contenido requiere recompilar y tocar código; no se puede iterar de forma data-driven.
- **Propuesta:** para el vertical slice es aceptable; si el contenido crece, mover a un repositorio de datos con validación y tests.

### ARCH-07 · Navegación y estado de juego comparten el mismo objeto
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/GameUiState.kt:60-63`.
- **Qué pasa:** `contentTab`, `isOptionsOpen` e `isLeadOpen` viven junto a monedas, zona y encuentro.
- **Impacto:** el saneado/guardado debe distinguir qué es persistible; los cambios de pestaña recomponen y derivan estado de juego sin necesidad.
- **Propuesta:** separar `NavigationUiState` (transitorio) de `GameState` (dominio) o, al menos, agruparlos en subobjetos.

---

## 8. Tests y calidad

### TEST-01 · No hay tests del ViewModel
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** único archivo de test: `app/src/test/java/com/example/myapplication/model/ModelTest.kt`; el constructor de `MainViewModel` es inyectable (`MainViewModel.kt:62-67`), pero no se usa en tests.
- **Qué pasa:** no se cubren toque, captura, caminar, intro, reset, gimnasio, persistencia ni navegación.
- **Impacto:** regresiones como las de esta auditoría pasan desapercibidas; el diseño testeable no se aprovecha.
- **Propuesta:** tests con `kotlinx-coroutines-test`, dobles de `GameStateStore`/`SpriteRepository` y un reloj inyectable; empezar por los flujos de FUN-03, FUN-05 y DAT-01.

### TEST-02 · No hay tests de serialización ni migraciones
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `data/GameStateStore.kt` sin tests; no hay tests de `encodeSet`/`decodeSet`.
- **Qué pasa:** no se verifica el formato de guardado, los IDs vacíos, los conjuntos con comas ni la restauración de datos antiguos.
- **Impacto:** un cambio de persistencia puede romper partidas sin que los tests lo detecten.
- **Propuesta:** tests de ida y vuelta y de saneado/migración; valorar Robolectric para `SharedPreferences` o extraer un códec puro.

### TEST-03 · No hay tests de tiempo ni del bucle de combate
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ModelTest` solo prueba reglas puras (`breakProjectile`, `advanceWave`, etc.), no el bucle de `MainViewModel` ni el comportamiento en background.
- **Qué pasa:** los problemas de reloj, recuperación de ráfagas y daño acumulado no están cubiertos.
- **Impacto:** son justo los fallos más difíciles de detectar manualmente.
- **Propuesta:** usar un reloj inyectable y `TestCoroutineScheduler`; probar ticks retrasados, reanudación y límites de catch-up.

### TEST-04 · No hay tests de UI, accesibilidad ni overlays
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `app/src/androidTest` está vacío; no hay tests de Compose.
- **Qué pasa:** no se comprueba que los overlays bloqueen toques, que atrás cierre la capa correcta, que el HUD no desborde ni que TalkBack pueda activar la Pokébola.
- **Impacto:** los bugs P0/P1 de interacción no tienen red de seguridad.
- **Propuesta:** tests de Compose con `createAndroidComposeRule`, pruebas de semántica y de configuración de pantalla/fuente.

### TEST-05 · No hay CI ni puerta de lint
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** no hay configuración de CI en el proyecto; el informe de lint local contiene decenas de warnings y no se usa como gate.
- **Qué pasa:** el build puede pasar con advertencias de recursos, versiones y accesibilidad; los tests no se ejecutan automáticamente.
- **Impacto:** deuda acumulada y regresiones que solo se ven manualmente.
- **Propuesta:** workflow de CI con `assembleDebug`, `testDebugUnitTest`, `lintDebug` y publicación del informe; establecer un baseline de lint y revisar nuevas warnings.

### TEST-06 · No se prueban los mapeos de assets
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:565-595`, `ui/Intro.kt:277-282`.
- **Qué pasa:** los `when` de GIF no tienen tests y usan fallback silencioso.
- **Impacto:** un ID nuevo o renombrado muestra el arte equivocado sin fallar.
- **Propuesta:** test de cobertura de todos los IDs del catálogo contra el repositorio de sprites y fallback explícito.

---

## 9. Build, publicación y legal

### REL-01 · `applicationId` y `namespace` son `com.example.myapplication`
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `app/build.gradle.kts:7,13`, `MainActivity.kt:1`, todos los paquetes.
- **Qué pasa:** el identificador es de ejemplo y el paquete no coincide con “Pokeclic”. Se mantuvo a propósito para conservar la partida y el archivo `kanto_clicker.xml`.
- **Impacto:** no se puede publicar con ese ID; cambiarlo ahora invalida las preferencias existentes salvo migración.
- **Propuesta:** planificar el cambio de ID con una migración de `SharedPreferences` (leer el archivo antiguo, escribir el nuevo y borrar) o exportar/importar la partida antes del cambio.

### REL-02 · El build de release no está firmado
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `app/build.gradle.kts:20-27`; no hay `signingConfig` ni keystore configurado.
- **Qué pasa:** `assembleRelease` genera un APK sin firma; no se puede instalar ni distribuir como release.
- **Impacto:** bloquea la entrega/publicación y las pruebas reales de release.
- **Propuesta:** configurar firma por variables de entorno/keystore fuera del repositorio, `versionCode`/`versionName` y un pipeline de release.

### REL-03 · Recursos y nombres con propiedad intelectual de Pokémon
- **Prioridad:** P1 · **Estado:** Confirmado
- **Evidencia:** `res/drawable/golem.gif`, sprites de starters, `profesor_oak.png`, nombres “Pokémon”, “Brock”, “Profesor Oak”, etc.
- **Qué pasa:** el proyecto usa nombres, personajes y arte de Pokémon sin licencia visible.
- **Impacto:** riesgo legal y de moderación si se distribuye en tiendas; no es apto para publicación comercial sin autorización o sustitución de assets.
- **Propuesta:** mantenerlo como proyecto privado/educativo o reemplazar nombres/arte por contenido original; añadir avisos de terceros si corresponde.

### REL-04 · Dependencias desactualizadas respecto a un SDK 37 muy nuevo
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `gradle/libs.versions.toml:2-10` (Compose BOM 2024.10.01, lifecycle 2.8.7, activity 1.9.3, compiler 2.0.0), `app/build.gradle.kts:8-10` (compile/target 37), lint reports de versiones nuevas.
- **Qué pasa:** se combina un SDK/AGP muy reciente con librerías de UI antiguas; el lint local ya avisa de versiones disponibles.
- **Impacto:** posibles incompatibilidades al actualizar, APIs nuevas sin soporte y deuda de migración.
- **Propuesta:** fijar una matriz de versiones compatible y actualizar por bloques (Compose BOM + compiler + lifecycle + activity), verificando build, tests y UI.

### REL-05 · Toolchain de build muy reciente y poco reproducible
- **Prioridad:** P2 · **Estado:** Riesgo
- **Evidencia:** `agp = 9.4.1`, `composeCompiler = 2.0.0`, `compileSdk 37`; `gradle.properties` con configuration cache.
- **Qué pasa:** no hay wrapper version pin documentado en la auditoría (sí `gradlew`), y el conjunto AGP/Kotlin/Compose compiler es sensible a versiones.
- **Impacto:** otro equipo o una máquina limpia puede no reproducir el build; actualizar una pieza puede romper la compilación.
- **Propuesta:** documentar JDK, Gradle, AGP y Kotlin exactos; usar toolchains y CI con la misma imagen.

### REL-06 · Versionado y artefactos manuales
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `app/build.gradle.kts:16-17`.
- **Qué pasa:** `versionCode = 1` y `versionName = "1.0"` están fijos; no hay estrategia de versionado ni artefactos de release.
- **Impacto:** cada entrega requiere edición manual y es fácil olvidar subir el código de versión.
- **Propuesta:** derivar de CI o de un archivo de versión; generar APK/AAB y notas de release.

### REL-07 · Sin licencia ni avisos de terceros
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** no hay `LICENSE`, `NOTICE` ni avisos en el repositorio del proyecto.
- **Qué pasa:** no se declara la licencia del código ni de los assets.
- **Impacto:** incertidumbre para colaboradores y para la distribución.
- **Propuesta:** añadir licencia para el código propio y un inventario de assets con su origen/permiso.

### REL-08 · Debug y release comparten `applicationId`
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `app/build.gradle.kts:12-27`.
- **Qué pasa:** no hay `applicationIdSuffix`/`versionNameSuffix` para debug.
- **Impacto:** no se pueden instalar debug y release a la vez; los datos de una build pueden mezclarse con la otra.
- **Propuesta:** sufijo `.debug` y nombre de app diferenciado para debug.

---

## 10. Producto y roadmap (gaps conocidos)

### PROD-01 · El mapa es un placeholder
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/Overlays.kt:224-240`, `res/values/strings.xml:15-16`.
- **Impacto:** el destino “Mapa” no aporta funcionalidad; solo muestra texto.
- **Propuesta:** diseñar el mapa de zonas o retirar el destino hasta que exista.

### PROD-02 · La hoja de Opciones no tiene acciones
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `ui/Overlays.kt:193-220`.
- **Impacto:** “Configuración” y “Acerca de” parecen filas pulsables pero no hacen nada.
- **Propuesta:** implementar ajustes (sonido, vibración, borrar partida) y “Acerca de”, o marcarlas como no disponibles.

### PROD-03 · No hay Pokédex y `seenSpecies` no se usa
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:465`, `viewmodel/GameUiState.kt:36`; no hay pantalla que lea `seenSpecies`.
- **Impacto:** el botón “Pokédex” es engañoso y la colección no tiene escaparate.
- **Propuesta:** pantalla de Pokédex con vistos/capturados o eliminar el destino.

### PROD-04 · Las monedas no tienen ningún sumidero
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:405-406` (recompensa), `viewmodel/GameUiState.kt:35`; sistema de mejoras retirado según `DOCUMENTACION.md` §21.
- **Impacto:** la recompensa principal del gimnasio no sirve para nada; se pierde el bucle de progresión.
- **Propuesta:** reintroducir mejoras (potencia, HP, CPS) o una tienda; mientras tanto, mostrar las monedas como “en desarrollo” o evitar la recompensa vacía.

### PROD-05 · La producción pasiva (CPS) está muerta
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `viewmodel/MainViewModel.kt:609`, `viewmodel/MainViewModel.kt:500-510`, `viewmodel/GameUiState.kt:29-30`.
- **Impacto:** hay un bucle permanente que nunca produce nada; el estado sugiere una mecánica inexistente.
- **Propuesta:** conectar CPS a una mejora o eliminar el bucle y el campo hasta implementarlo.

### PROD-06 · Contenido y balance mínimos
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `model/Catalogs.kt:49-63` (2 zonas), `70-95` (1 gimnasio), `20-28` (7 especies); umbrales provisionales documentados.
- **Impacto:** el vertical slice se termina rápido y no representa el alcance de Kanto.
- **Propuesta:** plan de contenido por etapas y tabla de balance; no es un bug, pero sí una limitación de producto.

### PROD-07 · Sin sonido ni vibración en combate/captura
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** solo hay háptica en el menú (`ui/PokemonBottomMenu.kt:332`); no hay audio.
- **Impacto:** el combate y la captura se sienten menos satisfactorios.
- **Propuesta:** añadir feedback háptico/sonoro ligero y opción de desactivarlo en Ajustes.

### PROD-08 · Una sola partida, sin exportar/importar
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `data/GameStateStore.kt` (un archivo de preferencias), `MainViewModel.kt:166-171`.
- **Impacto:** no hay copias manuales ni varias ranuras; un borrado o corrupción no se puede recuperar.
- **Propuesta:** exportar/importar JSON en Opciones o copia de seguridad documentada.

### PROD-09 · No hay tutorial de las reglas del gimnasio
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `ui/GymOverlay.kt:442-447` solo muestra una pista contextual.
- **Impacto:** el jugador debe deducir que las rocas se rompen tocándolas y que el líder solo recibe daño expuesto.
- **Propuesta:** primera vez con un tutorial breve o ayuda en pausa.

### PROD-10 · El final del contenido no se comunica
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `model/GameData.kt:140-145`, `MainViewModel.kt:400-411`.
- **Qué pasa:** al medallar el último gimnasio y estar en la última zona, el progreso se capa sin mensaje de “fin de contenido”.
- **Impacto:** el jugador puede pensar que el juego se ha roto.
- **Propuesta:** pantalla de fin de slice o mensaje “contenido en desarrollo”.

---

## 11. Documentación y repositorio

### DOC-01 · La documentación está desincronizada con el build actual
- **Prioridad:** P2 · **Estado:** Confirmado
- **Evidencia:** `DOCUMENTACION.md:940-947` sigue listando `androidx.fragment-ktx` y `lifecycle-viewmodel-compose`; `DOCUMENTACION.md:1045-1051` menciona `COUNTER_BIAS`, que ya no existe en `MainLayout`.
- **Impacto:** confunde a quien se incorpora y contradice la limpieza de dependencias ya realizada.
- **Propuesta:** regenerar las tablas de dependencias/estructura y revisar las guías de layout contra el código.

### DOC-02 · El repositorio Git está en la carpeta personal y el proyecto no tiene commits
- **Prioridad:** P1 (entorno) · **Estado:** Confirmado
- **Evidencia:** `git rev-parse --show-toplevel` devuelve `C:/Users/Saiciton`; `git log` no tiene commits; el proyecto aparece como `?? ./` desde su carpeta.
- **Qué pasa:** el control de versiones cubre todo el perfil del usuario, no el proyecto; no hay historial del código.
- **Impacto:** riesgo de perder trabajo, commits accidentales de archivos personales y dificultad para revisar cambios.
- **Propuesta:** inicializar un repositorio dentro de `MyApplication2` (o mover el `.git`), asegurar `.gitignore` para build/local.properties y hacer un primer commit del proyecto.

### DOC-03 · Hay assets duplicados en la raíz del proyecto
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** `bulbasaur.gif`, `charmander.gif`, `golem.gif`, `GymArena.jpg`, `rockGym.jpg`, `Profesor_Oak.png`, etc. en la raíz; los mismos recursos están en `app/src/main/res/drawable/`.
- **Impacto:** ruido, confusión sobre la fuente de verdad y peso innecesario si se versionan.
- **Propuesta:** mover los originales a una carpeta de diseño fuera del repo o eliminarlos; documentar el pipeline de assets.

### DOC-04 · El lint no está limpio y no hay baseline
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** informe local con decenas de warnings: `GifUsage`, `IconLocation`, `IconDuplicates`, `PluralsCandidate`, `UseKtx`, `LocalContextResourcesRead`, versiones nuevas.
- **Impacto:** las warnings reales se pierden entre ruido; algunas (plurales, recursos, versiones) son accionables.
- **Propuesta:** corregir las de recursos/accesibilidad, crear baseline para las inevitables y ejecutar lint en CI.

### DOC-05 · No hay estilo de código ni análisis estático más allá de lint
- **Prioridad:** P3 · **Estado:** Confirmado
- **Evidencia:** no hay ktlint/detekt/editorconfig.
- **Impacto:** estilo inconsistente y errores detectables (complejidad, imports, nombres) sin red.
- **Propuesta:** añadir ktlint o detekt y formatear en CI.

---

## 12. Anexo: comprobaciones sugeridas para cerrar cada bloque

1. **Overlays:** test de UI que pulse donde está “Borrar partida” con la intro/captura/principal abiertas y verifique que no se borra.
2. **Ciclo de vida:** iniciar gimnasio, poner la app en background 10 s, volver y comprobar que no hay daño retroactivo ni salvas acumuladas.
3. **Guardado:** crear un `GameSnapshot` con IDs desconocidos, valores negativos y zona fuera de rango; comprobar que la app arranca en un estado coherente.
4. **Intro:** dejar el nombre vacío y pulsar “Done” en el teclado; comprobar que no se puede avanzar.
5. **Accesibilidad:** recorrer la app con TalkBack; comprobar que la Pokébola se activa y que existe una forma de jugar el gimnasio.
6. **Layout:** probar 320 dp de ancho, `fontScale` 2.0 y landscape en main, intro y gimnasio.
7. **Rendimiento:** entrar al gimnasio y medir recomposiciones/memoria; comparar APK debug y release.
8. **Publicación:** `assembleRelease`, verificar firma, `applicationId`, tamaño y sustitución de assets con IP.

---

*Documento generado a partir de la lectura del código actual. No sustituye a pruebas manuales ni a una revisión de producto.*
