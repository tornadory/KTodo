package com.kos.ktodo;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.*;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import com.kos.ktodo.widget.UpdateService;

/**
 * Edit tags activity.
 *
 * @author <a href="mailto:konstantin.sobolev@gmail.com">Konstantin Sobolev</a>
 */
public class EditTags extends ListActivity {
	private static final String TAG = "EditTags";

	private final int RENAME_TAG_MENU_ITEM = Menu.FIRST;
	private final int DELETE_TAG_MENU_ITEM = RENAME_TAG_MENU_ITEM + 1;

	private TagsStorage tagsStorage;
	private Cursor allTagsCursor;
	private AlertDialog dialog;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.edit_tags);

		tagsStorage = new TagsStorage(this);
		tagsStorage.open();
		allTagsCursor = tagsStorage.getAllTagsExceptCursor(DBHelper.ALL_TAGS_METATAG_ID, DBHelper.UNFILED_METATAG_ID);
		startManagingCursor(allTagsCursor);
		final ListAdapter tagsAdapter = new SimpleCursorAdapter(
				this, android.R.layout.simple_list_item_1,
				allTagsCursor,
				new String[]{DBHelper.TAG_TAG}, new int[]{android.R.id.text1});

		setListAdapter(tagsAdapter);

		findViewById(R.id.add_tag_text).setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(final View view, final int keyCode, final KeyEvent keyEvent) {
				if (keyCode == KeyEvent.KEYCODE_ENTER)
					addTag();
				return false;
			}
		});
		findViewById(R.id.add_tag_button).setOnClickListener(new View.OnClickListener() {
			public void onClick(final View view) {
				addTag();
			}
		});
		final MyListView mlv = getMyListView();
		mlv.setDeleteItemListener(new MyListView.DeleteItemListener() {
			public void deleteItem(final long id) {
				deleteTag(id);
			}
		});

		registerForContextMenu(getListView());
	}

	private MyListView getMyListView() {
		return (MyListView) findViewById(android.R.id.list);
	}

//	@Override
//	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
////		Log.i(TAG, "onKeyDown: " + event);
//		final SlidingView sv = (SlidingView) findViewById(R.id.sliding_view);
//		if (event.getAction() == KeyEvent.ACTION_DOWN) {
//			if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
//				sv.switchLeft();
//				return true;
//			} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
//				sv.switchRight();
//				return true;
//			}
//		}
//		return super.onKeyDown(keyCode, event);
//	}


	@Override
	public void onDetachedFromWindow() {
		if (dialog != null && dialog.isShowing())
			dialog.cancel();
		super.onDetachedFromWindow();
	}

	@Override
	public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, RENAME_TAG_MENU_ITEM, Menu.NONE, R.string.rename);
		menu.add(0, DELETE_TAG_MENU_ITEM, Menu.NONE, R.string.delete);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item) {
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		if (info == null) return false;
		final long id = getListAdapter().getItemId(info.position);
		switch (item.getItemId()) {
			case RENAME_TAG_MENU_ITEM:
				renameTag(id);
				return true;
			case DELETE_TAG_MENU_ITEM:
				deleteTag(id);
				return true;
		}
		return super.onContextItemSelected(item);
	}

	private void deleteTag(final long id) {
		final TodoItemsStorage todoStorage = new TodoItemsStorage(this);
		todoStorage.open();
		try {
			final int byTag = todoStorage.getByTagCursor(id, TodoItemsSortingMode.PRIO_DUE_SUMMARY).getCount();
			if (byTag == 0) {
				tagsStorage.deleteTag(id);
			} else {
				final AlertDialog.Builder b = new AlertDialog.Builder(this);
				b.setTitle(R.string.delete_tag_title);
				b.setMessage(getString(R.string.items_depend_on_tag, byTag));
				if (allTagsCursor.getCount() > 1)
					b.setNeutralButton(R.string.move, new DialogInterface.OnClickListener() {
						public void onClick(final DialogInterface dialog, final int which) {
							moveItemsAndDeleteTag(id);
						}
					});
				b.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
					public void onClick(final DialogInterface dialog, final int which) {
						deleteItemsAndDeleteTag(id);
					}
				});
				b.show();
			}
		} finally {
			todoStorage.close();
		}
		updateView();
	}

	private void deleteItemsAndDeleteTag(final long id) {
		final TodoItemsStorage todoStorage = new TodoItemsStorage(EditTags.this);
		todoStorage.open();
		try {
			todoStorage.deleteByTag(id);
			tagsStorage.deleteTag(id);
			updateView();
		} finally {
			todoStorage.close();
		}

	}

	private void moveItemsAndDeleteTag(final long id) {
		final AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle(R.string.select_tag_title);
		final Cursor c = tagsStorage.getAllTagsExceptCursor(DBHelper.ALL_TAGS_METATAG_ID, DBHelper.UNFILED_METATAG_ID, id);
		final ListAdapter adapter = new SimpleCursorAdapter(
				this, android.R.layout.select_dialog_item,
				c, new String[]{DBHelper.TAG_TAG}, new int[]{android.R.id.text1});
		b.setAdapter(adapter, new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialog, final int which) {
				final long newID = adapter.getItemId(which);
				final TodoItemsStorage todoStorage = new TodoItemsStorage(EditTags.this);
				todoStorage.open();
				try {
					todoStorage.moveTodoItems(id, newID);
					tagsStorage.deleteTag(id);
					updateView();
				} finally {
					todoStorage.close();
				}
			}
		});
		b.show();
	}

	private void addTag() {
		final EditText et = (EditText) findViewById(R.id.add_tag_text);
		final String st = et.getText().toString();
		if (st.length() > 0) {
			if (tagsStorage.hasTag(st)) {
				warnTagExists();
			} else {
				tagsStorage.addTag(st);
				et.setText("");
				et.requestFocus();
				updateView();
			}
		}
	}

	private void warnTagExists() {
		final AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle(android.R.string.dialog_alert_title);
		b.setMessage(R.string.tag_already_exists);
		b.show();
	}

	private void renameTag(final long id) {
		final LayoutInflater inf = LayoutInflater.from(this);
		final View textEntryView = inf.inflate(R.layout.alert_text_entry, null);
		final String currentName = tagsStorage.getTag(id);
		final EditText editText = (EditText) textEntryView.findViewById(R.id.text_entry);
		editText.setMaxLines(1);
		editText.setText(currentName);

		final AlertDialog.Builder b = new AlertDialog.Builder(this);
		b.setTitle(R.string.rename_tag_title);
		b.setMessage(R.string.new_tag_name_text);
		b.setCancelable(true);
		b.setView(textEntryView);
		b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialogInterface, final int i) {
				final String st = editText.getText().toString();
				if (st.length() > 0 && !currentName.equals(st)) {
					if (tagsStorage.hasTag(st))
						warnTagExists();
					else {
						tagsStorage.renameTag(id, st);
						updateView();
					}
				}
			}
		});

		dialog = b.create();
		Util.setupEditTextEnterListener(editText, dialog);
		dialog.show();
	}

	private void updateView() {
		allTagsCursor.requery();
	}

	@Override
	protected void onPause() {
		updateWidgetsIfNeeded();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		updateWidgetsIfNeeded();
		tagsStorage.close();
		super.onDestroy();
	}

	private void updateWidgetsIfNeeded() {
		if (tagsStorage.hasModifiedDB()) {
			UpdateService.requestUpdateAll(this);
			startService(new Intent(this, UpdateService.class));
			tagsStorage.resetModifiedDB();
		}
	}
}
