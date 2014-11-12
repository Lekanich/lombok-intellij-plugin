package tt.another.inn;

import tt.another.RR;

import java.util.ArrayList;

public class Example extends RR {

	{
		int temp = SI;
		temp = i;
		temp = PSI;
		temp = pi;
		temp = PROSI;
		temp = proi;
		temp = PRSI;				// error
		temp = pri;					// error
	}

	public void setSimpleField() {
		NeverUsed neverUsed = new NeverUsed();
		String simpleField = NeverUsed.simpleField;
		int field1 = neverUsed.field;
		String simpleField1 = neverUsed.simpleField;
		neverUsed.simpleField = "";

		neverUsed.simpleField.getBytes();

		ArrayList<String> list = new ArrayList<>();
		list.add(neverUsed.simpleField);
	}

	public static void main(String[] args) {
		int field = NeverUsed.field;
		NeverUsed neverUsed = new NeverUsed();
		String field1 = neverUsed.simpleField;



		RR rr = new RR();
		int temp = rr.SI;			// not error !
		temp = rr.i;				// error	!

	// constants
		temp = rr.PSI;
		temp = rr.pi;
		temp = rr.PROSI;
		temp = RR.PROSI;			// not error
		temp = rr.proi ;			// error
		temp = rr.PRSI;				// error
		temp = rr.pri;				// error

		Example s = new Example();
		int proi1 = s.PROSI;
		proi1 = s.proi;
	}
}
