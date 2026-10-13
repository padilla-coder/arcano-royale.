# Arcano Royale: de juego web a APK y Play Store

Esta carpeta tiene el juego (`www/index.html`) y todo lo necesario para convertirlo en app Android con Capacitor.
El proyecto de Android todavía no está generado. Se crea con un comando (paso 2 de cada camino).

## Camino A: sin instalar nada (GitHub)

1. Crea un repositorio en github.com y sube el contenido de esta carpeta.
2. Entra a la pestaña **Actions**, elige **Compilar APK de Android** y pulsa **Run workflow**.
3. Cuando termine (unos 5 a 8 minutos), baja el archivo de la sección **Artifacts**:
   - `arcano-royale-apk`: el APK. Pásalo por WhatsApp o Drive; tus conocidos lo instalan permitiendo "orígenes desconocidos".
   - `arcano-royale-aab-sin-firmar`: el AAB sin firmar. No sirve todavía para Play Store (ver Camino B, paso 5).

## Camino B: en tu computador (Android Studio)

Necesitas Node 20 o más nuevo y Android Studio (trae Java y el SDK).

```bash
npm install
npx cap add android
npx @capacitor/assets generate --android   # crea los íconos y la pantalla de carga desde /resources
npx cap sync android
npx cap open android                       # abre Android Studio
```

1. En Android Studio espera a que termine el Gradle sync.
2. **Para probar:** conecta tu celular con depuración USB y pulsa Run, o usa **Build > Build APK(s)**.
3. **Para Play Store:** **Build > Generate Signed App Bundle / APK > Android App Bundle**.
4. Crea un keystore nuevo cuando te lo pida. **Guarda el archivo y las contraseñas en dos lugares.** Si los pierdes no podrás actualizar el juego.
5. El resultado es un `.aab`. Ese es el archivo que se sube a Play Console.

## Cambia esto antes de publicar

- **Id del paquete:** `com.arcanoroyale.game` en `capacitor.config.json`. Es único en toda la Play Store y no se puede cambiar después de publicar. Algo como `com.tunombre.arcanoroyale` está bien. Hazlo antes de `npx cap add android`.
- **Versión:** en `android/app/build.gradle`, `versionCode` sube de 1 en 1 con cada actualización que subas.

## Play Console

- Cuenta de desarrollador: pago único de 25 USD.
- Las cuentas personales nuevas suelen tener que hacer una **prueba cerrada con unos 12 testers durante 14 días** antes de poder publicar. Confirma el requisito actual dentro de Play Console, porque Google lo cambia.
- Pide: ícono 512x512, imagen destacada 1024x500, al menos 2 capturas de pantalla, descripción corta y larga, clasificación de contenido y formulario de seguridad de datos.
- **Política de privacidad:** pide una URL aunque no recojas datos. El juego guarda monedas y ajustes solo en el celular, sin cuentas, sin anuncios y sin internet.

## Editar el juego

`www/index.html` es el juego completo en un solo archivo. Si lo editas, corre `npx cap sync android` y vuelve a compilar.
`tools/` guarda la versión sin envoltorio que se usó para generar ese archivo. No la necesitas.

## Siguientes pasos de producto

Ideas que hacen que la gente vuelva: misiones diarias, más héroes y hechizos, un pase de temporada, y anuncios con recompensa (AdMob, con su plugin de Capacitor) para duplicar monedas. Cada una se puede agregar sobre este mismo código.
