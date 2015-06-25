package org.medicmobile.webapp.mobile;

import java.util.*;

public abstract class Func<X, Y> {
	abstract Y apply(X in);

	public static <A, B> List<B> map(A[] originals, Func<A, B> func) {
		List<B> result = new ArrayList<B>(originals.length);
		for(A original : originals) {
			result.add(func.apply(original));
		}
		return result;
	}

	public static <A> int findIndex(List<A> collection, Func<A, Boolean> func) {
		int index = 0;
		Iterator<A> i = collection.iterator();

		while(i.hasNext()) {
			if(func.apply(i.next())) return index;
			++index;
		}

		return -1;
	}
}
