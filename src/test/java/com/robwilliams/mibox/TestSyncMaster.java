package com.robwilliams.mibox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.robwilliams.mibox.snapshots.CloudFileSnapshot;
import com.robwilliams.mibox.snapshots.LocalFileSnapshot;

/**
 * Test the SyncMaster class as best as possible. This probably violates every test principle in the book,
 * since these are testing the internal private method of SyncMaster. But I just felt like that crazy block
 * of 20 IF statements needed some verification, albeit brittle verification.
 * <br><br>
 * @author Rob Williams
 *
 */
@SuppressWarnings("serial")
public class TestSyncMaster {

	// Note: I'm not testing performInitialSync directly because it's too complex an operation.
	// Instead I call it with different mock objects which effectively tests the private methods individually.
	
	private DateFormat dfm;
	private Method prepareMerge;
	private SyncMaster sm;
	
	@Before
	public void before() throws Exception {
		// make SyncMaster object, which is pretty empty
		// test will fill it with necessary state, if any
		sm = new SyncMaster();
		
		// set up date formatter, for easy test data creation
		dfm = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		dfm.setTimeZone(TimeZone.getTimeZone("America/Seattle")); //TODO: use ConfigUtil
		
		// use Reflection to get an invokable object for private method				
		prepareMerge = SyncMaster.class.getDeclaredMethod("prepareMerge", Map.class, Map.class);
		prepareMerge.setAccessible(true); // make it invokable
	}
	
	@After
	public void after() {
	}
	
	// Below follows a bunch of tests for the prepareMerge method.
	// One real world situation is tested per test, to keep them small and simple.

