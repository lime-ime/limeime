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

package net.toload.main.hd.limedb;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.widget.Toast;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.data.ChineseSymbol;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.data.ImObj;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.data.KeyboardObj;
import net.toload.main.hd.data.Mapping;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.data.Word;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEProgressListener;
import net.toload.main.hd.global.LIMEUtilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main database helper class for LIME Input Method Engine.
 * 
 * <p>This class manages all database operations for the LIME IME, including:
 * <ul>
 *   <li>Mapping file loading and storage</li>
 *   <li>Code-to-word query operations</li>
 *   <li>Related phrase management</li>
 *   <li>Input method (IM) and keyboard configuration</li>
 *   <li>User dictionary operations</li>
 *   <li>Database backup and restore</li>
 * </ul>
 * 
 * <p>The database uses a shared static connection that is accessible by both
 * DBServer and SearchServer instances. The connection is managed through
 * {@link #openDBConnection(boolean)} and can be held during maintenance operations
 * to prevent concurrent access.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Supports multiple input methods (phonetic, dayi, array, cj, etc.)</li>
 *   <li>Handles dual-code mapping for physical keyboards</li>
 *   <li>Provides code remapping for different keyboard layouts</li>
 *   <li>Manages related phrase learning and suggestions</li>
 *   <li>Includes blacklist caching for invalid codes</li>
 * </ul>
 * 
 * @author The LimeIME Open Source Project
 * @version 3.0+
 * @since API Level 21
 */
public class LimeDB extends LimeSQLiteOpenHelper {

    private static final boolean DEBUG = false;
    private static String TAG = "LimeDB";

    private static SQLiteDatabase db = null;  //Jeremy '12,5,1 add static modifier. Shared db instance for dbserver and searchserver
    private final static int DATABASE_VERSION = 101;

    //Jeremy '15, 6, 1 between search clause without using related column for better sorting order.

    //private final static Boolean fuzzySearch = false;
    // hold database connection when database is in maintainable. Jeremy '15,5,23
    private static boolean databaseOnHold = false;

    //Jeremy '11,8,5
    private final static String INITIAL_RESULT_LIMIT = "15";
    private final static String FINAL_RESULT_LIMIT = "210";
    //private final static int INITIAL_RELATED_LIMIT = 5;
    private final static int COMPOSING_CODE_LENGTH_LIMIT = 16; //Jeremy '12,5,30 changed from 12 to 16 because of improved performance using binary tree.
    private final static int DUALCODE_COMPOSING_LIMIT = 16; //Jeremy '12,5,30 changed from 7 to 16 because of improved performance using binary tree.
    private final static int DUALCODE_NO_CHECK_LIMIT = 2; //Jeremy '12,5,30 changed from 5 to 3 for phonetic correct valid code display.
    //private final static int BETWEEN_SEARCH_WAY_BACK_LEVELS = 5; //Jeremy '15,6,30

    private static boolean codeDualMapped = false;

    /**
     * Checks if the current code has dual mapping enabled.
     * 
     * <p>Dual mapping allows a single key to map to multiple characters,
     * which is useful for certain physical keyboard layouts.
     * 
     * @return true if dual mapping is active, false otherwise
     */
    public static boolean isCodeDualMapped() {
        return codeDualMapped;
    }

    /** Database column name for record ID */
    public final static String FIELD_ID = "_id";
    /** Database column name for input code */
    public final static String FIELD_CODE = "code";
    /** Database column name for output word */
    public final static String FIELD_WORD = "word";
    /** Database column name for related words list */
    public final static String FIELD_RELATED = LIME.DB_TABLE_RELATED;
    /** Database column name for user score */
    public final static String FIELD_SCORE = "score";
    /** Database column name for base frequency score from han converter */
    public final static String FIELD_BASESCORE = "basescore"; //jeremy '11,9,8 base frequency got from han converter when table loading.
    /** Database column name for phonetic code without tone symbols */
    public final static String FIELD_NO_TONE_CODE = "code3r";

    /** Virtual column name for exact match flag in query results */
    public final static String FILE_EXACT_MATCH = "exactmatch";

    //public final static String FIELD_DIC_id = "_id";
    //public final static String FIELD_DIC_pcode = "pcode";
    /** Database column name for parent word in related phrase table */
    public final static String FIELD_DIC_pword = "pword";
    //public final static String FIELD_DIC_ccode = "ccode";
    /** Database column name for child word in related phrase table */
    public final static String FIELD_DIC_cword = "cword";
    //public final static String FIELD_DIC_score = "score";
    //public final static String FIELD_DIC_is = "isDictionary";

    // for keyToChar
    private final static String DAYI_KEY = "1234567890qwertyuiopasdfghjkl;zxcvbnm,./";
    private final static String DAYI_CHAR =
            "言|牛|目|四|王|門|田|米|足|金|石|山|一|工|糸|火|艸|木|口|耳|人|革|日|土|手|鳥|月|立|女|虫|心|水|鹿|禾|馬|魚|雨|力|舟|竹";
    private final static String ARRAY_KEY = "qazwsxedcrfvtgbyhnujmik,ol.p;/";
    private final static String ARRAY_CHAR =
            "1^|1-|1v|2^|2-|2v|3^|3-|3v|4^|4-|4v|5^|5-|5v|6^|6-|6v|7^|7-|7v|8^|8-|8v|9^|9-|9v|0^|0-|0v|";
    private final static String BPMF_KEY = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-";
    private final static String BPMF_CHAR =
            "ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|ㄋ|ㄌ|ˇ|ㄍ|ㄎ|ㄏ|ˋ|ㄐ|ㄑ|ㄒ|ㄓ|ㄔ|ㄕ|ㄖ|ˊ|ㄗ|ㄘ|ㄙ|˙|ㄧ|ㄨ|ㄩ|ㄚ|ㄛ|ㄜ|ㄝ|ㄞ|ㄟ|ㄠ|ㄡ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ";


    private final static String SHIFTED_NUMBERIC_KEY = "!@#$%^&*()";
    private final static String SHIFTED_NUMBERIC_KEY_REMAP = "1234567890";

    private final static String SHIFTED_SYMBOL_KEY = "<>?_:+\"";
    private final static String SHIFTED_SYMBOL_KEY_REMAP = ",./-;='";

    private final static String ETEN_KEY = "abcdefghijklmnopqrstuvwxyz12347890-=;',./!@#$&*()<>?_+:\"";
    private final static String ETEN_KEY_REMAP = "81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg7634f0p;5tg/yh-";
    //private final static String DESIREZ_ETEN_KEY_REMAP = 	"-`81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg/";
    //private final static String MILESTONE_ETEN_KEY_REMAP =  "-`81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg/";
    //private final static String MILESTONE3_ETEN_KEY_REMAP = "-h81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg/";
    private final static String DESIREZ_ETEN_DUALKEY = "o,ukm9iq5axesa"; // remapped from "qwer uiop,vlnm";
    private final static String DESIREZ_ETEN_DUALKEY_REMAP = "7634f0p;thg/-h"; // remapped from "1234 7890;-/='";
    private final static String CHACHA_ETEN_DUALKEY = ",uknljvcrx1?"; // remapped from "werszxchglb?" 
    private final static String CHACHA_ETEN_DUALKEY_REMAP = "7634f0p/g-hy"; // remapped from "1234789-/=';";
    private final static String XPERIAPRO_ETEN_DUALKEY = "o,ukm9iqa52z"; // remapped from "qweruiopm,df";
    private final static String XPERIAPRO_ETEN_DUALKEY_REMAP = "7634f0p;th/-"; // remapped from "12347890;'=-";
    private final static String MILESTONE_ETEN_DUALKEY = "o,ukm9iq5aec"; // remapped from "qweruiop,mvh";
    private final static String MILESTONE_ETEN_DUALKEY_REMAP = "7634f0p;th/-"; // remapped from "12347890;'=-";
    private final static String MILESTONE2_ETEN_DUALKEY = "o,ukm9iq5aer"; //remapped from "qweruiop,mvg";
    private final static String MILESTONE2_ETEN_DUALKEY_REMAP = "7634f0p;th/-";
    private final static String MILESTONE3_ETEN_DUALKEY = "5aew"; // ",mvt"
    private final static String MILESTONE3_ETEN_DUALKEY_REMAP = "th/-";
    private final static String ETEN_CHAR =
            "ㄚ|ㄅ|ㄒ|ㄉ|ㄧ|ㄈ|ㄐ|ㄏ|ㄞ|ㄖ|ㄎ|ㄌ|ㄇ|ㄋ|ㄛ|ㄆ|ㄟ|ㄜ|ㄙ|ㄊ|ㄩ|ㄍ|ㄝ|ㄨ|ㄡ|ㄠ" +
                    "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|ㄓ|ㄔ|ㄕ|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄓ|ㄔ|ㄕ|ㄥ|ㄦ|ㄗ|ㄘ";
    private final static String DESIREZ_ETEN_CHAR =
            "@|`|ㄚ|ㄅ|ㄒ|ㄉ|(ㄧ/ˇ)|ㄈ|ㄐ|ㄏ|(ㄞ/ㄢ)|ㄖ|ㄎ|(ㄌ/ㄕ)|(ㄇ/ㄘ)|(ㄋ/ㄦ)|(ㄛ/ㄣ)|(ㄆ/ㄤ)|(ㄟ/˙)" +
                    "|(ㄜ/ˋ)|ㄙ|ㄊ|(ㄩ/ㄑ)|(ㄍ/ㄥ)|(ㄝ/ˊ)|ㄨ|ㄡ|ㄠ" +
                    "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|?";
    private final static String MILESTONE_ETEN_CHAR =
            "ㄦ|`|ㄚ|ㄅ|ㄒ|ㄉ|(ㄧ/ˇ)|ㄈ|ㄐ|(ㄏ/ㄦ)|(ㄞ/ㄢ)|ㄖ|ㄎ|ㄌ|(ㄇ/ㄘ)|ㄋ|(ㄛ/ㄣ)|(ㄆ/ㄤ)|(ㄟ/˙)" +
                    "|(ㄜ/ˋ)|ㄙ|ㄊ|(ㄩ/ㄑ)|(ㄍ/ㄥ)|(ㄝ/ˊ)|ㄨ|ㄡ|ㄠ" +
                    "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|ㄥ";
    private final static String MILESTONE2_ETEN_CHAR =
            "ㄦ|`|ㄚ|ㄅ|ㄒ|ㄉ|(ㄧ/ˇ)|ㄈ|(ㄐ/ㄦ)|ㄏ|(ㄞ/ㄢ)|ㄖ|ㄎ|ㄌ|(ㄇ/ㄘ)|ㄋ|(ㄛ/ㄣ)|(ㄆ/ㄤ)|(ㄟ/˙)" +
                    "|(ㄜ/ˋ)|ㄙ|ㄊ|(ㄩ/ㄑ)|(ㄍ/ㄥ)|(ㄝ/ˊ)|ㄨ|ㄡ|ㄠ" +
                    "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|ㄥ";
    private final static String MILESTONE3_ETEN_CHAR =
            "ㄦ|ㄘ|ㄚ|ㄅ|ㄒ|ㄉ|ㄧ|ㄈ|ㄐ|ㄏ|ㄞ|ㄖ|ㄎ|ㄌ|(ㄇ/ㄘ)|ㄋ|ㄛ|ㄆ|ㄟ|ㄜ|ㄙ|(ㄊ/ㄦ)|ㄩ|ㄍ|ㄝ|ㄨ|ㄡ|ㄠ" +
                    "|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|(ㄍ/ㄥ)|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|ㄥ";

    private final static String ETEN26_KEY = "qazwsxedcrfvtgbyhnujmikolp,.";
    private final static String ETEN26_KEY_REMAP_INITIAL = "y8lhnju2vkzewr1tcsmba9dixq<>";
    private final static String ETEN26_KEY_REMAP_FINAL = "y8lhnju7vk6ewr1tcsm3a94ixq<>";
    private final static String ETEN26_DUALKEY_REMAP = "o,gf;5p-s0/.pbdz2";
    private final static String ETEN26_DUALKEY = "yhvewrscpaxqs3467";
    private final static String ETEN26_CHAR_INITIAL =
            "(ㄗ/ㄟ)|ㄚ|ㄠ|(ㄘ/ㄝ)|ㄙ|ㄨ|ㄧ|ㄉ|(ㄕ/ㄒ)|ㄜ|ㄈ|(ㄍ/ㄑ)|(ㄊ/ㄤ)|(ㄐ/ㄓ)|ㄅ|ㄔ|(ㄏ/ㄦ)|(ㄋ/ㄣ)|ㄩ|ㄖ|(ㄇ/ㄢ)|ㄞ|ㄎ|ㄛ|(ㄌ/ㄥ)|(ㄆ/ㄡ)|，|。";
    private final static String ETEN26_CHAR_FINAL =
            "(ㄗ/ㄟ)|ㄚ|ㄠ|(ㄘ/ㄝ)|ㄙ|ㄨ|ㄧ|˙|(ㄕ/ㄒ)|ㄜ|ˊ|(ㄍ/ㄑ)|(ㄊ/ㄤ)|(ㄐ/ㄓ)|ㄅ|ㄔ|(ㄏ/ㄦ)|(ㄋ/ㄣ)|ㄩ|ˇ|(ㄇ/ㄢ)|ㄞ|ˋ|ㄛ|(ㄌ/ㄥ)|(ㄆ/ㄡ)|，|。";

    //Jeremy '12,5,31 use dual codes instead of initial/final remap for Hsu phonetic keyboard
    private final static String HSU_KEY = "azwsxedcrfvtgbyhnujmikolpq,.";
    private final static String HSU_KEY_REMAP_INITIAL = "hylnju2vbzfwe18csm5a9d.xq`<>";
    private final static String HSU_KEY_REMAP_FINAL = "hyl7ju6vb3fwe18csm4a9d.xq`<>";
    private final static String HSU_DUALKEY_REMAP = "g8t5r/-,okip0;n2z";
    private final static String HSU_DUALKEY = "vbf45x/uhecsad763";
    private final static String HSU_CHAR_INITIAL =
            "(ㄘ/ㄟ)|ㄗ|ㄠ|ㄙ|ㄨ|(ㄧ/ㄝ)|ㄉ|(ㄕ/ㄒ)|ㄖ|ㄈ|(ㄔ/ㄑ)|ㄊ|(ㄍ/ㄜ)|ㄅ|ㄚ|(ㄏ/ㄛ)|(ㄋ/ㄣ)|ㄩ|(ㄐ/ㄓ)|(ㄇ/ㄢ)|ㄞ|(ㄎ/ㄤ)|ㄡ|(ㄌ/ㄥ/ㄦ)|ㄆ|q|，|。";
    private final static String HSU_CHAR_FINAL =
            "(ㄘ/ㄟ)|ㄗ|ㄠ|(ㄙ/˙)|ㄨ|(ㄧ/ㄝ)|(ㄉ/ˊ)|(ㄕ/ㄒ)|ㄖ|(ㄈ/ˇ)|(ㄔ/ㄑ)|ㄊ|(ㄍ/ㄜ)|ㄅ|ㄚ|(ㄏ/ㄛ)|(ㄋ/ㄣ)|ㄩ|(ㄐ/ㄓ/ˋ)|(ㄇ/ㄢ)|ㄞ|(ㄎ/ㄤ)|ㄡ|(ㄥ/ㄦ)|ㄆ|q|，|。";

    private final static String DESIREZ_KEY = "@qazwsxedcrfvtgbyhnujmik?olp,.";
    private final static String DESIREZ_BPMF_KEY_REMAP = "1qaz2wsedc5tg6yh4uj8ik9ol0;-,.";
    private final static String DESIREZ_BPMF_DUALKEY_REMAP = "xrfvb3n7m,.p/";
    private final static String DESIREZ_BPMF_DUALKEY = "sedcg6h4jkl0;";
    private final static String DESIREZ_DUALKEY_REMAP = "1234567890;-/='";
    private final static String DESIREZ_DUALKEY = "qwertyuiop,vlnm";
    private final static String DESIREZ_BPMF_CHAR =
            "ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|(ㄋ/ㄌ)|(ㄍ/ㄐ)|(ㄎ/ㄑ)|(ㄏ/ㄒ)|ㄓ|ㄔ|(ㄕ/ㄖ)|(ˊ/ˇ)|ㄗ|(ㄘ/ㄙ)|(ˋ/˙)" +
                    "|ㄧ|(ㄨ/ㄩ)|ㄚ|ㄛ|(ㄜ/ㄝ)|ㄞ|ㄟ|(ㄠ/ㄡ)|(ㄢ/ㄣ)|(ㄤ/ㄥ)|ㄦ|,|.";
    private final static String DESIREZ_DAYI_CHAR =
            "@|(言/石)|人|心|(牛/山)|革|水|(目/一)|日|鹿|(四/工)|土|禾|(王/糸)|手|馬|(門/火)|鳥|魚|(田/艸)|月|雨|"
                    + "(米/木)|立|?|(足/口)|(女/竹)|(金/耳)|(力/虫)|舟";


    private final static String CHACHA_KEY = "qazwsxedcrfvtgbyhnujmik?olp,.";
    private final static String CHACHA_BPMF_KEY_REMAP = "qax2scedb5t3yh4uj68k.9o/0p-<>";
    private final static String CHACHA_BPMF_DUALKEY_REMAP = "1zwrfvnmgi,7l;";
    private final static String CHACHA_BPMF_DUALKEY = "qxsedchjt8k6op";
    private final static String CHACHA_DUALKEY_REMAP = "123456789-/=';";
    private final static String CHACHA_DUALKEY = "wersdfzxchglb?";
    private final static String CHACHA_BPMF_CHAR =
            "(ㄅ/ㄆ)|(ㄇ/ㄈ)|ㄌ|ㄉ|(ㄊ/ㄋ)|(ㄏ/ㄒ)|(ㄍ/ㄐ)|(ㄎ/ㄑ)|ㄖ|ㄓ|(ㄔ/ㄕ)|ˇ|ㄗ|(ㄘ/ㄙ)|ˋ|ㄧ|(ㄨ/ㄩ)|(ˊ/˙)" +
                    "|(ㄚ/ㄛ)|(ㄜ/ㄝ)|ㄡ|ㄞ|(ㄟ/ㄠ)|ㄥ|ㄢ|(ㄣ/ㄤ)|ㄦ|,|.";

    private final static String XPERIAPRO_KEY = "qazZwsxXedcCrfvVtgbByhnNujmMik`~ol'\"pP!/@";
    private final static String XPERIAPRO_BPMF_KEY_REMAP = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-";
    //private final static String XPERIAPRO_BPMF_DUALKEY_REMAP = 		"";
    //private final static String XPERIAPRO_BPMF_DUALKEY = 			"";
    private final static String XPERIAPRO_DUALKEY_REMAP = "1234567890;,=-";
    private final static String XPERIAPRO_DUALKEY = "qwertyuiopm.df";
    //private final static String XPERIAPRO_BPMF_CHAR =; // Use BPMF_CHAR 

    private final static String MILESTONE = "milestone";
    private final static String MILESTONE2 = "milestone2";
    private final static String MILESTONE3 = "milestone3";
    private final static String MILESTONE_DUALKEY_REMAP = "1234567890;'=-";
    private final static String MILESTONE_DUALKEY = "qwertyuiop,mhv";
    private final static String MILESTONE_KEY = "qazwsxedcrfvtgbyhnujmik,ol.p/?";
    private final static String MILESTONE_BPMF_CHAR =
            "(ㄅ/ㄆ)|ㄇ|ㄈ|(ㄉ/ㄊ)|ㄋ|ㄌ|(ㄍ/ˇ)|ㄎ|ㄏ|(ㄐ/ˋ)|ㄑ|ㄒ|(ㄓ/ㄔ)|ㄕ|ㄖ|(ㄗ/ˊ)|ㄘ|ㄙ|(ㄧ/˙)" +
                    "|ㄨ|ㄩ|(ㄚ/ㄛ)|ㄜ|(ㄝ/ㄤ)|(ㄞ/ㄟ)|ㄠ|ㄡ|(ㄢ/ㄣ)|ㄥ|ㄦ";
    private final static String MILESTONE_DAYI_CHAR =
            "(言/石)|人|心|(牛/山)|革|水|(目/一)|日|鹿|(四/工)|土|禾|(王/糸)|手|馬|(門/火)|鳥|魚|(田/艸)|月|雨|"
                    + "(米/木)|立|(力/虫)|(足/口)|女|舟|(金/耳)|竹|?";

    private final static String MILESTONE2_DUALKEY_REMAP = "1234567890;'=-";
    private final static String MILESTONE2_DUALKEY = "qwertyuiop,mgv";


    private final static String MILESTONE3_KEY = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p/";
    private final static String MILESTONE3_DUALKEY_REMAP = ";";
    private final static String MILESTONE3_DUALKEY = ",";
    private final static String MILESTONE3_BPMF_DUALKEY_REMAP = ";/-";
    private final static String MILESTONE3_BPMF_DUALKEY = "l.p";
    private final static String MILESTONE3_BPMF_CHAR =
            "ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|ㄋ|ㄌ|ˇ|ㄍ|ㄎ|ㄏ|ˋ|ㄐ|ㄑ|ㄒ|ㄓ|ㄔ|ㄕ|ㄖ|ˊ|ㄗ|ㄘ|ㄙ|˙|" +
                    "ㄧ|ㄨ|ㄩ|ㄚ|ㄛ|ㄜ|ㄝ|ㄞ|ㄟ|(ㄠ/ㄤ)|(ㄡ/ㄥ)|ㄢ|ㄣ|ㄥ";
    private final static String MILESTONE3_DAYI_CHAR =
            "言|石|人|心|牛|山|革|水|目|一|日|鹿|四|工|土|禾|王|糸|手|馬|門|火|鳥|魚|田|" +
                    "艸|月|雨|米|木|立|(力/虫)|足|口|女|舟|金|耳|竹";


    private final static String CJ_KEY = "qwertyuiopasdfghjklzxcvbnm";
    private final static String CJ_CHAR = "手|田|水|口|廿|卜|山|戈|人|心|日|尸|木|火|土|竹|十|大|中|重|難|金|女|月|弓|一";

    private final HashMap<String, HashMap<String, String>> keysDefMap = new HashMap<>();
    private final HashMap<String, HashMap<String, String>> keysReMap = new HashMap<>();
    private final HashMap<String, HashMap<String, String>> keysDualMap = new HashMap<>();

    private String lastCode = "";
    private String lastValidDualCodeList = "";

    private File filename = null;
    private String tableName = "custom";

      private int count = 0;
    // Jeremy '15,5,23 for new progress listener progress status update
    private int progressPercentageDone = 0;
    private String progressStatus;

    private boolean finish = false;

    private boolean isPhysicalKeyboardPressed = false;

    private static ConcurrentHashMap<String, Boolean> blackListCache = null;

    private final LIMEPreferenceManager mLIMEPref;

    private final Context mContext;

    private Thread loadingMappingThread = null;
    private boolean threadAborted = false;

    // Cache for Related Score
    private final HashMap<String, Integer> relatedScore = new HashMap<>();

    // Han and Emoji Databases
    private LimeHanConverter hanConverter;
    private EmojiConverter emojiConverter;

    private final int SLEEP_DELAY_1_SECOND_MS = 1000;
    /**
     * Initializes the LIME database with the given context.
     *
     * <p>This constructor:
     * <ul>
     *   <li>Initializes the SQLite database helper</li>
     *   <li>Creates a LIMEPreferenceManager instance</li>
     *   <li>Initializes the blacklist cache</li>
     *   <li>Opens the database connection</li>
     * </ul>
     *
     * <p>The database connection is opened immediately in the constructor to ensure
     * it's ready for use. The connection is shared statically across all LimeDB instances.
     *
     * @param context The Android context for accessing preferences and resources
     */
    public LimeDB(Context context) {

        super(context, LIME.DATABASE_NAME, DATABASE_VERSION);
        this.mContext = context;

        mLIMEPref = new LIMEPreferenceManager(mContext.getApplicationContext());


        blackListCache = new ConcurrentHashMap<>(LIME.LIMEDB_CACHE_SIZE);


        // Jeremy '12,4,7 open DB connection in constructor
        openDBConnection(true);

    }

    /**
     * Sets the finish flag indicating whether file loading is complete.
     * 
     * <p>This flag is used internally during file loading operations to track
     * completion status. It's set to true when loading completes successfully.
     * 
     * @param value true if loading is complete, false otherwise
     */
    public void setFinish(boolean value) {
        this.finish = value;
    }

    /**
     * Sets the filename for the mapping file to be loaded into the database.
     * 
     * <p>This method is called by DBServer before loading a mapping file.
     * The file will be processed by {@link #loadFileV2(String, LIMEProgressListener)}.
     * 
     * @param filename The file to load, or null to clear the filename
     */
    public void setFilename(File filename) {
        this.filename = filename;
    }

    /**
     * Sets the table name for word mapping queries.
     * 
     * <p>This method is called by LIMEService to set the active input method table.
     * All subsequent queries will use this table name unless explicitly overridden.
     * 
     * <p>The table name must be valid according to {@link #isValidTableName(String)}
     * to prevent SQL injection attacks.
     * 
     * @param tableName The table name to use for queries (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI, "custom")
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
        //checkLengthColumn(tableName);
        if (DEBUG) {
            Log.i(TAG, "settTableName(), tableName:" + tableName + " this.tableName:"
                    + this.tableName);
        }
    }

    /**
     * Safely retrieves a String value from a Cursor by column name.
     * 
     * <p>This helper method prevents IndexOutOfBoundsException when a column
     * doesn't exist in the cursor. Returns an empty string if the column is missing.
     * 
     * @param cursor The Cursor to read from
     * @param columnName The name of the column to retrieve
     * @return The string value, or empty string if column doesn't exist
     */
    public String getCursorString(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        if (index != -1) {
            String value = cursor.getString(index);
            return (value != null) ? value : ""; // Return empty string if column value is NULL
        }
        return ""; // Return empty string if column is missing
    }

    /**
     * Safely retrieves an integer value from a Cursor by column name.
     * 
     * <p>This helper method prevents IndexOutOfBoundsException when a column
     * doesn't exist in the cursor. Returns 0 if the column is missing.
     * 
     * @param cursor The Cursor to read from
     * @param columnName The name of the column to retrieve
     * @return The integer value, or 0 if column doesn't exist
     */
    public int getCursorInt(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        if (index != -1) {
            return cursor.getInt(index);
        }
        return 0; // Return 0 if column is missing
    }


    /**
     * Validates table name against whitelist to prevent SQL injection.
     * 
     * <p>This method checks if the provided table name is in the whitelist of
     * valid table names. It also supports backup table patterns (e.g., "phonetic_user").
     * 
     * <p>Valid table names include standard IM tables (phonetic, dayi, array, etc.),
     * system tables (related, im, keyboard), and backup tables ending with "_user".
     * 
     * @param tableName The table name to validate
     * @return true if table name is valid and safe to use, false otherwise
     */
    /**
     * Validates if a table name is in the whitelist of allowed tables.
     * 
     * @param tableName The table name to validate
     * @return true if the table name is valid, false otherwise
     */
    public boolean isValidTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        // Whitelist of valid table names
        String[] validTables = {
            LIME.DB_TABLE_ARRAY, LIME.DB_TABLE_ARRAY10,
            LIME.DB_TABLE_CJ, LIME.DB_TABLE_CJ5, LIME.DB_TABLE_CUSTOM,
            LIME.DB_TABLE_DAYI, LIME.DB_TABLE_ECJ, LIME.DB_TABLE_EZ,
            LIME.DB_TABLE_HS, LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_PINYIN,
            LIME.DB_TABLE_SCJ, LIME.DB_TABLE_WB,
            LIME.DB_TABLE_IMTABLE2, LIME.DB_TABLE_IMTABLE3, LIME.DB_TABLE_IMTABLE4,
            LIME.DB_TABLE_IMTABLE5, LIME.DB_TABLE_IMTABLE6, LIME.DB_TABLE_IMTABLE7,
            LIME.DB_TABLE_IMTABLE8, LIME.DB_TABLE_IMTABLE9, LIME.DB_TABLE_IMTABLE10,
            LIME.DB_TABLE_RELATED, LIME.DB_TABLE_IM, LIME.DB_TABLE_KEYBOARD
        };
        // Check exact match
        for (String validTable : validTables) {
            if (validTable.equals(tableName)) {
                return true;
            }
        }
        // Check for backup table pattern: "imtype_user" where imtype is valid
        if (tableName.endsWith("_user")) {
            String baseTable = tableName.substring(0, tableName.length() - 5);
            for (String validTable : validTables) {
                if (validTable.equals(baseTable)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Gets the current table name used for queries.
     * 
     * @return The current table name, or "custom" if not set
     */
    public String getTableName() {
        return this.tableName;
    }


    /**
     * Handles database schema upgrades when the database version changes.
     * 
     * <p>Currently, all upgrade code has been removed. This method logs the
     * version change but does not perform any migration. Future upgrades
     * should be implemented here if needed.
     * 
     * @param dbin The database to upgrade
     * @param oldVersion The old database version
     * @param newVersion The new database version
     */
    @Override
    public void onUpgrade(SQLiteDatabase dbin, int oldVersion, int newVersion) {

        Log.i(TAG, "OnUpgrade() db old version = " + oldVersion + ", new version = " + newVersion);


    }



    /**
     * Checks and updates the related table structure if needed.
     * 
     * <p>This method ensures the related table has the required columns and indexes:
     * <ul>
     *   <li>Adds basescore column if missing</li>
     *   <li>Creates pword index if missing</li>
     *   <li>Creates cword index if missing</li>
     * </ul>
     * 
     * <p>This is typically called during database initialization or upgrade.
     */
    public void checkAndUpdateRelatedTable() {
        // Check related table structure
        String CHECK_RELATED = "SELECT basescore FROM " + LIME.DB_TABLE_RELATED;


        // If system can find the score field which means the table still use old schema
        try (Cursor cursor = rawQuery(CHECK_RELATED)) {
            if (cursor == null || !cursor.moveToFirst()) {
                try {

                    String add_column = "ALTER TABLE " + LIME.DB_TABLE_RELATED + " ADD ";
                    add_column += LIME.DB_RELATED_COLUMN_BASESCORE + " INTEGER";

                    db.execSQL(add_column);

                    // Download and restore related DB
                } catch (SQLiteException e) {
                    Log.e(TAG, "Error in database operation", e);
                }
            }
        }
        try (Cursor cursor = db.query("sqlite_master", null, "type='index' and name = 'related_idx_pword'", null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                try {
                    db.execSQL("create index 'related_idx_pword' on related (pword)");
                }catch (SQLiteException e){
                    Log.e(TAG, "Error in database operation", e);
                }

            }
        }
        try (Cursor cursor = db.query("sqlite_master", null, "type='index' and name = 'related_idx_cword'", null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                try {
                    db.execSQL("create index 'related_idx_cword' on related (cword)");
                }catch (SQLiteException e){
                    Log.e(TAG, "Error in database operation", e);
                }

            }
        }


    }


    /**
     * Checks and updates phonetic keyboard settings consistency between preferences and database.
     * 
     * <p>This method ensures that the keyboard configuration stored in the database
     * matches the user's preference setting. It handles different phonetic keyboard types:
     * <ul>
     *   <li>hsu - Hsu phonetic keyboard</li>
     *   <li>eten26 - ETEN 26-key phonetic keyboard</li>
     *   <li>eten - ETEN 41-key phonetic keyboard</li>
     *   <li>standard - Standard BPMF phonetic keyboard (default)</li>
     * </ul>
     * 
     * <p>If the database configuration doesn't match the preference, it updates the
     * database to match the preference setting.
     */
    public void checkPhoneticKeyboardSetting() {
        if (checkDBConnection()) return;
        try {
            checkPhoneticKeyboardSettingOnDB(db);
        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }


    }


    /**
     * @param DBin sqlite database object
     */
    private void checkPhoneticKeyboardSettingOnDB(SQLiteDatabase DBin) {
        String selectedPhoneticKeyboardType = mLIMEPref.getPhoneticKeyboardType();
        if (DEBUG)
            Log.i("OnUpgrade()", "checkPhoneticKeyboardSettingOnDB:" + selectedPhoneticKeyboardType);
        switch (selectedPhoneticKeyboardType) {
            case LIME.IM_PHONETIC_KEYBOARD_TYPE_HSU:
                setIMKeyboardOnDB(DBin, LIME.IM_PHONETIC,
                        getKeyboardInfoOnDB(DBin, LIME.IM_PHONETIC_KEYBOARD_HSU, LIME.DB_IM_COLUMN_DESC), LIME.IM_PHONETIC_KEYBOARD_HSU);//jeremy '12,6,6 new hsu and et26 keybaord

                break;
            case LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26:
                setIMKeyboardOnDB(DBin, LIME.IM_PHONETIC,
                        getKeyboardInfoOnDB(DBin, LIME.IM_PHONETIC_KEYBOARD_ETEN26, LIME.DB_IM_COLUMN_DESC), LIME.IM_PHONETIC_KEYBOARD_ETEN26);
                break;
            case LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN:
                setIMKeyboardOnDB(DBin, LIME.IM_PHONETIC,
                        getKeyboardInfoOnDB(DBin, LIME.IM_PHONETIC_KEYBOARD_ETEN, LIME.DB_IM_COLUMN_DESC), LIME.IM_PHONETIC_KEYBOARD_ETEN);
                break;
            default:
                setIMKeyboardOnDB(DBin, LIME.IM_PHONETIC,
                        getKeyboardInfoOnDB(DBin, LIME.IM_PHONETIC_KEYBOARD_PHONETIC, LIME.DB_IM_COLUMN_DESC), LIME.IM_PHONETIC_KEYBOARD_PHONETIC
                );
                break;
        }
    }


    /**
     * Opens or reopens the database connection.
     * 
     * <p>This method manages the shared static database connection. If force_reload
     * is true, it closes any existing connection and opens a new one. Otherwise,
     * it returns true if a valid connection already exists.
     * 
     * <p>When reopening, this method also clears the related phrase score cache.
     * 
     * @param force_reload If true, force close and reopen the connection; if false,
     *                     return true if connection already exists
     * @return true if database connection is open and ready, false otherwise
     */
    public boolean openDBConnection(boolean force_reload) {
        if (DEBUG) {
            Log.i(TAG, "openDBConnection(), force_reload = " + force_reload);
            if (db != null)
                Log.i(TAG, "db.isOpen()" + db.isOpen());
        }

        if (!force_reload && db != null && db.isOpen()) {
            return true;
        } else {

            // Reset related phrase score cache
            relatedScore.clear();

            if (force_reload) {
                try {
                    if (db != null && db.isOpen()) {
                        db.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in database operation", e);
                }
            }
            db = this.getWritableDatabase();
            databaseOnHold = false;
            return db != null && db.isOpen();
        }
    }

    /**
     * Jeremy '12,5,1  checkDBConnection try to openDBConnection if db is not open.
     * Return true if the db connection is valid, return false if dbConnection is not valid
     *
     * @return return true if db connection is ready.
     */
    private boolean checkDBConnection() {
        //Jeremy '12,5,1 mapping loading. db is locked
        if (DEBUG)
            Log.i(TAG, "checkDBConnection()");

        if (databaseOnHold) {   //mapping loading in progress, database is not available for query
            if (DEBUG)
                Log.i(TAG, "checkDBConnection() : mapping loading ");
            if (Looper.myLooper() == null)
                Looper.prepare();
            Toast.makeText(mContext, mContext.getText(R.string.l3_database_loading), Toast.LENGTH_SHORT).show();
            Looper.loop();
            return true;
        } else
            return !openDBConnection(false);


    }

    /**
     * Deletes all records from the specified table.
     * 
     * <p>This method:
     * <ul>
     *   <li>Waits for any active loading thread to complete</li>
     *   <li>Deletes all records from the table if any exist</li>
     *   <li>Resets IM information for the table</li>
     *   <li>Clears the blacklist cache</li>
     * </ul>
     * 
     * <p>This is typically used when reloading a mapping file or resetting
     * an input method table.
     * 
     * @param table The table name to clear (must be a valid table name)
     */
    public void deleteAll(String table) {

        if (DEBUG)
            Log.i(TAG, "deleteAll()");
        if (loadingMappingThread != null) {
            threadAborted = true;
            while (loadingMappingThread.isAlive()) {
                Log.d(TAG, "deleteAll():waiting for loadingMappingThread stopped...");
                SystemClock.sleep(SLEEP_DELAY_1_SECOND_MS);
            }
        }

        if (countMapping(table) > 0)
            db.delete(table, null, null);


        finish = false;
        resetImInfo(table);

        if (blackListCache != null)
            blackListCache.clear();//Jeremy '12, 6,3 clear black list cache after mapping file updated 
    }


    /**
     * Counts the total number of records in the specified table.
     * 
     * <p>This method performs a simple SELECT COUNT(*) query on the table.
     * Returns 0 if the database is not ready, the table doesn't exist,
     * or if there are no records.
     * 
     * @param table The table name to count records from
     * @return The number of records in the table, or 0 if error or empty
     */
    public int countMapping(String table) {
        if (DEBUG)
            Log.i(TAG, "countMapping() on table:" + table);

        if (checkDBConnection()) return 0;

        try {

            // Validate table name before using in query
            if (!isValidTableName(table)) {
                Log.e(TAG, "countMapping(): Invalid table name: " + table);
                return 0;
            }
            Cursor cursor = db.rawQuery("SELECT * FROM " + table, null);
            if (cursor == null) return 0;
            int total = cursor.getCount();
            cursor.close();
            if (DEBUG)
                Log.i(TAG, "countMapping" + "Table," + table + ": " + total);
            return total;
        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }
        return 0;
    }

    /**
     * Gets the count of records loaded during the last file loading operation.
     * 
     * @return The number of records loaded, or 0 if no loading operation has occurred
     */
    public int getCount() {
        return count;
    }

    /**
     * Gets the progress percentage of the current file loading operation.
     * 
     * <p>This value ranges from 0 to 100, representing the percentage of the
     * file that has been processed. Useful for progress reporting during
     * mapping file imports.
     * 
     * @return Progress percentage (0-100), or 0 if no loading operation is in progress
     */
    public int getProgressPercentageDone() {
        return progressPercentageDone;
    }



    /**
     * Adds or updates a related phrase record in the database.
     * 
     * <p>This method handles user dictionary learning for related phrases:
     * <ul>
     *   <li>If the phrase doesn't exist, creates a new record with score 1</li>
     *   <li>If the phrase exists, increments the user score</li>
     *   <li>Removes Chinese symbols if learning is enabled</li>
     *   <li>Updates the total user dictionary record count</li>
     * </ul>
     * 
     * <p>The method respects the user's "learn related words" preference setting.
     * If learning is disabled and cword is not null, the method returns -1.
     * 
     * @param pword The parent word (previous word in the phrase)
     * @param cword The child word (next word in the phrase), or null for frequency tracking
     * @return The updated score after the operation, or -1 if operation was skipped
     */
    public synchronized int addOrUpdateRelatedPhraseRecord(String pword, String cword) {

        //Jeremy '12,4,17 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return -1;

        // Jeremy '11,6,12
        // Return if not learning related words and cword is not null (recording word frequency in IM relatedlist field)
        if (!mLIMEPref.getLearnRelatedWord() && cword != null) return -1;

        // Remove all the chinese symbols from the related words
        if (mLIMEPref.getLearnRelatedWord()) {
            try {
                // Remove Punctuation
                String[] chineseSymbols = ChineseSymbol.chineseSymbols.split("\\|");
                for (String s : chineseSymbols) {
                    assert cword != null;
                    cword = cword.replaceAll(s, "");
                    if (cword.isEmpty()) {
                        return -1;
                    }
                }
            }catch(Exception e){
                Log.e(TAG, "Error in database operation", e);
            }
        }

        int dictotal = Integer.parseInt(mLIMEPref.getTotalUserdictRecords());

        if (DEBUG)
            Log.i(TAG, "addOrUpdateRelatedPhraseRecord(): pword:" + pword + " cword:" + cword + "dictotoal:" + dictotal);

        int score = 1;

        ContentValues cv = new ContentValues();
        try {
            Mapping munit = this.isRelatedPhraseExistOnDB(db, pword, cword);

            if (munit == null) {
                cv.put(LIME.DB_RELATED_COLUMN_PWORD, pword);
                cv.put(LIME.DB_RELATED_COLUMN_CWORD, cword);
                cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, score);
                db.insert(LIME.DB_TABLE_RELATED, null, cv);
                dictotal++;
                mLIMEPref.setTotalUserdictRecords(String.valueOf(dictotal));
                if (DEBUG)
                    Log.i(TAG, "addOrUpdateRelatedPhraseRecord(): new record, dictotal:" + dictotal);
            } else {//the item exist in preload related database.
                Integer existingScore = relatedScore.get(munit.getId());
                if (existingScore == null) {
                    score = munit.getScore() + 1;
                    relatedScore.put(munit.getId(), score);
                } else {
                    score = existingScore + 1;
                    relatedScore.put(munit.getId(), score);
                }
                cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, score);
                db.update(LIME.DB_TABLE_RELATED, cv, FIELD_ID + " = " + munit.getId(), null);


                if (DEBUG)
                    Log.i(TAG, "addOrUpdateRelatedPhraseRecord():update score on existing record; score:" + score);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in addOrUpdateRelatedPhraseRecord() database operation", e);
        }

        return score;


    }

    /**
     * Adds or updates a mapping record in the current table with default score.
     * 
     * <p>This is a convenience method that calls
     * {@link #addOrUpdateMappingRecord(String, String, String, int)} with
     * the current tablename and a score of -1 (which triggers auto-increment).
     * 
     * @param code The input code
     * @param word The output word
     */
    public synchronized void addOrUpdateMappingRecord(String code, String word) {
        addOrUpdateMappingRecord(this.tableName, code, word, -1);
    }

    /**
     * Adds or updates a mapping record in the specified table.
     * 
     * <p>This method handles user dictionary learning for code-to-word mappings:
     * <ul>
     *   <li>If the mapping doesn't exist, creates a new record</li>
     *   <li>If the mapping exists, updates the score</li>
     *   <li>For phonetic table, also creates/updates the no-tone code column</li>
     *   <li>Removes the code from blacklist cache if it was previously blacklisted</li>
     * </ul>
     * 
     * <p>If score is -1, the method will auto-increment the existing score
     * or set it to 1 for new records.
     * 
     * @param table The table name to update (must be valid)
     * @param code The input code to map
     * @param word The output word
     * @param score The score to set, or -1 to auto-increment existing score or set to 1 for new records
     */
    public synchronized void addOrUpdateMappingRecord(String table, String code, String word, int score) {
        //String code = preProcessingRemappingCode(raw_code);  //Jeremy '12,6,4 the code is build from mapping.getcode() should not do remap again.
        if (DEBUG)
            Log.i(TAG, "addOrUpdateMappingRecord(), code = '" + code + "'. word=" + word + ", score =" + score);
        //Jeremy '12,4,17 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return;

        try {
            Mapping munit = isMappingExistOnDB(db, table, code, word);
            ContentValues cv = new ContentValues();

            if (munit == null) {
                if (!code.isEmpty() && !word.isEmpty()) {

                    cv.put(FIELD_CODE, code);
                    removeFromBlackList(code);  // remove from black list if it listed. Jeremy 12,6, 4
                    if (table.equals(LIME.DB_TABLE_PHONETIC)) {
                        String noToneCode = code.replaceAll("[ 3467]", "");
                        cv.put(FIELD_NO_TONE_CODE, noToneCode);//Jeremy '12,6,1, add missing space
                        removeFromBlackList(noToneCode); // remove from black list if it listed. Jeremy 12,6, 4
                    }
                    cv.put(FIELD_WORD, word);
                    cv.put(FIELD_SCORE, (score == -1) ? 1 : score);
                    db.insert(table, null, cv);


                    if (DEBUG)
                        Log.i(TAG, "addOrUpdateMappingRecord(): mapping does not exist, new record inserted");
                }

            } else {//the item exist in preload related database.

                int newScore = (score == -1) ? munit.getScore() + 1 : score;
                cv.put(FIELD_SCORE, newScore);
                db.update(table, cv, FIELD_ID + " = " + munit.getId(), null);
                if (DEBUG)
                    Log.i(TAG, "addOrUpdateMappingRecord(): mapping exist, update score on existing record; score:" + score);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in addOrUpdateMappingRecord() database operation", e);
        }

    }

    /**
     * Increments the score of a mapping record.
     * 
     * <p>This method increments the user score for a selected mapping:
     * <ul>
     *   <li>For related phrase records: updates the userscore in the related table</li>
     *   <li>For regular mapping records: updates the score in the IM table</li>
     * </ul>
     * 
     * <p>The score increment helps the system learn which mappings are preferred
     * by the user, improving future suggestions.
     * 
     * @param srcUnit The Mapping object containing the record to update
     */
    public synchronized void addScore(Mapping srcUnit) {

        //Jeremy '12,4,17 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return;

        //Jeremy '11,7,31  even selected from related list, update the corresponding score in im table.
        // Jeremy '11,6,12 Id=null denotes selection from related list in im table
        //Jeremy '11,9,8 query highest score first.  Erase related list if new score is not highest.
        try {

            if (srcUnit != null && srcUnit.getWord() != null &&
                    !srcUnit.getWord().trim().isEmpty()) {

                if (DEBUG) Log.i(TAG, "addScore(): addScore on word:" + srcUnit.getWord());

                if (srcUnit.isRelatedPhraseRecord()) {

                    int score;
                    Integer existingScore = relatedScore.get(srcUnit.getId());
                    if (existingScore == null) {
                        score = srcUnit.getScore() + 1;
                        relatedScore.put(srcUnit.getId(), score);
                    } else {
                        score = existingScore + 1;
                        relatedScore.put(srcUnit.getId(), score);
                    }

                    ContentValues cv = new ContentValues();
                    cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, score);
                    db.update(LIME.DB_TABLE_RELATED, cv, FIELD_ID + " = " + srcUnit.getId(), null);

                    //Log.i("TAG RELATED B", srcUnit.getId() + " : Related ADD Score :" + score);

                } else {
                    ContentValues cv = new ContentValues();
                    cv.put(FIELD_SCORE, srcUnit.getScore() + 1);
                    // Jeremy 11',7,29  update according to word instead of ID, may have multiple records matching word but with diff code/id
                    db.update(tableName, cv, FIELD_WORD + " = '" + srcUnit.getWord() + "'", null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }
    }


    /**
     * Retrieves all mapping records that match a given word.
     * 
     * <p>This method performs a reverse lookup - finding all codes that map to
     * a specific word. Results are sorted by score in descending order.
     * 
     * <p>This is useful for:
     * <ul>
     *   <li>Reverse lookup features</li>
     *   <li>Finding alternative codes for a word</li>
     *   <li>Phrase learning (getting code from word)</li>
     * </ul>
     * 
     * @param keyword The word to search for (must not be null or empty)
     * @param table The table name to search in
     * @return List of Mapping objects matching the word, or null if database error
     */
    public List<Mapping> getMappingByWord(String keyword, String table) {

        if (DEBUG)
            Log.i(TAG, "getMappingByWord(): table name:" + table + "  keyword:" + keyword);

        if (checkDBConnection()) return null;

        List<Mapping> result = new LinkedList<>();

        try {

            if (keyword != null && !keyword.trim().isEmpty()) {
                Cursor cursor;
                cursor = db.query(table, null, FIELD_WORD + " = '" + keyword + "'", null, null,
                        null, FIELD_SCORE + " DESC", null);
                if (DEBUG)
                    Log.i(TAG, "getMappingByWord():table name:" + table + "  keyword:"
                            + keyword + "  cursor.getCount:"
                            + cursor.getCount());

                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        do {
                            Mapping munit = new Mapping();
                            munit.setId(getCursorString(cursor,FIELD_ID));
                            munit.setCode(getCursorString(cursor, FIELD_CODE));
                            munit.setWord(getCursorString(cursor, FIELD_WORD));
                            munit.setExactMatchToWordRecord();
                            munit.setScore(getCursorInt(cursor, FIELD_SCORE));
                            result.add(munit);

                        } while (cursor.moveToNext());

                    }
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in getMappingByWord()", e);
        }

        if (DEBUG)
            Log.i(TAG, "getMappingByWord() Result.size() = " + result.size());


        return result;
    }
    /**
     * Performs reverse lookup to get all codes for a given keyword.
     *
     * <p>This method finds all input codes that map to the given word and
     * returns them as a formatted string with key names converted using
     * {@link #keyToKeyName(String, String, Boolean)}.
     *
     * <p>The result format is: "word=code1; code2; code3"
     *
     * <p>This method respects the reverse lookup table preference setting.
     * If reverse lookup is disabled or the table is set to "none", returns null.
     *
     * @param keyword The word to look up codes for
     * @return Formatted string of codes, or null if not found or reverse lookup disabled
     */
    public String getCodeListStringByWord(String keyword) {

        if (checkDBConnection()) return null;

        String table = mLIMEPref.getRerverseLookupTable(tableName);

        if (table.equals("none")) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        try {

            if (keyword != null && !keyword.trim().isEmpty()) {
                Cursor cursor;
                cursor = db.query(table, null, FIELD_WORD + " = '" + keyword + "'", null, null,
                        null, null, null);
                if (DEBUG)
                    Log.i(TAG, "getCodeListStringByWord():table name:" + table + "  keyword:"
                            + keyword + "  cursor.getCount:"
                            + cursor.getCount());

                if (cursor != null) {

                    if (cursor.moveToFirst()) {
                        // Use helper methods to safely get column values (validates column index >= 0)
                        String word = getCursorString(cursor, FIELD_WORD);
                        String code = getCursorString(cursor, FIELD_CODE);
                        result = new StringBuilder(word + "="
                                + keyToKeyName(code, table, false));
                        if (DEBUG)
                            Log.i(TAG, "getCodeListStringByWord():Code:" + code);


                        while (cursor.moveToNext()) {
                            result.append("; ").append(keyToKeyName(getCursorString(cursor,FIELD_CODE),
                                    table, false));
                            if (DEBUG)
                                Log.i(TAG, "getCodeListStringByWord():Code:"
                                        + getCursorString(cursor,FIELD_CODE));

                        }
                    }

                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in getCodeListStringByWord()", e);
        }

        if (DEBUG)
            Log.i(TAG, "getCodeListStringByWord() Result:" + result);

        return result.toString();
    }


    /**
     * Converts input codes into readable key names (composing text).
     * 
     * <p>This method maps keyboard keys to their phonetic/character representations:
     * <ul>
     *   <li>For phonetic: converts codes like "1qaz" to "ㄅㄆㄇㄈ"</li>
     *   <li>For dayi: converts codes to Chinese radicals</li>
     *   <li>For array: converts codes to array notation</li>
     * </ul>
     * 
     * <p>The conversion depends on:
     * <ul>
     *   <li>Table type (phonetic, dayi, array, etc.)</li>
     *   <li>Physical keyboard type (if physical keyboard is pressed)</li>
     *   <li>Phonetic keyboard type (for phonetic table)</li>
     *   <li>Whether composing text is being built (affects dual code handling)</li>
     * </ul>
     * 
     * <p>If the code length exceeds COMPOSING_CODE_LENGTH_LIMIT and composingText
     * is true, returns the original code without conversion.
     * 
     * @param code The input code to convert
     * @param table The table name (determines conversion rules)
     * @param composingText If true, this is for composing text display (may use dual codes)
     * @return The converted key name string, or original code if conversion fails
     */
    public String keyToKeyName(String code, String table, Boolean composingText) {
        //Jeremy '11,8,30 
        if (composingText && code.length() > COMPOSING_CODE_LENGTH_LIMIT)
            return code;

        String keyboardType = mLIMEPref.getPhysicalKeyboardType();
        String phonetickeyboardtype = mLIMEPref.getPhoneticKeyboardType();
        String keyTable = table;

        if (DEBUG)
            Log.i(TAG, "keyToKeyName():code:" + code +
                    " lastValidDualCodeList=" + lastValidDualCodeList +
                    " table:" + table + " tableName:" + tableName +
                    " isPhysicalKeyboardPressed:" + isPhysicalKeyboardPressed +
                    " keyboardType: " + keyboardType +
                    " composingText:" + composingText);


        if (isPhysicalKeyboardPressed) {
            if (composingText && table.equals(LIME.DB_TABLE_PHONETIC)) {// doing composing popup
                keyTable = table + keyboardType + phonetickeyboardtype;
            } else if (composingText)
                keyTable = table + keyboardType;
        } else if (composingText && tableName.equals(LIME.DB_TABLE_PHONETIC)) {
            keyTable = table + phonetickeyboardtype;
        }
        if (DEBUG)
            Log.i(TAG, "keyToKeyName():keyTable:" + keyTable);

        if (composingText) {// building composing text and get dual mapped codes		

            if (!code.equals(lastCode)) {
                // un-synchronized cache. do the preprocessing again.
                //preProcessingForExtraQueryConditions(preProcessingRemappingCode(code));
                getMappingByCode(code, false, false);
            }
            //String dualCodeList = lastValidDualCodeList;
            if (lastValidDualCodeList != null) {
                if (DEBUG)
                    Log.i(TAG, "keyToKeyName():lastValidDualCodeList:" + lastValidDualCodeList +
                            " table:" + table + " tableName:" + tableName);
                //code = dualCodeList;
                if (tableName.equals(LIME.DB_TABLE_PHONETIC)) {
                    keyTable = LIME.DB_TABLE_PHONETIC;
                    keyboardType = LIME.KEYBOARD_NORMAL;
                    phonetickeyboardtype = LIME.IM_PHONETIC_KEYBOARD_PHONETIC;
                }
                if (tableName.equals(LIME.DB_TABLE_DAYI)) {
                    keyTable = LIME.DB_TABLE_DAYI;
                    keyboardType = LIME.KEYBOARD_NORMAL;
                }

            }
        }

        if (DEBUG)
            Log.i(TAG, "keyToKeyName():code:" + code +
                    " table:" + table + " tableName:" + tableName + " keyTable:" + keyTable);

        if (keysDefMap.get(keyTable) == null
                || Objects.requireNonNull(keysDefMap.get(keyTable)).isEmpty()) {

            String keyString, keynameString, finalKeynameString = null;
            //Jeremy 11,6,4 Load keys and keynames from im table.
            keyString = getImInfo(table, "imkeys");
            keynameString = getImInfo(table, "imkeynames");

            // Force the system to use the Default KeyString for Array Keyboard
            if (table.equals(LIME.DB_TABLE_ARRAY)) {
                keyString = "";
                keynameString = "";
            }

            if (DEBUG)
                Log.i(TAG, "keyToKeyName(): load from db: imKeys:keyString=" + keyString + ", imKeynames=" + keynameString);

            if (table.equals(LIME.DB_TABLE_PHONETIC) || table.equals(LIME.DB_TABLE_DAYI) ||
                    keyString.isEmpty() || keynameString.isEmpty()) {
                switch (table) {
                    case LIME.DB_TABLE_CJ:
                    case LIME.DB_TABLE_SCJ:
                    case LIME.DB_TABLE_CJ5:
                    case LIME.DB_TABLE_ECJ:
                        keyString = CJ_KEY;
                        keynameString = CJ_CHAR;
                        break;
                    case LIME.DB_TABLE_PHONETIC:
                        if (composingText) {  // building composing text popup
                            if (phonetickeyboardtype.equals(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                                keyString = ETEN_KEY;
                                if (keyboardType.equals(MILESTONE) && isPhysicalKeyboardPressed)
                                    keynameString = MILESTONE_ETEN_CHAR;
                                else if (keyboardType.equals(MILESTONE2) && isPhysicalKeyboardPressed)
                                    keynameString = MILESTONE2_ETEN_CHAR;
                                else if (keyboardType.equals(MILESTONE3) && isPhysicalKeyboardPressed)
                                    keynameString = MILESTONE3_ETEN_CHAR;
                                else if (keyboardType.equals("desireZ") && isPhysicalKeyboardPressed)
                                    keynameString = DESIREZ_ETEN_CHAR;
                                else
                                    keynameString = ETEN_CHAR;
                            } else if (phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)) {
                                keyString = ETEN26_KEY;
                                keynameString = ETEN26_CHAR_INITIAL;
                                finalKeynameString = ETEN26_CHAR_FINAL;
                            } else if (phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU)) {
                                keyString = HSU_KEY;
                                keynameString = HSU_CHAR_INITIAL;
                                finalKeynameString = HSU_CHAR_FINAL;
                            } else if ((keyboardType.equals(MILESTONE) || keyboardType.equals(MILESTONE2))
                                    && isPhysicalKeyboardPressed) {
                                keyString = MILESTONE_KEY;
                                keynameString = MILESTONE_BPMF_CHAR;
                            } else if (keyboardType.equals(MILESTONE3) && isPhysicalKeyboardPressed) {
                                keyString = MILESTONE3_KEY;
                                keynameString = MILESTONE3_BPMF_CHAR;
                            } else if (keyboardType.equals("desireZ") && isPhysicalKeyboardPressed) {
                                keyString = DESIREZ_KEY;
                                keynameString = DESIREZ_BPMF_CHAR;
                            } else if (keyboardType.equals("chacha") && isPhysicalKeyboardPressed) {
                                keyString = CHACHA_KEY;
                                keynameString = CHACHA_BPMF_CHAR;
                            } else if (keyboardType.equals("xperiapro") && isPhysicalKeyboardPressed) {
                                keyString = XPERIAPRO_KEY;
                                keynameString = BPMF_CHAR;
                            } else {
                                keyString = BPMF_KEY;
                                keynameString = BPMF_CHAR;
                            }

                        } else {
                            keyString = BPMF_KEY;
                            keynameString = BPMF_CHAR;
                        }
                        break;
                    case LIME.DB_TABLE_ARRAY:
                        keyString = ARRAY_KEY;
                        keynameString = ARRAY_CHAR;
                        break;
                    case LIME.DB_TABLE_DAYI:
                        if (isPhysicalKeyboardPressed && composingText) { // only do this on composing mapping popup
                            switch (keyboardType) {
                                case MILESTONE:
                                case MILESTONE2:
                                    keyString = MILESTONE_KEY;
                                    keynameString = MILESTONE_DAYI_CHAR;
                                    break;
                                case MILESTONE3:
                                    keyString = MILESTONE3_KEY;
                                    keynameString = MILESTONE3_DAYI_CHAR;
                                    break;
                                case "desireZ":
                                    keyString = DESIREZ_KEY;
                                    keynameString = DESIREZ_DAYI_CHAR;
                                    break;
                                default:
                                    keyString = DAYI_KEY;
                                    keynameString = DAYI_CHAR;
                                    break;
                            }
                        } else {
                            keyString = DAYI_KEY;
                            keynameString = DAYI_CHAR;
                        }
                        break;
                }
            }
            if (DEBUG)
                Log.i(TAG, "keyToKeyname():keyboardType:" + keyboardType + " phonetickeyboardtype:" + phonetickeyboardtype +
                        " composing?:" + composingText +
                        " keyString:" + keyString + " keynameString:" + keynameString + " finalkeynameString:" + finalKeynameString);
            if (!keyString.isEmpty()) {
                HashMap<String, String> keyMap = new HashMap<>();
                HashMap<String, String> finalKeyMap = null;
                if (finalKeynameString != null)
                    finalKeyMap = new HashMap<>();

                String[] charlist = keynameString.split("\\|");
                String[] finalCharlist = null;

                if (finalKeyMap != null)
                    finalCharlist = finalKeynameString.split("\\|");

                // Ignore the exception of key name mapping.
                try {
                    for (int i = 0; i < keyString.length(); i++) {
                        keyMap.put(keyString.substring(i, i + 1), charlist[i]);
                        if (finalKeyMap != null)
                            finalKeyMap.put(keyString.substring(i, i + 1), finalCharlist[i]);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing key mapping", e);
                }

                keyMap.put("|", "|"); //put the seperator for multi-code display
                keysDefMap.put(keyTable, keyMap);
                if (finalKeyMap != null)
                    keysDefMap.put("final_" + keyTable, finalKeyMap);
            }

        }


        // Starting doing key to keyname conversion ------------------------------------
        if (keysDefMap.get(keyTable) == null
                || Objects.requireNonNull(keysDefMap.get(keyTable)).isEmpty()) {
            if (DEBUG)
                Log.i(TAG, "keyToKeyName():nokeysDefMap found!!");
            return code;

        } else {
            if (composingText &&
                    (lastValidDualCodeList != null)) //Jeremy '11,10,6 bug fixed on rmapping returning orignal code.
                code = lastValidDualCodeList;
            if (DEBUG)
                Log.i(TAG, "keyToKeyName():lastValidDualCodeList=" + lastValidDualCodeList);

            StringBuilder result = new StringBuilder();
            HashMap<String, String> keyMap = keysDefMap.get(keyTable);
            HashMap<String, String> finalKeyMap = keysDefMap.get("final_" + keyTable);
            // do the real conversion

            if (finalKeyMap == null) {
                for (int i = 0; i < code.length(); i++) {
                    assert keyMap != null;
                    String c = keyMap.get(code.substring(i, i + 1));
                    if (c != null) result.append(c);
                }
            } else {

                if (code.length() == 1) {

                    String c = "";
                    if (phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26) &&
                            (code.equals("q") || code.equals("w")
                                    || code.equals("d") || code.equals("f")
                                    || code.equals("j") || code.equals("k"))) {
                        // Dual mapped INITIALS have words mapped for ��and �� for ETEN26
                        assert keyMap != null;
                        c = keyMap.get(code);
                    } else if (phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU)) //Jeremy '12,5,31 process hsu with dual code mapping only.
                    {
                        assert keyMap != null;
                        c = keyMap.get(code);
                    }
                    //}else{
                    //	c = finalKeyMap.get(code);
                    //}
                    if (c != null) result = new StringBuilder(c.trim());
                } else {
                    for (int i = 0; i < code.length(); i++) {
                        String c;
                        if (i > 0) {
                            //Jeremy '12,6,3 If the last character is a tone symbol, the preceding will be intial
                            if (tableName.equals(LIME.DB_TABLE_PHONETIC)
                                    && i > 1
                                    && code.substring(0, i).matches(".+[sdfj ]$")
                                    && phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU)) {
                                if (DEBUG)
                                    Log.i(TAG, "preProcessingRemappingCode() hsu finalremap, subcode = " + code.substring(0, i));
                                c = keyMap.get(code.substring(i, i + 1));
                            } else if (tableName.equals(LIME.DB_TABLE_PHONETIC)
                                    && i > 1
                                    && code.substring(0, i).matches(".+[dfjk ]$")
                                    && phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)) {
                                if (DEBUG)
                                    Log.i(TAG, "preProcessingRemappingCode() hsu finalremap, subcode = " + code.substring(0, i));
                                c = keyMap.get(code.substring(i, i + 1));
                            } else
                                c = finalKeyMap.get(code.substring(i, i + 1));
                        } else {
                            assert keyMap != null;
                            c = keyMap.get(code.substring(i, i + 1));
                        }
                        if (c != null) result.append(c.trim());
                    }

                }
            }
            if (DEBUG)
                Log.i(TAG, "keyToKeyName():returning:" + result);

            if (result.toString().isEmpty()) {
                return code;
            } else {
                return result.toString();
            }
        }


    }

    private static final boolean probePerformance = false;
    
    /**
     * Retrieves mapping records that match the given code.
     * 
     * <p>This is the core query method for the IME. It performs a sophisticated
     * search that includes:
     * <ul>
     *   <li>Code preprocessing and remapping</li>
     *   <li>Dual code expansion (for physical keyboards)</li>
     *   <li>Between search (finds partial matches like "ab", "abc" when searching "abcd")</li>
     *   <li>No-tone code search (for phonetic table)</li>
     *   <li>Result sorting by exact match, code length, and score</li>
     * </ul>
     * 
     * <p>The method respects user preferences for:
     * <ul>
     *   <li>Sort suggestions (different for soft vs physical keyboard)</li>
     *   <li>Result limit (INITIAL_RESULT_LIMIT or FINAL_RESULT_LIMIT)</li>
     * </ul>
     * 
     * <p>Results are marked with exact match flags and sorted to prioritize:
     * <ol>
     *   <li>Exact matches with single-character words</li>
     *   <li>Exact matches</li>
     *   <li>Longer code matches</li>
     *   <li>Shorter code matches (up to 5 characters)</li>
     *   <li>Score and base score (if sorting enabled)</li>
     * </ol>
     * 
     * @param code The input code to search for
     * @param softKeyboard If true, uses soft keyboard sorting preference; if false, uses physical keyboard preference
     * @param getAllRecords If true, returns up to FINAL_RESULT_LIMIT records; if false, returns up to INITIAL_RESULT_LIMIT
     * @return List of Mapping objects matching the code, sorted by relevance, or null if database error
     */
    public List<Mapping> getMappingByCode(String code, boolean softKeyboard, boolean getAllRecords) {

        String codeOrig = code;

        long startTime=0;
        if (DEBUG||probePerformance) {
            startTime = System.currentTimeMillis();
            Log.i(TAG,"getMappingByCode(): code='" + code + ", table=" + tableName + ", getAllRecords=" + getAllRecords);
        }

        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return null;


        boolean sort;
        if (softKeyboard) sort = mLIMEPref.getSortSuggestions();
        else sort = mLIMEPref.getPhysicalKeyboardSortSuggestions();
        isPhysicalKeyboardPressed = !softKeyboard;

        // Add by Jeremy '10, 3, 27. Extension on multi table query.
        lastCode = code;
        lastValidDualCodeList = null; // reset the lastValidDualCodeList
        List<Mapping> result = null;

        //Two-steps query with code pre-processing. Jeremy '11,6,15
        // Step.1 Code re-mapping.  
        code = preProcessingRemappingCode(code);
        code = code.toLowerCase(Locale.US); //Jeremy '12,4,1 moved from SearchService.getMappingByCode();
        // Step.2 Build extra getMappingByCode conditions. (e.g. dualcode remap)
        Pair<String, String> extraConditions = preProcessingForExtraQueryConditions(code);
        String extraSelectClause = "";
        String extraExactMatchClause = "";
        if (extraConditions != null) {
            extraSelectClause = extraConditions.first;
            extraExactMatchClause = extraConditions.second;
        }
        //Jeremy '11,6,11 separated suggestions sorting option for physical keyboard


        try {
            if (!code.isEmpty()) {

                try {


                    Cursor cursor;
                    // Jeremy '11,8,2 Query noToneCode instead of code for code contains no tone symbols
                    // Jeremy '12,6,5 rewrite to consistent with expanddualcode
                    // Jeremy '15,6,6 always search no tone code for phonetic. The db will be upgraded in onUprade if code3r is not present

                    String codeCol = FIELD_CODE;

                    final boolean tonePresent = code.matches(".+[3467 ].*"); // Tone symbols present in any locoation except the first character
                    final boolean toneNotLast = code.matches(".+[3467 ].+"); // Tone symbols present in any locoation except the first and last character

                    if (tableName.equals(LIME.DB_TABLE_PHONETIC)) {
                        if (tonePresent) {
                            //LD phrase if tone symbols present but not in last character or in last character but the length > 4
                            // (phonetic combinations never has length >4)
                            if (toneNotLast || (code.length() > 4))
                                code = code.replaceAll("[3467 ]", "");

                        } else { // no tone symbols present, check NoToneCode column
                            codeCol = FIELD_NO_TONE_CODE;
                        }
                        code = code.trim();
                    }


                    String selectClause;
                    String sortClause;
                    String escapedCode = code.replaceAll("'", "''");
                    int codeLen = code.length();

                    String limitClause = (getAllRecords) ? FINAL_RESULT_LIMIT : INITIAL_RESULT_LIMIT;

                    //Jeremy '15, 6, 1 between search clause without using related column for better sorting order.
                    //if(betweenSearch){
                    selectClause = expandBetweenSearchClause(codeCol, code) + extraSelectClause;
                    String exactMatchCondition = " (" + codeCol + " ='" + escapedCode + "' " + extraExactMatchClause + ") ";
                    sortClause = "( exactmatch = 1 and ( score > 0 or  basescore >0) and length(word)=1) desc, exactmatch desc,"
                            + " (length(" + codeCol + ") >= " + codeLen + " ) desc, "
                            + "(length(" + codeCol + ") <= " + (Math.min(codeLen, 5)) + " )*length(" + codeCol + ") desc, ";


                    StringBuilder sortClauseBuilder = new StringBuilder(sortClause);
                    if (sort) sortClauseBuilder.append(" score desc, basescore desc, ");
                    sortClauseBuilder.append("_id asc");
                    String finalSortClause = sortClauseBuilder.toString();

                    String selectString = "select _id, code, code3r, word, score, basescore, " + exactMatchCondition + " as exactmatch  " +
                            " from " + tableName + " where word is not null and " + selectClause +
                            " order by " + finalSortClause + " limit " + limitClause;
                    cursor = db.rawQuery(selectString, null);

                    if (DEBUG)
                        Log.i(TAG, "getMappingByCode() between search select string:" + selectString);


                    // Jeremy '11,8,5 limit initial getMappingByCode to limited records
                    // Jeremy '11,6,15 Using getMappingByCode with preprocessed code and extra getMappingByCode conditions.

                    if (cursor != null) {
                        result = buildQueryResult(code, codeOrig, cursor, getAllRecords);
                        cursor.close();
                    }

                } catch (SQLiteException e) {
                    Log.e(TAG, "Error in database operation", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }

        if(DEBUG|| probePerformance){
            Log.i(TAG,"getMappingByCode() time elapsed = " + (System.currentTimeMillis() - startTime));
        }

        return result;
    }

    /**
     * Expands a search clause to include partial code matches using between search.
     * 
     * <p>This method creates a SQL WHERE clause that matches codes in a hierarchical manner:
     * <ul>
     *   <li>For code "abcd", it matches: "a", "ab", "abc", and codes starting with "abcd"</li>
     *   <li>Prefix matches are limited to the first 5 characters (or code length if shorter)</li>
     *   <li>Uses a BETWEEN-style range query (>= code AND < nextCode) for codes starting with the full code</li>
     * </ul>
     * 
     * <p>Example: For code "abc", this generates:
     * <pre>
     * code = 'a' or code = 'ab' or (code >= 'abc' and code < 'abd')
     * </pre>
     * 
     * <p>This allows finding mappings even when the user hasn't typed the complete code yet,
     * improving the user experience by showing suggestions as they type.
     * 
     * <p>The method properly escapes single quotes in the code to prevent SQL injection.
     * 
     * @param searchColumn The database column name to search in (e.g., "code" or "code3r")
     * @param code The input code to expand (will be escaped for SQL safety)
     * @return A SQL WHERE clause string with OR conditions for partial matches and a range query
     */
    private String expandBetweenSearchClause(String searchColumn, String code) {

        StringBuilder selectClause = new StringBuilder();

        int len = code.length();
        int end = (len > 5) ? 6 : len;

        if (len > 1) {
            for (int j = 0; j < end - 1; j++) {
                selectClause.append(searchColumn).append("= '").append(code.substring(0, j + 1).replaceAll("'", "''")).append("' or ");
            }
        }
        char[] chArray = code.toCharArray();
        chArray[code.length() - 1]++;
        String nextCode = new String(chArray);
        selectClause.append(" (").append(searchColumn).append(" >= '").append(code.replaceAll("'", "''")).append("' and ").append(searchColumn).append(" <'").append(nextCode.replaceAll("'", "''")).append("') ");
        if (DEBUG)
            Log.i(TAG, "expandBetweenSearchClause() selectClause: " + selectClause);
        return selectClause.toString();
    }

    /**
     * Preprocesses and remaps input codes based on keyboard type.
     * 
     * <p>This method handles keyboard-specific code remapping:
     * <ul>
     *   <li>Physical keyboard remapping (e.g., Milestone, DesireZ, ChaCha)</li>
     *   <li>Phonetic keyboard remapping (e.g., ETEN, ETEN26, HSU)</li>
     *   <li>Shifted key remapping (for soft keyboards)</li>
     * </ul>
     * 
     * <p>The remapping is cached in memory for performance. Different remapping
     * tables are used based on the combination of table name, physical keyboard type,
     * and phonetic keyboard type.
     * 
     * <p>For phonetic keyboards with dual code support (ETEN26, HSU), this method
     * handles initial/final remapping where the first character uses initial mapping
     * and subsequent characters use final mapping.
     * 
     * @param code The original input code
     * @return The remapped code, or empty string if code is null
     */
    public String preProcessingRemappingCode(String code) {
        if (DEBUG)
            Log.i(TAG, "preProcessingRemappingCode(): tableName = " + tableName + " , code=" + code);
        if (code != null) {
            String keyboardType = mLIMEPref.getPhysicalKeyboardType();
            String phoneticKeyboardType = mLIMEPref.getPhoneticKeyboardType();
            String keyString = "", keyRemapString = "", finalKeyRemapString = null;
            StringBuilder newcode = new StringBuilder(code);
            String remaptable = tableName;

            // Build cached hashmap remapping table name 
            if (isPhysicalKeyboardPressed) {
                if (tableName.equals(LIME.DB_TABLE_PHONETIC))
                    remaptable = tableName + keyboardType + phoneticKeyboardType;
                else
                    remaptable = tableName + keyboardType;
            } else if (tableName.equals(LIME.DB_TABLE_PHONETIC))
                remaptable = tableName + phoneticKeyboardType;


            // Build cached hashmap remapping table if it's not exist
            if (keysReMap.get(remaptable) == null
                    || Objects.requireNonNull(keysReMap.get(remaptable)).isEmpty()) {

                if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)) {
                    keyString = ETEN26_KEY;
                    keyRemapString = ETEN26_KEY_REMAP_INITIAL;
                    finalKeyRemapString = ETEN26_KEY_REMAP_FINAL;
                } else if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU)) {
                    keyString = HSU_KEY;
                    keyRemapString = HSU_KEY_REMAP_INITIAL;
                    finalKeyRemapString = HSU_KEY_REMAP_FINAL;
                } else if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phoneticKeyboardType.equals(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                    keyString = ETEN_KEY;
                    //+ SHIFTED_NUMBERIC_KEY + SHIFTED_SYMBOL_KEY;
                    keyRemapString = ETEN_KEY_REMAP;
                    //+ SHIFTED_NUMBERIC_ETEN_KEY_REMAP + SHIFTED_SYMBOL_ETEN_KEY_REMAP;
                } else if (isPhysicalKeyboardPressed
                        && tableName.equals(LIME.DB_TABLE_PHONETIC) && keyboardType.equals("desireZ")) {
                    //Desire Z phonetic keyboard
                    keyString = DESIREZ_KEY;
                    keyRemapString = DESIREZ_BPMF_KEY_REMAP;
                } else if (isPhysicalKeyboardPressed
                        && tableName.equals(LIME.DB_TABLE_PHONETIC) && keyboardType.equals("chacha")) {
                    //Desire Z phonetic keyboard
                    keyString = CHACHA_KEY;
                    keyRemapString = CHACHA_BPMF_KEY_REMAP;
                } else if (isPhysicalKeyboardPressed
                        && tableName.equals(LIME.DB_TABLE_PHONETIC) && keyboardType.equals("xperiapro")) {
                    //XPERIA PRO phonetic keyboard
                    keyString = XPERIAPRO_KEY;
                    keyRemapString = XPERIAPRO_BPMF_KEY_REMAP;

                } else if (!isPhysicalKeyboardPressed) {
                    if (tableName.equals(LIME.DB_TABLE_DAYI) || tableName.equals("ez")
                            || tableName.equals(LIME.DB_TABLE_PHONETIC) && phoneticKeyboardType.equals(LIME.DB_TABLE_PHONETIC)) {
                        keyString = SHIFTED_NUMBERIC_KEY + SHIFTED_SYMBOL_KEY;
                        keyRemapString = SHIFTED_NUMBERIC_KEY_REMAP + SHIFTED_SYMBOL_KEY_REMAP;
                    } else if (tableName.equals(LIME.DB_TABLE_ARRAY)) {
                        keyString = SHIFTED_SYMBOL_KEY;
                        keyRemapString = SHIFTED_SYMBOL_KEY_REMAP;
                    }

                }

                if (DEBUG)
                    Log.i(TAG, "preProcessingRemappingCode(): keyString=\"" + keyString + "\";keyRemapString=\"" + keyRemapString + "\"");


                if (!keyString.isEmpty()) {
                    HashMap<String, String> reMap = new HashMap<>();
                    HashMap<String, String> finalReMap = null;
                    if (finalKeyRemapString != null)
                        finalReMap = new HashMap<>();

                    for (int i = 0; i < keyString.length(); i++) {
                        reMap.put(keyString.substring(i, i + 1), keyRemapString.substring(i, i + 1));
                        if (finalReMap != null)
                            finalReMap.put(keyString.substring(i, i + 1), finalKeyRemapString.substring(i, i + 1));
                    }
                    keysReMap.put(remaptable, reMap);
                    if (finalReMap != null)
                        keysReMap.put("final_" + remaptable, finalReMap);
                }
            }

            if (keysReMap.get(remaptable) != null
                    && !Objects.requireNonNull(keysReMap.get(remaptable)).isEmpty()) {
                HashMap<String, String> reMap = keysReMap.get(remaptable);
                HashMap<String, String> finalReMap = keysReMap.get("final_" + remaptable);

                newcode = new StringBuilder();
                String c;

                if (finalReMap == null) {
                    for (int i = 0; i < code.length(); i++) {
                        String s = code.substring(i, i + 1);
                        assert reMap != null;
                        c = reMap.get(s);
                        newcode.append(Objects.requireNonNullElse(c, s));
                    }

                } else {

                    if (code.length() == 1) {
                        if (phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26) &&
                                (code.equals("q") || code.equals("w")
                                        || code.equals("d") || code.equals("f")
                                        || code.equals("j") || code.equals("k"))) {
                            assert reMap != null;
                            c = reMap.get(code);
                        } else if (phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU) &&
                                (code.equals("a") || code.equals("e") ||
                                        code.equals("s") || code.equals("d") || code.equals("f") || code.equals("j"))) {
                            assert reMap != null;
                            c = reMap.get(code);
                        } else {
                            c = finalReMap.get(code);
                        }
                        newcode = new StringBuilder(Objects.requireNonNullElse(c, code));

                    } else {
                        for (int i = 0; i < code.length(); i++) {
                            String s = code.substring(i, i + 1);
                            if (i > 0) {
                                //Jeremy '12,6,3 If the last character is a tone symbol, the preceding will be intial
                                if (tableName.equals(LIME.DB_TABLE_PHONETIC)
                                        && i > 1
                                        && code.substring(0, i).matches(".+[sdfj ]$")
                                        && phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU)) {
                                    if (DEBUG)
                                        Log.i(TAG, "preProcessingRemappingCode() hsu finalremap, subcode = " + code.substring(0, i));
                                    c = reMap.get(s);
                                } else if (tableName.equals(LIME.DB_TABLE_PHONETIC)
                                        && i > 1
                                        && code.substring(0, i).matches(".+[dfjk ]$")
                                        && phoneticKeyboardType.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)) {
                                    if (DEBUG)
                                        Log.i(TAG, "preProcessingRemappingCode() hsu finalremap, subcode = " + code.substring(0, i));
                                    c = reMap.get(s);
                                } else
                                    c = finalReMap.get(s);
                            } else {
                                assert reMap != null;
                                c = reMap.get(s);
                            }

                            newcode.append(Objects.requireNonNullElse(c, s));
                        }
                    }
                }
            }
            if (DEBUG)
                Log.i(TAG, "preProcessingRemappingCode():newcode=" + newcode);
            return newcode.toString();
        } else
            return "";
    }

    /**
     * Preprocesses code to build extra query conditions for dual code mapping.
     * 
     * <p>This method handles dual code mapping for physical keyboards where a single key
     * can map to multiple characters. It:
     * <ul>
     *   <li>Builds dual key mapping tables based on keyboard type (Milestone, DesireZ, etc.)</li>
     *   <li>Checks if the code contains dual-mapped characters</li>
     *   <li>Expands dual codes if needed (e.g., for phonetic codes with tone symbols in the middle)</li>
     *   <li>Returns additional SQL WHERE clause conditions for querying dual code variants</li>
     * </ul>
     * 
     * <p>Dual code mapping is used to support physical keyboards where certain keys can
     * produce different characters. For example, on a Milestone keyboard, the "q" key
     * might map to both "q" and "1" depending on context.
     * 
     * <p>The method returns a Pair containing:
     * <ul>
     *   <li>First element: Additional SELECT clause conditions (OR conditions for dual codes)</li>
     *   <li>Second element: Additional exact match conditions for dual codes</li>
     * </ul>
     * 
     * <p>If no dual code expansion is needed (code doesn't contain dual-mapped characters
     * and doesn't match expansion criteria), returns null.
     * 
     * <p>Special handling for phonetic table:
     * <ul>
     *   <li>If code has tone symbols in the middle (e.g., "a3b4"), expands dual codes</li>
     *   <li>Supports different phonetic keyboard types (ETEN, ETEN26, HSU)</li>
     * </ul>
     * 
     * <p>The dual code mapping tables are cached in memory for performance.
     * 
     * @param code The input code to process (already remapped by preProcessingRemappingCode)
     * @return Pair containing (SELECT clause, exact match clause) for dual codes, or null if no expansion needed
     */
    private Pair<String, String> preProcessingForExtraQueryConditions(String code) {
        if (DEBUG)
            Log.i(TAG, "preProcessingForExtraQueryConditions(): code = '" + code
                    + "', isPhysicalKeyboardPressed=" + isPhysicalKeyboardPressed);

        if (code != null) {
            String keyboardtype = mLIMEPref.getPhysicalKeyboardType();
            String phonetickeyboardtype = mLIMEPref.getPhoneticKeyboardType();
            StringBuilder dualcode;
            String dualKey = "";
            String dualKeyRemap = "";
            String remaptable = tableName;
            if (isPhysicalKeyboardPressed) {
                if (tableName.equals(LIME.DB_TABLE_PHONETIC))
                    remaptable = tableName + keyboardtype + phonetickeyboardtype;
                else
                    remaptable = tableName + keyboardtype;
            } else if (tableName.equals(LIME.DB_TABLE_PHONETIC)) {
                remaptable = tableName + phonetickeyboardtype;
            }


            if (keysDualMap.get(remaptable) == null
                    || Objects.requireNonNull(keysDualMap.get(remaptable)).isEmpty()) {
                if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)) {
                    dualKey = ETEN26_DUALKEY;
                    dualKeyRemap = ETEN26_DUALKEY_REMAP;
                } else if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.startsWith(LIME.IM_PHONETIC_KEYBOARD_HSU)) {
                    dualKey = HSU_DUALKEY;
                    dualKeyRemap = HSU_DUALKEY_REMAP;
                } else if (keyboardtype.equals(MILESTONE) && isPhysicalKeyboardPressed) {
                    if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                        dualKey = MILESTONE_ETEN_DUALKEY;
                        dualKeyRemap = MILESTONE_ETEN_DUALKEY_REMAP;
                    } else {
                        dualKey = MILESTONE_DUALKEY;
                        dualKeyRemap = MILESTONE_DUALKEY_REMAP;
                    }
                } else if (keyboardtype.equals(MILESTONE2) && isPhysicalKeyboardPressed) {
                    if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                        dualKey = MILESTONE2_ETEN_DUALKEY;
                        dualKeyRemap = MILESTONE2_ETEN_DUALKEY_REMAP;
                    } else {
                        dualKey = MILESTONE2_DUALKEY;
                        dualKeyRemap = MILESTONE2_DUALKEY_REMAP;
                    }
                } else if (keyboardtype.equals(MILESTONE3) && isPhysicalKeyboardPressed) {
                    if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                        dualKey = MILESTONE3_ETEN_DUALKEY;
                        dualKeyRemap = MILESTONE3_ETEN_DUALKEY_REMAP;
                    } else if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.DB_TABLE_PHONETIC)) {
                        dualKey = MILESTONE3_BPMF_DUALKEY;
                        dualKeyRemap = MILESTONE3_BPMF_DUALKEY_REMAP;
                    } else {
                        dualKey = MILESTONE3_DUALKEY;
                        dualKeyRemap = MILESTONE3_DUALKEY_REMAP;
                    }
                } else if (keyboardtype.equals("desireZ") && isPhysicalKeyboardPressed) {
                    if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                        dualKey = DESIREZ_ETEN_DUALKEY;
                        dualKeyRemap = DESIREZ_ETEN_DUALKEY_REMAP;
                    } else if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.DB_TABLE_PHONETIC)) {
                        dualKey = DESIREZ_BPMF_DUALKEY;
                        dualKeyRemap = DESIREZ_BPMF_DUALKEY_REMAP;
                    } else {
                        dualKey = DESIREZ_DUALKEY;
                        dualKeyRemap = DESIREZ_DUALKEY_REMAP;
                    }
                } else if (keyboardtype.equals("chacha") && isPhysicalKeyboardPressed) {
                    if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                        dualKey = CHACHA_ETEN_DUALKEY;
                        dualKeyRemap = CHACHA_ETEN_DUALKEY_REMAP;
                    } else if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.DB_TABLE_PHONETIC)) {
                        dualKey = CHACHA_BPMF_DUALKEY;
                        dualKeyRemap = CHACHA_BPMF_DUALKEY_REMAP;
                    } else {
                        dualKey = CHACHA_DUALKEY;
                        dualKeyRemap = CHACHA_DUALKEY_REMAP;
                    }
                } else if (keyboardtype.equals("xperiapro") && isPhysicalKeyboardPressed) {  //Jeremy '12,4,1
                    if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN)) {
                        dualKey = XPERIAPRO_ETEN_DUALKEY;
                        dualKeyRemap = XPERIAPRO_ETEN_DUALKEY_REMAP;
                    } else if (tableName.equals(LIME.DB_TABLE_PHONETIC) && phonetickeyboardtype.equals(LIME.DB_TABLE_PHONETIC)) {
                        // no dual key here
                        dualKey = "";
                        dualKeyRemap = "";
                    } else {
                        dualKey = XPERIAPRO_DUALKEY;
                        dualKeyRemap = XPERIAPRO_DUALKEY_REMAP;
                    }
                } else if (tableName.equals("ez") && !isPhysicalKeyboardPressed) { //jeremy '12,7,5 remap \ to `.
                    dualKey = "\\";
                    dualKeyRemap = "`";
                }

                HashMap<String, String> reMap = new HashMap<>();
                if (DEBUG)
                    Log.i(TAG, "preProcessingForExtraQueryConditions(): dualKey=" + dualKey + " dualKeyRemap=" + dualKeyRemap);
                for (int i = 0; i < dualKey.length(); i++) {
                    String key = dualKey.substring(i, i + 1);
                    String value = dualKeyRemap.substring(i, i + 1);
                    reMap.put(key, value);
                    reMap.put(value, value);
                }
                keysDualMap.put(remaptable, reMap);
            }
            // do real precessing now
            if (keysDualMap.get(remaptable) == null
                    || Objects.requireNonNull(keysDualMap.get(remaptable)).isEmpty()) {
                codeDualMapped = false;
                dualcode = new StringBuilder(code);
            } else {
                codeDualMapped = true;
                HashMap<String, String> reMap = keysDualMap.get(remaptable);
                dualcode = new StringBuilder();
                // testing if code contains dual mapped characters. 
                for (int i = 0; i < code.length(); i++) {
                    assert reMap != null;
                    String c = reMap.get(code.substring(i, i + 1));
                    if (c != null) dualcode.append(c);
                }
                if (DEBUG)
                    Log.i(TAG, "preProcessingForExtraQueryConditions(): dualcode=" + dualcode);


            }
            //Jeremy '11,8,12 if phonetic has tone symbol in the middle do the expanddualcode
            if (!dualcode.toString().equalsIgnoreCase(code)
                    || !code.equalsIgnoreCase(lastCode) // '11,8,18 Jeremy
                    || (tableName.equals(LIME.DB_TABLE_PHONETIC) && code.matches(".+[ 3467].+"))
                    ) {
                return expandDualCode(code, remaptable);
            }
        }
        return null;
    }

    /**
     * Builds a tree structure of all possible dual code variants for a given code.
     * 
     * <p>This method uses a tree-building algorithm to generate all possible code combinations
     * when dual key mapping is enabled. It processes the code character by character, creating
     * new variants at each level by replacing characters with their dual mappings.
     * 
     * <p>Algorithm:
     * <ol>
     *   <li>Starts with the original code at level 0</li>
     *   <li>For each character position (level i), checks if that character has a dual mapping</li>
     *   <li>If dual mapping exists, creates new code variants by replacing the character</li>
     *   <li>Builds variants level by level, checking blacklist cache to skip invalid codes</li>
     *   <li>Stops when no more dual mappings are found or codes are blacklisted</li>
     * </ol>
     * 
     * <p>Example: For code "qwe" with dual mapping q→1, w→2:
     * <ul>
     *   <li>Level 0: ["qwe"]</li>
     *   <li>Level 1: ["qwe", "1we"] (q can map to 1)</li>
     *   <li>Level 2: ["qwe", "1we", "q2e", "12e"] (w can map to 2)</li>
     *   <li>Level 3: ["qwe", "1we", "q2e", "12e"] (e has no dual mapping)</li>
     * </ul>
     * 
     * <p>Blacklist checking:
     * <ul>
     *   <li>Checks if codes are in the blacklist cache (invalid codes that return no results)</li>
     *   <li>Checks prefixes with wildcard (e.g., "ab%") to avoid expanding invalid code prefixes</li>
     *   <li>Skips codes shorter than DUALCODE_NO_CHECK_LIMIT without blacklist check</li>
     * </ul>
     * 
     * <p>Special handling for phonetic table:
     * <ul>
     *   <li>If a code has tone symbols in the middle (e.g., "a3b4"), also adds a no-tone variant</li>
     *   <li>No-tone variants are checked against blacklist before adding</li>
     * </ul>
     * 
     * <p>The resulting set contains all valid code variants that can be queried to find
     * mappings. This enables finding words even when the user types using different
     * key combinations on physical keyboards.
     * 
     * @param code The input code to build dual code variants for
     * @param keytablename The key table name used to look up dual mapping configuration
     * @return HashSet containing all valid dual code variants, including the original code
     */
    private HashSet<String> buildDualCodeList(String code, String keytablename) {

        if (DEBUG)
            Log.i(TAG, "buildDualCodeList(): code:" + code + ", keytablename=" + keytablename);

        HashMap<String, String> codeDualMap = keysDualMap.get(keytablename);
        HashSet<String> treeDualCodeList = new HashSet<>();

        if (codeDualMap != null && !codeDualMap.isEmpty()) {

            //Jeremy '12,6,4 
            SparseArray<List<String>> treemap = new SparseArray<>();
            for (int i = 0; i < code.length(); i++) {
                if (DEBUG)
                    Log.i(TAG, "buildDualCodeList() level : " + i);


                List<String> levelnMap = new LinkedList<>();
                List<String> lastLevelMap;
                if (i == 0) {
                    lastLevelMap = new LinkedList<>();
                    lastLevelMap.add(code);
                } else
                    lastLevelMap = treemap.get(i - 1);

                String c;
                String n;

                if (lastLevelMap == null || (lastLevelMap.isEmpty())) {
                    if (DEBUG)
                        Log.i(TAG, "buildDualCodeList() level : " + i + " ended because last level map is empty");
                    continue;
                }
                if (DEBUG)
                    Log.i(TAG, "buildDualCodeList() level : " + i + " lastlevelmap size = " + lastLevelMap.size());
                for (String entry : lastLevelMap) {
                    if (DEBUG)
                        Log.i(TAG, "buildDualCodeList() level : " + i + ", entry = " + entry);

                    if (entry.length() == 1) c = entry;
                    else
                        c = entry.substring(i, i + 1);


                    boolean codeMapped = false;
                    do {
                        if (DEBUG)
                            Log.i(TAG, "buildDualCodeList() newCode = '" + entry
                                    + "' blacklistKey = '" + cacheKey(entry.substring(0, i + 1) + "%")
                                    + "' blacklistValue = " + blackListCache.get(cacheKey(entry.substring(0, i + 1) + "%")));

                        if (entry.length() == 1 && !levelnMap.contains(entry)) {
                            if (blackListCache.get(cacheKey(entry)) == null)
                                treeDualCodeList.add(entry);
                            levelnMap.add(entry);
                            if (DEBUG)
                                Log.i(TAG, "buildDualCodeList() entry.length()==1 new code = '" + entry
                                        + "' added. treeDualCodeList.size = " + treeDualCodeList.size());
                            codeMapped = true;

                        } else if ((entry.length() > 1 && !levelnMap.contains(entry))
                                && blackListCache.get(cacheKey(entry.substring(0, i + 1) + "%")) == null) {
                            if (blackListCache.get(cacheKey(entry)) == null)
                                treeDualCodeList.add(entry);
                            levelnMap.add(entry);
                            if (DEBUG)
                                Log.i(TAG, "buildDualCodeList() new code = '" + entry
                                        + "' added. treeDualCodeList.size = " + treeDualCodeList.size());
                            codeMapped = true;


                        } else if (codeDualMap.get(c) != null && !Objects.equals(codeDualMap.get(c), c)) {
                            n = codeDualMap.get(c);
                            String newCode = getNewCode(entry, n, i);
                            if (DEBUG) {
                                assert newCode != null;
                                Log.i(TAG, "buildDualCodeList() newCode = '" + newCode
                                                + "' blacklistKey = '" + cacheKey(newCode)
                                                + "' blacklistValue = " + blackListCache.get(cacheKey(newCode))
                                                + "' blacklistKey = '" + cacheKey(newCode.substring(0, i + 1) + "%")
                                                + "' blacklistValue = " + blackListCache.get(cacheKey(newCode.substring(0, i + 1) + "%"))
                                );
                            }

                            assert newCode != null;
                            if (newCode.length() == 1 && !levelnMap.contains(newCode)) {
                                if (blackListCache.get(cacheKey(newCode)) == null)
                                    treeDualCodeList.add(newCode);
                                levelnMap.add(newCode);
                                if (DEBUG)
                                    Log.i(TAG, "buildDualCodeList() newCode.length()==1 treeDualCodeList new code = '" + newCode
                                            + "' added. treeDualCodeList.size = " + treeDualCodeList.size());
                                codeMapped = true;
                            } else if ((newCode.length() > 1 && !levelnMap.contains(newCode))
                                    && blackListCache.get(cacheKey(newCode.substring(0, i + 1) + "%")) == null) {
                                levelnMap.add(newCode);

                                if (blackListCache.get(cacheKey(newCode)) == null)
                                    treeDualCodeList.add(newCode);
                                if (DEBUG)
                                    Log.i(TAG, "buildDualCodeList() treeDualCodeList new code = '" + newCode
                                            + ", c = " + c
                                            + ", n = " + n
                                            + "' added. treeDualCodeList.size = " + treeDualCodeList.size());

                                codeMapped = true;

                            } else if (DEBUG)
                                Log.i(TAG, "buildDualCodeList()  blacklisted code = '" + newCode.substring(0, i + 1) + "%"
                                        + "'");

                            c = n;
                        } else {
                            if (DEBUG)
                                Log.i(TAG, "buildDualCodeList() level : " + i
                                        + " ended. treeDualCodeList.size = " + treeDualCodeList.size());
                            codeMapped = false;
                        }


                    } while (codeMapped);
                    treemap.put(i, levelnMap);


                }
            }


            //Jeremy '11,8,12 added for continuous typing.  
            if (tableName.equals(LIME.DB_TABLE_PHONETIC)) {
                HashSet<String> tempList = new HashSet<>(treeDualCodeList);
                for (String iterator_code : tempList) {
                    if (iterator_code.matches(".+[ 3467].+")) { // regular expression mathes tone in the middle
                        String newCode = iterator_code.replaceAll("[3467 ]", "");
                        //Jeremy '12,6,3 look-up the blacklist cache before add to the list.
                        if (DEBUG)
                            Log.i(TAG, "buildDualCodeList(): processing no tone code :" + newCode);
                        if (!newCode.isEmpty()
                                && !treeDualCodeList.contains(newCode)
                                && !checkBlackList(cacheKey(newCode))) {
                            treeDualCodeList.add(newCode);
                            if (DEBUG)
                                Log.i(TAG, "buildDualCodeList(): no tone code added:" + newCode);


                        }
                    }
                }
            }

        }


        if (DEBUG)
            Log.i(TAG, "buildDualCodeList(): treeDualCodeList.size()=" + treeDualCodeList.size());
        return treeDualCodeList;


    }

    private static String getNewCode(String entry, String n, int i) {
        String newCode;

        if (entry.length() == 1)
            newCode = n;
        else if (i == 0)
            newCode = n + entry.substring(1);
        else if (i == entry.length() - 1)
            newCode = entry.substring(0, entry.length() - 1) + n;
        else
            newCode = entry.substring(0, i) + n
                    + entry.substring(i + 1);
        return newCode;
    }

    /**
     * Jeremy '12,6,4 check black list on code , code + wildcard and reduced code with wildcard
     *
     * @param code blacklist query code
     * @return true if the cod is black listed
     */
    private boolean checkBlackList(String code) {
        boolean isBlacklisted = false;
        if (code.length() < DUALCODE_NO_CHECK_LIMIT) { //code too short, add anyway
            if (DEBUG)
                Log.i(TAG, "buildDualCodeList(): code too short add without check code=" + code);
        } else if (blackListCache.get(cacheKey(code)) != null) { //the code is blacklisted
            isBlacklisted = true;
            if (DEBUG)
                Log.i(TAG, "buildDualCodeList(): black listed code:" + code);
		/*}else if(blackListCache.get(cacheKey(code+"%")) != null){ //the code with wildcard is blacklisted
			if(DEBUG) 
				Log.i(TAG, "buildDualCodeList(): check black list code:"+ code 
					+ ", blackListCache.get(cacheKey(codeToCheck+%))="+blackListCache.get(cacheKey(code+"%")));
			isBlacklisted = true;
			if(DEBUG) 
				Log.i(TAG, "buildDualCodeList(): black listed code:"+ code+"%");*/
        } else {
            for (int i = DUALCODE_NO_CHECK_LIMIT - 1; i <= code.length(); i++) {
                String codeToCheck = code.substring(0, i) + "%";
                if (blackListCache.get(cacheKey(codeToCheck)) != null) {
                    isBlacklisted = true;
                    if (DEBUG)
                        Log.i(TAG, "buildDualCodeList(): black listed code:" + codeToCheck);
                    break;
                }

            }

        }
        return isBlacklisted;
    }


    /**
     * Jeremy '12,6,4 check black list on code , code + wildcard and reduced code with wildcard
     */
    private void removeFromBlackList(String code) {
        if (blackListCache.get(cacheKey(code)) != null)
            blackListCache.remove(cacheKey(code));

        for (int i = DUALCODE_NO_CHECK_LIMIT - 1; i <= code.length(); i++) {
            String codeToCheck = code.substring(0, i) + "%";
            if (blackListCache.get(cacheKey(codeToCheck)) != null)
                blackListCache.remove(cacheKey(codeToCheck));

        }


    }


    private Pair<String, String> expandDualCode(String code, String keytablename) {

        if (DEBUG)
            Log.i(TAG, "expandDualCode() code=" + code + ", keytablename = " + keytablename);

        HashSet<String> dualCodeList = buildDualCodeList(code, keytablename);
        StringBuilder selectClause = new StringBuilder();
        StringBuilder exactMatchClause = new StringBuilder();
        StringBuilder validDualCodeList = new StringBuilder();

        final boolean NOCheckOnExpand = code.length() < DUALCODE_NO_CHECK_LIMIT;
        final boolean searchNoToneCode = tableName.equals(LIME.DB_TABLE_PHONETIC);

        for (String dualcode : dualCodeList) {
            if (DEBUG)
                Log.i(TAG, "expandDualCode(): processing dual code = '" + dualcode + "'" + ". result = " + selectClause);


            String noToneCode = dualcode;
            String codeCol = FIELD_CODE;
            String[] col = {codeCol};

            if (tableName.equals(LIME.DB_TABLE_PHONETIC)) {
                final boolean tonePresent = dualcode.matches(".+[3467 ].*"); // Tone symbols present in any locoation except the first character
                final boolean toneNotLast = dualcode.matches(".+[3467 ].+"); // Tone symbols present in any locoation except the first and last character

                if (searchNoToneCode) { //noToneCode (phonetic combination without tones) is present
                    if (tonePresent) {
                        //LD phrase if tone symbols present but not in last character or in last character but the length > 4 (phonetic combinations never has length >4)
                        if (toneNotLast || (dualcode.length() > 4))
                            noToneCode = dualcode.replaceAll("[3467 ]", "");

                    } else { // no tone symbols present, check noToneCode column
                        codeCol = FIELD_NO_TONE_CODE;
                    }
                } else if (tonePresent && (toneNotLast || (dualcode.length() > 4))) //LD phrase and no noToneCode column present
                    noToneCode = dualcode.replaceAll("[3467 ]", "");
            }
            // do escape code for codes
            String queryCode = dualcode.trim().replaceAll("'", "''");
            String queryNoToneCode = noToneCode.trim().replaceAll("'", "''");


            if (queryCode.isEmpty()) continue;


            if (NOCheckOnExpand) {
                if (!dualcode.equals(code)) {
                    //result = result + " OR " + codeCol + "= '" + queryCode + "'";
                    selectClause.append(" or (").append(expandBetweenSearchClause(codeCol, dualcode)).append(") ");
                    exactMatchClause.append(" or ").append(codeCol).append(" ='").append(queryCode).append("' ");
                }
            } else {
                //Jeremy '11,8, 26 move valid code list building to buildqueryresult to avoid repeat query.
                try {
                    String selectValidCodeClause = codeCol + " = '" + queryCode + "'";
                    if (!dualcode.equals(noToneCode)) { //code with tones. should strip tone symbols and add to the select condition.
                        selectValidCodeClause = FIELD_CODE + " = '" + queryCode + "' OR " + FIELD_NO_TONE_CODE + " = '" + queryNoToneCode + "'";
                    }

                    if (DEBUG)
                        Log.i(TAG, "expandDualCode() selectClause for exactmatch = " + selectValidCodeClause);

                    Cursor cursor = db.query(tableName, col, selectValidCodeClause, null, null, null, null, "1");
                    if (cursor != null) {
                        if (cursor.moveToFirst()) { //fist entry exist, the code is valid.
                            if (DEBUG)
                                Log.i(TAG, "expandDualCode()  code = '" + dualcode + "' is valid code");
                            if (validDualCodeList.length() == 0) validDualCodeList = new StringBuilder(dualcode);
                            else validDualCodeList.append("|").append(dualcode);
                            if (!dualcode.equals(code)) {
                                //result = result + " OR " + codeCol + "= '" + queryCode + "'";
                                selectClause.append(" or (").append(expandBetweenSearchClause(codeCol, dualcode)).append(") ");
                                exactMatchClause.append(" or (").append(codeCol).append(" ='").append(queryCode).append("') ");
                            }
                        } else { //the code is not valid, keep it in the black list cache. Jeremy '12,6,3

                            char[] charray = dualcode.toCharArray();
                            charray[queryCode.length() - 1]++;
                            String nextcode = new String(charray);
                            nextcode = nextcode.replaceAll("'", "''");

                            selectValidCodeClause = codeCol + " > '" + queryCode + "' AND " + codeCol + " < '" + nextcode + "'";

                            if (!dualcode.equals(noToneCode)) { //code with tones. should strip tone symbols and add to the select condition.
                                charray = queryNoToneCode.toCharArray();
                                charray[noToneCode.length() - 1]++;
                                String nextNoToneCode = new String(charray);
                                nextNoToneCode = nextNoToneCode.replaceAll("'", "''");
                                selectValidCodeClause = "(" + codeCol + " > '" + queryCode + "' AND " + codeCol + " < '" + nextcode + "') "
                                        + "OR (" + codeCol + " > '" + queryNoToneCode + "' AND " + codeCol + " < '" + nextNoToneCode + "')";

                            }
                            cursor.close();
                            if (DEBUG)
                                Log.i(TAG, "expandDualCode() dualcode = '" + dualcode + "' noToneCode = '"
                                        + noToneCode + "' selectValidCodeClause for no exact match = " + selectValidCodeClause);


                            cursor = db.query(tableName, col, selectValidCodeClause,
                                    null, null, null, null, "1");


                            if (cursor == null || !cursor.moveToFirst()) { //code* returns no valid records add the code with wildcard to blacklist
                                blackListCache.put(cacheKey(dualcode + "%"), true);
                                // if (DEBUG)
                                Log.i(TAG, " expandDualCode() blackList wildcard code added, code = " + dualcode + "%"
                                        + ", cachekey = :" + cacheKey(dualcode + "%")
                                        + ", black list size = " + blackListCache.size()
                                        + ", blackListCache.get() = " + blackListCache.get(cacheKey(dualcode + "%")));

                            } else { //only add the code to black list
                                blackListCache.put(cacheKey(dualcode), true);
                                cursor.close();
                                if (DEBUG)
                                    Log.i(TAG, " expandDualCode() blackList code added, code = " + dualcode);
                            }


                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in database operation", e);
                }

            }
        }

        if (validDualCodeList.toString().isEmpty())
            lastValidDualCodeList = null;
        else
            lastValidDualCodeList = validDualCodeList.toString();

        if (DEBUG)
            Log.i(TAG, "expandDualCode(): result:" + selectClause + " validDualCodeList:" + validDualCodeList);
        return new Pair<>(selectClause.toString(), exactMatchClause.toString());

    }

    /**
     * Jeremy '12,6,3 Build unique cache key for black list cache.
     */

    private String cacheKey(String code) {

        return tableName + "_" + code;
    }

    /**
     * Process search results
     */
    private synchronized List<Mapping> buildQueryResult(String query_code, String codeorig, Cursor cursor, Boolean getAllRecords) {

        long startTime =0;
        if (DEBUG||probePerformance) {
            startTime = System.currentTimeMillis();
            Log.i(TAG, "buildQueryResult()");
        }


        List<Mapping> result = new ArrayList<>();


        HashSet<String> duplicateCheck = new HashSet<>();
        HashSet<String> validCodeMap = new HashSet<>();  //Jeremy '11,8,26
        int rsize = 0;
        //jeremy '11,8,30 reset lastVaidDualCodeList first.
        final boolean buildValidCodeList = lastValidDualCodeList == null;

        boolean searchNoToneColumn = tableName.equals(LIME.DB_TABLE_PHONETIC)
                && !query_code.matches(".+[3467 ].*");
        if (DEBUG) Log.i(TAG, "buildQueryResutl(): cursor.getCount()=" + cursor.getCount()
                + ". lastValidDualCodeList = " + lastValidDualCodeList);
        if (cursor.moveToFirst()) {
            int sLimit = mLIMEPref.getSimilarCodeCandidates();
            int sCount=0;
            if(DEBUG)
                Log.i(TAG,"buildQueryResult(): code=" + query_code + ", similar code limit=" + sLimit );

            do {
                String word = getCursorString(cursor,FIELD_WORD);
                //skip if word is null
                if (word == null || word.trim().isEmpty())
                    continue;
                String code = getCursorString(cursor,FIELD_CODE);
                Mapping m = new Mapping();
                m.setCode(code);
                m.setCodeorig(codeorig);
                m.setWord(word);
                m.setId(getCursorString(cursor, FIELD_ID));
                m.setScore(getCursorInt(cursor, FIELD_SCORE));
                m.setBasescore(getCursorInt(cursor, FIELD_BASESCORE));

                //String relatedlist = (betweenSearch)?null: cursor.getString(relatedColumn);

                boolean exactMatch = getCursorString(cursor, FILE_EXACT_MATCH).equals("1"); //Jeremy '15,6,3 new exact match virtual column built in query time.
                //m.setHighLighted((betweenSearch) && !exactMatch);//Jeremy '12,5,30 exact match, not from related list

                //Jeremy 15,6,3 new exact or partial record type
                if (exactMatch)
                    m.setExactMatchToCodeRecord();
                else
                    m.setPartialMatchToCodeRecord();

                //Jeremy '11,8,26 build valid code map
                //jeremy '11,8,30 add limit for valid code words for composing display
                if (buildValidCodeList) {
                    String noToneCode = getCursorString(cursor,FIELD_NO_TONE_CODE);
                    if (searchNoToneColumn && noToneCode != null
                            && noToneCode.trim().length() == query_code.replaceAll("[3467 ]", "").trim().length()
                            && validCodeMap.size() < DUALCODE_COMPOSING_LIMIT)
                        validCodeMap.add(noToneCode);
                    else if (code != null && code.length() == query_code.length())
                        validCodeMap.add(code);
                }


                // 06/Aug/2011 by Art: ignore the result when word == keyToKeyname(code)
                // Only apply to Array IM
                try {
                    if (code != null && code.length() == 1 && tableName.equals(LIME.DB_TABLE_ARRAY)) {
                        if (keyToKeyName(code, tableName, false).equals(m.getWord())) {
                            continue;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking keyToKeyname", e);
                }

                if (duplicateCheck.add(m.getWord())) {
                    result.add(m);

                    if(m.isPartialMatchToCodeRecord()) {
                        sCount ++;
                        if(sCount >sLimit) break;
                    }
                }
                rsize++;
                if(DEBUG)
                    Log.i(TAG,"buildQueryResult():  current code = " + m.getCode() + ", current word =" + m.getWord() +", similar code count=" + sCount + ", record counts" + rsize);
            } while (cursor.moveToNext());


            //Jeremy '11,8,26 build valid code map
            if (buildValidCodeList && !validCodeMap.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (String validCode : validCodeMap) {
                    if (DEBUG)
                        Log.i(TAG, "buildQueryResult(): buildValidCodeList: valicode=" + validCode);
                    if (first) {
                        sb.append(validCode);
                        first = false;
                    } else {
                        sb.append("|").append(validCode);
                    }
                }
                lastValidDualCodeList = sb.toString();
            }

        }


        // Add full shaped punctuation symbol to the third place  , and .
        if (query_code.length() == 1) {

            if ((query_code.equals(",") || query_code.equals("<")) && duplicateCheck.add("，")) {
                Mapping temp = new Mapping();
                temp.setCode(query_code);
                temp.setWord("，");
                if (result.size() > 3)
                    result.add(3, temp);
                else
                    result.add(temp);
            }
            if ((query_code.equals(".") || query_code.equals(">")) && duplicateCheck.add("。")) {
                Mapping temp = new Mapping();
                temp.setCode(query_code);
                temp.setWord("。");
                if (result.size() > 3)
                    result.add(3, temp);
                else
                    result.add(temp);
            }
        }


        Mapping hasMore = new Mapping();
        hasMore.setCode("has_more_records");
        hasMore.setWord("...");
        hasMore.setHasMoreRecordsMarkRecord();

        if (!getAllRecords && rsize == Integer.parseInt(INITIAL_RESULT_LIMIT))
            result.add(hasMore);

        if (DEBUG||probePerformance)
            Log.i(TAG, "buildQueryResult():query_code:" + query_code + " query_code.length:" + query_code.length()
                    + " result.size=" + result.size() + " query size:" + rsize + ", time elapsed = " + (System.currentTimeMillis()-startTime));
        return result;
    }

    /*
     * @return Cursor for
     *
    public Cursor getDictionaryAll() {
    //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
    if (!checkDBConnection()) return null;

    Cursor cursor;
    cursor = db.query("dictionary", null, null, null, null, null, null, null);
    return cursor;
    } */

    /**
     * Gets related phrase suggestions for a parent word.
     * 
     * <p>This method retrieves related phrase candidates that can follow the
     * given parent word. It respects the "similar enable" preference setting.
     * 
     * <p>If pword length > 1, it searches for phrases matching both the full
     * word and the last character, sorted by word length (longer first).
     * 
     * <p>Results are sorted by userscore and basescore descending, and limited
     * based on the getAllRecords parameter.
     * 
     * @param pword The parent word to get related phrases for
     * @param getAllRecords If true, returns up to FINAL_RESULT_LIMIT; if false, returns up to INITIAL_RESULT_LIMIT
     * @return List of Mapping objects containing related phrase suggestions, or empty list if disabled or error
     */
    public List<Mapping> getRelatedPhrase(String pword, boolean getAllRecords) {
        if (DEBUG)
            Log.i(TAG, "getRelatedPhrase(), " + getAllRecords);

        List<Mapping> result = new LinkedList<>();


        if (mLIMEPref.getSimiliarEnable()) {

            if (pword != null && !pword.trim().isEmpty()) {

                Cursor cursor;

                // Jeremy '11,8.23 remove group by condition to avoid sorting ordr
                // Jeremy '11,8,1 add group by cword to remove duplicate items.
                //Jeremy '11,6,12, Add constraint on cword is not null (cword =null is for recoding im related list selected count).
                //Jeremy '12,12,21 Add limitClause to limit candidates in only 1 page first.
                //					to do 2 stage query.
                //Jeremy '14,12,38 Add query on word length > 1 to include last character into query
                String limitClause;

                limitClause = (getAllRecords) ? FINAL_RESULT_LIMIT : INITIAL_RESULT_LIMIT;

                if (pword.length() > 1) {

                    String last = pword.substring(pword.length() - 1);

                    String selectString =
                            "SELECT " + FIELD_ID + ", " + FIELD_DIC_pword + ", " + FIELD_DIC_cword + ", "
                                    + LIME.DB_RELATED_COLUMN_BASESCORE + ", " + LIME.DB_RELATED_COLUMN_USERSCORE
                                    + ", length(" + FIELD_DIC_pword + ") as len FROM " + LIME.DB_TABLE_RELATED + " where "
                                    + FIELD_DIC_pword + " = '" + pword
                                    + "' or " + FIELD_DIC_pword + " = '" + last
                                    + "' and " + FIELD_DIC_cword + " is not null"
                                    + " order by len desc, " + LIME.DB_RELATED_COLUMN_USERSCORE + " desc, "
                                    + LIME.DB_RELATED_COLUMN_BASESCORE + " desc ";

                    selectString = selectString + " limit " + limitClause;

                    if (DEBUG)
                        Log.i(TAG, "getRelatedPhrase() selectString = " + selectString);

                    try {
                        cursor = db.rawQuery(selectString, null);
                    }catch(SQLiteException sqe){
                        if (DEBUG)
                            Log.e(TAG, "Error in database operation", sqe);

                        cursor = null;
                    }


                } else {
                    cursor = db.query(LIME.DB_TABLE_RELATED, null, FIELD_DIC_pword + " = '" + pword
                            + "' and " + FIELD_DIC_cword + " is not null "
                            , null, null, null, LIME.DB_RELATED_COLUMN_USERSCORE + " DESC, "
                            + LIME.DB_RELATED_COLUMN_BASESCORE + " DESC", limitClause);
                }
                if (cursor != null) {

                    if (cursor.moveToFirst()) {

                        int rsize = 0;
                        do {
                            Mapping munit = new Mapping();
                            //munit.setId(cursor.getString(cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_ID)));
                            //munit.setPword(cursor.getString(cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_PWORD)));
                            //munit.setCode("");
                            //munit.setWord(cursor.getString(cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_CWORD)));
                            //munit.setScore(cursor.getInt(cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_USERSCORE)));
                            //munit.setBasescore(cursor.getInt(cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_BASESCORE)));
                            // Before the loop (e.g., right after the query is executed)
                            //int idColIndex = cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_ID);
                            //int pwordColIndex = cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_PWORD);
                            //int cwordColIndex = cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_CWORD);
                            //int userScoreColIndex = cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_USERSCORE);
                            //int baseScoreColIndex = cursor.getColumnIndex(LIME.DB_RELATED_COLUMN_BASESCORE);


                            munit.setId(getCursorString(cursor, LIME.DB_RELATED_COLUMN_ID));
                            munit.setPword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD));
                            munit.setWord(getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD));
                            munit.setScore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE));
                            munit.setBasescore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE));
                            munit.setCode("");  munit.setRelatedPhraseRecord();
                            result.add(munit);
                            rsize++;
                        } while (cursor.moveToNext());
                        Mapping temp = new Mapping();
                        temp.setCode("has_more_records");
                        temp.setWord("...");
                        temp.setHasMoreRecordsMarkRecord();

                        if ((!getAllRecords && rsize == Integer.parseInt(INITIAL_RESULT_LIMIT)))
                            result.add(temp);
                    }
                    cursor.close();
                }
            }
        }
        return result;
    }

    /**
     * Prepares a backup of the related phrase database.
     * 
     * <p>This method attaches the source database file and copies all related
     * phrase records into it. The database connection is held during the operation.
     * 
     * @param sourcedbfile The path to the backup database file
     */
    public void prepareBackupRelatedDb(String sourcedbfile) {
        if (checkDBConnection()) return;

        holdDBConnection();
        db.execSQL("attach database '" + sourcedbfile + "' as sourceDB");
        db.execSQL("insert into sourceDB." + LIME.DB_TABLE_RELATED + " select * from " + LIME.DB_TABLE_RELATED);
        db.execSQL("detach database sourceDB");
        unHoldDBConnection();
    }

    /**
     * Prepares a backup of a mapping table database.
     * 
     * <p>This method attaches the source database file and copies:
     * <ul>
     *   <li>All records from the specified table</li>
     *   <li>IM information for the table</li>
     * </ul>
     * 
     * <p>The database connection is held during the operation.
     * 
     * @param sourcedbfile The path to the backup database file
     * @param sourcetable The table name to backup
     */
    public void prepareBackupDb(String sourcedbfile, String sourcetable) {
        if (checkDBConnection()) return;

        holdDBConnection();
        db.execSQL("attach database '" + sourcedbfile + "' as sourceDB");
        db.execSQL("insert into sourceDB." + LIME.DB_TABLE_CUSTOM + " select * from " + sourcetable);
        db.execSQL("insert into sourceDB." + LIME.DB_TABLE_IM + " select * from " + LIME.DB_TABLE_IM + " WHERE code='" + sourcetable + "'");
        db.execSQL("update sourceDB." + LIME.DB_TABLE_IM + " set " + LIME.DB_IM_COLUMN_CODE + "='" + sourcetable + "'");
        db.execSQL("detach database sourceDB");
        unHoldDBConnection();
    }

    /**
     * Imports related phrase data from a backup database file.
     * 
     * <p>This method:
     * <ul>
     *   <li>Deletes all existing related phrase records</li>
     *   <li>Attaches the backup database</li>
     *   <li>Copies all records from the backup</li>
     *   <li>Detaches the backup database</li>
     * </ul>
     * 
     * <p>The database connection is held during the operation.
     * 
     * @param sourcedbfile The backup database file to import from
     */
    public void importBackupRelatedDb(File sourcedbfile) {
        if (checkDBConnection()) return;

        // Reset IM Info
        deleteAll(LIME.DB_TABLE_RELATED);

        holdDBConnection();

        // Load data from DB File
        db.execSQL("attach database '" + sourcedbfile + "' as sourceDB");
        db.execSQL("insert into " + LIME.DB_TABLE_RELATED + " select * from sourceDB." + LIME.DB_TABLE_RELATED);
        db.execSQL("detach database sourceDB");
        unHoldDBConnection();
    }

    /**
     * Imports mapping data from a backup database file.
     * 
     * <p>This method:
     * <ul>
     *   <li>Deletes all existing records from the IM table</li>
     *   <li>Deletes IM information for the IM type</li>
     *   <li>Attaches the backup database</li>
     *   <li>Copies custom table records</li>
     *   <li>Copies and updates IM information</li>
     *   <li>Detaches the backup database</li>
     * </ul>
     * 
     * <p>The database connection is held during the operation.
     * 
     * @param sourceDBFile The backup database file to import from
     * @param imType The IM type to import (e.g., "custom")
     */
    public void importBackupDb(File sourceDBFile, String imType) {
        if (checkDBConnection()) return;

        // Reset IM Info
        deleteAll(imType);
        db.execSQL("delete from " + LIME.DB_TABLE_IM + " where " + LIME.DB_IM_COLUMN_CODE + "='" + imType + "'");

        holdDBConnection();

        // Load data from DB File
        db.execSQL("attach database '" + sourceDBFile + "' as sourceDB");
        db.execSQL("insert into " + imType + " select * from sourceDB." + LIME.DB_TABLE_CUSTOM);
        db.execSQL("update sourceDB." + LIME.DB_TABLE_IM + " set " + LIME.DB_IM_COLUMN_CODE + "='" + imType + "'");
        db.execSQL("insert into " + LIME.DB_TABLE_IM + " select * from sourceDB." + LIME.DB_TABLE_IM);
        db.execSQL("detach database sourceDB");
        unHoldDBConnection();
    }

    /**
     * Imports database data from another database file.
     * 
     * <p>This method imports both the mapping table and IM information from
     * the source database. The database connection is held during the operation.
     * 
     * @param sourceDBFile The path to the source database file
     * @param imType The IM type to import
     */
    public void importDb(String sourceDBFile, String imType) {
        if (checkDBConnection()) return;

        deleteAll(imType);
        holdDBConnection();
        db.execSQL("attach database '" + sourceDBFile + "' as sourceDB");
        db.execSQL("insert into " + imType + " select * from sourceDB." + imType);
        db.execSQL("insert into " + LIME.DB_TABLE_IM + " select * from sourceDB." + LIME.DB_TABLE_IM);
        db.execSQL("detach database sourceDB");
        unHoldDBConnection();

        countMapping(imType);
    }

    /**
     * Backs up user-learned records to a backup table.
     * 
     * <p>This method creates a backup table (table + "_user") containing all
     * records from the specified table that have a score > 0. The backup table
     * can be used to restore user data after reloading a mapping file.
     * 
     * <p>The backup table is created as a copy of the query results, sorted
     * by score descending.
     * 
     * @param table The table name to backup user records from
     */
    public void backupUserRecords(final String table) {
        if (checkDBConnection()) return;
        String backupTableName = table + "_user";

        String selectString = "select * from " + table +
                " where " + FIELD_WORD + " is not null and " +
                FIELD_SCORE + " >0 order by " + FIELD_SCORE + " desc";
        Cursor cursor = db.rawQuery(selectString, null);

        if (cursor != null && cursor.getCount() > 0) {
            cursor.close();
            try {
                db.execSQL("drop table " + backupTableName);
            } catch (Exception e) {
                Log.e(TAG, "Error removing table " + backupTableName, e);
            }
            db.execSQL("create table " + backupTableName + " as " + selectString);
        }

        countMapping(backupTableName);
    }



    /**
     * Checks if a backup table exists and has records.
     * 
     * <p>This method queries the backup table (table + "_user") to determine
     * if user data backup exists and contains records.
     * 
     * @param table The base table name to check backup for
     * @return true if backup table exists and has records, false otherwise
     */
    public boolean checkBackuptable(String table) {
        if (checkDBConnection()) return false;
        try {
            String backupTableName = table + "_user";
            Cursor cursor = db.rawQuery("select COUNT(*) as total from " + backupTableName, null);

            cursor.moveToFirst();

            int total = getCursorInt(cursor, "total");//cursor.getInt(cursor.getColumnIndex("total"));
            if ( total> 0) {
                Log.i("LIME", "Total size :" + total);
                return true;
            } else {
                return false;
            }
        } catch (SQLiteException s) {
            Log.e(TAG, "Error checking database table existence", s);
            return false;
        }
    }


    /**
     * Loads a mapping file into the database table.
     * 
     * <p>This method performs a complete import of a mapping file:
     * <ul>
     *   <li>Reads the file line by line</li>
     *   <li>Identifies the delimiter (comma, tab, pipe, or space)</li>
     *   <li>Supports .cin format files</li>
     *   <li>Parses code, word, score, and basescore</li>
     *   <li>Gets basescore from han converter if not provided</li>
     *   <li>Inserts records in a transaction for performance</li>
     *   <li>Updates IM information (name, source, amount, import date)</li>
     *   <li>Configures keyboard layout for the IM</li>
     * </ul>
     * 
     * <p>The loading operation runs in a background thread and reports progress
     * through the provided LIMEProgressListener. The database connection is held
     * during loading to prevent concurrent access.
     * 
     * <p>Supported file formats:
     * <ul>
     *   <li>Text files with delimiters: comma, tab, pipe (|), or space</li>
     *   <li>.cin format files (CIN input method format)</li>
     * </ul>
     * 
     * <p>File format: code[delimiter]word[delimiter]score[delimiter]basescore
     * 
     * @param table The table name to load data into (must be valid)
     * @param progressListener Listener for progress updates and completion notification.
     *                        Can be null if progress reporting is not needed.
     * @throws IllegalStateException if filename is not set via setFilename()
     */
    public synchronized void loadFileV2(final String table, final LIMEProgressListener progressListener) {

        if (DEBUG)
            Log.i(TAG, "loadFileV2()");
        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) {
            if (progressListener != null) {
                progressListener.onError(-1, "Database is not available. Please try to do it later");
            }
            return;
        }

        finish = false;
        progressPercentageDone = 0;
        count = 0;
        if (loadingMappingThread != null) {
            //threadAborted = true;
            while (loadingMappingThread.isAlive()) {
                Log.d(TAG, "loadFile():waiting for last loading loadingMappingThread stopped...");
                SystemClock.sleep(SLEEP_DELAY_1_SECOND_MS);
            }
            loadingMappingThread = null;
        }

        loadingMappingThread = new Thread() {

            public void run() {

                String delimiter_symbol = "";

                // Reset Database Table		
                //SQLiteDatabase db = getSqliteDb(false);
                if (DEBUG)
                    Log.i(TAG, "loadFileV2 loadingMappingThread starting...");


                try {
                    if (countMapping(table) > 0) db.delete(table, null, null);

                    if (table.equals(LIME.DB_TABLE_PHONETIC)) {
                        if (DEBUG) Log.i(TAG, "loadfile(), build code3r index.");
                        mLIMEPref.setParameter("checkLDPhonetic", "doneV2");
                        db.execSQL("CREATE INDEX phonetic_idx_code3r ON phonetic(code3r)");

                    }
                } catch (Exception e1) {
                    Log.e(TAG, "Error in database operation", e1);

                }


                resetImInfo(table);
                boolean isCinFormat = false;

                // Check if filename is null
                if (filename == null) {
                    Log.e(TAG, "loadFileV2: filename is null");
                    if (progressListener != null) {
                        progressListener.onError(-1, "Source file is not specified.");
                    }
                    return;
                }

                String imname = "";
                String line;
                String endkey = "";
                String selkey = "";
                String spacestyle = "";
                StringBuilder imkeys = new StringBuilder();
                StringBuilder imkeynames = new StringBuilder();


                // Check if source file is .cin format
                if (filename.getName().toLowerCase(Locale.US).endsWith(".cin")) {
                    isCinFormat = true;
                }

                // Base on first 100 line to identify the Delimiter
                try {
                    // Prepare Source File
                    FileReader fr = new FileReader(filename);
                    BufferedReader buf = new BufferedReader(fr);
                    int i = 0;
                    final int maxLinesToProcess = 100; // Maximum lines to process in a batch
                    List<String> templist = new ArrayList<>();
                    while ((line = buf.readLine()) != null
                            && !isCinFormat) {
                        templist.add(line);
                        if (i >= maxLinesToProcess) {
                            break;
                        } else {
                            i++;
                        }
                    }
                    delimiter_symbol = identifyDelimiter(templist);
                    templist.clear();
                    buf.close();
                    fr.close();
                } catch (Exception e) {
                    Log.e(TAG, "Source file reading error", e);
                    if (progressListener != null) {
                        progressListener.onError(-1, "Source file reading error.");
                    }
                }


                //HashSet<String> codeList = new HashSet<>();

                //db = getSqliteDb(false);

                //Jeremy '12,4,10 db will locked after beginTransaction();

                //Jeremy '15,5,23 new database on hold mechanism.
                holdDBConnection();
                db.beginTransaction();

                try {
                    // Prepare Source File
                    progressStatus = mContext.getResources().getText(R.string.setup_load_migrate_import).toString();
                    long fileLength = filename.length();
                    long processedLength = 0;
                    FileReader fr = new FileReader(filename);
                    BufferedReader buf = new BufferedReader(fr);
                    boolean firstline = true;
                    boolean inChardefBlock = false;
                    boolean inKeynameBlock = false;
                    //String precode = "";

                    while ((line = buf.readLine()) != null && !threadAborted) {
                        processedLength += line.getBytes().length + 2; // +2 for the eol mark.
                        progressPercentageDone = (int) ((float) processedLength / (float) fileLength * LIME.PROGRESS_COMPLETE_PERCENT);

                        //Log.i(TAG, line + " / " + delimiter_symbol.equals(" ") + " / " + line.indexOf(delimiter_symbol));
                        //if(DEBUG)
                        //	Log.i(TAG, "loadFile():loadFile()"+ progressPercentageDone +"% processed"
                        //			+ ". processedLength:" + processedLength + ". fileLength:" + fileLength + ", threadAborted=" + threadAborted);
                        if (progressPercentageDone > 99) progressPercentageDone = 99;

                        if (delimiter_symbol.equals(" ") && !line.contains(delimiter_symbol)) {
                            continue;
                        }

                        if (delimiter_symbol.equals(" ")) {
                            line = line.replaceAll(" {5}", " ");
                            line = line.replaceAll(" {4}", " ");
                            line = line.replaceAll(" {3}", " ");
                            line = line.replaceAll(" {2}", " ");
                        }

                        if (line.length() < 3) {
                            continue;
                        }

						/*
						 * If source is cin format start from the tag %chardef
						 * begin until %chardef end
						 */
                        if (isCinFormat) {
                            boolean bChardef = line.trim().toLowerCase(Locale.US).startsWith("%chardef");
                            boolean bKeyname = line.trim().toLowerCase(Locale.US).startsWith("%keyname");
                            if (!(inChardefBlock || inKeynameBlock)) {
                                // Modified by Jeremy '10, 3, 28. Some .cin have
                                // double space between $chardef and begin or
                                // end
                                boolean bBegin = line.trim().toLowerCase(Locale.US).endsWith("begin");
                                if (bChardef && bBegin
                                        ) {
                                    inChardefBlock = true;
                                }
                                if (bKeyname && bBegin
                                        ) {
                                    inKeynameBlock = true;
                                }
                                // Add by Jeremy '10, 3 , 27
                                // use %cname as mapping_version of .cin
                                // Jeremy '11,6,5 add selkey, endkey and spacestyle support
                                if (!(line.trim().toLowerCase(Locale.US).startsWith("%cname")
                                        || line.trim().toLowerCase(Locale.US).startsWith("%selkey")
                                        || line.trim().toLowerCase(Locale.US).startsWith("%endkey")
                                        || line.trim().toLowerCase(Locale.US).startsWith("%spacestyle")
                                )) {
                                    continue;
                                }
                            }
                            boolean bEnd = line.trim().toLowerCase(Locale.US).endsWith("end");
                            if (bKeyname && bEnd
                                    ) {
                                inKeynameBlock = false;
                                continue;
                            }
                            if (bChardef && bEnd
                                    ) {
                                break;
                            }
                        }

                        // Check if file contain BOM MARK at file header
                        if (firstline) {
                            byte[] srcstring = line.getBytes();
                            if (srcstring.length > 3) {
                                if (srcstring[0] == -17 && srcstring[1] == -69
                                        && srcstring[2] == -65) {
                                    byte[] tempstring = new byte[srcstring.length - 3];
                                    //int a = 0;
                                    System.arraycopy(srcstring, 3, tempstring, 0, srcstring.length - 3);
                                    line = new String(tempstring);
                                }
                            }
                            firstline = false;
                        } else if (line.trim().isEmpty()) {
                            continue;
                        }
                        //else { line.length() }

                        try {

                            int source_score = 0, source_basescore = 0;
                            String code = null, word = null;
                            if (isCinFormat) {
                                if (line.contains("\t")) {
                                    try {
                                        code = line.split("\t")[0];
                                        word = line.split("\t")[1];
                                    } catch (Exception e) {
                                        if (DEBUG) Log.e(TAG, "Error parsing line with tab delimiter: " + line, e);
                                        continue;
                                    }
                                    try {
                                        // Simply ignore error and try to load score and basescore values
                                        source_score = Integer.parseInt(line.split("\t")[2]);
                                        source_basescore = Integer.parseInt(line.split("\t")[3]);
                                    } catch (Exception e) {
                                        if (DEBUG) Log.e(TAG, "Error parsing score values from line: " + line, e);
                                    }
                                } else if (line.contains(" ")) {
                                    try {
                                        code = line.split(" ")[0];
                                        word = line.split(" ")[1];
                                    } catch (Exception e) {
                                        if (DEBUG) Log.e(TAG, "Error parsing line with space delimiter: " + line, e);
                                        continue;
                                    }
                                    try {
                                        // Simply ignore error and try to load score and basescore values
                                        source_score = Integer.parseInt(line.split(" ")[2]);
                                        source_basescore = Integer.parseInt(line.split(" ")[3]);
                                    } catch (Exception e) {
                                        if (DEBUG) Log.e(TAG, "Error parsing score values from line: " + line, e);
                                    }
                                }
                            } else {
                                if (delimiter_symbol.equals("|")) {
                                    try {
                                        code = line.split("\\|")[0];
                                        word = line.split("\\|")[1];
                                    } catch (Exception e) {
                                        if (DEBUG) Log.e(TAG, "Error parsing line with pipe delimiter: " + line, e);
                                        continue;
                                    }
                                    try {
                                        // Simply ignore error and try to load score and basescore values
                                        source_score = Integer.parseInt(line.split("\\|")[2]);
                                        source_basescore = Integer.parseInt(line.split("\\|")[3]);
                                    } catch (Exception e) {
                                        if (DEBUG) Log.e(TAG, "Error parsing score values from line: " + line, e);
                                    }
                                } else {
                                    try {
                                        code = line.split(delimiter_symbol)[0];
                                        word = line.split(delimiter_symbol)[1];
                                    } catch (Exception e) {
                                        if (DEBUG) Log.e(TAG, "Error parsing line with delimiter: " + line, e);
                                        continue;
                                    }
                                    try {
                                        // Simply ignore error and try to load score and basescore values
                                        source_score = Integer.parseInt(line.split(delimiter_symbol)[2]);
                                        source_basescore = Integer.parseInt(line.split(delimiter_symbol)[3]);
                                    } catch (Exception e) {
                                        if (DEBUG) Log.e(TAG, "Error parsing score values from line: " + line, e);
                                    }
                                }

                            }
                            if (code == null || code.trim().isEmpty()) {
                                continue;
                            } else {
                                code = code.trim();
                            }
                            if (word == null || word.trim().isEmpty()) {
                                continue;
                            } else {
                                word = word.trim();
                            }
                            if (code.toLowerCase(Locale.US).contains("@version@")) {
                                imname = word.trim();
                                continue;
                            } else if (code.toLowerCase(Locale.US).contains("%cname")) {
                                imname = word.trim();
                                continue;
                            } else if (code.toLowerCase(Locale.US).contains("%selkey")) {
                                selkey = word.trim();
                                if (DEBUG) Log.i(TAG, "loadfile(): selkey:" + selkey);
                                continue;
                            } else if (code.toLowerCase(Locale.US).contains("%endkey")) {
                                endkey = word.trim();
                                if (DEBUG) Log.i(TAG, "loadfile(): endkey:" + endkey);
                                continue;
                            } else if (code.toLowerCase(Locale.US).contains("%spacestyle")) {
                                spacestyle = word.trim();
                                continue;
                            } else {
                                code = code.toLowerCase(Locale.US);
                            }

                            if (inKeynameBlock) {  //Jeremy '11,6,5 preserve keyname blocks here.
                                imkeys.append(code.toLowerCase(Locale.US).trim());
                                String c = word.trim();
                                if (!c.isEmpty()) {
                                    if (imkeynames.length() == 0)
                                        imkeynames = new StringBuilder(c);
                                    else
                                        imkeynames.append("|").append(c);
                                }

                            } else {
                                count++;
                                ContentValues cv = new ContentValues();
                                cv.put(FIELD_CODE, code);

                                if (table.equals(LIME.DB_TABLE_PHONETIC)) {
                                    cv.put(FIELD_NO_TONE_CODE, code.replaceAll("[3467 ]", ""));
                                }
                                cv.put(FIELD_WORD, word);
                                cv.put(FIELD_SCORE, source_score);
                                if (source_basescore == 0) {
                                    source_basescore = getBaseScore(word);
                                }
                                cv.put(FIELD_BASESCORE, source_basescore);
                                db.insert(table, null, cv);
                            }

                        } catch (StringIndexOutOfBoundsException e) {
                            if (DEBUG) Log.e(TAG, "String index out of bounds", e);
                        }
                    }

                    buf.close();
                    fr.close();

                    db.setTransactionSuccessful();
                } catch (Exception e) {
                    setImInfo(table, "amount", "0");
                    setImInfo(table, "source", "Failed!!!");
                    Log.e(TAG, "Error in database operation", e);
                    if (progressListener != null) {
                        progressListener.onError(-1, "Table file import failed!");
                    }
                } finally {
                    if (DEBUG) Log.i(TAG, "loadfile(): main import loop final section");
                    db.endTransaction();
                    //mLIMEPref.holdDatabaseCoonection(false); // Jeremy '12,4,10 reset mapping_loading status
                    unHoldDBConnection();

                }

                // Fill IM information into the IM Table
                if (!threadAborted && filename != null) {
                    progressPercentageDone = LIME.PROGRESS_COMPLETE_PERCENT;
                    finish = true;

                    mLIMEPref.setParameter("_table", "");

                    setImInfo(table, "source", filename.getName());
                    if (imname.isEmpty()) {
                        setImInfo(table, "name", filename.getName());
                    } else {
                        setImInfo(table, "name", imname);
                    }
                    setImInfo(table, "amount", String.valueOf(count));
                    setImInfo(table, "import", new Date().toString()); //Jeremy '12,4,21 toLocaleString() is deprecated

                    if (DEBUG)
                        Log.i("limedb:loadfile()", "Fianlly section: source:"
                                + getImInfo(table, "source") + " amount:" + getImInfo(table, "amount"));

                    // If user download from LIME Default IM SET then fill in related information
                    if (filename.getName().equals("phonetic.lime") || filename.getName().equals("phonetic_adv.lime")) {
                        setImInfo(LIME.DB_TABLE_PHONETIC, "selkey", "123456789");
                        setImInfo(LIME.DB_TABLE_PHONETIC, "endkey", "3467'[]\\=<>?:\"{}|~!@#$%^&*()_+");
                        setImInfo(LIME.DB_TABLE_PHONETIC, "imkeys", ",-./0123456789;abcdefghijklmnopqrstuvwxyz'[]\\=<>?:\"{}|~!@#$%^&*()_+");
                        setImInfo(LIME.DB_TABLE_PHONETIC, "imkeynames", "ㄝ|ㄦ|ㄡ|ㄥ|ㄢ|ㄅ|ㄉ|ˇ|ˋ|ㄓ|ˊ|˙|ㄚ|ㄞ|ㄤ|ㄇ|ㄖ|ㄏ|ㄎ|ㄍ|ㄑ|ㄕ|ㄘ|ㄛ|ㄨ|ㄜ|ㄠ|ㄩ|ㄙ|ㄟ|ㄣ|ㄆ|ㄐ|ㄋ|ㄔ|ㄧ|ㄒ|ㄊ|ㄌ|ㄗ|ㄈ|、|「|」|＼|＝|，|。|？|：|；|『|』|│|～|！|＠|＃|＄|％|︿|＆|＊|（|）|－|＋");
                    }
                    if (filename.getName().equals("array.lime")) {
                        setImInfo(LIME.DB_TABLE_ARRAY, "selkey", "1234567890");
                        setImInfo(LIME.DB_TABLE_ARRAY, "imkeys", "abcdefghijklmnopqrstuvwxyz./;,?*#1#2#3#4#5#6#7#8#9#0");
                        setImInfo(LIME.DB_TABLE_ARRAY, "imkeynames", "1-|5⇣|3⇣|3-|3⇡|4-|5-|6-|8⇡|7-|8-|9-|7⇣|6⇣|9⇡|0⇡|1⇡|4⇡|2-|5⇡|7⇡|4⇣|2⇡|2⇣|6⇡|1⇣|9⇣|0⇣|0-|8⇣|？|＊|1|2|3|4|5|6|7|8|9|0");
                    } else {
                        if (!selkey.isEmpty()) setImInfo(table, "selkey", selkey);
                        if (!endkey.isEmpty()) setImInfo(table, "endkey", endkey);
                        if (!spacestyle.isEmpty()) setImInfo(table, "spacestyle", spacestyle);
                        if (!imkeys.toString().isEmpty()) setImInfo(table, "imkeys", imkeys.toString());
                        if (!imkeynames.toString().isEmpty()) setImInfo(table, "imkeynames", imkeynames.toString());
                    }
                    if (DEBUG)
                        Log.i(TAG, "loadFileV2():update IM info: imkeys:" + imkeys + " imkeynames:" + imkeynames);


                    // '11,5,23 by Jeremy: Preset keyboard info. by tablename
                    KeyboardObj kobj = getKeyboardObj(table);
                    if (table.equals(LIME.DB_TABLE_PHONETIC)) {
                        String selectedPhoneticKeyboardType =
                                mLIMEPref.getParameterString("phonetic_keyboard_type", LIME.DB_TABLE_PHONETIC);
                        switch (selectedPhoneticKeyboardType) {
                            case LIME.DB_TABLE_PHONETIC:
                                kobj = getKeyboardObj(LIME.DB_TABLE_PHONETIC);
                                break;
                            case LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN:
                                kobj = getKeyboardObj("phoneticet41");
                                break;
                            case LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26:
                                if (mLIMEPref.getParameterBoolean("number_row_in_english", false)) {
                                    kobj = getKeyboardObj("limenum");
                                } else {
                                    kobj = getKeyboardObj("lime");
                                }
                                break;
                            case "eten26_symbol":
                                kobj = getKeyboardObj("et26");
                                break;
                            case LIME.IM_PHONETIC_KEYBOARD_HSU:  //Jeremy '12,7,6 Add HSU english keyboard support
                                if (mLIMEPref.getParameterBoolean("number_row_in_english", false)) {
                                    kobj = getKeyboardObj("limenum");
                                } else {
                                    kobj = getKeyboardObj("lime");
                                }
                                break;
                            case "hsu_symbol":
                                kobj = getKeyboardObj(LIME.IM_PHONETIC_KEYBOARD_HSU);
                                break;
                        }
                    } else if (table.equals(LIME.DB_TABLE_DAYI)) {
                        kobj = getKeyboardObj("dayisym");
                    } else if (table.equals(LIME.DB_TABLE_CJ5)) {
                        kobj = getKeyboardObj("cj");
                    } else if (table.equals(LIME.DB_TABLE_ECJ)) {
                        kobj = getKeyboardObj("cj");
                    } else if (table.equals(LIME.DB_TABLE_ARRAY)) {
                        kobj = getKeyboardObj("arraynum");
                    } else if (table.equals("array10")) {
                        kobj = getKeyboardObj("phonenum");
                    } else if (table.equals("wb")) {
                        kobj = getKeyboardObj("wb");
                    } else if (table.equals("hs")) {
                        kobj = getKeyboardObj("hs");
                    } else if (kobj == null) {    //Jeremy '12,5,21 chose english with number keyboard if the optione is on for default keyboard.
                        if (mLIMEPref.getParameterBoolean("number_row_in_english", true)) {
                            kobj = getKeyboardObj("limenum");
                        } else {
                            kobj = getKeyboardObj("lime");
                        }
                    }
                    setIMKeyboard(table, kobj.getDescription(), kobj.getCode());
                }

                //finishing

            }
        };


        Thread reportProgressThread = new Thread() {
            public void run() {
                if (progressListener == null) {
                    // If no progress listener, just wait for loading thread to complete
                    while (loadingMappingThread.isAlive()) {
                        SystemClock.sleep(SLEEP_DELAY_1_SECOND_MS);
                    }
                    return;
                }

                long interval = progressListener.progressInterval();
                while (loadingMappingThread.isAlive()) {
                    SystemClock.sleep(interval);
                    progressListener.onProgress(progressPercentageDone, 0, progressStatus);
                }
                progressPercentageDone = 100;
                progressListener.onPostExecute(true, null, 0);

            }

        };


        threadAborted = false;
        loadingMappingThread.start();
        reportProgressThread.start();
    }


    /**
     * Identifies the delimiter used in a mapping file.
     * 
     * <p>This method analyzes the first lines of a file to determine which
     * delimiter is used: comma, tab, pipe (|), or space. It counts occurrences
     * of each delimiter and returns the most common one.
     * 
     * <p>This is used during file loading to correctly parse the file format.
     * 
     * @param src List of sample lines from the file (typically first 100 lines)
     * @return The identified delimiter: ",", "\t", "|", or " " (space)
     */
    public String identifyDelimiter(List<String> src) {

        int commaCount = 0;
        int tabCount = 0;
        int pipeCount = 0;
        int spaceCount = 0;

        for (String line : src) {
            if (line.contains("\t")) {
                tabCount++;
            }
            if (line.contains(",")) {
                commaCount++;
            }
            if (line.contains("|")) {
                pipeCount++;
            }
            if (line.contains(" ")) {
                spaceCount++;
            }
        }
        if (commaCount >= tabCount && commaCount >= pipeCount && commaCount >= spaceCount) {
            return ",";
        } else if (tabCount >= commaCount && tabCount >= pipeCount && tabCount >= spaceCount) {
            return "\t";
        } else if (pipeCount >= tabCount && pipeCount >= commaCount && pipeCount >= spaceCount) {
            return "|";
        } else {
            return " ";
        }

    }

   /* */

    /**
     * Checks if a specific mapping exists in the database.
     * 
     * <p>This private method queries the database to find a mapping record
     * matching the given code and optionally word. Used internally during
     * add/update operations to determine if a record already exists.
     * 
     * @param db The database to query
     * @param table The table name to search
     * @param code The input code to search for
     * @param word The output word to search for, or null/empty to match any word
     * @return Mapping object if found, null otherwise
     */
    private Mapping isMappingExistOnDB(SQLiteDatabase db, String table, String code, String word) {
        if (DEBUG)
            Log.i(TAG, "isMappingExistOnDB(), code = '" + code + "'");
        Mapping munit = null;
        if (code != null && !code.trim().isEmpty()) {


            Cursor cursor;
            // Process the escape characters of query
            code = code.replaceAll("'", "''");
            if (word == null || word.trim().isEmpty()) {
                cursor = db.query(table, null, FIELD_CODE + " = '"
                        + code + "'", null, null, null, null, null);
            } else {
                cursor = db.query(table, null, FIELD_CODE + " = '"
                        + code + "'" + " AND " + FIELD_WORD + " = '"
                        + word + "'", null, null, null, null, null);
            }
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    munit = new Mapping();
                    //int idColumn = cursor.getColumnIndex(FIELD_ID);
                    //int codeColumn = cursor.getColumnIndex(FIELD_CODE);
                    //int wordColumn = cursor.getColumnIndex(FIELD_WORD);
                    //int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
                    //int relatedColumn = cursor.getColumnIndex(FIELD_RELATED);

                    munit.setCode(getCursorString(cursor, FIELD_CODE));
                    munit.setWord(getCursorString(cursor, FIELD_WORD));
                    munit.setScore(getCursorInt(cursor, FIELD_SCORE));
                    //munit.setHighLighted(cursor.getString(relatedColumn));
                    //munit.setHighLighted(false);
                    munit.setExactMatchToCodeRecord();
                    if (DEBUG)
                        Log.i(TAG, "isMappingExistOnDB(), mapping is exist");
                } else if (DEBUG)
                    Log.i(TAG, "isMappingExistOnDB(), mapping is not exist");

                cursor.close();
            }

        }
        return munit;
    }




    /**
     * Gets the ID of the record with the highest score for a given code.
     * 
     * <p>This method is used to find which record should store the related word list
     * for a code. The record with the highest score (and basescore as tiebreaker)
     * is chosen.
     * 
     * @param db The database to query
     * @param table The table name to search
     * @param code The code to find the highest score record for
     * @return The _id of the highest score record, or -1 if not found
     */
    public int getHighestScoreIDOnDB(SQLiteDatabase db, String table, String code) {
        int ID = -1;
        if (code != null && !code.trim().isEmpty()) {
            // Process the escape characters of query
            code = code.replaceAll("'", "''");
            Cursor cursor = db.query(table, null, FIELD_CODE + " = '"
                            + code + "'", null, null, null,
                    FIELD_SCORE + " DESC, " + FIELD_BASESCORE + " DESC", null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    //int idColumn = cursor.getColumnIndex(FIELD_ID);
                    ID =getCursorInt(cursor,FIELD_ID);
                }

                //cursor.deactivate();
                cursor.close();
            }


        }
        return ID;

    }

    /**
     * Checks if a related phrase record exists in the user dictionary.
     * 
     * <p>This method queries the related table to find a record matching
     * the parent word and child word combination. Used to determine if a
     * phrase should be added as new or updated as existing.
     * 
     * @param pword The parent word (previous word in phrase)
     * @param cword The child word (next word in phrase), or null to check for frequency record
     * @return Mapping object if found, null otherwise
     */
    public Mapping isRelatedPhraseExist(String pword, String cword) {

        long startTime=0;
        if (DEBUG||probePerformance) {
            startTime = System.currentTimeMillis();
            Log.i(TAG,"isRelatedPhraseExist(): pword='" + pword + ", cword=" + cword );
        }
        if (checkDBConnection()) return null;
        Mapping munit = null;

        //SQLiteDatabase db = this.getSqliteDb(true);
        try {
            munit = isRelatedPhraseExistOnDB(db, pword, cword);

        } catch (Exception e) {

            Log.e(TAG, "Error in database operation", e);
        }

        if (DEBUG||probePerformance) {

            Log.i(TAG,"isRelatedPhraseExist(): time elapsed = " + (System.currentTimeMillis() - startTime) );
        }

        return munit;
    }

    /**
     * Jeremy '12/4/16 core of isUserDictExist()
     */
    private Mapping isRelatedPhraseExistOnDB(SQLiteDatabase db, String pword, String cword) {

        Mapping munit = null;
        if (pword != null && !pword.trim().isEmpty()) {
            Cursor cursor;

            if (cword == null || cword.trim().isEmpty()) {
                cursor = db.query(LIME.DB_TABLE_RELATED, null, FIELD_DIC_pword + " = '"
                        + pword + "'" + " AND " + FIELD_DIC_cword + " IS NULL"
                        , null, null, null, null, null);
            } else {
                cursor = db.query(LIME.DB_TABLE_RELATED, null, FIELD_DIC_pword + " = '"
                        + pword + "'" + " AND " + FIELD_DIC_cword + " = '"
                        + cword + "'", null, null, null, null, null);
            }

            if (cursor.moveToFirst()) {
                munit = new Mapping();
                munit.setId(getCursorString(cursor, LIME.DB_RELATED_COLUMN_ID));
                munit.setPword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD));
                munit.setWord(getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD));
                munit.setBasescore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE));
                munit.setScore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE));
                munit.setRelatedPhraseRecord();

            }
            cursor.close();


        }
        return munit;
    }

    /**
     * Resets all IM information for the specified input method.
     * 
     * <p>This method deletes all records from the im table for the given IM code.
     * Used when reloading an input method to clear old configuration.
     * 
     * @param im The IM code to reset (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI, "custom")
     */
    public synchronized void resetImInfo(String im) {
        //Jeremy '12,5,1
        if (checkDBConnection()) return;
        String removeString = "DELETE FROM im WHERE code='" + im + "'";
        db.execSQL(removeString);

    }

    /**
     * Gets IM information for a specific field.
     * 
     * <p>Retrieves configuration information stored in the im table for the
     * specified input method and field. Common fields include:
     * <ul>
     *   <li>name - Display name of the IM</li>
     *   <li>source - Source filename</li>
     *   <li>amount - Number of records</li>
     *   <li>import - Import date</li>
     *   <li>selkey - Selection keys</li>
     *   <li>endkey - End keys</li>
     *   <li>imkeys - Key mapping string</li>
     *   <li>imkeynames - Key name mapping string</li>
     *   <li>keyboard - Keyboard code</li>
     * </ul>
     * 
     * @param im The IM code (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI)
     * @param field The field name to retrieve
     * @return The field value, or empty string if not found or database error
     */
    public String getImInfo(String im, String field) {
        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return "";

        String iminfo = "";
        try {
            //String value = "";
            String selectString = "SELECT * FROM im WHERE code='" + im + "' AND title='" + field + "'";

            Cursor cursor = db.rawQuery(selectString, null);

            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    //int descCol = cursor.getColumnIndex(LIME.DB_IM_COLUMN_DESC);
                    iminfo = getCursorString(cursor,LIME.DB_IM_COLUMN_DESC);
                }
                cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }
        return iminfo;
    }

    /**
     * Removes a specific IM information field.
     * 
     * <p>Deletes the specified field record from the im table for the given IM.
     * 
     * @param im The IM code
     * @param field The field name to remove
     */
    public synchronized void removeImInfo(String im, String field) {
        if (DEBUG)
            Log.i(TAG, "removeImInfo()");
        if (checkDBConnection()) return;
        try {
            removeImInfoOnDB(db, im, field);
        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }
    }

    /**
     * Jeremy '12,6,7 for working with OnUpgrade() before db is created
     */
    private void removeImInfoOnDB(SQLiteDatabase dbin, String im, String field) {
        if (DEBUG)
            Log.i(TAG, "removeImInfoOnDB()");
        // Use parameterized query to prevent SQL injection
        dbin.delete(LIME.DB_TABLE_IM, 
            LIME.DB_IM_COLUMN_CODE + " = ? AND " + LIME.DB_IM_COLUMN_TITLE + " = ?",
            new String[]{im, field});
    }


    /**
     * Sets IM information for a specific field.
     * 
     * <p>Stores or updates configuration information in the im table. If the
     * field already exists, it is removed and reinserted with the new value.
     * 
     * @param im The IM code (e.g., LIME.DB_TABLE_PHONETIC, LIME.DB_TABLE_DAYI)
     * @param field The field name to set
     * @param value The value to store
     */
    public synchronized void setImInfo(String im, String field, String value) {
        //Jeremy '12,4,17 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return;

        ContentValues cv = new ContentValues();
        cv.put("code", im);
        cv.put("title", field);
        cv.put(LIME.DB_IM_COLUMN_DESC, value);

        removeImInfo(im, field);

        db.insert("im", null, cv);

    }


    /**
     * Gets a list of IM information records for a specific IM code.
     * 
     * @param code The IM code to retrieve information for
     * @return List of Im objects, or null if database error
     */
    public List<Im> getImList(String code) {

        if (checkDBConnection()) return null;

        List<Im> result = null;
        try {
            //SQLiteDatabase db = this.getSqliteDb(true);
            Cursor cursor = db.query("im", null, LIME.DB_IM_COLUMN_CODE + " = '" + code + "'", null, null, null, "code ASC", null);
            result = Im.getList(this, cursor);
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "getIm(): Cannot get IM", e);
        }
        return result;
    }


    /**
     * Gets a list of all IM objects with keyboard assignments.
     * 
     * <p>This method retrieves all IM records that have keyboard information,
     * returning them as ImObj objects sorted by code.
     * 
     * @return List of ImObj objects, or null if database error
     */
    public List<ImObj> getImList() {
        if (DEBUG)
            Log.i(TAG, "getIMList()");
        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return null;


        List<ImObj> result = new LinkedList<>();
        try {
            //SQLiteDatabase db = this.getSqliteDb(true);
            Cursor cursor = db.query("im", null, null, null, null, null, "code ASC", null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        String title = getCursorString(cursor, LIME.DB_IM_COLUMN_TITLE);
                        if (title.equals(LIME.DB_KEYBOARD)) {
                            ImObj kobj = new ImObj();
                            kobj.setCode(getCursorString(cursor, LIME.DB_IM_COLUMN_CODE));
                            kobj.setKeyboard(getCursorString(cursor, LIME.DB_IM_COLUMN_KEYBOARD));
                            result.add(kobj);
                        }
                    } while (cursor.moveToNext());
                }

                cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "getImList(): Cannot get IM List", e);
        }
        return result;
    }

    /**
     * Gets keyboard object information for a specific keyboard code.
     * 
     * <p>Retrieves keyboard configuration including layout definitions for
     * IM mode, English mode, symbol mode, etc. Special handling for "wb"
     * and "hs" keyboards which have hardcoded configurations.
     * 
     * @param keyboard The keyboard code (e.g., "lime", "limenum", "wb", "hs")
     * @return KeyboardObj with keyboard information, or null if not found or database error
     */
    public KeyboardObj getKeyboardObj(String keyboard) {

        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return null;

        if (keyboard == null || keyboard.isEmpty())
            return null;
        KeyboardObj kobj = null;

        if (!keyboard.equals("wb") && !keyboard.equals("hs")) {
            try {
                Cursor cursor = db.query(LIME.DB_TABLE_KEYBOARD, null, FIELD_CODE + " = '" + keyboard + "'", null, null, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        kobj = new KeyboardObj();
                        kobj.setCode(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_CODE));
                        kobj.setName(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_NAME));
                        kobj.setDescription(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DESC));
                        kobj.setType(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_TYPE));
                        kobj.setImage(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMAGE));
                        kobj.setImkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMKB));
                        kobj.setImshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMSHIFTKB));
                        kobj.setEngkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGKB));
                        kobj.setEngshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGSHIFTKB));
                        kobj.setSymbolkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLKB));
                        kobj.setSymbolshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB));
                        kobj.setDefaultkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTKB));
                        kobj.setDefaultshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTSHIFTKB));
                        kobj.setExtendedkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDKB));
                        kobj.setExtendedshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDSHIFTKB));
                    }

                    cursor.close();
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in database operation", e);
            }
        } else if (keyboard.equals("wb")) {
            kobj = new KeyboardObj();
            kobj.setCode("wb");
            kobj.setName("筆順五碼");
            kobj.setDescription("筆順五碼輸入法鍵盤");
            kobj.setType("phone");
            kobj.setImage("wb_keyboard_preview");
            kobj.setImkb("lime_wb");
            kobj.setImshiftkb("lime_wb");
            kobj.setEngkb("lime_abc");
            kobj.setEngshiftkb("lime_abc_shift");
            kobj.setSymbolkb("symbols");
            kobj.setSymbolshiftkb("symbols_shift");
        } else {
            kobj = new KeyboardObj();
            kobj.setCode("hs");
            kobj.setName("華象直覺");
            kobj.setDescription("華象直覺輸入法鍵盤");
            kobj.setType("phone");
            kobj.setImage("hs_keyboard_preview");
            kobj.setImkb("lime_hs");
            kobj.setImshiftkb("lime_hs_shift");
            kobj.setEngkb("lime_abc");
            kobj.setEngshiftkb("lime_abc_shift");
            kobj.setSymbolkb("symbols");
            kobj.setSymbolshiftkb("symbols_shift");
        }

        return kobj;
    }

    /**
     * Gets a specific field value from keyboard information.
     * 
     * @param keyboardCode The keyboard code
     * @param field The field name to retrieve (e.g., "name", LIME.DB_IM_COLUMN_DESC, "imkb")
     * @return The field value, or null if not found or database error
     */
    public String getKeyboardInfo(String keyboardCode, String field) {
        if (DEBUG)
            Log.i(TAG, "getKeyboardInfo()");
        if (checkDBConnection()) return null;
        String info = null;
        try {
            info = getKeyboardInfoOnDB(db, keyboardCode, field);
        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }
        return info;

    }

    /**
     * Jeremy '12,6,7 for working with OnUpgrade() before db is created
     */
    private String getKeyboardInfoOnDB(SQLiteDatabase dbin, String keyboardCode, String field) {
        if (DEBUG)
            Log.i(TAG, "getKeyboardInfoOnDB()");

        String info = null;

        Cursor cursor = dbin.query(LIME.DB_TABLE_KEYBOARD, null, FIELD_CODE + " = '" + keyboardCode + "'"
                , null, null, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                info = getCursorString(cursor, field);
            }
            cursor.close();
        }
        if (DEBUG)
            Log.i(TAG, "getKeyboardInfoOnDB() info = " + info);

        return info;
    }

    /**
     * Gets a list of all available keyboards.
     * 
     * <p>Retrieves all keyboard configurations from the database, sorted by name.
     * 
     * @return List of KeyboardObj objects, or null if database error
     */
    public List<KeyboardObj> getKeyboardList() {

        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return null;


        List<KeyboardObj> result = new LinkedList<>();
        try {
            Cursor cursor = db.query(LIME.DB_TABLE_KEYBOARD, null, null, null, null, null, "name ASC", null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        KeyboardObj kobj = new KeyboardObj();
                        kobj.setCode(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_CODE));
                        kobj.setName(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_NAME));
                        kobj.setDescription(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DESC));
                        kobj.setType(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_TYPE));
                        kobj.setImage(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMAGE));
                        kobj.setImkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMKB));
                        kobj.setImshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMSHIFTKB));
                        kobj.setEngkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGKB));
                        kobj.setEngshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGSHIFTKB));
                        kobj.setSymbolkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLKB));
                        kobj.setSymbolshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB));
                        kobj.setDefaultkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTKB));
                        kobj.setDefaultshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTSHIFTKB));
                        kobj.setExtendedkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDKB));
                        kobj.setExtendedshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDSHIFTKB));
                        result.add(kobj);
                    } while (cursor.moveToNext());
                }

                cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }
        return result;
    }

    /**
     * Sets the keyboard assignment for an input method.
     * 
     * <p>Stores the keyboard configuration in the im table, associating a
     * keyboard code with the IM. This determines which keyboard layout
     * is used when the IM is active.
     * 
     * @param im The IM code
     * @param value The keyboard description/name
     * @param keyboard The keyboard code
     */
    public synchronized void setIMKeyboard(String im, String value, String keyboard) {
        if (DEBUG)
            Log.i(TAG, "setIMKeyboard() im=" + im + " value= " + value + " keyboard= " + keyboard);
        if (checkDBConnection()) return;
        try {
            setIMKeyboardOnDB(db, im, value, keyboard);
        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }


    }

    /**
     * Sets the keyboard configuration for an input method directly on the database.
     *
     * <p>This method is used internally during database upgrades ({@code onUpgrade()})
     * when the standard database connection may not be fully initialized. It directly
     * operates on the provided {@code SQLiteDatabase} instance, bypassing the normal
     * connection management.
     *
     * <p>The method performs the following operations:
     * <ol>
     *   <li>Creates a new record in the {@code im} table with:
     *       <ul>
     *         <li>{@code code}: The IM code (e.g., {@link LIME#IM_PHONETIC})</li>
     *         <li>{@code title}: LIME.DB_KEYBOARD</li>
     *         <li>{@code desc}: The keyboard description/value</li>
     *         <li>{@code keyboard}: The keyboard code</li>
     *       </ul>
     *   </li>
     *   <li>Removes any existing keyboard configuration for the specified IM
     *       to ensure no duplicate entries</li>
     *   <li>Inserts the new keyboard configuration record</li>
     * </ol>
     *
     * <p><b>Note:</b> This method is private and should only be called from
     * {@link #setIMKeyboard(String, String, String)} or during database upgrade
     * operations. For normal operations, use the public {@code setIMKeyboard()} method
     * instead, which handles database connection management and error handling.
     *
     * @param dbin The SQLiteDatabase instance to operate on (typically passed from
     *             {@code onUpgrade()} or the shared database connection)
     * @param im The input method code (e.g., {@link LIME#IM_PHONETIC}, {@link LIME#IM_CJ})
     * @param value The keyboard description or name to store
     * @param keyboard The keyboard code to associate with the IM
     *                 (e.g., {@link LIME#IM_PHONETIC_KEYBOARD_HSU}, {@link LIME#IM_PHONETIC_KEYBOARD_ETEN})
     */
    private void setIMKeyboardOnDB(SQLiteDatabase dbin, String im, String value, String keyboard) {
        if (DEBUG)
            Log.i(TAG, "setIMKeyboardOnDB()");
        ContentValues cv = new ContentValues();
        cv.put(LIME.DB_IM_COLUMN_CODE, im);
        cv.put(LIME.DB_IM_COLUMN_TITLE, LIME.DB_KEYBOARD);
        cv.put(LIME.DB_IM_COLUMN_DESC, value);
        cv.put(LIME.DB_IM_COLUMN_KEYBOARD, keyboard);

        removeImInfoOnDB(dbin, im, LIME.DB_KEYBOARD);

        dbin.insert(LIME.DB_TABLE_IM, null, cv);
    }

    /**
     * Gets the keyboard code assigned to an input method.
     * 
     * <p>Retrieves the keyboard code from the im table for the specified IM.
     * Returns empty string if not found or database error.
     * 
     * @param im The IM code
     * @return The keyboard code, or empty string if not found
     */
    public String getKeyboardCode(String im) {
        //Jeremy '12,5,1 !checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return "";


        try {
            String selectString = "SELECT * FROM im WHERE code='" + im + "' AND title='keyboard'";

            Cursor cursor = db.rawQuery(selectString, null);

            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();

                    return getCursorString(cursor, LIME.DB_IM_COLUMN_KEYBOARD);
                }
                cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting keyboard from IM", e);
        }
        return "";
    }

    /**
     * Gets English word suggestions based on a prefix.
     * 
     * <p>This method queries the dictionary table using FTS (Full-Text Search)
     * to find words that start with the given prefix. Results are limited
     * by the similar code candidates preference setting.
     * 
     * <p>Used for English prediction features in the IME.
     * 
     * @param word The word prefix to search for
     * @return List of suggested words, or null if database error
     */
    public List<String> getEnglishSuggestions(String word) {

        //Jeremy '12,5,1 checkDBConnection() when db is restoring or replaced.
        if (checkDBConnection()) return null;


        List<String> result = new ArrayList<>();
        try {
            //String value = "";
            int similarSize = mLIMEPref.getSimilarCodeCandidates();

            String selectString = "SELECT word FROM dictionary WHERE word MATCH '" + word + "*' AND word <> '"+ word +"'ORDER BY word ASC LIMIT " + similarSize + ";";
            //SQLiteDatabase db = this.getSqliteDb(true);

            Cursor cursor = db.rawQuery(selectString, null);
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    do {
                        String w = getCursorString(cursor, "word");
                        if (w != null && !w.isEmpty()) {
                            result.add(w);
                        }
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting word list", e);
        }

        return result;
    }

    /**
     * Converts text to emoji suggestions.
     * 
     * <p>This method uses the emoji converter to find emoji mappings for the
     * given source text. The emoji database is automatically initialized if needed.
     * 
     * @param source The source text to convert
     * @param emoji The emoji mode (e.g., LIME.EMOJI_EN for English emoji)
     * @return List of Mapping objects containing emoji suggestions
     */
    public List<Mapping> emojiConvert(String source, int emoji){
        checkEmojiDB();
        return emojiConverter.convert(source, emoji);
    }

    /**
     * Converts between Traditional and Simplified Chinese.
     * 
     * <p>This method uses the han converter database to perform conversion
     * based on the specified option. The han database is automatically
     * initialized if needed.
     * 
     * @param input The Chinese text to convert
     * @param hanOption The conversion option (see LIME constants for options)
     * @return The converted text
     */
    public String hanConvert(String input, int hanOption) {
        checkHanDB();
        return hanConverter.convert(input, hanOption);
    }

    /**
     * Gets the base frequency score for a word from the han converter database.
     * 
     * <p>This score represents the frequency of the word in the han converter
     * database and is used as a basescore when loading mapping files.
     * 
     * @param input The word to get the base score for
     * @return The base score, or 0 if not found
     */
    public int getBaseScore(String input) {
        checkHanDB();
        return hanConverter.getBaseScore(input);

    }

    private void checkEmojiDB() {
        if (emojiConverter == null) {

            File emojiDBFile = LIMEUtilities.isFileNotExist(
                    mContext.getDatabasePath("emoji.db").getAbsolutePath());

            if (emojiDBFile != null)
                LIMEUtilities.copyRAWFile(mContext.getResources().openRawResource(R.raw.emoji), emojiDBFile);

            emojiConverter = new EmojiConverter(mContext);
        }
    }

    private void checkHanDB() {
        if (hanConverter == null) {

            //Jeremy '11,9,8 update handconverdb to v2 with base score in TCSC table
            File hanDBFile = LIMEUtilities.isFileExist(
                    mContext.getDatabasePath("hanconvert.db").getAbsolutePath());
            if (hanDBFile != null &&
                !hanDBFile.delete())
                    Log.w(TAG,"hanconvert.db file delete failed");
            File hanDBV2File = LIMEUtilities.isFileNotExist(
                    mContext.getDatabasePath("hanconvertv2.db").getAbsolutePath());

            if (DEBUG) Log.i(TAG, "LimeDB: checkHanDB(): hanDBV2Filepaht:" +
                    mContext.getDatabasePath("hanconvertv2.db").getAbsolutePath());

            if (hanDBV2File != null)
                LIMEUtilities.copyRAWFile(mContext.getResources().openRawResource(R.raw.hanconvertv2), hanDBV2File);
            else { // Jeremy '11,9,14 copy the db file if it's newer.
                hanDBV2File = LIMEUtilities.isFileExist(
                        mContext.getDatabasePath("hanconvertv2.db").getAbsolutePath());
                if (hanDBV2File != null && mLIMEPref.getParameterLong("hanDBDate") != hanDBV2File.lastModified())
                    LIMEUtilities.copyRAWFile(mContext.getResources().openRawResource(R.raw.hanconvertv2), hanDBV2File);
            }

            hanConverter = new LimeHanConverter(mContext);
        }
    }


    /**
     * Renames a table in the database.
     * 
     * <p>This method performs an ALTER TABLE RENAME operation. Use with caution
     * as it permanently changes the table name.
     * 
     * @param source The current table name
     * @param target The new table name
     */
    public void renameTableName(String source, String target) {
        if (checkDBConnection()) return;

        try {
            //ALTER TABLE foo RENAME TO bar
            db.execSQL("ALTER TABLE " + source + " RENAME TO " + target);
        } catch (Exception e) {
            Log.e(TAG, "Error in database operation", e);
        }
    }
    /**
     * Begins a database transaction.
     * 
     * <p>Starts a transaction for batch operations. Must be paired with
     * {@link #endTransaction()} to commit the transaction.
     * 
     * <p>Transactions improve performance for multiple database operations
     * by batching them together.
     */
    public void beginTransaction() {
        if (db != null && db.isOpen()) {
            db.beginTransaction();
        }
    }

    /**
     * Ends a database transaction and commits changes.
     * 
     * <p>Marks the transaction as successful and commits all changes made
     * since {@link #beginTransaction()} was called.
     */
    public void endTransaction() {
        if (db != null && db.isOpen()) {
            db.setTransactionSuccessful();
            db.endTransaction();
        }
    }

    /**
     * Gets all records from a table.
     * 
     * <p>Returns a Cursor containing all rows from the specified table.
     * The caller is responsible for closing the cursor.
     * 
     * @param table The table name to query
     * @return Cursor with all records, or null if database error
     */
    public Cursor list(String table) {
        Cursor cursor = null;
        if (db != null && db.isOpen()) {
            cursor = db.query(table, null, null, null, null, null, null);
        }
        return cursor;
    }

    /**
     * Inserts a record into the specified table using parameterized ContentValues.
     *
     * <p>This method uses parameterized input to prevent SQL injection.
     *
     * @param table The table name to insert into (must pass isValidTableName)
     * @param values The ContentValues representing column names and values
     * @return The row ID of the newly inserted row, or -1 if error
     */
    public long addRecord(String table, android.content.ContentValues values) {
        if (checkDBConnection()) return -1;
        if (!isValidTableName(table)) {
            Log.e(TAG, "addRecord(): Invalid table name: " + table);
            return -1;
        }
        try {
            return db.insert(table, null, values);
        } catch (Exception e) {
            Log.e(TAG, "Error inserting record into table: " + table, e);
            return -1;
        }
    }


    /**
     * Deletes records from a table using parameterized queries.
     * 
     * <p>This method uses parameterized queries to prevent SQL injection.
     * The table name is validated against a whitelist.
     * 
     * @param table The table name (must be valid according to {@link #isValidTableName(String)})
     * @param whereClause The WHERE clause (e.g., "id = ?") with ? placeholders
     * @param whereArgs The arguments to replace ? placeholders in whereClause
     * @return The number of rows deleted, or -1 if error
     */
    public int deleteRecord(String table, String whereClause, String[] whereArgs) {
        if (checkDBConnection()) return -1;
        
        if (!isValidTableName(table)) {
            Log.e(TAG, "deleteRecord(): Invalid table name: " + table);
            return -1;
        }
        
        try {
            return db.delete(table, whereClause, whereArgs);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting record from table: " + table, e);
            return -1;
        }
    }




    /**
     * Updates records in a table using parameterized queries.
     * 
     * <p>This method uses parameterized queries to prevent SQL injection.
     * The table name is validated against a whitelist.
     * 
     * @param table The table name (must be valid according to {@link #isValidTableName(String)})
     * @param values The values to update
     * @param whereClause The WHERE clause (e.g., "id = ?") with ? placeholders
     * @param whereArgs The arguments to replace ? placeholders in whereClause
     * @return The number of rows updated, or -1 if error
     */
    public int updateRecord(String table, android.content.ContentValues values, String whereClause, String[] whereArgs) {
        if (checkDBConnection()) return -1;
        
        if (!isValidTableName(table)) {
            Log.e(TAG, "updateRecord(): Invalid table name: " + table);
            return -1;
        }
        
        try {
            return db.update(table, values, whereClause, whereArgs);
        } catch (Exception e) {
            Log.e(TAG, "Error updating record in table: " + table, e);
            return -1;
        }
    }


    /**
     * Gets a list of all keyboards from the database.
     * 
     * <p>Retrieves all keyboard records sorted by name.
     * 
     * @return List of Keyboard objects, or empty list if database error
     */
    public List<Keyboard> getKeyboard() {
        List<Keyboard> result = new ArrayList<>();
        if (checkDBConnection()) return result;

        Cursor cursor = db.query(LIME.DB_TABLE_KEYBOARD, null, null,
                null, null, null, LIME.DB_KEYBOARD_COLUMN_NAME + " ASC");
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Keyboard r = Keyboard.get(cursor);
            result.add(r);
            cursor.moveToNext();
        }
        cursor.close();

        return result;
    }

    /**
     * Gets IM records filtered by code and/or type.
     * 
     * <p>Retrieves IM information records matching the specified code and type.
     * Either parameter can be null/empty to match all values.
     * 
     * @param code The IM code to filter by, or null/empty for all
     * @param type The IM type to filter by, or null/empty for all
     * @return List of Im objects, or empty list if database error
     */
    public List<Im> getIm(String code, String type) {

        List<Im> result = new ArrayList<>();
        if (checkDBConnection()) return result;

        Cursor cursor;
        StringBuilder queryBuilder = new StringBuilder();
        if (code != null && code.length() > 1) {
            queryBuilder.append(LIME.DB_IM_COLUMN_CODE).append("='").append(code).append("'");
        }
        if (type != null && type.length() > 1) {
            if (queryBuilder.length() > 0) {
                queryBuilder.append(" AND ");
            }
            queryBuilder.append(" ").append(LIME.DB_IM_COLUMN_TITLE).append("='").append(type).append("'");
        }
        String query = queryBuilder.length() > 0 ? queryBuilder.toString() : null;

        cursor = db.query(LIME.DB_TABLE_IM,
                null, query,
                null, null, null, LIME.DB_IM_COLUMN_DESC + " ASC");
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Im r = Im.get(this, cursor);
            result.add(r);
            cursor.moveToNext();
        }
        cursor.close();

        return result;
    }

    /**
     * Loads word records from a table with optional filtering and pagination.
     * 
     * <p>This method supports two search modes:
     * <ul>
     *   <li>searchByCode=true: Searches by code prefix (code LIKE 'query%')</li>
     *   <li>searchByCode=false: Searches by word substring (word LIKE '%query%')</li>
     * </ul>
     * 
     * <p>Results can be limited and paginated using maximum and offset parameters.
     * 
     * @param code The table name to query
     * @param query The search query string, or null/empty for all records
     * @param searchByCode If true, search by code; if false, search by word
     * @param maximum Maximum number of records to return (0 for no limit)
     * @param offset Offset for pagination (0 for first page)
     * @return List of Word objects, or empty list if database error
     */
    public List<Word> loadWord(String code, String query, boolean searchByCode, int maximum, int offset) {
        List<Word> result = new ArrayList<>();
        if (checkDBConnection()) return result;

        Cursor cursor;
        if (query != null && !query.isEmpty()) {
            if (searchByCode) {
                query = LIME.DB_COLUMN_CODE + " LIKE '" + query + "%' AND ifnull(" + LIME.DB_COLUMN_WORD + ", '') <> ''";
            } else {
                query = LIME.DB_COLUMN_WORD + " LIKE '%" + query + "%' AND ifnull(" + LIME.DB_COLUMN_WORD + ", '') <> ''";
            }
        } else {
            query = "ifnull(" + LIME.DB_COLUMN_WORD + ", '') <> ''";
        }

        String order;

        if (searchByCode) {
            order = LIME.DB_COLUMN_CODE + " ASC";
        } else {
            order = LIME.DB_COLUMN_WORD + " ASC";
        }

        if (maximum > 0) {
            order += " LIMIT " + maximum + " OFFSET " + offset;
        }


        cursor = db.query(code,
                null, query,
                null, null, null, order);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Word r = Word.get(cursor);
            result.add(r);
            cursor.moveToNext();
        }
        cursor.close();

        return result;
    }

    /**
     * Gets a single word record by ID.
     * 
     * @param code The table name
     * @param id The record ID (_id)
     * @return Word object, or null if not found or database error
     */
    public Word getWord(String code, long id) {
        if (checkDBConnection()) return null;
        Word w;
        Cursor cursor;

        String query = LIME.DB_COLUMN_ID + " = '" + id + "' ";

        cursor = db.query(code,
                null, query,
                null, null, null, null);

        cursor.moveToFirst();
        w = Word.get(cursor);
        cursor.close();
        return w;
    }

    /**
     * Sets the keyboard assignment for an IM using a Keyboard object.
     * 
     * <p>This method stores the keyboard configuration in the im table,
     * replacing any existing keyboard assignment for the IM.
     * 
     * @param code The IM code
     * @param keyboard The Keyboard object containing keyboard information
     */
    public void setImKeyboard(String code, Keyboard keyboard) {
        if (checkDBConnection()) return;

        String removeSQL = "DELETE FROM " + LIME.DB_TABLE_IM + " WHERE " + LIME.DB_IM_COLUMN_CODE + " = '" + code + "'" +
                " AND " + LIME.DB_IM_COLUMN_TITLE + " = '" + LIME.IM_TYPE_KEYBOARD + "'";
        db.execSQL(removeSQL);

        Im im = new Im();
        im.setCode(code);
        im.setKeyboard(keyboard.getCode());
        im.setTitle(LIME.IM_TYPE_KEYBOARD);
        im.setDesc(keyboard.getDesc());

        String addsql = Im.getInsertQuery(im);
        db.execSQL(addsql);

    }

    /**
     * Checks if a related phrase exists and returns its ID.
     * 
     * <p>This method queries the related table to find a record matching
     * the parent and child word combination. Returns the record ID if found.
     * 
     * @param pword The parent word (must not be null or empty)
     * @param cword The child word (must not be null or empty)
     * @return The record ID if found, 0 if not found, or 9999999 if database error
     */
    public int hasRelated(String pword, String cword) {

        try {
            Cursor cursor;

            String query = "";
            if (pword != null && !pword.isEmpty() && cword != null && !cword.isEmpty()) {
                query = LIME.DB_RELATED_COLUMN_PWORD + " = '" + pword + "' AND ";
                query += LIME.DB_RELATED_COLUMN_CWORD + " = '" + cword + "'";
            }

            cursor = db.query(LIME.DB_TABLE_RELATED,
                    null, query,
                    null, null, null, null);

            int id = 0;
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                Related r = Related.get(cursor);
                id = r.getId();
                cursor.moveToNext();
            }
            cursor.close();

            return id;
        }catch(SQLiteException sqe){
            Log.e(TAG, "Error getting related ID", sqe);
            return 9999999;
        }
    }

    /**
     * Loads related phrase records for a given parent word.
     * 
     * <p>This method searches for related phrases where the parent word matches
     * the given pword. If pword length > 1, it also searches for phrases matching
     * the last character. Results are sorted by userscore and basescore descending.
     * 
     * <p>Supports pagination through maximum and offset parameters.
     * 
     * @param pword The parent word to search for
     * @param maximum Maximum number of records to return (0 for no limit)
     * @param offset Offset for pagination (0 for first page)
     * @return List of Related objects, or empty list if database error
     */
    public List<Related> loadRelated(String pword, int maximum, int offset) {

        List<Related> result = new ArrayList<>();
        if (checkDBConnection()) return result;

        Cursor cursor;

        StringBuilder queryBuilder = new StringBuilder();
        String cword = "";

        if (pword != null && pword.length() > 1) {
            cword = pword.substring(1);
            pword = pword.substring(0, 1);
        }
        if (pword != null && !pword.isEmpty()) {
            queryBuilder.append(LIME.DB_RELATED_COLUMN_PWORD).append(" = '").append(pword).append("' AND ");
        }
        if (!cword.isEmpty()) {
            queryBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" LIKE '").append(cword).append("%' AND ");
        }

        queryBuilder.append("ifnull(").append(LIME.DB_RELATED_COLUMN_CWORD).append(", '') <> ''");
        String query = queryBuilder.toString();

        StringBuilder orderBuilder = new StringBuilder(LIME.DB_RELATED_COLUMN_USERSCORE);
        orderBuilder.append(" desc,").append(LIME.DB_RELATED_COLUMN_BASESCORE).append(" desc");

        if (maximum > 0) {
            orderBuilder.append(" LIMIT ").append(maximum).append(" OFFSET ").append(offset);
        }
        String order = orderBuilder.toString();

        cursor = db.query(LIME.DB_TABLE_RELATED,
                null, query,
                null, null, null, order);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Related r = Related.get(cursor);
            result.add(r);
            cursor.moveToNext();
        }
        cursor.close();

        return result;
    }

    /**
     * Gets a single related phrase record by ID.
     * 
     * @param id The record ID (_id)
     * @return Related object, or null if not found or database error
     */
    public Related getRelated(long id) {
        if (checkDBConnection()) return null;
        Related w = null;
        Cursor cursor;

        String query = LIME.DB_RELATED_COLUMN_ID + " = '" + id + "' ";

        cursor = db.query(LIME.DB_TABLE_RELATED,
                null, query,
                null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            w = Related.get(cursor);
        }
        if (cursor != null) {
            cursor.close();
        }

        return w;
    }

    /**
     * Counts the total number of records in a table.
     * 
     * <p>Performs a SELECT COUNT(*) query on the specified table.
     * 
     * @param table The table name to count
     * @return The number of records, or 0 if database error
     */
    public int count(String table) {

        if (checkDBConnection()) return 0;
        int total;


        Cursor cursor;
        String query = "SELECT COUNT(*) as count FROM " + table;
        cursor = db.rawQuery(query, null);
        cursor.moveToFirst();
        total = getCursorInt(cursor, LIME.DB_TOTAL_COUNT);
        cursor.close();

        return total;

    }

    /**
     * Gets the count of word records matching a query.
     * 
     * <p>Similar to {@link #loadWord(String, String, boolean, int, int)} but
     * returns only the count instead of the actual records. Useful for
     * pagination calculations.
     * 
     * @param table The table name to query
     * @param curQuery The search query, or null/empty for all records
     * @param searchByCode If true, search by code; if false, search by word
     * @return The count of matching records, or 0 if database error
     */
    public int getWordSize(String table, String curQuery, boolean searchByCode) {

        if (checkDBConnection()) return 0;

        int total;

        Cursor cursor;

        StringBuilder queryBuilder = new StringBuilder("SELECT COUNT(*) as count FROM ");
        queryBuilder.append(table).append(" WHERE ");

        if (curQuery != null && !curQuery.isEmpty()) {
            if (searchByCode) {
                queryBuilder.append(LIME.DB_COLUMN_CODE).append(" LIKE '").append(curQuery).append("%' AND ifnull(").append(LIME.DB_COLUMN_WORD).append(", '') <> ''");
            } else {
                queryBuilder.append(LIME.DB_COLUMN_WORD).append(" LIKE '%").append(curQuery).append("%' AND ifnull(").append(LIME.DB_COLUMN_WORD).append(", '') <> ''");
            }
        } else {
            queryBuilder.append(" ifnull(").append(LIME.DB_COLUMN_WORD).append(", '') <> ''");
        }
        String query = queryBuilder.toString();

        cursor = db.rawQuery(query, null);

        cursor.moveToFirst();
        total = getCursorInt(cursor, LIME.DB_TOTAL_COUNT);
        cursor.close();
        return total;

    }


    /**
     * Gets the count of related phrase records for a parent word.
     * 
     * <p>Similar to {@link #loadRelated(String, int, int)} but returns only
     * the count. Useful for pagination calculations.
     * 
     * @param pword The parent word to count related phrases for
     * @return The count of related phrases, or -1 if database error
     */
    public int getRelatedSize(String pword) {

        if (checkDBConnection()) return -1;
        int total;

        Cursor cursor;

        StringBuilder queryBuilder = new StringBuilder("SELECT COUNT(*) as count FROM ");
        queryBuilder.append(LIME.DB_TABLE_RELATED).append(" WHERE ");

        String cword = "";
        if (pword != null && !pword.isEmpty()) {
            cword = pword.substring(1);
            pword = pword.substring(0, 1);
        }

        if (pword != null && !pword.isEmpty()) {
            queryBuilder.append(LIME.DB_RELATED_COLUMN_PWORD).append(" = '").append(pword).append("' AND ");
        }
        if (!cword.isEmpty()) {
            queryBuilder.append(LIME.DB_RELATED_COLUMN_CWORD).append(" LIKE '").append(cword).append("%' AND ");
        }

        queryBuilder.append("ifnull(").append(LIME.DB_RELATED_COLUMN_CWORD).append(", '') <> ''");
        String query = queryBuilder.toString();

        cursor = db.rawQuery(query, null);

        cursor.moveToFirst();
        total = getCursorInt(cursor, LIME.DB_TOTAL_COUNT);
        cursor.close();

        return total;
    }


    /**
     * Holds the database connection to prevent concurrent access during maintenance.
     * 
     * <p>When the database is on hold, queries will show a toast message and wait
     * until the hold is released. This prevents corruption during operations like
     * file loading or backup/restore.
     * 
     * <p>Must be paired with {@link #unHoldDBConnection()} to release the hold.
     */
    public void holdDBConnection() {
        databaseOnHold = true;
    }

    /**
     * Releases the database connection hold.
     * 
     * <p>Allows normal database operations to resume after maintenance is complete.
     */
    public void unHoldDBConnection() {
        databaseOnHold = false;
    }

    /**
     * Checks if the database connection is currently on hold.
     * 
     * @return true if database is on hold (maintenance in progress), false otherwise
     */
    public boolean isDatabaseOnHold() {
        return databaseOnHold;
    }

