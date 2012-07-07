package net.toload.main.hd.limedb;

import net.toload.main.hd.global.LIME;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

public class LimeCP extends ContentProvider {

	private LimeDBHelper dbhelper;
	
	private static final UriMatcher sUriMatcher;
	private static final int INCOMING_LIME_INDICATOR = 10;
	private static final int INCOMING_LIME_ITEM_INDICATOR = 11;
	
	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(LIME.AUTHORITY, "lime/*/#", INCOMING_LIME_INDICATOR);
		sUriMatcher.addURI(LIME.AUTHORITY, "lime/*", INCOMING_LIME_ITEM_INDICATOR);
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SQLiteDatabase db = dbhelper.getWritableDatabase();
		int count = 0;
		String table = "";
		switch (sUriMatcher.match(uri)) {
			case INCOMING_LIME_ITEM_INDICATOR:
				table = uri.getPathSegments().get(1);
				break;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
		count = db.delete(table, selection, null);
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}
	
	
	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
			case INCOMING_LIME_INDICATOR:
				return LIME.LIME_CONTENT_TYPE;
			default:
					throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		SQLiteDatabase db = dbhelper.getWritableDatabase();
		switch (sUriMatcher.match(uri)) {
			case INCOMING_LIME_ITEM_INDICATOR:
				db.insert(uri.getPathSegments().get(1), null, values);
				getContext().getContentResolver().notifyChange(uri, null);
				return uri;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public boolean onCreate() {
		dbhelper = new LimeDBHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		String table = "";
		int limit = 0;
		
		switch (sUriMatcher.match(uri)) {
			case INCOMING_LIME_INDICATOR:
				 table = uri.getPathSegments().get(1);
				 limit = Integer.parseInt(uri.getPathSegments().get(2));
				 break;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}

		SQLiteDatabase db = dbhelper.getReadableDatabase();
		//Cursor b = db.query(table, null, null, null, null, null, null);
		Cursor c = null;
		
		if(limit > 0){
			c = db.query(table, null, selection	, null, null, null, sortOrder, String.valueOf(limit));
		}else{
			c = db.query(table, null, selection	, null, null, null, sortOrder, null);
		}
		c.setNotificationUri(getContext().getContentResolver(), uri);
		
		//Log.i("LIME", "COUNT B:"+b.getCount());
		//Log.i("LIME", "COUNT C:"+c.getCount());
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		SQLiteDatabase db = dbhelper.getWritableDatabase();
		int count = 0;
		String table = "";
		switch (sUriMatcher.match(uri)) {
			case INCOMING_LIME_ITEM_INDICATOR:
				table = uri.getPathSegments().get(1);
				break;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
		count = db.update(table, values, selection, null);
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

}
