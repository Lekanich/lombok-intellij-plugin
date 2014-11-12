package tt.another;

import lombok.Delegate;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import tt.Simple;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by alex on 5/21/14.
 */
@Accessors(fluent = true)
public class Result extends Simple {
	@Getter @Setter
	private int f;
	private int rr;

	{
//		this.field = 3;
		this.f();
	}

	Result() {
		rr = 3;
	}

	void aVoid() {
//		this.field = 3;
	}

	static interface Kesha {
		boolean add(String item);
	}

	@Delegate(types = Kesha.class)
	public final Collection<String> collection = new ArrayList<String>();


	public static void main(String[] args) {
		RR rr1 = new RR();
		int e = rr1.PSI;


		Simple simple = new Simple();
//		int field = simple.field;
		String d = "dsds";
		int[] rr = null;
//		simple.getPrimitive();
	}
}
