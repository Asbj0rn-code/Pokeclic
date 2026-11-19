# Plan de migración — catálogo de contenido basado en datos

> Documento de diseño **pendiente de implementación**, extraído de la versión anterior de
> `DOCUMENTACION.md` para no perder el contrato acordado. El estado actual del
> proyecto y el punto de partida están en `DOCUMENTACION.md` §21.

## Plan de migración a un catálogo de Pokémon basado en datos

> **Estado:** planificada, no implementada. La app sigue usando `Catalogs.kt`.
> Este documento define el contrato y el orden de trabajo para sustituirlo sin
> romper las partidas existentes. Los números de sección (`21.x`) se conservan
> del documento original.

### 21.1 Alcance y principios

El primer objetivo **no era meter los 151 Pokémon**, sino migrar el contenido que
existía al escribirse el plan (7 especies, 2 zonas y Brock) conservando el
comportamiento de entonces. Hoy el contenido es mucho mayor (151 especies, 13
zonas y 3 gimnasios), pero el pipeline y las reglas de la migración siguen
siendo las mismas: una vez validado, ampliar Kanto es añadir archivos, no tocar
lógica.

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

`gyms.json` conserva los campos actuales de `Gym` (`leaderHp`, postura, furia,
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

---

