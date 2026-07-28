# 🔄 SimpleDriveSync

> Aplicación nativa para Android de sincronización inteligente de carpetas públicas y privadas de Google Drive con almacenamiento local y protección anti-ban.

`SimpleDriveSync` es una aplicación diseñada para realizar copias de seguridad e incremental sync (estilo `git pull`) desde Google Drive a tu almacenamiento local sin re-descargar archivos existentes y protegiendo la descarga frente a límites de velocidad, cuotas 403 y bloqueos de conexión.

---

## 🚀 Características principales

- **📱 App Nativa Android (Kotlin + Jetpack Compose + Material 3):**
  - Interfaz moderna e intuitiva con soporte de temas claro/oscuro.
  - Selector de carpeta nativo con Storage Access Framework (SAF).
  - Autenticación con **Google OAuth 2.0 Web** o **API Key**.
  - Barra de progreso visual con estadísticas en tiempo real (`Descargados`, `Saltados`, `Errores`).
  - Auto-pausa inteligente de 30 minutos con cuenta regresiva ante límites de cuotas HTTP 403.
  - Delays configurables anti-throttling para evitar congelamiento de descargas.

---

## 🛠️ Estructura del proyecto

```text
SimpleDriveSync/
├── app/                  # Código fuente de la aplicación Android
│   ├── src/main/java/    # UI (Jetpack Compose), ViewModels y Data Layer
│   └── src/main/res/     # Recursos, íconos y layout
├── gradle/               # Gradle wrapper y versión catalog
├── .github/workflows/    # CI/CD automatizado con GitHub Actions
├── build.gradle.kts      # Configuración de Gradle raíz
└── settings.gradle.kts   # Configuración del proyecto
```

---

## 💻 Compilación e Instalación

### Descargar APK pre-compilado
1. Descarga el APK desde la sección de **Releases** de este repositorio.
2. Instala el APK en tu dispositivo Android.

### Compilar desde el código fuente
```bash
# Clonar el repositorio
git clone https://github.com/EduLz/SimpleDriveSync.git
cd SimpleDriveSync

# Compilar APK Debug
./gradlew assembleDebug
```

---

## 🛡️ Licencia

Distribuido bajo la licencia MIT. Consulta `LICENSE` para obtener más información.
