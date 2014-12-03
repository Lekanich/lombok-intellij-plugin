package tt.another;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;
import lombok.experimental.FieldDefaults;

/**
 * Created by alex on 16.10.14.
 */
//@Data
@Value
@FieldDefaults(makeFinal = true)
public class DataTest {

	public final int tt;

	public final int tt2;
	public int anInt;


	{
		List list = new ArrayList<>();
		list.
		getTt();
	}
}
