# Documentación — Pokeclic

> Documentación técnica del proyecto. Refleja el estado actual del código y el
> plan de migración pendiente. La app se llama **Pokeclic** (el paquete y el
> `applicationId` siguen siendo `com.example.myapplication` para no romper
> instalaciones). Todo el estado vive en un único `StateFlow` y la UI es plana en
> `ui/`. El catálogo de contenido sigue en código: la migración a datos
> versionados se detalla en §21.

---

## 1. Visión general

### Qué hace la app

Una app Android (nombre visible **Pokeclic**) cuya pantalla principal muestra un
Trainer, un HUD de zona y una Pokéball. La **Pokéball del bottom menu** es a la vez
el acceso a esa pantalla y el elemento interactivo: arrastrándola hacia arriba
crece y se convierte en la Pokéball de juego. Cada toque en ella:

1. Suma potencia de clic al progreso de la zona.
2. Traduce la posición del toque a una dirección (abajo, derecha, izquierda, arriba).
3. Reproduce la animación del Trainer correspondiente a esa dirección.

Si repites la misma dirección, la animación avanza de frame. Si dejas de tocar
1 segundo, vuelve al frame inicial.

La lógica de juego vive en `MainViewModel`, que expone un único `StateFlow` con el
estado de juego **y** el de navegación. El bucle de producción pasiva (CPS) se
mantiene como gancho idle: hoy vale 0, pero está listo para conectarse sin tocar
la UI.

### Stack técnico

| Elemento | Valor |
|---|---|
| Lenguaje | Kotlin (soporte integrado en AGP 9, sin plugin `kotlin.android` propio) |
| Android Gradle Plugin | 9.4.1 |
| `compileSdk` / `targetSdk` | 37 |
| `minSdk` | **29** (Android 10) |
| Arquitectura | **MVVM** (Model / View / ViewModel) |
| UI | **Jetpack Compose** + Material 3 |
| Compose BOM | 2024.10.01 (`ui` resuelve a 1.9.0) |
| Compose Compiler | plugin `org.jetbrains.kotlin.plugin.compose` 2.0.0 |
| Pantallas | **Composables puros** |
| Contenedor | 1 sola `Activity` (`MainActivity`) + `Scaffold` de Compose |
| Navegación | `MainViewModel` (estado único: `AppTab`, opciones y selector de principal) |
| Estado | `StateFlow` (sin `LiveData`), consumido con `collectAsStateWithLifecycle()` |
| Progreso y monedas | `zoneClicks`/`coins` como `Long`, con bucle pasivo de CPS (`viewModelScope`) |
| Posicionamiento | Componente propio `PercentageLayout` (guías + sesgo porcentual) |
| Bottom menu | Componente propio `PokemonBottomMenu` (barra con hendidura + Pokéball) |
| Tema | `Theme.Pokeclic`, pantalla completa (edge-to-edge) |
| Persistencia | `GameStateStore` (interfaz) + `SharedPreferencesGameStateStore` |
| Recursos gráficos | 16 frames del trainer, Pokéballs, hierba, rocas, gimnasio, Oak, GIFs de starters y sprites de combate |

---

## 2. Arquitectura MVVM

### Capas y responsabilidades

```
┌─────────────────────────────────────────────────────┐
│  VIEW  (ui/, plano)                                 │
│  Composables SIN estado. Reciben un estado         │
│  inmutable y devuelven eventos por lambdas.         │
│  Cero lógica de negocio y cero ViewModel.           │
├─────────────────────────────────────────────────────┤
│  VIEWMODEL  (viewmodel/)                            │
│  MainViewModel: juego + navegación + combate.       │
│  Expone un único StateFlow<GameUiState>.            │
├─────────────────────────────────────────────────────┤
│  MODEL  (model/)                                    │
│  GameData (entidades), Combat (reglas),             │
│  Catalogs (contenido + sprites). Sin Android.       │
├─────────────────────────────────────────────────────┤
│  DATA  (data/)                                      │
│  GameStateStore: contrato + implementación con      │
│  SharedPreferences. Es el borde Android.            │
└─────────────────────────────────────────────────────┘
```

### Reglas de dependencia

- **View → ViewModel**: la `MainActivity` cablea el `StateFlow` y los métodos del
  ViewModel a las lambdas de los composables. Los composables solo renderizan
  estado y emiten eventos (`onPokeBallTouch`, `onTallGrassToggle`, ...); no ven el
  ViewModel.
- **ViewModel → Model/Data**: usa los tipos y funciones puras de `model/` y el
  contrato `GameStateStore` de `data/`. Nunca importa `R`, así que la lógica es
  testeable en la JVM sin instrumentación.
- **Model → nada**: no depende de Android ni de Compose. `Catalogs.kt` sí conoce
  `R.drawable` en su implementación de sprites, pero la interfaz que ve el
  ViewModel (`SpriteRepository`) no.
- **La UI nunca calcula nada**: el ViewModel entrega `trainerFrameRes`, las
  opciones `leadOptions`/`starterOptions`, los nombres y las fracciones de barra
  ya resueltos.

### Decisiones de diseño y motivo

| Decisión | Motivo |
|---|---|
| **Composables sin estado (`state` + lambdas)** | `MainScreen(state = ..., onTallGrassToggle = ...)` no conoce el ViewModel. Se puede previsualizar y testear con estado de mentira, y no se acopla UI con negocio. |
| **`MainViewModel` con scope de Activity** | La Pokéball del bottom menu vive en `AppRoot` y comparte estado con `MainScreen`; ambos leen el mismo `StateFlow`. |
| **Un único ViewModel y un único `StateFlow`** | Juego, navegación y overlays comparten fuente de verdad. Elimina la sincronización entre ViewModels y los estados imposibles (dos pantallas abiertas a la vez). |
| **`StateFlow` en vez de `LiveData`** | Es lo idiomático con Compose; se consume con `collectAsStateWithLifecycle()`. |
| **`GameUiState` en su propio archivo (`viewmodel/GameUiState.kt`)** | La View importa solo el tipo de estado para los `@Preview`, nunca el ViewModel. |
| **Actualizaciones con `MutableStateFlow.update {}`** | Lecturas/escrituras atómicas: evita condiciones de carrera entre el bucle de CPS y los toques. |
| **`zoneClicks` y `coins`: `Long`** | En un clicker los contadores crecen sin techo; `Int` desbordaría a los ~2.100 millones. |
| **Modelo consolidado en 3 archivos** | `GameData`/`Combat`/`Catalogs` agrupan tipos pequeños y estables; menos archivos de una sola clase, misma separación de responsabilidades. |
| **`SpriteRepository` inyectado por constructor** | El ViewModel no depende de recursos de Android; la dependencia se declara en el borde (Activity). |
| **`viewModelScope` para reset, combate y CPS** | Sustituye a `Handler`/`Looper`. Los Jobs se cancelan solos al destruir el ViewModel (sin `onCleared`). |
| **Compose puro** | Las pantallas son composables; no hay layouts XML ni ViewModels por pantalla. |

---

## 3. Estructura de archivos

```
Pokeclic/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/myapplication/
│       │   │   ├── MainActivity.kt              # Host único: setContent, splash y cableado del único MainViewModel
│       │   │   ├── data/
│       │   │   │   └── GameStateStore.kt        # Interfaz de persistencia + SharedPreferencesGameStateStore
│       │   │   ├── model/                       # ── MODEL (sin Android) ──
│       │   │   │   ├── GameData.kt              # AppTab, Direction, TrainerState, Species, Zone, Encounter, opciones, snapshot
│       │   │   │   ├── Combat.kt                # Tipos, Gym, fases, proyectiles, GymState y reglas puras
│       │   │   │   └── Catalogs.kt              # SpeciesCatalog, ZoneCatalog, GymCatalog y SpriteRepository
│       │   │   ├── viewmodel/                   # ── VIEWMODEL ──
│       │   │   │   ├── GameUiState.kt           # Estado único (juego + navegación + combate)
│       │   │   │   └── MainViewModel.kt         # Toda la lógica: clics, zonas, captura, gimnasio, reset y CPS
│       │   │   └── ui/                          # ── VIEW (plano, 8 archivos) ──
│       │   │       ├── AppRoot.kt               # Shell: Scaffold, pestañas, overlays y previews
│       │   │       ├── MainScreen.kt            # Pantalla principal + GameHud
│       │   │       ├── PokemonBottomMenu.kt     # Barra, Pokéball, hendidura y controles
│       │   │       ├── GymOverlay.kt            # Escena del gimnasio, HUD y sprites del combate
│       │   │       ├── Overlays.kt              # Captura, selector de principal, opciones y mapa
│       │   │       ├── Intro.kt                 # Introducción del Profesor Oak + GIF animado
│       │   │       ├── Layout.kt                # PercentageLayout (guías + sesgo porcentual)
│       │   │       └── PokeBallSplash.kt        # Cortinilla de arranque
│       │   └── res/
│       │       ├── values/
│       │       │   ├── strings.xml
│       │       │   ├── colors.xml
│       │       │   └── themes.xml               # Theme.Pokeclic
│       │       ├── values-night/themes.xml
│       │       └── drawable/                    # Pokéballs, hierba, frames del trainer, rocas y gimnasio
│       └── test/                                # Tests unitarios JVM (ModelTest)
├── build.gradle.kts
├── settings.gradle.kts                          # rootProject.name = "Pokeclic"
├── gradle/libs.versions.toml
└── gradle.properties
```

### Organización actual

- **`model/`**: `GameData.kt`, `Combat.kt` y `Catalogs.kt`. Los dos primeros no
  dependen de Android; `Catalogs.kt` contiene el contenido provisional y la única
  implementación que conoce `R.drawable`.
- **`data/`**: contrato `GameStateStore` y su implementación con
  `SharedPreferences`.
- **`ui/`**: 8 archivos planos; cada uno agrupa una pantalla o tema visual.
- **Tests**: `src/test/` contiene `ModelTest`, ejecutable en la JVM. No hay tests
  de instrumentación.

---

## 4. Navegación y bottom menu

### Composición

`AppRoot` es el shell de la aplicación. Es un composable **sin estado**: recibe
`state: GameUiState` y reenvía cada interacción como una lambda.

```
Box
├── fondo del gimnasio          (solo si state.gym != null)
├── Scaffold
│   ├── bottomBar: PokemonBottomMenu   (se oculta durante el combate)
│   └── content: Box
│       ├── when (state.contentTab)
│       │   ├── AppTab.Home → MainScreen(state, …)
│       │   └── AppTab.Map  → MapScreen()
│       ├── GymOverlay / CaptureOverlay (capas de contenido)
│       └── (el contenido y el menú se omiten durante el combate)
├── IntroOverlay                (si state.needsStarter)
├── LeadOverlay                 (si state.isLeadOpen)
└── OptionsSheet                (si state.isOptionsOpen)
```

El menú está fuera del contenido y se mantiene visible en las pestañas normales,
pero **desaparece durante el combate de gimnasio**, que ocupa toda la pantalla.
El cambio de pantalla es un `when` sobre el estado: cambiar de pestaña solo
actualiza `GameUiState.contentTab`, y el progreso no se pierde porque vive en el
`MainViewModel` con scope de Activity.

### Anatomía del menú

```
┌────────────────────────────────────────────────┐
│                                                │
│                  contenido                     │
│                                                │
│                                    (Pokéball)  │  ← flota al expandirla
│                                                │
├────────────────────────────────────────────────┤
│   Mapa              ▄▄▄▄▄▄▄▄          Pokédex   │  ← barra con hendidura
└────────────────────────────────────────────────┘
```

| Parte | Componente | Notas |
|---|---|---|
| Barra | `MenuBar` | `Surface` blanco con `rememberBottomMenuNotchShape()` y borde de 1 dp. |
| Hendidura | `BottomMenuNotchShape` | `GenericShape` con dos `cubicTo`; la Pokéball se sienta en ella. |
| Botón lateral | `MenuIconItem` | Icono en círculo + etiqueta; se tiñe con el color primario al seleccionarse. |
| Pokéball | `MenuPokeBall` | `pokeball_menu.png` + `graphicsLayer` para subir y crecer. |

Los iconos son **Material Icons de relleno** como placeholder y viven en el
`private object MenuIcons` de `PokemonBottomMenu.kt`.

### Ajustes (`object BottomMenuDefaults`)

Están fuera del composable para poder iterar sobre el aspecto sin tocar la lógica
del gesto:

