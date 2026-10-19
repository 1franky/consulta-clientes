# Consulta de Clientes

Aplicación de escritorio (Java 21 + Swing) que consulta un número de cliente en la base **Carteras** de **SQL Server** y muestra el resultado en una tabla.

Se distribuye como un `.exe` para Windows **con Java embebido**: quien la usa no necesita instalar nada.
Las credenciales y las consultas SQL están en `config.properties`, **fuera del ejecutable**.

---

## Para usuarios (operaciones)

1. Descomprimir el `.zip` en cualquier carpeta (por ejemplo `C:\Herramientas\ConsultaClientes`).
2. Abrir `ConsultaClientes.exe`.
3. Escribir el número de cliente y pulsar **Consultar** (o Enter).

> No mover el `.exe` fuera de su carpeta: necesita las carpetas `app\` y `runtime\` y el archivo `config.properties` a su lado.

---

## Para desarrolladores

### Requisitos (solo en la máquina donde se compila)

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | **21** | Java 11 no sirve: `jpackage` existe desde Java 14. |
| Maven | 3.9+ | https://maven.apache.org/download.cgi — descomprimir y agregar `bin` al `PATH`. Verificar con `mvn -v`. |

### Estructura

```
consulta-clientes/
├── pom.xml                      ← dependencias (driver JDBC de SQL Server + FlatLaf)
├── build.bat                    ← compila y genera el .exe
├── config/config.properties     ← plantilla de configuración
├── config/logo.png              ← logo de la ventana (externo, editable)
├── packaging/icono.ico          ← ícono del .exe
├── src/main/resources/icono.png ← ícono de la ventana
└── src/main/java/com/empresa/consulta/
    ├── App.java                 ← punto de entrada
    ├── Config.java              ← lee el config externo
    ├── ConsultaService.java     ← acceso JDBC
    └── VentanaPrincipal.java    ← ventana Swing
```

### Compilar y generar el .exe (Windows)

1. Editar la línea de `JAVA_HOME` en `build.bat` con la ruta de tu JDK 21, por ejemplo:
   ```bat
   set "JAVA_HOME=C:\Program Files\Java\jdk-21"
   ```
2. En `cmd`, desde la carpeta del proyecto:
   ```bat
   build.bat
   ```

Comandos equivalentes, paso a paso:

```bat
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

mvn clean package

jpackage --type app-image --name ConsultaClientes --app-version 1.0.0 ^
  --input target\dist --main-jar consulta-clientes.jar ^
  --main-class com.empresa.consulta.App --icon packaging\icono.ico ^
  --dest target\installer

copy config\logo.png target\installer\ConsultaClientes\

copy config\config.properties target\installer\ConsultaClientes\
```

### Resultado

```
target\installer\ConsultaClientes\
├── ConsultaClientes.exe      ← lo que abre el usuario
├── config.properties         ← credenciales (editable)
├── logo.png                  ← logo (opcional)
├── app\                      ← jar de la aplicación
└── runtime\                  ← Java embebido
```

Comprimir la carpeta `ConsultaClientes` completa en un `.zip` y compartirla.

### Probar sin generar el .exe

```bat
mvn clean package
java -jar target\dist\consulta-clientes.jar
```

Con `config.properties` en la carpeta desde donde se ejecuta el comando.

---

## Configuración (`config.properties`)

```properties
sqlserver.nombre=Carteras
sqlserver.url=jdbc:sqlserver://servidor-sql:1433;databaseName=Carteras;encrypt=true;trustServerCertificate=true
sqlserver.usuario=usuario_lectura
sqlserver.password=cambiar_esto
sqlserver.consulta=SELECT * FROM dbo.maeDirectorio WHERE NumeroCliente = ?
```

El archivo se busca en este orden:

1. La ruta indicada con `-Dconfig=C:\ruta\config.properties`.
2. La carpeta donde está `ConsultaClientes.exe`.
3. La carpeta de trabajo actual.

Notas:

- La consulta debe tener **un solo `?`**, que se reemplaza por el número de cliente como valor numérico.
- En este archivo la barra invertida se escribe doble. Para una instancia con nombre de SQL Server: `jdbc:sqlserver://servidor\\INSTANCIA;databaseName=Carteras;...`
- `trustServerCertificate=true` evita errores con certificados autofirmados en SQL Server.
- **No subir credenciales reales al repositorio.** Editar `config.properties` solo en la carpeta distribuida.

### Logo e ícono

| Archivo | Uso | ¿Requiere recompilar? |
|---|---|---|
| `config/logo.png` | Logo arriba a la izquierda de la ventana (PNG con fondo transparente, se escala a 48 px de alto). `build.bat` lo copia junto al `.exe`. | No: basta con reemplazar `logo.png` en la carpeta distribuida. |
| `packaging/icono.ico` | Ícono del `.exe` en el Explorador de Windows (`jpackage --icon`). | Sí |
| `src/main/resources/icono.png` | Ícono de la ventana y de la barra de tareas (va dentro del jar). | Sí |

Si `logo.png` no existe, la ventana se muestra sin logo.

### Seguridad

- La contraseña queda en texto plano: usar un **usuario de solo lectura**.
- La conexión se abre en modo `readOnly`.
- Límites: 10 s para conectar, 30 s por consulta y un máximo de 1000 filas (configurables en `ConsultaService.java`).

---

## Instalador `.exe` (opcional)

Para generar un instalador en lugar de la carpeta portable, cambiar `--type app-image` por `--type exe` e instalar [WiX Toolset 3.14](https://github.com/wixtoolset/wix3/releases).
No se recomienda en este caso: se instalaría en `Program Files` y los usuarios sin permisos de administrador no podrían editar `config.properties`.
