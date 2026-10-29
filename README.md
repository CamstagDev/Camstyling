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
- **Sonidos por app**: WhatsApp, Telegram, Mensajes y Discord (y
  cualquier otra), por categoría, persona, grupo o palabra. Camstyling
  escucha las notificaciones y toca tu sonido; el sonido original de esa
  categoría se silencia una vez en Ajustes (la app abre esa pantalla).
  Así no depende de que la otra app respete nada al actualizarse.
- **Tonos por contacto**: un tono de llamada para cada contacto.

Estado de cada uno: ver CHANGELOG.md.

## Permisos

Ninguno se pide con un diálogo: la app abre la pantalla de Ajustes donde
se activa cada uno.

| Permiso | Para qué |
|---|---|
| Modificar ajustes del sistema | Cambiar el tono de llamada, de notificación y de alarma |
| Acceso a notificaciones | Ver qué notificación llegó y tocar el sonido de su regla |
| Contactos (más adelante) | Ponerle un tono a cada contacto |

## Comandos

No hace falta Android Studio: basta con un JDK 21 y las herramientas de
línea de comandos del SDK de Android, con `JAVA_HOME` y `ANDROID_HOME`
apuntando a ellos.

| Comando | Qué hace |
|---|---|
| `.\gradlew.bat assembleDebug` | APK de prueba en `app/build/outputs/apk/debug/` (se instala aparte, como "com.camstagdev.camstyling.debug") |
| `.\gradlew.bat assembleRelease` | APK firmado en `app/build/outputs/apk/release/app-release.apk` |
| `.\scripts\publicar.ps1 parche` | Versión nueva (parche, menor o mayor): sube el número, cierra el CHANGELOG, compila, hace commit y etiqueta, sube a GitHub y crea la Release con el APK |

Cada versión publicada también se sube a Google Drive (lo hace Claude
después de `publicar.ps1`).

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
  avisos/           el servicio que escucha las notificaciones
  sistema/          permisos y pantallas de Ajustes
scripts/            publicar.ps1
```
