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
- Tonos: ya se pueden cambiar los tonos del sistema (llamada, notificación
  y alarma), desde la lista del teléfono o desde cualquier archivo de audio,
  que se copia a la carpeta de tonos.

## 0.1.0 (2026-10-29)

- Primera versión: la base de la app. Pantalla de inicio con los módulos
  y el módulo Tonos con sus dos permisos ("Cambiar tonos del sistema" y
  "Acceso a notificaciones"), cada uno con un botón que abre su pantalla
  de Ajustes. El servicio que escucha las notificaciones ya se conecta,
  pero todavía no toca sonidos.
- Sin permiso de internet.
