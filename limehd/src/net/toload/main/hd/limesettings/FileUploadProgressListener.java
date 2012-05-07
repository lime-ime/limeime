package net.toload.main.hd.limesettings;

import java.util.Collection;
import java.util.HashMap;
import com.google.gdata.client.media.ResumableGDataFileUploader;
import com.google.gdata.client.uploader.ProgressListener;
import com.google.gdata.client.uploader.ResumableHttpFileUploader;
import com.google.gdata.data.docs.DocumentListEntry;

public class FileUploadProgressListener implements ProgressListener {

    private ResumableGDataFileUploader uploader;
    HashMap<String, DocumentListEntry> uploaded = new HashMap<String, DocumentListEntry>();// Maps.newHashMap();
    HashMap<String, String> failed = new HashMap<String, String>() ;//Maps.newHashMap();
    public FileUploadProgressListener() { }

    public void listenTo(ResumableGDataFileUploader target) {
      this.uploader = target;
    }

	@Override
    public void progressChanged(ResumableHttpFileUploader uploader)
    {
      //String fileId = ((FileUploadData) uploader.getData()).getFileName();
      /*switch(uploader.getUploadState()) {
        case COMPLETE:
        case CLIENT_ERROR:
          Log.d("LIME",fileId + ": Completed");
          break;
        case IN_PROGRESS:
        	Log.d("LIME",fileId + ":"
              + String.format("%3.0f", uploader.getProgress() * 100) + "%");
          break;
        case NOT_STARTED:
        	Log.d("LIME",fileId + ":" + "Not Started");
          break;
      }*/
    }

    public synchronized boolean isDone() {
      // check if all response streams are available.
      if (!uploader.isDone()) {
          return false;
      }else{
    	  return true;
      }
    }

    public synchronized Collection<DocumentListEntry> getUploaded() {
      if (!isDone()) {
        return null;
      }
      return uploaded.values();
    }


  }
