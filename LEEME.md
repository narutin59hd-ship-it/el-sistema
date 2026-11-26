# El Sistema 1.1.0 (mod de Fabric para Minecraft 1.21.1)

Despierta como Jugador: nivel (hasta 100), rango de cazador (E, D, C, B, A, S), 5 estadísticas,
6 razas y 5 clases con habilidad propia, árboles de habilidades, monstruos con rango, mazmorras
y una tienda. Todo con ventanas azules animadas del Sistema.

## Controles

- **V**: ventana del Sistema (o el despertar, si todavía no has despertado).
  - Pestañas **Estado**, **Habilidades** y **Tienda** (también con las teclas 1, 2 y 3).
- **R**: habilidad de tu clase.
- **Mayús en el aire**: planear (Dragón con el nudo «Alas»).
- Las teclas se cambian en Opciones > Controles > El Sistema.

## Novedades de la 1.1.0

### Monstruos con rango y nivel
- Todos los monstruos aparecen con un rango (E a S) y un nivel parecido al tuyo (a veces más alto).
- Al mirarlos ves su nombre: por ejemplo `[C] Zombi Nv.31`.
- Cuanto más rango y nivel: más vida, más daño, más armadura, más tamaño y mejores recompensas.
- Al matarlos ganas EXP y **oro**, y pueden soltar:
  - **Núcleo de maná** (del rango del monstruo): se vende en la tienda.
  - **Llave de mazmorra**: abre una puerta de mazmorra de su rango.
  - **Runa de habilidad** (rara): +1 punto de habilidad.

### Mazmorras
- De vez en cuando se abre una **puerta de mazmorra** cerca de ti (te avisa el Sistema).
  También puedes abrir una con una llave (clic derecho).
- Entra en la puerta: te lleva a una sala cerrada con **oleadas** de monstruos de ese rango
  y un **jefe** al final.
- Al ganar: oro, EXP, núcleos, botín y a veces llaves y runas. Luego vuelves a la puerta.
- Si mueres o sales, la mazmorra se cierra (tus cosas aparecen junto a la puerta).
- Dentro de la mazmorra no se pueden romper bloques.

### Tienda (pestaña Tienda)
- Compra comida, pociones, armaduras, espadas, tótems, manzanas, runas y llaves con oro.
- Botón para **vender todos tus núcleos de maná** de golpe.
- Mayús + clic compra 5.

### Raza Dragón y árboles de habilidades
- Nueva raza **Dragón**: más grande, más vida, armadura y daño, pero gana un 10% menos de EXP.
- Cada raza (6) y cada clase (5) tiene su **propio árbol de habilidades** de 5 nudos.
- Ganas **1 punto de habilidad por nivel** (y con las runas). Si ya tenías nivel, se te dan
  los puntos de los niveles que ya habías subido.
- Pestaña **Habilidades**: árbol de tu raza a la izquierda y el de tu clase a la derecha.
  Pasa el ratón por un nudo para ver qué hace y haz clic para mejorarlo.

## Compilar con GitHub

El repositorio compila solo con GitHub Actions al subir cambios. El `.jar` aparece en
**Releases** (a la derecha de la página principal del repositorio).

## Instalar

Fabric Loader para 1.21.1, Fabric API y el `.jar` en la carpeta `mods`
(quita el `el-sistema-1.0.0.jar` antiguo). Puede estar instalado a la vez que Niveles Locos.
Tu progreso de la 1.0.0 se mantiene.

## Comandos de prueba (con trucos activados)

- `/sistema exp 500`: te da 500 de EXP.
- `/sistema oro 5000`: te da 5000 de oro.
- `/sistema puntos 10`: te da 10 puntos de habilidad.
- `/sistema puerta C`: abre una puerta de mazmorra de rango C delante de ti (E, D, C, B, A o S).
- `/sistema cambiar`: vuelves a elegir raza y clase sin perder el nivel.
- `/sistema reiniciar`: empiezas de cero.
