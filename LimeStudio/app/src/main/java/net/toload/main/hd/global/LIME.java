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

import android.os.Environment;
import android.util.Log;

import java.text.DecimalFormat;

/**
 * Global constants and utility methods for LimeIME.
 * Merged from Lime.java and LIME.java.
 */
public class LIME {
	public static String PACKAGE_NAME;
	
	// Database Settings
	public static final String DATABASE_NAME = "lime.db";
	public static final String DATABASE_EXT = ".db";
	public static final String DATABASE_JOURNAL = "lime.db-journal";
	public static final String DATABASE_BACKUP_NAME = "backup.zip";
	public static final String DATABASE_JOURNAL_BACKUP_NAME = "backupJournal.zip";
	public static final String SHARED_PREFS_BACKUP_NAME = "shared_prefs.bak";
	public static final String DATABASE_CLOUD_TEMP = "cloudtemp.zip";
	
	public static String getLimeDataRootFolder() {
		return Environment.getDataDirectory() + "/data/" + LIME.PACKAGE_NAME;
	}
	
	// Download URLs - OpenFoundry
	public static final String DATABASE_OPENFOUNDRY_URL_BASED = "https://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2F";
	public static final String DATABASE_CLOUD_URL_BASED = "https://github.com/lime-ime/limeime/raw/master/Database/";
	
	// Special Version CJK Mapping Table Provided By Julian
	public static final String CJK_CJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fcj_CJK.lime.zip";
	public static final String CJK_ECJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fecj_CJK.lime.zip";
	public static final String CJK_PHONETIC_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fphonetic_CJK.lime.zip";
	public static final String CJK_PHONETICADV_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fphonetic_CJK.lime.zip";
	public static final String CJK_PINYIN_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fpinyin_CJK.cin.zip";
	public static final String CJK_HK_CJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fcj_CJK_HKSCS.lime.zip";
	public static final String CJK_HK_ECJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fecj_CJK_HKSCS.lime.zip";
	
	// Google Code URLs
	public static final String G_CJK_CJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/cj_CJK.lime";
	public static final String G_CJK_ECJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/ecj_CJK.lime";
	public static final String G_CJK_PHONETIC_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/phonetic_CJK.lime";
	public static final String G_CJK_PHONETICADV_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/phonetic_adv_CJK.lime";
	public static final String G_CJK_PINYIN_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/pinyin_CJK.cin";
	public static final String G_CJK_HK_CJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/cj_CJK_HKSCS.lime";
	public static final String G_CJK_HK_ECJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/ecj_CJK_HKSCS.lime";
	
	// OV CIN files download URL
	public static final String DAYI_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/dayi3.cin";
	public static final String PINYI_TW_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/pinyinbig5.cin";
	public static final String PINYI_CN_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/pinyin.cin";
	
	// OpenFoundry Download URLs
	public static final String CJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fcj.zip";
	public static final String SCJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fscj.zip";
	public static final String ARRAY_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Farray.zip";
	public static final String ARRAY10_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Farray10.zip";
	public static final String EZ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fez.zip";
	public static final String PHONETIC_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fphonetic.zip";
	public static final String PHONETICADV_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fphonetic_adv_CJK.zip";
	public static final String CJ5_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fcj5.zip";
	public static final String ECJ_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fecj.zip";
	public static final String WB_DOWNLOAD_URL = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fwb.zip";
	
	// Google Code Download URLs
	public static final String G_CJ_11643_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/cangjie.cin";
	public static final String G_PHONETIC_11643_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/bopomofo.cin";
	public static final String G_CJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/cj.zip";
	public static final String G_SCJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/scj.zip";
	public static final String G_ARRAY_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/array.zip";
	public static final String G_ARRAY10_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/array10.zip";
	public static final String G_EZ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/ez.zip";
	public static final String G_PHONETIC_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/phonetic.zip";
	public static final String G_PHONETICADV_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/phonetic_adv_CJK.zip";
	public static final String G_CJ5_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/cj5.zip";
	public static final String G_ECJ_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/ecj.zip";
	public static final String G_WB_DOWNLOAD_URL = "http://limeime.googlecode.com/svn/branches/database/wb.zip";
	
