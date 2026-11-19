# Documentación — Pokeclic

> Referencia técnica del proyecto. Refleja el estado del código en la rama de
> trabajo actual (noviembre de 2026). La app y el paquete son
> **`com.dsrs.pokeclic`**; el nombre visible sigue siendo **Pokeclic**.
>
> La arquitectura es **MVVM**: un `MainViewModel` con la lógica de juego y
> navegación, un `MapViewModel` para la pantalla del mapa, estado inmutable en
> `StateFlow` y persistencia versionada. El contenido de juego sigue en código
> (`Catalogs.kt`); la Pokédex y el mapa de Kanto ya se sirven desde assets JSON.

---

## 1. Visión general

### Qué es

Un juego **clicker** para Android basado en la primera generación de Pokémon.
Se avanza por Kanto pulsando la Pokébola, aparecen Pokémon salvajes mientras se
camina, se capturan en un minijuego de barra y se desafía a los líderes de
gimnasio. El objetivo es conseguir las 8 medallas y completar las 151 entradas
de la Pokédex.

### Bucle de juego

1. Cada toque en la Pokébola suma `clickPower` al progreso de la zona. El
   progreso **se capa en el umbral**: llegar al 100 % no avanza solo.
2. El mismo toque marca actividad de caminata. En zonas con fauna, 3 segundos de
   toques continuados llenan la barra de encuentro y abren un salvaje.
3. El encuentro se resuelve en el **minijuego de captura** (riel vertical, barra
   con gravedad y medidor de progreso). Al capturar: la especie pasa a la run, a
   la Pokédex y paga monedas **solo la primera vez**.
4. Con la barra llena aparece el botón **Avanzar**. En zonas con gimnasio el
   avance exige haber ganado ese gimnasio **en la run actual**.
5. Los gimnasios son combates clicker con fases excluyentes (ataque / expuesto),
   furia obligatoria y recompensa en monedas y medalla.
6. El **reset** conserva la meta permanente (monedas, avistamientos, capturas
   históricas, medallas, starter y vínculo) y vacía la run. El **borrado total**
   elimina todo y vuelve a la introducción.

### Pantallas y capas

| Pantalla / capa | Contenido |
|---|---|
| **Home** | HUD compacto, entrenador y fondo de ruta animado |
| **Mapa** | Mapa de Kanto con carcasa, zoom y zonas tocables |
| **Pokédex** | 151 filas con estado y contadores, dentro de su carcasa |
| **Gimnasio** | Entrada (cortinilla, discurso y tutorial) + combate |
| **Captura** | Minijuego a pantalla completa sobre la ruta congelada |
| **Intro** | Diálogo del Profesor Oak: nombre y starter |
| **Tutorial** | Diálogos guiados de partida nueva |
| **Opciones / Principal / Splash** | Hoja modal, selector de principal y cortinilla |

### Stack técnico

| Elemento | Valor |
|---|---|
| Lenguaje | Kotlin (soporte integrado en AGP 9, sin plugin `kotlin.android`) |
| Android Gradle Plugin | 9.1.1 |
| `compileSdk` / `targetSdk` | 37 |
| `minSdk` | **29** (Android 10) |
| Arquitectura | **MVVM** (View / ViewModel / Model / Data) |
| UI | **Jetpack Compose** + Material 3 |
| Compose BOM | 2024.10.01 |
| Compose Compiler | plugin `org.jetbrains.kotlin.plugin.compose` 2.2.10 |
| Estado | `StateFlow` inmutable (`@Immutable`), consumido con `collectAsStateWithLifecycle()` |
| Contenedor | 1 sola `Activity` (`MainActivity`) + `Scaffold` de Compose |
| Navegación | estado (`AppTab` + flags) en `GameUiState` |
| Persistencia | `SharedPreferences` con `schema_version` |
| Contenido | `Catalogs.kt` (código) + assets JSON de Pokédex y mapa |
| Progreso y monedas | `zoneClicks` / `coins` como `Long` |

### Contenido actual

- **151 especies** en `SpeciesCatalog` y en `assets/pokedex_catalogo.json`.
- **13 zonas** jugables en `ZoneCatalog`.
- **3 gimnasios**: Brock (rocas), Misty (anillos) y Lt. Surge (Voltorb).
- **47 ubicaciones** de Kanto en el catálogo del mapa.
- **8 fondos de ruta** con arte propio; el resto usa el arte antiguo de ruta.
- **151 sprites** de Pokédex en `drawable-nodpi/pokedex_0xxx.png`.

---

## 2. Arquitectura MVVM

### Capas y responsabilidades

```
┌──────────────────────────────────────────────────────────────┐
│  VIEW  (ui/)                                                 │
│  Composables SIN estado. Reciben estado inmutable y          │
│  devuelven eventos por lambdas. Sin lógica de negocio.       │
├──────────────────────────────────────────────────────────────┤
│  VIEWMODEL  (viewmodel/)                                     │
│  MainViewModel: juego + navegación + captura + gimnasio.     │
│  MapViewModel: carga del mapa y hit-testing de zonas.        │
│  Motores: EncounterEngine, CatchingEngine, GymCombatEngine,  │
│  TrainerAnimator, GameProjection.                            │
├──────────────────────────────────────────────────────────────┤
│  MODEL  (model/)                                             │
│  Entidades, reglas puras, catálogos y Pokédex.               │
│  Solo `Catalogs.kt` conoce `R.drawable` (sprites).           │
├──────────────────────────────────────────────────────────────┤
│  DATA  (data/)                                               │
│  GameStateStore (SharedPreferences), PokedexCatalogLoader,   │
│  KantoMapData (assets). Es el borde Android.                 │
└──────────────────────────────────────────────────────────────┘
```

### Los dos ViewModels

- **`MainViewModel`** es el único dueño del estado de juego y de navegación.
  Expone un único `StateFlow<GameUiState>` y todos los eventos de UI. La
  `MainActivity` cablea ese flujo y esos métodos a las lambdas de `AppRoot`.
- **`MapViewModel`** es el único que toca la capa de datos del mapa: carga el
  mapeo de Kanto **una vez** en `onScreenShown()` y resuelve el hit-testing en
  `onTap(u, v)`. Expone `StateFlow<MapUiState>` con los nombres ya resueltos. La
  pantalla solo convierte el toque a coordenadas normalizadas y reenvía.

### Motores del `MainViewModel`

El ViewModel conserva la superficie de eventos y la persistencia; los bucles y
reglas con sustancia viven en colaboradores internos:

| Colaborador | Responsabilidad |
|---|---|
| `GameProjection` | Recalcula los campos derivados (zona, potencia, gimnasio, Pokédex, tutorial) en cada cambio |
| `EncounterEngine` | Temporizador de caminata y aparición de salvajes |
| `CatchingEngine` | Bucle del minijuego de captura y cierre del encuentro |
| `GymCombatEngine` | Entrada al gimnasio, bucle del combate, ataques, furia, victoria y derrota |
| `TrainerAnimator` | Fotograma del entrenador, segundo fotograma lateral y vuelta al reposo |

Todos escriben a través de `mutate(persist)`, así que hay **un solo escritor**:
deriva el estado y decide si se persiste.

### Reglas de dependencia

- **View → ViewModel**: `MainActivity` conecta estado y acciones. Los
  composables no ven ningún ViewModel.
- **ViewModel → Model/Data**: usa tipos y reglas puras de `model/` y los
  contratos de `data/`. `MainViewModel` no importa `R`, así que es testeable en
  la JVM sin instrumentación.
- **Model → nada**: no depende de Android ni de Compose, salvo
  `DefaultSpriteRepository` en `Catalogs.kt`, que traduce `Direction` a
  `R.drawable`. La interfaz `SpriteRepository` no conoce Android.
- **La UI no calcula nada**: el ViewModel entrega `trainerFrameRes`, opciones,
  nombres, progreso, filas de la Pokédex y estado del mapa ya resueltos.

### Decisiones de diseño y motivo

| Decisión | Motivo |
|---|---|
| **Composables sin estado (`state` + lambdas)** | Se previsualizan y testean con estado de mentira, y no se acoplan al negocio. |
| **Dos ViewModels (juego y mapa)** | El mapa tiene su propio ciclo (carga de assets, hit-testing) y no contamina el estado de juego. |
| **Motores extraídos del ViewModel** | Cada bucle (caminar, capturar, combatir) tiene una responsabilidad y se prueba por separado. |
| **`GameUiState` `@Immutable`** | Promesa real: los campos se reemplazan con `copy`, nunca se mutan. Compose puede saltarse recomposiciones. |
| **`StateFlow` en vez de `LiveData`** | Idiomático con Compose; se consume con `collectAsStateWithLifecycle()`. |
| **Actualizaciones con `updateAndGet`** | Lecturas/escrituras atómicas entre los bucles y los toques. |
| **`zoneClicks` y `coins`: `Long`** | En un clicker los contadores crecen sin techo. |
| **Avance de zona manual** | El jugador decide cuándo dejar la zona; el progreso se capa en el umbral. |
| **Contenido en código, Pokédex y mapa en assets** | El contenido jugable cambia con el código y los tests; la presentación (151 filas, 47 zonas) es dato versionado. |
| **`viewModelScope` para todos los bucles** | Los Jobs mueren con el ViewModel; no hace falta `onCleared()`. |
| **Un solo sentido de persistencia** | Todo pasa por `mutate`; el estado transitorio se escribe con `persist = false`. |

---

## 3. Estructura de archivos

