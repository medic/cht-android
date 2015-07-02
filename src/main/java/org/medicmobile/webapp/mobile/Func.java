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

	public static <A> A find(List<A> collection, Func<A, Boolean> func) {
		int index = 0;
		Iterator<A> i = collection.iterator();

		while(i.hasNext()) {
			A next = i.next();
			Boolean result = func.apply(next);
			if(result != null && result) return next;
		}

		return null;
	}

	public static <A> int indexOf(List<A> collection, Func<A, Boolean> func) {
		int index = 0;
		Iterator<A> i = collection.iterator();

		while(i.hasNext()) {
			Boolean result = func.apply(i.next());
			if(result != null && result) return index;
			++index;
		}

		return -1;
	}

	public static <A> boolean any(List<A> collection, Func<A, Boolean> func) {
		Iterator<A> i = collection.iterator();

		while(i.hasNext()) {
			Boolean result = func.apply(i.next());
			if(result != null && result) return true;
		}

		return false;
	}
}
