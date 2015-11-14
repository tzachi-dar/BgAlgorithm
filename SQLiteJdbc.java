import java.sql.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.awt.SecondaryLoop;
import java.io.*;

class Sensor {
	
	Sensor (long started_at, long stopped_at, String uuid, int id) {
		this.started_at = started_at;
		this.stopped_at = stopped_at;
		this.uuid = uuid;
		this.id = id;
		double hours = (stopped_at - started_at) / 60000 / 60;
		days = hours / 24;
	}

	public String toString() {
		double hours = (stopped_at - started_at) / 60000 / 60;
		days = hours / 24;
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
	double days;
}


class RawData {
	
	RawData(double raw_value, long timestamp, int sensor_id) {
		this.raw_value = raw_value;
		this.timestamp = timestamp;
		this.sensor_id = sensor_id;
	}
	
	RawData(RawData old) {
		this.raw_value = old.raw_value;
		this.timestamp = old.timestamp;
		this.sensor_id = old.sensor_id;
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
		if (Math.abs(raw.timestamp - timeStamp) > 30* 60000) {
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
	
	// This function is called to help fil a gap of points that we are missing.
	// we have two points, first and last, the distance between them is gap, and we are calculating the i'th point.
	// for example, if we have points that are located at place 0,3 their gap is 2, and we will be called to calculate
	// point 0,1
	public static RawData CreatePoint(RawData first, RawData last, int gap, int location) {
		assert location < gap;
		assert location >=0;
		assert gap > 0 ;
                assert first.timestamp < last.timestamp;
		if(first.sensor_id  != last.sensor_id) {
			System.err.println("Warning, we have a gap that contains 2 sensors.");
		}
		
		double ratio = (location + 1.0) / (gap + 1);
		RawData ret = new RawData(first.raw_value + ratio * (last.raw_value - first.raw_value),
				                  (long)(first.timestamp + ratio * (last.timestamp - first.timestamp)),
				                  last.sensor_id);
		return ret;
		
	}
	
	double raw_value;
	long timestamp;
	int noise_level; // a number from 0 to 4 like nightscout is using 4 is biggest noise, -1 = failed to calculate
	double noise_quantity1; // a number representing the noise level (method 1)
	double noise_quantity2; // a number representing the noise level (method 1)
	int sensor_id;
}


class Calibration {
	
	Calibration(double measured_bg, long timestamp, int sensor_id,
				double xdrip_dist, double xdrip_slope, double xdrip_intercept) {
		this.measured_bg = measured_bg;
		this.timestamp = timestamp;
		this.sensor_id = sensor_id;
		this.xdrip_dist = xdrip_dist;
		this.xdrip_slope = xdrip_slope;
		this.xdrip_intercept = xdrip_intercept;
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

	// These are just used to calculate the performance of xDrip.
	// Once we have extracted the xdrip algorithm, these can be removed
	double xdrip_dist;
	double xdrip_slope;
	double xdrip_intercept;

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

interface INoiseAlgorithm {
	public void CalculateNoise(List<RawData> rawData);
}


// This class makes sure that one has a list of points with a difference of 5 minutes in between. If points are missing it 
// creates a point with the average number. If points are missing from the start it uses a clone of first existing point.
// If there are duplicate data, that data is removed.
// It is needed in order to allow other algorithms to work without caring for the *VERY* dirty details.

class RawDataSmoother {

        // create a smooth list of packets.
        // the returned list will be in size n, and will work on points smaller or equal to location.
        // for example, if you have captured 200 good packets (on rawData they are from 0 to 199),
        // and you call it with location 150, and size 10, you will get back a list of 10 packets
        // that will be packet 141 - 150 of the original list.
        // Another example, you have missed a few packets, and you only got packets corrosponding to time:
        // ..., 103,104,107,108,109,110,  (please note that on the original list, the points you have are having
        // following numbers.)
        // so, calling this function with location of point 110, will do the following:
        // it will return a list of 10 packets, first 2 packets will be a clone of 103.
        // than point 104. than 2 points will be created that have the average (linear regression) of the points
        // between 104 and 107. than points 107-110 will be coppied.


	public static List<RawData> smoothList(List<RawData> rawData, int location, int size) {
		System.err.println("CalculateList called rawData.size() = " + rawData.size() + " location = " + location +
				" size = " + size);
		for(int k = 0; k < size ; k++) {
                        int current = location - size + 1 +k;
                        if (current < 0) {
                            continue;
                        }
			System.err.println("k = " + k + " " + "location = " + current + " "+ rawData.get(current).timestamp / 1000 +
                            " " + rawData.get(current).raw_value);
		}
		if(location >= rawData.size()) {
			System.err.println("Reading out of list list.size() = " + rawData.size() + " location = " + location);
			return null;
		}
		RawData[] raw_data_array = new RawData[size];
		
                // Go over existing packets and store them in an array according to their time.
                int data_found = 0; 

                // we need this strange stoping condition to avoid duplicate points causing us to loose data.
                long lastPointTime = 0;
                for(int i = 0; data_found < size && location - i>=0;i++) {
                        int current_point = location - i;
                        if( (i!=0) && (lastPointTime - rawData.get(current_point).timestamp  < 2.5 *60000)) {
                            // we have two points that are too close to each other. ignoring this packet.
                            System.err.println("We have two points that are too close: "+ current_point);
                            continue;
                        }
                        lastPointTime = rawData.get(current_point).timestamp ;
			int new_location = size - 1 - DistanceFromTime(rawData.get(location).timestamp, rawData.get(current_point).timestamp);
			if(new_location < 0) {
				// point is too far in the past
				break;
			}
			if(raw_data_array[new_location] != null) {
				// We already have a point at this bucket... too bad
				System.err.println("we have two points that share the same 5 minutes " + (current_point) + " timestamp = " + 
                                    (rawData.get(current_point ).timestamp / 1000));
				continue;
			}
			System.out.println("Setting raw_data_array at location " + new_location);
			raw_data_array[new_location] = rawData.get(current_point);
			data_found++;
		}
		
		System.err.println("data_found = " + data_found);
		if(data_found != size) {
			// now we have to fill points that are missing
			int first_good_point = size-1;
			boolean looking_for_point = false;
			for(int i = 1 ; i <= size; i++) {
                                int current_point = size - i;
				if(raw_data_array[current_point] == null) {
					// we need to calculate this point.
					looking_for_point = true;
					continue;
				} else {
					if(looking_for_point) {
						// we have a good point, need to fill the gap...
						
						
						// first_good_point and (size - i) are the two points that we actually have 
						int gap = first_good_point - current_point - 1;
						System.err.println("closing gap, first_good_point = " + first_good_point + " gap = " + gap + " current_point = " + current_point);
						assert gap >= 1;
						for(int j = 0; j < gap ; j++ ) {
							System.err.println("closing gap - setting point " + (current_point +1 + j));
							raw_data_array[current_point +1 + j] = RawData.CreatePoint(raw_data_array[current_point], raw_data_array[first_good_point], gap , j);
						}
						
						// so far we have closed all gaps 
						looking_for_point = false;
						
					}  
					first_good_point = current_point;
					assert looking_for_point == false;
				}
				
				
			}
			if (looking_for_point) {
				// We did not have the first points, so we just fill the gap with the first point we have.
				System.err.println("closing last gap, first_good_point = " + first_good_point);
				for(int j = first_good_point - 1; j >= 0; j-- ) {
					raw_data_array[j] = new RawData(raw_data_array[first_good_point]);
					System.err.println("setting point j = " + j + " first_good_point - j = " + (first_good_point - j));
					raw_data_array[j].timestamp = raw_data_array[first_good_point].timestamp - SAMPLE_TIME * (first_good_point - j);
				}
			}
			
		}
		// sanity check
		for(int k = 0; k < size; k++) {
			System.err.println("k = " + k + " " + raw_data_array[k].timestamp / 1000 + " " + raw_data_array[k].raw_value);
			assert raw_data_array[k] != null ;
		}
		return new ArrayList<RawData>(Arrays.asList(raw_data_array));		
	}
	
	static int DistanceFromTime(long t1, long t2) {
		double delta = t1 -t2;
		return (int) Math.round(delta / SAMPLE_TIME);
	}
	final static int SAMPLE_TIME = 300000;
}


class NoiseCalculator implements INoiseAlgorithm {

	private final int NOISE_PERIOD = 10; // in unites of 5 minutes
	
	public void CalculateNoise(List<RawData> rawData) {
		// calculate that inefficiently point by point in order to allow it to work just like in xdrip
		int i;
		for (i=0 ; i < 10 /*rawData.size()*/; i++) { //???
			
			List<RawData> goodList = RawDataSmoother.smoothList(rawData, i, NOISE_PERIOD + 1);
			// sanity check
			for(int k = 0; k < NOISE_PERIOD + 1; k++)
				assert goodList.get(k) != null;
			
			// From now and on, we have a list in the size NOISE_PERIOD, and we are interested in the last point
			CalculateNoise(goodList, NOISE_PERIOD );
		}
	}
	
	// rawData list of points, should alrady be good. i some point in the list, practicaly, it's last.
	private void CalculateNoise(List<RawData> rawData, int i) {
        assert rawData.size() >= i;
		// Find the list of 10 points that match the same 
		if(i != NOISE_PERIOD ) {
			// start trivialy don't calculate if not enough data
			rawData.get(i).noise_level = -1;
			assert false;
			return;
		}

		for(int k = 0; k < NOISE_PERIOD + 1; k++)
			assert rawData.get(k) != null;
		
		double []diffs = new double[NOISE_PERIOD -1];
		// the points are [i-9, i]
		// Are they 5 minutes apart?
		int k=0;
		for (int j=i-(NOISE_PERIOD - 1); j <i - 1; j++, k++) {
			System.err.println("j = " + j);
			if(!followingReadings(rawData.get(j), rawData.get(j+1))) {
				rawData.get(i).noise_level = -1;
				assert false;
				return;
			}
			
			// calculate the difference vector
			diffs[k] = rawData.get(j).raw_value - rawData.get(j+1).raw_value;
			
		}
		
		// now for the score method 1: sum of diff + give a 4x boost for level change:
		double noiseTotal1 = 0;
		for(int j=0; j < NOISE_PERIOD - 2; j++) {
			if(sameDirection(diffs[j], diffs[j+1]) ) {
				noiseTotal1 += Math.abs(diffs[j]);
			} else {
				noiseTotal1 += 4 * Math.abs(diffs[j]);
			}
		}
		System.err.println("i = " + i);
		rawData.get(i).noise_quantity1 = noiseTotal1 / (NOISE_PERIOD -1);
		rawData.get(i).noise_level = levelFromTotal(rawData.get(i).noise_quantity1);
		
		// now for the score method 2: sum of diff of the diffs. 
		double noiseTotal2 = 0;
		for(int j=0; j < NOISE_PERIOD - 2; j++) {
				noiseTotal2 += Math.abs(diffs[j] - diffs[j+1]);
		}
		rawData.get(i).noise_quantity2 = noiseTotal2 / (NOISE_PERIOD -1);
	}
	boolean sameDirection(double diff1 , double diff2) {
		if (diff1 * diff2 > 0) {
			// to have a positive value, weather they are both true or both false
			return true;
		}
		return false;
	}
	
	int levelFromTotal(double noiseTotal) {
		if (noiseTotal < 3) return 0;
		if (noiseTotal < 7) return 1;
		if (noiseTotal < 12) return 2;
		if (noiseTotal < 20) return 3;
		return 4;
		
	}
	
	private boolean followingReadings(RawData p1, RawData p2) {
		if (p2.timestamp -  p1.timestamp > 7.5*60000) {
			// Points are too far away
			return false;
		}
		if (p2.timestamp -  p1.timestamp < 2.5*60000) {
			// points are too close, how did that happen?
			return false;
		}
		return true;
	}
		
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

			Calibration calAverage = new Calibration((cal.get(0).measured_bg + cal.get(1).measured_bg) /2, (cal.get(0).timestamp + cal.get(1).timestamp) /2, cal.get(0).sensor_id,
										 cal.get(0).xdrip_dist, cal.get(0).xdrip_slope, cal.get(0).xdrip_intercept);

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

class CalibPoint {
	double[] raw_value;
	double bg_value;
	long timestamp;
	CalibPoint(double[] raw, double bg, long timestamp) {
		this.raw_value = raw;
		this.bg_value = bg;
		this.timestamp = timestamp;
	}
}

interface Evaluator {
	// evaluate how well the parameters fit the algoritm to the target
	public double evaluate(double[] p);
}

class SteepestDescent {
	public static final double GRAD_DELTA = 1e-8;
	double[] solution;

	public boolean optimize(double[] startPos, double tolerance, int maxIter, int gradType, Evaluator evalFunc) {
		int numVar = startPos.length;
		double  gVal;
		double  LHSval;
		double  RHSval;
		double[] temp = new double[numVar];
		double[] z = new double[numVar];

		solution = startPos.clone();
		int numIter = 1;

		while (numIter <= maxIter) {
			double g1 = evalFunc.evaluate(solution);
			double centVal = g1;
			double zMag = 0.0;
			for (int i = 0; i < numVar; i++) {
				switch(gradType) {
					//simple forward gradient
					case 0  :
						solution[i] += GRAD_DELTA;
						RHSval = evalFunc.evaluate(solution);
						solution[i] -= GRAD_DELTA;
						z[i] = (RHSval - centVal) / GRAD_DELTA;
						break;
					//central gradient
					case 1  :
						solution[i] += GRAD_DELTA;
						RHSval = evalFunc.evaluate(solution);
						solution[i] -= 2 * GRAD_DELTA;
						LHSval = evalFunc.evaluate(solution);
						solution[i] += GRAD_DELTA;
						z[i]         = (RHSval - LHSval) / (2.0 * GRAD_DELTA);
						break;
					//quadratic fit using cramers rule then deriv = 2ax+b
					case 2  :
					default :
						solution[i] += GRAD_DELTA;
						double Xr = solution[i];
						double Yr = evalFunc.evaluate(solution);
						solution[i] -= 2 * GRAD_DELTA;
						double Xl = solution[i];
						double Yl = evalFunc.evaluate(solution);
						solution[i] += GRAD_DELTA;
						double Xc = solution[i];
						double Yc = g1;

						double detA  = Xr*Xr*(Xl-Xc) - Xr*(Xl*Xl-Xc*Xc) + (Xl*Xl*Xc-Xc*Xc*Xl);
						double detA1 = Yr*(Xl-Xc) - Xr*(Yl-Yc) + (Yl*Xc-Yc*Xl);
						double detA2 = Xr*Xr*(Yl-Yc) - Yr*(Xl*Xl-Xc*Xc) + (Xl*Xl*Yc-Xc*Xc*Yl);
						z[i]  = (2 * detA1 * Xc + detA2) / detA;
						break;
				}
				zMag += z[i] * z[i];
			}
			zMag = Math.sqrt(zMag);

			if (zMag < 1e-12) {
				// Zero Gradient - might be a minimum
				return true;
			}

			double alpha1 = 0.0;
			double alpha3 = 1.0;
			for (int i = 0; i < numVar; i++) {
				z[i] /= zMag;
				temp[i] = solution[i] - alpha3 * z[i];
			}
			double g3 = evalFunc.evaluate(temp);

			while (g3 >= g1) {
				alpha3 /= 2.0;
				for (int i = 0; i < numVar; i++) {
					temp[i]  = solution[i] - alpha3 * z[i];
				}
				g3 = evalFunc.evaluate(temp);

				if (alpha3 < tolerance / 2.0) {
					// No likely improvement - might have minimum
					return true;
				}
			}

			double alpha2 = alpha3 / 2.0;
			for (int i = 0; i < numVar; i++) {
				temp[i]  = solution[i] - alpha2 * z[i];
			}
			double g2 = evalFunc.evaluate(temp);

			if (Math.abs(alpha2) < 1e-10 || Math.abs(alpha3) < 1e-10 || Math.abs((alpha3 - alpha2)) < 1e-10) {
				// Division by zero imminant!
				return false;
			}
			double h1 = (g2 - g1) / alpha2;
			double h2 = (g3 - g2) / (alpha3 - alpha2);
			double h3 = (h2 - h1) / alpha3;

			if (Math.abs(h3) < 1e-10) {
				// Division by zero imminant!
				return false;
			}
			double alpha0 = 0.5 * (alpha2 - (h1 / h3));
			for (int i = 0; i < numVar; i++) {
				temp[i]  = solution[i] - alpha0 * z[i];
			}
			double g0 = evalFunc.evaluate(temp);

			if (g0 < g3) {
				gVal   = g0;
				alpha1 = alpha0;
			}
			else {
				gVal   = g3;
				alpha1 = alpha3;
			}

			if (alpha1 > 0.5) {
				alpha1 = 0.5;
			}
			for (int i = 0; i < numVar; i++) {
				solution[i] -= alpha1 * z[i];
			}

			if (Math.abs(g1 - gVal) < tolerance) {
				// Found successfully
				return true;
			}

			numIter++;
		}

		// Maximum number of iterations exceeded
		return true;
	}
}

class LineFitAlgorithm implements BgAlgorithm, Evaluator {
	long startTime;
	double[] parms = new double[2];

	List<CalibPoint> calibPnts = new LinkedList<CalibPoint>();

	LineFitAlgorithm() {
	}

	public String toString() {
		return  "LineFitAlgorithm";
	}

	public double evaluate(double[] p) {
		double err = 0;
		int idx = 0;

		for (CalibPoint pnt : calibPnts) {
			idx++;
			double bg = p[0] * pnt.raw_value[0] + p[1];
			if (idx<calibPnts.size()-10) continue;
			err += (bg-pnt.bg_value)*(bg-pnt.bg_value);//*idx;
		}
		// minimize the mean square error
		err /= calibPnts.size();
		return err;
	}

	public void startSensor(long sensorStartTime) {
		startTime = sensorStartTime;
		calibPnts.clear();
	}

	public void calibrationReceived(List<Calibration> cal, List<RawData> rawData) {
		if (cal.size()==0 || rawData.size()==0) return;
		RawData lastRaw = rawData.get(rawData.size()-1);
		Calibration lastCalib = cal.get(cal.size()-1);
		if (Math.abs(lastRaw.timestamp - lastCalib.timestamp) > 12*60000) {
			// Calibration and raw values too far apart > 12 minutes.
			return;
		}
		double[] raw_values = new double[1];
		raw_values[0] = lastRaw.raw_value;
		calibPnts.add(new CalibPoint(raw_values, lastCalib.measured_bg, lastCalib.timestamp));
		// Fit parameters to line
		SteepestDescent opti = new SteepestDescent();
		boolean result = opti.optimize(parms, 0.00001, 100, 0, this);
		for (int i=0;i<parms.length;i++) {
			parms[i] = opti.solution[i];
		}
		double err = evaluate(parms);
		//System.out.println("Error = "+err+ " "+parms[0]+" "+parms[1]);
	}

	public double calculateBG(List<RawData> rawData, long bgTimeStamp) {
		double bg = parms[0] * rawData.get(rawData.size()-1).raw_value + parms[1];
		return bg;
	}
}

class xDripAlgorithm implements BgAlgorithm {
	long startTime;

	Calibration lastCalib;

	xDripAlgorithm() {
	}

	public String toString() {
		return  "xDripAlgorithm";
	}

	public void startSensor(long sensorStartTime) {
		startTime = sensorStartTime;
		lastCalib = null;
	}

	public void calibrationReceived(List<Calibration> cal, List<RawData> rawData) {
		if (cal.size()==0 || rawData.size()==0) return;
		lastCalib = cal.get(cal.size()-1);
	}

	public double calculateBG(List<RawData> rawData, long bgTimeStamp) {
		// Apply age adjusting
		double raw_data = rawData.get(rawData.size()-1).raw_value;
		double age_adjusted_raw_value;
		double adjust_for = (86400000 * 1.9) - (rawData.get(rawData.size()-1).timestamp - startTime);
		if (adjust_for > 0) {
			age_adjusted_raw_value = (((.45) * (adjust_for / (86400000 * 1.9))) * raw_data) + raw_data;
		} else {
			age_adjusted_raw_value = raw_data;
		}
		double bg = lastCalib.xdrip_slope * age_adjusted_raw_value + lastCalib.xdrip_intercept;
		return bg;
	}
}

class NoiseCalcultor {
	public static void  calculateNoise() {
		
	}
}


class AlgorithmChecker {

	void plotRaw(List<RawData> rawBg, List<Calibration> calibrations, List<RawData> calculatedBg, long sensorStart, String fileName) {
		if (rawBg!=null)
			try {
				PrintWriter pw = new PrintWriter(new FileWriter(fileName+"_raw.csv"));
				for (RawData raw : rawBg) {
					// time in days.
					double timeFromStart = (double)(raw.timestamp - sensorStart) / 60000 / 60 / 24;
					pw.println(timeFromStart + ", "+raw.raw_value + ", " +  raw.noise_quantity1 + ", " + 
					           raw.noise_quantity2 + ", " + raw.noise_level);
				}
				pw.close();
			} catch (Exception e)
			{
				System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			}
		if (calibrations!=null)
			try {
				PrintWriter pw = new PrintWriter(new FileWriter(fileName+"_calib.csv"));
				for (Calibration cal : calibrations) {
					// time in days.
					double timeFromStart = (double)(cal.timestamp - sensorStart) / 60000 / 60 / 24;
					pw.println(timeFromStart+", "+cal.measured_bg);
				}
				pw.close();
			} catch (Exception e)
			{
				System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			}
		if (calculatedBg!=null)
			try {
				PrintWriter pw = new PrintWriter(new FileWriter(fileName+"_calc.csv"));
				for (RawData raw : calculatedBg) {
					// time in days.
					double timeFromStart = (double)(raw.timestamp - sensorStart) / 60000 / 60 / 24;
					pw.println(timeFromStart+", "+raw.raw_value);
				}
				pw.close();
			} catch (Exception e)
			{
				System.err.println( e.getClass().getName() + ": " + e.getMessage() );
			}

	}

	double checkAlgorithm(List<Sensor> sensors, List<RawData> rawBg, List<Calibration> calibrations, BgAlgorithm algorithm) {
		
		double totalError = 0;
		int numValidSensors = 0;
		for (Sensor sensor: sensors) {
			long startTime = sensor.started_at;
			long endTime = sensor.stopped_at;
			
			List<RawData> sensorRawBg = RawData.FilterBySensor(rawBg, sensor.id);
			List<Calibration> sensorCalibrations = Calibration.FilterBySensor(calibrations, sensor.id);
			List<RawData> bgCalculated = new LinkedList<RawData>();

			double mard = checkSensor(sensor, sensorRawBg, sensorCalibrations, algorithm, bgCalculated);
			if (mard<0) continue;

			plotRaw(sensorRawBg, sensorCalibrations, bgCalculated, startTime, "sensor"+sensor.id);

			totalError += mard;
			numValidSensors++;
		}
		
		double averageError = totalError / numValidSensors;
		
		System.out.println("\n*** Average error for [" + algorithm + "] algorithm is " + averageError );
		return averageError;
	}
	
	double checkSensor(Sensor sensor, List<RawData> rawBg, List<Calibration> calibrations, BgAlgorithm algorithm, List<RawData> bgCalculated) {
		System.out.println("\n--- Checking sensor ---\n" + sensor+ "\ncalibrations.size() = " + calibrations.size());
		
		if (calibrations.size() < 2 || rawBg.size() < 10 || sensor.days<3) {
			System.err.println("We are ignoring this sensor since we don't have enough data for it");
			return -1.0;
		}
		System.out.println("rawBg.size() = " + rawBg.size() + "\nfirst raw is [" + rawBg.get(0) + "]\nlast raw is  [" +rawBg.get(rawBg.size() - 1)+"]");
		double error = 0;
		double xdripError = 0;
		
		int numberOfCalibrations = 0;
		algorithm.startSensor(sensor.started_at);
		
		bgCalculated.clear();
		List<Calibration> calibHistory = new LinkedList<Calibration>();
		List<RawData> rawDataHistory = new LinkedList<RawData>();
		int rawIndex = 0;
		for(int i = 0 ; i < calibrations.size(); i++) {
			Calibration calibration = calibrations.get(i);
			long timeStamp = calibration.timestamp;
			double measuredBg = calibration.measured_bg;
			calibHistory.add(calibration);

			// add rawdata that occured before this calibration
			while (rawIndex<rawBg.size() && rawBg.get(rawIndex).timestamp <= timeStamp) {
				rawDataHistory.add(rawBg.get(rawIndex));
				// Calculate the bg with the algorith, we use this to plot the algorithm results
				if (i>=2) { // only if we already had 2 calibrations
					double bg = algorithm.calculateBG(rawDataHistory, rawBg.get(rawIndex).timestamp);
					RawData bgData = new RawData(bg, rawBg.get(rawIndex).timestamp, rawBg.get(rawIndex).sensor_id);
					bgCalculated.add(bgData);
				}
				rawIndex++;
			}
			//List<RawData> rawDataHistory = RawData.FilterByDate(rawBg, sensor.started_at, timeStamp);
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
				xdripError += calibration.xdrip_dist / measuredBg;
				numberOfCalibrations++;
			}
			// Provide data to algorithm in order to train or adjust paramaters
			algorithm.calibrationReceived(calibHistory, rawDataHistory);
		}

		// add calculated bg until end of sensor
		while (rawIndex<rawBg.size()) {
			rawDataHistory.add(rawBg.get(rawIndex));
			// Calculate the bg with the algorith, we use this to plot the algorithm results
			double bg = algorithm.calculateBG(rawDataHistory, rawBg.get(rawIndex).timestamp);
			RawData bgData = new RawData(bg, rawBg.get(rawIndex).timestamp, rawBg.get(rawIndex).sensor_id);
			bgCalculated.add(bgData);
			rawIndex++;
		}

		double averageError = error / numberOfCalibrations;
		double averageErrorxdrip = xdripError / numberOfCalibrations;
		
		System.out.println("Average MARD error for this sensor = " + averageError + "\nMARD for xDrip based on latest calculated bg = [" + averageErrorxdrip +"]");
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
		
		// Calculate the noise first
		INoiseAlgorithm noiseAlgorithm = new NoiseCalculator();
		noiseAlgorithm.CalculateNoise(rawBg);
		
		
		AlgorithmChecker algorithmChecker = new AlgorithmChecker();

		algorithmChecker.checkAlgorithm(Sensors, rawBg, calibrations, new xDripAlgorithm());
//		algorithmChecker.checkAlgorithm(Sensors, rawBg, calibrations, new LineFitAlgorithm());
		
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
		List<Sensor> Sensors = new ArrayList <Sensor>();
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
		List<RawData> RawDataList = new ArrayList <RawData>();
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
		List<Calibration> Calibrations = new ArrayList <Calibration>();
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
				double dist = rs.getDouble("distance_from_estimate");
				double slope = rs.getDouble("slope");
				double intercept = rs.getDouble("intercept");
				Calibration calibration = new Calibration(measured_bg, timestamp, id, dist, slope, intercept);
				//System.out.println(calibration);
				Calibrations.add(calibration);
				//System.out.println(v+" "+measured_bg);
			
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