```
Pokeclic/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── kanto_locations.json     # 47 ubicaciones (id, nombre, tipo)
│       │   │   ├── kanto_regions.json       # geometrías de las zonas del mapa
│       │   │   └── pokedex_catalogo.json    # 151 especies para la Pokédex
│       │   ├── java/com/dsrs/pokeclic/
│       │   │   ├── MainActivity.kt          # Host: setContent, splash y factorías
│       │   │   ├── data/
│       │   │   │   ├── GameStateStore.kt    # Contrato + SharedPreferences (schema v2)
│       │   │   │   ├── KantoMapData.kt      # Carga y validación del mapeo de Kanto
│       │   │   │   └── PokedexCatalogLoader.kt # Carga única del catálogo de la dex
│       │   │   ├── dbg/
│       │   │   │   └── MapEditorContract.kt # Puente con el editor de mapa (solo debug)
│       │   │   ├── model/                   # ── MODEL ──
│       │   │   │   ├── GameData.kt          # AppTab, Direction, Species, Zone, Encounter, snapshot
│       │   │   │   ├── Combat.kt            # Tipos, Gym, fases, proyectiles, anillos, Voltorb
│       │   │   │   ├── CatchingGame.kt      # Reglas puras del minijuego de captura
│       │   │   │   ├── Catalogs.kt          # SpeciesCatalog, ZoneCatalog, GymCatalog, sprites
│       │   │   │   ├── Pokedex.kt           # Catálogo, estados y filas de la Pokédex
│       │   │   │   ├── PokedexProjector.kt  # Proyección memoizada de las 151 filas
│       │   │   │   ├── MapRegions.kt        # Zonas del mapa y hit-testing (u, v)
│       │   │   │   ├── MapRegionsJson.kt    # Parseo y validación de los JSON del mapa
│       │   │   │   └── Tutorial.kt          # Máquina de estados del tutorial guiado
│       │   │   ├── viewmodel/               # ── VIEWMODEL ──
│       │   │   │   ├── GameUiState.kt       # Estado único inmutable
│       │   │   │   ├── MainViewModel.kt     # Eventos, mutate, persistencia y navegación
│       │   │   │   ├── GameProjection.kt    # Campos derivados
│       │   │   │   ├── EncounterEngine.kt   # Caminata y encuentros
│       │   │   │   ├── CatchingEngine.kt    # Bucle de captura
│       │   │   │   ├── GymCombatEngine.kt   # Combate de gimnasio
│       │   │   │   ├── TrainerAnimator.kt   # Animación del entrenador
│       │   │   │   ├── MapUiState.kt        # Estado de la pantalla del mapa
│       │   │   │   └── MapViewModel.kt      # Carga y hit-testing del mapa
│       │   │   └── ui/                      # ── VIEW ──
│       │   │       ├── AppRoot.kt           # Shell: capas, Scaffold, menú y previews
│       │   │       ├── MainScreen.kt        # Pantalla principal + HUD + CTA
│       │   │       ├── Overlays.kt          # Mapa, hoja de opciones y selector de principal
│       │   │       ├── PokemonBottomMenu.kt # Barra, Pokéball, hendidura y controles
│       │   │       ├── RouteBackground.kt   # Fondo de ruta en bucle y movimiento
│       │   │       ├── RouteBackgrounds.kt  # Catálogo de fondos por zona
│       │   │       ├── WorldFrame.kt        # Proyección del mundo (arte → pantalla)
│       │   │       ├── WorldTrainer.kt      # Entrenador sobre la proyección del mundo
│       │   │       ├── Layout.kt            # PercentageLayout (guías + sesgo)
│       │   │       ├── Intro.kt             # Introducción del Profesor Oak
│       │   │       ├── TutorialOverlay.kt   # Diálogos del tutorial guiado
│       │   │       ├── GymIntro.kt          # Entrada al gimnasio (bienvenida/tutorial/cartel)
│       │   │       ├── GymEntry.kt          # Cortinilla y guion del líder
│       │   │       ├── GymOverlay.kt        # Escena y HUD del combate
│       │   │       ├── GymSceneLayouts.kt   # Composición por gimnasio
│       │   │       ├── GymRageSpeech.kt     # Discurso de furia
│       │   │       ├── GymVictory.kt        # Despedida y entrega de medalla
│       │   │       ├── LeaderDialogue.kt    # Marco compartido de diálogos del líder
│       │   │       ├── CatchingOverlay.kt   # Escena del minijuego de captura
│       │   │       ├── DexScreen.kt         # Pantalla de Pokédex con carcasa
│       │   │       ├── PokedexSprites.kt    # Mapa dexNumber → sprite
│       │   │       ├── MapCarcasa.kt        # Carcasa de la pestaña Mapa
│       │   │       ├── MapTransform.kt      # Zoom, desplazamiento y (u, v)
│       │   │       ├── MapRegionBadge.kt    # Indicador de la zona tocada
│       │   │       ├── RetroTheme.kt        # Colores, tipografía y formas retro
│       │   │       ├── GameUiComponents.kt  # Botón, moneda y helpers compartidos
│       │   │       ├── PixelFont.kt         # Fuente 5×7 para el cartel de combo
│       │   │       ├── PixelArt.kt          # Recorte de margen de sprites
│       │   │       ├── ModalBlocking.kt     # Aislamiento de input de capas modales
│       │   │       ├── RockShatterFx.kt     # Estallido de roca
│       │   │       ├── WaterRingFx.kt       # Acierto/fallo del anillo de Misty
│       │   │       └── PokeBallSplash.kt    # Cortinilla de arranque
│       │   ├── keepRules/rules.keep         # Reglas de R8 conservadas
│       │   └── res/
│       │       ├── values/                  # strings, colors y Theme.Pokeclic
│       │       ├── values-night/            # Variante nocturna del tema
│       │       ├── drawable/                # Trainer, líderes, medallas y GIFs
│       │       ├── drawable-nodpi/          # Fondos, mapas, carcasa, Pokédex y FX
│       │       └── xml/                     # Backup y extracción de datos
│       ├── debug/
│       │   ├── AndroidManifest.xml          # Registra el editor de mapa (solo debug)
│       │   ├── java/com/dsrs/pokeclic/dbg/  # Editor visual: actividad, pantalla, sesión y almacén
│       │   └── res/values/strings.xml       # Textos del editor
│       ├── test/java/com/dsrs/pokeclic/     # 34 suites JVM (modelo, data, VM y UI)
│       └── testDebug/java/com/dsrs/pokeclic/dbg/ # Tests del editor de mapa
├── docs/                                    # Guías y documentos de diseño
├── gradle/libs.versions.toml                # Catálogo de versiones
├── build.gradle.kts
├── settings.gradle.kts                      # rootProject.name = "Pokeclic"
└── gradle.properties
```

### Tests

No hay tests de instrumentación: todo corre en la JVM con
`./gradlew testDebugUnitTest`. Suites por área:

| Área | Suites |
|---|---|
| Modelo y reglas | `ModelTest`, `CatalogTest`, `CatchingGameTest`, `TutorialTest`, `MapRegionsTest`, `MapRegionsJsonTest`, `PokedexCatalogTest`, `PokedexProjectorTest` |
| Datos | `GameStateStoreTest`, `KantoMapDataTest`, `PokedexParserTest` |
| ViewModel | `MainViewModelTest`, `MainViewModelTutorialTest`, `MapViewModelTest`, `PokedexDexTest` |
| UI y catálogos | `AppRootTest`, `GymLeaderScriptTest`, `GymSceneLayoutsTest`, `GymVictoryScriptTest`, `IntroScriptTest`, `MapTransformTest`, `PixelFontTest`, `PokedexSpritesTest`, `RockShatterFxTest`, `WaterRingFxTest`, `Route*Test`, `WorldFrameTest` |

---

## 4. Shell, navegación y menú

### Composición de `AppRoot`

`AppRoot` es el shell **sin estado**: recibe `GameUiState`, `MapUiState` y las
lambdas. Su orden de composición, de abajo a arriba:

```
BoxWithConstraints
├── fondo de la escena
│   ├── gimnasio activo → arte del gimnasio (roca, agua o eléctrico)
│   ├── si no, Home con fauna o arte propio → RouteScrollingBackground
│   │   (también detrás del minijuego de captura, con la ruta congelada)
│   └── si no, pestaña Mapa → MapOceanBackdrop
├── WorldTrainer (Home, sin escena ni intro)
├── Scaffold
│   ├── bottomBar: PokemonBottomMenu (con fundido y reglas de ocultación)
│   └── content:
│       ├── Home → MainScreen
│       ├── Map  → MapCarcasaScreen
│       ├── GymOverlay    (si state.gym != null)
│       └── CatchingOverlay (si hay encuentro y no hay gimnasio)
├── GymIntroLayer (si state.gymIntro != null)
├── IntroOverlay (si needsStarter)
├── LeadOverlay (si isLeadOpen)
├── OptionsSheet (si isOptionsOpen)
└── TutorialLayer (diálogos del tutorial, siempre por encima)
```

La **Pokédex** es una rama temprana: si `isPokedexOpen`, `AppRoot` compone
`DexScreen` + el menú real y retorna antes del bloque anterior.

### Capas que toman la pantalla

- **Gimnasio** (`gym != null` o `gymIntro != null`): ocupa todo; no se compone el
  contenido de fondo ni el menú.
- **Captura** (`catching != null` con encuentro): escena transparente sobre la
  ruta congelada; tampoco hay menú.
- **Intro** (`needsStarter`): opaca; no se compone el juego.
- **Pokédex**: pantalla propia con su carcasa y el menú encima de la base.
- **Principal** (`isLeadOpen`), **opciones** y **splash**: el juego queda visible
  pero el menú no se compone (aislamiento de input).

`Modifier.modalBlocking()` consume los toques de toda capa modal. La Pokébola usa
un detector que ignora el consumo (`requireUnconsumed = false`), así que la única
forma de aislarla es **no componerla**; por eso el menú se oculta bajo cualquier
capa modal.

