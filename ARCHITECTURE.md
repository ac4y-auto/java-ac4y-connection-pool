# java-ac4y-connection-pool - Architektúra Dokumentáció

## Áttekintés

Az `ac4y-connection-pool` modul JNDI-alapú adatbázis connection pooling kezelést biztosít Java EE / Jakarta EE környezetekhez. Application server által kezelt DataSource-okat használ a connection pool-hoz.

**Verzió:** 1.0.0
**Java verzió:** 11
**Szervezet:** ac4y-auto

## Fő Komponensek

### 1. Connection Pool Manager

#### `DBConnection`
JNDI DataSource alapú connection pool kezelő osztály.

**Felelősség:**
- JNDI lookup végrehajtása
- DataSource elérése az application server pool-ból
- Connection lekérése a pool-ból
- Connection tárolása és visszaadása

**Konstruktor:**
```java
public DBConnection() throws NamingException, SQLException
```

**Működési Elv:**

1. **Initial Context létrehozása:**
```java
Context initialContext = new InitialContext();
```

2. **Environment Context lookup:**
```java
Context environmentContext = (Context) initialContext.lookup("java:comp/env");
```

3. **DataSource lookup:**
```java
String dataResourceName = "jdbc/ac4y";
DataSource dataSource = (DataSource) environmentContext.lookup(dataResourceName);
```

4. **Connection lekérése a pool-ból:**
```java
setConnection(dataSource.getConnection());
```

**Metódusok:**
- `getConnection()`: Pooled connection lekérése
- `setConnection(Connection connection)`: Connection beállítása (private)

**JNDI Név:**
- Default: `jdbc/ac4y`
- Teljes JNDI path: `java:comp/env/jdbc/ac4y`

## JNDI Konfiguráció

### Tomcat (context.xml)

```xml
<Context>
    <Resource name="jdbc/ac4y"
              auth="Container"
              type="javax.sql.DataSource"
              maxTotal="100"
              maxIdle="30"
              maxWaitMillis="10000"
              username="dbuser"
              password="dbpassword"
              driverClassName="com.mysql.cj.jdbc.Driver"
              url="jdbc:mysql://localhost:3306/mydb?autoReconnect=true"/>
</Context>
```

### JBoss / WildFly (standalone.xml)

```xml
<subsystem xmlns="urn:jboss:domain:datasources:5.0">
    <datasources>
        <datasource jndi-name="java:comp/env/jdbc/ac4y"
                    pool-name="Ac4yPool"
                    enabled="true"
                    use-java-context="true">
            <connection-url>jdbc:mysql://localhost:3306/mydb</connection-url>
            <driver>mysql</driver>
            <pool>
                <min-pool-size>10</min-pool-size>
                <max-pool-size>100</max-pool-size>
            </pool>
            <security>
                <user-name>dbuser</user-name>
                <password>dbpassword</password>
            </security>
        </datasource>
    </datasources>
</subsystem>
```

### GlassFish / Payara

Admin Console-on keresztül:
1. Resources → JDBC → JDBC Connection Pools → New
   - Pool Name: Ac4yPool
   - Resource Type: javax.sql.DataSource
   - Database Driver: com.mysql.cj.jdbc.Driver

2. Resources → JDBC → JDBC Resources → New
   - JNDI Name: jdbc/ac4y
   - Pool Name: Ac4yPool

### WebLogic

```xml
<jdbc-data-source>
    <name>ac4y-datasource</name>
    <jndi-name>jdbc/ac4y</jndi-name>
    <data-source-type>GENERIC</data-source-type>
    <jdbc-driver-params>
        <url>jdbc:mysql://localhost:3306/mydb</url>
        <driver-name>com.mysql.cj.jdbc.Driver</driver-name>
        <properties>
            <property>
                <name>user</name>
                <value>dbuser</value>
            </property>
            <property>
                <name>password</name>
                <value>dbpassword</value>
            </property>
        </properties>
    </jdbc-driver-params>
    <jdbc-connection-pool-params>
        <initial-capacity>10</initial-capacity>
        <max-capacity>100</max-capacity>
    </jdbc-connection-pool-params>
</jdbc-data-source>
```

### web.xml Konfiguráció

Az alkalmazás `web.xml`-jében hivatkozni kell a DataSource-ra:

```xml
<web-app>
    <resource-ref>
        <description>Ac4y Database Connection Pool</description>
        <res-ref-name>jdbc/ac4y</res-ref-name>
        <res-type>javax.sql.DataSource</res-type>
        <res-auth>Container</res-auth>
    </resource-ref>
</web-app>
```

## Függőségek

### Maven Függőség

```xml
<dependency>
    <groupId>ac4y</groupId>
    <artifactId>ac4y-base</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Tranzitív függőségek:**
- ac4y-utility (1.0.0) - ac4y-base-n keresztül

**Java EE API-k (provided scope):**

Az application server biztosítja ezeket:

```xml
<!-- Már az application server-ben -->
<dependency>
    <groupId>javax.naming</groupId>
    <artifactId>javax.naming-api</artifactId>
    <version>1.2</version>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>javax.sql</groupId>
    <artifactId>javax.sql-api</artifactId>
    <version>1.0</version>
    <scope>provided</scope>
</dependency>
```

## Tipikus Használati Minták

### 1. Egyszerű Connection Lekérés

```java
try {
    DBConnection dbConnection = new DBConnection();
    Connection conn = dbConnection.getConnection();

    // Query végrehajtása
    PreparedStatement ps = conn.prepareStatement("SELECT * FROM users");
    ResultSet rs = ps.executeQuery();

    while (rs.next()) {
        // feldolgozás
    }

    rs.close();
    ps.close();
    conn.close(); // Visszaadja a pool-nak!

} catch (NamingException e) {
    System.err.println("JNDI DataSource not found: jdbc/ac4y");
    e.printStackTrace();
} catch (SQLException e) {
    System.err.println("Database error");
    e.printStackTrace();
}
```

**Fontos:** A `conn.close()` NEM zárja le a kapcsolatot, hanem visszaadja a pool-nak!

### 2. Try-with-resources Használat

```java
try {
    DBConnection dbConnection = new DBConnection();

    try (Connection conn = dbConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement("SELECT * FROM products");
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            String name = rs.getString("name");
            // feldolgozás
        }
    }

} catch (NamingException | SQLException e) {
    e.printStackTrace();
}
```

### 3. Transaction Kezelés Pooled Connection-nel

```java
Connection conn = null;
try {
    DBConnection dbConnection = new DBConnection();
    conn = dbConnection.getConnection();

    conn.setAutoCommit(false);

    // Műveletek
    PreparedStatement ps1 = conn.prepareStatement("UPDATE ...");
    ps1.executeUpdate();

    PreparedStatement ps2 = conn.prepareStatement("INSERT ...");
    ps2.executeUpdate();

    conn.commit();

    ps1.close();
    ps2.close();

} catch (Exception e) {
    if (conn != null) {
        try {
            conn.rollback();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
    e.printStackTrace();
} finally {
    if (conn != null) {
        try {
            conn.setAutoCommit(true);
            conn.close(); // Visszaadás a pool-nak
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
```

### 4. Servlet Használat

```java
@WebServlet("/users")
public class UserServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            DBConnection dbConnection = new DBConnection();
            Connection conn = dbConnection.getConnection();

            PreparedStatement ps = conn.prepareStatement("SELECT * FROM users");
            ResultSet rs = ps.executeQuery();

            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("name")));
            }

            rs.close();
            ps.close();
            conn.close();

            request.setAttribute("users", users);
            request.getRequestDispatcher("/users.jsp").forward(request, response);

        } catch (NamingException | SQLException e) {
            throw new ServletException("Database error", e);
        }
    }
}
```

### 5. DAO Pattern Integráció

```java
public class UserDAO {

