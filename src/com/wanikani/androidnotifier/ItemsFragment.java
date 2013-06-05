package com.wanikani.androidnotifier;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Vector;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.wanikani.wklib.Item;
import com.wanikani.wklib.Kanji;
import com.wanikani.wklib.Radical;
import com.wanikani.wklib.SRSLevel;
import com.wanikani.wklib.Vocabulary;

/* 
 *  Copyright (c) 2013 Alberto Cuda
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This fragment shows item lists. The user can choose how to filter
 * and how to sort them. Sorting is done through a simple comparator;
 * stock comparators provided by WKLib are enough for our purposes.
 * Filters is slightly more complex, and we use an implementation 
 * of a specific interface (@link Filter) for each of them. 
 */
public class ItemsFragment extends Fragment implements Tab, Filter.Callback {

	/**
	 * This enum represents the kind of additional information to display on
	 * each item's description. Since this app should run on small handests
	 * too, the room is not much, so the kind of string returned depends
	 * a lot on the kind of ordering the user chose.
	 * Note also that the string may depend on the filter, since they
	 * use different WK APIs, returning different subsets of the data schema.
	 */
	enum ItemInfo {
		
		/**
		 * Display days elapsed since unlock date. If the item has not
		 * been unlocked yet (or if unlock date is not available), 
		 * it returns an empty string.
		 */
		AGE {
			public String getInfo (Resources res, Item i)
			{
				Date unlock;
				long now, age;

				unlock = i.getUnlockedDate ();
				if (unlock == null)
					return ""; /* Not unlocked, or not available */
				
				now = System.currentTimeMillis ();
				age = now - unlock.getTime ();

				/* Express age in minutes */
				age /= 60 * 1000;
				if (age < 60)
					return res.getString (R.string.fmt_ii_less_than_one_hour);
				
				/* Express age in hours */
				age = Math.round (((float) age) / 60);
				if (age == 1) 
					return res.getString (R.string.fmt_ii_one_hour);
				if (age < 24) 
					return res.getString (R.string.fmt_ii_hours, age);
				
				/* Express age in days */
				age = Math.round (((float) age) / 24);
				if (age == 1) 
					return res.getString (R.string.fmt_ii_one_day);

				return res.getString (R.string.fmt_ii_days, age);
			}
		},
		
		/**
		 * Displays the percentage of correct answers.
		 * If intermediate (meaning & reading) stats are available, they
		 * are displayed too.
		 */
		ERRORS {
			public String getInfo (Resources res, Item i)
			{
				int pmean, pread;
				
				if (i.stats == null || 
					i.stats.reading == null || i.stats.meaning == null)
					return res.getString (R.string.fmt_ii_percent, i.percentage);
				
				pmean = i.stats.meaning.correct * 100 / 
						(i.stats.meaning.correct + i.stats.meaning.incorrect);
				pread = i.stats.reading.correct * 100 / 
						(i.stats.reading.correct + i.stats.reading.incorrect);
				
				return res.getString (R.string.fmt_ii_percent_full,
									  i.percentage, pmean, pread);
			}
		};
		
		/**
		 * Returns a string which describes the item.
		 * @param res the resource container
		 * @param i the item to be described
		 * @return a description
		 */
		public abstract String getInfo (Resources res, Item i);		
	}

	/**
	 * A simple wrapper of a level item, to be associated to the ListView. 
	 * Currently only the level number is used, so this structure is 
	 * almost pointless... 
	 */
	private static class Level {

		/// The level number
		public int level;
		
		/**
		 * Constructor
		 * @param level the level number
		 */
		public Level (int level)
		{
			this.level = level;
		}
		
	}
	
	/**
	 * This listener is registered to the level's ListView.
	 * When an item is clicked, all the items of that level are displayed.
	 * So far, the "apprentice" filter and the ordering is kept as it was
	 * beforehand (don't know if it's a good idea).
	 */
	class LevelClickListener implements AdapterView.OnItemClickListener {
	