### Anatomía del menú

```
┌────────────────────────────────────────────────┐
│                  contenido                     │
│                                    (Pokéball)  │  ← flota al expandirla
├────────────────────────────────────────────────┤
│   Mapa              ▄▄▄▄▄▄▄▄          Pokédex   │  ← barra de acero
└────────────────────────────────────────────────┘
```

| Parte | Componente | Notas |
|---|---|---|
| Barra | `MenuBar` | `Surface` con `rememberBottomMenuNotchShape()` y borde de 1 dp. |
| Hendidura | `BottomMenuNotchShape` | `GenericShape` con dos `cubicTo`; la Pokéball se sienta en ella. |
| Botón lateral | `MenuIconItem` | Icono en círculo + etiqueta; se tiñe al seleccionarse. |
| Pokéball | `MenuPokeBall` | `pokeball_menu.png` + `graphicsLayer`. |

Los iconos son **Material Icons de relleno** y viven en el `private object
MenuIcons` de `PokemonBottomMenu.kt`.

### Ajustes (`object BottomMenuDefaults`)

| Constante | Valor | Para qué |
|---|---|---|
| `BAR_HEIGHT` | `96.dp` | Alto de la barra. |
| `POKEBALL_SIZE` | `100.dp` | Diámetro en reposo. |
| `POKEBALL_BOTTOM_PADDING` | `45.dp` | Posición vertical en reposo. |
| `POKEBALL_OFFSET_X` | `0.dp` | Posición horizontal en reposo. |
| `POKEBALL_EXPANDED_SIZE` | `280.dp` | Diámetro expandida. |
| `POKEBALL_EXPANDED_BOTTOM` | `55.dp` | Posición vertical expandida. |
| `POKEBALL_EXPANDED_OFFSET_X` | `0.dp` | Posición horizontal expandida. |
| `EXPANDED_SHADOW` | `0.dp` | Sombra, solo al expandirse. |
| `NOTCH_WIDTH` | `75.dp` | Semiancho de la hendidura en reposo. |
| `NOTCH_EXPANDED_WIDTH` | `140.dp` | Semiancho expandida. |
| `NOTCH_DEPTH` | `60.dp` | Fondo de la hendidura (constante). |
| `DRAG_SENSITIVITY` | `110.dp` | Recorrido de arrastre de 0 a 1. |
| `SNAP_THRESHOLD` | `0.20f` | Umbral para quedar arriba / volver abajo. |
| `TAP_PUNCH_SCALE` | `0.95f` | "Golpe" visual al tocar (`1f` = sin efecto). |
| `CONTAINER_HEIGHT` | *derivado* | Alto que reserva el `Scaffold`. **No se edita.** |

Tamaño y posición son **absolutos en dp**, no relativos entre sí. El fondo de la
hendidura debe quedar por debajo de la parte baja de la Pokéball:

```
fondo de la Pokéball = BAR_HEIGHT - POKEBALL_BOTTOM_PADDING   → 51 dp
NOTCH_DEPTH          = ese valor + el hueco que dejes debajo   → 60 dp
```

El hueco se abre a la vez que la Pokéball: su semiancho interpola entre
`NOTCH_WIDTH` y `NOTCH_EXPANDED_WIDTH` con el mismo `progress` del gesto.

> **Por qué no lleva sombra la barra.** La sombra de una forma cóncava sangra
> hacia el interior del hueco y aparece un halo oscuro alrededor de la Pokéball.
> La separación con el contenido la hace el `border` de 1 dp.

> **Por qué `MenuBar` recibe el `MutableFloatState`.** Si leyera un `Float`, la
> suscripción quedaría en `PokemonBottomMenu` y se recompondría el menú entero en
> cada frame. Leyendo dentro de `MenuBar`, solo se recompone la barra.

### Destinos y hoja de opciones

| Destino | Control | Resultado |
|---|---|---|
| **Mapa** | Botón izquierdo | `AppTab.Map`: `MapCarcasaScreen` |
| **Home** | Pokéball central | `AppTab.Home`: `MainScreen` |
| **Pokédex** | Botón derecho | Abre `OptionsSheet`; desde su fila *Pokédex* se entra a `DexScreen` |

`AppTab` solo tiene `Map` y `Home`. Opciones y Pokédex **no son pestañas**: la
hoja vive en `isOptionsOpen` y la dex en `isPokedexOpen`.

Filas de la hoja de opciones:

| Fila | Acción |
|---|---|
| Configuración | Decorativa (sin acción) |
| Acerca de | Decorativa (sin acción) |
| Cambiar principal | Abre `LeadOverlay` |
| Reset | `onReset()` (conserva la meta) |
| Pokédex (TEST) | `onOpenDex()` |
| Borrar partida (test) | Confirmación + `onWipeGame()` |
| Saltos de zona | Solo en builds debug: llenan la barra del gimnasio elegido |

Mientras la dex está abierta, los destinos del menú la cierran y navegan; la
Pokébola no se expande y el botón físico **Atrás** vuelve al juego.

### Gestos de la Pokébola

| Gesto | Estado | Resultado |
|---|---|---|
| Toque | En reposo | `onHomeClick` |
| Toque | Expandida | `onPokeBallGameClick` → clic de juego |
| Arrastre arriba y soltar | cualquiera | Se expande y va a Home |
| Arrastre abajo y soltar | cualquiera | Vuelve a su sitio |

Un único `pointerInput` con `awaitEachGesture`: solo consume cuando el dedo
supera el `touchSlop`, así que un toque limpio no se pierde. El movimiento va en
`graphicsLayer` (`translationY` y `scale`), de modo que el `Scaffold` solo reserva
`CONTAINER_HEIGHT` y la bola expandida flota sobre el contenido. La posición que
recibe el clic sigue siendo local a la Pokébola (los eventos no se transforman).

En el **mapa** y en la **Pokédex** la Pokébola no se expande: un arrastre
completado cuenta como toque y vuelve a la aventura.

---

## 5. Pantalla principal

### Elementos

| Elemento | Ubicación | Interactivo |
|---|---|---|
| HUD compacto | Franja superior, dentro de un `Column` con padding | No |
| CTA de progresión | Debajo del HUD | Gimnasio / Avanzar |
| Entrenador | `WorldTrainer`, sobre el mismo `Box` que el fondo | No |
| Fondo de ruta | Capa inferior, a pantalla completa | No (lo mueve el clic) |
| Pops `+N` | Capa superior, `PopLayer` | No |

### HUD compacto

`GameHud` muestra solo la run:

- **Cartel de zona** a la izquierda (placa crema con el nombre).
- **Monedas** a la derecha (`CoinPlate`), con pop `+N` cuando aumentan.
- **Barra EXP** con el contador `zoneClicks / zoneRequirement` superpuesto.

El botón de gimnasio y el de avanzar **no viven dentro del HUD**: están en
`ProgressionAction`, justo debajo, para que aparecer o desaparecer no cambie la
altura del panel ni mueva al entrenador. Las acciones de reset y cambio de
principal se movieron a la hoja de opciones.

### Avance de zona

- `advanceZone()` capa `zoneClicks` en el umbral: nunca hay overshoot.
- `canAdvanceZone` exige barra llena, que no haya gimnasio pendiente y que no sea
  la última zona.
- `isGymReady` exige gimnasio pendiente **en la run** y barra llena.
- `onAdvanceZone()` solo avanza si `canAdvanceZone`; en la última zona no pasa
  nada.

### Fondo de ruta animado

En Home, si la zona tiene fauna o arte propio, `AppRoot` compone
`RouteScrollingBackground` detrás del `Scaffold` transparente. La **misma**
instancia de `RouteBackground.motion` mueve el dibujo y decide la dirección del
entrenador, así que no pueden desincronizarse.

Zonas con arte y trayectoria propios (`RouteBackgrounds.byZone`):
**Pueblo Paleta**, **Bosque Verde**, **Ciudad Verde**, **Ciudad Plateada**,
**Ruta 3**, **Mt.Moon**, **Ciudad Celeste** y **Ciudad Carmín**. Las cuatro
últimas son **horizontales** (`axis = HORIZONTAL`). Las demás usan `DEFAULT`
(`background_ruta.png`).

| Aspecto | Decisión |
|---|---|
| Fuente del movimiento | Contador efímero `pokePulses` de `AppRoot`: un clic = un paso de `TILES_PER_CLICK = 1/25` baldosa |
| Fase | **Por zona** (`remember(state.zoneId)`): al cambiar de zona vuelve a cero |
| Frenado | Sin inercia: `MAX_STEP_NANOS = 100 ms` (mínimo `MIN_STEP_NANOS = 30 ms`) |
| Eje | `RouteBackgroundMotion.axis`: `VERTICAL` (por defecto) u `HORIZONTAL` (marco girado 90°) |
| Trayectoria | Tramos `RouteLeg` de un solo eje; alternar recto y lateral cambia la orientación |
| Ejes infinitos | El arte se repite en bucle y las columnas se dibujan contiguas como losetas |
| Arranque | `startAnchor` (vertical) y `startOffset` (lateral) |
| Bucle sin costura | `seamCutRow = 0` si el arte es continuo; si no, la fila de corte |
| Escalado | `FilterQuality.None` al ampliar; independiente de la resolución |
| Memoria | Los ocho fondos verificados declaran `artScale = 4`: se decodifican a 1/4 y se escalan, con 16× menos memoria |

El recorrido se mide en **baldosas**, no en px ni dp. La fase vive normalizada
en `[0f, 1f)` y se actualiza dentro de `withFrameNanos`: una pulsación no
recompone ni redibuja la capa.

### Dirección del entrenador

