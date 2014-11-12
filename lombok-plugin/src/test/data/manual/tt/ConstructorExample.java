package tt;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Created by alex on 07.10.14.
 */
//@RequiredArgsConstructor(staticName = "of")
//@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ConstructorExample<T> {
	private int x, y;
	@NonNull
	private T description;

//	@NoArgsConstructor
	@RequiredArgsConstructor
//	@AllArgsConstructor
	@FieldDefaults(makeFinal = true)
	public static class NoArgsExample {
		private String field1;
		@NonNull private String field2;

		private final String field3;
		@NonNull private final String field4;

		{
			NoArgsExample noArgsExample = new NoArgsExample("", "", "", "");
		}
	}

	@FieldDefaults(makeFinal = true)
	final public static class Class1 {
		private String field1;
		private String field2;
		@NonNull private String field3;
		private final String field4;
	}

	@NoArgsConstructor
	@FieldDefaults(makeFinal = true)
	final public static class Class2 {
		private String field1;
		private String field2;
		@NonNull private String field3;
		private final String field4;

		{
			new Class2();
		}
	}

	@AllArgsConstructor
	@FieldDefaults(makeFinal = true)
	final public static class Class3 {
		private String field1;
		private String field2;
		@NonNull private String field3;
		private final String field4;

		{
			new Class3("", "", "", "");

		}
	}

	@RequiredArgsConstructor
	@FieldDefaults(makeFinal = true)
	final public static class Class4 {
		private String field1;
		private String field2;
		@NonNull private String field3;
		private final String field4;

		{
			new Class4("", "", "", "");
		}
	}

	@RequiredArgsConstructor
	final public static class Class5 {
		private String field1;
		private String field2;
		@NonNull private String field3;
		private final String field4;

		{
			new Class5("", "");
		}
	}
}