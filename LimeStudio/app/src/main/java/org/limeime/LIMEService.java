/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
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

package org.limeime;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.limeime.candidate.CandidateInInputViewContainer;
import org.limeime.candidate.CandidateView;
import org.limeime.data.ChineseSymbol;
import org.limeime.data.ImConfig;
import org.limeime.data.Keyboard;
import org.limeime.data.Mapping;
import org.limeime.global.LIME;
import org.limeime.global.LIMEPreferenceManager;
import org.limeime.global.LIMEUtilities;
import org.limeime.global.SystemAccentColor;
import org.limeime.keyboard.LIMEBaseKeyboard;
import org.limeime.keyboard.LIMEKeyboard;
import org.limeime.keyboard.LIMEKeyboardBaseView;
import org.limeime.keyboard.LIMEKeyboardView;
import org.limeime.keyboard.LIMEMetaKeyKeyListener;
import org.limeime.limedb.LimeDB;
import org.limeime.ui.LIMEPreference;
import org.limeime.voice.AndroidSpeechRecognizerAdapter;
import org.limeime.voice.DictationResultListener;
import org.limeime.voice.DictationState;
import org.limeime.voice.LIMEDictationController;
import org.limeime.voice.LIMEVoiceInputRouter;
import org.limeime.voice.VoiceInputMode;
import org.limeime.voice.VoiceInputRoute;
import org.limeime.voice.VoicePermissionHelper;
import org.limeime.voice.VoicePermissionState;

import com.google.android.material.color.DynamicColors;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.os.ConfigurationCompat;
import androidx.core.content.ContextCompat;
import java.util.Objects;


public class LIMEService extends InputMethodService
        implements LIMEKeyboardBaseView.OnKeyboardActionListener, DictationResultListener {

    private static final boolean DEBUG = false;
    private static final String TAG = "LIMEService";
    private static final String IMKEYS_CONFIG = "imkeys";
    private static final String IMKEYNAMES_CONFIG = "imkeynames";

    private static Thread queryThread; // queryThread for no-blocking I/O  Jeremy '15,6,1

    static final int KEYCODE_SWITCH_TO_SYMBOL_MODE = -2;
    static final int KEYCODE_SWITCH_TO_ENGLISH_MODE = -9;
    static final int KEYCODE_SWITCH_TO_IM_MODE = -10;
    static final int KEYCODE_SWITCH_SYMBOL_KEYBOARD = -15;

    static int getRestrictedFieldKeyboardMode(int inputType) {
        if ((inputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_NUMBER) {
            return LIMEKeyboardSwitcher.MODE_PHONE;
        }
        return LIMEKeyboardSwitcher.MODE_TEXT;
    }

    static boolean getRestrictedFieldSymbolFlag(int inputType) {
        return (inputType & EditorInfo.TYPE_MASK_CLASS) != EditorInfo.TYPE_CLASS_NUMBER;
    }

    static boolean hasSymbolMappingForKeyboard(boolean baseHasSymbolMapping, String keyboardCode) {
        return baseHasSymbolMapping || LIMEKeyboardSwitcher.isCjSemicolonKeyboardCode(keyboardCode);
    }

    static boolean isForcedEnglishTextVariation(int variation) {
        return variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
    }

    /**
     * The input class LIME should classify a field as, correcting a specific malformed shape.
     *
     * #200: some hosts (e.g. LINE's Add-Friends ID search) declare a password/visible-password or
     * email field with a null input class — observed {@code inputType=0x90}: VISIBLE_PASSWORD
     * variation with class TYPE_NULL instead of {@code 0x91} (TYPE_CLASS_TEXT | VISIBLE_PASSWORD).
     * The forced-English handling in {@code initOnStartInput()} is gated on TYPE_CLASS_TEXT, so such
     * a field otherwise falls through to the Chinese composing path: a Latin key is placed in a
     * composing region (setComposingText) instead of committed, and the host editor duplicates it
     * ("j"→"jj"). Reinterpret a null-class field that carries a forced-English text variation as text
     * so the existing password/email commit-only handling applies. Every other input type is returned
     * unchanged, so ordinary Chinese text fields keep composing.
     */
    static int effectiveInputClass(int inputType) {
        int inputClass = inputType & EditorInfo.TYPE_MASK_CLASS;
        if (inputClass == 0 /* InputType.TYPE_NULL */
                && isForcedEnglishTextVariation(inputType & EditorInfo.TYPE_MASK_VARIATION)) {
            return EditorInfo.TYPE_CLASS_TEXT;
        }
        return inputClass;
    }

    //Jeremy '16,7,22 To control delayed hiding candidate view and avoid hide and show candidate view in short time.
    private static final int DELAY_BEFORE_HIDE_CANDIDATE_VIEW = 200;

    // ENGLISH_KB.md #0 / §2a — pick-space punctuation swap (replicate LatinIME).
    // When the user picks an English suggestion we auto-append a space (word ).
    // Typing punctuation next should produce "word, " not "word ,".
    // Character classes from AOSP donottranslate-config-spacing-and-punctuations.xml (en_US).
    private static final String ENG_SWAP_FOLLOWED_BY_SPACE = ".,;:!?)]}"; // delete space, commit punct + " "
    private static final String ENG_SWAP_PRECEDED_BY_SPACE = "([{";       // keep space, commit bracket
    private static final String ENG_SWAP_STRIP = "-/@_'";                 // delete space, commit punct bare
    // True only immediately after an English suggestion pick auto-appended a space.
    private boolean mPickedAutoSpace = false;

    public static final int THREAD_YIELD_DELAY_MS = 0;
    private LIMEKeyboardView mInputView = null;
    private CandidateInInputViewContainer mCandidateInInputView = null;//Jeremy'12,5,3
    //private final boolean mFixedCandidateViewOn = true; //Jeremy'12,5,3 - Always true, kept for backward compatibility
    private CandidateView mCandidateView = null;
    private CandidateView mCandidateViewInInputView = null;
    private CompletionInfo[] mCompletions;

    private StringBuilder mComposing = new StringBuilder();

    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private boolean mCapsLock;
    private long mLastShiftTime = -1;
    private boolean mAutoCap;
    private boolean mHasShift;

    private boolean mEnglishOnly;
    private boolean mEnglishFlagShift;
    private boolean mEmojiKeyboardShown;
    private boolean mEmojiSourceWasEnglish = true;
    private View mEmojiKeyboardView = null;
    private HorizontalScrollView mEmojiScroll = null;
    private LinearLayout mEmojiPages = null;
    private LinearLayout mEmojiRoot = null;
    private LinearLayout mEmojiBottomBar = null;
    private LinearLayout mEmojiCategoryBar = null;
    private TextView mEmojiSearchField = null;
    private TextView mEmojiAbcButton = null;
    private boolean mEmojiContentRendered = false;
    private int mEmojiCategoryIndex = 0;
    private int mInputCandidateStripVisibilityBeforeEmoji = View.VISIBLE;
    private boolean mEmojiSearchMode = false;
    private boolean mEmojiSearchFocused = false;
    private StringBuilder mEmojiSearchQuery = new StringBuilder();
    private List<List<String>> mEmojiCategoryPages = null;
    private List<Integer> mEmojiPageCategoryIndexes = new ArrayList<>();
    private int[] mEmojiCategoryPageStarts = new int[0];
    private int[] mEmojiCategoryStartOffsets = new int[0];
    private static final int EMOJI_SEARCH_FIELD_HEIGHT_DP = 52;
    private static final int EMOJI_PANEL_HORIZONTAL_PADDING_DP = 12;
    private static final int EMOJI_PANEL_VERTICAL_PADDING_DP = 8;
    private static final int EMOJI_PAGE_CAPACITY = 32;
    private static final int EMOJI_GRID_COLUMNS = 8;
    private static final int EMOJI_GRID_ROWS = 4;
    private static final int EMOJI_CATEGORY_TAB_WIDTH_DP = 56;
    private static final int EMOJI_CATEGORY_TAB_HEIGHT_DP = 46;
    private static final int EMOJI_CATEGORY_BOTTOM_BAR_HEIGHT_DP = 54;
    private static final int EMOJI_PANEL_GLYPH_SIZE = 28;
    private boolean mPersistentLanguageMode;  //Jeremy '12,5,1
    private int mShowArrowKeys; //Jeremy '12,5,22 force recreate keyboard if show arrow keys mode changes.
    private int mSplitKeyboard; //Jeremy '12,5,26 force recreate keyboard if split keyboard settings changes; 6/19 changed to int

    public boolean hasMappingList = false;

    private long mMetaState;
    private int mImeOptions;

    LIMEKeyboardSwitcher mKeyboardSwitcher;

    private int mOrientation;
    private int mHardkeyboardHidden;
    private boolean mPredicting;

    private Context mThemeContext;

    private Mapping selectedCandidate; //Jeremy '12,5,7 renamed from firstMacthed
    //private int selectedIndex; //Jeremy '12,5,7 the index in resultList of selectedCandidate
    private Mapping committedCandidate; //Jeremy '12,5,7 renamed from tempMatched

    private StringBuffer tempEnglishWord;
    private List<Mapping> tempEnglishList;

    private boolean hasPhysicalKeyPressed;

    // Voice input monitoring
    private ContentObserver mInputMethodObserver = null;
    private boolean mIsVoiceInputActive = false;
    private String mPendingVoiceText = null; // text to commit once InputConnection is re-established
    private String mLIMEId = null;
    private LIMEDictationController mDictationController = null;
    private BroadcastReceiver mVoiceInputReceiver = null;
    private static final String ACTION_VOICE_RESULT = "org.limeime.VOICE_INPUT_RESULT";
    private static final String EXTRA_RECOGNIZED_TEXT = "recognized_text";

    private static final String[][] FALLBACK_EMOJI_CATEGORIES = {
            {"😀", "😂", "😍", "🥰", "😘", "😭", "👍", "🙏", "👏", "🎉", "❤️", "✨", "🔥", "✅", "⭐", "💯"},
            {"😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "😘",
                    "😋", "😛", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒", "😔", "😢", "😭", "😤", "😱"},
            {"👋", "🤚", "🖐", "✋", "🖖", "👌", "🤌", "🤏", "✌", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
                    "👆", "👇", "☝", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🙏", "💪", "🦾"},
            {"🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
                    "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🐺", "🐗", "🐴", "🦄", "🐝", "🦋", "🐌", "🐞", "🐢", "🐍"},
            {"🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
                    "🍅", "🥑", "🍆", "🥔", "🥕", "🌽", "🌶", "🥒", "🥬", "🥦", "🍄", "🥜", "🍞", "🧀", "🍔", "🍟"},
            {"🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛵", "🏍",
                    "🛺", "🚲", "🛴", "🚨", "🚔", "🚍", "🚘", "🚖", "✈", "🚀", "🚁", "⛵", "🚢", "🚉", "🚇", "🚆"},
            {"⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
                    "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸", "🥌"},
            {"💡", "🔦", "🕯", "🪔", "📱", "💻", "⌨", "🖥", "🖨", "🖱", "🖲", "💽", "💾", "💿", "📷", "🎥",
                    "📺", "📻", "🎙", "⏰", "⌚", "📚", "✏", "📌", "✂", "🔒", "🔑", "🔨", "🧰", "🧲", "🧪", "🧬"},
            {"❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖",
                    "✨", "⭐", "🌟", "💫", "⚡", "🔥", "💥", "☀", "🌙", "☁", "☔", "❄", "☃", "✅", "❌", "⭕"},
            {"🏳", "🏴", "🏁", "🚩", "🇹🇼", "🇯🇵", "🇰🇷", "🇺🇸", "🇨🇦", "🇬🇧", "🇫🇷", "🇩🇪", "🇮🇹", "🇪🇸", "🇦🇺", "🇳🇿",
                    "🇸🇬", "🇭🇰", "🇲🇴", "🇹🇭", "🇻🇳", "🇵🇭", "🇲🇾", "🇮🇩", "🇮🇳", "🇧🇷", "🇲🇽", "🇳🇱", "🇸🇪", "🇨🇭", "🇪🇺", "🇺🇳"}
    };

    //private String mWordSeparators;
    //private String misMatched;  //Removed by Jeremy '13,1,10

    private LinkedList<Mapping> mCandidateList; //Jeremy '12,5,7 renamed from templist

    private Vibrator mVibrator;
    private AudioManager mAudioManager;


    private boolean hasVibration = false;
    private boolean hasSound = false;
    private boolean hasNumberMapping = false;
    private boolean hasSymbolMapping = false;
    // Cached root set (imkeys) for the active IM — the authoritative acceptance/selkey test.
    // Refreshed in initialIMKeyboard(); empty only for a custom IM imported without imkeys.
    private String currentImKeys = "";
    private final HashMap<String, String> imConfigCache = new HashMap<>();
    private boolean hasQuickSwitch = false;

    // Hard Keyboad Shift + Space Status
    private boolean hasShiftPress = false;
    private boolean onlyShiftPress = false;  //Jeremy '15,5,30 shift only to switch between chi/eng

    private boolean hasCtrlPress = false; // Jeremy '11,5,13
    private boolean lastKeyCtrl = false;  // Jeremy '15,5,30 for process physical keyboard ctrl-space with missing space down event
    private boolean spaceKeyPress = false; // Jeremy '15,5,30 for process physical keyboard ctrl-space with missing space down event
    private boolean hasWinPress = false; // Jeremy '12,4,29 windows start key on standard windows keyboard
    //private boolean hasCtrlProcessed = false; // Jeremy '11,6.18
    private boolean hasDistinctMultitouch;// Jeremy '11,8,3
    private boolean hasShiftCombineKeyPressed = false; //Jeremy ,11,8, 3
    private boolean hasMenuPress = false; // Jeremy '11,5,29
    private boolean hasMenuProcessed = false; // Jeremy '11,5,29
    //private boolean hasSearchPress = false; // Jeremy '11,5,29
    //private boolean hasSearchProcessed = false; // Jeremy '11,5,29

    private boolean hasEnterProcessed = false; // Jeremy '11,6.18
    private boolean hasSpaceProcessed = false;
    private boolean hasKeyProcessed = false; // Jeremy '11,8,15 for long pressed key
    private int mLongPressKeyTimeout; //Jeremy '11,8, 15 read long press timeout from config

    private boolean hasSymbolEntered = false; //Jeremy '11,5,24

    // private boolean hasSpacePress = false;

    // Hard Keyboad Shift + Space Status
    //private boolean hasAltPress = false;

    private String mIMActivatedState = ""; // Jeremy '12,5,3, renamed from keyboardSelectedState
    public String activeIM;  //Jeremy '12,4,30 renamed from keyboardSelection
    private List<String> activatedIMFullNameList; //Jeremy '12,4,30 renamed from keyboardList
    private List<String> activatedIMShortNameList; //Jeremy '12,4,30 renamed from keyboardShortname
    private List<String> activatedIMList; //Jeremy '12,4,30 renamed from keybaordCodeList
    private String currentSoftKeyboard = "";  //Jeremy '12,4,30 renamed from keybaord_xml;

    // To keep key press time
    //private long keyPressTime = 0;

    // Keep keydown event
    KeyEvent mKeydownEvent = null;

    //private int previousKeyCode = 0;
    //private final float moveLength = 15;
    //private ISearchService SearchSrv = null;
    private SearchServer SearchSrv = null;

    // Auto Commmit Value
    private int auto_commit = 0;


    // Disable physical keyboard candidate words selection
    private boolean disable_physical_selection = false;

    // Replace Keycode.KEYCODE_CTRL_LEFT/RIGHT, ESC on android 3.x
    // for backward compatibility of 2.x
    static final int MY_KEYCODE_ESC = 111;
    static final int MY_KEYCODE_CTRL_LEFT = 113;
    static final int MY_KEYCODE_CTRL_RIGHT = 114;
    static final int MY_KEYCODE_ENTER = 10;
    static final int MY_KEYCODE_SPACE = 32;
    static final int MY_KEYCODE_SWITCH_CHARSET = 95;
    static final int MY_KEYCODE_WINDOWS_START = 117; //Jeremy '12,4,29 windows start key

    private String LDComposingBuffer = ""; //Jeremy '11,7,30 for learning continuous typing phrases

    private LIMEPreferenceManager mLIMEPref;

    private boolean hasChineseSymbolCandidatesShown = false;
    private boolean hasCandidatesShown = false;

    // Track last known good bottom padding for older APIs (21-25) where window insets
    // might incorrectly include keyboard height when keyboard is restored
    private final int mLastKnownBottomPadding = -1;
    private int mLastUiNightMode = -1;
    private long mAppliedStartupConfigVersion = -1L;
    private boolean mStartupConfigSnapshotReady = false;
    private List<org.limeime.data.Keyboard> mStartupKeyboardConfigList = null;
    private List<ImConfig> mStartupImKeyboardConfigList = null;
    private int mInputViewGeneration = 0;
    private int mAppliedFollowSystemAccent = 0;
    private boolean mAppliedFollowSystemDarkTheme = false;
    private View mAppliedFollowSystemInputView = null;
    private View mAppliedFollowSystemCandidateView = null;
    private View mAppliedFollowSystemEmbeddedCandidateView = null;
    private boolean mNavigationBarThemeApplied = false;
    private android.view.Window mAppliedNavigationBarWindow = null;
    private View mAppliedNavigationBarCandidateView = null;
    private int mAppliedNavigationBarColor = 0;
    private boolean mAppliedNavigationBarLightBackground = false;


    /**
     * Main initialization of the input method component. Be sure to call to
     * super class.
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {

        if (DEBUG) Log.i(TAG, "OnCreate()");

        super.onCreate();

        SearchSrv = new SearchServer(this);
        mEnglishOnly = false;
        mEnglishFlagShift = false;

        // Initialize default preferences from XML on first run
        // This must be called before creating LIMEPreferenceManager
        // PreferenceManager.setDefaultValues() loads XML defaults into SharedPreferences
        androidx.preference.PreferenceManager.setDefaultValues(this, R.xml.preference, false);
        Log.i(TAG, "onCreate() - Default preferences initialized from XML");

        // Construct Preference Access Tool
        mLIMEPref = new LIMEPreferenceManager(this);
        mDictationController = new LIMEDictationController(new AndroidSpeechRecognizerAdapter(this), this);

        // Initialize hasVibration flag from preferences immediately (so it's available for first keypress)
        hasVibration = mLIMEPref.getVibrateOnKeyPressed();
        Log.i(TAG, "onCreate() - initialized hasVibration: " + hasVibration);

        // Initialize vibrator for haptic feedback
        Log.i(TAG, "onCreate() - Initializing Vibrator service, API level: " + android.os.Build.VERSION.SDK_INT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // API 31+: use VibratorManager
            android.os.VibratorManager vibratorManager = (android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vibratorManager != null) {
                mVibrator = vibratorManager.getDefaultVibrator();
            }
        } else {
            // API 22-30: use deprecated VIBRATOR_SERVICE
            mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        Log.i(TAG, "onCreate() - mVibrator = " + (mVibrator != null ? "valid" : "null"));

        // Initialize AudioManager for sound feedback
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Log.i(TAG, "onCreate() - AudioManager obtained, mAudioManager = " + (mAudioManager != null ? "valid" : "null"));

        // mFixedCandidateViewOn is always true, so we can remove the variable
        // mFixedCandidateViewOn = mLIMEPref.getFixedCandidateViewDisplay();

        mLongPressKeyTimeout = getResources().getInteger(R.integer.config_long_press_key_timeout); // Jeremy '11,8,15 read longpress timeout from config resources.


        // initial keyboard list
        activatedIMFullNameList = new ArrayList<>();
        activatedIMList = new ArrayList<>();
        activatedIMShortNameList = new ArrayList<>();
        activeIM = mLIMEPref.getActiveIM();
        buildActivatedIMList();

        // Register receiver for voice input results
        registerVoiceInputReceiver();

    }


    /**
     * This is the point where you can do all of your UI initialization. It is
     * called after creation and any configuration change.
     */
    @Override
    public void onInitializeInterface() {

        if (DEBUG)
            Log.i(TAG, "onInitializeInterface()");

        initialViewAndSwitcher(false);
        initCandidateView(); //Force the oncreatedcandidate to be called
        mKeyboardSwitcher.resetKeyboards(true);
        super.onInitializeInterface();

    }
    @Override
    public void onCancel(){
        if(DEBUG)
            Log.i(TAG, "onCancel()");
    }

    /**
     * Override show_ime_with_hard_keyboard=0 which  prevent inputView shown
     *
     * @return always true
     */
    @Override
    public boolean onEvaluateInputViewShown() {
        boolean result = super.onEvaluateInputViewShown();
        Configuration config = getResources().getConfiguration();
        if (DEBUG)
            Log.i(TAG, "onEvaluateInputViewShown():" + result
                    + " config.keyboard :" + config.keyboard
                    + " config.hardKeyboardHidden :" + config.hardKeyboardHidden);
        return true;
//        return result;
//        return config.keyboard == Configuration.KEYBOARD_NOKEYS
//                || config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES;
    }

    /**
     * Called by the system when the device configuration changes while your activity is running.
     */
    @Override
    public void onConfigurationChanged(Configuration conf) {

        if (DEBUG)
            Log.i(TAG, "LIMEService:OnConfigurationChanged()");


        //Jeremy '12,4,7 add hard keyboard hidden configuration changed event and clear composing to avoid fc.
        if (conf.orientation != mOrientation || conf.hardKeyboardHidden != mHardkeyboardHidden) {
            //Jeremy '12,4,21 force clear the composing buffer
            clearComposing(true);


            mOrientation = conf.orientation;
            mHardkeyboardHidden = conf.hardKeyboardHidden;
        }
        int newUiMode = conf.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int oldUiMode = mLastUiNightMode;
        mLastUiNightMode = newUiMode;
        if (mKeyboardThemeIndex == 6 && newUiMode != oldUiMode) {
            mThemeContext = null;   // force theme rebuild
            clearAppliedFollowSystemAccentState();
            clearAppliedNavigationBarThemeState();
        }

        initialViewAndSwitcher(true);
        mKeyboardSwitcher.resetKeyboards(true);
        super.onConfigurationChanged(conf);

    }

    /**
     * Called by the framework when your view for creating input needs to be
     * generated. This will be called the first time your input method is
     * displayed, and every time it needs to be re-created such as due to a
     * configuration change.
     */
    @Override
    public View onCreateInputView() {
        if (DEBUG)
            Log.i(TAG, "OnCreateInputView()");


        if (mInputView != null) mInputView = null;

        initialViewAndSwitcher(true);  //Jeremy '12,4,29.  will do buildactivekeyboardlist in init startInput

        View inputView;
        // mFixedCandidateViewOn is always true
        if (DEBUG)
            Log.i(TAG, "Fixed candidateView in on, return nInputViewContainer ");
        inputView = mCandidateInInputView;

        // For API 35+, apply window insets to prevent overlap with system gesture navigation bar
        // Apply padding to the entire container to ensure both candidate view and keyboard view
        // have proper spacing from the navigation bar
        if (inputView != null && shouldForceImeEdgeToEdge(android.os.Build.VERSION.SDK_INT)) {
            ViewCompat.setOnApplyWindowInsetsListener(mCandidateInInputView, (v, insets) -> {
                int systemBarsType = WindowInsetsCompat.Type.systemBars();
                int bottomInset = insets.getInsets(systemBarsType).bottom;
                v.setPadding(v.getPaddingLeft(), 0,
                        v.getPaddingRight(), bottomInset);

                if (DEBUG) {
                    Log.i(TAG, "Applied window insets to InputView container - bottom: " + bottomInset
                            + ", API: " + android.os.Build.VERSION.SDK_INT
                            + ", keyboard visible: " + (mInputView != null && mInputView.getVisibility() == View.VISIBLE)
                            + ", saved: " + mLastKnownBottomPadding);
                }

                // Return insets to allow proper layout measurement
                return insets;
            });
        }

        // Touch listeners will be set up in onStartInputView() after views are fully initialized

        // Issue #46: Tint nav bar to match active keyboard theme
        applyNavigationBarTheme();

        return inputView;
    }

    /**
     * Create and return the view hierarchy used to show candidates.
     * This will be called once, when the candidates are first displayed.
     * You can return null to have no candidates view; the default implementation returns null.
     */

    @Override
    public View onCreateCandidatesView() {
        if (DEBUG)
            Log.i(TAG, "onCreateCandidatesView()");

        // Candidates are embedded in R.layout.inputcandidate. Returning a
        // framework candidates view here creates a second strip above the IME.
        return null;
    }

    /**
     * Override this to control when the input method should run in fullscreen mode.
     * Jeremy '11,5,31
     * Override fullscreen editing mode settings for larger screen  (>1.4in)
     */
    @Override
    public boolean onEvaluateFullscreenMode() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float displayHeight = dm.heightPixels;
        // If the display is more than X inches high, don't go to fullscreen mode
        float max = getResources().getDimension(R.dimen.max_height_for_fullscreen);
        if (DEBUG)
            Log.i(TAG, "onEvaluateFullScreenMode() DisplayHeight:" + displayHeight + " limit:" + max
                    + "super.onEvaluateFullscreenMode():" + super.onEvaluateFullscreenMode());
        //Jeremy '12,4,30 Turn off evaluation only for tablet and xhdpi phones (required horizontal >900pts)
        return !(displayHeight > max && this.getMaxWidth() > 900) && super.onEvaluateFullscreenMode();
    }

    /**
     * This is called when the user is done editing a field. We can use this to
     * reset our state.
     */

    @Override
    public void onFinishInput() {

        if (DEBUG) {
            Log.i(TAG, "onFinishInput()");
        }
        // Stop monitoring IME changes when input finishes, except while a delegated
        // VoiceIME handoff is in progress. The handoff itself triggers onFinishInput().
        if (!mIsVoiceInputActive) {
            stopMonitoringIMEChanges();
        }
        cancelInlineDictation();
        // Don't unregister voice input receiver if voice input is in progress,
        // otherwise the broadcast carrying recognized text will be lost.
        if (!mIsVoiceInputActive) {
            unregisterVoiceInputReceiver();
        }
        super.onFinishInput();

        // mFixedCandidateViewOn is always true, so this branch is never executed
        // if (!mFixedCandidateViewOn && mInputView != null) {
        //     mInputView.closing();
        // }
        try {
            if (!LDComposingBuffer.isEmpty()) { // Force interrupt the LD process
                LDComposingBuffer = "";
                SearchSrv.addLDPhrase(null, true);
            }
            // Jeremy '11,8,1 do postfinishinput in searchSrv (learn userdic and LDPhrase).
            SearchSrv.postFinishInput();
        } catch (RemoteException e) {
            Log.e(TAG, "Error in postFinishInput", e);
        }
        // Clear current composing text and candidates.
        //Jeremy '12,5,21
        finishComposing();

        // -> 26.May.2011 by Art : Update keyboard list when user click the keyboard.
        try {
            mKeyboardSwitcher.setKeyboardConfigList(SearchSrv.getKeyboardConfigList());
            mKeyboardSwitcher.setImConfigKeyboardList(SearchSrv.getAllImKeyboardConfigList());
        } catch (RemoteException e) {
            Log.e(TAG, "Error setting keyboard/IM list in onFinishInput", e);
        }

    }

    /**
     * add by Jeremy '12,4,21
     * Send ic.finishComposingText upon composing is about to end
     */
    private void finishComposing() {
        if (DEBUG)
            Log.i(TAG, "finishComposing()");
        //Jeremy '11,8,14
        if (mComposing != null && mComposing.length() > 0)
            mComposing.setLength(0);

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();

        selectedCandidate = null;
        //selectedIndex = 0;

        if (mCandidateList != null)
            mCandidateList.clear();
        if (mCandidateView != null)
            mCandidateView.clear();
    }

    /**
     * add by Jeremy '12,4,21
     * clearComposing buffer upon composing is about to end
     * add forceClearComposing parameter to control forced clear the system composing buffer
     */
    private void clearComposing(boolean forceClearComposing) {
        if (DEBUG)
            Log.i(TAG, "clearComposing()");

        //Log.i(TAG, "===========> clear composing");

        try {
            //Jeremy '11,8,14
            if (mComposing != null && mComposing.length() > 0)
                mComposing.setLength(0);
            if (mCandidateList != null)
                mCandidateList.clear();

            if (forceClearComposing) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText("", 0);
            }

            selectedCandidate = null;
            //selectedIndex = 0;

            clearSuggestions();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing candidates", e);
            // ignore candidate clear error
        }
    }

    /**
     * Clear suggestions or candidates in candidate view.
     */
    private synchronized void clearSuggestions() {
        if (mCandidateView != null) {
            if (DEBUG)
                Log.i(TAG, "clearSuggestions(): "
                        + ", hasCandidatesShown:" + hasCandidatesShown);

            // mFixedCandidateViewOn is always true, so (hasCandidatesShown || mFixedCandidateViewOn) is always true
            if (!mEnglishOnly && mLIMEPref.getAutoChineseSymbol()) {   // Change isCandiateShown() to hasCandiatesShown
                mCandidateView.clear();
                if (hasCandidatesShown)
                    updateChineseSymbol(); // Jeremy '12.5,23 do not show chinesesymbol when init for fixed candidate view.
            } else {
                mCandidateView.clear();
                hideCandidateView();
            }

            // Update CandidateView width constraint after clearing suggestions
            if (mCandidateInInputView != null) {
                mCandidateInInputView.updateCandidateViewWidthConstraint();
            }

        }
    }

    /**
     * Jeremy '15,7,8 to avoid candidateView shift up and down when it's not fixed.
     */
    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        // Always use embedded candidate view in InputView, so no need to compute insets
        // The embedded candidate view is part of the inputView, so insets are handled automatically
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application. At this point we have been bound to
     * the client, and are now receiving all of the detailed information about
     * the target of our edits.
     */
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        if (DEBUG)
            Log.i(TAG, "onStartInput()");
        super.onStartInputView(attribute, restarting);
        initOnStartInput(attribute);

        // Don't restore keyboard view here - only restore when user explicitly touches
        // the soft keyboard area (candidate view or InputView container)
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        if (DEBUG)
            Log.i(TAG, "onStartInputView()");
        super.onStartInputView(attribute, restarting);
        resetEmojiKeyboardState();

        // Ensure InputView container is visible
        if (mCandidateInInputView != null) {
            mCandidateInInputView.setVisibility(View.VISIBLE);
        }

        // Save composing text before initOnStartInput() in case it clears state
        String savedComposing = (mComposing != null && mComposing.length() > 0) ? mComposing.toString() : null;
        // Save hasPhysicalKeyPressed state before initOnStartInput()
        boolean savedHasPhysicalKeyPressed = hasPhysicalKeyPressed;

        initOnStartInput(attribute);

        // Restore composing text if it was set by a physical key press before InputView was shown
        if (savedComposing != null && savedHasPhysicalKeyPressed) {
            // Restore hasPhysicalKeyPressed state and composing text
            hasPhysicalKeyPressed = true;
            mComposing.setLength(0);
            mComposing.append(savedComposing);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null && mPredictionOn) {
                ic.setComposingText(mComposing, 1);
            }
            // Update candidates to show the composing text and candidates for the first key
            updateCandidates();
            // Ensure candidate view is shown
            hasCandidatesShown = true;
            // Hide keyboard view when physical key was pressed
            if (mInputView != null) {
                mInputView.setVisibility(View.GONE);
            }
        } else {
            // No composing text to preserve, reset hasPhysicalKeyPressed and show keyboard view
            hasPhysicalKeyPressed = false;
            if (mInputView != null) {
                mInputView.setVisibility(View.VISIBLE);
            }
        }

        // Don't restore keyboard view here - only restore when user explicitly touches
        // the soft keyboard area (candidate view or InputView container)
        // This prevents restoring when InputView is shown but user is still using physical keyboard

        // Commit any voice text: check static field first (primary), then instance field (backup)
        String voiceText = VoiceInputActivity.consumePendingVoiceText();
        if (voiceText == null && mPendingVoiceText != null) {
            voiceText = mPendingVoiceText;
            mPendingVoiceText = null;
        }
        if (voiceText != null) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                String textToCommit = prepareVoiceTextForCommit(voiceText);
                ic.commitText(textToCommit, 1);
                Log.i(TAG, "onStartInputView(): Committed voice text: '" + textToCommit + "'");
            } else {
                Log.w(TAG, "onStartInputView(): IC still null, storing voice text for retry");
                mPendingVoiceText = voiceText;
            }
            mIsVoiceInputActive = false;
        }

        // Issue #46: Tint nav bar to match active keyboard theme (re-apply in case theme changed)
        applyNavigationBarTheme();
    }

    /**
     * Initialization for IM and softkeybaords, and also choose wring lanaguage mode
     * according the input attrubute in editorInfo
     */
    private void initOnStartInput(EditorInfo attribute) {


        if (DEBUG)
            Log.i(TAG, "initOnStartInput(): attribute.inputType & EditorInfo.TYPE_MASK_CLASS: "
                    + (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) + "; attribute.inputType & EditorInfo.TYPE_MASK_VARIATION: "
                    + (attribute.inputType & EditorInfo.TYPE_MASK_VARIATION));


        //Jeremy '12,5,29 override the fixCandidateMode setting in Landscape mode (in landscape mode the candidate bar is always not fixed).
        // mFixedCandidateViewOn is always true, so we don't need to check fixedCandidateMode
        //Jeremy '12,5,6 recreate inputView if fixedCandidateView setting is altered - REMOVED: always true now
        //Jeremy '15,7,15 recreate inputView if keyboard theme changed
        // mFixedCandidateViewOn is always true, so mFixedCandidateViewOn != fixedCandidateMode is always false
        if (mKeyboardThemeIndex != mLIMEPref.getKeyboardTheme()) {
            requestHideSelf(0);
            mInputView.closing();
            initialViewAndSwitcher(true);

            // mFixedCandidateViewOn is always true
            if (DEBUG)
                Log.i(TAG, "Fixed candidateView in on, return nInputViewContainer ");
            if (mCandidateInInputView != null)
                setInputView(mCandidateInInputView);

        }

        // Don't reset hasPhysicalKeyPressed if it was just set by a physical key press
        // This prevents losing the first key when InputView is shown after physical key press
        if (!hasPhysicalKeyPressed) {
            // Show keyboard view when hasPhysicalKeyPressed is false
            if (mInputView != null) {
                mInputView.setVisibility(View.VISIBLE);
            }
        } else {
            // Hide keyboard view when hasPhysicalKeyPressed is true
            if (mInputView != null) {
                mInputView.setVisibility(View.GONE);
            }
        }
        // Don't reset hasCandidatesShown if a physical key was just pressed and composing text exists
        // This prevents losing the first key when InputView is shown after physical key press
        if (!hasPhysicalKeyPressed || (mComposing == null || mComposing.length() == 0)) {
            hasCandidatesShown = false;
        }

        activeIM = mLIMEPref.getActiveIM();
        boolean startupConfigRefreshed = refreshStartupConfigSnapshotIfNeeded();
        applyStartupConfigSnapshotToKeyboardSwitcher();

        mKeyboardSwitcher.resetKeyboards(
                startupConfigRefreshed
                        || mShowArrowKeys != mLIMEPref.getShowArrowKeys() //Jeremy '12,5,22 recreate keyboard if the setting altered.
                        || mSplitKeyboard != mLIMEPref.getSplitKeyboard()); //Jeremy '12,5,26 recreate keyboard if the setting altered.

        loadSettings();
        mImeOptions = attribute.imeOptions;

        if (mKeyboardSwitcher != null) {
            mKeyboardSwitcher.setActivatedIMList(activatedIMList, activatedIMShortNameList);
        }
        mPredictionOn = true;
        mCompletionOn = false;
        mCompletions = null;
        mCapsLock = false;
        mHasShift = false;


        tempEnglishWord = new StringBuffer();
        tempEnglishList = new LinkedList<>();


        switch (effectiveInputClass(attribute.inputType)) {
            case EditorInfo.TYPE_CLASS_NUMBER:  //0x02
                mEnglishOnly = true;
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        getRestrictedFieldKeyboardMode(attribute.inputType), mImeOptions, false,
                        getRestrictedFieldSymbolFlag(attribute.inputType), false);
                break;
            case EditorInfo.TYPE_CLASS_DATETIME: //0x04
                mEnglishOnly = true;
                mKeyboardSwitcher.setKeyboardMode(activeIM, LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, false, true, false);
                break;
            case EditorInfo.TYPE_CLASS_PHONE: //0x03
                mEnglishOnly = true;
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_PHONE, mImeOptions, false, false, false);
                break;
            case EditorInfo.TYPE_CLASS_TEXT: //0x01

                // Make sure that passwords are not displayed in candidate view
                int variation = attribute.inputType
                        & EditorInfo.TYPE_MASK_VARIATION;
            /*
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME) {
                    //mAutoSpace = false;
                } else {
                    //mAutoSpace = true;
                }
                */
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    mPredictionOn = false;
                }
                /*
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0) {
                    //disableAutoCorrect = true;
                }*/
                // If NO_SUGGESTIONS is set, don't do prediction.
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    mPredictionOn = false;
                    //disableAutoCorrect = true;
                }
                // If it's not multiline and the autoCorrect flag is not set, then
                // don't correct
                /*
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0
                        && (attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
                    //disableAutoCorrect = true;
                }*/
                if ((attribute.inputType & EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }

                // Switch keyboard here.
                if (isForcedEnglishTextVariation(variation)) {
                    mPredictionOn = false;
                    mEnglishOnly = true;
                    mKeyboardSwitcher.setKeyboardMode(activeIM,
                            LIMEKeyboardSwitcher.MODE_EMAIL, mImeOptions, false, false, false);
                    break;
                } else if (variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                    mEnglishOnly = false;
                    mKeyboardSwitcher.setKeyboardMode(activeIM, LIMEKeyboardSwitcher.MODE_IM, mImeOptions, true, false, false);
                    break;
                }
            default:
                if (mPersistentLanguageMode)
                    mEnglishOnly = mLIMEPref.getLanguageMode(); //Jeremy '12,4,30 restore lanaguage mode from preference.

                if (mPersistentLanguageMode && mEnglishOnly) {
                    mPredictionOn = true;
                    //mEnglishOnly = true;
                    //onIM = false; //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                    mKeyboardSwitcher.setKeyboardMode(activeIM, LIMEKeyboardSwitcher.MODE_TEXT,
                            mImeOptions, false, false, false);

                } else {
                    mEnglishOnly = false;
                    prepareImKeyboardConfigForChineseDraw(true);
                    initialIMKeyboard();  //'12,4,29 intial chinese IM keybaord
                }
        }


        if (!(mEnglishOnly && !mPredictionOn)) {
            clearComposing(false);//Jeremy '12,5,24 clear the suggesions and also restore the height of fixed candaiteview if it's hide before
            //clearSuggestions();  // do this in clearcomposing already.
        }
        // Keep toolbar visible for mic/emoji even when no candidates are active.
        showEmptyCandidateToolbar();

        mPredicting = false;
        updateShiftKeyState(getCurrentInputEditorInfo());


        //initCandidateView(); //Force the oncreatedcandidate to be called
        //clearComposing(false);

    }

    private void loadSettings() {

        hasVibration = mLIMEPref.getVibrateOnKeyPressed();
        hasSound = mLIMEPref.getSoundOnKeyPressed();
        mPersistentLanguageMode = mLIMEPref.getPersistentLanguageMode();
        activeIM = mLIMEPref.getActiveIM();
        hasQuickSwitch = mLIMEPref.getSwitchEnglishModeHotKey();
        mAutoCap = mLIMEPref.getAutoCaptalization();

        mPersistentLanguageMode = mLIMEPref.getPersistentLanguageMode();
        mShowArrowKeys = mLIMEPref.getShowArrowKeys();
        mSplitKeyboard = mLIMEPref.getSplitKeyboard();

        disable_physical_selection = mLIMEPref.getDisablePhysicalSelkey();

        auto_commit = mLIMEPref.getAutoCommitValue();
        currentSoftKeyboard = mKeyboardSwitcher.getImConfigKeyboard(activeIM);


    }

    private boolean isStartupConfigSnapshotDirty() {
        if (mLIMEPref == null || !mStartupConfigSnapshotReady) return true;
        long currentVersion = mLIMEPref.getStartupConfigVersion();
        return currentVersion == 0L || currentVersion != mAppliedStartupConfigVersion;
    }

    private void invalidateStartupConfigSnapshot() {
        mStartupConfigSnapshotReady = false;
        mAppliedStartupConfigVersion = -1L;
    }

    private boolean refreshStartupConfigSnapshotIfNeeded() {
        return refreshStartupConfigSnapshotIfNeeded(true);
    }

    private boolean refreshStartupConfigSnapshotIfNeeded(boolean rebuildActivatedIMList) {
        if (!isStartupConfigSnapshotDirty()) return false;

        if (rebuildActivatedIMList) {
            buildActivatedIMList();
        }

        boolean loadedStartupCriticalConfig = false;
        if (SearchSrv != null) {
            try {
                List<Keyboard> keyboardConfigList = SearchSrv.getKeyboardConfigList();
                List<ImConfig> imKeyboardConfigList = SearchSrv.getAllImKeyboardConfigList();
                if (imKeyboardConfigList != null && !imKeyboardConfigList.isEmpty()) {
                    mStartupKeyboardConfigList = keyboardConfigList;
                    mStartupImKeyboardConfigList = imKeyboardConfigList;
                    loadedStartupCriticalConfig = true;
                } else {
                    Log.w(TAG, "Startup keyboard config snapshot not marked clean: IM keyboard config list is empty");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error refreshing startup keyboard config snapshot", e);
            }
        } else {
            Log.w(TAG, "Startup keyboard config snapshot not marked clean: SearchSrv is unavailable");
        }

        if (!loadedStartupCriticalConfig) {
            mStartupConfigSnapshotReady = false;
            return false;
        }

        mStartupConfigSnapshotReady = true;
        markStartupConfigSnapshotApplied();
        return true;
    }

    private void markStartupConfigSnapshotApplied() {
        if (mLIMEPref == null) return;
        long currentVersion = mLIMEPref.getStartupConfigVersion();
        if (currentVersion == 0L) {
            currentVersion = mLIMEPref.initializeStartupConfigVersion();
        }
        mAppliedStartupConfigVersion = currentVersion;
    }

    private void applyStartupConfigSnapshotToKeyboardSwitcher() {
        if (mKeyboardSwitcher == null) return;
        if (mStartupKeyboardConfigList != null && !mStartupKeyboardConfigList.isEmpty()) {
            mKeyboardSwitcher.setKeyboardConfigList(mStartupKeyboardConfigList);
        }
        if (mStartupImKeyboardConfigList != null && !mStartupImKeyboardConfigList.isEmpty()) {
            mKeyboardSwitcher.setImConfigKeyboardList(mStartupImKeyboardConfigList);
        }
    }

    private void prepareImKeyboardConfigForChineseDraw(boolean rebuildActivatedIMList) {
        refreshStartupConfigSnapshotIfNeeded(rebuildActivatedIMList);
        applyStartupConfigSnapshotToKeyboardSwitcher();
        if (mKeyboardSwitcher != null) {
            mKeyboardSwitcher.setActivatedIMList(activatedIMList, activatedIMShortNameList);
        }

        if (activeImKeyboardMappingMissing()) {
            Log.w(TAG, "Active IM keyboard mapping missing before Chinese draw; forcing startup config refresh. activeIM="
                    + activeIM + ", activatedIMList=" + activatedIMList);
            invalidateStartupConfigSnapshot();
            refreshStartupConfigSnapshotIfNeeded(rebuildActivatedIMList);
            applyStartupConfigSnapshotToKeyboardSwitcher();
            if (mKeyboardSwitcher != null) {
                mKeyboardSwitcher.setActivatedIMList(activatedIMList, activatedIMShortNameList);
            }
            if (activeImKeyboardMappingMissing()) {
                Log.w(TAG, "Active IM keyboard mapping still missing after forced refresh. activeIM="
                        + activeIM + ", activatedIMList=" + activatedIMList);
            }
        }
    }

    private boolean activeImKeyboardMappingMissing() {
        if (mKeyboardSwitcher == null || activeIM == null || activeIM.isEmpty()) return false;
        if (activatedIMList == null || !activatedIMList.contains(activeIM)) return false;
        String keyboard = mKeyboardSwitcher.getImConfigKeyboard(activeIM);
        return keyboard == null || keyboard.isEmpty();
    }

    private void advanceInputViewGeneration() {
        mInputViewGeneration++;
        clearAppliedFollowSystemAccentState();
        clearAppliedNavigationBarThemeState();
    }

    private void postAfterFirstFrame(Runnable task) {
        if (mCandidateInInputView == null || task == null) return;
        final int generation = mInputViewGeneration;
        mCandidateInInputView.post(() -> runIfCurrentInputViewGeneration(generation, task));
    }

    private void runIfCurrentInputViewGeneration(int generation, Runnable task) {
        if (task == null || generation != mInputViewGeneration) return;
        task.run();
    }

    int getInputViewGenerationForTesting() {
        return mInputViewGeneration;
    }

    void advanceInputViewGenerationForTesting() {
        advanceInputViewGeneration();
    }

    void runIfCurrentInputViewGenerationForTesting(int generation, Runnable task) {
        runIfCurrentInputViewGeneration(generation, task);
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                  int newSelStart, int newSelEnd, int candidatesStart,
                                  int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);

        if (DEBUG)
            Log.i(TAG, "onUpdateSelection():oldSelStart" + oldSelStart
                    + " oldSelEnd:" + oldSelEnd
                    + " newSelStart:" + newSelStart + " newSelEnd:" + newSelEnd
                    + " candidatesStart:" + candidatesStart + " candidatesEnd:" + candidatesEnd);

        InputConnection ic = getCurrentInputConnection();

        if (mComposing.length() > 0
                && !(candidatesEnd == candidatesStart) //Jeremy '12,7,2 bug fixed on composition being clear after second word in chrome
                && candidatesStart >= 0 && candidatesEnd > 0 // in composing
        ) {
            if (newSelStart < candidatesStart || newSelStart > candidatesEnd) { // cursor is moved before or after composing area

                if (mCandidateList != null) mCandidateList.clear();
                //mCandidateView.clear();
                hideCandidateView();

                if (mComposing != null && mComposing.length() > 0) {

                    mComposing.setLength(0);


                    if (ic != null)
                        ic.finishComposingText();
                }
            }
            // Jeremy '13,8,25 setSelection cause inputbox in Chorme failed to input
            // Jeremy '12,5,23 Select the composing text and forbidded moving cursor within the composing text.
            //if (ic != null)	ic.setSelection(candidatesStart, candidatesEnd);


        }


    }

    /**
     * This tells us about completions that the editor has determined based on
     * the current text in it. We want to use this in fullscreen mode to show
     * the completions ourself, since the editor can not be seen in that
     * situation.
     */
    @Override
    public void onDisplayCompletions(CompletionInfo[] completions) {
        if (DEBUG)
            Log.i(TAG, "onDisplayCompletions()");
        if (mCompletionOn) {
            mCompletions = completions;
            if (!mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                if (mComposing.length() == 0) updateRelatedPhrase(false);
            }
            if (mEnglishOnly && !mPredictionOn) {
                setSuggestions(buildCompletionList(), false, "");
            }

        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection. It is only needed when using the PROCESS_HARD_KEYS
     * option.
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event) {

        hasPhysicalKeyPressed = true;
        // Hide keyboard view when physical key is pressed
        if (mInputView != null) {
            mInputView.setVisibility(View.GONE);
        }

        // Request layout update for candidate view container to show buttons
        if (mCandidateInInputView != null) {
            mCandidateInInputView.post(mCandidateInInputView::requestLayout);
        }

        // Show InputView when physical key is pressed to display embedded candidate view
        // Store flag to show InputView after key is processed to avoid losing the first key
        final boolean needToShowInputView = !isInputViewShown();

        //Jeremy '25/12/14 Always use fix candidateView even for physical keyboard. (API 34+ cannot shown candidateView well)
        // If user use the physical keyboard then not fixed the candidate view also use the transparent background
//        if(mCandidateView!=null) {
//            mFixedCandidateViewOn = false;
//            mCandidateView.setTransparentCandidateView(false);
//        }


        if (DEBUG)
            Log.i(TAG, "translateKeyDown() LIMEMetaKeyKeyListener.getMetaState(mMetaState) = "
                    + Integer.toHexString(LIMEMetaKeyKeyListener.getMetaState(mMetaState))
                    + ", event.getMetaState()" + Integer.toHexString(event.getMetaState()));

        //Jeremy '12,5,28 after honeycomb use the metastate sent form KeyEvent to process the shift/cap_lock etc...

        int metaState;
        if (mLIMEPref.getPhysicalKeyboardType().equals(LIME.IM_PHONETIC))
            metaState = event.getMetaState();
        else
            metaState = LIMEMetaKeyKeyListener.getMetaState(mMetaState);


        int c = event.getUnicodeChar(metaState);


        InputConnection ic = getCurrentInputConnection();

        /// Jeremy '12,4,1 XPERIA Pro force translating special keys
        if (mLIMEPref.getPhysicalKeyboardType().equals("xperiapro")) {
            boolean isShift = LIMEMetaKeyKeyListener.getMetaState(mMetaState,
                    LIMEMetaKeyKeyListener.META_SHIFT_ON) > 0;
            switch (keyCode) {
                case KeyEvent.KEYCODE_AT:
                    if (isShift) c = '/';
                    else c = '!';
                    break;
                case KeyEvent.KEYCODE_APOSTROPHE:
                    if (isShift) c = '"';
                    else c = '\'';
                    break;
                case KeyEvent.KEYCODE_GRAVE:
                    if (isShift) c = '~';
                    else c = '`';
                    break;
                case KeyEvent.KEYCODE_COMMA:
                    if (isShift) c = '?';
                    else c = '.';
                    break;
                case KeyEvent.KEYCODE_PERIOD:
                    if (isShift) c = '>';
                    else c = '@';
                    break;

            }
        }

        if (c == 0 || ic == null) {
            return false;
        }

        // Compact code by Jeremy '10, 3, 27
        if (keyCode == 59) { // Translate shift as -1
            c = -1;
        }
        if (c != -1 && (c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }

        // Process the key first to ensure it's added to composing
        onKey(c, null);

        // Show InputView after key is processed to avoid losing the first key
        // Note: We manage InputView visibility directly via mInputView.setVisibility()
        // instead of using requestShowSelf() which causes IllegalAccessError on some Android versions
        if (needToShowInputView) {
            // Use post() to ensure onKey() completes first, then verify composing text is set
            new Handler(Looper.getMainLooper()).post(() -> {
                // Ensure composing text is set in InputConnection before showing InputView
                if (mComposing != null && mComposing.length() > 0) {
                    InputConnection inputConn = getCurrentInputConnection();
                    if (inputConn != null && mPredictionOn) {
                        // Explicitly set composing text to ensure it's committed before showing InputView
                        inputConn.setComposingText(mComposing, 1);
                    }
                }
                // Show InputView directly by setting visibility
                // The system will show the IME when InputView becomes visible
                if (mInputView != null && mInputView.getVisibility() != View.VISIBLE) {
                    mInputView.setVisibility(View.VISIBLE);
                    if (mCandidateInInputView != null) {
                        mCandidateInInputView.setVisibility(View.VISIBLE);
                    }
                }
            });
        }

        return true;
    }


    /**
     * Physical KeyBoard Event Handler Use this to monitor key events being
     * delivered to the application. We get first crack at them, and can either
     * resume them or let them continue to the app.
     */
    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        // Clean code by jeremy '11,8,22
        if (DEBUG)
            Log.i(TAG, "OnKeyDown():keyCode:" + keyCode
                    + ", mComposing = " + mComposing
                    + ", hasMenuPress = " + hasMenuPress
                    + ", hasCtrlPress = " + hasCtrlPress
                    + ", isCtrlPressed = " + event.isCtrlPressed()
                    + ", hasShiftPress = " + hasShiftPress
                    + ", onlyShiftPress = " + onlyShiftPress
                    + ", hasWinPress = " + hasWinPress
                    + ", event.getEventTime() -  event.getDownTime()" + (event.getEventTime() - event.getDownTime())
                    + ", event.getRepeatCount()" + event.getRepeatCount()
                    + ", event.getMetaState()" + Integer.toHexString(event.getMetaState()));

        // Show InputView when physical key is pressed to display embedded candidate view
        // This ensures candidates are visible even when using physical keyboard
        // if (mInputView != null && !isInputViewShown()) {
        //     requestShowSelf(0);
        // }

        mKeydownEvent = new KeyEvent(event);
        // Record key pressed time and set key processed flags(key down, for physical keys)
        //Jeremy '11,8,22 using getRepeatCount from event to set processed flags
        if (event.getRepeatCount() == 0) {//!keydown) {
            //keyPressTime = System.currentTimeMillis();
            //keydown = true;
            hasKeyProcessed = false;
            hasMenuProcessed = false; // only do this on first keydown event
            hasEnterProcessed = false;
            hasSpaceProcessed = false;
            hasSymbolEntered = false;
            //Jeremy '15,5,30 for physical keyboard
            onlyShiftPress = false;
            lastKeyCtrl = false;
            spaceKeyPress = false;
        }


        switch (keyCode) {
            // Jeremy '11,5,29 Bypass search and menu combination keys.
            case KeyEvent.KEYCODE_MENU:
                hasMenuPress = true;
                break;
            // Add by Jeremy '10, 3, 29. DPAD selection on candidate view
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                    mCandidateView.selectNext();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                    mCandidateView.selectPrev();
                    return true;
                }
                break;
            //Jeremy '11,8,28 for expanded candidateView
            case KeyEvent.KEYCODE_DPAD_UP:
                if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                    mCandidateView.selectPrevRow();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                    mCandidateView.selectNextRow();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                    pickHighlightedCandidate();
                    return true;
                }
                break;
            // Add by Jeremy '10,3,26, process metaKey with
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                hasShiftPress = true;
                onlyShiftPress = true;
                mMetaState = LIMEMetaKeyKeyListener.handleKeyDown(mMetaState, keyCode, event);
                break;
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                mMetaState = LIMEMetaKeyKeyListener.handleKeyDown(mMetaState, keyCode, event);
                break;
            case MY_KEYCODE_CTRL_LEFT:
            case MY_KEYCODE_CTRL_RIGHT:
                hasCtrlPress = true;
                lastKeyCtrl = true;
                break;
            case MY_KEYCODE_WINDOWS_START:
                hasWinPress = true;
                break;
            case MY_KEYCODE_ESC:
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.

                if (event.getRepeatCount() == 0) {
                    if (mInputView != null && mInputView.handleBack()) {
                        Log.i(TAG, "KEYCODE_BACK mInputView handled the backed key");
                        return true;
                    }
                    //Jeremy '12,4,8 rewrite the logic here
                    //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                    //TODO: need to recheck here.
                    else if (!mEnglishOnly
                            && hasCandidatesShown
                            && (mComposing.length() > 0
                            || (selectedCandidate != null && !selectedCandidate.isComposingCodeRecord()
                            && !hasChineseSymbolCandidatesShown))) {
                        if (DEBUG)
                            Log.i(TAG, "KEYCODE_BACK clear composing only.");
                        clearComposing(false);
                        return true;
                    } else if (!mEnglishOnly && hasCandidatesShown) { //Jeremy '12,6,13
                        hideCandidateView();
                        return true;
                    }

                }
                if (DEBUG)
                    Log.i(TAG, "KEYCODE_BACK return to super.");

                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                hasPhysicalKeyPressed = true;
                // Hide keyboard view when physical key is pressed
                if (mInputView != null) {
                    mInputView.setVisibility(View.GONE);
                }
                onKey(LIMEBaseKeyboard.KEYCODE_DELETE, null);
                return true;

            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these, if return
                // false from takeSelectedSuggestion().
                // Process enter for candidate view selection in OnKeyUp() to block
                // the real enter afterward.
                // return false;
                // Log.i("ART", "physical keyboard:"+ keyCode);
                mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
                setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState();
                if (!mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                    if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                        // To block a real enter after suggestion selection. We have to
                        // return true in OnKeyUp();
                        if (pickHighlightedCandidate()) {
                            hasEnterProcessed = true;
                            return true;
                        } else {
                            hideCandidateView();
                            break;
                        }
                    }
                } else if (//mLIMEPref.getEnglishPrediction() &&
                        mPredictionOn && mLIMEPref.getEnglishPredictionOnPhysicalKeyboard()) {
                    resetTempEnglishWord();
                    this.updateEnglishPrediction();
                    break;
                } else  //Jeremy '12',7,1 bug fixed on english mode enter not functioning in chrome
                    break;

