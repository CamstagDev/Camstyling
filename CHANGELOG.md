# Cambios de Camstyling

Cada versión publicada tiene su sección. Lo que se va haciendo se anota
en "Sin publicar"; `scripts/publicar.ps1` la convierte en la sección de
la versión nueva.

## Sin publicar

- Módulo nuevo **Vibración**:
  - 10 patrones predeterminados (toque corto, doble, triple, latido, largo,
    timbre clásico, ráfaga, ola, tic-tac y SOS), cada uno con su dibujo y
    un botón para probarlo.
  - Patrones propios con un editor: pulsos, duración, pausa, fuerza, forma
    (pareja, sube o baja) y repeticiones.
  - "Desde un sonido": arma una vibración que sigue el ritmo de un archivo
    de audio (por ejemplo, tu tono de llamada).
  - Vibración por app: el servicio de notificaciones vibra con el patrón de
    cada app. Respeta modo silencio, "No molestar" y canales silenciosos.
  - Vibración de llamadas: mientras suena el teléfono vibra con su patrón y
    para al contestar o colgar. Pide el permiso "Teléfono".
  - Avisa si el motor del teléfono no cambia la fuerza (solo prende y apaga).
- Tonos: **sonidos por app**. Reglas con su propio sonido (de la lista del
  teléfono o de un archivo) para toda una app, una categoría (mensajes,
  grupos...), una persona o grupo, o los mensajes con una palabra. Si
  coinciden varias, gana la más precisa. El servicio de notificaciones lo
  toca con el volumen de notificaciones (corta a los 10 s); en modo
  vibración solo vibra y respeta "No molestar" y canales silenciosos. Las
  categorías y los chats se ofrecen a medida que llegan notificaciones. El
  sonido original se apaga una vez en los ajustes de esa app o categoría
  (la tarjeta abre esa pantalla) y avisa si sigue prendido.
- Tonos: en "¿Cuándo suena tu tono?" se ven las cuatro opciones con el
  teléfono acostado (van de a dos por renglón); antes "Una palabra"
  quedaba cortada abajo.
- Tonos: **tonos por contacto**. Un tono de llamada para cada contacto,
  guardado en el mismo contacto (lo toca la app de Teléfono). Pide el
  permiso "Contactos".
- Tonos: ya se pueden cambiar los tonos del sistema (llamada, notificación
  y alarma), desde la lista del teléfono o desde cualquier archivo de audio,
  que se copia a la carpeta de tonos.

- Módulo nuevo **Fondos**:
  - Poner una imagen de fondo en inicio, en bloqueo o en las dos.
  - Cambio automático con las imágenes de una carpeta, al azar o en orden,
    cada 1, 3, 6 o 12 horas o cada día.
  - Fondo de noche: otra carpeta (o una sola imagen) entre dos horas, por
    ejemplo de 20:00 a 07:00.
  - Sin gastar batería: nada corre todo el tiempo; Android despierta a la
    app más o menos cuando toca. Las fotos se leen achicadas a la pantalla.
  - No pide permiso de archivos: solo puede leer la carpeta que elijas.

- Módulo nuevo **Perfiles**:
  - Perfiles por días y horas (trabajo, noche, fin de semana...) que
    cambian el timbre (sonido, vibrar, silencio), el volumen de llamada y
    de notificaciones, los tonos y la vibración de llamadas. Al terminar,
    el teléfono vuelve a como estaba.
  - Horas de silencio: pone "No molestar" y solo suenan las alarmas, las
    llamadas de favoritos y las excepciones elegidas (personas, grupos,
    palabras o apps enteras), que Camstyling toca como alarma.
  - Sin gastar batería: una alarma inexacta por cada horario, que se
    vuelve a armar al prender el teléfono. Pide "Acceso a No molestar".

## 0.1.0 (2026-10-29)

- Primera versión: la base de la app. Pantalla de inicio con los módulos
  y el módulo Tonos con sus dos permisos ("Cambiar tonos del sistema" y
  "Acceso a notificaciones"), cada uno con un botón que abre su pantalla
  de Ajustes. El servicio que escucha las notificaciones ya se conecta,
  pero todavía no toca sonidos.
- Sin permiso de internet.
