# El Sistema 1.4.0 (mod de Fabric para Minecraft 1.21.1)

Despierta como Jugador: nivel (hasta 500, con prestigio), rango de cazador (E, D, C, B, A, S) con examen de ascenso, 5 estadísticas,
6 razas y 5 clases con habilidad propia, árboles de habilidades, **ejército de sombras**,
monstruos propios en las mazmorras, armas y armaduras exclusivas de la tienda, **misiones ocultas**,
**jefes secretos** y el **cambio de clase**. Todo con ventanas animadas del Sistema.

## Controles

- **V**: ventana del Sistema (o el despertar, si todavía no has despertado).
  - Pestañas **Estado**, **Habilidades**, **Sombras**, **Tienda**, **Misiones** y **Rango** (también con las teclas 1 a 6).
- **R**: habilidad de tu clase.
- **G**: ¡Levántate! (extraer la sombra de una criatura muerta).
- **H**: invocar todas tus sombras / guardarlas todas.
- **Mayús en el aire**: planear (Dragón con el nudo «Alas»).
- Las teclas se cambian en Opciones > Controles > El Sistema.

## Novedades de la 1.4.0

### ¡Levántate! con 3 intentos
- Cada cadáver tiene **3 intentos**. Cuanto más alto es el rango del muerto, **más maná cuesta** y **menos
  probabilidad** hay: E 75% · D 60% · C 46% · B 34% · A 24% · S 15% (los jefes, un 40% menos).
- Coste por intento: E 10 · D 15 · C 22 · B 30 · A 40 · S 50 de maná (los jefes, un 50% más).
- Ser de un rango mayor que el muerto ayuda (+5% por rango) y ser de un rango menor lo pone difícil (-10% por rango).
- Bajo la mira se ve la **probabilidad, los intentos que quedan y el maná** que cuesta.
- Nuevo nudo **Perspicacia** en el árbol de sombras: +4% de probabilidad por rango (hasta 10 rangos).

### Sombras más fuertes (y mortales)
- Todas las sombras atacan, también las que no sabían (vacas, ovejas, caballos...).
- Su daño depende de **la criatura** (su ataque o, si no tiene, su vida) **y de su rango**:
  E x1 · D x1,25 · C x1,6 · B x2 · A x2,6 · S x3,5. Las sombras arqueras disparan más fuerte según su nivel y rango.
- En la pestaña Sombras se ve el **ataque** de cada una.
- **Se las puede matar**: una sombra caída queda **inhabilitada 3 días de Minecraft** (dormir los hace pasar más rápido).

### Pestaña RANGO: examen de ascenso
- El rango de cazador ya **no sube solo**: al llegar al nivel del rango siguiente (D 10, C 25, B 45, A 65, S 85)
  se desbloquea su **examen de ascenso** (mazmorra privada con 3 oleadas y un **examinador**).
- Si lo superas subes de rango para siempre y ganas sus bonus:

| Rango | Bonus |
|---|---|
| D | +5% de daño y vida |
| C | +10% de daño y vida, +5% de EXP |
| B | +15% de daño y vida, +10% de EXP, +1 sombra |
| A | +20% de daño y vida, +15% de EXP, +2 sombras, +5% de levantar sombras |
| S | +30% de daño y vida, +25% de EXP, +3 sombras, +10% de levantar sombras, +10% de velocidad |

- Los cazadores de antes conservan el rango que ya tenían por su nivel.
- La tienda y la misión «Más allá de tu rango» usan este rango.

### Nivel 500 y prestigio
- El nivel máximo sube de 100 a **500** (los monstruos, las mazmorras S y las sombras también llegan al 500).
  A partir del nivel 100 cada nivel cuesta algo más de EXP.
- En el nivel 500, en la pestaña Rango, puedes hacer el **PRESTIGIO**: vuelves al nivel 1 (estadísticas a 10)
  y conservas raza, clase, árboles, sombras, oro, objetos y rango. A cambio:
  - Rango único **MONARCA** (MONARCA II, III... si lo repites).
  - El **arma del Monarca** de tu clase (indestructible): Filo del Monarca, Arco del Monarca, Báculo del Monarca,
    Colmillos del Monarca o Martillo del Monarca.
  - Por cada prestigio (hasta 10): +10% de EXP, +5% de daño y de vida, +2 sombras y +1 punto de estadística por nivel.

