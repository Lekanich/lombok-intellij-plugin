package tt;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;


//@Value
//@AllArgsConstructor
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class RequerConstructorClass {
	int someField;
	int someField2;

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@RequiredArgsConstructor
	static class PingResult {
		Integer bytes;
		Integer time;			// ms
		Integer ttl;
		String ip;


	}

	public void aVoid() {
		PingResult pingResult = new PingResult(0,0,0,"");
		RequerConstructorClass aClass = new RequerConstructorClass(3, 4);
//		someField = 2;
	}
}

