# El Sistema 1.2.0 (mod de Fabric para Minecraft 1.21.1)

Despierta como Jugador: nivel (hasta 100), rango de cazador (E, D, C, B, A, S), 5 estadísticas,
6 razas y 5 clases con habilidad propia, árboles de habilidades, **ejército de sombras**,
monstruos propios en las mazmorras, armas y armaduras exclusivas de la tienda. Todo con ventanas
animadas del Sistema.

## Controles

- **V**: ventana del Sistema (o el despertar, si todavía no has despertado).
  - Pestañas **Estado**, **Habilidades**, **Sombras** y **Tienda** (también con las teclas 1, 2, 3 y 4).
- **R**: habilidad de tu clase.
- **G**: ¡Levántate! (extraer la sombra de una criatura muerta).
- **H**: invocar todas tus sombras / guardarlas todas.
- **Mayús en el aire**: planear (Dragón con el nudo «Alas»).
- Las teclas se cambian en Opciones > Controles > El Sistema.

## Novedades de la 1.2.0

### Ejército de sombras
- Cuando matas a una criatura, su cadáver **humea durante 10 segundos**. Pulsa **G** cerca
  (a menos de 10 bloques) para gritar **¡Levántate!**.
- Cuesta maná (10 + 5 por rango, +20 si es un jefe) y puede fallar: tienes 3 intentos por cadáver.
  Es más fácil con criaturas de rango menor que el tuyo y más difícil con jefes.
- Si sale bien, la criatura se alza como **tu sombra**: negra y morada, con su nombre
  `☾ Sombra · ...`. Te sigue, te defiende y ataca a lo que tú atacas.
- Las sombras **no mueren**: si caen, vuelven a tu sombra y se recuperan en 60 segundos.
- Ganan bajas y **suben de nivel**. También te dan parte de la EXP y el oro de lo que matan.
- No se pueden extraer: creepers, aldeanos, el Warden, el Wither, el dragón y algunos animales.
- Límite: **2 sombras** al empezar. Súbelo en el árbol **Monarca de las Sombras** y con la
  armadura del Monarca.
- Pestaña **Sombras**: lista de tu ejército con su rango, nivel, bajas y estado, una vista en 3D
  de la sombra elegida y botones para invocarla, guardarla o liberarla.
- Al cerrar el juego, tus sombras se guardan y vuelven a salir cuando entras.

### Árbol del Monarca de las Sombras (compartido por todas las razas y clases)
- Ejército de sombras (+1 sombra por rango), Extracción perfecta (+5% de probabilidad),
  Poder de las sombras (+5% de vida y daño), Legión (+2 sombras por rango) y
  Soberano de la noche (se curan solas y vuelven antes).

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
- `/sistema reiniciar`: empiezas de cero (también pierdes tus sombras).
- Huevos de los monstruos nuevos: en la pestaña de huevos del modo creativo.