### Comandos nuevos (con trucos)
- `/sistema nivel <1-500>`, `/sistema rango <E..S>`, `/sistema examen`, `/sistema prestigio`.

## Novedades de la 1.3.0

### Cambio de clase (como en la serie)
- Al llegar al **nivel 40** aparece una misión oculta: **El despertar de la clase**.
- En la pestaña **Misiones** pulsa **INICIAR PRUEBA**: entras solo en una mazmorra especial
  (rango B) con 4 oleadas de élite y un jefe secreto, el **Guardián del Juicio**.
- Si lo derrotas, tu clase **muta** a una clase superior, con bonus nuevos y una habilidad mejorada:

| Clase | Mutación | Habilidad nueva |
|---|---|---|
| Espadachín | **Señor de la Guerra** | Tormenta de acero: tajo enorme (x2.2), ralentiza y te da Fuerza |
| Arquero | **Cazador Celestial** | Lluvia celestial: además cae una lluvia de flechas donde miras |
| Mago | **Hechicero del Abismo** | Lanza del abismo: 32 bloques, más daño y estalla con Marchitamiento |
| Asesino | **Nigromante** | Dominio de las sombras: tus sombras salen gratis con Fuerza II y Velocidad II |
| Tanque | **Bastión Inmortal** | Muralla eterna: Resistencia III, Absorción IV, te curas un 30% |

- Además ganas +5 puntos de estadística y la **Hoja del Juicio** (espada legendaria).
- Si fallas, puedes repetir la prueba. Tras ganarla tienes 10 s para hacer **¡Levántate!** sobre el Guardián.

### Misiones ocultas
- No se ven hasta que cumples su condición secreta (en la lista salen como **???** con una pista).
- Cazador nocturno, La legión silenciosa, Cazador de jefes, Más allá de tu rango y las de los jefes secretos.
- Cada una da una **recompensa oculta**: puntos, oro, elixires, sombras extra para siempre o armas legendarias.

### Hitos de nivel (recompensas ocultas)
- Al llegar a los niveles 10, 20, 30, 50, 60, 70, 80, 90 y 100 el Sistema te da premios secretos
  (puntos, oro, objetos y sombras extra). Si ya tenías esos niveles, los recibes al entrar.

### Jefes secretos
- **Bestia del Eclipse**: algunas noches aparece cerca de cazadores de nivel 30 o más (más a menudo con
  luna llena). Huye al amanecer. Recompensa: **Colmillo del Eclipse** (marchita y te cura).
- **Reina Carmesí**: a veces es el jefe de las mazmorras B, A y S. Nubes de veneno y soldados.
  Recompensa: **Aguijón Carmesí** (Veneno II).
- **Coloso Primordial**: a veces es el jefe de las mazmorras S. Terremotos que lanzan por los aires.
  Recompensa: **Corazón Primordial** (+2 sombras para siempre).
- Todos tienen barra de jefe, ataques especiales, una fase de furia y se les puede extraer la sombra.

## Novedades de la 1.2.0

### Ejército de sombras
- Cuando matas a una criatura, su cadáver **humea durante 10 segundos**. Pulsa **G** cerca
  (a menos de 10 bloques) para gritar **¡Levántate!**.
- Cuesta maná (10 + 5 por rango, +20 si es un jefe) y puede fallar: tienes 3 intentos por cadáver.
  Es más fácil con criaturas de rango menor que el tuyo y más difícil con jefes.
- Si sale bien, la criatura se alza como **tu sombra**: negra y morada, con su nombre
  `☾ Sombra · ...`. Te sigue, te defiende y ataca a lo que tú atacas.
