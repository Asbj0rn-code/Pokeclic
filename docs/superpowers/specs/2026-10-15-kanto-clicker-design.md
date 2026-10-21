# Kanto Clicker — Documento de diseño

Fecha: 2026-10-15
Estado: aprobado (previo al plan de implementación)

## 1. Intención

Un juego idle/clicker para Android (Kotlin + Jetpack Compose, arquitectura MVVM)
ambientado en Kanto (primera generación) que se entrega como proyecto de la
asignatura de programación móvil.

- **Para quién**: el jugador, con el objetivo de completar las 8 medallas.
- **Qué gana**: un bucle corto y satisfactorio de "clic → capturar → mejorar →
  superar un gimnasio → resetear con más poder".
- **Cómo se ve el éxito**: se puede jugar de principio a fin, el bucle engancha,
  los números están ajustados y todo persiste entre sesiones.
- **Juego finito**: termina al conseguir las 8 medallas de gimnasio.

## 2. Decisiones clave (con por qué)

- **Dos usos del mismo toque (pokéball) según el modo**: en la zona avanzas
  hacia el gimnasio; en la hierba alta "caminas" para encontrar salvajes. Obliga
  a elegir entre empujar o farmear, y reaprovecha la mecánica de toque que ya
  existe.
- **Pokédex en dos registros**: *visto* (permanente, nunca se pierde) y *en
  posesión* (de la run, otorga % de clic, se pierde al resetear). Da peso a la
  decisión de resetear.
- **Monedas permanentes y únicas por especie**: una especie solo paga monedas la
  primera vez que se captura (en cualquier run). Evita farmear la misma especie
  para romper la economía. Los gimnasios también pagan.
- **Reset manual** (no automático): el jugador decide cuándo. Conserva visto,
  monedas, mejoras y starter; pierde la Pokédex en posesión y el progreso de run.
- **Gimnasio como muro con combate por oleadas**: aguantas una oleada de rocas
  mientras la barra de stamina del líder **baja sola por tiempo** (10 s); cuando
  queda **expuesto** (y deja de atacar), los toques sobre él restan vida. Por
  debajo del 25 % de vida el líder entra en **furia** (oleadas más densas y
  rápidas). Las fases son excluyentes, así que no se puede atacar y defenderse a
  la vez. Es el punto donde las mejoras permanentes se notan: daño (potencia de
  clic), margen (vida máxima) y tipo.
- **Tipos bidireccionales sobre el principal**: fuerte contra el tipo del rival
  → ventaja; débil → desventaja. En el gimnasio multiplica el daño; en la
  captura, la dificultad.
- **Starter permanente y evolutivo**: se elige una vez y sobrevive a todos los
  resets; siempre equipable. Es el único Pokémon que sube de nivel de forma
  permanente (mediante *vínculo*), de modo que nunca se empieza una run con las
  manos vacías y existe un ancla afectiva con progresión.

## 3. Objetivo y no-objetivos

**Objetivos**

- Bucle de juego completo y jugable de principio a fin.
- Persistencia local entre sesiones.
- Código MVP limpio, con estado en ViewModels y UI stateless.

**No-objetivos**

- Multijugador / online.
- Contenido más allá de Kanto y las 8 medallas.
- Arte/animación final (se usan los recursos existentes y placeholders).
- Monetización.
- Balance perfecto (los números iniciales son placeholders a testear).

**Anti-objetivos** (fracaso aunque "funcione")

- Bucle aburrido.
- Dos minijuegos (pesca y gimnasio) a medio hacer.
- Números sin equilibrar.
- Contenido inacabado para la entrega.
- Sin persistencia.

## 4. Diseño del sistema

### 4.1 Bucle principal

1. El jugador toca la pokéball en una **zona**. Cada toque suma un clic de zona.
2. Al alcanzar el umbral de clics de la zona (`clickRequirement`, p. ej. 100)
   desbloquea el avance hacia la siguiente zona y, al final, el gimnasio.
3. En **hierba alta** los toques no cuentan para el umbral: hacen *caminar*. Si
   el jugador deja de tocar, no aparece ningún salvaje. Cada X segundos de
   caminar aparece un **salvaje**.
4. **Captura** (minijuego tipo pesca; en el vertical slice, versión simple): si
   se captura, la especie pasa a *visto* (si es la primera vez, paga monedas una
   vez para siempre) y a *en posesión* de la run.
5. **Principal**: cualquier Pokémon en posesión de la run puede equiparse y
   cambiarse en cualquier momento. El starter permanente siempre está
   disponible. El tipo del principal ventaja/penaliza según el rival.
