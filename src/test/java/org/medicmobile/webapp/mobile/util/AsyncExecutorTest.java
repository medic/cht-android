package org.medicmobile.webapp.mobile.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@RunWith(RobolectricTestRunner.class)
public class AsyncExecutorTest {
	@Rule
	public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

	@Mock
	private Callable<String> mockCallable;
	@Mock
	private Consumer<String> mockConsumer;

	@Test
	public void executeAsync() throws Exception {
		String expectedMessage = "Hello World";
		when(mockCallable.call()).thenReturn(expectedMessage);

		// Mock the handler to just run the post in-line
		try (MockedConstruction<Handler> ignored = mockConstruction(Handler.class,
			(mockHandler, context) -> when(mockHandler.post(ArgumentMatchers.any())).then(AdditionalAnswers.answerVoid(Runnable::run)))) {
			AsyncExecutor executor = new AsyncExecutor();
			String actualMessage = executor.executeAsync(mockCallable, mockConsumer).get();

			assertEquals(expectedMessage, actualMessage);
			verify(mockConsumer).accept(ArgumentMatchers.eq(expectedMessage));
		}
	}

	@Test
	public void executeAsync_exception() throws Exception {
		String expectedMessage = "Hello World";
		NullPointerException expectedException = new NullPointerException(expectedMessage);
		when(mockCallable.call()).thenThrow(expectedException);

		// Mock the handler to just run the post in-line
		try (MockedConstruction<Handler> ignored = mockConstruction(Handler.class,
			(mockHandler, context) -> when(mockHandler.post(ArgumentMatchers.any())).then(AdditionalAnswers.answerVoid(Runnable::run)))) {
			AsyncExecutor executor = new AsyncExecutor();
			Future<String> execution = executor.executeAsync(mockCallable, mockConsumer);
			assertThrows(expectedMessage, ExecutionException.class, execution::get);

			verify(mockConsumer, never()).accept(ArgumentMatchers.any());
		}
	}
}