| Constante | Valor | Para qué |
|---|---|---|
| `BAR_HEIGHT` | `96.dp` | Alto de la barra blanca. |
| `POKEBALL_SIZE` | `100.dp` | Diámetro de la Pokéball **en reposo**. |
| `POKEBALL_BOTTOM_PADDING` | `45.dp` | Posición vertical **en reposo**: borde inferior de la bola al de la barra. |
| `POKEBALL_OFFSET_X` | `0.dp` | Posición horizontal **en reposo**. |
| `POKEBALL_EXPANDED_SIZE` | `280.dp` | Diámetro de la Pokéball **expandida**. |
| `POKEBALL_EXPANDED_BOTTOM` | `55.dp` | Posición vertical **expandida**: borde inferior de la bola al de la barra. |
| `POKEBALL_EXPANDED_OFFSET_X` | `0.dp` | Posición horizontal **expandida**. |
| `EXPANDED_SHADOW` | `0.dp` | Sombra de la Pokéball **solo al expandirse**. |
| `NOTCH_WIDTH` | `75.dp` | Semiancho de la hendidura **en reposo**. |
| `NOTCH_EXPANDED_WIDTH` | `140.dp` | Semiancho de la hendidura **expandida**: se abre a la vez que la Pokéball. |
| `NOTCH_DEPTH` | `60.dp` | Fondo de la hendidura, desde el borde superior de la barra. Constante. |
| `DRAG_SENSITIVITY` | `110.dp` | Recorrido de arrastre para ir de 0 a 1. |
| `SNAP_THRESHOLD` | `0.20f` | Umbral para quedar arriba / volver abajo. |
| `TAP_PUNCH_SCALE` | `0.95f` | Escala del "golpe" visual de la Pokéball al tocarla (`1f` = sin efecto). |
| `CONTAINER_HEIGHT` | *derivado* | Alto que reserva el `Scaffold`. **No se edita.** |

### Los dos estados se editan por separado

Tamaño y posición de la Pokéball son **absolutos en dp**, no relativos entre sí:

```kotlin
POKEBALL_SIZE            = 100.dp   // en reposo
POKEBALL_BOTTOM_PADDING  = 45.dp
POKEBALL_EXPANDED_SIZE   = 280.dp   // expandida, sea cual sea el tamaño en reposo
POKEBALL_EXPANDED_BOTTOM = 55.dp
```

Se puede cambiar cualquiera de los cuatro sin que los demás se muevan. También hay
desplazamiento horizontal (`POKEBALL_OFFSET_X` y `POKEBALL_EXPANDED_OFFSET_X`) por
si quieres la bola descentrada.

> **Por qué `POKEBALL_EXPANDED_BOTTOM` y no "cuánto sube".** La escala se aplica
> alrededor del centro, así que al cambiar el diámetro el centro se mueve solo y
> un desplazamiento relativo obliga a rehacer la cuenta. Midiendo el **borde
> inferior respecto al borde inferior de la barra**, el valor significa lo mismo
> sea cual sea el tamaño. Súbelo para que la bola quede más arriba.

`pokeBallTransform()` es la función que traduce esos dp a los números del
`graphicsLayer`. Todo lo que se interpola con el `progress` del gesto está ahí
dentro: el desplazamiento horizontal, el vertical y la escala.

`CONTAINER_HEIGHT` es **derivado** (`BAR_HEIGHT` más lo que sobresale la Pokéball)
para que, al cambiar el tamaño o la posición de la bola, el hueco que reserva el
`Scaffold` se ajuste solo y no dejes la barra pegada al contenido.

**Las medidas de la hendidura van en dp, no como fracción de la barra.** Así
`NOTCH_WIDTH` y `NOTCH_DEPTH` se editan por separado: ensanchar el hueco no
arrastra su fondo. Las dos están topadas en `BottomMenuNotchShape` para que
ningún valor pueda generar un `path` inválido.

> **Profundidad y ancho están relacionados.** La Pokéball flota dentro del hueco,
> así que el fondo de la hendidura debe quedar **por debajo** de su parte baja:
>
> ```
> fondo de la Pokéball = BAR_HEIGHT - POKEBALL_BOTTOM_PADDING   → 96 - 45 = 51 dp
> NOTCH_DEPTH          = ese valor + el hueco que dejes debajo   → 51 + 9 = 60 dp
> ```
>
> Con menos de 51 dp la bola queda medio enterrada en la barra blanca (se ve
> maciza); con mucho más, la barra pierde franja blanca en el centro. El ancho
> tiene la condición contraria: por debajo de `POKEBALL_SIZE / 2` (50 dp) la
> bola no cabe suelta.

### El hueco se abre a la vez que la Pokéball

La silueta de la barra **no es fija**: su semiancho interpola entre
`NOTCH_WIDTH` y `NOTCH_EXPANDED_WIDTH` con el mismo `progress` que mueve la
Pokéball, de modo que el hueco crece con ella y la bola nunca llega a invadir la
barra.

```kotlin
val shape = rememberBottomMenuNotchShape(
    width = lerp(NOTCH_WIDTH, NOTCH_EXPANDED_WIDTH, progress.floatValue),
    depth = NOTCH_DEPTH          // el fondo no cambia durante el gesto
)
```

Es el mismo modelo de "dos estados editables por separado" que la Pokéball:
`NOTCH_WIDTH` en reposo, `NOTCH_EXPANDED_WIDTH` expandida, los dos en dp y
ajustables por separado. Ponlos iguales si prefieres que el hueco no cambie.

> **Coste de animar una forma.** Cada frame del arrastre y del muelle construye
> una silueta nueva. Es inherente a animar una `Shape` paramétrica, no un
> descuido: los dos `MenuIconItem` de los lados no se recomponen porque sus
> parámetros no cambian, así que el coste queda en la `Surface`. Si notaras
> tirones en un móvil antiguo, la salida sería acortar el muelle, no tocar la
> forma.

> **Por qué `MenuBar` recibe el estado y no un `Float`.** Si leyera el progreso
> con un `Float`, la suscripción quedaría en `PokemonBottomMenu` y el menú entero
> se recompone en cada frame. Pasándole el `MutableFloatState`, la lectura queda
> dentro de `MenuBar`: solo se recompone la barra, y el resto del menú sigue
> leyendo el progreso dentro del `graphicsLayer`, que difiere la lectura.

La forma se construye en px (`BottomMenuNotchShape`) porque `GenericShape` no
tiene `Density`. El composable `rememberBottomMenuNotchShape()` hace la
conversión de dp y **recuerda** la instancia: sin `remember` se crearía un
`GenericShape` nuevo en cada recomposición y la `Surface` se redibujaría sin
motivo.

> **La barra no lleva `Modifier.shadow`, y es a propósito.** La sombra de una
> forma cóncava no se queda dentro de la silueta: al desenfocarse sangra hacia
> el interior del hueco, y como la hendidura es justo donde vive la Pokéball,
> aparecía un halo oscuro alrededor y debajo de ella. La separación con el
> contenido la hace el `border` de 1 dp, que sí sigue el contorno. Si quieres
> volumen de nuevo, sube la elevación **con cuidado** y comprueba la hendidura.

### La Pokéball central

Es el control principal del menú y tiene tres comportamientos:

| Gesto | Estado | Resultado |
|---|---|---|
| Toque | En reposo | `onHomeClick` → pantalla principal |
| Toque | Expandida | `onPokeBallGameClick(pos)` → `MainViewModel.onPokeBallTouch(...)` |
| Arrastre arriba y soltar | cualquiera | Se expande y avisa con `onExpandedChange(true)`; además lleva a la pantalla principal |
| Arrastre abajo y soltar | cualquiera | Vuelve a su sitio con `onExpandedChange(false)` |

**El movimiento** se guía con un `progress` continuo de `0f` a `1f`, que
interpola los dos estados absolutos: desplazamiento horizontal, vertical y
escala. Al soltar, un `Animatable` con
`spring(DampingRatioMediumBouncy, StiffnessLow)` lleva el progreso a `0f` o `1f` y
avisa con háptica (`LongPress`) cuando cambia de estado.

**Por qué un único `pointerInput`.** `detectVerticalDragGestures` no distingue el
toque final, y añadir un `clickable` aparte depende del orden de los modificadores
(consumir o no el evento cambia quién recibe el `up`). Aquí se usa `awaitEachGesture`
y solo se llama a `change.consume()` cuando el dedo supera el `touchSlop`: un
toque limpio nunca se consume y llega intacto a la rama del `onTap`.

**Por qué la posición va en `graphicsLayer`.** `translationY` y `scale` no consumen
espacio de layout, así que el `Scaffold` solo reserva `CONTAINER_HEIGHT` y la
Pokéball expandida queda flotando sobre el contenido. Además `graphicsLayer` **no**
transforma las coordenadas de los eventos de puntero, así que el offset que recibe
`onPokeBallTouch` sigue siendo local a la pokeball y el cálculo de dirección no
cambia.

**Insets.** El `NavigationBar` de Material aplicaba su propio padding; el menú
propio lo hace explícito con `navigationBarsPadding()` para que la barra blanca no
quede bajo la barra de gestos.

### Destinos (`AppTab`) y hoja de opciones

| Destino | Botón del menú | Contenido |
|---|---|---|
| **Mapa** | Botón izquierdo (`Icons.Filled.Map`) | `MapScreen`, ocupa todo el espacio bajo el menú |
| **Principal** | Pokéball central | `MainScreen`: HUD, trainer y hierba |
| **Opciones** | Botón derecho, *Pokédex* (`Icons.Filled.Book`) | `OptionsSheet` (`ModalBottomSheet`, no cambia de pantalla) |

`AppTab` solo tiene `Map` y `Home`: son las pantallas que ocupan la zona de
contenido. **Opciones no es una pestaña**: es una capa que se dibuja encima y su
visibilidad vive en `GameUiState.isOptionsOpen`.

> **El botón de Pokédex es un placeholder**: por ahora hace lo mismo que el de
> Opciones (abre el bottom sheet). El `enum AppTab` no lleva icono ni etiqueta,
> porque el menú los resuelve por su cuenta.

### Comportamiento

| Acción | Resultado |
|---|---|
| Pulsar el destino ya activo | No hace nada (se queda donde está) |
| Mapa → Principal | Vuelve a la pantalla principal |
| Principal → Mapa | Muestra el mapa |
| Botón de Pokédex | Abre la hoja de opciones sin cambiar de pantalla (placeholder) |
| Pulsar Mapa o Pokédex con la Pokéball expandida | La Pokéball se pliega sola (`settleTo(false)`) antes de navegar |
| Cualquier botón con la hoja abierta | Cierra la hoja y navega |
| Expandir la Pokéball desde el mapa | Va a la pantalla principal y se agranda |
| Atrás con la hoja abierta | Cierra la hoja (comportamiento de `ModalBottomSheet`) |
| Atrás en Principal | Sale de la app |
| Rotar con la hoja abierta | El `MainViewModel` conserva `isOptionsOpen = true` |

---

## 5. Pantalla principal

### Elementos de la pantalla

| Elemento | Tamaño | Sesgo vertical | Sesgo horizontal | Interactivo |
|---|---|---|---|---|
| HUD | automático | primera fila del `Column` | ancho completo | Botones de gimnasio/reset/principal |
| Trainer (+ capas de hierba) | `180.dp` | `-0.55f` | centrado | No |
| Botón de hierba alta | `80.dp` | `0.80f` | `0.90f` (derecha) | Sí (entra/sale del modo) |
| Poké Ball | — | — | — | **No está aquí**: vive en el bottom menu |

Los valores están centralizados en `object MainLayout` (`ui/MainScreen.kt`).

### El HUD reserva espacio (no se superpone)

`MainScreen` es un `Column`: el **HUD** (`GameHud`) ocupa la primera fila con su
altura natural y el **entrenador + hierba** van en una `Box` con `weight(1f)`
justo debajo. Así el HUD no se superpone al trainer: si crece mucho (nombre,
monedas, zona, barras, botones…), reduce el espacio restante. `TRAINER_BIAS` se
ajusta sobre esa caja, no sobre la pantalla completa.

### Capas de hierba alta sobre el trainer

El botón de hierba alta **alterna** la visibilidad de dos imágenes superpuestas
en la posición exacta del trainer:

| Capa | Imagen | Contenido |
|---|---|---|
| Fondo | `tall_grass_full` | Matorral completo, oculta al trainer |
| Medio | trainer | Sprite animado |
| Frente | `tall_grass_mid` | Matorral parcial, solo tapa la parte baja |

Las tres se apilan en un mismo `Box` (`contentAlignment = Center`), por lo que
comparten el mismo centro **por construcción**: no pueden desalinearse. En un
`Box` el orden de dibujo es de fondo a frente, así que el orden de las llamadas
es exactamente el orden de las capas.

El estado vive en el ViewModel (`GameUiState.isTallGrass`) y se alterna
con `MainViewModel.onTallGrassToggle()`. Las capas solo se componen cuando están
visibles, así que cuando se ocultan no se dibujan ni interceptan toques.

### Estado expuesto

`GameUiState` vive en su propio archivo (`viewmodel/GameUiState.kt`), separado del
ViewModel para que la View pueda importar el tipo de estado sin arrastrar el
ViewModel (por ejemplo en los `@Preview`). Es **inmutable** y agrupa juego,
navegación y combate:

