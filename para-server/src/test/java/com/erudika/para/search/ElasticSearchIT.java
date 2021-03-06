/*
 * Copyright 2013-2016 Erudika. http://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.para.search;

import com.erudika.para.core.App;
import com.erudika.para.core.ParaObject;
import com.erudika.para.core.Tag;
import com.erudika.para.persistence.DAO;
import static com.erudika.para.search.SearchTest.u;
import com.erudika.para.utils.Config;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.mockito.Mockito.mock;

/**
 *
 * @author Alex Bogdanovski [alex@erudika.com]
 */
public class ElasticSearchIT extends SearchTest {

	@BeforeClass
	public static void setUpClass() {
		System.setProperty("para.env", "embedded");
		System.setProperty("para.app_name", "para-test");
		System.setProperty("para.cluster_name", "para-test");
		System.setProperty("para.es.shards", "2");
		s = new ElasticSearch(mock(DAO.class));
		ElasticSearchUtils.createIndex(Config.APP_NAME_NS);
		ElasticSearchUtils.createIndex(appid1);
		ElasticSearchUtils.createIndex(appid2);
		ElasticSearchUtils.createIndex("root-index");
		SearchTest.init();
	}

	@AfterClass
	public static void tearDownClass() {
		ElasticSearchUtils.deleteIndex(Config.APP_NAME_NS);
		ElasticSearchUtils.deleteIndex(appid1);
		ElasticSearchUtils.deleteIndex(appid2);
		ElasticSearchUtils.deleteIndex("root-index");
		ElasticSearchUtils.shutdownClient();
		SearchTest.cleanup();
	}

	@Test
	public void testCreateDeleteExistsIndex() {
		String appid3 = "test-index";
		String badAppid = "test index 123";

		ElasticSearchUtils.createIndex("");
		assertFalse(ElasticSearchUtils.existsIndex(""));

		ElasticSearchUtils.createIndex(appid3);
		assertTrue(ElasticSearchUtils.existsIndex(appid3));

		ElasticSearchUtils.deleteIndex(appid3);
		assertFalse(ElasticSearchUtils.existsIndex(appid3));

		assertFalse(ElasticSearchUtils.createIndex(badAppid));
		assertFalse(ElasticSearchUtils.existsIndex(badAppid));
		assertFalse(ElasticSearchUtils.deleteIndex(appid3));
		assertFalse(ElasticSearchUtils.deleteIndex(badAppid));
	}

	@Test
	public void testRebuildIndex() {
		// TODO
	}

	@Test
	public void testGetSearchClusterMetadata() {
		assertFalse(ElasticSearchUtils.getSearchClusterInfo().isEmpty());
	}

	@Test
	public void testGetIndexNameForAlias() throws InterruptedException {
		ElasticSearchUtils.createIndex("test-index");
		assertNull(ElasticSearchUtils.getIndexNameForAlias(""));
		assertEquals("test-index_1", ElasticSearchUtils.getIndexNameForAlias("test-index"));
		ElasticSearchUtils.deleteIndex("test-index");
	}

	@Test
	public void testRangeQuery() {
		// many terms
		Map<String, Object> terms1 = new HashMap<String, Object>();
		terms1.put(Config._TIMESTAMP + " <", 1111111111L);

		Map<String, Object> terms2 = new HashMap<String, Object>();
		terms2.put(Config._TIMESTAMP + "<=", u.getTimestamp());

		List<ParaObject> res1 = s.findTerms(u.getType(), terms1, true);
		List<ParaObject> res2 = s.findTerms(u.getType(), terms2, true);

		assertEquals(1, res1.size());
		assertEquals(1, res2.size());

		assertEquals(u.getId(), res1.get(0).getId());
		assertEquals(u.getId(), res2.get(0).getId());
	}

	@Test
	public void testSharedIndex() throws InterruptedException {
		String app1 = "myapp1";
		String app2 = "myapp2";
		String root = "root-index";

		App rootApp = new App("rootapp");
		s.index(root, rootApp);

		assertTrue(ElasticSearchUtils.addIndexAlias(root, app1, true));
		assertTrue(ElasticSearchUtils.addIndexAlias(root, app2, true));

		Tag t1 = new Tag("t1");
		Tag t2 = new Tag("t2");
		Tag t3 = new Tag("t3");

		t1.setAppid(app1);
		t2.setAppid(app2);
		t3.setAppid(app1);

		s.index(t1.getAppid(), t1);
		s.index(t2.getAppid(), t2);
		s.index(t3.getAppid(), t3);

		Thread.sleep(1000);

		// top view of all docs in shared index
		assertEquals(1, s.getCount(root, "app").intValue());
		assertEquals(3, s.getCount(root, "tag").intValue());
		// local view for each app space
		assertEquals(2, s.getCount(app1, "tag").intValue());
		assertEquals(1, s.getCount(app2, "tag").intValue());

		List<Tag> l1 = s.findQuery(app1, "tag", "*");
		assertEquals(2, l1.size());
		List<Tag> l2 = s.findQuery(app2, "tag", "*");
		assertEquals(l2.get(0), t2);
	}
}