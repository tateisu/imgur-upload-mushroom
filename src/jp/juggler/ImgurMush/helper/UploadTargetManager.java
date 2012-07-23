package jp.juggler.ImgurMush.helper;

import android.content.SharedPreferences;
import android.database.DataSetObserver;
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

	    album_loader = new AlbumLoader(act,new AlbumLoader.Callback() {
			@Override
			public void onLoad() {
				account_selection_changed(spAccount.getSelectedItemPosition());
			}
		});
	    album_adapter = new AlbumAdapter(act,act.getString(R.string.album_not_select));
	    
	    account_adapter.registerDataSetObserver(new DataSetObserver() {
			@Override public void onChanged() {
				super.onChanged();
				account_selection_changed(spAccount.getSelectedItemPosition());
			}
		});
        
        spAccount.setAdapter(account_adapter);
		spAlbum.setAdapter(album_adapter);

		spAccount.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> arg0, View arg1,int pos, long arg3) {
				account_selection_changed(pos);
				
			}
			@Override public void onNothingSelected(AdapterView<?> arg0) {
				account_selection_changed(-1);
				
			}
		});
        
		setWillSelect();
		
		act.lifecycle_manager.add(activity_listener);
	}
	
	LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override
		public void onNewIntent() {
			setWillSelect();
		}
	};
	

	/////////////////////////////////////////////////////////////////////////////
	
	String lastused_account_name = null;
	
	void setWillSelect(){
		// アカウントは初期化中でもアクセスできるはず…
		SharedPreferences pref = act.pref();
		String target_account = pref.getString(PrefKey.KEY_DEFAULT_ACCOUNT, PrefKey.VALUE_LAST_USED);
		if( target_account.equals( PrefKey.VALUE_LAST_USED ) ){
			target_account = pref.getString(PrefKey.KEY_ACCOUNT_LAST_USED,PrefKey.VALUE_ACCOUNT_ANONYMOUS);
		}
		if( target_account.equals(PrefKey.VALUE_ACCOUNT_ANONYMOUS) ){
			target_account = null;
		}
		account_adapter.selectByName(spAccount,target_account);
		// アルバムの選択を初期化するフラグを設定
		lastused_account_name = target_account;
	}
	
	void account_selection_changed(int idx) {
		// 少し遅延してアルバム選択を直す
		last_account_idx = idx;
		act.ui_handler.removeCallbacks(account_selection_changed_delayed);
		act.ui_handler.postDelayed(account_selection_changed_delayed,60);
	}
	
	int last_account_idx;
	Runnable  account_selection_changed_delayed = new Runnable() {
		@Override
		public void run() {
			log.d("account_selection_changed_delayed");
			ImgurAccount account = (ImgurAccount)account_adapter.getItem(last_account_idx);
			if( account == null ){
				log.d("missing account info");
				album_adapter.clear(act.getString(R.string.album_not_select));
				return;
			}
			Iterable<ImgurAlbum> list = album_loader.findAlbumList(account.name);
			if( list == null ){
				log.d("missing album info");
				album_adapter.clear(act.getString(R.string.album_loading));
				return;
			}
			album_adapter.replace(list,act.getString(R.string.album_not_select));
			// 最後にアップロードしたアルバムを選択する？
			if( account.name.equals( lastused_account_name ) ){
				lastused_account_name = null;
				SharedPreferences pref = act.pref();
				String key = "album_name" + (account ==null ? "" : ("_" + account.name ) );
				String last_album = pref.getString(key,null);
				log.d("last_album=%s",last_album);
				final int idx = album_adapter.findByName(last_album);
				if( idx >= 0 ){
					act.ui_handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							spAlbum.setSelection(idx);
						}
					},60);
					return;
				}
				spAlbum.setSelection(0);
			}
		}
	};


//	public ImgurAccount getSelectedAccount() {
//		return get_account(spAccount.getSelectedItemPosition());
//	}
//
//	private ImgurAccount get_account(int position){
//		ImgurAccount item = (ImgurAccount)account_adapter.getItem(position);
//		return item;
//	}

	public ImgurAlbum getSelectedAlbum() {
		return (ImgurAlbum)album_adapter.getItem(spAlbum.getSelectedItemPosition()); 
	}

	public ImgurAccount getSelectedAccount() {
		return (ImgurAccount)account_adapter.getItem(spAccount.getSelectedItemPosition()); 
	}

	public void reloadAccount() {
		account_adapter.reload();
	}



}
