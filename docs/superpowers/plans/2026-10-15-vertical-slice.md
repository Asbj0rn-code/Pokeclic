# Plan de implementación — Vertical slice

Fecha: 2026-10-15
Alcance: Etapa 0 (modelo + persistencia) y Etapa 1 (bucle completo en una zona).

## Etapa 0 — Modelo + persistencia

1. `model/PokemonType.kt` — enum de tipos + tabla de efectividad
   (`effectiveness(attacker, defender): Float`).
2. `model/Species.kt` — id, nombre, tipo, dificultad, recompensa.
3. `model/Zone.kt` — id, nombre, `clickRequirement`, especie(s) salvaje(s).
4. `model/Upgrade.kt` — id, nombre, coste base, escalado, efecto.
5. `model/GameSnapshot.kt` — subconjunto persistible (meta + run).
6. `model/repository/SpeciesCatalog.kt`, `ZoneCatalog.kt`, `UpgradeCatalog.kt` —
   catálogos data-driven.
7. `model/repository/GameStateStore.kt` (interfaz) +
   `data/SharedPreferencesGameStateStore.kt` (impl. Android).

## Etapa 1 — Bucle en una zona

8. `viewmodel/GameUiState.kt` — estado de juego ampliado (meta + run + UI).
9. `viewmodel/MainViewModel.kt` — extender:
   - clic en pokéball → si no hay hierba: `zoneClicks++`; si hay hierba:
     actividad de caminar.
   - bucle de caminar (temporizador solo con toques recientes) → spawn.
   - bucle CPS → `zoneClicks` si no hay hierba.
   - `capture()` → visto/en posesión + monedas la primera vez.
   - `buyUpgrade()` → gasta monedas.
   - `reset()` → limpia run, conserva meta.
   - avanzar zona al alcanzar el umbral.
   - persistir cambios en el store.
10. UI:
    - `ui/home/GameHud.kt` — monedas, zona, progreso, botones (entrar/salir
      hierba, mejorar, reset).
    - `ui/catch/CaptureOverlay.kt` — captura simple (toque/timing).
    - `ui/map/MapScreen.kt` — mostrar zona actual y progreso.
11. Cableado en `AppRoot`/`MainActivity`: crear el store con `applicationContext`
    e inyectarlo en la factoría del ViewModel.

## Verificación

- `./gradlew.bat assembleDebug` y `testDebugUnitTest` en verde tras Etapa 0 y
  tras Etapa 1.
- Test unitario del modelo: efectividad de tipos y avance de zona.