//    public void updateBackupScore(String imtype, List<Word> scorelist) {
//        if (checkDBConnection()) return;
//        db.beginTransaction();
//        for (Word w : scorelist) {
//            String updatesql = Word.getUpdateScoreQuery(imtype, w);
//            db.execSQL(updatesql);
//        }
//        db.endTransaction();
//        db.setTransactionSuccessful();
//    }

    /**
     * Executes a raw SQL query.
     * 
     * <p>This method performs basic validation on SELECT queries to extract
     * and validate table names against the whitelist. However, for production
     * use, consider requiring table names as separate parameters for better
     * security.
     * 
     * <p><b>Warning:</b> Use with caution. While some validation is performed,
     * this method executes raw SQL which could be vulnerable to SQL injection
     * if not used carefully.
     * 
     * @param query The raw SQL query string to execute
     * @return Cursor with query results, or null if error or invalid table name
     */
    public Cursor rawQuery(String query) {
        if (checkDBConnection()) return null;
        
        // Basic validation: check if query contains potentially dangerous patterns
        if (query != null && query.toLowerCase().contains("select")) {
            // Extract table name from SELECT query for validation
            // Simple pattern: "select * from tablename" or "SELECT * FROM tablename"
            String lowerQuery = query.toLowerCase().trim();
            if (lowerQuery.startsWith("select")) {
                // Try to extract table name (simplified - may not catch all cases)
                // For production, consider more robust parsing or requiring table name as separate parameter
                String[] parts = query.split("(?i)\\s+from\\s+");
                if (parts.length > 1) {
                    String tablePart = parts[1].trim().split("\\s+")[0].trim();
                    // Remove any trailing characters like WHERE, etc.
                    tablePart = tablePart.split("\\s+")[0];
                    if (!isValidTableName(tablePart)) {
                        Log.e(TAG, "rawQuery(): Invalid table name in query: " + tablePart);
                        return null;
                    }
                }
            }
        }
        
        try {
            return db.rawQuery(query, null);
        } catch (Exception e) {
            Log.e(TAG, "Error executing raw query", e);
        }
        return null;
    }

