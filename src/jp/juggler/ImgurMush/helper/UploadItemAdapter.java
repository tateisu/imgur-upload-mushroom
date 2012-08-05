package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;

import jp.juggler.ImgurMush.R;
import jp.juggler.util.HelperEnvUI;

import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class UploadItemAdapter extends BaseAdapter{

	ArrayList<UploadItem> item_list = new ArrayList<UploadItem>();

	final HelperEnvUI env;
	final int min_height;
	final MultiplePreviewLoader preview_loader;

	public UploadItemAdapter(HelperEnvUI env) {
		this.env = env;
		this.min_height = (int)(0.5f + env.density * 96 );
		
		preview_loader = new MultiplePreviewLoader(env,true);
	}

	@Override
	public int getCount() {
		return item_list.size();
	}

	@Override
	public long getItemId(int idx) {
		return 0;
	}

	@Override
	public Object getItem(int idx) {
		if( idx < 0 || idx >= item_list.size() ) return null;
		return item_list.get(idx);
	}

	static class ViewHolder {
		TextView text;
		ImageView image;
		UploadItem item;
	}
	
	ColorDrawable preview_disabled = new ColorDrawable(0xff888888);

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		ViewHolder holder;
		if(view==null){
			view = env.inflater.inflate(R.layout.lv_upload ,null );
			view.setTag( holder = new ViewHolder() );
			holder.text = (TextView)view.findViewById(R.id.text);
			holder.image = (ImageView)view.findViewById(R.id.image);
		}else{
			holder = (ViewHolder)view.getTag();
		}
		try{
			final UploadItem item = (UploadItem)getItem(position);
			holder.item = item; // コールバック内で照合する
			if( item.preview != null ){
				holder.image.setImageBitmap( item.preview );
				holder.text.setText( item == null ? "" : item.desc );
			}else{
				holder.image.setImageDrawable( preview_disabled );
				final ViewHolder holder_ = holder;
				preview_loader.request( item.file , false, min_height,min_height, new MultiplePreviewLoader.Callback() {
					@Override public void onLoad(int w, int h, Bitmap bitmap) {
						item.w = w;
						item.h = h;
						item.updateDesc(env);
						if(bitmap!=null) item.preview = bitmap;
						//
						if( holder_.item != item ) return;
						if( item.preview != null ) holder_.image.setImageBitmap( item.preview );
						holder_.text.setText( item.desc );
					}
				});
			}
		}catch(Throwable ex){
			holder.text.setText("(error)");

		}
		return view;
	}


	public void clear() {
		item_list.clear();
		notifyDataSetChanged();
	}

	public void replace( Iterable<UploadItem> new_data ){
		item_list.clear();
		for( UploadItem item: new_data){
			item_list.add(item);
		}
		notifyDataSetChanged();
	}

	public void remove(int idx) {
		if( idx >= 0 && idx < item_list.size() ){
			item_list.remove(idx);
			notifyDataSetChanged();
		}
	}

	public void add(UploadItem item) {
		item_list.add(item);
		notifyDataSetChanged();
		env.show_toast(false,R.string.selection_added);
	}

	public void replace_item(int idx, UploadItem item) {
		if( idx >= 0 && idx < item_list.size() ){
			item_list.remove(idx);
			item_list.add(idx,item);
			notifyDataSetChanged();
		}
	}
}