	// Database Download Targets
	public static final String IM_DOWNLOAD_TARGET_PRELOADED = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Flime1207.zip";
	public static final String IM_DOWNLOAD_TARGET_EMPTY = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fempty1109.zip";
	public static final String IM_DOWNLOAD_TARGET_PHONETIC_ONLY = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fphoneticonly1207.zip";
	public static final String IM_DOWNLOAD_TARGET_PHONETIC_HS_ONLY = "http://www.openfoundry.org/websvn/filedetails.php?repname=limeime&path=%2Fdatabases%2Flimehd%2Fphonetichs1207.zip";
	public static final String G_IM_DOWNLOAD_TARGET_PRELOADED = "http://limeime.googlecode.com/svn/branches/database/lime1207.zip";
	public static final String G_IM_DOWNLOAD_TARGET_EMPTY = "http://limeime.googlecode.com/svn/branches/database/empty1109.zip";
	public static final String G_IM_DOWNLOAD_TARGET_PHONETIC_ONLY = "http://limeime.googlecode.com/svn/branches/database/phoneticonly1207.zip";
	
	// Database Source File Names
	public static final String DATABASE_SOURCE_DAYI = "dayi.cin";
	public static final String DATABASE_SOURCE_PHONETIC = "phonetic.lime";
	public static final String DATABASE_SOURCE_PHONETIC_CNS = "bopomofo.cin";
	public static final String DATABASE_SOURCE_PHONETICADV = "phonetic_adv_CJK.lime";
	public static final String DATABASE_SOURCE_CJ = "cj.lime";
	public static final String DATABASE_SOURCE_CJ_CNS = "cangjie.cin";
	public static final String DATABASE_SOURCE_CJ5 = "cj5.lime";
	public static final String DATABASE_SOURCE_ECJ = "ecj.lime";
	public static final String DATABASE_SOURCE_SCJ = "scj.lime";
	public static final String DATABASE_SOURCE_ARRAY = "array.lime";
	public static final String DATABASE_SOURCE_ARRAY10 = "array10.lime";
	public static final String DATABASE_SOURCE_WB = "stroke5.cin";
	public static final String DATABASE_SOURCE_EZ = "ez.lime";
	public static final String DATABASE_SOURCE_PINYIN_BIG5 = "pinyinbig5.cin";
	public static final String DATABASE_SOURCE_PINYIN_GB = "pinyin.cin";
	public static final String DATABASE_SOURCE_PINYIN_LIME = "pinyin_CJK.cin";
	public static final String DATABASE_SOURCE_CJ_LIME = "cj_CJK.lime";
	public static final String DATABASE_SOURCE_ECJ_LIME = "ecj_CJK.lime";
	public static final String DATABASE_SOURCE_PHONETIC_LIME = "phonetic_CJK.lime";
	public static final String DATABASE_SOURCE_FILENAME = "lime.zip";
	public static final String DATABASE_SOURCE_FILENAME_EMPTY = "empty.zip";
	