//    public void execSQL(String insertsql) {
//        if (checkDBConnection()) return;
//        try {
//            db.execSQL(insertsql);
//        } catch (Exception e) {
//            Log.w(TAG, "Ignore all possible exceptions~");
//        }
//    }

    /**
     * Resets all LIME settings to factory defaults.
     * 
     * <p>This method:
     * <ul>
     *   <li>Closes and deletes the main database, then restores from raw resource</li>
     *   <li>Closes and deletes the emoji database, then restores from raw resource</li>
     *   <li>Closes and deletes the han converter database, then restores from raw resource</li>
     *   <li>Reopens database connections</li>
     * </ul>
     * 
     * <p>This is a destructive operation that will erase all user data including
     * learned mappings and related phrases.
     */
    public void resetLimeSetting(){

        if(db != null)
            db.close();

        File dbFile= mContext.getDatabasePath(LIME.DATABASE_NAME);
             dbFile.deleteOnExit();
        LIMEUtilities.copyRAWFile(mContext.getResources().openRawResource(R.raw.lime), dbFile);
        openDBConnection(true);

        if(emojiConverter != null)
            emojiConverter.close();

        emojiConverter = null;
        File emojiDbFile =mContext.getDatabasePath( "emoji.db");
             emojiDbFile.deleteOnExit();
        LIMEUtilities.copyRAWFile(mContext.getResources().openRawResource(R.raw.emoji), emojiDbFile);
        emojiConverter = new EmojiConverter(mContext);

        if(hanConverter != null)
            hanConverter.close();

        hanConverter = null;
        File hanDBFile = mContext.getDatabasePath( "hanconvert.db");
             hanDBFile.deleteOnExit();
        File hanDB2File = mContext.getDatabasePath("hanconvertv2.db");
             hanDB2File.deleteOnExit();

        LIMEUtilities.copyRAWFile(mContext.getResources().openRawResource(R.raw.hanconvertv2), hanDB2File);
        hanConverter = new LimeHanConverter(mContext);

    }
}

