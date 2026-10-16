# ⛏️ Proyecto-Minecraft · Mod de Enanos (Fantasía)

Mod personal para **Minecraft Java 26.3** con **Fabric** y **GeckoLib 5.5.7**. Agrega una civilización enana a las aldeas de montaña y nieve: cuatro oficios, un guerrero con tres rangos, comercio, amistad, mineros contratables y armaduras con modelos 3D propios.

> Estado: en desarrollo. Lo marcado como *sin probar* está generado y compilado en parte, pero aún no verificado en el juego.

---

## 🧙 Los enanos

| Rol | Puesto de trabajo | Qué hace |
| :-- | :-- | :-- |
| **Herrero** | Yunque | Vende herramientas con Eficiencia, armaduras por niveles y, a nivel 5, las herramientas enanas. |
| **Cervecero** | Barril | Vende 4 brebajes con efectos de larga duración. |
| **Minero** | Alto horno | Vende minerales. Contratado, excava túneles desde el Estandarte Minero y guarda el botín en un cofre. |
| **Guerrero** | Campana de la aldea | Vigila de noche (no duerme) y pelea con un hacha a dos manos. |

Aparecen en aldeas de biomas de montaña y nevados, hasta 4 por aldea.

## 🪓 Guerreros: tres rangos

Aparecen con un 60 % / 30 % / 10 % de probabilidad. Usan las mismas animaciones (`idle_axe`, `walk_axe`, `attack_axe`); cambia el equipo y las estadísticas.

| Nivel 1 · Cobre | Nivel 2 · Guardia *(nuevo, sin probar)* | Nivel 3 · Veterano |
| :-: | :-: | :-: |
| Vida +10 · Armadura +4 · Daño +2 | Vida +20 · Armadura +8 · Daño +4 | Vida +30 · Armadura +12 · Daño +7 |

**Nivel 1 (cobre)** — secuencia de ataque y caminata:

![Guerrero nivel 1](Mod%20Minecraft/art/blockbench/preview_warrior_1.png)

**Nivel 2 (guardia)** — casco redondo con cruz, barba roja, peto de acero sobre malla:

![Guerrero nivel 2](Mod%20Minecraft/art/blockbench/preview_warrior_2.png)

**Nivel 3 (veterano)** — armadura de placas completa:

![Guerrero nivel 3](Mod%20Minecraft/art/blockbench/preview_warrior_3.png)

> Estas vistas son renders planos de los modelos (sin texturas) generados por `art/make_dwarf_geo.py`. Para verlos con textura abre los `.bbmodel` en Blockbench.

## 🛡️ Armaduras y herramientas

Todas se consiguen comerciando con el herrero. Las armaduras de guerrero tienen modelo 3D al ponértelas y son las únicas armaduras que vende (las antiguas de bronce/acero/enano ya no están a la venta).

| Set | Compara con | Protección (set completo) | Durabilidad | Dureza |
| :-- | :-- | :-: | :-: | :-: |
| Guerrero cobre (herrero nv. 2) | Cobre (10) → **11** | +10 % | +18 % | 0 |
| Guerrero acero (nv. 3) | Hierro (15) → **18** | +20 % | +20 % | 0 |
| Guerrero veterano (nv. 5) | Diamante (20) → **23** | +15 % | +15 % | 2 → 2.3 |

> La protección va en puntos enteros por pieza, por eso el cobre queda en +10 % (11 en vez de 11.5).


![Armadura enana](Mod%20Minecraft/art/out/preview.png)

![Herramientas enanas](Mod%20Minecraft/art/out/tools.png)

Herramientas enanas: 1850 de durabilidad, velocidad 8.6 y +3.5 de daño (entre diamante y netherita).

## 🍺 Brebajes

![Brebajes](Mod%20Minecraft/art/out/brews.png)

| Brebaje | Efecto |
| :-- | :-- |
| Ale | Resistencia I |
| Mead | Prisa II |
| Stout | Fuerza I |
| Spirit | Resistencia II + Fuerza I + Prisa II |

## 🤝 Sistema de amistad

| Etapa | Puntos | Beneficio |
| :-- | :-: | :-- |
| Desconfiado | 0 | Precios +25 % |
| Conocido | 15 | Precios normales |
| Amigo | 50 | −10 %; puede seguirte (Shift + mano vacía) |
| De confianza | 110 | −20 %; se puede contratar a los mineros (8 esmeraldas) |
| Aliado | 200 | −30 % |

Se suma comerciando (+1), defendiéndolos (+4) o con regalos agachado (diamante, manzana dorada, brebajes, oro, comida).
Con **Alt + clic central** sobre un enano se abre su panel (nivel, XP, amistad).

## ⛏️ Estandarte Minero

Controla a los mineros contratados en 32 bloques: túnel de 1×1, 2×2 o 3×3, profundidad de 5 a 64, botón Mine/Stop. Al llenarse el inventario o de noche, depositan lo extraído en un cofre cercano.

## 🎮 Cómo probarlo

1. Necesitas **JDK 25**. Doble clic en `Mod Minecraft/Lanzar Mod Enanos.bat` (o `gradlew runClient` dentro de `Mod Minecraft/dwarfmod`).
2. Invoca enanos con el comando (requiere permisos de operador):
   - `/dwarf blacksmith`, `/dwarf brewer`, `/dwarf miner`
   - `/dwarf warrior` (rango al azar) o `/dwarf warrior 1|2|3`
3. Para ver la armadura nivel 2: sube de nivel al herrero hasta el 3 y cómprala, o créala con `/give`.

## 🎨 Cómo se generan los modelos

Los modelos y texturas salen de scripts de Python (necesitan Python 3 con `pillow` y `numpy`); no se editan a mano los archivos generados.

```bash
cd "Mod Minecraft/art"
python make_dwarf_geo.py     # enano GeckoLib, animaciones, texturas, .bbmodel y previews
python make_bbmodel.py       # armaduras 3D del jugador (WarriorGearModel.java)
python make_warrior_items.py # íconos y archivos de los sets de guerrero
```

Los archivos para Blockbench quedan en `Mod Minecraft/art/blockbench/` (requiere el plugin GeckoLib).

## 🗺️ Pendiente

- [ ] Probar en el juego el guerrero nivel 2 y su armadura (y hacer sus íconos a juego).
- [ ] Corregir `BannerGameTest`: falla en «No dwarf became working» tras pulsar *Mine*.
- [ ] Goblin: ya hay modelo, animaciones y textura en `art/goblin/`, pero falta la entidad en Java.
- [ ] Sonidos propios (hoy usan los del aldeano).
- [ ] Estructuras propias (minas, tabernas, fortalezas).
- [ ] Recetas en la mesa de herrería como alternativa al comercio.

## 📁 Estructura

```
Mod Minecraft/
├── dwarfmod/   código del mod (Fabric + GeckoLib)
├── art/        scripts de arte, modelos Blockbench y previews
├── MODLOG.md   bitácora técnica del desarrollo
└── Lanzar Mod Enanos.bat
```
