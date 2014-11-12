package tt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.Final;
import lombok.experimental.NonFinal;


/**
 * @author Suburban Squirrel
 * @version <version>
 * @since <version>
 */
public class FinalTest {

	@Final
	FinalTest(String cons , @NonFinal long zz) {
		zz = 4;							// ok
		cons = "";						// todo \e
		int yCons = 4;					// ok
		yCons = 4;						// todo \e
	}

	@Final
	public void method(final int finalField, int y, @NonFinal long z) {
		@NonFinal int tmp = 4; 			// ok

		final int zh;					// ok
		tmp = zh;						// todo \e
		zh = 4;							// ok
		zh = 4;							// todo \e
		int finita;						// ok
		tmp = finita;					// todo \e
		finita = 4;						// ok
		tmp = finita;					// ok
		finita  = 4;					// todo \e

		finalField = 4;					// todo \e
		int x = 3;						// ok
		x = 4;							// todo \e
		y = 4;							// todo \e
		@NonFinal long zero;			// ok
		zero = 3;						// ok
		zero = 3;						// ok

		List<String> list = new ArrayList<>();
		String sLamda = "";
		@NonFinal String anLamda = "";
		Stream.iterate(1, (i) -> i++).forEach(i -> list.add(sLamda+=""));			// todo wrong	\e
		Stream.iterate(1, (i) -> i++).forEach(i -> list.add(anLamda+=""));			// todo wrong	\e
	}
}
