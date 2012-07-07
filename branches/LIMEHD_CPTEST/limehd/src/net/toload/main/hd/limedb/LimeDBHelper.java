package net.toload.main.hd.limedb;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.activation.MimetypesFileTypeMap;

import net.toload.main.hd.global.LIME;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class LimeDBHelper extends SQLiteOpenHelper {


	private final static int DATABASE_VERSION = 77;
	
	LimeDBHelper(Context context) {
		super(context, LIME.DATABASE_NAME,null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {
	}
	
	
}
