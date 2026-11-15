# Apariciones de Pokémon en Kanto

Plan de encuentros salvajes de Pokeclic. Las listas **activas** viven en
`ZoneCatalog` (`app/src/main/java/com/dsrs/pokeclic/model/Catalogs.kt`) y las
bloquean los tests; este documento recoge la correspondencia con el mapa y las
apariciones **planificadas** de las ubicaciones que todavía no son jugables. No
duplica las listas activas.

El documento es autosuficiente: cuando se retire la carpeta temporal de
referencia, conserva las asignaciones futuras y las decisiones necesarias para
implementarlas.

## Convenciones

- **IDs de ubicación** (columna «Ubicación»): numeración de
  `app/src/main/assets/kanto_locations.json`. **No son IDs de `ZoneCatalog`**:
  una zona futura todavía no tiene ID de zona.
- **IDs de especie**: claves estables de `SpeciesCatalog`; se acompaña el número
  de la Pokédex nacional como `id (#dex)`.
- Un solo tipo por especie, según `app/src/main/assets/pokedex_catalogo.json`.
- El selector de encuentros es **uniforme** (`wildSpeciesIds.random`): todas las
  especies de una zona tienen la misma probabilidad, sin pesos ni rarezas.
- La recompensa de captura es **única por especie**: se cobra la primera vez y
  no vuelve a pagarse aunque la especie aparezca en varias ubicaciones.
