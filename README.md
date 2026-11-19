# Pokeclic

Pokeclic es un juego **clicker** para Android basado en la primera generación de
Pokémon. Avanzas por Kanto pulsando la Pokébola, aparecen Pokémon salvajes
mientras caminas, los capturas en un minijuego de barra y desafías a los líderes
de gimnasio. El objetivo es conseguir las 8 medallas y completar las 151 entradas
de la Pokédex.

- **13 zonas jugables** de Kanto, con fondo animado propio en 8 de ellas.
- **Minijuego de captura** de riel vertical con barra y dificultad por especie.
- **Pokédex de 151 especies** con estados avistado / registrado / capturado.
- **Mapa de Kanto** con zoom, desplazamiento e indicador de la zona tocada.
- **3 gimnasios** con mecánicas distintas: rocas (Brock), anillos (Misty) y
  cuadrícula de Voltorb (Lt. Surge).
- **Introducción del Profesor Oak** y tutorial guiado para partidas nuevas.

## Stack

| Elemento | Valor |
|---|---|
| Lenguaje | Kotlin |
| Android Gradle Plugin | 9.1.1 (Kotlin integrado) |
| `compileSdk` / `targetSdk` | 37 |
| `minSdk` | 29 |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | MVVM (View / ViewModel / Model / Data) |
| Estado | `StateFlow` + `collectAsStateWithLifecycle()` |
| Persistencia | `SharedPreferences` con `schema_version` |

## Requisitos

- Android Studio con soporte para AGP 9 (JDK 17 o superior en el IDE).
- SDK de Android 37 instalado.
- Un dispositivo o emulador con Android 10 (API 29) o superior.

## Compilar y ejecutar

```bash
# Compilar el APK de debug
./gradlew assembleDebug

# Instalar en el dispositivo o emulador conectado
./gradlew installDebug

# Tests unitarios de la JVM
./gradlew testDebugUnitTest
```

En Windows PowerShell los comandos encadenados usan `;`, no `&&`.

## Documentación

- [`DOCUMENTACION.md`](DOCUMENTACION.md): referencia técnica del proyecto
  (arquitectura, pantallas, minijuegos, persistencia, recursos y guías de
  extensión).
- [`ISSUES.md`](ISSUES.md): auditoría histórica de bugs y riesgos (20/10/2026);
  el estado actual vive en `DOCUMENTACION.md`.
- [`docs/apariciones-planificadas.md`](docs/apariciones-planificadas.md):
  encuentros salvajes de las ubicaciones de Kanto aún no jugables.
- [`docs/plan-migracion-contenido.md`](docs/plan-migracion-contenido.md):
  contrato del catálogo de contenido basado en datos (pendiente).
- [`docs/superpowers/`](docs/superpowers/): diseño original y plan de la primera
  rebanada jugable.

## Estructura

```
app/src/main/java/com/dsrs/pokeclic/
├── MainActivity.kt        # Host único: Composition + factorías de ViewModel
├── data/                  # Persistencia, carga de assets, mapeo de Kanto
├── dbg/                   # Puente con el editor de mapa (solo debug)
├── model/                 # Entidades, reglas puras, catálogos y Pokédex
├── ui/                    # Composables sin estado + tema retro
└── viewmodel/             # MainViewModel, MapViewModel y motores de juego
```

Los tests unitarios viven en `app/src/test/` y corren en la JVM; no hay tests de
instrumentación.
