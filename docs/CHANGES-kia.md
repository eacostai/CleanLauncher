# Rediseño Kia Sorento 2012 (rama `redesign/kia-sorento-ui`)

Base: `main` en `e4170d4` («Build portable, CI y nuevas funciones»).

## Qué cambió

| Commit | Cambio | Archivos |
|---|---|---|
| Quitar Pico y Placa | Se eliminan el interruptor de la configuración, la preferencia, la frase de texto a voz, el clip grabado y la mención en el README. | `GreeterService.java`, `Profile.java`, `SetupActivity.java`, `Speech.java`, `res/raw/v_picoplaca.mp3` (borrado), `README.md` |
| Saludo | La pantalla principal muestra «¡Bienvenido, EDDY!». Usa el nombre del perfil en mayúsculas; por defecto es Eddy. El subtítulo solo dice «Buenos días / Buenas tardes / Buenas noches». | `Profile.java`, `MainActivity.java`, `SetupActivity.java` (ejemplo del campo nombre) |
| Tema grafito | El tema oscuro pasa a fondo grafito `#0B0D10`, paneles `#151A21` y acento azul eléctrico `#1E90FF`, y es el tema por defecto. El tema claro no cambia. El morado del anillo, dial, deslizadores, ecualizador y aviso de volumen pasa a azul. | `Ui.java`, `res/values/styles.xml`, `res/values/colors.xml` (nuevo), `RingView.java`, `DialView.java`, `LevelSlider.java`, `VolumeOverlay.java`, `EqActivity.java`, `RadioActivity.java`, `BtActivity.java`, `BtDevicesActivity.java`, `OilActivity.java` |
| Pantalla principal | Barra superior con la foto propia del carro, el saludo y los indicadores reales (teléfono BT, red, clima, aceite). Reproductor a la izquierda; reloj, radio FM, Bluetooth y volumen a la derecha; dock con etiquetas. La foto incluida del Optra ya no se muestra. | `MainActivity.java`, `AndroidManifest.xml` (`ACCESS_NETWORK_STATE`), `res/drawable/ic_wifi.xml` (nuevo) |
| Volver a la distribución original | A pedido, la pantalla principal vuelve a las tarjetas originales (saludo con foto y clima, reloj y aceite, radio FM, música, dock). Se revierte el commit anterior. | `MainActivity.java`, `AndroidManifest.xml`, `res/drawable/ic_wifi.xml` (borrado) |
| Foto de la Sorento | Kia Sorento recortada y en gris grafito, con versión de faros encendidos para el efecto de luces. Reemplaza las fotos del Optra. | `res/drawable-nodpi/sorento*.png` (nuevas), `optra*.png` (borradas), `Profile.java`, `MainActivity.java` |
| Música, mapa y velocímetro | La tarjeta de Radio FM pasa a ser la de Música: muestra lo que suena en cualquier fuente (Bluetooth, USB o apps como YouTube, YouTube Music o Spotify), con anterior, reproducir y siguiente, y una fila con las apps de música instaladas. Donde estaba la música va un mapa con tu posición y el botón «Navegar con Google Maps». Encima del auto, un velocímetro con la velocidad real del GPS. La radio FM sigue en el dock. | `MainActivity.java`, `MusicApps.java`, `MapView.java`, `SpeedView.java` (nuevos) |
| Mapa de Google y animación Kia | El mapa de la pantalla principal pasa a ser el mapa de Google (el que se inserta en páginas web, sin clave), movible con el dedo; sus enlaces abren Google Maps. El velocímetro dice por qué no hay velocidad (buscando GPS, ubicación apagada, falta permiso) y al tocarlo ofrece arreglarlo. Animación de inicio con el logo de Kia al encender el carro o la pantalla y al abrir el launcher. | `MapView.java`, `SpeedView.java`, `MainActivity.java`, `SplashActivity.java` (nueva), `GreeterService.java`, `AndroidManifest.xml`, `res/drawable-nodpi/kia_logo.png` (nueva) |
| Mapa en marco y SORENTO | El mapa de Google se carga dentro de un marco, como en una página web (cargado directo salía vacío). Debajo del auto aparece «SORENTO» con letras espaciadas. | `MapView.java`, `MainActivity.java`, `Profile.java` |
| Música dentro de su tarjeta | La tarjeta muestra la app que suena (su icono, nombre, carátula de fondo, título, barra de progreso con tiempo) sin salir del inicio, con anterior, reproducir y siguiente. Tocar el icono de una app reanuda su música desde el inicio cuando la app lo permite; si no, la abre (mantener pulsado la abre siempre). Si falta el permiso para ver la música de otras apps, la tarjeta lo dice y lleva a activarlo. | `MainActivity.java`, `NowPlaying.java`, `MusicApps.java`, `LevelBar.java` (nueva) |
| Voz al abrir | El saludo por voz suena también al abrir el launcher (5 s después; máximo uno por minuto). Usa texto a voz para decir el nombre y, si la radio no tiene voz en español, los clips grabados. | `GreeterService.java`, `MainActivity.java` |