```kotlin
data class GameUiState(
    // meta permanente: playerName, coins, seenSpecies, rewardedSpecies,
    //                 medals, starterSpeciesId, starterBond
    // run: ownedSpecies, leadSpeciesId, zoneIndex, zoneClicks
    // hierba/encuentros: isTallGrass, walkProgress, encounter
    // combate: gym
    // visual: trainer, trainerFrameRes
    // navegación: contentTab, isOptionsOpen, isLeadOpen
    // derivados: zoneName, zoneRequirement, clickPower, clicksPerSecond,
    //            maxHp, leadName, leadType, isGymReady, gymLeaderName,
    //            needsStarter, leadOptions, starterOptions
)
```

- `zoneClicks` y `coins` son **`Long`**: en un clicker los contadores crecen sin
  techo y con `Int` desbordarían a los ~2.100 millones.
- `trainerFrameRes` es el `R.drawable` ya resuelto, para que la View no tenga que
  conocer el mapa de frames.
- `leadOptions`/`starterOptions` llegan como listas de `LeadOption`/`StarterOption`
  resueltas; la UI no consulta catálogos.
- `clicksPerSecond` es la producción pasiva (CPS) del género idle (ver abajo).
- `zoneProgress` e `isLeadCandidate()` son derivaciones **puras** del propio estado.

El composable `MainScreen` **no** conoce el ViewModel: recibe `GameUiState` y las
lambdas. La `MainActivity` es quien conecta estado y acciones.

### Potencia de clic y progreso

Cada toque de la Pokéball suma `clickPower` al `zoneClicks`. `clickPower` se deriva
en el ViewModel como `(1 + bonus).roundToLong().coerceAtLeast(1L)`, donde el bonus
suma un 5 % por cada especie en posesión (starter incluido) y un 2 % extra por
nivel de vínculo cuando el starter está equipado. El progreso de zona se muestra
en el HUD y se calcula con `zoneProgress`; no hay un texto "Clicks: N".

### Producción pasiva (CPS)

El género idle exige un ingreso automático por segundo. Vive en el ViewModel como
un bucle controlado dentro de `viewModelScope`:

```kotlin
private fun startPassiveIncomeLoop() {
    viewModelScope.launch {
        while (isActive) {
            delay(CPS_TICK_MS)            // 1.000 ms
            val state = _uiState.value
            val busy = state.isTallGrass || state.encounter != null || state.gym != null
            if (state.clicksPerSecond <= 0L || busy) continue
            mutate { addZoneClicks(it, it.clicksPerSecond) }
        }
    }
}
```

- `delay` **suspende** la corrutina; no bloquea el hilo principal.
- El bucle se cancela solo cuando se destruye el ViewModel (`viewModelScope`), así
  que **no hace falta `onCleared()`**.
- Solo escribe estado cuando `clicksPerSecond > 0` y no hay una actividad que deba
  pausar el avance (hierba, encuentro o gimnasio).
- Hoy `clicksPerSecond` vale 0: es el gancho listo para conectar producción pasiva
  sin tocar la UI. Bastará con actualizar ese campo y el bucle empezará a producir.

### Actualizaciones atómicas

Todas las mutaciones usan `MutableStateFlow.update {}`, que es una
compare-and-set atómica. Es lo que evita condiciones de carrera entre el bucle de
CPS y los toques, que corren en corrutinas distintas:

```kotlin
_uiState.update { state ->
    state.copy(zoneClicks = state.zoneClicks + state.clickPower)
}
```

### Detección de dirección

La Poké Ball se divide en 4 cuadrantes de 90°:

```kotlin
val center = sizePx / 2f
val angle = Math.toDegrees(atan2(touchY - center, touchX - center))

return when {
    angle >= -45.0 && angle < 45.0 -> Direction.RIGHT    // 0° ± 45°
    angle >= 45.0 && angle < 135.0 -> Direction.FRONT   // 90° ± 45°
    angle >= 135.0 || angle < -135.0 -> Direction.LEFT  // 180° ± 45°
    else -> Direction.BACK                              // -90° ± 45°
}
```

- `atan2` devuelve `[-π, π]`: 0° = derecha, 90° = **abajo** (en pantalla +Y es
  hacia abajo), ±180° = izquierda, -90° = arriba.
- Por eso `FRONT` corresponde a tocar **abajo** y `BACK` a tocar **arriba**.
- Las coordenadas son relativas al centro de la pokeball, no a la pantalla.

### Reglas de animación

| Situación | Frame resultante | Temporizador |
|---|---|---|
| Cambia la dirección | 0 de la nueva dirección | Se cancela |
| Misma dirección | `(actual + 1) % 4` | Se programa reset a 1 s |
| Pasa 1 s sin tocar | Vuelve a 0 | — |

> **Nota de comportamiento:** al cambiar de dirección el Trainer queda en el
> frame 0 de esa dirección; la rama `if (directionChanged)` de
> `MainViewModel.onPokeBallTouch()` controla ese reinicio.

El temporizador usa `viewModelScope` y cancela el `Job` anterior, de forma que
no se acumulan resets:

```kotlin
private fun scheduleFrameReset() {
    resetJob?.cancel()
    resetJob = viewModelScope.launch {
        delay(FRAME_RESET_DELAY_MS)   // 1000 ms
        _uiState.update { state ->
            state.copy(
                trainer = state.trainer.copy(currentFrame = 0),
                trainerFrameRes = sprites.frame(state.trainer.currentDirection, 0)
            )
        }
    }
}
```

### Captura del toque

El toque lo captura la Pokéball del bottom menu. `AppRoot` (sin estado) lo
reenvía a la lambda `onPokeBallTouch`, que la `MainActivity` conectó al ViewModel,
con el tamaño real en px calculado a partir de `BottomMenuDefaults.POKEBALL_SIZE`:

```kotlin
val pokeBallSizePx = with(LocalDensity.current) { BottomMenuDefaults.POKEBALL_SIZE.toPx() }

PokemonBottomMenu(
    onPokeBallGameClick = { offset ->
        onPokeBallTouch(offset.x, offset.y, pokeBallSizePx)
    },
    // ...
)
```

`offset` es la posición local dentro de la Pokéball, que es lo que necesita
`directionFrom`. Sigue siendo válida aunque esté escalada y desplazada, porque
`graphicsLayer` no transforma las coordenadas de puntero.

---

## 6. Posicionamiento porcentual (`PercentageLayout`)

Este es el componente que permite **mover elementos con valores porcentuales
que funcionan en cualquier tamaño de pantalla**. Implementa guías y bias
porcentual sin depender de ConstraintLayout.

### Uso

```kotlin
PercentageLayout {                                   // zona 10 % … 90 % de la altura
    Image(..., Modifier.size(180.dp).anchorVertically(bias = 0.50f))
    Text(..., Modifier.anchorVertically(bias = 0.35f))
    Image(..., Modifier.size(80.dp).anchor(verticalBias = 0.80f, horizontalBias = 0.90f))
}
```

### API

| Modifier | Efecto |
|---|---|
| `Modifier.anchorVertically(bias)` | Sesgo vertical dentro de la zona de contenido. `0f` = pegado arriba, `0.5f` = centrado, `1f` = pegado abajo. |
| `Modifier.anchorHorizontally(bias)` | Sesgo horizontal respecto a los bordes de la pantalla. |
| `Modifier.anchor(verticalBias, horizontalBias)` | Ambos a la vez. |

`bias` es el equivalente a `app:layout_constraintVertical_bias` de
ConstraintLayout: `0f` arriba, `0.5f` centro, `1f` abajo.

### Parámetros de `PercentageLayout`

| Parámetro | Por defecto | Significado |
|---|---|---|
| `topPercent` | `0.10f` | Guía superior inicial (fracción de la altura). |
| `bottomPercent` | `0.90f` | Guía inferior inicial. |
| `maxTopPercent` | `0.30f` | La guía superior nunca sube más. |
| `minBottomPercent` | `0.70f` | La guía inferior nunca baja de aquí. |
| `applyInsets` | `true` | Corrige las guías con barras del sistema y notch. |

### Cómo funciona

1. Los hijos se miden con `Constraints(maxWidth = ancho del padre)` y altura
   libre, para que `Modifier.size(...)` y `fillMaxWidth()` funcionen.
2. Se calculan las guías **sobre la altura real del contenedor**, no sobre
   valores fijos:

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

El `coerceAtMost` / `coerceAtLeast` evita que el contenido se solape en pantallas
con barras muy altas.

### Inset automático

```kotlin
val bars  = WindowInsets.systemBars.asPaddingValues()
val cutout = WindowInsets.displayCutout.asPaddingValues()
val topDp = maxOf(bars.calculateTopPadding(), cutout.calculateTopPadding())
```

El inset superior (barra de estado o notch) **suma**, el inferior (barra de
navegación o barra de gestos) **resta**. El resultado es que la composición es
idéntica en cualquier móvil.

### Valores actuales de la pantalla principal

Centralizados en `object MainLayout` (`ui/MainScreen.kt`):

```kotlin
object MainLayout {
    val TRAINER_SIZE = 180.dp
    val TALL_GRASS_SIZE = 80.dp

    const val TRAINER_BIAS = -0.55f      // trainer (mueve también las capas)
    const val TALL_GRASS_BIAS = 0.8f     // botón de hierba
    const val TALL_GRASS_H_BIAS = 0.9f   // botón de hierba: hacia la derecha
    val HUD_PADDING = 16.dp              // margen del HUD

    // Capas de hierba alta, centradas sobre la posición del trainer
    val TALL_GRASS_FULL_SIZE = 120.dp
    val TALL_GRASS_MID_SIZE = 120.dp
    val TALL_GRASS_X_OFFSET = 3.dp       // desplazamiento horizontal de la hierba
    val TALL_GRASS_Y_OFFSET = 38.dp      // desplazamiento vertical de la hierba
}
```

> La Pokéball **no** se posiciona desde aquí: la dibuja el bottom menu. Sus
> ajustes están en `ui/PokemonBottomMenu.kt`.

**Para mover un elemento, cambia su `BIAS`. Para mover toda la zona, cambia
`ContentZoneDefaults.TOP` / `BOTTOM`.**

### Mover trainer + hierba

Trainer y las dos capas comparten un único `Box`, así que **un solo sesgo los
mueve a todos**: `TRAINER_BIAS`.

### Mover la hierba respecto al trainer

`TALL_GRASS_X_OFFSET` e `TALL_GRASS_Y_OFFSET` (en `dp`) desplazan **las dos
capas a la vez**, sin separarlas entre sí:

```kotlin
val TALL_GRASS_X_OFFSET = 3.dp     // positivo = derecha, negativo = izquierda
val TALL_GRASS_Y_OFFSET = 38.dp    // positivo = abajo,    negativo = arriba
```

Afecta solo al desplazamiento visual: `Modifier.offset` no altera el tamaño del
`Box`, así que el trainer no se mueve ni cambia el tamaño de la zona.

### Posicionar en ambas dimensiones a la vez

Para elementos que necesitan sesgo vertical **y** horizontal (como el botón de
hierba alta) usa `anchor(...)`:

```kotlin
Modifier.anchor(
    verticalBias = MainLayout.TALL_GRASS_BIAS,
    horizontalBias = MainLayout.TALL_GRASS_H_BIAS
)
```

> **No encadenes `anchorVertically` + `anchorHorizontally`.** Cada uno inserta su
> propio dato de anclaje y, al haber dos, **solo se aplica el último**. `anchor()`
> mete ambos sesgos en un único dato, que es lo correcto.

### Por qué no se usa `constraintlayout-compose`

El componente propio cubre el caso actual (guías + bias + insets) sin añadir una
dependencia ni otro sistema de medidas. Si en el futuro hiciera falta un grafo de
restricciones complejo, habría que reconsiderarlo.

> **Aviso importante (Compose UI 1.9):** si añades layouts personalizados,
> ten en cuenta que `MeasurePolicy.measure` recibe `List<Measurable>` (no un
> `Measurable` suelto) y que `ParentDataModifier` expone
> `Density.modifyParentData(parentData): Any` en lugar de `modifierParentData`.

### Limitaciones conocidas

- Un elemento solo admite **un** `AnchorData`; si encadenas dos anclas, gana la
  última. Usa `anchor(verticalBias, horizontalBias)` para ambos ejes.
- Los sesgos **no se recortan**: un valor fuera de `0f..1f` coloca el elemento
  fuera de la zona de contenido. Se dibuja igualmente, pero deja de estar
  protegido por los límites ni los insets.
- La altura se deja sin límite (`maxHeight = Infinity`), por lo que un
  `fillMaxHeight()` dentro de `PercentageLayout` produciría una altura
  indeterminada. Usa siempre `Modifier.size(...)` o tamaño intrínseco.
- Solo hay anclaje **porcentual**; para márgenes fijos usa `Modifier.padding`
  o `Modifier.offset`.

---

## 7. Pantalla de mapa

`MapScreen` es un **composable sin estado y sin lógica**: solo muestra un texto
centrado. Ocupa todo el espacio del contenido, es decir, la pantalla completa por
debajo del bottom menu.

Para darle contenido, añade los composables dentro de `PercentageLayout` para
que se comporten igual que en la pantalla principal.

---

## 8. Menú de opciones

