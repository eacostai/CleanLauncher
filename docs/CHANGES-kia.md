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
| Voz al abrir | El saludo por voz suena también al abrir el launcher (5 s después; máximo uno por minuto). Usa texto a voz para decir el nombre y, si la radio no tiene voz en español, los clips grabados. | `GreeterService.java`, `MainActivity.java` |

Sin cambios: `RadioClient`, `BtClient`, `SoundClient`, `NowPlaying`, `MediaListener`, `MusicBean`, `BTDevice`,
`ScreenOffActivity`, `Odometer`, `Weather`, `EqAuto`, `build.sh`, el flujo de CI y `backup/`.

Se conserva todo lo que añadió `e4170d4` en la pantalla principal: la configuración inicial, los accesos
para radios que no son NWD (radio, Bluetooth, ecualizador y ajustes de la propia radio), el cambio
automático de tema con las luces y la recarga al cambiar el nombre o la foto.

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
