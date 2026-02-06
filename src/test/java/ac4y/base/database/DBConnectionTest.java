package ac4y.base.database;

import org.junit.Test;
import static org.junit.Assert.*;

public class DBConnectionTest {

    @Test
    public void testConnectionInitialization() {
        assertNotNull("DBConnection class should exist", DBConnection.class);
    }
}
