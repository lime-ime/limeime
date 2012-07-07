package net.toload.main.hd.limedb;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.activation.MimetypesFileTypeMap;

import net.toload.main.hd.global.LIME;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public class LimeCPHelper {

	
	public static Cursor query(Context ctx, String table, String[] projection, String selection, String sortorder, String limit){
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = LIME.LIME_CONTENT_URI;
		if(limit == null){
			limit = "0";
		}
		Uri queryUri = Uri.withAppendedPath(uri, table+"/"+ limit);
		Cursor cursor = cr.query(queryUri, projection, selection, null, sortorder);
		return cursor;
	}
	

	public static void update(Context ctx, String table, ContentValues cv, String selection){
		ContentResolver cr = ctx.getContentResolver();

		Uri uri = LIME.LIME_CONTENT_URI;
		Uri updateUri = Uri.withAppendedPath(uri, table);
		cr.update(updateUri, cv, selection, null);
	}


	public static void insert(Context ctx, String table, ContentValues values){
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = LIME.LIME_CONTENT_URI;
		Uri insertUri = Uri.withAppendedPath(uri, table);
		cr.insert(insertUri, values);
	}

	public static void delete(Context ctx, String table, String selection){
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = LIME.LIME_CONTENT_URI;
		Uri resetUri = Uri.withAppendedPath(uri, table);
		cr.delete(resetUri, selection, null);
	}

	public static void reset(Context ctx, String table){
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = LIME.LIME_CONTENT_URI;
		Uri resetUri = Uri.withAppendedPath(uri, table);
		cr.delete(resetUri, null, null);
	}
	
	
	/*private static MimetypesFileTypeMap mimetype = new MimetypesFileTypeMap();

	public static void resetCloudTable(Context ctx){
		if(ctx == null){ return; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		Uri delUri = Uri.withAppendedPath(uri, "reset");
		cr.delete(delUri, null, null);
	}
	
	public static void removeCloudItemByResourceid(Context ctx, String resourceid){
		if(ctx == null){ return; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		Uri delUri = Uri.withAppendedPath(uri, "id/"+resourceid);
		cr.delete(delUri, null, null);
	}
	
	public static void removeSyncItemByResourceId(Context ctx, String resourceid){
		if(ctx == null){ return; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri delUri = Uri.withAppendedPath(uri, "id/"+resourceid);
		cr.delete(delUri, null, null);
	}
	public static void removeSyncItem(Context ctx, int no){
		if(ctx == null){ return; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri delUri = Uri.withAppendedPath(uri, Integer.toString(no));
		cr.delete(delUri, null, null);
	}
	
	public static void removeSyncItem(Context ctx, List<FileItem> items){
		if(ctx == null){ return; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		for(FileItem i: items){
			Uri delUri = Uri.withAppendedPath(uri, Integer.toString(i.getNo()));
			cr.delete(delUri, null, null);
		}
	}

	public static void removeRestoreSync(Context ctx, String resourceid){
		if(ctx == null){ return; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri delUri = Uri.withAppendedPath(uri, "id/"+resourceid);
		cr.delete(delUri, null, null);
	}

	public static void removeBackupSync(Context ctx, String path){
		if(ctx == null){ return; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri delUri = Uri.withAppendedPath(uri, "path/"+Uri.encode(path));
		cr.delete(delUri, null, null);
	}
	
	
	public static int countSyncItem(Context ctx, int source, int control){
		if(ctx == null){ return 0; }
		ContentResolver cr = ctx.getContentResolver();
		
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, Integer.toString(source) + "/" + Integer.toString(control));
		
		List<FileItem> list = new ArrayList<FileItem>();
		Cursor cursor = cr.query(queryUri, null, null, null, null);
		int count = cursor.getCount();
			   cursor.close();
			   
	    return count;
	}

	
	public static List<FileItem> lsSyncItem(Context ctx, int source, int control, int sort){
		List<FileItem> list = new ArrayList<FileItem>();
		if(ctx == null){ return list; }
		ContentResolver cr = ctx.getContentResolver();
		
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, Integer.toString(source) + "/" + Integer.toString(control));
		
		Cursor cursor = cr.query(queryUri, null, null, null, null);
		for(cursor.moveToFirst() ; !cursor.isAfterLast() ; cursor.moveToNext()) {
			
			FileItem item = new FileItem();
			 item.setNo(getColumnInt(cursor, "no"));
			 
		     item.setSynccontrol(getColumnInt(cursor, GFBG.DATABASE_SYNC_CONTROL));
		     item.setResourceid(getColumnString(cursor, GFBG.DATABASE_SYNC_RESOURCEID));
		     item.setSynctype(getColumnInt(cursor, GFBG.DATABASE_SYNC_SYNCTYPE));

		     item.setSourcepath(getColumnString(cursor, GFBG.DATABASE_SYNC_PATH));
		     item.setFilename(getColumnString(cursor, GFBG.DATABASE_SYNC_FILENAME));
		     item.setProgress(getColumnInt(cursor, GFBG.DATABASE_SYNC_PROGRESS));
		     item.setType(getColumnInt(cursor, GFBG.DATABASE_SYNC_TYPE));
		     item.setTarget(getColumnString(cursor, GFBG.DATABASE_SYNC_TARGETPATH));
		     item.setLength(Long.parseLong(getColumnString(cursor, GFBG.DATABASE_SYNC_LENGTH)));
		     
		     if((new File(GFBG.DATABASE_SYNC_SOURCEFOLDER)).getPath() != null &&
		    		 (new File(GFBG.DATABASE_SYNC_SOURCEFOLDER)).getPath().length() == 1){
			     item.setAbsolutepath("");
		     }else{
		    	 String path = getColumnString(cursor, GFBG.DATABASE_SYNC_SOURCEFOLDER);
		    	 if(path != null && path.indexOf(item.getFilename()) != -1){
				     item.setAbsolutepath(path.substring(0, path.lastIndexOf("/")));
		    	 }else if(path == null){
		    		 item.setAbsolutepath("");
		    	 }else{
				     item.setAbsolutepath(path);
		    	 }
		     }
		     item.setLastmodified((Long.parseLong(getColumnString(cursor, GFBG.DATABASE_SYNC_LASTMODIFIED))) );
		
			 list.add(item);
		}
		
		cursor.close();
		
		if(sort != 0){
			if(sort == GFBG.SORT_DESC){
				Collections.sort(list, SORT_MODIFIED_DATE);
			}else if(sort == GFBG.SORT_ASC){
				Collections.sort(list, SORT_MODIFIED_DATE);
				Collections.reverse(list);
			}
		}
		
		return list;
	}

	public static void updateDbCloudItemLength(Context ctx, String resourceid, long length){
		if(ctx == null){ return ; }
		ContentResolver cr = ctx.getContentResolver();
		
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		Uri updateUri = Uri.withAppendedPath(uri, "id/"+resourceid);

		ContentValues values = new ContentValues();
					  values.put(GFBG.DATABASE_CLOUD_LENGTH, length);
		cr.update(updateUri, values, null, null);
	}
	
    public static void updateSyncControl(Context ctx, int no, int type){
		if(ctx == null){ return ; }
		ContentResolver cr = ctx.getContentResolver();
		
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri updateUri = Uri.withAppendedPath(uri, Integer.toString(no));

		ContentValues values = new ContentValues();
					  values.put(GFBG.DATABASE_SYNC_CONTROL, type);
		cr.update(updateUri, values, null, null);
	}


    public static void updateSyncProgress(Context ctx, int no, int progress){
		if(ctx == null){ return ; }
		ContentResolver cr = ctx.getContentResolver();
		
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri updateUri = Uri.withAppendedPath(uri, Integer.toString(no));

		ContentValues values = new ContentValues();
					  
					  if(progress < 100){
						  	values.put(GFBG.DATABASE_SYNC_CONTROL, GFBG.SYNC_CONTROL_ACTIVE);
				      }else if(progress >= 100){
				    		progress = 100;
				    		values.put(GFBG.DATABASE_SYNC_CONTROL, GFBG.SYNC_CONTROL_COMPLETE);
				      }
					  values.put(GFBG.DATABASE_SYNC_PROGRESS, progress);
						
		cr.update(updateUri, values, null, null);
	}
    
    
	public static int countByItemControl(Context ctx, int source, int control){
		if(ctx == null){ return 0; }
		ContentResolver cr = ctx.getContentResolver();
		
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, Integer.toString(source) + "/" + Integer.toString(control));
		
		List<FileItem> list = new ArrayList<FileItem>();
		Cursor cursor = cr.query(queryUri, null, null, null, null);
		int count = cursor.getCount();
		cursor.close();
		return count;
	}
	
	public static List<FileItem> ls(Context ctx, String parentid, int sort){
			List<FileItem> list = new ArrayList<FileItem>();
			if(ctx == null){ return list; }
	    	return ls(ctx, parentid,0 , sort);
	}
		
	public static List<FileItem> ls(Context ctx, String parentid,  int type, int sort){ 
		
		if(ctx == null){ return null; }
		
		ContentResolver cr = ctx.getContentResolver();
			
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, parentid);
			
		List<FileItem> list = new ArrayList<FileItem>();
		Cursor cursor = cr.query(queryUri, null, null, null, null);
			
		for(cursor.moveToFirst() ; !cursor.isAfterLast() ; cursor.moveToNext()) {
			FileItem item = getCloudFileItem(cursor);
			 if(item.getType() == GFBG.TYPE_FOLDER){
				item.setFolder(true);
				item.setAmount(count(ctx, item.getResourceid()));
			 }else{
				item.setFolder(false);
			 }
			 
			if(type != 0){
				if(item.getType() == type){
					list.add(item);
				}
			}else{
				list.add(item);
			}
		}
		cursor.close();
		
		if(sort != 0){
			List<FileItem> folders = new ArrayList<FileItem>();
			List<FileItem> files = new ArrayList<FileItem>();
			for(FileItem f: list){
				if(f.isFolder()){
					folders.add(f);
				}else{
					files.add(f);
				}
			}
			
			if(sort == GFBG.SORT_DESC){
				list.clear();
				Collections.sort(files, SORT_FILENAME);
				list.addAll(files);
				Collections.sort(folders, SORT_FILENAME);
				list.addAll(folders);
			}else if(sort == GFBG.SORT_ASC){
				list.clear();
				Collections.sort(folders, SORT_FILENAME);
				Collections.reverse(folders);
				list.addAll(folders);
				Collections.sort(files, SORT_FILENAME);
				Collections.reverse(files);
				list.addAll(files);
			}
		}
			
		return list;
	}

	public static void addSyncItem(Context ctx, List<FileItem> items, String target, HashMap<String, Object> setting, boolean showHidden){
		if(ctx == null){ return ; }
		addSyncItem(ctx, items, target, setting, null, showHidden);
	}
	
	public static void addSyncItem(Context ctx, List<FileItem> items, String target, HashMap<String, Object> setting,  String sourcefolder, boolean showHidden){

		if(ctx == null){ return ; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		
		for(FileItem item : items){
			Calendar cal = Calendar.getInstance();
		
			if(item.isFolder()){
				if(item.getResourceid() != null){
					List<FileItem> templist = ls(ctx, item.getResourceid(), 0);
					String foldername = "";
					if(sourcefolder != null){
						foldername = sourcefolder+"/"+ item.getTitle();
					}else{
						foldername = "/"+ item.getTitle();
					}
					addSyncItem(ctx, templist, target, setting, foldername, showHidden);
				}else{
					File tempfile = new File(item.getAbsolutepath());
					List<FileItem> templist = FileManager.ls(tempfile, 0, showHidden);
					String foldername = "";
					if(sourcefolder != null){
						foldername = sourcefolder+"/"+ tempfile.getName();
					}else{
						foldername = "/"+ tempfile.getName();
					}
					addSyncItem(ctx, templist, target, setting, foldername, showHidden);
				}
			}else{
				ContentValues values = new ContentValues();
				
					if(item.getResourceid() != null){
						if(isResotreSyncExists(ctx, item.getResourceid())){
							continue;
						}
						values.put("resourceid", item.getResourceid());
						values.put("filename", item.getTitle());
						values.put("source_path", item.getParentid() + ", " + item.getResourceid());
						values.put("source_type", GFBG.SYNC_SOURCE_CLOUD);
					}else{
						if(isBackupSyncExists(ctx, item.getAbsolutepath())){
							continue;
						}
						values.put("filename", item.getFilename());
						values.put("source_path", item.getAbsolutepath());
						values.put("source_type", GFBG.SYNC_SOURCE_DEVICE);
					}
	
					values.put("target_path", target);
					values.put("type", item.getType());
							
					if(item.getDescription() != null){
						String[] fields = item.getDescription().split(",");
						try{
							Long lvalue = Long.parseLong(fields[0].trim());
							String lpath = fields[1].trim();
							values.put("description", lvalue + ", "+lpath);
						}catch(Exception e){
							e.printStackTrace();
						}
					}
					
					values.put("length", item.getLength());
					values.put("sync_type", String.valueOf((Integer)setting.get(GFBG.SYNC_TYPE)));
					values.put("progress", 0);
					values.put("sync_control", GFBG.SYNC_CONTROL_PENDING);
					values.put("last_modified", String.valueOf(cal.getTimeInMillis()));
					if(sourcefolder != null && !sourcefolder.equals("")){
						values.put("source_folder", sourcefolder);
					}
	
				cr.insert(uri, values);
			}
		}
	}
	
	
	public static void addCloudItem(Context ctx, List<FileItem> items){
		if(ctx == null){ return ; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		for(FileItem item : items){
			if(item.getResourceid() == null || isCloudExists(ctx, item.getResourceid())){
				continue;
			}
			String url = "https://docs.google.com/feeds/default/private/full/"+item.getResourceid();
			Calendar cal = Calendar.getInstance();
					
			ContentValues values = new ContentValues();
						  values.put("resourceid", item.getResourceid());
						  values.put("parentid", item.getParentid());
						  values.put("filename", item.getFilename());
						  values.put("title", item.getTitle());
						  values.put("description", item.getDescription());
						  values.put("source_path", url);
						  values.put("device_path", item.getAbsolutepath());
						  values.put("type", item.getType());
						  values.put("length", item.getLength());
						  values.put("last_synced", cal.getTimeInMillis());
						  values.put("last_modified", item.getLastmodified());
						  values.put("canread", item.isReadable()?1:0);
						  values.put("canwrite", item.isWritable()?1:0);
			
			cr.insert(uri, values);
		}
		
	}
	
	public static void removeDbCloudFolder(Context ctx, String parentid){
		if(ctx == null){ return ; }
		ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, parentid);
		
		List<Integer> removeObjects = new ArrayList<Integer>();
		List<FileItem> list = new ArrayList<FileItem>();
		Cursor cursor = cr.query(queryUri, null, null, null, null);
		
		for(cursor.moveToFirst() ; !cursor.isAfterLast() ; cursor.moveToNext()) {
			removeObjects.add(getColumnInt(cursor, "no"));
		}
		cursor.close();
		for(Integer i: removeObjects){
			Uri delUri = Uri.withAppendedPath(uri, Integer.toString(i));
			cr.delete(delUri, null, null);
		}
	}

    public static String getColumnString(Cursor cc, String cname){
	    int i = cc.getColumnIndex(cname);
	    return cc.getString(i);
    }
    
    public static int getColumnInt(Cursor cc, String cname){
	    int i = cc.getColumnIndex(cname);
	    return cc.getInt(i);
    }

	static final Comparator<FileItem> SORT_MODIFIED_DATE = new Comparator<FileItem>() {
	    public int compare(FileItem e1, FileItem e2) {
	    		Long a = new Long(e1.getLastmodified());
	    		Long b = new Long(e2.getLastmodified());
	    		return a.compareTo(b);
		}
    };
    
	static final Comparator<FileItem> SORT_FILENAME = new Comparator<FileItem>() {
	    public int compare(FileItem e1, FileItem e2) {
	    		return e2.getFilename().compareTo(e1.getFilename());
		}
    };
    
    public static FileItem getCloudFileItem(Cursor cursor){
    	FileItem item = new FileItem();
		 item.setNo(getColumnInt(cursor, "no"));
	     item.setLastmodified(Long.parseLong(getColumnString(cursor, GFBG.DATABASE_CLOUD_LAST_MODIFIED)));
	     item.setAbsolutepath(getColumnString(cursor, GFBG.DATABASE_CLOUD_DEVICE_PATH));
	     item.setFilename(getColumnString(cursor, GFBG.DATABASE_CLOUD_TITLE));
	     item.setLength(Long.parseLong(getColumnString(cursor, GFBG.DATABASE_CLOUD_LENGTH)));

	     item.setResourceid(getColumnString(cursor, GFBG.DATABASE_CLOUD_RESOURCEID));
	     item.setEtag(getColumnString(cursor, GFBG.DATABASE_CLOUD_ETAG));
	     item.setParentid(getColumnString(cursor, GFBG.DATABASE_CLOUD_PARENTID));
	     item.setTitle(getColumnString(cursor, GFBG.DATABASE_CLOUD_TITLE));
	     item.setDescription(getColumnString(cursor, GFBG.DATABASE_CLOUD_DESCRIPTION));
	     item.setLastsynced(Long.parseLong(getColumnString(cursor, GFBG.DATABASE_CLOUD_LAST_SYNCED)));
	     item.setSourcepath(getColumnString(cursor, GFBG.DATABASE_CLOUD_SOURCE_PATH));
	     item.setType(getColumnInt(cursor, GFBG.DATABASE_CLOUD_TYPE));
		 item.setMimetype(mimetype.getContentType(item.getFilename()));
		 item.setReadablesize(FileManager.readableFileSize(item.getLength()));
		 item.setReadable(getColumnInt(cursor, GFBG.DATABASE_CLOUD_CANREAD)==1?true:false);
		 item.setWritable(getColumnInt(cursor, GFBG.DATABASE_CLOUD_CANWRITE)==1?true:false);
		 return item;
    }


    public static boolean isCloudEmpty(Context ctx){
    	ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, "root");
			
		List<FileItem> list = new ArrayList<FileItem>();
		Cursor cursor = cr.query(queryUri, null, null, null, null);
		if(cursor.getCount() > 0){
			cursor.close();
			return false;
		}else{
			cursor.close();
			return true;
		}
	}
    
    public static int count(Context ctx, String parentid){
		if(ctx == null){ return 0; }
    	ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, parentid);
			
		List<FileItem> list = new ArrayList<FileItem>();
		Cursor cursor = cr.query(queryUri, null, null, null, null);

		int count = cursor.getCount();
		cursor.close();
		return count;
	}
    
    public static boolean isCloudExists(Context ctx, String resourceid){
		if(ctx == null){ return false; }
    	ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, "id/"+resourceid);
			
		List<FileItem> list = new ArrayList<FileItem>();
		Cursor cursor = cr.query(queryUri, null, null, null, null);
		if(cursor.getCount() > 0){
			cursor.close();
			return true;
		}else{
			cursor.close();
			return false;
		}
    }
     
    public static boolean isBackupSyncExists(Context ctx, String path){
		if(ctx == null){ return false; }
    	ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, "path/"+Uri.encode(path));
			
		List<FileItem> list = new ArrayList<FileItem>();
		Cursor cursor = cr.query(queryUri, null, null, null, null);
		if(cursor.getCount() > 0){
			cursor.close();
			return true;
		}else{
			cursor.close();
			return false;
		}
    }
    
    public static boolean isResotreSyncExists(Context ctx, String resourceid){
		if(ctx == null){ return false; }
    	ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.SYNCITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, "id/"+resourceid);
			
		List<FileItem> list = new ArrayList<FileItem>();
		Cursor cursor = cr.query(queryUri, null, null, null, null);
		if(cursor.getCount() > 0){
			cursor.close();
			return true;
		}else{
			cursor.close();
			return false;
		}
    }
    
    public static FileItem getFileItem(Context ctx, String resourceid){
		if(ctx == null){ return new FileItem(); }

    	ContentResolver cr = ctx.getContentResolver();
		Uri uri = GFBG.CLOUDITEM_CONTENT_URI;
		Uri queryUri = Uri.withAppendedPath(uri, "id/"+resourceid);

		Cursor cursor = cr.query(queryUri, null, null, null, null);
			
		if(cursor.getCount() > 0){
			cursor.moveToFirst();
			FileItem item = new FileItem();
			 item.setNo(getColumnInt(cursor, "no"));
		     item.setLastmodified(Long.parseLong(getColumnString(cursor, GFBG.DATABASE_CLOUD_LAST_MODIFIED)));
		     item.setAbsolutepath(getColumnString(cursor, GFBG.DATABASE_CLOUD_DEVICE_PATH));
		     item.setFilename(getColumnString(cursor, GFBG.DATABASE_CLOUD_TITLE));
		     item.setLength(Long.parseLong(getColumnString(cursor, GFBG.DATABASE_CLOUD_LENGTH)));
		     item.setResourceid(getColumnString(cursor, GFBG.DATABASE_CLOUD_RESOURCEID));
		     item.setParentid(getColumnString(cursor, GFBG.DATABASE_CLOUD_PARENTID));
		     item.setTitle(getColumnString(cursor, GFBG.DATABASE_CLOUD_TITLE));
		     item.setDescription(getColumnString(cursor, GFBG.DATABASE_CLOUD_DESCRIPTION));
		     item.setLastsynced(Long.parseLong(getColumnString(cursor, GFBG.DATABASE_CLOUD_LAST_SYNCED)));
		     item.setSourcepath(getColumnString(cursor, GFBG.DATABASE_CLOUD_SOURCE_PATH));
		     item.setType(getColumnInt(cursor, GFBG.DATABASE_CLOUD_TYPE));
			 item.setMimetype(mimetype.getContentType(item.getFilename()));
			 item.setReadablesize(FileManager.readableFileSize(item.getLength()));
			 if(item.getType() == GFBG.TYPE_FOLDER){
				item.setFolder(true);
				item.setAmount(count(ctx, item.getResourceid()));
			 }else{
				item.setFolder(false);
			 }
			 item.setReadable(getColumnInt(cursor, GFBG.DATABASE_CLOUD_CANREAD)==1?true:false);
			 item.setWritable(getColumnInt(cursor, GFBG.DATABASE_CLOUD_CANWRITE)==1?true:false);
			 return item;
		}
		cursor.close();
		return null;
	}
    

	public static List<FileItem> getFileItemFromParent(Context ctx, List<FileItem> items, HashMap check) {
		
		List<FileItem> result = new ArrayList<FileItem>();
		if(ctx == null){ return result; }
		
		long size = 0;
		if(check == null){
			check = new HashMap<String,String>();
		}
		
		for(FileItem item : items){
			Calendar cal = Calendar.getInstance();
		
			if(item.isFolder()){
				List<FileItem> templist = ls(ctx, item.getResourceid(), 0);
				result.add(item);
				result.addAll(getFileItemFromParent(ctx, templist, check));
			}else{
				if(check.get(item.getResourceid()) == null){
					result.add(item);
					check.put(item.getResourceid(), item.getResourceid());
				}
			}
		}
		return result;
	}

	public static long countCloudFileAmount(Context ctx, List<FileItem> items, HashMap check) {
		if(ctx == null){ return 0; }
		
		long size = 0;
		if(check == null){
			check = new HashMap<String,String>();
		}
		
		for(FileItem item : items){
			Calendar cal = Calendar.getInstance();
		
			if(item.isFolder()){
				List<FileItem> templist = ls(ctx, item.getResourceid(), 0);
				size += countCloudFileAmount(ctx, templist, check);
			}else{
				if(check.get(item.getResourceid()) == null){
					size ++;
					check.put(item.getResourceid(), item.getResourceid());
				}
			}
		}
		return size;
	}
	
	public static String getCloudFolderPath(Context ctx, String value){
		if(ctx == null){ return ""; }
		String result = "";
		if(value.equals("root")){
			result = "/";
		}else{
			String dir = "";
			List<String> dirs = new ArrayList<String>();
			FileItem target = getFileItem(ctx, value);
			do{
				dirs.add(target.getTitle());
				target = getFileItem(ctx, target.getParentid());
			}while(target != null);
			
			for(int i = (dirs.size()-1); i >= 0; i--){
				dir += "/"+dirs.get(i);
			}
			result = dir;
		}
		return result;
	}
	
	public static String getCloudPathFull(Context ctx, String resourceid){
		if(ctx == null){ return ""; }
		String dir = "";
		List<String> dirs = new ArrayList<String>();
		FileItem target = getFileItem(ctx, resourceid);
		do{
			dirs.add(target.getTitle());
			target = getFileItem(ctx, target.getParentid());
		}while(target != null);
		
		for(int i = (dirs.size()-1); i >= 0; i--){
			dir += "/"+dirs.get(i);
		}
		return dir;
	}
	
	
	public static FileItem getFolderFileItem(Context ctx, String parentid, String filename){
		if(ctx == null){ return new FileItem(); }
		List<FileItem> templist = ls(ctx, parentid, 0);
		for(FileItem i : templist){
			if(i.getFilename().equals(filename)){
				return i;
			}
		}
		return null;
	}
	
	public static FileItem getChildFolder(Context ctx, String parentid, String title){
		if(ctx == null){ return new FileItem(); }
		List<FileItem> templist = ls(ctx, parentid, 0);
		for(FileItem i : templist){
			if(i.getTitle().equals(title)){
				return i;
			}
		}
		return null;
	}

	public static List<FileItem> search(Context ctx, String parentid, String keyword){
		List<FileItem> result = new ArrayList<FileItem>();
		if(ctx == null){ return result; }
		
		List<FileItem> temp = ls(ctx, parentid, GFBG.SORT_ASC);
		List<FileItem> results = new ArrayList<FileItem>();
		
		for(FileItem i : temp){
			if(i.isFolder()){
				results.addAll(search(ctx, i.getResourceid(), keyword));
			}else{
				if(i.getTitle().toLowerCase().indexOf(keyword.toLowerCase()) != -1){
					if(i.isReadable()){
						results.add(i);
					}
				}
			}
		}
		
		return results;
	}
	
	public static List<FileItem> sort(List<FileItem> list, int sort){
		
		List<FileItem> folders = new ArrayList<FileItem>();
		List<FileItem> files = new ArrayList<FileItem>();
		for(FileItem f: list){
			if(f.isFolder()){
				folders.add(f);
			}else{
				files.add(f);
			}
		}
		
		if(sort == GFBG.SORT_DESC){
			list.clear();
			Collections.sort(files, SORT_FILENAME);
			list.addAll(files);
			Collections.sort(folders, SORT_FILENAME);
			list.addAll(folders);
		}else if(sort == GFBG.SORT_ASC){
			list.clear();
			Collections.sort(folders, SORT_FILENAME);
			Collections.reverse(folders);
			list.addAll(folders);
			Collections.sort(files, SORT_FILENAME);
			Collections.reverse(files);
			list.addAll(files);
		}
		return list;
	}*/
	
}
