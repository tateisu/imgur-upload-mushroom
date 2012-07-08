package jp.juggler.ImgurMush;

import java.util.ArrayList;

import android.app.AlertDialog.Builder;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.preference.ListPreference;
import android.util.AttributeSet;

public class Pref_RemoveAccount extends ListPreference{
	ContentResolver cr;
	Context context;
	
	public Pref_RemoveAccount(Context context) {
		super(context);
		this.context = context;
	}

	public Pref_RemoveAccount(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
	}


	void setup(){
		cr = context.getContentResolver();

		ArrayList<ImgurAccount> list = new ArrayList<ImgurAccount>();
		
		Cursor c = cr.query(ImgurAccount.meta.uri,null,null,null,ImgurAccount.COL_NAME+" asc");
		if( c!= null ){
			try{
				int colidx_id   = c.getColumnIndex(ImgurAccount.COL_ID);
				int colidx_name = c.getColumnIndex(ImgurAccount.COL_NAME);
				while( c.moveToNext() ){
					ImgurAccount item = new ImgurAccount();
					item.id = c.getLong(colidx_id);
					item.name = c.getString(colidx_name);
					list.add(item);
				}
			}finally{
				c.close();
			}
		}
		int count = list.size();
		String[] list_e = new String[count];
		String[] list_v = new String[list.size()];
		for(int i=0;i<count;++i){
			ImgurAccount item = list.get(i);
			list_e[i]= item.name;
			list_v[i]= Long.toString(item.id );
		}
		setEntries( list_e );
		setEntryValues( list_v );
	}
	

	@Override
	protected boolean callChangeListener(Object newValue) {
		cr.delete(Uri.withAppendedPath(ImgurAccount.meta.uri,"/"+ newValue),null,null);
		return false;
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder) {
		setup();
		super.onPrepareDialogBuilder(builder);
	}
	
}
