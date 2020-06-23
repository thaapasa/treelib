package fi.hut.cs.treelib.btree;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import fi.hut.cs.treelib.Component;
import fi.hut.cs.treelib.Key;
import fi.hut.cs.treelib.KeyRange;
import fi.hut.cs.treelib.LinkPage;
import fi.hut.cs.treelib.Owner;
import fi.hut.cs.treelib.Page;
import fi.hut.cs.treelib.PageID;
import fi.hut.cs.treelib.PageValue;
import fi.hut.cs.treelib.VisualizablePage;
import fi.hut.cs.treelib.common.AbstractTreePage;
import fi.hut.cs.treelib.common.PagePath;
import fi.hut.cs.treelib.common.SMOPolicy;
import fi.hut.cs.treelib.gui.TreeDrawStyle;
import fi.hut.cs.treelib.internal.KeyRangeImpl;
import fi.hut.cs.treelib.storage.PageBuffer;
import fi.hut.cs.treelib.storage.PageFactory;
import fi.hut.cs.treelib.util.KeyUtils;
import fi.tuska.util.Callback;
import fi.tuska.util.CollectionUtils;
import fi.tuska.util.Pair;

/**
 * Must not contain direct references to other pages.
 * 
 * @author tuska
 */
public class BTreePage<K extends Key<K>, V extends PageValue<?>> extends
		AbstractTreePage<K, V, BTreePage<K, V>> implements LinkPage<K, V>,
		Component {

	private static final Logger log = Logger.getLogger(BTreePage.class);
	public static final int PAGE_IDENTIFIER = 0xB7ba6e;

	private PageID nextPage = PageID.INVALID_PAGE_ID;

	private final boolean showLeafPageValues;
	private final SMOPolicy smoPolicy;

	protected final BTree<K, V> tree;

	/**
	 * PageValues are instances of PageID for index pages and different
	 * PageValue classes for data pages.
	 */
	private TreeMap<KeyRange<K>, PageValue<?>> contents = new TreeMap<KeyRange<K>, PageValue<?>>();

	protected BTreePage(BTree<K, V> tree, PageID id, int pageSize) {
		super(id, tree, pageSize);

		this.showLeafPageValues = tree.showLeafPageValues;
		this.tree = tree;
		this.smoPolicy = tree.getDBConfig().getSMOPolicy();

		calculateCapacity();
	}

	@Override
	protected void calculateCapacity() {
		super.calculateCapacity();
	}

	protected void setNextPage(PageID nextPage) {
		this.nextPage = nextPage;
		setDirty(true);
	}

	public PageID getNextPage() {
		return nextPage;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V removeEntry(K key) {
		assert isLeafPage();
		return (V) contents.remove(KeyRangeImpl.getKeyRange(key));
	}

	@Override
	public boolean contains(K key) {
		if (isLeafPage()) {
			return contents.containsKey(KeyRangeImpl.getKeyRange(key));
		} else {
			return getKeyRange().contains(key);
		}
	}

	/**
	 * At entry: this node is fixed.
	 * 
	 * At exit: this node is still fixed, the side-linked nodes have been
	 * visited and fixed and unfixed after they've been visited.
	 */
	public boolean getRange(final KeyRange<K> range,
			final Callback<Pair<K, V>> callback, Owner owner) {
		assert isLeafPage();

		final K max = range.getMax();
		return traverseLeafEntries(range.getMin(), new Callback<Pair<K, V>>() {
			@Override
			public boolean callback(Pair<K, V> entry) {
				K cur = entry.getFirst();
				if (cur.compareTo(max) >= 0) {
					// Gone past the range, return
					return true;
				}
				assert range.contains(cur);
				callback.callback(entry);
				// True to continue search
				return true;
			}
		}, owner);
	}

	/**
	 * @return true if all callbacks returned true
	 */
	@SuppressWarnings("unchecked")
	protected boolean getAll(KeyRange<K> range, Callback<Pair<K, V>> callback) {
		assert isLeafPage();
		for (Entry<KeyRange<K>, PageValue<?>> entry : this.contents.entrySet()) {
			if (range.contains(entry.getKey().getMin())) {
				if (!callback.callback(new Pair<K, V>(entry.getKey().getMin(),
						(V) entry.getValue()))) {
					// Search stop indicated (callback returned false)
					return false;
				}
			}
		}
		// All callbacks returned true, so continue
		return true;
	}

	/**
	 * Call to detach this node from the tree (from its parent).
	 */
	protected void detach(BTreePage<K, V> parent) {
		if (parent != null) {
			parent.removeContents(getKeyRange(), getPageID());
		}
	}

	/**
	 * Must only be used by the visualizer program. Will directly fetch the
	 * linked node from node buffer (without locking), and will unfix the page
	 * so that the visualizer does not need to worry about page fixing.
	 */
	@Override
	public List<Page<K, V>> getLinks() {
		List<Page<K, V>> links = new ArrayList<Page<K, V>>();
		if (nextPage.isValid()) {
			BTreePage<K, V> node = dbConfig.getPageBuffer().fixPage(nextPage,
					factory, false, tree.internalOwner);
			links.add(node);
			// This makes the method unusable except for visualization
			dbConfig.getPageBuffer().unfix(node, tree.internalOwner);
		}
		return links;
	}

	/**
	 * Only used for visualization. Does not follow proper page fixing policies
	 * (collects unfixed pages).
	 */
	protected void collectPagesAtHeight(int height,
			Collection<VisualizablePage<K, V>> result) {
		if (getHeight() == height) {
			result.add(this);
		} else {
			if (getHeight() > height) {
				for (Entry<KeyRange<K>, PageValue<?>> entry : this.contents
						.entrySet()) {
					PageID childID = (PageID) entry.getValue();
					BTreePage<K, V> child = dbConfig.getPageBuffer().fixPage(
							childID, factory, false, tree.internalOwner);
					child.collectPagesAtHeight(height, result);
					dbConfig.getPageBuffer().unfix(child, tree.internalOwner);
				}
			}
		}
	}

	/** Sets the node dirty. */
	@Override
	public void putContents(KeyRange<K> key, PageValue<?> value) {
		// No direct links to other nodes
		assert !(value instanceof Page<?, ?>);
		assert !isFull() : String.format(
				"Page %s is full when storing contents", getName());

		contents.put(key, value);
		setDirty(true);
	}

	/**
	 * Removes the contents with given key from the node.
	 * 
	 * @param value
	 *            the expected value. Can be null when removing contents of leaf
	 *            nodes.
	 */
	protected PageValue<?> removeContents(KeyRange<K> key, PageValue<?> value) {
		PageValue<?> v = contents.remove(key);
		if (!isLeafPage()) {
			assert v != null;
			assert value != null;
			assert value.equals(v) : "Expected " + value + ", got " + v;
		}
		setDirty(true);
		return v;
	}

	/**
	 * Called when moving nodes from one child to a sibling.
	 */
	protected void updateKeyRange(BTreePage<K, V> child, KeyRange<K> newRange) {
		assert !isLeafPage();
		KeyRange<K> oldRange = child.getKeyRange();

		PageID storedID = (PageID) contents.get(oldRange);
		assert storedID != null : "No router found with range " + oldRange
				+ " in " + contents;
		assert storedID.intValue() == child.getPageID().intValue() : "Wrong child found, "
				+ storedID + " != " + child;

		Object v = contents.remove(oldRange);
		assert v != null;
		contents.put(newRange, child.getPageID());
		setDirty(true);

		child.setKeyRange(newRange);
	}

	/**
	 * @return the sibling page, fixed to the page buffer. Remember to release
	 *         it.
	 */
	protected BTreePage<K, V> getHigherSibling(KeyRange<K> keyRange, Owner owner) {
		assert !isLeafPage();
		Entry<KeyRange<K>, PageValue<?>> entry = contents.higherEntry(keyRange);
		if (entry == null)
			return null;
		PageID router = (PageID) entry.getValue();
		assert !entry.getKey().equals(keyRange);
		return dbConfig.getPageBuffer().fixPage(router, factory, false, owner);
	}

	/**
	 * @return the sibling page, fixed to the page buffer. Remember to release
	 *         it.
	 */
	protected BTreePage<K, V> getLowerSibling(KeyRange<K> keyRange, Owner owner) {
		assert !isLeafPage();
		Entry<KeyRange<K>, PageValue<?>> entry = contents.lowerEntry(keyRange);
		if (entry == null)
			return null;
		PageID router = (PageID) entry.getValue();
		assert !entry.getKey().equals(keyRange);
		return dbConfig.getPageBuffer().fixPage(router, factory, false, owner);
	}

	/**
	 * Convenience method from VisualizablePage. Does not follow proper page
	 * fixing policies. Must only be used by the visualization program.
	 */
	@Override
	public List<VisualizablePage<K, V>> getChildren() {
		if (isLeafPage()) {
			// Leaves have no children
			return new ArrayList<VisualizablePage<K, V>>();
		}
		List<VisualizablePage<K, V>> res = new ArrayList<VisualizablePage<K, V>>();

		for (PageValue<?> child : contents.values()) {
			PageID router = (PageID) child;
			BTreePage<K, V> bChild = dbConfig.getPageBuffer().fixPage(router,
					factory, false, tree.internalOwner);
			res.add(bChild);
			// The pages are released before the calling program uses them, so
			// this only works for the visualization program.
			dbConfig.getPageBuffer().unfix(bChild, tree.internalOwner);
		}
		return res;
	}

	@SuppressWarnings("unchecked")
	public Pair<K, V> floorEntry(K key) {
		if (isLeafPage()) {
			Entry<KeyRange<K>, PageValue<?>> entry = contents
					.floorEntry(KeyRangeImpl.getKeyRange(key));
			return entry != null ? new Pair<K, V>(entry.getKey().getMin(),
					(V) entry.getValue()) : null;
		} else {
			// No values in index nodes
			return null;
		}
	}

	@Override
	public PageValue<?> getEntry(K key) {
		for (KeyRange<K> entry : contents.keySet()) {
			if (entry.contains(key)) {
				return contents.get(entry);
			}
		}
		return null;
	}

	@Override
	public int getEntryCount() {
		return contents.size();
	}

	protected Iterator<Map.Entry<KeyRange<K>, PageValue<?>>> contentIterator() {
		return contents.entrySet().iterator();
	}

	protected TreeMap<KeyRange<K>, PageValue<?>> getContents() {
		return contents;
	}

	@Override
	public TextLine[] getPageData(int version, TreeDrawStyle scheme) {
		TextLine[] keys = new TextLine[contents.size()];
		int c = 0;
		for (Entry<KeyRange<K>, PageValue<?>> entry : contents.entrySet()) {
			if (isLeafPage()) {
				String key = entry.getKey().getMin().toString();
				keys[c++] = new TextLine(showLeafPageValues ? key + ": "
						+ entry.getValue() : key, entry.getKey(),
						entry.getValue());
			} else {
				keys[c++] = new TextLine(entry.getKey().toString() + ": "
						+ ((PageID) entry.getValue()).intValue(),
						entry.getKey(), entry.getValue());
			}
		}
		return keys;
	}

	/**
	 * Moves the first key to another sibling, so this page's key range will
	 * change.
	 * 
	 * @return the new starting point of this page's key range
	 */
	protected K moveFirst(BTreePage<K, V> toSibling) {
		Map.Entry<KeyRange<K>, PageValue<?>> entry = contents.firstEntry();
		KeyRange<K> range = entry.getKey();
		PageValue<?> value = entry.getValue();
		removeContents(range, value);
		toSibling.putContents(range, value);
		return contents.firstKey().getMin();
	}

	/**
	 * Moves the last key to another sibling, so the sibling page's key range
	 * will change.
	 * 
	 * @return the new starting point of the sibling page's key range
	 */
	protected K moveLast(BTreePage<K, V> toSibling) {
		Map.Entry<KeyRange<K>, PageValue<?>> entry = contents.lastEntry();
		KeyRange<K> range = entry.getKey();
		PageValue<?> value = entry.getValue();
		removeContents(range, value);
		toSibling.putContents(range, value);
		return range.getMin();
	}

	/**
	 * Dirties this node, the sibling and the parent
	 */
	protected void moveAllEntries(BTreePage<K, V> toSibling,
			BTreePage<K, V> parent) {
		setDirty(true);
		toSibling.setDirty(true);
		while (!contents.isEmpty()) {
			moveLast(toSibling);
		}
		KeyRange<K> range = new KeyRangeImpl<K>(KeyUtils.min(getKeyRange()
				.getMin(), toSibling.getKeyRange().getMin()), KeyUtils.max(
				getKeyRange().getMax(), toSibling.getKeyRange().getMax()));
		parent.updateKeyRange(toSibling, range);
	}

	@Override
	protected void clearContents() {
		contents.clear();
		setDirty(true);
	}

	@Override
	public int getSingleEntrySize() {
		int size = 0;

		// Both page types first store the (min) key
		size += dbConfig.getKeyPrototype().getByteDataSize();

		if (isLeafPage()) {
			// Leaf pages store key + value
			size += dbConfig.getValuePrototype().getByteDataSize();
		} else {
			// Index pages store key + child pointer
			size += PageID.PROTOTYPE.getByteDataSize();
		}

		return size;
	}

	@Override
	protected KeyRange<K> loadSingleEntry(ByteBuffer data, int index,
			int entryCount) {
		// Read min key
		KeyRange<K> range = null;

		// Read stored value
		PageValue<?> value = null;
		if (isLeafPage()) {
			K key = dbConfig.getKeyPrototype().readFromBytes(data);
			range = KeyRangeImpl.getKeyRange(key);
			value = dbConfig.getValuePrototype().readFromBytes(data);
		} else {
			K key = dbConfig.getKeyPrototype().readFromBytes(data);
			value = PageID.PROTOTYPE.readFromBytes(data);
			K next = getKeyRange().getMax();
			// If we're not at the last position yet, the max key is the same
			// as the min key of the next entry
			if (index < entryCount - 1) {
				next = dbConfig.getKeyPrototype().readFromBytes(data);
				// Rewind
				data.position(data.position()
						- dbConfig.getKeyPrototype().getByteDataSize());
			}
			range = new KeyRangeImpl<K>(key, next);
		}

		log.debug(range);

		assert value != null;
		putContents(range, value);
		return range;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected Object saveSingleEntry(ByteBuffer data, int index, Object position) {
		KeyRange<K> lastSavedKey = (KeyRange<K>) position;
		Map.Entry<KeyRange<K>, PageValue<?>> entry = null;
		if (lastSavedKey == null) {
			// Store the first key
			entry = contents.firstEntry();
		} else {
			entry = contents.higherEntry(lastSavedKey);
		}
		assert entry != null;

		KeyRange<K> range = entry.getKey();
		// Both page types only store the (min) key
		range.getMin().writeToBytes(data);

		// Write stored contents
		PageValue<?> value = entry.getValue();
		assert value != null;
		value.writeToBytes(data);

		return range;
	}

	/**
	 * Custom data: Key range + next page id
	 * 
	 * @return page header size, in bytes
	 */
	@Override
	protected int getCustomHeaderSize() {
		int size = 0;
		size += nextPage.getByteDataSize();
		return size;
	}

	@Override
	protected void savePageCustomHeader(ByteBuffer pageData) {
		// Custom data: super data (key range) + next node id
		// Save next node id
		nextPage.writeToBytes(pageData);
	}

	@Override
	protected void loadPageCustomHeader(ByteBuffer pageData) {
		// Custom data: super data (key range) + next node id
		// Load next node id
		nextPage = nextPage.readFromBytes(pageData);
	}

	/** Bits 0-15 are reserved, 16-31 can be used by extensions */
	@Override
	protected void setCustomSettingBits(int settingBits) {
	}

	/** Bits 0-15 are reserved, 16-31 can be used by extensions */
	@Override
	protected int getCustomSettingBits() {
		return 0;
	}

	@Override
	public boolean isRoot(PagePath<K, V, ? extends Page<K, V>> path) {
		return isRoot();
	}

	@Override
	public boolean processEntries(
			Callback<Pair<KeyRange<K>, PageValue<?>>> callback) {
		for (Entry<KeyRange<K>, PageValue<?>> entry : contents.entrySet()) {
			if (!callback.callback(new Pair<KeyRange<K>, PageValue<?>>(entry
					.getKey(), entry.getValue())))
				return false;
		}
		return true;
	}

	@Override
	public PageID findChildPointer(K key) {
		if (isLeafPage())
			throw new UnsupportedOperationException(
					"Not supported for child pages");
		return (PageID) getEntry(key);
	}

	@Override
	public List<PageValue<?>> getEntries() {
		return CollectionUtils.entrySetToValueList(contents.entrySet());
	}

	@Override
	protected int getMinEntries() {
		return smoPolicy.getMinEntries(this);
	}

	@Override
	public boolean containsChild(PageID childID) {
		assert !isLeafPage();
		for (Entry<?, PageValue<?>> entry : contents.entrySet()) {
			if (childID.equals(entry.getValue()))
				return true;
		}
		return false;
	}

	/**
	 * Traverses entries starting from the start entry, calling the callback
	 * function for each entry.
	 */
	protected boolean traverseLeafEntries(K start,
			Callback<Pair<K, V>> callback, Owner owner) {
		assert isLeafPage();

		BTreePage<K, V> node = this;
		KeyRange<K> range = new KeyRangeImpl<K>(start, start.getMaxKey());

		while (node != null) {
			if (!node.getAll(range, callback)) {
				// Stop indicated
				if (node != this)
					dbConfig.getPageBuffer().unfix(node, owner);
				return false;
			}
			if (!range.contains(node.getKeyRange().getMax())) {
				// Maximum key is no longer in the range, so stop the search
				if (node != this)
					dbConfig.getPageBuffer().unfix(node, owner);
				return true;
			}

			// Fetch next, linked node and fix it (latch-coupling, or
			// fix-coupling right now...)
			BTreePage<K, V> next = node.nextPage.isValid() ? dbConfig
					.getPageBuffer().fixPage(node.nextPage, factory, false,
							owner) : null;
			// Release previous node (leave this node fixed, it will be
			// unfixed by the caller).
			if (node != this)
				dbConfig.getPageBuffer().unfix(node, owner);

			node = next;
		}
		// All traversed
		return true;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void checkConsistency(Object... params) {
		assert params.length > 0;
		PagePath<K, V, BTreePage<K, V>> path = (PagePath<K, V, BTreePage<K, V>>) params[0];

		assert this == path.getCurrent();
		int entries = getEntryCount();
		if (isRoot(path)) {
			if (isLeafPage()) {
				assert entries >= 1;
			} else {
				assert entries >= 2;
			}
		} else {
			assert entries >= getMinEntries() : entries + " < "
					+ getMinEntries();
		}

		for (KeyRange<K> range : contents.keySet()) {
			assert getKeyRange().contains(range);
		}

		if (!isLeafPage()) {
			PageBuffer buffer = tree.getPageBuffer();
			PageFactory<BTreePage<K, V>> factory = tree.getPageFactory();
			for (PageValue<?> val : getContents().values()) {
				PageID pageID = (PageID) val;
				BTreePage<K, V> child = buffer.fixPage(pageID, factory, false,
						tree.internalOwner);
				path.descend(child);
				child.checkConsistency(path);
				path.ascend();
				buffer.unfix(child, tree.internalOwner);
			}
		}
	}

	@Override
	public void printDebugInfo() {
		super.printDebugInfo();
		for (Entry<KeyRange<K>, PageValue<?>> entry : contents.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
	}

	@Override
	public int getTypeIdentifier() {
		return PAGE_IDENTIFIER;
	}
}
