package org.medicmobile.webapp.mobile;

final class JavascriptUtils {

	private JavascriptUtils() {}

	static String safeFormat(String js, Object... args) {
		Object[] escapedArgs = new Object[args.length];

		for (int i=0; i<args.length; ++i) {
			Object argument = args[i];
			escapedArgs[i] = argument == null ? null : jsEscape(argument);
		}

		return String.format(js, escapedArgs);
	}

	private static String jsEscape(Object s) {
		return s.toString()
				.replaceAll("'",  "\\'")
				.replaceAll("\n", "\\n");
	}
}
