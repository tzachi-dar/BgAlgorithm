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

	// This function makes sure that we do not return data that is not far away (more than 12 minutes)
	// from the time that we wanted
	private static RawData limitData(RawData raw, long timeStamp) {
		if (Math.abs(raw.timestamp - timeStamp) > 12* 60000) {
			// they are too far apart
			System.err.println("Skiping point because distance is " + (raw.timestamp - timeStamp) / 60000 + " minutes");
			return null;
		}
		return raw;
		
	}
	
	// Get the last point before the calibration.
	public static RawData getByTime(List<RawData> rawBg, long timestamp) {
		RawData rawLast = rawBg.get(0);
		for(RawData raw : rawBg) {
			if(raw.timestamp > timestamp) {
				// We have gone too far, return the previous one
				return limitData(rawLast, timestamp);
			}
			rawLast = raw;
		}
		// try returning the last one, we might be over the border but not in much.
		return limitData(rawLast, timestamp);
	}
	
	double raw_value;
	long timestamp;
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
		List<Calibration> Calibrations = new LinkedList <Calibration>();
		for (Calibration sample : data ) {
			if(sample.in_range(start, end)) {
				Calibrations.add(sample);
			}
		}
		// 2 initial calibrations should be from the same time. If this is not so, very likely someone has used on override
		// calibration. I'll duplicate the first calibration.	
		if (Calibrations.size()>=2) {
			Calibration cal0 = Calibrations.get(0);
			Calibration cal1 = Calibrations.get(1);
			if(cal1.timestamp - cal0.timestamp  > 10 * 60000) {
				System.err.println("Duplicated the first calibration " + cal1 + " " + cal0);
				Calibrations.add(0, cal0);
			}
		}
		return Calibrations;
	}
	
	double measured_bg;
	long timestamp;
}


// The return type of the algorithm
class CalibrationParameters {
	double  slope;
	double intercept;
	
	public String toString() {
		return  "slope = " + slope + " intercept = " + intercept;
	}
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
	InitialAlgorithm(double initialSlope) {
		this.initialSlope = initialSlope;
	}
	
	public CalibrationParameters calcluateInital(Calibration cal1, Calibration cal2, RawData rawData0, long sensorStartTime) {
		CalibrationParameters params =  new CalibrationParameters();
		
		Calibration calAverage = new Calibration((cal1.measured_bg + cal2.measured_bg) /2, (cal1.timestamp + cal2.timestamp) /2);
		
		params.slope = initialSlope; // Just a guess
		params.intercept = calAverage.measured_bg  - params.slope * rawData0.raw_value ;
		
		System.out.println("cal1 " +cal1 + " cal2 " + cal2 + " rawData0 =" + rawData0 +  "params = " + params);
		
		return params;
	}
	
	public CalibrationParameters calcluate(Calibration cal, List<RawData> rawData, long sensorStartTime) {
		CalibrationParameters params =  new CalibrationParameters();
		
		params.slope = 0; // Just to have a zero value
		params.intercept = 120 ; // we will always give the second point as 120.
		return params;
	}
	
	public double errorWeight(double MeasuredBG, double calculatedBg) {
		return Math.abs(MeasuredBG - calculatedBg);
	}
	
	public String toString() {
		return  "Algorithm is initialSlope = " + initialSlope;
	}
	
	final double initialSlope;
	
}

class AlgorithmChecker {
	double checkAlgorithm(List<Sensor> sensors, List<RawData> rawBg, List<Calibration> calibrations, BgAlgorithm algorithm) {
		
		double totalError = 0;
		
		for (Sensor sensor: sensors) {
			long startTime = sensor.started_at;
			long endTime = sensor.stopped_at;
			
			List<RawData>  sensorRawBg = RawData.FilterByDate(rawBg, startTime, endTime);
			List<Calibration> sensorCalibrations = Calibration.FilterByDate(calibrations, startTime, endTime);
			
			totalError += checkSensor(sensor, sensorRawBg, sensorCalibrations, algorithm);
			
		}
		
		double averageError = totalError / sensors.size() ;
		
		System.out.println("*** Average error for " + algorithm + " algorithm is " + averageError );
		return averageError;
	}
	
	double checkSensor(Sensor sensor, List<RawData> rawBg, List<Calibration> calibrations, BgAlgorithm algorithm) {
		System.out.println("\n Checking sensor! " + sensor+ " calibrations.size() = " + calibrations.size());
		
		if(calibrations.size() < 2 || rawBg.size() < 10) {
			System.err.println("We are ignoring this sensor since we don't have enough data for it");
			return 0;
		}
		System.out.println(" rawBg.size() = " + rawBg.size() + " first raw " + rawBg.get(0) + " last raw " +rawBg.get(rawBg.size() - 1));
		double error = 0;
		
		RawData rawBgTime = RawData.getByTime(rawBg, calibrations.get(0).timestamp);
		if (rawBgTime == null) {
			// We did not find a close enough point, so we simply ignore this sensor
			// TODO (tzachi) don't ignore this sensor
			System.err.println("We are ignoring this sensor because of problems with inital calibrations (fix me)");
			return 0;
		}
		
		int numberOfSensors = 0;
		
		CalibrationParameters calibrationParameters = algorithm.calcluateInital(calibrations.get(0), calibrations.get(1), rawBgTime, sensor.started_at);
		for(int i = 2 ; i < /* ?????? */ Math.min(calibrations.size(),  3); i++) {
			Calibration calibration = calibrations.get(i);
			long timeStamp = calibration.timestamp;
			double measuredBg = calibration.measured_bg;
			
			rawBgTime = RawData.getByTime(rawBg, timeStamp);
			if (rawBgTime == null) {
				// We did not find a close enough point, so we simply ignore this calibration
				System.err.println("We are ignoring this calibration since we did not find data to match it.");
				continue;
			}
			double calculatedBg = calibrationParameters.slope * rawBgTime.raw_value + calibrationParameters.intercept;
			
			System.out.println("Calibration measurment: calibration: " + calibration + " rawBgTime: " + rawBgTime + " calculatedBg:" + calculatedBg);
			
			error += algorithm.errorWeight(measuredBg, calculatedBg);
			numberOfSensors += 1;
			
			calibrationParameters = algorithm.calcluate(calibration, rawBg, sensor.started_at);
		}
		
		double averageError = error / numberOfSensors;
		
		System.out.println("Average error for this sensor is + " + averageError);
		return averageError;
	}
	
}



// A simple class to read SensorData from xDrip database
public class SQLiteJdbc
{

	public static void main( String args[] ) {
		
		if(args.length != 1) {
			System.err.println("usage of program is: java -classpath \".;sqlite-jdbc-3.8.7.jar\" SQLiteJdbc dbname" );
			return;
		}
	
		List<Sensor> Sensors = ReadSensors(args[0]);
		List<RawData> rawBg = ReadRawBg(args[0]);
		List<Calibration> calibrations = ReadCalibrations(args[0]);
		
		AlgorithmChecker algorithmChecker = new AlgorithmChecker();
		for(double slope = 0.2; slope < 2.0 ; slope += 0.05) {
			algorithmChecker.checkAlgorithm(Sensors, rawBg, calibrations, new InitialAlgorithm(slope));
		}
		
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
				//System.out.println(sensor);
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
				//System.out.println(rawData);
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
				//System.out.println(calibration);
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
		
		// TODO: tzachi (make sure calibrations are sorted by time).
		
		return Calibrations;
	}

	

}