	// Database Cloud URLs
	public static final String LIME_OLD_VERSION_URL = DATABASE_OPENFOUNDRY_URL_BASED + "limehd_3_9_1.apk";
	public static final String DATABASE_CLOUD_IM_WB = DATABASE_CLOUD_URL_BASED + "wb.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_WB = DATABASE_OPENFOUNDRY_URL_BASED + "wb.zip";
	public static final String DATABASE_CLOUD_IM_WB_KEYBOARD = "wb";
	public static final String DATABASE_CLOUD_IM_PINYINGB = DATABASE_CLOUD_URL_BASED + "pinyingb.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_PINYINGB = DATABASE_OPENFOUNDRY_URL_BASED + "pinyingb.zip";
	public static final String DATABASE_CLOUD_IM_PINYINGB_KEYBOARD = "lime";
	public static final String DATABASE_CLOUD_IM_PINYIN = DATABASE_CLOUD_URL_BASED + "pinyin.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_PINYIN = DATABASE_OPENFOUNDRY_URL_BASED + "pinyin.zip";
	public static final String DATABASE_CLOUD_IM_PINYIN_KEYBOARD = "lime";
	public static final String DATABASE_CLOUD_IM_PHONETICCOMPLETE_BIG5 = DATABASE_CLOUD_URL_BASED + "phoneticcompletebig5.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_PHONETICCOMPLETE_BIG5 = DATABASE_OPENFOUNDRY_URL_BASED + "phoneticcompletebig5.zip";
	public static final String DATABASE_CLOUD_IM_PHONETICCOMPLETE = DATABASE_CLOUD_URL_BASED + "phoneticcomplete.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_PHONETICCOMPLETE = DATABASE_OPENFOUNDRY_URL_BASED + "phoneticcomplete.zip";
	public static final String DATABASE_CLOUD_IM_PHONETIC_BIG5 = DATABASE_CLOUD_URL_BASED + "phoneticbig5.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_PHONETIC_BIG5 = DATABASE_OPENFOUNDRY_URL_BASED + "phoneticbig5.zip";
	public static final String DATABASE_CLOUD_IM_PHONETIC = DATABASE_CLOUD_URL_BASED + "phonetic.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_PHONETIC = DATABASE_OPENFOUNDRY_URL_BASED + "phonetic.zip";
	public static final String DATABASE_CLOUD_IM_PHONETIC_KEYBOARD = "phonetic";
	public static final String DATABASE_CLOUD_IM_EZ = DATABASE_CLOUD_URL_BASED + "ez.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_EZ = DATABASE_OPENFOUNDRY_URL_BASED + "ez.zip";
	public static final String DATABASE_CLOUD_IM_EZ_KEYBOARD = "ez";
	public static final String DATABASE_CLOUD_IM_ECJHK = DATABASE_CLOUD_URL_BASED + "ecjhk.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_ECJHK = DATABASE_OPENFOUNDRY_URL_BASED + "ecjhk.zip";
	public static final String DATABASE_CLOUD_IM_ECJHK_KEYBOARD = "cj";
	public static final String DATABASE_CLOUD_IM_ECJ = DATABASE_CLOUD_URL_BASED + "ecj.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_ECJ = DATABASE_OPENFOUNDRY_URL_BASED + "ecj.zip";
	public static final String DATABASE_CLOUD_IM_ECJ_KEYBOARD = "cj";
	public static final String DATABASE_CLOUD_IM_DAYI = DATABASE_CLOUD_URL_BASED + "dayi.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_DAYI = DATABASE_OPENFOUNDRY_URL_BASED + "dayi.zip";
	public static final String DATABASE_CLOUD_IM_DAYIUNI_BIG5 = DATABASE_CLOUD_URL_BASED + "dayiunibig5.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_DAYIUNI_BIG5 = DATABASE_OPENFOUNDRY_URL_BASED + "dayiunibig5.zip";
	public static final String DATABASE_CLOUD_IM_DAYIUNI = DATABASE_CLOUD_URL_BASED + "dayiuni.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_DAYIUNI = DATABASE_OPENFOUNDRY_URL_BASED + "dayiuni.zip";
	public static final String DATABASE_CLOUD_IM_DAYIUNIP_BIG5 = DATABASE_CLOUD_URL_BASED + "dayiunipbig5.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_DAYIUNIP_BIG5 = DATABASE_OPENFOUNDRY_URL_BASED + "dayiunipbig5.zip";
	public static final String DATABASE_CLOUD_IM_DAYIUNIP = DATABASE_CLOUD_URL_BASED + "dayiunip.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_DAYIUNIP = DATABASE_OPENFOUNDRY_URL_BASED + "dayiunip.zip";
	public static final String DATABASE_CLOUD_IM_DAYI_KEYBOARD = "dayisym";
	public static final String DATABASE_CLOUD_IM_CJHK = DATABASE_CLOUD_URL_BASED + "cjhk.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_CJHK = DATABASE_OPENFOUNDRY_URL_BASED + "cjhk.zip";
	public static final String DATABASE_CLOUD_IM_CJHK_KEYBOARD = "cj";
	public static final String DATABASE_CLOUD_IM_SCJ = DATABASE_CLOUD_URL_BASED + "scj.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_SCJ = DATABASE_OPENFOUNDRY_URL_BASED + "scj.zip";
	public static final String DATABASE_CLOUD_IM_SCJ_KEYBOARD = "limenum";
	public static final String DATABASE_CLOUD_IM_CJ5 = DATABASE_CLOUD_URL_BASED + "cj5.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_CJ5 = DATABASE_OPENFOUNDRY_URL_BASED + "cj5.zip";
	public static final String DATABASE_CLOUD_IM_CJ5_KEYBOARD = "cj";
	public static final String DATABASE_CLOUD_IM_CJ_BIG5 = DATABASE_CLOUD_URL_BASED + "cjbig5.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_CJ_BIG5 = DATABASE_OPENFOUNDRY_URL_BASED + "cjbig5.zip";
	public static final String DATABASE_CLOUD_IM_CJ = DATABASE_CLOUD_URL_BASED + "cj.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_CJ = DATABASE_OPENFOUNDRY_URL_BASED + "cj.zip";
	public static final String DATABASE_CLOUD_IM_CJ_KEYBOARD = "cj";
	public static final String DATABASE_CLOUD_IM_ARRAY10 = DATABASE_CLOUD_URL_BASED + "array10.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_ARRAY10 = DATABASE_OPENFOUNDRY_URL_BASED + "array10.zip";
	public static final String DATABASE_CLOUD_IM_ARRAY10_KEYBOARD = "phonenum";
	public static final String DATABASE_CLOUD_IM_ARRAY = DATABASE_CLOUD_URL_BASED + "array.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_ARRAY = DATABASE_OPENFOUNDRY_URL_BASED + "array.zip";
	public static final String DATABASE_CLOUD_IM_ARRAY_KEYBOARD = "arraynum";
	public static final String DATABASE_CLOUD_IM_HS = DATABASE_CLOUD_URL_BASED + "hs.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_HS = DATABASE_OPENFOUNDRY_URL_BASED + "hs.zip";
	public static final String DATABASE_CLOUD_IM_HS_V1 = DATABASE_CLOUD_URL_BASED + "hs1.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_HS_V1 = DATABASE_OPENFOUNDRY_URL_BASED + "hs1.zip";
	public static final String DATABASE_CLOUD_IM_HS_V2 = DATABASE_CLOUD_URL_BASED + "hs2.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_HS_V2 = DATABASE_OPENFOUNDRY_URL_BASED + "hs2.zip";
	public static final String DATABASE_CLOUD_IM_HS_V3 = DATABASE_CLOUD_URL_BASED + "hs3.zip";
	public static final String DATABASE_OPENFOUNDRY_IM_HS_V3 = DATABASE_OPENFOUNDRY_URL_BASED + "hs3.zip";
	public static final String DATABASE_CLOUD_IM_HS_KEYBOARD = "hs";
	
