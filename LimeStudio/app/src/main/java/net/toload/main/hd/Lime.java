package net.toload.main.hd;

import android.os.Environment;

import java.text.DecimalFormat;

/**
 * Created by Art Hung on 2015/4/24.
 */
public class Lime {

    // Database Setting

    final public static String DB_NAME = "lime.db";
    final public static String DATABASE_FOLDER =  Environment.getDataDirectory() + "/data/net.toload.main.hd/databases";

    // Database Tables and columns

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


    public static final String DB_COLUMN_ID = "_id";

    public static final String DB_COLUMN_CODE = "code";
    public static final String DB_COLUMN_CODE3R = "code3r";
    public static final String DB_COLUMN_WORD = "word";
    public static final String DB_COLUMN_RELATED = "related";
    public static final String DB_COLUMN_SCORE = "score";
    public static final String DB_COLUMN_BASESCORE = "basescore";


    public static final String DB_IM = "im";
    public static final String DB_IM_COLUMN_ID = "_id";
    public static final String DB_IM_COLUMN_CODE = "code";
    public static final String DB_IM_COLUMN_TITLE = "title";
    public static final String DB_IM_COLUMN_DESC = "desc";
    public static final String DB_IM_COLUMN_KEYBOARD = "keyboard";
    public static final String DB_IM_COLUMN_DISABLE = "disable";
    public static final String DB_IM_COLUMN_SELKEY = "selkey";
    public static final String DB_IM_COLUMN_ENDKEY = "endkey";
    public static final String DB_IM_COLUMN_SPACESTYLE = "spacestyle";


    public static final String DB_RELATED = "related";
    public static final String DB_RELATED_COLUMN_ID = "_id";
    public static final String DB_RELATED_COLUMN_PWORD = "pword";
    public static final String DB_RELATED_COLUMN_CWORD = "cword";
    public static final String DB_RELATED_COLUMN_SCORE = "score";

    public static final String DB_KEYBOARD = "keyboard";
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

    public static final String IM_TYPE_NAME = "name";
    public static final String IM_TYPE_SOURCE = "source";
    public static final String IM_TYPE_AMOUNT = "amount";
    public static final String IM_TYPE_IMPORT = "import";
    public static final String IM_TYPE_KEYBOARD = "keyboard";

    public static final int IM_MANAGE_DISPLAY_AMOUNT = 50;

    public static String format(int number){
        try {
            DecimalFormat df = new DecimalFormat("##,##,##,##,##,##,##0");
            return df.format(number);
        }catch(Exception e){
            e.printStackTrace();
            return "0";
        }
    }
}
