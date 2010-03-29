package com.kos.ktodo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import static com.kos.ktodo.DBHelper.*;

public class TodoItemsStorage {
	private static final String TAG = "TodoItemsStorage";

	private SQLiteDatabase db;
	private DBHelper helper;

	public TodoItemsStorage(final Context context) {
		helper = new DBHelper(context);
	}

	public void open() {
		db = helper.getWritableDatabase();
	}

	public void close() {
		helper.close();
	}

	public TodoItem addTodoItem(final TodoItem item) {
		final ContentValues cv = fillValues(item);
		final long id = db.insert(TODO_TABLE_NAME, null, cv);
		return new TodoItem(id, item.tagID, item.done, item.summary, item.body);
	}

	private ContentValues fillValues(final TodoItem item) {
		final ContentValues cv = new ContentValues();
		cv.put(TODO_TAG_ID, item.tagID);
		cv.put(TODO_DONE, item.done);
		cv.put(TODO_SUMMARY, item.summary);
		cv.put(TODO_BODY, item.body);
		return cv;
	}

	public boolean saveTodoItem(final TodoItem item) {
		final ContentValues cv = fillValues(item);
		return db.update(TODO_TABLE_NAME, cv, TODO_ID + "=" + item.id, null) > 0;
	}

	public void moveTodoItems(final long fromTag, final long toTag) {
		final ContentValues cv = new ContentValues();
		cv.put(TODO_TAG_ID, toTag);
		db.update(TODO_TABLE_NAME, cv, TODO_TAG_ID + "=" + fromTag, null);
	}

	public boolean deleteTodoItem(final long id) {
		return db.delete(TODO_TABLE_NAME, TODO_ID + "=" + id, null) > 0;
	}

	public int deleteByTag(final long tagID) {
		return db.delete(TODO_TABLE_NAME, TODO_TAG_ID + "=" + tagID, null);
	}

	public Cursor getByTagCursor(final long tagID) {
		return db.query(TODO_TABLE_NAME, new String[]{TODO_ID, TODO_DONE, TODO_BODY, TODO_SUMMARY},
				TODO_TAG_ID + "=" + tagID, null, null, null, null);
	}

	public TodoItem loadTodoItem(final long id) {
		final Cursor cursor = db.query(TODO_TABLE_NAME, new String[]{
				TODO_TAG_ID, TODO_DONE, TODO_SUMMARY, TODO_BODY},
				TODO_ID + "=" + id, null, null, null, null);
		TodoItem res = null;
		if (cursor.moveToFirst()) {
			res = new TodoItem(
					id,
					cursor.getInt(0),
					cursor.getInt(1) != 0,
					cursor.getString(2),
					cursor.getString(3)
			);
		}
		cursor.close();
		return res;
	}
}