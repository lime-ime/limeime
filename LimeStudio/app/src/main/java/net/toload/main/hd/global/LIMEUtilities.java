/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.global;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.toload.main.hd.LIMEService;
import net.toload.main.hd.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

//

/**
 * @author jrywu
 *
 */
public class LIMEUtilities {
	static final String TAG = "LIMEUtilities";
	static final boolean DEBUG = false;

	public static boolean isUnicodeSurrogate(String word){  // emoji icons are within these surrogate areas
		if(word!=null && word.length()==2 ){
			char[] chArray = word.toCharArray();
			return Character.isSurrogatePair(chArray[0],chArray[1]);
		}
		return false;

	}

	/**
	 * Return the filepath if the file not exist in the target path
	 */
	public static File isFileNotExist(String filepath){
		
		File mfile = new File(filepath);
		if(mfile.exists())
			return null;
		else
			return mfile;
	}
	
	public static File isFileExist(String filepath){
		
		File mfile = new File(filepath);
		if(mfile.exists())
			return mfile;
		else
			return null;
	}

	/*
*   Zip single file into single zip.
*   sourceFile should be assigned with absolute path.
*
 */
	public static void zip(String zipFilePath, String sourceFile, Boolean OverWrite) 	throws Exception {
		zip(zipFilePath,sourceFile,"",OverWrite);
	}
	/*
	*   Zip singl file into single zip.
	*   sourceFile is specify relative to the baseFolderPath.
	*   sourceFile should be assigned with absolute path if baseFolderPath is null of empty
	*
 	*/
	public static void zip(String zipFilePath, String sourceFile, String baseFolderPath, Boolean OverWrite) 	throws Exception {
		List<String> sourceFileList = new ArrayList<>();
		sourceFileList.add(sourceFile);
		zip(zipFilePath,sourceFileList,baseFolderPath,OverWrite);
	}
	/*
	*   Zip multile files into single zip.
	*   sourceFiles should be assigned with absolute path.
	*
	 */
	public static void zip(String zipFilePath, List<String> sourceFiles, Boolean OverWrite) 	throws Exception {
		zip(zipFilePath, sourceFiles, "", OverWrite);
	}
	/*
	*   Zip multiple files into single zip.
	*   sourceFiles is specify relative to the baseFolderPath.
	*   sourceFiles should be assigned with absolute path if baseFolderPath is null of empty
	*
	 */
	public static void zip(String zipFilePath, List<String> sourceFiles, String baseFolderPath, Boolean OverWrite) 	throws Exception {
		File zipFile = new File(zipFilePath);
		if( zipFile.exists() && !OverWrite) return;
		else if(zipFile.exists() && OverWrite && !zipFile.delete()) Log.w(TAG,"Failed to delete existing zip file");

		ZipOutputStream zos;
		FileOutputStream outStream;
		outStream = new FileOutputStream(zipFile);
		zos = new ZipOutputStream(outStream);

		if(baseFolderPath == null) baseFolderPath="";

		for(String item : sourceFiles) {
			String itemName = (item.startsWith(File.separator) || baseFolderPath.endsWith(File.separator) )?item:(File.separator + item) ;

			if(baseFolderPath.isEmpty()) //absolute path
				addFileToZip(baseFolderPath + itemName, zos);
			else  //relative path
				addFileToZip(baseFolderPath + itemName, baseFolderPath, zos);


		}
		zos.flush();
		zos.close();

	}

	private static void addFileToZip(String sourceFilePath, String baseFolderPath, ZipOutputStream zos) throws Exception {
		addFileToZip("", sourceFilePath, baseFolderPath, zos);
	}
	private static void addFileToZip( String sourceFilePath, ZipOutputStream zos) throws Exception {
		addFileToZip("", sourceFilePath, "", zos);
	}

	private static void addFileToZip(String sourceFolderPath, String sourceFilePath, String baseFolderPath, ZipOutputStream zos) throws Exception {

		File item = new File(sourceFilePath);
        //if( item==null || !item.exists()) return; //skip if the file is not exist
		if (isSymLink(item)) return ; // do nothing to symbolic links.

		if(baseFolderPath == null) baseFolderPath = "";

		if (item.isDirectory()) {
			for (String subItem : Objects.requireNonNull(item.list())) {
				addFileToZip(sourceFolderPath + File.separator + item.getName(), sourceFilePath + File.separator  + subItem, baseFolderPath, zos);
			}
		} else {
			byte[] buf = new byte[LIME.BUFFER_SIZE_100KB];
			int len;
            try (FileInputStream inStream = new FileInputStream(sourceFilePath)) {

                String entryPath;
                if (baseFolderPath.isEmpty()) {
                    entryPath = sourceFilePath;
                } else {
                    entryPath = sourceFilePath.substring(baseFolderPath.length());
                }

                if (entryPath.startsWith(File.separator)) {
                    entryPath = entryPath.substring(1);
                }

                zos.putNextEntry(new ZipEntry(entryPath));

                while ((len = inStream.read(buf)) > 0) {
                    zos.write(buf, 0, len);
                }
            }

        }
	}