	// Database Tables
	public static final String DB_TABLE_IMTABLE2 = "imtable2";
	public static final String DB_TABLE_IMTABLE3 = "imtable3";
	public static final String DB_TABLE_IMTABLE4 = "imtable4";
	public static final String DB_TABLE_IMTABLE5 = "imtable5";
	public static final String DB_TABLE_IMTABLE6 = "imtable6";
	public static final String DB_TABLE_IMTABLE7 = "imtable7";
	public static final String DB_TABLE_IMTABLE8 = "imtable8";
	public static final String DB_TABLE_IMTABLE9 = "imtable9";
	public static final String DB_TABLE_IMTABLE10 = "imtable10";
	public static final String DB_TABLE_ARRAY = "array";
	public static final String DB_TABLE_ARRAY10 = "array10";
	public static final String DB_TABLE_CJ = "cj";
	public static final String DB_TABLE_CJ5 = "cj5";
	public static final String DB_TABLE_CUSTOM = "custom";
	public static final String DB_TABLE_DAYI = "dayi";
	public static final String DB_TABLE_ECJ = "ecj";
	public static final String DB_TABLE_EZ = "ez";
	public static final String DB_TABLE_HS = "hs";
	public static final String DB_TABLE_PHONETIC = "phonetic";
	public static final String DB_TABLE_PINYIN = "pinyin";
	public static final String DB_TABLE_SCJ = "scj";
	public static final String DB_TABLE_WB = "wb";
	
