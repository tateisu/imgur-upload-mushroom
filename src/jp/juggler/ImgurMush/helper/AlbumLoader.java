package jp.juggler.ImgurMush.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

import android.database.Cursor;
import android.widget.Toast;

import jp.juggler.ImgurMush.BaseActivity;
import jp.juggler.ImgurMush.Config;
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
	}

	LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override
		public void onStart() {
			load_thread = new LoadThread();
			load_thread.start();
		}
		@Override
		public void onStop() {
			load_thread.joinASync(log,"album loader");
		}
	};
	
	HashMap<String,HashMap<String,ImgurAlbum>> map1;
	HashMap<String,ArrayList<ImgurAlbum>> map2;
	
	
	public ImgurAlbum findAlbum(String account_name, String album_id) {
		if( account_name == null || album_id == null ) return null;
		if(map1==null) return null;
		HashMap<String,ImgurAlbum> set = map1.get(account_name);
		if(set==null) return null;
		return set.get(album_id);
	}
	public Iterable<ImgurAlbum> findAlbumList(String account_name) {
		if( account_name == null ){
			log.e("findAlbumList: target name is null");
			return null;
		}
		if(map2==null){
			log.e("findAlbumList: now loading..");
			return null;
		}
		return map2.get(account_name);
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
					map1 = tmp_map1;
					map2 = tmp_map2;
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
						list.add(new ImgurAlbum(account,src));
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