		public void onItemClick (AdapterView<?> adapter, View view, int position, long id)
		{
			Level l;
			
			l = (Level) adapter.getItemAtPosition (position);
			setLevelFilter (l.level);
		}
		
	}

	/**
	 * The implementation of the levels' ViewList. Pretty straightforward.
	 * No sorting or filtering.
	 * Levels are instances of the {@link Level} class (though I guess 
	 * Integers could have been used as well.
	 */
	class LevelListAdapter extends BaseAdapter {

		/// The current level set
		List<Level> levels;
		
		/**
		 * Constructor.
		 */
		public LevelListAdapter ()
		{
			levels = new Vector<Level> ();
		}
		
		@Override
		public int getCount ()
		{
			return levels.size ();
		}
		
		@Override
		public Level getItem (int position)
		{
			return levels.get (position);
		}
		
		/**
		 * Deletes all the items in the list
		 */
		public void clear ()
		{
			levels = new Vector<Level> ();
			notifyDataSetChanged ();
		}

		/**
		 * Replaces the items in the list with a new set.
		 * @param levels the new list
		 */
		public void replace (List<Level> levels)
		{
			clear ();
			this.levels.addAll (levels);
			notifyDataSetChanged ();
		}
				
		@Override
		public long getItemId (int position)
		{
			return position;			
		}
	
		@Override
		public View getView (int position, View row, ViewGroup parent) 
		{
			LayoutInflater inflater;
			TextView view;

			inflater = main.getLayoutInflater ();
			row = inflater.inflate (R.layout.items_level, parent, false);
			view = (TextView) row.findViewById (R.id.tgr_level);
			view.setText (Integer.toString (getItem (position).level));
			
			levelAdded (getItem (position).level, row);
			
			return row;
		}		
	}
	
	/**
	 * The implementation of the items' ViewList. Items are instances
	 * of the WKLib {@link Item} class. This class implements sorting
	 * but no filtering.
	 */
	class ItemListAdapter extends BaseAdapter {

		/// The current list of items. It is always sorted.
		List<Item> items;

		/// The current comparator
		Comparator<Item> cmp;
		
		/// What to put into the "extra info" textview
		ItemInfo iinfo;

		/**
		 * Constructor
		 * @param cmp the comparator
		 * @param iinfo what to put into the "extra info" textview
		 */
		public ItemListAdapter (Comparator<Item> cmp, ItemInfo iinfo)
		{
			this.cmp = cmp;
			this.iinfo = iinfo;
			
			items = new Vector<Item> ();
		}		

		/**
		 * Changes the comparator. Ordinarily the extra info is strictly
		 * bound to the comparator, so we give a chance to update it as well.
		 * @param cmp the comparator
		 * @param iinfo what to put into the "extra info" textview
		 */
		public void setComparator (Comparator<Item> cmp, ItemInfo iinfo)
		{
			this.cmp = cmp;
			this.iinfo = iinfo;

			invalidate ();
		}
		
		@Override
		public int getCount ()
		{
			return items.size ();
		}
		
		@Override
		public Item getItem (int position)
		{
			return items.get (position);
		}
		
		@Override
		public long getItemId (int position)
		{
			return position;			
		}
	
		/**
		 * Empties the list
		 */
		public void clear ()
		{
			items.clear ();
			items.clear ();
			notifyDataSetChanged ();
		}

		/**
		 * Appends the items to the list. Afterward the list is sorted again.
		 * @param newItems the additional items to show
		 */
		public void addAll (List<Item> newItems)
		{
			items.addAll (newItems);
			invalidate ();
		}
		
		/**
		 * Sorts the collection again, also refreshing the list.
		 */
		private void invalidate ()
		{		
			Collections.sort (items, cmp);
			notifyDataSetChanged ();
		}
		
