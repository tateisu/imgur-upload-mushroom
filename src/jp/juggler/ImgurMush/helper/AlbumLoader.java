package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.widget.Toast;

import jp.juggler.ImgurMush.Config;
import jp.juggler.ImgurMush.PrefKey;
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

		reload();
	}

	LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override public void onNewIntent() {
			reload();
		}
		@Override public void onDestroy() {
			load_thread.joinASync(log,"album loader");
			cache_save();
		}
	};

	public void reload() {
		if(load_thread != null ) load_thread.joinASync(log,"album loader");
		load_thread = new LoadThread();
		load_thread.start();
	}

	static final ConcurrentHashMap<String,AlbumList> cache = new ConcurrentHashMap<String,AlbumList>();
	
	public AlbumList findAlbumList(String account_name) {
		if( account_name == null ) return null;
		return cache.get(account_name);
	}
	
	public ImgurAlbum findAlbum(String account_name, String album_id) {
		AlbumList list = findAlbumList(account_name);
		return list==null?null :list.get(album_id);
	}

	void cache_load(){
		// 既にロード済みならロードしない
		if( cache.size() > 0 ) return;
		//
		SharedPreferences pref = act.pref();
		int n = pref.getInt(PrefKey.KEY_ALBUM_CACHE_COUNT,0);
		for(int i=0;i<n;++i){
			try{
				AlbumList result = new AlbumList();
				String account_name = pref.getString(PrefKey.KEY_ALBUM_CACHE_ACCOUNT_NAME+i,null);
				JSONArray list = new JSONArray(pref.getString(PrefKey.KEY_ALBUM_CACHE_ALBUM_LIST+i,null));
				for(int j=0,je=list.length();j<je;++j){
					result.add( new ImgurAlbum(account_name,list.getJSONObject(j)));
				}
				result.from = AlbumList.FROM_CACHE;
				result.update_map();
				cache.put(account_name,result);
			}catch(Throwable ex){
				ex.printStackTrace();
			}
		}
	}

	void cache_save(){
		SharedPreferences.Editor e = act.pref().edit();
		int i=0;
		for( Map.Entry<String,AlbumList> entry : cache.entrySet() ){
			String account_name = entry.getKey();
			AlbumList result = entry.getValue();
			if(result.from == AlbumList.FROM_RESPONSE ){
				try{
					JSONArray json_list = new JSONArray();
					for( ImgurAlbum album : result.iter() ){
						json_list.put( album.toJSON() );
					}
					e.putString( PrefKey.KEY_ALBUM_CACHE_ACCOUNT_NAME+i, account_name );
					e.putString( PrefKey.KEY_ALBUM_CACHE_ALBUM_LIST+i, json_list.toString() );
				}catch(Throwable ex){
					ex.printStackTrace();
				}
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
			final HashMap<String,AlbumList> tmp_map = new HashMap<String,AlbumList> ();
			// アカウント一覧をロードする
			final ArrayList<ImgurAccount> account_list = ImgurAccount.loadAll( act.cr ,bCancelled);
			// 各アカウントのアルバム一覧をロードする
			for( ImgurAccount account : account_list ){
				if(bCancelled.get()) return;
				tmp_map.put( account.name, load(account) );
			}
			act.ui_handler.post(new Runnable() {
				@Override
				public void run() {
					if(bCancelled.get()) return;
					for(Map.Entry<String,AlbumList> entry : tmp_map.entrySet() ){
						String key = entry.getKey();
						AlbumList new_result = entry.getValue();
						AlbumList old_result = cache.get(key);
						// エラーレスポンスはあまり有用ではないので、古いデータがエラー以外なら更新しない
						if( new_result.from == AlbumList.FROM_ERROR
						&& old_result != null
						&& old_result.from != AlbumList.FROM_ERROR
						){
							continue;
						}
						cache.put( key, new_result );
					}
					callback.onLoad();
				}
			});
		}

		AlbumList load(ImgurAccount account){
			AlbumList data = new AlbumList();
			data.from = AlbumList.FROM_ERROR;
			try{
				SignedClient client = new SignedClient();
				client.prepareConsumer(account,Config.CONSUMER_KEY,Config.CONSUMER_SECRET);
				//
				SignedClient.JSONResult result = client.json_signed_get(act,"http://api.imgur.com/2/account/albums.json?count=999");
				if( result.err != null ){
					if( -1 != result.err.indexOf("No albums found") ){
						log.d("No albums found.");
						// アルバムがないのは正常ケース
						data.from = AlbumList.FROM_RESPONSE;
					}else{
						act.show_toast(Toast.LENGTH_LONG,result.err);
					}
				}else if( result.json.isNull("albums") ){
					act.show_toast(Toast.LENGTH_LONG,String.format("missing 'albums' in response: %s",result.str));
				}else{
					log.d("albums found.");
					JSONArray src_list = result.json.getJSONArray("albums");
					for(int j=0,je=src_list.length();j<je;++j){
						JSONObject src = src_list.getJSONObject(j);
						data.add(new ImgurAlbum(account.name,src));
					}
					data.from = AlbumList.FROM_RESPONSE;
				}
			}catch(Throwable ex){
				act.report_ex(ex);
			}
			data.update_map();
			return data;
			// nullを返すと UploadTargetManager の初期化が終わらないことになってしまうので、
		}
	}
}
