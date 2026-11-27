# El Sistema 1.9.0 (mod de Fabric para Minecraft 1.21.1)

Despierta como Jugador: nivel (hasta 500, con prestigio), rango de cazador (E, D, C, B, A, S) con examen de ascenso, 5 estadísticas,
6 razas y 5 clases con habilidad propia, árboles de habilidades, **ejército de sombras**,
monstruos propios en las mazmorras, armas y armaduras exclusivas de la tienda, **misiones ocultas**,
**jefes secretos** y el **cambio de clase**. Todo con ventanas animadas del Sistema.

## Controles

- **V**: ventana del Sistema (o el despertar, si todavía no has despertado).
  - Pestañas **Estado**, **Habilidades**, **Sombras**, **Tienda**, **Misiones** y **Rango** (también con las teclas 1 a 6)
    y el engranaje de los **Ajustes** (tecla 7).
- **R**: habilidad de tu clase.
- **G**: ¡Levántate! (extraer la sombra de una criatura muerta).
- **H**: invocar todas tus sombras / guardarlas todas.
- **Mayús en el aire**: planear (Dragón con el nudo «Alas»).
- Las teclas se cambian en Opciones > Controles > El Sistema.

## Novedades de la 1.9.0: la Ciudad de los Cazadores, nueva y mucho más grande

La ciudad se ha rehecho entera: ahora es una ciudad de **7x7 manzanas** (unos 200x200 bloques) con calles de verdad
(asfalto, pasos de cebra, aceras, semáforos, farolas, paradas de autobús y coches aparcados), un **río con puentes**,
paseo y parque en la orilla sur, y un bosque alrededor. Se construye **poco a poco al arrancar el mundo** (sin
congelar el servidor): si quieres ir mientras se construye, verás el porcentaje y te llevará en cuanto esté. Si
vienes de la 1.8, la ciudad vieja se sustituye por la nueva automáticamente.

- **Plaza del Cristal**: fuente circular, cerezos, bancos y el **Cristal del Sistema** con un **haz de luz azul**
  que se ve desde toda la ciudad. El **Portal de regreso** es el arco morado del lado oeste de la plaza.
- **Torre de la Asociación** (la más alta, con emblema y aguja): vestíbulo con recepción (Lucía), tablón de
  contratos, tablero de rangos y **dos ascensores**: quédate un momento en la cabina y subes al **mirador**
  (a 73 bloques de altura) para ver toda la ciudad. El del mirador te baja.
- **Arena del Sistema** (Vera): combates de 3 oleadas por rango (la última con un **Campeón de la Arena**).
  Cuesta oro entrar y si ganas te llevas **oro y esencias** (además de lo normal por cada monstruo). La piedra imán
  del foso te sube si te rindes.
- **Estación de Puertas** (Hugo): por una tarifa de la Asociación te **abre una puerta de mazmorra de tu rango**
  delante del gran anillo.
- **Centro Médico** (Irene): cura completa, limpieza de efectos malos, revisión completa (vida y maná) y tótems.
- **Comedor del Cazador** (Tomás): comida y el banquete del cazador (Fuerza y Regeneración).
- **Tienda 24 h** (Paco): antorchas, flechas, perlas de ender, cohetes, botellas de experiencia, mapa y brújula.
- La **Herrería**, la **Alquimia**, las **Esencias** y el **Gremio**, con interiores nuevos; el **Centro Comercial
  Nexo** con atrio y fuente; el **Jardín** con estanque; parque infantil, **Parque Central** con lago y la
  **Estación Central** con su tren.
- **Tu apartamento** (al suroeste de la plaza): salón con cocina, **biblioteca con mesa de encantamientos de nivel 30**,
  dormitorio con dos camas (para **reaparecer en la ciudad**) y taller con todas las mesas de trabajo.
- **22 habitantes** con aspecto propio (13 nuevos): tenderos en su puesto y vecinos que pasean por su barrio.
  El contrato «Turista del Sistema» ahora pide hablar con los 22.
- **Viaje dentro de la ciudad**: en el Sistema, **MISIONES > CIUDAD**, cada sitio tiene un botón **IR** para ir
  al momento (estando en la ciudad).
- De noche se encienden las ventanas de las torres, las farolas, los semáforos y las luces rojas de las antenas.
- En la ciudad ya se pueden abrir cofres, puertas y camas aunque lleves un bloque en la mano (lo que no se puede
  es poner bloques, quemar ni cavar).
- Comando de operador: `/sistema ciudad reconstruir` para volver a construirla desde cero.