`OptionsSheet` (`ui/Overlays.kt`) es un `ModalBottomSheet` de **Material 3**,
100 % Compose. Se dibuja dentro del árbol de `AppRoot`.

- **Visibilidad**: la manda `GameUiState.isOptionsOpen`. `AppRoot` hace
  `if (state.isOptionsOpen) OptionsSheet(onDismiss = onCloseOptions)`.
- **Apertura**: `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, así
  que se abre directamente al contenido.
- **Cierre**: `onDismissRequest` (arrastre hacia abajo, toque fuera o botón atrás)
  llama a `onDismiss`, que ejecuta `MainViewModel.onCloseOptions()`.
- **Estado persistente**: `MainViewModel` tiene scope de Activity, así que
  `isOptionsOpen` sobrevive a la rotación.

Contenido actual (`OptionsContent`): título, separador y dos `ListItem`
decorativos (*Configuración* y *Acerca de*) sin acción asociada.

---

## 9. Recursos

### Drawables

| Archivo | Uso |
|---|---|
| `pokeball_menu.png` | Pokéball del bottom menu (100 dp en reposo, escala hasta 280 dp) |
| `pokeball_splash_top.png`, `pokeball_splash_bottom.png` | Cortinilla de arranque |
| `tall_grass_button.png` | Botón de hierba alta (80 dp) |
| `tall_grass_full.png` | Capa de fondo: matorral completo sobre el trainer (120 dp) |
| `tall_grass_mid.png` | Capa frontal: matorral parcial sobre el trainer (120 dp) |
| `front0-3.png` | Frames "front" (toque abajo) |
| `right0-3.png` | Frames "right" (toque derecha) |
| `left0-3.png` | Frames "left" (toque izquierda) |
| `back0-3.png` | Frames "back" (toque arriba) |
| `profesor_oak.png` | Retrato de la introducción |
| `bulbasaur.gif`, `charmander.gif`, `squirtle.gif` | Starters animados de la intro |
| `bulbasaur_back.gif`, `charmander_back.gif`, `squirtle_back.gif` | Principal visto de espaldas en el gimnasio |
| `golem.gif` | Enemigo de Brock |
| `rock.png` | Proyectil del gimnasio |
| `rock_gym.jpg` | Fondo del gimnasio |
| `ic_launcher_background.xml`, `ic_launcher_foreground.xml` | Icono adaptativo |

Nombres en minúsculas obligatorio (a-z, 0-9, `_`).

### Cómo añadir una imagen nueva

1. Copia el archivo a `app/src/main/res/drawable/` con nombre **en minúsculas** y
   solo `[a-z0-9_]` (AAPT2 rechaza mayúsculas: `tallGrassButton.png` →
   `tall_grass_button.png`).
2. Añade su `contentDescription` en `strings.xml` si aporta información.
3. Añade el composable en la pantalla correspondiente.
4. Declara tamaño y posición en el `object` de configuración de esa pantalla.

> **Aviso de tamaño:** `tall_grass_button.png` ocupa 1500×1500 px (394 KB) pero
> se muestra a 80 dp. Conviene reducirla a unas 300×300 px o pasarla a WebP.
> `rock_gym.jpg` pesa ~3,4 MB y es el recurso más grande del APK; es el candidato
> prioritario a compresión. `tall_grass_full.png` y `tall_grass_mid.png` son de
> 750×750 px y se muestran a 120 dp, así que también admiten reducción.

### Strings (`res/values/strings.xml`)

La lista completa vive en el recurso; los grupos son:

| Grupo | Claves representativas | Uso |
|---|---|---|
| App y navegación | `app_name`, `nav_*`, `map_placeholder` | Nombre, menú y mapa |
| Pantalla principal | `trainer_desc`, `tall_grass_desc`, `hud_*` | Trainer, HUD y hierba |
| Acciones | `action_reset`, `action_gym`, `action_wipe` | Botones del HUD |
| Captura | `catch_*` | Overlay de encuentro |
| Starter y principal | `starter_type`, `lead_*` | Intro y selector de principal |
| Gimnasio | `gym_*` | HUD, fases y resultado del combate |
| Introducción | `intro_*` | Diálogo del Profesor Oak |
| Opciones | `options_*` | Hoja de opciones |

### Temas

`Theme.Pokeclic` hereda de `Theme.MaterialComponents.DayNight.NoActionBar`:

| Atributo | Valor | Motivo |
|---|---|---|
| `windowFullscreen` | `true` | Sin barra de estado |
| `statusBarColor` / `navigationBarColor` | `transparent` | Edge-to-edge |
| `windowLayoutInDisplayCutoutMode` | `shortEdges` | Contenido hasta los bordes en pantallas con notch |

---

## 10. Dependencias

Definidas en `gradle/libs.versions.toml` (version catalog).

### Plugins

| Plugin | Versión |
|---|---|
| `com.android.application` | 9.4.1 |
| `org.jetbrains.kotlin.plugin.compose` | 2.0.0 |

> AGP 9 trae el soporte de Kotlin integrado. **No** apliques
> `org.jetbrains.kotlin.android`: entra en conflicto con la versión que ya está
> en el classpath. El `jvmTarget` se configura en `app/build.gradle.kts` con
> `tasks.withType<KotlinCompile> { compilerOptions.jvmTarget... }`.

### Librerías

| Grupo | Uso |
|---|---|
| `androidx.appcompat`, `com.google.android.material` | `AppCompatActivity` y el tema `MaterialComponents.DayNight` |
| `androidx.core-ktx` | Utilidades base |
| `androidx.lifecycle.viewmodel-ktx` | `ViewModel`, `viewModelScope`, `viewModelFactory` |
| `androidx.lifecycle.runtime-compose` | `collectAsStateWithLifecycle()` |
| `androidx.activity-compose` | `setContent {}` y `by viewModels()` |
| `androidx.compose.ui`, `ui-graphics`, `ui-tooling-preview` | UI, dibujo y previews |
| `androidx.compose.material3` | Componentes Material 3 (`ModalBottomSheet`, `Scaffold`…) |
| `androidx.compose.material-icons-extended` | Iconos de relleno del menú |
| `androidx.compose.ui.tooling` (`debugImplementation`) | Inspector y previews en debug |
| `junit` (`testImplementation`) | Tests unitarios de `ModelTest` |

Compose se coordina con el BOM `2024.10.01`.

### Build release

```kotlin
optimization {
    enable = true
    packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
}
```

`material-icons-extended` aporta muchos iconos; la optimización de release es
lo que permite que R8 elimine los que no se usan.

---

## 11. Ciclos de vida y flujos

### Arranque

1. `MainActivity.onCreate()` crea **un único** `MainViewModel` con scope de
   Activity mediante `viewModelFactory { initializer { ... } }`, porque pide un
   `SpriteRepository` y un `GameStateStore`.
2. `setContent { MaterialTheme { ... } }`. Dentro del `setContent` se recoge el
   único estado con `collectAsStateWithLifecycle()` y se pasa a `AppRoot` junto
   con las lambdas.
3. `AppRoot` dibuja el `Scaffold` y, como `contentTab` empieza en `AppTab.Home`,
   compone `MainScreen(state = state, onTallGrassToggle = ...)`.
4. `PokeBallSplash` se dibuja encima y se retira al terminar la animación.
5. Cambiar de pestaña es cambiar `GameUiState.contentTab`, y Compose recompone el
   `when`.

### Toque

```
MenuPokeBall (toque sin arrastrar, ya expandida)
  → AppRoot.onPokeBallGameClick(offset)
      → onPokeBallTouch(offset.x, offset.y, pokeBallSizePx)   ← lambda que cableó MainActivity
          → MainViewModel.onPokeBallTouch(x, y, sizePx)
              → directionFrom(...)          // lógica de negocio
              → _uiState.update { ... }     // progreso + trainer + drawable (atómico)
              → scheduleFrameReset()        // si no cambió la dirección
  → el StateFlow emite → recomposición de MainScreen
```

`MainScreen` solo reacciona al estado que ya calculó el ViewModel; no sabe que
existe la Pokéball.

### Arrastre de la Pokéball

```
MenuPokeBall.pointerInput (awaitEachGesture)
  → progress += deltaVertical / DRAG_SENSITIVITY      // 0f..1f, continuo
  → graphicsLayer: translationY y escala proportionales al progreso
  → al soltar: si supera el umbral se queda expandido
      → onExpandedChange(expanded)
          → MainViewModel.onPokeBallExpanded(expanded) → AppTab.Home si expandida
      → spring(Muelle) lleva progress a 1f o 0f
```

### Botón de hierba alta

```
TallGrassButton.onClick
  → onTallGrassToggle()                     ← lambda que cableó MainActivity
      → MainViewModel.onTallGrassToggle()
          → _uiState.update { isTallGrass = !it.isTallGrass }
  → recomposición de MainScreen
      → TrainerGrassStack dibuja (o no) las dos capas
```

### Producción pasiva (CPS)

```
MainViewModel.init → startPassiveIncomeLoop()
  → while (isActive) { delay(1000) ; si no está ocupado, addZoneClicks(clicksPerSecond) }
  → el Job vive en viewModelScope: se cancela al destruir el ViewModel
