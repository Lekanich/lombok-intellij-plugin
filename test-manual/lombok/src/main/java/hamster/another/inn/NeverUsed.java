package tt.another.inn;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;

/**
 * Created by alex on 10.09.14.
 */
@FieldDefaults(level = AccessLevel.PUBLIC)
public class NeverUsed {
	static int field;
	static String simpleField;

	{
		int field1 = field;
	}

	public void setSimpleField() {
		int field1 = field;
		String simpleField1 = simpleField;
		String simpleField2 = this.simpleField;
		this.simpleField = "";
		simpleField = "3";

		this.simpleField.getBytes();
		simpleField.getBytes();

		ArrayList<String> list = new ArrayList<>();
		list.add(simpleField);
		list.add(this.simpleField);
	}

	final public static class R {
		private void aVoid () {
			NeverUsed neverUsed = new NeverUsed();
			Class<? extends NeverUsed> aClass = neverUsed.getClass();
			ArrayList list = new ArrayList();
			list.add(neverUsed);
		}
	}
}
