package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;

import jp.juggler.ImgurMush.R;

import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class UploadItemAdapter extends BaseAdapter{

	ArrayList<UploadItem> item_list = new ArrayList<UploadItem>();

	final BaseActivity act;
	final int min_height;
	final MultiplePreviewLoader preview_loader;

	public UploadItemAdapter(BaseActivity act) {
		this.act = act;

		float density = act.getResources().getDisplayMetrics().density;
		this.min_height = (int)(0.5f + density * 96 );
		
		preview_loader = new MultiplePreviewLoader(act,true);
	}


	public void clear() {
		item_list.clear();
		notifyDataSetChanged();
	}

	public void replace( Iterable<UploadItem> new_data ){
		item_list.clear();
		for( UploadItem album: new_data){
			item_list.add(album);
		}
		notifyDataSetChanged();
	}

//	public int findByName(String name) {
//		final int offset = (strNonSelection==null?0:1);
//		if( name != null ){
//			for(int i=0,ie=album_list.size();i<ie;++i){
//				UploadItem album = album_list.get(i);
//				if( name.equals(album.album_name ) ) return offset + i;
//			}
//		}
//		return -1;
//	}


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
			view = act.inflater.inflate(R.layout.lv_upload ,null );
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
						item.updateDesc(act);
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


	public void remove(int idx) {
		if( idx >= 0 && idx < item_list.size() ){
			item_list.remove(idx);
			notifyDataSetChanged();
		}
	}


	public void add(UploadItem item) {
		item_list.add(item);
		notifyDataSetChanged();
		act.show_toast(Toast.LENGTH_SHORT,R.string.selection_added);
	}

	public void replace_item(int idx, UploadItem item) {
		if( idx >= 0 && idx < item_list.size() ){
			item_list.remove(idx);
			item_list.add(idx,item);
			notifyDataSetChanged();
		}
	}



}