	// Input Method Types
	public static final String IM_ARRAY = "array";
	public static final String IM_ARRAY10 = "array10";
	public static final String IM_CJ_BIG5 = "cjbig5";
	public static final String IM_CJ = "cj";
	public static final String IM_CJHK = "cjhk";
	public static final String IM_CJ5 = "cj5";
	public static final String IM_CUSTOM = "custom";
	public static final String IM_DAYI = "dayi";
	public static final String IM_DAYIUNI = "dayiuni";
	public static final String IM_DAYIUNI_BIG5 = "dayiunibig5";
	public static final String IM_DAYIUNIP = "dayiunip";
	public static final String IM_DAYIUNIP_BIG5 = "dayiunipbig5";
	public static final String IM_ECJ = "ecj";
	public static final String IM_ECJHK = "ecjhk";
	public static final String IM_EZ = "ez";
	public static final String IM_HS = "hs";
	public static final String IM_HS_V1 = "hs1";
	public static final String IM_HS_V2 = "hs2";
	public static final String IM_HS_V3 = "hs3";
	public static final String IM_PHONETIC = "phonetic";
	public static final String IM_PHONETIC_ADV = "phoneticadv";
	public static final String IM_PHONETIC_BIG5 = "phoneticbig5";
	public static final String IM_PHONETIC_ADV_BIG5 = "phoneticadvbig5";

    public static final String IM_PHONETIC_STANDARD = "standard";

    public static final String KEYBOARD_NORMAL = "normal_keyboard";

    public static final String IM_PHONETIC_KEYBOARD_PHONETIC = "phonetic";
    public static final String IM_PHONETIC_KEYBOARD_HSU = "hsu";
    public static final String IM_PHONETIC_KEYBOARD_TYPE_HSU = "hsu";
    public static final String IM_PHONETIC_KEYBOARD_ETEN = "phoneticet41";
    public static final String IM_PHONETIC_KEYBOARD_TYPE_ETEN = "eten";
    public static final String IM_PHONETIC_KEYBOARD_TYPE_ETEN26 = "eten26";
    public static final String IM_PHONETIC_KEYBOARD_ETEN26 = "et26";
    public static final String IM_PHONETIC_KEYBOARD_TYPE_ETEN26_SYMBOL = "eten26_symbol";
	public static final String IM_PINYIN = "pinyin";
	public static final String IM_PINYINGB = "pinyingb";
	public static final String IM_SCJ = "scj";
	public static final String IM_WB = "wb";
	
	// Database Columns
	public static final String DB_COLUMN_ID = "_id";
	public static final String DB_COLUMN_CODE = "code";
	public static final String DB_COLUMN_CODE3R = "code3r";
	public static final String DB_COLUMN_WORD = "word";
	public static final String DB_COLUMN_RELATED = "related";
	public static final String DB_COLUMN_SCORE = "score";
	public static final String DB_COLUMN_BASESCORE = "basescore";
	
	// IM Table Columns
	public static final String DB_TABLE_IM = "im";
    public static final String DB_KEYBOARD = "keyboard";
	public static final String DB_IM_COLUMN_ID = "_id";
	public static final String DB_IM_COLUMN_CODE = "code";
	public static final String DB_IM_COLUMN_TITLE = "title";
	public static final String DB_IM_COLUMN_DESC = "desc";
	public static final String DB_IM_COLUMN_KEYBOARD = "keyboard";
	public static final String DB_IM_COLUMN_DISABLE = "disable";
	public static final String DB_IM_COLUMN_SELKEY = "selkey";
	public static final String DB_IM_COLUMN_ENDKEY = "endkey";
	public static final String DB_IM_COLUMN_SPACESTYLE = "spacestyle";
	
	// Related Table Columns
	public static final String DB_TABLE_RELATED = "related";
	public static final String DB_RELATED_COLUMN_ID = "_id";
	public static final String DB_RELATED_COLUMN_PWORD = "pword";
	public static final String DB_RELATED_COLUMN_CWORD = "cword";
	public static final String DB_RELATED_COLUMN_BASESCORE = "basescore";
	public static final String DB_RELATED_COLUMN_USERSCORE = "score";
	