## Novedades de la 1.8.2
- Comando **/spawn** (no necesita trucos): te lleva a la plaza de la Ciudad de los Cazadores. Si ya estás en la
  ciudad, te devuelve a la plaza. Para volver al mundo normal, el arco de la plaza.

## Novedades de la 1.8.1
- La primera vez que entras a un mundo **apareces en la Ciudad de los Cazadores** (para salir, el arco de la plaza).
- Si mueres y no tienes cama ni ancla de reaparición, **reapareces en la ciudad**.

## Novedades de la 1.8.0

### Pack de texturas del Sistema
- Viene **dentro del mod** y se pone solo la primera vez. Se quita y se vuelve a poner en **Ajustes** (engranaje,
  tecla 7) o en Opciones > Paquetes de recursos («El Sistema: interfaz, luz y shaders»).
- Botones, deslizadores, casillas y fondos de los menús como las ventanas del Sistema (azul oscuro con esquinas
  brillantes); barra rápida, barra de experiencia y punto de mira nuevos; **inventario y cofres** oscuros con
  huecos azules (los títulos van en azul claro); **sol** con resplandor y **luna** azul.
- Todo son dibujos propios (tools/pack_sistema.py), con las mismas medidas que las texturas de Minecraft 1.21.1.

### Shaders para los tonos de luz
- **Tonos de luz**: antorchas, faroles, fuego y lava más cálidos, noches con luz de luna azul y sombras profundas
  de color azul oscuro. Se tiñe el mapa de luz de Minecraft, así que vale para bloques y criaturas, casi no gasta
  y funciona también con Sodium.
- **Shader de tonos** (post-procesado): las sombras tiran al azul del Sistema, lo que brilla resplandece y los
  bordes de la pantalla se oscurecen un poco. Intensidad **SUAVE / NORMAL / INTENSO** (o NO) en Ajustes
  (clic para subir, clic derecho para bajar). Con el **modo rendimiento** va sin resplandor.
- Si tienes Iris con un shader puesto, el mod no usa los suyos.

## Novedades de la 1.7.0

### La Ciudad de los Cazadores (un mapa aparte)
- En la ventana del Sistema: **MISIONES > CIUDAD > VIAJAR A LA CIUDAD** (o `/sistema ciudad`). Llegas a la plaza,
  junto al **Cristal del Sistema**. Para volver: el mismo botón (VOLVER A CASA) o cruzar el **Portal de regreso**
  de la esquina suroeste de la plaza. Vuelves exactamente a donde estabas.
- La ciudad se construye sola la primera vez (calles, torres con las ventanas encendidas de noche, parques,
  farolas...) y no se puede romper ni llenar de bloques (salvo en creativo). No salen monstruos.
- Sus habitantes (diseños propios):
  - **Lucía**, recepcionista de la **Asociación de Cazadores** (el edificio grande del norte): contratos.
  - **Bruno**, en la **Herrería**: mejora armas y despierta habilidades ocultas con esencias.
  - **Selene**, en la **Alquimia**: pociones y elixires por oro.
  - **Ezequiel**, el **mercader de esencias**: rarezas por esencias (y cambia oro por esencias).
  - **Darío**, maestro del **Gremio de cazadores**: contratos difíciles.
  - Y cuatro vecinos que pasean y te cuentan cosas.
- Las tiendas de la ciudad **sólo se usan en persona**: haz clic derecho sobre el tendero.

### Esencias (la nueva moneda)
- Se ganan matando monstruos con rango (E 1 · D 1 · C 2 · B 3 · A 5 · S 8; los jefes, x5; un jefe secreto, +40)
  y completando mazmorras (E 4 · D 8 · C 14 · B 25 · A 40 · S 70). Se ven junto al oro (◆).

### Herrería: mejoras y habilidades ocultas
- **Mejorar un arma** (+1 ... +10): cada nivel da un **6 % más de daño** (+60 % en +10). Cuesta 8, 14, 22, 32, 45,
  60, 80, 105, 135 y 170 esencias. Sirve para espadas, hachas, arcos, ballestas, tridentes y las armas del mod.
- **Habilidad oculta** (armas y piezas de armadura, 30 esencias; cambiarla, 20): al despertarla sale una al azar.
  - Armas: Vampirismo, Llama negra, Eco, Verdugo, Cazador de jefes, Tormenta, Sed de maná y Escarcha.
  - Armadura (se suman por pieza): Espinas, Evasión, Regeneración, Ligereza, Fortaleza, Pozo de maná, Ignífugo
    y Último aliento (te salva de morir una vez cada 5 minutos).
- La mejora y la habilidad se ven en la descripción del objeto.

