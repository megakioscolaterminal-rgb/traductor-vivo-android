# Traductor Vivo (Android)

Proyecto Android nativo para traducir conversaciones habladas a español, con detección automática de idioma y voz femenina.

## Funciones
- Entrada de voz con detección automática de rango abierto (no hace falta seleccionar idioma de origen).
- Idioma de salida: español (`es`).
- Voz de salida: `es-ES-ElviraNeural` (femenina).
- Interfaz sin transcripciones ni traducciones escritas.
- Inicio/detención manual y permisos de micrófono.

## Requisito imprescindible
Se necesita un recurso de **Azure AI Speech**. La aplicación solicita:
- Clave del recurso Speech.
- Región del recurso (por ejemplo, `eastus`).
- Endpoint WebSocket universal v2 del recurso, con formato `wss://NOMBRE-DEL-RECURSO.cognitiveservices.azure.com/stt/speech/universal/v2`.

La clave y los datos de conexión se guardan en las preferencias privadas de este teléfono. No se incluye ninguna clave en el código ni en el APK. La clave de una app cliente puede extraerse de un dispositivo comprometido; para distribuir la app a terceros, conviene reemplazar el acceso directo por un backend que emita tokens temporales.

## Crear el APK

### Opción A: GitHub Actions
1. Subí el contenido de esta carpeta a un repositorio GitHub.
2. Abrí la pestaña **Actions**.
3. Ejecutá **Construir APK Android** (o esperá a que termine la compilación automática al subir el código).
4. Abrí la ejecución terminada y descargá el artefacto **TraductorVivo-APK**.
5. Dentro del ZIP está `app-debug.apk`. Copialo al Android e instalalo. Puede ser necesario autorizar la instalación desde esa fuente.

### Opción B: Android Studio
Abrí la carpeta como proyecto Gradle y ejecutá `assembleDebug`.

## Notas
- La detección abierta puede identificar idiomas adicionales; la app está pensada para español, inglés, portugués, francés, alemán e italiano.
- Se necesita Internet durante el uso. La voz se genera en la nube; no se descargan paquetes de idioma al dispositivo.
- La traducción se entrega por frases/segmentos reconocidos, no es interpretación simultánea palabra por palabra. Dejá una pausa natural al terminar cada intervención.
- La primera versión funciona mientras la pantalla de la app está abierta; para uso en segundo plano habría que añadir un servicio Android en primer plano y su notificación persistente.
