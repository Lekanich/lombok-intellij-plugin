package tt.custom;

import lombok.NonNull;
import lombok.experimental.NonNullArgs;


/**
 * @author Suburban Squirrel
 * @version <version>
 * @since <version>
 */
public class NonNullArgsTest {

	@NonNullArgs
	public NonNullArgsTest(@NonNull String some, @NonNull NonNullArgsTest arg2, Integer arg3) {

	}


	@NonNullArgs
	public NonNullArgsTest(String some, @NonNull NonNullArgsTest arg2, Integer arg3, int i) {

	}

	@NonNullArgs
	public void some1(String some, @NonNull NonNullArgsTest arg2, Integer arg3) {

	}

	@NonNullArgs
	public static void some2(String some, @NonNull NonNullArgsTest arg2, Integer arg3) {

	}


	public void some1(String some, @NonNull NonNullArgsTest arg2, Integer arg3, int i) {

	}

	public static void some2(String some, @NonNull NonNullArgsTest arg2, Integer arg3, @NonNull int i) {

	}

}