### Misiones secundarias (contratos) con recompensas ocultas
- Lucía: Primer contrato, Plaga de goblins, El ejército crece y Turista del Sistema.
- Darío: Cazador de élite, Rompepuertas, Matajefes y Leyenda oculta.
- Se aceptan y se cobran hablando con quien las da; la recompensa es «???» hasta que la cobras.
- En MISIONES > CIUDAD ves cómo vas con cada contrato.

### Comandos nuevos (con trucos)
- `/sistema ciudad`: ir a la ciudad (o volver si ya estás).
- `/sistema esencias 100`: da 100 esencias.

## Novedades de la 1.6.0

### Menú de ajustes (engranaje de la ventana del Sistema, tecla 7)
- **Partículas del mod**: SÍ / NO. Sin ellas no salen las partículas de las habilidades, las sombras, los jefes
  ni las mazmorras. Las puertas de mazmorra, los cadáveres que se pueden levantar y las nubes de veneno de la
  Reina Carmesí se siguen viendo con unas pocas, porque hacen falta para jugar.
- **Modo rendimiento**: todo más ligero para el PC. Sólo sale 1 de cada 3 partículas del mod, no hay auras en las
  sombras ni remolinos en las puertas y los ojos, runas y magma de los monstruos no brillan.
- Los ajustes son de cada jugador (en un servidor cada uno elige los suyos) y se guardan en `config/elsistema.json`.

### Animaciones nuevas de las sombras
- **¡Levántate!**: el humo y las almas se juntan en un círculo morado sobre el cadáver y la sombra sale despacio
  del suelo; al terminar estalla en almas. Los bordes de la pantalla se oscurecen de morado mientras tanto.
- **Invocar** (H o INVOCAR): cada sombra sale deprisa de un charco de oscuridad, una detrás de otra.
- **Guardar** (H o GUARDAR): las sombras se hunden en el suelo antes de desaparecer. Las que caen en combate
  también se hunden en su sombra.

## Novedades de la 1.5.0

### Más EXP y monstruos más difíciles
- Matar cualquier criatura da **5 veces más EXP**.
- Los monstruos con rango tienen **+50% de vida**, **+35% de daño**, más armadura y aguante al empuje según su rango,
  y te ven desde más lejos. Las sombras no reciben esta dificultad extra.

### Las sombras suben de nivel contigo y tienen su propio árbol
- Cada vez que ganas EXP, todas tus sombras (menos las caídas) ganan la misma cantidad. La que mata además gana
  la EXP de su presa. Usan la misma tabla de EXP que tú: las de nivel bajo te alcanzan rápido.
- Cada nivel que sube una sombra le da **1 punto** para su **árbol propio**. En la pestaña Sombras elige una
  sombra y pulsa **ÁRBOL PROPIO**:
  - Garras afiladas: +4% de daño por rango (20 rangos).
  - Cuerpo de sombra: +5% de vida por rango (20).
  - Coraza oscura: +1 de armadura por rango (15).
  - Paso veloz: +3% de velocidad por rango (10).
  - Sed de sangre: se cura el 2% del daño que hace por rango (10, cuesta 2 puntos).
  - Golpe brutal: +3% de probabilidad de golpe doble por rango (10, cuesta 2).
  - Regeneración: +0,5 de vida por segundo por rango (10, cuesta 2).
- La pestaña Sombras muestra la barra de EXP de cada sombra y un `*` cuando alguna tiene puntos.

### Monstruos con diseños nuevos
- Modelos y texturas nuevos (diseños propios, a doble resolución) para el goblin, el orco, el caballero maldito,
  el coloso, el lobo colmillo de acero, el oso de hielo, la hormiga soldado y los cuatro jefes secretos.
- Los ojos, las runas y el magma brillan en la oscuridad.

### Comando nuevo (con trucos)
- `/sistema sombras 10`: da 10 puntos de árbol a todas tus sombras.

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
- `/sistema sombras 10`: da 10 puntos de árbol propio a cada una de tus sombras.
- `/sistema puerta C`: abre una puerta de mazmorra de rango C delante de ti (E, D, C, B, A o S).
- `/sistema cambiar`: vuelves a elegir raza y clase sin perder el nivel.
- `/sistema reiniciar`: empiezas de cero (también pierdes tus sombras, misiones y mutación).
- `/sistema jefe eclipse` (o `reina`, `coloso`, `guardian`): hace aparecer un jefe secreto delante de ti.
- `/sistema prueba`: empieza la prueba del cambio de clase aunque no tengas nivel 40.
- Huevos de los monstruos nuevos: en la pestaña de huevos del modo creativo.