```

### Rotación

- El único `MainViewModel` de Activity sobrevive: no se pierden el progreso ni el
  destino visible.
- `GameUiState` conserva `contentTab`, `isOptionsOpen` e `isLeadOpen` (la hoja o el
  selector siguen abiertos y el botón marcado) sin `rememberSaveable`.

### Destrucción

- Los Jobs de reset de frame, caminar, CPS y combate mueren con `viewModelScope`.
  **No hace falta `onCleared()`**.
- Solo hay una composición, la de `setContent`.

---

## 12. Guía rápida: mover elementos

### Mover un elemento concreto

`ui/MainScreen.kt` → `object MainLayout` → cambia el `BIAS`:

```kotlin
const val TRAINER_BIAS     = -0.55f
const val TALL_GRASS_BIAS  = 0.80f
const val TALL_GRASS_H_BIAS = 0.90f
```

Rango habitual: `0f` (guía superior) a `1f` (guía inferior). Se admiten valores
fuera de ese rango: `TRAINER_BIAS = -0.55f` sube al trainer por encima de la guía
superior. El layout no los recorta.

### Mover o redimensionar la Pokéball del menú

`ui/PokemonBottomMenu.kt` → `object BottomMenuDefaults`. Los dos estados se
editan por separado, y todo en dp:

| Quiero… | Cambio |
|---|---|
| Cambiar su tamaño en reposo | `POKEBALL_SIZE` |
| Cambiar su sitio en reposo | `POKEBALL_BOTTOM_PADDING` (vertical) y `POKEBALL_OFFSET_X` (lateral) |
| Cambiar su tamaño expandida | `POKEBALL_EXPANDED_SIZE` |
| Cambiar su sitio expandida | `POKEBALL_EXPANDED_BOTTOM` (súbelo para que quede más alta) y `POKEBALL_EXPANDED_OFFSET_X` |
| Que proyecte sombra al expandirse | `EXPANDED_SHADOW` (`0.dp` = sin sombra) |
| Que el hueco no cambie de ancho | `NOTCH_EXPANDED_WIDTH` = `NOTCH_WIDTH` |
| Que el hueco se abra más al expandir | `NOTCH_EXPANDED_WIDTH` (súbelo; el tope es la mitad del ancho de la barra) |
| Que el gesto cueste más o menos | `DRAG_SENSITIVITY` y `SNAP_THRESHOLD` |
| Cambiar la respuesta visual al toque | `TAP_PUNCH_SCALE` (`1f` = sin "golpe") |

Los dos tamaños son independientes: puedes tener 100 dp en reposo y 320 dp
expandida sin que nada se recalcule a mano.

> `CONTAINER_HEIGHT` **no se edita**: se deriva de `BAR_HEIGHT` y de cuánto
> sobresale la Pokéball, así que el hueco que reserva el `Scaffold` se ajusta
> solo. La Pokéball se dibuja con `graphicsLayer`, que no la recorta al
> expandirse, así que subirla mucho no la corta: solo se superpone más al
> contenido de la pantalla.

### Mover toda la zona de contenido

`ui/Layout.kt` → `object ContentZoneDefaults`:

```kotlin
const val TOP    = 0.10f   // más alto → la zona empieza más abajo
const val BOTTOM = 0.90f
const val MAX_TOP    = 0.30f   // tope de seguridad superior
const val MIN_BOTTOM = 0.70f   // tope de seguridad inferior
```

### Cambiar tamaños fijos

`MainLayout.TRAINER_SIZE` / `MainLayout.TALL_GRASS_SIZE` (en `dp`, escalan con
la densidad), `MainLayout.HUD_PADDING` para el margen superior del HUD y
`BottomMenuDefaults.POKEBALL_SIZE` para la del menú.

### Añadir un elemento nuevo a la pantalla principal

```kotlin
PercentageLayout {
    // ...elementos existentes...

    Text(
        text = "Hola",
        modifier = Modifier.anchorVertically(bias = 0.30f)   // elige la posición
    )
}
```

### Posicionar en horizontal

```kotlin
Modifier.anchorHorizontally(bias = 0.25f)   // 0f = pegado a la izquierda
```

### Ajustar las capas de hierba alta

| Quiero… | Cambio |
|---|---|
| Mover trainer + capas juntos | `TRAINER_BIAS` |
| Subir o bajar la hierba sobre el trainer | `TALL_GRASS_Y_OFFSET` (en `dp`, afecta a las dos capas) |
| Mover la hierba a izquierda o derecha | `TALL_GRASS_X_OFFSET` (en `dp`, afecta a las dos capas) |
| Cambiar el tamaño de la capa de fondo | `TALL_GRASS_FULL_SIZE` |
| Cambiar el tamaño de la capa frontal | `TALL_GRASS_MID_SIZE` |
| Desplazar solo una capa | Pasa un `offsetX` / `offsetY` propio en esa llamada a `TallGrassLayer` |
| Añadir otra capa | Añade un `Image` en `TrainerGrassStack` en el orden de capas que quieras |

El **orden de las capas** es el orden de las llamadas dentro de
`TrainerGrassStack`: la primera se dibuja al fondo y la última al frente.

### Previsualizar como en el dispositivo

Los `@Preview` deben reproducir el montaje **real**, no la pantalla suelta; si no,
el preview y el emulador difieren (el entrenador salía en otro sitio y el HUD
parecía más corto):

- **Dispositivo**: `device = "spec:width=411dp,height=914dp,dpi=420"` (el AVD de
  pruebas, `Medium_Phone`: 1080×2400 px @ 420 dpi) y `showSystemUi = true`. Está
  centralizado en la constante `PREVIEW_DEVICE`.
- **Con bottom menu**: `MainScreen` se previsualiza dentro de un `Scaffold` con el
  `PokemonBottomMenu` real (helper `MainScreenStage`), porque en la app el menú
  reserva `CONTAINER_HEIGHT` y eso desplaza al entrenador.
- **Estado realista**: usar `previewGameState(...)`, **no** `GameUiState()` en
  blanco (vacío deja el HUD corto y el preview engaña).
- El montaje **completo** (con overlays) tiene sus previews en `AppRoot`:
  `AppRootGamePreview`, `AppRootTallGrassPreview`, `AppRootGymPreview` y
  `AppRootIntroPreview`.

> Los insets del sistema pueden no reproducirse en el preview aunque pongas
> `showSystemUi = true`; por eso `PercentageLayout` puede verse unos dp más arriba
> que en el dispositivo.

---

## 13. Extensibilidad

### Añadir una nueva pestaña con pantalla

El menú tiene **tres huecos fijos** (izquierda, centro, derecha), así que una
pestaña nueva no siempre cabrá sin reordenar el menú.

1. Crea `ui/NuevaScreen.kt` con su composable (sin estado propio).
2. Añade la entrada al `enum class AppTab` (`model/GameData.kt`).
3. Añade la rama en el `when` de `AppRoot.kt`:
   ```kotlin
   AppTab.Nueva -> NuevaScreen(state = ..., onEvento = ...)
   ```
4. Conecta sus lambdas desde `MainActivity`. Si necesita estado, añádelo a
   `GameUiState` y resuélvelo en `MainViewModel`; no crees un segundo ViewModel
   sin una razón de peso.
5. Añade su botón en el `MenuBar` pasando `onSelectTab(AppTab.Nueva)`.

### Añadir un nuevo desplegable (bottom sheet)

1. Crea el composable del sheet con `ModalBottomSheet`, como `OptionsSheet`.
2. Añade un campo boolean a `GameUiState` (p. ej. `isAboutOpen`) y sus
   `onOpen...` / `onClose...` en `MainViewModel`.
3. En `AppRoot`, dibújalo tras el `Scaffold` cuando el flag sea `true`.
4. Añade un botón en el `MenuBar` que llame a `onOpen...`.

### Añadir una nueva dirección de animación

1. Añade el valor al `enum class Direction`.
2. Añade el array de 4 drawables en `DefaultSpriteRepository`.
3. Ajusta los rangos de ángulo en `MainViewModel.directionFrom()`.
4. Sube los PNG a `res/drawable/`.

### Cambiar el tiempo del reset o el tick de CPS

`MainViewModel` → `FRAME_RESET_DELAY_MS = 1_000L` y `CPS_TICK_MS = 1_000L`.

### Sustituir los iconos provisionales

Cambia el `ImageVector` en el `private object MenuIcons` de
`PokemonBottomMenu.kt`. Si usas iconos propios, ponlos en `res/drawable/` y usa
`painterResource(id)` en lugar de `imageVector`.

### Ajustar el movimiento de la Pokéball

Todo está en `BottomMenuDefaults`: tamaño, cuánto sube, cuánto crece, sensibilidad
del arrastre y umbral de enganche. No hace falta tocar la lógica del gesto.

### Escribir tests del ViewModel

`MainViewModel` no importa `R` ni ninguna clase de Android, así que se testea
directamente en la JVM. Basta con pasar un `SpriteRepository` falso y un
`GameStateStore` en memoria:

```kotlin
class FakeSprites : SpriteRepository { /* devuelve ids inventados */ }
class FakeStore : GameStateStore { /* guarda en memoria */ }

val vm = MainViewModel(FakeSprites(), FakeStore())
vm.onPokeBallTouch(touchX = 0f, touchY = 100f, sizePx = 200f)   // 100 dp en xhdpi
assertTrue(vm.uiState.value.zoneClicks > 0L)
```

El ViewModel también expone los métodos de navegación (`onSelectTab`,
`onOpenOptions`, etc.), que se prueban sobre el mismo `uiState`.

---

## 14. Compatibilidad de la partida actual

El guardado actual es un `GameSnapshot` plano en `SharedPreferences`, dentro de
`shared_prefs/kanto_clicker.xml`. `SharedPreferencesGameStateStore` escribe una
clave por campo:

| Clave | Campo | Formato |
|---|---|---|
| `player_name` | `playerName` | Texto o vacío |
| `coins` | `coins` | `Long` |
| `seen` | `seenSpecies` | IDs separados por comas |
| `rewarded` | `rewardedSpecies` | IDs separados por comas |
| `medals` | `medals` | IDs separados por comas |
| `starter` | `starterSpeciesId` | Texto o vacío |
| `starter_bond` | `starterBond` | `Int` |
| `owned` | `ownedSpecies` | IDs separados por comas |
| `lead` | `leadSpeciesId` | Texto o vacío |
| `zone_index` | `zoneIndex` | `Int` |
| `zone_clicks` | `zoneClicks` | `Long` |

Limitaciones que condicionan la migración:

- **No hay `schemaVersion`**: el formato se deduce por la presencia de `coins`.
- `owned` es un `Set<String>`: no puede representar dos individuos de la misma
  especie, nivel, experiencia, movimientos, habilidad ni vínculo por Pokémon.
- Los IDs son la única relación con el contenido; si un ID desaparece, el dato
  queda huérfano.
- El estado transitorio (encuentro, caminar, gimnasio, navegación y animación) no
  se persiste a propósito.
- El nombre histórico `kanto_clicker` y el `applicationId` **no se cambian**:
  hacerlo invalidaría las partidas instaladas.

La migración a `PokemonInstance` y a un guardado versionado se define en §21.

---

## 15. Depuración

| Problema | Causa probable | Solución |
|---|---|---|
| Los elementos se salen de la pantalla | `bias` inesperado o fuera del rango útil | Revisar el `BIAS` en `object MainLayout` |
| Un elemento se solapa con otro | Ambos tienen sesgos cercanos | Separar los `BIAS` |
| Solo se mueve en vertical u horizontal | Se encadenaron `anchorVertically` + `anchorHorizontally` y ganó el último | Usar `anchor(verticalBias, horizontalBias)` en una sola llamada |
| La zona se come la pantalla | `ContentZoneDefaults` mal ajustados | Revisar `TOP`, `BOTTOM`, `MAX_TOP`, `MIN_BOTTOM` |
| La Pokéball no responde | El `pointerInput` no llega al composable | Comprobar que el área no está cubierta por otro elemento |
| La Pokéball no reacciona al arrastre | `pointerInput(Unit)` con un `isExpanded` capturado: queda congelado | Leer siempre `state.value` o usar `rememberUpdatedState` para los callbacks |
| El arrastre va a tirones | Se lanza una corrutina por evento (`launch { snapTo() }`) | Escribir en un `mutableFloatStateOf` y usar `Animatable` solo al soltar |
| El toque no dispara nada al soltar | Se consumió el evento antes de superar el `touchSlop` | Consumir **solo** cuando el gesto ya es arrastre |
| El clic siempre cuenta como "front" | `sizePx` incorrecto | Debe pasarse el tamaño real en px, no en dp |
| La dirección sale invertida al girar | Se pasó la posición global en vez de la local | La Pokéball entrega `down.position`, que ya es local |
| Un cambio de pestaña reinicia el progreso | Se reinicia el estado en `AppRoot` | El progreso debe vivir en `MainViewModel` (scope de Activity) |
| El botón de Pokédex queda marcado sin hoja abierta | `isOptionsOpen` no se actualizó | Revisar `MainViewModel.onCloseOptions()` / `onSelectTab()` |
| El mapa tapa el bottom menu | El contenido ignora `innerPadding` | El `when` vive dentro del `Box` con `padding(innerPadding)` |
| La barra blanca queda bajo la barra de gestos | Falta el inset inferior | `navigationBarsPadding()` en el contenedor del menú |
| Crash al crear el ViewModel (`NoSuchMethodException`) | Constructor con parámetros sin `Factory` | Usar `viewModelFactory { initializer { ... } }` en la Activity |
| Error de compilación en un layout propio | Firmas de Compose UI 1.9 | `MeasureScope.measure(List<Measurable>, …)` y `Density.modifyParentData(…)` |

### Depuración visual

- `@Preview` está disponible en `MainScreen`, `MapScreen` y en los cinco previews
  del bottom menu (`1 · En reposo`, `2 · Arrastrando (50 %)`, `3 · Expandida`,
  `4 · Con Mapa seleccionado` y `5 · Hendidura (silueta)`).
- **El 5 es el que sirve para ajustar la hendidura**: dibuja solo la silueta
  sobre un fondo oscuro, con la Pokéball en su posición real de reposo encima,
  sin iconos ni etiquetas que la tapen. Cambia `NOTCH_WIDTH`, `NOTCH_DEPTH` y
  `POKEBALL_BOTTOM_PADDING` y míralo: cualquier cambio se ve al instante.
- El fondo de los previews es verde (`PreviewBackground`) **a propósito**: la
  hendidura se dibuja dejando ver lo que hay detrás, así que con un fondo claro
  la curva desaparece y da la sensación de que los valores no hacen nada.
- Para aislar el menú se usa `MenuStage`, que imita el contenido con un trainer de
  mentira. Su `initialProgress` permite ver `0f` (reposo), `0.5f` (a medio camino)
  o `1f` (expandida); es el mismo parámetro que alimenta el gesto real.
- `MainScreen` y `MapScreen` son composables sin estado, así que sus `@Preview`
  pasan un `GameUiState` de mentira (con `trainerFrameRes = R.drawable.front0`,
  para no pintar con id 0) y no construyen ningún ViewModel. `MainScreenStage`
  añade el `Scaffold` y el bottom menu reales.
  `AppRoot` tiene previews propios para juego, hierba, gimnasio e introducción.
- **Importante:** los `@Preview` no reproducen los insets reales, así que
  `navigationBarsPadding()` vale 0 y la barra se ve pegada al borde inferior
  (en el dispositivo queda sobre la barra de gestos).
- Live Rendering tampoco siempre dibuja el `shadowElevation` de `graphicsLayer`.
  Si en el preview parece un cuadrado alrededor de la Pokéball y en el
  dispositivo no, es el renderizador, no el código.
- Para ver el resultado real, usa Layout Inspector o ejecuta en un dispositivo.

---

## 16. Comandos útiles

```bash
# Compilar
./gradlew assembleDebug

# Instalar en dispositivo o emulador
./gradlew installDebug

# Tests unitarios
./gradlew testDebugUnitTest

# Limpiar
./gradlew clean

# Logs
adb logcat | Select-String "myapplication"

# Lanzar la Activity
adb shell am start -n com.example.myapplication/.MainActivity

