import java.sql.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

class Sensor {
	
	Sensor (long started_at, long stopped_at, String uuid, int id) {
		this.started_at = started_at;
		this.stopped_at = stopped_at;
		this.uuid = uuid;
		this.id = id;
	}

	public String toString() {
		double hours = (stopped_at - started_at) / 60000 / 60;
		double days = hours / 24; 
		DecimalFormat df = new DecimalFormat("#.00"); 
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		return   "ID         : " + id +
			   "\nUUID       : " + uuid+
			   "\nStart date : " + dateFormat.format(started_at) +
			   "\nEnd date   : " + dateFormat.format(stopped_at) +
			   "\nDays       : " + df.format(days);
	}
	
	long started_at;
	long stopped_at;
	String uuid;
	int id;
}


class RawData {
	
	RawData(double raw_value, long timestamp, int sensor_id) {
		this.raw_value = raw_value;
		this.timestamp = timestamp;
		this.sensor_id = sensor_id;
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

	static List<RawData> FilterBySensor(List<RawData> data, int sensor_id) {
		List<RawData> RawDataList = new LinkedList <RawData>();
		for (RawData sample : data ) {
			if(sample.sensor_id == sensor_id) {
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
	int sensor_id;
}


class Calibration {
	
	Calibration(double measured_bg, long timestamp, int sensor_id) {
		this.measured_bg = measured_bg;
		this.timestamp = timestamp;
		this.sensor_id = sensor_id;
	}
	
	public String toString() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		return  dateFormat.format(timestamp) + " " + measured_bg;
	}
	
	private boolean in_range(long start, long end) {
		return timestamp >= start && timestamp <= end;
	}
	
	static List<Calibration> FilterByDate(List<Calibration> data, long start, long end) {
		List<Calibration> CalibrationList = new LinkedList <Calibration>();
		for (Calibration sample : data ) {
			if(sample.in_range(start, end)) {
				CalibrationList.add(sample);
			}
		}
		// 2 initial calibrations should be from the same time. If this is not so, very likely someone has used on override
		// calibration. I'll duplicate the  calibration.
		if (CalibrationList.size()>=2) {
			Calibration cal0 = CalibrationList.get(0);
			Calibration cal1 = CalibrationList.get(1);
			if(cal1.timestamp - cal0.timestamp  > 10 * 60000) {
				System.err.println("Duplicated the first calibration " + cal1 + " " + cal0);
				CalibrationList.add(0, cal0);
			}
		}
		return CalibrationList;
	}
	
	static List<Calibration> FilterBySensor(List<Calibration> data, int sensor_id) {
		List<Calibration> CalibrationList = new LinkedList <Calibration>();
		for (Calibration sample : data ) {
			if(sample.sensor_id == sensor_id) {
				CalibrationList.add(sample);
			}
		}
		return CalibrationList;
	}

	double measured_bg;
	long timestamp;
	int sensor_id;
}


// The return type of the algorithm
//FIXME: This should be internal to the algorithm
class CalibrationParameters {
	double  slope;
	double intercept;
	
	public String toString() {
		return  "slope = " + slope + " intercept = " + intercept;
	}
}

// This is the algorithm that we are checking...
interface BgAlgorithm {

	// Start a new sensor with the given starting time
	public void startSensor(long sensorStartTime);
	// Pass calibrations and raw data to algorithm, called whenever a new calibration value was received
	public void calibrationReceived(List<Calibration> cal, List<RawData> rawData);
	// Calculate the BG at time bgTimeStamp, given the raw data.
	public double calculateBG(List<RawData> rawData, long bgTimeStamp);
}

// An example algorithm just to get going...
class InitialAlgorithm implements BgAlgorithm {
	InitialAlgorithm(double initialSlope) {
		this.initialSlope = initialSlope;
	}
	
	public void startSensor(long sensorStartTime) {
		params = null;
	}

	public void calibrationReceived(List<Calibration> cal, List<RawData> rawData) {
		if (cal.size()==2) {
			params =  new CalibrationParameters();

			Calibration calAverage = new Calibration((cal.get(0).measured_bg + cal.get(1).measured_bg) /2, (cal.get(0).timestamp + cal.get(1).timestamp) /2, cal.get(0).sensor_id);

			params.slope = initialSlope; // Just a guess
			params.intercept = calAverage.measured_bg  - params.slope * rawData.get(rawData.size()-1).raw_value;
		}
	}

	public double calculateBG(List<RawData> rawData, long bgTimeStamp) {
		RawData rawBgTime = RawData.getByTime(rawData, bgTimeStamp);
		double calculatedBg = params.slope * rawBgTime.raw_value + params.intercept;
		return calculatedBg;
	}

	public String toString() {
		return  "Algorithm is initialSlope = " + initialSlope;
	}
	
	final double initialSlope;
	CalibrationParameters params;
}

class AlgorithmChecker {
	double checkAlgorithm(List<Sensor> sensors, List<RawData> rawBg, List<Calibration> calibrations, BgAlgorithm algorithm) {
		
		double totalError = 0;
		int numValidSensors = 0;
		for (Sensor sensor: sensors) {
			long startTime = sensor.started_at;
			long endTime = sensor.stopped_at;
			
			List<RawData> sensorRawBg = RawData.FilterBySensor(rawBg, sensor.id);
			List<Calibration> sensorCalibrations = Calibration.FilterBySensor(calibrations, sensor.id);
			double mard = checkSensor(sensor, sensorRawBg, sensorCalibrations, algorithm);
			if (mard<0) continue;

			totalError += mard;
			numValidSensors++;
		}
		
		double averageError = totalError / numValidSensors;
		
		System.out.println("\n*** Average error for [" + algorithm + "] algorithm is " + averageError );
		return averageError;
	}
	
	double checkSensor(Sensor sensor, List<RawData> rawBg, List<Calibration> calibrations, BgAlgorithm algorithm) {
		System.out.println("\n--- Checking sensor ---\n" + sensor+ "\ncalibrations.size() = " + calibrations.size());
		
		if (calibrations.size() < 2 || rawBg.size() < 10) {
			System.err.println("We are ignoring this sensor since we don't have enough data for it");
			return -1.0;
		}
		System.out.println("rawBg.size() = " + rawBg.size() + "\nfirst raw is [" + rawBg.get(0) + "]\nlast raw is  [" +rawBg.get(rawBg.size() - 1)+"]");
		double error = 0;
		
		int numberOfCalibrations = 0;
		algorithm.startSensor(sensor.started_at);
		
		List<Calibration> calibHistory = new LinkedList<Calibration>();
		for(int i = 0 ; i < calibrations.size(); i++) {
			Calibration calibration = calibrations.get(i);
			long timeStamp = calibration.timestamp;
			double measuredBg = calibration.measured_bg;
			calibHistory.add(calibration);

			List<RawData> rawDataHistory = RawData.FilterByDate(rawBg, sensor.started_at, timeStamp);
			RawData rawBgTime = RawData.getByTime(rawBg, timeStamp);
			if (rawBgTime == null) {
				// We did not find a close enough point, so we simply ignore this calibration
				System.err.println("We are ignoring this calibration since we did not find data to match it.");
				continue;
			}
			// Skip error calculation for the first two calibrations
			if (i>=2) {
				double calculatedBg = algorithm.calculateBG(rawDataHistory, timeStamp);
				error += Math.abs(measuredBg - calculatedBg) / measuredBg;
				numberOfCalibrations++;
			}
			// Provide data to algorithm in order to train or adjust paramaters
			algorithm.calibrationReceived(calibHistory, rawDataHistory);
		}

		double averageError = error / numberOfCalibrations;
		
		System.out.println("Average MARD error for this sensor = " + averageError);
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
		FixSensorsStopTime(Sensors, rawBg, calibrations);
		
		AlgorithmChecker algorithmChecker = new AlgorithmChecker();
		for(double slope = 0.2; slope < 2.0 ; slope += 0.05) {
			algorithmChecker.checkAlgorithm(Sensors, rawBg, calibrations, new InitialAlgorithm(slope));
			break;
		}
		
	}
	public static void FixSensorsStopTime(List<Sensor> sensors, List<RawData> rawBg, List<Calibration> calibrations) {
		long[] sensorEnd = new long[sensors.get(sensors.size()-1).id+1];

		for (Sensor sensor : sensors) {
			sensorEnd[sensor.id] = sensor.stopped_at;
		}
		// Find last raw reading
		for (RawData raw : rawBg) {
			sensorEnd[raw.sensor_id] = Math.max(raw.timestamp, sensorEnd[raw.sensor_id]);
		}
		// Find last calibration
		for (Calibration calib : calibrations) {
			sensorEnd[calib.sensor_id] = Math.max(calib.timestamp, sensorEnd[calib.sensor_id]);
		}
		// Update sensor stop time
		for (Sensor sensor : sensors) {
			sensor.stopped_at = sensorEnd[sensor.id];
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
			ResultSet rs = stmt.executeQuery( "SELECT * FROM SENSORS ORDER BY _id;" );
			while ( rs.next() ) {
				int id = rs.getInt("_id");
				String  uuid = rs.getString("uuid");
				long started_at= (long)rs.getDouble("started_at");
				long stopped_at= (long)rs.getDouble("stopped_at");
				System.out.println( "ID = " + id );
				System.out.println( "started_at = " + started_at );
				System.out.println();
				Sensor sensor = new Sensor(started_at, stopped_at, uuid, id);
				Sensors.add(sensor);
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
			ResultSet rs = stmt.executeQuery( "SELECT * FROM BGREADINGS ORDER BY timestamp;" );
			while ( rs.next() ) {
				double raw = rs.getDouble("raw_data");
				int id = rs.getInt("sensor");
				long timestamp = (long)rs.getDouble("timestamp");
				RawData rawData = new RawData(raw, timestamp, id);
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
			ResultSet rs = stmt.executeQuery( "SELECT * FROM CALIBRATION ORDER BY timestamp;" );
			while ( rs.next() ) {
				double measured_bg = rs.getDouble("bg");
				long timestamp = (long)rs.getDouble("timestamp");
				int id = rs.getInt("sensor");
				Calibration calibration = new Calibration(measured_bg, timestamp, id);
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
