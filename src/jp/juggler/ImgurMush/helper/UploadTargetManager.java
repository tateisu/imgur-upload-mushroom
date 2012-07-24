package jp.juggler.ImgurMush.helper;

import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.SystemClock;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;
import jp.juggler.ImgurMush.BaseActivity;
import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.ImgurMush.R;
import jp.juggler.ImgurMush.data.ImgurAccount;
import jp.juggler.ImgurMush.data.ImgurAlbum;
import jp.juggler.util.LifeCycleListener;
import jp.juggler.util.LogCategory;

public class UploadTargetManager {
	static final LogCategory log = new LogCategory("UploadTargetManager");
	
	final BaseActivity act;
	final AccountAdapter account_adapter;
	final AlbumAdapter album_adapter;
	final AlbumLoader album_loader;
	
	final Spinner spAccount;
	final Spinner spAlbum;
	
	public UploadTargetManager(BaseActivity act){
		this.act = act;
        spAccount = (Spinner)act.findViewById(R.id.account);
        spAlbum = (Spinner)act.findViewById(R.id.album);

	    account_adapter = new AccountAdapter(act,act.getString(R.string.account_anonymous) );
	    album_adapter = new AlbumAdapter(act,act.getString(R.string.album_not_select));
        spAccount.setAdapter(account_adapter);
		spAlbum.setAdapter(album_adapter);

		spAccount.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				account_selection_changed(false,pos,"account selected");
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {
			}
		});
	    album_loader = new AlbumLoader(act,new AlbumLoader.Callback() {
			@Override
			public void onLoad() {
				account_selection_changed(true,spAccount.getSelectedItemPosition(),"album loaded");
			}
		});
	    account_adapter.registerDataSetObserver(new DataSetObserver() {
			@Override public void onChanged() {
				super.onChanged();
				account_selection_changed(true,spAccount.getSelectedItemPosition(),"account data changed");
			}
		});
        
	    spAlbum.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				log.d("album selection changed: %d",pos);
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {
				log.d("album selection loat");
			}
		});
	    
		act.lifecycle_manager.add(activity_listener);

		selection_init();
	}
	
	LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override
		public void onNewIntent() {
			selection_init();
		}

		@Override
		public void onPause() {
			selection_save();
		}
	};

	/////////////////////////////////////////////////////////////////////////////
	
	String  lastused_account_name = null;
	String  lastused_album_name = null;
	boolean init_complete = false;
	
	void selection_init(){
		SharedPreferences pref = act.pref();
		// 
		init_complete = false;
		//
		lastused_account_name = pref.getString(PrefKey.KEY_ACCOUNT_LAST_USED,null);
		if( PrefKey.VALUE_ACCOUNT_ANONYMOUS.equals(lastused_account_name) ) lastused_account_name = null;
		//
		if( lastused_account_name == null ){
			lastused_album_name = null;
		}else{
			lastused_album_name = pref.getString("album_name_"+lastused_account_name,null);
		}
		log.d("load selection: account=%s,album=%s",lastused_account_name,lastused_album_name);
		// アカウントは初期化中でもアクセスできるはず…
		account_adapter.selectByName(spAccount,lastused_account_name);
		last_selection_change = SystemClock.uptimeMillis();
	}
	
	void selection_save(){
		if( isLoading() ) return;
		//
		SharedPreferences.Editor e = act.pref().edit();
		ImgurAlbum album = (ImgurAlbum)album_adapter.getItem(spAlbum.getSelectedItemPosition());
		if( album != null ){
			e.putString(PrefKey.KEY_ACCOUNT_LAST_USED,album.account_name);
			e.putString("album_name_"+album.account_name,album.album_name);
			log.d("save selection: account=%s,album=%s",album.account_name,album.album_name);
		}else{
			ImgurAccount account = (ImgurAccount)account_adapter.getItem( spAccount.getSelectedItemPosition());
			if( account != null ){
				e.putString(PrefKey.KEY_ACCOUNT_LAST_USED,account.name);
				e.remove("album_name_"+account.name);
				log.d("save selection: account=%s,",account.name);
			}else{
				e.remove(PrefKey.KEY_ACCOUNT_LAST_USED);
				log.d("save selection: nanasi");
			}
		}
		e.commit();
	}
	long last_selection_change = 0;
	public boolean isLoading() {
		if( !init_complete ){
			log.d("isLoading: initialize is not complete");
			return true;
		}
		long now = SystemClock.uptimeMillis();
		if( now - last_selection_change <= 333 ){
			log.d("isLoading: too short last event");
			return true;
		}
		return false;
	}

	void account_selection_changed(boolean bAlbumLoaded, int last_account_idx,String desc) {
		last_selection_change = SystemClock.uptimeMillis();

		ImgurAccount account = (ImgurAccount)account_adapter.getItem(last_account_idx);
		if( account == null ){
			log.d("missing account: %s",desc);
			// アカウントが選択されていない
			album_adapter.clear(act.getString(R.string.album_not_select));
			
			// 初期選択がカラならこの時点で初期化完了とみなす
			if( lastused_account_name == null ){
				if( !init_complete ){
					init_complete = true;
					log.d("init_complete(1): %s",desc);
				}
			}
		}else{
			// アカウントが選択されている

			// アルバム選択肢を読む
			Iterable<ImgurAlbum> list = album_loader.findAlbumList(account.name);
			if( list == null ){
				log.d("missing album: %s",desc);
				// アルバム一覧を読めない。ロード中かエラーだろう
				album_adapter.clear(act.getString(R.string.album_loading));
			}else{
				// アルバムを読めたら初期化完了とみなす
				if( !init_complete ){
					init_complete = true;
					log.d("init_complete(2): %s",desc);
				}

				// 直前までの選択。異なるアカウントのものはnullとする
				ImgurAlbum old_selection= (ImgurAlbum)album_adapter.getItem(spAlbum.getSelectedItemPosition());
				if( old_selection != null && ! account.name.equals(old_selection.account_name) ) old_selection = null;

				if( bAlbumLoaded || old_selection ==null  ){

					// アルバム選択肢を設定する
					album_adapter.replace(list,act.getString(R.string.album_not_select));

					if( account.name.equals( lastused_account_name ) ){
						// 選択の初期化がまだおわっておらず現在のアカウントとマッチするのなら、アルバムを選択しなおす
						log.d("initialize album selection: %s %s %s",desc,lastused_account_name,lastused_album_name);
						lastused_account_name = null;
						final int idx = album_adapter.findByName(lastused_album_name);
						spAlbum.setSelection(idx < 0 ? 0 : idx);
					}else if( old_selection != null ){
						// アカウントは変化していないが、データが更新されたかもしれない。
						// なるべく以前の選択を維持する
						int idx = album_adapter.findByName(old_selection.album_name);
						spAlbum.setSelection(idx);
					}else{
						// アカウントの選択が変わったので、アルバムの選択をリセットする
						log.d("reset album selection: %s",desc);
						spAlbum.setSelection(0);
					}
					// ただし、 selection changed イベントが発生するのはこれより少し後になる
				}
			}
		}
	}
	
	///////////////////////////////////

	public ImgurAccount getSelectedAccount() {
		return (ImgurAccount)account_adapter.getItem(spAccount.getSelectedItemPosition()); 
	}

	public ImgurAlbum getSelectedAlbum() {
		return (ImgurAlbum)album_adapter.getItem(spAlbum.getSelectedItemPosition()); 
	}

	public void reload() {
		account_adapter.reload();
	}


}