La Pokébola **ya no elige la dirección según el lado que se toca**. El clic
avanza el fondo y el eje del tramo decide la animación:

| Fondo | Tramo | Entrenador |
|---|---|---|
| Vertical | recto | `BACK` |
| Vertical | lateral derecha | `RIGHT` |
| Vertical | lateral izquierda | `LEFT` |
| Horizontal | recto | `RIGHT` |
| Horizontal | `LEFT` | sube (mira hacia arriba) |
| Horizontal | `RIGHT` | baja (mira hacia abajo) |

`TrainerAnimator` arranca cada dirección por su `startFrame()` (los laterales
empiezan en el segundo fotograma), encadena el ciclo y vuelve a `FRONT` frame 0
tras 1 segundo sin pulsar. El paso lateral programa su segundo fotograma a 60 ms.

### Feedback del clic

Cada incremento real de `zoneClicks` genera un **pop flotante** `+N` que sube,
se desvanece y se autoelimina. Es el **único** feedback: no hay flash de pantalla
ni sacudida del sprite. Con la barra llena o un encuentro abierto el delta es 0 y
no se genera.

### Clic y captura

`onPokeBallTouch(direction)` es el punto de entrada: si el tutorial no está en
juego libre, no hace nada. Si hay un encuentro, la Pokébola no interviene en la
partida (el minijuego tiene su propio control). Si no, marca actividad de
caminata y suma `clickPower`. Con la barra de zona llena, el delta es 0 porque el
progreso se capa.

`clickPower` se deriva en `GameProjection`:

```
clickPower = (1 + 0.05 × especies en posesión + 0.02 × vínculo si el starter
              está equipado).roundToLong().coerceAtLeast(1)
```

---

## 6. Posicionamiento porcentual (`PercentageLayout`)

Componente propio para mover elementos con valores porcentuales en cualquier
pantalla, sin `ConstraintLayout`.

### Uso

```kotlin
PercentageLayout {
    Image(..., Modifier.size(180.dp).anchorVertically(bias = 0.50f))
    Text(..., Modifier.anchorVertically(bias = 0.35f))
    Image(..., Modifier.size(80.dp).anchor(verticalBias = 0.80f, horizontalBias = 0.90f))
}
```

| Modifier | Efecto |
|---|---|
| `Modifier.anchorVertically(bias)` | Sesgo vertical dentro de la zona. `0f` arriba, `0.5f` centro, `1f` abajo. |
| `Modifier.anchor(verticalBias, horizontalBias)` | Ambos sesgos en un solo anclaje. |

### Parámetros (`ContentZoneDefaults`)

| Parámetro | Por defecto | Significado |
|---|---|---|
| `TOP` | `0.10f` | Guía superior (fracción de la altura). |
| `BOTTOM` | `0.90f` | Guía inferior. |
| `MAX_TOP` | `0.30f` | La guía superior nunca sube más. |
| `MIN_BOTTOM` | `0.70f` | La guía inferior nunca baja de aquí. |
| `applyInsets` | `true` | Corrige las guías con barras del sistema y notch. |

### Cómo funciona

1. Los hijos se miden con `Constraints(maxWidth = ancho del padre)` y altura
   libre.
2. Las guías se calculan sobre la altura real del contenedor:

```kotlin
val topLimit    = (height * topPercent    + topInsetPx).coerceAtMost(height * maxTopPercent)
val bottomLimit = (height * bottomPercent - bottomInsetPx).coerceAtLeast(height * minBottomPercent)
val zoneHeight  = (bottomLimit - topLimit).coerceAtLeast(0f)
```

3. Cada hijo se coloca según su sesgo:

```kotlin
val x = ((width  - placeable.width)  * anchor.horizontalBias).roundToInt()
val y = (topLimit + (zoneHeight - placeable.height) * anchor.verticalBias).roundToInt()
```

El inset superior suma y el inferior resta; la composición es idéntica en
cualquier móvil.

### Limitaciones

- Un elemento solo admite **un** `AnchorData`; usa `anchor(...)` para ambos ejes.
- Los sesgos **no se recortan**: fuera de `0f..1f` el elemento queda fuera de la
  zona.
- La altura se deja libre (`maxHeight = Infinity`): no uses `fillMaxHeight()`
  dentro; usa `size(...)` o tamaño intrínseco.
- Solo hay anclaje porcentual; para márgenes fijos usa `padding` u `offset`.

---

## 7. Pantalla de mapa

La pestaña **Mapa** es una pantalla real, no un placeholder. Se compone como
`MapCarcasaScreen` → `MapScreen`, con la carcasa entre el mapa y los controles.

### Capas

1. **Océano** (`MapOceanBackdrop`, `#0185DB`) detrás de todo, también bajo el
   hueco del menú.
2. **Mapa** (`MapScreen`): `mapa_kanto.png` con `ContentScale.Fit`.
3. **Carcasa** (`MapCarcasaFrame`): cabecera, marco y módulo inferior.
4. **Controles flotantes**: indicador de zona y botón de restaurar vista (botón
   del editor solo en debug).

La carcasa está separada del diseño original de 910 × 1728 px:

| Pieza | Asset | Tamaño | Comportamiento |
|---|---|---|---|
| Cabecera | `mapa_cabecera.webp` | 910×138 | Escala proporcional al ancho |
| Marco | `mapa_marco.9.png` | 912×1256 | NinePatch; solo se estira el centro |
| Módulo inferior | `mapa_modulo_inferior.webp` | 910×114 | Proporción nativa |
| Base del menú | `mapa_base_menu.webp` | 910×222 | Recorte centrado tras el menú real |

El amarillo `#FDC702` es el respaldo medido del asset.

### Datos del mapa

Dos assets, separando **nombres** de **geografía**:

- `assets/kanto_locations.json`: catálogo de **47 ubicaciones** con `id`
  numérico estable, nombre y tipo (`CITY`, `ROUTE`, `SPECIAL`).
- `assets/kanto_regions.json`: geometrías normalizadas `(u, v)` sobre la imagen
  original. Cada región es un **círculo**, un **rectángulo** o un **polígono**, y
  referencia al catálogo por `catalogId` o incrusta `name`/`type` si es nueva.

Los solapes se resuelven por prioridad: ciudad (3) > especial (2) > ruta (1). En
empate gana la primera del JSON. `selectAt` devuelve la región principal y las
demás que contienen el punto.

`KantoMapData.parseKantoMapData()` valida los dos documentos y sus referencias.
La carga de Android (`loadKantoMapData`) **nunca lanza**: un fallo deja el mapa
sin zonas y el juego sigue funcionando; el diagnóstico queda en Logcat con la
etiqueta `KantoMap`.

### `MapViewModel`

- `onScreenShown()` carga el mapeo **una vez** y limpia la selección anterior.
- `onTap(u, v)` hace el hit-testing y entrega `MapUiState(primaryName,
  otherNames)` con los nombres ya resueltos.
- Si el enlace al catálogo está roto, usa el id técnico como último recurso.

### Interacción

- **Pellizco** para zoom y **arrastre** para desplazar; la rotación se descarta.
- **Doble toque**: amplía a 2× anclado al punto; si ya está ampliado, restaura.
- **Toque simple**: envía `(u, v)` al ViewModel; fuera de toda zona el indicador
  desaparece.
- **Botón de restaurar** (`Replay`): vuelve a la vista completa con animación.
- Límites: `MapFitZoom = 1`, `MapMaxZoom = 5`, `MapDoubleTapZoom = 2`.

El indicador (`MapRegionBadge`) está fijo en la esquina superior derecha del área
visible, con márgenes de zona segura y ancho máximo del 70 % del contenedor.

### Editor de mapa (solo debug)

En compilaciones depurables aparece un botón de llave que abre
`MapEditorActivity` (vive en el source set `debug` y en el manifiesto de debug).
El puente está en `dbg/MapEditorContract.kt`: un `Intent` por acción, sin tipos
del editor en `main`. En release la triple barrera (botón, actividad y captura de
`ActivityNotFoundException`) lo deja inofensivo.

---

## 8. Pokédex

Pantalla propia a pantalla completa (`isPokedexOpen`), con la **carcasa** de la
consola y el menú real encima de la base.

### Estructura visual

| Pieza | Asset | Notas |
|---|---|---|
| Cabecera | `pokedex_cabecera.webp` (941×280) | Sin controles dibujados: los contadores se superponen |
| Marco | `pokedex_marco.9.png` (943×1106) | NinePatch; la lista vive solo en el interior declarado |
| Base | `pokedex_base.webp` (941×288) | Detrás del espacio del menú |
| Respaldo | — | Rojo `#FA1B1C` a pantalla completa, sin franjas |

La lista es una `LazyColumn` de 151 filas. Cada fila muestra número, sprite,
nombre, tipo y una Pokéball que codifica el estado. El sprite se recorta con
`opaqueBounds()` y se normaliza a un recuadro de 88×72 dp, con
`FilterQuality.None`.

### Estados

| Estado | Cuándo | Presentación |
|---|---|---|
| `UNKNOWN` | Nunca conocida | Número y recuadro con «?»; sin nombre, tipo ni sprite |
| `SEEN` | Vista y no capturada | Sprite y Pokéball gris tenue |
| `REGISTERED` | Capturada en una run anterior, no poseída ahora | Pokéball sin color a opacidad plena y etiqueta «Registrado» |
| `CAUGHT` | Poseída ahora o starter elegido | Pokéball a color |

`caughtSpecies ⊆ seenSpecies` se mantiene por construcción. Los contadores:

- **Avistados**: toda especie conocida (`SEEN + REGISTERED + CAUGHT`).
- **Capturados**: capturadas alguna vez (`CAUGHT + REGISTERED`); no bajan al
  resetear la run.

