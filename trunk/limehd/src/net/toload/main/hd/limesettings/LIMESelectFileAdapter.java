package net.toload.main.hd.limesettings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.activation.MimetypesFileTypeMap;

import net.toload.main.hd.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class LIMESelectFileAdapter extends BaseAdapter {

	private File currentdir;
	private List<File> list;
	private LayoutInflater mInflater;
	private Context mContext;
	private BaseAdapter adapter;
	
	public LIMESelectFileAdapter(Context context, List<File> ls) {
		this.list = ls;
		this.mContext = context;
		this.mInflater = LayoutInflater.from(context);
		this.adapter = this;
	}
	

	public int getCount() {		
		return list.size();
	}

	public Object getItem(int position) {
		return list.get(position);
	}

	public long getItemId(int arg0) {
		return arg0;
	}

	public View getView(final int position, View convertView, ViewGroup parent) {

		final ViewHolder holder;
		if (convertView == null) {
			convertView = mInflater.inflate(R.layout.imgstring, null);
			holder = new ViewHolder();
			holder.image = (ImageView)convertView.findViewById(R.id.img_function_icon);
			holder.filename = (TextView)convertView.findViewById(R.id.txt_function_name);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}

		if(list.get(position).isDirectory()){
			holder.image.setImageResource(R.drawable.folder);
		}else{
			holder.image.setImageResource(R.drawable.text_x_generic);
		}
		holder.filename.setText(list.get(position).getName());
		
		return convertView;
	}
	
	static class ViewHolder{
		ImageView image;
		TextView filename;
		TextView detail;
	}

	

}