# Ver el árbol de dependencias resueltas (útil si cambia una versión de Compose)
./gradlew :app:dependencies --configuration debugCompileClasspath
```

En Windows PowerShell los comandos encadenados usan `;`, no `&&`.

---

## 17. Juego: bucle, estado y persistencia (vertical slice)

Primera rebanada jugable: una zona completa con avance, hierba alta, captura
simple, monedas, principal y reset. El diseño completo vive en
`docs/superpowers/specs/2026-10-15-kanto-clicker-design.md` y el plan en
`docs/superpowers/plans/2026-10-15-vertical-slice.md`.

### Bucle

1. En una zona, cada toque de la Pokéball suma `clickPower` clics de avance.
2. Al alcanzar el umbral de la zona se avanza a la siguiente.
3. En **hierba alta** los toques **no** avanzan: marcan actividad. Un
   temporizador que solo corre si hay toques recientes llena la barra de caminar;
   al completarse aparece un salvaje.
4. Con un encuentro activo, cada toque lanza una Pokéball (captura con
   probabilidad). Al capturar: pasa a *visto* y a *en posesión*; si es la
   primera vez que se captura la especie, paga monedas **una sola vez**.
5. Las monedas se acumulan para futuras compras o recompensas.
6. El reset vacía la run y conserva monedas, visto, especies ya recompensadas,
   medallas, starter y vínculo.

### Archivos

| Archivo | Rol |
|---|---|
| `model/GameData.kt` | `Species`, `Zone`, `Encounter`, `TrainerState`, `LeadOption`, `StarterOption`, `advanceZone()`, `GameSnapshot` |
| `model/Combat.kt` | tipos, `Gym`, fases, proyectiles, `GymState` y reglas puras |
| `model/Catalogs.kt` | `SpeciesCatalog`, `ZoneCatalog`, `GymCatalog`, `SpriteRepository` |
| `data/GameStateStore.kt` | contrato de persistencia + implementación `SharedPreferences` |
| `ui/MainScreen.kt` | pantalla principal + HUD |
| `ui/Overlays.kt` | overlay de captura y selector de principal |

### Estado (`GameUiState`)

Cuatro grupos:

- **meta permanente**: `coins`, `seenSpecies`, `rewardedSpecies`, `medals`,
  `starterSpeciesId`, `starterBond`, `playerName`.
- **run**: `ownedSpecies`, `leadSpeciesId`, `zoneIndex`, `zoneClicks`.
- **transitorio/UI**: `isTallGrass`, `walkProgress`, `encounter`, `gym`,
  `trainer` y la navegación (`contentTab`, `isOptionsOpen`, `isLeadOpen`).
- **derivados**: nombre de zona, `clickPower`, `maxHp`, `isGymReady`,
  `leadName`, `leadType`, `needsStarter`, `leadOptions`, `starterOptions`.

El ViewModel rellena los derivados en `derive()`, así que la UI no calcula nada.
`seenSpecies` (visto) y `rewardedSpecies` (ya pagó monedas) son permanentes;
`ownedSpecies` (en posesión) y el progreso de zona son de la run.

### Persistencia

`GameStateStore` es el contrato (sin detalles de almacenamiento);
`SharedPreferencesGameStateStore` es la implementación real y se construye en
`MainActivity` con `applicationContext`. Se guarda un `GameSnapshot`; el estado
transitorio (encuentro, caminar, animación, navegación y combate) queda fuera.
El archivo de preferencias mantiene el nombre histórico
`shared_prefs/kanto_clicker.xml` para no invalidar partidas existentes.

### Cómo extender el contenido

- **Especie nueva**: añade una entrada a `SpeciesCatalog` en `Catalogs.kt`.
- **Zona nueva**: añade una entrada a `ZoneCatalog.zones` (el orden es la
  posición en el mapa).
- **Gimnasio nuevo**: añade una entrada a `GymCatalog` y referencia su `id` en la
  zona (`gymId`).
- **Ajustar números**: `ENCOUNTER_SECONDS`, `WALK_ACTIVITY_WINDOW_MS`,
  `BASE_CAPTURE_FACTOR`, `PER_OWNED_SPECIES_BONUS`, `BASE_MAX_HP` y
  `STARTER_BOND_CLICK_BONUS` en `MainViewModel`, y los umbrales de `ZoneCatalog`.

> Este flujo es el actual, todavía con contenido en código. La sustitución por
> JSON versionado, modelos normalizados y guardado migrado se planifica en §21.

---

## 18. Cortinilla de arranque

Al abrir la app (arranque en frío) se dibuja una cortinilla a pantalla completa
formada por dos imágenes que juntas la cubren:

- `res/drawable/pokeball_splash_top.png` — parte visual **arriba**; sale subiendo
  por el borde superior.
- `res/drawable/pokeball_splash_bottom.png` — parte visual **abajo**; sale bajando
  por el borde inferior.

Ambas se cargan a la vez; tras una breve pausa se separan en paralelo hasta
desaparecer, dejando ver la app.

### Cómo funciona

- Composable `ui/PokeBallSplash.kt`: un `BoxWithConstraints` a
  `fillMaxSize()` con las dos imágenes, movidas con `graphicsLayer` (la animación
  solo invalida esas capas). El recorrido es la altura de la pantalla, que basta
  para sacarlas por completo.
- `MainActivity` la dibuja encima del resto dentro de un `Box`, y la quita cuando
  `onFinished` avisa. Se recuerda con `rememberSaveable`, así que una **rotación
  no la repite**: solo aparece en un arranque real.

### Ajustes

| Quiero… | Cambio |
|---|---|
| Cambiar la pausa antes de abrirse | `SPLASH_HOLD_MS` en `PokeBallSplash.kt` |
| Cambiar la duración de la apertura | `SPLASH_SLIDE_MS` en `PokeBallSplash.kt` |
| Que reaparezca en cada rotación | Quitar `rememberSaveable` en `MainActivity` |
| Cambiar las imágenes | Sustituye los PNG en `res/drawable/` (nombres en minúsculas) |

---

## 19. Starter, principal, tipos y gimnasios

Segunda rebanada: elección del **starter permanente**, **Pokémon principal**
intercambiable, **ventaja de tipos bidireccional** y el **gimnasio de Brock**.

### Starter (permanente y evolutivo)

Al arrancar, si no hay starter elegido (`needsStarter`), se muestra la
**introducción** del Profesor Oak (`IntroOverlay`, ver §20), que termina con la
elección del starter entre Bulbasaur / Charmander / Squirtle. La elección:

- Es **permanente**: sobrevive a los resets.
- **Cuenta** para el `clickPower` como una especie más en posesión, aunque no sea
  el principal.
- Es el único que sube con **vínculo** (`starterBond`, +1 al ganar un gimnasio):
  mientras esté equipado como principal, da un bonus de clic por nivel
  (`STARTER_BOND_CLICK_BONUS`). La **evolución** por hitos de vínculo queda para
  una etapa posterior.
- Se guarda en `GameSnapshot.starterSpeciesId` y `starterBond`.

### Principal (lead)

`LeadOverlay` (abierto desde el HUD) lista las especies en posesión **de la run**
más el starter, y permite fijar el `leadSpeciesId`. El principal:

- Es **de la run**: al resetear, vuelve al starter.
- **No** aporta un bonus propio de clic: su función principal es su **tipo** en el
  gimnasio. Si el principal es el starter, sí se aplica el bonus de vínculo descrito
  arriba.

### Tipos y daño

La eficacia es **bidireccional** (`PokemonType.effectiveness`): el tipo del
principal se compara con el del gimnasio. `typeDamageMultiplier(leadType, gymType)`
(`model/Combat.kt`) lo traduce a un multiplicador de daño:

| Situación | Multiplicador |
|---|---|
| Súper eficaz | `effectiveness` = `2f` |
| Poco eficaz | `0.5f` |
| Neutro o sin principal | `1f` |

Es una función **pura** para poder testearla en la JVM.

### Gimnasio (Brock)

Una zona con `gymId` **termina** en ese gimnasio: al alcanzar su umbral el
progreso se capa y **no se avanza** hasta ganar la medalla.

El combate es un minijuego por **fases excluyentes** en el `GymOverlay`, a
pantalla completa: durante el combate **desaparece el bottom menu** (barra y
Pokéball). La escena es vertical, con el enemigo arriba-centro y el principal
abajo-centro. El HUD superior resume solo al líder (nombre, tipo, vida y pista);
la **vida del jugador** se muestra en una barra anclada al borde inferior, junto
al sprite del principal, y el botón de rendirse desaparece del HUD (solo queda
en el panel de derrota):

1. **Fase de ataque** (`GymPhase.ATTACKING`): cae una **oleada** de rocas
   (`rock.png`) por varias columnas durante `waveDurationMs` (10 s). La barra de
   **stamina** es ese reloj de oleada: **baja sola por tiempo**, no al romper
   rocas. Tocar una roca la rompe y evita su impacto; si una roca alcanza al
   jugador, le resta `projectileDamage` HP. El objetivo es **aguantar la oleada
   entera** (cada ráfaga lanza entre 1 y `projectilesPerSpawn` rocas a la vez).
2. **Fase expuesta** (`GymPhase.EXPOSED`): al llegar la stamina a 0, el líder deja
   de lanzar rocas (se vacían las activas) y se recupera **por tiempo**
   (`exposureDurationMs`, 2,5 s). Mientras dura, los toques sobre el enemigo le
   restan `potencia de clic × multiplicador de tipo`.
3. **Furia**: cuando al líder le queda **menos del 25 %** de vida (`rage`), sus
   oleadas son **más densas y rápidas** (`rageSpawnIntervalMs`,
   `rageProjectilesPerSpawn`, `rageProjectileTravelMs`); el HUD lo anuncia con
   «¡Enfurecido!» y la barra de stamina se tiñe de rojo.
4. Las fases son **mutuamente excluyentes**: no se puede atacar al líder mientras
   lanza rocas, ni hay rocas que romper mientras está expuesto. Eso cierra el
   antiguo *exploit* de dos manos (atacar con una y defender con la otra).
5. **Ganas** al vaciar la barra del líder; **pierdes** al quedarte sin HP y desde
   el panel de derrota puedes **reintentar** sin coste o **rendirte** (reinicia
   el combate).
6. Al ganar se añade `gymId` a `medals`, se pagan monedas (`Gym.coinReward`) y se
   sale del overlay; en el reset las **medallas persisten**, así que los
   gimnasios ya ganados se **saltan**.

El sprite del enemigo se mapea por `gymId` (Brock → `golem.gif`) y el del jugador
por `leadSpeciesId` (los tres starters tienen back). Para cualquier principal sin
arte se dibuja un **placeholder por tipo** (disco del color del tipo + nombre), de
modo que el combate sigue siendo jugable. La animación de ataque es un sustituto
intercambiable: un *lunge* del sprite y un destello del color del principal en el
punto tocado.

La vida máxima es fija (`BASE_MAX_HP = 5`). Los gimnasios escalan (vida del líder,
duración de oleada, cadencia/velocidad y número de proyectiles, daño). La lógica
de avance vive en `advanceZone(...)` (`model/GameData.kt`), pura y testeable.

### Mecánica del combate (arquitectura)

El ViewModel es dueño de las rocas (`Projectile`: columna, instante de salida y
duración de caída) y corre un **tick de combate** (`GYM_TICK_MS`). En la fase de
ataque descuenta el reloj de oleada, lanza ráfagas (eligiendo columnas al azar y
sin repetir) y detecta las rocas que llegan abajo; durante la furia cambia la
cadencia, el número y la velocidad de las ráfagas. En la fase expuesta solo
descuenta la recuperación. Las transiciones (reloj → expuesto, recuperar → atacar,
impacto → HP, romper → quitar roca) están en `GymCombat`, **funciones puras** sin
reloj ni corrutinas, para poder testearlas en la JVM. La vista **no** recibe
actualizaciones por fotograma: interpola la caída con el reloj de fotogramas a
partir de `spawnedAtMs` y reparte los toques en una única capa `pointerInput`
(enemigo si está expuesto; si no, la roca más cercana).

### Archivos

| Archivo | Rol |
|---|---|
| `model/Combat.kt` | `Gym`, `GymState`, fases, `Projectile`, `GymCombat` y `typeDamageMultiplier()` |
| `model/GameData.kt` | `Zone`, `advanceZone()` y el resto de entidades |
| `model/Catalogs.kt` | `GymCatalog` (Brock) y sprites |
| `ui/Intro.kt` | introducción del Profesor Oak (nombre + starter, ver §20) |
| `ui/Overlays.kt` | selector de principal |
| `ui/GymOverlay.kt` | escena, HUD, sprites, rocas, stamina, expuesto y feedback |

### Estado nuevo (`GameUiState`)

- **Meta**: `medals`, `starterSpeciesId`, `starterBond`.
- **Run**: `leadSpeciesId` (se resetea al starter en `onReset()`).
- **Transitorio**: `gym` (`GymState?`) con vida del líder/jugador, reloj de oleada,
  fase, furia, recuperación y proyectiles.
- Derivados en `derive()`: `maxHp`, `leadName`, `leadType`, `isGymReady`,
  `gymLeaderName`, `needsStarter`, `leadOptions`.

### Cómo extender el contenido

- **Gimnasio nuevo**: añade una entrada a `GymCatalog` y referencia su `id` en
  `ZoneCatalog` (`gymId`). Ajusta `leaderHp`, `waveDurationMs`,
  `exposureDurationMs`, `columns`, `spawnIntervalMs`, `projectilesPerSpawn`,
  `projectileTravelMs`, `projectileDamage`, la pareja `rage*` y `coinReward`.
  Añade su sprite en el mapeo de `GymOverlay.kt` (`enemyGifFor`; si no, placeholder).
- **Principal nuevo con arte**: añade su back en el mapeo de `GymOverlay.kt`
  (`backGifFor`); los recursos deben ir en minúsculas (`bulbasaur_back.gif`).
- **Ajustar el ritmo del combate**: `GYM_TICK_MS` y `COMBAT_FIRST_SPAWN_MS` en
  `MainViewModel`; duración de oleada, exposición, ráfagas y furia por gimnasio en
  `GymCatalog`.
- **Ajustar el starter**: `SpeciesCatalog.starters` y
  `STARTER_BOND_CLICK_BONUS` en `MainViewModel`.
- **Ideas futuras** (con hueco en el modelo, sin implementar): sprites reales de
  ataque (hoy hay *lunge* + destello) y proyectiles que aguantan varios toques
  (`Projectile.hitsRemaining` ya existe; el umbral de furia es
  `RAGE_HP_FRACTION`).

### Tests

`ModelTest` cubre, además de tipos/costes, el `typeDamageMultiplier`
(ventaja/desventaja/neutro), la progresión y el aterrizaje de `Projectile`,
`advanceZone` (capado con gimnasio pendiente, auto-salto con medalla, avance
normal y capado en la última zona) y las reglas de `GymCombat` (romper roca, daño
por impacto, reloj de oleada que expone al líder, fin de la exposición y umbral de
furia). Todo corre en la JVM con `./gradlew testDebugUnitTest`.

---

## 20. Introducción: el Profesor Oak (nombre + starter)

Es la **primera pantalla** de una partida nueva. Se muestra cuando `needsStarter`
es verdadero (`starterSpeciesId == null`).

### Aspecto

- **Fondo blanco** a pantalla completa (tapa también el bottom menu).
- **Retrato de Oak** (`res/drawable/profesor_oak.png`) centrado arriba.
- **Caja de diálogo** ancho completo abajo, **estilo Pokémon**: rectángulo blanco
  con borde negro y esquinas redondeadas.
- Al completar una línea aparece un `▼` como pista para continuar.

### Secuencia y control

Fases (índice entero en `IntroOverlay`):

0. «¡Hola! Soy el Profesor Oak.»
1. «¡Bienvenido al mundo Pokémon!»
2. «Este mundo está habitado por unas criaturas llamadas Pokémon.»
3. **Pregunta el nombre** (interactiva).
4. «¡Encantado, {nombre}! …»
5. **Elige el starter** (interactiva).
6. «¡{starter} será tu compañero! …» → termina la intro.

- **Máquina de escribir**: `LaunchedEffect(phase)` revela letras con
  `TYPE_CHAR_MS` (~35 ms) de retardo.
- **Toque sobre la caja**: si aún se está escribiendo, completa la línea; si ya
  está completa, avanza a la fase siguiente.
- **Nombre**: `OutlinedTextField` + botón *Aceptar* (deshabilitado en blanco; máx.
  12 caracteres). El teclado en pantalla aparece al enfocar el campo y se oculta
  al aceptar; `imePadding()` evita que tape la caja. También acepta con la tecla
  *Hecho*.
- **Starter**: los tres GIF animados en fila, tocables, con nombre y tipo.

### GIF animados sin dependencias

Compose no anima GIFs con `painterResource`, así que `ui/Intro.kt`
los decodifica con `ImageDecoder` (API 28+; el proyecto es minSdk 29) a un
`AnimatedImageDrawable` y lo muestra dentro de un `AndroidView`/`ImageView`. El
filtrado se desactiva (`setFilterBitmap(false)`) porque son pixel-art diminutos
(37–43 px) que se escalan y deben verse nítidos.

### Persistencia del nombre

El nombre es **meta permanente** y se muestra en el HUD («Entrenador: …»). Se
persiste junto al starter en una sola operación:

- `MainViewModel.onFinishIntro(playerName, starterSpeciesId)` fija `playerName`,
  `starterSpeciesId`, `starterBond = 0` y `leadSpeciesId` en un único `mutate`.
- `GameSnapshot.playerName` se guarda bajo la clave `player_name` en
  SharedPreferences.
- Nada se persiste hasta terminar la intro: si se cierra a medias, vuelve a
  empezar (el starter sigue sin elegir).

### Archivos

| Archivo | Rol |
|---|---|
| `ui/Intro.kt` | Introducción completa (fases, máquina de escribir, nombre, starter y GIF animado con `ImageDecoder`) |
| `res/drawable/profesor_oak.png` | Retrato de Oak |
| `res/drawable/bulbasaur.gif`, `charmander.gif`, `squirtle.gif` | Starters animados |

### Cómo ajustarlo

- **Textos**: cadenas `intro_*` en `strings.xml`.
- **Velocidad de escritura**: `TYPE_CHAR_MS` en `Intro.kt`.
- **Longitud del nombre**: `MAX_NAME_LENGTH` en `Intro.kt`.
- **Arte**: sustituir `profesor_oak.png`; para nuevos starters, añadir el GIF y su
  entrada en `starterGif(...)`.

### Botón provisional para testear

El HUD incluye bajo *Cambiar principal* un botón **«Borrar partida (test)»**
(`OutlinedButton`, cadena `action_wipe`) que llama a `MainViewModel.onWipeGame()`:
borra lo persistido (`GameStateStore.clear()`) y vuelve a una primera partida
(la intro de Oak). Es **temporal**, solo para no depender de `adb shell pm clear`
al probar; se eliminará cuando ya no haga falta.

---

## 21. Migración a un catálogo de Pokémon basado en datos

> **Estado:** planificada, no implementada. La app sigue usando `Catalogs.kt`.
> Esta sección define el contrato y el orden de trabajo para sustituirlo sin
> romper las partidas existentes.

### 21.1 Alcance y principios

El primer objetivo **no es meter los 151 Pokémon**, sino migrar el contenido que ya
existe (7 especies, 2 zonas y Brock) conservando el comportamiento actual. Una vez
validado el pipeline, ampliar Kanto es añadir archivos, no tocar lógica.

Principios:

1. **Contenido estático, partida dinámica y reglas separados.**
   - Contenido: especies, tipos, movimientos, evoluciones, zonas y gimnasios.
   - Partida: qué Pokémon posee el jugador, su nivel, vínculo y progreso.
   - Reglas: captura, daño, evolución y efectos; código puro y testeable.
2. **El catálogo no va en Room.** Es contenido de solo lectura, versionado con la
   app. Room añadiría migraciones, consultas y una base de datos para datos que
   caben en assets.
3. **IDs estables e inmutables.** `pikachu`, `raichu-alola`, `ruta1`: nunca el
   nombre visible ni el número de Pokédex como clave. Renombrar una especie no
   cambia su ID.
4. **La partida guarda referencias + instancias, no definiciones.** Un
   `PokemonInstance` ocupa pocos bytes; la definición de especie se carga del
   catálogo.
5. **Todo se valida y se versiona.** `schemaVersion` para el guardado y
   `contentVersion` para el catálogo. Un ID roto debe fallar en tests, no en
   producción.
6. **No modelar lo que todavía no se juega.** Movimientos, habilidades y efectos
   pueden reservarse en el esquema, pero no se implementan hasta que una mecánica
   los use.

### 21.2 Estructura propuesta

```
app/src/main/assets/content/
├── manifest.json       # versión, generación y lista de archivos
├── types.json          # tabla de tipos
├── species.json        # especies base
├── forms.json          # formas regionales/alternativas (opcional al inicio)
├── moves.json          # movimientos (fase de combate)
├── abilities.json      # habilidades y pasivas (fase de efectos)
├── learnsets.json      # movimientos aprendidos por especie/nivel
├── evolutions.json     # relaciones de evolución
├── effects.json        # efectos declarativos reutilizables
├── encounters.json     # tablas de encuentro por zona
├── zones.json          # zonas y referencias a encuentros/gimnasios
└── gyms.json           # líderes y configuración del combate
```

Los assets se leen **una vez** al arrancar. Nada de parsear JSON en cada
recomposición, clic o frame de combate.

### 21.3 Contrato del contenido

#### `manifest.json`

```json
{
  "schemaVersion": 1,
  "contentVersion": "1.0.0",
  "generation": 1,
  "files": [
    "types.json",
    "species.json",
    "moves.json",
    "learnsets.json",
    "evolutions.json",
    "effects.json",
    "encounters.json",
    "zones.json",
    "gyms.json"
  ]
}
```

- `schemaVersion` cambia si cambia la **forma** del JSON.
- `contentVersion` cambia al ajustar datos o balance.
- `generation` permite dividir el catálogo por generación más adelante.
- `files` evita cargar archivos que esa versión de la app no conoce.

> `manifest.schemaVersion` versiona la **forma del catálogo**;
> `GameSnapshot.schemaVersion` versiona la **forma del guardado**. Son independientes.

#### `species.json`

```json
[
  {
    "id": "pikachu",
    "dexNumber": 25,
    "nameKey": "species.pikachu",
    "types": ["electric"],
    "baseStats": {
      "hp": 35,
      "attack": 55,
      "defense": 40,
      "specialAttack": 50,
      "specialDefense": 50,
      "speed": 90
    },
    "captureDifficulty": 0.2,
    "coinReward": 5,
    "spriteKey": "pikachu",
    "learnsetId": "pikachu"
  }
]
```

Reglas:

- `id` y `nameKey` son obligatorios y únicos.
- `types` admite uno o dos tipos; el enum actual de un solo tipo se sustituye por
  una lista.
- `baseStats` puede quedar fuera de la primera migración si el combate aún no los
  usa, pero el esquema debe decidirlo antes de escribir los assets. En el modelo
  de dominio es opcional para no inventar datos que aún no se juegan.
- `learnsetId` es opcional; se rellena cuando exista la mecánica de movimientos.
- `spriteKey` no es una ruta: la implementación de sprites traduce esa clave a
  `R.drawable.*`. Así el contenido no depende de nombres de recursos Android.
- `captureDifficulty` y `coinReward` conservan la semántica actual.

#### `zones.json` y `encounters.json`

La zona referencia una tabla de encuentros; la tabla contiene especies y pesos.
Esto sustituye `wildSpeciesIds` y permite probabilidades distintas por especie.

```json
[
  {
    "id": "ruta1",
    "nameKey": "zone.ruta1",
    "clickRequirement": 10,
    "encounterTableId": "ruta1_grass",
    "gymId": null
  }
]
```

```json
[
  {
    "id": "ruta1_grass",
    "entries": [
      { "speciesId": "pidgey", "weight": 70 },
      { "speciesId": "rattata", "weight": 30 }
    ]
  }
]
```

`gyms.json` conserva los campos actuales de `Gym` (`leaderHp`, oleadas, furia,
`coinReward`…), pero con `nameKey` y `spriteKey` en lugar de nombres y recursos
hardcodeados.

#### `learnsets.json` y `evolutions.json`

Las relaciones se guardan por ID, nunca anidadas dentro de la especie:

```json
[
  {
    "id": "pikachu",
    "moves": [
      { "level": 1, "moveId": "thundershock" },
      { "level": 4, "moveId": "growl" }
    ]
  }
]
```

```json
[
  {
    "id": "pikachu-raichu",
    "from": "pikachu",
    "to": "raichu",
    "trigger": { "kind": "ITEM", "itemId": "thunder-stone" }
  }
]
```

Para evoluciones por nivel, `trigger` sería
`{ "kind": "LEVEL", "level": 16 }`. El validador comprueba que `from` y `to`
existen y que no hay ciclos.

#### `effects.json`

Los efectos se declaran como datos y se resuelven con un intérprete. No debe
haber un `when (speciesId)` por Pokémon.

```json
[
  {
    "id": "static",
    "trigger": "ON_HIT",
    "chance": 0.3,
    "target": "ATTACKER",
    "effect": { "kind": "APPLY_STATUS", "status": "PARALYSIS", "turns": 1 }
  }
]
```

Contrato de dominio sugerido:

```kotlin
sealed interface Effect {
    data class Damage(val amount: Int) : Effect
    data class Heal(val amount: Int) : Effect
    data class ApplyStatus(val status: Status, val turns: Int) : Effect
    data class ModifyStat(val stat: Stat, val stages: Int) : Effect
    data class GrantCurrency(val coins: Long) : Effect
}

