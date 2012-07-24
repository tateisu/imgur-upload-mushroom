package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.widget.Toast;

import jp.juggler.ImgurMush.BaseActivity;
import jp.juggler.ImgurMush.Config;
import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.ImgurMush.R;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.ImgurMush.data.SignedClient;

import jp.juggler.util.LifeCycleListener;
import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;

public class AlbumLoader {
	static final LogCategory log = new LogCategory("AlbumLoader");

	public interface Callback{
		void onLoad();
	}

	final BaseActivity act;
	final Callback callback;

	public AlbumLoader(BaseActivity act,Callback callback){
		this.act = act;
		this.callback = callback;
		act.lifecycle_manager.add(activity_listener);
		
		cache_load();
		
		init();
	}

	LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override public void onNewIntent() {
			init();
		}
		@Override public void onDestroy() {
			load_thread.joinASync(log,"album loader");
			cache_save();
		}
	};

	void init(){
		if(load_thread != null ) load_thread.joinASync(log,"album loader");
		load_thread = new LoadThread();
		load_thread.start();
	}

	static final ConcurrentHashMap<String,HashMap<String,ImgurAlbum>> map1 = new ConcurrentHashMap<String,HashMap<String,ImgurAlbum>>();
	static final ConcurrentHashMap<String,ArrayList<ImgurAlbum>> map2 = new ConcurrentHashMap<String,ArrayList<ImgurAlbum>>();
	
	public ImgurAlbum findAlbum(String account_name, String album_id) {
		if( map1 !=null && account_name != null && album_id != null ){
			HashMap<String,ImgurAlbum> set = map1.get(account_name);
			if( set != null ) return set.get(album_id);
		}
		return null;
	}

	public Iterable<ImgurAlbum> findAlbumList(String account_name) {
		if( map2!=null && account_name != null ){
			return map2.get(account_name);
		}
		return null;
	}

	void cache_load(){
		// 既にロード済みならロードしない
		if( map2.size() > 0 ) return;
		//
		SharedPreferences pref = act.pref();
		int n = pref.getInt(PrefKey.KEY_ALBUM_CACHE_COUNT,0);
		for(int i=0;i<n;++i){
			try{
				String account_name = pref.getString(PrefKey.KEY_ALBUM_CACHE_ACCOUNT_NAME+i,null);
				JSONArray list = new JSONArray(pref.getString(PrefKey.KEY_ALBUM_CACHE_ALBUM_LIST+i,null));
				ArrayList<ImgurAlbum> dst_list = new ArrayList<ImgurAlbum>();
				for(int j=0,je=list.length();j<je;++j){
					dst_list.add( new ImgurAlbum(account_name,list.getJSONObject(j)));
				}
				map2.put(account_name,dst_list);
			}catch(Throwable ex){
				ex.printStackTrace();
			}
		}
	}

	void cache_save(){
		SharedPreferences.Editor e = act.pref().edit();
		int i=0;
		for( Map.Entry<String,ArrayList<ImgurAlbum>> entry : map2.entrySet() ){
			try{
				JSONArray list = new JSONArray();
				for( ImgurAlbum album : entry.getValue()){
					list.put( album.toJSON() );
				}
				e.putString( PrefKey.KEY_ALBUM_CACHE_ACCOUNT_NAME+i, entry.getKey() );
				e.putString( PrefKey.KEY_ALBUM_CACHE_ALBUM_LIST+i, list.toString() );
			}catch(Throwable ex){
				ex.printStackTrace();
			}
			++i;
		}
		e.putInt(PrefKey.KEY_ALBUM_CACHE_COUNT,i);
		e.commit();
	}
	///////////////////////////
	
	
	LoadThread load_thread;
	class LoadThread extends WorkerBase{
		AtomicBoolean bCancelled = new AtomicBoolean(false);
		
		@Override
		public void cancel() {
			bCancelled.set(true);
			notifyEx();
		}

		@Override
		public void run() {
			final HashMap<String,HashMap<String,ImgurAlbum>> tmp_map1= new HashMap<String,HashMap<String,ImgurAlbum>> ();
			final HashMap<String,ArrayList<ImgurAlbum>> tmp_map2 = new HashMap<String,ArrayList<ImgurAlbum>> ();
			// アカウント一覧をロードする
			final ArrayList<ImgurAccount> account_list = new ArrayList<ImgurAccount>();
			try{
				ImgurAccount.ColumnIndex colidx = new ImgurAccount.ColumnIndex();
				Cursor c = act.cr.query(ImgurAccount.meta.uri,null,null,null,null);
				try{
					while( c.moveToNext() ){
						ImgurAccount item = ImgurAccount.loadFromCursor(c,colidx);
						if(item!=null) account_list.add(item);
						if(bCancelled.get()) return;
					}
				}finally{
					c.close();
				}
			}catch(Throwable ex){
				ex.printStackTrace();
				return;
			}
			// 各アカウントのアルバム一覧をロードする
			for( ImgurAccount account : account_list ){
				if(bCancelled.get()) return;
				ArrayList<ImgurAlbum> album_list = load(account);
				if(album_list != null ){
					HashMap<String,ImgurAlbum> map1_sub = new HashMap<String,ImgurAlbum>();
					for( ImgurAlbum album :  album_list ){
						map1_sub.put( album.album_id , album );
					}
					tmp_map1.put( account.name , map1_sub );
					Collections.sort(album_list);
					tmp_map2.put( account.name ,album_list );
				}
			}
			act.ui_handler.post(new Runnable() {
				@Override
				public void run() {
					if(bCancelled.get()) return;
					for(Map.Entry<String,HashMap<String,ImgurAlbum>> entry : tmp_map1.entrySet() ){
						map1.put( entry.getKey(), entry.getValue() );
					}
					for(Map.Entry<String,ArrayList<ImgurAlbum>> entry : tmp_map2.entrySet() ){
						map2.put( entry.getKey(), entry.getValue() );
					}
					callback.onLoad();
				}
			});
		}

		ArrayList<ImgurAlbum> load(ImgurAccount account){
			try{
				SignedClient client = new SignedClient();
				client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
				JSONObject result = client.json_signed_get("http://api.imgur.com/2/account/albums.json?count=999");
				if( client.last_rcode != 404 ) client.error_report(act,result);
				if( result != null ){
					if( ! result.has("albums") ){
						act.show_toast(Toast.LENGTH_LONG,act.getString(R.string.album_data_invalid));
						return null;
					}
					ArrayList<ImgurAlbum> list = new ArrayList<ImgurAlbum>();
					JSONArray src_list = result.getJSONArray("albums");
					for(int j=0,je=src_list.length();j<je;++j){
						JSONObject src = src_list.getJSONObject(j);
						list.add(new ImgurAlbum(account.name,src));
					}
					return list;
				}
			}catch(Throwable ex){
				act.report_ex(ex);
			}
			return null;
		}
	}
}