6. Con monedas se compran **mejoras permanentes** y **vínculo** para el starter.
7. El **gimnasio** es un muro: hay que ganar un **combate por fases** (romper
   rocas para dejar al líder expuesto y golpearlo entonces) para obtener la
   medalla. Si no se puede superar, toca mejorar o resetear.
8. **Reset** manual: vacía la Pokédex en posesión y el progreso de run; conserva
   visto, monedas, mejoras, medallas y starter.
9. Al ganar los 8 gimnasios, **fin del juego**.

### 4.2 Entidades de datos

- `PokemonType` (enum): Fire, Water, Grass, Electric, Normal, Rock, ... (solo los
  necesarios).
- `Species`: id, nombre, tipo, dificultad de captura, recompensa de monedas.
- `Zone`: id, nombre, `clickRequirement`, ids de especies salvajes.
- `Upgrade`: id, nombre, coste, efecto, coste incremental por nivel.
- `GameSnapshot`: subconjunto persistible del estado (meta + run).
- `Gym`: id, medalla, tipo, recompensa de monedas, y parámetros del combate
  (vida del líder, duración de oleada y de exposición, columnas de proyectiles,
  cadencia y número por ráfaga, velocidad de caída, daño por impacto y parámetros
  de furia).

### 4.3 Estado

- **Meta (permanente)**: `coins`, `seenSpecies`, `upgrades`, `starterSpeciesId`,
  `starterBond`, `medals`.
- **Run**: `ownedSpecies`, `leadSpeciesId`, `zoneIndex`, `zoneClicks`.
- **Sesión/UI**: `isTallGrass`, progreso de caminar, `encounter` actual, estado
  del minijuego, animación del entrenador.

`ownedSpecies` y `leadSpeciesId` se limpian/ resetean en el reset; el resto de
meta se conserva.

### 4.4 Tipos

Tabla de efectividad simplificada (fuerte contra / débil contra). El tipo del
principal se compara con el tipo del rival:

- Fuerte → multiplica el daño (×2) / captura más fácil.
- Débil → reduce el daño (×0.5) / captura más difícil.
- Neutro → sin modificador.

### 4.5 Persistencia

`GameStateStore` (interfaz, sin dependencias de Android) + implementación con
`SharedPreferences`. El ViewModel depende de la interfaz; la `Activity` construye
la implementación con `applicationContext`. Se serializa un `GameSnapshot` en
claves primitivas (sets y mapas como cadenas delimitadas).

### 4.6 UI / navegación

Se reutiliza el shell existente (`AppRoot`, tabs Map/Home, hoja de opciones).
Pantallas/áreas nuevas:

- **Mapa**: selección de zona, estado de progreso, entrada a hierba alta, acceso
  al gimnasio.
- **Captura**: overlay con el minijuego.
- **Gimnasio**: combate por fases (romper rocas → líder expuesto → golpearlo).
- **Pokédex**: visto / en posesión, equipar principal.
- **Mejoras**: comprar mejoras permanentes y vínculo del starter.
- **Reset** y **fin de juego**.

## 5. Arquitectura

- MVVM con un único `StateFlow<GameUiState>` expuesto por
  `MainViewModel` (extendido de su rol actual de "pokéball/entrenador" a
  ViewModel de juego completo). Alternativa considerada: un `GameViewModel`
  separado; se descarta renombrar para reducir el churn sobre el código ya
  verificado.
- Catálogos data-driven (`ZoneCatalog`, `SpeciesCatalog`, `UpgradeCatalog`) para
  que añadir contenido sea añadir datos, no lógica.
- Lógica de juego en el ViewModel; UI stateless con lambdas de eventos.
- Sin Fragments; Compose puro.

## 6. Plan por etapas

- **Etapa 0** — Modelo + persistencia.
- **Etapa 1** — Vertical slice: una zona, bucle completo (clics → umbral →
  avance, hierba alta con captura simple, visto/en posesión, monedas, una mejora,
  reset).
- **Etapa 2** — Minijuego de gimnasio (combate clicker + tipos) y 1 medalla.
- **Etapa 3** — Contenido de Kanto como datos (zonas, especies, 8 gimnasios) +
  minijuego de pesca real.
- **Etapa 4** — Balance y pulido.

## 7. Decisiones dejadas al constructor

- Valores concretos de umbrales, costes, cadencias y velocidades (placeholders).
- Estructura exacta del árbol de mejoras (propuesta: potencia de clic, % por
  especie, vida máxima, probabilidad de captura, vínculo del starter).
- Detalles del minijuego de pesca (se pospone a la Etapa 3; en la Etapa 1 la
  captura es simple).
- Diseño visual concreto de cada pantalla.