enum class EffectTrigger { ON_CAPTURE, ON_CLICK, ON_HIT, ON_GYM_HIT, ON_WAVE_START }

enum class EffectTarget { SELF, ATTACKER, ALLY, ALL }

data class EffectRule(
    val id: String,
    val trigger: EffectTrigger,
    val chance: Float,
    val target: EffectTarget,
    val effect: Effect
)
```

`EffectResolver.resolve(rules, context, random)` devuelve eventos de juego y no
toca Android ni Compose. La aleatoriedad se inyecta para que los tests sean
deterministas. Los efectos se implementan **solo cuando la mecánica que los usa
exista**; hasta entonces, `effects.json` puede estar vacío o no cargarse.

### 21.4 Modelos de dominio

La migración introduce dos conceptos que hoy no existen:

```kotlin
data class SpeciesDefinition(
    val id: String,
    val dexNumber: Int,
    val nameKey: String,
    val types: List<PokemonType>,
    val baseStats: BaseStats? = null,
    val captureDifficulty: Float,
    val coinReward: Int,
    val spriteKey: String,
    val learnsetId: String? = null
)

data class BaseStats(
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val speed: Int
)

data class PokemonInstance(
    val instanceId: String,
    val speciesId: String,
    val level: Int = 5,
    val xp: Int = 0,
    val bond: Int = 0,
    val moves: List<String> = emptyList(),
    val abilityId: String? = null,
    val nickname: String? = null
)
```

- `SpeciesDefinition` es inmutable y vive en el catálogo.
- `PokemonInstance` es estado de partida y se persiste.
- `instanceId` identifica al individuo. Se genera al capturar o al migrar; nunca
  se reutiliza.
- El nivel, XP y movimientos pueden quedar en valores por defecto hasta que el
  combate los necesite, pero el campo existe desde el guardado v2 para no migrar
  dos veces.
- `leadSpeciesId`/`starterSpeciesId` pasan a `leadInstanceId` y a un
  `starter: PokemonInstance?`. El starter conserva su vínculo y nivel aunque se
  resetee la run; `ownedPokemon` contiene solo los individuos de la run y no lo
  duplica.

`GameSnapshot` v2 propuesto:

```kotlin
data class GameSnapshot(
    val schemaVersion: Int = 2,
    val contentVersion: String,
    val playerName: String?,
    val coins: Long,
    val seenSpecies: Set<String>,
    val rewardedSpecies: Set<String>,
    val unknownSpecies: Set<String> = emptySet(),
    val medals: Set<String>,
    val starter: PokemonInstance?,
    val ownedPokemon: List<PokemonInstance>,
    val leadInstanceId: String?,
    val zoneId: String,
    val zoneClicks: Long
)
```

`seenSpecies`, `rewardedSpecies` y `unknownSpecies` siguen siendo IDs de especie
porque son progreso global, no individuos. `zoneId` sustituye a `zoneIndex`:
reordenar el catálogo no debe cambiar en qué zona está el jugador. El `derive()` de
la UI construye las opciones de principal con `ownedPokemon + starter`.

### 21.5 Capa de acceso al contenido

Estructura recomendada:

```
data/content/
├── ContentDataSource.kt      # interfaz: lee texto/bytes de assets
├── AssetContentDataSource.kt # AssetManager + kotlinx.serialization
├── ContentRepository.kt      # carga, indexa y expone GameContent
└── ContentValidator.kt       # integridad y reglas de contenido