	public static boolean isSymLink(File filePath) throws IOException {
		if (filePath == null)
			throw new NullPointerException("filePath cannot be null");
		File canonical;
		if (filePath.getParent() == null) {
			canonical = filePath;
		} else {
			File canonDir = Objects.requireNonNull(filePath.getParentFile()).getCanonicalFile();
			canonical = new File(canonDir, filePath.getName());
		}
		return !canonical.getCanonicalFile().equals(canonical.getAbsoluteFile());
	}

	public static List<String> unzip(String zipFilePath, String targetFolder, Boolean OverWrite) throws IOException {
		return unzip(new File(zipFilePath), new File(targetFolder), OverWrite);
	}

    public static List<String> unzip(File zipFile,
                                     File targetDirectory,
                                     boolean overWrite) throws IOException {
        List<String> returnFilePaths = new ArrayList<>();

        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            throw new IOException("Failed to create target dir: " + targetDirectory.getAbsolutePath());
        }

        try (ZipFile zip4jFile = new ZipFile(zipFile)) {
            List<FileHeader> fileHeaders = zip4jFile.getFileHeaders();

            for (FileHeader fileHeader : fileHeaders) {
                String itemName = fileHeader.getFileName();

                if (itemName == null || itemName.isEmpty()) {
                    continue;
                }

                File targetFile;

                // Handle absolute /data/ paths: only restore into our package root
                if (itemName.startsWith("/data/")
                        || itemName.startsWith(Environment.getDataDirectory() + File.separator)) {

                    String packageRoot = LIME.getLimeDataRootFolder(); // e.g. /data/user/0/your.pkg
                    File abs = new File(itemName).getCanonicalFile();
                    String root = new File(packageRoot).getCanonicalPath() + File.separator;

                    // Skip if not under our package root
                    if (!abs.getPath().startsWith(root)) {
                        continue;
                    }

                    targetFile = abs;
                } else {
                    // Normal relative entry under targetDirectory
                    File out = new File(targetDirectory, itemName).getCanonicalFile();
                    String destRoot = targetDirectory.getCanonicalPath() + File.separator;

                    // Zip‑Slip guard: keep inside targetDirectory
                    if (!out.getPath().startsWith(destRoot)) {
                        continue;
                    }

                    targetFile = out;
                }

                // Create parent directories if they don't exist
                File parentDir = targetFile.getParentFile();
                if (parentDir != null && !parentDir.isDirectory() && !parentDir.mkdirs()) {
                    throw new IOException("Failed to ensure parent directory: " + parentDir.getAbsolutePath());
                }

                if (fileHeader.isDirectory()) {
                    if (!targetFile.isDirectory() && !targetFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + targetFile.getAbsolutePath());
                    }
                    returnFilePaths.add(targetFile.getAbsolutePath());
                    continue;
                }

                if (targetFile.exists()) {
                    if (!overWrite) {
                        returnFilePaths.add(targetFile.getAbsolutePath());
                        continue;
                    }
                    if (!targetFile.delete()) {
                        throw new IOException("Failed to delete existing file: " + targetFile);
                    }
                }

                // Extract the file using Zip4j
                zip4jFile.extractFile(fileHeader, targetDirectory.getAbsolutePath());
                returnFilePaths.add(targetFile.getAbsolutePath());
            }
        }

        return returnFilePaths;
    }


    public static boolean copyFile(String sourceFilePath, String targetFilePath, Boolean overWrite) {
		File sourceFile = isFileExist(sourceFilePath);
		if(sourceFilePath == null || sourceFile == null || targetFilePath == null) return false;
		File targetFile = isFileExist(targetFilePath);
		if(targetFile!=null && !overWrite ) return false;
		if(targetFile == null) targetFile = new File(targetFilePath);
		try{
			FileInputStream inStream = new FileInputStream(sourceFile);
			FileOutputStream outSteram = new FileOutputStream(targetFile);
			copyRAWFile(inStream, outSteram);
			return true;
		}
		catch(Exception e){
			Log.e(TAG, "Error copying file", e);
			return false;
		}

	}
	public static void copyRAWFile(InputStream	inStream, File newfile){
		try{
			FileOutputStream fs = new FileOutputStream(newfile);
			copyRAWFile(inStream, fs);
			fs.close();
		}
		catch(Exception e){      
    		Log.e(TAG, "Error copying raw file to new file", e);
           }   
	}
	public static void copyRAWFile(InputStream	inStream, FileOutputStream outStream){
	    	try{
	    		int bytesum=0, byteread;

	            byte[] buffer  = new byte[LIME.BUFFER_SIZE_100KB];
	            while((byteread = inStream.read(buffer))!=-1) {
                    bytesum += byteread;
                    System.out.println(bytesum);
                    outStream.write(buffer, 0, byteread);
                }
	            inStream.close();
				outStream.close();
	        	}   
	    	catch(Exception e){      
	    		Log.e(TAG, "Error copying raw file", e);
	           }   
	     }

	
	/** Add by Jeremy '12,4,23 Show notification with notification builder in compatibility package replacing the deprecated alert dialog creation 

	 */

	public static void showNotification(Context context, Boolean autoCancel,  CharSequence title, CharSequence message, Intent intent){

		NotificationManager mNotificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		String channelId = "lime_notification_channel";
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(
					channelId,
					"LIME Notifications",
					NotificationManager.IMPORTANCE_DEFAULT);
			mNotificationManager.createNotificationChannel(channel);
		}

		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(context, channelId) // Pass channel ID here
						.setLargeIcon(getNotificationIconBitmap(context))
						.setContentTitle(title)
						.setAutoCancel(autoCancel)
						.setTicker(message)
						.setContentText(message);


        mBuilder.setSmallIcon(R.drawable.logobw);


		mNotificationManager.notify(501, mBuilder.build());
	}

	private static int getNotificationIcon() {
		//boolean whiteIcon = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
		return R.drawable.logobw ;
	}

	private static Bitmap getNotificationIconBitmap(Context context) {

		return BitmapFactory.decodeResource(context.getResources(), R.drawable.logo);
	}

	
	public static String isVoiceSearchServiceExist(Context context){
		if(DEBUG) Log.i(TAG, "isVoiceSearchServiceExist()");
		
		InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		List<InputMethodInfo> mInputMethodProperties = imm.getEnabledInputMethodList();
	
		//boolean isVoiceSearchServiceEnabled = false;
		for (int i = 0; i < mInputMethodProperties.size(); i++) {
			InputMethodInfo imi = mInputMethodProperties.get(i);
			if(DEBUG) Log.i(TAG, "enabled IM " + i + ":" + imi.getId());

			if(imi.getId().equals("com.google.android.voicesearch/.ime.VoiceInputMethodService")){
				return "com.google.android.voicesearch/.ime.VoiceInputMethodService";
			}else if(imi.getId().equals("com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime.VoiceInputMethodService")){
				return "com.google.android.googlequicksearchbox/com.google.android.voicesearch.ime.VoiceInputMethodService";
			}
		}
		return null;
		
	}
	
	public static boolean isLIMEEnabled(Context context){
		InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		List<InputMethodInfo> mInputMethodProperties = imm.getEnabledInputMethodList();
		String limeID = getLIMEID(context);

		boolean isLIMEActive = false; 
		
		for (int i = 0; i < mInputMethodProperties.size(); i++) {
			InputMethodInfo imi = mInputMethodProperties.get(i);
			if(DEBUG) Log.i(TAG, "enabled IM " + i + ":" + imi.getId());
			if(imi.getId().equals(limeID)){
				isLIMEActive = true;
				break;
			}
		}
		return isLIMEActive;
	}

	public static boolean isLIMEActive(Context context){
		String activeIM = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD); 
		String limeID = getLIMEID(context);
		
		if(DEBUG) Log.i(TAG, "active IM:" + activeIM + " LIME IM:" + limeID);
		return activeIM.equals(limeID);
	}
	
	public static String getLIMEID(Context context){
		ComponentName LIMEComponentName = new ComponentName(context, LIMEService.class);
		return LIMEComponentName.flattenToShortString();
	}
	
	public static String getVoiceSearchIMId(Context context){
		ComponentName voiceInputComponent = 
				new ComponentName("com.google.android.voiceSearch", "com.google.android.voicesearch.ime.VoceInputMethdServce");
		if(DEBUG)
			Log.i(TAG, "getVoiceSearchIMId(), Comment name = "
					+ voiceInputComponent.flattenToString() + ", id = " 
					+ voiceInputComponent.flattenToShortString());
		return voiceInputComponent.flattenToShortString();
	}
	
	public static void showInputMethodSettingsPage(Context context){
		Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   	 	context.startActivity(intent);
	}
	public static void showInputMethodPicker(Context context){
		((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE)).showInputMethodPicker();
	}

    File getDbFolder(Context ctx)
    {
        return ctx.getDatabasePath(LIME.DATABASE_NAME).getParentFile();
    }

	/**
	 * Progress callback interface for download operations
	 */
	public interface DownloadProgressCallback {
		/**
		 * Called when download progress updates
		 * @param percent Progress percentage (0-100)
		 */
		void onProgress(int percent);
	}

	/**
	 * Abort flag supplier interface for download operations (API 21+ compatible)
	 * Replaces java.util.function.Supplier&lt;Boolean&gt; which requires API 24+
	 */
	public interface AbortFlagSupplier {
		/**
		 * Gets the current abort flag state
		 * @return true if download should be aborted, false otherwise
		 */
		boolean get();
	}

	/**
	 * Shared method to download a remote file.
	 * Supports both temporary file creation (for cache) and specific file creation (for persistent storage).
	 * 
	 * @param url The URL to download from
	 * @param targetFile The target file to write to (if null, creates temp file in cacheDir)
	 * @param cacheDir Context cache directory (required if targetFile is null)
	 * @param progressCallback Optional progress callback (can be null)
	 * @param abortFlagSupplier Optional supplier function that returns abort flag state (can be null)
	 * @return The downloaded file, or null if download failed
	 */
	public static File downloadRemoteFile(String url, File targetFile, File cacheDir, 
			DownloadProgressCallback progressCallback, AbortFlagSupplier abortFlagSupplier) {
		if(DEBUG)
			Log.i(TAG, "downloadRemoteFile() Starting: " + url);

		try {
			URL downloadUrl = new URL(url);
			URLConnection conn = downloadUrl.openConnection();
			conn.connect();
			InputStream is = conn.getInputStream();
			long remoteFileSize = conn.getContentLength();
			long downloadedSize = 0;

			if(DEBUG)
				Log.i(TAG, "downloadRemoteFile() contentLength: " + remoteFileSize);

			if(is == null){
				throw new RuntimeException("stream is null");
			}

			File downloadedFile;
			if (targetFile != null) {
				// Use specific target file
				File downloadFolder = targetFile.getParentFile();
				if (downloadFolder != null && !downloadFolder.exists()) {
					if (!downloadFolder.mkdirs()) {
						Log.w(TAG, "Failed to create target folder: " + downloadFolder.getAbsolutePath());
					}
				}
				downloadedFile = targetFile;
				if(downloadedFile.exists() && !downloadedFile.delete()) {
					Log.w(TAG, "Failed to delete existing downloadedFile");
				}
			} else {
				// Create temp file in cache directory
				if (cacheDir == null) {
					throw new IllegalArgumentException("cacheDir cannot be null when targetFile is null");
				}
				downloadedFile = File.createTempFile(LIME.DATABASE_IM_TEMP, LIME.DATABASE_IM_TEMP_EXT, cacheDir);
				downloadedFile.deleteOnExit();
			}

			// Use try-with-resources to ensure streams are closed even if exceptions occur
			try (FileOutputStream fos = new FileOutputStream(downloadedFile);
			     InputStream inputStream = is) {
				// Use 128KB buffer for better performance on modern devices
				byte[] buf = new byte[LIME.BUFFER_SIZE_128KB];
				do{
					// Check abort flag if provided
					if (abortFlagSupplier != null && abortFlagSupplier.get()) {
						if(DEBUG)
							Log.i(TAG, "downloadRemoteFile() aborted by user");
						break;
					}

					// InputStream.read() is already blocking and will wait for data
					int numread = inputStream.read(buf);
					if(numread <= 0) {
						break;
					}

					fos.write(buf, 0, numread);
					downloadedSize += numread; // Track actual bytes downloaded

					// Update progress if callback provided and size is known
					if (progressCallback != null && remoteFileSize > 0) {
						float percent = ((float)downloadedSize / (float)remoteFileSize) * 100;
						progressCallback.onProgress((int)percent);
					}

					if(DEBUG)
						Log.i(TAG, "downloadRemoteFile(), contentLength: " + remoteFileSize 
								+ ", downloadedSize: " + downloadedSize);
				} while(true);
			}

			return downloadedFile;

		} catch (FileNotFoundException e) {
			Log.e(TAG, "downloadRemoteFile() FileNotFoundException: can't open file for writing.", e);
		} catch (MalformedURLException e) {
			Log.e(TAG, "downloadRemoteFile() MalformedURLException: " + url, e);
		} catch (IOException e){
			Log.e(TAG, "downloadRemoteFile() IOException: " + e.getMessage(), e);
		} catch (Exception e){
			Log.e(TAG, "downloadRemoteFile() Exception: " + e.getMessage(), e);
		}
		if(DEBUG)
			Log.i(TAG, "downloadRemoteFile() failed.");
		return null;
	}


}