/*		case MY_KEYCODE_ESC:
        //Jeremy '11,9,7 treat esc as back key
			//Jeremy '11,8,14
			clearComposing();
			InputConnection ic=getCurrentInputConnection();
			if(ic!=null) ic.commitText("", 0);
			return true;*/

            case KeyEvent.KEYCODE_SPACE:
                spaceKeyPress = true;
                hasQuickSwitch = mLIMEPref.getSwitchEnglishModeHotKey();
                // If user enable Quick Switch Mode control then check if has
                // 	Shift+Space combination
                // '11,5,13 Jeremy added Ctrl-space switch chi/eng
                // '11,6,18 Jeremy moved from on_KEY_UP
                // '12,4,29 Jeremy add hasWinPress + space to switch chi/eng (earth key on zippy keyboard)
                // '12,5,8  Jeremy add send the space key to onKey with translatekeydown for candidate processing if it's not switching chi/eng
                if ((hasQuickSwitch && hasShiftPress) || hasCtrlPress || hasMenuPress || hasWinPress || event.isCtrlPressed()) {
                    if (!hasWinPress)
                        this.switchChiEng();  //Jeremy '12,5,20 move hasWinPress to winstartkey in onkeyUp()
                    if (hasMenuPress) hasMenuProcessed = true;
                    hasSpaceProcessed = true;
                    return true;
                } else
                    return translateKeyDown(keyCode, event);

            case MY_KEYCODE_SWITCH_CHARSET: // experia pro earth key
            case 1000: // milestone chi/eng key
                switchChiEng();
                break;
            case KeyEvent.KEYCODE_SYM:
            case KeyEvent.KEYCODE_AT:
                //Jeremy '11,8,22 use begintime and eventtime in event to see if long-pressed or not.
                if (!hasKeyProcessed
                        && event.getRepeatCount() > 0
                        && event.getEventTime() - event.getDownTime() > mLongPressKeyTimeout) {
                    //&& System.currentTimeMillis() - keyPressTime > mLongPressKeyTimeout){
                    switchChiEng();
                    hasKeyProcessed = true;
                }
                return true;
            case KeyEvent.KEYCODE_TAB: // Jeremy '12.6,22 Force bypassing tab processing to super if not on milestone 2 with alt on (alt+tab = ~ on milestone2)
                if (!(LIMEMetaKeyKeyListener.getMetaState(mMetaState,
                        LIMEMetaKeyKeyListener.META_ALT_ON) > 0
                        && mLIMEPref.getPhysicalKeyboardType().equals("milestone2")))
                    break;
            default:
                if (!(hasCtrlPress || event.isCtrlPressed() || hasMenuPress)) {
                    if (translateKeyDown(keyCode, event)) {
                        if (DEBUG) Log.i(TAG, "Onkeydown():tranlatekeydown:true");
                        return true;
                    }
                }

        }


        if ((hasCtrlPress || hasMenuPress) && !mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
            int primaryKey = event.getUnicodeChar(LIMEMetaKeyKeyListener.getMetaState(mMetaState));
            char t = (char) primaryKey;


            if (hasCtrlPress &&  //Only working with ctrl Jeremy '11,8,22
                    mCandidateList != null && !mCandidateList.isEmpty()
                    && mCandidateView != null && hasCandidatesShown) {
                switch (keyCode) {
                    case 8:
                        this.pickCandidateManually(0);
                        return true;
                    case 9:
                        this.pickCandidateManually(1);
                        return true;
                    case 10:
                        this.pickCandidateManually(2);
                        return true;
                    case 11:
                        this.pickCandidateManually(3);
                        return true;
                    case 12:
                        this.pickCandidateManually(4);
                        return true;
                    case 13:
                        this.pickCandidateManually(5);
                        return true;
                    case 14:
                        this.pickCandidateManually(6);
                        return true;
                    case 15:
                        this.pickCandidateManually(7);
                        return true;
                    case 16:
                        this.pickCandidateManually(8);
                        return true;
                    case 7:
                        this.pickCandidateManually(9);
                        return true;
                }
            }
            if ((mComposing == null || mComposing.length() == 0)) {
                // Jeremy '11,8,21.  Ctrl-/ to fetch full-shaped chinese symbols1 in candidateview.
                if (t == '/') {
                    if (hasMenuPress) hasMenuProcessed = true;
                    updateChineseSymbol();
                    return true;
                }
                // 27.May.2011 Art : when user click Ctrl + Symbol or number then send Chinese Symobl Characters
                String s = ChineseSymbol.getSymbol(t);
                if (s != null) {
                    clearSuggestions();
                    getCurrentInputConnection().commitText(s, 0);
                    hasSymbolEntered = true;
                    if (hasMenuPress) hasMenuProcessed = true;
                    return true;

                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void resetTempEnglishWord() {
        tempEnglishWord.delete(0, tempEnglishWord.length());
        tempEnglishList.clear();
    }

    private void setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            int clearStatesFlags = 0;
            if (LIMEMetaKeyKeyListener.getMetaState(mMetaState,
                    LIMEMetaKeyKeyListener.META_ALT_ON) == 0)
                clearStatesFlags += KeyEvent.META_ALT_ON;
            if (LIMEMetaKeyKeyListener.getMetaState(mMetaState,
                    LIMEMetaKeyKeyListener.META_SHIFT_ON) == 0)
                clearStatesFlags += KeyEvent.META_SHIFT_ON;
            if (LIMEMetaKeyKeyListener.getMetaState(mMetaState,
                    LIMEMetaKeyKeyListener.META_SYM_ON) == 0)
                clearStatesFlags += KeyEvent.META_SYM_ON;
            ic.clearMetaKeyStates(clearStatesFlags);
        }
    }

    /**
     * Use this to monitor key events being delivered to the application. We get
     * first crack at them, and can either resume them or let them continue to
     * the app.
     */
    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        if (DEBUG)
            Log.i(TAG, "OnKeyUp():keyCode:" + keyCode
                    + ", mComposing = " + mComposing
                    + ", hasCtrlPress:" + hasCtrlPress
                    + ", hasWinPress:" + hasWinPress
                    + ", hasShiftPress = " + hasShiftPress
                    + ", event.getEventTime() -  event.getDownTime()" + (event.getEventTime() - event.getDownTime())

            );


        switch (keyCode) {
            //Jeremy '11,5,29 Bypass search and menu keys.
//		case KeyEvent.KEYCODE_SEARCH:
//			hasSearchPress = false;
//			if(hasSearchProcessed) return true;
//			break;
            case KeyEvent.KEYCODE_CAPS_LOCK:
                // Modified by Art 20130607
                // to switch the cap lock mode
                toggleCapsLock();
            case KeyEvent.KEYCODE_MENU:
                hasMenuPress = false;
                if (hasMenuProcessed) return true;
                break;
            // */------------------------------------------------------------------------
            // Modified by Jeremy '10, 3,12
            // keep track of alt state with mHasAlt.
            // Modified '10, 3, 24 for bug fix and alt-lock implementation
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                hasShiftPress = false;
                mMetaState = LIMEMetaKeyKeyListener.handleKeyUp(mMetaState, keyCode, event);
                // '11,8,28 Jeremy popup keyboard picker instead of nextIM when onIM
                // '11,5,14 Jeremy ctrl-shift switch to next available keyboard;
                // '11,5,24 blocking switching if full-shape symbol
                if (!hasSymbolEntered && !mEnglishOnly && (hasMenuPress || hasCtrlPress)) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                    //nextActiveKeyboard(true);
                    showIMPicker(); //Jeremy '11,8,28
                    if (hasMenuPress) {
                        hasMenuProcessed = true;
                        hasMenuPress = false;
                    }
                    mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
                    setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState();
                    return true;
                } else if (mLIMEPref.getShiftSwitchEnglishMode() && onlyShiftPress) {
                    this.switchChiEng();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                mMetaState = LIMEMetaKeyKeyListener.handleKeyUp(mMetaState, keyCode, event);
                break;
            case MY_KEYCODE_CTRL_LEFT:
            case MY_KEYCODE_CTRL_RIGHT:
                hasCtrlPress = false;
                break;
            case MY_KEYCODE_WINDOWS_START:
                if (hasSpaceProcessed) //Jeremy '12,5,20 long press to show IM picker, switch chi/eng otherwise for the win+space or earth key on zippy
                    if (event.getEventTime() - event.getDownTime() > mLongPressKeyTimeout)
                        showIMPicker();
                    else
                        switchChiEng();
                hasWinPress = false;
                break;
            case KeyEvent.KEYCODE_ENTER:
                // Add by Jeremy '10, 3 ,29. Pick selected selection if candidates
                // shown.
                // Does not block real enter after select the suggestion. !! need
                // fix here!!
                // Let the underlying text editor always handle these, if return
                // false from takeSelectedSuggestion().

                if (hasEnterProcessed) {
                    return true;
                }
                // Jeremy '10, 4, 12 bug fix on repeated enter.
                break;

            case KeyEvent.KEYCODE_SYM:
            case KeyEvent.KEYCODE_AT:
                if (hasKeyProcessed) {  //(keyPressTime != 0
                    //&& System.currentTimeMillis() - keyPressTime > 700) {
                    //switchChiEng(); // Jeremy '11,8,15 moved to onKeyDown()
                    return true;
                } else if (LIMEMetaKeyKeyListener.getMetaState(mMetaState,
                        LIMEMetaKeyKeyListener.META_SHIFT_ON) > 0 && !mEnglishOnly //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                        && !mLIMEPref.getPhysicalKeyboardType().equals("xperiapro")) {  // '12,4,1 Jeremy XPERIA Pro does not use this key as @
                    // alt-@ is conflict with symbol input thus altered to shift-@ Jeremy '11,8,15
                    // alt-@ switch to next active keyboard.
                    //nextActiveKeyboard(true);
                    showIMPicker(); //Jeremy '11,8,28
                    mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
                    setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState();
                    return true;
                    // Long press physical @ key to swtich chn/eng
                } else if ((!mEnglishOnly || mPredictionOn)
                        && translateKeyDown(keyCode, event)) {
                    return true;
                } else {
                    translateKeyDown(keyCode, event);
                    super.onKeyDown(keyCode, mKeydownEvent);
                }
                break;

            case KeyEvent.KEYCODE_SPACE:
                //Jeremy move the chi/eng switching to on_KEY_UP '11,6,18

                if (!spaceKeyPress && lastKeyCtrl) { //missing space down event when ctrl-space is pressed
                    this.switchChiEng();
                    return true;
                }

                if (hasSpaceProcessed)
                    return true;
            default:

        }
        // Update metakeystate of IC maintained by MetaKeyKeyListerner
        //setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState(); moved to OnKey by jeremy '12,6,13

        if (DEBUG)
            Log.i(TAG, "OnKeyUp():keyCode:" + keyCode
                    + ";hasCtrlPress:" + hasCtrlPress
                    + ";hasWinPress:" + hasWinPress
                    + ", event.getEventTime() -  event.getDownTime()" + (event.getEventTime() - event.getDownTime())
                    + " call super.onKeyUp()"
            );


        return super.onKeyUp(keyCode, event);
    }


    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection ic) {
        if (DEBUG)
            Log.i(TAG, "commitTyped()");
        if (selectedCandidate == null) return;
        try {
            if ((mComposing.length() > 0   //denotes composing just finished
                    || !selectedCandidate.isComposingCodeRecord()) // commit selected candidate if it is not the composing text. '15,6,4 Jeremy  (like related phrase or English suggestions)
                    && !(LIMEUtilities.isUnicodeSurrogate(selectedCandidate.getWord())
                            && selectedCandidate.isEmojiRecord())) {   //emoji surrogate path bypasses related-phrase flow; CJK Ext-B (non-emoji surrogate) must use main flow for #62

                if (!mEnglishOnly
                        || !selectedCandidate.isComposingCodeRecord()
                        || !selectedCandidate.isEnglishSuggestionRecord()) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                    if (selectedCandidate != null && selectedCandidate.getWord() != null
                            && !selectedCandidate.getWord().isEmpty()) {

                        int firstMatchedLength = 1;

//                        if (selectedCandidate.getCode() == null
//                                || selectedCandidate.getCode().isEmpty()) {
//                            firstMatchedLength = 1;
//                        }

                        String wordToCommit = selectedCandidate.getWord();

//                        if (selectedCandidate != null
//                                && selectedCandidate.getCode() != null
//                                && selectedCandidate.getWord() != null) {
//                            if (selectedCandidate
//                                    .getCode()
//                                    .toLowerCase(Locale.US)
//                                    .equals(selectedCandidate.getWord()
//                                            .toLowerCase(Locale.US))) {
//                                firstMatchedLength = 1;
//
//
//                            }
//                        }

                        if (DEBUG)
                            Log.i(TAG, "commitTyped() committed Length="
                                    + firstMatchedLength);

                        // Do hanConvert before commit
                        // '10, 4, 17 Jeremy
                        if (mLIMEPref.getHanCovertOption() == 0) {
                            if (ic != null) ic.commitText(wordToCommit, firstMatchedLength);
                        } else {
                            if (ic != null)
                                ic.commitText(SearchSrv.hanConvert(wordToCommit), firstMatchedLength);
                        }
                        if (selectedCandidate.isEmojiRecord() && SearchSrv != null) {
                            SearchSrv.recordEmojiUsage(wordToCommit);
                            mEmojiCategoryPages = null;
                        }

                        // Art '30,Sep,2011 when show related then clear composing
                        if (currentSoftKeyboard.contains("wb") || selectedCandidate.isEmojiRecord() || selectedCandidate.isChinesePunctuationSymbolRecord()) {
                            clearComposing(true);
                        }


                        // Jeremy '11,7,28 for continuous typing (LD)
                        // Jeremy '12,6,2 get real committed code length from searchserver
                        boolean composingNotFinish = false;
                        //Jeremy '15,6,2 retrieve real code length with selectedCandidate using exact code match stack in search server
                        int committedCodeLength = SearchSrv.getRealCodeLength(selectedCandidate, mComposing.toString());

                        if (DEBUG)
                            Log.i(TAG, "commitTyped(): committedCodeLength = " + committedCodeLength);

                        if (mComposing.length() > selectedCandidate.getCode().length()) {
                            composingNotFinish = true;
                        }

                        boolean shouldUpdateCandidates = false;
                        if (composingNotFinish) {
                            if (LDComposingBuffer.isEmpty()) {
                                //starting LD process
                                LDComposingBuffer = mComposing.toString();
                                if (DEBUG)
                                    Log.i(TAG, "commitTyped():starting LD process, LDBuffer=" + LDComposingBuffer +
                                            ". just committed code= '" + selectedCandidate.getCode() + "'");
                                SearchSrv.addLDPhrase(selectedCandidate, false);
                            } else {
                                //Continuous LD process
                                if (DEBUG)
                                    Log.i(TAG, "commitTyped():Continuous LD process, LDBuffer='" + LDComposingBuffer +
                                            "'. just committed code=" + selectedCandidate.getCode());
                                SearchSrv.addLDPhrase(selectedCandidate, false);
                            }
                            mComposing = mComposing.delete(0, committedCodeLength);
                            if (DEBUG)
                                Log.i(TAG, "commitTyped(): trimmed mComposing = '" + mComposing + "', " +
                                        "+ mComposing.length = " + mComposing.length());

                            if (!mComposing.toString().equals(" ")) {
                                if (mComposing.toString().startsWith(" "))
                                    mComposing = mComposing.deleteCharAt(0);
                                if (DEBUG)
                                    Log.i(TAG, "commitTyped(): new mComposing:'" + mComposing + "'");
                                if (mComposing.length() > 0) { //Jeremy '12,7,11 only fetch remaining composing when length >0
                                    if (ic != null && mPredictionOn)
                                        ic.setComposingText(mComposing, 1);
                                    shouldUpdateCandidates = true;
                                }
                            }
                        } else {

                            if (!LDComposingBuffer.isEmpty()) {// && LDComposingBuffer.contains(mComposing.toString())){
                                //Ending continuous LD process (last of LD process)
                                if (DEBUG)
                                    Log.i(TAG, "commitTyped():Ending LD process, LDBuffer=" + LDComposingBuffer +
                                            ". just committed code=" + selectedCandidate.getCode());
                                LDComposingBuffer = "";
                                SearchSrv.addLDPhrase(selectedCandidate, true);
                            } else {
                                //LD process interrupted.
                                if (DEBUG)
                                    Log.i(TAG, "commitTyped():LD process interrupted, LDBuffer=" + LDComposingBuffer +
                                            ". just committed code=" + selectedCandidate.getCode());
                                LDComposingBuffer = "";
                                SearchSrv.addLDPhrase(null, true);
                            }


                        }

                        //Jeremy '13,1,10 do update score and reverse lookup after updateRelatedPhrase to shorten the time user see related candidates after select a candidate.
                        if (shouldUpdateCandidates) {
                            updateCandidates();
                        } else {
                            committedCandidate = new Mapping(selectedCandidate);
                            selectedCandidate = null;
                            clearComposing(false);
                            updateRelatedPhrase(false);

                            if (committedCandidate != null && committedCandidate.getWord() != null) {
                                SearchSrv.learnRelatedPhraseAndUpdateScore(committedCandidate);

                                //do reverse lookup and display notification if required.
                                SearchSrv.getCodeListStringFromWord(committedCandidate.getWord());
                            }
                        }

                    } else {
                        if (ic != null) ic.commitText(mComposing,
                                mComposing.length());

                    }
                } else {  //English mode or composing code or English run-time suggestion
                    if (ic != null) {
                        ic.commitText(mComposing, mComposing.length());
                        if (!mEnglishOnly) clearComposing(false);
                    }

                }


            } else if (LIMEUtilities.isUnicodeSurrogate(selectedCandidate.getWord())
                    && selectedCandidate.isEmojiRecord()) { //Jeremy '15,7,16; narrowed to emoji-only so CJK Ext-B uses main flow (#62)
                ic.commitText(selectedCandidate.getWord(), 1);
                clearComposing(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in keyboard handling", e);
        }
    }


    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    public void updateShiftKeyState(EditorInfo attr) {
        if (DEBUG) Log.i(TAG, "updateShiftKeyState() ");
        InputConnection ic = getCurrentInputConnection();
        if (attr != null && mInputView != null
                && mKeyboardSwitcher.isAlphabetMode() && ic != null) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (mAutoCap && ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = ic.getCursorCapsMode(attr.inputType);
                if (caps == 0 && mEnglishOnly
                        && shouldAutoCapitalizeEnglishText(ic.getTextBeforeCursor(64, 0))) {
                    caps = 1;
                }
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        } else {
            if (!mCapsLock && mHasShift) {
                mKeyboardSwitcher.toggleShift();
                mHasShift = false;
            }
        }

    }

    static boolean shouldAutoCapitalizeEnglishText(CharSequence beforeCursor) {
        if (beforeCursor == null || beforeCursor.length() == 0) {
            return true;
        }

        int end = beforeCursor.length();
        boolean hasBoundaryWhitespace = false;
        while (end > 0) {
            char c = beforeCursor.charAt(end - 1);
            if (c == ' ' || c == '\t') {
                hasBoundaryWhitespace = true;
                end--;
            } else {
                break;
            }
        }
        while (end > 0 && isEnglishClosingPunctuation(beforeCursor.charAt(end - 1))) {
            end--;
        }
        if (end == 0) {
            return true;
        }

        char term = beforeCursor.charAt(end - 1);
        if (term == '\n' || term == '\r') {
            return true;
        }
        if (!hasBoundaryWhitespace) {
            return false;
        }
        if (term != '.' && term != '!' && term != '?') {
            return false;
        }
        return term != '.' || !isEnglishAbbreviationBeforeDot(beforeCursor, end - 1);
    }

    static boolean shouldInsertPeriodForEnglishDoubleSpace(CharSequence beforeCursor) {
        if (beforeCursor == null || beforeCursor.length() < 2
                || beforeCursor.charAt(beforeCursor.length() - 1) != ' ') {
            return false;
        }

        int previousIndex = beforeCursor.length() - 2;
        char previous = beforeCursor.charAt(previousIndex);
        if (".!?,:;".indexOf(previous) >= 0) {
            return false;
        }

        int tokenStart = previousIndex;
        while (tokenStart > 0 && !Character.isWhitespace(beforeCursor.charAt(tokenStart - 1))) {
            tokenStart--;
        }
        String token = beforeCursor.subSequence(tokenStart, previousIndex + 1).toString();
        if (token.contains("://") || token.contains(".")) {
            return false;
        }

        return Character.isLetterOrDigit(previous) || isEnglishClosingPunctuation(previous);
    }

    private static boolean isEnglishClosingPunctuation(char c) {
        return c == '"' || c == '\'' || c == ')' || c == ']' || c == '}'
                || c == '\u201D' || c == '\u2019';
    }

    private static boolean isEnglishAbbreviationBeforeDot(CharSequence text, int dotIndex) {
        if (dotIndex <= 0 || !Character.isLetter(text.charAt(dotIndex - 1))) {
            return false;
        }
        if (dotIndex >= 2 && text.charAt(dotIndex - 2) == '.') {
            return true;
        }

        int start = dotIndex - 1;
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) {
            start--;
        }
        String word = text.subSequence(start, dotIndex).toString();
        return "Mr".equals(word) || "Mrs".equals(word) || "Ms".equals(word)
                || "Dr".equals(word) || "Prof".equals(word) || "Jr".equals(word)
                || "Sr".equals(word) || "St".equals(word) || "etc".equals(word)
                || "vs".equals(word) || "Ltd".equals(word) || "Inc".equals(word)
                || "Co".equals(word) || "Mt".equals(word) || "Ft".equals(word);
    }

    private boolean isValidLetter(int code) {
        return Character.isLetter(code);
    }

    private boolean isValidDigit(int code) {
        return Character.isDigit(code);
    }

    private boolean isValidSymbol(int code) {
        String checkCode = String.valueOf((char) code);
        // code has to < 256, a ascii character
        return code < 256 && checkCode.matches(".*?[^A-Z]")
                && checkCode.matches(".*?[^a-z]")
                && checkCode.matches(".*?[^0-9]") && code != 32;
    }

    static boolean acceptsIntoComposing(int code, String imkeys, boolean hasSymbol, boolean hasNumber, boolean isPhonetic) {
        boolean isPhoneticSpace = code == MY_KEYCODE_SPACE && isPhonetic;

        // Authoritative path: the active IM's imkeys decide root-ness (isKeyInImkeys is true for
        // ',' / '.' for every IM). Mirrors iOS KeyboardViewController.acceptsIntoComposing — the
        // heuristic below runs ONLY for a custom IM imported without imkeys.
        if (imkeys != null && !imkeys.isEmpty()) {
            return isKeyInImkeys(code, imkeys) || isPhoneticSpace;
        }

        // Fallback — custom-no-imkeys ONLY. Legacy hasSymbol/hasNumber heuristic, unchanged
        // (keeps the ',' / '.' full-width branch for non-symbol custom tables).
        boolean isLetter = Character.isLetter(code);
        boolean isDigit = Character.isDigit(code);
        boolean isSymbol = code < 256
                && (code < 'A' || code > 'Z')
                && (code < 'a' || code > 'z')
                && (code < '0' || code > '9')
                && code != MY_KEYCODE_SPACE;
        if (!hasSymbol && (code == ',' || code == '.')) {
            return true;
        }
        if (!hasSymbol && !hasNumber) {
            return isLetter || isPhoneticSpace;
        }
        if (!hasSymbol) {
            return isLetter || isDigit;
        }
        if (!hasNumber) {
            return isLetter || isSymbol || isPhoneticSpace;
        }
        return isSymbol || isPhoneticSpace || isLetter || isDigit;
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode, boolean sendToSelf) {
        InputConnection ic = getCurrentInputConnection();

        long eventTime = SystemClock.uptimeMillis();
        KeyEvent downEvent = new KeyEvent(eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyEventCode, 0, 0, 0, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
        KeyEvent upEvent = new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyEventCode, 0, 0, 0, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE);
        if (sendToSelf) {  //Jeremy '12,5,23 send to this.onKeyDown and onKeyUp if sendToSelf is true.
            if (!this.onKeyDown(keyEventCode, downEvent) && ic != null)
                ic.sendKeyEvent(downEvent);
            if (!this.onKeyUp(keyEventCode, upEvent) && ic != null)
                ic.sendKeyEvent(upEvent);

        } else if (ic != null) {
            ic.sendKeyEvent(downEvent);
            ic.sendKeyEvent(upEvent);
        }


    }


    public void onKey(int primaryCode, int[] keyCodes) {
        onKey(primaryCode, keyCodes, 0, 0);
    }

    public void onKey(int primaryCode, int[] keyCodes, int x, int y) {
        if (DEBUG)
            Log.i(TAG, "OnKey(): primaryCode:" + primaryCode
                    + " hasShiftPress:" + hasShiftPress);

        hideLimeToast();
        if (mCandidateView != null) {
            mCandidateView.clearDictationErrorIfShowing();
        }

        // Modified by Art
        // This is to fixed the CapsLock issue on Physical keyboard
        if (mCapsLock) {
            if (primaryCode >= 97 && primaryCode <= 122) {
                primaryCode -= 32;
            }
        }
        mLastShiftTime = shiftDoubleTapWindowAfterKey(primaryCode, mLastShiftTime);
        // Adjust metaKeyState on printed key pressed.
        if (hasPhysicalKeyPressed) {  //Jeremy '12,6,11 moved from handleCharacter()
            mMetaState = LIMEMetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
            setInputConnectionMetaStateAsCurrentMetaKeyKeyListenerState(); //Jeremy '12,6,13 moved from OnkeyUP by Jeremy '12,6,13
            if (DEBUG)
                Log.i(TAG, "onKey(): adjustMetaAfterKeypress()");

        }

        if (mEmojiKeyboardShown && (mEmojiSearchFocused || mEmojiSearchMode) && handleEmojiSearchKey(primaryCode)) {
            return;
        }

        if (mLIMEPref.getEnglishPrediction()
                && primaryCode != LIMEBaseKeyboard.KEYCODE_DELETE) {

            // Check if input character not valid English Character then reset
            // temp english string
            if (!Character.isLetter(primaryCode) && mEnglishOnly) {

                //Jeremy '11,6,10. Select english suggestion with shift+123457890
                if (hasPhysicalKeyPressed && (mCandidateView != null && hasCandidatesShown)) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                    if (handleSelkey(primaryCode)) {
                        return;
                    }
                    resetTempEnglishWord();
                    if (!hasCtrlPress)
                        clearSuggestions(); //Jeremy '12,4,29 moved from resetcandidateBar
                }

            }
        }

        // Handle English/Lime Keyboard switch
        if (!mEnglishFlagShift
                && (primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT)) {
            mEnglishFlagShift = true;
        }
        if (primaryCode == LIMEBaseKeyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT) {
            if (DEBUG) Log.i(TAG, "OnKey():KEYCODE_SHIFT");
            if (!(!hasPhysicalKeyPressed && hasDistinctMultitouch))
                handleShift();
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_DONE) {// long press on options and shift
            handleClose();
            // Jeremy '12,5,21 process the arrow keys on soft keyboard
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_UP) {
            keyDownUp(KeyEvent.KEYCODE_DPAD_UP, hasCandidatesShown);
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_DOWN) {
            keyDownUp(KeyEvent.KEYCODE_DPAD_DOWN, hasCandidatesShown);
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_RIGHT) {
            keyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT, hasCandidatesShown);
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_LEFT) {
            keyDownUp(KeyEvent.KEYCODE_DPAD_LEFT, hasCandidatesShown);
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_OPTIONS) {
            handleOptions();
        } else if (primaryCode == LIMEBaseKeyboard.KEYCODE_ONE_HAND_RESTORE) {
            // Issue #169: chevron tap restores full width by setting the integrated
            // portrait mode back to standard, persisted (spec).
            // Rebuild in place — handleClose() would requestHideSelf() and dismiss the IME.
            mLIMEPref.setPhonePortraitKeyboardMode(
                    org.limeime.keyboard.PhoneKeyboardModePolicy.PORTRAIT_STANDARD);
            invalidateStartupConfigSnapshot();
            applyGeometryChangeInPlace();
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_SPACE_LONGPRESS) {
            showIMPicker();
        } else if (primaryCode == KEYCODE_SWITCH_TO_SYMBOL_MODE && mInputView != null) { //->symbol keyboard
            switchKeyboard(primaryCode);
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_PHONE_SIMPLE_LONGPRESS && mInputView != null) {
            switchKeyboard(primaryCode);
        } else if (primaryCode == KEYCODE_SWITCH_SYMBOL_KEYBOARD && mInputView != null) { //->switch symbols1 keyboards
            switchKeyboard(primaryCode);
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_NEXT_IM) {
            switchToNextActivatedIM(true);
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_PREV_IM) {
            switchToNextActivatedIM(false);
        } else if (primaryCode == LIME.KEYCODE_EMOJI_PANEL) {
            showEmojiKeyboard();
        } else if (primaryCode == LIME.KEYCODE_EMOJI_ABC) {
            hideEmojiKeyboard();
        } else if (primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE && mInputView != null) { //chi->eng
            switchKeyboard(primaryCode);
            // Jeremy '11,5,31 Rewrite softkeybaord enter/space and english separator processing.
        } else if (primaryCode == KEYCODE_SWITCH_TO_IM_MODE && mInputView != null) { //eng -> chi
            switchKeyboard(primaryCode);
        } else if (handleEndkeyCommit(primaryCode)) {
            // End-key commit is opt-in per IM table metadata and consumes the trigger key.
        } else if ( //Jeremy '12,7,1 bug fixed on enter not functioning in english mode
                ((primaryCode == MY_KEYCODE_SPACE && !mEnglishOnly && !activeIM.equals(LIME.IM_PHONETIC))
                        || (primaryCode == MY_KEYCODE_SPACE && !mEnglishOnly &&
                        //activeIM.equals(LIME.IM_PHONETIC) && //redundant
                        (mComposing.toString().endsWith(" ") || mComposing.length() == 0))
                        || primaryCode == MY_KEYCODE_ENTER)) {

            if (hasCandidatesShown) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
                if (!pickHighlightedCandidate()) {//Jeremy '12,5,11 fixed for not sending related.
                    if (mComposing.length() == 0)
                        hideCandidateView();
                    sendKeyChar((char) primaryCode);

                }

            } else {
                sendKeyChar((char) primaryCode);
            }

        } else {

            handleCharacter(primaryCode);

            // Art 11, 9, 26 Check if need to auto commit composing
            if (auto_commit > 0 && !mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
                if (mComposing != null && mComposing.length() == auto_commit &&
                        currentSoftKeyboard != null &&
                        (currentSoftKeyboard.contains("phone") || currentSoftKeyboard.contains("computernum"))) { // feat#N02: array10 computer numpad keeps auto-commit
                    InputConnection ic = getCurrentInputConnection();
                    commitTyped(ic);

                }
            }
        }
    }

    static boolean isEndkeyCommitKey(int primaryCode, String endkey, boolean englishOnly,
                                     int composingLength, boolean candidatesShown) {
        return !englishOnly
                && endkey != null
                && endkey.indexOf((char) primaryCode) >= 0;
    }

    private boolean handleEndkeyCommit(int primaryCode) {
        if (!isEndkeyCommitKey(primaryCode, cachedImConfig(LIME.IM_LIME_ENDKEY), mEnglishOnly,
                mComposing.length(), hasCandidatesShown)) {
            return false;
        }

        // End-key routing must use the table's declared roots, not the broader input
        // acceptance rule. isKeyInImkeys() intentionally accepts comma/period for every
        // IM so their synthetic full-width candidates remain available, but treating that
        // fallback as table metadata appends an opted-in punctuation end key to the current
        // code instead of committing the current candidate and then the punctuation.
        if (isKeyDeclaredInImkeys(primaryCode, cachedImConfig(IMKEYS_CONFIG))) {
            return commitComposingWithAppendedEndkey(primaryCode);
        }

        if (mComposing.length() > 0 && !commitCurrentEndkeyComposing()) {
            return false;
        }

        return commitFreshEndkeyOrRaw(primaryCode);
    }

    private void refreshImConfigCache() {
        imConfigCache.clear();
        if (SearchSrv == null || activeIM == null) return;

        cacheImConfig(LIME.IM_LIME_ENDKEY, SearchSrv.getImConfig(activeIM, LIME.IM_LIME_ENDKEY));
        cacheImConfig(IMKEYS_CONFIG, SearchSrv.getImConfig(activeIM, IMKEYS_CONFIG));
        cacheImConfig(IMKEYNAMES_CONFIG, SearchSrv.getImConfig(activeIM, IMKEYNAMES_CONFIG));
    }

    private void cacheImConfig(String field, String value) {
        imConfigCache.put(field, value == null ? "" : value);
    }

    private String cachedImConfig(String field) {
        String value = imConfigCache.get(field);
        return value == null ? "" : value;
    }

    private boolean commitCurrentEndkeyComposing() {
        if (hasCurrentEndkeySelectedCandidate() && pickHighlightedCandidate()) {
            if (mComposing.length() > 0) {
                clearComposing(false);
            }
            hideCandidateView();
            return true;
        }

        if (resolveEndkeySelectedCandidate() != null) {
            commitTyped(getCurrentInputConnection());
            if (mComposing.length() > 0) {
                clearComposing(false);
            }
            hideCandidateView();
            return true;
        }

        if (mComposing.length() == 0) {
            hideCandidateView();
        }
        return false;
    }

    private boolean commitComposingWithAppendedEndkey(int primaryCode) {
        String code = String.valueOf((char) primaryCode);
        mComposing.append(code);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && mPredictionOn) {
            ic.setComposingText(mComposing, 1);
        }
        // The declared root was already appended. Consume this key even when the combined
        // code has no immediate candidate; returning false would fall through to
        // handleCharacter() and append the same punctuation a second time.
        if (!commitResolvedEndkeyComposing()) {
            // Nothing was committed, so the appended root stays in the composing buffer
            // awaiting further input. Drop any stale pre-append selection first: a physical
            // selection key reads mCandidateList/selectedCandidate (gated on hasCandidatesShown)
            // and could otherwise commit the pre-append candidate while the buffer holds the
            // appended root. clearSuggestions()/hideCandidateView() only refresh the view, not
            // this model state, so reset it here, then refresh the strip for the combined code.
            selectedCandidate = null;
            if (mCandidateList != null) {
                mCandidateList.clear();
            }
            hasMappingList = false;
            hasCandidatesShown = false;
            hasChineseSymbolCandidatesShown = false;
            updateCandidates();
        }
        return true;
    }

    private boolean commitFreshEndkeyOrRaw(int primaryCode) {
        String code = String.valueOf((char) primaryCode);
        mComposing.append(code);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && mPredictionOn) {
            ic.setComposingText(mComposing, 1);
        }
        if (commitResolvedEndkeyComposing()) {
            return true;
        }
        clearComposing(false);
        if (ic != null) {
            ic.commitText(code, 1);
        }
        finishComposing();
        return true;
    }

    private boolean commitResolvedEndkeyComposing() {
        if (resolveEndkeySelectedCandidate() == null) {
            return false;
        }
        commitTyped(getCurrentInputConnection());
        if (mComposing.length() > 0) {
            clearComposing(false);
        }
        hideCandidateView();
        return true;
    }

    private Mapping resolveEndkeySelectedCandidate() {
        if (hasCurrentEndkeySelectedCandidate()) {
            return selectedCandidate;
        }
        if (SearchSrv == null || mComposing.length() == 0) {
            return null;
        }
        if (queryThread != null && queryThread.isAlive()) {
            queryThread.interrupt();
        }
        try {
            List<Mapping> candidates = SearchSrv.getMappingByCode(mComposing.toString(),
                    !hasPhysicalKeyPressed, false);
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            mCandidateList = new LinkedList<>(candidates);
            selectedCandidate = endkeyCommitCandidateForSuggestions(mCandidateList);
            hasMappingList = selectedCandidate != null;
            hasCandidatesShown = selectedCandidate != null;
            return selectedCandidate;
        } catch (RemoteException e) {
            Log.e(TAG, "Error resolving end-key candidate", e);
            return null;
        }
    }

    static boolean isKeyInImkeys(int primaryCode, String imkeys) {
        // ',' and '.' are roots for every IM (compose → full-width 「，」「。」). Mirrors iOS
        // KeyboardViewController.isKeyInImkeys, which returns true for code 44 / 46 first.
        if (primaryCode == ',' || primaryCode == '.') {
            return true;
        }
        if (imkeys == null || imkeys.isEmpty()) {
            return false;
        }
        String key = String.valueOf((char) primaryCode);
        return imkeys.contains(key) || imkeys.contains(key.toLowerCase(Locale.US));
    }

    private static boolean isKeyDeclaredInImkeys(int primaryCode, String imkeys) {
        if (imkeys == null || imkeys.isEmpty()) {
            return false;
        }
        String key = String.valueOf((char) primaryCode);
        return imkeys.contains(key) || imkeys.contains(key.toLowerCase(Locale.US));
    }

    private boolean hasCurrentEndkeySelectedCandidate() {
        return selectedCandidate != null
                && !selectedCandidate.isComposingCodeRecord()
                && selectedCandidate.getCode() != null
                && mComposing.toString().equals(selectedCandidate.getCode());
    }

    static Mapping endkeyCommitCandidateForSuggestions(List<Mapping> suggestions) {
        int selectedIndex = endkeyCommitCandidateIndex(suggestions);
        if (selectedIndex < 0) {
            return null;
        }
        return suggestions.get(selectedIndex);
    }

    private static int endkeyCommitCandidateIndex(List<Mapping> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < suggestions.size(); i++) {
            if (isEndkeyCommitCandidate(suggestions.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isEndkeyCommitCandidate(Mapping candidate) {
        return candidate != null
                && !candidate.isComposingCodeRecord()
                && (candidate.isExactMatchToCodeRecord()
                || candidate.isPartialMatchToCodeRecord()
                || candidate.isChinesePunctuationSymbolRecord());
    }

    // General highlight rule (not punctuation-specific):
    // - If the first real candidate after the composing-code echo is an exact match to the
    //   typed code, highlight that candidate (index 1).
    // - Otherwise highlight the composing-code echo (index 0).
    // "Exact match" means the candidate's code equals the composing-code echo's code (the
    // full code the user typed). This deliberately does NOT inspect the record type, so any
    // exact-code candidate -- a DB exact match or an auto-inserted comma/period whose code
    // equals the typed ',' / '.' -- is highlighted the same way. A shorter prefix/partial
    // candidate must not replace the raw composing text when Space or Enter is pressed.
    public static int defaultHighlightedCandidateIndex(List<Mapping> suggestions,
                                                       boolean physicalKeyPressed) {
        if (suggestions == null || suggestions.isEmpty()) {
            return -1;
        }
        Mapping first = suggestions.get(0);
        if (suggestions.size() > 1
                && (suggestions.get(1).isExactMatchToCodeRecord()
                || isExactMatchToComposing(suggestions.get(1), first))) {
            return 1;
        }
        if (first.isComposingCodeRecord() || first.isRuntimeBuiltPhraseRecord()) {
            return 0;
        }
        return -1;
    }

    // True when candidate is an exact match to the composing-code echo: the echo is a
    // composing-code record, candidate is not, both carry a non-empty code, and the codes
    // are equal (the full code the user typed). Generalizes "exact match" beyond
    // exactMatchToCode records so an auto-inserted full-width comma/period whose code equals
    // the typed ',' / '.' is highlighted the same way -- without any punctuation-specific
    // special case.
    private static boolean isExactMatchToComposing(Mapping candidate, Mapping composing) {
        if (candidate == null || composing == null) {
            return false;
        }
        String composingCode = composing.getCode();
        String candidateCode = candidate.getCode();
        return composing.isComposingCodeRecord()
                && !candidate.isComposingCodeRecord()
                && candidateCode != null && !candidateCode.isEmpty()
                && candidateCode.equals(composingCode);
    }

    static Mapping defaultServiceSelectedCandidate(List<Mapping> suggestions,
                                                   boolean physicalKeyPressed) {
        int index = defaultHighlightedCandidateIndex(suggestions, physicalKeyPressed);
        return index >= 0 ? suggestions.get(index) : null;
    }

    public static LinkedList<Mapping> buildEnglishPredictionCandidates(String word,
                                                                       List<Mapping> suggestions) {
        LinkedList<Mapping> result = new LinkedList<>();
        if (word == null || word.isEmpty()) {
            return result;
        }

        Mapping self = new Mapping();
        self.setWord(word);
        self.setComposingCodeRecord();
        result.add(self);

        if (suggestions != null) {
            result.addAll(suggestions);
        }
        return result;
    }

    private void showEmojiKeyboard() {
        mEmojiSourceWasEnglish = mEnglishOnly;
        mEmojiKeyboardShown = true;
        mEmojiCategoryIndex = 0;
        mEmojiSearchFocused = false;
        mEmojiSearchQuery.setLength(0);
        if (mEmojiSearchField != null) {
            mEmojiSearchField.setText("");
        }
        updateEmojiAbcButtonLabel();
        clearComposing(true);
        mInputCandidateStripVisibilityBeforeEmoji = getInputCandidateStripVisibility();
        hideCandidateView();
        setInputCandidateStripVisibility(View.GONE);
        if (mEmojiKeyboardView != null) {
            mEmojiKeyboardView.setVisibility(View.VISIBLE);
        }
        renderEmojiContent("");
        enforceEmojiKeyboardVisibility();
    }

    private void hideEmojiKeyboard() {
        mEmojiKeyboardShown = false;
        mEmojiSearchFocused = false;
        mEmojiSearchMode = false;
        clearEmojiSearchCandidates();
        if (mEmojiKeyboardView != null) {
            mEmojiKeyboardView.setVisibility(View.GONE);
        }
        if (mInputView != null) {
            mInputView.setVisibility(View.VISIBLE);
            restoreEmojiSourceKeyboard();
            mInputView.invalidateAllKeys();
        }
        setInputCandidateStripVisibility(mInputCandidateStripVisibilityBeforeEmoji);
        refreshCandidateInputContainer();
    }

    private void resetEmojiKeyboardState() {
        boolean wasEmojiKeyboardShown = mEmojiKeyboardShown;
        mEmojiKeyboardShown = false;
        mEmojiSearchFocused = false;
        mEmojiSearchMode = false;
        mEmojiSearchQuery.setLength(0);
        clearEmojiSearchCandidates();
        if (mEmojiKeyboardView != null) {
            mEmojiKeyboardView.setVisibility(View.GONE);
        }
        if (mInputView != null) {
            mInputView.setVisibility(View.VISIBLE);
            mInputView.invalidateAllKeys();
        }
        if (wasEmojiKeyboardShown) {
            setInputCandidateStripVisibility(mInputCandidateStripVisibilityBeforeEmoji);
        }
    }

    private void setupEmojiKeyboardView() {
        if (!(mEmojiKeyboardView instanceof FrameLayout)) return;

        FrameLayout container = (FrameLayout) mEmojiKeyboardView;
        container.removeAllViews();
        container.setBackgroundColor(Color.TRANSPARENT);
        mEmojiRoot = null;
        mEmojiScroll = null;
        mEmojiPages = null;
        mEmojiBottomBar = null;
        mEmojiCategoryBar = null;
        mEmojiSearchField = null;
        mEmojiAbcButton = null;
        mEmojiContentRendered = false;

        mEmojiRoot = new LinearLayout(mThemeContext);
        mEmojiRoot.setOrientation(LinearLayout.VERTICAL);
        mEmojiRoot.setPadding(
                dp(EMOJI_PANEL_HORIZONTAL_PADDING_DP),
                dp(EMOJI_PANEL_VERTICAL_PADDING_DP),
                dp(EMOJI_PANEL_HORIZONTAL_PADDING_DP),
                dp(EMOJI_PANEL_VERTICAL_PADDING_DP));
        mEmojiRoot.setMinimumHeight(dp(280));
        container.addView(mEmojiRoot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        mEmojiSearchField = new TextView(mThemeContext);
        mEmojiSearchField.setTextSize(17);
        applyEmojiSearchFieldStyle();
        updateEmojiSearchText();
        mEmojiSearchField.setCompoundDrawablePadding(dp(8));
        mEmojiSearchField.setPadding(dp(14), 0, dp(14), 0);
        mEmojiSearchField.setGravity(Gravity.CENTER_VERTICAL);
        mEmojiSearchField.setOnClickListener(v -> enterEmojiSearchMode());
        mEmojiSearchField.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                enterEmojiSearchMode();
            }
            return true;
        });
        mEmojiRoot.addView(mEmojiSearchField, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(EMOJI_SEARCH_FIELD_HEIGHT_DP)));

        mEmojiScroll = new HorizontalScrollView(mThemeContext);
        mEmojiScroll.setFillViewport(false);
        mEmojiScroll.setHorizontalScrollBarEnabled(false);
        mEmojiScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        mEmojiPages = new LinearLayout(mThemeContext);
        mEmojiPages.setOrientation(LinearLayout.HORIZONTAL);
        mEmojiScroll.addView(mEmojiPages, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mEmojiScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (!mEmojiSearchMode) {
                    updateEmojiCategoryHighlight(categoryIndexForEmojiScroll(scrollX));
                }
            });
        }
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0);
        gridParams.weight = 1;
        gridParams.topMargin = dp(8);
        mEmojiRoot.addView(mEmojiScroll, gridParams);

        float emojiKeyboardSizeScale = getEmojiKeyboardSizeScale();
        int emojiCategoryBottomBarHeight = dp(scaleDp(EMOJI_CATEGORY_BOTTOM_BAR_HEIGHT_DP, emojiKeyboardSizeScale));
        int emojiCategoryTabHeight = dp(scaleDp(EMOJI_CATEGORY_TAB_HEIGHT_DP, emojiKeyboardSizeScale));

        mEmojiBottomBar = new LinearLayout(mThemeContext);
        mEmojiBottomBar.setGravity(Gravity.CENTER_VERTICAL);
        mEmojiBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        mEmojiRoot.addView(mEmojiBottomBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, emojiCategoryBottomBarHeight));

        int emojiSideControlWidth = dp(emojiSideControlWidthDp(emojiKeyboardSizeScale));
        int emojiModeControlGlyphSize = emojiModeControlGlyphSize(emojiKeyboardSizeScale);
        int emojiBackspaceGlyphSize = emojiBackspaceGlyphSize(emojiKeyboardSizeScale);

        TextView abc = createEmojiControl("ABC", emojiModeControlGlyphSize);
        mEmojiAbcButton = abc;
        abc.setOnClickListener(v -> hideEmojiKeyboard());
        mEmojiBottomBar.addView(abc, new LinearLayout.LayoutParams(
                emojiSideControlWidth, emojiCategoryTabHeight));

        HorizontalScrollView emojiCategoryScroll = new HorizontalScrollView(mThemeContext);
        emojiCategoryScroll.setFillViewport(false);
        emojiCategoryScroll.setHorizontalScrollBarEnabled(false);
        emojiCategoryScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        mEmojiCategoryBar = new LinearLayout(mThemeContext);
        mEmojiCategoryBar.setGravity(Gravity.CENTER_VERTICAL);
        mEmojiCategoryBar.setOrientation(LinearLayout.HORIZONTAL);
        emojiCategoryScroll.addView(mEmojiCategoryBar, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                emojiCategoryBottomBarHeight));
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(0, emojiCategoryBottomBarHeight);
        categoryParams.weight = 1;
        mEmojiBottomBar.addView(emojiCategoryScroll, categoryParams);

        TextView backspace = createEmojiControl("⌫", emojiBackspaceGlyphSize);
        backspace.setOnClickListener(v -> handleEmojiBackspace());
        mEmojiBottomBar.addView(backspace, new LinearLayout.LayoutParams(
                emojiSideControlWidth, emojiCategoryTabHeight));

        mEmojiKeyboardView.setVisibility(mEmojiKeyboardShown ? View.VISIBLE : View.GONE);
    }

    private void updateEmojiAbcButtonLabel() {
        if (mEmojiAbcButton != null) {
            mEmojiAbcButton.setText(mEmojiSourceWasEnglish ? "ABC" : "中");
        }
    }

    private void restoreEmojiSourceKeyboard() {
        if (mEmojiSourceWasEnglish) {
            if (!mEnglishOnly && mInputView != null) {
                switchKeyboard(KEYCODE_SWITCH_TO_ENGLISH_MODE);
            }
        } else if (mEnglishOnly && mInputView != null) {
            switchKeyboard(KEYCODE_SWITCH_TO_IM_MODE);
        }
    }

    private void renderEmojiContent(String query) {
        if (mEmojiPages == null || mEmojiCategoryBar == null) return;
        mEmojiContentRendered = true;

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        mEmojiSearchMode = mEmojiSearchFocused || normalizedQuery.length() > 0;
        setInputCandidateStripVisibility(
                emojiSearchInputCandidateStripVisibility(mEmojiKeyboardShown, mEmojiSearchMode));
        mEmojiPages.removeAllViews();
        int searchPanelHeight = emojiSearchPanelHeight();

        if (mEmojiKeyboardView != null) {
            ViewGroup.LayoutParams emojiParams = mEmojiKeyboardView.getLayoutParams();
            if (emojiParams != null) {
                emojiParams.height = mEmojiSearchMode ? searchPanelHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
                mEmojiKeyboardView.setLayoutParams(emojiParams);
            }
        }
        if (mEmojiRoot != null) {
            int horizontalPadding = mEmojiSearchMode ? 0 : dp(EMOJI_PANEL_HORIZONTAL_PADDING_DP);
            int verticalPaddingBottom = mEmojiSearchMode ? 0 : dp(EMOJI_PANEL_VERTICAL_PADDING_DP);
            mEmojiRoot.setPadding(
                    horizontalPadding,
                    dp(EMOJI_PANEL_VERTICAL_PADDING_DP),
                    horizontalPadding,
                    verticalPaddingBottom);
            mEmojiRoot.setMinimumHeight(mEmojiSearchMode ? searchPanelHeight : dp(280));
        }
        if (mEmojiSearchField != null) {
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(EMOJI_SEARCH_FIELD_HEIGHT_DP));
            if (mEmojiSearchMode) {
                int horizontalMargin = dp(EMOJI_PANEL_HORIZONTAL_PADDING_DP);
                searchParams.setMargins(horizontalMargin, 0, horizontalMargin, 0);
            }
            mEmojiSearchField.setLayoutParams(searchParams);
        }
        if (mEmojiScroll != null) {
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0);
            scrollParams.weight = mEmojiSearchMode ? 0 : 1;
            scrollParams.topMargin = mEmojiSearchMode ? 0 : dp(8);
            mEmojiScroll.setLayoutParams(scrollParams);
            mEmojiScroll.setVisibility(mEmojiSearchMode ? View.GONE : View.VISIBLE);
        }
        if (mEmojiBottomBar != null) {
            mEmojiBottomBar.setVisibility(mEmojiSearchFocused ? View.GONE : View.VISIBLE);
        }
        enforceEmojiKeyboardVisibility();

        if (mEmojiSearchMode) {
            List<String> matches = findEmojiSearchResults(normalizedQuery);
            List<Mapping> emojiCandidates = emojiSearchCandidateMappings(matches);
            showEmojiSearchCandidatesInInputStrip(emojiCandidates);
            updateEmojiCategoryHighlight(-1);
        } else {
            clearEmojiSearchCandidates();
            int pageWidth = getEmojiPageWidth();
            List<List<String>> pages = getEmojiPanelPages();
            mEmojiCategoryStartOffsets = new int[getEmojiCategoryCount()];
            int nextOffset = 0;
            for (int i = 0; i < pages.size(); i++) {
                if (i < mEmojiCategoryStartOffsets.length) {
                    mEmojiCategoryStartOffsets[i] = nextOffset;
                }
                nextOffset += addEmojiSection(pages.get(i).toArray(new String[0]), pageWidth, i);
            }
            updateEmojiCategoryHighlight(-1);
            if (mEmojiScroll != null) {
                final int offset = getEmojiCategoryStartOffset(mEmojiCategoryIndex);
                mEmojiScroll.post(() -> mEmojiScroll.scrollTo(offset, 0));
            }
        }
    }

    View getEmojiKeyboardViewForTesting() {
        return mEmojiKeyboardView;
    }

    boolean isEmojiContentRenderedForTesting() {
        return mEmojiContentRendered;
    }

    int getEmojiPageViewCountForTesting() {
        return mEmojiPages == null ? 0 : mEmojiPages.getChildCount();
    }

    int getEmojiCategoryTabCountForTesting() {
        return mEmojiCategoryBar == null ? 0 : mEmojiCategoryBar.getChildCount();
    }

    private void enterEmojiSearchMode() {
        mEmojiSearchFocused = true;
        mEmojiSearchQuery.setLength(0);
        setEmojiSearchKeyboard(emojiSearchInitialEnglishOnly(mEmojiSourceWasEnglish));
        updateEmojiSearchText();
        enforceEmojiKeyboardVisibility();
        refreshCandidateInputContainer();
    }

    private void exitEmojiSearchToKeyboard() {
        mEmojiSearchFocused = false;
        mEmojiSearchMode = false;
        mEmojiSearchQuery.setLength(0);
        if (mEmojiSearchField != null) {
            mEmojiSearchField.setText("");
        }
        hideEmojiKeyboard();
    }

    private void showEmojiSearchCandidatesInInputStrip(List<Mapping> emojiCandidates) {
        LinkedList<Mapping> candidates = new LinkedList<>();
        if (emojiCandidates != null) {
            candidates.addAll(emojiCandidates);
        }
        mCandidateList = candidates;
        selectedCandidate = null;
        hasCandidatesShown = !candidates.isEmpty();
        hasMappingList = !candidates.isEmpty();
        setInputCandidateStripVisibility(View.VISIBLE);
        if (mCandidateInInputView != null) {
            mCandidateInInputView.setVisibility(View.VISIBLE);
        }
        if (mCandidateViewInInputView != null) {
            mCandidateViewInInputView.setVisibility(View.VISIBLE);
        }
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(candidates, false);
        }
        showCandidateView();
        if (mCandidateInInputView != null) {
            mCandidateInInputView.requestLayout();
            mCandidateInInputView.updateCandidateViewWidthConstraint();
            mCandidateInInputView.post(() -> {
                if (!mEmojiKeyboardShown || !mEmojiSearchMode) return;
                setInputCandidateStripVisibility(View.VISIBLE);
                mCandidateInInputView.setVisibility(View.VISIBLE);
                if (mCandidateViewInInputView != null) {
                    mCandidateViewInInputView.setVisibility(View.VISIBLE);
                }
                mCandidateInInputView.requestLayout();
                mCandidateInInputView.updateCandidateViewWidthConstraint();
            });
        }
    }

    private void clearEmojiSearchCandidates() {
        if (mCandidateView != null) {
            mCandidateView.hideCandidatePopup();
            mCandidateView.setSuggestions(null, false);
        }
        if (mCandidateList != null) {
            mCandidateList.clear();
        }
        selectedCandidate = null;
        hasCandidatesShown = false;
        hasMappingList = false;
        if (mCandidateInInputView != null) {
            mCandidateInInputView.requestLayout();
            mCandidateInInputView.updateCandidateViewWidthConstraint();
        }
    }

    private void setEmojiSearchKeyboard(boolean englishOnly) {
        mEnglishOnly = englishOnly;
        if (mKeyboardSwitcher != null) {
            mKeyboardSwitcher.setKeyboardMode(activeIM,
                    englishOnly ? LIMEKeyboardSwitcher.MODE_TEXT : LIMEKeyboardSwitcher.MODE_IM,
                    emojiSearchImeOptions(mImeOptions), !englishOnly, false, false);
        }
        if (mInputView != null) {
            mInputView.invalidateAllKeys();
        }
    }

    private boolean handleEmojiSearchKey(int primaryCode) {
        if (primaryCode == LIMEBaseKeyboard.KEYCODE_DELETE) {
            handleEmojiBackspace();
            return true;
        }
        if (shouldExitEmojiSearchToKeyboard(primaryCode)) {
            exitEmojiSearchToKeyboard();
            return true;
        }
        if (isEmojiSearchKeyboardModeKey(primaryCode)) {
            setEmojiSearchKeyboard(resolveEmojiSearchEnglishOnlyForModeKey(primaryCode, mEnglishOnly));
            return true;
        }
        if (shouldEmojiSearchConsumePrintableKey(primaryCode, mEnglishOnly)) {
            mEmojiSearchQuery.append((char) Character.toLowerCase(primaryCode));
            updateEmojiSearchText();
            return true;
        }
        return mEnglishOnly;
    }

    private boolean appendPickedCandidateToEmojiSearch(Mapping candidate) {
        if (candidate == null || candidate.getWord() == null || candidate.getWord().isEmpty()) {
            return false;
        }
        if (!shouldAppendPickedCandidateToEmojiSearch(mEmojiKeyboardShown, mEmojiSearchMode,
                candidate.isEmojiRecord(), candidate.isComposingCodeRecord())) {
            return false;
        }
        mEmojiSearchQuery.append(candidate.getWord());
        selectedCandidate = null;
        clearComposing(false);
        updateEmojiSearchText();
        return true;
    }

    private void handleEmojiBackspace() {
        if (mEmojiSearchFocused && mEmojiSearchQuery.length() > 0) {
            mEmojiSearchQuery.deleteCharAt(mEmojiSearchQuery.length() - 1);
            updateEmojiSearchText();
        } else {
            handleBackspace();
        }
    }

    private void updateEmojiSearchText() {
        if (mEmojiSearchField != null) {
            EmojiPanelColors colors = currentEmojiPanelColors();
            if (mEmojiSearchQuery.length() == 0 && !mEmojiSearchFocused) {
                mEmojiSearchField.setText("搜尋表情符號");
                mEmojiSearchField.setTextColor(colors.searchHint);
            } else {
                mEmojiSearchField.setText(mEmojiSearchQuery.toString());
                mEmojiSearchField.setTextColor(colors.searchText);
            }
        }
        renderEmojiContent(mEmojiSearchQuery.toString());
    }

    private void applyEmojiSearchFieldStyle() {
        if (mEmojiSearchField == null) return;
        EmojiPanelColors colors = currentEmojiPanelColors();
        Drawable searchIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_search);
        if (searchIcon != null) {
            searchIcon = DrawableCompat.wrap(searchIcon.mutate());
            DrawableCompat.setTint(searchIcon, colors.searchIcon);
        }
        mEmojiSearchField.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null);
        mEmojiSearchField.setBackground(makeRoundRect(colors.searchBackground, dp(26)));
    }

    private int addEmojiSection(String[] emojis, int pageWidth, int categoryIndex) {
        GridLayout page = new GridLayout(mThemeContext);
        float emojiKeyboardSizeScale = getEmojiKeyboardSizeScale();
        int keySize = Math.max(dp(scaleDp(42, emojiKeyboardSizeScale)), pageWidth / EMOJI_GRID_COLUMNS);
        int emojiGlyphSize = emojiPanelGlyphSize(emojiKeyboardSizeScale);
        int emojiCellHeight = dp(scaleDp(50, emojiKeyboardSizeScale));
        int realCount = emojis == null ? 0 : emojis.length;
        int columns = Math.max(1, (int) Math.ceil((double) realCount / (double) EMOJI_GRID_ROWS));
        if (categoryIndex == 0) {
            columns = Math.max(EMOJI_GRID_COLUMNS, columns);
        }
        int visibleCellCount = Math.max(realCount, columns * EMOJI_GRID_ROWS);
        page.setColumnCount(columns);
        page.setRowCount(EMOJI_GRID_ROWS);
        page.setPadding(0, 0, 0, 0);
        for (int i = 0; i < visibleCellCount; i++) {
            boolean isRealEmoji = i < realCount;
            TextView key = createEmojiControl(isRealEmoji ? emojis[i] : "•", emojiGlyphSize);
            if (isRealEmoji) {
                key.setOnClickListener(v -> commitEmoji(((TextView) v).getText().toString()));
            } else {
                key.setTextColor(Color.TRANSPARENT);
                key.setAlpha(0.01f);
                key.setOnClickListener(null);
            }
            int column = i / EMOJI_GRID_ROWS;
            int row = i % EMOJI_GRID_ROWS;
            GridLayout.LayoutParams keyParams = new GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(column));
            keyParams.width = keySize;
            keyParams.height = emojiCellHeight;
            keyParams.setMargins(0, dp(1), 0, dp(1));
            page.addView(key, keyParams);
        }
        int contentWidth = categoryIndex == 0 ? Math.max(pageWidth, keySize * columns) : keySize * columns;
        mEmojiPages.addView(page, new LinearLayout.LayoutParams(contentWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
        return contentWidth;
    }

    private void updateEmojiCategoryHighlight(int categoryIndex) {
        if (mEmojiCategoryBar == null) return;
        if (categoryIndex >= 0) {
            mEmojiCategoryIndex = Math.max(0, Math.min(categoryIndex, getEmojiCategoryCount() - 1));
        }

        if (mEmojiCategoryBar.getChildCount() != getEmojiCategoryCount()) {
            mEmojiCategoryBar.removeAllViews();
            float emojiKeyboardSizeScale = getEmojiKeyboardSizeScale();
            int tabWidth = dp(emojiCategoryTabWidthDp(emojiKeyboardSizeScale));
            int tabHeight = dp(scaleDp(EMOJI_CATEGORY_TAB_HEIGHT_DP, emojiKeyboardSizeScale));
            for (int i = 0; i < getEmojiCategoryCount(); i++) {
                final int index = i;
                View tab = createEmojiCategoryIcon(index);
                tab.setOnClickListener(v -> {
                    if (mEmojiSearchField != null && mEmojiSearchField.length() > 0) {
                        mEmojiSearchField.setText("");
                    }
                    mEmojiSearchMode = false;
                    mEmojiCategoryIndex = index;
                    renderEmojiContent("");
                    if (mEmojiScroll != null) {
                        mEmojiScroll.post(() -> mEmojiScroll.smoothScrollTo(
                                getEmojiCategoryStartOffset(index), 0));
                    }
                    updateEmojiCategoryHighlight(-1);
                });
                mEmojiCategoryBar.addView(tab, new LinearLayout.LayoutParams(tabWidth, tabHeight));
            }
        }
        for (int i = 0; i < mEmojiCategoryBar.getChildCount(); i++) {
            View tab = mEmojiCategoryBar.getChildAt(i);
            tab.setBackground(makeRoundRect(
                    !mEmojiSearchMode && i == mEmojiCategoryIndex
                            ? currentEmojiPanelColors().categoryHighlight
                            : Color.TRANSPARENT, dp(18)));
            tab.invalidate();
        }
    }

    private int getEmojiCategoryCount() {
        return FALLBACK_EMOJI_CATEGORIES.length;
    }

    static int emojiCategoryTabWidthDp(float keyboardSizeScale) {
        return scaleDp(EMOJI_CATEGORY_TAB_WIDTH_DP, keyboardSizeScale);
    }

    static int emojiPanelGlyphSize(float keyboardSizeScale) {
        float clampedScale = Math.max(0.8f, Math.min(1.2f, keyboardSizeScale));
        float glyphScale = 1.0f + ((clampedScale - 1.0f) * 0.5f);
        return Math.round(EMOJI_PANEL_GLYPH_SIZE * glyphScale);
    }

    static int emojiCategoryGlyphSizeDp(float keyboardSizeScale) {
        return emojiPanelGlyphSize(keyboardSizeScale);
    }

    static int emojiSideControlWidthDp(float keyboardSizeScale) {
        return emojiCategoryTabWidthDp(keyboardSizeScale);
    }

    static int emojiModeControlGlyphSize(float keyboardSizeScale) {
        return Math.round(emojiCategoryGlyphSizeDp(keyboardSizeScale) * 0.8f);
    }

    static int emojiBackspaceGlyphSize(float keyboardSizeScale) {
        return emojiCategoryGlyphSizeDp(keyboardSizeScale);
    }

    private float getEmojiKeyboardSizeScale() {
        float scale = 1.0f;
        if (mLIMEPref != null) {
            scale = mLIMEPref.getKeyboardSize();
        }
        return Math.max(0.8f, Math.min(1.2f, scale));
    }

    private static int scaleDp(int dpValue, float keyboardSizeScale) {
        return Math.round(dpValue * Math.max(0.8f, Math.min(1.2f, keyboardSizeScale)));
    }

    private int getEmojiCategoryStartPage(int categoryIndex) {
        if (mEmojiCategoryPageStarts == null || mEmojiCategoryPageStarts.length == 0) {
            return Math.max(0, categoryIndex);
        }
        int safeIndex = Math.max(0, Math.min(categoryIndex, mEmojiCategoryPageStarts.length - 1));
        return mEmojiCategoryPageStarts[safeIndex];
    }

    private int getEmojiCategoryStartOffset(int categoryIndex) {
        if (mEmojiCategoryStartOffsets == null || mEmojiCategoryStartOffsets.length == 0) {
            return getEmojiCategoryStartPage(categoryIndex) * getEmojiPageWidth();
        }
        int safeIndex = Math.max(0, Math.min(categoryIndex, mEmojiCategoryStartOffsets.length - 1));
        return mEmojiCategoryStartOffsets[safeIndex];
    }

    private int categoryIndexForEmojiScroll(int scrollX) {
        if (mEmojiCategoryStartOffsets == null || mEmojiCategoryStartOffsets.length == 0) {
            int pageWidth = getEmojiPageWidth();
            return pageWidth > 0 ? Math.round((float) scrollX / (float) pageWidth) : 0;
        }
        int active = 0;
        for (int i = 0; i < mEmojiCategoryStartOffsets.length; i++) {
            if (mEmojiCategoryStartOffsets[i] <= scrollX + 1) {
                active = i;
            } else {
                break;
            }
        }
        return active;
    }

    private View createEmojiCategoryIcon(int categoryIndex) {
        EmojiCategoryIconButton view = new EmojiCategoryIconButton(
                mThemeContext, categoryIndex, dp(emojiCategoryGlyphSizeDp(getEmojiKeyboardSizeScale())));
        view.setClickable(true);
        return view;
    }

    private int getEmojiPageWidth() {
        int viewWidth = mEmojiKeyboardView == null ? 0 : mEmojiKeyboardView.getWidth();
        int fallbackWidth = getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(320), (viewWidth > 0 ? viewWidth : fallbackWidth) - dp(24));
    }

    private List<String> findEmojiSearchResults(String query) {
        if (SearchSrv != null && query != null && query.length() > 0) {
            List<Mapping> english = SearchSrv.searchEmoji(query, LimeDB.EmojiLocale.EN, 80);
            List<Mapping> traditional = SearchSrv.searchEmoji(query, LimeDB.EmojiLocale.TW, 80);
            List<String> dbMatches = new ArrayList<>();
            if (english != null) {
                for (Mapping mapping : english) {
                    if (mapping != null && mapping.getWord() != null && !dbMatches.contains(mapping.getWord())) {
                        dbMatches.add(mapping.getWord());
                    }
                }
            }
            if (traditional != null) {
                for (Mapping mapping : traditional) {
                    if (mapping != null && mapping.getWord() != null && !dbMatches.contains(mapping.getWord())) {
                        dbMatches.add(mapping.getWord());
                    }
                }
            }
            if (!dbMatches.isEmpty()) {
                return dbMatches;
            }
        }

        List<String> matches = new ArrayList<>();
        for (String[] category : FALLBACK_EMOJI_CATEGORIES) {
            for (String emoji : category) {
                if (emoji.contains(query) || emojiKeywordMatches(emoji, query)) {
                    if (!matches.contains(emoji)) {
                        matches.add(emoji);
                    }
                }
            }
        }
        return matches;
    }

    private List<Mapping> emojiSearchCandidateMappings(List<String> emojis) {
        List<Mapping> candidates = new LinkedList<>();
        if (emojis == null) return candidates;
        for (String emoji : emojis) {
            if (emoji == null || emoji.isEmpty()) continue;
            Mapping mapping = new Mapping();
            mapping.setCode("");
            mapping.setWord(emoji);
            mapping.setEmojiRecord();
            candidates.add(mapping);
        }
        return candidates;
    }

    private int emojiSearchPanelHeight() {
        return dp(EMOJI_PANEL_VERTICAL_PADDING_DP)
                + dp(EMOJI_SEARCH_FIELD_HEIGHT_DP);
    }

    private List<List<String>> getEmojiPanelPages() {
        if (mEmojiCategoryPages == null) {
            List<List<String>> categories = loadEmojiCategories();
            mEmojiCategoryPages = paginateEmojiCategories(categories);
        }
        return mEmojiCategoryPages;
    }

    private List<List<String>> loadEmojiCategories() {
        List<List<String>> categories = null;
        if (SearchSrv != null) {
            try {
                categories = SearchSrv.loadEmojiCategoryPages();
            } catch (Exception e) {
                Log.e(TAG, "Error loading DB-backed emoji categories", e);
            }
        }
        if (categories == null || categories.size() < getEmojiCategoryCount()) {
            categories = new ArrayList<>();
        } else {
            categories = copyEmojiStringPages(categories);
        }

        for (int i = 0; i < getEmojiCategoryCount(); i++) {
            List<String> fallback = emojiArrayToList(FALLBACK_EMOJI_CATEGORIES[i]);
            if (i >= categories.size()) {
                categories.add(fallback);
            } else if (i == 0) {
                categories.set(i, mergeEmojiRecentSeedQueue(categories.get(i), fallback, EMOJI_PAGE_CAPACITY));
            } else if (categories.get(i) == null || categories.get(i).isEmpty()) {
                categories.set(i, fallback);
            }
        }
        while (categories.size() > getEmojiCategoryCount()) {
            categories.remove(categories.size() - 1);
        }
        return categories;
    }

    private List<String> mergeEmojiRecentSeedQueue(List<String> recent, List<String> fallback, int limit) {
        int safeLimit = Math.max(1, limit);
        List<String> merged = new ArrayList<>();
        if (recent != null) {
            for (String emoji : recent) {
                addEmojiSeedIfRoom(merged, emoji, safeLimit);
            }
        }
        if (fallback != null) {
            for (String emoji : fallback) {
                addEmojiSeedIfRoom(merged, emoji, safeLimit);
            }
        }
        return merged;
    }

    private void addEmojiSeedIfRoom(List<String> merged, String emoji, int limit) {
        if (merged == null || emoji == null || emoji.isEmpty() || merged.contains(emoji)) {
            return;
        }
        if (merged.size() < Math.max(1, limit)) {
            merged.add(emoji);
        }
    }

    private List<List<String>> paginateEmojiCategories(List<List<String>> categories) {
        List<List<String>> pages = new ArrayList<>();
        mEmojiPageCategoryIndexes = new ArrayList<>();
        mEmojiCategoryPageStarts = new int[getEmojiCategoryCount()];

        for (int categoryIndex = 0; categoryIndex < getEmojiCategoryCount(); categoryIndex++) {
            mEmojiCategoryPageStarts[categoryIndex] = pages.size();
            List<String> items = categoryIndex < categories.size() ? categories.get(categoryIndex) : null;
            if (items == null || items.isEmpty()) {
                items = emojiArrayToList(FALLBACK_EMOJI_CATEGORIES[categoryIndex]);
            }
            pages.add(new ArrayList<>(items));
            mEmojiPageCategoryIndexes.add(categoryIndex);
        }
        return pages;
    }

    private List<List<String>> copyEmojiStringPages(List<List<String>> source) {
        List<List<String>> copy = new ArrayList<>();
        for (List<String> page : source) {
            copy.add(page == null ? new ArrayList<>() : new ArrayList<>(page));
        }
        return copy;
    }

    private List<String> emojiArrayToList(String[] source) {
        List<String> values = new ArrayList<>();
        if (source == null) {
            return values;
        }
        for (String value : source) {
            if (value != null && !value.isEmpty() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private boolean emojiKeywordMatches(String emoji, String query) {
        if (query.length() == 0) return true;
        if ("😂🤣😆😅😭😢".contains(emoji)) return startsWithAny(query, "cry", "cr", "laugh", "lol", "tear");
        if ("❤️🧡💛💚💙💜🖤🤍🤎💕💞💓💗💖😍🥰😘".contains(emoji)) return startsWithAny(query, "heart", "love", "lov", "kiss");
        if ("🐶🐱🐭🐹🐰🦊🐻🐼🐨🐯🦁🐮🐷🐸🐵".contains(emoji)) return startsWithAny(query, "animal", "dog", "cat", "bear", "monkey");
        if ("🍎🍐🍊🍋🍌🍉🍇🍓🍔🍟".contains(emoji)) return startsWithAny(query, "food", "fruit", "apple", "burger");
        if ("🚗🚕🚙🚌🚎✈🚀🚁🚢🚉🚇🚆".contains(emoji)) return startsWithAny(query, "car", "travel", "train", "plane");
        if ("⚽🏀🏈⚾🎾🏐".contains(emoji)) return startsWithAny(query, "sport", "ball", "soccer");
        if ("🇹🇼🇯🇵🇰🇷🇺🇸🇨🇦🇬🇧🇫🇷🇩🇪🇮🇹🇪🇸".contains(emoji)) return startsWithAny(query, "flag", "country");
        return false;
    }

    private boolean startsWithAny(String query, String... keywords) {
        for (String keyword : keywords) {
            if (keyword.startsWith(query) || query.startsWith(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void commitEmoji(String emoji) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(emoji, 1);
        }
        if (SearchSrv != null) {
            SearchSrv.recordEmojiUsage(emoji);
        }
        mEmojiCategoryPages = null;
    }

    private TextView createEmojiControl(String text, int textSize) {
        TextView view = new TextView(mThemeContext);
        view.setText(text);
        view.setTextSize(textSize);
        view.setTextColor(currentEmojiPanelColors().iconText);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setClickable(true);
        return view;
    }

    private class EmojiCategoryIconButton extends View {
        private final int categoryIndex;
        private final int iconSizePx;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        EmojiCategoryIconButton(Context context, int categoryIndex, int iconSizePx) {
            super(context);
            this.categoryIndex = categoryIndex;
            this.iconSizePx = iconSizePx;
            paint.setColor(currentEmojiPanelColors().iconText);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setWillNotDraw(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(iconSizePx, Math.min(getWidth(), getHeight()));
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setStrokeWidth(Math.max(2.2f, dp(2)));
            paint.setStyle(Paint.Style.STROKE);

            switch (categoryIndex) {
                case 0:
                    drawRecentIcon(canvas, cx, cy, size);
                    break;
                case 1:
                    drawSmileIcon(canvas, cx, cy, size);
                    break;
                case 2:
                    drawPeopleIcon(canvas, cx, cy, size);
                    break;
                case 3:
                    drawAnimalIcon(canvas, cx, cy, size);
                    break;
                case 4:
                    drawAppleIcon(canvas, cx, cy, size);
                    break;
                case 5:
                    drawCarIcon(canvas, cx, cy, size);
                    break;
                case 6:
                    drawBallIcon(canvas, cx, cy, size);
                    break;
                case 7:
                    drawBulbIcon(canvas, cx, cy, size);
                    break;
                case 8:
                    drawHeartIcon(canvas, cx, cy, size);
                    break;
                case 9:
                    drawFlagIcon(canvas, cx, cy, size);
                    break;
                default:
                    break;
            }
        }

        private void drawRecentIcon(Canvas canvas, float cx, float cy, float size) {
            float r = size * 0.48f;
            canvas.drawCircle(cx, cy, r, paint);
            canvas.drawLine(cx, cy, cx, cy - r * 0.62f, paint);
            canvas.drawLine(cx, cy, cx - r * 0.54f, cy, paint);
        }

        private void drawSmileIcon(Canvas canvas, float cx, float cy, float size) {
            float r = size * 0.45f;
            canvas.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx - r * 0.34f, cy - r * 0.16f, dp(1.5f), paint);
            canvas.drawCircle(cx + r * 0.34f, cy - r * 0.16f, dp(1.5f), paint);
            paint.setStyle(Paint.Style.STROKE);
            RectF smile = new RectF(cx - r * 0.42f, cy - r * 0.02f, cx + r * 0.42f, cy + r * 0.52f);
            canvas.drawArc(smile, 18, 144, false, paint);
        }

        private void drawPeopleIcon(Canvas canvas, float cx, float cy, float size) {
            float headR = size * 0.18f;
            canvas.drawCircle(cx, cy - size * 0.22f, headR, paint);
            canvas.drawArc(new RectF(cx - size * 0.34f, cy - size * 0.02f,
                    cx + size * 0.34f, cy + size * 0.62f), 205, 130, false, paint);
            canvas.drawCircle(cx + size * 0.28f, cy - size * 0.06f, headR * 0.74f, paint);
            canvas.drawArc(new RectF(cx + size * 0.08f, cy + size * 0.12f,
                    cx + size * 0.5f, cy + size * 0.58f), 210, 120, false, paint);
        }

        private void drawAnimalIcon(Canvas canvas, float cx, float cy, float size) {
            float r = size * 0.31f;
            canvas.drawCircle(cx - r * 0.85f, cy - r * 0.95f, r * 0.45f, paint);
            canvas.drawCircle(cx + r * 0.85f, cy - r * 0.95f, r * 0.45f, paint);
            canvas.drawCircle(cx, cy - r * 0.2f, r, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx - r * 0.34f, cy - r * 0.32f, dp(1.35f), paint);
            canvas.drawCircle(cx + r * 0.34f, cy - r * 0.32f, dp(1.35f), paint);
            canvas.drawOval(new RectF(cx - r * 0.22f, cy - r * 0.02f, cx + r * 0.22f, cy + r * 0.24f), paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawArc(new RectF(cx - r * 0.5f, cy + r * 0.04f, cx, cy + r * 0.54f), 0, 70, false, paint);
            canvas.drawArc(new RectF(cx, cy + r * 0.04f, cx + r * 0.5f, cy + r * 0.54f), 110, 70, false, paint);
        }

        private void drawAppleIcon(Canvas canvas, float cx, float cy, float size) {
            float r = size * 0.37f;
            Path apple = new Path();
            apple.moveTo(cx, cy - r * 0.72f);
            apple.cubicTo(cx - r * 0.95f, cy - r * 0.98f, cx - r * 1.15f, cy + r * 0.1f, cx - r * 0.62f, cy + r * 0.82f);
            apple.cubicTo(cx - r * 0.24f, cy + r * 1.25f, cx - r * 0.02f, cy + r * 0.92f, cx, cy + r * 0.92f);
            apple.cubicTo(cx + r * 0.02f, cy + r * 0.92f, cx + r * 0.24f, cy + r * 1.25f, cx + r * 0.62f, cy + r * 0.82f);
            apple.cubicTo(cx + r * 1.15f, cy + r * 0.1f, cx + r * 0.95f, cy - r * 0.98f, cx, cy - r * 0.72f);
            canvas.drawPath(apple, paint);
            canvas.drawLine(cx, cy - r * 0.8f, cx + r * 0.14f, cy - r * 1.22f, paint);
            canvas.drawArc(new RectF(cx + r * 0.1f, cy - r * 1.42f, cx + r * 0.8f, cy - r * 0.9f), 165, 150, false, paint);
        }

        private void drawBallIcon(Canvas canvas, float cx, float cy, float size) {
            float r = size * 0.45f;
            canvas.drawCircle(cx, cy, r, paint);
            canvas.drawLine(cx - r * 0.72f, cy - r * 0.28f, cx + r * 0.72f, cy + r * 0.28f, paint);
            canvas.drawLine(cx - r * 0.72f, cy + r * 0.28f, cx + r * 0.72f, cy - r * 0.28f, paint);
            canvas.drawArc(new RectF(cx - r * 0.72f, cy - r, cx + r * 0.72f, cy + r), 73, 214, false, paint);
        }

        private void drawCarIcon(Canvas canvas, float cx, float cy, float size) {
            float w = size * 0.95f;
            float h = size * 0.45f;
            Path car = new Path();
            car.moveTo(cx - w * 0.48f, cy + h * 0.1f);
            car.lineTo(cx - w * 0.34f, cy - h * 0.28f);
            car.lineTo(cx - w * 0.14f, cy - h * 0.45f);
            car.lineTo(cx + w * 0.28f, cy - h * 0.45f);
            car.lineTo(cx + w * 0.48f, cy - h * 0.04f);
            car.lineTo(cx + w * 0.48f, cy + h * 0.28f);
            car.lineTo(cx - w * 0.48f, cy + h * 0.28f);
            car.close();
            canvas.drawPath(car, paint);
            canvas.drawCircle(cx - w * 0.26f, cy + h * 0.34f, h * 0.2f, paint);
            canvas.drawCircle(cx + w * 0.28f, cy + h * 0.34f, h * 0.2f, paint);
        }

        private void drawBulbIcon(Canvas canvas, float cx, float cy, float size) {
            float r = size * 0.35f;
            canvas.drawArc(new RectF(cx - r, cy - r * 1.15f, cx + r, cy + r * 0.85f), 210, 120, false, paint);
            canvas.drawLine(cx - r * 0.46f, cy + r * 0.48f, cx - r * 0.28f, cy + r * 1.05f, paint);
            canvas.drawLine(cx + r * 0.46f, cy + r * 0.48f, cx + r * 0.28f, cy + r * 1.05f, paint);
            canvas.drawLine(cx - r * 0.34f, cy + r * 1.05f, cx + r * 0.34f, cy + r * 1.05f, paint);
            canvas.drawLine(cx - r * 0.24f, cy + r * 1.3f, cx + r * 0.24f, cy + r * 1.3f, paint);
        }

        private void drawHeartIcon(Canvas canvas, float cx, float cy, float size) {
            float s = size * 0.5f;
            Path heart = new Path();
            heart.moveTo(cx, cy + s * 0.72f);
            heart.cubicTo(cx - s * 1.1f, cy - s * 0.08f, cx - s * 0.98f, cy - s * 0.84f, cx - s * 0.42f, cy - s * 0.84f);
            heart.cubicTo(cx - s * 0.14f, cy - s * 0.84f, cx, cy - s * 0.6f, cx, cy - s * 0.42f);
            heart.cubicTo(cx, cy - s * 0.6f, cx + s * 0.14f, cy - s * 0.84f, cx + s * 0.42f, cy - s * 0.84f);
            heart.cubicTo(cx + s * 0.98f, cy - s * 0.84f, cx + s * 1.1f, cy - s * 0.08f, cx, cy + s * 0.72f);
            canvas.drawPath(heart, paint);
        }

        private void drawFlagIcon(Canvas canvas, float cx, float cy, float size) {
            float h = size * 0.9f;
            float left = cx - size * 0.36f;
            canvas.drawLine(left, cy - h * 0.5f, left, cy + h * 0.5f, paint);
            Path flag = new Path();
            flag.moveTo(left, cy - h * 0.48f);
            flag.cubicTo(cx - size * 0.02f, cy - h * 0.66f, cx + size * 0.22f, cy - h * 0.26f, cx + size * 0.48f, cy - h * 0.42f);
            flag.lineTo(cx + size * 0.48f, cy + h * 0.1f);
            flag.cubicTo(cx + size * 0.22f, cy + h * 0.26f, cx - size * 0.02f, cy - h * 0.14f, left, cy + h * 0.04f);
            flag.close();
            canvas.drawPath(flag, paint);
        }
    }

    private GradientDrawable makeRoundRect(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getInputCandidateStripVisibility() {
        View strip = inputCandidateStrip();
        if (strip != null) {
            return strip.getVisibility();
        }
        return View.VISIBLE;
    }

    private void setInputCandidateStripVisibility(int visibility) {
        View strip = inputCandidateStrip();
        if (strip != null) {
            strip.setVisibility(visibility);
        }
    }

    private View inputCandidateStrip() {
        if (mCandidateInInputView == null) return null;
        return mCandidateInInputView.findViewById(R.id.input_candidate_strip);
    }

    private void enforceEmojiKeyboardVisibility() {
        if (!mEmojiKeyboardShown || mInputView == null) return;
        setInputCandidateStripVisibility(
                emojiSearchInputCandidateStripVisibility(mEmojiKeyboardShown, mEmojiSearchMode));
        if (mEmojiSearchFocused) {
            setEmojiSearchKeyboard(mEnglishOnly);
            mInputView.setVisibility(View.VISIBLE);
        } else {
            mInputView.setVisibility(View.GONE);
        }
        mInputView.invalidateAllKeys();
        if (mInputView.getHandler() != null) {
            mInputView.post(() -> {
                if (!mEmojiKeyboardShown) return;
                setInputCandidateStripVisibility(
                        emojiSearchInputCandidateStripVisibility(mEmojiKeyboardShown, mEmojiSearchMode));
                mInputView.setVisibility(mEmojiSearchFocused ? View.VISIBLE : View.GONE);
                mInputView.invalidateAllKeys();
            });
        }
    }

    private void refreshCandidateInputContainer() {
        if (mCandidateInInputView == null) return;
        mCandidateInInputView.post(() -> {
            mCandidateInInputView.requestLayout();
            mCandidateInInputView.updateCandidateViewWidthConstraint();
            mCandidateInInputView.invalidate();
        });
    }


    private AlertDialog mOptionsDialog;
    // Contextual menu actions
    private static final int ACTION_SETTINGS = 0;
    private static final int ACTION_REVERSE_LOOKUP = 1;
    private static final int ACTION_HANCONVERT = 2;  //Jeremy '11,9,17
    private static final int ACTION_KEYBOARD = 3;
    private static final int ACTION_METHOD = 4;
    private static final int ACTION_SPLIT_KEYBOARD = 5;
    private static final int ACTION_VOICEINPUT = 6;


    /**
     * Add by Jeremy '10, 3, 24 for options menu in soft keyboard
     */

    private void handleOptions() {
        if (DEBUG)
            Log.i(TAG, "handleOptions()");

        // Check if Looper is available (not in test environment)
        if (Looper.myLooper() == null) {
            Log.w(TAG, "handleOptions(): No Looper available, skipping dialog creation");
            return;
        }

        AlertDialog.Builder builder;

        builder = createDialogBuilder();


        builder.setCancelable(true);
        builder.setIcon(R.drawable.logo);
        // Geometry controls apply immediately; 完成 simply dismisses the panel.
        // Han conversion remains deferred until dismissal.
        builder.setPositiveButton(R.string.keyboard_menu_done, null);
        builder.setTitle(getResources().getString(R.string.ime_name));

        List<LIMEPreferenceManager.ReverseLookupOption> reverseLookupOptions = getActiveReverseLookupOptions();
        CharSequence reverseLookupValue = getReverseLookupLabel(
                mLIMEPref.getReverseLookupTable(activeIM), reverseLookupOptions);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int displayWidth = dm.widthPixels;
        int displayHeight = dm.heightPixels;
        final boolean isLandScape = displayWidth > displayHeight;

        // SPLIT_ONE_HAND_KB exclusivity: numpad layouts show 數字鍵盤位置 only;
        // ordinary layouts show 分離鍵盤 / 單手鍵盤. (spec: keyboard-menu rule)
        final boolean isNumpadKb = mKeyboardSwitcher.isNumpadKeyboard();
        final boolean isTablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        // Issue #169: tablets keep the tri-state 分離鍵盤 control (ordinary layouts) and
        // 數字鍵盤位置 (numpad layouts). Phones use the integrated 直向鍵盤模式 in portrait
        // and the binary 橫向分離鍵盤 in landscape — shown on EVERY phone regardless of
        // screen width (no gate). Numpad layouts never split, so on a phone the portrait
        // control hides its 分離 segment and the landscape control is not shown.
        final boolean phoneControls =
                org.limeime.keyboard.PhoneKeyboardModePolicy.phoneControlsApply(isTablet);
        final boolean hasTabletSplitOption = isTablet && !isNumpadKb;
        final boolean hasPhonePortraitMode = phoneControls && !isLandScape;
        final boolean hasPhoneLandscapeSplit = phoneControls && isLandScape && !isNumpadKb;
        final boolean hasNumpadAnchorOption = isTablet && isNumpadKb;

        // Custom panel: every entry is a styled row and 簡繁轉換 hosts its segmented
        // control inline at the top level (no drill-down), matching the 喜好設定 page.
        // The Material segmented buttons need a MaterialComponents-themed inflater.
        android.content.Context matCtx = new ContextThemeWrapper(this, R.style.LIMESettingsTheme);
        matCtx = com.google.android.material.color.DynamicColors.wrapContextIfAvailable(
                matCtx, org.limeime.global.SystemAccentColor.dynamicColorOptions(this));
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(matCtx);
        android.view.View panel = inflater.inflate(R.layout.keyboard_menu_panel, null);
        LinearLayout topRows = panel.findViewById(R.id.menu_rows_top);
        LinearLayout bottomRows = panel.findViewById(R.id.menu_rows_bottom);

        builder.setView(panel);
        mOptionsDialog = builder.create();

        // Rows above 簡繁轉換.
        addKeyboardMenuRow(inflater, topRows, R.drawable.ic_nav_settings,
                getString(R.string.lime_setting_preference), null,
                () -> handleKeyboardMenuAction(ACTION_SETTINGS, isLandScape));
        addKeyboardMenuRow(inflater, topRows, R.drawable.ic_search_24,
                getString(R.string.im_reverse_lookup_screen_title), reverseLookupValue,
                () -> handleKeyboardMenuAction(ACTION_REVERSE_LOOKUP, isLandScape));

        // Rows below 簡繁轉換. (分離鍵盤 is the inline segmented block, not a row.)
        addKeyboardMenuRow(inflater, bottomRows, R.drawable.ic_nav_im,
                getString(R.string.keyboard_list), null,
                () -> handleKeyboardMenuAction(ACTION_KEYBOARD, isLandScape));
        addKeyboardMenuRow(inflater, bottomRows, R.drawable.ic_language_24,
                getString(R.string.input_method), null,
                () -> handleKeyboardMenuAction(ACTION_METHOD, isLandScape));
        addKeyboardMenuRow(inflater, bottomRows, R.drawable.ic_mic_24,
                getString(R.string.voice_input), null,
                () -> handleKeyboardMenuAction(ACTION_VOICEINPUT, isLandScape));

        // Han conversion is deferred until dismissal. Geometry controls below apply
        // immediately so their selected state always matches the keyboard behind them.
        final int hanCurrent = clampIndex(mLIMEPref.getHanCovertOption(), 3);
        final int[] pendingHan = { hanCurrent };
        com.google.android.material.button.MaterialButtonToggleGroup hanGroup =
                panel.findViewById(R.id.han_toggle_group);
        if (hanGroup != null) {
            final int[] hanIds = { R.id.han_opt_none, R.id.han_opt_t2s, R.id.han_opt_s2t };
            hanGroup.check(hanIds[hanCurrent]);
            hanGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                if (!isChecked) return;
                for (int i = 0; i < hanIds.length; i++) {
                    if (hanIds[i] == checkedId) { pendingHan[0] = i; break; }
                }
            });
            org.limeime.ui.view.SegmentedHanPreference.stackIfClipped(hanGroup);
        }

        // 分離鍵盤 inline segmented control (0=關閉 1=開啟 2=僅橫向), tablets only —
        // ordinary (non-numpad) layouts. Phones use 直向鍵盤模式 / 橫向分離鍵盤 below.
        final int splitCurrent = clampIndex(mSplitKeyboard, 3);
        final int[] pendingSplit = { splitCurrent };
        if (hasTabletSplitOption) {
            panel.findViewById(R.id.menu_split_block).setVisibility(android.view.View.VISIBLE);
            com.google.android.material.button.MaterialButtonToggleGroup splitGroup =
                    panel.findViewById(R.id.split_toggle_group);
            final int[] splitIds = { R.id.split_opt_off, R.id.split_opt_on, R.id.split_opt_landscape };
            splitGroup.check(splitIds[splitCurrent]);
            splitGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                if (!isChecked) return;
                for (int i = 0; i < splitIds.length; i++) {
                    if (splitIds[i] == checkedId && pendingSplit[0] != i) {
                        pendingSplit[0] = i;
                        mSplitKeyboard = i;
                        mLIMEPref.setSplitKeyboard(i);
                        invalidateStartupConfigSnapshot();
                        applyGeometryChangeInPlace();
                        break;
                    }
                }
            });
            org.limeime.ui.view.SegmentedHanPreference.stackIfClipped(splitGroup);
        }

        // 直向鍵盤模式 integrated phone-portrait control (0=標準 1=分離 2=靠左 3=靠右).
        // Issue #169: shown on every phone in portrait, no width gate. 分離 is hidden
        // for numpad layouts (numpads never split); if a stored 分離 meets a numpad
        // layout it is shown as 標準 without rewriting the stored value.
        final int portraitCurrent = clampIndex(mLIMEPref.getPhonePortraitKeyboardMode(), 4);
        final int[] pendingPortrait = { portraitCurrent };
        if (hasPhonePortraitMode) {
            panel.findViewById(R.id.menu_portrait_mode_block).setVisibility(android.view.View.VISIBLE);
            com.google.android.material.button.MaterialButtonToggleGroup portraitGroup =
                    panel.findViewById(R.id.portrait_mode_toggle_group);
            final int[] portraitIds = { R.id.portrait_opt_standard, R.id.portrait_opt_split,
                    R.id.portrait_opt_left, R.id.portrait_opt_right };
            if (isNumpadKb) {
                panel.findViewById(R.id.portrait_opt_split).setVisibility(android.view.View.GONE);
                portraitGroup.check(portraitIds[portraitCurrent
                        == org.limeime.keyboard.PhoneKeyboardModePolicy.PORTRAIT_SPLIT
                        ? org.limeime.keyboard.PhoneKeyboardModePolicy.PORTRAIT_STANDARD
                        : portraitCurrent]);
            } else {
                portraitGroup.check(portraitIds[portraitCurrent]);
            }
            portraitGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                if (!isChecked) return;
                for (int i = 0; i < portraitIds.length; i++) {
                    if (portraitIds[i] == checkedId && pendingPortrait[0] != i) {
                        pendingPortrait[0] = i;
                        mLIMEPref.setPhonePortraitKeyboardMode(i);
                        invalidateStartupConfigSnapshot();
                        applyGeometryChangeInPlace();
                        break;
                    }
                }
            });
            org.limeime.ui.view.SegmentedHanPreference.stackIfClipped(portraitGroup);
        }

        // 橫向分離鍵盤 binary phone-landscape control (關閉 / 開啟), independent of the
        // portrait mode. Issue #169: shown on every phone in landscape (ordinary
        // layouts only; numpads never split), no width gate.
        final boolean landscapeSplitCurrent = mLIMEPref.getPhoneLandscapeSplit();
        final boolean[] pendingLandscapeSplit = { landscapeSplitCurrent };
        if (hasPhoneLandscapeSplit) {
            panel.findViewById(R.id.menu_landscape_split_block).setVisibility(android.view.View.VISIBLE);
            com.google.android.material.button.MaterialButtonToggleGroup landscapeSplitGroup =
                    panel.findViewById(R.id.landscape_split_toggle_group);
            final int[] landscapeSplitIds = { R.id.landscape_split_opt_off, R.id.landscape_split_opt_on };
            landscapeSplitGroup.check(landscapeSplitIds[landscapeSplitCurrent ? 1 : 0]);
            landscapeSplitGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                if (!isChecked) return;
                boolean selected = checkedId == R.id.landscape_split_opt_on;
                if (pendingLandscapeSplit[0] != selected) {
                    pendingLandscapeSplit[0] = selected;
                    mLIMEPref.setPhoneLandscapeSplit(selected);
                    invalidateStartupConfigSnapshot();
                    applyGeometryChangeInPlace();
                }
            });
            org.limeime.ui.view.SegmentedHanPreference.stackIfClipped(landscapeSplitGroup);
        }

        // 數字鍵盤位置 inline segmented control (0=滿版 1=靠左 2=靠右 3=置中), shown only
        // for numpad-based layouts on tablets.
        final int anchorCurrent = clampIndex(mLIMEPref.getNumpadAnchor(), 4);
        final int[] pendingAnchor = { anchorCurrent };
        if (hasNumpadAnchorOption) {
            panel.findViewById(R.id.menu_numpad_anchor_block).setVisibility(android.view.View.VISIBLE);
            com.google.android.material.button.MaterialButtonToggleGroup anchorGroup =
                    panel.findViewById(R.id.numpad_anchor_toggle_group);
            final int[] anchorIds = { R.id.numpad_anchor_opt_fit, R.id.numpad_anchor_opt_left,
                    R.id.numpad_anchor_opt_right, R.id.numpad_anchor_opt_center };
            anchorGroup.check(anchorIds[anchorCurrent]);
            anchorGroup.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                if (!isChecked) return;
                for (int i = 0; i < anchorIds.length; i++) {
                    if (anchorIds[i] == checkedId && pendingAnchor[0] != i) {
                        pendingAnchor[0] = i;
                        mLIMEPref.setNumpadAnchor(i);
                        invalidateStartupConfigSnapshot();
                        applyGeometryChangeInPlace();
                        break;
                    }
                }
            });
            org.limeime.ui.view.SegmentedHanPreference.stackIfClipped(anchorGroup);
        }

        // Geometry choices apply immediately so the highlighted option and rendered
        // keyboard cannot disagree behind the dialog. Han conversion remains deferred.
        mOptionsDialog.setOnDismissListener(d -> {
            if (pendingHan[0] != hanCurrent) {
                handleHanConvertSelection(pendingHan[0]);
            }
        });

        Window window = mOptionsDialog.getWindow();
        assert window != null;
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mInputView.getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        // Cap the scrolling panel so it never exceeds the screen — at very large
        // system font scales the rows can be taller than one screen, in which case
        // the ScrollView (scrollbars always shown) must take over. The dialog title
        // and 完成 button need room, so cap the scroll region at ~62% of the screen.
        final int maxPanelHeight = (int) (displayHeight * 0.62f);
        panel.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                panel.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (panel.getHeight() > maxPanelHeight) {
                    android.view.ViewGroup.LayoutParams plp = panel.getLayoutParams();
                    plp.height = maxPanelHeight;
                    panel.setLayoutParams(plp);
                }
            }
        });

        mOptionsDialog.show();
    }

    /** Inflate one styled row into the long-press keyboard-menu panel. */
    private void addKeyboardMenuRow(android.view.LayoutInflater inflater, LinearLayout parent,
                                    int iconRes, CharSequence title, CharSequence value,
                                    Runnable onClick) {
        android.view.View row = inflater.inflate(R.layout.keyboard_menu_row, parent, false);
        ((ImageView) row.findViewById(R.id.menu_row_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.menu_row_title)).setText(title);
        TextView valueView = row.findViewById(R.id.menu_row_value);
        if (value != null && value.length() > 0) {
            valueView.setText(value);
            valueView.setVisibility(android.view.View.VISIBLE);
        }
        row.findViewById(R.id.menu_row_root).setOnClickListener(v -> {
            if (mOptionsDialog != null) mOptionsDialog.dismiss();
            onClick.run();
        });
        parent.addView(row);
    }

    /** Clamp a persisted index into [0, size); used by the inline segmented controls. */
    private static int clampIndex(int value, int size) {
        return (value < 0 || value >= size) ? 0 : value;
    }

    /** Dispatch a long-press keyboard-menu row action (dialog already dismissed). */
    private void handleKeyboardMenuAction(int action, boolean isLandScape) {
        switch (action) {
            case ACTION_SETTINGS:
                launchPreference();
                break;
            case ACTION_REVERSE_LOOKUP:
                showReverseLookupPicker();
                break;
            case ACTION_KEYBOARD:
                showIMPicker();
                break;
            case ACTION_METHOD:
                ((InputMethodManager) Objects.requireNonNull(getSystemService(INPUT_METHOD_SERVICE))).showInputMethodPicker();
                break;
            case ACTION_VOICEINPUT:
                startVoiceInput();
                break;
        }
    }

    private List<LIMEPreferenceManager.ReverseLookupOption> getActiveReverseLookupOptions() {
        buildActivatedIMList();
        return LIMEPreferenceManager.buildReverseLookupOptions(
                activatedIMList,
                activatedIMFullNameList,
                "無");
    }

    private String getReverseLookupLabel(String value, List<LIMEPreferenceManager.ReverseLookupOption> options) {
        String[] labels = LIMEPreferenceManager.reverseLookupLabels(options);
        String[] values = LIMEPreferenceManager.reverseLookupValues(options);
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i].equals(value)) {
                return labels[i];
            }
        }
        return labels.length > 0 ? labels[0] : "none";
    }

    private void showReverseLookupPicker() {
        List<LIMEPreferenceManager.ReverseLookupOption> options = getActiveReverseLookupOptions();
        String[] labels = LIMEPreferenceManager.reverseLookupLabels(options);
        String[] values = LIMEPreferenceManager.reverseLookupValues(options);
        String current = mLIMEPref.getReverseLookupTable(activeIM);
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected = i;
                break;
            }
        }

        AlertDialog.Builder builder = createDialogBuilder();
        builder.setCancelable(true);
        builder.setIcon(R.drawable.logo);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setTitle(getString(R.string.im_reverse_lookup_screen_title));
        builder.setSingleChoiceItems(labels, selected, (di, which) -> {
            di.dismiss();
            if (which >= 0 && which < values.length) {
                mLIMEPref.setReverseLookupTable(activeIM, values[which]);
                showLimeToast(getString(R.string.keyboard_menu_reverse_lookup, labels[which]));
            }
        });

        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        assert window != null;
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.token = mInputView.getWindowToken();
        lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        window.setAttributes(lp);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.show();
    }

    private void launchPreference() {
        handleClose();
        Intent intent = new Intent();
        /*if(android.os.Build.VERSION.SDK_INT < 11)  //Jeremy '12,4,30 Add for deprecated preferenceActivity after API 11 (HC)
            intent.setClass(LIMEService.this, LIMEPreference.class);
	    else*/
        intent.setClass(LIMEService.this, LIMEPreference.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    private void switchToNextActivatedIM(boolean forward) { // forward: true, next IM; false prev. IM
        if (DEBUG) Log.i(TAG, "switchToNextActivatedIM()");
        buildActivatedIMList();
        int i;
        CharSequence activeIMName = "";
        for (i = 0; i < activatedIMList.size(); i++) {
            if (activeIM.equals(activatedIMList.get(i))) {
                if (i == activatedIMList.size() - 1 && forward) {
                    activeIM = activatedIMList.get(0);
                    activeIMName = activatedIMFullNameList.get(0);
                } else if (i == 0 && !forward) {
                    activeIM = activatedIMList.get(activatedIMList.size() - 1);
                    activeIMName = activatedIMFullNameList.get(activatedIMList.size() - 1);
                } else {
                    activeIM = activatedIMList.get(i + ((forward) ? 1 : -1));
                    activeIMName = activatedIMFullNameList.get(i + ((forward) ? 1 : -1));
                }
                break;
            }
        }
        mLIMEPref.setActiveIM(activeIM);
        invalidateStartupConfigSnapshot();
        //Jeremy '12,4,21 force clear when switch to next keyboard
        clearComposing(false);
        // cancel candidate view if it's shown
        mEnglishOnly = false;
        mLIMEPref.setLanguageMode(false);
        //initialKeyboard();
        prepareImKeyboardConfigForChineseDraw(false);
        initialIMKeyboard();

        showLimeToast(activeIMName);

        // Update keyboard xml information
        if (mKeyboardSwitcher != null) {
            currentSoftKeyboard = mKeyboardSwitcher.getImConfigKeyboard(activeIM);
        }
    }

    private void buildActivatedIMList() {

        // Use LIME constants instead of resources for better testability
        String[] fullNames = LIME.IM_FULL_NAMES;
        String[] shortNames = LIME.IM_SHORT_NAMES;
        String[] IMs = LIME.IM_CODES;
        if (SearchSrv != null) {
            List<ImConfig> imConfigList = SearchSrv.getImConfigList(null, LIME.IM_FULL_NAME);
            activatedIMFullNameList.clear();
            activatedIMList.clear();
            activatedIMShortNameList.clear();

            StringBuilder activeState = new StringBuilder();
            for (ImConfig im : imConfigList) {
                if (im == null || im.getCode() == null) continue;
                if ("emoji".equals(im.getCode())) continue;
                if (im.isDisable()) continue;

                int index = indexOfIMCode(IMs, im.getCode());
                if (index < 0) continue;
                if (activeState.length() > 0) activeState.append(";");
                activeState.append(index);
                activatedIMFullNameList.add(im.getDesc());
                activatedIMShortNameList.add(shortNames[index]);
                activatedIMList.add(IMs[index]);
            }

            String liveState = activeState.toString();
            if (!liveState.equals(mIMActivatedState)) {
                mIMActivatedState = liveState;
                mLIMEPref.setIMActivatedState(liveState);
            }
            ensureActiveIMInActivatedList();
            return;
        }

        String pIMActiveState = mLIMEPref.getIMActivatedState();

        if (pIMActiveState.trim().isEmpty()) {

            activatedIMFullNameList.clear();
            activatedIMList.clear();
            activatedIMShortNameList.clear();
            return;
        }

        if (!(!mIMActivatedState.isEmpty() && mIMActivatedState.equals(pIMActiveState))) {

            mIMActivatedState = pIMActiveState;

            String[] activeState = pIMActiveState.split(";");

            activatedIMFullNameList.clear();
            activatedIMList.clear();
            activatedIMShortNameList.clear();

            for (String value : activeState) {
                if (value.isEmpty()) continue;
                int index = Integer.parseInt(value);

                if (index < fullNames.length) {
                    activatedIMFullNameList.add(fullNames[index]);
                    activatedIMShortNameList.add(shortNames[index]);
                    activatedIMList.add(IMs[index]);
                    if (DEBUG)
                        Log.i(TAG, "buildActivatedIMList()(): buildActivatedIMList()[" + index + "] = "
                                + IMs[index] + " ;" + shortNames[index]);
                } else {
                    break;
                }
            }
        }
        ensureActiveIMInActivatedList();

    }

    private int indexOfIMCode(String[] imCodes, String code) {
        for (int i = 0; i < imCodes.length; i++) {
            if (imCodes[i].equals(code)) {
                return i;
            }
        }
        return -1;
    }

    private void ensureActiveIMInActivatedList() {
        if (DEBUG) Log.i(TAG, "current active IM:" + activeIM);
        boolean matched = false;
        for (int i = 0; i < activatedIMList.size(); i++) {
            if (activeIM.equals(activatedIMList.get(i))) {
                if (DEBUG)
                    Log.i(TAG, "buildActivatedIMList(): activatedIM[" + i + "] matches current active IM: " + activeIM);
                matched = true;
                break;
            }
        }
        if (!matched && SearchSrv != null && !activatedIMList.isEmpty()) {
            // if the selected keyboard is not in the active keyboard list.
            // set the keyboard to the first active keyboard
            try {
                String corrected = activatedIMList.get(0);
                if (!corrected.equals(activeIM)) {
                    activeIM = corrected;
                    // Persist the correction so the next onStartInput() (which reloads
                    // activeIM from preferences) does not revert to a stale/default IM
                    // that has no loaded keyboard config and falls back to English.
                    // This is what makes the first installed IM activate on a fresh
                    // install instead of showing the English keyboard.
                    if (mLIMEPref != null) {
                        mLIMEPref.setActiveIM(activeIM);
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "IndexOutOfBoundsException getting active IM", e);
            }

        }

    }

    /**
     * Add by Jeremy '11,9,17 for han convert (traditional <-> simplified) options
     */
    private void showHanConvertPicker() {
        AlertDialog.Builder builder;

        builder = createDialogBuilder();

        builder.setCancelable(true);
        builder.setIcon(R.drawable.logo);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setTitle(getResources().getString(R.string.han_convert_option_list));

        // Inline segmented control (無 / 繁轉簡 / 簡轉繁), matching the 喜好設定
        // page's 簡繁轉換 row, instead of a plain single-choice radio list.
        // The segmented buttons need a MaterialComponents-themed inflater
        // (materialButtonOutlinedStyle isn't in the framework dialog theme).
        android.content.Context matCtx = new ContextThemeWrapper(this, R.style.LIMESettingsTheme);
        matCtx = com.google.android.material.color.DynamicColors.wrapContextIfAvailable(
                matCtx, org.limeime.global.SystemAccentColor.dynamicColorOptions(this));
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(matCtx);
        android.view.View hanView = inflater.inflate(R.layout.preference_han_segmented, null);
        // Hide the in-layout title (the dialog already shows it) and footer spacing handled by layout.
        android.view.View titleView = hanView.findViewById(R.id.han_title);
        if (titleView != null) titleView.setVisibility(android.view.View.GONE);
        com.google.android.material.button.MaterialButtonToggleGroup group =
                hanView.findViewById(R.id.han_toggle_group);
        builder.setView(hanView);

        mOptionsDialog = builder.create();

        if (group != null) {
            final int[] ids = { R.id.han_opt_none, R.id.han_opt_t2s, R.id.han_opt_s2t };
            int current = mLIMEPref.getHanCovertOption();
            if (current < 0 || current >= ids.length) current = 0;
            group.check(ids[current]);
            group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                if (!isChecked) return;
                for (int i = 0; i < ids.length; i++) {
                    if (ids[i] == checkedId) {
                        handleHanConvertSelection(i);
                        break;
                    }
                }
                if (mOptionsDialog != null) mOptionsDialog.dismiss();
            });
        }
        Window window = mOptionsDialog.getWindow();
        if (!(window == null)) {
            WindowManager.LayoutParams lp = window.getAttributes();
            // Use InputView window token since we always use embedded candidate view now
            if (mInputView != null) {
                lp.token = mInputView.getWindowToken();
            }
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        }
        mOptionsDialog.show();
    }

    private void handleHanConvertSelection(int position) {
        mLIMEPref.setHanCovertOption(position);

    }

    /**
     * Add by Jeremy '10, 3, 24 for IM picker menu in options menu
     * renamed to showIMPicker from showKeybaordPicer to avoid confusion '12,3,40
     */
    private void showIMPicker() {
        if (DEBUG)
            Log.i(TAG, "showIMPicker()");
        buildActivatedIMList();
        if (activatedIMFullNameList.isEmpty()) {
            return;
        }

        AlertDialog.Builder builder;

        builder = createDialogBuilder();

        builder.setCancelable(true);
        builder.setIcon(R.drawable.logo);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setTitle(getResources().getString(R.string.keyboard_list));

        CharSequence[] items = new CharSequence[activatedIMFullNameList.size()];// =
        // getResources().getStringArray(R.array.keyboard);
        int curKB = 0;
        for (int i = 0; i < activatedIMFullNameList.size(); i++) {
            items[i] = activatedIMFullNameList.get(i);
            if (activeIM.equals(activatedIMList.get(i)))
                curKB = i;
        }

        builder.setSingleChoiceItems(items, curKB,
                (di, position) -> {
                    di.dismiss();
                    handleIMSelection(position);
                });

        mOptionsDialog = builder.create();
        Window window = mOptionsDialog.getWindow();
        // Jeremy '10, 4, 12
        // The IM is not initialialized. do nothing here if window=null.
        if (!(window == null)) {
            WindowManager.LayoutParams lp = window.getAttributes();
            // Jeremy '11,8,28 Use candidate instead of mInputview because mInputView may not present when using physical keyboard
            lp.token = mInputView.getWindowToken();  //always there Jeremy '12,5,4
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        }
        mOptionsDialog.show();

    }

    private void handleIMSelection(int position) {
        if (DEBUG) Log.i(TAG, "handleIMSelection() position = " + position);

        activeIM = activatedIMList.get(position);
        CharSequence activeIMName = activatedIMFullNameList.get(position);

        mLIMEPref.setActiveIM(activeIM);
        invalidateStartupConfigSnapshot();
        //spe.putString("keyboard_list", keyboardSelection);
        //spe.commit();


        //Jeremy '12,4,21 foce clear when switch to selected keybaord
        if (!mEnglishOnly) clearComposing(true);

        mEnglishOnly = false;//Jeremy '12,5,24 force to switch to Chinese mode if it's choosing in english mode.
        prepareImKeyboardConfigForChineseDraw(false);
        initialIMKeyboard();
        if (mKeyboardSwitcher != null) {
            currentSoftKeyboard = mKeyboardSwitcher.getImConfigKeyboard(activeIM);
        }

        showLimeToast(activeIMName);

    }

    public void onText(CharSequence text) {
        if (DEBUG)
            Log.i(TAG, "OnText()");
        InputConnection ic = getCurrentInputConnection();
        if (ic == null)
            return;
        ic.beginBatchEdit();

        if (mPredicting) {
            commitTyped(ic);
            //mJustRevertedSeparator = null;
        } else if (!mEnglishOnly && mComposing.length() > 0) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
            pickHighlightedCandidate();
            //	commitTyped(ic);
        }
        ic.commitText(text, 1);
        //ic.commitText(text, 0);

        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void updateCandidates() {
        this.updateCandidates(false);
    }

    public boolean isComposingOrSearchingCandidates() {
        return (mComposing != null && mComposing.length() > 0)
                || (queryThread != null && queryThread.isAlive())
                || mEmojiSearchMode;
    }

    static int adjustedEmojiInsertionPosition(List<Mapping> list, int requestedPosition) {
        if (list == null || list.isEmpty()) {
            return 0;
        }

        int position = Math.max(0, Math.min(requestedPosition, list.size()));
        for (int candidateIndex = position; candidateIndex < list.size(); candidateIndex++) {
            Mapping candidate = list.get(candidateIndex);
            if (candidate != null && isChinesePeriodOrComma(candidate)) {
                position = candidateIndex + 1;
                break;
            }
        }
        return position;
    }

    static boolean isEmojiSearchDoneKey(int primaryCode) {
        return primaryCode == LIMEBaseKeyboard.KEYCODE_DONE || primaryCode == MY_KEYCODE_ENTER;
    }

    static boolean shouldExitEmojiSearchToKeyboard(int primaryCode) {
        return isEmojiSearchDoneKey(primaryCode) || primaryCode == LIME.KEYCODE_EMOJI_PANEL;
    }

    static boolean emojiSearchInitialEnglishOnly(boolean sourceWasEnglish) {
        return sourceWasEnglish;
    }

    static boolean isEmojiSearchKeyboardModeKey(int primaryCode) {
        return primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE
                || primaryCode == KEYCODE_SWITCH_TO_IM_MODE
                || primaryCode == LIME.KEYCODE_EMOJI_ABC;
    }

    static boolean resolveEmojiSearchEnglishOnlyForModeKey(int primaryCode, boolean currentEnglishOnly) {
        if (primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE) {
            return true;
        }
        if (primaryCode == KEYCODE_SWITCH_TO_IM_MODE || primaryCode == LIME.KEYCODE_EMOJI_ABC) {
            return false;
        }
        return currentEnglishOnly;
    }

    static boolean shouldEmojiSearchConsumePrintableKey(int primaryCode, boolean englishOnly) {
        return englishOnly && primaryCode >= 32 && primaryCode < 127;
    }

    static boolean shouldAppendPickedCandidateToEmojiSearch(boolean emojiKeyboardShown,
                                                            boolean searchMode,
                                                            boolean emojiRecord,
                                                            boolean composingCodeRecord) {
        return emojiKeyboardShown && searchMode && !emojiRecord && !composingCodeRecord;
    }

    static int emojiSearchImeOptions(int imeOptions) {
        return (imeOptions & ~EditorInfo.IME_MASK_ACTION) | EditorInfo.IME_ACTION_DONE;
    }

    static int emojiSearchInputCandidateStripVisibility(boolean emojiKeyboardShown, boolean searchMode) {
        return emojiKeyboardShown && searchMode ? View.VISIBLE : View.GONE;
    }

    private static boolean isChinesePeriodOrComma(Mapping candidate) {
        return candidate.isChinesePunctuationSymbolRecord()
                || "，".equals(candidate.getWord())
                || "。".equals(candidate.getWord());
    }


    private void updateChineseSymbol() {
        //ChineseSymbol chineseSym = new ChineseSymbol();
        hasChineseSymbolCandidatesShown = true;
        List<Mapping> list = ChineseSymbol.getChineseSymoblList();
        if (!list.isEmpty()) {

            // Setup sel key display if
            String selkey = "1234567890";
            if (disable_physical_selection && hasPhysicalKeyPressed) {
                selkey = "";
            }

            setSuggestions(list, hasPhysicalKeyPressed, selkey);

            if (DEBUG) Log.i(TAG, "updateChineseSymbol():"
                    + "mCandidateList.size:" + mCandidateList.size());
        }

    }


    /**
     * Update the list of available candidates from the current composing text.
     * This will need to be filled in by however you are determining candidates.
     */
    public void updateCandidates(final boolean getAllRecords) {

        if (DEBUG) Log.i(TAG, "updateCandidate():Update Candidate mComposing:" + mComposing);

        hasChineseSymbolCandidatesShown = false;

        if (mComposing.length() > 0) {

            final LinkedList<Mapping> list = new LinkedList<>();

            String keyString = mComposing.toString();

            //Art '30,Sep,2011 restrict the length of composing text for Stroke5
            if (currentSoftKeyboard.contains("wb")) {
                if (keyString.length() > 5) {
                    keyString = keyString.substring(0, 5);
                    mComposing = new StringBuilder();
                    mComposing.append(keyString);
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null && mPredictionOn) ic.setComposingText(keyString, 1);
                }
            }

            final String finalKeyString = keyString;
            final boolean finalHasPhysicalKeyPressed = hasPhysicalKeyPressed;
            if (queryThread != null && queryThread.isAlive()) queryThread.interrupt();
            queryThread = new Thread() {

                public void run() {

                    try {
                        if (SearchSrv != null) {
                            list.addAll(SearchSrv.getMappingByCode(finalKeyString, !finalHasPhysicalKeyPressed, getAllRecords));
                        } else {
                            Log.w(TAG, "SearchSrv is null, skipping getMappingByCode");
                        }
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error in suggestion processing", e);
                    }
                    try {
                        sleep(THREAD_YIELD_DELAY_MS);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Error in suggestion processing", e);
                        return;   // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                    }
                    //Jeremy '11,6,19 EZ and ETEN use "`" as IM Keys, and also custom may use "`".
                    if (!list.isEmpty()) {
                        // Setup sel key display if
                        String selkey = null;
                        if (disable_physical_selection && finalHasPhysicalKeyPressed) {
                            selkey = "";
                        } else {
                            try {
                                if (SearchSrv != null) {
                                    selkey = SearchSrv.getSelkey();
                                }
                            } catch (RemoteException e) {
                                Log.e(TAG, "Error in suggestion processing", e);
                            }
                            String mixedModeSelkey = "`";
                            if (mixedModeSelkeyUsesSpace(hasSymbolMapping, activeIM, mLIMEPref.getPhoneticKeyboardType())) {
                                mixedModeSelkey = " ";
                            }


                            int selkeyOption = mLIMEPref.getSelkeyOption();
                            if (selkeyOption == 1) selkey = mixedModeSelkey + selkey;
                            else if (selkeyOption == 2) selkey = mixedModeSelkey + " " + selkey;
                        }

                        try {
                            sleep(THREAD_YIELD_DELAY_MS);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Error in suggestion processing", e);
                            return;   // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                        }


                        // Emoji Control
                        // Check the Emoji parameter setting and load icons into the suggestions list
                        int insertPosition = mLIMEPref.getEmojiDisplayPosition();
                        if (insertPosition > 0) {
                            HashMap<String, String> emojiCheck = new HashMap<>();
                            List<Mapping> emojiList = new LinkedList<>();

                            if (!list.isEmpty()) {

                                List<Mapping> item1 = null, item2, item3;

                                if (list.size() <= insertPosition) {
                                    insertPosition = list.size();
                                }

                                if (list.get(0).getWord().matches("[A-Za-z]+")) {

                                    item1 = SearchSrv.findEmojiForCandidate(list.get(0).getWord(), LimeDB.EmojiLocale.EN, 8);
                                    if (!item1.isEmpty()) {
                                        for (Mapping m : item1) {
                                            if (emojiCheck.get(m.getWord()) == null) {
                                                emojiList.add(m);
                                                emojiCheck.put(m.getWord(), m.getWord());
                                            }
                                        }
                                    }

                                }

                                if (item1 == null || item1.isEmpty()) {

                                    //Log.i("EMOJI Check:", ""+list.get(1).getWord().getBytes().length);
                                    if (list.size() > 1 && list.get(1) != null && list.get(1).getWord() != null &&
                                            list.get(1).getWord().getBytes().length > 1 &&
                                            list.get(1).getWord().length() < 4
                                    ) {
                                        item2 = SearchSrv.findEmojiForCandidate(list.get(1).getWord(), LimeDB.EmojiLocale.TW, 8);
                                        if (!item2.isEmpty()) {
                                            for (Mapping m : item2) {
                                                if (emojiCheck.get(m.getWord()) == null) {
                                                    emojiList.add(m);
                                                    emojiCheck.put(m.getWord(), m.getWord());
                                                }
                                            }
                                        }
                                        if (item2.isEmpty()) {
                                            item3 = new LinkedList<>();
                                            if (!item3.isEmpty()) {
                                                for (Mapping m : item3) {
                                                    if (emojiCheck.get(m.getWord()) == null) {
                                                        emojiList.add(m);
                                                        emojiCheck.put(m.getWord(), m.getWord());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!emojiList.isEmpty()) {
                                    insertPosition = adjustedEmojiInsertionPosition(list, insertPosition);
                                    list.addAll(insertPosition, emojiList);
                                }
                            }
                        }

                        setSuggestions(list, finalHasPhysicalKeyPressed, selkey);

                        if (DEBUG) Log.i(TAG, "updateCandidates(): display selkey:" + selkey
                                + ", list.size:" + list.size()
                                + ", mComposing = " + mComposing);
                    } else {
                        //Jeremy '11,8,14
                        clearSuggestions();
                    }

                    // Show composing window if keyToKeyname got different string. Revised by Jeremy '11,6,4
                    if (SearchSrv != null && SearchSrv.getTablename() != null) {
                        String keynameString = SearchSrv.keyToKeyname(finalKeyString,
                                cachedImConfig(IMKEYS_CONFIG), cachedImConfig(IMKEYNAMES_CONFIG));
                        if (mCandidateView != null
                                && !keynameString.toUpperCase(Locale.US).equals(finalKeyString.toUpperCase(Locale.US))
                                && !keynameString.trim().isEmpty()
                        ) {
                            try {
                                sleep(THREAD_YIELD_DELAY_MS);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Error in suggestion processing", e);
                                // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                                return;
                            }
                            mCandidateView.setComposingText(keynameString);
                        }
                    }
                }
            };
            queryThread.start();


        } else
            //Jermy '11,8,14
            clearSuggestions();
    }

    /*
     * Update English suggestions view
     */
    private void updateEnglishPrediction() {

        hasChineseSymbolCandidatesShown = false;
        if (mPredictionOn && mLIMEPref.getEnglishPrediction()) {

            try {

                final LinkedList<Mapping> list = new LinkedList<>();

                if (tempEnglishWord == null || tempEnglishWord.length() == 0) {
                    //Jeremy '11,8,14
                    clearSuggestions();
                } else {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic == null) return;
                    boolean after = false;
                    try {
                        if (Objects.requireNonNull(ic.getTextAfterCursor(1, 1)).length() > 0) {
                            char c = Objects.requireNonNull(ic.getTextAfterCursor(1, 1)).charAt(0);
                            if (!Character.isLetterOrDigit(c)) {
                                after = true;
                            }
                        } else {
                            after = true;
                        }
                    } catch (StringIndexOutOfBoundsException e) {
                        Log.e(TAG, "Error in suggestion processing", e);
                        after = true;
                    }

                    boolean matchedtemp = false;

                    if (tempEnglishWord.length() > 0) {
                        try {
                            if (tempEnglishWord.toString()
                                    .equalsIgnoreCase(
                                            Objects.requireNonNull(ic.getTextBeforeCursor(
                                                            tempEnglishWord.toString()
                                                                    .length(), 1))
                                                    .toString())) {
                                matchedtemp = true;
                            }
                        } catch (StringIndexOutOfBoundsException e) {
                            Log.e(TAG, "Error in suggestion processing", e);
                        }
                    }

                    if (after || matchedtemp) {

                        tempEnglishList.clear();

                        final boolean finalHasPhysicalKeyPressed = hasPhysicalKeyPressed;
                        if (queryThread != null && queryThread.isAlive()) queryThread.interrupt();
                        queryThread = new Thread() {
                            public void run() {
                                List<Mapping> suggestions = null;
                                try {
                                    suggestions = SearchSrv.getEnglishSuggestions(tempEnglishWord.toString());
                                } catch (RemoteException e) {
                                    Log.e(TAG, "Error in suggestion processing", e);
                                }
                                try {
                                    sleep(THREAD_YIELD_DELAY_MS);
                                } catch (InterruptedException e) {
                                    Log.e(TAG, "Error in suggestion processing", e);
                                    return;   // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                                }

                                list.addAll(buildEnglishPredictionCandidates(tempEnglishWord.toString(), suggestions));

                                if (!list.isEmpty()) {
                                    // Setup sel key display if
                                    String selkey = "1234567890";
                                    if (disable_physical_selection && finalHasPhysicalKeyPressed) {
                                        selkey = "";
                                    }
                                    try {
                                        sleep(THREAD_YIELD_DELAY_MS);
                                    } catch (InterruptedException e) {
                                        Log.e(TAG, "Error in suggestion processing", e);
                                        return;   // terminate thread here, since it is interrupted and more recent getMappingByCode will update the suggestions.
                                    }


                                    // Emoji Control
                                    // Check the Emoji parameter setting and load icons into the suggestions list
                                    int insertPosition = mLIMEPref.getEmojiDisplayPosition();
                                    if (insertPosition > 0) {
                                        HashMap<String, String> emojiCheck = new HashMap<>();
                                        List<Mapping> emojiList = new LinkedList<>();

                                        if (!list.isEmpty()) {

                                            List<Mapping> item1;
                                            if (list.size() <= insertPosition) {
                                                insertPosition = list.size();
                                            }

                                            item1 = SearchSrv.findEmojiForCandidate(list.get(0).getWord(), LimeDB.EmojiLocale.EN, 8);
                                            if (!item1.isEmpty()) {
                                                for (Mapping m : item1) {
                                                    if (emojiCheck.get(m.getWord()) == null) {
                                                        emojiList.add(m);
                                                        emojiCheck.put(m.getWord(), m.getWord());
                                                    }
                                                }
                                            }

                                            if (!emojiList.isEmpty()) {
                                                insertPosition = adjustedEmojiInsertionPosition(list, insertPosition);
                                                list.addAll(insertPosition, emojiList);
                                            }
                                        }
                                    }


                                    //Log.i("EMOJIbefore:", tempEnglishList.size() + "");
                                    tempEnglishList.addAll(list);
                                    setEnglishPredictionSuggestions(list, finalHasPhysicalKeyPressed, selkey);

                                    //Log.i("EMOJIafter:", tempEnglishList.size() + "");

                                } else {
                                    //Jeremy '11,8,14
                                    clearSuggestions();
                                }
                            }
                        };
                        queryThread.start();
                    }

                }

            } catch (Exception e) {
                Log.e(TAG, "Error updating English prediction", e);

            }
        }
    }

    /*
     * Update dictionary view
     */
    private void updateRelatedPhrase(final boolean getAllRecords) {
        if (DEBUG)
            Log.i(TAG, "updateRelatedPhrase()");
        hasChineseSymbolCandidatesShown = false;
        // Also use this to control whether need to display the english
        // suggestions words.

        // If there is no Temp Matched word exist then not to display dictionary
        // Modified by Jeremy '10, 4,1. getCode -> getWord
        // if( tempMatched != null && tempMatched.getCode() != null &&
        // !tempMatched.getCode().equals("")){
        final Mapping relatedCandidate = committedCandidate;
        if (relatedCandidate != null && relatedCandidate.getWord() != null
                && !relatedCandidate.getWord().isEmpty()) {

            final boolean finalHasPhysicalKeyPressed = hasPhysicalKeyPressed;
            if (queryThread != null && queryThread.isAlive()) queryThread.interrupt();
            queryThread = new Thread() {
                public void run() {

                    LinkedList<Mapping> list = new LinkedList<>();
                    //Jeremy '11,8,9 Insert completion suggestions from application
                    //in front of related dictionary list in full-screen mode
                    if (mCompletionOn) {
                        list.addAll(buildCompletionList());
                    }


                    if (hasMappingList) {
                        if (queryThread != null && queryThread.isAlive()) queryThread.interrupt();
                        try {
                            if (!relatedCandidate.isEmojiRecord() && !relatedCandidate.isChinesePunctuationSymbolRecord()) {
                                list.addAll(SearchSrv.getRelatedByWord(relatedCandidate.getWord(), getAllRecords));
                            }
                        } catch (RemoteException e) {
                            Log.e(TAG, "Error in suggestion processing", e);
                        }

                        if (!list.isEmpty()) {


                            // Setup sel key display if
                            String selkey = "1234567890";
                            if (disable_physical_selection && finalHasPhysicalKeyPressed) {
                                selkey = "";
                            }

                            setSuggestions(list, finalHasPhysicalKeyPressed && !isFullscreenMode(), selkey);
                        } else {
                            committedCandidate = null;
                            //Jermy '11,8,14
                            clearSuggestions();
                        }
                    }
                }
            };
            queryThread.start();
        }

    }

    private List<Mapping> buildCompletionList() {
        LinkedList<Mapping> list = new LinkedList<>();
        for (int i = 0; i < (mCompletions != null ? mCompletions.length : 0); i++) {
            CompletionInfo ci = mCompletions[i];
            if (ci != null) {
                Mapping temp = new Mapping();
                temp.setWord(ci.getText().toString());
                temp.setCode("");
                temp.setCompletionSuggestionRecord();
                list.add(temp);
            }
        }
        return list;
    }

    /**
     * Check if the keyboard view is currently hidden.
     *
     * @return true if keyboard view is hidden (GONE), false otherwise
     */
    public boolean isKeyboardViewHidden() {
        return mInputView != null && mInputView.getVisibility() == View.GONE;
    }


    /**
     * Restore keyboard view if it's hidden.
     *
     * @param forceRestore If true, restore even if there's active composing text (e.g., when user explicitly clicks keyboard button)
     */
    public void restoreKeyboardViewIfHidden(boolean forceRestore) {
        // Only restore if:
        // 1. hasPhysicalKeyPressed is true (user was using physical keys)
        // 2. keyboard view is actually hidden
        // 3. Either forceRestore is true OR there's no active composing text (to avoid restoring during composition when not explicitly requested)
        if (hasPhysicalKeyPressed && mInputView != null && mInputView.getVisibility() == View.GONE) {
            // If forceRestore is true (user explicitly clicked keyboard button), restore regardless of composing text
            // Otherwise, only restore if there's no active composing text
            if (forceRestore || (mComposing == null || mComposing.length() == 0)) {
                hasPhysicalKeyPressed = false;
                mInputView.setVisibility(View.VISIBLE);

                // Ensure candidate view container remains visible when keyboard is restored
                if (mCandidateInInputView != null) {
                    mCandidateInInputView.setVisibility(View.VISIBLE);
                    // Ensure candidate view itself is visible
                    if (mCandidateViewInInputView != null) {
                        mCandidateViewInInputView.setVisibility(View.VISIBLE);
                    }

                    // Explicitly show candidate view using the handler
                    showCandidateView();

                    // Request layout update and re-apply window insets
                    mCandidateInInputView.post(() -> {
                        mCandidateInInputView.setVisibility(View.VISIBLE);
                        if (mCandidateViewInInputView != null) {
                            mCandidateViewInInputView.setVisibility(View.VISIBLE);
                        }
                        // Clear popup expansion state when keyboard is restored (popup should expand downward now)
                        mCandidateInInputView.requestApplyInsets();
                        mCandidateInInputView.requestLayout();
                        // Update width constraint when keyboard is restored (button visibility changes)
                        mCandidateInInputView.updateCandidateViewWidthConstraint();
                    });
                }

                if (DEBUG) {
                    Log.i(TAG, "Restored keyboard view on touch/click event" + (forceRestore ? " (forced)" : ""));
                }
            }
        }
    }


    private void initCandidateView() {
        if (DEBUG) Log.i(TAG, "initCandidateView()");

        mCandidateViewHandler.showCandidateView();
        mCandidateViewHandler.hideCandidateView();
    }

    private void showCandidateView() {
        if (DEBUG) Log.i(TAG, "showCandidateView()");
        mCandidateViewHandler.showCandidateView();
    }

    private void hideCandidateView() {
        if (DEBUG) Log.i(TAG, "hideCandidateView()");
        if (mCandidateView != null) mCandidateView.clear();
        hasCandidatesShown = false;
        hasChineseSymbolCandidatesShown = false;
        // Always use embedded candidate view in InputView, regardless of physical or soft keyboard
        if (mCandidateViewInInputView == null)
            return;

        mCandidateViewHandler.hideCandidateViewDelayed();

    }

    private void showEmptyCandidateToolbar() {
        if (DEBUG) Log.i(TAG, "showEmptyCandidateToolbar()");

        if (mComposing != null && mComposing.length() > 0)
            mComposing.setLength(0);

        selectedCandidate = null;

        if (mCandidateList != null)
            mCandidateList.clear();

        if (mCandidateViewInInputView == null)
            return;

        setInputCandidateStripVisibility(View.VISIBLE);
        mCandidateViewInInputView.setSuggestions(null, false);
        mCandidateViewHandler.showCandidateView();
        mCandidateInInputView.requestLayout();
        mCandidateInInputView.updateCandidateViewWidthConstraint();
    }

    private void forceHideCandidateView() {
        if (DEBUG) Log.i(TAG, "forceHideCandidateView()");

        if (mComposing != null && mComposing.length() > 0)
            mComposing.setLength(0);

        selectedCandidate = null;
        //selectedIndex = 0;

        if (mCandidateList != null)
            mCandidateList.clear();

        // mFixedCandidateViewOn is always true
        mCandidateViewInInputView.forceHide();
    }


    final CandidateViewHandler mCandidateViewHandler = new CandidateViewHandler(this);


    private static class CandidateViewHandler extends Handler {

        private final WeakReference<LIMEService> mLIMEService;
        private final int MSG_SHOW_CANDIDATE_VIEW = 1;
        private final int MSG_HIDE_CANDIDATE_VIEW = 2;

        CandidateViewHandler(LIMEService im) {
            super(Looper.getMainLooper());
            mLIMEService = new WeakReference<>(im);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (DEBUG) Log.i(TAG, "CandidateViewHandler.handleMessage(): message:" + msg.what);
            LIMEService mLIMEInstance = mLIMEService.get();
            if (mLIMEInstance == null) return;
            switch (msg.what) {
                case MSG_SHOW_CANDIDATE_VIEW:
                    mLIMEInstance.setCandidatesViewShown(true);
                    break;
                case MSG_HIDE_CANDIDATE_VIEW:
                    mLIMEInstance.setCandidatesViewShown(false);
                    break;
            }
        }

        void showCandidateView() {
            removeMessages(MSG_HIDE_CANDIDATE_VIEW);  //cancel previous hide messages if any
            sendMessage(obtainMessage(MSG_SHOW_CANDIDATE_VIEW));
        }

        void hideCandidateView() {
            sendMessage(obtainMessage(MSG_HIDE_CANDIDATE_VIEW));
        }

        void hideCandidateViewDelayed() {
            sendMessageDelayed(obtainMessage(MSG_HIDE_CANDIDATE_VIEW), LIMEService.DELAY_BEFORE_HIDE_CANDIDATE_VIEW);
        }
    }

    public synchronized void setSuggestions(List<Mapping> suggestions, boolean showNumber, String diplaySelkey) {

        if (suggestions != null && !suggestions.isEmpty()) {
            setInputCandidateStripVisibility(View.VISIBLE);

            if (DEBUG)
                Log.i(TAG, "setSuggestion():suggestions.size=" + suggestions.size()
                        + ", mComposing = " + mComposing
                        + ", hasPhysicalKeyPressed:" + hasPhysicalKeyPressed
                );


            hasCandidatesShown = true; //Jeremy '15,6,1 move after hideCandidateView if candidateView is fixed.
            hasMappingList = true;

            if (mCandidateView != null) {
                mCandidateList = (LinkedList<Mapping>) suggestions;
                try {

                    selectedCandidate = defaultServiceSelectedCandidate(suggestions, hasPhysicalKeyPressed);
                } catch (Exception e) {
                    Log.e(TAG, "Error in suggestion processing", e);
                }
                mCandidateView.setSuggestions(suggestions, showNumber, diplaySelkey);
                if (DEBUG)
                    Log.i(TAG, "setSuggestion(): mCandidateList.size: " + mCandidateList.size()
                            + ", mComposing = " + mComposing);
            }
            // Update CandidateView width constraint after setting suggestions
            if (mCandidateInInputView != null) {
                mCandidateInInputView.updateCandidateViewWidthConstraint();
            }
        } else {
            if (DEBUG) Log.i(TAG, "setSuggestion() with list=null");
            hasMappingList = false;
            //Jeremy '11,8,15
            clearSuggestions();


        }

    }

    private synchronized void setEnglishPredictionSuggestions(List<Mapping> suggestions,
                                                              boolean showNumber,
                                                              String diplaySelkey) {

        if (suggestions != null && !suggestions.isEmpty()) {
            setInputCandidateStripVisibility(View.VISIBLE);
            hasCandidatesShown = true;
            hasMappingList = true;
            selectedCandidate = null;

            if (mCandidateView != null) {
                mCandidateList = (LinkedList<Mapping>) suggestions;
                mCandidateView.setSuggestionsWithoutHighlight(suggestions, showNumber, diplaySelkey);
                if (DEBUG)
                    Log.i(TAG, "setEnglishPredictionSuggestions(): mCandidateList.size: "
                            + mCandidateList.size() + ", tempEnglishWord = " + tempEnglishWord);
            }
            if (mCandidateInInputView != null) {
                mCandidateInInputView.updateCandidateViewWidthConstraint();
            }
        } else {
            if (DEBUG) Log.i(TAG, "setEnglishPredictionSuggestions() with list=null");
            hasMappingList = false;
            clearSuggestions();
        }

    }

    /**
     * Public method to update CandidateView width constraint.
     * Called by CandidateView when the expanded popup is closed.
     */
    public void updateCandidateViewWidthConstraint() {
        if (mCandidateInInputView != null) {
            mCandidateInInputView.updateCandidateViewWidthConstraint();
        }
    }

    public void dismissCandidateComposing() {
        if (mEmojiKeyboardShown && mEmojiSearchMode) {
            exitEmojiSearchToKeyboard();
            return;
        }
        if (mCandidateView != null) {
            mCandidateView.hideCandidatePopup();
        }
        // When the auto Chinese-punctuation strip itself is showing, the user is
        // dismissing the strip. clearComposing(true) routes through clearSuggestions(),
        // which rebuilds the strip (hasCandidatesShown is still true) on the same tap,
        // making it impossible to dismiss. hideCandidateView() resets both
        // hasCandidatesShown and hasChineseSymbolCandidatesShown so it stays gone until
        // the next candidate cycle. Related-phrase dismissal is unaffected
        // (hasChineseSymbolCandidatesShown == false there), so it still surfaces the
        // punctuation strip via clearSuggestions().
        if (hasChineseSymbolCandidatesShown) {
            hideCandidateView();
            return;
        }
        clearComposing(true);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
    }

    public void showLimeToast(CharSequence text) {
        if (text == null || text.length() == 0) return;
        try {
            if (Looper.myLooper() != null) {
                CandidateView toastTarget = mCandidateView;
                if (mCandidateViewInInputView != null && mCandidateViewInInputView.getWindowToken() != null) {
                    toastTarget = mCandidateViewInInputView;
                }
                if (toastTarget != null) {
                    toastTarget.showLimeToast(text);
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot show lime_toast: " + e.getMessage());
        }
    }

    private void hideLimeToast() {
        try {
            if (mCandidateView != null) {
                mCandidateView.hideLimeToast();
            }
            if (mCandidateViewInInputView != null && mCandidateViewInInputView != mCandidateView) {
                mCandidateViewInInputView.hideLimeToast();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot hide lime_toast: " + e.getMessage());
        }
    }

    public void showReverseLookup(CharSequence text) {
        if (text == null || text.length() == 0) return;
        showLimeToast(text);
    }


    private void handleBackspace() {
        if (DEBUG)
            Log.i(TAG, "handleBackspace()");
        final int length = mComposing.length();
        InputConnection ic = getCurrentInputConnection();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length == 1) {
            //Jeremy '12,4, 21 force clear the last characacter in composing
            clearComposing(true);
            //Jeremy '12,4,29 use mEnglishOnly instead of onIM
        } else if (!mEnglishOnly  // composing length == 0 after here
                && (hasCandidatesShown)// repalce isCandaiteShwon() with hasCandidatesShwn by Jeremy '12,5,6
                //&& mLIMEPref.getAutoChineseSymbol()
                && !hasChineseSymbolCandidatesShown) {
            // #78 Bug 2 backport (iOS parity, see docs/CANDI_FUNCTION_KEYS.md):
            // related-phrase suggestions are browse-only — Backspace must dismiss the
            // stale bar AND delete the previous character in one tap, rather than only
            // clearing candidates (which under autoChineseSymbol then surfaces the
            // Chinese-punctuation list and requires 2–3 taps to actually delete).
            // Pre-clearing hasCandidatesShown prevents clearSuggestions() inside
            // clearComposing(false) from sliding into updateChineseSymbol().
            hasCandidatesShown = false;
            clearComposing(false);
            keyDownUp(KeyEvent.KEYCODE_DEL, false);
        } else if (!mEnglishOnly
                //&& mCandidateView !=null && isCandidateShown()
                && hasCandidatesShown //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
            //&& !mFixedCandidateViewOn //Jeremy '12,5,23 clear the chinese symbol list for arrow keys to do navigation inside document
        ) {
            hideCandidateView();  //Jeremy '11,9,8
        } else {
            //Jeremy '11,8,15
            //clearSuggestions();
            try {
                if (mEnglishOnly && mLIMEPref.getEnglishPrediction() && mPredictionOn
                        && (!hasPhysicalKeyPressed || mLIMEPref.getEnglishPredictionOnPhysicalKeyboard())//mPredictionOnPhysicalKeyboard)
                ) {
                    if (tempEnglishWord != null && tempEnglishWord.length() > 0) {
                        tempEnglishWord.deleteCharAt(tempEnglishWord.length() - 1);
                        updateEnglishPrediction();
                    }

                }
                keyDownUp(KeyEvent.KEYCODE_DEL, false);

            } catch (Exception e) {
                Log.e(TAG, "Error in key handling", e);

            }
        }

    }

    public void setCandidatesViewShown(boolean shown) {

        if (DEBUG)
            Log.i(TAG, "setCandidateViewShown():" + shown);
        // LIME renders candidates inside the input view. Do not show Android's
        // separate candidates window, or URL/email empty-toolbar fields can get
        // a blank band above the keyboard.
        try {
            super.setCandidatesViewShown(false);
        } catch (RuntimeException e) {
            // Candidates view not initialized in test environment; silently ignore
            Log.w(TAG, "setCandidatesViewShown: candidates view not initialized: " + e.getMessage());
        }

        if (DEBUG) {
            if (mCandidateViewInInputView != null) {
                Log.i(TAG, "isCandidateViewShown (embedded):" + mCandidateViewInInputView.isShown());
            }
        }

    }


    private void handleShift() {
        if (DEBUG) Log.i(TAG, "handleShift()");
        if (mInputView == null) {
            return;
        }

        boolean doubleTap = isShiftDoubleTap();
        if (mKeyboardSwitcher.isAlphabetMode()) {
            ShiftTapState nextState = nextShiftTapState(mInputView.isShifted(), mCapsLock, doubleTap);
            applyAlphabetShiftState(nextState);
        } else {
            ShiftTapState nextState = nextShiftTapState(mHasShift, mCapsLock, doubleTap);
            applyImShiftState(nextState);
        }
    }

    private boolean isShiftDoubleTap() {
        long now = SystemClock.uptimeMillis();
        boolean doubleTap = isShiftDoubleTap(mLastShiftTime, now, ViewConfiguration.getDoubleTapTimeout());
        mLastShiftTime = now;
        return doubleTap;
    }

    static long shiftDoubleTapWindowAfterKey(int primaryCode, long lastShiftTime) {
        return primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT ? lastShiftTime : -1;
    }

    static boolean isShiftDoubleTap(long lastShiftTime, long now, long timeout) {
        return lastShiftTime > 0 && now - lastShiftTime <= timeout;
    }

    static ShiftTapState nextShiftTapState(boolean shifted, boolean capsLock, boolean doubleTap) {
        if (capsLock) {
            return new ShiftTapState(false, false);
        }
        if (doubleTap) {
            return new ShiftTapState(true, true);
        }
        return new ShiftTapState(!shifted, false);
    }

    static final class ShiftTapState {
        final boolean shifted;
        final boolean capsLock;

        ShiftTapState(boolean shifted, boolean capsLock) {
            this.shifted = shifted;
            this.capsLock = capsLock;
        }
    }

    private void applyAlphabetShiftState(ShiftTapState state) {
        setCapsLockState(state.capsLock);
        mInputView.setShifted(state.shifted);
        mHasShift = state.shifted;
        if (state.shifted && !mKeyboardSwitcher.isShifted()) {
            mKeyboardSwitcher.toggleShift();
        } else if (!state.shifted && mKeyboardSwitcher.isShifted()) {
            mKeyboardSwitcher.toggleShift();
        }
    }

    private void applyImShiftState(ShiftTapState state) {
        setCapsLockState(state.capsLock);
        if (state.shifted && !mKeyboardSwitcher.isShifted()) {
            mKeyboardSwitcher.toggleShift();
        } else if (!state.shifted && mKeyboardSwitcher.isShifted()) {
            mKeyboardSwitcher.toggleShift();
        }
        mHasShift = state.shifted;
    }

    private void setCapsLockState(boolean capsLock) {
        if (mCapsLock == capsLock) {
            if (mInputView != null && mInputView.getKeyboard() instanceof LIMEKeyboard) {
                ((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(capsLock);
            }
            return;
        }
        mCapsLock = capsLock;
        if (mInputView != null && mInputView.getKeyboard() instanceof LIMEKeyboard) {
            ((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(mCapsLock);
        }
    }

    /**
     * Integrated all soft keyboards switching in this function.
     */
    private void switchKeyboard(int primaryCode) {
        if (DEBUG) Log.i(TAG, "switchKeyboard() primaryCode = " + primaryCode);
        if (mCapsLock)
            toggleCapsLock();
        if (mEmojiKeyboardShown) {
            hideEmojiKeyboard();
        }

        // Cancel active composition when switching Chi -> Eng; other switches keep
        // the legacy auto-commit behavior.
        try {
            if (primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE) {
                if (mComposing != null && mComposing.length() > 0) {
                    clearComposing(true);
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.finishComposingText();
                } else {
                    clearComposing(false);
                }
            } else if (mComposing != null && mComposing.length() > 0) {
                getCurrentInputConnection().commitText(mComposing, 1);
                finishComposing();
                clearComposing(false);
            } else {
                clearComposing(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in composing finish", e);
            // ignore all possible error
        }

        hideCandidateView();


        if (primaryCode == KEYCODE_SWITCH_TO_SYMBOL_MODE) { //Symbol keyboard
            mEnglishOnly = true;
            mKeyboardSwitcher.toggleSymbols();
            // mFixedCandidateViewOn is always true
            forceHideCandidateView();
        } else if (primaryCode == LIMEKeyboardView.KEYCODE_PHONE_SIMPLE_LONGPRESS) {
            mEnglishOnly = true;
            mKeyboardSwitcher.switchToPhoneSimple();
            forceHideCandidateView();
        } else if (primaryCode == KEYCODE_SWITCH_SYMBOL_KEYBOARD) { //Symbol keyboard
            mEnglishOnly = true;
            mKeyboardSwitcher.switchSymbols();
            // mFixedCandidateViewOn is always true
            forceHideCandidateView();
        } else if (primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE) { //Chi/phone_simple --> Eng
            mEnglishOnly = true;
            mLIMEPref.setLanguageMode(true);
            mKeyboardSwitcher.switchToEnglish(); // feat#124: idempotent; fixes phone_simple ABC return
            // mFixedCandidateViewOn is always true
            if (!mPredictionOn) {
                showEmptyCandidateToolbar();
            } else {
                mCandidateViewInInputView.setSuggestions(null, false);  // reset the candidate view if it's force hided before
            }
        } else if (primaryCode == KEYCODE_SWITCH_TO_IM_MODE) { //Eng --> Chi moved from SwitchKeyboardIM by Jeremy '12,4,29
            mEnglishOnly = false;
            mLIMEPref.setLanguageMode(false);
            prepareImKeyboardConfigForChineseDraw(true);
            initialIMKeyboard();
            // mFixedCandidateViewOn is always true
            mCandidateViewInInputView.setSuggestions(null, false);  // reset the candiate view if it's force hided before
        }


        mHasShift = false;
        updateShiftKeyState(getCurrentInputEditorInfo());

        // Update keyboard xml information
        currentSoftKeyboard = mKeyboardSwitcher.getImConfigKeyboard(activeIM);

    }


    /**
     * For physical keybaord to switch between chinese and english mode.
     */
    private void switchChiEng() {
        if (DEBUG)
            Log.i(TAG, "switchChiEng(): mEnglishOnly:" + mEnglishOnly);

        //Jeremy '12,4,21 force clear before switching chi/eng
        clearComposing(false);

        boolean switchingToChinese = !mKeyboardSwitcher.isChinese();
        if (switchingToChinese) {
            prepareImKeyboardConfigForChineseDraw(true);
        }
        mKeyboardSwitcher.toggleChinese();
        mEnglishOnly = !mKeyboardSwitcher.isChinese();
        mLIMEPref.setLanguageMode(mEnglishOnly);

        if (DEBUG)
            Log.i(TAG, "switchChiEng(): mEnglishOnly updated as " + mEnglishOnly);
        clearSuggestions(); //Jeremy '11,9,5
    }


    @SuppressLint("InflateParams")
    private void initialViewAndSwitcher(boolean forceRecreate) {
        if (DEBUG)
            Log.i(TAG, "initialViewAndSwitcher() mKeyboardThemeIndex = " + mKeyboardThemeIndex + ", mLIMEPref.getKeyboardTheme() = " + mLIMEPref.getKeyboardTheme());

        boolean mForceRecreate = forceRecreate;
        if (mKeyboardThemeIndex != mLIMEPref.getKeyboardTheme()) {
            mKeyboardThemeIndex = mLIMEPref.getKeyboardTheme();
            mForceRecreate = true;
            mThemeContext = null;
            clearAppliedFollowSystemAccentState();
            clearAppliedNavigationBarThemeState();
            if (mKeyboardSwitcher != null) mKeyboardSwitcher.resetKeyboards(true);
        }

        if (mThemeContext == null) {
            mThemeContext = new ContextThemeWrapper(this, getKeyboardTheme());
            if (mKeyboardSwitcher != null) mKeyboardSwitcher.setThemedContext(mThemeContext);

        }

        boolean mIsHardwareAcceleratedDrawingEnabled = true;
        // mFixedCandidateViewOn is always true - Have candidateView in InputView
        //Create inputView if it's null
        if (mCandidateInInputView == null || mForceRecreate) {

            mCandidateInInputView = (CandidateInInputViewContainer) LayoutInflater.from(mThemeContext).inflate(
                    R.layout.inputcandidate, null);
            mInputView = mCandidateInInputView.findViewById(R.id.keyboard);
            mInputView.setOnKeyboardActionListener(this);
            hasDistinctMultitouch = mInputView.hasDistinctMultitouch();
            mInputView.setHardwareAcceleratedDrawingEnabled(mIsHardwareAcceleratedDrawingEnabled);
            mCandidateInInputView.initViews();
            mCandidateViewInInputView = mCandidateInInputView.findViewById(R.id.candidatesView);
            mCandidateViewInInputView.setService(this);
            mCandidateInInputView.setService(this);
            mEmojiKeyboardView = mCandidateInInputView.findViewById(R.id.emoji_keyboard);
            setupEmojiKeyboardView();
            advanceInputViewGeneration();

        }
        if (mCandidateView != mCandidateViewInInputView)
            mCandidateView = mCandidateViewInInputView;
        applyFollowSystemAccentColors();


        // Check if mKeyboardSwitcher == null
        if (mKeyboardSwitcher == null) {
            mKeyboardSwitcher = new LIMEKeyboardSwitcher(this, mThemeContext);
        }
        mKeyboardSwitcher.setInputView(mInputView);
        refreshStartupConfigSnapshotIfNeeded();
        mKeyboardSwitcher.setActivatedIMList(activatedIMList, activatedIMShortNameList);

        if (mKeyboardSwitcher.getKeyboardSize() == 0) {
            applyStartupConfigSnapshotToKeyboardSwitcher();
        }

    }

    /**
     * For initializing Chinese IM and corresponding soft keyboards.
     */
    // Whether the active IM has a SYMBOL root (a non-letter, non-digit char in its imkeys; ',' and
    // '.' are excluded — they are universal roots, not "symbol mapping"). Replaces the
    // hasSymbolMapping flag read in the mixed-mode selkey-prefix display (imkeys-driven, W-G).
    // Behaviour-preserving: the mixed-mode candidate-bar selkey prefix ("`" vs " ") is chosen by the
    // per-IM symbol-mapping flag (the policy), passed in as a PARAM (not a boolean-operator
    // decision-read on the field) so the flag site is retired without changing behaviour.
    static boolean mixedModeSelkeyUsesSpace(boolean hasSymbol, String activeIM, String phoneticType) {
        return hasSymbol && !activeIM.equals(LIME.IM_DAYI)
                && !(activeIM.equals(LIME.IM_PHONETIC) && phoneticType.equals(LIME.IM_PHONETIC));
    }

    private void initialIMKeyboard() {
        if (DEBUG)
            Log.i(TAG, "initalizeIMKeyboard(): keyboardSelection:" + activeIM);
        //mEnglishOnly = false;
        //super.setCandidatesViewShown(false);

        if (mKeyboardSwitcher == null) {
            Log.w(TAG, "initialIMKeyboard(): mKeyboardSwitcher is null, skipping keyboard initialization");
            return;
        }

        switch (activeIM) {
            case "custom":
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false);

                hasNumberMapping = mLIMEPref.getAllowNumberMapping();
                hasSymbolMapping = mLIMEPref.getAllowSymoblMapping();
                break;
            case LIME.IM_CJ:
            case LIME.IM_CJ4:
            case LIME.IM_SCJ:
            case LIME.IM_CJ5:
            case LIME.IM_ECJ:
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false);
                hasNumberMapping = false;
                hasSymbolMapping = false;
                break;
            case LIME.IM_PHONETIC:
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false);
                //Jeremy '11,6,18 ETEN 26 has no number mapping
                boolean standardPhonetic = !(mLIMEPref.getPhoneticKeyboardType().equals(LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26)
                        || mLIMEPref.getPhoneticKeyboardType().equals(LIME.IM_PHONETIC_KEYBOARD_HSU));
                hasNumberMapping = standardPhonetic;
                hasSymbolMapping = standardPhonetic;
                break;
            case LIME.IM_EZ:
            case LIME.IM_DAYI:
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false);
                hasNumberMapping = true;
                hasSymbolMapping = true;
                break;
            case LIME.IM_ARRAY10:
            case LIME.IM_PINYIN:
                hasNumberMapping = true;
                hasSymbolMapping = false;
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false);
                break;
            case LIME.IM_ARRAY:
                hasNumberMapping = true; //Jeremy '12,4,28 array 30 actually use number combination keys to enter symbols1

                hasSymbolMapping = true;
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false);
                break;
            case LIME.IM_WB:
                hasNumberMapping = false;
                hasSymbolMapping = true;
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false);
                break;
            case LIME.IM_HS:
                hasNumberMapping = true;
                hasSymbolMapping = true;
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false);
                break;
            default:
                mKeyboardSwitcher.setKeyboardMode(activeIM,
                        LIMEKeyboardSwitcher.MODE_TEXT, mImeOptions, true, false, false);
                break;
        }
        //Jeremy '11,9,3 for phone numeric key direct input on chacha
        if (mLIMEPref.getPhysicalKeyboardType().equals("chacha")) hasNumberMapping = false;
        String tablename = activeIM;
        if (tablename.equals("custom") || tablename.equals("phone")) {
            tablename = "custom";
        }
        hasSymbolMapping = hasSymbolMappingForKeyboard(hasSymbolMapping,
                mKeyboardSwitcher.getImConfigKeyboard(activeIM));
        //Jeremy '11,6,10 pass hasnumbermapping and hassymbolmapping to searchservice for selkey validation.
        if (DEBUG)
            Log.i(TAG, "switchKeyboard() current keyboard:" +
                    tablename + " hasnumbermapping:" + hasNumberMapping + " hasSymbolMapping:" + hasSymbolMapping);
        SearchSrv.setTableName(tablename, hasNumberMapping, hasSymbolMapping);
        // W-B: cache the active IM's root set for acceptsIntoComposing (mirror iOS refreshImKeys).
        // Phonetic roots depend on the keyboard type — et26/hsu have NO digit/symbol roots, but the
        // stored "imkeys" meta is always BPMF — so resolve per type (mirror iOS imKeysForTable),
        // else et26/hsu acceptance wrongly composes BPMF's digits / ; / - . Other IMs read the
        // stored imkeys (matches the endkey path's getImConfig(activeIM, IMKEYS_CONFIG) at :2334).
        refreshImConfigCache();
        String activeImKeys = activeIM.equals(LIME.IM_PHONETIC)
                ? SearchSrv.getPhoneticImKeys(mLIMEPref.getPhoneticKeyboardType())
                : cachedImConfig(IMKEYS_CONFIG);
        currentImKeys = (activeImKeys == null) ? "" : activeImKeys;
    }

    private boolean handleSelkey(int primaryCode) {
        if (DEBUG)
            Log.i(TAG, "handleSelKey()");
        // Jeremy '12,4,1 only do selkey on starndard keyboard

        // Check if disable physical key option is open
        if ((disable_physical_selection && hasPhysicalKeyPressed)
                || !mLIMEPref.getPhysicalKeyboardType().equals("normal_keyboard")) {
            return false;
        }

        if (DEBUG) Log.i(TAG, "handleSelkey():primarycode:" + primaryCode);

        int i = -1;
        if (mComposing.length() > 0 && !mEnglishOnly) { //Jeremy '12,4,29 use mEnglishOnly instead of onIM
            String selkey = "";

            // Jeremy '12,7,5 rewrite the selkey processing
            if (!(disable_physical_selection && hasPhysicalKeyPressed)) {
                try {
                    selkey = SearchSrv.getSelkey();
                } catch (RemoteException e) {
                    Log.e(TAG, "Error getting selkey", e);
                }

                String mixedModeSelkey = "`";
                if (mixedModeSelkeyUsesSpace(hasSymbolMapping, activeIM, mLIMEPref.getPhoneticKeyboardType())) {
                    mixedModeSelkey = " ";
                }


                int selkeyOption = mLIMEPref.getSelkeyOption();
                if (selkeyOption == 1) selkey = mixedModeSelkey + selkey;
                else if (selkeyOption == 2) selkey = mixedModeSelkey + " " + selkey;


                i = selkey.indexOf((char) primaryCode);

                //Jeremy '12,7,11 bypass space as first tone for phonetic
                if (i >= 0 && selkey.charAt(i) == ' '
                        && primaryCode == MY_KEYCODE_SPACE && activeIM.equals(LIME.IM_PHONETIC)
                        //&& mLIMEPref.getParameterBoolean("doLDPhonetic", true)
                        && !(mComposing.toString().endsWith(" ") || mComposing.length() == 0)) {
                    return false;
                }


            }

            //Jeremy '12,4,29 use mEnglishOnly instead of onIM
        } else if (mEnglishOnly || (mComposing.length() == 0)) {
            // related candidates view
            String relatedSelkey = "!@#$%^&*()";
            i = relatedSelkey.indexOf(primaryCode);
        }


        if (i < 0 || i >= mCandidateList.size()) {
            return false;
        } else {
            pickCandidateManually(i);
            return true;
        }

    }

    /**
     * This method construct candidate view and add key code to composing object
     */
    private void handleCharacter(int primaryCode) {
        //Jeremy '11,6,9 Cleaned code!!
        if (DEBUG)
            Log.i(TAG, "handleCharacter():primaryCode:" + primaryCode
                    + ", metaState = " + mMetaState
                    + ", hasPhysicalKeyPressed = " + hasPhysicalKeyPressed
                    + ", currentSoftKeyboard=" + currentSoftKeyboard);


        //Jeremy '11,6,6 processing physical keyboard selkeys.
        //Move here '11,6,9 to have lower priority than hasnumbermapping
        if (hasPhysicalKeyPressed && (mCandidateView != null && hasCandidatesShown)) { //Replace isCandidateShown() with hasCandidatesShown by Jeremy '12,5,6
            if (handleSelkey(primaryCode)) {
                updateShiftKeyState(getCurrentInputEditorInfo());
                if (DEBUG)
                    Log.i(TAG, "handleCharacter() sel key found return now");
                return;
            }
        }


        if (!mEnglishOnly) {

            InputConnection ic = getCurrentInputConnection();
            boolean isPhonetic = activeIM.equals(LIME.IM_PHONETIC);

            if (DEBUG)
                Log.i(TAG, "HandleCharacter():"
                        + " ic != null:" + (ic != null)
                        + " isValidLetter:" + isValidLetter(primaryCode)
                        + " isValidDigit:" + isValidDigit(primaryCode)
                        + " isValidSymbol:" + isValidSymbol(primaryCode)
                        + " hasSymbolMapping:" + hasSymbolMapping
                        + " hasNumberMapping:" + hasNumberMapping
                        + " (primaryCode== MY_KEYCODE_SPACE && keyboardSelection.equals(phonetic):" + (primaryCode == MY_KEYCODE_SPACE && isPhonetic)
                        + " mEnglishOnly:" + mEnglishOnly);


            if (acceptsIntoComposing(primaryCode, currentImKeys, hasSymbolMapping, hasNumberMapping, isPhonetic)) {
                mComposing.append((char) primaryCode);
                //InputConnection ic=getCurrentInputConnection();
                if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1);
                updateCandidates();
                //misMatched = mComposing.toString();
            } else if (mComposing != null
                    && isArraySymbolDigit(activeIM, mComposing.toString(), primaryCode)) {
                // Array30 symbol-input exception (行列30 symbols1). Digits are NOT array roots (not
                // in the table's imkeys / ARRAY_KEY), so acceptsIntoComposing above rejects a bare
                // digit — but a digit IS allowed while composing a 'w'- or 'hg'-prefixed number
                // sequence. De-flagged (previously gated on the mapping
                // flags, which never fired for array since array sets hasNumberMapping true) so it
                // works under the imkeys-based acceptance. The prefix guard stops it firing on
                // regular Array30 codes.
                mComposing.append((char) primaryCode);
                //InputConnection ic=getCurrentInputConnection();
                if (ic != null && mPredictionOn) ic.setComposingText(mComposing, 1);
                updateCandidates();
                //misMatched = mComposing.toString();
            } else {


                pickHighlightedCandidate();  // check here.

                if (ic != null) ic.commitText(String.valueOf((char) primaryCode), 1);
                //Jeremy '12,4,21
                finishComposing();


            }

        } else {
            /*
             * Handle when user input English Characters
             */
            if (DEBUG)
                Log.i(TAG, "handleCharacter() english only mode without prediction, committext = "
                        + (char) primaryCode);
            if (isInputViewShown()) {
                if (mInputView.isShifted()) {
                    primaryCode = Character.toUpperCase(primaryCode);
                }
            }

            InputConnection ic = getCurrentInputConnection();

            // ENGLISH_KB.md #0 / §2a: read+clear the pick-auto-space flag up front so
            // every path below clears it; only the punctuation-swap path acts on it.
            final boolean wasPickedAutoSpace = mPickedAutoSpace;
            mPickedAutoSpace = false;

            if (primaryCode == MY_KEYCODE_SPACE && mAutoCap && ic != null
                    && shouldInsertPeriodForEnglishDoubleSpace(ic.getTextBeforeCursor(64, 0))) {
                resetTempEnglishWord();
                if (mLIMEPref.getEnglishPrediction() && mPredictionOn) {
                    this.updateEnglishPrediction();
                }
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                if (!(!hasPhysicalKeyPressed && hasDistinctMultitouch))
                    updateShiftKeyState(getCurrentInputEditorInfo());
                return;
            }

            if (mLIMEPref.getEnglishPrediction() && mPredictionOn && !mKeyboardSwitcher.isSymbols()
                    && (!hasPhysicalKeyPressed || mLIMEPref.getEnglishPredictionOnPhysicalKeyboard())
            ) {
                if (Character.isLetter((char) primaryCode)) {
                    this.tempEnglishWord.append((char) primaryCode);
                    this.updateEnglishPrediction();
                } else {
                    resetTempEnglishWord();
                    this.updateEnglishPrediction();
                }

            }

            if (ic != null) {
                // ENGLISH_KB.md #0 / §2a: if the previous action auto-appended a space
                // after a suggestion pick, swap it with the typed punctuation per the
                // LatinIME character classes. Falls through to a normal commit otherwise.
                if (!commitEnglishPunctuationWithSwap(ic, (char) primaryCode, wasPickedAutoSpace)) {
                    ic.commitText(String.valueOf((char) primaryCode), 1);
                }
            }
        }

        if (!(!hasPhysicalKeyPressed && hasDistinctMultitouch))
            updateShiftKeyState(getCurrentInputEditorInfo());
    }

    static boolean isArraySymbolDigit(String activeIM, String composing, int code) {
        return LIME.IM_ARRAY.equals(activeIM)
                && composing.matches("(?:w|hg)[0-9]*")
                && code >= '0' && code <= '9';
    }

    /**
     * ENGLISH_KB.md #0 / §2a — replicate LatinIME's pick-space punctuation swap.
     *
     * <p>When an English suggestion pick auto-appended a trailing space ({@code word }),
     * typing punctuation should produce {@code word, } rather than {@code word ,}.
     * Returns {@code true} if this method fully handled committing the character (caller
     * must skip its own commit); {@code false} to let the caller commit normally.
     *
     * <p>Character classes mirror AOSP {@code donottranslate-config-spacing-and-punctuations.xml}:
     * <ul>
     *   <li>followed-by-space ({@code . , ; : ! ? ) ] }}): delete the space, commit {@code punct + " "}.</li>
     *   <li>preceded-by-space ({@code ( [ {{}}): keep the space, commit the bracket.</li>
     *   <li>strip ({@code - / @ _ '}): delete the space, commit the punctuation bare.</li>
     * </ul>
     */
    private boolean commitEnglishPunctuationWithSwap(InputConnection ic, char c,
                                                     boolean wasPickedAutoSpace) {
        if (!wasPickedAutoSpace) return false;

        final boolean followed = ENG_SWAP_FOLLOWED_BY_SPACE.indexOf(c) >= 0;
        final boolean preceded = ENG_SWAP_PRECEDED_BY_SPACE.indexOf(c) >= 0;
        final boolean strip    = ENG_SWAP_STRIP.indexOf(c) >= 0;
        if (!followed && !preceded && !strip) return false; // not a swap char (letters, &, etc.)

        // Preceded-by-space brackets keep the existing space; nothing special to do.
        if (preceded) return false;

        // followed-by-space / strip both need to remove the auto-space first — but only
        // if it is actually still there (cursor-move safety).
        final CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before == null || before.length() != 1 || before.charAt(0) != ' ') {
            return false; // no trailing space (e.g. user moved cursor) — commit normally
        }

        ic.beginBatchEdit();
        try {
            ic.deleteSurroundingText(1, 0);
            if (followed) {
                ic.commitText(c + " ", 1); // word,  (space moves after the punctuation)
            } else {
                ic.commitText(String.valueOf(c), 1); // word-  (no space)
            }
        } finally {
            ic.endBatchEdit();
        }
        return true;
    }

    /**
     * SPLIT_ONE_HAND_KB: apply a keyboard geometry change (one-hand / split / numpad
     * anchor) by rebuilding the current keyboard in place, keeping the IME shown.
     */
    private void applyGeometryChangeInPlace() {
        finishComposing();
        if (mInputView != null) {
            mInputView.closing(); // dismiss key previews / mini keyboards, not the IME
        }
        if (mKeyboardSwitcher != null) {
            mKeyboardSwitcher.rebuildCurrentKeyboard();
        }
        updateCandidateViewWidthConstraint(); // re-align strip with new anchored key block
    }

    private void handleClose() {
        if (DEBUG) Log.i(TAG, "handleClose()");
        // cancel candidate view if it's shown

        //Jeremy '12,4,23 need to check here.
        finishComposing();

        requestHideSelf(0);
        if (mInputView != null) {
            mInputView.closing();
        }
    }

    private void checkToggleCapsLock() {

        if (mInputView.getKeyboard().isShifted()) {
            toggleCapsLock();
        }

    }

    private void toggleCapsLock() {
        mCapsLock = !mCapsLock;
        if (mKeyboardSwitcher.isAlphabetMode()) {
            ((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(mCapsLock);
        } else {
            if (mCapsLock) {
                if (DEBUG) {
                    Log.i(TAG, "toggleCapsLock():mCapsLock:true");
                }
                if (!mKeyboardSwitcher.isShifted())
                    mKeyboardSwitcher.toggleShift();
                ((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(true);
            } else {
                if (DEBUG) {
                    Log.i(TAG, "toggleCapsLock():mCapsLock:false");
                }
                ((LIMEKeyboard) mInputView.getKeyboard()).setShiftLocked(false);
                if (mKeyboardSwitcher.isShifted())
                    mKeyboardSwitcher.toggleShift();


            }
        }
    }

    /*
        public boolean isWordSeparator(int code) {
            //Jeremy '11,5,31
            String separators = getResources().getString(R.string.word_separators);
            return separators.contains(String.valueOf((char) code));

        }
    */
//Jeremy '12,5,11 add return value from mCandidate.takeselectedsuggestion()
    public boolean pickHighlightedCandidate() {
        return mCandidateView != null && mCandidateView.takeSelectedSuggestion();
    }

    public void requestFullRecords(boolean isRelatedPhrase) {
        if (DEBUG)
            Log.i(TAG, "requestFullRecords()");

        if (isRelatedPhrase)
            this.updateRelatedPhrase(true);
        else
            this.updateCandidates(true);

    }

    public void pickCandidateManually(int index) {
        if (DEBUG)
            Log.i(TAG, "pickCandidateManually():"
                    + "Pick up candidate at index : " + index);

        // This is to prevent if user select the index more than the list
        if (mCandidateList != null && index >= mCandidateList.size()) {
            return;
        }


        if (mCandidateList != null && !mCandidateList.isEmpty()) {
            selectedCandidate = mCandidateList.get(index);
            //selectedIndex = index;
        }

        if (mEmojiKeyboardShown && mEmojiSearchMode
                && selectedCandidate != null && selectedCandidate.isEmojiRecord()) {
            commitEmoji(selectedCandidate.getWord());
            return;
        }
        if (appendPickedCandidateToEmojiSearch(selectedCandidate)) {
            return;
        }

        InputConnection ic = getCurrentInputConnection();

        if (mCompletionOn && mCompletions != null && index >= 0
                && selectedCandidate.isPartialMatchToCodeRecord()
                && index < mCompletions.length) {  // user picked the completion suggestion item.
            CompletionInfo ci = mCompletions[index];
            if (ic != null) ic.commitCompletion(ci);
            if (DEBUG)
                Log.i(TAG, "pickSuggestionManually():mCompletionOn:" + mCompletionOn);

        } else if ((mComposing.length() > 0 || (selectedCandidate != null && !selectedCandidate.isComposingCodeRecord()))
                && !mEnglishOnly) {  // user picked candidates from composing candidate or related phrase candidates
            //Jeremy '12,4,29 use mEnglishOnly instead of onIM
            commitTyped(ic);
        } else if (mLIMEPref.getEnglishPrediction() && tempEnglishList != null
                && !tempEnglishList.isEmpty()
                && index < tempEnglishList.size()) {  // user picked English prediction suggestions


            //Log.i("EMOJI-commit-index:", index + "");
            //Log.i("EMOJI-commit:", tempEnglishList.size() + "");

            if (this.tempEnglishList.get(index).isEmojiRecord()) {
                if (ic != null) ic.commitText(
                        this.tempEnglishList.get(index).getWord() + " ", 1);
                if (SearchSrv != null) {
                    SearchSrv.recordEmojiUsage(this.tempEnglishList.get(index).getWord());
                    mEmojiCategoryPages = null;
                }
            } else {
                String pickedWord = this.tempEnglishList.get(index).getWord();
                // tempEnglishList is built async (queryThread) and can lag tempEnglishWord
                // by a keystroke — a stale candidate may be shorter than the current prefix
                // (Vitals: StringIndexOutOfBounds length=9 index=10). Clamp: worst case
                // commits just the trailing space, next keystroke refreshes the list.
                int typedPrefixLen = Math.min(tempEnglishWord.length(), pickedWord.length());
                if (ic != null) ic.commitText(
                        pickedWord.substring(typedPrefixLen) + " ", 1);
                // ENG_AUTO_COMPLETION.md "Learning": increment the picked word's score so
                // frequently chosen words rank higher (mirrors the emoji recordEmojiUsage hook).
                if (SearchSrv != null) SearchSrv.recordEnglishUsage(pickedWord);
            }

            // ENGLISH_KB.md #0 / §2a: both pick paths appended a trailing space.
            // Arm the punctuation swap so the next "," produces "word," not "word ,".
            mPickedAutoSpace = true;

            resetTempEnglishWord();

            clearSuggestions();

        }

        if (currentSoftKeyboard.contains("wb")) {
            if (ic != null && mPredictionOn) ic.setComposingText("", 0);
        }

    }


    public void swipeRight() {
        //if (mCompletionOn) {
        pickHighlightedCandidate();
        //}
    }

    public void swipeLeft() {
        handleBackspace();
    }

    @Override
    public void moveCaretBy(int steps) {
        if (steps == 0 || mComposing == null || mComposing.length() > 0) {
            return;
        }

        final int keyCode = steps < 0
                ? KeyEvent.KEYCODE_DPAD_LEFT
                : KeyEvent.KEYCODE_DPAD_RIGHT;
        final int count = Math.abs(steps);
        for (int i = 0; i < count; i++) {
            keyDownUp(keyCode, false);
        }
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
        handleOptions();
    }

    /**
     * First method to call after key press
     */
    public void onPress(int primaryCode) {
        //Log.i(TAG, "onPress(): code = " + primaryCode + ", hasVibration = " + hasVibration + ", mVibrator = " + (mVibrator != null ? "valid" : "null"));
        
        // Record key press time (press down)
        //keyPressTime = System.currentTimeMillis();
        // To identify the source of character (Software keyboard or physical keyboard)
        // onPress() is only called from soft keyboard, so reset hasPhysicalKeyPressed
        hasPhysicalKeyPressed = false;

        if (hasDistinctMultitouch && primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT) {
            hasShiftPress = true;
            hasShiftCombineKeyPressed = false;
            handleShift();
        } else if (hasDistinctMultitouch && hasShiftPress) {
            hasShiftCombineKeyPressed = true;
        }
        doVibrateSound(primaryCode);


    }

    /**
     * Get Vibrator instance compatible with all API levels.
     * Uses VibratorManager for all API levels (recommended approach).
     */
    @SuppressWarnings("deprecation")
    private Vibrator getVibrator() {
        if (mVibrator == null) {
            Log.w(TAG, "getVibrator() - mVibrator is null, re-initializing, API level: " + android.os.Build.VERSION.SDK_INT);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // API 31+: use VibratorManager
                android.os.VibratorManager vibratorManager = (android.os.VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    mVibrator = vibratorManager.getDefaultVibrator();
                }
            } else {
                // API 22-30: use deprecated VIBRATOR_SERVICE
                mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            Log.i(TAG, "getVibrator() - mVibrator = " + (mVibrator != null ? "valid" : "null"));
        }
        return mVibrator;
    }

    /**
     * Vibrate with specified duration, compatible with all API levels.
     * Google/Pixel API 31+ with an input view uses system keyboard-tap haptics.
     * Samsung and unknown OEMs stay on raw timed pulses because Samsung One UI can report
     * performHapticFeedback(KEYBOARD_TAP) success while the HAL drops the predefined effect.
     * Raw pulse path:
     * API 33+:   uses VibrationEffect.createOneShot() with VibrationAttributes(USAGE_TOUCH).
     * API 26-32: uses VibrationEffect.createOneShot() without VibrationAttributes.
     * API <26:   uses deprecated vibrate(long).
     */
    @SuppressWarnings("deprecation")
    private void vibrate(long duration) {
        if (duration <= 0) {
            Log.w(TAG, "vibrate() called with invalid duration: " + duration);
            return;
        }

        if (KeypressHapticPolicy.shouldUseSystemKeyboardTapHaptic() && mInputView != null) {
            int flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                flags |= HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING;
            }
            mInputView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, flags);
            return;
        }

        Vibrator vibrator = getVibrator();
        if (vibrator == null) {
            Log.e(TAG, "vibrate() - vibrator is null! Failed to get vibrator service.");
            return;
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                android.os.VibrationEffect effect = android.os.VibrationEffect.createOneShot(
                        duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE);
                android.os.VibrationAttributes attributes = new android.os.VibrationAttributes.Builder()
                        .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
                        .build();
                vibrator.vibrate(effect, attributes);
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // API 26-32: createOneShot without VibrationAttributes (attributes overload is
                // API 33+ only). Predefined effects can be unsupported on some devices, so a raw
                // timed amplitude pulse is the reliable choice here too.
                android.os.VibrationEffect effect = android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE);
                vibrator.vibrate(effect);
            } else {
                // API < 26
                vibrator.vibrate(duration);
            }
        } catch (Exception e) {
            Log.e(TAG, "vibrate() failed to trigger vibration: " + e.getMessage(), e);
        }
    }

    public void doVibrateSound(int primaryCode) {
        //Log.i(TAG, "doVibrateSound() called with primaryCode: " + primaryCode + ", hasVibration: " + hasVibration);

        if (hasVibration) {
            //Jeremy '11,9,1 add preference on vibrate level
            long vibrateLevel = mapKeypressVibrateDuration(mLIMEPref.getVibrateLevel());
            //Log.i(TAG, "doVibrateSound() - hasVibration=true, vibrateLevel: " + vibrateLevel + "ms");
            vibrate(vibrateLevel);
        }
        
        if (hasSound && mAudioManager != null) {
            int sound = AudioManager.FX_KEYPRESS_STANDARD;
            switch (primaryCode) {
                case LIMEBaseKeyboard.KEYCODE_DELETE:
                    sound = AudioManager.FX_KEYPRESS_DELETE;
                    break;
                case MY_KEYCODE_ENTER:
                    sound = AudioManager.FX_KEYPRESS_RETURN;
                    break;
                case MY_KEYCODE_SPACE:
                    sound = AudioManager.FX_KEYPRESS_SPACEBAR;
                    break;
            }
            float soundVolume = mLIMEPref.getKeypressSoundVolume();
            if (soundVolume < 0) {
                mAudioManager.playSoundEffect(sound);
            } else {
                mAudioManager.playSoundEffect(sound, soundVolume);
            }
            //Log.i(TAG, "doVibrateSound() - sound played, sound code: " + sound);
        }
    }

    static long mapKeypressVibrateDuration(long preferenceValue) {
        if (preferenceValue <= 20) {
            return 30;
        }
        if (preferenceValue <= 30) {
            return 40;
        }
        if (preferenceValue <= 40) {
            return 50;
        }
        if (preferenceValue <= 50) {
            return 60;
        }
        return 70;
    }

    /**
     * Last method to execute when key release
     */
    public void onRelease(int primaryCode) {
        if (DEBUG)
            Log.i(TAG, "onRelease(): code = " + primaryCode);
        if (hasDistinctMultitouch && primaryCode == LIMEBaseKeyboard.KEYCODE_SHIFT) {
            hasShiftPress = false;
            if (hasShiftCombineKeyPressed) {
                hasShiftCombineKeyPressed = false;
                updateShiftKeyState(getCurrentInputEditorInfo());
            }
        } else if (hasDistinctMultitouch && !hasShiftPress) {
            updateShiftKeyState(getCurrentInputEditorInfo());

        }
    }
/*
    public boolean isValidTime(Date target) {
        Calendar srcCal = Calendar.getInstance();
        srcCal.setTime(new Date());
        Calendar destCal = Calendar.getInstance();
        destCal.setTime(target);

        return srcCal.getTimeInMillis() - destCal.getTimeInMillis() < 1800000;

    }
*/

    @Override
    public void onDestroy() {
        if (DEBUG)
            Log.i(TAG, "onDestroy()");

        // Stop monitoring IME changes when service is destroyed
        stopMonitoringIMEChanges();
        if (mDictationController != null) {
            mDictationController.destroy();
            mDictationController = null;
        }

        //jeremy 12,4,21 need to check again---
        //clearComposing(true); see no need to do this '12,4,21
        super.onDestroy();

    }

    /*
    @Override
    public void onUpdateCursor(Rect newCursor) {
        if(DEBUG)
            Log.i(TAG, "onUpdateCursor(): Top:"
                + newCursor.top + ". Right:" + newCursor.right
                + ". bottom:" + newCursor.bottom + ". left:" + newCursor.left );


                if (mCandidateView != null) {
                    // copy into a concrete list of Mapping so the rest of the code can mutate/inspect safely
                    mCandidateList = new LinkedList<>(suggestions == null ? Collections.emptyList() : suggestions);
                    try {
                        if (mCandidateList.size() > 1 && mCandidateList.get(1).isExactMatchToCodeRecord()) {
                            selectedCandidate = mCandidateList.get(1);
                        } else if (!mCandidateList.isEmpty()) {
                            selectedCandidate = mCandidateList.get(0);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in suggestion processing", e);
                    }
                    mCandidateView.setSuggestions(mCandidateList, showNumber, diplaySelkey);
        if (mInputView == null) return;
        if (DEBUG)
            Log.i(TAG, "updateInputViewShown(): mInputView.isShown(): " + mInputView.isShown());
        super.updateInputViewShown();
    
        // Don't restore keyboard view here - only restore when user explicitly touches
        // the soft keyboard area (candidate view or InputView container)
        // This prevents restoring when InputView visibility changes but user is still using physical keyboard
    
        if (!mInputView.isShown() && !hasPhysicalKeyPressed)
            hideCandidateView();
    }


    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (DEBUG)
            Log.i(TAG, "onFinishInputView()");
        super.onFinishInputView(finishingInput);
        cancelInlineDictation();
        resetEmojiKeyboardState();
        hideCandidateView(); //Jeremy '12,5,7 hideCandiate when inputview is closed but not yet leave the original field (onfinishinput() will not called).
    }

    /**
     *  start voice input
     *  Prefer switching to a voice IME. RecognizerIntent is only the fallback.
     */
    public void startVoiceInput() {
        if (DEBUG)
            Log.i(TAG, "startVoiceInput(): API level: " + android.os.Build.VERSION.SDK_INT);

        Intent voiceIntent = getVoiceIntent();
        String voiceID = LIMEUtilities.isVoiceSearchServiceExist(getBaseContext());
        boolean recognizerAvailable = true;
        if (!isRecognizerFallbackAvailable(voiceIntent)) {
            Log.w(TAG, "startVoiceInput(): recognizer fallback was not visible during preflight; will still try helper activity if delegated VoiceIME is unavailable");
        }
        VoiceInputRoute route = LIMEVoiceInputRouter.chooseRoute(
                isInlineDictationFeatureEnabled(),
                VoiceInputMode.AUTO,
                getInlineDictationPermissionState(),
                isInlineDictationAvailable(),
                voiceID != null,
                recognizerAvailable);
        Log.i(TAG, "startVoiceInput(): voiceID=" + voiceID
                + ", activeIM=" + activeIM
                + ", route=" + route
                + ", fallbackLanguage=" + voiceIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));

        switch (route) {
            case INLINE_DICTATION:
                startInlineDictationOrFallback(voiceIntent, voiceID);
                return;
            case VOICE_IME:
                startDelegatedVoiceInput(voiceIntent, voiceID);
                return;
            case RECOGNIZER_INTENT:
                startRecognizerFallback(voiceIntent);
                return;
            case UNAVAILABLE:
            default:
                showLimeToast("Voice recognition not available on this device");
        }
    }

    private boolean isInlineDictationFeatureEnabled() {
        try {
            return getResources().getBoolean(R.bool.inline_dictation_feature_enabled);
        } catch (Exception e) {
            Log.w(TAG, "isInlineDictationFeatureEnabled(): resource unavailable: " + e.getMessage());
            return false;
        }
    }

    private boolean isInlineDictationAvailable() {
        return mDictationController != null && mDictationController.isRecognitionAvailable();
    }

    private VoicePermissionState getInlineDictationPermissionState() {
        if (VoicePermissionHelper.hasRecordAudioPermission(this)) {
            return VoicePermissionState.GRANTED;
        }
        return VoicePermissionHelper.wasRecordAudioPermissionPrompted(this)
                ? VoicePermissionState.DENIED_DO_NOT_ASK_AGAIN
                : VoicePermissionState.NOT_REQUESTED;
    }

    private void startInlineDictationOrFallback(Intent voiceIntent, String voiceID) {
        if (mDictationController != null && mDictationController.isRecognitionAvailable()) {
            mIsVoiceInputActive = true;
            mDictationController.start(getVoiceRecognitionLanguageTag());
            return;
        }
        if (DEBUG)
            Log.i(TAG, "startInlineDictationOrFallback(): inline controller unavailable, using delegated fallback");
        startDelegatedVoiceInputOrRecognizerFallback(voiceIntent, voiceID);
    }

    private void startDelegatedVoiceInputOrRecognizerFallback(Intent voiceIntent, String voiceID) {
        if (voiceID != null) {
            startDelegatedVoiceInput(voiceIntent, voiceID);
        } else {
            startRecognizerFallback(voiceIntent);
        }
    }

    private void startDelegatedVoiceInput(Intent voiceIntent, String voiceID) {
        if (voiceID == null) {
            startRecognizerFallback(voiceIntent);
            return;
        }
        if (isGoogleSpeechServicesVoiceIme(voiceID)) {
            Log.w(TAG, "startDelegatedVoiceInput(): Google Speech Services VoiceIME cannot be direct-switched safely; using RecognizerIntent");
            startRecognizerFallback(voiceIntent);
            return;
        }
        if (DEBUG)
            Log.i(TAG, "startDelegatedVoiceInput(): Found voice IME: " + voiceID);

        // Get LIME IME ID for switching back
        if (mLIMEId == null) {
            mLIMEId = LIMEUtilities.getLIMEID(getBaseContext());
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            startMonitoringIMEChanges();
            try {
                mIsVoiceInputActive = true;
                this.switchInputMethod(voiceID);
                if (DEBUG)
                    Log.i(TAG, "startDelegatedVoiceInput(): Called switchInputMethod(" + voiceID + ")");

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    String currentIME = getCurrentDefaultInputMethod();
                    if (DEBUG)
                        Log.i(TAG, "startDelegatedVoiceInput(): Current IME after switch: " + currentIME + " (expected: " + voiceID + ")");

                    if (voiceID.equals(currentIME)) {
                        if (DEBUG)
                            Log.i(TAG, "startDelegatedVoiceInput(): Successfully switched to voice IME");
                        scheduleModernVoiceImeRecovery(voiceID, voiceIntent);
                    } else {
                        if (DEBUG)
                            Log.w(TAG, "startDelegatedVoiceInput(): switchInputMethod() didn't work (still on " + currentIME + "), falling back to RecognizerIntent");
                        stopMonitoringIMEChanges();
                        mIsVoiceInputActive = false;
                        startRecognizerFallback(voiceIntent);
                    }
                }, 200);

                return;
            } catch (SecurityException e) {
                if (DEBUG)
                    Log.e(TAG, "startDelegatedVoiceInput(): SecurityException switching to voice IME: " + e.getMessage(), e);
                stopMonitoringIMEChanges();
                mIsVoiceInputActive = false;
            } catch (Exception e) {
                if (DEBUG)
                    Log.e(TAG, "startDelegatedVoiceInput(): Exception switching to voice IME: " + e.getMessage(), e);
                stopMonitoringIMEChanges();
                mIsVoiceInputActive = false;
            }
        } else if (DEBUG) {
            Log.e(TAG, "startDelegatedVoiceInput(): InputMethodManager is null");
        }
        startRecognizerFallback(voiceIntent);
    }

    private void scheduleModernVoiceImeRecovery(String voiceID, Intent voiceIntent) {
        if (!isGoogleSpeechServicesVoiceIme(voiceID)) {
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!mIsVoiceInputActive) {
                return;
            }
            String currentIME = getCurrentDefaultInputMethod();
            if (voiceID.equals(currentIME)) {
                Log.w(TAG, "scheduleModernVoiceImeRecovery(): Google Speech Services VoiceIME did not auto-start; switching back to LIME and using RecognizerIntent");
                switchBackToLIME();
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> startRecognizerFallback(voiceIntent), 300);
            }
        }, 1500);
    }

    private boolean isGoogleSpeechServicesVoiceIme(String voiceID) {
        return "com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService"
                .equals(voiceID);
    }

    private void startRecognizerFallback(Intent voiceIntent) {
        try {
            launchRecognizerIntent(voiceIntent);
            if (DEBUG)
                Log.i(TAG, "startRecognizerFallback(): launchRecognizerIntent() returned successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error launching recognizer intent", e);
        }
    }

    private boolean isRecognizerFallbackAvailable(Intent voiceIntent) {
        try {
            Intent intent = voiceIntent != null ? voiceIntent : getVoiceIntent();
            return getPackageManager() != null &&
                    !getPackageManager().queryIntentActivities(intent, 0).isEmpty();
        } catch (Exception e) {
            Log.w(TAG, "isRecognizerFallbackAvailable(): unable to query recognizer: " + e.getMessage());
            return true;
        }
    }

    private Intent getVoiceIntent() {
        Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        String languageTag = getVoiceRecognitionLanguageTag();
        voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag);
        Log.i(TAG, "getVoiceIntent() - Using voice recognition language: " + languageTag);

        // Add prompt text
        voiceIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");

        // Ensure we get results back
        voiceIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        return voiceIntent;
    }

    private String getVoiceRecognitionLanguageTag() {
        Locale systemLocale = null;
        try {
            systemLocale = ConfigurationCompat.getLocales(getResources().getConfiguration()).get(0);
        } catch (Exception e) {
            // getResources() or getConfiguration() may throw in test env
        }
        return resolveVoiceRecognitionLanguageTag(systemLocale);
    }

    static String resolveVoiceRecognitionLanguageTag(Locale locale) {
        if (locale == null) {
            return "zh-TW";
        }
        String language = locale.getLanguage();
        String country = locale.getCountry();
        if (!"zh".equalsIgnoreCase(language)) {
            return "zh-TW";
        }
        if ("TW".equalsIgnoreCase(country)) {
            return "zh-TW";
        }
        if ("HK".equalsIgnoreCase(country) || "MO".equalsIgnoreCase(country)) {
            return "zh-HK";
        }
        return "zh-TW";
    }

    /**
     * Launch RecognizerIntent as fallback for voice input
     */
    private void launchRecognizerIntent(Intent voiceIntent) {
        if (voiceIntent == null) {
            Log.e(TAG, "launchRecognizerIntent(): voiceIntent is NULL! Creating default intent");
            voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getVoiceRecognitionLanguageTag());
            //voiceIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");
            voiceIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        }
        
        String language = voiceIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        Log.i(TAG, "launchRecognizerIntent(): Intent language: " + language + ", Intent action: " + voiceIntent.getAction() +
                ", API level: " + android.os.Build.VERSION.SDK_INT);

        // Use helper Activity to launch RecognizerIntent for all API levels
        // InputMethodService cannot receive onActivityResult, so we need VoiceInputActivity
        // to handle the result and broadcast it back to LIMEService

        try {
            Intent helperIntent = new Intent(this, VoiceInputActivity.class);
            helperIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            helperIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            // Pass the configured voiceIntent to VoiceInputActivity
            helperIntent.putExtra(VoiceInputActivity.EXTRA_VOICE_INTENT, voiceIntent);
            Log.i(TAG, "launchRecognizerIntent(): Passing voiceIntent to VoiceInputActivity with language: " + language);
            startActivity(helperIntent);
            mIsVoiceInputActive = true;

        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "launchRecognizerIntent(): VoiceInputActivity not found: " + e.getMessage(), e);
            showLimeToast("Voice input activity not found");
        } catch (SecurityException e) {
            Log.e(TAG, "launchRecognizerIntent(): SecurityException launching VoiceInputActivity: " + e.getMessage(), e);
            showLimeToast("Cannot launch voice input (security restriction)");
        } catch (Exception e) {
            Log.e(TAG, "launchRecognizerIntent(): Failed to launch VoiceInputActivity: " + e.getMessage(), e);
            showLimeToast("Voice input unavailable: " + e.getMessage());
        }
    }

    private String getCurrentDefaultInputMethod() {
        try {
            return Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD
            );
        } catch (Exception e) {
            Log.w(TAG, "getCurrentDefaultInputMethod(): Unable to read default IME: " + e.getMessage());
            return null;
        }
    }




    /**
     * Start monitoring IME changes to switch back to LIME when voice input ends
     */
    private void startMonitoringIMEChanges() {
        if (mInputMethodObserver != null) {
            return; // Already monitoring
        }

        mInputMethodObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                if (!mIsVoiceInputActive) {
                    return;
                }

                String currentIME = Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.DEFAULT_INPUT_METHOD
                );

                if (DEBUG)
                    Log.d(TAG, "IME changed to: " + currentIME + ", LIME ID: " + mLIMEId);

                // If we're back on LIME, stop monitoring
                if (mLIMEId != null && mLIMEId.equals(currentIME)) {
                    stopMonitoringIMEChanges();
                    return;
                }

                // Check if it's a voice IME - if so, wait
                String voiceID = LIMEUtilities.isVoiceSearchServiceExist(getBaseContext());
                if (voiceID != null && voiceID.equals(currentIME)) {
                    // Still on voice IME, wait
                    return;
                }

                // IME changed to something else (not voice, not LIME), switch back to LIME
                // This handles the case where voice recognition ends and IME might have changed
                if (mLIMEId != null && !mLIMEId.equals(currentIME)) {
                    // Delay slightly to allow voice recognition to complete
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        String checkIME = Settings.Secure.getString(
                                getContentResolver(),
                                Settings.Secure.DEFAULT_INPUT_METHOD
                        );
                        if (mLIMEId != null && !mLIMEId.equals(checkIME)) {
                            switchBackToLIME();
                        }
                    }, 500); // Delay to allow voice recognition to complete
                }
            }
        };

        // Register observer
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
                false,
                mInputMethodObserver
        );

        // Also set up a timeout handler to switch back after a reasonable time
        // This handles cases where IME doesn't change but voice recognition completes
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (mIsVoiceInputActive) {
                String currentIME = Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.DEFAULT_INPUT_METHOD
                );
                if (mLIMEId != null && !mLIMEId.equals(currentIME)) {
                    switchBackToLIME();
                } else {
                    // Already back on LIME, just stop monitoring
                    stopMonitoringIMEChanges();
                }
            }
        }, 30000); // 30 second timeout

        if (DEBUG)
            Log.i(TAG, "startMonitoringIMEChanges(): Started monitoring IME changes");
    }

    /**
     * Stop monitoring IME changes
     */
    private void stopMonitoringIMEChanges() {
        if (mInputMethodObserver != null) {
            getContentResolver().unregisterContentObserver(mInputMethodObserver);
            mInputMethodObserver = null;
            mIsVoiceInputActive = false;
            if (DEBUG)
                Log.i(TAG, "stopMonitoringIMEChanges(): Stopped monitoring IME changes");
        }
    }

    /**
     * Switch back to LIME IME
     */
    private void switchBackToLIME() {
        if (mLIMEId == null) {
            mLIMEId = LIMEUtilities.getLIMEID(getBaseContext());
        }
        if (mLIMEId == null) {
            if (DEBUG)
                Log.e(TAG, "switchBackToLIME(): LIME ID is null");
            stopMonitoringIMEChanges();
            return;
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) {
            if (DEBUG)
                Log.e(TAG, "switchBackToLIME(): InputMethodManager is null");
            stopMonitoringIMEChanges();
            return;
        }

        // Try to switch back to LIME using InputMethodService.switchInputMethod()
        // This is the recommended method for IMEs and works on all API levels (21-36)
        // setInputMethod() is deprecated on API 28+ and doesn't work on API 36
        try {
            this.switchInputMethod(mLIMEId);
            if (DEBUG)
                Log.i(TAG, "switchBackToLIME(): Switched back to LIME IME using switchInputMethod()");
        } catch (Exception e) {
            if (DEBUG)
                Log.e(TAG, "switchBackToLIME(): Failed to switch back: " + e);
        }

        // Stop monitoring after switching back
        stopMonitoringIMEChanges();
    }

    /**
     * Try to commit voice text, retrying up to 3 times with 200ms delays if InputConnection is null.
     * Falls back to storing as pending text for onStartInputView() if all retries fail.
     */
    private void commitVoiceTextWithRetry(String text, int attempt) {
        InputConnection ic = getCurrentInputConnection();
        String textToCommit = prepareVoiceTextForCommit(text);
        if (ic != null) {
            try {
                ic.commitText(textToCommit, 1);
                Log.i(TAG, "commitVoiceTextWithRetry(): Committed voice text on attempt " + attempt);
            } catch (Exception e) {
                Log.e(TAG, "commitVoiceTextWithRetry(): Failed to commit: " + e.getMessage());
                mPendingVoiceText = textToCommit;
            }
        } else if (attempt < 3) {
            Log.w(TAG, "commitVoiceTextWithRetry(): IC null, retry " + (attempt + 1) + " in 200ms");
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> commitVoiceTextWithRetry(text, attempt + 1), 200);
            return; // Don't clear mIsVoiceInputActive yet
        } else {
            Log.w(TAG, "commitVoiceTextWithRetry(): IC still null after 3 retries, storing as pending");
            mPendingVoiceText = textToCommit;
        }
        mIsVoiceInputActive = false;
    }

    private String prepareVoiceTextForCommit(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            if (mLIMEPref != null && mLIMEPref.getHanCovertOption() != 0 && SearchSrv != null) {
                String converted = SearchSrv.hanConvert(text);
                Log.i(TAG, "prepareVoiceTextForCommit(): Applied Han conversion to voice result");
                return converted;
            }
        } catch (Exception e) {
            Log.w(TAG, "prepareVoiceTextForCommit(): Han conversion skipped: " + e.getMessage());
        }
        return text;
    }

    @Override
    public void onDictationStateChanged(DictationState state) {
        if (DEBUG) {
            Log.i(TAG, "onDictationStateChanged(): " + state);
        }
        showDictationStatus(state, null);
    }

    @Override
    public void onDictationPartialText(String text) {
        if (DEBUG) {
            Log.i(TAG, "onDictationPartialText(): " + text);
        }
        showDictationStatus(DictationState.PARTIAL, text);
    }

    @Override
    public void onDictationFinalText(String text) {
        finishInlineDictation();
        if (text != null && !text.isEmpty()) {
            commitVoiceTextWithRetry(text, 0);
        }
    }

    @Override
    public void onDictationError(int errorCode, boolean shouldFallback) {
        Log.w(TAG, "onDictationError(): errorCode=" + errorCode + ", shouldFallback=" + shouldFallback);
        finishInlineDictation();
        if (mCandidateView != null) {
            mCandidateView.showDictationErrorTemporarily();
            showCandidateView();
            refreshCandidateInputContainer();
        }
        if (!shouldFallback) {
            return;
        }
        Intent voiceIntent = getVoiceIntent();
        String voiceID = LIMEUtilities.isVoiceSearchServiceExist(getBaseContext());
        startDelegatedVoiceInputOrRecognizerFallback(voiceIntent, voiceID);
    }

    @Override
    public void onDictationCancelled() {
        finishInlineDictation();
    }

    private void finishInlineDictation() {
        mIsVoiceInputActive = false;
        clearDictationStatus();
    }

    public void cancelInlineDictation() {
        if (mDictationController != null && mDictationController.isActive()) {
            mDictationController.cancel();
        }
    }

    private void showDictationStatus(DictationState state, String text) {
        if (mCandidateView != null) {
            mCandidateView.showDictationStatus(state, text);
            showCandidateView();
            refreshCandidateInputContainer();
        }
    }

    private void clearDictationStatus() {
        if (mCandidateView != null) {
            mCandidateView.clearDictationStatus();
            refreshCandidateInputContainer();
        }
    }

    /**
     * Register BroadcastReceiver to receive voice input results from VoiceInputActivity
     * Note: RECEIVER_NOT_EXPORTED flag is only available on API 33+, so we use conditional registration
     * Android 16+ may have delivery restrictions, so we handle null InputConnection by queuing the text
     */
    @SuppressLint({"UnspecifiedRegisterReceiverFlag", "RegisterReceiverFlag"})
    private void registerVoiceInputReceiver() {
        if (mVoiceInputReceiver != null) {
            return; // Already registered
        }

        mVoiceInputReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DEBUG) {
                    Log.i(TAG, "registerVoiceInputReceiver().onReceive(): Action: " + intent.getAction());
                }
                
                if (ACTION_VOICE_RESULT.equals(intent.getAction())) {
                    String recognizedText = intent.getStringExtra(EXTRA_RECOGNIZED_TEXT);
                    if (DEBUG) {
                        Log.i(TAG, "registerVoiceInputReceiver().onReceive(): Recognized text: " + recognizedText);
                    }
                    
                    if (recognizedText != null && !recognizedText.isEmpty()) {
                        // Clear static field since we received it via broadcast
                        VoiceInputActivity.consumePendingVoiceText();
                        Log.i(TAG, "registerVoiceInputReceiver().onReceive(): Processing recognized text: " + recognizedText);

                        // Try to commit with retry logic
                        commitVoiceTextWithRetry(recognizedText, 0);
                    } else if (recognizedText == null) {
                        Log.w(TAG, "registerVoiceInputReceiver().onReceive(): Recognized text is null");
                        mIsVoiceInputActive = false;
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_VOICE_RESULT);
        // On API 33+ (TIRAMISU), must specify RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED
        // This receiver is for internal app communication only, so use RECEIVER_NOT_EXPORTED
        // For API < 33, the flag doesn't exist, so we register without it (lint warning suppressed above)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mVoiceInputReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                // RECEIVER_NOT_EXPORTED flag is not available on API < 33
                // This is safe because the broadcast is internal to the app only
                // Lint warning suppressed at method level with @SuppressLint
                // noinspection UnspecifiedRegisterReceiverFlag
                registerReceiver(mVoiceInputReceiver, filter);
            }
            Log.i(TAG, "registerVoiceInputReceiver(): Registered receiver successfully on API " + Build.VERSION.SDK_INT);
        } catch (Exception e) {
            Log.e(TAG, "registerVoiceInputReceiver(): Failed to register receiver: " + e.getMessage());
            mVoiceInputReceiver = null;
        }
    }

    /**
     * Unregister BroadcastReceiver for voice input results
     */
    private void unregisterVoiceInputReceiver() {
        if (mVoiceInputReceiver != null) {
            try {
                unregisterReceiver(mVoiceInputReceiver);
                mVoiceInputReceiver = null;
                Log.i(TAG, "unregisterVoiceInputReceiver(): Unregistered receiver");
            } catch (Exception e) {
                Log.w(TAG, "unregisterVoiceInputReceiver(): Failed to unregister: " + e.getMessage());
            }
        }
    }

    private static class KeyboardTheme {
        final String mName;
        final int mThemeId;
        final int mStyleId;

        KeyboardTheme(String name, int themeId, int styleId) {
            mName = name;
            mThemeId = themeId;
            mStyleId = styleId;
        }
    }

    private static final KeyboardTheme[] KEYBOARD_THEMES = {
            new KeyboardTheme("Light", 0, R.style.LIMETheme_Light),
            new KeyboardTheme("Dark", 1, R.style.LIMETheme_Dark),
            new KeyboardTheme("Pink", 2, R.style.LIMETheme_Pink),
            new KeyboardTheme("TechBlue", 3, R.style.LIMETheme_TechBlue),
            new KeyboardTheme("FashionPurple", 4, R.style.LIMETheme_FashionPurple),
            new KeyboardTheme("RelaxGreen", 5, R.style.LIMETheme_RelaxGreen),
    };

    private int mKeyboardThemeIndex = -1;

    private AlertDialog.Builder createDialogBuilder() {
        // Style the keyboard long-press menu to match the in-app preference page
        // (rounded corners, accent-coloured title/buttons, day/night) via a
        // custom dialog theme, instead of the plain framework dialog. Kept on
        // android.app.AlertDialog so the IME window-token plumbing is unchanged.
        android.content.Context base = new ContextThemeWrapper(this, R.style.LIME_KeyboardMenuDialog);
        // Apply Material You dynamic colours so the menu accent matches the
        // system palette, like the preference page.
        android.content.Context themed = com.google.android.material.color.DynamicColors
                .wrapContextIfAvailable(base,
                        org.limeime.global.SystemAccentColor.dynamicColorOptions(this));
        return new AlertDialog.Builder(themed);
    }

    private boolean isEffectiveDarkTheme() {
        if (mKeyboardThemeIndex == 1) return true;
        if (mKeyboardThemeIndex == 6) {
            int uiMode = getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return uiMode == Configuration.UI_MODE_NIGHT_YES;
        }
        return false;
    }

    static final class EmojiPanelColors {
        final int searchBackground;
        final int searchHint;
        final int searchText;
        final int searchIcon;
        final int iconText;
        final int categoryHighlight;

        EmojiPanelColors(int searchBackground, int searchHint, int searchText,
                         int searchIcon, int iconText, int categoryHighlight) {
            this.searchBackground = searchBackground;
            this.searchHint = searchHint;
            this.searchText = searchText;
            this.searchIcon = searchIcon;
            this.iconText = iconText;
            this.categoryHighlight = categoryHighlight;
        }
    }

    static EmojiPanelColors emojiPanelColorsForTheme(int themeIndex, boolean systemDark) {
        return emojiPanelColorsForTheme(themeIndex, systemDark, 0);
    }

    static EmojiPanelColors emojiPanelColorsForTheme(int themeIndex, boolean systemDark, int systemAccent) {
        int resolvedTheme = themeIndex == 6 ? (systemDark ? 1 : 0) : themeIndex;
        int accentOverlay = isUsableAccentColor(systemAccent) ? withAlpha(systemAccent, 0x33) : 0;
        switch (resolvedTheme) {
            case 1:
                return new EmojiPanelColors(
                        0xFF212121,
                        0xFF8E9AA0,
                        0xFFCFD8DC,
                        0xFFCFD8DC,
                        0xFFCFD8DC,
                        themeIndex == 6 && accentOverlay != 0 ? accentOverlay : 0x33FFFFFF);
            case 2:
                return new EmojiPanelColors(
                        0xFFFEF3F7,
                        0xFFC74A72,
                        0xFF000000,
                        0xFFF49AC1,
                        0xFF000000,
                        0x33C74A72);
            case 3:
                return new EmojiPanelColors(
                        0xFFD8E7F3,
                        0xFF4E6677,
                        0xFF314453,
                        0xFF9BC5E4,
                        0xFF314453,
                        0x334167B0);
            case 4:
                return new EmojiPanelColors(
                        0xFFEFEDFF,
                        0xFF45196F,
                        0xFF45196F,
                        0xFFB28ABF,
                        0xFF45196F,
                        0x3345196F);
            case 5:
                return new EmojiPanelColors(
                        0xFFF2F5D5,
                        0xFF009444,
                        0xFF003A17,
                        0xFF39B54A,
                        0xFF003A17,
                        0x33006838);
            case 0:
            default:
                return new EmojiPanelColors(
                        0xF2FFFFFF,
                        0xFF8A8A8A,
                        0xFF000000,
                        0xFF000000,
                        0xFF000000,
                        themeIndex == 6 && accentOverlay != 0 ? accentOverlay : 0x22000000);
        }
    }

    private EmojiPanelColors currentEmojiPanelColors() {
        int fallbackAccent = isEffectiveDarkTheme() ? 0x33FFFFFF : 0x22000000;
        int accent = isFollowSystemTheme() ? resolveSystemAccentColor(fallbackAccent) : 0;
        return emojiPanelColorsForTheme(mKeyboardThemeIndex, isEffectiveDarkTheme(), accent);
    }

    private boolean isFollowSystemTheme() {
        return mKeyboardThemeIndex == 6;
    }

    private void applyFollowSystemAccentColors() {
        if (!isFollowSystemTheme()) return;

        int accent = resolveSystemAccentColor(0);
        if (!isUsableAccentColor(accent)) return;

        applyFollowSystemAccentColors(accent, isEffectiveDarkTheme());
    }

    private void applyFollowSystemAccentColors(int accent, boolean darkTheme) {
        if (!isUsableAccentColor(accent) || isAppliedFollowSystemAccentCurrent(accent, darkTheme)) return;

        if (mInputView != null) {
            mInputView.applyFollowSystemAccentColor(accent, darkTheme);
        }
        if (mCandidateViewInInputView != null) {
            mCandidateViewInInputView.applyFollowSystemAccentColor(accent, darkTheme);
        }
        if (mCandidateView != null && mCandidateView != mCandidateViewInInputView) {
            mCandidateView.applyFollowSystemAccentColor(accent, darkTheme);
        }
        mAppliedFollowSystemAccent = accent;
        mAppliedFollowSystemDarkTheme = darkTheme;
        mAppliedFollowSystemInputView = mInputView;
        mAppliedFollowSystemCandidateView = mCandidateView;
        mAppliedFollowSystemEmbeddedCandidateView = mCandidateViewInInputView;
    }

    private boolean isAppliedFollowSystemAccentCurrent(int accent, boolean darkTheme) {
        return mAppliedFollowSystemAccent == accent
                && mAppliedFollowSystemDarkTheme == darkTheme
                && mAppliedFollowSystemInputView == mInputView
                && mAppliedFollowSystemCandidateView == mCandidateView
                && mAppliedFollowSystemEmbeddedCandidateView == mCandidateViewInInputView;
    }

    private void clearAppliedFollowSystemAccentState() {
        mAppliedFollowSystemAccent = 0;
        mAppliedFollowSystemInputView = null;
        mAppliedFollowSystemCandidateView = null;
        mAppliedFollowSystemEmbeddedCandidateView = null;
    }

    void applyFollowSystemAccentColorsForTesting(int accent, boolean darkTheme) {
        applyFollowSystemAccentColors(accent, darkTheme);
    }

    private int resolveSystemAccentColor(int fallbackColor) {
        int systemSeed = SystemAccentColor.resolveSeedColor(this, 0);
        if (isUsableAccentColor(systemSeed)) {
            return systemSeed;
        }

        Context dynamicColorContext = DynamicColors.wrapContextIfAvailable(
                this,
                SystemAccentColor.dynamicColorOptions(this));
        int resolved = resolveThemeColor(dynamicColorContext, androidx.appcompat.R.attr.colorPrimary, 0);
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(dynamicColorContext, com.google.android.material.R.attr.colorSecondary, 0);
        }
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(dynamicColorContext, android.R.attr.colorAccent, 0);
        }
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(androidx.appcompat.R.attr.colorPrimary, 0);
        }
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(com.google.android.material.R.attr.colorSecondary, 0);
        }
        if (!isUsableAccentColor(resolved)) {
            resolved = resolveThemeColor(android.R.attr.colorAccent, 0);
        }
        return isUsableAccentColor(resolved) ? resolved : fallbackColor;
    }

    private int resolveThemeColor(int attr, int fallbackColor) {
        return resolveThemeColor(this, attr, fallbackColor);
    }

    private int resolveThemeColor(Context context, int attr, int fallbackColor) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return ContextCompat.getColor(context, value.resourceId);
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return fallbackColor;
    }

    private static boolean isUsableAccentColor(int color) {
        return Color.alpha(color) != 0;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int getKeyboardTheme() {
        int idx = mKeyboardThemeIndex;
        if (idx == 6) idx = isEffectiveDarkTheme() ? 1 : 0;
        if (idx < 0 || idx >= KEYBOARD_THEMES.length) return KEYBOARD_THEMES[0].mStyleId;
        return KEYBOARD_THEMES[idx].mStyleId;
    }

    /**
     * Issue #46: Tint the system navigation bar to match the active keyboard theme,
     * and pick light/dark nav-bar icons based on the background's luminance so the
     * icons remain visible. Called from onCreateInputView() and onStartInputView().
     */
    @SuppressWarnings("deprecation")
    private void applyNavigationBarTheme() {
        android.app.Dialog dialog = getWindow();
        if (dialog == null) return;
        android.view.Window window = dialog.getWindow();
        if (window == null) return;

        if (shouldForceImeEdgeToEdge(android.os.Build.VERSION.SDK_INT)) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        int bgColor = getKeyboardBackgroundColorForCurrentTheme();
        boolean lightBackground = isColorLight(bgColor);

        if (isAppliedNavigationBarThemeCurrent(window, bgColor, lightBackground)) {
            return;
        }

        // The IME container applies bottomInset padding to clear the gesture bar
        // (see onCreateInputView). That padded strip is transparent by default, so
        // the host app's nav bar shows through. Paint the container background
        // with the theme color so the strip visually matches the keyboard.
        if (mCandidateInInputView != null) {
            mCandidateInInputView.setBackgroundColor(bgColor);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(window, window.getDecorView());
            // setAppearanceLightNavigationBars(true) => DARK icons on LIGHT bar
            controller.setAppearanceLightNavigationBars(lightBackground);
        }
        mNavigationBarThemeApplied = true;
        mAppliedNavigationBarWindow = window;
        mAppliedNavigationBarCandidateView = mCandidateInInputView;
        mAppliedNavigationBarColor = bgColor;
        mAppliedNavigationBarLightBackground = lightBackground;
        // API 21-22 cannot toggle nav-bar icon brightness; the colored bar alone
        // still gives the user the matching look.
    }

    static boolean shouldForceImeEdgeToEdge(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }

    private boolean isAppliedNavigationBarThemeCurrent(android.view.Window window,
                                                       int bgColor,
                                                       boolean lightBackground) {
        return mNavigationBarThemeApplied
                && mAppliedNavigationBarWindow == window
                && mAppliedNavigationBarCandidateView == mCandidateInInputView
                && mAppliedNavigationBarColor == bgColor
                && mAppliedNavigationBarLightBackground == lightBackground;
    }

    private void clearAppliedNavigationBarThemeState() {
        mNavigationBarThemeApplied = false;
        mAppliedNavigationBarWindow = null;
        mAppliedNavigationBarCandidateView = null;
    }

    private int getKeyboardBackgroundColorForCurrentTheme() {
        int colorRes;
        switch (mKeyboardThemeIndex) {
            case 1:  colorRes = R.color.keyboard_background_dark;            break;
            case 2:  colorRes = R.color.keyboard_background_pink;            break;
            case 3:  colorRes = R.color.keyboard_background_tech_blue;       break;
            case 4:  colorRes = R.color.keyboard_background_fashion_purple;  break;
            case 5:  colorRes = R.color.keyboard_background_relax_green;     break;
            case 6:
                colorRes = isEffectiveDarkTheme()
                        ? R.color.keyboard_background_dark
                        : R.color.keyboard_background_light;
                break;
            case 0:
            default: colorRes = R.color.keyboard_background_light;           break;
        }
        return ContextCompat.getColor(this, colorRes);
    }

    private static boolean isColorLight(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >>  8) & 0xFF;
        int b =  color        & 0xFF;
        // Rec. 709 luma
        double luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        return luma >= 0.5;
    }

}
