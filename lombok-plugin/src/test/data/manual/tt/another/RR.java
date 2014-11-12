package tt.another;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

/**
 * NO ERRORS
 */
@FieldDefaults(level = AccessLevel.PROTECTED)
public class RR {
	static int SI;
	int i;
	public static int PSI;
	public int pi;
	protected static int PROSI;
	protected int proi;
	private static int PRSI;
	private int pri;

	{
		int temp = SI;
		temp = i;
		temp = PSI;
		temp = pi;
		temp = PROSI;
		temp = proi;
		temp = PRSI;
		temp = pri;
	}

	public RR() {

		int tt = RR.PROSI;
		pri = 3;
		System.err.println(pri);
	}

	public static void main(String[] args) {
		RR rr = new RR();
		int temp = rr.SI;
		temp = rr.i;
		temp = rr.PSI;
		temp = rr.pi;
		temp = rr.PROSI;
		temp = rr.proi;
		temp = rr.PRSI;
		temp = rr.pri;
	}
}