		/**
		 * Fills the specific fields for a radical layout.
		 * @param row the view to fill
		 * @param radical the radical to describe
		 */
		protected void fillRadical (View row, Radical radical)
		{
			ImageView iw;
			TextView tw;
			
			tw = (TextView) row.findViewById (R.id.it_glyph);
			iw = (ImageView) row.findViewById (R.id.img_glyph);
			if (radical.character != null) {
				tw = (TextView) row.findViewById (R.id.it_glyph);
				tw.setText (radical.character);
				tw.setVisibility (View.VISIBLE);
				iw.setVisibility (View.GONE);
			} else if (radical.bitmap != null) {		
				iw.setImageBitmap (radical.bitmap);
				tw.setVisibility (View.GONE);
				iw.setVisibility (View.VISIBLE);				
			} /* else { should never happen! } */

		}

		/**
		 * Fills the specific fields for a kanji layout.
		 * @param row the view to fill
		 * @param radical the kanji to describe
		 */
		protected void fillKanji (View row, Kanji kanji)
		{
			TextView ktw, otw;
			TextView tw;
			
			otw = (TextView) row.findViewById (R.id.it_onyomi);
			otw.setText (kanji.onyomi);

			ktw = (TextView) row.findViewById (R.id.it_kunyomi);
			ktw.setText (kanji.kunyomi);
			
			switch (kanji.importantReading) {
			case ONYOMI:
				otw.setTextColor (importantColor);
				ktw.setTextColor (normalColor);
				break;

			case KUNYOMI:
				otw.setTextColor (normalColor);
				ktw.setTextColor (importantColor);
				break;
			}

			tw = (TextView) row.findViewById (R.id.it_glyph);
			tw.setText (kanji.character);
		}
		
		/**
		 * Fills the specific fields for a vocab layout.
		 * @param row the view to fill
		 * @param radical the vocab to describe
		 */
		protected void fillVocab (View row, Vocabulary vocab)
		{
			TextView tw;
			
			tw = (TextView) row.findViewById (R.id.it_reading);
			tw.setText (vocab.kana);

			tw = (TextView) row.findViewById (R.id.it_glyph);
			tw.setText (vocab.character);
		}

		@Override
		public View getView (int position, View row, ViewGroup parent) 
		{
			LayoutInflater inflater;
			ImageView iw;
			TextView tw;
			Item item;

			inflater = main.getLayoutInflater ();			

			item = getItem (position);
			switch (item.type) {
			case RADICAL:
				row = inflater.inflate (R.layout.items_radical, parent, false);
				fillRadical (row, (Radical) item);
				break;
									
			case KANJI:
				row = inflater.inflate (R.layout.items_kanji, parent, false);
				fillKanji (row, (Kanji) item);
				break;

			case VOCABULARY:
				row = inflater.inflate (R.layout.items_vocab, parent, false);
				fillVocab (row, (Vocabulary) item);
				break;
			}

			if (currentFilter != levelf) {
				tw = (TextView) row.findViewById (R.id.it_level);
				tw.setText (Integer.toString (item.level));				
			}
			
			iw = (ImageView) row.findViewById (R.id.img_srs);
			if (item.stats != null) {
				iw.setImageDrawable (srsht.get (item.stats.srs));
				iw.setVisibility (View.VISIBLE);
			} else
				iw.setVisibility (View.GONE);
			
			tw = (TextView) row.findViewById (R.id.it_info);
			tw.setText (iinfo.getInfo (getResources (), item));
			
			tw = (TextView) row.findViewById (R.id.it_meaning);
			tw.setText (item.meaning);
			
			return row;
		}		
	}
	
	/**
	 * This listener is registered to the items' ListView.
	 * When an item is clicked we open the specific page on the WK website.
	 * May become a longclick listener.. don't want it to be too annoying
	 */
	class ItemClickListener implements AdapterView.OnItemClickListener {
		