	@Test
	public void testPrepareMergeForUnchangedFile() throws Exception {
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			put("file2", new LocalFileSnapshot("file2", dfm.parse("2011-07-14 20:15:00"), "12345", new Date(0), "", new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file2", new CloudFileSnapshot("file2", dfm.parse("2011-07-14 20:15:00"), "12345", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// file was not changed, so expect unchanged actions
		assertEquals("LocalFileUnchangedAction", localFileSnapshots.get("file2").getAction().getClass().getSimpleName());
		assertEquals("DummyFileAction", cloudFileSnapshots.get("file2").getAction().getClass().getSimpleName());
	}
	
	@Test
	public void testPrepareMergeForNewLocalFile() throws Exception {
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			put("file1", new LocalFileSnapshot("file1", dfm.parse("2011-07-14 20:15:00"), "12345", new Date(0), "", new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			// no file1 in cloud
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// file1 is in local, not in cloud. Expected LocalFileAddAction for local snapshot.
		assertEquals("LocalFileAddedAction", localFileSnapshots.get("file1").getAction().getClass().getSimpleName());
	}
	
	@Test
	public void testPrepareMergeForNewCloudFile() throws Exception {
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			// no file1b in local
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file1b", new CloudFileSnapshot("file1b", dfm.parse("2011-07-14 20:15:00"), "12346", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// file1b is in cloud, not in local. Expected CloudFileAddAction for cloud snapshot.
		assertEquals("CloudFileAddedAction", cloudFileSnapshots.get("file1b").getAction().getClass().getSimpleName());
	}
	
	@Test
	/**
	 * Test that prepareMerge properly notices there is a conflict
	 * when two files are modified separately but at same second.
	 * @throws Exception
	 */
	public void testPrepareMergeForConflictType1() throws Exception {
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			put("file2", new LocalFileSnapshot("file2", dfm.parse("2011-07-14 20:16:00"), "23456", new Date(0), "", new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file2", new CloudFileSnapshot("file2", dfm.parse("2011-07-14 20:16:00"), "23457", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// make sure a conflict is returned
		assertEquals("LocalFileConflictAction", localFileSnapshots.get("file2").getAction().getClass().getSimpleName());
		assertEquals("DummyFileAction", cloudFileSnapshots.get("file2").getAction().getClass().getSimpleName());
	}
	
	@Test
	/**
	 * Test that prepareMerge properly notices there is a conflict
	 * when two files are modified separately since the last sync time.
	 * @throws Exception
	 */
	public void testPrepareMergeForConflictType2() throws Exception {
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			put("file2", new LocalFileSnapshot("file2", dfm.parse("2011-07-14 20:17:00"), "23458", dfm.parse("2011-07-14 20:16:00"), "23456", new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file2", new CloudFileSnapshot("file2", dfm.parse("2011-07-14 20:18:00"), "23457", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// make sure a conflict is returned
		assertEquals("LocalFileConflictAction", localFileSnapshots.get("file2").getAction().getClass().getSimpleName());
		assertEquals("DummyFileAction", cloudFileSnapshots.get("file2").getAction().getClass().getSimpleName());
	}
	
	@Test
	/**
	 * Test that prepareMerge properly doesn't get a conflict just
	 * because there was a last-sync time and a file changed.
	 * @throws Exception
	 */
	public void testPrepareMergeForCorrectlyNotConflicting1() throws Exception {
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			put("file5", new LocalFileSnapshot("file5", dfm.parse("2011-07-14 20:16:00"), "23456", dfm.parse("2011-07-14 20:16:00"), "23456",  new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file5", new CloudFileSnapshot("file5", dfm.parse("2011-07-14 21:54:23"), "23457", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// make sure a conflict is NOT returned, but the proper action is
		assertEquals("DummyFileAction", localFileSnapshots.get("file5").getAction().getClass().getSimpleName());
		assertEquals("CloudFileChangedAction", cloudFileSnapshots.get("file5").getAction().getClass().getSimpleName());
	}
	
	@Test
	/**
	 * Test that prepareMerge properly doesn't get a conflict just
	 * because there was a last-sync time and both last mod dates changed.
	 * @throws Exception
	 */
	public void testPrepareMergeForCorrectlyNotConflicting2() throws Exception {
		// local file's last-mod-date changed since last sync, but the hash remains the same.
		// cloud file actually did change.
		// therefore, someone just touch'd the local file, and there is no reason to conflict over this.
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			put("file5", new LocalFileSnapshot("file5", dfm.parse("2011-07-15 20:16:00"), "23456", dfm.parse("2011-07-14 20:16:00"), "23456",  new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file5", new CloudFileSnapshot("file5", dfm.parse("2011-07-14 22:22:22"), "23457", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// make sure a conflict is NOT returned, but the proper action is
		assertEquals("DummyFileAction", localFileSnapshots.get("file5").getAction().getClass().getSimpleName());
		assertEquals("CloudFileChangedAction", cloudFileSnapshots.get("file5").getAction().getClass().getSimpleName());
	}
	
	@Test
	/**
	 * Test that prepareMerge properly doesn't get a conflict just
	 * when the local and cloud hashes are equal, even if everything else is weird.
	 * @throws Exception
	 */
	public void testPrepareMergeForCorrectlyNotConflicting3() throws Exception {
		// local file and cloud file were both touch'd since last mod date,
		// but since hash remained the same there is no conflict
		// We take this as local change since that is most recent.
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			put("file5", new LocalFileSnapshot("file5", dfm.parse("2011-07-15 20:16:00"), "77777", dfm.parse("2011-07-14 20:16:00"), "23456",  new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file5", new CloudFileSnapshot("file5", dfm.parse("2011-07-14 22:22:22"), "77777", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// make sure a conflict is NOT returned, but the proper action is
		assertEquals("LocalFileChangedAction", localFileSnapshots.get("file5").getAction().getClass().getSimpleName());
		assertEquals("DummyFileAction", cloudFileSnapshots.get("file5").getAction().getClass().getSimpleName());
	}
	
	@Test
	public void testPrepareMergeForCloudChange() throws Exception {
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			put("file2", new LocalFileSnapshot("file2", dfm.parse("2011-07-14 20:16:00"), "23456", new Date(0), "",  new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file2", new CloudFileSnapshot("file2", dfm.parse("2011-07-14 20:17:00"), "23457", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// make sure cloud change is returned
		assertEquals("DummyFileAction", localFileSnapshots.get("file2").getAction().getClass().getSimpleName());
		assertEquals("CloudFileChangedAction", cloudFileSnapshots.get("file2").getAction().getClass().getSimpleName());
	}
	
	@Test
	public void testPrepareMergeForLocalChange() throws Exception {
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			put("file3", new LocalFileSnapshot("file3", dfm.parse("2011-07-17 20:16:00"), "23488", new Date(0), "",  new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file3", new CloudFileSnapshot("file3", dfm.parse("2011-07-14 20:17:00"), "23457", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
		
		// make sure local change is returned
		assertEquals("LocalFileChangedAction", localFileSnapshots.get("file3").getAction().getClass().getSimpleName());
		assertEquals("DummyFileAction", cloudFileSnapshots.get("file3").getAction().getClass().getSimpleName());
	}
	
	@Test
	public void testPrepareMergeForLocalDelete() throws Exception {
		// TODO: Implement. Will need to use mocking for the SimpleDB call.
	}
	
	@Test
	public void testPrepareMergeForCloudDelete() throws Exception {
		// TODO: Implement. Will need to use mocking for the SimpleDB call.
	}
	
	@Test
	/**
	 * Makes sure an IntegrityError is thrown when time travel is detected (aka data corruption).
	 * @throws Exception
	 */
	public void testPrepareMergeForNotAllowingTimeTravel() throws Exception {
		Map<String, LocalFileSnapshot> localFileSnapshots = new HashMap<String, LocalFileSnapshot>() {{
			// they had Java in 1901, obviously. Pretty sure that's the year it was invented
			put("file2", new LocalFileSnapshot("file2", dfm.parse("1901-07-14 20:15:00"), "past_perfect", dfm.parse("2011-07-14 20:15:00"), "future_perfect",  new Date(0), true));
		}};
		Map<String, CloudFileSnapshot> cloudFileSnapshots = new HashMap<String, CloudFileSnapshot>() {{
			put("file2", new CloudFileSnapshot("file2", dfm.parse("2011-07-14 20:15:00"), "future_perfect", "otherbox", 0));
		}};
				
		// actually call prepareMerge method
		try {
			prepareMerge.invoke(sm, localFileSnapshots, cloudFileSnapshots);
			fail("Expected IntegrityError was not thrown when time traveling!");
		} catch (InvocationTargetException ex) {
			assertEquals("IntegrityError", ex.getTargetException().getClass().getSimpleName());
			// success
		}
	}
}
