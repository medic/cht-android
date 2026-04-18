package org.medicmobile.webapp.mobile.p2p;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
	* Tests for SyncMutex — ensures only one sync operation runs at a time.
	*
	* Guards: (single sync), (server priority), (timeout)
	*/
public class SyncMutexTest {

	private SyncMutex mutex;
	private boolean serverReachable;

	@Before
	public void setUp() {
		serverReachable = false;
		mutex = new SyncMutex(() -> serverReachable);
	}

	// ========================================================================
	// Only one sync active at a time
	// ========================================================================

	@Test
	public void testAcquireWhenIdle() {
		assertTrue("Should acquire when idle", mutex.tryAcquire(SyncMutex.SyncType.P2P));
		assertTrue("Should be active after acquire", mutex.isActive());
		assertEquals(SyncMutex.SyncType.P2P, mutex.getCurrentType());
	}

	@Test
	public void testAcquireFailsWhenAlreadyActive() {
		assertTrue(mutex.tryAcquire(SyncMutex.SyncType.P2P));
		assertFalse("Should fail when P2P already active", mutex.tryAcquire(SyncMutex.SyncType.SERVER));
		assertFalse("Should fail when P2P already active", mutex.tryAcquire(SyncMutex.SyncType.P2P));
	}

	@Test
	public void testAcquireServerFailsWhenP2pActive() {
		assertTrue(mutex.tryAcquire(SyncMutex.SyncType.P2P));
		assertFalse("Server should not preempt active P2P", mutex.tryAcquire(SyncMutex.SyncType.SERVER));
	}

	@Test
	public void testAcquireP2pFailsWhenServerActive() {
		assertTrue(mutex.tryAcquire(SyncMutex.SyncType.SERVER));
		assertFalse("P2P should not preempt active server sync", mutex.tryAcquire(SyncMutex.SyncType.P2P));
	}

	@Test
	public void testReleaseAllowsReacquire() {
		assertTrue(mutex.tryAcquire(SyncMutex.SyncType.P2P));
		mutex.release();
		assertFalse("Should not be active after release", mutex.isActive());
		assertNull("Current type should be null after release", mutex.getCurrentType());

		assertTrue("Should be able to acquire after release", mutex.tryAcquire(SyncMutex.SyncType.SERVER));
		assertEquals(SyncMutex.SyncType.SERVER, mutex.getCurrentType());
	}

	@Test
	public void testDoubleReleaseIsSafe() {
		assertTrue(mutex.tryAcquire(SyncMutex.SyncType.P2P));
		mutex.release();
		mutex.release(); // should not throw
		assertFalse(mutex.isActive());
	}

	@Test
	public void testIdleState() {
		assertFalse("Should not be active when freshly created", mutex.isActive());
		assertNull("Current type should be null when idle", mutex.getCurrentType());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAcquireNullTypeThrows() {
		mutex.tryAcquire(null);
	}

	// ========================================================================
	// Server sync has priority (yield signal)
	// ========================================================================

	@Test
	public void testShouldYieldWhenP2pActiveAndServerReachable() {
		assertTrue(mutex.tryAcquire(SyncMutex.SyncType.P2P));
		serverReachable = true;

		assertTrue("P2P should yield when server becomes reachable", mutex.shouldYieldToServer());
	}

	@Test
	public void testShouldNotYieldWhenP2pActiveAndServerUnreachable() {
		assertTrue(mutex.tryAcquire(SyncMutex.SyncType.P2P));
		serverReachable = false;

		assertFalse("P2P should not yield when server unreachable", mutex.shouldYieldToServer());
	}

	@Test
	public void testShouldNotYieldWhenServerSyncActive() {
		assertTrue(mutex.tryAcquire(SyncMutex.SyncType.SERVER));
		serverReachable = true;

		assertFalse("Server sync should not yield to itself", mutex.shouldYieldToServer());
	}

	@Test
	public void testShouldNotYieldWhenIdle() {
		serverReachable = true;
		assertFalse("Should not yield when no sync is active", mutex.shouldYieldToServer());
	}

	@Test
	public void testShouldNotYieldWithNullChecker() {
		SyncMutex mutexNoChecker = new SyncMutex(null);
		assertTrue(mutexNoChecker.tryAcquire(SyncMutex.SyncType.P2P));

		assertFalse("Should not yield when no reachability checker", mutexNoChecker.shouldYieldToServer());
	}

	// ========================================================================
	// Activity touch
	// ========================================================================

	@Test
	public void testTouchActivityDoesNotThrow() {
		mutex.tryAcquire(SyncMutex.SyncType.P2P);
		mutex.touchActivity(); // should not throw
		assertTrue(mutex.isActive());
	}

	@Test
	public void testTouchActivityWhenIdleDoesNotThrow() {
		mutex.touchActivity(); // should not throw even when idle
		assertFalse("Mutex should remain idle after touch", mutex.isActive());
	}

	// ========================================================================
	// Acquire-release cycles
	// ========================================================================

	@Test
	public void testMultipleAcquireReleaseCycles() {
		for (int i = 0; i < 100; i++) {
			SyncMutex.SyncType type = (i % 2 == 0) ? SyncMutex.SyncType.P2P : SyncMutex.SyncType.SERVER;
			assertTrue("Acquire should succeed on cycle " + i, mutex.tryAcquire(type));
			assertEquals(type, mutex.getCurrentType());
			assertTrue(mutex.isActive());
			mutex.release();
			assertFalse(mutex.isActive());
		}
	}

	// ========================================================================
	// Thread safety (basic smoke test)
	// ========================================================================

	@Test
	public void testConcurrentAcquireOnlyOneWins() throws InterruptedException {
		final int[] successCount = {0};
		final int threadCount = 10;
		Thread[] threads = new Thread[threadCount];

		for (int i = 0; i < threadCount; i++) {
			threads[i] = new Thread(() -> {
				if (mutex.tryAcquire(SyncMutex.SyncType.P2P)) {
					synchronized (successCount) {
						successCount[0]++;
					}
				}
			});
		}

		for (Thread t : threads) {
			t.start();
		}
		for (Thread t : threads) {
			t.join(5000);
		}

		assertEquals("Only one thread should acquire the mutex", 1, successCount[0]);
	}
}