### Flujo de datos

```
assets/pokedex_catalogo.json
  → PokedexCatalogLoader.load(context)   (una vez por proceso, nunca lanza)
  → MainViewModel(pokedexLoad)
  → GameProjection → PokedexProjector.project(...)  (memoizado por historial)
  → GameUiState.dexEntries / dexSeenCount / dexCaughtCount / dexLoadFailed
  → DexScreen(state.toDexUiState(), ...)
```

`PokedexCatalog` valida en su `init`: números consecutivos desde 1, ids únicos
con formato `[a-z0-9_]+`, nombre y tipo no vacíos. Un fallo de carga muestra un
mensaje en la pantalla y **no bloquea el juego**.

### Apertura y cierre

`onOpenDex()` solo entra si el tutorial lo permite y no hay starter pendiente,
selector, encuentro, gimnasio ni capa de entrada. Al abrir se pausa el
temporizador de caminar (`walkEngine.resetActivity()`); al cerrar hace falta una
pulsación nueva para reactivarlo. El **Atrás** físico también cierra, con los
mismos guards.

### Extender la Pokédex

- Añade o corrige la entrada en `pokedex_catalogo.json` (orden = número).
- Añade el sprite `pokedex_0xxx.png` en `drawable-nodpi/` y su entrada en
  `PokedexSprites.kt`.
- `PokedexParserTest` cruza el catálogo de la dex con `SpeciesCatalog` y avisa si
  divergen en nombre, tipo o número.

---

## 9. Juego: estado y persistencia

### `GameUiState`

Es **inmutable** (`@Immutable`) y agrupa juego, navegación y derivados:

```kotlin
data class GameUiState(
    // meta permanente: playerName, coins, seenSpecies, rewardedSpecies,
    //                 caughtSpecies, medals, starterSpeciesId, starterBond
    // run: ownedSpecies, leadSpeciesId, gymClearedRun, zoneIndex, zoneClicks
    // encuentros: walkProgress, encounter, catching
    // gimnasio: gym, gymIntro
    // tutorial: tutorialPhase, tutorialMessageClosed
    // visual: trainer, trainerFrameRes
    // navegación: contentTab, isOptionsOpen, isLeadOpen, isPokedexOpen
    // derivados: zoneName, zoneId, zoneRequirement, ownedCount, clickPower,
    //            maxHp, leadName, leadType, isGymReady, gymLeaderName, gymId,
    //            gymMechanic, canAdvanceZone, hasWilds, needsStarter,
    //            leadOptions, starterOptions, dexEntries, dexSeenCount,
    //            dexCaughtCount, dexLoadFailed
)
```

- Los **tres conjuntos permanentes no son intercambiables**: `seenSpecies`
  registra conocimiento, `caughtSpecies` la historia que alimenta la Pokédex y
  `rewardedSpecies` el pago único de monedas.
- `ownedSpecies` y `gymClearedRun` son **de la run**.
- `zoneProgress` e `isLeadCandidate()` son derivaciones puras del propio estado.

### Persistencia

`GameStateStore` es el contrato; `SharedPreferencesGameStateStore` es la
implementación, en `shared_prefs/kanto_clicker.xml`. El nombre histórico se
mantiene para no invalidar partidas.

| Clave | Campo | Formato |
|---|---|---|
| `schema_version` | Versión del formato | `Int` (actual: **2**) |
| `player_name` | `playerName` | Texto o vacío |
| `coins` | `coins` | `Long` |
| `seen` | `seenSpecies` | IDs separados por comas |
| `rewarded` | `rewardedSpecies` | IDs separados por comas |
| `caught` | `caughtSpecies` | IDs separados por comas |
| `medals` | `medals` | IDs separados por comas |
| `starter` | `starterSpeciesId` | Texto o vacío |
| `starter_bond` | `starterBond` | `Int` |
| `owned` | `ownedSpecies` | IDs separados por comas |
| `lead` | `leadSpeciesId` | Texto o vacío |
| `gym_cleared_run` | `gymClearedRun` | IDs separados por comas |
| `zone_index` | `zoneIndex` | `Int` |
| `zone_clicks` | `zoneClicks` | `Long` |
| `tutorial_phase` | `tutorialPhase` | Nombre del enum |
| `tutorial_message_closed` | `tutorialMessageClosed` | `Boolean` |

Reglas de compatibilidad:

- Un guardado sin `schema_version` se acepta como versión **1**.
- Un guardado de una versión **futura** se ignora (no se reinterpreta).
- Valores negativos se normalizan a 0.
- La primera partida se detecta por la existencia de `coins`.
- `clear()` usa `commit()` (destructivo y síncrono); `save()` usa `apply()`.
- El estado transitorio (encuentro, captura, caminar, gimnasio, navegación y
  animación) **no se persiste** a propósito.

Se escribe en **cada** mutación persistente (incluido cada clic); el volumen es
mínimo y `apply()` no bloquea el hilo de UI.

---

## 10. Minijuego de captura

### Mecánica

El salvaje se mueve por un **riel vertical**; la Pokébola es la barra del
jugador. Mantener pulsado acelera la barra hacia arriba; al soltar, la gravedad
la hace caer. El medidor de progreso sube mientras la barra solapa al sprite y
baja cuando no.

- Llenar el progreso → **capturado**.
- Llegar a 0 (tras la gracia inicial) → **escapado**.
- La dificultad de la especie (`captureDifficulty`) solo cambia el movimiento del
  sprite: suavizado y cadencia de dardos.

### Constantes (`CatchingDefaults`)

| Constante | Valor | Uso |
|---|---|---|
| `BAR_SIZE` | `0.22` | Tamaño de la barra (fracción del riel) |
| `BAR_RISE_ACCEL` | `3.0` | Aceleración al mantener |
| `GRAVITY` | `-3.0` | Caída al soltar |
| `MAX_BAR_SPEED` | `1.2` | Velocidad máxima |
| `FILL_RATE` | `0.40`/s | Progreso con solape |
| `DRAIN_RATE` | `0.20`/s | Progreso perdido sin solape |
| `INITIAL_GRACE_SECONDS` | `1.0` | Margen inicial sin huida |
| `THROW_MS` + `REACTION_MS` | `900` + `200` | Vuelo y pausa antes de mover el riel |
| `SMOOTHING_EASY` / `HARD` | `1.2` / `0.3` | Suavizado por dificultad |
| `DART_EASY` / `HARD` | `0.4` / `1.6` por segundo | Dardos por dificultad |
| `DART_BOOST_SECONDS` | `0.5` | Duración del impulso tras un dardo |
| `WANDER_RANGE` | `0.35` | Vagabundeo suave |

### Bucle y cierre

- `CatchingEngine.start()` espera el lanzamiento y la reacción y luego avanza el
  estado cada **16 ms** con `dt` real (tope 0,1 s).
- `CatchingGame.step(state, held, dt, random)` es **puro**: no conoce Android ni
  el reloj, y recibe la aleatoriedad inyectada.
- `onCatchingResultContinue()` aplica el resultado:
  - **capturado**: suma la especie a `ownedSpecies`, `caughtSpecies` y
    `seenSpecies`, y paga `coinReward` si es la primera vez
    (`rewardedSpecies`);
  - **escapado**: no cambia nada.
  En ambos casos se vuelve al juego y el temporizador de caminar queda listo.

### Escena (`CatchingOverlay`)

Es un *takeover* a pantalla completa: la ruta queda congelada detrás (la escena
es transparente), no hay HUD ni menú, y la piel imita la pantalla de captura de
la GBA (riel de energía, Pokébola, medidor y cajas de diálogo). La UI solo pinta
el `CatchingState` y notifica mantener/soltar y la salida del resultado.

### Archivos

| Archivo | Rol |
|---|---|
| `model/CatchingGame.kt` | Reglas puras, constantes y configuración por dificultad |
| `viewmodel/CatchingEngine.kt` | Bucle de ticks y cierre del encuentro |
| `viewmodel/EncounterEngine.kt` | Aparición del salvaje al caminar |
| `ui/CatchingOverlay.kt` | Escena, lanzamiento, resultado y assets `fx_catch_*` |

---

## 11. Gimnasios

### Entrada

Pulsar **Gimnasio** no abre el combate directamente: se cierra una cortinilla
con las dos mitades de la Pokébola, se queda cerrada 1 segundo y se abre
descubriendo el escenario. El cambio de escena ocurre con la cortinilla cerrada,
así que nunca se ve un escenario vacío.

Después llega `GymIntroPhase`:

1. `WELCOME`: retrato del líder y discurso (Brock y Misty tienen guion propio;
   Lt. Surge usa el panel genérico).
2. `TUTORIAL`: explicación de la mecánica en **3 pasos** que se revelan solos,
   con «Repetir tutorial» y «Entendido».
3. `COUNTDOWN`: cartel de arranque; al terminar empieza el combate.

Una línea puede declarar un **segundo retrato** (`altPortraitRes`): mientras la
máquina de escribir avanza, los dos se alternan cada 140 ms (así Misty mueve la
boca). El tamaño del retrato lo limitan tres topes (`leaderPortraitSize`):
fracción del ancho, ancho máximo y fracción del alto.

### Combate

El combate es un minijuego por **fases excluyentes**:

1. **Ataque** (`GymPhase.ATTACKING`): el líder lanza su mecánica. Cada acierto
   del jugador suma postura; un fallo resta postura y HP. La postura **solo**
   cambia con aciertos del jugador (`GymCombat.addPosture`).
2. **Expuesto** (`GymPhase.EXPOSED`): al llenarse la postura, se rompe; el líder
   deja de atacar y se abre una ventana de 2,5 s. Los toques sobre el enemigo
   restan `clickPower × multiplicador de tipo`.
