package jp.juggler.ImgurMush;

import java.util.ArrayList;

import jp.juggler.ImgurMush.data.ImgurAccount;

import android.app.AlertDialog.Builder;
import android.content.ContentResolver;
import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class Pref_RemoveAccount extends ListPreference{
	final Context context;
	final ContentResolver cr;

	public Pref_RemoveAccount(Context context) {
		super(context);
		this.context = context;
		this.cr = context.getContentResolver();
	}

	public Pref_RemoveAccount(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		this.cr = context.getContentResolver();
	}

	@Override
	protected boolean callChangeListener(Object newValue) {
		cr.delete(ImgurAccount.meta.uriFromId(Long.parseLong((String)newValue,10)),null,null);
		return false;
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder) {
		setup();
		super.onPrepareDialogBuilder(builder);
	}

	void setup(){
		ArrayList<ImgurAccount> list = ImgurAccount.loadAll(cr,null);
		// 選択肢キャプションと値の配列に分ける
		int count = list.size();
		String[] list_e = new String[count];
		String[] list_v = new String[count];
		for(int i=0;i<count;++i){
			ImgurAccount item = list.get(i);
			list_e[i]= item.name;
			list_v[i]= Long.toString(item.id );
		}
		setEntries( list_e );
		setEntryValues( list_v );
	}
}