- Si caen, vuelven a tu sombra (desde la 1.4.0 quedan inhabilitadas 3 días de Minecraft: ver arriba).
- Ganan bajas y **suben de nivel**. También te dan parte de la EXP y el oro de lo que matan.
- No se pueden extraer: creepers, aldeanos, el Warden, el Wither, el dragón y algunos animales.
- Límite: **2 sombras** al empezar. Súbelo en el árbol **Monarca de las Sombras** y con la
  armadura del Monarca.
- Pestaña **Sombras**: lista de tu ejército con su rango, nivel, bajas y estado, una vista en 3D
  de la sombra elegida y botones para invocarla, guardarla o liberarla.
- Al cerrar el juego, tus sombras se guardan y vuelven a salir cuando entras.

### Árbol del Monarca de las Sombras (compartido por todas las razas y clases)
- Ejército de sombras (+1 sombra por rango), Perspicacia (antes Extracción perfecta: probabilidad de levantar),
  Poder de las sombras (+5% de vida y daño), Legión (+2 sombras por rango) y
  Soberano de la noche (se curan solas).

### Árboles de habilidades nuevos
- Nueva pestaña con 3 árboles (Raza, Clase y Sombras), nudos octogonales, líneas de luz
  animadas, cielo estrellado y un panel con el efecto **actual** y el del **siguiente rango**.
- Clic en un nudo para verlo; botón **MEJORAR** (o clic derecho en el nudo) para subirlo.

### Monstruos de mazmorra (solo aparecen en las mazmorras)
- **Goblin**, **Lobo colmillo de acero**, **Oso de hielo**, **Orco**, **Hormiga soldado**,
  **Caballero maldito** y **Coloso de piedra**, cada uno con su aspecto, fuerza y efectos
  (veneno, lentitud, debilidad, marchitamiento o empujones).
- Cada mazmorra tiene su tema y su jefe: Rey goblin, Licántropo alfa, Señor de la guerra orco,
  Oso de hielo ancestral, Reina hormiga y Coloso de piedra.
- Son la mejor fuente de sombras.

### Tienda con categorías
- **Consumibles**: poción de maná, elixir de vida, piedra de retorno, tótems, runas...
- **Armas exclusivas**: Daga del asesino, Báculo arcano (clic derecho: rayo arcano),
  Espada del caballero negro, Hacha del berserker y **Guadaña del Monarca** (tus sombras
  atacan a quien golpeas).
- **Armaduras exclusivas** con bonus de conjunto (las 4 piezas puestas):
  - Cazador: Velocidad I.
  - Archimago: +40 de maná máximo y +50% de regeneración de maná.
  - Caballero negro: Resistencia I.
  - Monarca: +3 sombras en tu ejército y +15% de poder de las sombras.
- **Mazmorras**: llaves de todos los rangos.
- Las cosas buenas piden **rango de cazador** (D, C, B, A o S) para poder comprarlas.

## Compilar con GitHub

El repositorio compila solo con GitHub Actions al subir cambios. El `.jar` aparece en
**Releases**. La compilación también arranca un servidor y un cliente de prueba que crean
todos los monstruos y sombras, construyen las mazmorras y hacen capturas de la ventana.

## Instalar

Fabric Loader para 1.21.1, Fabric API y el `.jar` en la carpeta `mods`
(quita el `.jar` antiguo de El Sistema). Tu progreso de las versiones anteriores se mantiene.

## Comandos de prueba (con trucos activados)

- `/sistema exp 500`: te da 500 de EXP.
- `/sistema oro 5000`: te da 5000 de oro.
- `/sistema puntos 10`: te da 10 puntos de habilidad.
- `/sistema puerta C`: abre una puerta de mazmorra de rango C delante de ti (E, D, C, B, A o S).
- `/sistema cambiar`: vuelves a elegir raza y clase sin perder el nivel.
- `/sistema reiniciar`: empiezas de cero (también pierdes tus sombras, misiones y mutación).
- `/sistema jefe eclipse` (o `reina`, `coloso`, `guardian`): hace aparecer un jefe secreto delante de ti.
- `/sistema prueba`: empieza la prueba del cambio de clase aunque no tengas nivel 40.
- Huevos de los monstruos nuevos: en la pestaña de huevos del modo creativo.
