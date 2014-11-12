package tt;

import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by alex on 14.10.14.
 */
@FieldDefaults(makeFinal = true)
public class FieldDefaultsFinalTest {
	int anInt;			// todo \e
	final int sd;		// todo \e
	int anInt2;			// todo \e
	final int sd2;		// todo \e
	int anInt3;			// ok
	final int sd3;		// ok
	int anInt4;
	final int sd4;
	int anInt5;
	final int sd5;
	String sLamda = null;
	final String anLamda = null;
	List<String> listLamda = null;
	final List<String> anListLamda = null;

	public FieldDefaultsFinalTest() {
		try {
			this.anInt = 3;			// todo wrong
			this.sd = 4;			// todo wrong
			this.anInt = 3;			// todo wrong	\e
			this.sd = 4;			// todo wrong	\e
			this.anInt4 = 3;		// todo wrong
			this.sd4 = 4;			// todo wrong
			this.anInt5 = 3;		// todo wrong
			this.sd5 = 4;			// todo wrong
		} catch (Exception e) {
			this.anInt2 = 3;		// todo wrong
			this.sd2 = 4;			// todo wrong
			this.anInt4 = 3;		// todo wrong	\e
			this.sd4 = 4;			// todo wrong	\e
		} finally {
			this.anInt3 = 3;
			this.sd3 = 4;
			this.anInt5 = 3;		// todo wrong	\e
			this.sd5 = 4;			// todo wrong	\e
		}
	}

	public void setsLamda() {
		Stream.iterate(1, (i)->i++).forEach(i -> sLamda = "");			// todo wrong	\e
		Stream.iterate(1, (i)->i++).forEach(i -> anLamda = "");			// todo wrong	\e
		Stream.iterate(1, (i)->i++).forEach(i -> listLamda.add(""));
		Stream.iterate(1, (i)->i++).forEach(i -> anListLamda.add(""));
	}
}
