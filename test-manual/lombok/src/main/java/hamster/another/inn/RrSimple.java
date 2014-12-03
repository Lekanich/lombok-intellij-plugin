package tt.another.inn;

import tt.another.RR;

/**
 * Created by alex on 08.09.14.
 */
public class RrSimple {
	static int si;
	int i;
	public static int psi;
	public int pi;
	protected static int prosi;
	protected int proi;
	private static int prsi;
	private int pri;

	{
		int temp = si;
		temp = i;
		temp = psi;
		temp = pi;
		temp = prosi;
		temp = proi;
		temp = prsi;
		temp = pri;
	}

	public static void main(String[] args) {
		RR rr = new RR();
		int temp = rr.SI;			// error FD
		temp = rr.i;				// error FD

		temp = rr.PSI;
		temp = rr.pi;
		temp = rr.PROSI;			// error
		temp = rr.proi;				// error
		temp = rr.prsi;				// error
		temp = rr.pri;				// error
	}
}
