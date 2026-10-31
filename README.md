# Camstyling

Herramientas para Android que el sistema debería traer de fábrica, sin
anuncios, sin rastreo y sin gastar batería. La app **no tiene permiso de
internet**: Android le impide conectarse, así que no puede mandar nada a
ningún lado.

Android 10 o más nuevo. Pensada para teléfonos Motorola (Android casi puro).

## Módulos

### Tonos

- **Tonos del sistema**: llamada, notificación y alarma con cualquier
  archivo de audio. Los sonidos se copian a las carpetas de tonos del
  teléfono, así siguen sonando aunque borres el archivo original.
- **Sonidos por app**: WhatsApp, Telegram, Mensajes, Discord o cualquier
  otra, por toda la app, categoría, persona, grupo o palabra. Camstyling
  escucha las notificaciones y toca tu sonido; el sonido original de esa
  categoría se silencia una vez en Ajustes (la app abre esa pantalla).
  Así no depende de que la otra app respete nada al actualizarse.
- **Tonos por contacto**: un tono de llamada para cada contacto.

### Vibración

- **Patrones**: los de los teléfonos de antes (doble toque, latido, timbre
  clásico, SOS...) y los tuyos, con pulsos, duración, pausa, fuerza y
  repeticiones. Muchos teléfonos solo prenden y apagan el motor: ahí la
  fuerza no cambia nada, pero los tiempos sí (la app lo avisa).
- **Al ritmo de un sonido**: escucha un archivo de audio y arma una
  vibración que lo sigue, más fuerte donde suena más fuerte.
- **Por app**: cada app vibra con su patrón (la vibración original de esa
  app se apaga una vez en sus ajustes de notificación).
- **Llamadas**: vibra con su patrón mientras suena. El tono lo sigue
  tocando el sistema; para que no se mezclen, se apaga "Vibrar en
  llamadas" en Ajustes de sonido.

### Fondos

- **Una imagen**: de fondo en inicio, en bloqueo o en las dos.
- **Cambio automático**: elige una carpeta y el fondo cambia solo con sus
  imágenes, al azar o en orden, cada hora, cada varias horas o cada día.
- **De noche**: otra carpeta (o una sola imagen) entre las horas que
  elijas.
- Sin servicio prendido ni alarmas exactas: Android despierta a la app
  cuando toca, así que el cambio puede atrasarse un rato.

### Perfiles

- **Por horario**: timbre, volúmenes, tonos y vibración de llamadas
  distintos según el día y la hora. Al terminar, todo vuelve a como
  estaba.
- **Horas de silencio**: "No molestar" con excepciones (personas, grupos,
  palabras o apps) que sí suenan, más las llamadas de favoritos.

### Avisos

- **Historial**: lo que llega queda guardado (solo en el teléfono), aunque
  lo descartes o la app lo borre.
- **Recordatorio**: lo importante vuelve a sonar mientras siga sin abrir.
- **Linterna**: destellos al llegar algo de quien elijas.

### Adicionales

Detalles que no hacen falta, cada uno con su interruptor: la animación al
cargar (solo con el teléfono bloqueado) y atajos para colores, íconos y
letra (Android no deja cambiarlos desde una app sin root).

### Respaldo

Todos los ajustes a un archivo, y de vuelta.

Estado de cada uno: ver CHANGELOG.md.

## Permisos

"Teléfono" y "Contactos" se piden con un diálogo; para los demás la app abre la
pantalla de Ajustes donde se activa cada uno. Vibrar y poner el fondo
no necesitan permiso que preguntar, y Fondos tampoco pide permiso de
archivos: solo lee la carpeta que el usuario elige.

| Permiso | Para qué |
|---|---|
| Modificar ajustes del sistema | Cambiar el tono de llamada, de notificación y de alarma |
| Acceso a notificaciones | Ver qué notificación llegó y tocar el sonido o la vibración de su regla |
| Teléfono | Saber cuándo suena una llamada para vibrar con su patrón (no lee números) |
| Contactos | Ponerle un tono de llamada a cada contacto |
| Acceso a No molestar | Perfiles: poner silencio y las horas de silencio |

## Comandos

No hace falta Android Studio: basta con un JDK 21 y las herramientas de
línea de comandos del SDK de Android, con `JAVA_HOME` y `ANDROID_HOME`
apuntando a ellos.

| Comando | Qué hace |
|---|---|
| `.\gradlew.bat assembleDebug` | APK de prueba en `app/build/outputs/apk/debug/` (se instala aparte, como "com.camstagdev.camstyling.debug") |
| `.\gradlew.bat assembleRelease` | APK firmado en `app/build/outputs/apk/release/app-release.apk` |
| `.\scripts\publicar.ps1 parche` | Versión nueva (parche, menor o mayor): sube el número, cierra el CHANGELOG, compila, hace commit y etiqueta, sube a GitHub y crea la Release con el APK |

Después de `publicar.ps1`, Claude agrega el enlace de la versión (Release
y APK directo) arriba del documento "Camstyling - Descargas", en la
carpeta Camstyling de Google Drive (documento con id
`1SRdBVo-0BhgXBtEnPy0tqxOhTIqz6cBb-HVZQsebYO8`). El APK no se sube a
Drive: vive en la Release de GitHub.

## Firma

Android solo instala una actualización encima de la app si está firmada
con la **misma llave**. Si se pierde, hay que desinstalar (y se pierden los
ajustes de la app). La llave está solo en esta PC, fuera de git:

- `camstyling.jks`: la llave
- `keystore.properties`: sus contraseñas

**Guarda una copia de los dos archivos** en un lugar seguro fuera de esta PC.

## Dónde está cada cosa

```
app/src/main/java/com/camstagdev/camstyling/
  MainActivity.kt   arranque y navegación entre módulos
  ui/               tema y pantalla de inicio (lista de módulos)
  tonos/            módulo de tonos
  vibracion/        módulo de vibración: patrones, editor y "al ritmo de un sonido"
  avisos/           lo que despierta el sistema: notificaciones y llamadas entrantes
  sistema/          permisos y pantallas de Ajustes
scripts/            publicar.ps1
```
