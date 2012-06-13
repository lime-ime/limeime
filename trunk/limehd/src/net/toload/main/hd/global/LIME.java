package net.toload.main.hd.global;

import android.os.Environment;

public class LIME {
	
	public static final String DAYI_DOWNLOAD_URL = "http://openvanilla.googlecode.com/svn/trunk/Modules/SharedData/dayi3.cin";
	
	// OpenFoundary
	public static final String CJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fcj.zip";
	public static final String SCJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fscj.zip";
	public static final String ARRAY_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Farray.zip";
	public static final String ARRAY10_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Farray10.zip";
	public static final String EZ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fez.zip";
	public static final String PHONETIC_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fphonetic.zip";
	public static final String PHONETICADV_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fphonetic_adv.zip";
	public static final String CJ5_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fcj5.zip";
	public static final String ECJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fecj.zip";
	public static final String WB_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fwb.zip";
	public static final String HS_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/hs.zip";
	public static final String IM_DOWNLOAD_TARGET_PRELOADED = "http://limeime.googlecode.com/svn/branches/database/lime1206.zip";
	//public static final String IM_DOWNLOAD_TARGET_PRELOADED = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Flime1109.zip";
	public static final String IM_DOWNLOAD_TARGET_EMPTY = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fempty1109.zip";
	//public static final String IM_DOWNLOAD_TARGET_PHONETIC_ONLY = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fphoneticonly1109.zip";
	public static final String IM_DOWNLOAD_TARGET_PHONETIC_ONLY = "http://limeime.googlecode.com/svn/branches/database/phoneticonly1206.zip";
	public static final String IM_DOWNLOAD_TARGET_PHONETIC_HS_ONLY = "http://limeime.googlecode.com/svn/branches/database/phonetichs1206.zip";
	//public static final String IM_DOWNLOAD_TARGET_PHONETIC_HS_ONLY = "http://limeime.googlecode.com/svn/branches/database/phonetichs0208.zip";
	
	// Google Code
	public static final String G_CJ_11643_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/cangjie.cin";
	public static final String G_PHONETIC_11643_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/bopomofo.cin";
	public static final String G_CJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/cj.zip";
	public static final String G_SCJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/scj.zip";
	public static final String G_ARRAY_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/array.zip";
	public static final String G_ARRAY10_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/array10.zip";
	public static final String G_EZ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/ez.zip";
	public static final String G_PHONETIC_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/phonetic.zip";
	public static final String G_PHONETICADV_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/phonetic_adv.zip";
	public static final String G_CJ5_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/cj5.zip";
	public static final String G_ECJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/ecj.zip";
	public static final String G_WB_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/wb.zip";
	public static final String G_HS_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/hs.zip";
	public static final String G_IM_DOWNLOAD_TARGET_PRELOADED = "http://limeime.googlecode.com/svn/branches/database/lime1206.zip";
	public static final String G_IM_DOWNLOAD_TARGET_EMPTY = "http://limeime.googlecode.com/svn/branches/database/empty1109.zip";
	public static final String G_IM_DOWNLOAD_TARGET_PHONETIC_ONLY = "http://limeime.googlecode.com/svn/branches/database/phoneticonly1206.zip";
	public static final String G_IM_DOWNLOAD_TARGET_PHONETIC_HS_ONLY = "http://limeime.googlecode.com/svn/branches/database/phonetichs1206.zip";
	
	public static final String IM_LOAD_LIME_ROOT_DIRECTORY = Environment.getExternalStorageDirectory() + "/limehd/";
	public static final String DOWNLOAD_START = "download_start";
	public static final String DATABASE_DOWNLOAD_STATUS = "database_download_status";
	public static final String DATABASE_NAME = "lime.db";
	public static final String DATABASE_SOURCE_DAYI = "dayi.cin";
	public static final String DATABASE_SOURCE_PHONETIC = "phonetic.lime";
	public static final String DATABASE_SOURCE_PHONETIC_CNS = "bopomofo.cin";
	public static final String DATABASE_SOURCE_PHONETICADV = "phonetic_adv.lime";
	public static final String DATABASE_SOURCE_CJ = "cj.lime";
	public static final String DATABASE_SOURCE_CJ_CNS = "cangjie.cin";
	public static final String DATABASE_SOURCE_CJ5 = "cj5.lime";
	public static final String DATABASE_SOURCE_ECJ = "ecj.lime";
	public static final String DATABASE_SOURCE_SCJ = "scj.lime";
	public static final String DATABASE_SOURCE_ARRAY = "array.lime";
	public static final String DATABASE_SOURCE_ARRAY10 = "array10.lime";
	public static final String DATABASE_SOURCE_HS = "hs.lime";
	public static final String DATABASE_SOURCE_WB = "stroke5.cin";
	public static final String DATABASE_SOURCE_EZ = "ez.lime";
	public static final String DATABASE_SOURCE_FILENAME = "lime.zip";
	public static final String DATABASE_SOURCE_FILENAME_EMPTY = "empty.zip";
	public static final String DATABASE_DECOMPRESS_FOLDER =  Environment.getDataDirectory() + "/data/net.toload.main.hd/databases";
	public static final String DATABASE_DECOMPRESS_FOLDER_SDCARD = Environment.getExternalStorageDirectory() + "/limehd/databases";
	public static final String DATABASE_BACKUP_NAME = "backup.zip";
	public static final String DATABASE_CLOUD_TEMP = "cloudtemp.zip";
	public static final String IM_CJ_STATUS = "im_cj_status";
	public static final String IM_SCJ_STATUS = "im_scj_status";
	public static final String IM_PHONETIC_STATUS = "im_phonetic_status";
	public static final String IM_DAYI_STATUS = "im_dayi_status";
	public static final String IM_CUSTOM_STATUS = "im_custom_status";
	public static final String IM_EZ_STATUS = "im_ez_status";
	
	public static final String IM_MAPPING_FILENAME = "im_mapping_filename";
	public static final String IM_MAPPING_VERSION = "im_mapping_version";
	public static final String IM_MAPPING_TOTAL = "im_mapping_total";
	public static final String IM_MAPPING_DATE = "im_mapping_date";

	public static final String CANDIDATE_SUGGESTION = "candidate_suggestion";
	public static final String TOTAL_USERDICT_RECORD = "total_userdict_record";
	public static final String LEARNING_SWITCH = "learning_switch";

	


	public final static String SEARCHSRV_RESET_CACHE = "searchsrv_reset_cache";
	public final static int SEARCHSRV_RESET_CACHE_SIZE = 500;
	public final static int LIMEDB_CACHE_SIZE = 1024;
	
}