Sin cambios: `RadioClient`, `BtClient`, `SoundClient`, `NowPlaying`, `MediaListener`, `MusicBean`, `BTDevice`,
`ScreenOffActivity`, `Odometer`, `Weather`, `EqAuto`, `build.sh`, el flujo de CI y `backup/`.

Se conserva todo lo que añadió `e4170d4` en la pantalla principal: la configuración inicial, los accesos
para radios que no son NWD (radio, Bluetooth, ecualizador y ajustes de la propia radio), el cambio
automático de tema con las luces y la recarga al cambiar el nombre o la foto.

## Mapa y velocímetro

- El velocímetro usa el GPS (una lectura por segundo). Muestra 0 por debajo de 2 km/h para no marcar el
  temblor del GPS estacionado. Sin lectura en 5 segundos indica el motivo: «buscando GPS» (bajo techo
  es normal), «activa la ubicación», «toca para permitir» o «sin GPS» si el equipo no tiene receptor.
- Google Maps como app no se puede meter dentro de otra app, así que la tarjeta muestra el mapa de Google
  para páginas web, centrado en tu posición. Se recarga al avanzar unos 250 m (como mucho cada 20 s) y
  necesita internet. El botón «Navegar con Google Maps» abre la app para la navegación guiada.

## Animación de inicio

Logo de Kia con una línea de luz azul, brillo que lo recorre y el saludo; dura unos 3 segundos y se
salta tocando la pantalla. Aparece al dar contacto, al encenderse la pantalla y al abrir el launcher,
una vez cada 30 segundos como máximo. En Android 10 o más nuevo (por ejemplo, una tablet de prueba)
el sistema solo deja mostrarla al encender la pantalla si el launcher tiene el permiso de superposición.

## Saludo por voz

Suena al dar contacto y al abrir el launcher. Usa el motor de texto a voz de la radio para decir
«¡Buenas tardes, Eddy! Bienvenido.» junto con el clima y los avisos. Si la radio no tiene voz en español,
usa los clips grabados del dueño original, que no dicen tu nombre.

La foto de la Sorento queda incluida en las dos variantes (`sh build.sh` y `VARIANT=share sh build.sh`).

## Pruebas hechas (sin hardware)

- Compila sin errores con los mismos pasos que `build.sh` (aapt2 → javac `--release 8` → dex → zipalign →
  apksigner, minSdk 25, targetSdk 28). Diferencias del entorno: android.jar de API 23 en lugar de 33, y
  retrolambda + `dx` en lugar de `d8`.
- Sin referencias a «pico», «placa» ni «Jhon» en código, recursos ni README.
- El texto «¡Bienvenido, » y la conversión a mayúsculas están en el `classes.dex` generado.

No se probó en una radio ni en un emulador: el aspecto final y las funciones con hardware están sin verificar.

## Cómo probarlo en la radio

1. Compila en tu PC con `sh build.sh` o `VARIANT=share sh build.sh` (sin `install`).
2. Guarda antes una copia del APK que tienes instalado.
3. Instálalo solo cuando lo decidas, y revisa:
   - Pantalla principal: saludo, hora, indicadores, que nada quede cortado en tu resolución.
   - Radio FM: frecuencia visible, anterior/siguiente desde la tarjeta, pantalla de radio.
   - Música: Bluetooth, USB y apps (Spotify, YouTube Music); play/pausa y saltar.
   - Bluetooth: nombre del teléfono en la barra y en la tarjeta; marcador y dispositivos.
   - Volumen: aviso flotante al girar la perilla o con el volante; número en la tarjeta.
   - Pantalla apagada: se apaga, los botones del volante siguen funcionando, se despierta con reversa o al tocar.
   - Al dar contacto: vuelve la última fuente de audio y suena el saludo, sin Pico y Placa.
   - Configuración (Apps → Configurar): ya no aparece el interruptor de Pico y Placa.
   - Dock: navegación, apps, ecualizador y ajustes abren su pantalla.

## Reversión

- Código: `main` no se tocó. Basta con no fusionar la rama o revertir el commit concreto.
- Radio: reinstala el APK guardado, o elige el launcher de fábrica como aplicación de inicio en Ajustes.
