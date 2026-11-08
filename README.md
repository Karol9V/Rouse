# Sistema de inventario y ventas

Aplicación web para administrar productos, proveedores, usuarios y ventas de un
negocio. Incluye una interfaz web renderizada con Thymeleaf, una API REST y
autenticación mediante sesión.

> **Última actualización documentada:** 7 de noviembre de 2026  
> **Versión del proyecto:** `0.0.1-SNAPSHOT`

## Contenido

- [Características](#características)
- [Tecnologías](#tecnologías)
- [Requisitos](#requisitos)
- [Configuración](#configuración)
- [Ejecución](#ejecución)
- [Pruebas](#pruebas)
- [Documentación Javadoc](#documentación-javadoc)
- [Estructura](#estructura)
- [Interfaz web](#interfaz-web)
- [API REST](#api-rest)
- [Autenticación y permisos](#autenticación-y-permisos)
- [Base de datos](#base-de-datos)
- [Manejo de errores](#manejo-de-errores)

## Características

- Inicio de sesión y cierre de sesión con sesión HTTP.
- Contraseñas codificadas con BCrypt.
- Control de acceso por rol (administrador y vendedor).
- Protección CSRF para solicitudes que modifican datos.
- Administración de productos, proveedores y usuarios.
- Registro, consulta, actualización y eliminación de ventas.
- Descuento de inventario al registrar una venta; se rechaza la operación si
  falta el producto o no hay existencias suficientes.
- Interfaz web en español con vistas Thymeleaf y cliente JavaScript.
- API REST JSON con respuestas HTTP y manejo centralizado de errores.

## Tecnologías

- Java 17
- Spring Boot 4.1.1
- Spring MVC y Spring Web
- Thymeleaf
- Spring JDBC (`JdbcTemplate`)
- MySQL
- Spring Security Crypto (BCrypt)
- JUnit y Mockito para pruebas
- Maven Wrapper

## Requisitos

- JDK 17.
- MySQL disponible y accesible por la aplicación.
- No es obligatorio instalar Maven globalmente: el repositorio incluye Maven
  Wrapper (`mvnw` y `mvnw.cmd`).

Comprueba las versiones instaladas:

```powershell
java -version
.\mvnw.cmd -version
```

## Configuración

La aplicación lee la conexión de base de datos de variables de entorno. Los
valores predeterminados están definidos en `src/main/resources/application.properties`:

| Variable | Predeterminado | Descripción |
|---|---|---|
| `DB_HOST` | `localhost` | Servidor MySQL |
| `DB_PORT` | `3306` | Puerto MySQL |
| `DB_USER` | `root` | Usuario MySQL |
| `DB_PASSWORD` | *(vacío)* | Contraseña MySQL |

La base de datos esperada se llama `bd_pasteleria`. Configura las variables antes
de iniciar la aplicación, por ejemplo, en PowerShell:

```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_USER = "root"
$env:DB_PASSWORD = "tu contraseña"
```

No guardes credenciales reales en el repositorio. Usa las variables de entorno
correspondientes al entorno local o de despliegue.

## Ejecución

Desde la raíz del proyecto:

```powershell
.\mvnw.cmd spring-boot:run
```

Abre [http://localhost:8080/login](http://localhost:8080/login) en el navegador.
La aplicación requiere que MySQL esté iniciado y que exista la base de datos
configurada.

Para empaquetar el proyecto:

```powershell
.\mvnw.cmd clean package
```

El JAR ejecutable se genera en `target/inventario-0.0.1-SNAPSHOT.jar`:

```powershell
java -jar target/inventario-0.0.1-SNAPSHOT.jar
```

## Pruebas

Ejecuta todas las pruebas automatizadas:

```powershell
.\mvnw.cmd test
```

Las pruebas unitarias de controladores usan JUnit y Mockito; los DAOs y la base
de datos no se conectan a un servidor real en esas pruebas. También se incluyen
pruebas de permisos y una prueba de carga del contexto Spring.

## Documentación Javadoc

Las clases Java públicas incluyen documentación Javadoc. Genera el sitio HTML
localmente con:

```powershell
.\mvnw.cmd javadoc:javadoc
```

Los archivos generados quedan en `target/reports/apidocs/`; abre
`target/reports/apidocs/index.html` para consultar la documentación. Este directorio
es una salida de compilación y no necesita añadirse al control de versiones.

## Estructura

```text
src/
├── main/
│   ├── java/com/prueba/inventario/
│   │   ├── controller/
│   │   │   ├── mvc/          # Rutas web y selección de vistas
│   │   │   └── res/          # API REST y manejo de errores
│   │   ├── model/
│   │   │   ├── dao/          # Acceso JDBC a MySQL
│   │   │   └── vo/           # Objetos de datos del dominio
│   │   ├── security/         # Sesión, permisos, interceptor y BCrypt
│   │   └── InventarioApplication.java
│   └── resources/
│       ├── static/           # CSS, JavaScript e imágenes
│       ├── templates/        # Vistas Thymeleaf
│       └── application.properties
└── test/java/com/prueba/inventario/
    ├── controller/           # Pruebas unitarias de controladores
    └── security/             # Pruebas de permisos
```

## Interfaz web

| Ruta | Descripción |
|---|---|
| `/login` | Formulario de inicio de sesión |
| `/` | Página inicial |
| `/productos` | Módulo de productos |
| `/ventas` | Módulo de ventas |
| `/proveedores` | Módulo de proveedores |
| `/usuarios` | Módulo de usuarios |

Las rutas web, excepto `/login` y recursos estáticos, requieren una sesión
autenticada. El rol vendedor solo puede acceder a la página inicial, productos y
ventas; las páginas de proveedores y usuarios son para administradores.

## API REST

Los endpoints reciben y devuelven JSON, excepto cuando se indica otro estado.
Los identificadores se representan como números enteros largos.

### Autenticación — `/api/auth`

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/login` | Inicia sesión con `correo` y `contrasena` |
| `GET` | `/api/auth/me` | Devuelve la identidad y el token CSRF de la sesión |
| `POST` | `/api/auth/logout` | Cierra la sesión |

Ejemplo de inicio de sesión:

```json
{
  "correo": "usuario@example.com",
  "contrasena": "contraseña"
}
```

Una respuesta correcta incluye los datos del usuario y `csrfToken`. El servidor
mantiene la sesión HTTP; el cliente debe enviar la cookie de sesión y el token
CSRF en `X-CSRF-TOKEN` para las operaciones que modifican datos.

### Productos — `/api/productos`

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/productos` | Lista productos |
| `GET` | `/api/productos/{id}` | Consulta un producto |
| `POST` | `/api/productos` | Crea un producto |
| `PUT` | `/api/productos/{id}` | Actualiza un producto |
| `DELETE` | `/api/productos/{id}` | Elimina un producto |

Los campos de un producto son `nombre`, `descripcion`, `categoria`,
`precioVenta`, `costo`, `stock`, `stockMinimo`, `unidadMedida`, `idProveedor` y
`estado`. El identificador se asigna al crear el registro. Ejemplo:

```json
{
  "nombre": "Torta de chocolate",
  "descripcion": "Torta mediana",
  "categoria": "Repostería",
  "precioVenta": 25000,
  "costo": 14000,
  "stock": 8,
  "stockMinimo": 2,
  "unidadMedida": "unidad",
  "idProveedor": 1,
  "estado": "Disponible"
}
```

### Proveedores — `/api/proveedores`

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/proveedores` | Lista proveedores |
| `GET` | `/api/proveedores/{id}` | Consulta un proveedor |
| `POST` | `/api/proveedores` | Crea un proveedor |
| `PUT` | `/api/proveedores/{id}` | Actualiza un proveedor |
| `DELETE` | `/api/proveedores/{id}` | Elimina un proveedor |

Los campos editables son `nombreEmpresa`, `nombreContacto`, `telefono`,
`correo`, `direccion` y `estado`.
`nombreEmpresa`, `nombreContacto` y `correo` son obligatorios; los demás campos
pueden omitirse o ser `null`, incluida `direccion`.

### Usuarios — `/api/usuarios`

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/usuarios` | Lista usuarios (sin incluir hashes de contraseña) |
| `GET` | `/api/usuarios/roles` | Lista los roles disponibles |
| `GET` | `/api/usuarios/{id}` | Consulta un usuario |
| `POST` | `/api/usuarios` | Crea un usuario; la contraseña se codifica con BCrypt |
| `PUT` | `/api/usuarios/{id}` | Actualiza un usuario |
| `DELETE` | `/api/usuarios/{id}` | Elimina un usuario |

El cuerpo de creación contiene `nombre`, `apellido`, `correo`, `contrasena`,
`rol` y `estado`. La contraseña es obligatoria al crear; al actualizar se puede
omitir o dejar vacía para conservar la contraseña existente. El endpoint de
roles devuelve los valores permitidos para el campo `rol`.

### Ventas — `/api/ventas`

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/ventas` | Lista ventas |
| `GET` | `/api/ventas/{id}` | Consulta una venta |
| `POST` | `/api/ventas` | Registra una venta y sus detalles |
| `PUT` | `/api/ventas/{id}` | Actualiza los campos de una venta |
| `DELETE` | `/api/ventas/{id}` | Elimina una venta |

El cuerpo de creación incluye `metodoPago` y una lista `detalles`. Cada detalle
requiere `idProducto`, `cantidad` y `precioUnitario`. El subtotal y total se
calculan en el servidor; `descuento` es opcional y por defecto es cero.

```json
{
  "metodoPago": "Efectivo",
  "estado": "Completada",
  "descuento": 0,
  "detalles": [
    {
      "idProducto": 1,
      "cantidad": 2,
      "precioUnitario": 12000
    }
  ]
}
```

Un administrador debe incluir `idUsuario`; un vendedor queda asociado
automáticamente a su propio usuario autenticado.

En el cuerpo de creación, `fechaVenta` e `idUsuario` son opcionales para un
vendedor. Los importes `subtotal` y `total` se calculan en el servidor; `estado`
es opcional y depende de los valores aceptados por el sistema.

### Detalles de venta — `/api/detalles-venta`

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/detalles-venta` | Lista todos los detalles |
| `GET` | `/api/detalles-venta?ventaId={id}` | Lista los detalles de una venta |
| `GET` | `/api/detalles-venta/{id}` | Consulta un detalle |
| `POST` | `/api/detalles-venta` | Crea un detalle |
| `PUT` | `/api/detalles-venta/{id}` | Actualiza un detalle |
| `DELETE` | `/api/detalles-venta/{id}` | Elimina un detalle |

El cuerpo de creación/actualización contiene `idVenta`, `idProducto`,
`cantidad` y `precioUnitario`.

### Respuestas habituales

- `200 OK`: consulta correcta.
- `201 Created`: recurso creado; la cabecera `Location` identifica el recurso.
- `204 No Content`: actualización o eliminación correcta.
- `400 Bad Request`: solicitud o datos inválidos.
- `401 Unauthorized`: falta una sesión válida o las credenciales son
  incorrectas.
- `403 Forbidden`: rol sin permisos o token CSRF inválido.
- `404 Not Found`: no se encontró el recurso.
- `409 Conflict`: conflicto con el inventario o restricciones de integridad.
- `503 Service Unavailable`: error de acceso a la base de datos.

Los errores de la API tienen un cuerpo JSON con el campo `message`.

## Autenticación y permisos

- El inicio de sesión acepta usuarios con estado `Activo` y roles
  `Administrador`/`Admin` o `Vendedor`.
- Las contraseñas se validan y almacenan usando BCrypt; la aplicación no
  devuelve hashes de contraseña en las respuestas REST.
- Los permisos se verifican para cada solicitud a través de un interceptor.
- El vendedor puede consultar productos y ventas; puede crear, actualizar y
  eliminar ventas, pero no administrar productos, usuarios ni proveedores.
- El administrador tiene acceso a todos los módulos y operaciones.
- Las solicitudes de escritura autenticadas deben incluir `X-CSRF-TOKEN`.

## Base de datos

La persistencia se realiza con JDBC y MySQL. El código consulta las tablas
`usuarios`, `productos`, `proveedores`, `ventas` y `detalle_venta`, incluyendo
relaciones entre ventas, usuarios y productos.

El repositorio no incluye scripts SQL ni migraciones para crear la base de datos
o sus tablas. Antes de ejecutar la aplicación, prepara `bd_pasteleria` con un
esquema compatible con los DAO del proyecto. La aplicación requiere permisos
de lectura y escritura para el usuario configurado.

## Manejo de errores

El manejador REST centralizado traduce errores comunes a respuestas JSON:

- Violaciones de integridad de la base de datos: `409 Conflict`.
- Errores de acceso a datos: `503 Service Unavailable`.
- JSON inválido: `400 Bad Request`.
- Errores de estado generados por los controladores (por ejemplo, stock
  insuficiente): conservan el estado HTTP correspondiente.
