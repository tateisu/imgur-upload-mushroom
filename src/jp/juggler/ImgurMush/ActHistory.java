package jp.juggler.ImgurMush;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONObject;


import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

public class ActHistory extends ActivityBase {
	static final String TAG = "ImgurMush";
	static final boolean account_filter_enabled = false;
	
	ListView listview;
	MyCursorAdapter adapter;
	LayoutInflater inflater;
	ContentResolver cr;
	DataLoader loader;
	
	String anony_name;
	String no_album;
	String album_unknown;
	String album_loading;
	Spinner spAccount;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_list);
		
		setResult(RESULT_CANCELED);
		loader = new DataLoader(self,true,"cache");
		loader.default_expire = 1000 * 86400 * 10;
		loader.no_cache_load = true;
		
		anony_name = getString(R.string.account_anonymous);
		no_album = getString(R.string.no_album);
		album_unknown = getString(R.string.album_unknown);
		album_loading = getString(R.string.album_loading);
		
		inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
		cr = getContentResolver();
		listview = (ListView)findViewById(R.id.list);
		adapter = new MyCursorAdapter(this,cr.query(DataProvider.history.uri,null,null,null,ImgurHistory.COL_UPLOAD_TIME+" desc"),true);
		int count = adapter.getCount();
		Log.d(TAG,"history count="+count);
		listview.setAdapter(adapter);
		listview.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position,long arg3) {
				long id = adapter.getItemId(position);
				final ImgurHistory item = ImgurHistory.load(cr,id);
				new AlertDialog.Builder(self)
				.setItems(getResources().getStringArray(R.array.history_context_menu), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch(which){
						case 0: return_url(item); break;
						case 1: open_browser( item.image ); break; 
						case 2: open_browser( item.page ); break; 
						case 3: open_browser( item.delete ); break;
						case 4: remove_hitosy( item  ); break; 
						}
					}
				})
				.setCancelable(true)
				.show();
			}
			
		});
		
		if( account_filter_enabled ){
			spAccount = (Spinner)findViewById(R.id.account);
			spAccount.setAdapter(account_adapter);
			spAccount.setOnItemSelectedListener(new OnItemSelectedListener() {
	
					@Override
					public void onItemSelected(AdapterView<?> arg0, View arg1,int pos, long arg3) {
						Log.d(TAG,"onItemSelected");
					}
	
					@Override
					public void onNothingSelected(AdapterView<?> arg0) {
						Log.d(TAG,"onNothingSelected");
					}
				});
			
			account_cursor = cr.query(ImgurAccount.meta.uri,null,null,null,ImgurAccount.COL_NAME+" asc");
			account_cursor.registerDataSetObserver(new DataSetObserver() {
				@Override
				public void onChanged() {
					account_adapter.notifyDataSetChanged();
				}
				@Override
				public void onInvalidated() {
					account_adapter.notifyDataSetInvalidated();
				}
			});
		}
	}
	
	///////////////////////////////////////////////
	
	@Override
	protected void onResume() {
		super.onResume();
		loader.reset();
	}

	@Override
	protected void onPause() {
		super.onPause();
		loader.stop();
	}


	void return_url(ImgurHistory item){
		int type = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(self).getString("URL_mode","0"));
		Intent intent = new Intent();
		switch(type){
		default:
		case 0:
			intent.putExtra("url",item.image);
			break;
		case 1:
			intent.putExtra("url",item.page);
			break;
		}
		setResult(RESULT_OK,intent);
		finish();
	}

	void remove_hitosy(ImgurHistory item){
		cr.delete(Uri.withAppendedPath(DataProvider.history.uri,"/"+item.id),null,null);
	}
	
	void open_browser(final String url){
		ui_handler.post(new Runnable() {
			@Override
			public void run() {
				if(isFinishing()) return;
				startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
			}
		});
	}
	
	static class ViewHolder{
		ImageView image;
		TextView text;
		long id;
	}
	class MyCursorAdapter extends CursorAdapter{
		int colidx_id;
		int colidx_page;
		int colidx_delete;
		int colidx_image;
		int colidx_square;
		int colidx_time;
		int colidx_account_name;
		int colidx_album_id;
		
		
		public MyCursorAdapter(Context context, Cursor c, boolean autoRequery) {
			super(context, c, autoRequery);
			 colidx_id = c.getColumnIndex(ImgurHistory.COL_ID);
			 colidx_page = c.getColumnIndex(ImgurHistory.COL_IMGUR_PAGE);
			 colidx_delete = c.getColumnIndex(ImgurHistory.COL_DELETE_PAGE);
			 colidx_image = c.getColumnIndex(ImgurHistory.COL_IMAGE_LINK);
			 colidx_square = c.getColumnIndex(ImgurHistory.COL_SQUARE);
			 colidx_time = c.getColumnIndex(ImgurHistory.COL_UPLOAD_TIME);
			 colidx_account_name = c.getColumnIndex(ImgurHistory.COL_ACCOUNT_NAME);
			 colidx_album_id = c.getColumnIndex(ImgurHistory.COL_ALBUM_ID);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			final ViewHolder holder = (ViewHolder)view.getTag();
			final long id = holder.id = cursor.getLong(colidx_id);
			final String thum_url = cursor.getString(colidx_square);
			final String account_name = cursor.isNull(colidx_account_name)?anony_name:cursor.getString(colidx_account_name);
			final String album_id = cursor.isNull(colidx_album_id)?null:cursor.getString(colidx_album_id);
			final String album_name = (album_id == null ? no_album : getAlbumName( account_name, album_id ));
			
			holder.text.setText(String.format("%s\n%s / %s\n%s"
					,DateUtils.formatDateTime(self,cursor.getLong(colidx_time),DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_24HOUR)
					,account_name
					,album_name
					,cursor.getString(colidx_page)
			));

			DataLoader.clearImageView(holder.image);
			
			if( thum_url != null && thum_url.length() > 0 ){
				loader.request( thum_url ,new DataLoader.Listener() {
					
					
					@Override
					public void onError(String msg) {
						if( msg.equals("HTTP error 404") ){
							ContentValues v = new ContentValues();
							v.put(ImgurHistory.COL_SQUARE,"");
							cr.update(Uri.withAppendedPath(ImgurHistory.meta.uri,"/"+id),v,null,null);
						}
					}

					@Override public void onData(File file,byte[] data) {
						if( id != holder.id ) return;
						if( data == null ){
							try{
								FileInputStream fis = new FileInputStream(file);
								try{
									ByteArrayOutputStream bao = new ByteArrayOutputStream( fis.available() );
									byte[] tmp = new byte[4096];
									for(;;){
										int delta = fis.read(tmp,0,tmp.length);
										if( delta <= 0 ) break;
										bao.write(tmp,0,delta);
									}
									data = bao.toByteArray();
								}finally{
									fis.close();
								}
							}catch(Throwable ex){
								return;
							}
						}
						Bitmap b = DataLoader.decodePurgeableBitmap(data);
						if(b!=null){
							DataLoader.clearImageView(holder.image);
							holder.image.setImageBitmap(b);
						}
					}
				});
			}
			
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			View view = inflater.inflate(R.layout.lv_history,null);
			ViewHolder holder = new ViewHolder();
			view.setTag(holder);
			holder.image = (ImageView)view.findViewById(R.id.image);
			holder.text = (TextView)view.findViewById(R.id.text);
			return view;
		}
		
	}

	///////////////////////////////////
	

	
	
	void load_account_selection(SharedPreferences pref){
		String target_account = pref.getString("pref_default_account", "<>lastused");
		if( target_account.equals("<>lastused") ){
			target_account = pref.getString("account_name","<>anonymous");
		}
		if( target_account.equals("<>anonymous") ){
			target_account = null;
		}
		Log.d(TAG,"load_account_selection: "+target_account);
		
		if( account_cursor != null && account_cursor.getCount() > 0 ){
			for(int i=0,ie=account_cursor.getCount();i<=ie;++i){
				if( i==0 ){
					if( target_account ==null ){
						spAccount.setSelection(i);
						break;
					}
				}else{
					ImgurAccount account = get_account(i);
					if( account != null && account.name.equals(target_account) ){
						spAccount.setSelection(i);
						break;
					}
				}
			}
		}
	}

	

	// アカウントリストを更新する
	void account_reload(){
		account_cursor.requery();
		account_adapter.notifyDataSetChanged();
	}
	
	int account_cursor_colidx_id = -1;
	int account_cursor_colidx_name;
	int account_cursor_colidx_token;
	int account_cursor_colidx_secret;

	ImgurAccount get_account(){
		return get_account(spAccount.getSelectedItemPosition());
	}
	ImgurAccount get_account(int position){
		if(position <= 0 || !account_cursor.moveToPosition(position -1)) return null;
		
		if( account_cursor_colidx_id < 0 ){
			account_cursor_colidx_id = account_cursor.getColumnIndex(ImgurAccount.COL_ID);
			account_cursor_colidx_name = account_cursor.getColumnIndex(ImgurAccount.COL_NAME);
			account_cursor_colidx_token = account_cursor.getColumnIndex(ImgurAccount.COL_TOKEN);
			account_cursor_colidx_secret = account_cursor.getColumnIndex(ImgurAccount.COL_SECRET);
		}
		
		ImgurAccount item = new ImgurAccount();
		item.id     = account_cursor.getLong(account_cursor_colidx_id);
		item.name   = account_cursor.getString(account_cursor_colidx_name);
		item.token  = account_cursor.getString(account_cursor_colidx_token);
		item.secret = account_cursor.getString(account_cursor_colidx_secret);
		return item;
		
	}


	
	class AccountViewHolder{
		TextView tvName;
	}
	Cursor account_cursor;
	AccountAdapter account_adapter = new AccountAdapter();
	class AccountAdapter extends BaseAdapter{
		@Override
		public int getCount() {
			return 1+(account_cursor==null? 0 : account_cursor.getCount());
		}

		@Override
		public Object getItem(int arg0) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View view, ViewGroup parent) {
			return make_view( position,  view,  parent,android.R.layout.simple_spinner_item);
		}

		@Override
		public View getDropDownView(int position, View view,ViewGroup parent) {
			return make_view( position,  view,  parent,android.R.layout.simple_spinner_dropdown_item);
		}
		
		private View make_view(int position, View view, ViewGroup parent,int layout){
			AccountViewHolder holder;
			if(view==null){
				view = inflater.inflate(layout ,null );
				view.setTag( holder = new AccountViewHolder() );
				holder.tvName = (TextView)view.findViewById(android.R.id.text1);
			}else{
				holder = (AccountViewHolder)view.getTag();
			}
			if( position == 0 ){
				holder.tvName.setText(getString(R.string.account_anonymous));
			}else{
				ImgurAccount item = get_account(position);
				if( item == null ){
					holder.tvName.setText("(error)");
				}else{
					holder.tvName.setText(item.name);
				}
			}
			return view;
		}
	}
	
	///////////////////
	
	HashMap<String,HashMap<String,String> > album_map = new HashMap<String,HashMap<String,String> >();
	HashSet<String> album_map_busy = new HashSet<String>();


	String getAlbumName(final String account_name,final String album_id){
		if( album_map_busy.contains(account_name) ) return album_loading;
		HashMap<String,String> map = album_map.get( account_name );
		if( map != null ){
			String album_name = map.get(album_id);
			return album_name!= null ? album_name : album_unknown;
		}
		album_map_busy.add( account_name );
		new AsyncTask<Void,Void,HashMap<String,String> >(){
			@Override
			protected HashMap<String, String> doInBackground(Void... params) {
				HashMap<String,String> map = new HashMap<String,String>();
				try{
					ImgurAccount account = ImgurAccount.loadByName( getContentResolver(), account_name );
					if( account != null ){
						SignedClient client = new SignedClient();
						client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
						JSONObject result = client.json_signed_get("http://api.imgur.com/2/account/albums.json?count=999");
						if( client.last_rcode != 404 ) client.error_report(self,result);
						if( result.has("albums") ){
							JSONArray src_list = result.getJSONArray("albums");
							for(int i=0,ie=src_list.length();i<ie;++i){
								JSONObject item = src_list.getJSONObject(i);
								map.put( item.getString("id") , item.getString("title") );
							}
						}
					}
				}catch(Throwable ex){
					report_ex(ex);
				}
				return map;
			}

			@Override
			protected void onPostExecute(HashMap<String, String> result) {
				album_map.put( account_name,result );
				album_map_busy.remove( account_name );
				adapter.notifyDataSetChanged();
			}
			
		}.execute();
		return album_loading;
	}
}
