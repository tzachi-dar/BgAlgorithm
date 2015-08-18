import java.sql.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

class Sensor {
	
	Sensor (long started_at, long stopped_at, String uuid) {
		this.started_at = started_at;
		this.stopped_at = stopped_at;
		this.uuid = uuid;
	}

	public String toString() {
		double hours = (stopped_at - started_at) / 60000 / 60;
		double days = hours / 24; 
		DecimalFormat df = new DecimalFormat("#.00"); 
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		return  dateFormat.format(started_at) + " - " +dateFormat.format(stopped_at) + " " +df.format(days);
	}
	
	long started_at;
	long stopped_at;
	String uuid;
	
}


class RawData {
	
	RawData(double raw_value, long time) {
		this.raw_value = raw_value;
		this.time = time;		
	}

	public String toString() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		return  dateFormat.format(time) + " " + raw_value;
	}
 	
	double raw_value;
	long time;
}


// A simple class to read SensorData from xDrip database
public class SQLiteJDBC
{
	public static void main( String args[] ) {
		ReadSensors("export20150814-184324.sqlite");
		ReadRawBg("export20150814-184324.sqlite");
	}
	public static List<Sensor> ReadSensors(String dbName )
	{
		Connection c = null;
		Statement stmt = null;
		List<Sensor> Sensors = new LinkedList <Sensor>();
		try {
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:" + dbName);
			c.setAutoCommit(false);
			System.out.println("Opened database successfully");

			stmt = c.createStatement();
			ResultSet rs = stmt.executeQuery( "SELECT * FROM SENSORS;" );
			while ( rs.next() ) {
				int id = rs.getInt("_id");
				String  uuid = rs.getString("uuid");
				long started_at= (long)rs.getDouble("started_at");
				long stopped_at= (long)rs.getDouble("stopped_at");
				System.out.println( "ID = " + id );
				System.out.println( "started_at = " + started_at );
				System.out.println();
				// TODO(tzachi) Fix the old sensor read time based on new sensor start time.
				Sensor sensor = new Sensor(started_at, stopped_at, uuid);
				Sensors.add(sensor);
				System.out.println(sensor);
			}
			rs.close();
			stmt.close();
			c.close();
		} catch ( Exception e ) {
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		System.out.println("Sensors read successfully");
		return Sensors;
	}

	public static List<RawData> ReadRawBg(String dbName )
	{
		Connection c = null;
		Statement stmt = null;
		List<RawData> RawDataList = new LinkedList <RawData>();
		try {
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:" + dbName);
			c.setAutoCommit(false);
			System.out.println("Opened database successfully");

			stmt = c.createStatement();
			ResultSet rs = stmt.executeQuery( "SELECT * FROM BGREADINGS;" );
			while ( rs.next() ) {
				double raw = rs.getDouble("raw_data");

				
				long timestamp = (long)rs.getDouble("timestamp");
				System.out.println( "timestamp = " + timestamp + " "  + raw);
				RawData rawData = new RawData(raw, timestamp);
				System.out.println(rawData);
				RawDataList.add(rawData);
			
			}
			rs.close();
			stmt.close();
			c.close();
		} catch ( Exception e ) {
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		System.out.println("Sensors read successfully");
		return RawDataList;
	}

	
	

}