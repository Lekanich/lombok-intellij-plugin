package tt.another;

import lombok.Data;
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
		getTt();
	}
}