## 8. Resolución del builder check

Estas cuestiones las plantea un constructor ajeno al documento; se resuelven
aquí para no dejarlas a la improvisación.

1. **Condición de victoria del gimnasio.** El líder tiene una barra de vida y una
   barra de **stamina** que es el **reloj de la oleada**: baja por tiempo mientras
   caen ráfagas de rocas por varias columnas. El jugador debe aguantar la oleada
   (10 s) rompiendo rocas; las que impactan restan HP. Al agotarse la stamina el
   líder queda **expuesto**: deja de lanzar y se recupera por tiempo; solo
   entonces los toques sobre el enemigo restan
   `potencia de clic × multiplicador de tipo` (×2 fuerte, ×0.5 débil, ×1 neutro).
   Por debajo del **25 %** de vida el líder entra en **furia**: ráfagas más
   numerosas y rápidas. **Se gana vaciando la barra del líder con HP > 0; se
   pierde al quedarse sin HP (reintento sin coste).** Las fases excluyentes
   eliminan el exploit de atacar y defender a la vez. El HP máximo es una mejora
   permanente (base 5) y los gimnasios escalan en vida, oleada, cadencia, número y
   velocidad de rocas y daño, de modo que un HP alto (cada vez más caro) no
   trivializa el combate. La economía mantiene honesta la rama de vida. *(El
   vertical slice no implementa gimnasios; es la Etapa 2.)*
2. **Zonas ↔ gimnasios.** Patrón por "tramo": varias zonas y, al final del
   tramo, un gimnasio. Al ganar, se obtiene la medalla y se avanza de tramo. En
   el reset, `zoneIndex` vuelve a 0 pero las medallas persisten: las zonas se
   re-recorren y los gimnasios ya ganados **se saltan automáticamente** (no se
   repiten), de modo que la run consiste en alcanzar el siguiente gimnasio no
   superado.
3. **Caminar en la hierba alta.** El progreso de "caminar" avanza por un
   temporizador que **solo corre mientras hay toques recientes** (p. ej. último
   toque hace < 1 s). Si el jugador deja de tocar, el temporizador se detiene y
   no aparece ningún salvaje. Al llegar a X segundos aparece un encuentro y el
   progreso se reinicia. Así se respeta "cada X segundos" y "si dejas de tocar,
   no sale nada".
4. **Origen del % de clic.** El bonus de clic es la **suma de todas las especies
   en posesión** (cada una aporta su %), no solo el principal. El starter
   permanente cuenta siempre como poseída para este bonus (más su bonus de
   vínculo). El **principal solo aporta el tipo** para los emparejamientos.
5. **Starter: vínculo y evolución.** El starter se elige la primera vez
   (onboarding) entre Bulbasaur/Charmander/Squirtle. El *onboarding* es una
   introducción con el Profesor Oak (nombre del entrenador + máquina de escribir
   + elección del starter); el nombre es meta permanente y se muestra en el HUD.
   El *vínculo* sube con
   medallas y runs completadas; cada nivel otorga +% de clic cuando va equipado y
   refuerza su ventaja de tipo. Evoluciona en hitos de vínculo (p. ej. niveles 5
   y 10), desbloqueando un bonus mayor. *(Definido aquí; su implementación es
   posterior al vertical slice.)*
6. **¿Sobrevive el CPS?** Sí. El ingreso pasivo actual se mantiene y suma clics
   al progreso de la zona **solo cuando no se está en hierba alta**; nunca
   avanza el "caminar".
7. **Navegación.** Se conservan las 2 pestañas (Map, Home) y la hoja de opciones.
   Las pantallas nuevas (Pokédex, Mejoras, selección de starter) son **overlays a
   pantalla completa** lanzados desde Home; captura y gimnasio son **overlays
   modales**. Así no se vuelve a tocar el shell.

## 9. Notas para el constructor

- El proyecto actual ya tiene el refactor MVVM verificado (`assembleDebug` y
  `testDebugUnitTest` en verde): `MainViewModel`, `MainUiState`,
  `NavigationViewModel`, `AppRoot`, `MainScreen`, `MapScreen`, `OptionsSheet`.
- El combate de gimnasio es un **modo** a pantalla completa: durante el combate se
  oculta el bottom menu (barra y pokéball). La escena muestra al enemigo arriba y
  al principal abajo; una única capa de toque decide entre golpear al enemigo
  expuesto o romper la roca más cercana. Las reglas de fase/oleada/furia viven
  en `GymCombat` (puras) y el ViewModel solo aporta el reloj y el spawn.
- Mantener `DOCUMENTACION.md` actualizado con cada etapa.