model/content/
├── ContentModels.kt          # DTOs/definiciones puras
├── GameContent.kt            # agregado con mapas inmutables
└── EffectResolver.kt         # efectos puros
```

`GameContent` debe exponer índices ya construidos:

```kotlin
data class GameContent(
    val manifest: ContentManifest,
    val species: Map<String, SpeciesDefinition>,
    val moves: Map<String, MoveDefinition>,
    val learnsets: Map<String, Learnset>,
    val evolutions: Map<String, List<Evolution>>,
    val encounters: Map<String, EncounterTable>,
    val zones: List<ZoneDefinition>,
    val gyms: Map<String, GymDefinition>,
    val effects: Map<String, EffectRule>
)
```

Flujo de arranque:

1. `ContentRepository` lee `manifest.json` y los archivos declarados.
2. Deserializa con `kotlinx.serialization` (`ignoreUnknownKeys = false` para
   detectar errores de esquema en debug).
3. `ContentValidator` comprueba integridad referencial.
4. Publica un `StateFlow<ContentState>` (`Loading`, `Ready`, `Error`).
5. Un `ContentGate` mantiene la cortinilla mientras `Loading`; al pasar a `Ready`,
   `MainActivity` crea `MainViewModel` con el `GameContent` ya validado. La UI no
   cambia de forma perceptible.
6. En `Error`, se muestra un mensaje recuperable con la ruta del archivo inválido;
   nunca se arranca con contenido a medias.

En tests, `MainViewModel` recibe un `GameContent` de fixture, sin `AssetManager`.

Los DTOs anotados con `@Serializable` viven en `data/content/` y se mapean a los
modelos puros de `model/content/`. No se anotan los modelos de UI ni los de
dominio: así un cambio de formato JSON no contamina `GameUiState`.

### 21.6 Dependencias que habrá que añadir

| Dependencia | Fase | Uso |
|---|---|---|
| `org.jetbrains.kotlin.plugin.serialization` | 1 | Generar serializers; usar la versión alineada con el Kotlin que aporta AGP 9 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1 | Leer y escribir JSON |
| `androidx.datastore:datastore-preferences` | 4 | Guardado v2 y migración desde SharedPreferences |
| `io.coil-kt.coil3:coil-compose` (opcional) | 6 | Caché de GIF si el catálogo crece |

No se añaden ahora: la migración de la fase 1 puede empezar con el catálogo en
tests sin tocar el guardado ni la UI.

### 21.7 Persistencia y migración del guardado

Se mantiene `kanto_clicker` como nombre histórico, pero se cambia el formato:

- `DataStore<Preferences>` con una clave `save_v2` que contiene JSON serializado.
- `GameSaveStore` como interfaz; `DataStoreGameSaveStore` como implementación.
- `SaveMigrator` lee el `GameSnapshot` legacy de `SharedPreferences` y genera v2.
- No se borra el guardado legacy hasta que v2 se haya escrito correctamente.

Algoritmo de migración:

1. Si existe `save_v2`, cargarlo y validar `schemaVersion`.
2. Si no existe, leer las claves legacy (`coins`, `owned`, `starter`, `lead`…).
3. Convertir cada ID de `owned` en un `PokemonInstance`:
   - `instanceId = "legacy-" + speciesId` (determinista, evita duplicados en la
     primera migración).
   - `level = 5`, `xp = 0`, `bond = 0`, `moves = []`.
   - `starter` se crea aparte con el `starterBond` legacy; no se mete también en
     `ownedPokemon`.
   - `leadInstanceId` apunta al starter o a una instancia de la run; si el ID no
     es válido, cae al starter.
4. Conservar `seenSpecies`, `rewardedSpecies`, `medals`, monedas y progreso.
5. Convertir `zoneIndex` legacy en el `zoneId` de esa posición en el catálogo; si
   está fuera de rango, usar la primera zona y registrar el valor original.
6. Escribir v2 con `schemaVersion = 2` y el `contentVersion` cargado.
7. Si un ID no existe en el catálogo, **no borrar la partida**: registrarlo en
   `unknownSpecies`, omitirlo de la UI y conservarlo en el save. Si el catálogo lo
   recupera en una versión futura, vuelve a estar disponible.
8. Si el JSON está corrupto, exponer un estado de error recuperable; no hacer
   `clear()` silencioso.

Reglas de compatibilidad:

- Los IDs de especie deben ser los mismos que usa el guardado actual
  (`bulbasaur`, `charmander`, `pidgey`…).
- `zoneIndex` legacy se traduce una sola vez a `zoneId`; a partir de v2 la partida
  no depende del orden del catálogo.
- Cada cambio de forma del guardado crea un `SaveMigrator` encadenable; nunca se
  reescriben migradores ya publicados.
- Al cargar v2, un `leadInstanceId` que no exista en `ownedPokemon` ni en `starter`
  cae al starter; si tampoco hay starter, se deja `null` y la UI pide elegir.
- Tests con fixtures de guardados legacy reales, incluidos IDs desconocidos y
  campos ausentes.

### 21.8 Fases de implementación

| Fase | Entrega | Criterio de aceptación |
|---|---|---|
| **0. Contrato** | Esquema JSON, IDs y decisiones de alcance | Revisado y congelado antes de escribir datos |
| **1. Serialización** | `kotlinx.serialization`, DTOs, `ContentValidator` y fixtures | Tests de parseo y validación en verde |
| **2. Contenido actual** | JSON con las 7 especies, 2 zonas y Brock | Paridad exacta con `Catalogs.kt` |
| **3. Repositorio** | `ContentRepository`, `GameContent` y carga desde assets | La app arranca y juega igual; sin `SpeciesCatalog` en el ViewModel |
| **4. Instancias y save v2** | `PokemonInstance`, DataStore y `SaveMigrator` | Un guardado legacy se migra y sobrevive a cerrar/abrir |
| **5. Efectos** | `EffectResolver` y efectos declarativos | Tests deterministas; sin `when` por especie |
| **6. Kanto** | Contenido por generación, sprites y balance | Validación completa y rendimiento aceptable |

Orden obligatorio: **1 → 2 → 3 → 4**. La fase 5 puede posponerse; la 6 no empieza
hasta que las anteriores estén estables.

### 21.9 Validación

`ContentValidator` debe comprobar, como mínimo:

- IDs únicos en cada archivo.
- `manifest.files` existe y no referencia archivos ausentes.
- `species.types` existen en `types.json`.
- `spriteKey` y `encounterTableId` resuelven; `learnsetId` resuelve si está
  presente.
- `evolutions.from`/`to` existen y el grafo no tiene ciclos.
- `zones.gymId` existe; `encounters.speciesId` existe.
- Cada zona tiene al menos una entrada de encuentro válida.
- Los starters son exactamente tres, existen y tienen datos completos.
- Pesos de encuentro > 0; probabilidades y multiplicadores en rango.
- `contentVersion` y `schemaVersion` están presentes.
- Los recursos de sprite referenciados existen (test de integración de recursos).

Los errores deben incluir **archivo, ruta JSON e ID**, no solo “JSON inválido”.

### 21.10 Pruebas

- **Parseo:** cada archivo se deserializa con el esquema actual.
- **Paridad:** el contenido JSON produce las mismas especies, zonas, gimnasio y
  recompensas que `Catalogs.kt` en la fase 2.
- **Validación:** fixtures con ID duplicado, referencia rota, evolución cíclica,
  peso negativo y starter faltante.
- **Guardado:** round-trip v2; migración legacy → v2 con y sin starter/lead;
  IDs desconocidos; JSON corrupto.
- **Efectos:** `EffectResolver` con `Random` sembrado; probabilidad 0 y 1;
  objetivos inválidos.
- **Regresión:** `ModelTest`, `testDebugUnitTest`, `lintDebug` y `assembleDebug`.

### 21.11 Rendimiento y escala

- Cargar una vez y reutilizar mapas inmutables; no leer assets en `derive()`.
- Mantener el JSON pequeño y dividirlo por generación o por dominio si crece.
- Evitar un único archivo de 151 especies con movimientos y evoluciones anidadas.
- No usar reflexión para resolver efectos; `EffectResolver` interpreta un conjunto
  cerrado de tipos.
- Para muchos GIF, sustituir la decodificación manual por Coil/Glide con caché en
  disco y memoria. `ImageDecoder` es suficiente para los tres starters actuales,
  no para un catálogo grande.
- Si se activa R8, conservar las reglas de `kotlinx.serialization`.

### 21.12 Decisiones y riesgos

| Decisión | Elección | Motivo |
|---|---|---|
| Formato del catálogo | JSON en assets | Legible, versionable, editable sin recompilar la lógica |
| Base de datos del catálogo | Ninguna; mapas en memoria | Solo lectura y volumen pequeño |
| Guardado | DataStore + JSON versionado | Menos código que Room y migraciones explícitas |
| Efectos | Datos + intérprete | Evita lógica duplicada por Pokémon |
| IDs | Slug estable | Sobrevive a traducciones y renombrados |

Riesgos principales:

- **Sobreingeniería:** modelar movimientos, IVs, habilidades y efectos antes de
  que exista el combate. Mitigación: campos opcionales y fases separadas.
- **Datos inconsistentes:** las referencias por ID son frágiles sin validator.
  Mitigación: validar en tests y al cargar.
- **Pérdida de partidas:** cambiar claves o limpiar preferencias. Mitigación:
  migrador, cuarentena y no borrar el formato legacy hasta confirmar v2.
- **Rendimiento de imágenes:** cientos de GIF decodificados a la vez. Mitigación:
  caché y carga bajo demanda.
- **Localización:** nombres dentro del JSON. Mitigación: `nameKey` y textos en
  `strings.xml`/recursos por idioma.

### 21.13 Definición de terminado

- [ ] `Catalogs.kt` ya no contiene especies, zonas ni gimnasios hardcodeados.
- [ ] El contenido actual se carga desde assets y supera la prueba de paridad.
- [ ] `ContentValidator` corre en tests y falla con referencias rotas.
- [ ] `PokemonInstance` se persiste y el guardado legacy se migra sin pérdida.
- [ ] `schemaVersion` y `contentVersion` están en el guardado y el manifiesto.
- [ ] Los efectos, si existen, se resuelven sin `when` por especie.
- [ ] `assembleDebug`, `testDebugUnitTest` y `lintDebug` pasan.
- [ ] Las partidas existentes siguen abriendo con `kanto_clicker`.