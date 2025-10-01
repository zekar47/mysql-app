# Programa MySQL

## 📖 Descripción General

**MySQL Workbench Mini** es una aplicación de escritorio desarrollada en Java Swing que proporciona una interfaz gráfica para interactuar con bases de datos MySQL. Ofrece funcionalidades similares a herramientas profesionales como MySQL Workbench pero con un enfoque minimalista y exclusivamente para operaciones básicas CRUD. Aparte cuenta con una opción para ejecutar comandos arbitrarios de MySQL.

## ✨ Características Principales

### 🔐 Sistema de Conexión

- **Interfaz de login** con campos para host, puerto, usuario y contraseña
- **Selección de base de datos** con capacidad de crear nuevas bases de datos

### 🗃️ Editor de Tablas

- **Navegación entre tablas** con combobox selector
- **Visualización de datos** en formato tabla editable
- **Operaciones CRUD** completas:
  - ✅ **Crear**: Insertar nuevas filas
  - 📖 **Leer**: Visualizar datos existentes
  - ✏️ **Actualizar**: Editar celdas directamente
  - 🗑️ **Eliminar**: Borrar filas seleccionadas

### 🔧 Funcionalidades Avanzadas

- **Validación de tipos de datos** en tiempo real
- **Verificación de claves foráneas** (FK)
- **Detección de ENUMs** con menús desplegables automáticos
- **Información de metadatos** en tooltips
- **Tema oscuro** integrado

## 🏗️ Arquitectura del Código

### Estructura Principal

```
App.java
├── createAndShowLogin()
├── createDatabaseSelector()
├── createMainEditor()
├── loadAndShowTable()
└── Clases Internas:
    ├── DatabaseTableModel
    ├── TableMetadata
    ├── ColumnInfo
    ├── ForeignKeyInfo
    ├── RowChange
    └── HeaderRenderer
```

### 🔄 Flujo de la Aplicación

1. **Login** → **Selector de BD** → **Editor Principal**
2. En el editor: **Seleccionar tabla** → **Editar datos** → **Aplicar cambios**

## 🎨 Componentes de Interfaz Gráfica

### Login (`createAndShowLogin`)

```java
// Componentes principales:
JTextField hostField, portField, userField
JPasswordField passField
JButton connectBtn
JTextArea status
```

### Selector de Base de Datos (`createDatabaseSelector`)

```java
JComboBox<String> dbBox - Lista desplegable de bases de datos
JButton useBtn - Botón para confirmar selección
```

### Editor Principal (`createMainEditor`)

```java
// Barra superior
JComboBox<String> tableBox - Selector de tablas
JButton refreshBtn, applyBtn, runSqlBtn, disconnectBtn, addRowBtn, deleteRowBtn

// Área central
JTable tableView - Tabla de datos principal
JScrollPane centerScroll - Panel desplazable

// Área inferior
JTextArea messages - Consola de mensajes
```

## 🗃️ Sistema de Modelo de Datos

### DatabaseTableModel

Clase central que extiende `DefaultTableModel` y maneja:

```java
public class DatabaseTableModel extends DefaultTableModel {
    private List<Integer> rowStatus;      // 0=sin cambios, 1=actualizado, 2=eliminado, 3=insertado
    private List<Vector<Object>> originalRows;  // Copia de seguridad de los datos originales
    private TableMetadata meta;           // Metadatos de la tabla

    // Métodos principales:
    boolean hasRealChanges()              // Detecta cambios reales (no falsos positivos)
    boolean applyChanges(JTextArea)       // Aplica cambios a la BD
    void discardChanges()                 // Descarta cambios pendientes
    void addNewRow()                      // Añade fila para inserción
    void markRowsForDeletion(int[])       // Marca filas para eliminar
}
```

### Sistema de Estados de Filas

- **0**: Sin cambios
- **1**: Actualizada (modificada)
- **2**: Eliminada (marcada para borrar)
- **3**: Nueva (para insertar)

## 🔍 Manejo de Metadatos

### TableMetadata

Almacena información estructural de las tablas:

```java
class TableMetadata {
    String tableName;
    List<ColumnInfo> columns;
    List<String> primaryKeys;
    List<ForeignKeyInfo> foreignKeys;   // Claves foráneas importadas
    List<ForeignKeyInfo> exportedKeys;  // Claves exportadas (referenciadas)
}
```

### ColumnInfo

Información detallada de cada columna:

```java
class ColumnInfo {
    String name;           // Nombre de la columna
    int jdbcType;          // Tipo JDBC (Types.INTEGER, etc.)
    String typeName;       // Nombre del tipo (VARCHAR, INT, etc.)
    boolean nullable;      // ¿Permite NULL?
    List<String> enumValues; // Valores posibles para ENUM (NUEVO)
}
```

