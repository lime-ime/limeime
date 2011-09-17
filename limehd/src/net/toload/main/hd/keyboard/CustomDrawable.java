package net.toload.main.hd.keyboard;

import net.toload.main.hd.R;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class CustomDrawable extends Drawable {
	
	private static final String TAG = CustomDrawable.class.getSimpleName();
	private ColorFilter cf;
	private String text;
	
	CustomDrawable(String text){
		this.text = text;
	}

	@Override
	public int getOpacity() {
		// TODO Auto-generated method stub
		return 100;
	}

	@Override
	public void setAlpha(int alpha) {
		// TODO Auto-generated method stub
	}

	@Override
	public void draw(Canvas canvas) {
    	Paint nPaint = new Paint();
    	nPaint.setAntiAlias(true);
    	nPaint.setTextSize(28);
    	nPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		canvas.drawText(text, 0f, 0f, nPaint);
	
	}

	@Override
	public void setColorFilter(ColorFilter arg0) {
		this.cf = cf;
	}
}