- Sin encuentros en pueblos/ciudades (26–35) ni en la Liga Pokémon (47).
- **Mew (#151)** no tiene apariciones por decisión expresa.

## Zonas jugables y correspondencia

| Zona (`ZoneCatalog`) | Nombre mostrado | Ubicación de referencia | ¿Encuentros? |
|---|---|---|---|
| `pueblo_paleta` | Pueblo Paleta | 26 · Pueblo Paleta | No (ciudad) |
| `ruta1` | Ruta 1 | 1 · Ruta 1 | Sí |
| `ciudad_verde` | Ciudad Verde | 27 · Ciudad Verde | No (ciudad) |
| `ruta2` | Ruta 2 | 2 · Ruta 2 | Sí |
| `bosque_verde` | Bosque Verde | 36 · Bosque Verde | Sí |
| `ciudad_plateada` | Ciudad Plateada | 28 · Ciudad Plateada | No (ciudad) |
| `ruta3` | Ruta 3 | 3 · Ruta 3 | Sí |
| `mt_moon` | Mt.Moon | 37 · Monte Moon | Sí |
| `ruta4` | Ruta 4 | 4 · Ruta 4 | Sí |
| `ciudad_celeste` | Ciudad Celeste | 29 · Ciudad Celeste | No (ciudad) |
| `ruta5` | Ruta 5 - Vía Subterránea | 5 · Ruta 5 | Sí |
| `ruta6` | Ruta 6 | 6 · Ruta 6 | Sí |
| `ciudad_carmin` | Ciudad Carmín | 30 · Ciudad Carmín | No (ciudad) |

> Las listas activas son las de `ZoneCatalog` y las comprueba `CatalogTest`; no
> se copian aquí para no duplicar la fuente. Ya reflejan el plan definitivo: por
> ejemplo, Rattata no aparece en la Ruta 6 (sí en las Rutas 1, 2 y 4).

## Apariciones planificadas (28 ubicaciones)

Quedan **332 relaciones** especie-ubicación y **121 especies** pendientes de
aparecer; el total del plan es 374 relaciones (42 activas y 332 futuras). Las
tablas están ordenadas por ID de referencia.

### Rutas futuras (7–25)

| Ubicación | Nombre | Especies previstas |
|---|---|---|
| 7 | Ruta 7 | pidgey (#16), vulpix (#37), oddish (#43), meowth (#52), growlithe (#58), bellsprout (#69) |
| 8 | Ruta 8 | pidgey (#16), ekans (#23), sandshrew (#27), vulpix (#37), meowth (#52), growlithe (#58) |
| 9 | Ruta 9 | charmander (#4), rattata (#19), spearow (#21), ekans (#23), sandshrew (#27) |
| 10 | Ruta 10 | spearow (#21), ekans (#23), sandshrew (#27), psyduck (#54), tentacool (#72), slowpoke (#79), krabby (#98), voltorb (#100), horsea (#116), magikarp (#129) |
| 11 | Ruta 11 | squirtle (#7), spearow (#21), ekans (#23), sandshrew (#27), psyduck (#54), tentacool (#72), slowpoke (#79), farfetchd (#83), drowzee (#96), krabby (#98), horsea (#116), magikarp (#129) |
| 12 | Ruta 12 | pidgey (#16), oddish (#43), gloom (#44), venonat (#48), psyduck (#54), bellsprout (#69), weepinbell (#70), tentacool (#72), slowpoke (#79), krabby (#98), horsea (#116), magikarp (#129), snorlax (#143) |
| 13 | Ruta 13 | pidgey (#16), pidgeotto (#17), oddish (#43), gloom (#44), venonat (#48), psyduck (#54), bellsprout (#69), weepinbell (#70), tentacool (#72), slowpoke (#79), krabby (#98), horsea (#116), magikarp (#129), ditto (#132) |
| 14 | Ruta 14 | pidgey (#16), pidgeotto (#17), oddish (#43), gloom (#44), venonat (#48), bellsprout (#69), weepinbell (#70), hypno (#97), ditto (#132) |
| 15 | Ruta 15 | ivysaur (#2), pidgey (#16), pidgeotto (#17), oddish (#43), gloom (#44), venonat (#48), bellsprout (#69), weepinbell (#70), hypno (#97), mr_mime (#122) |
| 16 | Ruta 16 | rattata (#19), raticate (#20), spearow (#21), persian (#53), doduo (#84), eevee (#133), porygon (#137), snorlax (#143) |
| 17 | Ruta 17 | rattata (#19), raticate (#20), spearow (#21), fearow (#22), persian (#53), doduo (#84) |
| 18 | Ruta 18 | rattata (#19), raticate (#20), spearow (#21), fearow (#22), persian (#53), doduo (#84), dodrio (#85), hitmonlee (#106), hitmonchan (#107), lickitung (#108) |
| 19 | Ruta 19 | wartortle (#8), psyduck (#54), tentacool (#72), tentacruel (#73), slowpoke (#79), krabby (#98), kingler (#99), horsea (#116), seadra (#117), staryu (#120), magikarp (#129), gyarados (#130) |
| 20 | Ruta 20 | psyduck (#54), poliwhirl (#61), tentacool (#72), tentacruel (#73), slowpoke (#79), shellder (#90), krabby (#98), kingler (#99), horsea (#116), seadra (#117), staryu (#120), magikarp (#129), gyarados (#130) |
| 21 | Ruta 21 | psyduck (#54), poliwhirl (#61), tentacool (#72), tentacruel (#73), slowpoke (#79), shellder (#90), krabby (#98), kingler (#99), tangela (#114), horsea (#116), seadra (#117), staryu (#120), magikarp (#129), gyarados (#130) |
| 22 | Ruta 22 | rattata (#19), spearow (#21), psyduck (#54), mankey (#56), poliwag (#60), slowpoke (#79), goldeen (#118) |
| 23 | Ruta 23 | pidgeot (#18), spearow (#21), fearow (#22), ekans (#23), arbok (#24), sandshrew (#27), sandslash (#28), psyduck (#54), mankey (#56), primeape (#57), poliwag (#60), poliwhirl (#61), slowpoke (#79), dodrio (#85), goldeen (#118), gyarados (#130) |
| 24 | Ruta 24 | bulbasaur (#1), caterpie (#10), metapod (#11), butterfree (#12), weedle (#13), kakuna (#14), beedrill (#15), pidgey (#16), oddish (#43), psyduck (#54), abra (#63), bellsprout (#69), tentacool (#72), slowpoke (#79), krabby (#98), horsea (#116), magikarp (#129) |
| 25 | Ruta 25 | caterpie (#10), metapod (#11), butterfree (#12), weedle (#13), kakuna (#14), beedrill (#15), pidgey (#16), oddish (#43), psyduck (#54), abra (#63), bellsprout (#69), slowpoke (#79), goldeen (#118) |

### Ubicaciones especiales futuras (38–46)

| Ubicación | Nombre | Especies previstas |
|---|---|---|
| 38 | Cueva Diglett | diglett (#50), dugtrio (#51) |
| 39 | Túnel Roca | zubat (#41), mankey (#56), machop (#66), geodude (#74), graveler (#75), onix (#95) |
| 40 | Torre Pokémon | gastly (#92), haunter (#93), cubone (#104) |
| 41 | Central de Energía | pikachu (#25), magnemite (#81), magneton (#82), voltorb (#100), electrode (#101), electabuzz (#125), zapdos (#145) |
| 42 | Islas Espuma | blastoise (#9), zubat (#41), golbat (#42), psyduck (#54), golduck (#55), poliwhirl (#61), tentacruel (#73), slowpoke (#79), slowbro (#80), seel (#86), dewgong (#87), shellder (#90), krabby (#98), horsea (#116), jynx (#124), gyarados (#130), lapras (#131), articuno (#144) |
| 43 | Zona Safari | venusaur (#3), nidoran_f (#29), nidorina (#30), nidoran_m (#32), nidorino (#33), paras (#46), parasect (#47), venonat (#48), venomoth (#49), psyduck (#54), poliwag (#60), slowpoke (#79), doduo (#84), exeggcute (#102), rhyhorn (#111), chansey (#113), kangaskhan (#115), goldeen (#118), seaking (#119), scyther (#123), pinsir (#127), tauros (#128), dratini (#147), dragonair (#148) |
| 44 | Mansión Pokémon | charmeleon (#5), rattata (#19), raticate (#20), vulpix (#37), growlithe (#58), ponyta (#77), rapidash (#78), grimer (#88), muk (#89), koffing (#109), weezing (#110), magmar (#126), ditto (#132), omanyte (#138), kabuto (#140) |
| 45 | Calle Victoria | charizard (#6), arbok (#24), raichu (#26), sandslash (#28), nidoqueen (#31), nidoking (#34), clefable (#36), ninetales (#38), wigglytuff (#40), zubat (#41), golbat (#42), vileplume (#45), primeape (#57), arcanine (#59), poliwrath (#62), alakazam (#65), machop (#66), machoke (#67), machamp (#68), victreebel (#71), geodude (#74), golem (#76), cloyster (#91), gengar (#94), onix (#95), exeggutor (#103), marowak (#105), rhydon (#112), starmie (#121), vaporeon (#134), jolteon (#135), flareon (#136), omastar (#139), kabutops (#141), aerodactyl (#142), moltres (#146) |
| 46 | Cueva Celeste | golbat (#42), parasect (#47), psyduck (#54), golduck (#55), primeape (#57), poliwag (#60), poliwhirl (#61), kadabra (#64), machoke (#67), geodude (#74), graveler (#75), slowpoke (#79), slowbro (#80), magneton (#82), electrode (#101), goldeen (#118), gyarados (#130), ditto (#132), dragonite (#149), mewtwo (#150) |

## Decisiones de reubicación y asignación

- **Iniciales y sus líneas**: Bulbasaur 24 → Ivysaur 15 → Venusaur 43;
  Charmander 9 → Charmeleon 44 → Charizard 45; Squirtle 11 → Wartortle 19 →
  Blastoise 42. Elegir inicial no adelanta su aparición salvaje.
- **Evoluciones por piedra o intercambio**: se concentran en Calle Victoria (45):
  Nidoqueen, Nidoking, Clefable, Ninetales, Wigglytuff, Vileplume, Arcanine,
  Poliwrath, Alakazam, Machamp, Victreebel, Golem, Cloyster, Gengar, Exeggutor,
  Starmie, Vaporeon, Jolteon y Flareon.
- **Fósiles**: Omanyte y Kabuto en Mansión Pokémon (44); Omastar, Kabutops y
  Aerodactyl en Calle Victoria (45).
- **Legendarios**: Articuno en Islas Espuma (42), Zapdos en Central de Energía
  (41), Moltres en Calle Victoria (45) y Mewtwo en Cueva Celeste (46). Mew queda
  sin apariciones.
- **Reubicaciones que explican las listas**:
  - Poliwhirl: se retira de las Rutas 6, 22 y 25; queda en 20, 21, 23, 42 y 46.
  - Gyarados: solo 19, 20, 21, 23, 42 y 46 (sin rutas tempranas de pesca).
  - Lapras: Islas Espuma (42).
  - Jynx: Islas Espuma (42).
  - Hitmonlee y Hitmonchan: Ruta 18.
  - Mr. Mime: Ruta 15.
  - Farfetch'd: Ruta 11 (desde la Ruta 6).
  - Eevee y Porygon: Ruta 16 (desde la Ruta 7).
  - Ponyta y Rapidash: Mansión Pokémon (44).
  - Magmar: Mansión Pokémon (44), porque Monte Ascuas no existe en el catálogo.
  - Shellder: Rutas 20, 21 e Islas Espuma (42), sin ciudades.
- **Sin pesca ni surf**: el proyecto no separa métodos de encuentro; una especie
  con aparición en una ruta puede salir como cualquier otra.

## Cómo habilitar una ubicación futura

1. Crear la zona en `ZoneCatalog` cuando se desarrolle su contenido.
2. **Preservar la compatibilidad del índice de zona guardado**: `zoneIndex` se
   persiste y el índice es la posición en el mapa. Cualquier inserción que
   desplace zonas existentes necesita una migración o una decisión explícita; no
   se impone aquí una regla de «añadir siempre al final» ni una progresión
   concreta: se revisará al desarrollar esas zonas.
3. Rellenar `wildSpeciesIds` con las especies de este documento, sin mover
   especies entre ubicaciones.
4. No inventar IDs de `ZoneCatalog` antes de crear la zona: el ID numérico de
   este documento es el de `kanto_locations.json`.
5. Mantener las ciudades sin encuentros y Mew fuera de las listas.

## Pendiente antes de habilitar los iniciales salvajes

Cuando una zona incluya a Bulbasaur, Charmander o Squirtle (Rutas 24, 9 y 11),
hay que revisar antes dos problemas:

- **Doble bonus de clic**: `onFinishIntro` no añade el starter a `ownedSpecies`,
  pero `GameUiState.derive` cuenta `ownedSpecies.size + 1`. Si se captura
  salvaje el inicial elegido, entraría en `ownedSpecies` y su +5 % se contaría
  dos veces.
- **Recompensa no cobrada en la intro**: elegir inicial no lo añade a
  `rewardedSpecies`, así que su primera captura salvaje pagaría la recompensa de
  la especie.

No se corrige ahora: ni ViewModel, ni intro, ni bonus, ni recompensas. Debe
revisarse antes de activar esas rutas. Capturar salvaje un inicial **no elegido**
no duplica nada: se comporta como una especie normal.