3. **Furia**: con menos del 25 % de vida, los ataques son más densos y rápidos.
   La primera vez que un golpe cruzaría el umbral, `gateFirstRage` lo frena y
   obliga a jugar una ronda completa de furia (`rageRoundPlayed`) antes de poder
   matar al líder. El cruce abre además el **discurso de furia**: el combate se
   detiene y se muestra la cara de furia del líder con dos líneas.

`typeDamageMultiplier(leadType, gymType)` es una función pura: 2× si es súper
eficaz, 0,5× si es poco eficaz, 1× si es neutro o no hay principal.

### Mecánicas por gimnasio

| Gimnasio | Zona | Mecánica | Recompensa | Vida |
|---|---|---|---|---|
| **Brock** | Ciudad Plateada | Lluvia de rocas (`PROJECTILES`) | 40 | 80 |
| **Misty** | Ciudad Celeste | Anillos rítmicos (`TIMING_RINGS`) | 60 | 90 |
| **Lt. Surge** | Ciudad Carmín | Cuadrícula 4×4 de Voltorb (`VOLTORB_GRID`) | 80 | 120 |

- **Brock**: 5 columnas, 2 rocas cada 650 ms, 1,8 s de caída, 1 de daño, 14
  aciertos de postura. En furia: 450 ms, 2 rocas y 1,1 s.
- **Misty**: un anillo cada 750 ms, ventana de 170 ms, viaje 950–1700 ms, 12
  aciertos, 1 de daño por fallo. En furia: 520 ms y 700–1200 ms.
- **Surge**: 16 celdas; los Voltorb se encienden cada 520 ms con una ventana de
  1500 ms (en furia 340/950). Apagar a tiempo evita el daño; tocar una celda
  apagada descarga. 16 aciertos de postura y 2,5 s de exposición.

### Victoria y derrota

- **Victoria** (`GymOutcome.WON`): el enemigo se hunde y se apaga; después llega
  la despedida (`GymVictory`), con caras por línea y la medalla apareciendo en el
  centro. Al cerrar la última, `onGymVictoryDone()` cobra medalla y monedas. Los
  gimnasios sin despedida propia se cierran al terminar el desvanecimiento.
- **Derrota** (`GymOutcome.LOST`): panel con **reintentar** (sin coste) y
  **rendirse** (abandona el intento).
- La **medalla** persiste entre runs, pero `gymClearedRun` se vacía al resetear:
  un reset obliga a repetir el combate.

### Arquitectura

- `GymCombatEngine` orquesta el bucle (`GYM_TICK_MS = 50`, primera oleada a 800
  ms, cuenta atrás de 1000 ms), la entrada, los ataques, la furia y los
  resultados.
- `GymCombat` (en `model/Combat.kt`) contiene las transiciones **puras**:
  acierto → postura, postura llena → expuesto, recuperar → atacar, impacto → HP y
  el umbral de furia.
- La vista (`GymOverlay`) **no** recibe actualizaciones por fotograma: interpola
  la caída con el reloj de frames desde `spawnedAtMs` y reparte los toques en una
  única capa `pointerInput`.

### Assets

| Asset | Uso |
|---|---|
| `rock_gym.png` / `water_gym.png` / `electric_gym.jpg` | Fondos 2:3 de los tres gimnasios |
| `rock.png`, `rock_shatter.png` | Proyectil y estallido de Brock |
| `hydro_burst.png`, `hydro_implode.png` | Acierto y fallo del anillo de Misty |
| `voltorb_capa1..9.png`, `voltorb_aura.png`, `voltorb_blast.png` | Voltorb de Surge |
| `bulbasaur_back.gif`, `charmander_back.gif`, `squirtle_back.gif` | Principales vistos de espaldas |
| `golem.gif`, `starmie.gif`, `raichu.gif` | Enemigos de Brock, Misty y Surge |

Los efectos de pixelfx (`rock_shatter`, `hydro_burst`, `hydro_implode` y los
Voltorb) se generan con la herramienta de pixelfx **fuera de este repositorio** y
se copian a `drawable-nodpi/`. `RockShatterFxTest` y `WaterRingFxTest` comprueban
que las hojas reales coinciden con sus rejillas.

### Añadir un gimnasio

1. Añade una entrada a `GymCatalog` con su `id`, tipo, vida, postura, ventana y
   parámetros de la mecánica.
2. Referencia el `id` en la zona (`gymId`).
3. Añade el fondo y los sprites; registra su composición en `GymSceneLayouts`
   (sin entrada usa `DEFAULT`).
4. Opcional: guion de entrada (`GymEntry.leaderScriptFor`), furia
   (`GymRageSpeech`) y victoria (`GymVictory`). Sin guion, se usan los textos
   genéricos.

---

## 12. Introducción y tutorial

### Introducción del Profesor Oak

Es la primera pantalla de una partida nueva (`needsStarter`). Es un guion de
líneas con:

- **Máquina de escribir** (`TYPE_CHAR_MS = 35 ms`); el toque completa la línea y
  luego avanza.
- **Widgets** por línea (`IntroWidget`): `NONE`, `NAME`, `CONFIRM_NAME`,
  `STARTER`, `CONFIRM_STARTER`.
- **Moods** de Oak (`OakMood`): `TALKING`, `ANGRY`, `DROP`, `OFFERING`, con sus
  retratos (`profesor_oak*`).
- El nombre se limita a **12 caracteres** y no puede quedar en blanco.
- Al elegir el starter, sus GIF se muestran en fila; la elección se confirma
  antes de terminar.

Al terminar, `onFinishIntro(name, starterId)` fija en **una sola mutación**:
nombre, starter, vínculo 0 y principal; marca el starter como **visto y
capturado** en la Pokédex y dispara `OAK_FINISHED` en el tutorial. Nada se
persiste hasta terminar la intro.

### Tutorial guiado

`model/Tutorial.kt` es una máquina de estados **pura**:

| Fase | Qué espera |
|---|---|
| `PENDING` | Oak aún no terminó (o partida heredada) |
| `OFFER` | Respuesta a «¿Quieres un tutorial?» |
| `CONFIRM` | «Empezar tutorial» / «Omitir» |
| `DEPLOY_POKEBALL` | Desplegar la Pokébola |
| `OPEN_MAP` → `RETURN_MAP` | Abrir el mapa y volver |
| `OPEN_POKEDEX` → `RETURN_POKEDEX` | Abrir la Pokédex y volver |
| `FAREWELL` | Cerrar la despedida de Oak |
| `EXP_WAIT` → `EXP_NOTICE` → `ADVANCE_WAIT` | Ver la barra llena y avanzar |
| `SKIPPED` / `COMPLETED` | Finales absorbentes |

Reglas:

- Las transiciones solo ocurren con **acciones reales** (`TutorialAction`); una
  acción fuera de fase es un **no-op**.
- Cada fase declara qué interacciones de juego permite (`TutorialInteraction`).
  Mientras hay un mensaje visible, **nada del juego responde**.
- Los guards viven en `MainViewModel` (`tutorialAllows`) y cubren toda la
  operación: bloqueado, el método devuelve el estado intacto.
- `derive()` normaliza dos transiciones dependientes del juego: `PENDING` +
  starter → `OFFER`, y `EXP_WAIT` + barra llena → `EXP_NOTICE`.
- `tutorialPhase` se persiste por **nombre del enum** (`tutorial_phase`);
  renombrar una fase rompe guardados salvo que se añada un mapeo. Un guardado sin
  la clave resuelve `COMPLETED` si ya hay starter y `PENDING` si no.
- Limitación conocida: al reabrir a mitad de un diálogo paginado se recupera la
  fase y si el mensaje estaba abierto, pero la página vuelve al inicio.

### Archivos

| Archivo | Rol |
|---|---|
| `ui/Intro.kt` | Guion, retratos, nombre, starter y GIF con `ImageDecoder` |
| `model/Tutorial.kt` | Fases, acciones, transiciones y predicados |
| `ui/TutorialOverlay.kt` | Diálogos paginados, preguntas y scrim |
| `ui/AppRoot.kt` | Capa del tutorial y bloqueo de menú/Atrás |
| `viewmodel/MainViewModel.kt` | Guards sobre los eventos reales |

---

## 13. Tema retro y componentes compartidos

- **`RetroTheme.kt`**: `RetroColors` (paleta del juego), formas cortadas
  (`PanelShape`, `PlateShape`, `CellShape`, `ButtonShape`), `RetroTypeColors` y
  `RetroTypography` (aplicada en `MainActivity` con
  `MaterialTheme(typography = RetroTypography)`), además de `PixelImage`.
- **`GameUiComponents.kt`**: `GameColors`, `Modifier.bouncyClickable`,
  `CoinDisc` y `RetroButton`.
- **`PixelFont.kt`**: fuente 5×7 portada de pixelfx. Dibuja los glifos con
  rectángulos (no con `Text`) para el cartel de combo; conserva el contorno, los
  tramos de color, el ensanche de entrada y el brillo.
- **`PixelArt.kt`**: `opaqueBounds()` recorta el margen transparente de los
  sprites para normalizarlos en un recuadro fijo.
- **`ModalBlocking.kt`**: `Modifier.modalBlocking()` consume todos los eventos de
  puntero de una capa modal sin bloquear sus propios controles.
- **`Layout.kt`**: `PercentageLayout` y sus anclajes (ver §6).

### Regla de capas modales

Todo overlay modal nuevo debe:

1. aplicar `Modifier.modalBlocking()` en su nodo raíz;
2. hacer que `AppRoot` **no componga** cualquier superficie de juego cuyos
   detectores ignoren el consumo (la Pokébola es el caso conocido);
