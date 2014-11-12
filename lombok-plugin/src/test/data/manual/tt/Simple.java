package tt;

import java.util.Arrays;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.ExtensionMethod;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j;
import tt.another.BuilderExample;

/**
 * @author alex
 */
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@ExtensionMethod({Arrays.class, Simple.Util.class})
@Log4j
public class Simple {
	int field;
	@Getter private int primitive = 0;
	private int[] in = new int[] {6,2,3,3};
	private long[] dermo = new long[]{3,3,3,3};
	private Integer[] array = new Integer[]{4, 5, 6, 7};
	public String ss = "Kuku";
	private Integer number = null;
	@Getter @Setter boolean gerasim;			// must error on setter?

	{
		gerasim = true;
		field = 2;
//		field = 3;
	}

//	{
//		field = 2;
//	}

	public Simple() {
//		field = 0 ;
	}
//
	private Simple(int f) {
		this();
		int i = 3;
	}



	public void ee(Simple t) {
//		this.getGerasim();
		this.isGerasim();

		this.rr();
		getPrimitive();
		getInt().parallelSort();
		in.kk();
		in.parallelSort();


		dermo.parallelSort();
		getMe().dermo.parallelSort();
//		int length = in.;
		String s = Arrays.toString(getInt());
		s.toLowerCase();
		number.someMethod() ;
//		number.dd() ;

		int i = 4;
		i.dd();
		i.dd() ;
		i.someMethod();
		i.dd();
		i.someMethod();
		primitive.dd();
		int[] anInt = getMe().getMe().getInt();
		getMe().getMe().getI().dd();
		s.tt();
		System.out.println(s);
	}

	private Simple getMe() { return this; }

	private int getI() { return 3;}

	public void rr()
	{
		double[] t = null;
	}

	static {
		long t = 3;
		t.dd();
		t.someMethod();
//		t.
//		t.dd();
	}

	public int[] getInt() { return in; }

	public static void main(String[] args) {
		Simple s = new Simple();
		s.ee(s);
	}

	public static class Cl2 extends Cl1 {
		public int ii = 3;

		public String getCl2() { return "";}
	}

	public static class Cl1 {
		public int anInt;

		public String getCl1() { return "";}
	}

	@Log4j
	final public static class Util {
		public static void tt(String t) {}
		public static void sort(int[] t) {
			boolean traceEnabled = log.isTraceEnabled();
			System.out.println("tut");
		}
		public static <T extends Comparable> void someMethod(T t) {}
		public static void rr(Simple t) {}
		public static void kk(int[] t) {}
		public static void dd(long t) {}
	}
}