		public void onItemClick (AdapterView<?> adapter, View view, int position, long id)
		{
			Intent intent;
			String url;
			Item i;
			
			i = (Item) adapter.getItemAtPosition (position);
			url = null;
			switch (i.type) {
			case RADICAL:
				url = "http://www.wanikani.com/vocabulary/" + i.character;
				break;
				
			case KANJI:
				url = "http://www.wanikani.com/kanji/" + i.character;
				break;
				
			case VOCABULARY:
				url = "http://www.wanikani.com/vocabulary/" + i.character;
				break;
			}
			
			if (url == null)
				return;	/* That's strange for sure */
			
			intent = new Intent (Intent.ACTION_VIEW);
			intent.setData (Uri.parse (url));
			
			startActivity (intent);
		}
		
	}

	/**
	 * The listener registered to the filter and sort buttons.
	 * It shows or hide the menu, according to the well-known
	 * menu pattern. 
	 */
	class MenuPopupListener implements View.OnClickListener {
		
		public void onClick (View view)
		{
			boolean filterV, sortV;
			View filterW, sortW;
			
			filterW = parent.findViewById (R.id.menu_filter);
			filterV = filterW.getVisibility () == View.VISIBLE;

			sortW = parent.findViewById (R.id.menu_order);			
			sortV = sortW.getVisibility () == View.VISIBLE;
			
			filterW.setVisibility (View.GONE);
			sortW.setVisibility (View.GONE);
			
			switch (view.getId ()) {
			case R.id.btn_item_filter:
				if (!filterV)
					filterW.setVisibility (View.VISIBLE);
				break;

			case R.id.btn_item_sort:
				if (!sortV)
					sortW.setVisibility (View.VISIBLE);
				break;
				
			}
		}
	}
	
	/**
	 * The listener registered to the filter/sort radio group buttons.
	 * When an item is clicked, it updates the list accordingly.
	 */
	class RadioGroupListener implements Button.OnClickListener {
		
		public void onClick (View view)
		{
			View filterW, sortW;

			filterW = parent.findViewById (R.id.menu_filter);
			sortW = parent.findViewById (R.id.menu_order);			
			
			filterW.setVisibility (View.GONE);
			sortW.setVisibility (View.GONE);
			
			switch (view.getId ()) {
			case R.id.btn_filter_all:
				sortBySRS ();
				setLevelFilter (currentLevel, false);
				break;

			case R.id.btn_filter_missing:
				sortBySRS ();
				setLevelFilter (currentLevel, true);
				break;
				
			case R.id.btn_filter_critical:
				sortByErrors ();
				setCriticalFilter ();
				break;

			case R.id.btn_filter_unlocks:
				sortByTime ();
				setUnlockFilter ();
				break;

			case R.id.btn_sort_errors:
				sortByErrors ();
				break;

			case R.id.btn_sort_srs:
				sortBySRS ();
				break;
				
			case R.id.btn_sort_time:
				sortByTime ();
				break;

			case R.id.btn_sort_type:
				sortByType ();
			}
		}
		
		/**
		 * Switches to SRS sort order, fixing both ListView and radio buttons.
		 */
		private void sortBySRS ()
		{
			RadioGroup rg;
			
			rg = (RadioGroup) parent.findViewById (R.id.rg_order);
			rg.check (R.id.btn_sort_srs);
			iad.setComparator (Item.SortBySRS.INSTANCE, ItemInfo.AGE);				
		}
		
		/**
		 * Switches to age sort order, fixing both ListView and radio buttons.
		 */
		private void sortByTime ()
		{
			RadioGroup rg;
			
			rg = (RadioGroup) parent.findViewById (R.id.rg_order);
			rg.check (R.id.btn_sort_time);
			iad.setComparator (Item.SortByType.INSTANCE, ItemInfo.AGE);				
		}

		/**
		 * Switches to errors sort order, fixing both ListView and radio buttons.
		 */
		private void sortByErrors ()
		{
			RadioGroup rg;
			
			rg = (RadioGroup) parent.findViewById (R.id.rg_order);
			rg.check (R.id.btn_sort_errors);
			iad.setComparator (Item.SortByErrors.INSTANCE, ItemInfo.ERRORS);
		}