3. cubrir el botón **Atrás** si la capa no debe abandonarse con él.

---

## 14. Recursos

### `drawable/`

| Grupo | Archivos |
|---|---|
| Entrenador | `front0-3.png`, `right0-3.png`, `left0-3.png`, `back0-3.png` |
| Oak | `profesor_oak.png`, `profesor_oak_talk.png`, `profesor_oak_angry*.png`, `profesor_oak_drop.png`, `profesor_oak_offering.png` |
| Líderes | `brock*.png`, `misty*.png` |
| Medallas | `medalla_roca.png`, `medalla_cascada.png` |
| Starters | `bulbasaur.gif`, `charmander.gif`, `squirtle.gif` y sus `*_back.gif` |
| Enemigos | `golem.gif`, `starmie.gif`, `raichu.gif` |
| Combate | `rock.png`, `pokeball_menu.png`, `pokeball_splash_*.png` |
| Launcher | `ic_launcher_background.xml`, `ic_launcher_foreground.xml` |

### `drawable-nodpi/`

| Grupo | Archivos |
|---|---|
| Fondos de ruta | `background_pallet_town.png`, `background_bosque_verde.png`, `background_ciudad_verde.png`, `background_ciudad_plateada.png`, `background_ruta3.png`, `background_mt_moon.png`, `background_ciudad_celeste.png`, `background_ciudad_carmin.png`, `background_ruta.png` |
| Gimnasios | `rock_gym.png`, `water_gym.png`, `electric_gym.jpg` |
| Efectos | `rock_shatter.png`, `hydro_burst.png`, `hydro_implode.png`, `fx_catch_success.png`, `fx_catch_break.png`, `voltorb_*.png` |
| Mapa | `mapa_kanto.png`, `mapa_cabecera.webp`, `mapa_marco.9.png`, `mapa_modulo_inferior.webp`, `mapa_base_menu.webp` |
| Pokédex | `pokedex_cabecera.webp`, `pokedex_marco.9.png`, `pokedex_base.webp`, `pokedex_0001..0151.png` |

Nombres en minúsculas obligatorio (a-z, 0-9, `_`). Los assets que no deben
reescalarse por densidad van en `drawable-nodpi/`.

### Cómo añadir una imagen

1. Copia el archivo con nombre en minúsculas y solo `[a-z0-9_]`.
2. Si es pixel art que no debe reescalarse, usa `drawable-nodpi/`.
3. Añade su `contentDescription` en `strings.xml` si aporta información.
4. Declara tamaño y posición en el `object` de configuración de esa pantalla.

### Strings (`res/values/strings.xml`)

| Grupo | Uso |
|---|---|
| `app_*`, `poke_*`, `trainer_*`, `nav_*` | Nombre y navegación |
| `hud_*` | Zona, monedas y barra EXP |
| `action_*` | Avanzar, gimnasio, reset y wipe |
| `catch_*`, `catching_*` | Encuentro y minijuego de captura |
| `lead_*` | Selector de principal |
| `gym_*` | Entrada, combate, furia y victoria |
| `dex_*` | Pokédex, estados y contadores |
| `map_*` | Mapa, carcasa y editor |
| `options_*`, `wipe_*` | Hoja de opciones y borrado |
| `intro_*` | Guion del Profesor Oak |
| `tutorial_*` | Tutorial guiado |

### Temas

`Theme.Pokeclic` hereda de `Theme.MaterialComponents.DayNight.NoActionBar`:

| Atributo | Valor | Motivo |
|---|---|---|
| `windowFullscreen` | `true` | Sin barra de estado |
| `statusBarColor` / `navigationBarColor` | `transparent` | Edge-to-edge |
| `windowLayoutInDisplayCutoutMode` | `shortEdges` | Contenido hasta los bordes con notch |

La app es **solo vertical**: `screenOrientation="portrait"` y
`appCategory="game"` (necesario para mantener el bloqueo en pantallas ≥ 600 dp
desde Android 16).

---

## 15. Dependencias y build

Definidas en `gradle/libs.versions.toml`.

### Plugins

| Plugin | Versión |
|---|---|
| `com.android.application` | 9.1.1 |
| `org.jetbrains.kotlin.plugin.compose` | 2.2.10 |

> AGP 9 trae el soporte de Kotlin integrado. **No** apliques
> `org.jetbrains.kotlin.android`: entra en conflicto con la versión del
> classpath. `jvmTarget` se configura en `app/build.gradle.kts`.

### Librerías

| Grupo | Uso |
|---|---|
| `androidx.appcompat`, `com.google.android.material` | `AppCompatActivity` y el tema |
| `androidx.core-ktx` | Utilidades base |
| `androidx.lifecycle.viewmodel-ktx` | `ViewModel`, `viewModelScope`, `viewModelFactory` |
| `androidx.lifecycle.runtime-compose` | `collectAsStateWithLifecycle()` |
| `androidx.activity-compose` | `setContent {}` y `by viewModels()` |
| `androidx.compose.ui`, `ui-graphics`, `ui-tooling-preview` | UI, dibujo y previews |
| `androidx.compose.material3` | Componentes Material 3 |
| `androidx.compose.material-icons-extended` | Iconos del menú y la hoja |
| `androidx.compose.ui.tooling` (`debugImplementation`) | Inspector y previews |
| `junit`, `kotlinx-coroutines-test` | Tests unitarios |
| `org.json:json` (`testImplementation`) | `org.json` en la JVM de los tests |

Compose se coordina con el BOM **2024.10.01**.

### Build release

```kotlin
optimization {
    enable = true
    packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
}
```

`material-icons-extended` aporta muchos iconos; la optimización permite que R8
elimine los que no se usan.

---

## 16. Ciclos de vida y flujos

### Arranque

1. `MainActivity.onCreate()` crea `MainViewModel` y `MapViewModel` con
   `viewModelFactory { initializer { ... } }`.
2. Carga el catálogo de la Pokédex **una vez por proceso**
   (`PokedexCatalogLoader.load`) y lo inyecta en el ViewModel.
3. `setContent { MaterialTheme(typography = RetroTypography) { ... } }` recoge los
   dos `StateFlow` y pasa estado y lambdas a `AppRoot`.
4. `AppRoot` compone la pantalla y la `PokeBallSplash` se dibuja encima hasta que
   termina.
5. El `MapViewModel` no carga nada hasta que la pestaña Mapa aparece
   (`onScreenShown`).

### Toque

```
MenuPokeBall (toque expandida)
  → AppRoot.onPokeBallGameClick
      → pokePulses++                               // fondo de ruta
      → routeBackground.motion.directionForStep()  // dirección del entrenador
      → MainViewModel.onPokeBallTouch(direction)
          → guard del tutorial
          → TrainerAnimator.animate(...)
          → EncounterEngine.noteActivity(...)
          → GameProjection.project(...) → StateFlow
```

### Captura

```
EncounterEngine (3 s de caminata) → Encounter + CatchingState
  → CatchingOverlay (mantener/soltar)
  → CatchingEngine (tick 16 ms)
  → CatchingGame.step (puro)
  → panel de resultado → onCatchingResultContinue()
  → owned/caught/seen/coins + sin encuentro
```

### Rotación y destrucción

- Los dos ViewModels sobreviven a la rotación: no se pierden progreso, pantalla
  ni capas abiertas.
- Los Jobs de caminar, captura, gimnasio y animación mueren con
  `viewModelScope`; no hace falta `onCleared()`.
- Solo hay una composición, la de `setContent`.

---

## 17. Guía rápida

### Mover un elemento de cromo

`ui/MainScreen.kt` → `object MainLayout` (`HUD_PADDING`). Para anclar un elemento
suelto dentro de la zona de contenido usa `Modifier.anchor(...)`.

### Mover o redimensionar la Pokébola

`ui/PokemonBottomMenu.kt` → `object BottomMenuDefaults`:

| Quiero… | Cambio |
|---|---|
| Tamaño en reposo / expandida | `POKEBALL_SIZE` / `POKEBALL_EXPANDED_SIZE` |
| Sitio en reposo / expandida | `POKEBALL_BOTTOM_PADDING` + `POKEBALL_OFFSET_X` / `POKEBALL_EXPANDED_BOTTOM` + `POKEBALL_EXPANDED_OFFSET_X` |
| Sombra al expandir | `EXPANDED_SHADOW` |
| Hueco fijo o más ancho | `NOTCH_EXPANDED_WIDTH` = o > `NOTCH_WIDTH` |
| Gesto más o menos sensible | `DRAG_SENSITIVITY` y `SNAP_THRESHOLD` |
| Respuesta visual al toque | `TAP_PUNCH_SCALE` (`1f` lo desactiva) |

`CONTAINER_HEIGHT` **no se edita**: se deriva de `BAR_HEIGHT` y de cuánto
sobresale la Pokébola.

### Mover toda la zona de contenido

`ui/Layout.kt` → `object ContentZoneDefaults` (`TOP`, `BOTTOM`, `MAX_TOP`,
`MIN_BOTTOM`).

### Mover el entrenador

`ui/WorldFrame.kt` → `WorldDefaults`:

| Quiero… | Cambio |
|---|---|
| Subir o bajar | `TRAINER_CENTER_ART_Y` (px de arte) |
| Cambiar su tamaño | `TRAINER_BOX_ART_SIZE` (px de arte) |
| Cambiar fotogramas | `SpriteRepository` en `Catalogs.kt` |

### Añadir una animación de zona

1. Deja el PNG en `drawable-nodpi/` (idealmente un ancho de pantalla y continuo
   en ambos ejes).
2. Añade la entrada a `RouteBackgrounds.byZone` con el `Zone.id`, el arte y una
   lista de `RouteLeg`. Para un fondo horizontal, `axis = HORIZONTAL`.
