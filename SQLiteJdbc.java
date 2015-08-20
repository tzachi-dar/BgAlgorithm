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
	
	RawData(double raw_value, long timestamp) {
		this.raw_value = raw_value;
		this.timestamp = timestamp;		
	}

	public String toString() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		return  dateFormat.format(timestamp) + " " + raw_value;
	}
	
	private boolean in_range(long start, long end) {
		return timestamp >= start && timestamp <= end;
	}
	
	static List<RawData> FilterByDate(List<RawData> data, long start, long end) {
		List<RawData> RawDataList = new LinkedList <RawData>();
		for (RawData sample : data ) {
			if(sample.in_range(start, end)) {
				RawDataList.add(sample);
			}
			
		}
		return RawDataList;
	}
 	
	double raw_value;
	long timestamp;
	public static RawData getByTime(List<RawData> rawBg, long timeStamp2) {
		// TODO Auto-generated method stub
		return null;
	}
}


class Calibration {
	
	Calibration(double measured_bg, long timestamp) {
		this.measured_bg = measured_bg;
		this.timestamp = timestamp;
	}
	
	public String toString() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		return  dateFormat.format(timestamp) + " " + measured_bg;
	}
	
	private boolean in_range(long start, long end) {
		return timestamp >= start && timestamp <= end;
	}
	
	static List<Calibration> FilterByDate(List<Calibration> data, long start, long end) {
		List<Calibration> RawDataList = new LinkedList <Calibration>();
		for (Calibration sample : data ) {
			if(sample.in_range(start, end)) {
				RawDataList.add(sample);
			}
		}
		return RawDataList;
	}
	
	double measured_bg;
	long timestamp;
}


// The return type of the algorithm
class CalibrationParameters {
	double  slope;
	double intercept;
}

// This is the algorithm that we are checking...
interface BgAlgorithm {
	CalibrationParameters calcluateInital(Calibration cal1, Calibration cal2, RawData rawData0, long sensorStartTime);
	
	CalibrationParameters calcluate(Calibration cal, List<RawData> rawData, long sensorStartTime);
	
	// In the future will probably change to something more complicated
	double errorWeight(double MeasuredBG, double calculatedBg);	
}

// An example algorithm just to get going...
class InitialAlgorithm implements BgAlgorithm {
	public CalibrationParameters calcluateInital(Calibration cal1, Calibration cal2, RawData rawData0, long sensorStartTime) {
		CalibrationParameters params =  new CalibrationParameters();
		
		params.slope = 1.1; // Just a guess
		params.intercept = (cal1.measured_bg + cal2.measured_bg) / 2 - params.slope * rawData0.raw_value ;
		return params;
	}
	
	public CalibrationParameters calcluate(Calibration cal, List<RawData> rawData, long sensorStartTime) {
		CalibrationParameters params =  new CalibrationParameters();
		
		params.slope = 0; // Just a guess
		params.intercept = 120 ;
		return params;
	}
	
	public double errorWeight(double MeasuredBG, double calculatedBg) {
		return Math.abs(MeasuredBG - calculatedBg);
	}
	
}


class AlgorithmChecker {
	double checkAlgorithm(List<Sensor> sensors, List<RawData> rawBg, List<Calibration> calibrations, BgAlgorithm algorithm) {
		
		double error = 0;
		
		for (Sensor sensor: sensors) {
			long startTime = sensor.started_at;
			long endTime = sensor.stopped_at;
			
			List<RawData>  sensorRawBg = RawData.FilterByDate(rawBg, startTime, endTime);
			List<Calibration> sensorCalibrations = Calibration.FilterByDate(calibrations, startTime, endTime);
			
			error = checkSensor(sensor, sensorRawBg, sensorCalibrations, algorithm);
			
		}
		
		return error;
	}
	
	double checkSensor(Sensor sensor, List<RawData> rawBg, List<Calibration> calibrations, BgAlgorithm algorithm) {
		
		double error = 0;
		
		// TODO(tzachi) assert in the case that there are not enough calibratios, or no raw_bg
		
		CalibrationParameters calibrationParameters = algorithm.calcluateInital(calibrations.get(0), calibrations.get(1), rawBg.get(0), sensor.started_at);
		for(int i = 2 ; i < calibrations.size(); i++) {
			Calibration calibration = calibrations.get(i);
			long timeStamp = calibration.timestamp;
			double measuredBg = calibration.measured_bg;
			
			RawData rawBgTime = RawData.getByTime(rawBg, timeStamp);
			double calculatedBg = calibrationParameters.slope * rawBgTime.raw_value + calibrationParameters.intercept;
			
			error += algorithm.errorWeight(measuredBg, calculatedBg);
			
			calibrationParameters = algorithm.calcluate(calibration, rawBg, sensor.started_at);
		}
		
		return error / (calibrations.size() - 2);
		
	}
	
}



// A simple class to read SensorData from xDrip database
public class SQLiteJdbc
{

	public static void main( String args[] ) {
		List<Sensor> Sensors = ReadSensors("export20150814-184324.sqlite");
		List<RawData> rawBg = ReadRawBg("export20150814-184324.sqlite");
		List<Calibration> calibrations = ReadCalibrations("export20150814-184324.sqlite");
		
		AlgorithmChecker algorithmChecker = new AlgorithmChecker();
		algorithmChecker.checkAlgorithm(Sensors, rawBg, calibrations, new InitialAlgorithm());
		
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
		System.out.println("Rawdata read successfully");
		return RawDataList;
	}

	public static List<Calibration> ReadCalibrations(String dbName )
	{
		Connection c = null;
		Statement stmt = null;
		List<Calibration> Calibrations = new LinkedList <Calibration>();
		try {
			Class.forName("org.sqlite.JDBC");
			c = DriverManager.getConnection("jdbc:sqlite:" + dbName);
			c.setAutoCommit(false);
			System.out.println("Opened database successfully");

			stmt = c.createStatement();
			ResultSet rs = stmt.executeQuery( "SELECT * FROM CALIBRATION;" );
			while ( rs.next() ) {
				double measured_bg = rs.getDouble("bg");
				long timestamp = (long)rs.getDouble("timestamp");
				Calibration calibration = new Calibration(measured_bg, timestamp);
				System.out.println(calibration);
				Calibrations.add(calibration);
			
			}
			rs.close();
			stmt.close();
			c.close();
		} catch ( Exception e ) {
			System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			System.exit(0);
		}
		System.out.println("Calibrations read successfully");
		return Calibrations;
	}

	

}