package ac4y.base.database;


import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DBConnection {

	public DBConnection() throws NamingException, SQLException {

		Context initialContext = new InitialContext();
		String dataResourceName = "jdbc/ac4y";
		Context environmentContext = (Context) initialContext.lookup("java:comp/env");
		DataSource dataSource = (DataSource) environmentContext.lookup(dataResourceName);
		setConnection(dataSource.getConnection());

	} // DBConnection

	private Connection connection;

	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	public Connection getConnection() {

		return connection;

	} // getConnection

} // DBConnection