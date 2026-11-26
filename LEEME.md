# El Sistema (mod de Fabric para Minecraft 1.21.1)

Despierta como Jugador: nivel (hasta 100), rango de cazador (E, D, C, B, A, S), 5 estadísticas,
5 razas y 5 clases con habilidad propia. Todo con ventanas azules animadas del Sistema.

## Controles

- **V**: ventana de estado (o el despertar, si todavía no has despertado).
- **R**: habilidad de tu clase.
- Se cambian en Opciones > Controles > El Sistema.

## Compilar con GitHub (igual que con Niveles Locos)

1. En GitHub crea un repositorio **nuevo**, por ejemplo `el-sistema`.
2. Pulsa **uploading an existing file** y arrastra TODO lo que hay dentro de esta carpeta,
   incluida la carpeta `.github`. Pulsa **Commit changes**.
3. Comprueba que en el repositorio existe `.github/workflows/build.yml`. Si no se subió,
   créalo con **Add file > Create new file** y pega el contenido de ese archivo.
4. Ve a la pestaña **Actions** y espera el tick verde (unos 5 minutos).
5. Descarga el mod:
   - **Lo más fácil:** en la página principal del repositorio, a la derecha, en **Releases**,
     abre la última y descarga `el-sistema-1.0.0.jar`.
   - O bien en **Actions** > la ejecución > **Artifacts** > `el-sistema-mod` (viene dentro de un zip).

Si sale una X roja, abre el paso en rojo y copia el error.

## Instalar

Igual que el otro mod: Fabric Loader para 1.21.1, Fabric API y el `.jar` en la carpeta `mods`.
Puede estar instalado a la vez que Niveles Locos.

## Comandos de prueba (con trucos activados)

- `/sistema exp 500`: te da 500 de EXP.
- `/sistema cambiar`: vuelves a elegir raza y clase sin perder el nivel.
- `/sistema reiniciar`: empiezas de cero.