	// Keyboard Table Columns
	public static final String DB_TABLE_KEYBOARD = "keyboard";
	public static final String DB_KEYBOARD_COLUMN_ID = "_id";
	public static final String DB_KEYBOARD_COLUMN_CODE = "code";
	public static final String DB_KEYBOARD_COLUMN_NAME = "name";
	public static final String DB_KEYBOARD_COLUMN_DESC = "desc";
	public static final String DB_KEYBOARD_COLUMN_TYPE = "type";
	public static final String DB_KEYBOARD_COLUMN_IMAGE = "image";
	public static final String DB_KEYBOARD_COLUMN_IMKB = "imkb";
	public static final String DB_KEYBOARD_COLUMN_IMSHIFTKB = "imshiftkb";
	public static final String DB_KEYBOARD_COLUMN_ENGKB = "engkb";
	public static final String DB_KEYBOARD_COLUMN_ENGSHIFTKB = "engshiftkb";
	public static final String DB_KEYBOARD_COLUMN_SYMBOLKB = "symbolkb";
	public static final String DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB = "symbolshiftkb";
	public static final String DB_KEYBOARD_COLUMN_DEFAULTKB = "defaultkb";
	public static final String DB_KEYBOARD_COLUMN_DEFAULTSHIFTKB = "defaultshiftkb";
	public static final String DB_KEYBOARD_COLUMN_EXTENDEDKB = "extendedkb";
	public static final String DB_KEYBOARD_COLUMN_EXTENDEDSHIFTKB = "extendedshiftkb";
	public static final String DB_KEYBOARD_COLUMN_DISABLE = "disable";
	public static final String DB_TOTAL_COUNT = "count";
	
	// IM Type Fields
	public static final String IM_TYPE_NAME = "name";
	public static final String IM_TYPE_SOURCE = "source";
	public static final String IM_TYPE_AMOUNT = "amount";
	public static final String IM_TYPE_IMPORT = "import";
	public static final String IM_TYPE_KEYBOARD = "keyboard";
	public static final String IM_TYPE_SELKEY = "selkey";
	public static final String IM_TYPE_ENDKEY = "endkey";
	public static final String IM_TYPE_SPACESTYLE = "spacestyle";
	
	// Database and IM Status
	public static final int IM_MANAGE_DISPLAY_AMOUNT = 100;
	public static final String DB_CHECK_RELATED_USERSCORE = "db_user_score_check";
	public static final String DATABASE_DOWNLOAD_STATUS = "database_download_status";
	public static final String DOWNLOAD_START = "download_start";
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
	
	// Cache Settings
	public final static int SEARCHSRV_RESET_CACHE_SIZE = 256;
	public final static int LIMEDB_CACHE_SIZE = 1024;
	
	// News and Content
	public static final String LIME_NEWS_CONTENT = "lime_news_content";
	public static final String LIME_NEWS_CONTENT_URL = "https://github.com/lime-ime/limeime/raw/master/Resources/Message/content.html";
	
	// File System
	public static final String separator = java.io.File.separator;
	public static final String DATABASE_IM_TEMP = "temp";
	public static final String DATABASE_IM_TEMP_EXT = "zip";
	
	
	// UI Constants
	public static final float HALF_ALPHA_VALUE = .5f;
	public static final float NORMAL_ALPHA_VALUE = 1f;
	
	// Buffer Sizes (in bytes)
	public static final int BUFFER_SIZE_1KB = 1024;
	public static final int BUFFER_SIZE_2KB = 2048;
	public static final int BUFFER_SIZE_4KB = 4096;
	public static final int BUFFER_SIZE_64KB = 65536;

	public static final int HANDLER_DELAY_MINIMAL_MS = 1; // Minimal delay for handler messages
	
	// Progress Percentage Constants
	public static final int PROGRESS_COMPLETE_PERCENT = 100; // 100% progress
	
	// Emoji Parameters
	public static final int EMOJI_EN = 1;
	public static final int EMOJI_TW = 2;
	public static final int EMOJI_CN = 3;
	public static final String EMOJI_FIELD_TAG = "tag";
	public static final String EMOJI_FIELD_VALUE = "value";
	
	
	// Global Utility Methods
	public static String format(int number) {
		try {
			DecimalFormat df = new DecimalFormat("###,###,###,###,###,###,##0");
			return df.format(number);
		} catch (Exception e) {
			Log.e("LIME", "Error formatting number", e);
			return "0";
		}
	}
	
	public static String formatSqlValue(String value) {
		if (value != null) {
			value = value.replaceAll("\"", "\"\"");
			value = value.replaceAll("'", "\\\'");
			return value;
		} else {
			return "";
		}
	}
}