		/**
		 * Switches to type sort order, fixing both ListView and radio buttons.
		 */
		private void sortByType ()
		{
			RadioGroup rg;
			
			rg = (RadioGroup) parent.findViewById (R.id.rg_order);
			rg.check (R.id.btn_sort_type);
			iad.setComparator (Item.SortByType.INSTANCE, ItemInfo.AGE);
		}

	}

	//	/// The main activity
	MainActivity main;
	
	/// The root view of the fragment
	View parent;

	/* ---------- Levels stuff ---------- */
	
	/// The list adapter of the levels' list
	LevelListAdapter lad;
	
	/// The levels' list view
	ListView lview;

	/// The levels' list click listener
	LevelClickListener lcl;
	
	/// The last level clicked so far
	int currentLevel;
	
	/// The number of levels
	int levels;

	/// Normal reading color
	int normalColor;
	
	/// Important reading color
	int importantColor;
	
	/// Selected level color
	int selectedColor;
	
	/// Unselected level color
	int unselectedColor;
	
	/* ---------- Items stuff ---------- */

	/// The list adapter of the items' list
	ItemListAdapter iad;
	
	/// The items' list view
	ListView iview;
	
	/// The items' list click listener
	ItemClickListener icl;
	
	/* ---------- Sort/filter stuff ---------- */

	/// The popup menu listener
	MenuPopupListener mpl;
	
	/// The menu buttons' listener
	RadioGroupListener rgl;
	
	/// True if the "other filters" button is spinning
	boolean spinning;

	/// A SRS level to turtle icon map
	EnumMap<SRSLevel, Drawable> srsht;

	/// True if the apprentice filter is set (i.e. we are displaying only apprentice
	/// items
	boolean apprentice;

	/* ---------- Filters ---------- */
	
	/// The level filter instance
	LevelFilter levelf;	

	/// The critical items filter instance
	CriticalFilter criticalf;	
	
	/// The recent unlocks items filter instance
	UnlockFilter unlockf;

	/// The current filter
	private Filter currentFilter;
	
	@Override
	public void setMainActivity (MainActivity main)
	{
		this.main = main;
	}
	
	/**
	 * Creation of the fragment. We build up all the singletons, leaving the
	 * bundle alone, because we set the retain instance flag to <code>true</code>.
	 * 	@param bundle the saved instance state
	 */
	@Override
	public void onCreate (Bundle bundle)
	{
		Resources res;

		super.onCreate (bundle);

		setRetainInstance (true);

		currentLevel = -1;
		levelf = new LevelFilter (this);
		criticalf = new CriticalFilter (this);
		unlockf = new UnlockFilter (this);
		currentFilter = null;
		
		lcl = new LevelClickListener ();
		icl = new ItemClickListener ();
		
		mpl = new MenuPopupListener ();
		rgl = new RadioGroupListener ();

		res = getResources ();
		srsht = new EnumMap<SRSLevel, Drawable> (SRSLevel.class);
    	srsht.put (SRSLevel.APPRENTICE, res.getDrawable (R.drawable.apprentice));
    	srsht.put (SRSLevel.GURU, res.getDrawable (R.drawable.guru));
    	srsht.put (SRSLevel.MASTER, res.getDrawable (R.drawable.master));
    	srsht.put (SRSLevel.ENLIGHTEN, res.getDrawable (R.drawable.enlighten));
    	srsht.put (SRSLevel.BURNED, res.getDrawable (R.drawable.burned));
    	
    	normalColor = res.getColor (R.color.normal);
    	importantColor = res.getColor (R.color.important);
    	selectedColor = res.getColor (R.color.selected);
    	unselectedColor = res.getColor (R.color.unselected);
	}
	