3. Ajusta `startAnchor`, `startOffset`, `seamCutRow` y `artScale` si hace falta.

No hay que tocar la pantalla, el ViewModel ni el sprite.

### Previsualizar como en el dispositivo

- **Dispositivo**: `device = "spec:width=411dp,height=914dp,dpi=420"` y
  `showSystemUi = true`.
- **Con bottom menu**: `MainScreenStage` compone el menú real.
- **Estado realista**: usa `previewGameState(...)`, no un `GameUiState()` vacío.
- **Montaje completo**: `AppRootGamePreview`, `AppRootGymPreview`,
  `AppRootGymIntroPreview`, `AppRootIntroPreview`, `AppRootCatchingPreview`,
  `AppRootDexPreview`, `AppRootTutorialOfferPreview` y
  `AppRootTutorialDeployPreview`.
- Los previews **no** reproducen los insets reales ni siempre el
  `shadowElevation` de `graphicsLayer`.

---

## 18. Extensibilidad

### Añadir una zona jugable

1. Añade la entrada a `ZoneCatalog.zones` (el orden es la posición).
2. Define `wildSpeciesIds` (vacío = ciudad sin fauna) y, si toca, `gymId`.
3. Opcional: arte y trayectoria en `RouteBackgrounds.byZone`.
4. Si la zona tiene gimnasio, añádelo antes a `GymCatalog`.
5. `CatalogTest` bloquea el orden y exige que cada zona pida más clics que la
   anterior; `RouteBackgroundsTest` valida el arte y los tramos.

### Añadir una especie

- Añade la entrada a `SpeciesCatalog.species` (id estable, nombre, tipo,
  dificultad, recompensa y número de dex).
- La Pokédex se alimenta de `pokedex_catalogo.json`: añade o corrige ahí la
  entrada y su sprite.
- Para que aparezca, inclúyela en el `wildSpeciesIds` de una zona.

### Añadir una pantalla o pestaña

1. Crea `ui/NuevaScreen.kt` sin estado propio.
2. Añade el destino a `AppTab` (**solo hay Mapa y Home**; el menú tiene tres
   huecos fijos, así que una pestaña nueva exige reordenarlo).
3. Añade la rama en el `when` de `AppRoot`.
4. Conecta sus lambdas desde `MainActivity`. Si necesita estado, resuélvelo en el
   ViewModel correspondiente.

### Añadir una hoja modal

1. Crea el composable con `ModalBottomSheet`, como `OptionsSheet`.
2. Añade un flag a `GameUiState` y sus `onOpen...` / `onClose...` en
   `MainViewModel`.
3. Compónla en `AppRoot` y oculta el menú si sus detectores ignoran el consumo.
4. Añade la fila que la abre.

### Añadir una región al mapa

1. Añade la ubicación a `kanto_locations.json` (id numérico estable) o usa una ya
   existente.
2. Dibuja su geometría en `kanto_regions.json` y enlázala por `catalogId`.
3. Para hacerlo cómodo, usa el editor visual en debug (botón de llave).
4. `MapRegionsTest` y `MapRegionsJsonTest` validan geometrías y referencias.

### Añadir un paso al tutorial

1. Añade la fase y su acción a `model/Tutorial.kt` y su transición en `advance()`.
2. Declara la interacción en `allowedInteractions` y el momento del mensaje.
3. Añade el texto en `strings.xml` (`tutorial_*`) y mapea la fase en
   `tutorialMessageRes` (`ui/TutorialOverlay.kt`).
4. Cablea la detección en `MainViewModel`/`AppRoot` si el paso usa una
   interacción nueva.

**El nombre del enum es contrato de guardado**: renombrar una fase rompe los
guardados existentes salvo que se añada un mapeo.

### Añadir una dirección de animación

1. Añade el valor a `enum class Direction`.
2. Añade su array de frames en `DefaultSpriteRepository`.
3. Ajusta `directionForStep`/`RouteBackgroundMotion` si debe salir de un tramo.
4. Sube los PNG a `res/drawable/`.

### Cambiar ritmos

| Ritmo | Dónde |
|---|---|
| Reset del entrenador | `TrainerAnimator.FRAME_RESET_DELAY_MS` |
| Caminata y encuentros | `EncounterEngine.WALK_*` y `ENCOUNTER_SECONDS` |
| Captura | `CatchingDefaults` |
| Combate de gimnasio | `GymCombatEngine.GYM_TICK_MS`, `GymCatalog` |
| Animación de la Pokébola | `BottomMenuDefaults` |
| Splash | `PokeBallSplash.SPLASH_HOLD_MS` / `SPLASH_SLIDE_MS` |

---

## 19. Depuración

| Problema | Causa probable | Solución |
|---|---|---|
| Los elementos se salen de la pantalla | `bias` fuera del rango útil | Revisar el sesgo en el `object` de esa pantalla |
| Un elemento se solapa con otro | Sesgos cercanos | Separarlos |
| Solo se mueve en vertical | Se usó `anchorVertically` | Usar `anchor(verticalBias, horizontalBias)` |
| La Pokéball no responde | El `pointerInput` no llega al composable | Comprobar que no hay una capa encima |
| El arrastre va a tirones | Se lanza una corrutina por evento | Escribir en `mutableFloatStateOf` y animar solo al soltar |
| El toque no dispara al soltar | Se consumió antes del `touchSlop` | Consumir **solo** cuando el gesto ya es arrastre |
| Un cambio de pestaña reinicia el progreso | Estado reiniciado en la UI | El progreso vive en `MainViewModel` |
| Crash al crear el ViewModel | Constructor con parámetros sin `Factory` | `viewModelFactory { initializer { ... } }` |
| Error de compilación en un layout propio | Firmas de Compose UI 1.9 | `MeasureScope.measure(List<Measurable>, …)` y `Density.modifyParentData(…)` |
| La barra blanca queda bajo la barra de gestos | Falta el inset inferior | `navigationBarsPadding()` en el menú |
| El mapa tapa el menú | El contenido ignora el hueco reservado | `bottomReserved` en `MapScreen` |
| La Pokédex muestra error | Fallo de asset o JSON | Logcat con la etiqueta `PokedexCatalog` |
| El mapa no muestra zonas | Fallo de assets o referencias rotas | Logcat con la etiqueta `KantoMap` |

### Depuración visual

- `@Preview` en `MainScreen`, `MapScreen`, `DexScreen`, `CatchingOverlay`,
  `GymIntro`, `PokemonBottomMenu` (5 variantes) y los previews completos de
  `AppRoot`.
- El fondo verde de los previews del menú es **a propósito**: la hendidura deja
  ver lo que hay detrás.
- La variante *Hendidura (silueta)* dibuja solo la forma: sirve para ajustar
  `NOTCH_WIDTH`, `NOTCH_DEPTH` y `POKEBALL_BOTTOM_PADDING`.
- Para ver el resultado real, usa Layout Inspector o un dispositivo.

### Herramientas de desarrollo (solo debug)

- **Editor visual del mapa**: botón de llave en la esquina inferior izquierda de
  la pestaña Mapa.
- **Saltos de zona**: filas de la hoja de opciones que dejan la barra llena en
  Ciudad Plateada, Ciudad Celeste o Ciudad Carmín.
- **Borrar partida**: fila de la hoja con confirmación (existe también en
  release; es provisional).
- El editor y los saltos de zona se componen solo si `Context.isDebugBuild()`; en
  release no existen.

---

## 20. Comandos útiles

```bash
# Compilar
./gradlew assembleDebug

# Instalar en dispositivo o emulador
./gradlew installDebug

# Tests unitarios
./gradlew testDebugUnitTest

# Limpiar
./gradlew clean

# Logs del juego
adb logcat | Select-String "pokeclic"

# Lanzar la Activity
adb shell am start -n com.dsrs.pokeclic/.MainActivity

# Árbol de dependencias resueltas
./gradlew :app:dependencies --configuration debugCompileClasspath
```

En Windows PowerShell los comandos encadenados usan `;`, no `&&`.

---

## 21. Compatibilidad y migración

### Estado actual

- **Pokédex**: ya es dato (`assets/pokedex_catalogo.json` + sprites). La fuente
  de verdad del juego sigue siendo `SpeciesCatalog`.
- **Mapa**: ya es dato (`kanto_locations.json` + `kanto_regions.json`).
- **Contenido jugable** (especies, zonas, gimnasios): sigue en `Catalogs.kt`.
- **Guardado**: versionado (`schema_version = 2`), sin migraciones complejas.
- **Paquete**: `com.dsrs.pokeclic`; el archivo de preferencias conserva el nombre
  histórico `kanto_clicker`.

### Limitaciones del guardado

- `ownedSpecies` es un `Set<String>`: no representa individuos, niveles,
  experiencia, movimientos ni vínculo por Pokémon.
- Los IDs son la única relación con el contenido: un ID que desaparezca deja el
  dato huérfano.
- El estado transitorio no se persiste a propósito.

### Plan pendiente

La migración a contenido versionado (`manifest.json`, `species.json`,
`encounters.json`, `gyms.json`…) y a instancias (`PokemonInstance`) sigue
planificada, no implementada. El contrato completo está en
[`docs/plan-migracion-contenido.md`](docs/plan-migracion-contenido.md); el diseño
de referencia, en
`docs/superpowers/specs/2026-10-15-kanto-clicker-design.md`, y el plan de la
primera rebanada, en `docs/superpowers/plans/2026-10-15-vertical-slice.md`.

Las apariciones de las ubicaciones aún no jugables viven en
[`docs/apariciones-planificadas.md`](docs/apariciones-planificadas.md); las
listas activas están solo en `ZoneCatalog`.
