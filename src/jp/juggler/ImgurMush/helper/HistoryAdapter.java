package jp.juggler.ImgurMush.helper;

import java.io.File;

import jp.juggler.ImgurMush.R;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.data.ImgurHistory;
import jp.juggler.util.HelperEnvUI;
import jp.juggler.util.LifeCycleListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class HistoryAdapter extends BaseAdapter {

	final HelperEnvUI env;
	final DataLoader thumbnail_loader;
	final String strNoAccount;
	final String strNoAlbum;
	final ImgurHistory.ColumnIndex colidx = new ImgurHistory.ColumnIndex();
	final AlbumLoader album_loader;
	final ContentObserver content_observer;
	final DataSetObserver data_observer;

	Cursor cursor;
	boolean mDataValid;

	public HistoryAdapter(HelperEnvUI env) {
		this.env =env;
		this.album_loader = new AlbumLoader(env,new AlbumLoader.Callback() {
			@Override
			public void onLoad() {
				notifyDataSetChanged();
			}
		});

		strNoAccount = env.getString(R.string.account_anonymous);
		strNoAlbum = env.getString(R.string.album_not_select);
		//
		thumbnail_loader = new DataLoader(env,true,true,"cache");
		thumbnail_loader.default_expire = 1000 * 86400 * 10;
		//
		data_observer= new DataSetObserver() {

			@Override
			public void onChanged() {
				mDataValid = true;
				notifyDataSetChanged();
			}

			@Override
			public void onInvalidated() {
				mDataValid = false;
				notifyDataSetInvalidated();
			}
		};

		content_observer = new ContentObserver(env.handler) {
			@Override
			public boolean deliverSelfNotifications() {
				return false;
			}

			@SuppressWarnings("deprecation")
			@Override
			public void onChange(boolean selfChange) {
				mDataValid = cursor.requery();
			}
		};

		setFilter(null,null);
		
		env.lifecycle_manager.add(activity_listener);
	}

	LifeCycleListener activity_listener = new LifeCycleListener(){

		@Override
		public void onDestroy() {
			if(cursor!=null) cursor.close();
		}

	};

	public void clearFilter() {
		setFilter(null,null);
	}

	public void setFilter(ImgurAccount account, ImgurAlbum album) {
		if(cursor!=null) cursor.close();
		cursor = ImgurHistory.query(env.cr,account,album);
		cursor.registerContentObserver(content_observer);
		cursor.registerDataSetObserver(data_observer);
		mDataValid = true;
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		if(!mDataValid) return 0;
		return cursor.getCount();
	}

	@Override
	public Object getItem(int position) {
		if(!mDataValid) return null;
		if(!cursor.moveToPosition(position) ) return null;
		return ImgurHistory.loadFromCursor(cursor,colidx);
	}

	@Override
	public long getItemId(int position) {
		ImgurHistory item = (ImgurHistory)getItem(position);
		return item==null? -1L : item.id;
	}

	static class ViewHolder{
		ImageView image;
		TextView text;
		long id;
	}

	ColorDrawable loading_drawable = new ColorDrawable(0xff333333);

	@Override
	public View getView(int position, View view, ViewGroup parent) {

		ViewHolder holder;
		if( view != null ){
			holder = (ViewHolder)view.getTag();
		}else{
			view = env.inflater.inflate(R.layout.lv_history,null);
			holder = new ViewHolder();
			view.setTag(holder);
			holder.image = (ImageView)view.findViewById(R.id.image);
			holder.text = (TextView)view.findViewById(R.id.text);
		}

		holder.image.setImageDrawable(loading_drawable);

		final ImgurHistory item = (ImgurHistory)getItem(position);
		if( item == null ){
			holder.text.setText("");
		}else{
			holder.id = item.id;
			ImgurAlbum album = album_loader.findAlbum( item.account_name, item.album_id );
			holder.text.setText(String.format("%s\n%s / %s\n%s"
					,TextFormat.formatTime(env.context,item.upload_time)
					,(item.account_name==null ? strNoAccount : item.account_name )
					,(album==null? strNoAlbum : album.album_name)
					,item.page
			));
			if( item.square != null && item.square.length() > 0 ){
				final ViewHolder holder_ = holder;
				thumbnail_loader.request( item.square , DataLoader.DATATYPE_BITMAP, 1000*60*10, new DataLoader.Listener<Bitmap>() {
					@Override public void onError(String msg) {
						if( msg != null && msg.equals("HTTP error 404") ){
							ImgurHistory.deleteById(env.cr,item.id);
						}
					}
					@Override public void onData(File file,Bitmap data) {
						if( item.id == holder_.id ) holder_.image.setImageBitmap(data);
					}
				});
			}
		}
		return view;
	}

}
