package net.toload.main;

import android.content.Context;
import android.database.Cursor;
import android.provider.UserDictionary.Words;

public class MainDictionary extends ExpandableDictionary {
	
    private Context mContext;
    private ISearchService mSearchSrv;
	private LimeDB db = null;
	public final static String FIELD_id = "_id";
	public final static String FIELD_WORD = "word";
	public final static String FIELD_SCORE = "score";
    
	public MainDictionary(Context context) {
		super(context);
		mContext = context;	
		   loadDictionary();
	}
	public MainDictionary(Context context, ISearchService SearchSrv) {
		super(context);
		mContext = context;
		mSearchSrv = SearchSrv;
		   loadDictionary();
		
	}

	@Override
	public void getWords(WordComposer composer, WordCallback callback) {
        super.getWords(composer, callback);
		
	}

	@Override
	public boolean isValidWord(CharSequence word) {
		 return super.isValidWord(word);
	}
	
    private synchronized void loadDictionary() {
    	if(db == null){loadLimeDB();}
    	Cursor cursor = db.getDictionaryAll();
    	addWords(cursor);
    	
    }
    
    private void addWords(Cursor cursor) {
        clearDictionary();

        final int maxWordLength = getMaxWordLength();
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {   	
				int wordColumn = cursor.getColumnIndex(FIELD_WORD);
				int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
				String word = cursor.getString(wordColumn);
                int frequency = cursor.getInt(scoreColumn);                // Safeguard against adding really long words. Stack may overflow due
                // to recursion
                if (word.length() < maxWordLength) {
                    super.addWord(word, frequency);
                }
                cursor.moveToNext();
            }
        }
        cursor.close();
    }
    
    @Override
    public synchronized void addWord(String word, int frequency) {
        
        // Safeguard against adding long words. Can cause stack overflow.
        if (word.length() >= getMaxWordLength()) return;

        super.addWord(word, frequency);

        Words.addWord(getContext(), word, frequency, Words.LOCALE_TYPE_CURRENT);
        
    }
    
    private void loadLimeDB()
	{	
		FileUtilities fu = new FileUtilities();
		fu.copyPreLoadLimeDB(mContext);			
		db = new LimeDB(mContext);
	}
}