## 🎯 Funcionalidades Especiales

### Detección Automática de ENUM

```java
private static List<String> getEnumValues(String tableName, String columnName) {
    // Ejecuta: SHOW COLUMNS FROM tabla LIKE 'columna'
    // Parsea: ENUM('valor1','valor2') → Lista de valores
}
```

### Configuración de Editores para ENUM

```java
private static void setupEnumEditors(JTable table, TableMetadata meta) {
    for (ColumnInfo col : meta.columns) {
        if (col.enumValues != null) {
            JComboBox<String> comboBox = new JComboBox<>(col.enumValues.toArray(new String[0]));
            column.setCellEditor(new DefaultCellEditor(comboBox));
        }
    }
}
```

### Tooltips Inteligentes

```java
public String getColumnTooltip(int colIndex) {
    ColumnInfo ci = meta.columns.get(colIndex);
    StringBuilder sb = new StringBuilder();
    sb.append(ci.name).append(" : ").append(ci.typeName);
    if (ci.enumValues != null) {
        sb.append(" ENUM: ").append(String.join(", ", ci.enumValues));
    }
    // ... más información
}
```

## ⚙️ Sistema de Validación

### Validación de Tipos

```java
private String validateRowTypes(Vector<Object> values) {
    // Verifica conversiones numéricas
    // Valida restricciones NOT NULL
    // Comprueba formatos básicos
}
```

### Validación de Claves Foráneas

```java
private String validateForeignKeys(Vector<Object> values) throws SQLException {
    // Para cada FK, verifica que el valor referenciado exista
    // Ejecuta: SELECT COUNT(*) FROM tabla_referencia WHERE columna = ?
}
```

## 🔄 Transacciones y Aplicación de Cambios

### Flujo de `applyChanges()`

1. **Validación** de tipos y claves foráneas
2. **Preparación** de operaciones (INSERT, UPDATE, DELETE)
3. **Ejecución en transacción**:
   ```java
   connection.setAutoCommit(false);
   try {
       // Ejecutar DELETEs, INSERTs, UPDATEs
       connection.commit();
   } catch (SQLException e) {
       connection.rollback();
   }
   ```
4. **Actualización** del modelo con datos frescos

## 🎨 Personalización de la Interfaz

### Tema Oscuro

```java
private static void setDarkTheme() {
    UIManager.put("Panel.background", new Color(45, 45, 45));
    UIManager.put("Label.foreground", new Color(230, 230, 230));
    // ... más configuraciones de colores
}
```

## 🛠️ Métodos de Utilidad

### Construcción de Modelos desde ResultSet

```java
private static DefaultTableModel buildTableModel(ResultSet rs) throws SQLException {
    // Convierte un ResultSet en un DefaultTableModel
}
```

### Escapado de Identificadores

```java
private static String quoteIdentifier(String id) {
    return "`" + id.replace("`", "``") + "`";
}
```

## 🔄 Manejo de Eventos

### Cambio de Tabla

```java
tableBox.addActionListener(e -> {
    if (currentModel.hasRealChanges()) {
        // Mostrar diálogo de confirmación
        // Aplicar, descartar o cancelar
    }
});
```

### Botones de Acción

- **Refresh Tables**: Recarga la lista de tablas
- **Add Row**: Añade nueva fila para inserción
- **Delete Selected**: Marca filas para eliminación
- **Apply Changes**: Aplica cambios pendientes
- **Run SQL**: Ejecuta consultas SQL personalizadas
- **Disconnect**: Cierra conexión y vuelve al login

## 🐛 Solución de Problemas Conocidos

### Problemas Resueltos

1. **Diálogo de cambios innecesario**
   - **Causa**: La fila de inserción vacía se consideraba como cambio
   - **Solución**: Método `hasRealChanges()` que ignora filas vacías

2. **Columna _MARK_ confusa**
   - **Causa**: Enfoque poco intuitivo para marcar eliminaciones
   - **Solución**: Reemplazado por botones explícitos "Añadir Fila" y "Eliminar Seleccionados"

3. **ENUMs sin interfaz amigable**
   - **Causa**: Los campos ENUM requerían escritura manual de valores
   - **Solución**: Detección automática y menús desplegables

## 📋 Requisitos del Sistema

- **MySQL**: Probado con la versión 8.4
  La versión de Java es declarativa y reproducible gracias a [flake.nix](./flake.nix), y la versión del conector gracias a [pom.xml](pom.xml).

## 🚀 Instrucciones de Ejecución

2. **Ejecutar y Compilar**:
   ```bash
   mvn clean compile -q exec:java
   ```