    public List<User> findAll() throws NamingException, SQLException {
        List<User> users = new ArrayList<>();

        DBConnection dbConnection = new DBConnection();
        Connection conn = dbConnection.getConnection();

        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(mapUser(rs));
            }
        } finally {
            conn.close();
        }

        return users;
    }

    public User findById(int id) throws NamingException, SQLException {
        DBConnection dbConnection = new DBConnection();
        Connection conn = dbConnection.getConnection();

        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return mapUser(rs);
            }
            return null;
        } finally {
            conn.close();
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(rs.getInt("id"), rs.getString("name"), rs.getString("email"));
    }
}
```

## Connection Pool vs Direct Connection

### ac4y-database (Direct Connection)
```java
// Properties-ből direkt connection
DBConnection dbConn = new DBConnection("db.properties");
Connection conn = dbConn.getConnection();
// Minden hívás új connection-t nyit!
```

### ac4y-connection-pool (Pooled Connection)
```java
// JNDI-ből pooled connection
DBConnection dbConn = new DBConnection();
Connection conn = dbConn.getConnection();
// Pool-ból ad vissza létező connection-t!
```

**Előnyök:**
- **Teljesítmény**: Nincs connection létrehozási overhead
- **Skálázhatóság**: Több egyidejű kérés hatékony kezelése
- **Resource management**: Application server kezeli a pool-t
- **Connection reuse**: Gyors connection újrafelhasználás

**Hátrányok:**
- **Komplexitás**: Application server konfiguráció szükséges
- **Deployment**: JNDI konfiguráció minden környezetben
- **Portability**: Application server függő

## AI Agent Használati Útmutató

### Gyors Döntési Fa

**Kérdés:** Milyen környezetben fut az alkalmazás?

1. **Java EE / Jakarta EE (Tomcat, JBoss, GlassFish)** → `ac4y-connection-pool`
   - JNDI DataSource van? → `new DBConnection()`
   - Több egyidejű felhasználó? → Connection pool KÖTELEZŐ

2. **Standalone Java (CLI, Desktop app)** → `ac4y-database`
   - Nincs application server? → Használd ac4y-database-t
   - Egyszerű script? → Direkt connection elég

3. **Spring Boot / Micronaut** → Saját connection pool
   - Modern framework? → Használd a framework pool-ját (HikariCP)

### Token-hatékony Tudás

**Mit tartalmaz:**
- JNDI DataSource lookup
- Pooled connection kezelés
- Java EE integration

**Mit NEM tartalmaz:**
- Pool konfiguráció (→ application server)
- Connection pool implementáció (→ application server biztosítja)
- Custom pool logic

**Függőségek:**
- ac4y-base (1.0.0)
- javax.naming API (provided)
- javax.sql API (provided)

**Kivételek:**
- NamingException (JNDI lookup hiba)
- SQLException (database hiba)

**JNDI név:** `java:comp/env/jdbc/ac4y`

## Troubleshooting

### Problem: NamingException: Name jdbc/ac4y is not bound

**Ok:** DataSource nincs konfigurálva az application server-ben.

**Megoldás:**
1. Ellenőrizd a DataSource konfigurációt (context.xml, standalone.xml, stb.)
2. Ellenőrizd a JNDI nevet: `jdbc/ac4y`
3. Restart application server

### Problem: SQLException: No suitable driver found

**Ok:** JDBC driver nincs telepítve az application server-be.

**Megoldás:**
1. Másold a JDBC driver JAR-t az application server lib könyvtárába
   - Tomcat: `$CATALINA_HOME/lib/`
   - JBoss: `$JBOSS_HOME/modules/`
   - GlassFish: `$GLASSFISH_HOME/domains/domain1/lib/`

### Problem: Connection pool exhausted

**Ok:** Túl sok egyidejű connection, vagy connection leak.

**Megoldás:**
1. Növeld a pool max-capacity-t
2. Ellenőrizd, hogy minden connection `close()` hívódik
3. Használj try-with-resources-t
4. Connection timeout beállítása

## Build és Telepítés

```bash
# Build
mvn clean install

# Test (Note: Teszteléshez mock JNDI context kell!)
mvn test

# Deploy to GitHub Packages
mvn deploy
```

**GitHub Packages:**
```xml
<dependency>
    <groupId>ac4y</groupId>
    <artifactId>ac4y-connection-pool</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Best Practices

1. **Mindig close()-old a connection-öket** (visszaadás a pool-nak)
2. **Try-with-resources használata** ajánlott
3. **Connection leak detection** engedélyezése éles környezetben
4. **Pool size tuning** terhelés alapján
5. **Monitoring** connection pool metrikák figyelése
6. **Test JNDI név konzisztenciája** dev/test/prod környezetekben
7. **Resource-ref** deklarálása web.xml-ben

## Megjegyzések

- Ez a modul **csak Java EE / Jakarta EE környezetekhez** való
- Standalone alkalmazásokhoz használd az `ac4y-database` modult
- A connection pool-t az application server kezeli, nem ez a library
- JNDI név hard-coded: `jdbc/ac4y` - egyedi névhez fork és módosítás szükséges