	/**
	 * Builds the GUI and create the default item listing. Which is
	 * by item type, sorted by time.
	 * @param inflater the inflater
	 * @param container the parent view
	 * @param savedInstance an (unused) bundle
	 */
	@Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container,
            				  Bundle bundle) 
    {
		RadioGroup rg;
		ImageButton btn;
		int i;
		
		super.onCreateView (inflater, container, bundle);
		
		parent = inflater.inflate(R.layout.items, container, false);
		
		lad = new LevelListAdapter ();
		lview = (ListView) parent.findViewById (R.id.lv_levels);
		lview.setAdapter (lad);
		lview.setOnItemClickListener (lcl);

		iad = new ItemListAdapter (Item.SortByType.INSTANCE, ItemInfo.AGE);
		iview = (ListView) parent.findViewById (R.id.lv_items);
		iview.setAdapter (iad);
		iview.setOnItemClickListener (icl);
		
		btn = (ImageButton) parent.findViewById (R.id.btn_item_filter);
		btn.setOnClickListener (mpl);
		btn = (ImageButton) parent.findViewById (R.id.btn_item_sort);
		btn.setOnClickListener (mpl);
		
		rg = (RadioGroup) parent.findViewById (R.id.rg_filter);
		for (i = 0; i < rg.getChildCount (); i++)
			rg.getChildAt (i).setOnClickListener (rgl);
		rg.check (R.id.btn_filter_all);
		
		rg = (RadioGroup) parent.findViewById (R.id.rg_order);
		for (i = 0; i < rg.getChildCount (); i++)
			rg.getChildAt (i).setOnClickListener (rgl);
		rg.check (R.id.btn_sort_type);
		
    	return parent;
    }
	
	@Override
	public void onResume ()
	{
		super.onResume ();

		refreshComplete (main.getDashboardData ());
	}
	
	/**
	 * Called when data has been refreshed. Actually the only field we
	 * are intested in is the user's level, so we store it even if it
	 * the view has not been created yet. On the other hand, if the
	 * gui exists, we also update the level list. 
	 */
	public void refreshComplete (DashboardData dd)
	{
		List<Level> l;
		Level level;
		int i, clevel;
		
		/* This must be done as soon as possible */
		levels = dd.level;
		
		if (!isResumed ())
			return;
		
		/* Again, try not to be annoying. We only refresh the levels'
		 * list if it already dispayed (i.e. currentFilter==levelf) 
		 * or we are at app startup (i.e. currentFilter==null).
		 * The item list is updated only at all startup (currentLevel<0) */
		if (currentFilter == null || currentFilter == levelf) {
			clevel = currentLevel > 0 ? currentLevel : dd.level;
		
			l = new Vector<Level> (dd.level);		
			for (i = dd.level; i > 0; i--) {
				level = new Level (i);
				l.add (level);
			}
			lad.replace (l);
			lad.notifyDataSetChanged ();
		
			if (currentLevel < 0)
				setLevelFilter (clevel);
		}
	}
	
	/**
	 * Called when the view is destroyed. We take this chance to stop all
	 * the threads that may attempt to update a dead view. 
	 */
	@Override
	public void onDestroyView ()
	{
		super.onDetach ();
		
		currentFilter = null;
		
		levelf.stopTask ();
		criticalf.stopTask ();
		unlockf.stopTask ();
	}

	/**
	 * Switches to level list filter. The apprentice filter is kept
	 * only if we are just switching from a level to another level.
	 * If we are switching e.g. from unlock filter to level filter,
	 * it is cleared.
	 * @param level the level to display 
	 */
	private void setLevelFilter (int level)
	{
		if (currentFilter != levelf)
			apprentice = false;
		setLevelFilter (level, apprentice);
	}

	/**
	 * Switches to level list filter. 
	 * @param level the level to display
	 * @param apprentice the apprentice flag 
	 */
	private void setLevelFilter (int level, boolean apprentice)
	{
		RadioGroup fg;
		
		fg = (RadioGroup) parent.findViewById (R.id.rg_filter);
		fg.check (apprentice ? R.id.btn_filter_missing : R.id.btn_filter_all); 
		
		this.apprentice = apprentice;
		currentFilter = levelf;
		levelf.select (main.getConnection (), level, apprentice);
	}
	
	/**
	 * Switches to critical items filter. 
	 */
	private void setCriticalFilter ()
	{
		RadioButton btn;
		
		btn = (RadioButton) parent.findViewById (R.id.btn_filter_critical); 
		btn.setSelected (true);

		currentFilter = criticalf;
		criticalf.select (main.getConnection ());
	}

	/**
	 * Switches to recent unlocks filter. 
	 */
	private void setUnlockFilter ()
	{
		RadioButton btn;
		
		btn = (RadioButton) parent.findViewById (R.id.btn_filter_unlocks); 
		btn.setSelected (true);

		currentFilter = unlockf;
		unlockf.select (main.getConnection ());
	}

	/**
	 * Replaces the contents of the items' list view contents with a new list.
	 * @param sfilter the source filter 
	 * @param list the new list
	 * @param ok if this is the complete list or something went wrong while
	 * loading data
	 */
	@Override
	public void setData (Filter sfilter, List<Item> list, boolean ok)
	{
		if (sfilter != currentFilter)
			return;

		iad.clear ();
		iad.addAll (list);
		iad.notifyDataSetChanged ();
	}

	/**
	 * Updates the contents of the items' list view contents, by adding
	 * new items.
	 * @param sfilter the source filter 
	 * @param list the new list
	 * @param ok if this is the complete list or something went wrong while
	 * loading data
	 */
	@Override
	public void addData (Filter sfilter, List<Item> list)
	{
		if (sfilter != currentFilter)
			return;
		iad.addAll (list);
		iad.notifyDataSetChanged ();
	}

	@Override
	public void clearData (Filter sfilter)
	{
		if (sfilter != currentFilter)
			return;

		iad.clear ();
		iad.notifyDataSetChanged ();
	}
	
	/**
	 * Does nothing. Actually we may display a small "warning" icon.
	 * @param sfilter the filter which is publishing these items
	 * @param ok tells if this is the complete list or something went wrong while
	 * loading data
	 */
	@Override
	public void noMoreData (Filter sfilter, boolean ok)
	{
		/* empty */
	}
	
	/**
	 * Show or hide the dashboard data spinner. 
	 * Needed to implement the @param Tab interface, but we actually ignore this.
	 * @param enable true if should be shown
	 */
	public void spin (boolean enable)
	{
		/* empty */
	}

	@Override
	public void selectLevel (Filter filter, int level, boolean spinning)
	{
		if (filter != currentFilter)
			return;
		
		if (currentLevel != level && currentLevel > 0)
			selectLevel (currentLevel, false, false);
		
		currentLevel = level;
		this.spinning = spinning;
		
		selectLevel (level, true, spinning);
	}

	/**
	 * Internal implementation of the {@link #selectLevel(Filter, int, boolean)}
	 * logic. This gets called when it is certain that we are being called
	 * by the correct filter, or no filter is involved, so checks are skipped.
	 * In addition, this method may be called also to <i>unselect</i> a level.
	 * @param level the level on which to act
	 * @param select <code>true</code> if the level should be selected.
	 * 	Otherwise it is unselected
	 * @param spin <code>true</code> if the spinner should be displayed.
	 * 	Otherwise it is hidden. Makes sense only if <code>select</code>
	 *  is <code>true</code> too
	 */
	protected void selectLevel (int level, boolean select, boolean spin)
	{
		View row;
		
		row = lview.getChildAt (levels - level);
		if (row != null) 	/* May happen if the level is not visible */
			selectLevel (row, select, spin);
	}

	/**
	 * Internal implementation of the {@link #selectLevel(Filter, int, boolean)}
	 * logic. This gets called when it is certain that we are being called
	 * by the correct filter, or no filter is involved, so checks are skipped.
	 * In addition, this method may be called also to <i>unselect</i> a level.
	 * @param row the level row on which to act
	 * @param select <code>true</code> if the level should be selected.
	 * 	Otherwise it is unselected
	 * @param spin <code>true</code> if the spinner should be displayed.
	 * 	Otherwise it is hidden. Makes sense only if <code>select</code>
	 *  is <code>true</code> too
	 */
	private void selectLevel (View row, boolean select, boolean spin)
	{
		TextView tw;
		View sw;
		
		selectOtherFilter (false, false);
		
		tw = (TextView) row.findViewById (R.id.tgr_level);
		sw = row.findViewById (R.id.pb_level);
		if (spin) {
			tw.setVisibility (View.GONE);
			sw.setVisibility (View.VISIBLE);
		} else {
			tw.setVisibility (View.VISIBLE);
			sw.setVisibility (View.GONE);
		}
		if (select) {
			tw.setTextColor (selectedColor);
			tw.setTypeface (null, Typeface.BOLD);
		} else {
			tw.setTextColor (unselectedColor);
			tw.setTypeface (null, Typeface.NORMAL);			
		}
	}

	@Override
	public void selectOtherFilter (Filter filter, boolean spinning)
	{
		if (filter != currentFilter)
			return;
		
		selectOtherFilter (true, spinning);
	}
	
	/**
	 * Internal implementation of the {@link #selectOtherFilter(Filter, boolean)}
	 * logic. This gets called when it is certain that we are being called
	 * by the correct filter, or no filter is involved, so checks are skipped.
	 * In addition, this method may be called also to <i>exit</i> filter mode.
	 * When entering filter mode, the level list is hidden.
	 * @param select <code>true</code> if the level should be selected.
	 * 	Otherwise it is unselected
	 * @param spin <code>true</code> if the spinner should be displayed.
	 * 	Otherwise it is hidden. Makes sense only if <code>select</code>
	 *  is <code>true</code> too
	 */
	public void selectOtherFilter (boolean selected, boolean spinning)
	{
		View filterPB, filterBtn, levels;
		
		filterBtn = parent.findViewById (R.id.btn_item_filter);
		filterPB = parent.findViewById (R.id.pb_item_filter);
		levels = parent.findViewById (R.id.lv_levels);
		
		if (spinning) {
			filterBtn.setVisibility (View.GONE);
			filterPB.setVisibility (View.VISIBLE);
		} else {
			filterBtn.setVisibility (View.VISIBLE);
			filterPB.setVisibility (View.GONE);			
		}
		
		levels.setVisibility (selected ? View.GONE : View.VISIBLE);
	}

	/**
	 * This methods is called by the levels' list adapter, right before
	 * displaying a new row. It is needed to make sure that the spinner
	 * is displayed on the newly created row if it should.
	 * @param level the level being created
	 * @param row the row being created
	 */
	public void levelAdded (int level, View row)
	{
		if (currentLevel == level)
			selectLevel (row, true, spinning);
		else
			selectLevel (row, false, false);
	}
	
	/**
	 * Returns the tab name ID.
	 * @param the <code>tag_items</code> ID
	 */
	public int getName ()
	{
		return R.string.tag_items;
	}

	/**
	 * Clears the cache and redisplays data. This may be called quite early,
	 * so we check the null pointers. 
	 */
	public void flush ()
	{
		/* Actually we could just bail out at the first
		 * if.. however it looks nicer this way... */
		if (criticalf != null)
			criticalf.flush ();
		
		if (levelf != null)
			levelf.flush ();
		
		if (unlockf != null)
			unlockf.flush ();
		
		if (currentFilter == criticalf)
			criticalf.select (main.getConnection ());
		else if (currentFilter == levelf && currentLevel > 0)
			levelf.select (main.getConnection (), currentLevel, apprentice);
		else if (currentFilter == unlockf)
			unlockf.select (main.getConnection ());		
	}

	/**
	 * This item has a scroll view.
	 * @return true
	 */
	public boolean hasScroll ()
	{
		 return true;
	}
}